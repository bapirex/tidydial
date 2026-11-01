package net.evopoint.tidydial.data

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.evopoint.tidydial.model.PhoneEntry
import net.evopoint.tidydial.model.PhoneRow
import java.io.IOException
import java.time.Clock
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Writes backup files before an update or a restore, and prepares restores from them.
 *
 * Backups complement the undo journal: the journal lives in TidyDial's private storage and is
 * lost on uninstall, a backup lives in a folder the user picked and is not. Neither undo nor a
 * restore ever needs a backup folder.
 */
class BackupManager(
    private val store: BackupStore,
    private val settings: SettingsRepository,
    private val contacts: ContactsDataSource,
    private val appVersion: String,
    private val formatter: PhoneNumberFormatter,
    /** Read on every backup: file names and `createdAt` follow the device's current time zone. */
    private val clock: () -> Clock = Clock::systemDefaultZone,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** The largest file written or read, so every backup TidyDial writes can be restored. */
    private val maxFileBytes: Int = MAX_FILE_BYTES,
) {
    sealed interface WriteOutcome {
        /** Backups are turned off; nothing was written. */
        data object Off : WriteOutcome

        /** [names] of the files written, the snapshot first. */
        data class Written(val names: List<String>) : WriteOutcome

        /** The backup could not be written, so the update must not run. */
        data class Failed(val failure: BackupFailure) : WriteOutcome

        data object PermissionDenied : WriteOutcome
    }

    sealed interface RestoreOutcome {
        data class Ready(val backup: BackupFile, val plan: RestorePlan) : RestoreOutcome

        data class Invalid(val error: BackupReadError) : RestoreOutcome

        /** The current numbers could not be read to compare them with the backup. */
        data object ContactsFailed : RestoreOutcome

        data object PermissionDenied : RestoreOutcome
    }

    /**
     * Call before any contact is written, like the undo journal: a snapshot of every phone number
     * when the folder holds none yet, then the changes of [entries] (written as
     * `originalNumber -> formattedNumber`). Nothing is written when backups are off.
     *
     * The snapshot holds every phone row of every account, skipped accounts included: any account
     * can be included later, so this covers every number TidyDial could ever change. It is
     * written again for each folder pick (the same folder too) and after the folder went missing,
     * so a folder that was deleted or replaced does not take the only snapshot with it. A snapshot
     * written after earlier updates already holds their changes.
     */
    suspend fun backUpBeforeUpdate(entries: List<PhoneEntry>, countryIso: String): WriteOutcome =
        backUp(countryIso, withSnapshot = true) { byId ->
            entries
                .filter { it.formattedNumber != it.originalNumber }
                .distinctBy { it.dataId }
                .map { entry ->
                    // A row deleted since the list was loaded keeps what the list knew; the
                    // update skips it anyway.
                    val identity = byId[entry.dataId]?.identity()
                        ?: RowIdentity(entry.dataId, entry.rawContactId, accountType = entry.accountType)
                    ChangeEntry(identity, before = entry.originalNumber, after = entry.formattedNumber)
                }
        }

    /**
     * Call before a restore writes [changes] (`current -> restored`): a changes file, so the
     * numbers a restore replaces can be brought back after an uninstall too. Restoring that file
     * reverts the restore. No snapshot. Nothing is written when backups are off, and the caller
     * goes on with the restore when this fails: a restore must work without a backup folder.
     */
    suspend fun backUpBeforeRestore(changes: List<JournalEntry>, countryIso: String): WriteOutcome =
        backUp(countryIso, withSnapshot = false) { byId ->
            // A row deleted since the restore was planned is not written, so it needs no entry.
            changes.mapNotNull { change ->
                byId[change.dataId]?.let { ChangeEntry(it.identity(), before = change.before, after = change.after) }
            }
        }

    private suspend fun backUp(
        countryIso: String,
        withSnapshot: Boolean,
        changesOf: (Map<Long, PhoneRow>) -> List<ChangeEntry>,
    ): WriteOutcome {
        val prefs = settings.backup.first()
        if (!prefs.enabled) return WriteOutcome.Off
        val folder = prefs.folderUri ?: return WriteOutcome.Failed(BackupFailure.NO_FOLDER)
        if (!store.hasAccess(folder)) return WriteOutcome.Failed(BackupFailure.NO_ACCESS)
        val rows = when (val result = contacts.readPhoneRows()) {
            is PhoneRowsResult.Success -> result.rows
            PhoneRowsResult.PermissionDenied -> return WriteOutcome.PermissionDenied
            is PhoneRowsResult.Error -> return WriteOutcome.Failed(BackupFailure.READ_CONTACTS_FAILED)
        }
        // Read on every backup, so a time zone change while the app runs is followed.
        val now = OffsetDateTime.now(clock()).truncatedTo(ChronoUnit.SECONDS)
        val names = ArrayList<String>(2)

        if (withSnapshot && prefs.snapshotFolderUri != folder) {
            val snapshot = BackupFile.Snapshot(
                createdAt = now,
                appVersion = appVersion,
                countryIso = countryIso,
                entries = rows.map { SnapshotEntry(it.identity(), it.number) },
            )
            when (val result = write(folder, BackupNames.snapshot(now.toLocalDateTime()), snapshot)) {
                is BackupWriteResult.Failed -> return failed(result.failure)
                is BackupWriteResult.Written -> names += result.displayName
            }
            try {
                settings.markSnapshotWritten(folder)
            } catch (e: IOException) {
                // At worst the next backup writes another snapshot.
                Log.w(TAG, "Could not remember the snapshot", e)
            }
        }

        val changes = BackupFile.Changes(
            createdAt = now,
            appVersion = appVersion,
            countryIso = countryIso,
            entries = changesOf(rows.associateBy { it.dataId }),
        )
        return when (val result = write(folder, BackupNames.changes(now.toLocalDateTime()), changes)) {
            is BackupWriteResult.Failed -> failed(result.failure)
            is BackupWriteResult.Written -> WriteOutcome.Written(names + result.displayName)
        }
    }

    /**
     * A folder that went missing took its snapshot with it. Made again under the same name it
     * keeps its URI (and may keep its grant), so the snapshot is forgotten here, not only when the
     * user picks a folder.
     */
    private suspend fun failed(failure: BackupFailure): WriteOutcome {
        if (failure == BackupFailure.FOLDER_MISSING) {
            try {
                settings.forgetSnapshot()
            } catch (e: IOException) {
                Log.w(TAG, "Could not forget the snapshot", e)
            }
        }
        return WriteOutcome.Failed(failure)
    }

    /**
     * Reads the backup the user picked and plans what restoring it would write. Works without a
     * backup folder, so also right after a reinstall.
     */
    suspend fun prepareRestore(documentUri: String, countryIso: String): RestoreOutcome {
        val text = try {
            store.read(documentUri, maxFileBytes)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the backup", e)
            return RestoreOutcome.Invalid(BackupReadError.UNREADABLE)
        }
        val backup = try {
            withContext(defaultDispatcher) { BackupCodec.decode(text) }
        } catch (e: BackupFormatException) {
            Log.w(TAG, "Not a usable backup: ${e.message}")
            return RestoreOutcome.Invalid(e.error)
        }
        val rows = when (val result = contacts.readPhoneRows()) {
            is PhoneRowsResult.Success -> result.rows
            PhoneRowsResult.PermissionDenied -> return RestoreOutcome.PermissionDenied
            is PhoneRowsResult.Error -> return RestoreOutcome.ContactsFailed
        }
        val plan = withContext(defaultDispatcher) { RestorePlanner.plan(backup, rows, formatter, countryIso) }
        return RestoreOutcome.Ready(backup, plan)
    }

    /** A file too large for [prepareRestore] to read back is not written: it could never be restored. */
    private suspend fun write(folder: String, name: String, file: BackupFile): BackupWriteResult {
        val bytes = withContext(defaultDispatcher) { BackupCodec.encode(file).toByteArray(Charsets.UTF_8) }
        if (bytes.size > maxFileBytes) {
            Log.w(TAG, "$name would take ${bytes.size} bytes, more than $maxFileBytes")
            return BackupWriteResult.Failed(BackupFailure.TOO_LARGE)
        }
        return store.write(folder, name, bytes)
    }

    private fun PhoneRow.identity() = RowIdentity(
        dataId = dataId,
        rawContactId = rawContactId,
        lookupKey = lookupKey,
        accountType = accountType,
        accountName = accountName,
        sourceId = sourceId,
        displayName = displayName,
    )

    companion object {
        private const val TAG = "BackupManager"

        /**
         * The largest backup written or read. A snapshot takes about 320 bytes per phone row
         * (pretty-printed, Latin names), so this fits about 50,000 rows, fewer with names in
         * scripts that take more bytes: about twice Google's limit of 25,000 contacts per account,
         * while the text and its JSON tree (about five times the file) stay within a phone's heap.
         * Larger files are not written, as they could not be restored.
         */
        const val MAX_FILE_BYTES = 16 * 1024 * 1024
    }
}
