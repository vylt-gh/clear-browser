package dev.clearbrowser.feature.reading

/**
 * One tab, as far as the chrome is concerned.
 *
 * The engine is deliberately *not* here. A tab outlives the WebView that renders
 * it -- it is created from a saved session before anything is loaded, it can sit
 * in the background with no engine at all, and the engine is replaced wholesale
 * when the browsing profile changes. Keeping the two apart is what lets this
 * class be plain Kotlin and unit tested while the engine layer stays Android.
 */
data class Tab(
    val id: Long,
    /** Page title, or empty when the page has not said. */
    val title: String = "",
    /**
     * The page's URL, or the URL to load when this tab is first shown.
     *
     * Empty means a fresh tab: there is nothing to load, so the start page is
     * what the user sees.
     */
    val url: String = "",
    /** Scroll position, kept so a restored tab comes back where it was left. */
    val scrollY: Int = 0,
)

/**
 * The open tabs and which one is showing.
 *
 * ## Why this is a class and not a list in the ViewModel
 *
 * Closing a tab has rules that are easy to get subtly wrong and impossible to
 * see on screen until it is wrong: closing the active tab has to move to a
 * neighbour rather than to the first tab, closing a background tab must not
 * disturb the active one, and the last tab cannot be closed at all without the
 * caller deciding what replaces it. Those rules live here, where they can be
 * asserted, instead of being spread across three callbacks in a ViewModel that
 * holds an Activity.
 *
 * Ids are supplied by the caller and never reused, so a tab that has been closed
 * cannot be mistaken for a new one by an engine callback that arrived late.
 */
class TabStrip(
    initial: List<Tab> = emptyList(),
    active: Long? = null,
) {
    private val _tabs = initial.toMutableList()

    val tabs: List<Tab> get() = _tabs

    var activeId: Long? = null
        private set

    init {
        this.activeId = active?.takeIf { id -> _tabs.any { it.id == id } }
            ?: _tabs.firstOrNull()?.id
    }

    val active: Tab? get() = _tabs.firstOrNull { it.id == activeId }

    /**
     * Add [tab] at the end without selecting it.
     *
     * Not selecting is the point: a tab restored from a session, or one opened
     * from a link that the user is not ready to leave for, must not steal focus
     * from the page they are reading.
     */
    fun add(tab: Tab) {
        _tabs += tab
        if (activeId == null) activeId = tab.id
    }

    fun select(id: Long) {
        if (_tabs.any { it.id == id }) activeId = id
    }

    /**
     * Remove a tab and report which tab, if any, is now active.
     *
     * Closing the active tab moves to the tab that took its place -- the next
     * one to the right -- and to the one before it when it was the last. That
     * is the position the closed tab occupied, which is where the eye already
     * is, rather than always jumping back to the first tab. Closing the last
     * remaining tab leaves the strip empty and the caller is expected to open a
     * fresh one: this class does not invent tabs, because a tab needs an id
     * from the caller.
     */
    fun close(id: Long): Long? {
        val index = _tabs.indexOfFirst { it.id == id }
        if (index < 0) return activeId
        val wasActive = activeId == id
        _tabs.removeAt(index)
        if (wasActive) {
            activeId = when {
                _tabs.isEmpty() -> null
                // The tab that took its place, or the last one if it was last.
                index <= _tabs.lastIndex -> _tabs[index].id
                else -> _tabs.last().id
            }
        }
        return activeId
    }

    /** Drop every tab. Used when the browsing profile changes underneath them. */
    fun clear() {
        _tabs.clear()
        activeId = null
    }

    /** Replace a tab's details in place, keeping its position and id. */
    fun update(id: Long, transform: (Tab) -> Tab) {
        val index = _tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        _tabs[index] = transform(_tabs[index])
    }

    /** The same list, shaped for the state object the chrome reads. */
    fun summaries(): List<Tab> = _tabs.toList()
}
