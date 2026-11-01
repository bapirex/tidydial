package net.evopoint.tidydial.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.evopoint.tidydial.model.CallOption
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.PhoneEntry
import net.evopoint.tidydial.model.PhoneRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

// Robolectric only for a working org.json.
@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = FakeBackupStore()
    private val settings = SettingsRepository(InMemoryPreferences(), CountryCatalog()) { listOf("us") }

    // 09:04:12 in Paris, which is UTC+1 on 31 October 2026.
    private var clock = Clock.fixed(Instant.parse("2026-10-31T08:04:12Z"), ZoneId.of("Europe/Paris"))

    private val rows = listOf(
        PhoneRow(101, 10, "lookup-1", "com.google", "avery@example.com", "source-10", "Avery Sample", "2125550101"),
        PhoneRow(601, 60, "lookup-6", "", null, null, "Frankie Fictional", "6175550199"),
        // A skipped messaging account: still in the snapshot.
        PhoneRow(1402, 140, "lookup-14", "com.whatsapp", "+16465550144", "wa-140", "Dana Testperson", "6465550144"),
    )

    private val contacts = FakeRows()
    private val manager = BackupManager(store, settings, contacts, "1.2.0", PhoneNumberFormatter(), { clock }, dispatcher)

    private val entries = listOf(
        entry(101, 10, "2125550101", "+1 212-555-0101", "com.google"),
        entry(601, 60, "6175550199", "+1 617-555-0199", ""),
        // Already formatted: not a change.
        entry(301, 30, "+1 415-555-0132", "+1 415-555-0132", "com.google"),
    )

    private fun entry(dataId: Long, rawContactId: Long, original: String, formatted: String, accountType: String) =
        PhoneEntry(
            dataId = dataId,
            rawContactId = rawContactId,
            contactId = rawContactId / 10,
            displayName = "Sample",
            originalNumber = original,
            formattedNumber = formatted,
            typeLabel = "Mobile",
            accountType = accountType,
            accountLabel = "Google",
            isValid = true,
            isModified = original != formatted,
        )

    private suspend fun backupsOn() {
        settings.setBackupFolder(FOLDER, "TidyDial backups")
        store.access += FOLDER
    }

    private fun file(index: Int): BackupFile = BackupCodec.decode(store.files(FOLDER)[index].content)

    // region Writing

    @Test
    fun nothingIsWrittenWhileOff() = runTest(dispatcher) {
        settings.setBackupEnabled(false)
        assertEquals(BackupManager.WriteOutcome.Off, manager.backUpBeforeUpdate(entries, "US"))
        assertTrue(store.folders.isEmpty())
        assertEquals(0, contacts.reads)
    }

    @Test
    fun firstBackupWritesTheSnapshotThenTheChanges() = runTest(dispatcher) {
        backupsOn()

        val outcome = manager.backUpBeforeUpdate(entries, "US")

        val names = listOf("TidyDial snapshot 2026-10-31 090412.json", "TidyDial backup 2026-10-31 090412.json")
        assertEquals(BackupManager.WriteOutcome.Written(names), outcome)
        assertEquals(names, store.files(FOLDER).map { it.name })
        assertEquals(FOLDER, settings.backup.first().snapshotFolderUri)

        val snapshot = file(0) as BackupFile.Snapshot
        assertEquals(OffsetDateTime.parse("2026-10-31T09:04:12+01:00"), snapshot.createdAt)
        assertEquals("1.2.0", snapshot.appVersion)
        assertEquals("US", snapshot.countryIso)
        // Every account, skipped ones too.
        assertEquals(listOf(101L, 601L, 1402L), snapshot.entries.map { it.row.dataId })
        assertEquals("6465550144", snapshot.entries.last().number)
    }

    @Test
    fun changesCarryTheRowIdentity() = runTest(dispatcher) {
        backupsOn()
        manager.backUpBeforeUpdate(entries, "US")

        val changes = file(1) as BackupFile.Changes
        assertEquals(
            listOf(
                ChangeEntry(
                    RowIdentity(101, 10, "lookup-1", "com.google", "avery@example.com", "source-10", "Avery Sample"),
                    before = "2125550101",
                    after = "+1 212-555-0101",
                ),
                ChangeEntry(
                    RowIdentity(601, 60, "lookup-6", "", null, null, "Frankie Fictional"),
                    before = "6175550199",
                    after = "+1 617-555-0199",
                ),
            ),
            changes.entries,
        )
    }

    @Test
    fun rowDeletedSinceTheLoadKeepsWhatTheListKnew() = runTest(dispatcher) {
        backupsOn()
        contacts.rows = rows.filter { it.dataId != 601L }
        manager.backUpBeforeUpdate(entries, "US")

        val frankie = (file(1) as BackupFile.Changes).entries.single { it.row.dataId == 601L }
        assertEquals(RowIdentity(601, 60), frankie.row)
    }

    @Test
    fun laterBackupsOnlyWriteTheChanges() = runTest(dispatcher) {
        backupsOn()
        manager.backUpBeforeUpdate(entries, "US")

        val outcome = manager.backUpBeforeUpdate(entries, "US")

        // Same second: the name gets a suffix instead of replacing the first backup.
        val name = "TidyDial backup 2026-10-31 090412 (2).json"
        assertEquals(BackupManager.WriteOutcome.Written(listOf(name)), outcome)
        assertEquals(3, store.files(FOLDER).size)
        assertTrue(file(2) is BackupFile.Changes)
    }

    @Test
    fun noFolderAccessWritesNothing() = runTest(dispatcher) {
        backupsOn()
        store.access.clear()
        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.NO_ACCESS),
            manager.backUpBeforeUpdate(entries, "US"),
        )
        assertTrue(store.files(FOLDER).isEmpty())
        assertEquals(0, contacts.reads)
    }

    @Test
    fun enabledWithoutAFolderFails() = runTest(dispatcher) {
        settings.setBackupEnabled(true)
        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.NO_FOLDER),
            manager.backUpBeforeUpdate(entries, "US"),
        )
    }

    @Test
    fun failedSnapshotStopsBeforeTheChanges() = runTest(dispatcher) {
        backupsOn()
        store.failWriteOf = "TidyDial snapshot"

        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.WRITE_FAILED),
            manager.backUpBeforeUpdate(entries, "US"),
        )
        assertTrue(store.files(FOLDER).isEmpty())
        assertNull(settings.backup.first().snapshotFolderUri)
    }

    @Test
    fun failedChangesAreReported() = runTest(dispatcher) {
        backupsOn()
        store.failWriteOf = "TidyDial backup"

        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.WRITE_FAILED),
            manager.backUpBeforeUpdate(entries, "US"),
        )
        // The snapshot is complete and stays; the next backup to this folder does not repeat it.
        assertEquals(FOLDER, settings.backup.first().snapshotFolderUri)
        store.failWriteOf = null
        assertEquals(
            BackupManager.WriteOutcome.Written(listOf("TidyDial backup 2026-10-31 090412.json")),
            manager.backUpBeforeUpdate(entries, "US"),
        )
    }

    @Test
    fun newFolderGetsItsOwnSnapshot() = runTest(dispatcher) {
        backupsOn()
        manager.backUpBeforeUpdate(entries, "US")

        // Folder A was deleted, or the changes file failed there, and the user picked folder B.
        settings.setBackupFolder(OTHER_FOLDER, "Other folder")
        store.access += OTHER_FOLDER
        val outcome = manager.backUpBeforeUpdate(entries, "US")

        val names = listOf("TidyDial snapshot 2026-10-31 090412.json", "TidyDial backup 2026-10-31 090412.json")
        assertEquals(BackupManager.WriteOutcome.Written(names), outcome)
        assertEquals(names, store.files(OTHER_FOLDER).map { it.name })
        assertEquals(OTHER_FOLDER, settings.backup.first().snapshotFolderUri)
        val snapshot = BackupCodec.decode(store.files(OTHER_FOLDER)[0].content) as BackupFile.Snapshot
        assertEquals(listOf(101L, 601L, 1402L), snapshot.entries.map { it.row.dataId })
    }

    @Test
    fun pickingTheSameFolderAgainWritesANewSnapshot() = runTest(dispatcher) {
        backupsOn()
        manager.backUpBeforeUpdate(entries, "US")

        // The folder was emptied, or deleted and made again under the same name (same tree URI),
        // and the user picked it again.
        store.folders.getValue(FOLDER).clear()
        settings.setBackupFolder(FOLDER, "TidyDial backups")
        val outcome = manager.backUpBeforeUpdate(entries, "US")

        val names = listOf("TidyDial snapshot 2026-10-31 090412.json", "TidyDial backup 2026-10-31 090412.json")
        assertEquals(BackupManager.WriteOutcome.Written(names), outcome)
        assertEquals(FOLDER, settings.backup.first().snapshotFolderUri)
    }

    @Test
    fun aMissingFolderTakesItsSnapshotWithIt() = runTest(dispatcher) {
        backupsOn()
        manager.backUpBeforeUpdate(entries, "US")

        store.failWrite = BackupFailure.FOLDER_MISSING
        manager.backUpBeforeUpdate(entries, "US")
        assertNull(settings.backup.first().snapshotFolderUri)

        // Made again under the same name, the folder keeps its URI and grant: no new pick needed.
        store.failWrite = null
        store.folders.getValue(FOLDER).clear()
        val outcome = manager.backUpBeforeUpdate(entries, "US") as BackupManager.WriteOutcome.Written
        assertEquals("TidyDial snapshot 2026-10-31 090412.json", outcome.names.first())
    }

    @Test
    fun aBackupTooLargeToRestoreIsNotWritten() = runTest(dispatcher) {
        val snapshotBytes = snapshotOfRows().length
        val small = BackupManager(
            store, settings, contacts, "1.2.0", PhoneNumberFormatter(), { clock }, dispatcher, snapshotBytes - 1,
        )
        backupsOn()

        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.TOO_LARGE),
            small.backUpBeforeUpdate(entries, "US"),
        )
        assertTrue(store.files(FOLDER).isEmpty())
        assertNull(settings.backup.first().snapshotFolderUri)
    }

    @Test
    fun aBackupAtTheLimitIsWrittenAndRestored() = runTest(dispatcher) {
        // Writer and reader share the limit: whatever is written can be read back.
        val limited = BackupManager(
            store, settings, contacts, "1.2.0", PhoneNumberFormatter(), { clock }, dispatcher, snapshotOfRows().length,
        )
        backupsOn()

        val outcome = limited.backUpBeforeUpdate(entries, "US") as BackupManager.WriteOutcome.Written

        val snapshot = "$FOLDER/${outcome.names.first()}"
        assertTrue(limited.prepareRestore(snapshot, "US") is BackupManager.RestoreOutcome.Ready)
    }

    @Test
    fun aLargeBookFitsTheLimit() = runTest(dispatcher) {
        // 45,000 phone rows with realistic fields (a merged contact's lookup key, an email-style
        // account name): far more than most books, and still restorable.
        contacts.rows = List(45_000) { i ->
            PhoneRow(
                dataId = 100_000L + i,
                rawContactId = 50_000L + i,
                lookupKey = "3789r$i-2B4D3F2B4B4D2B.3789r${i + 1}-2B4D3F2B4B",
                accountType = "com.google",
                accountName = "avery.sample@example.com",
                sourceId = "5e1f0a9b8c7d6e5f",
                displayName = "Sample Person $i",
                number = "212555${(i % 10_000).toString().padStart(4, '0')}",
            )
        }
        backupsOn()

        val outcome = manager.backUpBeforeUpdate(emptyList(), "US") as BackupManager.WriteOutcome.Written

        val restore = manager.prepareRestore("$FOLDER/${outcome.names.first()}", "US")
        assertEquals(45_000, ((restore as BackupManager.RestoreOutcome.Ready).backup as BackupFile.Snapshot).size)
    }

    /** The snapshot [rows] encode to at 09:04:12, as [BackupManager] writes it. */
    private fun snapshotOfRows(): String = BackupCodec.encode(
        BackupFile.Snapshot(
            createdAt = OffsetDateTime.parse("2026-10-31T09:04:12+01:00"),
            appVersion = "1.2.0",
            countryIso = "US",
            entries = rows.map { SnapshotEntry(it.identity(), it.number) },
        ),
    )

    @Test
    fun fileNamesFollowATimeZoneChange() = runTest(dispatcher) {
        backupsOn()
        manager.backUpBeforeUpdate(entries, "US")

        // Same instant, but the device moved to New York (UTC-4 on this date) while TidyDial ran.
        clock = Clock.fixed(Instant.parse("2026-10-31T08:04:12Z"), ZoneId.of("America/New_York"))
        manager.backUpBeforeUpdate(entries, "US")

        assertEquals("TidyDial backup 2026-10-31 040412.json", store.files(FOLDER).last().name)
        assertEquals(OffsetDateTime.parse("2026-10-31T04:04:12-04:00"), file(2).createdAt)
    }

    @Test
    fun missingFolderIsReported() = runTest(dispatcher) {
        backupsOn()
        store.failWrite = BackupFailure.FOLDER_MISSING
        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.FOLDER_MISSING),
            manager.backUpBeforeUpdate(entries, "US"),
        )
    }

    @Test
    fun unreadableContactsFailTheBackup() = runTest(dispatcher) {
        backupsOn()
        contacts.result = PhoneRowsResult.Error(IllegalStateException("test"))
        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.READ_CONTACTS_FAILED),
            manager.backUpBeforeUpdate(entries, "US"),
        )

        contacts.result = PhoneRowsResult.PermissionDenied
        assertEquals(BackupManager.WriteOutcome.PermissionDenied, manager.backUpBeforeUpdate(entries, "US"))
        assertTrue(store.files(FOLDER).isEmpty())
    }

    @Test
    fun restoreIsBackedUpAsChangesWithoutASnapshot() = runTest(dispatcher) {
        backupsOn()
        // Current -> restored, as a restore plan has them; 999 was deleted since planning.
        val restore = listOf(
            JournalEntry(101, before = "2125550101", after = "(212) 555-0101"),
            JournalEntry(999, before = "+1 212-555-0199", after = "2125550199"),
        )

        val outcome = manager.backUpBeforeRestore(restore, "US")

        assertEquals(BackupManager.WriteOutcome.Written(listOf("TidyDial backup 2026-10-31 090412.json")), outcome)
        val changes = file(0) as BackupFile.Changes
        assertEquals(
            listOf(
                ChangeEntry(
                    RowIdentity(101, 10, "lookup-1", "com.google", "avery@example.com", "source-10", "Avery Sample"),
                    before = "2125550101",
                    after = "(212) 555-0101",
                ),
            ),
            changes.entries,
        )
        // A restore never takes the folder's snapshot slot.
        assertNull(settings.backup.first().snapshotFolderUri)
    }

    @Test
    fun restoreBackupFollowsTheSetting() = runTest(dispatcher) {
        val restore = listOf(JournalEntry(101, before = "2125550101", after = "(212) 555-0101"))
        settings.setBackupEnabled(false)
        assertEquals(BackupManager.WriteOutcome.Off, manager.backUpBeforeRestore(restore, "US"))

        backupsOn()
        store.failWrite = BackupFailure.FOLDER_MISSING
        assertEquals(
            BackupManager.WriteOutcome.Failed(BackupFailure.FOLDER_MISSING),
            manager.backUpBeforeRestore(restore, "US"),
        )
    }

    // endregion

    // region Restoring

    @Test
    fun restoreIsPlannedFromThePickedFile() = runTest(dispatcher) {
        store.documents[PICKED] = BackupCodec.encode(
            BackupFile.Changes(
                createdAt = OffsetDateTime.parse("2026-10-30T18:30:00+01:00"),
                appVersion = "1.1.0",
                countryIso = "US",
                entries = listOf(
                    // Holds "after": restored.
                    ChangeEntry(rows[0].identity(), "(212) 555-0101", "2125550101"),
                    // Holds neither "before" nor "after": edited since.
                    ChangeEntry(rows[1].identity(), "617-555-0199", "+1 617-555-0199"),
                    ChangeEntry(RowIdentity(999, 99), "2125550199", "+1 212-555-0199"),
                ),
            ),
        )

        val outcome = manager.prepareRestore(PICKED, "US") as BackupManager.RestoreOutcome.Ready

        assertEquals(BackupKind.CHANGES, outcome.backup.kind)
        assertEquals(
            RestorePlan(
                changes = listOf(JournalEntry(101, "2125550101", "(212) 555-0101")),
                changedSince = 1,
                notFound = 1,
                alreadyRestored = 0,
            ),
            outcome.plan,
        )
    }

    @Test
    fun unusableFilesAreReported() = runTest(dispatcher) {
        assertEquals(
            BackupManager.RestoreOutcome.Invalid(BackupReadError.UNREADABLE),
            manager.prepareRestore("content://picked/missing.json", "US"),
        )
        store.documents[PICKED] = """{"app":"TidyDial","format":9}"""
        assertEquals(
            BackupManager.RestoreOutcome.Invalid(BackupReadError.NEWER_FORMAT),
            manager.prepareRestore(PICKED, "US"),
        )
        store.documents[PICKED] = "Name,Phone\nAvery,2125550101"
        assertEquals(
            BackupManager.RestoreOutcome.Invalid(BackupReadError.NOT_A_BACKUP),
            manager.prepareRestore(PICKED, "US"),
        )
        assertEquals(0, contacts.reads)
    }

    @Test
    fun deeplyNestedFilesAreRejected() = runTest(dispatcher) {
        store.documents[PICKED] = "[".repeat(BackupManager.MAX_FILE_BYTES / 2)
        assertEquals(
            BackupManager.RestoreOutcome.Invalid(BackupReadError.NOT_A_BACKUP),
            manager.prepareRestore(PICKED, "US"),
        )
    }

    @Test
    fun tooLargeFilesAreNotRead() = runTest(dispatcher) {
        store.documents[PICKED] = " ".repeat(BackupManager.MAX_FILE_BYTES + 1)
        assertEquals(
            BackupManager.RestoreOutcome.Invalid(BackupReadError.UNREADABLE),
            manager.prepareRestore(PICKED, "US"),
        )
    }

    @Test
    fun restoreNeedsTheContacts() = runTest(dispatcher) {
        backupsOn()
        manager.backUpBeforeUpdate(entries, "US")
        val backup = "$FOLDER/TidyDial backup 2026-10-31 090412.json"

        contacts.result = PhoneRowsResult.PermissionDenied
        assertEquals(BackupManager.RestoreOutcome.PermissionDenied, manager.prepareRestore(backup, "US"))
        contacts.result = PhoneRowsResult.Error(IllegalStateException("test"))
        assertEquals(BackupManager.RestoreOutcome.ContactsFailed, manager.prepareRestore(backup, "US"))
    }

    // endregion

    private inner class FakeRows : ContactsDataSource {
        var rows: List<PhoneRow> = this@BackupManagerTest.rows
        var result: PhoneRowsResult? = null
        var reads = 0

        override suspend fun readPhoneRows(): PhoneRowsResult {
            reads++
            return result ?: PhoneRowsResult.Success(rows)
        }

        override suspend fun load(config: FormatConfig): LoadResult = throw UnsupportedOperationException()

        override suspend fun apply(entries: List<PhoneEntry>, onProgress: ProgressCallback): UpdateResult =
            throw UnsupportedOperationException()

        override suspend fun restore(journal: List<JournalEntry>, onProgress: ProgressCallback): UpdateResult =
            throw UnsupportedOperationException()

        override suspend fun write(changes: List<JournalEntry>, onProgress: ProgressCallback): UpdateResult =
            throw UnsupportedOperationException()

        override suspend fun callOptions(entry: PhoneEntry, regionIso: String): List<CallOption> =
            throw UnsupportedOperationException()
    }

    private class InMemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        const val FOLDER = "content://tree/TidyDial backups"
        const val OTHER_FOLDER = "content://tree/Other folder"
        const val PICKED = "content://downloads/picked.json"

        fun PhoneRow.identity() =
            RowIdentity(dataId, rawContactId, lookupKey, accountType, accountName, sourceId, displayName)
    }
}
