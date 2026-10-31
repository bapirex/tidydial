package net.evopoint.tidydial.model

/**
 * A phone Data row with what a backup needs to find it again after a reinstall or a re-sync,
 * when its [dataId] may have changed.
 *
 * @property lookupKey `Contacts.LOOKUP_KEY` of the aggregate contact.
 * @property accountType raw `RawContacts.ACCOUNT_TYPE`; empty for device-only contacts, as in [PhoneEntry].
 * @property sourceId `RawContacts.SOURCE_ID`, the sync adapter's id of the raw contact on its
 *   server; null for device-only contacts.
 */
data class PhoneRow(
    val dataId: Long,
    val rawContactId: Long,
    val lookupKey: String?,
    val accountType: String,
    val accountName: String?,
    val sourceId: String?,
    val displayName: String?,
    val number: String,
)
