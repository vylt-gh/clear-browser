package dev.clearbrowser.core.data

/** One page in the history, as the list and the chrome need it. */
data class HistoryEntry(
    val url: String,
    val title: String = "",
    val visitedAtMillis: Long = 0L,
)

/**
 * The rules of browsing history.
 *
 * Kept out of [HistoryStore] and free of Android types so the decisions that
 * make a history useful -- one entry per page, newest first, a bounded size --
 * can be asserted without a file system or a device.
 *
 * ## One entry per page, not one per visit
 *
 * A person who reloads an article five times while reading it did not visit five
 * different places. Duplicates make the list longer without making it more
 * useful, and the newest visit is the one worth showing, so a repeated URL is
 * moved to the front and its time updated rather than appended again.
 *
 * ## The list is bounded
 *
 * History grows without limit by nature, and the whole file is rewritten
 * whenever an entry changes. A cap keeps both the file and the list honest: old
 * entries are ordinary, and the oldest is the one that can be spared.
 */
class HistoryLog(
    initial: List<HistoryEntry> = emptyList(),
    private val limit: Int = MAX_ENTRIES,
) {
    private val items = mutableListOf<HistoryEntry>()

    init {
        // One implementation of the rules, not two: a log built from disk and a
        // log built in a test must agree about duplicates and bad schemes.
        restore(initial)
    }

    /** Newest first. A copy, so callers cannot mutate the log. */
    val entries: List<HistoryEntry> get() = items.toList()

    /**
     * Record a visit, returning false when [url] is not a page worth keeping.
     *
     * A blank title never erases a title already known for the page: some loads
     * finish before the document has a title, and the next one should not leave
     * the history with a nameless row.
     */
    fun record(url: String, title: String, atMillis: Long): Boolean {
        val normalized = normalizeWebUrl(url) ?: return false
        val previous = items.firstOrNull { it.url == normalized }
        items.removeAll { it.url == normalized }
        items.add(
            0,
            HistoryEntry(
                url = normalized,
                title = title.trim().ifBlank { previous?.title.orEmpty() },
                visitedAtMillis = atMillis,
            ),
        )
        if (items.size > limit) {
            items.subList(limit, items.size).clear()
        }
        return true
    }

    fun remove(url: String) {
        items.removeAll { it.url == url }
    }

    fun clear() {
        items.clear()
    }

    /** Replace the log with what was read from disk, applying the same rules. */
    fun restore(entries: List<HistoryEntry>) {
        items.clear()
        items.addAll(
            entries.mapNotNull { entry ->
                val normalized = normalizeWebUrl(entry.url) ?: return@mapNotNull null
                entry.copy(url = normalized)
            }
                .sortedByDescending { it.visitedAtMillis }
                // The file is the one place a duplicate can get in, by hand or
                // from a future format change. The rule "one row per page" holds
                // after a restore too, or it does not really hold.
                .distinctBy { it.url }
                .take(limit),
        )
    }

    companion object {
        /**
         * Enough to answer "where was I last week" without turning the file
         * into something that has to be streamed rather than read at startup.
         */
        const val MAX_ENTRIES = 1000
    }
}
