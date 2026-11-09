@file:SuppressLint("StaticFieldLeak")

package dev.clearbrowser.feature.reading

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.clearbrowser.core.data.BookmarkStore
import dev.clearbrowser.core.data.BrowserSettings
import dev.clearbrowser.core.data.FaviconStore
import dev.clearbrowser.core.data.HistoryStore
import dev.clearbrowser.core.data.SavedTab
import dev.clearbrowser.core.data.SessionStore
import dev.clearbrowser.core.data.SettingsStore
import dev.clearbrowser.core.data.extractFaviconHost
import dev.clearbrowser.core.data.resolveAddress
import dev.clearbrowser.core.engine.BrowserProfile
import dev.clearbrowser.core.engine.ClearGeckoRuntime
import dev.clearbrowser.core.engine.Engine
import dev.clearbrowser.core.engine.EngineListener
import dev.clearbrowser.core.engine.GeckoViewEngine
import dev.clearbrowser.core.engine.LoadState
import dev.clearbrowser.core.engine.WebViewEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/** The blank document the engine shows when there is nothing to restore. */
private const val ABOUT_BLANK = "about:blank"

data class ReadingUiState(
    val url: String = "",
    val title: String = "",
    val loadState: LoadState = LoadState.IDLE,
    val secure: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val chromeMode: ChromeMode = ChromeMode.SUMMONED,
    /** True if chrome was hidden manually via button/press, overriding hideHandle until restored. */
    val temporaryHandle: Boolean = false,
    val settings: BrowserSettings = BrowserSettings(),
    /** A JS alert/confirm/prompt awaiting an answer. Never left dangling. */
    val jsDialog: dev.clearbrowser.core.engine.JsDialog? = null,
    /** True while the window is a private one. */
    val incognito: Boolean = false,
    /**
     * Whether the page is at (or within a few pixels of) the top.
     *
     * Exists to decide how much top inset the page gets. See the note in
     * [ReadingViewModel.onScrollChanged]: a `position: fixed` header is
     * unreachable while it sits under the status bar, but insetting the page
     * permanently costs the user the top of every page once they scroll. These
     * two facts cannot both be satisfied at once, so this picks between them
     * based on where the user is.
     */
    val scrollAtTop: Boolean = true,
    /** The open tabs, in the order they were opened. */
    val tabs: List<Tab> = emptyList(),
    /** Which tab the chrome and the page are showing. */
    val activeTabId: Long? = null,
    /** True while the tab switcher is up. */
    val tabsVisible: Boolean = false,
    /** Whether pages are asked for their desktop layout. */
    val desktopSite: Boolean = false,
    /** Whether the page on screen is bookmarked. */
    val isBookmarked: Boolean = false,
    /** Which half of the library the sheet is showing, while it is up. */
    val libraryTab: LibraryTab? = null,
    /** Live query suggestions, matching bookmarks, and history entries. */
    val searchSuggestions: List<dev.clearbrowser.core.data.SearchSuggestion> = emptyList(),
    /** Dominant or average top bar color of the webpage, null if not sampled or on start page. */
    val topBarColor: Int? = null,
    /** Whether the webpage top background is light, for status bar icon contrast. */
    val isTopBarLight: Boolean = true,
    /** Blurred micro-buffer backdrop sampled from the page region directly behind the chrome. */
    val backdropBlur: androidx.compose.ui.graphics.ImageBitmap? = null,
    val backdropBlurVersion: Long = 0L,
    /** Whether an HTML5 element or video is currently in fullscreen mode. */
    val isFullscreen: Boolean = false,
    /** Whether the Find in Page bar is visible. */
    val findInPageVisible: Boolean = false,
    /** The active search query for Find in Page. */
    val findQuery: String = "",
    /** Active match index (1-based, or 0 if none). */
    val findActiveMatch: Int = 0,
    /** Total number of matches found on the page. */
    val findTotalMatches: Int = 0,
    /** Whether the popup action menu from the three-dots button is open. */
    val menuPopupVisible: Boolean = false,
    /** Whether the full settings bottom sheet is open. */
    val settingsVisible: Boolean = false,
    /** Whether the extensions management sheet is open. */
    val extensionsVisible: Boolean = false,
    /** Active context menu info for long-pressed link/image element. */
    val contextMenu: dev.clearbrowser.core.engine.ContextMenuInfo? = null,
    /** Active extension popup view session displayed in a sheet. */
    val extensionPopup: ExtensionPopupState? = null,
    /** Cached favicons for open tabs. */
    val favicons: Map<String, Bitmap> = emptyMap(),
    /** Whether the first-run onboarding screen is visible. */
    val onboardingVisible: Boolean = false,
    /** Count of bookmarks imported in the current session, or null if none imported yet. */
    val importedBookmarksCount: Int? = null,
) {
    /** What the address field shows when it is not focused. */
    val displayHost: String
        get() = if (isStartPage) "Search"
        else url.substringAfter("://").substringBefore('/').substringBefore('?')
            .removePrefix("www.").ifEmpty { "Search" }

    /**
     * True while there is no page, which is every fresh launch.
     *
     * The browser used to open `example.com` here. That was wrong in a way that
     * is easy to miss: `example.com` is a placeholder owned by IANA, its only
     * purpose is to be an example in documentation, and loading it made the app
     * look like it had a homepage that it did not have. A browser with nothing
     * remembered should show nothing, which is what this is.
     */
    val isStartPage: Boolean
        get() = url.isBlank() || url == ABOUT_BLANK

    /**
     * True only while a load is genuinely in flight.
     *
     * `COMMITTED` deliberately does not count. On any page that redirects,
     * `onPageCommitVisible` for the final document can arrive *after*
     * `onPageFinished` for the redirect target, so including it left the chrome
     * showing a stop button on a fully loaded page.
     */
    val isLoading: Boolean
        get() = loadState == LoadState.LOADING

    /**
     * Whether the star in the menu should offer to save the page on screen.
     *
     * False on the start page, where there is nothing to save, and in a private
     * window, where saving it to disk would break the window's one promise. The
     * menu reads this rather than asking the ViewModel, so it can show *why*
     * nothing is being offered instead of a disabled control.
     */
    val canBookmark: Boolean
        get() = !isStartPage && !incognito
}

/**
 * Owns the browsing session: the tabs, the engine behind the one on screen, and
 * the chrome's mode.
 *
 * ## Why there is exactly one attached engine at a time
 *
 * Tabs are real `WebView`s and each one keeps its document, history and scroll
 * position while it is not showing. But only the visible tab is *attached* to
 * the container: the rest are detached, paused, and left alone. Keeping every
 * WebView in the hierarchy instead would leave each hidden tab's compositor
 * running behind the one on screen, which is how a browser ends up using four
 * hundred megabytes to display a page about cats.
 *
 * The consequence to remember when reading this file: an engine may not exist
 * yet for a tab that exists. A tab is created from a saved session before
 * anything is loaded, so [`engines`] is filled lazily on first activation, and
 * every use of an engine for the *active* tab has to go through
 * [ensureActiveEngine].
 *
 * ## Where a session starts
 *
 * With one blank tab. An explicit intent wins over the restored page but not
 * over the restored tabs: a link tapped in another app opens in a new tab rather
 * than replacing someone's work.
 */
