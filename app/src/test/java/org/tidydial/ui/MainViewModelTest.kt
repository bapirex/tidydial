package org.tidydial.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import org.tidydial.data.ContactsDataSource
import org.tidydial.data.CountryCatalog
import org.tidydial.data.JournalEntry
import org.tidydial.data.LoadResult
import org.tidydial.data.PhoneNumberFormatter
import org.tidydial.data.ProgressCallback
import org.tidydial.data.SettingsRepository
import org.tidydial.data.UndoJournal
import org.tidydial.data.UpdateResult
import org.tidydial.model.FormatConfig
import org.tidydial.model.NumberFormatStyle
import org.tidydial.model.PhoneEntry
import org.tidydial.ui.preview.SampleData

// Robolectric only for a working org.json inside UndoJournal.
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        contacts = FakeContacts()
        journalDir = tempFolder.newFolder()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): MainViewModel {
        val settings = SettingsRepository(preferences, catalog) { listOf("us") }
        val viewModel = MainViewModel(
            contacts = contacts,
            settings = settings,
            undoJournal = UndoJournal(journalDir, dispatcher),
            countryCatalog = catalog,
            formatter = PhoneNumberFormatter(),
            defaultDispatcher = dispatcher,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun TestScope.grantedViewModel(): MainViewModel {
        val viewModel = createViewModel()
        viewModel.onAction(UiAction.PermissionRechecked(granted = true))
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.collectEvents(viewModel: MainViewModel): List<UiEvent> {
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
    private class FakeContacts : ContactsDataSource {
        val numbers: MutableMap<Long, String> =
            SampleData.entries.associateTo(LinkedHashMap()) { it.dataId to it.originalNumber }
        var extraEntries: List<PhoneEntry> = emptyList()
        var removedIds: Set<Long> = emptySet()
        var loadGate: CompletableDeferred<Unit>? = null
        var applyGate: CompletableDeferred<Unit>? = null
        var failLoad = false
        var denyLoad = false

        /** Writes of these rows throw; they are reported as failed (and left unchanged). */
        var failIds: Set<Long> = emptySet()
        var loadCalls = 0
        var cancelledLoads = 0
        var applyCalls = 0
        var lastConfig: FormatConfig? = null
        val progressKinds = mutableListOf<ProgressKind>()

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
            applyGate?.await()
            return write(entries.map { JournalEntry(it.dataId, it.originalNumber, it.formattedNumber) }, onProgress)
        }

        override suspend fun restore(journal: List<JournalEntry>, onProgress: ProgressCallback): UpdateResult =
            write(journal.map { JournalEntry(it.dataId, it.after, it.before) }, onProgress)

        /** Every number counts as already formatted from now on. */
        fun formatEverything() {
            SampleData.entries.filter { it.isValid }.forEach { numbers[it.dataId] = it.formattedNumber }
        }

        private suspend fun write(changes: List<JournalEntry>, onProgress: ProgressCallback): UpdateResult {
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
