package dev.clearbrowser.core.data

/**
 * A suggested search query, bookmark, or history item presented while typing
 * in the expanded omnibox.
 */
sealed interface SearchSuggestion {
    /** Primary label displayed to the user. */
    val title: String

    /** Optional secondary line (e.g. full URL for bookmarks/history). */
    val subtitle: String?

    /** Target to navigate to or insert into the omnibox field. */
    val destination: String

    /**
     * Live search engine completion for a typed query.
     */
    data class Query(
        val query: String,
    ) : SearchSuggestion {
        override val title: String get() = query
        override val subtitle: String? get() = null
        override val destination: String get() = query
    }

    /**
     * Saved bookmark matching the typed query, or a recent bookmark when blank.
     */
    data class Bookmark(
        override val title: String,
        val url: String,
    ) : SearchSuggestion {
        override val subtitle: String get() = url
        override val destination: String get() = url
    }

    /**
     * Visited history entry matching the typed query, or a recent visit when blank.
     */
    data class History(
        override val title: String,
        val url: String,
    ) : SearchSuggestion {
        override val subtitle: String get() = url
        override val destination: String get() = url
    }
}
