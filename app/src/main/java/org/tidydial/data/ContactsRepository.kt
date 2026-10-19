package org.tidydial.data

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tidydial.R
import org.tidydial.model.AccountInfo
import org.tidydial.model.FormatConfig
import org.tidydial.model.PhoneEntry

sealed interface LoadResult {
    /**
     * @property entries numbers outside excluded accounts, sorted by contact name.
     * @property accounts every account type on the device with its count before exclusion.
     */
    data class Success(
        val entries: List<PhoneEntry>,
        val accounts: List<AccountInfo>,
    ) : LoadResult

    data object PermissionDenied : LoadResult

    data class Error(val cause: Throwable) : LoadResult
}

/**
 * @property updated rows written.
 * @property skippedChanged rows left alone because their number changed (or the row was deleted)
 *   since it was loaded.
 * @property failed rows the provider refused to write.
 * @property journal the changes that were written, as `before -> after`.
 * @property permissionRevoked the contacts permission was lost during the run.
 * @property skippedIds `dataId`s counted in [skippedChanged].
 * @property failedIds `dataId`s whose write threw for a reason other than losing the permission. A
 *   failed batch can commit some rows before it throws, so these may already hold the new value.
 *   Rows refused or never attempted because the permission was lost are in none of [journal],
 *   [skippedIds] and [failedIds].
 */
data class UpdateResult(
    val updated: Int,
    val skippedChanged: Int,
    val failed: Int,
    val journal: List<JournalEntry>,
    val permissionRevoked: Boolean = false,
    val skippedIds: Set<Long> = emptySet(),
    val failedIds: Set<Long> = emptySet(),
)

typealias ProgressCallback = suspend (done: Int, total: Int) -> Unit

interface ContactsDataSource {
    suspend fun load(config: FormatConfig): LoadResult

    /** Writes `formattedNumber` for every modified entry, unless its number changed since loading. */
    suspend fun apply(
        entries: List<PhoneEntry>,
        onProgress: ProgressCallback = { _, _ -> },
    ): UpdateResult

    /** Reverts a journal (`after -> before`), unless a number changed since it was written. */
    suspend fun restore(
        journal: List<JournalEntry>,
        onProgress: ProgressCallback = { _, _ -> },
    ): UpdateResult
}

