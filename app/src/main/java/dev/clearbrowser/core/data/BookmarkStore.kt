package dev.clearbrowser.core.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable bookmarks, and the flow the chrome reads them through.
 *
 * Writes use the same off-main-thread, newest-snapshot-wins pattern as
 * [HistoryStore]. A bookmark changes only when the user taps the star, so the
 * cost is small either way; doing it here keeps both library files behaving the
 * same way, and keeps file I/O out of a tap regardless.
 */
class BookmarkStore internal constructor(private val file: File) {

    private val list = BookmarkList()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "clear-bookmarks-writer").apply { isDaemon = true }
    }
    private val saveQueued = AtomicBoolean(false)

    @Volatile
    private var snapshot: List<Bookmark> = emptyList()

    private val _entries = MutableStateFlow<List<Bookmark>>(emptyList())

    /** Newest first. Empty until the stored bookmarks have been read. */
    val entries: StateFlow<List<Bookmark>> = _entries.asStateFlow()

    init {
        list.restore(readFromDisk())
        snapshot = list.entries
        _entries.value = snapshot
    }

    /** Save the page, or remove it when it is already saved. */
    fun toggle(url: String, title: String, atMillis: Long = System.currentTimeMillis()) {
        list.toggle(url, title, atMillis)
        publish()
    }

    /** Whether [url] is saved, under the same identity [toggle] uses. */
    fun isBookmarked(url: String): Boolean = list.isBookmarked(url)

    fun remove(url: String) {
        list.remove(url)
        publish()
    }

    fun clear() {
        list.clear()
        publish()
    }

    fun exportHtml(): String = BookmarkCodec.exportToHtml(snapshot)

    fun importBookmarks(content: String): Int {
        val parsed = BookmarkCodec.importFromHtmlOrJson(content)
        val count = list.import(parsed)
        if (count > 0) {
            publish()
        }
        return count
    }

    private fun publish() {
        snapshot = list.entries
        _entries.value = snapshot
        scheduleWrite()
    }

    private fun scheduleWrite() {
        if (!saveQueued.compareAndSet(false, true)) return
        writer.execute {
            saveQueued.set(false)
            writeToDisk(snapshot)
        }
    }

    private fun readFromDisk(): List<Bookmark> {
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val url = json.optString(KEY_URL)
                if (url.isBlank()) {
                    null
                } else {
                    Bookmark(
                        url = url,
                        title = json.optString(KEY_TITLE),
                        createdAtMillis = json.optLong(KEY_CREATED_AT, 0L),
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Discarding unreadable bookmarks", it)
            runCatching { AtomicJsonFile.delete(file) }
            emptyList()
        }
    }

    private fun writeToDisk(bookmarks: List<Bookmark>) {
        runCatching {
            if (bookmarks.isEmpty()) {
                AtomicJsonFile.delete(file)
                return@runCatching
            }
            val array = JSONArray()
            bookmarks.forEach { bookmark ->
                array.put(
                    JSONObject().apply {
                        put(KEY_URL, bookmark.url)
                        put(KEY_TITLE, bookmark.title)
                        put(KEY_CREATED_AT, bookmark.createdAtMillis)
                    },
                )
            }
            AtomicJsonFile.write(file, array.toString())
        }.onFailure {
            Log.w(TAG, "Could not persist bookmarks", it)
        }
    }

    companion object {
        private const val TAG = "ClearBrowser"
        private const val FILE_NAME = "library/bookmarks.json"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_CREATED_AT = "createdAt"

        fun forContext(context: Context): BookmarkStore =
            BookmarkStore(File(context.filesDir, FILE_NAME))
    }
}
