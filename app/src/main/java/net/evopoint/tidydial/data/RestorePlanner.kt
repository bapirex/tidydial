package net.evopoint.tidydial.data

import net.evopoint.tidydial.model.PhoneRow

/**
 * What a restore will write, and why the other entries are left alone.
 *
 * @property changes rows to write, as `before` (the current number) `-> after` (the number from
 *   the backup). They go through the optimistic write, so a row edited after planning is skipped.
 * @property changedSince entries whose row holds a number the backup does not allow to replace:
 *   it was edited since (a changes backup), or changed into a different number (a snapshot).
 *   Also changes entries whose `before` and `after` are different numbers, which TidyDial never
 *   writes: the file was edited or damaged.
 * @property notFound entries whose row could not be found on this device.
 * @property alreadyRestored entries whose row already holds the backup's number.
 */
data class RestorePlan(
    val changes: List<JournalEntry>,
    val changedSince: Int,
    val notFound: Int,
    val alreadyRestored: Int,
)

/**
 * Matches backup entries to the current phone rows and applies the safety rules. Pure, so it is
 * tested on the JVM.
 *
 * A row is found by its `dataId` when that row belongs to the same raw contact of the same account,
 * with the same source id when both have one (true on the same contacts database, also after
 * reinstalling TidyDial). Otherwise, as after a re-sync or on a new phone where the same ids can
 * belong to someone else, it is searched among the rows of the same raw contact: by account and
 * source id for synced accounts, else by lookup key within the same account. Device-only contacts
 * on a new phone get new lookup keys, so they are reported as not found rather than guessed.
 *
 * Safety rules:
 * - A changes backup only restores a row that still holds exactly its `after`, so a later edit is
 *   never overwritten, and only when `before` is the same dialled number as `after`: TidyDial only
 *   ever reformats, so anything else comes from an edited or damaged file.
 * - A snapshot only restores a row whose number differs from the snapshot's but is the same
 *   dialled number ([PhoneNumberFormatter.isSameDialledNumber]): only the formatting is reverted,
 *   and a number changed into a different one is never touched.
 */
object RestorePlanner {

    private data class SourceKey(val accountType: String, val accountName: String?, val sourceId: String)

    private class Target(val row: RowIdentity, val number: String, val expected: String?)

    /** [fallbackRegion] reads national numbers when the backup does not say its country. */
    fun plan(
        backup: BackupFile,
        rows: List<PhoneRow>,
        formatter: PhoneNumberFormatter,
        fallbackRegion: String,
    ): RestorePlan {
        val region = backup.countryIso ?: fallbackRegion
        val byId = rows.associateBy { it.dataId }
        val bySource = rows
            .filter { it.sourceId != null }
            .groupBy { SourceKey(it.accountType, it.accountName, it.sourceId!!) }
        val byLookup = rows.filter { it.lookupKey != null }.groupBy { it.lookupKey!! }
        val targets = when (backup) {
            is BackupFile.Changes -> backup.entries.map { Target(it.row, number = it.before, expected = it.after) }
            is BackupFile.Snapshot -> backup.entries.map { Target(it.row, number = it.number, expected = null) }
        }

        val claimed = HashSet<Long>()
        val changes = ArrayList<JournalEntry>()
        var changedSince = 0
        var notFound = 0
        var alreadyRestored = 0

        for (target in targets) {
            if (target.expected != null && !formatter.isSameDialledNumber(target.number, target.expected, region)) {
                changedSince++
                continue
            }
            val restorable = { row: PhoneRow ->
                row.dataId !in claimed && if (target.expected != null) {
                    row.number == target.expected
                } else {
                    row.number != target.number && formatter.isSameDialledNumber(row.number, target.number, region)
                }
            }
            val restore = { row: PhoneRow ->
                claimed += row.dataId
                changes += JournalEntry(row.dataId, before = row.number, after = target.number)
            }

            // A data row never moves to another raw contact or account, so a different one means
            // the id comes from another contacts database.
            val direct = byId[target.row.dataId]?.takeIf { it.isSameRowAs(target.row) }
            if (direct != null) {
                when {
                    direct.number == target.number -> alreadyRestored++
                    restorable(direct) -> restore(direct)
                    else -> changedSince++
                }
                continue
            }

            val identity = target.row
            val candidates = when {
                identity.sourceId != null ->
                    bySource[SourceKey(identity.accountType, identity.accountName, identity.sourceId)]

                identity.lookupKey != null -> byLookup[identity.lookupKey]?.filter {
                    it.accountType == identity.accountType && it.accountName == identity.accountName
                }

                else -> null
            }.orEmpty().sortedBy { it.dataId }
            val match = candidates.firstOrNull(restorable)
            when {
                match != null -> restore(match)
                candidates.any { it.number == target.number } -> alreadyRestored++
                // The number was edited into another one, or removed from the contact.
                candidates.isNotEmpty() -> changedSince++
                else -> notFound++
            }
        }
        return RestorePlan(changes, changedSince, notFound, alreadyRestored)
    }

    /** A source id is assigned on the first sync, so it only has to agree when both have one. */
    private fun PhoneRow.isSameRowAs(identity: RowIdentity): Boolean =
        rawContactId == identity.rawContactId &&
            accountType == identity.accountType &&
            accountName == identity.accountName &&
            (sourceId == null || identity.sourceId == null || sourceId == identity.sourceId)
}
