package dev.clearbrowser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.clearbrowser.core.data.BookmarkStore
import dev.clearbrowser.core.data.HistoryStore
import dev.clearbrowser.core.data.SessionStore
import dev.clearbrowser.core.data.SettingsStore
import dev.clearbrowser.feature.reading.ChromeMode
import dev.clearbrowser.feature.reading.ReadingScreen
import dev.clearbrowser.feature.reading.ReadingViewModel

class MainActivity : ComponentActivity() {

    private val app: ClearBrowserApp
        get() = application as ClearBrowserApp

    private val settings: SettingsStore
        get() = app.settings

    private val history: HistoryStore
        get() = app.history

    private val bookmarks: BookmarkStore
        get() = app.bookmarks

    override fun onCreate(savedInstanceState: Bundle?) {
        // The fast restore path. If the system killed us and kept this Bundle,
        // hand it straight to the ViewModel so the WebView can restore its
        // history and scroll natively. The durable file is the fallback for when
        // it did not survive.
        savedInstanceState?.getBundle(SessionStore.BUNDLE_KEY)?.let { state ->
            pendingRestoredState = state
        }

        // Must precede setContent. Makes the system bars transparent, which is
        // the whole point: the page, not a scrim, meets the screen edge.
        enableEdgeToEdge()
        // Our own chrome already sits on top of the navigation bar, so the
        // system's contrast scrim would double up into a visible band.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        setContent { Browser() }
    }

    /**
     * `singleTask` means a second link arrives here rather than in a new
     * Activity, so a new intent has to be picked up explicitly.
     */
    /**
     * Persist cookies on the way out.
     *
     * A browser process is a routine Android-kill target, and cookies live in
     * memory until flushed. Without this, backgrounding the app to check a
     * 2FA code can log you out of the thing you were logging into.
     */
    override fun onStop() {
        super.onStop()
        // `onStop` is the only callback that reliably runs before the process
        // can be killed, so the durable snapshot happens here. Cookies and the
        // session both go: a browser that loses the page on every app switch
        // is not a browser anyone keeps.
        vm?.persistSession()
    }

