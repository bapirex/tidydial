package net.evopoint.tidydial.ui

import net.evopoint.tidydial.data.BackupKind
import net.evopoint.tidydial.data.BackupReadError
import net.evopoint.tidydial.model.AccountInfo
import net.evopoint.tidydial.model.AppChat
import net.evopoint.tidydial.model.CallOption
import net.evopoint.tidydial.model.ContactGroup
import net.evopoint.tidydial.model.Country
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.PhoneEntry
import java.time.OffsetDateTime

enum class PermissionStatus {
    /** Not checked yet (first frames after launch). */
    Unknown,
    Granted,

    /** Not granted, but the system dialog can still be shown. */
    Denied,

    /** The system will not show the dialog again; the user has to use app settings. */
    PermanentlyDenied,
}

enum class ContactFilter { ALL, CHANGES, SELECTED, INVALID }

enum class ProgressKind { APPLY, UNDO, RESTORE }

/**
 * Progress of a running update, undo or restore; [done] goes from 0 to [total].
 * [isBackingUp]: the backup file is being written before the update starts.
 */
data class UpdateProgress(
    val done: Int,
    val total: Int,
    val kind: ProgressKind,
    val isBackingUp: Boolean = false,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Counts over every loaded number, independent of the current filter and search, so the filter
 * chips stay stable while the user types.
 *
 * @property total all numbers (excluded accounts are not loaded).
 * @property changes numbers that would be rewritten ([PhoneEntry.isModified]).
 * @property selected numbers selected for the next update.
 * @property invalid numbers libphonenumber considers invalid.
 */
data class EntryCounts(
    val total: Int = 0,
    val changes: Int = 0,
    val selected: Int = 0,
    val invalid: Int = 0,
) {
    fun of(filter: ContactFilter): Int = when (filter) {
        ContactFilter.ALL -> total
        ContactFilter.CHANGES -> changes
        ContactFilter.SELECTED -> selected
        ContactFilter.INVALID -> invalid
    }
}

/** A call started from a number row, once the ways to call it are known. */
sealed interface CallRequest {
    /** Start [option]: the only way to call, the one picked, or one now allowed. */
    data class Launch(val option: CallOption) : CallRequest

    /**
     * [option] only starts for apps that hold CALL_PHONE: ask for it unless it is granted already,
     * and report with [UiAction.CallPermissionResult].
     */
    data class RequestPermission(val option: CallOption.App) : CallRequest

    /** The call permission was refused: open the app's [chat] with the contact instead. */
    data class OpenChat(val chat: AppChat) : CallRequest

    /**
     * Ask how to call [number] (as shown in the list) of [name]: [options] starts with the phone
     * app (unless the device has none), followed by other apps.
     */
    data class Choose(
        val name: String,
        val number: String,
        val options: List<CallOption>,
    ) : CallRequest
}

/**
 * The Backups section of the options sheet.
 *
 * @property folderName the backup folder, or null when none was chosen.
 * @property folderAvailable TidyDial still has access to the folder (false after the grant was
 *   revoked); a deleted folder only shows when a backup fails.
 * @property error why the last backup setting did not take effect. Shown inside the sheet or
 *   dialog the user is looking at (the backup prompt, the failure dialog or the options sheet),
 *   since a Snackbar would be hidden behind it; cleared by the next backup action or
 *   [UiAction.DismissBackupError].
 */
data class BackupUi(
    val enabled: Boolean = false,
    val folderName: String? = null,
    val folderAvailable: Boolean = true,
    val error: BackupError? = null,
)

enum class BackupError {
    /** TidyDial could not keep access to the picked folder. */
    FOLDER_FAILED,

    /** The backup choice could not be saved. */
    SAVE_FAILED,

    /** No app could be opened to pick a folder. */
    NO_PICKER,
}

/** A backup question that holds an update until it is answered. */
sealed interface BackupDialog {
    /** The first update, before the user chose a backup folder or to continue without. */
    data object FirstUpdate : BackupDialog

    /**
     * The backup could not be written to [folderName], so the update did not start. [tooLarge]:
     * the file would be too large to restore, so another folder would not help.
     */
    data class WriteFailed(val folderName: String?, val tooLarge: Boolean = false) : BackupDialog
}

/**
 * Asks before restoring [count] numbers from a backup of [kind] made at [createdAt].
 * [changedSince] and [notFound] entries will be left alone.
 */
data class RestoreConfirmation(
    val kind: BackupKind,
    val createdAt: OffsetDateTime,
    val count: Int,
    val changedSince: Int,
    val notFound: Int,
)

/**
 * Everything the UI renders.
 *
 * @property isLoading the first load is running and nothing is shown yet.
 * @property isRefreshing a reload is running while the previous list stays visible.
 * @property loadError the last load failed (a [UiEvent.LoadFailed] was also sent).
 * @property groups contacts to display after filter and search; a group only holds its matching
 *   entries and groups without any are dropped. The Selected filter shows what was selected when
 *   it was opened, so numbers unticked there stay visible.
 * @property selection selected `dataId`s; only valid, modified entries are ever selected.
 * @property query the raw search text; [groups] follows it after a short debounce. Text fields
 *   should keep their own state and only report changes via [UiAction.SetQuery].
 * @property detectedCountryIso the auto-detected country (SIM, network, locale).
 * @property accounts every account type on the device with its count, for the exclusion switches.
 * @property countries all supported countries sorted by localized name (empty until computed).
 * @property progress non-null while an update or undo runs.
 * @property canUndo a journal of the last update exists.
 * @property call a call to start, to choose an app for or to ask the call permission for; the UI
 *   reports back with [UiAction.PickCall], [UiAction.CallPermissionResult], [UiAction.DismissCall]
 *   or [UiAction.CallFailed].
 * @property backup the backup settings.
 * @property backupDialog a backup question holding an update; the UI answers with
 *   [UiAction.BackupFolderChosen], [UiAction.ContinueWithoutBackup], [UiAction.UpdateWithoutBackup]
 *   or [UiAction.DismissBackupDialog].
 * @property restore a restore waiting for [UiAction.ConfirmRestore] or [UiAction.DismissRestore].
 * @property preparingRestore the backup picked for a restore is being read and compared with the
 *   contacts, which can take seconds for a large book or a cloud file; [UiAction.DismissRestore]
 *   stops it.
 */
data class UiState(
    val permission: PermissionStatus = PermissionStatus.Unknown,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val loadError: Boolean = false,
    val groups: List<ContactGroup> = emptyList(),
    val counts: EntryCounts = EntryCounts(),
    val selection: Set<Long> = emptySet(),
    val filter: ContactFilter = ContactFilter.ALL,
    val query: String = "",
    val config: FormatConfig = FormatConfig(),
    val detectedCountryIso: String = config.countryIso,
    val accounts: List<AccountInfo> = emptyList(),
    val countries: List<Country> = emptyList(),
    val progress: UpdateProgress? = null,
    val canUndo: Boolean = false,
    val call: CallRequest? = null,
    val backup: BackupUi = BackupUi(),
    val backupDialog: BackupDialog? = null,
    val restore: RestoreConfirmation? = null,
    val preparingRestore: Boolean = false,
) {
    val isUpdating: Boolean get() = progress != null

    /** Selectable numbers among [groups] (what is on screen). */
    val visibleSelectable: Int = groups.sumOf { group -> group.entries.count { it.isSelectable } }

    /** Selectable numbers among [groups] that are not selected; "Select all shown" adds these. */
    val visibleUnselected: Int = groups.sumOf { group ->
        group.entries.count { it.isSelectable && it.dataId !in selection }
    }

    /** Every account that has numbers is skipped in the options, so nothing was loaded. */
    val allAccountsSkipped: Boolean
        get() = counts.total == 0 &&
            accounts.any { it.count > 0 } &&
            accounts.filter { it.count > 0 }.all { it.type in config.excludedAccountTypes }

    /** True once a load succeeded and found no numbers at all (as opposed to no matches). */
    val hasNoContacts: Boolean get() = !isLoading && !loadError && counts.total == 0

    /** Whether [entry] can be ticked: only valid numbers that would actually change. */
    fun isSelectable(entry: PhoneEntry): Boolean = entry.isSelectable

    fun isSelected(entry: PhoneEntry): Boolean = entry.dataId in selection
}

/** Only valid numbers that would actually change can be selected. */
val PhoneEntry.isSelectable: Boolean get() = isValid && isModified

/** One-shot messages; the UI formats them with plurals. */
sealed interface UiEvent {
    /** An update finished. [skippedChanged] numbers were edited elsewhere since loading. */
    data class Updated(val updated: Int, val skippedChanged: Int, val failed: Int) : UiEvent

    /** An undo finished. [skippedChanged] numbers were edited since the update and kept. */
    data class Undone(val restored: Int, val skippedChanged: Int, val failed: Int) : UiEvent

    /** The undo journal could not be written before an update, so no contact was changed. */
    data object JournalSaveFailed : UiEvent

    /** The format options could not be saved; the previous ones stay in effect. */
    data object SettingsSaveFailed : UiEvent

    data object LoadFailed : UiEvent

    /** Contacts access was lost (revoked in settings) while the app was running. */
    data object PermissionRevoked : UiEvent

    /** No app could be opened for a call (none installed, or it refused). */
    data object CallFailed : UiEvent

    /**
     * The call permission that [app] needs was refused, and it has no chat to open instead.
     * [permanently]: the system will not ask again, only app settings can grant it.
     */
    data class CallPermissionDenied(val app: String, val permanently: Boolean) : UiEvent

    /**
     * The call permission that [app] needs is refused for good, so its chat opened instead of
     * the call. Sent once the user is back from the chat, so it is not missed.
     */
    data class CallOpenedChat(val app: String) : UiEvent

    /**
     * A restore from a backup finished. [skippedChanged] numbers were changed since the backup
     * and kept; [notFound] could not be found on this device. [backupFailed]: backups are on, but
     * the backup of the replaced numbers could not be written (the restore ran anyway).
     */
    data class RestoredFromBackup(
        val restored: Int,
        val skippedChanged: Int,
        val notFound: Int,
        val failed: Int,
        val backupFailed: Boolean = false,
    ) : UiEvent

    /**
     * The picked backup has nothing to write: [alreadyRestored] numbers already match it,
     * [changedSince] were changed since and [notFound] could not be found on this device.
     */
    data class NothingToRestore(val alreadyRestored: Int, val changedSince: Int, val notFound: Int) : UiEvent

    /** The numbers to back up could not be read, so the update did not start. */
    data object BackupReadFailed : UiEvent

    /** The picked file could not be restored from. */
    data class BackupUnreadable(val error: BackupReadError) : UiEvent

    /** No app could be opened to pick a backup file. */
    data object NoFilePicker : UiEvent
}

sealed interface UiAction {
    data class ToggleEntry(val dataId: Long) : UiAction

    /**
     * Selects all of [dataIds] (one number stored in several accounts), or clears them when they
     * are all selected.
     */
    data class ToggleEntries(val dataIds: List<Long>) : UiAction

    /**
     * Selects the contact's shown numbers [dataIds], or clears them if all were selected. Numbers
     * hidden by the filter or search are left alone.
     */
    data class ToggleContact(val contactId: Long, val dataIds: List<Long>) : UiAction

    /** Adds every selectable number currently displayed (after filter and search). */
    data object SelectAllVisible : UiAction

    data object ClearSelection : UiAction

    data class SetFilter(val filter: ContactFilter) : UiAction

    data class SetQuery(val query: String) : UiAction

    data object Refresh : UiAction

    data object ApplySelected : UiAction

    data object UndoLast : UiAction

    /** Saves the settings and reloads the contacts with them. */
    data class UpdateConfig(val config: FormatConfig) : UiAction

    /** Result of the system permission dialog. */
    data class PermissionResult(val granted: Boolean, val canAskAgain: Boolean) : UiAction

    /** Permission state checked on resume (the user may have changed it in settings). */
    data class PermissionRechecked(val granted: Boolean) : UiAction

    /** The call button of the number stored as [dataIds] was tapped. */
    data class Call(val dataIds: List<Long>) : UiAction

    /** [option] was picked in the sheet that asks how to call. */
    data class PickCall(val option: CallOption) : UiAction

    /** Answer to [CallRequest.RequestPermission]; [canAskAgain] as for [PermissionResult]. */
    data class CallPermissionResult(val granted: Boolean, val canAskAgain: Boolean) : UiAction

    /** The call was started, or the user closed the choice without calling. */
    data object DismissCall : UiAction

    /** The app picked for the call could not be opened. */
    data object CallFailed : UiAction

    /** Opens TidyDial's page in system settings; handled by the UI, not the ViewModel. */
    data object OpenAppSettings : UiAction

    /**
     * The folder picker returned [uri] (null when cancelled). A chosen folder turns backups on
     * and continues an update held by [UiState.backupDialog].
     */
    data class BackupFolderChosen(val uri: String?) : UiAction

    /** Answers the first-update prompt without backups, and continues the update. */
    data object ContinueWithoutBackup : UiAction

    /**
     * Answers a [BackupDialog.WriteFailed]: runs the update it held once without a backup.
     * Backups stay on for later updates.
     */
    data object UpdateWithoutBackup : UiAction

    /** Closes [UiState.backupDialog]; the update it held does not start. */
    data object DismissBackupDialog : UiAction

    /**
     * Turns backups on (only sent when a folder is set; otherwise the UI opens the folder picker)
     * or off.
     */
    data class SetBackupEnabled(val enabled: Boolean) : UiAction

    /** The surface showing [BackupUi.error] closed. */
    data object DismissBackupError : UiAction

    /** The user picked the backup file [uri] to restore from. */
    data class RestoreFileChosen(val uri: String) : UiAction

    data object ConfirmRestore : UiAction

    /** Closes [UiState.restore], or stops reading the backup while [UiState.preparingRestore]. */
    data object DismissRestore : UiAction

    /** No app could be opened to pick a backup folder; shown as [BackupUi.error]. */
    data object FolderPickerUnavailable : UiAction

    /** No app could be opened to pick a backup file. */
    data object FilePickerUnavailable : UiAction
}
