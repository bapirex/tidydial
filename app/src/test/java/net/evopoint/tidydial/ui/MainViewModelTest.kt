package net.evopoint.tidydial.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.evopoint.tidydial.data.BackupCodec
import net.evopoint.tidydial.data.BackupFailure
import net.evopoint.tidydial.data.BackupFile
import net.evopoint.tidydial.data.BackupKind
import net.evopoint.tidydial.data.BackupManager
import net.evopoint.tidydial.data.BackupReadError
import net.evopoint.tidydial.data.ChangeEntry
import net.evopoint.tidydial.data.ContactsDataSource
import net.evopoint.tidydial.data.CountryCatalog
import net.evopoint.tidydial.data.FakeBackupStore
import net.evopoint.tidydial.data.JournalEntry
import net.evopoint.tidydial.data.LoadResult
import net.evopoint.tidydial.data.PhoneNumberFormatter
import net.evopoint.tidydial.data.PhoneRowsResult
import net.evopoint.tidydial.data.ProgressCallback
import net.evopoint.tidydial.data.RowIdentity
import net.evopoint.tidydial.data.SettingsRepository
import net.evopoint.tidydial.data.UndoJournal
import net.evopoint.tidydial.data.UpdateResult
import net.evopoint.tidydial.model.AppChat
import net.evopoint.tidydial.model.CallOption
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import net.evopoint.tidydial.model.PhoneEntry
import net.evopoint.tidydial.model.PhoneRow
import net.evopoint.tidydial.ui.preview.SampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// Robolectric only for a working org.json inside UndoJournal and the backup files.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val catalog = CountryCatalog()
    private lateinit var contacts: FakeContacts
    private lateinit var journalDir: File
    private var preferences: DataStore<Preferences> = InMemoryPreferences()
    private var backupPreferences: DataStore<Preferences> = InMemoryPreferences()
    private val log = mutableListOf<String>()
    private val backupStore = FakeBackupStore(log)
    private val clock = Clock.fixed(Instant.parse("2026-10-31T09:04:12Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        contacts = FakeContacts(log)
        journalDir = tempFolder.newFolder()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settings() = SettingsRepository(preferences, catalog, backupPreferences) { listOf("us") }

    /**
     * [backupChoiceMade]: the backup prompt was answered with "Continue without" (unless a folder
     * was set up already), so updates start right away as before backups existed.
     */
    private fun TestScope.createViewModel(backupChoiceMade: Boolean = true): MainViewModel {
        val settings = settings()
        if (backupChoiceMade) {
            runBlocking { if (!settings.backup.first().asked) settings.setBackupEnabled(false) }
        }
        val formatter = PhoneNumberFormatter()
        val viewModel = MainViewModel(
            contacts = contacts,
            settings = settings,
            undoJournal = UndoJournal(journalDir, dispatcher),
            countryCatalog = catalog,
            formatter = formatter,
            backupStore = backupStore,
            backups = BackupManager(backupStore, settings, contacts, "1.2.0", formatter, { clock }, dispatcher),
            defaultDispatcher = dispatcher,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun TestScope.grantedViewModel(backupChoiceMade: Boolean = true): MainViewModel {
        val viewModel = createViewModel(backupChoiceMade)
        viewModel.onAction(UiAction.PermissionRechecked(granted = true))
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.collectEvents(viewModel: MainViewModel): MutableList<UiEvent> {
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

    private val MainViewModel.ui: UiState get() = state.value

    private fun UiState.contactIds() = groups.map { it.contactId }

    // region Loading and selection

    @Test
    fun loadPreselectsValidModifiedEntries() = runTest(dispatcher) {
        val viewModel = grantedViewModel()

        val state = viewModel.ui
        assertEquals(PermissionStatus.Granted, state.permission)
        assertFalse(state.isLoading)
        assertEquals(SampleData.defaultSelection, state.selection)
        assertEquals(setOf(101L, 102L, 103L, 201L, 501L, 601L), state.selection)
        assertEquals(EntryCounts(total = 10, changes = 6, selected = 6, invalid = 2), state.counts)
        // There is work to do, so the list opens on the Changes filter.
        assertEquals(ContactFilter.CHANGES, state.filter)
        assertEquals(listOf(1L, 2L, 5L, 6L), state.contactIds())
        assertEquals("US", state.detectedCountryIso)
        assertTrue(state.countries.isNotEmpty())
        assertEquals(SampleData.accounts, state.accounts)
    }

    @Test
    fun initialLoadShowsLoadingUntilDone() = runTest(dispatcher) {
        contacts.loadGate = CompletableDeferred()
        val viewModel = grantedViewModel()
        assertTrue(viewModel.ui.isLoading)
        assertFalse(viewModel.ui.isRefreshing)

        contacts.loadGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.ui.isLoading)
        assertEquals(10, viewModel.ui.counts.total)
    }

    @Test
    fun invalidAndUnmodifiedEntriesCannotBeSelected() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val before = viewModel.ui.selection

        viewModel.onAction(UiAction.ToggleEntry(401L)) // invalid
        viewModel.onAction(UiAction.ToggleEntry(301L)) // already formatted
        viewModel.onAction(UiAction.ToggleEntry(999L)) // unknown
        advanceUntilIdle()
        assertEquals(before, viewModel.ui.selection)

        viewModel.onAction(UiAction.ToggleEntry(101L))
        advanceUntilIdle()
        assertFalse(101L in viewModel.ui.selection)
        assertEquals(5, viewModel.ui.counts.selected)

        viewModel.onAction(UiAction.ToggleEntry(101L))
        advanceUntilIdle()
        assertTrue(101L in viewModel.ui.selection)
    }

    @Test
    fun toggleContactSelectsOrClearsItsSelectableEntries() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val avery = listOf(101L, 102L, 103L)

        viewModel.onAction(UiAction.ToggleContact(1L, avery))
        advanceUntilIdle()
        assertTrue(viewModel.ui.selection.none { it in avery })

        viewModel.onAction(UiAction.ToggleEntry(102L))
        viewModel.onAction(UiAction.ToggleContact(1L, avery))
        advanceUntilIdle()
        assertTrue(viewModel.ui.selection.containsAll(avery))

        // Contact 5 has one selectable and one formatted number; the formatted one is ignored.
        viewModel.onAction(UiAction.ToggleContact(5L, listOf(501L, 502L)))
        advanceUntilIdle()
        assertFalse(501L in viewModel.ui.selection)
        assertFalse(502L in viewModel.ui.selection)
    }

    @Test
    fun toggleContactLeavesHiddenNumbersAlone() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        // Avery's work number is deliberately unticked; the Selected filter then hides it.
        viewModel.onAction(UiAction.ToggleEntry(102L))
        viewModel.onAction(UiAction.SetFilter(ContactFilter.SELECTED))
        advanceUntilIdle()
        val shown = viewModel.ui.groups.single { it.contactId == 1L }.entries.map { it.dataId }
        assertEquals(listOf(101L, 103L), shown)

        // The avatar shows "all selected"; tapping it clears only what the card shows.
        viewModel.onAction(UiAction.ToggleContact(1L, shown))
        advanceUntilIdle()
        assertTrue(viewModel.ui.selection.none { it in setOf(101L, 102L, 103L) })

        viewModel.onAction(UiAction.ToggleContact(1L, shown))
        advanceUntilIdle()
        assertTrue(viewModel.ui.selection.containsAll(shown))
        assertFalse(102L in viewModel.ui.selection)
    }

    @Test
    fun toggleEntriesSelectsAndClearsTogether() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.ToggleEntry(101L))
        advanceUntilIdle()

        // One of the two is selected: the row shows unchecked, so a tap selects both.
        viewModel.onAction(UiAction.ToggleEntries(listOf(101L, 102L)))
        advanceUntilIdle()
        assertTrue(viewModel.ui.selection.containsAll(listOf(101L, 102L)))

        viewModel.onAction(UiAction.ToggleEntries(listOf(101L, 102L, 401L)))
        advanceUntilIdle()
        assertTrue(viewModel.ui.selection.none { it in setOf(101L, 102L) })
    }

    @Test
    fun selectedFilterKeepsRowsUntickedThere() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.SetFilter(ContactFilter.SELECTED))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 5L, 6L), viewModel.ui.contactIds())

        // A mis-tap: the row stays, unchecked, and can be ticked again in place.
        viewModel.onAction(UiAction.ToggleEntry(601L))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 5L, 6L), viewModel.ui.contactIds())
        assertFalse(601L in viewModel.ui.selection)
        assertEquals(5, viewModel.ui.counts.selected)

        viewModel.onAction(UiAction.ToggleEntry(601L))
        advanceUntilIdle()
        assertTrue(601L in viewModel.ui.selection)

        // Re-entering the filter shows the current selection.
        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.SetFilter(ContactFilter.SELECTED))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 5L), viewModel.ui.contactIds())

        // Clearing the selection empties the view.
        viewModel.onAction(UiAction.ClearSelection)
        advanceUntilIdle()
        assertTrue(viewModel.ui.groups.isEmpty())
    }

    @Test
    fun firstLoadOpensOnChangesOnlyWhenThereAreSome() = runTest(dispatcher) {
        contacts.formatEverything()
        val tidy = grantedViewModel()
        assertEquals(ContactFilter.ALL, tidy.ui.filter)
    }

    @Test
    fun firstLoadKeepsAFilterTheUserPicked() = runTest(dispatcher) {
        contacts.loadGate = CompletableDeferred()
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.SetFilter(ContactFilter.INVALID))
        contacts.loadGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(ContactFilter.INVALID, viewModel.ui.filter)

        // Later loads never change the filter.
        viewModel.onAction(UiAction.SetFilter(ContactFilter.ALL))
        viewModel.onAction(UiAction.Refresh)
        advanceUntilIdle()
        assertEquals(ContactFilter.ALL, viewModel.ui.filter)
    }

    @Test
    fun selectAllVisibleAndClear() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.ClearSelection)
        advanceUntilIdle()
        assertTrue(viewModel.ui.selection.isEmpty())

        viewModel.onAction(UiAction.SetQuery("Avery"))
        advanceUntilIdle()
        viewModel.onAction(UiAction.SelectAllVisible)
        advanceUntilIdle()
        assertEquals(setOf(101L, 102L, 103L), viewModel.ui.selection)

        viewModel.onAction(UiAction.SetQuery(""))
        viewModel.onAction(UiAction.SetFilter(ContactFilter.INVALID))
        advanceUntilIdle()
        viewModel.onAction(UiAction.SelectAllVisible)
        advanceUntilIdle()
        assertEquals(setOf(101L, 102L, 103L), viewModel.ui.selection)
        assertEquals(0, viewModel.ui.visibleSelectable)
    }

    @Test
    fun selectAllVisibleSelectsWhatIsOnScreenDuringDebounce() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.ClearSelection)
        advanceUntilIdle()

        // The list still shows every change until the search debounce fires.
        viewModel.onAction(UiAction.SetQuery("Avery"))
        advanceTimeBy(50)
        runCurrent()
        assertEquals(listOf(1L, 2L, 5L, 6L), viewModel.ui.contactIds())
        viewModel.onAction(UiAction.SelectAllVisible)
        advanceUntilIdle()
        assertEquals(SampleData.defaultSelection, viewModel.ui.selection)
    }

    @Test
    fun refreshKeepsManualChoicesAndPreselectsNewEntries() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.ToggleEntry(101L))
        advanceUntilIdle()

        contacts.extraEntries = listOf(
            sampleEntry(801L, 8L, "Harper Nobody", "7185550111", "+1 718-555-0111"),
        )
        viewModel.onAction(UiAction.Refresh)
        advanceUntilIdle()

        val selection = viewModel.ui.selection
        assertFalse(101L in selection)
        assertTrue(102L in selection)
        assertTrue(801L in selection)
        assertFalse(viewModel.ui.isRefreshing)
    }

    @Test
    fun selectionDropsEntriesThatDisappear() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        contacts.removedIds = setOf(601L)

        viewModel.onAction(UiAction.Refresh)
        advanceUntilIdle()

        assertFalse(601L in viewModel.ui.selection)
        assertEquals(5, viewModel.ui.counts.selected)
    }

    // endregion

    // region Filter and search

    @Test
    fun filterKeepsOnlyMatchingEntries() = runTest(dispatcher) {
        val viewModel = grantedViewModel()

        viewModel.onAction(UiAction.SetFilter(ContactFilter.INVALID))
        advanceUntilIdle()
        assertEquals(listOf(4L, 7L), viewModel.ui.contactIds())

        viewModel.onAction(UiAction.SetFilter(ContactFilter.CHANGES))
        advanceUntilIdle()
        val plumbing = viewModel.ui.groups.single { it.contactId == 5L }
        assertEquals(listOf(501L), plumbing.entries.map { it.dataId })
        assertFalse(3L in viewModel.ui.contactIds())

        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.SetFilter(ContactFilter.SELECTED))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 5L), viewModel.ui.contactIds())
        // Counts ignore the filter.
        assertEquals(10, viewModel.ui.counts.total)
    }

    @Test
    fun searchIsDebouncedAndMatchesNamesAndDigits() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.SetFilter(ContactFilter.ALL))
        advanceUntilIdle()

        viewModel.onAction(UiAction.SetQuery("avery"))
        advanceTimeBy(100)
        runCurrent()
        assertEquals("avery", viewModel.ui.query)
        assertEquals(SampleData.groups.size, viewModel.ui.groups.size)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(1L), viewModel.ui.contactIds())

        viewModel.onAction(UiAction.SetQuery("7946-0958"))
        advanceUntilIdle()
        assertEquals(listOf(2L), viewModel.ui.contactIds())

        // Matches the formatted number only.
        viewModel.onAction(UiAction.SetQuery("+1 617"))
        advanceUntilIdle()
        assertEquals(listOf(6L), viewModel.ui.contactIds())

        viewModel.onAction(UiAction.SetQuery("nobody matches this"))
        advanceUntilIdle()
        assertTrue(viewModel.ui.groups.isEmpty())

        // Clearing the search is immediate.
        viewModel.onAction(UiAction.SetQuery(""))
        runCurrent()
        assertEquals(SampleData.groups.size, viewModel.ui.groups.size)
    }

    // endregion

    // region Update and undo

    @Test
    fun applyThenUndo() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        assertFalse(viewModel.ui.canUndo)

        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertEquals(UiEvent.Updated(updated = 5, skippedChanged = 0, failed = 0), events.single())
        assertTrue(viewModel.ui.canUndo)
        assertNull(viewModel.ui.progress)
        assertEquals(5, UndoJournal(journalDir, dispatcher).load().size)
        assertEquals("+1 212-555-0101", contacts.numbers[101L])
        assertEquals("6175550199", contacts.numbers[601L])
        // Reloaded: only the skipped number still needs a change, and it stays deselected.
        assertEquals(1, viewModel.ui.counts.changes)
        assertTrue(viewModel.ui.selection.isEmpty())
        assertEquals(listOf(ProgressKind.APPLY), contacts.progressKinds.distinct())

        viewModel.onAction(UiAction.UndoLast)
        advanceUntilIdle()

        assertEquals(UiEvent.Undone(restored = 5, skippedChanged = 0, failed = 0), events.last())
        assertFalse(viewModel.ui.canUndo)
        assertTrue(UndoJournal(journalDir, dispatcher).load().isEmpty())
        assertEquals("2125550101", contacts.numbers[101L])
        assertEquals(6, viewModel.ui.counts.changes)
        // Restored numbers were not selectable before, so they are pre-selected again; the
        // number the user deselected keeps that choice.
        assertEquals(SampleData.defaultSelection - 601L, viewModel.ui.selection)
    }

    @Test
    fun undoSkipsNumbersEditedSinceTheUpdate() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        contacts.numbers[101L] = "+1 212 555 0000"
        viewModel.onAction(UiAction.UndoLast)
        advanceUntilIdle()

        assertEquals(UiEvent.Undone(restored = 5, skippedChanged = 1, failed = 0), events.last())
        assertEquals("+1 212 555 0000", contacts.numbers[101L])
        assertFalse(viewModel.ui.canUndo)
    }

    @Test
    fun journalIsOnDiskBeforeAnyContactIsWritten() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        contacts.applyGate = CompletableDeferred()

        viewModel.onAction(UiAction.ApplySelected)
        runCurrent()
        assertEquals(1, contacts.applyCalls)
        // Mid-run (as if the process died here) the planned changes can already be undone.
        val planned = UndoJournal(journalDir, dispatcher).load()
        assertEquals(SampleData.defaultSelection, planned.map { it.dataId }.toSet())
        assertEquals("2125550101", planned.single { it.dataId == 101L }.before)
        assertEquals("+1 212-555-0101", planned.single { it.dataId == 101L }.after)

        contacts.applyGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(6, UndoJournal(journalDir, dispatcher).load().size)
    }

    @Test
    fun journalShrinksToWrittenAndFailedRows() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        contacts.numbers[102L] = "212 555 0102 edited elsewhere"
        contacts.failIds = setOf(103L)

        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertEquals(UiEvent.Updated(updated = 4, skippedChanged = 1, failed = 1), events.single())
        // 102 was skipped and is dropped; 103 failed and may have been written, so it is kept.
        assertEquals(
            setOf(101L, 103L, 201L, 501L, 601L),
            UndoJournal(journalDir, dispatcher).load().map { it.dataId }.toSet(),
        )
    }

    @Test
    fun updateThatWritesNothingKeepsThePreviousUndo() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        val first = UndoJournal(journalDir, dispatcher).load()
        assertEquals(5, first.size)

        // 601 is edited elsewhere before the second update, so nothing is written.
        viewModel.onAction(UiAction.ToggleEntry(601L))
        contacts.numbers[601L] = "617 555 0199"
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertEquals(first, UndoJournal(journalDir, dispatcher).load())
        assertTrue(viewModel.ui.canUndo)
    }

    @Test
    fun journalSaveFailureLeavesContactsUntouched() = runTest(dispatcher) {
        // A plain file where the directory should be: every write throws an IOException.
        journalDir = tempFolder.newFile()
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        val before = contacts.numbers.toMap()

        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertEquals(listOf(UiEvent.JournalSaveFailed), events)
        assertEquals(0, contacts.applyCalls)
        assertEquals(before, contacts.numbers)
        assertNull(viewModel.ui.progress)
        assertFalse(viewModel.ui.canUndo)
        assertEquals(SampleData.defaultSelection, viewModel.ui.selection)
    }

    @Test
    fun undoKeepsRowsThatFailedForARetry() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        contacts.failIds = setOf(101L)
        contacts.numbers[102L] = "+1 212 555 0000"
        viewModel.onAction(UiAction.UndoLast)
        advanceUntilIdle()

        assertEquals(UiEvent.Undone(restored = 4, skippedChanged = 1, failed = 1), events.last())
        // Only the failed row is left: the edited one would be skipped again.
        assertEquals(listOf(101L), UndoJournal(journalDir, dispatcher).load().map { it.dataId })
        assertTrue(viewModel.ui.canUndo)

        contacts.failIds = emptySet()
        viewModel.onAction(UiAction.UndoLast)
        advanceUntilIdle()
        assertEquals(UiEvent.Undone(restored = 1, skippedChanged = 0, failed = 0), events.last())
        assertEquals("2125550101", contacts.numbers[101L])
        assertFalse(viewModel.ui.canUndo)
    }

    @Test
    fun canUndoReflectsJournalOnDisk() = runTest(dispatcher) {
        UndoJournal(journalDir, dispatcher).save(listOf(JournalEntry(101L, "2125550101", "+1 212-555-0101")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.ui.canUndo)
    }

    @Test
    fun progressIsReportedAndLoadIsBlockedWhileUpdating() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val loadsBefore = contacts.loadCalls
        contacts.applyGate = CompletableDeferred()

        viewModel.onAction(UiAction.ApplySelected)
        runCurrent()
        assertEquals(UpdateProgress(0, 6, ProgressKind.APPLY), viewModel.ui.progress)
        assertTrue(viewModel.ui.isUpdating)

        viewModel.onAction(UiAction.Refresh)
        viewModel.onAction(UiAction.ApplySelected)
        runCurrent()
        assertEquals(loadsBefore, contacts.loadCalls)
        assertEquals(1, contacts.applyCalls)

        contacts.applyGate!!.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.ui.progress)
        // One reload after the update finished.
        assertEquals(loadsBefore + 1, contacts.loadCalls)
    }

    @Test
    fun applyWithEmptySelectionDoesNothing() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.ClearSelection)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        assertEquals(0, contacts.applyCalls)
    }

    // endregion

    // region Backups

    /** Backups on, into [FOLDER], which TidyDial holds a grant for. */
    private fun TestScope.backedUpViewModel(): MainViewModel {
        runBlocking { settings().setBackupFolder(FOLDER, FOLDER_NAME) }
        backupStore.access += FOLDER
        return grantedViewModel()
    }

    private fun backupFile(name: String): BackupFile =
        BackupCodec.decode(backupStore.files(FOLDER).single { it.name == name }.content)

    /** The identity a backup holds for [dataId], from the fake provider's rows. */
    private fun identityOf(dataId: Long): RowIdentity {
        val rows = runBlocking { contacts.readPhoneRows() } as PhoneRowsResult.Success
        val row = rows.rows.single { it.dataId == dataId }
        return RowIdentity(row.dataId, row.rawContactId, row.lookupKey, row.accountType, row.accountName, row.sourceId, row.displayName)
    }

    @Test
    fun firstUpdateShowsPromptAndWritesNothing() = runTest(dispatcher) {
        val viewModel = grantedViewModel(backupChoiceMade = false)
        val events = collectEvents(viewModel)

        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertEquals(BackupDialog.FirstUpdate, viewModel.ui.backupDialog)
        assertEquals(0, contacts.applyCalls)
        assertNull(viewModel.ui.progress)
        assertTrue(UndoJournal(journalDir, dispatcher).load().isEmpty())
        assertTrue(events.isEmpty())
        assertEquals(SampleData.defaultSelection, viewModel.ui.selection)
    }

    @Test
    fun continueWithoutRemembersChoiceAndUpdates() = runTest(dispatcher) {
        val viewModel = grantedViewModel(backupChoiceMade = false)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        viewModel.onAction(UiAction.ContinueWithoutBackup)
        advanceUntilIdle()

        assertNull(viewModel.ui.backupDialog)
        assertEquals(UiEvent.Updated(updated = 5, skippedChanged = 0, failed = 0), events.single())
        assertTrue(backupStore.folders.isEmpty())
        assertEquals(BackupUi(enabled = false), viewModel.ui.backup)

        // The choice is remembered: the next update starts right away.
        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        assertNull(viewModel.ui.backupDialog)
        assertEquals(2, contacts.applyCalls)
        assertTrue(settings().backup.first().asked)
    }

    @Test
    fun dismissingThePromptCancelsTheUpdate() = runTest(dispatcher) {
        val viewModel = grantedViewModel(backupChoiceMade = false)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        viewModel.onAction(UiAction.DismissBackupDialog)
        advanceUntilIdle()
        assertNull(viewModel.ui.backupDialog)
        assertEquals(0, contacts.applyCalls)

        // Nothing was remembered, so the next update asks again.
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        assertEquals(BackupDialog.FirstUpdate, viewModel.ui.backupDialog)
    }

    @Test
    fun cancelledFolderPickerKeepsThePrompt() = runTest(dispatcher) {
        val viewModel = grantedViewModel(backupChoiceMade = false)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        viewModel.onAction(UiAction.BackupFolderChosen(null))
        advanceUntilIdle()
        assertEquals(BackupDialog.FirstUpdate, viewModel.ui.backupDialog)
        assertEquals(0, contacts.applyCalls)
    }

    @Test
    fun chosenFolderBacksUpThenUpdates() = runTest(dispatcher) {
        val viewModel = grantedViewModel(backupChoiceMade = false)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        viewModel.onAction(UiAction.BackupFolderChosen(FOLDER))
        advanceUntilIdle()

        assertNull(viewModel.ui.backupDialog)
        assertEquals(BackupUi(enabled = true, folderName = FOLDER_NAME), viewModel.ui.backup)
        val snapshotName = "TidyDial snapshot 2026-10-31 090412.json"
        val changesName = "TidyDial backup 2026-10-31 090412.json"
        // Both files are in the folder before any contact is written.
        assertEquals(listOf("backup:$snapshotName", "backup:$changesName", "apply"), log)
        val snapshot = backupFile(snapshotName) as BackupFile.Snapshot
        assertEquals(SampleData.entries.map { it.dataId }, snapshot.entries.map { it.row.dataId })
        assertEquals("2125550101", snapshot.entries.first().number)
        val changes = backupFile(changesName) as BackupFile.Changes
        assertEquals(SampleData.defaultSelection, changes.entries.map { it.row.dataId }.toSet())
        val avery = changes.entries.single { it.row.dataId == 101L }
        assertEquals(ChangeEntry(avery.row, "2125550101", "+1 212-555-0101"), avery)
        assertEquals("source-10", avery.row.sourceId)
        assertEquals("tester@example.com", avery.row.accountName)
        assertEquals("US", changes.countryIso)
        assertEquals(UiEvent.Updated(updated = 6, skippedChanged = 0, failed = 0), events.single())

        // The next update only writes its changes; the snapshot is written once.
        contacts.numbers[101L] = "2125550101"
        viewModel.onAction(UiAction.Refresh)
        advanceUntilIdle()
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        assertEquals(
            listOf(snapshotName, changesName, "TidyDial backup 2026-10-31 090412 (2).json"),
            backupStore.files(FOLDER).map { it.name },
        )
    }

    @Test
    fun backupFailureBlocksTheUpdate() = runTest(dispatcher) {
        val viewModel = backedUpViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        val journal = UndoJournal(journalDir, dispatcher).load()
        assertEquals(5, journal.size)

        backupStore.failWrite = BackupFailure.FOLDER_MISSING
        viewModel.onAction(UiAction.ToggleEntry(601L))
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertEquals(BackupDialog.WriteFailed(FOLDER_NAME), viewModel.ui.backupDialog)
        assertEquals(1, contacts.applyCalls)
        assertEquals("6175550199", contacts.numbers[601L])
        // The previous update can still be undone.
        assertEquals(journal, UndoJournal(journalDir, dispatcher).load())
        assertTrue(viewModel.ui.canUndo)
        assertNull(viewModel.ui.progress)
        assertEquals(1, events.size)
        assertEquals(setOf(601L), viewModel.ui.selection)
    }

    @Test
    fun choosingAFolderAfterAFailureResumesTheUpdate() = runTest(dispatcher) {
        val viewModel = backedUpViewModel()
        backupStore.failWrite = BackupFailure.NO_ACCESS
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        assertTrue(viewModel.ui.backupDialog is BackupDialog.WriteFailed)

        backupStore.failWrite = null
        viewModel.onAction(UiAction.BackupFolderChosen(OTHER_FOLDER))
        advanceUntilIdle()

        assertNull(viewModel.ui.backupDialog)
        assertEquals(1, contacts.applyCalls)
        assertEquals("Other folder", viewModel.ui.backup.folderName)
        // The snapshot was not written before, so the new folder gets one.
        assertEquals(2, backupStore.files(OTHER_FOLDER).size)
        assertEquals(listOf(FOLDER), backupStore.released)
    }

    @Test
    fun updateWithoutBackupRunsOnceAndKeepsBackupsOn() = runTest(dispatcher) {
        val viewModel = backedUpViewModel()
        val events = collectEvents(viewModel)
        backupStore.failWrite = BackupFailure.WRITE_FAILED
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        viewModel.onAction(UiAction.UpdateWithoutBackup)
        advanceUntilIdle()

        assertNull(viewModel.ui.backupDialog)
        assertEquals(1, contacts.applyCalls)
        assertEquals(UiEvent.Updated(updated = 6, skippedChanged = 0, failed = 0), events.single())
        assertTrue(backupStore.files(FOLDER).isEmpty())
        // Only this update skipped the backup: the next one tries again, and is held again.
        assertTrue(viewModel.ui.backup.enabled)
        contacts.numbers[101L] = "2125550101"
        viewModel.onAction(UiAction.Refresh)
        advanceUntilIdle()
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        assertEquals(BackupDialog.WriteFailed(FOLDER_NAME), viewModel.ui.backupDialog)
        assertEquals(1, contacts.applyCalls)
    }

    @Test
    fun turningBackupsOnNeedsAFolder() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.SetBackupEnabled(true))
        advanceUntilIdle()
        assertFalse(viewModel.ui.backup.enabled)

        viewModel.onAction(UiAction.BackupFolderChosen(FOLDER))
        advanceUntilIdle()
        assertEquals(BackupUi(enabled = true, folderName = FOLDER_NAME), viewModel.ui.backup)
        // No update was waiting, so none starts.
        assertEquals(0, contacts.applyCalls)
    }

    @Test
    fun revokedFolderAccessBlocksWithoutWriting() = runTest(dispatcher) {
        val viewModel = backedUpViewModel()
        backupStore.access.clear()
        viewModel.onAction(UiAction.PermissionRechecked(granted = true))
        advanceUntilIdle()
        assertFalse(viewModel.ui.backup.folderAvailable)

        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertEquals(BackupDialog.WriteFailed(FOLDER_NAME), viewModel.ui.backupDialog)
        assertTrue(backupStore.files(FOLDER).isEmpty())
        assertEquals(0, contacts.applyCalls)
    }

    @Test
    fun undoNeedsNoBackup() = runTest(dispatcher) {
        val viewModel = backedUpViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        backupStore.access.clear()
        backupStore.failWrite = BackupFailure.WRITE_FAILED
        viewModel.onAction(UiAction.UndoLast)
        advanceUntilIdle()

        assertEquals(UiEvent.Undone(restored = 6, skippedChanged = 0, failed = 0), events.last())
        assertEquals("2125550101", contacts.numbers[101L])
        assertEquals(2, backupStore.files(FOLDER).size)
        assertNull(viewModel.ui.backupDialog)
    }

    @Test
    fun readFailureBlocksTheUpdateWithoutBlamingTheFolder() = runTest(dispatcher) {
        val viewModel = backedUpViewModel()
        val events = collectEvents(viewModel)
        contacts.failRows = true
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        assertNull(viewModel.ui.backupDialog)
        assertEquals(listOf<UiEvent>(UiEvent.BackupReadFailed), events)
        assertEquals(0, contacts.applyCalls)

        // Its Retry runs the same update once the contacts can be read.
        contacts.failRows = false
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        assertEquals(1, contacts.applyCalls)
        assertEquals(2, backupStore.files(FOLDER).size)
    }

    private class Restorable(val viewModel: MainViewModel, val backup: String, val events: MutableList<UiEvent>)

    /**
     * An update with backups on, then 102 edited elsewhere and Frankie (601) deleted. [Restorable.events]
     * starts empty after the update.
     */
    private fun TestScope.updatedThenChanged(): Restorable {
        val viewModel = backedUpViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()
        events.clear()
        contacts.numbers[102L] = "212 555 0102 edited elsewhere"
        contacts.removedIds = setOf(601L)
        return Restorable(viewModel, "$FOLDER/TidyDial backup 2026-10-31 090412.json", events)
    }

    @Test
    fun restoreShowsConfirmationWithCounts() = runTest(dispatcher) {
        val restorable = updatedThenChanged()
        val viewModel = restorable.viewModel

        viewModel.onAction(UiAction.RestoreFileChosen(restorable.backup))
        advanceUntilIdle()

        assertEquals(
            RestoreConfirmation(
                kind = BackupKind.CHANGES,
                createdAt = OffsetDateTime.parse("2026-10-31T09:04:12Z"),
                count = 4,
                changedSince = 1,
                notFound = 1,
            ),
            viewModel.ui.restore,
        )
        assertEquals(0, contacts.progressKinds.count { it == ProgressKind.RESTORE })

        viewModel.onAction(UiAction.DismissRestore)
        advanceUntilIdle()
        assertNull(viewModel.ui.restore)
        viewModel.onAction(UiAction.ConfirmRestore)
        advanceUntilIdle()
        assertEquals("+1 212-555-0101", contacts.numbers[101L])
    }

    @Test
    fun confirmedRestoreIsJournaledAndUndoable() = runTest(dispatcher) {
        val restorable = updatedThenChanged()
        val viewModel = restorable.viewModel
        val events = restorable.events
        val filesBefore = backupStore.files(FOLDER).size
        viewModel.onAction(UiAction.RestoreFileChosen(restorable.backup))
        advanceUntilIdle()

        viewModel.onAction(UiAction.ConfirmRestore)
        advanceUntilIdle()

        assertNull(viewModel.ui.restore)
        assertEquals(
            UiEvent.RestoredFromBackup(restored = 4, skippedChanged = 1, notFound = 1, failed = 0),
            events.single(),
        )
        assertEquals("2125550101", contacts.numbers[101L])
        assertEquals("212 555 0102 edited elsewhere", contacts.numbers[102L])
        // With backups on, the numbers the restore replaced are backed up before it writes.
        assertEquals(filesBefore + 1, backupStore.files(FOLDER).size)
        assertEquals(listOf("backup:TidyDial backup 2026-10-31 090412 (2).json", "write"), log.takeLast(2))
        val restoreBackup = backupFile("TidyDial backup 2026-10-31 090412 (2).json") as BackupFile.Changes
        val averyRestore = restoreBackup.entries.single { it.row.dataId == 101L }
        assertEquals("+1 212-555-0101", averyRestore.before)
        assertEquals("2125550101", averyRestore.after)
        val journal = UndoJournal(journalDir, dispatcher).load()
        assertEquals(JournalEntry(101L, "+1 212-555-0101", "2125550101"), journal.single { it.dataId == 101L })
        assertTrue(viewModel.ui.canUndo)

        viewModel.onAction(UiAction.UndoLast)
        advanceUntilIdle()
        assertEquals(UiEvent.Undone(restored = 4, skippedChanged = 0, failed = 0), events.last())
        assertEquals("+1 212-555-0101", contacts.numbers[101L])
    }

    @Test
    fun restoreWorksWithoutABackupFolder() = runTest(dispatcher) {
        // As right after a reinstall: no backup choice, no folder, a file picked by the user.
        contacts.numbers[101L] = "+1 212-555-0101"
        val picked = "content://downloads/backup.json"
        backupStore.documents[picked] = BackupCodec.encode(
            BackupFile.Changes(
                createdAt = OffsetDateTime.parse("2026-10-30T18:30:00+01:00"),
                appVersion = "1.2.0",
                countryIso = "US",
                entries = listOf(ChangeEntry(identityOf(101L), "2125550101", "+1 212-555-0101")),
            ),
        )
        val viewModel = grantedViewModel(backupChoiceMade = false)
        val events = collectEvents(viewModel)

        viewModel.onAction(UiAction.RestoreFileChosen(picked))
        advanceUntilIdle()
        assertEquals(1, viewModel.ui.restore?.count)
        viewModel.onAction(UiAction.ConfirmRestore)
        advanceUntilIdle()

        assertNull(viewModel.ui.backupDialog)
        assertEquals("2125550101", contacts.numbers[101L])
        assertEquals(
            UiEvent.RestoredFromBackup(restored = 1, skippedChanged = 0, notFound = 0, failed = 0),
            events.single(),
        )
        assertFalse(settings().backup.first().asked)
    }

    @Test
    fun unreadableBackupIsReported() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        backupStore.documents["content://picked/newer.json"] =
            """{"app":"TidyDial","format":2,"kind":"changes","entries":[]}"""
        backupStore.documents["content://picked/other.json"] = """{"app":"SomethingElse","format":1}"""

        viewModel.onAction(UiAction.RestoreFileChosen("content://picked/missing.json"))
        advanceUntilIdle()
        viewModel.onAction(UiAction.RestoreFileChosen("content://picked/newer.json"))
        advanceUntilIdle()
        viewModel.onAction(UiAction.RestoreFileChosen("content://picked/other.json"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                UiEvent.BackupUnreadable(BackupReadError.UNREADABLE),
                UiEvent.BackupUnreadable(BackupReadError.NEWER_FORMAT),
                UiEvent.BackupUnreadable(BackupReadError.NOT_A_BACKUP),
            ),
            events,
        )
        assertNull(viewModel.ui.restore)
    }

    @Test
    fun nothingToRestoreIsReported() = runTest(dispatcher) {
        // The backup's update was undone already: every number holds its "before".
        val restorable = updatedThenChanged()
        val viewModel = restorable.viewModel
        contacts.removedIds = emptySet()
        viewModel.onAction(UiAction.UndoLast)
        advanceUntilIdle()
        val events = restorable.events
        events.clear()

        viewModel.onAction(UiAction.RestoreFileChosen(restorable.backup))
        advanceUntilIdle()

        // 102 was edited elsewhere, so the undo kept it; every other number holds its "before".
        assertEquals(listOf(UiEvent.NothingToRestore(alreadyRestored = 5, changedSince = 1, notFound = 0)), events)
        assertNull(viewModel.ui.restore)
    }

    @Test
    fun restoreIsIgnoredWhileUpdating() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        backupStore.documents["content://picked/backup.json"] = BackupCodec.encode(
            BackupFile.Changes(
                createdAt = OffsetDateTime.parse("2026-10-30T18:30:00Z"),
                appVersion = "1.2.0",
                countryIso = "US",
                entries = listOf(ChangeEntry(identityOf(101L), "2125550101", "+1 212-555-0101")),
            ),
        )
        contacts.applyGate = CompletableDeferred()
        viewModel.onAction(UiAction.ApplySelected)
        runCurrent()

        viewModel.onAction(UiAction.RestoreFileChosen("content://picked/backup.json"))
        runCurrent()
        contacts.applyGate!!.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.ui.restore)
    }

    @Test
    fun folderThatCannotBeAdoptedIsReported() = runTest(dispatcher) {
        val viewModel = grantedViewModel(backupChoiceMade = false)
        val events = collectEvents(viewModel)
        backupStore.grantable = emptySet()
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        viewModel.onAction(UiAction.BackupFolderChosen(FOLDER))
        advanceUntilIdle()

        // Shown in the prompt itself: a Snackbar would be hidden behind it.
        assertTrue(events.isEmpty())
        assertEquals(BackupError.FOLDER_FAILED, viewModel.ui.backup.error)
        assertNull(viewModel.ui.backup.folderName)
        // The prompt stays for another folder or "Continue without".
        assertEquals(BackupDialog.FirstUpdate, viewModel.ui.backupDialog)
        assertEquals(0, contacts.applyCalls)

        // The next choice clears it.
        backupStore.grantable = null
        viewModel.onAction(UiAction.BackupFolderChosen(FOLDER))
        advanceUntilIdle()
        assertNull(viewModel.ui.backup.error)
        assertEquals(1, contacts.applyCalls)
    }

    @Test
    fun unsavedBackupChoiceIsShownInThePrompt() = runTest(dispatcher) {
        backupPreferences = FailingPreferences()
        val viewModel = grantedViewModel(backupChoiceMade = false)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.ApplySelected)
        advanceUntilIdle()

        viewModel.onAction(UiAction.ContinueWithoutBackup)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertEquals(BackupError.SAVE_FAILED, viewModel.ui.backup.error)
        assertEquals(BackupDialog.FirstUpdate, viewModel.ui.backupDialog)
        assertEquals(0, contacts.applyCalls)

        viewModel.onAction(UiAction.DismissBackupDialog)
        advanceUntilIdle()
        assertNull(viewModel.ui.backup.error)
    }

    @Test
    fun unsavedToggleIsShownInTheOptionsSheet() = runTest(dispatcher) {
        backupPreferences = FailingPreferences()
        val viewModel = grantedViewModel(backupChoiceMade = false)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.SetBackupEnabled(false))
        advanceUntilIdle()
        assertEquals(BackupError.SAVE_FAILED, viewModel.ui.backup.error)
        assertTrue(events.isEmpty())

        // Closing the sheet clears it, so it does not show again when the sheet reopens.
        viewModel.onAction(UiAction.DismissBackupError)
        advanceUntilIdle()
        assertNull(viewModel.ui.backup.error)
    }

    @Test
    fun pickerUnavailabilityIsReportedWhereItWasAsked() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        // The folder picker is opened from a sheet or dialog: shown there.
        viewModel.onAction(UiAction.FolderPickerUnavailable)
        advanceUntilIdle()
        assertEquals(BackupError.NO_PICKER, viewModel.ui.backup.error)
        assertTrue(events.isEmpty())

        // The file picker closes the options sheet first: a Snackbar.
        viewModel.onAction(UiAction.FilePickerUnavailable)
        advanceUntilIdle()
        assertEquals(listOf(UiEvent.NoFilePicker), events)
    }

    @Test
    fun restoreGoesOnWhenItsBackupFails() = runTest(dispatcher) {
        val restorable = updatedThenChanged()
        val viewModel = restorable.viewModel
        viewModel.onAction(UiAction.RestoreFileChosen(restorable.backup))
        advanceUntilIdle()
        val filesBefore = backupStore.files(FOLDER).size

        backupStore.failWrite = BackupFailure.FOLDER_MISSING
        viewModel.onAction(UiAction.ConfirmRestore)
        advanceUntilIdle()

        assertEquals(
            UiEvent.RestoredFromBackup(restored = 4, skippedChanged = 1, notFound = 1, failed = 0, backupFailed = true),
            restorable.events.single(),
        )
        assertEquals("2125550101", contacts.numbers[101L])
        assertEquals(filesBefore, backupStore.files(FOLDER).size)
        assertNull(viewModel.ui.backupDialog)
        assertTrue(viewModel.ui.canUndo)
    }

    // endregion

    // region Load lifecycle

    @Test
    fun newLoadCancelsThePreviousOne() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        contacts.loadGate = CompletableDeferred()

        viewModel.onAction(UiAction.Refresh)
        runCurrent()
        assertTrue(viewModel.ui.isRefreshing)
        viewModel.onAction(UiAction.Refresh)
        runCurrent()

        assertEquals(1, contacts.cancelledLoads)
        assertTrue(viewModel.ui.isRefreshing)

        contacts.loadGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.ui.isRefreshing)
        assertEquals(3, contacts.loadCalls)
    }

    @Test
    fun loadErrorSetsFlagAndSendsEvent() = runTest(dispatcher) {
        contacts.failLoad = true
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.PermissionRechecked(granted = true))
        advanceUntilIdle()

        assertTrue(viewModel.ui.loadError)
        assertFalse(viewModel.ui.isLoading)
        assertEquals(listOf(UiEvent.LoadFailed), events)

        contacts.failLoad = false
        viewModel.onAction(UiAction.Refresh)
        advanceUntilIdle()
        assertFalse(viewModel.ui.loadError)
        assertEquals(10, viewModel.ui.counts.total)
    }

    @Test
    fun updateConfigSavesAndReloads() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val loadsBefore = contacts.loadCalls
        val config = viewModel.ui.config.copy(formatStyle = NumberFormatStyle.E164, countryIso = "GB")

        viewModel.onAction(UiAction.UpdateConfig(config))
        advanceUntilIdle()

        assertEquals(config, viewModel.ui.config)
        assertEquals(loadsBefore + 1, contacts.loadCalls)
        assertEquals(config, contacts.lastConfig)
    }

    @Test
    fun settingsSaveFailureIsReportedWithoutReloading() = runTest(dispatcher) {
        preferences = FailingPreferences()
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        val loadsBefore = contacts.loadCalls
        val previous = viewModel.ui.config

        viewModel.onAction(UiAction.UpdateConfig(previous.copy(formatStyle = NumberFormatStyle.E164)))
        advanceUntilIdle()

        assertEquals(listOf(UiEvent.SettingsSaveFailed), events)
        assertEquals(previous, viewModel.ui.config)
        assertEquals(loadsBefore, contacts.loadCalls)
    }

    @Test
    fun exampleNumberDelegatesToFormatter() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(
            PhoneNumberFormatter().exampleNumber("DE", NumberFormatStyle.E164),
            viewModel.exampleNumber("DE", NumberFormatStyle.E164),
        )
    }

    // endregion

    // region Permission

    @Test
    fun permissionResults() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(PermissionStatus.Unknown, viewModel.ui.permission)

        viewModel.onAction(UiAction.PermissionRechecked(granted = false))
        advanceUntilIdle()
        assertEquals(PermissionStatus.Denied, viewModel.ui.permission)

        viewModel.onAction(UiAction.PermissionResult(granted = false, canAskAgain = true))
        advanceUntilIdle()
        assertEquals(PermissionStatus.Denied, viewModel.ui.permission)

        viewModel.onAction(UiAction.PermissionResult(granted = false, canAskAgain = false))
        advanceUntilIdle()
        assertEquals(PermissionStatus.PermanentlyDenied, viewModel.ui.permission)

        // A resume check cannot tell the two apart, so the stronger state stays.
        viewModel.onAction(UiAction.PermissionRechecked(granted = false))
        advanceUntilIdle()
        assertEquals(PermissionStatus.PermanentlyDenied, viewModel.ui.permission)
        assertEquals(0, contacts.loadCalls)

        viewModel.onAction(UiAction.PermissionRechecked(granted = true))
        advanceUntilIdle()
        assertEquals(PermissionStatus.Granted, viewModel.ui.permission)
        assertEquals(1, contacts.loadCalls)

        // Resuming again with the permission still granted does not reload.
        viewModel.onAction(UiAction.PermissionRechecked(granted = true))
        advanceUntilIdle()
        assertEquals(1, contacts.loadCalls)
    }

    @Test
    fun permissionGrantedFromDialogLoads() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onAction(UiAction.PermissionResult(granted = true, canAskAgain = true))
        advanceUntilIdle()
        assertEquals(PermissionStatus.Granted, viewModel.ui.permission)
        assertEquals(10, viewModel.ui.counts.total)
    }

    @Test
    fun revokedPermissionClearsDataAndNotifies() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)

        viewModel.onAction(UiAction.PermissionRechecked(granted = false))
        advanceUntilIdle()

        assertEquals(PermissionStatus.Denied, viewModel.ui.permission)
        assertTrue(viewModel.ui.groups.isEmpty())
        assertTrue(viewModel.ui.selection.isEmpty())
        assertEquals(listOf(UiEvent.PermissionRevoked), events)
    }

    @Test
    fun permissionDeniedDuringLoadIsReported() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        contacts.denyLoad = true

        viewModel.onAction(UiAction.Refresh)
        advanceUntilIdle()

        assertEquals(PermissionStatus.Denied, viewModel.ui.permission)
        assertFalse(viewModel.ui.isRefreshing)
        assertEquals(listOf(UiEvent.PermissionRevoked), events)
    }

    // endregion

    // region Calling

    private val whatsAppVoice = CallOption.App(
        label = "WhatsApp",
        isVideo = false,
        packageName = "org.example.whatsapp",
        activityName = "org.example.CallActivity",
        dataId = 9001,
        mimeType = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call",
    )

    /** A call whose activity needs CALL_PHONE, with the app's chat to fall back to. */
    private val guardedVoice = CallOption.App(
        label = "Signal",
        isVideo = false,
        packageName = "org.example.signal",
        activityName = "org.example.signal.CallActivity",
        dataId = 9002,
        mimeType = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call",
        needsCallPermission = true,
        chat = AppChat(
            packageName = "org.example.signal",
            activityName = "org.example.signal.ChatActivity",
            dataId = 9003,
            mimeType = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact",
        ),
    )

    @Test
    fun callWithOnlyThePhoneAppDialsDirectly() = runTest(dispatcher) {
        val viewModel = grantedViewModel()

        viewModel.onAction(UiAction.Call(listOf(601L)))
        advanceUntilIdle()

        // The number as stored, not the formatted one the list shows.
        assertEquals(CallRequest.Launch(CallOption.Phone("6175550199")), viewModel.ui.call)
        assertEquals("US", contacts.callLookups.single().second)
    }

    @Test
    fun callWithOtherAppsAsksHowToCall() = runTest(dispatcher) {
        contacts.callApps = listOf(whatsAppVoice)
        val viewModel = grantedViewModel()

        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()

        assertEquals(
            CallRequest.Choose(
                name = "Avery Sample",
                number = "+1 212-555-0101",
                options = listOf(CallOption.Phone("2125550101"), whatsAppVoice),
            ),
            viewModel.ui.call,
        )
    }

    @Test
    fun invalidNumberIsShownAsStored() = runTest(dispatcher) {
        contacts.callApps = listOf(whatsAppVoice)
        val viewModel = grantedViewModel()

        viewModel.onAction(UiAction.Call(listOf(401L)))
        advanceUntilIdle()

        assertEquals("555-01", (viewModel.ui.call as CallRequest.Choose).number)
    }

    @Test
    fun callLeavesTheSelectionAlone() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val before = viewModel.ui.selection

        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()

        assertEquals(before, viewModel.ui.selection)
    }

    @Test
    fun newerTapReplacesARunningLookup() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        contacts.callGate = CompletableDeferred()

        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()
        viewModel.onAction(UiAction.Call(listOf(301L)))
        advanceUntilIdle()
        contacts.callGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, contacts.callLookups.size)
        assertEquals(CallRequest.Launch(CallOption.Phone("+1 415-555-0132")), viewModel.ui.call)
    }

    @Test
    fun dismissClearsTheCall() = runTest(dispatcher) {
        contacts.callApps = listOf(whatsAppVoice)
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()

        viewModel.onAction(UiAction.DismissCall)
        advanceUntilIdle()

        assertNull(viewModel.ui.call)
        assertTrue(events.isEmpty())
    }

    @Test
    fun failedCallClearsAndNotifies() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.Call(listOf(601L)))
        advanceUntilIdle()

        viewModel.onAction(UiAction.CallFailed)
        advanceUntilIdle()

        assertNull(viewModel.ui.call)
        assertEquals(listOf(UiEvent.CallFailed), events)
    }

    @Test
    fun singleAppWithoutAPhoneAppStartsDirectly() = runTest(dispatcher) {
        contacts.canDial = false
        contacts.callApps = listOf(whatsAppVoice)
        val viewModel = grantedViewModel()

        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()

        assertEquals(CallRequest.Launch(whatsAppVoice), viewModel.ui.call)
    }

    @Test
    fun singleAppThatNeedsTheCallPermissionAsksForItDirectly() = runTest(dispatcher) {
        contacts.canDial = false
        contacts.callApps = listOf(guardedVoice)
        val viewModel = grantedViewModel()

        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()

        assertEquals(CallRequest.RequestPermission(guardedVoice), viewModel.ui.call)
    }

    @Test
    fun pickingAnAppWithoutAPermissionStartsIt() = runTest(dispatcher) {
        val viewModel = choosing(whatsAppVoice, guardedVoice)

        viewModel.onAction(UiAction.PickCall(whatsAppVoice))
        advanceUntilIdle()

        assertEquals(CallRequest.Launch(whatsAppVoice), viewModel.ui.call)
    }

    @Test
    fun pickingTheGuardedAppAsksForTheCallPermission() = runTest(dispatcher) {
        val viewModel = choosing(whatsAppVoice, guardedVoice)

        viewModel.onAction(UiAction.PickCall(guardedVoice))
        advanceUntilIdle()

        assertEquals(CallRequest.RequestPermission(guardedVoice), viewModel.ui.call)
    }

    @Test
    fun pickingThePhoneAppDials() = runTest(dispatcher) {
        val viewModel = choosing(guardedVoice)

        viewModel.onAction(UiAction.PickCall(CallOption.Phone("2125550101")))
        advanceUntilIdle()

        assertEquals(CallRequest.Launch(CallOption.Phone("2125550101")), viewModel.ui.call)
    }

    @Test
    fun grantedCallPermissionStartsTheCall() = runTest(dispatcher) {
        val viewModel = choosing(guardedVoice)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.PickCall(guardedVoice))

        viewModel.onAction(UiAction.CallPermissionResult(granted = true, canAskAgain = true))
        advanceUntilIdle()

        assertEquals(CallRequest.Launch(guardedVoice), viewModel.ui.call)
        assertTrue(events.isEmpty())
    }

    @Test
    fun refusedCallPermissionOpensTheChat() = runTest(dispatcher) {
        val viewModel = choosing(guardedVoice)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.PickCall(guardedVoice))

        viewModel.onAction(UiAction.CallPermissionResult(granted = false, canAskAgain = true))
        advanceUntilIdle()

        assertEquals(CallRequest.OpenChat(guardedVoice.chat!!), viewModel.ui.call)
        assertTrue(events.isEmpty())
    }

    @Test
    fun permanentlyRefusedCallPermissionStillOpensTheChat() = runTest(dispatcher) {
        val viewModel = choosing(guardedVoice)
        viewModel.onAction(UiAction.PickCall(guardedVoice))

        viewModel.onAction(UiAction.CallPermissionResult(granted = false, canAskAgain = false))
        advanceUntilIdle()

        assertEquals(CallRequest.OpenChat(guardedVoice.chat!!), viewModel.ui.call)
    }

    @Test
    fun refusedCallPermissionWithoutAChatIsExplained() = runTest(dispatcher) {
        val noChat = guardedVoice.copy(chat = null)
        val viewModel = choosing(noChat)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.PickCall(noChat))

        viewModel.onAction(UiAction.CallPermissionResult(granted = false, canAskAgain = true))
        advanceUntilIdle()

        assertNull(viewModel.ui.call)
        assertEquals(listOf(UiEvent.CallPermissionDenied("Signal", permanently = false)), events)
    }

    @Test
    fun permanentlyRefusedCallPermissionWithoutAChatPointsToSettings() = runTest(dispatcher) {
        val noChat = guardedVoice.copy(chat = null)
        val viewModel = choosing(noChat)
        val events = collectEvents(viewModel)
        viewModel.onAction(UiAction.PickCall(noChat))

        viewModel.onAction(UiAction.CallPermissionResult(granted = false, canAskAgain = false))
        advanceUntilIdle()

        assertNull(viewModel.ui.call)
        assertEquals(listOf(UiEvent.CallPermissionDenied("Signal", permanently = true)), events)
    }

    @Test
    fun callPermissionResultWithoutARequestIsIgnored() = runTest(dispatcher) {
        val viewModel = choosing(guardedVoice)
        val events = collectEvents(viewModel)
        val choice = viewModel.ui.call

        viewModel.onAction(UiAction.CallPermissionResult(granted = false, canAskAgain = false))
        advanceUntilIdle()

        assertEquals(choice, viewModel.ui.call)
        assertTrue(events.isEmpty())
    }

    @Test
    fun pickWithoutAChoiceIsIgnored() = runTest(dispatcher) {
        val viewModel = grantedViewModel()

        viewModel.onAction(UiAction.PickCall(guardedVoice))
        advanceUntilIdle()

        assertNull(viewModel.ui.call)
    }

    /** Avery's first number, with the sheet asking how to call it with the phone app and [apps]. */
    private fun TestScope.choosing(vararg apps: CallOption.App): MainViewModel {
        contacts.callApps = apps.toList()
        val viewModel = grantedViewModel()
        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()
        assertTrue(viewModel.ui.call is CallRequest.Choose)
        return viewModel
    }

    @Test
    fun nothingToCallWithIsReported() = runTest(dispatcher) {
        contacts.canDial = false
        val viewModel = grantedViewModel()
        val events = collectEvents(viewModel)

        viewModel.onAction(UiAction.Call(listOf(601L)))
        advanceUntilIdle()

        assertNull(viewModel.ui.call)
        assertEquals(listOf(UiEvent.CallFailed), events)
    }

    @Test
    fun revokedPermissionDropsThePendingCall() = runTest(dispatcher) {
        val viewModel = grantedViewModel()
        contacts.callGate = CompletableDeferred()
        viewModel.onAction(UiAction.Call(listOf(101L)))
        advanceUntilIdle()

        viewModel.onAction(UiAction.PermissionRechecked(granted = false))
        contacts.callGate!!.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.ui.call)
    }

    // endregion

    private class InMemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    /** Reads work; every write fails like a full disk. */
    private class FailingPreferences : DataStore<Preferences> {
        override val data: Flow<Preferences> = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw IOException("No space left on device")
    }

    /** Simulates the contacts provider with the optimistic `before -> after` write semantics. */
    private class FakeContacts(private val log: MutableList<String> = mutableListOf()) : ContactsDataSource {
        val numbers: MutableMap<Long, String> =
            SampleData.entries.associateTo(LinkedHashMap()) { it.dataId to it.originalNumber }
        var extraEntries: List<PhoneEntry> = emptyList()
        var removedIds: Set<Long> = emptySet()
        var loadGate: CompletableDeferred<Unit>? = null
        var applyGate: CompletableDeferred<Unit>? = null
        var failLoad = false
        var denyLoad = false
        var failRows = false

        /** Writes of these rows throw; they are reported as failed (and left unchanged). */
        var failIds: Set<Long> = emptySet()
        var loadCalls = 0
        var cancelledLoads = 0
        var applyCalls = 0
        var lastConfig: FormatConfig? = null
        val progressKinds = mutableListOf<ProgressKind>()

        /** Apps offered besides the phone app; [callGate] holds the lookup until completed. */
        var callApps: List<CallOption.App> = emptyList()
        var canDial = true
        var callGate: CompletableDeferred<Unit>? = null
        val callLookups = mutableListOf<Pair<PhoneEntry, String>>()

        override suspend fun load(config: FormatConfig): LoadResult {
            loadCalls++
            lastConfig = config
            try {
                loadGate?.await()
            } catch (e: CancellationException) {
                cancelledLoads++
                throw e
            }
            if (denyLoad) return LoadResult.PermissionDenied
            if (failLoad) return LoadResult.Error(IllegalStateException("test"))
            val entries = SampleData.entries
                .filter { it.dataId !in removedIds }
                .map { sample ->
                    val current = numbers.getValue(sample.dataId)
                    sample.copy(
                        originalNumber = current,
                        formattedNumber = if (sample.isValid) sample.formattedNumber else current,
                        isModified = sample.isValid && current != sample.formattedNumber,
                    )
                }
            return LoadResult.Success(entries + extraEntries, SampleData.accounts)
        }

        override suspend fun apply(entries: List<PhoneEntry>, onProgress: ProgressCallback): UpdateResult {
            applyCalls++
            progressKinds += ProgressKind.APPLY
            log += "apply"
            applyGate?.await()
            return commit(entries.map { JournalEntry(it.dataId, it.originalNumber, it.formattedNumber) }, onProgress)
        }

        override suspend fun restore(journal: List<JournalEntry>, onProgress: ProgressCallback): UpdateResult =
            commit(journal.map { JournalEntry(it.dataId, it.after, it.before) }, onProgress)

        override suspend fun write(changes: List<JournalEntry>, onProgress: ProgressCallback): UpdateResult {
            log += "write"
            progressKinds += ProgressKind.RESTORE
            return commit(changes, onProgress)
        }

        /** The sample rows with made-up identities: Google contacts carry a source id. */
        override suspend fun readPhoneRows(): PhoneRowsResult {
            if (denyLoad) return PhoneRowsResult.PermissionDenied
            if (failRows) return PhoneRowsResult.Error(IllegalStateException("test"))
            val rows = SampleData.entries.filter { it.dataId !in removedIds }.map { entry ->
                val synced = entry.accountType.isNotEmpty()
                PhoneRow(
                    dataId = entry.dataId,
                    rawContactId = entry.rawContactId,
                    lookupKey = "lookup-${entry.contactId}",
                    accountType = entry.accountType,
                    accountName = if (synced) "tester@example.com" else null,
                    sourceId = if (synced) "source-${entry.rawContactId}" else null,
                    displayName = entry.displayName,
                    number = numbers.getValue(entry.dataId),
                )
            }
            return PhoneRowsResult.Success(rows)
        }

        override suspend fun callOptions(entry: PhoneEntry, regionIso: String): List<CallOption> {
            callLookups += entry to regionIso
            callGate?.await()
            return listOfNotNull(CallOption.Phone(entry.originalNumber).takeIf { canDial }) + callApps
        }

        /** Every number counts as already formatted from now on. */
        fun formatEverything() {
            SampleData.entries.filter { it.isValid }.forEach { numbers[it.dataId] = it.formattedNumber }
        }

        private suspend fun commit(changes: List<JournalEntry>, onProgress: ProgressCallback): UpdateResult {
            val written = mutableListOf<JournalEntry>()
            val skipped = mutableSetOf<Long>()
            val failed = mutableSetOf<Long>()
            onProgress(0, changes.size)
            changes.forEachIndexed { index, change ->
                when {
                    change.dataId in failIds -> failed += change.dataId
                    numbers[change.dataId] == change.before -> {
                        numbers[change.dataId] = change.after
                        written += change
                    }

                    else -> skipped += change.dataId
                }
                onProgress(index + 1, changes.size)
            }
            return UpdateResult(
                updated = written.size,
                skippedChanged = skipped.size,
                failed = failed.size,
                journal = written,
                skippedIds = skipped,
                failedIds = failed,
            )
        }
    }

    private companion object {
        const val FOLDER = "content://tree/TidyDial backups"
        const val FOLDER_NAME = "TidyDial backups"
        const val OTHER_FOLDER = "content://tree/Other folder"

        fun sampleEntry(dataId: Long, contactId: Long, name: String, original: String, formatted: String) =
            PhoneEntry(
                dataId = dataId,
                rawContactId = contactId * 10,
                contactId = contactId,
                displayName = name,
                originalNumber = original,
                formattedNumber = formatted,
                typeLabel = "Mobile",
                accountType = SampleData.GOOGLE,
                accountLabel = "Google",
                isValid = true,
                isModified = true,
            )
    }
}
