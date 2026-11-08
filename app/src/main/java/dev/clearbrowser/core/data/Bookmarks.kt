package dev.clearbrowser.core.data

/** One saved page. */
data class Bookmark(
    val url: String,
    val title: String = "",
    val createdAtMillis: Long = 0L,
)

/**
 * The rules of the bookmark list.
 *
 * ## Why a bookmark is identified by its URL
 *
 * The star has to answer one question instantly: is this page saved. That means
 * the same page reached through two links -- one with a `#section`, one
 * without -- must not appear twice, and toggling it off has to remove the entry
 * the star is talking about. The URL, normalised by [normalizeWebUrl], is the
 * only identity the list needs, so there is no second id that can drift.
 *
 * Newest first, for the same reason history is: the bookmark someone wants is
 * far more often the one they just made than the one they made last year.
 */
class BookmarkList(initial: List<Bookmark> = emptyList()) {
    private val items = mutableListOf<Bookmark>()

    init {
        restore(initial)
    }

    /** Newest first. A copy, so callers cannot mutate the list. */
    val entries: List<Bookmark> get() = items.toList()

    fun isBookmarked(url: String): Boolean {
        val normalized = normalizeWebUrl(url) ?: return false
        return items.any { it.url == normalized }
    }

    /**
     * Save [url] if it is not saved, remove it if it is.
     *
     * @return true when the page is now bookmarked.
     */
    fun toggle(url: String, title: String, atMillis: Long): Boolean {
        val normalized = normalizeWebUrl(url) ?: return false
        val index = items.indexOfFirst { it.url == normalized }
        if (index >= 0) {
            items.removeAt(index)
            return false
        }
        items.add(0, Bookmark(normalized, title.trim(), atMillis))
        return true
    }

    fun remove(url: String) {
        items.removeAll { it.url == url }
    }

    fun clear() {
        items.clear()
    }

    /**
     * Merge new bookmarks into the list, deduplicating by normalized URL.
     *
     * @return Number of new bookmarks successfully imported.
     */
    fun import(newEntries: List<Bookmark>): Int {
        var count = 0
        newEntries.forEach { entry ->
            val normalized = normalizeWebUrl(entry.url) ?: return@forEach
            if (items.none { it.url == normalized }) {
                items.add(entry.copy(url = normalized, title = entry.title.trim()))
                count++
            }
        }
        if (count > 0) {
            items.sortByDescending { it.createdAtMillis }
        }
        return count
    }

    /** Replace the list with what was read from disk, applying the same rules. */
    fun restore(entries: List<Bookmark>) {
        items.clear()
        items.addAll(
            entries.mapNotNull { entry ->
                val normalized = normalizeWebUrl(entry.url) ?: return@mapNotNull null
                entry.copy(url = normalized)
            }
                .sortedByDescending { it.createdAtMillis }
                .distinctBy { it.url },
        )
    }
}
