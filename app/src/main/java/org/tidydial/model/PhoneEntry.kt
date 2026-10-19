package org.tidydial.model

/**
 * One phone number row (`ContactsContract.Data` row with the Phone mimetype).
 *
 * @property accountType raw `RawContacts.ACCOUNT_TYPE`; empty for device-only contacts.
 * @property accountLabel human readable account name.
 * @property isModified true when [formattedNumber] differs from [originalNumber].
 */
data class PhoneEntry(
    val dataId: Long,
    val rawContactId: Long,
    val contactId: Long,
    val displayName: String,
    val originalNumber: String,
    val formattedNumber: String,
    val typeLabel: String,
    val accountType: String,
    val accountLabel: String,
    val isValid: Boolean,
    val isModified: Boolean,
)
