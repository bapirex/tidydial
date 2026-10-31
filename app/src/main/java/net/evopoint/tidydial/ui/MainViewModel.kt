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
import net.evopoint.tidydial.data.BackupFailure
import net.evopoint.tidydial.data.BackupManager
import net.evopoint.tidydial.data.BackupStore
import net.evopoint.tidydial.data.ContactsDataSource
import net.evopoint.tidydial.data.CountryCatalog
import net.evopoint.tidydial.data.JournalEntry
import net.evopoint.tidydial.data.LoadResult
import net.evopoint.tidydial.data.PhoneNumberFormatter
import net.evopoint.tidydial.data.RestorePlan
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
    private val backupStore: BackupStore,
    private val backups: BackupManager,
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
        val backup: BackupUi = BackupUi(),
        val backupDialog: BackupDialog? = null,
        val restore: RestoreConfirmation? = null,
    )

    /** How an update, undo or restore ended; null in [runUpdate] when the journal was not saved. */
    private sealed interface RunResult {
        /**
         * [changedBefore] and [notFound]: restore entries left alone before writing.
         * [backupFailed]: the backup before a restore could not be written.
         */
        data class Done(
            val result: UpdateResult,
            val changedBefore: Int = 0,
            val notFound: Int = 0,
            val backupFailed: Boolean = false,
        ) : RunResult

        /** The backup could not be saved first, so nothing was written. */
        data class BackupFailed(val failure: BackupFailure) : RunResult

        data object PermissionLost : RunResult
    }

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
    private var restoreJob: Job? = null

    /** What [UiState.restore] would write once confirmed. */
    private var pendingRestore: RestorePlan? = null

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
            backup = status.backup,
            backupDialog = status.backupDialog,
            restore = status.restore,
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
        viewModelScope.launch {
            settings.backup.collect { prefs ->
                val available = prefs.folderUri?.let { backupStore.hasAccess(it) } ?: true
                status.update {
                    it.copy(
                        backup = it.backup.copy(
                            enabled = prefs.enabled,
                            folderName = prefs.folderName,
                            folderAvailable = available,
                        ),
                    )
                }
            }
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
            is UiAction.BackupFolderChosen -> onBackupFolderChosen(action.uri)
            UiAction.ContinueWithoutBackup -> continueWithoutBackup()
            UiAction.UpdateWithoutBackup -> {
                status.update { it.copy(backupDialog = null, backup = it.backup.copy(error = null)) }
                applySelected(skipBackup = true)
            }

            UiAction.DismissBackupDialog -> status.update {
                it.copy(backupDialog = null, backup = it.backup.copy(error = null))
            }

            is UiAction.SetBackupEnabled -> setBackupEnabled(action.enabled)
            UiAction.DismissBackupError -> setBackupError(null)
            is UiAction.RestoreFileChosen -> prepareRestore(action.uri)
            UiAction.ConfirmRestore -> confirmRestore()
            UiAction.DismissRestore -> {
                pendingRestore = null
                status.update { it.copy(restore = null) }
            }

            UiAction.FolderPickerUnavailable -> setBackupError(BackupError.NO_PICKER)
            UiAction.FilePickerUnavailable -> eventChannel.trySend(UiEvent.NoFilePicker)
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

    /** [skipBackup]: the user chose to run this update without the backup that failed. */
    private fun applySelected(skipBackup: Boolean = false) {
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
            val backup = settings.backup.first()
            if (!backup.asked) {
                // The first update waits for a backup choice; nothing is written yet.
                status.update { it.copy(backupDialog = BackupDialog.FirstUpdate) }
                return@launch
            }
            runUpdate(ProgressKind.APPLY, entries.size) {
                if (backup.enabled && !skipBackup) {
                    // Write-ahead, like the journal: the backup is in the user's folder before
                    // any contact changes, and a backup that cannot be written stops the update.
                    setBackingUp(true)
                    val country = settings.config.first().countryIso
                    when (val outcome = backups.backUpBeforeUpdate(entries, country)) {
                        is BackupManager.WriteOutcome.Failed -> return@runUpdate RunResult.BackupFailed(outcome.failure)
                        BackupManager.WriteOutcome.PermissionDenied -> return@runUpdate RunResult.PermissionLost
                        else -> setBackingUp(false)
                    }
                }
                journaledWrite(planned) {
                    contacts.apply(entries) { done, total -> reportProgress(done, total) }
                }
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
            // Undo never needs a backup: it must work when the backup folder is gone.
            runUpdate(ProgressKind.UNDO, journal.size) {
                val result = contacts.restore(journal) { done, total -> reportProgress(done, total) }
                // Keep what failed or was never attempted so the user can retry. Skipped rows were
                // edited since the update and would be skipped again, so they are dropped.
                val done = result.journal.mapTo(HashSet()) { it.dataId } + result.skippedIds
                saveJournal(journal.filter { it.dataId !in done })
                RunResult.Done(result)
            }
        }
    }

    /**
     * Saves [planned] as the journal, runs [write] and shrinks the journal to what was written.
     * Write-ahead: the journal is on disk before any contact changes, so a crash or a killed
     * process mid-run can still be undone. Undo skips rows that were not written.
     */
    private suspend fun journaledWrite(
        planned: List<JournalEntry>,
        write: suspend () -> UpdateResult,
    ): RunResult.Done? {
        val previous = undoJournal.load()
        if (!saveJournal(planned)) return null
        val result = write()
        // Keep what was written plus rows that failed after a partial batch commit; if nothing
        // could have changed, the previous update stays undoable.
        val written = result.journal.mapTo(HashSet()) { it.dataId } + result.failedIds
        val keep = planned.filter { it.dataId in written }
        saveJournal(keep.ifEmpty { previous })
        return RunResult.Done(result)
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
     * Runs [block] (backup, write and journal bookkeeping) to completion even if the ViewModel is
     * cleared, so the journal on disk always matches what was written. Then reports and reloads.
     * A null result means nothing was written because the journal could not be saved first.
     */
    private suspend fun runUpdate(kind: ProgressKind, total: Int, block: suspend () -> RunResult?) {
        loadJob?.cancel()
        status.update {
            it.copy(progress = UpdateProgress(0, total, kind), isLoading = false, isRefreshing = false)
        }
        val outcome = try {
            withContext(NonCancellable) { block() }
        } finally {
            status.update { it.copy(progress = null) }
        }
        val canUndo = undoJournal.load().isNotEmpty()
        status.update { it.copy(canUndo = canUndo) }
        when (outcome) {
            null -> eventChannel.trySend(UiEvent.JournalSaveFailed)
            RunResult.PermissionLost -> onPermissionLost()
            is RunResult.BackupFailed -> {
                Log.w(TAG, "Backup failed (${outcome.failure}), the update did not start")
                if (outcome.failure == BackupFailure.READ_CONTACTS_FAILED) {
                    // Not the folder's fault: choosing it again would not help.
                    eventChannel.trySend(UiEvent.BackupReadFailed)
                } else {
                    refreshBackupAccess()
                    status.update { it.copy(backupDialog = BackupDialog.WriteFailed(it.backup.folderName)) }
                }
            }

            is RunResult.Done -> {
                val result = outcome.result
                eventChannel.trySend(
                    when (kind) {
                        ProgressKind.APPLY -> UiEvent.Updated(result.updated, result.skippedChanged, result.failed)
                        ProgressKind.UNDO -> UiEvent.Undone(result.updated, result.skippedChanged, result.failed)
                        ProgressKind.RESTORE -> UiEvent.RestoredFromBackup(
                            restored = result.updated,
                            skippedChanged = result.skippedChanged + outcome.changedBefore,
                            notFound = outcome.notFound,
                            failed = result.failed,
                            backupFailed = outcome.backupFailed,
                        )
                    },
                )
                if (result.permissionRevoked) onPermissionLost() else load()
            }
        }
    }

    private fun setBackingUp(backingUp: Boolean) {
        status.update { current ->
            val progress = current.progress ?: return@update current
            current.copy(progress = progress.copy(isBackingUp = backingUp))
        }
    }

    private fun reportProgress(done: Int, total: Int) {
        status.update { current ->
            val progress = current.progress ?: return@update current
            current.copy(progress = progress.copy(done = done, total = total))
        }
    }

    // endregion

    // region Backups

    /**
     * Keeps access to the picked folder and turns backups on. An update held by the backup prompt
     * or a failed backup continues with it.
     */
    private fun onBackupFolderChosen(uri: String?) {
        // Cancelled: a prompt or dialog stays open for another choice.
        if (uri == null) return
        setBackupError(null)
        viewModelScope.launch {
            val folder = backupStore.adoptFolder(uri)
            if (folder == null) {
                setBackupError(BackupError.FOLDER_FAILED)
                return@launch
            }
            val previous = settings.backup.first().folderUri
            try {
                settings.setBackupFolder(folder.uri, folder.name)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save the backup folder", e)
                if (folder.uri != previous) backupStore.releaseFolder(folder.uri)
                setBackupError(BackupError.SAVE_FAILED)
                return@launch
            }
            // Android caps the grants an app may keep, so the old folder's is given back.
            if (previous != null && previous != folder.uri) backupStore.releaseFolder(previous)
            refreshBackupAccess()
            if (status.value.backupDialog != null) {
                status.update { it.copy(backupDialog = null) }
                // The dialog is modal, so the selection is still the one the user confirmed.
                applySelected()
            }
        }
    }

    private fun continueWithoutBackup() {
        setBackupError(null)
        viewModelScope.launch {
            try {
                settings.setBackupEnabled(false)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save the backup choice", e)
                // The prompt stays open and shows why.
                setBackupError(BackupError.SAVE_FAILED)
                return@launch
            }
            status.update { it.copy(backupDialog = null) }
            applySelected()
        }
    }

    private fun setBackupEnabled(enabled: Boolean) {
        setBackupError(null)
        viewModelScope.launch {
            // Turning backups on needs a folder; the UI opens the picker instead.
            if (enabled && settings.backup.first().folderUri == null) return@launch
            try {
                settings.setBackupEnabled(enabled)
            } catch (e: IOException) {
                Log.w(TAG, "Could not save the backup choice", e)
                setBackupError(BackupError.SAVE_FAILED)
            }
        }
    }

    private fun setBackupError(error: BackupError?) {
        status.update { it.copy(backup = it.backup.copy(error = error)) }
    }

    private fun refreshBackupAccess() {
        viewModelScope.launch {
            val uri = settings.backup.first().folderUri ?: return@launch
            val available = backupStore.hasAccess(uri)
            status.update { it.copy(backup = it.backup.copy(folderAvailable = available)) }
        }
    }

    /** Reads and plans a restore from the backup the user picked, then asks to confirm it. */
    private fun prepareRestore(uri: String) {
        if (updateJob?.isActive == true || restoreJob?.isActive == true) return
        restoreJob = viewModelScope.launch {
            val country = settings.config.first().countryIso
            when (val outcome = backups.prepareRestore(uri, country)) {
                is BackupManager.RestoreOutcome.Invalid -> eventChannel.trySend(UiEvent.BackupUnreadable(outcome.error))
                BackupManager.RestoreOutcome.ContactsFailed -> eventChannel.trySend(UiEvent.LoadFailed)
                BackupManager.RestoreOutcome.PermissionDenied -> onPermissionLost()
                is BackupManager.RestoreOutcome.Ready -> {
                    val plan = outcome.plan
                    if (plan.changes.isEmpty()) {
                        eventChannel.trySend(
                            UiEvent.NothingToRestore(plan.alreadyRestored, plan.changedSince, plan.notFound),
                        )
                        return@launch
                    }
                    pendingRestore = plan
                    status.update {
                        it.copy(
                            restore = RestoreConfirmation(
                                kind = outcome.backup.kind,
                                createdAt = outcome.backup.createdAt,
                                count = plan.changes.size,
                                changedSince = plan.changedSince,
                                notFound = plan.notFound,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Writes the planned restore like an update: journaled first, so "Undo last update" undoes
     * it. With backups on, the numbers it replaces are backed up first, so they outlive an
     * uninstall too; but a restore never needs a folder, so a backup that fails is reported and
     * the restore goes on.
     */
    private fun confirmRestore() {
        val plan = pendingRestore ?: return
        pendingRestore = null
        status.update { it.copy(restore = null) }
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            runUpdate(ProgressKind.RESTORE, plan.changes.size) {
                var backupFailed = false
                if (settings.backup.first().enabled) {
                    setBackingUp(true)
                    val country = settings.config.first().countryIso
                    when (val outcome = backups.backUpBeforeRestore(plan.changes, country)) {
                        is BackupManager.WriteOutcome.Failed -> {
                            Log.w(TAG, "Backup before the restore failed (${outcome.failure}), restoring anyway")
                            backupFailed = true
                            refreshBackupAccess()
                        }

                        BackupManager.WriteOutcome.PermissionDenied -> return@runUpdate RunResult.PermissionLost
                        else -> Unit
                    }
                    setBackingUp(false)
                }
                journaledWrite(plan.changes) {
                    contacts.write(plan.changes) { done, total -> reportProgress(done, total) }
                }?.copy(changedBefore = plan.changedSince, notFound = plan.notFound, backupFailed = backupFailed)
            }
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
        // The folder's access may have been removed in the meantime.
        refreshBackupAccess()
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
        restoreJob?.cancel()
        pendingRestore = null
        loaded.value = null
        selection.value = emptySet()
        status.update {
            it.copy(
                permission = PermissionStatus.Denied,
                isLoading = false,
                isRefreshing = false,
                loadError = false,
                call = null,
                backupDialog = null,
                restore = null,
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
                    backupStore = container.backupStore,
                    backups = container.backupManager,
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
