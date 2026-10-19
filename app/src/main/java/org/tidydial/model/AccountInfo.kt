package org.tidydial.model

/**
 * A contacts account type present on the device.
 *
 * @property type raw `RawContacts.ACCOUNT_TYPE`; empty for numbers stored only on the device.
 * @property label human readable name.
 * @property count phone numbers stored under this account type, counted before exclusion.
 */
data class AccountInfo(
    val type: String,
    val label: String,
    val count: Int,
)