class AndroidContactsRepository(
    context: Context,
    private val formatter: PhoneNumberFormatter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ContactsDataSource {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    override suspend fun load(config: FormatConfig): LoadResult {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return LoadResult.PermissionDenied
        val snapshot = try {
            withContext(ioDispatcher) { readSnapshot() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return LoadResult.PermissionDenied
        } catch (e: Exception) {
            Log.e(TAG, "Could not read contacts", e)
            return LoadResult.Error(e)
        }
        if (snapshot == null) {
            return LoadResult.Error(IllegalStateException("Contacts provider unavailable"))
        }
        return withContext(defaultDispatcher) { buildResult(snapshot, config) }
    }

    override suspend fun apply(
        entries: List<PhoneEntry>,
        onProgress: ProgressCallback,
    ): UpdateResult {
        val changes = entries
            .filter { it.formattedNumber != it.originalNumber }
            .distinctBy { it.dataId }
            .map { JournalEntry(it.dataId, before = it.originalNumber, after = it.formattedNumber) }
        return execute(changes, onProgress)
    }

    override suspend fun restore(
        journal: List<JournalEntry>,
        onProgress: ProgressCallback,
    ): UpdateResult {
        val changes = journal.map { JournalEntry(it.dataId, before = it.after, after = it.before) }
        return execute(changes, onProgress)
    }

    private fun hasPermission(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // region Reading

    private class Row(
        val dataId: Long,
        val rawContactId: Long,
        val contactId: Long,
        val displayName: String?,
        val number: String,
        val type: Int,
        val label: String?,
        var accountType: String,
    )

    private class Snapshot(val rows: List<Row>, val authenticatorLabels: Map<String, String>)

    private suspend fun readSnapshot(): Snapshot? {
        val scopeContext = currentCoroutineContext()
        val cursor = queryPhones() ?: return null
        val rows = ArrayList<Row>(cursor.count.coerceAtLeast(0))
        var hasAccountColumn = true
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Phone._ID)
            val rawIdx = c.getColumnIndex(Phone.RAW_CONTACT_ID)
            val contactIdx = c.getColumnIndex(Phone.CONTACT_ID)
            val nameIdx = c.getColumnIndex(Phone.DISPLAY_NAME_PRIMARY)
            val numberIdx = c.getColumnIndexOrThrow(Phone.NUMBER)
            val typeIdx = c.getColumnIndex(Phone.TYPE)
            val labelIdx = c.getColumnIndex(Phone.LABEL)
            val accountIdx = c.getColumnIndex(RawContacts.ACCOUNT_TYPE)
            hasAccountColumn = accountIdx >= 0
            while (c.moveToNext()) {
                if (c.position % CANCEL_CHECK_INTERVAL == 0) scopeContext.ensureActive()
                val number = c.stringAt(numberIdx)
                if (number.isNullOrBlank()) continue
                rows += Row(
                    dataId = c.getLong(idIdx),
                    rawContactId = c.longAt(rawIdx),
                    contactId = c.longAt(contactIdx),
                    displayName = c.stringAt(nameIdx),
                    number = number,
                    type = if (typeIdx >= 0 && !c.isNull(typeIdx)) c.getInt(typeIdx) else Phone.TYPE_OTHER,
                    label = c.stringAt(labelIdx),
                    accountType = c.stringAt(accountIdx).orEmpty(),
                )
            }
        }
        if (!hasAccountColumn) {
            val accounts = queryRawContactAccountTypes()
            rows.forEach { it.accountType = accounts[it.rawContactId].orEmpty() }
        }
        return Snapshot(rows, authenticatorLabels())
    }

    private fun queryPhones(): Cursor? = try {
        resolver.query(Phone.CONTENT_URI, PROJECTION_WITH_ACCOUNT, null, null, SORT_ORDER)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Account type not available on the phone view, using raw contacts", e)
        resolver.query(Phone.CONTENT_URI, PROJECTION, null, null, SORT_ORDER)
    }

    private fun queryRawContactAccountTypes(): Map<Long, String> {
        val map = HashMap<Long, String>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE),
            null,
            null,
            null,
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(RawContacts._ID)
            val accountIdx = c.getColumnIndex(RawContacts.ACCOUNT_TYPE)
            while (c.moveToNext()) {
                map[c.getLong(idIdx)] = c.stringAt(accountIdx).orEmpty()
            }
        }
        return map
    }

    /** Labels the account apps themselves provide; may be empty if the packages are not visible. */
    private fun authenticatorLabels(): Map<String, String> {
        val authenticators = try {
            AccountManager.get(appContext).authenticatorTypes
        } catch (e: Exception) {
            return emptyMap()
        }
        val packageManager = appContext.packageManager
        val labels = HashMap<String, String>()
        for (description in authenticators) {
            if (description.labelId == 0) continue
            val label = try {
                packageManager.getText(description.packageName, description.labelId, null)
            } catch (e: Exception) {
                null
            }
            if (!label.isNullOrBlank()) labels[description.type] = label.toString()
        }
        return labels
    }

    private suspend fun buildResult(snapshot: Snapshot, config: FormatConfig): LoadResult.Success {
        val scopeContext = currentCoroutineContext()
        val resources = appContext.resources
        val unknownName = appContext.getString(R.string.unknown_contact_name)
        val labels = HashMap<String, String>()
        val labelFor = { type: String ->
            labels.getOrPut(type) { accountLabel(type, snapshot.authenticatorLabels) }
        }
        val counts = LinkedHashMap<String, Int>()
        val entries = ArrayList<PhoneEntry>(snapshot.rows.size)

        snapshot.rows.forEachIndexed { index, row ->
            if (index % CANCEL_CHECK_INTERVAL == 0) scopeContext.ensureActive()
            counts[row.accountType] = (counts[row.accountType] ?: 0) + 1
            if (row.accountType in config.excludedAccountTypes) return@forEachIndexed
            val result = formatter.format(row.number, config)
            entries += PhoneEntry(
                dataId = row.dataId,
                rawContactId = row.rawContactId,
                contactId = row.contactId,
                displayName = row.displayName?.takeIf { it.isNotBlank() } ?: unknownName,
                originalNumber = row.number,
                formattedNumber = result.formattedNumber,
                typeLabel = Phone.getTypeLabel(resources, row.type, row.label).toString(),
                accountType = row.accountType,
                accountLabel = labelFor(row.accountType),
                isValid = result.isValid,
                isModified = result.isModified,
            )
        }

        val collator = Collator.getInstance()
        val accounts = counts
            .map { (type, count) -> AccountInfo(type, labelFor(type), count) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        return LoadResult.Success(entries, accounts)
    }

    private fun accountLabel(type: String, authenticatorLabels: Map<String, String>): String =
        when {
            type.isEmpty() || type in DEVICE_ACCOUNT_TYPES ->
                appContext.getString(R.string.account_device)

            type in SIM_ACCOUNT_TYPES -> appContext.getString(R.string.account_sim)
            else -> authenticatorLabels[type]
                ?: KNOWN_ACCOUNT_LABELS[type]
                ?: labelFromAccountType(type)
        }

    // endregion

    // region Writing

    private enum class Outcome { UPDATED, SKIPPED, FAILED }

    /** [revokedAt]: index of the row that hit a SecurityException; later rows were not attempted. */
    private class BatchOutcome(val outcomes: List<Outcome>, val revokedAt: Int = -1) {
        val permissionRevoked: Boolean get() = revokedAt >= 0
    }

    /**
     * Applies `before -> after` changes. Runs to completion even if the caller is cancelled, so the
     * returned journal always describes what was written.
     */
    private suspend fun execute(
        changes: List<JournalEntry>,
        onProgress: ProgressCallback,
    ): UpdateResult = withContext(NonCancellable + ioDispatcher) {
        val total = changes.size
        if (total == 0) return@withContext UpdateResult(0, 0, 0, emptyList())
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            return@withContext UpdateResult(0, 0, total, emptyList(), permissionRevoked = true)
        }

        var updated = 0
        var skipped = 0
        var failed = 0
        var done = 0
        val journal = ArrayList<JournalEntry>(total)
        val skippedIds = HashSet<Long>()
        val failedIds = HashSet<Long>()
        onProgress(0, total)

        for (batch in changes.chunked(BATCH_SIZE)) {
            val result = applyBatch(batch)
            batch.forEachIndexed { index, change ->
                when (result.outcomes[index]) {
                    Outcome.UPDATED -> {
                        updated++
                        journal += change
                    }

                    Outcome.SKIPPED -> {
                        skipped++
                        skippedIds += change.dataId
                    }

                    Outcome.FAILED -> {
                        failed++
                        val attempted = !result.permissionRevoked || index < result.revokedAt
                        if (attempted) failedIds += change.dataId
                    }
                }
            }
            done += batch.size
            if (result.permissionRevoked) {
                failed += total - done
                return@withContext UpdateResult(
                    updated, skipped, failed, journal,
                    permissionRevoked = true,
                    skippedIds = skippedIds,
                    failedIds = failedIds,
                )
            }
            onProgress(done, total)
        }
        UpdateResult(updated, skipped, failed, journal, skippedIds = skippedIds, failedIds = failedIds)
    }

    private fun applyBatch(batch: List<JournalEntry>): BatchOutcome {
        val operations = batch.mapTo(ArrayList<ContentProviderOperation>(batch.size)) { it.toOperation() }
        try {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, operations)
            val outcomes = batch.indices.map { index ->
                if ((results.getOrNull(index)?.count ?: 0) > 0) Outcome.UPDATED else Outcome.SKIPPED
            }
            return BatchOutcome(outcomes)
        } catch (e: Exception) {
            Log.w(TAG, "Batch update failed, retrying one by one", e)
        }

        val outcomes = ArrayList<Outcome>(batch.size)
        var revokedAt = -1
        for ((index, change) in batch.withIndex()) {
            if (revokedAt >= 0) {
                outcomes += Outcome.FAILED
                continue
            }
            outcomes += try {
                applySingle(change)
            } catch (e: SecurityException) {
                revokedAt = index
                Outcome.FAILED
            } catch (e: Exception) {
                Log.w(TAG, "Update of data row ${change.dataId} failed", e)
                Outcome.FAILED
            }
        }
        return BatchOutcome(outcomes, revokedAt)
    }

    private fun applySingle(change: JournalEntry): Outcome {
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(change.toOperation()))
        if ((results.firstOrNull()?.count ?: 0) > 0) return Outcome.UPDATED
        // A failed batch can commit rows before a yield point; those already hold the new value.
        return if (currentNumber(change.dataId) == change.after) Outcome.UPDATED else Outcome.SKIPPED
    }

    private fun currentNumber(dataId: Long): String? =
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Phone.NUMBER),
            "${Data._ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(dataId.toString(), Phone.CONTENT_ITEM_TYPE),
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Optimistic update: only matches while the row still holds the number we expect. */
    private fun JournalEntry.toOperation(): ContentProviderOperation =
        ContentProviderOperation.newUpdate(Data.CONTENT_URI)
            .withSelection(
                OPTIMISTIC_SELECTION,
                arrayOf(dataId.toString(), Phone.CONTENT_ITEM_TYPE, before),
            )
            .withValue(Phone.NUMBER, after)
            .withYieldAllowed(true)
            .build()

    // endregion

    private companion object {
        const val TAG = "ContactsRepository"
        const val BATCH_SIZE = 50
        const val CANCEL_CHECK_INTERVAL = 256

        val PROJECTION = arrayOf(
            Phone._ID,
            Phone.RAW_CONTACT_ID,
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
        )
        val PROJECTION_WITH_ACCOUNT = PROJECTION + RawContacts.ACCOUNT_TYPE

        val SORT_ORDER =
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${Phone.CONTACT_ID} ASC, ${Phone._ID} ASC"

        val OPTIMISTIC_SELECTION =
            "${Data._ID} = ? AND ${Data.MIMETYPE} = ? AND ${Phone.NUMBER} = ?"

        /** Local "phone" accounts used by various OEMs instead of a null account. */
        val DEVICE_ACCOUNT_TYPES = setOf(
            "vnd.sec.contact.phone",
            "com.android.localphone",
            "com.android.huawei.phone",
            "com.oppo.contacts.device",
            "com.sonyericsson.localcontacts",
            "com.htc.android.pcsc",
            "com.motorola.contacts.preloaded",
        )

        val SIM_ACCOUNT_TYPES = setOf(
            "vnd.sec.contact.sim",
            "vnd.sec.contact.sim2",
            "com.android.sim",
            "com.android.contacts.sim",
        )

        /** Brand names (not translated) for accounts whose package label may not be visible. */
        val KNOWN_ACCOUNT_LABELS = mapOf(
            "com.google" to "Google",
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "org.thoughtcrime.securesms" to "Signal",
            "com.viber.voip" to "Viber",
            "ch.threema.app" to "Threema",
            "com.skype.contacts.sync" to "Skype",
            "com.skype.raider" to "Skype",
            "com.facebook.auth.login" to "Facebook",
            "com.facebook.messenger" to "Messenger",
            "com.google.android.apps.tachyon" to "Google Meet",
            "com.microsoft.office.outlook.USER_ACCOUNT" to "Outlook",
            "com.android.exchange" to "Exchange",
            "com.google.android.gm.exchange" to "Exchange",
            "eu.faircode.email" to "FairEmail",
            "at.bitfire.davdroid" to "DAVx⁵",
            "at.bitfire.davdroid.address_book" to "DAVx⁵",
        )

        private val GENERIC_SEGMENTS = setOf(
            "com", "org", "net", "io", "de", "ch", "eu", "at", "android", "apps", "app",
            "contacts", "contact", "sync", "account", "accounts", "type",
        )

        fun labelFromAccountType(type: String): String {
            val segment = type.split('.')
                .firstOrNull { it.isNotBlank() && it.lowercase(Locale.ROOT) !in GENERIC_SEGMENTS }
                ?: type.substringAfterLast('.').ifBlank { type }
            return segment.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }
}

private fun Cursor.stringAt(index: Int): String? =
    if (index < 0 || isNull(index)) null else getString(index)

private fun Cursor.longAt(index: Int, default: Long = -1L): Long =
    if (index < 0 || isNull(index)) default else getLong(index)
