package net.evopoint.tidydial.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

enum class BackupKind(val json: String) {
    /** The changes of one update, as `before -> after`. */
    CHANGES("changes"),

    /** Every phone number as it was when the snapshot was taken, before that backup's update. */
    SNAPSHOT("snapshot"),
}

/**
 * Who a backed-up number belongs to: its row ids on the device that wrote the backup, and what
 * finds the row again where those ids changed (a reinstall keeps them, a re-sync or a new phone
 * does not). See [net.evopoint.tidydial.model.PhoneRow].
 */
data class RowIdentity(
    val dataId: Long,
    val rawContactId: Long,
    val lookupKey: String? = null,
    val accountType: String = "",
    val accountName: String? = null,
    val sourceId: String? = null,
    val displayName: String? = null,
)

data class ChangeEntry(val row: RowIdentity, val before: String, val after: String)

data class SnapshotEntry(val row: RowIdentity, val number: String)

/**
 * A backup file. [countryIso] is the country TidyDial read numbers without a country code in
 * when the file was written; a snapshot is compared in it, since national numbers depend on it.
 */
sealed interface BackupFile {
    val createdAt: OffsetDateTime
    val appVersion: String
    val countryIso: String?
    val kind: BackupKind

    /** Number of entries. */
    val size: Int

    data class Changes(
        override val createdAt: OffsetDateTime,
        override val appVersion: String,
        override val countryIso: String?,
        val entries: List<ChangeEntry>,
    ) : BackupFile {
        override val kind: BackupKind get() = BackupKind.CHANGES
        override val size: Int get() = entries.size
    }

    data class Snapshot(
        override val createdAt: OffsetDateTime,
        override val appVersion: String,
        override val countryIso: String?,
        val entries: List<SnapshotEntry>,
    ) : BackupFile {
        override val kind: BackupKind get() = BackupKind.SNAPSHOT
        override val size: Int get() = entries.size
    }
}

enum class BackupReadError {
    /** The file could not be opened or read, or it is too large. */
    UNREADABLE,

    /** Not JSON, not written by TidyDial, or damaged. */
    NOT_A_BACKUP,

    /** Written by a newer TidyDial in a format this version does not know. */
    NEWER_FORMAT,
}

class BackupFormatException(val error: BackupReadError, message: String) : Exception(message)

/**
 * Versioned JSON for backup files. Readable on purpose: testers can open a backup and see what
 * changed. Decoding is strict: a file with any invalid entry is rejected as a whole, so a restore
 * never applies part of a damaged file. Unknown fields are ignored for forward compatibility.
 * Nullable fields are written as JSON `null`.
 */
object BackupCodec {
    const val APP = "TidyDial"
    const val FORMAT = 1

    private const val KEY_APP = "app"
    private const val KEY_FORMAT = "format"
    private const val KEY_KIND = "kind"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_APP_VERSION = "appVersion"
    private const val KEY_COUNTRY = "countryIso"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_DATA_ID = "dataId"
    private const val KEY_RAW_CONTACT_ID = "rawContactId"
    private const val KEY_LOOKUP_KEY = "lookupKey"
    private const val KEY_ACCOUNT_TYPE = "accountType"
    private const val KEY_ACCOUNT_NAME = "accountName"
    private const val KEY_SOURCE_ID = "sourceId"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_BEFORE = "before"
    private const val KEY_AFTER = "after"
    private const val KEY_NUMBER = "number"
    private const val INDENT = 2
    private const val BYTE_ORDER_MARK = "\uFEFF"

    /**
     * A backup nests three levels (file, entries, entry); room is left for fields a later
     * version may add. org.json parses recursively, so deeper input could overflow the stack.
     */
    private const val MAX_NESTING = 16

