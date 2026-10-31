package net.evopoint.tidydial.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.SyncFailedException

/** A folder the user picked for backups; [name] is what to show for it. */
data class BackupFolder(val uri: String, val name: String)

enum class BackupFailure {
    /** Backups are on, but no folder was chosen. */
    NO_FOLDER,

    /** TidyDial no longer holds access to the folder (revoked, or a reinstall). */
    NO_ACCESS,

    /** The folder was moved or deleted. */
    FOLDER_MISSING,

    /** The provider refused the file or failed while writing it. */
    WRITE_FAILED,

    /** The numbers to back up could not be read from the contacts. */
    READ_CONTACTS_FAILED,
}

sealed interface BackupWriteResult {
    data class Written(val documentUri: String, val displayName: String) : BackupWriteResult

    data class Failed(val failure: BackupFailure) : BackupWriteResult
}

/**
 * Where backup files go: a folder the user picked through the Storage Access Framework, which
 * needs no permission and outlives TidyDial. URIs are strings so callers stay free of Android.
 */
interface BackupStore {
    /** Takes a persistable read and write grant on the picked tree; null when it cannot be used. */
    suspend fun adoptFolder(treeUri: String): BackupFolder?

    /** Gives a grant back (Android caps how many an app may keep). */
    suspend fun releaseFolder(treeUri: String)

    /** TidyDial still holds a write grant (lost on reinstall). A deleted folder only shows on write. */
    suspend fun hasAccess(treeUri: String): Boolean

    /**
     * Creates a new document [displayName] in the folder and writes [content]; never replaces a
     * file. A name that is taken gets " (2)", " (3)"…
     */
    suspend fun write(treeUri: String, displayName: String, content: ByteArray): BackupWriteResult

    /**
     * Reads a document the user picked (no folder needed) as UTF-8. Throws [IOException] when it
     * cannot be read or is larger than [maxBytes].
     */
    suspend fun read(documentUri: String, maxBytes: Int): String
}

/** [BackupStore] on `DocumentsContract`, without the DocumentFile library. */
class SafBackupStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupStore {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun adoptFolder(treeUri: String): BackupFolder? = withContext(ioDispatcher) {
        val tree = treeUri.toUri()
        try {
            resolver.takePersistableUriPermission(tree, GRANT_FLAGS)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not keep access to the backup folder", e)
            return@withContext null
        }
        BackupFolder(treeUri, folderName(tree))
    }

    override suspend fun releaseFolder(treeUri: String) {
        withContext(ioDispatcher) {
            try {
                resolver.releasePersistableUriPermission(treeUri.toUri(), GRANT_FLAGS)
            } catch (e: SecurityException) {
                // Already gone, for example after the folder's app was uninstalled.
            }
        }
    }

    override suspend fun hasAccess(treeUri: String): Boolean = withContext(ioDispatcher) {
        holdsWriteGrant(treeUri.toUri())
    }

    override suspend fun write(
        treeUri: String,
        displayName: String,
        content: ByteArray,
    ): BackupWriteResult = withContext(ioDispatcher) {
        val tree = treeUri.toUri()
        if (!holdsWriteGrant(tree)) return@withContext BackupWriteResult.Failed(BackupFailure.NO_ACCESS)
        val document = try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = freeName(displayName, childNames(tree))
            // createDocument never replaces a file; most providers also rename on a clash.
            DocumentsContract.createDocument(resolver, parent, MIME_TYPE, name)
                ?.let { it to name }
                ?: return@withContext BackupWriteResult.Failed(BackupFailure.WRITE_FAILED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "Backup folder is gone", e)
            return@withContext BackupWriteResult.Failed(BackupFailure.FOLDER_MISSING)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Backup folder is gone", e)
            return@withContext BackupWriteResult.Failed(BackupFailure.FOLDER_MISSING)
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to the backup folder", e)
            return@withContext BackupWriteResult.Failed(BackupFailure.NO_ACCESS)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create the backup file", e)
            return@withContext BackupWriteResult.Failed(BackupFailure.WRITE_FAILED)
        }
        val (uri, name) = document
        try {
            val stream = resolver.openOutputStream(uri, "w") ?: throw IOException("No output stream")
            stream.use { out ->
                out.write(content)
                out.flush()
                // Local providers hand out a file descriptor: make sure the bytes reach the disk.
                try {
                    (out as? FileOutputStream)?.fd?.sync()
                } catch (e: SyncFailedException) {
                    Log.w(TAG, "Could not sync the backup file", e)
                }
            }
            BackupWriteResult.Written(uri.toString(), name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the backup file", e)
            // A half-written backup must not look like a good one.
            try {
                DocumentsContract.deleteDocument(resolver, uri)
            } catch (deleteError: Exception) {
                Log.w(TAG, "Could not delete the incomplete backup file", deleteError)
            }
            BackupWriteResult.Failed(BackupFailure.WRITE_FAILED)
        }
    }

    override suspend fun read(documentUri: String, maxBytes: Int): String = withContext(ioDispatcher) {
        val stream = try {
            resolver.openInputStream(documentUri.toUri())
        } catch (e: SecurityException) {
            throw IOException("No access to $documentUri", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("Cannot open $documentUri", e)
        } ?: throw IOException("No input stream for $documentUri")
        val bytes = stream.use { it.readAtMost(maxBytes + 1) }
        if (bytes.size > maxBytes) throw IOException("Larger than $maxBytes bytes")
        bytes.toString(Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK)
    }

    private fun holdsWriteGrant(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    /** Display names in the folder, to avoid creating a duplicate (Google Drive allows them). */
    private fun childNames(tree: Uri): Set<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val names = HashSet<String>()
        resolver.query(children, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let(names::add)
        }
        return names
    }

    private fun freeName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        return (2..MAX_SUFFIX).asSequence()
            .map { BackupNames.withSuffix(name, it) }
            .firstOrNull { it !in taken }
            ?: name
    }

    private fun folderName(tree: Uri): String {
        val documentId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: IllegalArgumentException) {
            return tree.lastPathSegment.orEmpty()
        }
        val name = try {
            resolver.query(
                DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
                arrayOf(Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the backup folder's name", e)
            null
        }
        // Document ids such as "primary:Documents/TidyDial" end with the folder's path.
        return name?.takeIf { it.isNotBlank() }
            ?: documentId.substringAfterLast(':').substringAfterLast('/').ifBlank { documentId }
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "SafBackupStore"
        const val MIME_TYPE = "application/json"
        const val MAX_SUFFIX = 99
        const val BUFFER_SIZE = 8 * 1024
        const val BYTE_ORDER_MARK = "\uFEFF"
        const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
