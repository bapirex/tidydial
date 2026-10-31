package net.evopoint.tidydial.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.evopoint.tidydial.App
import net.evopoint.tidydial.data.ContactsDataSource
import net.evopoint.tidydial.data.CountryCatalog
import net.evopoint.tidydial.data.JournalEntry
import net.evopoint.tidydial.data.LoadResult
import net.evopoint.tidydial.data.PhoneNumberFormatter
import net.evopoint.tidydial.data.SettingsRepository
import net.evopoint.tidydial.data.UndoJournal
import net.evopoint.tidydial.data.UpdateResult
import net.evopoint.tidydial.model.AccountInfo
import net.evopoint.tidydial.model.CallOption
import net.evopoint.tidydial.model.ContactGroup
import net.evopoint.tidydial.model.Country
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import net.evopoint.tidydial.model.PhoneEntry
import net.evopoint.tidydial.model.groupByContact
import java.io.IOException
import java.util.Locale

class MainViewModel(
    private val contacts: ContactsDataSource,
    private val settings: SettingsRepository,
    private val undoJournal: UndoJournal,
    private val countryCatalog: CountryCatalog,
    private val formatter: PhoneNumberFormatter,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private class Loaded(
        val entries: List<PhoneEntry>,
        val groups: List<ContactGroup>,
        val accounts: List<AccountInfo>,
    ) {
        val selectableIds: Set<Long> =
            entries.asSequence().filter { it.isSelectable }.map { it.dataId }.toSet()
    }

    /** State that is not derived from the contact list. */
    private data class Status(
        val permission: PermissionStatus = PermissionStatus.Unknown,
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val loadError: Boolean = false,
        val progress: UpdateProgress? = null,
        val canUndo: Boolean = false,
        val detectedCountryIso: String = SettingsRepository.FALLBACK_COUNTRY,
        val countries: List<Country> = emptyList(),
        val call: CallRequest? = null,
    )

    private class Display(val groups: List<ContactGroup>, val counts: EntryCounts)

    private val status = MutableStateFlow(Status())
    private val loaded = MutableStateFlow<Loaded?>(null)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val filter = MutableStateFlow(ContactFilter.ALL)
    private val query = MutableStateFlow("")

    /**
     * What the Selected filter shows: the selection when the filter, search or data last changed.
     * Unticking a number there keeps its row (unchecked) so a mis-tap can be undone in place.
     */
    private val selectedView = MutableStateFlow<Set<Long>>(emptySet())

    /** The first successful load picks a default filter unless the user already chose one. */
    private var userPickedFilter = false
    private var hasLoadedOnce = false

    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var updateJob: Job? = null
    private var callJob: Job? = null

    @OptIn(FlowPreview::class)
    private val display: Flow<Display> = combine(
        loaded,
        selection,
        filter,
        query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        selectedView,
    ) { data, selected, currentFilter, currentQuery, shownAsSelected ->
        if (data == null) {
            Display(emptyList(), EntryCounts(selected = selected.size))
        } else {
            Display(
                groups = filterGroups(data.groups, shownAsSelected, currentFilter, currentQuery),
                counts = EntryCounts(
                    total = data.entries.size,
                    changes = data.entries.count { it.isModified },
                    selected = selected.size,
                    invalid = data.entries.count { !it.isValid },
                ),
            )
        }
    }.flowOn(defaultDispatcher)

    private val inputs = combine(selection, filter, query) { s, f, q -> Triple(s, f, q) }

    val state: StateFlow<UiState> = combine(
        status,
        display,
        inputs,
        settings.config,
        loaded,
    ) { status, display, (selected, currentFilter, currentQuery), config, data ->
        UiState(
            permission = status.permission,
            isLoading = status.isLoading,
            isRefreshing = status.isRefreshing,
            loadError = status.loadError,
            groups = display.groups,
            counts = display.counts,
            selection = selected,
            filter = currentFilter,
            query = currentQuery,
            config = config,
            detectedCountryIso = status.detectedCountryIso,
            accounts = data?.accounts.orEmpty(),
            countries = status.countries,
            progress = status.progress,
            canUndo = status.canUndo,
            call = status.call,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    init {
        viewModelScope.launch {
            val canUndo = undoJournal.load().isNotEmpty()
            status.update { it.copy(canUndo = canUndo) }
        }
        viewModelScope.launch(defaultDispatcher) {
            val detected = settings.detectedCountryIso
            val countries = countryCatalog.countries(Locale.getDefault())
            status.update { it.copy(detectedCountryIso = detected, countries = countries) }
        }
    }

    fun onAction(action: UiAction) {
        when (action) {
            is UiAction.ToggleEntry -> toggleEntries(listOf(action.dataId))
            is UiAction.ToggleEntries -> toggleEntries(action.dataIds)
            is UiAction.ToggleContact -> toggleEntries(action.dataIds)
            UiAction.SelectAllVisible -> selectAllVisible()
            UiAction.ClearSelection -> {
                selection.value = emptySet()
                selectedView.value = emptySet()
            }

            is UiAction.SetFilter -> {
                userPickedFilter = true
                selectedView.value = selection.value
                filter.value = action.filter
            }

            is UiAction.SetQuery -> {
                if (action.query != query.value) selectedView.value = selection.value
                query.value = action.query
            }
            UiAction.Refresh -> load()
            UiAction.ApplySelected -> applySelected()
            UiAction.UndoLast -> undoLast()
            is UiAction.UpdateConfig -> updateConfig(action.config)
            is UiAction.PermissionResult -> onPermissionResult(action.granted, action.canAskAgain)
            is UiAction.PermissionRechecked -> onPermissionRechecked(action.granted)
            is UiAction.Call -> call(action.dataIds)
            is UiAction.PickCall -> pickCall(action.option)
            is UiAction.CallPermissionResult -> onCallPermissionResult(action.granted, action.canAskAgain)
            UiAction.DismissCall -> status.update { it.copy(call = null) }
            UiAction.CallFailed -> {
                status.update { it.copy(call = null) }
                eventChannel.trySend(UiEvent.CallFailed)
            }
            // The UI opens the settings itself.
            UiAction.OpenAppSettings -> Unit
        }
    }

    /** The example mobile number of [region] in [style], for the options sheet preview. */
    fun exampleNumber(region: String, style: NumberFormatStyle): String? =
        formatter.exampleNumber(region, style)

    // region Selection

    /**
     * Selects all selectable [dataIds], or clears them when they are all selected already. The UI
     * passes only the numbers it shows, so hidden numbers keep their state.
     */
    private fun toggleEntries(dataIds: Collection<Long>) {
        val data = loaded.value ?: return
        val ids = dataIds.filterTo(HashSet()) { it in data.selectableIds }
        if (ids.isEmpty()) return
        selection.update { current ->
            if (current.containsAll(ids)) current - ids else current + ids
        }
    }

    /** Selects what is on screen: the groups of the last state, which follow the debounced search. */
    private fun selectAllVisible() {
        val data = loaded.value ?: return
        val ids = state.value.groups.flatMap { group -> group.entries.map { it.dataId } }
            .filter { it in data.selectableIds }
        selection.update { it + ids }
    }

    // endregion

    // region Loading

    private fun load() {
        val current = status.value
        if (current.permission != PermissionStatus.Granted || current.progress != null) return
        loadJob?.cancel()
        // Started lazily so loadJob is assigned before the body can reach its finally block.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            status.update {
                val hasData = loaded.value != null
                it.copy(isLoading = !hasData, isRefreshing = hasData, loadError = false)
            }
            try {
                val config = settings.config.first()
                when (val result = contacts.load(config)) {
                    is LoadResult.Success -> onLoaded(result)
                    LoadResult.PermissionDenied -> onPermissionLost()
                    is LoadResult.Error -> {
                        status.update { it.copy(loadError = true) }
                        eventChannel.trySend(UiEvent.LoadFailed)
                    }
                }
            } finally {
                if (loadJob === currentCoroutineContext().job) {
                    status.update { it.copy(isLoading = false, isRefreshing = false) }
                }
            }
        }
        loadJob = job
        job.start()
    }

    private fun onLoaded(result: LoadResult.Success) {
        val previous = loaded.value
        val next = Loaded(result.entries, result.entries.groupByContact(), result.accounts)
        val previousSelection = selection.value
        // Keep the user's choice for numbers that were already selectable; pre-select the rest.
        selection.value = next.selectableIds.filterTo(HashSet()) { id ->
            if (previous != null && id in previous.selectableIds) id in previousSelection else true
        }
        selectedView.value = selection.value
        if (!hasLoadedOnce) {
            hasLoadedOnce = true
            // Open on the numbers that need work; a tidy address book opens on All.
            if (!userPickedFilter && next.entries.any { it.isModified }) {
                filter.value = ContactFilter.CHANGES
            }
        }
        loaded.value = next
    }

    private fun updateConfig(config: FormatConfig) {
        viewModelScope.launch {
            try {
                settings.update(config)
            } catch (e: IOException) {
                // The stored config (and so the list) stays as it was.
                Log.w(TAG, "Could not save the format options", e)
                eventChannel.trySend(UiEvent.SettingsSaveFailed)
                return@launch
            }
            load()
        }
    }

    // endregion

    // region Update and undo

    private fun applySelected() {
        if (updateJob?.isActive == true) return
        val data = loaded.value ?: return
        val selected = selection.value
        val entries = data.entries.filter { it.dataId in selected && it.isSelectable }
        if (entries.isEmpty()) return
        val planned = entries
            .filter { it.formattedNumber != it.originalNumber }
            .distinctBy { it.dataId }
            .map { JournalEntry(it.dataId, before = it.originalNumber, after = it.formattedNumber) }
        updateJob = viewModelScope.launch {
            runUpdate(ProgressKind.APPLY, entries.size) {
                val previous = undoJournal.load()
                // Write-ahead: the journal is on disk before any contact changes, so a crash or a
                // killed process mid-run can still be undone. Undo skips rows that were not written.
                if (!saveJournal(planned)) return@runUpdate null
                val result = contacts.apply(entries) { done, total -> reportProgress(done, total) }
                // Keep what was written plus rows that failed after a partial batch commit; if
                // nothing could have changed, the previous update stays undoable.
                val written = result.journal.mapTo(HashSet()) { it.dataId } + result.failedIds
                val keep = planned.filter { it.dataId in written }
                saveJournal(keep.ifEmpty { previous })
                result
            }
        }
    }

    private fun undoLast() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            val journal = undoJournal.load()
            if (journal.isEmpty()) {
                status.update { it.copy(canUndo = false) }
                return@launch
            }
            runUpdate(ProgressKind.UNDO, journal.size) {
                val result = contacts.restore(journal) { done, total -> reportProgress(done, total) }
                // Keep what failed or was never attempted so the user can retry. Skipped rows were
                // edited since the update and would be skipped again, so they are dropped.
                val done = result.journal.mapTo(HashSet()) { it.dataId } + result.skippedIds
                saveJournal(journal.filter { it.dataId !in done })
                result
            }
        }
    }

    /** Saves (or, for an empty list, clears) the journal; false when the file could not be written. */
    private suspend fun saveJournal(entries: List<JournalEntry>): Boolean = try {
        undoJournal.save(entries)
        true
    } catch (e: IOException) {
        Log.w(TAG, "Could not save the undo journal", e)
        false
    }

    /**
     * Runs [block] (write + journal bookkeeping) to completion even if the ViewModel is cleared,
     * so the journal on disk always matches what was written. Then reports and reloads. A null
     * result means nothing was written because the journal could not be saved first.
     */
    private suspend fun runUpdate(kind: ProgressKind, total: Int, block: suspend () -> UpdateResult?) {
        loadJob?.cancel()
        status.update {
            it.copy(progress = UpdateProgress(0, total, kind), isLoading = false, isRefreshing = false)
        }
        val result = try {
            withContext(NonCancellable) { block() }
        } finally {
            status.update { it.copy(progress = null) }
        }
        val canUndo = undoJournal.load().isNotEmpty()
        status.update { it.copy(canUndo = canUndo) }
        if (result == null) {
            eventChannel.trySend(UiEvent.JournalSaveFailed)
            return
        }
        eventChannel.trySend(
            when (kind) {
                ProgressKind.APPLY -> UiEvent.Updated(result.updated, result.skippedChanged, result.failed)
                ProgressKind.UNDO -> UiEvent.Undone(result.updated, result.skippedChanged, result.failed)
            },
        )
        if (result.permissionRevoked) onPermissionLost() else load()
    }

    private fun reportProgress(done: Int, total: Int) {
        status.update { current ->
            val progress = current.progress ?: return@update current
            current.copy(progress = progress.copy(done = done, total = total))
        }
    }

    // endregion

    // region Calling

    /**
     * Looks up the ways to call the number stored as [dataIds]. A single way (the phone app or
     * another app) starts right away; otherwise the user picks. Nothing to call with is reported
     * like a failed call. A newer tap replaces a lookup still running.
     */
    private fun call(dataIds: List<Long>) {
        val data = loaded.value ?: return
        val entry = data.entries.firstOrNull { it.dataId in dataIds } ?: return
        callJob?.cancel()
        callJob = viewModelScope.launch {
            val region = settings.config.first().countryIso
            val options = contacts.callOptions(entry, region)
            if (options.isEmpty()) {
                eventChannel.trySend(UiEvent.CallFailed)
                return@launch
            }
            val request = options.singleOrNull()?.let(::startRequest) ?: CallRequest.Choose(
                name = entry.displayName,
                number = if (entry.isValid) entry.formattedNumber else entry.originalNumber,
                options = options,
            )
            status.update { it.copy(call = request) }
        }
    }

    private fun pickCall(option: CallOption) {
        status.update { if (it.call is CallRequest.Choose) it.copy(call = startRequest(option)) else it }
    }

    /** Starts [option], first asking for the call permission if its app needs it. */
    private fun startRequest(option: CallOption): CallRequest =
        if (option is CallOption.App && option.needsCallPermission) {
            CallRequest.RequestPermission(option)
        } else {
            CallRequest.Launch(option)
        }

    /**
     * Starts the call once allowed. Refused, the app's chat with the contact opens instead, where
     * the user can still call; an app without one is explained in a Snackbar.
     */
    private fun onCallPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        val option = (status.value.call as? CallRequest.RequestPermission)?.option ?: return
        val next = when {
            granted -> CallRequest.Launch(option)
            option.chat != null -> CallRequest.OpenChat(option.chat)
            else -> null
        }
        status.update { it.copy(call = next) }
        if (next == null) {
            eventChannel.trySend(UiEvent.CallPermissionDenied(option.label, permanently = !canAskAgain))
        }
    }

    // endregion

    // region Permission

    private fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        when {
            granted -> onGranted()
            canAskAgain -> setPermission(PermissionStatus.Denied)
            else -> setPermission(PermissionStatus.PermanentlyDenied)
        }
    }

    private fun onPermissionRechecked(granted: Boolean) {
        val current = status.value.permission
        when {
            granted -> onGranted()
            current == PermissionStatus.Granted -> onPermissionLost()
            // A recheck cannot tell "denied" from "permanently denied"; keep the stronger state.
            current == PermissionStatus.PermanentlyDenied -> Unit
            else -> setPermission(PermissionStatus.Denied)
        }
    }

    private fun onGranted() {
        if (status.value.permission == PermissionStatus.Granted) return
        setPermission(PermissionStatus.Granted)
        load()
    }

    private fun onPermissionLost() {
        val wasGranted = status.value.permission == PermissionStatus.Granted
        loadJob?.cancel()
        callJob?.cancel()
        loaded.value = null
        selection.value = emptySet()
        status.update {
            it.copy(
                permission = PermissionStatus.Denied,
                isLoading = false,
                isRefreshing = false,
                loadError = false,
                call = null,
            )
        }
        if (wasGranted) eventChannel.trySend(UiEvent.PermissionRevoked)
    }

    private fun setPermission(permission: PermissionStatus) {
        status.update { it.copy(permission = permission) }
    }

    // endregion

    companion object {
        private const val TAG = "MainViewModel"
        private const val SEARCH_DEBOUNCE_MS = 150L
        private const val STOP_TIMEOUT_MS = 5_000L
        private val NON_DIGITS = Regex("\\D")
        private const val DIAL_PUNCTUATION = "+-()./ "

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as App).container
                MainViewModel(
                    contacts = container.contactsRepository,
                    settings = container.settingsRepository,
                    undoJournal = container.undoJournal,
                    countryCatalog = container.countryCatalog,
                    formatter = container.formatter,
                )
            }
        }

        /**
         * Applies [filter] per entry and [query] per contact name or number digits. Groups keep
         * only their matching entries; empty groups are dropped. [selection] is what the Selected
         * filter shows.
         */
        internal fun filterGroups(
            groups: List<ContactGroup>,
            selection: Set<Long>,
            filter: ContactFilter,
            query: String,
        ): List<ContactGroup> {
            val text = query.trim()
            val digits = if (text.all { it.isDigit() || it in DIAL_PUNCTUATION }) {
                text.replace(NON_DIGITS, "")
            } else {
                ""
            }
            return groups.mapNotNull { group ->
                val nameMatches = text.isEmpty() || group.displayName.contains(text, ignoreCase = true)
                val entries = group.entries.filter { entry ->
                    entry.matches(filter, selection) &&
                        (nameMatches || entry.matchesNumber(text, digits))
                }
                when {
                    entries.isEmpty() -> null
                    entries.size == group.entries.size -> group
                    else -> group.copy(entries = entries)
                }
            }
        }

        private fun PhoneEntry.matches(filter: ContactFilter, selection: Set<Long>): Boolean =
            when (filter) {
                ContactFilter.ALL -> true
                ContactFilter.CHANGES -> isModified
                ContactFilter.SELECTED -> dataId in selection
                ContactFilter.INVALID -> !isValid
            }

        private fun PhoneEntry.matchesNumber(text: String, digits: String): Boolean {
            if (originalNumber.contains(text, ignoreCase = true)) return true
            if (formattedNumber.contains(text, ignoreCase = true)) return true
            if (digits.isEmpty()) return false
            return originalNumber.replace(NON_DIGITS, "").contains(digits) ||
                formattedNumber.replace(NON_DIGITS, "").contains(digits)
        }
    }
}