    fun encode(file: BackupFile): String {
        val entries = JSONArray()
        when (file) {
            is BackupFile.Changes -> file.entries.forEach { entry ->
                entries.put(entry.row.toJson().put(KEY_BEFORE, entry.before).put(KEY_AFTER, entry.after))
            }

            is BackupFile.Snapshot -> file.entries.forEach { entry ->
                entries.put(entry.row.toJson().put(KEY_NUMBER, entry.number))
            }
        }
        return JSONObject()
            .put(KEY_APP, APP)
            .put(KEY_FORMAT, FORMAT)
            .put(KEY_KIND, file.kind.json)
            .put(KEY_CREATED_AT, file.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put(KEY_APP_VERSION, file.appVersion)
            .put(KEY_COUNTRY, file.countryIso ?: JSONObject.NULL)
            .put(KEY_ENTRIES, entries)
            .toString(INDENT)
    }

    /** Parses and validates [text]; throws [BackupFormatException] for anything but a valid backup. */
    fun decode(text: String): BackupFile {
        val json = text.removePrefix(BYTE_ORDER_MARK)
        if (nestsDeeperThan(json, MAX_NESTING)) throw notABackup("Nested too deeply")
        val value = try {
            JSONTokener(json).nextValue()
        } catch (e: JSONException) {
            throw notABackup("Not JSON")
        } catch (e: StackOverflowError) {
            // Only reachable through syntax the scan above does not model; never crash on a file.
            throw notABackup("Nested too deeply")
        }
        val root = value as? JSONObject ?: throw notABackup("Not a JSON object")
        if (root.opt(KEY_APP) != APP) throw notABackup("Not a TidyDial file")
        val format = root.optInteger(KEY_FORMAT) ?: throw notABackup("No format")
        if (format > FORMAT) {
            throw BackupFormatException(BackupReadError.NEWER_FORMAT, "Format $format is newer than $FORMAT")
        }
        if (format < 1) throw notABackup("Bad format $format")
        val kind = BackupKind.entries.firstOrNull { it.json == root.opt(KEY_KIND) }
            ?: throw notABackup("Unknown kind")
        val createdAt = try {
            OffsetDateTime.parse(root.opt(KEY_CREATED_AT) as? String ?: "", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (e: DateTimeParseException) {
            throw notABackup("Bad createdAt")
        }
        val appVersion = root.opt(KEY_APP_VERSION) as? String ?: ""
        val countryIso = root.optStringOrNull(KEY_COUNTRY)?.uppercase(Locale.ROOT)
        val array = root.opt(KEY_ENTRIES) as? JSONArray ?: throw notABackup("No entries")
        val items = List(array.length()) { index ->
            array.opt(index) as? JSONObject ?: throw notABackup("Entry $index is not an object")
        }
        return when (kind) {
            BackupKind.CHANGES -> BackupFile.Changes(
                createdAt = createdAt,
                appVersion = appVersion,
                countryIso = countryIso,
                entries = items.map { item ->
                    if (item.has(KEY_NUMBER)) throw notABackup("Snapshot entry in a changes backup")
                    val before = item.requireNumber(KEY_BEFORE)
                    val after = item.requireNumber(KEY_AFTER)
                    if (before == after) throw notABackup("Entry without a change")
                    ChangeEntry(item.toIdentity(), before, after)
                }.distinctBy { it.row.dataId },
            )

            BackupKind.SNAPSHOT -> BackupFile.Snapshot(
                createdAt = createdAt,
                appVersion = appVersion,
                countryIso = countryIso,
                entries = items.map { item ->
                    if (item.has(KEY_BEFORE) || item.has(KEY_AFTER)) {
                        throw notABackup("Changes entry in a snapshot")
                    }
                    SnapshotEntry(item.toIdentity(), item.requireNumber(KEY_NUMBER))
                }.distinctBy { it.row.dataId },
            )
        }
    }

    /**
     * Whether brackets outside of strings nest deeper than [limit]. Both quote characters open a
     * string, as org.json accepts single-quoted strings too.
     */
    private fun nestsDeeperThan(text: String, limit: Int): Boolean {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (c in text) {
            when {
                quote != null -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == quote -> quote = null
                }

                c == '"' || c == '\'' -> quote = c
                c == '{' || c == '[' -> if (++depth > limit) return true
                c == '}' || c == ']' -> depth--
            }
        }
        return false
    }

    private fun RowIdentity.toJson(): JSONObject = JSONObject()
        .put(KEY_DATA_ID, dataId)
        .put(KEY_RAW_CONTACT_ID, rawContactId)
        .put(KEY_LOOKUP_KEY, lookupKey ?: JSONObject.NULL)
        .put(KEY_ACCOUNT_TYPE, accountType)
        .put(KEY_ACCOUNT_NAME, accountName ?: JSONObject.NULL)
        .put(KEY_SOURCE_ID, sourceId ?: JSONObject.NULL)
        .put(KEY_DISPLAY_NAME, displayName ?: JSONObject.NULL)

    private fun JSONObject.toIdentity(): RowIdentity = RowIdentity(
        dataId = optInteger(KEY_DATA_ID) ?: throw notABackup("Entry without dataId"),
        rawContactId = optInteger(KEY_RAW_CONTACT_ID) ?: throw notABackup("Entry without rawContactId"),
        lookupKey = optStringOrNull(KEY_LOOKUP_KEY)?.takeIf { it.isNotBlank() },
        accountType = optStringOrNull(KEY_ACCOUNT_TYPE).orEmpty(),
        accountName = optStringOrNull(KEY_ACCOUNT_NAME),
        sourceId = optStringOrNull(KEY_SOURCE_ID)?.takeIf { it.isNotBlank() },
        displayName = optStringOrNull(KEY_DISPLAY_NAME),
    )

    /** A whole number (JSON numbers without a fraction), or null. */
    private fun JSONObject.optInteger(key: String): Long? = when (val value = opt(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }

    private fun JSONObject.optStringOrNull(key: String): String? = opt(key) as? String

    private fun JSONObject.requireNumber(key: String): String =
        (opt(key) as? String)?.takeIf { it.isNotBlank() } ?: throw notABackup("Entry without $key")

    private fun notABackup(message: String) = BackupFormatException(BackupReadError.NOT_A_BACKUP, message)
}

/**
 * File names for backups, in local time with seconds. Not localized, so they sort by date and
 * read the same in every language.
 */
object BackupNames {
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss", Locale.ROOT)
    private const val EXTENSION = ".json"

    /** "TidyDial backup 2026-10-31 090412.json" */
    fun changes(time: LocalDateTime): String = "TidyDial backup ${STAMP.format(time)}$EXTENSION"

    /** "TidyDial snapshot 2026-10-31 090412.json" */
    fun snapshot(time: LocalDateTime): String = "TidyDial snapshot ${STAMP.format(time)}$EXTENSION"

    /** "TidyDial backup 2026-10-31 090412 (2).json" for [n] = 2. */
    fun withSuffix(name: String, n: Int): String = "${name.removeSuffix(EXTENSION)} ($n)$EXTENSION"
}
