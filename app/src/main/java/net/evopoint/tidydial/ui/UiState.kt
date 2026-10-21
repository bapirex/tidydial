package net.evopoint.tidydial.ui

import net.evopoint.tidydial.model.AccountInfo
import net.evopoint.tidydial.model.ContactGroup
import net.evopoint.tidydial.model.Country
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.PhoneEntry

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

enum class ProgressKind { APPLY, UNDO }

/** Progress of a running update or undo; [done] goes from 0 to [total]. */
data class UpdateProgress(
    val done: Int,
    val total: Int,
    val kind: ProgressKind,
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
}
