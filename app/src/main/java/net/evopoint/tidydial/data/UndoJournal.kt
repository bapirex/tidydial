package net.evopoint.tidydial.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One number change: [before] was replaced by [after] in the Data row [dataId]. */
data class JournalEntry(
    val dataId: Long,
    val before: String,
    val after: String,
)

/**
 * Persists the changes of the last update so it can be undone, even after a restart.
 *
 * The file holds phone numbers, so the app keeps it in `noBackupFilesDir`, which Android never
 * backs up or copies to a new device. The backup rules also exclude [FILE_NAME] and its temporary
 * file in case a [directory] inside `filesDir` is used.
 *
 * [save] throws [IOException] when the file cannot be written.
 */
class UndoJournal(
    directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val file = File(directory, FILE_NAME)
    private val tempFile = File(directory, "$FILE_NAME.tmp")
    private val mutex = Mutex()

    /** Replaces the stored journal. An empty list clears it. Throws [IOException] on failure. */
    suspend fun save(entries: List<JournalEntry>) {
        if (entries.isEmpty()) {
            clear()
            return
        }
        val json = JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_ENTRIES, JSONArray().apply { entries.forEach { put(it.toJson()) } })
            .toString()
        mutex.withLock {
            withContext(ioDispatcher) { writeAtomically(json.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** The stored journal, or an empty list when there is none or it cannot be read. */
    suspend fun load(): List<JournalEntry> = mutex.withLock {
        withContext(ioDispatcher) {
            if (!file.exists()) return@withContext emptyList<JournalEntry>()
            try {
                parse(file.readText(Charsets.UTF_8))
            } catch (e: IOException) {
                emptyList()
            } catch (e: JSONException) {
                emptyList()
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            withContext(ioDispatcher) {
                tempFile.delete()
                file.delete()
            }
        }
    }

    private fun writeAtomically(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(tempFile).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        try {
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parse(text: String): List<JournalEntry> {
        val array = JSONObject(text).optJSONArray(KEY_ENTRIES) ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            JournalEntry(
                dataId = item.getLong(KEY_ID),
                before = item.getString(KEY_BEFORE),
                after = item.getString(KEY_AFTER),
            )
        }
    }

    private fun JournalEntry.toJson(): JSONObject = JSONObject()
        .put(KEY_ID, dataId)
        .put(KEY_BEFORE, before)
        .put(KEY_AFTER, after)

    companion object {
        const val FILE_NAME = "undo_journal.json"

        private const val VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_ID = "id"
        private const val KEY_BEFORE = "before"
        private const val KEY_AFTER = "after"
    }
}
