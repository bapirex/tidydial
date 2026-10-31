package net.evopoint.tidydial.data

import java.io.IOException

/**
 * An in-memory [BackupStore] that behaves like the Storage Access Framework: picked folders must
 * be adopted before writes, names never clash, and access can be lost.
 */
internal class FakeBackupStore(
    /** Shared with fakes of other dependencies, to check what happened in which order. */
    val log: MutableList<String> = mutableListOf(),
) : BackupStore {

    class File(val name: String, val content: String)

    /** Folder URI to its files, in the order they were written. */
    val folders = LinkedHashMap<String, MutableList<File>>()

    /** Folders TidyDial holds a write grant for. */
    val access = mutableSetOf<String>()

    /** Folders [adoptFolder] can take a grant on; null means any. */
    var grantable: Set<String>? = null

    /** Every write fails like this while set. */
    var failWrite: BackupFailure? = null

    /** Fail only the write whose name starts with this. */
    var failWriteOf: String? = null

    /** Documents the user can pick for a restore, by URI. */
    val documents = mutableMapOf<String, String>()

    val released = mutableListOf<String>()

    fun files(folder: String): List<File> = folders[folder].orEmpty()

    override suspend fun adoptFolder(treeUri: String): BackupFolder? {
        if (grantable?.contains(treeUri) == false) return null
        access += treeUri
        folders.getOrPut(treeUri) { mutableListOf() }
        return BackupFolder(treeUri, treeUri.substringAfterLast('/'))
    }

    override suspend fun releaseFolder(treeUri: String) {
        released += treeUri
        access -= treeUri
    }

    override suspend fun hasAccess(treeUri: String): Boolean = treeUri in access

    override suspend fun write(treeUri: String, displayName: String, content: ByteArray): BackupWriteResult {
        if (treeUri !in access) return BackupWriteResult.Failed(BackupFailure.NO_ACCESS)
        failWrite?.let { return BackupWriteResult.Failed(it) }
        if (failWriteOf?.let { displayName.startsWith(it) } == true) {
            return BackupWriteResult.Failed(BackupFailure.WRITE_FAILED)
        }
        val folder = folders.getOrPut(treeUri) { mutableListOf() }
        val taken = folder.mapTo(HashSet()) { it.name }
        val name = if (displayName !in taken) {
            displayName
        } else {
            (2..99).map { BackupNames.withSuffix(displayName, it) }.first { it !in taken }
        }
        folder += File(name, content.toString(Charsets.UTF_8))
        log += "backup:$name"
        val uri = "$treeUri/$name"
        documents[uri] = content.toString(Charsets.UTF_8)
        return BackupWriteResult.Written(uri, name)
    }

    override suspend fun read(documentUri: String, maxBytes: Int): String {
        val text = documents[documentUri] ?: throw IOException("No document $documentUri")
        // Test documents are ASCII: one byte per character.
        if (text.length > maxBytes) throw IOException("Too large")
        return text
    }
}
