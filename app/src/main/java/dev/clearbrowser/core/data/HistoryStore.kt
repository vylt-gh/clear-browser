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
 * Durable browsing history, and the flow the chrome reads it through.
 *
 * ## Why writes happen off the main thread
 *
 * The file is rewritten whenever the list changes, and a page finishing a load
 * is exactly when the user is about to touch the screen. Writing up to a
 * thousand entries there would put file I/O in the path of a scroll. A single
 * background thread owns the writing, and only the newest snapshot is written:
 * several visits during one write collapse into the next pass instead of
 * queueing a write each.
 *
 * The in-memory [HistoryLog] is only touched on the caller's thread, which is
 * the main thread in this app. The writer thread sees an immutable snapshot
 * published through a `@Volatile` field, so it never reads a list being mutated.
 */
class HistoryStore internal constructor(private val file: File) {

    private val log = HistoryLog()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "clear-history-writer").apply { isDaemon = true }
    }
    private val saveQueued = AtomicBoolean(false)

    @Volatile
    private var snapshot: List<HistoryEntry> = emptyList()

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    /** Newest first. Empty until the stored history has been read. */
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    init {
        log.restore(readFromDisk())
        snapshot = log.entries
        _entries.value = snapshot
    }

    fun record(url: String, title: String, atMillis: Long = System.currentTimeMillis()) {
        if (!log.record(url, title, atMillis)) return
        publish()
    }

    fun remove(url: String) {
        log.remove(url)
        publish()
    }

    fun clear() {
        log.clear()
        publish()
    }

    private fun publish() {
        snapshot = log.entries
        _entries.value = snapshot
        scheduleWrite()
    }

    private fun scheduleWrite() {
        if (!saveQueued.compareAndSet(false, true)) return
        writer.execute {
            // Cleared before the write, not after: a visit that lands while the
            // file is being written then queues the next pass instead of being
            // dropped on the floor.
            saveQueued.set(false)
            writeToDisk(snapshot)
        }
    }

    private fun readFromDisk(): List<HistoryEntry> {
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val url = json.optString(KEY_URL)
                if (url.isBlank()) {
                    null
                } else {
                    HistoryEntry(
                        url = url,
                        title = json.optString(KEY_TITLE),
                        visitedAtMillis = json.optLong(KEY_VISITED_AT, 0L),
                    )
                }
            }
        }.getOrElse {
            // A corrupt history is not worth stopping the browser over. Starting
            // clean is recoverable; refusing to start is not.
            Log.w(TAG, "Discarding unreadable history", it)
            runCatching { AtomicJsonFile.delete(file) }
            emptyList()
        }
    }

    private fun writeToDisk(entries: List<HistoryEntry>) {
        runCatching {
            if (entries.isEmpty()) {
                AtomicJsonFile.delete(file)
                return@runCatching
            }
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject().apply {
                        put(KEY_URL, entry.url)
                        put(KEY_TITLE, entry.title)
                        put(KEY_VISITED_AT, entry.visitedAtMillis)
                    },
                )
            }
            AtomicJsonFile.write(file, array.toString())
        }.onFailure {
            Log.w(TAG, "Could not persist history", it)
        }
    }

    companion object {
        private const val TAG = "ClearBrowser"
        private const val FILE_NAME = "library/history.json"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_VISITED_AT = "visitedAt"

        fun forContext(context: Context): HistoryStore =
            HistoryStore(File(context.filesDir, FILE_NAME))
    }
}
