package net.evopoint.tidydial.data

import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.PhoneRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class RestorePlannerTest {

    private val formatter = PhoneNumberFormatter()
    private val createdAt = OffsetDateTime.parse("2026-10-31T09:04:12Z")

    private fun row(
        dataId: Long,
        number: String,
        rawContactId: Long = 10,
        lookupKey: String? = "lookup-1",
        accountType: String = GOOGLE,
        accountName: String? = ACCOUNT,
        sourceId: String? = "source-10",
    ) = PhoneRow(dataId, rawContactId, lookupKey, accountType, accountName, sourceId, "Avery Sample", number)

    private fun PhoneRow.identity() =
        RowIdentity(dataId, rawContactId, lookupKey, accountType, accountName, sourceId, displayName)

    private fun changes(vararg entries: ChangeEntry, countryIso: String? = "US") =
        BackupFile.Changes(createdAt, "1.2.0", countryIso, entries.toList())

    private fun snapshot(vararg entries: SnapshotEntry, countryIso: String? = "US") =
        BackupFile.Snapshot(createdAt, "1.2.0", countryIso, entries.toList())

    private fun plan(backup: BackupFile, rows: List<PhoneRow>, fallbackRegion: String = "US") =
        RestorePlanner.plan(backup, rows, formatter, fallbackRegion)

    // region Changes backups

    @Test
    fun changedRowIsRestored() {
        val backedUp = row(101, "2125550101")
        val plan = plan(
            changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")),
            listOf(backedUp.copy(number = "+1 212-555-0101")),
        )
        assertEquals(RestorePlan(listOf(JournalEntry(101, "+1 212-555-0101", "2125550101")), 0, 0, 0), plan)
    }

    @Test
    fun rowEditedSinceIsKept() {
        val backedUp = row(101, "2125550101")
        val plan = plan(
            changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")),
            // Even the same number, formatted differently: only the exact "after" is replaced.
            listOf(backedUp.copy(number = "+1 (212) 555-0101")),
        )
        assertEquals(RestorePlan(emptyList(), changedSince = 1, notFound = 0, alreadyRestored = 0), plan)
    }

    @Test
    fun rowThatAlreadyHoldsTheOldNumberIsCountedAsRestored() {
        val backedUp = row(101, "2125550101")
        val plan = plan(changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")), listOf(backedUp))
        assertEquals(RestorePlan(emptyList(), changedSince = 0, notFound = 0, alreadyRestored = 1), plan)
    }

    @Test
    fun missingRowIsNotFound() {
        val backedUp = row(101, "2125550101")
        val plan = plan(
            changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")),
            listOf(row(999, "+1 415-555-0132", rawContactId = 99, lookupKey = "lookup-9", sourceId = "source-99")),
        )
        assertEquals(RestorePlan(emptyList(), changedSince = 0, notFound = 1, alreadyRestored = 0), plan)
    }

    @Test
    fun removedNumberOfAContactThatStillExistsCountsAsChanged() {
        val backedUp = row(101, "2125550101")
        val plan = plan(
            changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")),
            listOf(row(102, "+1 212-555-0102")),
        )
        assertEquals(RestorePlan(emptyList(), changedSince = 1, notFound = 0, alreadyRestored = 0), plan)
    }

    @Test
    fun resyncedRowIsFoundByAccountAndSourceId() {
        val backedUp = row(101, "2125550101")
        // After a re-sync the provider gave every row and raw contact new ids.
        val resynced = listOf(
            row(5001, "+1 212-555-0102", rawContactId = 500, lookupKey = "new-key"),
            row(5002, "+1 212-555-0101", rawContactId = 500, lookupKey = "new-key"),
        )
        val plan = plan(changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")), resynced)
        assertEquals(listOf(JournalEntry(5002, "+1 212-555-0101", "2125550101")), plan.changes)
    }

    @Test
    fun sourceIdOfAnotherAccountDoesNotMatch() {
        val backedUp = row(101, "2125550101")
        val otherAccount = row(5002, "+1 212-555-0101", rawContactId = 500, accountName = "other@example.com")
        val plan = plan(changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")), listOf(otherAccount))
        assertEquals(1, plan.notFound)
    }

    @Test
    fun deviceContactIsFoundByLookupKeyInTheSameAccount() {
        val backedUp = row(601, "6175550199", rawContactId = 60, lookupKey = "lookup-6", accountType = "", accountName = null, sourceId = null)
        val rows = listOf(
            // The same lookup key in another account is a different raw contact of the same person.
            row(7001, "+1 617-555-0199", rawContactId = 700, lookupKey = "lookup-6"),
            row(7002, "+1 617-555-0199", rawContactId = 701, lookupKey = "lookup-6", accountType = "", accountName = null, sourceId = null),
        )
        val plan = plan(changes(ChangeEntry(backedUp.identity(), "6175550199", "+1 617-555-0199")), rows)
        assertEquals(listOf(JournalEntry(7002, "+1 617-555-0199", "6175550199")), plan.changes)
    }

    @Test
    fun entryWithoutIdentityIsNotFound() {
        val backedUp = row(601, "6175550199", rawContactId = 60, lookupKey = null, accountType = "", accountName = null, sourceId = null)
        val plan = plan(
            changes(ChangeEntry(backedUp.identity(), "6175550199", "+1 617-555-0199")),
            listOf(row(7002, "+1 617-555-0199", rawContactId = 701, lookupKey = null, accountType = "", accountName = null, sourceId = null)),
        )
        assertEquals(1, plan.notFound)
    }

    @Test
    fun sameDataIdOfAnotherRawContactIsNotTrusted() {
        val backedUp = row(101, "2125550101")
        val rows = listOf(
            // Id 101 is now someone else's number (another contacts database).
            row(101, "+1 212-555-0101", rawContactId = 77, lookupKey = "lookup-7", sourceId = "source-77"),
            row(5002, "+1 212-555-0101", rawContactId = 500),
        )
        val plan = plan(changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")), rows)
        assertEquals(listOf(JournalEntry(5002, "+1 212-555-0101", "2125550101")), plan.changes)
    }

    @Test
    fun sameIdsInAnotherAccountAreNotTrusted() {
        val backedUp = row(101, "2125550101")
        val rows = listOf(
            // New phone: raw contact 10 / row 101 is someone else's, in another account.
            row(101, "+1 212-555-0101", accountName = "someone@example.com", lookupKey = "lookup-7", sourceId = "source-77"),
            // The real row, found again by its account and source id.
            row(5002, "+1 212-555-0101", rawContactId = 500, lookupKey = "new-key"),
        )
        val plan = plan(changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")), rows)
        assertEquals(RestorePlan(listOf(JournalEntry(5002, "+1 212-555-0101", "2125550101")), 0, 0, 0), plan)
    }

    @Test
    fun sameIdsWithAnotherSourceIdAreNotTrusted() {
        val backedUp = row(101, "2125550101")
        val rows = listOf(
            // Contacts storage was cleared: the ids start again, the same account, another contact.
            row(101, "+1 646-555-0177", lookupKey = "lookup-7", sourceId = "source-77"),
            row(5002, "+1 212-555-0101", rawContactId = 500, lookupKey = "new-key"),
        )
        val plan = plan(changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")), rows)
        assertEquals(listOf(JournalEntry(5002, "+1 212-555-0101", "2125550101")), plan.changes)
        assertEquals(0, plan.changedSince)
    }

    @Test
    fun sourceIdAssignedSinceTheBackupStillMatches() {
        // Backed up before the contact's first sync, which gives the raw contact its source id.
        val backedUp = row(101, "2125550101", sourceId = null)
        val plan = plan(
            changes(ChangeEntry(backedUp.identity(), "2125550101", "+1 212-555-0101")),
            listOf(row(101, "+1 212-555-0101")),
        )
        assertEquals(listOf(JournalEntry(101, "+1 212-555-0101", "2125550101")), plan.changes)
    }

    @Test
    fun changeIntoADifferentNumberIsNeverRestored() {
        // TidyDial only reformats: this entry comes from an edited or damaged file.
        val backedUp = row(101, "2125550101")
        val plan = plan(
            changes(ChangeEntry(backedUp.identity(), "2125550100", "+1 212-555-0199")),
            listOf(row(101, "+1 212-555-0199")),
        )
        assertEquals(RestorePlan(emptyList(), changedSince = 1, notFound = 0, alreadyRestored = 0), plan)
    }

    @Test
    fun everyChangeTheFormatterMakesCanBeRestored() {
        val originals = listOf("2125550101", "(212) 555-0102 x12", "212.555.0103;34", "+442079460958", "1-800-555-0104")
        val config = FormatConfig(countryIso = "US", extensionDelimiter = ExtensionDelimiter.COMMA)
        originals.forEachIndexed { index, original ->
            val formatted = formatter.format(original, config)
            assertTrue(original, formatted.isModified)
            val backedUp = row(101L + index, original)
            val plan = plan(
                changes(ChangeEntry(backedUp.identity(), original, formatted.formattedNumber)),
                listOf(backedUp.copy(number = formatted.formattedNumber)),
            )
            assertEquals(original, listOf(JournalEntry(backedUp.dataId, formatted.formattedNumber, original)), plan.changes)
        }
    }

    @Test
    fun aRowIsOnlyWrittenOnce() {
        val first = row(101, "2125550101")
        val plan = plan(
            changes(
                ChangeEntry(first.identity(), "2125550101", "+1 212-555-0101"),
                // A second entry for the same row from a later id (re-synced, then backed up again).
                ChangeEntry(first.identity().copy(dataId = 909, rawContactId = 90), "212 555 0101", "+1 212-555-0101"),
            ),
            listOf(first.copy(number = "+1 212-555-0101")),
        )
        assertEquals(listOf(JournalEntry(101, "+1 212-555-0101", "2125550101")), plan.changes)
        assertEquals(1, plan.changedSince)
    }

    // endregion

    // region Snapshots

    @Test
    fun snapshotRevertsOnlyTheFormatting() {
        val rows = listOf(
            row(101, "+1 212-555-0101"),
            row(102, "2125550102"),
            row(103, "+1 212-555-0199"),
        )
        val plan = plan(
            snapshot(
                SnapshotEntry(rows[0].identity(), "2125550101"),
                // Never changed.
                SnapshotEntry(rows[1].identity(), "2125550102"),
                // Changed into a different number since: kept.
                SnapshotEntry(rows[2].identity(), "2125550103"),
            ),
            rows,
        )
        assertEquals(
            RestorePlan(
                changes = listOf(JournalEntry(101, "+1 212-555-0101", "2125550101")),
                changedSince = 1,
                notFound = 0,
                alreadyRestored = 1,
            ),
            plan,
        )
    }

    @Test
    fun snapshotKeepsADifferentExtension() {
        val rows = listOf(row(501, "+1 310-555-0142;34"), row(502, "+1 310-555-0143;12"))
        val plan = plan(
            snapshot(
                SnapshotEntry(rows[0].identity(), "3105550142,12"),
                // Same extension, another delimiter: only formatting.
                SnapshotEntry(rows[1].identity(), "3105550143 ext. 12"),
            ),
            rows,
        )
        assertEquals(listOf(JournalEntry(502, "+1 310-555-0143;12", "3105550143 ext. 12")), plan.changes)
        assertEquals(1, plan.changedSince)
    }

    @Test
    fun snapshotReadsNationalNumbersInItsOwnCountry() {
        val rows = listOf(row(201, "+44 20 7946 0958"))
        val entry = SnapshotEntry(rows[0].identity(), "020 7946 0958")
        // Written in the UK, restored with the country now set to the US.
        assertEquals(1, plan(snapshot(entry, countryIso = "GB"), rows, fallbackRegion = "US").changes.size)
        // Read as a US number it is a different one, so it is kept.
        assertEquals(0, plan(snapshot(entry, countryIso = "US"), rows, fallbackRegion = "GB").changes.size)
        // A backup without a country uses the current one.
        assertEquals(1, plan(snapshot(entry, countryIso = null), rows, fallbackRegion = "GB").changes.size)
    }

    @Test
    fun snapshotIncludesRowsOfSkippedAccounts() {
        val whatsApp = row(1402, "+1 646-555-0144", rawContactId = 140, accountType = "com.whatsapp", sourceId = "wa-140")
        val plan = plan(snapshot(SnapshotEntry(whatsApp.identity(), "6465550144")), listOf(whatsApp))
        assertEquals(listOf(JournalEntry(1402, "+1 646-555-0144", "6465550144")), plan.changes)
    }

    @Test
    fun resyncedSnapshotRowIsMatchedByNumber() {
        val backedUp = listOf(row(101, "2125550101"), row(102, "2125550102"))
        val resynced = listOf(
            row(5001, "+1 212-555-0102", rawContactId = 500),
            row(5002, "+1 212-555-0101", rawContactId = 500),
        )
        val plan = plan(
            snapshot(SnapshotEntry(backedUp[0].identity(), "2125550101"), SnapshotEntry(backedUp[1].identity(), "2125550102")),
            resynced,
        )
        assertEquals(
            listOf(
                JournalEntry(5002, "+1 212-555-0101", "2125550101"),
                JournalEntry(5001, "+1 212-555-0102", "2125550102"),
            ),
            plan.changes,
        )
    }

    // endregion

    private companion object {
        const val GOOGLE = "com.google"
        const val ACCOUNT = "avery@example.com"
    }
}