    /**
     * The ViewModel outlives this Activity across a configuration change, so the
     * Activity references are dropped here rather than being pinned until the
     * ViewModel clears. The session is persisted on the way out, which is what
     * lets the replacement Activity pick up exactly where this one left off.
     *
     * `isChangingConfigurations` separates "recreated, carry on" from "going
     * away". The browser needs both, but they mean opposite things for private
     * mode: a rotation continues the same private session, while a real finish
     * must not let a private window come back at all.
     */
    override fun onDestroy() {
        vm?.onHostDestroyed(finishing = !isChangingConfigurations)
        vm = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        vm?.saveWebViewState()?.let { outState.putBundle(SessionStore.BUNDLE_KEY, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.dataString ?: return
        val viewModel = vm ?: return
        // No deferral needed, and deliberately none: `withFrameNanos` requires a
        // MonotonicFrameClock, which only exists inside a composition, so calling
        // it from `lifecycleScope` throws. `setStartUrl` already handles the
        // engine-not-yet-attached case by holding the URL until attach.
        viewModel.setStartUrl(target)
    }

    private var vm: ReadingViewModel? = null

    /**
     * A WebView state Bundle handed over by the system, if any. Held until the
     * ViewModel exists, because the ViewModel is what owns the engine.
     */
    private var pendingRestoredState: android.os.Bundle? = null

    /**
     * Eagerly constructed, and deliberately not `by lazy`.
     *
     * `registerForActivityResult` throws if the LifecycleOwner is already
     * STARTED, and a lazy would first be touched from inside `setContent` --
     * long after `onStart`. Field initialisers run before `onCreate`, which is
     * the documented-safe window for registering launchers.
     */
    private val host: MainActivityHost = MainActivityHost(this)

    /**
     * Lazy, and not a field initialiser: `SessionStore` reads `filesDir`, and a
     * field initialiser runs before `attachBaseContext`, so the base context is
     * still null and it throws. The first touch is in `onCreate`, long after the
     * context is attached.
     */
    private val sessionStore: SessionStore by lazy { SessionStore(this) }

    @Composable
    private fun Browser() {
        val viewModel: ReadingViewModel = viewModel(
            factory = ReadingViewModel.Factory(settings, sessionStore, history, bookmarks),
        )
        // Assigned for onNewIntent and the lifecycle callbacks, which all
        // arrive outside composition.
        vm = viewModel
        pendingRestoredState?.let {
            viewModel.setSavedWebViewState(it)
            pendingRestoredState = null
        }

        val state by viewModel.state.collectAsStateWithLifecycle()
        val historyEntries by viewModel.history.collectAsStateWithLifecycle()
        val bookmarkEntries by viewModel.bookmarks.collectAsStateWithLifecycle()

        // Recorded before the engine attaches, so the very first load is the
        // requested page rather than a detour through the start page.
        val startUrl = intent?.dataString
        LaunchedEffect(Unit) { viewModel.setStartUrl(startUrl) }

        applyImmersive(state.settings.immersiveMode)

        // The status bar is transparent over the page, so its icons have to
        // contrast with whatever is behind them. With no page tinting left
        // there is nothing page-specific to react to, so the system scheme is
        // the answer -- and the app's own surface is a near match for it.
        applyStatusBarIcons(systemIsDark = isSystemInDarkTheme())

        // Back is a browsing gesture before it is an app gesture. Priority order
        // matters: dismiss what the user summoned, then walk history, and only
        // then leave. Getting this backwards -- exiting the browser while ten
        // pages of history sit behind you -- is the single most common way
        // Android browsers annoy people.
        BackHandler(enabled = true) {
            when {
                // The sheets are the first thing to go: a back gesture while a
                // sheet is open means "close this", never "leave the page".
                state.libraryTab != null -> viewModel.closeLibrary()
                state.tabsVisible -> viewModel.closeTabs()
                state.chromeMode == ChromeMode.EXPANDED -> viewModel.collapseOmnibox()
                state.chromeMode == ChromeMode.READING -> viewModel.toggleChrome()
                state.canGoBack -> viewModel.goBack()
                else -> finish()
            }
        }

        ReadingScreen(
            state = state,
            bookmarks = bookmarkEntries,
            history = historyEntries,
            attachEngine = { parent -> viewModel.attach(parent, this, host) },
            onSystemDarkChange = viewModel::setSystemDark,
            onBack = viewModel::goBack,
            onReload = viewModel::reloadOrStop,
            onStop = viewModel::reloadOrStop,
            onMenu = { viewModel.setOverlay(true) },
            onOpenTabs = viewModel::openTabs,
            onSelectTab = { id ->
                // Selecting is also dismissing: the user picked a page to look
                // at, so the switcher has done its job.
                viewModel.selectTab(id)
                viewModel.closeTabs()
            },
            onCloseTab = viewModel::closeTab,
            onNewTab = {
                viewModel.newTab()
                viewModel.closeTabs()
            },
            onCloseTabs = viewModel::closeTabs,
            onExpandOmnibox = viewModel::expandOmnibox,
            onChromeBounds = viewModel::reserveChromeBounds,
            onToggleChrome = viewModel::toggleChrome,
            onNavigate = viewModel::navigate,
            onCollapseOmnibox = viewModel::collapseOmnibox,
            onToggleBookmark = viewModel::toggleBookmark,
            onOpenLibrary = { viewModel.openLibrary() },
            onSelectLibraryTab = viewModel::selectLibraryTab,
            onOpenLibraryEntry = viewModel::openLibraryEntry,
            onRemoveBookmark = viewModel::removeBookmark,
            onRemoveHistory = viewModel::removeHistoryEntry,
            onClearHistory = viewModel::clearHistory,
            onCloseLibrary = viewModel::closeLibrary,
            onDesktopSiteChange = viewModel::setDesktopSite,
            onChromePositionChange = settings::setChromePosition,
            onSearchEngineChange = settings::setSearchEngine,
            onAutoHideChange = settings::setAutoHideChrome,
            onDynamicColorChange = settings::setDynamicColor,
            onImmersiveChange = { viewModel.setImmersive(it) },
            onIncognitoChange = viewModel::setIncognito,
            onDismissJsDialog = viewModel::dismissJsDialog,
        )
    }

    /**
     * Choose status bar icon colour to contrast with the system scheme.
     *
     * `isAppearanceLightStatusBars` means "what is behind the bar is light, so
     * draw dark icons". Cached so the window call only happens when the answer
     * actually changes.
     */
    private fun applyStatusBarIcons(systemIsDark: Boolean) {
        if (lastSystemIsDark == systemIsDark) return
        lastSystemIsDark = systemIsDark
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !systemIsDark
    }

    /** Null until the first pass, so the initial call is never skipped. */
    private var lastSystemIsDark: Boolean? = null

    /**
     * Immersive hides the system bars as well as our chrome, so a page is the
     * only thing on screen. Transient-by-swipe is mandatory: a way back has to
     * exist or the user is trapped.
     */
    private fun applyImmersive(immersive: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
