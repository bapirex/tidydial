package org.tidydial.ui.preview

import org.tidydial.model.AccountInfo
import org.tidydial.model.ContactGroup
import org.tidydial.model.Country
import org.tidydial.model.FormatConfig
import org.tidydial.model.PhoneEntry
import org.tidydial.model.groupByContact
import org.tidydial.ui.ContactFilter
import org.tidydial.ui.EntryCounts
import org.tidydial.ui.MainViewModel
import org.tidydial.ui.PermissionStatus
import org.tidydial.ui.UiState
import org.tidydial.ui.UpdateProgress
import org.tidydial.ui.isSelectable

/**
 * Obviously fake contacts for previews and screenshot tests: names are placeholders and numbers
 * use the ranges reserved for fiction (US 555-01xx, UK 020 7946 0xxx). Formatted values match
 * what [org.tidydial.data.PhoneNumberFormatter] produces for US + INTERNATIONAL.
 */
object SampleData {
    const val GOOGLE = "com.google"
    const val DEVICE = ""
    const val WHATSAPP = "com.whatsapp"
    const val MEET = "com.google.android.apps.tachyon"

    val config = FormatConfig(countryIso = "US")

    val accounts: List<AccountInfo> = listOf(
        AccountInfo(DEVICE, "Device", 3),
        AccountInfo(GOOGLE, "Google", 7),
        AccountInfo(WHATSAPP, "WhatsApp", 4),
    )

    val countries: List<Country> = listOf(
        Country("AU", "Australia", 61),
        Country("CA", "Canada", 1),
        Country("FR", "France", 33),
        Country("DE", "Germany", 49),
        Country("IN", "India", 91),
        Country("JP", "Japan", 81),
        Country("GB", "United Kingdom", 44),
        Country("US", "United States", 1),
    )

    val entries: List<PhoneEntry> = listOf(
        // Several numbers on one contact, all needing changes.
        entry(101, 1, "Avery Sample", "2125550101", "+1 212-555-0101", "Mobile", GOOGLE),
        entry(102, 1, "Avery Sample", "(212) 555 0102", "+1 212-555-0102", "Work", GOOGLE),
        entry(103, 1, "Avery Sample", "212.555.0103", "+1 212-555-0103", "Home", DEVICE),
        // A UK number under a US config keeps its country code.
        entry(201, 2, "Blake Placeholder", "+442079460958", "+44 20 7946 0958", "Mobile", GOOGLE),
        // Already formatted: valid, nothing to do.
        entry(301, 3, "Casey Demo", "+1 415-555-0132", "+1 415-555-0132", "Mobile", GOOGLE),
        // Invalid: flagged, never selectable.
        entry(401, 4, "Dana Testperson", "555-01", "555-01", "Other", DEVICE, valid = false),
        // Extension with a pause, plus a second, already formatted number.
        entry(501, 5, "Example Plumbing Co.", "3105550142,12", "+1 310-555-0142;12", "Work", GOOGLE),
        entry(502, 5, "Example Plumbing Co.", "+1 310-555-0143", "+1 310-555-0143", "Main", GOOGLE),
        entry(601, 6, "Frankie Fictional", "6175550199", "+1 617-555-0199", "Mobile", DEVICE),
        entry(701, 7, "Gale Mockname", "02079460123", "02079460123", "Home", GOOGLE, valid = false),
    )

    val groups: List<ContactGroup> = entries.groupByContact()

    /**
     * A mostly tidy address book: most numbers are already formatted, one contact has the same
     * number synced from Google and Google Meet, and two numbers still need a change.
     */
    val mostlyTidyEntries: List<PhoneEntry> = listOf(
        entry(1101, 11, "Avery Sample", "+1 212-555-0111", "+1 212-555-0111", "Mobile", GOOGLE),
        entry(1102, 11, "Avery Sample", "+1 212-555-0112", "+1 212-555-0112", "Work", GOOGLE),
        entry(1201, 12, "Blake Placeholder", "+44 20 7946 0959", "+44 20 7946 0959", "Mobile", GOOGLE),
        entry(1301, 13, "Casey Demo", "4155550133", "+1 415-555-0133", "Mobile", GOOGLE),
        entry(1302, 13, "Casey Demo", "4155550133", "+1 415-555-0133", "Mobile", MEET, accountLabel = "Google Meet"),
        entry(1401, 14, "Dana Testperson", "+1 646-555-0144", "+1 646-555-0144", "Mobile", GOOGLE),
        entry(1402, 14, "Dana Testperson", "+1 646-555-0144", "+1 646-555-0144", "Mobile", WHATSAPP),
        entry(1501, 15, "Example Plumbing Co.", "+1 310-555-0145", "+1 310-555-0145", "Work", GOOGLE),
        entry(1601, 16, "Frankie Fictional", "(617) 555-0166", "+1 617-555-0166", "Home", DEVICE),
        entry(1701, 17, "Gale Mockname", "+1 718-555-0177", "+1 718-555-0177", "Mobile", DEVICE),
        entry(1801, 18, "Harper Nobody", "+1 202-555-0188", "+1 202-555-0188", "Mobile", GOOGLE),
        entry(1901, 19, "Indigo Example", "+33 1 99 00 12 34", "+33 1 99 00 12 34", "Mobile", GOOGLE),
    )

    /** Every number already formatted. */
    val tidyEntries: List<PhoneEntry> = mostlyTidyEntries
        .filter { !it.isModified }

    /** Ids the app pre-selects after a load: every valid number that would change. */
    val defaultSelection: Set<Long> = entries.filter { it.isSelectable }.map { it.dataId }.toSet()

    /** A ready-to-render state; groups and counts are derived like the ViewModel does. */
    fun uiState(
        permission: PermissionStatus = PermissionStatus.Granted,
        filter: ContactFilter = ContactFilter.ALL,
        query: String = "",
        selection: Set<Long> = defaultSelection,
        entries: List<PhoneEntry> = SampleData.entries,
        isLoading: Boolean = false,
        isRefreshing: Boolean = false,
        loadError: Boolean = false,
        progress: UpdateProgress? = null,
        canUndo: Boolean = false,
        config: FormatConfig = SampleData.config,
    ): UiState = UiState(
        permission = permission,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        loadError = loadError,
        groups = if (isLoading) {
            emptyList()
        } else {
            MainViewModel.filterGroups(entries.groupByContact(), selection, filter, query)
        },
        counts = if (isLoading) {
            EntryCounts()
        } else {
            EntryCounts(
                total = entries.size,
                changes = entries.count { it.isModified },
                selected = selection.size,
                invalid = entries.count { !it.isValid },
            )
        },
        selection = selection,
        filter = filter,
        query = query,
        config = config,
        detectedCountryIso = "US",
        accounts = accounts,
        countries = countries,
        progress = progress,
        canUndo = canUndo,
    )

    private fun entry(
        dataId: Long,
        contactId: Long,
        name: String,
        original: String,
        formatted: String,
        type: String,
        accountType: String,
        valid: Boolean = true,
        accountLabel: String = accounts.first { it.type == accountType }.label,
    ) = PhoneEntry(
        dataId = dataId,
        rawContactId = contactId * 10,
        contactId = contactId,
        displayName = name,
        originalNumber = original,
        formattedNumber = formatted,
        typeLabel = type,
        accountType = accountType,
        accountLabel = accountLabel,
        isValid = valid,
        isModified = original != formatted,
    )
}
