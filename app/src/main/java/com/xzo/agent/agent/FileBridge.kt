package com.xzo.agent.agent

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges the agent's file tools to the Storage Access Framework.
 *
 * The Activity observes [requests] and launches ACTION_CREATE_DOCUMENT /
 * ACTION_OPEN_DOCUMENT, then calls [deliver] with the picked Uri (or null when
 * the user cancels). Everything is suspending, so the agent loop simply awaits
 * the user's choice.
 */
class FileBridge(private val appContext: Context) {

    sealed interface Request {
        val id: Long

        data class Create(
            override val id: Long,
            val suggestedName: String,
            val mime: String
        ) : Request

        data class Open(
            override val id: Long,
            val mimes: Array<String>
        ) : Request {
            override fun equals(other: Any?) = other is Open && other.id == id
            override fun hashCode() = id.hashCode()
        }

        data class OpenTree(override val id: Long) : Request
    }

    val requests = MutableSharedFlow<Request>(extraBufferCapacity = 8)

    private val pending = AtomicReference<Pair<Long, CompletableDeferred<Uri?>>?>(null)
    private var counter = 0L

    /** Called by the Activity when the SAF picker returns. */
    fun deliver(id: Long, uri: Uri?) {
        val p = pending.get() ?: return
        if (p.first == id) {
            pending.compareAndSet(p, null)
            p.second.complete(uri)
        }
    }

    fun cancelAll() {
        pending.getAndSet(null)?.second?.complete(null)
    }

    private suspend fun await(request: Request, timeoutMs: Long = 5 * 60_000L): Uri? {
        val deferred = CompletableDeferred<Uri?>()
        pending.set(request.id to deferred)
        requests.emit(request)
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    data class SavedFile(val uri: Uri, val name: String, val bytes: Int)

    /** Ask the user where to save, then write [content]. */
    suspend fun createDocument(name: String, mime: String, content: String): SavedFile? {
        val id = ++counter
        val uri = await(Request.Create(id, name, mime)) ?: return null
        return withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return@withContext null
            SavedFile(uri, displayName(uri) ?: name, content.toByteArray(Charsets.UTF_8).size)
        }
    }

    data class LoadedFile(
        val uri: Uri,
        val name: String,
        val mime: String,
        val text: String,
        val imageDataUrl: String? = null
    )

    /** Ask the user to pick a file, then read it as text. */
    suspend fun openDocument(mimes: Array<String> = arrayOf("*/*"), maxChars: Int = 200_000): LoadedFile? {
        val id = ++counter
        val uri = await(Request.Open(id, mimes)) ?: return null
        return readUri(uri, maxChars)
    }

    suspend fun readUri(uri: Uri, maxChars: Int = 200_000): LoadedFile? = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermissionSafely(uri)
            val mime = appContext.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = displayName(uri) ?: uri.lastPathSegment ?: "file"

            // Images are converted to an inline data URL for vision models.
            if (mime.startsWith("image/")) {
                val dataUrl = com.xzo.agent.util.Images.toDataUrl(appContext, uri)
                return@runCatching LoadedFile(
                    uri = uri,
                    name = name,
                    mime = mime,
                    text = if (dataUrl == null) "" else "[image ${com.xzo.agent.util.Images.approxKb(dataUrl)} KB]",
                    imageDataUrl = dataUrl
                )
            }
            val text = appContext.contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).use { br ->
                    val sb = StringBuilder()
                    val buf = CharArray(8192)
                    while (true) {
                        val n = br.read(buf)
                        if (n < 0) break
                        sb.append(buf, 0, n)
                        if (sb.length >= maxChars) { sb.append("\n…[truncated]"); break }
                    }
                    sb.toString()
                }
            }.orEmpty()
            LoadedFile(uri, name, mime, text)
        }.getOrNull()
    }

    /* ------------------------- folder access ------------------------- */

    data class FolderEntry(val name: String, val mime: String, val size: Long, val uri: Uri)

    @Volatile
    private var grantedTree: Uri? = null

    /** Ask for a folder (ACTION_OPEN_DOCUMENT_TREE) and list its files. */
    suspend fun openFolder(limit: Int = 60): List<FolderEntry>? {
        val id = ++counter
        val uri = await(Request.OpenTree(id)) ?: return null
        grantedTree = uri
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return listFolder(limit)
    }

    suspend fun listFolder(limit: Int = 60): List<FolderEntry> = withContext(Dispatchers.IO) {
        val tree = grantedTree ?: return@withContext emptyList()
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(appContext, tree)
            ?: return@withContext emptyList()
        doc.listFiles()
            .filter { it.isFile }
            .take(limit)
            .map {
                FolderEntry(
                    name = it.name ?: "unnamed",
                    mime = it.type ?: "application/octet-stream",
                    size = it.length(),
                    uri = it.uri
                )
            }
    }

    suspend fun readFromFolder(name: String, maxChars: Int): LoadedFile? {
        val entry = listFolder(400).firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        return readUri(entry.uri, maxChars)
    }

    fun hasFolder(): Boolean = grantedTree != null

    fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}

private fun android.content.ContentResolver.takePersistableUriPermissionSafely(uri: Uri) {
    runCatching {
        takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