class ReadingViewModel(
    /**
     * The host Activity, held only while a host is attached. Nulled in
     * [onHostDestroyed], which the Activity calls from `onDestroy`.
     */
    private var hostActivity: Activity?,
    private val settingsStore: SettingsStore,
    private val sessionStore: SessionStore,
    /** The host seam, supplied at attach and released on destroy, as above. */
    private var host: dev.clearbrowser.core.engine.BrowserHost?,
    private val historyStore: HistoryStore? = null,
    private val bookmarkStore: BookmarkStore? = null,
    private val downloadsStore: dev.clearbrowser.core.data.DownloadsStore? = null,
    private val sitePermissionStore: dev.clearbrowser.core.data.SitePermissionStore? = null,
    private val suggestionsFetcher: dev.clearbrowser.core.data.SearchSuggestionsFetcher = dev.clearbrowser.core.data.DefaultSearchSuggestionsFetcher,
    private val faviconStore: FaviconStore? = null,
) : ViewModel() {

    private var suggestionsJob: kotlinx.coroutines.Job? = null

    private val scrollIntent = ScrollIntentDetector()

    /** Tabs, and the rules about which one is showing. Pure Kotlin, tested. */
    private val strip = TabStrip()

    /**
     * The engine for each tab that has one. A tab with no entry here has never
     * been shown, or was restored from a session and not visited yet.
     */
    private val engines = mutableMapOf<Long, Engine>()

    /** Engine-side state, keyed by tab id and dropped when the tab closes. */
    private val pendingScroll = mutableMapOf<Long, Int>()
    private val lastHeight = mutableMapOf<Long, Int>()
    private val restoredStates = mutableMapOf<Long, Bundle>()

    private var nextTabId = 1L

    /**
     * Release everything that references the Activity, and snapshot the session
     * so the replacement host can pick up where this one left off.
     *
     * ## Why this exists
     *
     * A ViewModel outlives its Activity across a configuration change. The
     * manifest declares the common ones so the browser does not reload on
     * rotation or a dark-mode flip, but a locale or colour-mode change still
     * recreates the Activity while the ViewModel lives on. Holding the Activity
     * across that boundary leaks it, and holding a WebView built from it is
     * worse still.
     *
     * Rather than fight the lifecycle, it is respected: drop the references,
     * persist the session, and let the new host re-attach. The session store
     * already exists for exactly this, so recovery costs nothing extra.
     *
     * `StaticFieldLeak` is suppressed on the class because lint cannot see that
     * both references are nullable and are nulled here, which the Activity calls
     * from `onDestroy`.
     *
     * @param finishing true when the Activity is going away for good, false when
     *   it is only being recreated (rotation, locale or colour-mode change).
     *   The distinction decides what happens to private mode, and getting it
     *   wrong is a privacy bug in one direction and a usability bug in the other.
     *
     * ## Why private mode survives a configuration change but not a finish
     *
     * On a configuration change the *same* private session continues in a new
     * Activity, so the replacement has to re-enter the private profile. Snapping
     * back to normal would silently move the user out of the private window they
     * are still using, and the pages they were reading would then be restored
     * into the normal profile.
     *
     * On a real finish there is no session left to continue. A private window
     * that reopened itself on the next launch would be worse than useless: it
     * would look private while nothing about it is being kept, and it would
     * restore URLs that the user is entitled to assume are gone.
     */
    fun onHostDestroyed(finishing: Boolean) {
        val wasIncognito = engineProfile.isIncognito
        runCatching { persistSession() }
        destroyAllEngines()
        container = null
        hostActivity = null
        host = null
        if (finishing) {
            engineProfile = BrowserProfile.DEFAULT
            _state.value = _state.value.copy(incognito = false)
        } else if (wasIncognito) {
            // Keep the profile. There is no separate colour state to drop any
            // more, so the state needs no touch at all on this path.
            Unit
        }
    }

    private val _state = MutableStateFlow(ReadingUiState())
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    /**
     * Load progress, kept out of [ReadingUiState] on purpose.
     *
     * Progress ticks at up to 100Hz for the whole of every load. In the main
     * state object that meant every tick recomposed the theme, the status-bar
     * scrim and the reveal handle -- work proportional to the screen, repeated
     * continuously, for a one-pixel line. A separate flow lets the progress bar
     * be the only thing that redraws.
     */
    private val progressFlow = MutableStateFlow(0)
    val progress: StateFlow<Int> = progressFlow.asStateFlow()

    private var isSystemDark: Boolean = false

    /**
     * The library, as the sheets read it.
     *
     * Kept in flows of their own rather than in [ReadingUiState] for the reason
     * load progress is: a long list recomposing the chrome on every entry would
     * turn a page load into a full-screen redraw. Only the sheet reads these.
     *
     * The store is nullable so a preview or test can build a ViewModel without a
     * file system; those get an empty, permanently quiet flow, held here so a
     * getter cannot hand back a different object on every read.
     */
    val history: StateFlow<List<dev.clearbrowser.core.data.HistoryEntry>>
        get() = historyStore?.entries ?: fallbackHistory

    private val fallbackHistory =
        MutableStateFlow<List<dev.clearbrowser.core.data.HistoryEntry>>(emptyList())

    val bookmarks: StateFlow<List<dev.clearbrowser.core.data.Bookmark>>
        get() = bookmarkStore?.entries ?: fallbackBookmarks

    private val fallbackBookmarks =
        MutableStateFlow<List<dev.clearbrowser.core.data.Bookmark>>(emptyList())

    val downloads: StateFlow<List<dev.clearbrowser.core.data.DownloadItem>>
        get() = downloadsStore?.downloads ?: fallbackDownloads

    private val fallbackDownloads =
        MutableStateFlow<List<dev.clearbrowser.core.data.DownloadItem>>(emptyList())

    val sitePermissions: StateFlow<List<dev.clearbrowser.core.data.SitePermission>>
        get() = sitePermissionStore?.permissions ?: fallbackSitePermissions

    private val fallbackSitePermissions =
        MutableStateFlow<List<dev.clearbrowser.core.data.SitePermission>>(emptyList())

    val installedExtensions: StateFlow<List<dev.clearbrowser.core.engine.ExtensionInfo>>
        get() = ClearGeckoRuntime.installedExtensions

    init {
        viewModelScope.launch {
            settingsStore.state.collect { settings ->
                _state.value = _state.value.copy(settings = settings)
                applyTextZoom(settings.textZoom)
            }
        }
    }

    private fun applyTextZoom(zoom: Int) {
        val factor = (zoom.coerceIn(50, 300)) / 100f
        runCatching {
            ClearGeckoRuntime.setFontSizeFactor(factor)
        }
    }

    /**
     * A URL an incoming `ACTION_VIEW` intent asked for.
     *
     * Held separately from [attach] because the Activity has an intent before the
     * Compose tree exists, and the engine may not be attached yet either way.
     */
    private var pendingStartUrl: String? = null

    /**
     * The WebView state Bundle from `onSaveInstanceState`, if the system kept it.
     *
     * It carries the whole session -- every tab plus each engine's own state --
     * so it is preferred over the file when present. See [SessionStore].
     */
    private var savedSessionBundle: Bundle? = null

    /** The container the engine attaches to. Survives engine rebuilds. */
    private var container: ViewGroup? = null

    private var engineAttached = false

    private var engineProfile: BrowserProfile = BrowserProfile.DEFAULT

    /**
     * Desktop-site mode, kept here rather than in the composition.
     *
     * A tab opened after the switch would otherwise get a brand new engine with
     * the default user agent, so the setting would silently apply to every page
     * except the one the user then looked at.
     */
    private var desktopSiteEnabled = false

    /**
     * Hand the engine its container. Safe to call repeatedly.
     *
     * ## Why the guard is not paranoia
     *
     * This is wired to `AndroidView`'s `update` block, and `update` is a
     * *recomposition* callback, not an initialisation one. Anything that causes
     * the screen to recompose with a new lambda identity -- a state change, a
     * theme change, the reordering of a single child -- can call it again. Every
     * call here attaches a `WebView` to the container, so an unguarded version
     * does not merely waste work: it stacks a second view on top of the first.
     *
     * The identity check is on the *container*, not just "is an engine present",
     * because a genuinely new container (after the host was released) still has
     * to be filled.
     */
    fun attach(parent: ViewGroup, activity: Activity, browserHost: dev.clearbrowser.core.engine.BrowserHost) {
        if (engineAttached && container === parent && hostActivity === activity) return
        hostActivity = activity
        host = browserHost
        container = parent

        val firstAttach = !engineAttached
        engineAttached = true

        if (firstAttach) {
            restoreTabs()
            val requested = pendingStartUrl
            pendingStartUrl = null
            if (requested != null) openRequested(requested)
        } else {
            // A new container means the old one is gone with the old Activity.
            // Only the active tab is re-parented: the others were deliberately
            // detached and must stay that way.
            ensureActiveEngine()
        }
        publishSession()
        applyTextZoom(_state.value.settings.textZoom)
    }

    // ------------------------------------------------------------------ restore

    /**
     * Build the starting set of tabs, in order of preference: the Bundle the
     * system kept, then the durable file, then a single blank tab.
     */
    private fun restoreTabs() {
        val bundle = savedSessionBundle
        savedSessionBundle = null
        val restored = bundle?.let { SessionStore.readBundle(it) }
        if (restored != null) {
            adopt(restored.tabs, restored.activeIndex, restored.engineStates)
            return
        }
        val session = sessionStore.load()
        adopt(session.tabs, session.activeIndex, emptyList())
    }

    private fun adopt(tabs: List<SavedTab>, activeIndex: Int, states: List<Bundle?>) {
        if (tabs.isEmpty()) {
            newTab()
            return
        }
        var activeId: Long? = null
        tabs.forEachIndexed { index, saved ->
            val tab = Tab(
                id = nextTabId++,
                title = saved.title,
                // A page this browser no longer has is treated as a blank tab
                // rather than loaded. See [isRetiredStartPage].
                url = if (isRetiredStartPage(saved.url)) "" else saved.url,
                scrollY = saved.scrollY,
            )
            strip.add(tab)
            states.getOrNull(index)?.let { restoredStates[tab.id] = it }
            if (index == activeIndex) activeId = tab.id
        }
        activeId?.let { strip.select(it) }
        ensureActiveEngine()
        pushActiveTab()
    }

    /**
     * Open the URL an intent asked for.
     *
     * In place when the session is a single blank tab, which is every cold start,
     * and in a new tab otherwise: a link tapped in another app is an addition to
     * what the user has open, not a replacement for it.
     */
    private fun openRequested(url: String) {
        val onlyBlank = strip.tabs.size == 1 && strip.tabs.first().url.isBlank()
        if (onlyBlank) {
            ensureActiveEngine()?.loadUrl(url)
        } else {
            newTab(url)
        }
    }

    // ------------------------------------------------------------------- tabs

    /** Open a new tab, optionally at [url]. Selects it. */
    fun newTab(url: String = "") {
        val tab = Tab(id = nextTabId++, url = url)
        strip.add(tab)
        activate(tab.id)
    }

    fun selectTab(id: Long) {
        if (id == strip.activeId) return
        activate(id)
    }

    /**
     * Close a tab.
     *
     * Closing the last one opens a fresh blank tab rather than leaving the
     * browser with no tabs at all: a browser window with nothing in it is not a
     * state anyone wants to be in, and every other browser behaves this way.
     */
    fun closeTab(id: Long) {
        val wasActive = strip.activeId == id
        engines.remove(id)?.let { runCatching { it.destroy() } }
        pendingScroll.remove(id)
        lastHeight.remove(id)
        restoredStates.remove(id)

        strip.close(id)
        if (strip.tabs.isEmpty()) {
            newTab()
            return
        }
        if (wasActive) {
            ensureActiveEngine()
            pushActiveTab()
        } else {
            publishTabs()
        }
    }

    fun openTabs() {
        // Switcher and menu are both full sheets, and the omnibox has the
        // keyboard: only one of the three can be up at a time, so opening this
        // closes the others rather than sitting on top of them.
        _state.value = _state.value.copy(
            tabsVisible = true,
            chromeMode = ChromeMode.SUMMONED,
            // Only one sheet can be up at a time; opening this one closes the
            // library rather than stacking a second modal sheet on it.
            libraryTab = null,
            menuPopupVisible = false,
            settingsVisible = false,
            extensionsVisible = false,
        )
        loadFaviconsForTabs(_state.value.tabs)
    }

    fun closeTabs() {
        if (_state.value.tabsVisible) _state.value = _state.value.copy(tabsVisible = false)
    }

    /**
     * Make [id] the tab on screen.
     *
     * The order matters: the outgoing tab reports its scroll position *before*
     * it is detached, because a detached WebView will not be asked again, and
     * the position is what puts the user back where they were.
     */
    private fun activate(id: Long) {
        if (strip.tabs.none { it.id == id }) return
        val previous = strip.activeId
        // A tab can be active before it has an engine: a fresh one from
        // [newTab], or the strip's automatic selection when it was empty. So
        // "already active" alone is not enough to skip the work -- the engine
        // has to exist too, or the tab would come up with no page and no way to
        // get one.
        if (previous == id && engines.containsKey(id)) return
        if (previous != null && previous != id) {
            saveScroll(previous)
            engines[previous]?.let { engine ->
                engine.setActive(false)
                engine.detach()
            }
        }
        strip.select(id)
        ensureActiveEngine()
        scrollIntent.reset()
        progressFlow.value = 0
        pushActiveTab()
    }

    /**
     * The engine for the active tab, created and attached if it does not exist.
     *
     * Every path that loads anything goes through here, because a tab restored
     * from a session exists without an engine: the engine is not built until the
     * tab is actually shown, which is what keeps ten restored tabs from costing
     * ten renderers.
     */
    private fun ensureActiveEngine(): Engine? {
        val tab = strip.active ?: return null
        engines[tab.id]?.let { existing ->
            container?.let { existing.attach(it) }
            existing.setActive(true)
            return existing
        }
        val parent = container ?: return null
        val activity = hostActivity ?: return null
        val browserHost = host ?: return null

        val engine = GeckoViewEngine(
            activity,
            TabListener(tab.id, engineProfile.isIncognito),
            browserHost,
            engineProfile,
        )
        engines[tab.id] = engine
        engine.desktopUserAgentEnabled = desktopSiteEnabled
        engine.setPageBackgroundColor(neutralFallback())
        engine.attach(parent)
        engine.setActive(true)

        val restored = restoredStates.remove(tab.id)
        if (restored != null && runCatching { engine.restoreState(restored) }.getOrDefault(false)) {
            // The engine restored its own history, including the current page.
            pendingScroll[tab.id] = tab.scrollY
        } else if (tab.url.isNotBlank() && tab.url != ABOUT_BLANK) {
            pendingScroll[tab.id] = tab.scrollY
            engine.loadUrl(tab.url)
        }
        return engine
    }

    /** The active tab's engine, if one exists. Never creates one. */
    private fun engineForActive(): Engine? = strip.activeId?.let { engines[it] }

    private fun destroyAllEngines() {
        engines.values.forEach { runCatching { it.destroy() } }
        engines.clear()
        pendingScroll.clear()
        lastHeight.clear()
        restoredStates.clear()
    }

    private fun saveScroll(id: Long?) {
        if (id == null) return
        val engine = engines[id] ?: return
        val y = runCatching { engine.currentScrollY() }.getOrDefault(0)
        strip.update(id) { it.copy(scrollY = y) }
    }

    // ---------------------------------------------------------------- profiles

    /**
     * Switch between private and normal browsing.
     *
     * Every tab is torn down rather than moved: the storage profile is fixed
     * when a WebView is created, so a tab cannot cross the boundary without
     * being rebuilt, and a page carried into the wrong profile is exactly the
     * leak private mode exists to prevent.
     *
     * Two privacy decisions are deliberate here:
     *  - Leaving incognito **erases** the private profile rather than just
     *    forgetting about it. The data is already unreachable, but scrubbing
     *    means a future reuse of the profile name cannot resurrect anything.
     *  - Incognito is **not persisted**. A restored session must never reappear
     *    inside a private window, and a private window must never come back as a
     *    normal one. Not remembering the mode is what makes both impossible.
     */
    fun setIncognito(enabled: Boolean) {
        val target = if (enabled) BrowserProfile.INCOGNITO else BrowserProfile.DEFAULT
        if (target == engineProfile) return
        engineProfile = target
        // Publish the new profile before rebuilding the tabs: the new tab's
        // first state push asks whether the page is bookmarked, and it has to
        // see the window it is actually in.
        _state.value = _state.value.copy(incognito = target.isIncognito)

        val leavingIncognito = target == BrowserProfile.DEFAULT
        if (leavingIncognito) engines.values.forEach { runCatching { it.clearProfileData() } }
        val carriedUrl = strip.active?.url
        destroyAllEngines()
        strip.clear()

        // Start clean in the new profile rather than replaying a private page
        // into a normal window, where it would be written to disk.
        val reopen = if (leavingIncognito) null else carriedUrl
        newTab(if (reopen.isNullOrBlank() || reopen == ABOUT_BLANK) "" else reopen)
    }

    // ------------------------------------------------------------- persistence

    /**
     * Snapshot the session for persistence. Called from `onStop`, which is the
     * only lifecycle callback reliable enough for this.
     *
     * A private window writes nothing. The URLs are the whole point of what a
     * user browses privately, and `onStop` is called every time the app is
     * backgrounded -- including when the process is about to be killed. Writing
     * the file here would leave a durable record of every private page, which is
     * the exact failure mode a private window exists to prevent, and it would be
     * silent: nothing about a saved file is visible to the user.
     *
     * Cookies are flushed for the same reason, and by the same rule. `flush()`
     * forces the in-memory jar to disk, so flushing a private profile would
     * achieve precisely what the private profile was created to avoid.
     */
    fun persistSession() {
        // Leaving any previously saved *normal* session alone is deliberate: it
        // belongs to the normal profile and will be restored into the normal
        // profile. Deleting it here would only throw away the user's real
        // session because they happened to rotate while in a private window.
        if (engineProfile.isIncognito) return
        engines.values.firstOrNull()?.let { runCatching { it.flushCookies() } }
        saveScroll(strip.activeId)

        // Blank tabs are kept, not filtered out. A tab the user just opened is
        // part of what they left behind, and dropping it would silently move
        // them to some other page on return. Whether there is anything worth
        // saving at all is [SessionState.hasPage]'s decision: a session of
        // nothing but blank tabs is not written, which is the cold-start case.
        var active = 0
        val saved = strip.tabs.mapIndexed { index, tab ->
            if (tab.id == strip.activeId) active = index
            SavedTab(url = tab.url, title = tab.title, scrollY = tab.scrollY)
        }
        sessionStore.save(
            dev.clearbrowser.core.data.SessionState(
                tabs = saved,
                activeIndex = active,
                savedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * The Bundle for `onSaveInstanceState`, or null when there is nothing to say.
     *
     * It contains the tab list as well as each engine's own state, so the two
     * cannot drift apart between a process death and the restore. See
     * [SessionStore.writeBundle].
     */
    fun saveWebViewState(): Bundle? {
        val tabs = strip.tabs
        if (tabs.isEmpty()) return null
        saveScroll(strip.activeId)
        val states = tabs.map { tab -> engines[tab.id]?.let { runCatching { it.saveState() }.getOrNull() } }
        val out = Bundle()
        SessionStore.writeBundle(
            out = out,
            tabs = tabs.map { SavedTab(url = it.url, title = it.title, scrollY = it.scrollY) },
            activeIndex = tabs.indexOfFirst { it.id == strip.activeId }.coerceAtLeast(0),
            engineStates = states,
        )
        return out
    }

    fun setSavedWebViewState(state: Bundle?) {
        savedSessionBundle = state
    }

    /** Record the URL an incoming intent asked us to open. */
    fun setStartUrl(url: String?) {
        if (url.isNullOrBlank()) return
        val normalized = resolveAddress(url, _state.value.settings.searchEngine) ?: return
        if (engineAttached) {
            openRequested(normalized)
        } else {
            pendingStartUrl = normalized
        }
    }

    /**
     * Told by the composition whenever the system scheme changes, so the neutral
     * page background can follow it.
     */
    fun setSystemDark(dark: Boolean) {
        if (isSystemDark == dark) return
        isSystemDark = dark
    }

    // ------------------------------------------------------------ user intent

    fun navigate(raw: String) {
        suggestionsJob?.cancel()
        val url = resolveAddress(raw, _state.value.settings.searchEngine) ?: return
        _state.value = _state.value.copy(
            chromeMode = ChromeMode.SUMMONED,
            searchSuggestions = emptyList(),
        )
        scrollIntent.reset()
        // A fresh tab has no engine yet; this is what creates one for it.
        val engine = ensureActiveEngine() ?: return
        engine.loadUrl(url)
    }

    fun reloadOrStop() {
        val engine = engineForActive() ?: return
        if (_state.value.isLoading) engine.stop() else engine.reload()
    }

    fun canGoBack(): Boolean = !_state.value.isStartPage

    fun canGoForward(): Boolean {
        val engine = engineForActive()
        return (engine != null && engine.canGoForward()) || _state.value.canGoForward
    }

    fun goBack(): Boolean {
        if (_state.value.isStartPage) return false
        val engine = engineForActive()
        val canBack = (engine?.canGoBack() == true) || _state.value.canGoBack
        if (engine != null && canBack) {
            revealChrome()
            engine.goBack()
            _state.value = _state.value.copy(
                chromeMode = ChromeMode.SUMMONED,
                canGoBack = engine.canGoBack(),
                canGoForward = engine.canGoForward(),
            )
            return true
        }
        goToStartPage()
        return true
    }

    fun goToStartPage() {
        val tab = strip.active ?: return
        engines.remove(tab.id)?.let { runCatching { it.destroy() } }
        strip.update(tab.id) { it.copy(url = ABOUT_BLANK, title = "", scrollY = 0) }
        pendingScroll.remove(tab.id)
        lastHeight.remove(tab.id)
        ensureActiveEngine()
        revealChrome()
        _state.value = _state.value.copy(
            url = ABOUT_BLANK,
            title = "",
            loadState = LoadState.IDLE,
            secure = true,
            canGoBack = false,
            canGoForward = false,
            scrollAtTop = true,
            topBarColor = null,
            isTopBarLight = !isSystemDark,
            chromeMode = ChromeMode.SUMMONED,
            tabs = strip.summaries(),
        )
    }

    fun goForward(): Boolean {
        val engine = engineForActive()
        if (engine != null && (engine.canGoForward() || _state.value.canGoForward)) {
            revealChrome()
            engine.goForward()
            _state.value = _state.value.copy(
                canGoBack = engine.canGoBack(),
                canGoForward = engine.canGoForward(),
            )
            return true
        }
        return false
    }

    fun setImmersive(enabled: Boolean) = settingsStore.setImmersiveMode(enabled)

    /** Persist cookies. Called when backgrounding, which is the only safe time. */
    fun flushCookies() {
        engines.values.firstOrNull()?.let { runCatching { it.flushCookies() } }
    }

    fun clearBrowsingData(
        clearHistory: Boolean = true,
        clearCookies: Boolean = true,
        clearCache: Boolean = true,
    ) {
        if (clearHistory) {
            clearHistory()
        }
        engines.values.forEach { runCatching { it.clearBrowsingData(clearCookies, clearCache) } }
    }

    fun setTextZoom(zoom: Int) {
        settingsStore.setTextZoom(zoom)
    }

    fun reserveChromeBounds(bounds: android.graphics.RectF?) {
        // Every engine is told, not just the active one: the reservations live on
        // the shared container, so a tab that is detached now still needs them
        // when it comes back.
        engines.values.forEach { runCatching { it.reserveChromeBounds(bounds) } }
    }

    fun setDesktopSite(enabled: Boolean) {
        desktopSiteEnabled = enabled
        _state.value = _state.value.copy(desktopSite = enabled)
        engines.values.forEach { runCatching { it.desktopUserAgentEnabled = enabled } }
    }

    fun toggleChrome() {
        val mode = _state.value.chromeMode
        if (mode.isChromeVisible) {
            hideChromeManually()
        } else {
            revealChrome()
        }
    }

    fun hideChromeManually() {
        _state.value = _state.value.copy(
            chromeMode = ChromeMode.READING,
            temporaryHandle = true,
        )
    }

    fun expandOmnibox() {
        _state.value = _state.value.copy(
            chromeMode = ChromeMode.EXPANDED,
            temporaryHandle = false,
        )
        val initialQuery = if (_state.value.isStartPage) "" else _state.value.url
        onOmniboxQueryChanged(initialQuery)
    }

    fun collapseOmnibox() {
        suggestionsJob?.cancel()
        _state.value = _state.value.copy(
            chromeMode = ChromeMode.SUMMONED,
            temporaryHandle = false,
            searchSuggestions = emptyList(),
        )
    }

    fun exitFullScreen() {
        engineForActive()?.exitFullScreen()
        _state.value = _state.value.copy(isFullscreen = false)
    }

    fun openFindInPage() {
        _state.value = _state.value.copy(
            findInPageVisible = true,
            findQuery = "",
            findActiveMatch = 0,
            findTotalMatches = 0,
            menuPopupVisible = false,
            settingsVisible = false,
            extensionsVisible = false,
        )
    }

    fun setFindQuery(query: String) {
        _state.value = _state.value.copy(findQuery = query)
        if (query.isBlank()) {
            engineForActive()?.clearFindInPage()
            _state.value = _state.value.copy(findActiveMatch = 0, findTotalMatches = 0)
        } else {
            engineForActive()?.findInPage(query, forward = true)
        }
    }

    fun findNext() {
        val query = _state.value.findQuery
        if (query.isNotBlank()) {
            engineForActive()?.findInPage(query, forward = true)
        }
    }

    fun findPrevious() {
        val query = _state.value.findQuery
        if (query.isNotBlank()) {
            engineForActive()?.findInPage(query, forward = false)
        }
    }

    fun closeFindInPage() {
        engineForActive()?.clearFindInPage()
        _state.value = _state.value.copy(
            findInPageVisible = false,
            findQuery = "",
            findActiveMatch = 0,
            findTotalMatches = 0,
        )
    }

    fun onOmniboxQueryChanged(query: String) {
        suggestionsJob?.cancel()
        val trimmed = query.trim()
        val isIncognito = _state.value.incognito

        if (trimmed.isEmpty()) {
            val recentBookmarks = (bookmarkStore?.entries?.value ?: emptyList())
                .take(3)
                .map { dev.clearbrowser.core.data.SearchSuggestion.Bookmark(title = it.title.ifBlank { it.url }, url = it.url) }

            val recentHistory = if (!isIncognito) {
                (historyStore?.entries?.value ?: emptyList())
                    .filter { h -> recentBookmarks.none { it.url == h.url } }
                    .take(3)
                    .map { dev.clearbrowser.core.data.SearchSuggestion.History(title = it.title.ifBlank { it.url }, url = it.url) }
            } else emptyList()

            _state.value = _state.value.copy(searchSuggestions = recentBookmarks + recentHistory)
            return
        }

        val queryLower = trimmed.lowercase()
        val matchingBookmarks = (bookmarkStore?.entries?.value ?: emptyList())
            .filter { it.title.lowercase().contains(queryLower) || it.url.lowercase().contains(queryLower) }
            .take(2)
            .map { dev.clearbrowser.core.data.SearchSuggestion.Bookmark(title = it.title.ifBlank { it.url }, url = it.url) }

        val matchingHistory = if (!isIncognito) {
            (historyStore?.entries?.value ?: emptyList())
                .filter { h ->
                    matchingBookmarks.none { it.url == h.url } &&
                        (h.title.lowercase().contains(queryLower) || h.url.lowercase().contains(queryLower))
                }
                .take(2)
                .map { dev.clearbrowser.core.data.SearchSuggestion.History(title = it.title.ifBlank { it.url }, url = it.url) }
        } else emptyList()

        val localMatches = matchingBookmarks + matchingHistory
        _state.value = _state.value.copy(searchSuggestions = localMatches)

        val engine = _state.value.settings.searchEngine
        suggestionsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            val webResults = suggestionsFetcher.fetch(engine, trimmed)
                .filter { suggestion ->
                    suggestion.isNotBlank() &&
                        !suggestion.trim().equals(trimmed, ignoreCase = true) &&
                        localMatches.none { it.title.equals(suggestion, ignoreCase = true) }
                }
                .take(5)
                .map { dev.clearbrowser.core.data.SearchSuggestion.Query(it) }

            _state.value = _state.value.copy(
                searchSuggestions = localMatches + webResults,
            )
        }
    }

    /**
     * The menu sheet, which is the only overlay [ChromeMode.OVERLAID] means.
     *
     * The tab switcher is tracked separately ([ReadingUiState.tabsVisible])
     * precisely because the two must not be open at once: they are both full
     * sheets, and whichever opened second would hide the first while the state
     * claimed both were up.
     */
    fun toggleMenuPopup() {
        val current = _state.value.menuPopupVisible
        _state.value = _state.value.copy(
            menuPopupVisible = !current,
            settingsVisible = false,
            extensionsVisible = false,
        )
    }

    fun openMenuPopup() {
        _state.value = _state.value.copy(
            menuPopupVisible = true,
            settingsVisible = false,
            extensionsVisible = false,
        )
    }

    fun closeMenuPopup() {
        if (_state.value.menuPopupVisible) {
            _state.value = _state.value.copy(menuPopupVisible = false)
        }
    }

    fun openSettings() {
        _state.value = _state.value.copy(
            settingsVisible = true,
            menuPopupVisible = false,
            extensionsVisible = false,
            libraryTab = null,
            tabsVisible = false,
        )
    }

    fun closeSettings() {
        if (_state.value.settingsVisible) {
            _state.value = _state.value.copy(settingsVisible = false)
        }
    }

    fun openOnboarding() {
        _state.value = _state.value.copy(
            onboardingVisible = true,
            settingsVisible = false,
            menuPopupVisible = false,
            extensionsVisible = false,
            libraryTab = null,
            tabsVisible = false,
        )
    }

    fun completeOnboarding() {
        settingsStore.setOnboardingCompleted(true)
        if (_state.value.onboardingVisible) {
            _state.value = _state.value.copy(onboardingVisible = false)
        }
    }

    fun openExtensions() {
        _state.value = _state.value.copy(
            extensionsVisible = true,
            menuPopupVisible = false,
            settingsVisible = false,
            libraryTab = null,
            tabsVisible = false,
        )
    }

    fun closeExtensions() {
        if (_state.value.extensionsVisible) {
            _state.value = _state.value.copy(extensionsVisible = false)
        }
    }

    fun closeContextMenu() {
        if (_state.value.contextMenu != null) {
            _state.value = _state.value.copy(contextMenu = null)
        }
    }

    fun openUBlockDashboard() {
        _state.value = _state.value.copy(
            extensionsVisible = false,
            menuPopupVisible = false,
            settingsVisible = false,
        )
        dev.clearbrowser.core.engine.ClearGeckoRuntime.getUBlockDashboardUrl { url ->
            val target = url ?: dev.clearbrowser.core.engine.ClearGeckoRuntime.uBlockOptionsUrl
            if (!target.isNullOrBlank()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    navigate(target)
                }
            } else {
                android.util.Log.w("ReadingViewModel", "uBlock dashboard URL not resolved yet")
            }
        }
    }

    fun openExtensionStore() {
        _state.value = _state.value.copy(
            extensionsVisible = false,
            menuPopupVisible = false,
            settingsVisible = false,
        )
        navigate(FIREFOX_EXTENSION_STORE_URL)
    }

    fun openExtensionPopup(extensionId: String) {
        android.util.Log.i("ClearBrowser", "openExtensionPopup: ext=$extensionId")
        _state.value = _state.value.copy(
            extensionsVisible = false,
            menuPopupVisible = false,
        )
        val engine = ensureActiveEngine()
        android.util.Log.i("ClearBrowser", "openExtensionPopup: engine=$engine")
        engine?.triggerExtensionAction(extensionId)
    }

    fun closeExtensionPopup() {
        _state.value = _state.value.copy(extensionPopup = null)
    }

    fun openExtensionSettings(url: String) {
        _state.value = _state.value.copy(
            extensionsVisible = false,
            menuPopupVisible = false,
            settingsVisible = false,
        )
        if (url.isNotBlank()) {
            navigate(url)
        }
    }

    fun uninstallExtension(extensionId: String) {
        dev.clearbrowser.core.engine.ClearGeckoRuntime.uninstallExtension(extensionId)
    }

    fun setOverlay(overlaid: Boolean) {
        if (overlaid) openMenuPopup() else closeMenuPopup()
    }

    // --------------------------------------------------------- EngineListener

    /**
     * One listener per tab.
     *
     * The ViewModel itself used to be the listener, which was fine while there
     * was one engine. With tabs it would be a bug: a background tab finishing a
     * load would overwrite the address bar and the back button of the tab the
     * user is actually looking at. So each listener knows its tab, and every
     * callback that touches the *chrome* is filtered by whether that tab is the
     * active one. The ones that touch the tab list are not filtered -- a
     * background tab's title still belongs in the switcher.
     */
    private inner class TabListener(
        private val tabId: Long,
        /**
         * The profile this tab was created in, captured rather than read from
         * [engineProfile] on each callback.
         *
         * Switching profiles destroys the engines, but a destroyed engine's
         * listener can still deliver a queued callback for a moment. Reading the
         * *current* profile then would be the one case where a private page is
         * written to the normal history: the profile has already flipped while
         * the private tab's event is still in flight.
         */
        private val incognito: Boolean,
    ) : EngineListener {

        private val isActive: Boolean get() = strip.activeId == tabId

        /**
         * The page this tab has loaded, as recorded for history.
         *
         * URL and title arrive on separate callbacks, and either can come first,
         * so the visit is only written once both are known -- or when the next
         * navigation is about to replace them. Recording on each callback would
         * turn every page into two entries, and recording the title-less one
         * first would leave history with rows that identify a page only by host.
         */
        private var visitedUrl: String? = null
        private var visitedTitle: String = ""
        /** True once the load for [visitedUrl] finished, title or not. */
        private var visitCompleted = false
        /** Last URL successfully loaded or committed in this tab. */
        private var lastCommittedUrl: String = ""

        /**
         * Start recording a visit to [url].
         *
         * The start page and non-web schemes never begin a visit: recording them
         * would fill history with rows that cannot be reopened, and *show* as
         * history with nothing behind them.
         */
        private fun beginVisit(url: String) {
            val normalized = dev.clearbrowser.core.data.normalizeWebUrl(url)
            if (normalized == null) {
                visitedUrl = null
                visitedTitle = ""
                visitCompleted = false
                return
            }
            visitedUrl = normalized
            visitedTitle = ""
            visitCompleted = false
        }

        /**
         * Write the visit in progress, if any.
         *
         * The entry is written at most once: the fields are cleared here, so a
         * second call without a new visit does nothing rather than recording the
         * same page again.
         *
         * A title-less visit is dropped unless [allowUntitled] is set. Redirect
         * chains announce every hop as a URL change, and writing those would put
         * pages the user never saw -- `http://` to `https://`, consent
         * interstitials, tracking redirects -- at the top of history twice per
         * link followed. A visit that genuinely loaded and has no title at all
         * is still recorded, because [visitCompleted] says it arrived.
         */
        private fun flushVisit(allowUntitled: Boolean = false) {
            val url = visitedUrl ?: return
            val title = visitedTitle
            visitedUrl = null
            visitedTitle = ""
            visitCompleted = false
            if (title.isBlank() && !allowUntitled) return
            recordHistory(url, title, incognito)
        }

        override fun onLoadStateChanged(state: LoadState) {
            // Recorded for every tab, not only the visible one: a background tab
            // that finishes loading while the user reads elsewhere is still a
            // page they opened, and it would otherwise fall out of history
            // because no second callback arrives when it is activated.
            //
            // The write waits a moment because the title callback can arrive
            // *after* the load finishes. Flushing immediately would record the
            // page without its name, and the later title would have nothing left
            // to attach to. A title arriving first flushes this visit itself, so
            // the delayed call finds nothing to do.
            if (state == LoadState.COMPLETE) {
                visitCompleted = true
                val url = visitedUrl
                if (url != null) {
                    viewModelScope.launch {
                        delay(TITLE_GRACE_MS)
                        if (visitedUrl == url) flushVisit(allowUntitled = true)
                    }
                }
            }
            if (!isActive) return
            _state.value = _state.value.copy(loadState = state)
            if (state == LoadState.COMPLETE) progressFlow.value = 0
        }

        override fun onProgressChanged(progress: Int) {
            if (!isActive) return
            // Kept out of the state object that drives the theme, so a progress
            // tick cannot invalidate the colour scheme and the scrim.
            progressFlow.value = progress.coerceIn(0, 100)
        }

        override fun onUrlChanged(url: String) {
            val existingUrl = strip.tabs.find { it.id == tabId }?.url.orEmpty()
            if (lastCommittedUrl.isBlank() && existingUrl.isNotBlank() && existingUrl != url) {
                lastCommittedUrl = existingUrl
            }
            strip.update(tabId) { it.copy(url = url) }
            publishTabs()
            // A new URL means the previous page is finished, so flush it before
            // the next one starts. This runs for every tab, not only the visible
            // one, or a background navigation would never enter history.
            // Title-less hops are redirects and are dropped; see [flushVisit].
            flushVisit(allowUntitled = visitCompleted)
            beginVisit(url)
            if (!isActive) return
            reportUrl(url)
            beginNavigation(url)
            refreshBookmarked()
        }

        override fun onTitleChanged(title: String) {
            strip.update(tabId) { it.copy(title = title) }
            publishTabs()
            // The title is the last half of the visit: once it lands, the entry
            // is complete and is written. A later title change (a single-page
            // app renaming the document) starts no new visit and so writes
            // nothing, which is right: it is the same page.
            //
            // A blank title is not the last half of anything: some documents
            // report one before their metadata is parsed, and flushing it would
            // make the real title that follows arrive too late. The load's
            // completion is the fallback for pages that never have a title.
            if (visitedUrl != null && title.isNotBlank()) {
                visitedTitle = title
                flushVisit(allowUntitled = true)
            }
            if (!isActive) return
            _state.value = _state.value.copy(title = title)
        }

        override fun onSecurityStateChanged(secure: Boolean) {
            if (!isActive) return
            _state.value = _state.value.copy(secure = secure)
        }

        override fun onScrollChanged(
            scrollY: Int,
            canScrollUp: Boolean,
            canScrollDown: Boolean,
            userTouchActive: Boolean,
        ) {
            if (!isActive) return
            onActiveScroll(tabId, scrollY, canScrollUp, canScrollDown, userTouchActive)
        }

        override fun onPageCommitted(url: String) {
            lastCommittedUrl = url
            if (!isActive) return
            // Deferred: the view may not be laid out at its final size yet.
            viewModelScope.launch {
                delay(SAMPLE_DELAY_MS)
                tryApplyPendingScroll(tabId)
            }
        }

        override fun onExternalDownloadStarted(url: String) {
            // A download response does not render HTML into the tab. Clear any pending
            // history entry for this file URL so it does not pollute the user's history.
            visitedUrl = null
            visitedTitle = ""
            visitCompleted = false

            val previousUrl = lastCommittedUrl
            if (previousUrl.isNotBlank() && previousUrl != url) {
                // Revert tab URL to the last valid page the user actually saw,
                // preventing session restore from auto-reloading this download on restart.
                strip.update(tabId) { it.copy(url = previousUrl) }
                publishTabs()
                if (isActive) {
                    reportUrl(previousUrl)
                    _state.value = _state.value.copy(
                        url = previousUrl,
                        loadState = LoadState.COMPLETE,
                    )
                    progressFlow.value = 0
                }
            } else {
                // This tab was newly opened purely to trigger this download (e.g. window.open / target=_blank).
                if (strip.tabs.size > 1) {
                    closeTab(tabId)
                } else {
                    // Only tab: reset back to empty start page
                    strip.update(tabId) { it.copy(url = "", title = "") }
                    publishTabs()
                    if (isActive) {
                        reportUrl("")
                        _state.value = _state.value.copy(
                            url = "",
                            title = "",
                            loadState = LoadState.COMPLETE,
                        )
                        progressFlow.value = 0
                    }
                }
            }
        }

        override fun onRenderProcessGone() {
            if (!isActive) return
            // The renderer is gone but our process survived. A crashed tab
            // should cost the user that tab's page, not the whole browser.
            engines[tabId]?.setPageBackgroundColor(neutralFallback())
        }

        /**
         * A `target=_blank` link or `window.open`.
         *
         * Opened in a new tab and focused, which is what the page asked for and
         * what every other browser does. It used to replace the current page,
         * which loses the article the user was reading when a link opens a
         * helper window.
         */
        override fun onNewWindowRequested(url: String) {
            newTab(url)
        }

        override fun onJsDialog(dialog: dev.clearbrowser.core.engine.JsDialog) {
            // Only the visible tab may raise a dialog: a prompt from a page the
            // user cannot see is unanswerable in every sense.
            if (!isActive) {
                dialog.onDismiss()
                return
            }
            _state.value = _state.value.copy(jsDialog = dialog)
        }

        override fun onTopBarColorSampled(color: Int, isLight: Boolean) {
            if (!isActive) return
            _state.value = _state.value.copy(topBarColor = color, isTopBarLight = isLight)
        }

        override fun onBackdropBlurSampled(bitmap: androidx.compose.ui.graphics.ImageBitmap) {
            if (!isActive) return
            _state.value = _state.value.copy(
                backdropBlur = bitmap,
                backdropBlurVersion = _state.value.backdropBlurVersion + 1L,
            )
        }

        override fun onFullScreenChanged(fullScreen: Boolean) {
            if (!isActive) return
            _state.value = _state.value.copy(isFullscreen = fullScreen)
        }

        override fun onFindResult(activeMatch: Int, totalMatches: Int) {
            if (!isActive) return
            _state.value = _state.value.copy(
                findActiveMatch = activeMatch,
                findTotalMatches = totalMatches,
            )
        }

        override fun onContextMenu(info: dev.clearbrowser.core.engine.ContextMenuInfo) {
            if (!isActive) return
            _state.value = _state.value.copy(contextMenu = info)
        }

        override fun onExtensionPopupRequested(extensionId: String, title: String, session: Any) {
            android.util.Log.i("ClearBrowser", "onExtensionPopupRequested: ext=$extensionId, title=$title, isActive=$isActive")
            if (!isActive) return
            _state.value = _state.value.copy(
                extensionPopup = ExtensionPopupState(
                    extensionId = extensionId,
                    title = title,
                    session = session as org.mozilla.geckoview.GeckoSession,
                ),
                extensionsVisible = false,
                menuPopupVisible = false,
            )
        }

        override fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
            if (!isActive) return
            _state.value = _state.value.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
            )
        }
    }

    private fun onActiveScroll(
        tabId: Long,
        scrollY: Int,
        canScrollUp: Boolean,
        canScrollDown: Boolean,
        userTouchActive: Boolean,
    ) {
        lastHeight[tabId] = max(lastHeight[tabId] ?: 0, scrollY + VIEWPORT_ESTIMATE_PX)
        if ((pendingScroll[tabId] ?: 0) > 0) tryApplyPendingScroll(tabId)

        val atTop = scrollY <= AT_TOP_SLOP_PX
        if (atTop != _state.value.scrollAtTop) {
            _state.value = _state.value.copy(scrollAtTop = atTop)
        }

        // At or near the top, chrome should always be visible.
        if (atTop) {
            revealChrome()
        }

        if (!userTouchActive) {
            scrollIntent.reset()
            return
        }

        when (scrollIntent.onScroll(scrollY, canScrollUp, canScrollDown)) {
            ScrollIntentDetector.Direction.DOWN -> hideForReading()
            ScrollIntentDetector.Direction.UP -> revealChrome()
            ScrollIntentDetector.Direction.NONE -> Unit
        }
    }

    /**
     * The address shown in the omnibox, throttled.
     *
     * ## Why the URL does not go straight into state
     *
     * The WebView reports the URL on every `doUpdateVisitedHistory`, which
     * includes every history entry a `pushState` call creates. A single-page app
     * can fire dozens of these while the user reads, and each one that reaches
     * [ReadingUiState] is a full-screen recomposition: the theme, the chrome, the
     * scrim, the reveal handle. The user's only visible symptom is that scrolling
     * feels heavy and the address bar sometimes lags behind the page.
     *
     * Nothing is lost by coalescing. A URL that is 150ms stale is still the URL
     * the user is looking at, and the throttle below also skips the write
     * entirely when the URL has not actually changed, which is the common case
     * for these callbacks.
     */
    private var pendingUrl: String? = null
    private var urlFlushScheduled = false

    private fun reportUrl(url: String) {
        if (url == _state.value.url) return
        pendingUrl = url
        if (urlFlushScheduled) return
        urlFlushScheduled = true
        viewModelScope.launch {
            delay(URL_COALESCE_MS)
            urlFlushScheduled = false
            val latest = pendingUrl ?: return@launch
            pendingUrl = null
            val activeEngine = engineForActive()
            val engineCanBack = activeEngine?.canGoBack() == true
            val engineCanForward = activeEngine?.canGoForward() == true
            if (latest == _state.value.url) {
                _state.value = _state.value.copy(
                    canGoBack = engineCanBack || _state.value.canGoBack,
                    canGoForward = engineCanForward || _state.value.canGoForward,
                )
                return@launch
            }
            _state.value = _state.value.copy(
                url = latest,
                canGoBack = engineCanBack || _state.value.canGoBack,
                canGoForward = engineCanForward || _state.value.canGoForward,
            )
        }
    }

    private fun tryApplyPendingScroll(tabId: Long) {
        val target = pendingScroll[tabId] ?: 0
        if (target <= 0) return
        // Wait until the document is at least as tall as the offset we want,
        // otherwise the scroll silently clamps to the current bottom.
        val height = lastHeight[tabId] ?: 0
        if (height in 1 until target) return
        val engine = engines[tabId] ?: return
        engine.scrollTo(target)
        if (engine.currentScrollY() >= target - SCROLL_STICK_SLOP) {
            pendingScroll.remove(tabId)
        }
    }

    fun dismissJsDialog() {
        _state.value = _state.value.copy(jsDialog = null)
    }

    // ------------------------------------------------------------- chrome mode

    private fun beginNavigation(url: String) {
        // Re-aim the scroll detector at the new document. Without this a fling
        // that was in flight across a navigation could be credited to the page
        // the user just landed on.
        scrollIntent.reset()
        if (url.isBlank() || url == ABOUT_BLANK) {
            _state.value = _state.value.copy(topBarColor = null)
        }
    }

    private fun hideForReading() {
        if (!_state.value.settings.autoHideChrome) return
        val mode = _state.value.chromeMode
        // Never yank the chrome out from under something being typed into or
        // read in a sheet.
        if (mode == ChromeMode.EXPANDED || mode == ChromeMode.OVERLAID) return
        if (_state.value.tabsVisible) return
        // A sheet covers the page, so whatever is scrolling is not something the
        // user is reading; hiding the chrome behind it would only mean the
        // controls are gone when the sheet closes.
        if (_state.value.libraryTab != null) return
        // Do not hide chrome while reading at or near the top of the page.
        if (_state.value.scrollAtTop) return
        _state.value = _state.value.copy(
            chromeMode = ChromeMode.READING,
            temporaryHandle = false,
        )
    }

    fun revealChrome() {
        if (_state.value.chromeMode == ChromeMode.READING || _state.value.temporaryHandle) {
            _state.value = _state.value.copy(
                chromeMode = ChromeMode.SUMMONED,
                temporaryHandle = false,
            )
            engineForActive()?.sampleChromeBackdrop()
        }
    }

    // ------------------------------------------------------------------ state

    /**
     * Republish the active tab's page details as the chrome's state, including
     * whether the page on screen is bookmarked.
     *
     * The bookmark answer is recomputed here rather than cached on its own,
     * because navigation is the only thing that changes which page is on screen
     * and this is the single place that learns about it.
     */
    private fun pushActiveTab() {
        val tab = strip.active ?: return
        val engine = engines[tab.id]
        val isBlank = tab.url.isBlank() || tab.url == ABOUT_BLANK
        _state.value = _state.value.copy(
            url = tab.url,
            title = tab.title,
            loadState = LoadState.IDLE,
            secure = true,
            canGoBack = engine?.canGoBack() ?: false,
            canGoForward = engine?.canGoForward() ?: false,
            scrollAtTop = tab.scrollY <= AT_TOP_SLOP_PX,
            jsDialog = null,
            tabs = strip.summaries(),
            activeTabId = tab.id,
            topBarColor = if (isBlank) null else _state.value.topBarColor,
            backdropBlur = if (isBlank) null else _state.value.backdropBlur,
            isFullscreen = false,
            findInPageVisible = false,
        )
        if (!isBlank) {
            engine?.sampleChromeBackdrop()
        }
        refreshBookmarked()
    }

    private fun publishTabs() {
        val summaries = strip.summaries()
        val openHosts = summaries.mapNotNull { extractFaviconHost(it.url) }.toSet()
        val retainedFavicons = _state.value.favicons.filterKeys { it in openHosts }
        _state.value = _state.value.copy(
            tabs = summaries,
            activeTabId = strip.activeId,
            favicons = retainedFavicons,
        )
        loadFaviconsForTabs(summaries)
    }

    private fun loadFaviconsForTabs(tabs: List<Tab>) {
        val store = faviconStore ?: return
        viewModelScope.launch {
            val isPrivate = _state.value.incognito
            for (tab in tabs) {
                val host = extractFaviconHost(tab.url) ?: continue
                if (_state.value.favicons.containsKey(host)) continue
                val bmp = store.loadFavicon(tab.url, isPrivate = isPrivate)
                if (bmp != null) {
                    _state.value = _state.value.copy(
                        favicons = _state.value.favicons + (host to bmp),
                    )
                }
            }
        }
    }

    /** Called after restoring, when the state has to include the tab list. */
    private fun publishSession() = publishTabs()

    // -------------------------------------------------------------- bookmarks

    /**
     * The page the star acts on, when there is one. See
     * [ReadingUiState.canBookmark].
     */
    private val canBookmark: Boolean
        get() = _state.value.canBookmark

    /**
     * Record one finished page in history, unless it belongs to a private window.
     *
     * The profile gate is the same one session persistence uses, for the same
     * reason: history on disk is a durable record of what was read, and a
     * private window's promise is that nothing is kept. A private *bookmark*
     * would be the identical leak, so [toggleBookmark] refuses there too.
     *
     * [incognito] comes from the tab's listener rather than from the current
     * profile, so a callback arriving just after a profile switch cannot carry a
     * private page into the normal history.
     */
    private fun recordHistory(url: String, title: String, incognito: Boolean) {
        if (incognito) return
        historyStore?.record(url, title)
    }

    /** Whether the page on screen is in the bookmark list. */
    private fun refreshBookmarked() {
        val current = _state.value
        // A private window can never save, and showing a filled star for a page
        // that was bookmarked in a normal window would claim otherwise.
        val bookmarked = if (!current.isStartPage && !current.incognito) {
            // Asked of the store, which owns the one definition of "same page"
            // (normalised URL, no fragment). Comparing here as well would be a
            // second copy of that rule, free to drift.
            bookmarkStore?.isBookmarked(current.url) == true
        } else {
            false
        }
        if (bookmarked != current.isBookmarked) {
            _state.value = current.copy(isBookmarked = bookmarked)
        }
    }

    /**
     * Save the page on screen, or remove it when it is already saved.
     *
     * The menu stays open: the star is a state the user may want to change their
     * mind about in the same visit, and closing the sheet on a toggle would make
     * a second tap a navigation rather than an un-toggle.
     */
    fun toggleBookmark() {
        if (!canBookmark) return
        val current = _state.value
        val url = current.url
        bookmarkStore?.toggle(url, current.title)
        refreshBookmarked()
    }

    fun openLibrary(tab: LibraryTab = LibraryTab.BOOKMARKS) {
        _state.value = _state.value.copy(
            libraryTab = tab,
            chromeMode = ChromeMode.SUMMONED,
            tabsVisible = false,
            menuPopupVisible = false,
            settingsVisible = false,
            extensionsVisible = false,
        )
        if (tab == LibraryTab.DOWNLOADS) {
            refreshDownloads()
        }
    }

    fun closeLibrary() {
        if (_state.value.libraryTab != null) {
            _state.value = _state.value.copy(libraryTab = null)
        }
    }

    fun selectLibraryTab(tab: LibraryTab) {
        _state.value = _state.value.copy(libraryTab = tab)
        if (tab == LibraryTab.DOWNLOADS) {
            refreshDownloads()
        }
    }

    fun refreshDownloads() {
        viewModelScope.launch {
            downloadsStore?.refresh()
        }
    }

    fun removeDownload(id: Long) {
        viewModelScope.launch {
            downloadsStore?.remove(id)
        }
    }

    /** Open a saved page in a new tab and close the sheet. */
    fun openLibraryEntry(url: String) {
        closeLibrary()
        newTab(url)
    }

    fun removeBookmark(url: String) {
        bookmarkStore?.remove(url)
        refreshBookmarked()
    }

    fun removeHistoryEntry(url: String) {
        historyStore?.remove(url)
    }

    fun clearHistory() {
        historyStore?.clear()
    }

    fun exportBookmarksHtml(): String = bookmarkStore?.exportHtml().orEmpty()

    fun importBookmarks(content: String): Int {
        val count = bookmarkStore?.importBookmarks(content) ?: 0
        if (count > 0) {
            _state.value = _state.value.copy(importedBookmarksCount = count)
        }
        return count
    }

    fun revokeSitePermission(origin: String, type: String) {
        sitePermissionStore?.revoke(origin, type)
        ClearGeckoRuntime.revokePermissionFromHost(origin)
    }

    fun revokeSitePermissionsForOrigin(origin: String) {
        sitePermissionStore?.revokeOrigin(origin)
        ClearGeckoRuntime.revokePermissionFromHost(origin)
    }

    fun clearAllSitePermissions() {
        sitePermissionStore?.clearAll()
        ClearGeckoRuntime.clearAllPermissions()
    }

    /** The neutral surface for this scheme. Never black. */
    private fun neutralFallback(): Int = if (isSystemDark) 0xFF141218.toInt() else 0xFFF7F2FA.toInt()

    override fun onCleared() {
        destroyAllEngines()
        super.onCleared()
    }

    /**
     * Deliberately does not take the Activity or the host.
     *
     * They are supplied to [attach] instead, so the ViewModel only ever holds
     * references to an Activity it was actually given, and can drop them when
     * that host goes away. Passing them through the constructor would pin the
     * first Activity for the ViewModel's whole life, including across a
     * configuration change.
     */
    class Factory(
        private val settingsStore: SettingsStore,
        private val sessionStore: SessionStore,
        private val historyStore: HistoryStore,
        private val bookmarkStore: BookmarkStore,
        private val downloadsStore: dev.clearbrowser.core.data.DownloadsStore? = null,
        private val sitePermissionStore: dev.clearbrowser.core.data.SitePermissionStore? = null,
        private val faviconStore: FaviconStore? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReadingViewModel(
                null,
                settingsStore,
                sessionStore,
                null,
                historyStore,
                bookmarkStore,
                downloadsStore,
                sitePermissionStore = sitePermissionStore,
                faviconStore = faviconStore,
            ) as T
    }

    private companion object {
        const val FIREFOX_EXTENSION_STORE_URL = "https://addons.mozilla.org/android/"
        /**
         * The homepage this browser used to open, kept only so a session file
         * that still names it can be recognised and discarded. See
         * [isRetiredStartPage].
         */
        const val RETIRED_START_URL = "https://example.com"

        /**
         * Tolerance for accepting a restored scroll as landed. Exact equality is
         * too strict: the page settles a pixel or two short of the target.
         */
        const val SCROLL_STICK_SLOP = 8

        /** Rough viewport height, so a bottom-of-page scroll is not blocked. */
        const val VIEWPORT_ESTIMATE_PX = 1600
        const val SAMPLE_DELAY_MS = 120L

        /**
         * How long a finished load waits for its title before history is written
         * without one.
         *
         * `onPageFinished` and `onReceivedTitle` have no promised order. Long
         * enough that the usual late title wins, short enough that history is on
         * disk before the user has moved on to the next page.
         */
        const val TITLE_GRACE_MS = 300L

        /**
         * How long URL reports are coalesced.
         *
         * Long enough to swallow a burst of `pushState` calls from a single-page
         * app, short enough that the address bar still feels attached to the
         * page. Below roughly a frame the throttle saves nothing; well above it
         * the bar visibly lags behind a real navigation.
         */
        const val URL_COALESCE_MS = 150L

        /**
         * How close to the top still counts as "at the top".
         *
         * Small, so the inset does not flicker on a sub-pixel overscroll or a
         * trackpad's first few pixels, and large enough that rubber-banding at
         * the top does not release the inset and then reapply it.
         */
        const val AT_TOP_SLOP_PX = 24

        /**
         * Whether a restored URL is the homepage this browser no longer has.
         *
         * Releases before the placeholder homepage was removed wrote
         * `example.com` into the session file on every stop, so an upgrading
         * install would open it once more and then appear to be stuck with it.
         * One test against a fixed string is enough to retire the record; the
         * alternative is a versioned migration for a single constant.
         */
        fun isRetiredStartPage(url: String): Boolean =
            url.trimEnd('/').equals(RETIRED_START_URL, ignoreCase = true)
    }
}
