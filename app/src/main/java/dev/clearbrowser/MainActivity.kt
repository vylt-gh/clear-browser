package dev.clearbrowser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
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

    private val downloads: dev.clearbrowser.core.data.DownloadsStore by lazy {
        dev.clearbrowser.core.data.DownloadsStore(applicationContext)
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                vm?.refreshDownloads()
            }
        }
    }

    private fun openDownload(item: dev.clearbrowser.core.data.DownloadItem) {
        val uri = downloads.getUriForDownloadedFile(item.id)
            ?: item.localUri?.let { Uri.parse(it) }
        if (uri != null) {
            val mime = downloads.getMimeTypeForDownloadedFile(item.id)
                ?: item.mimeType
                ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                startActivity(Intent.createChooser(intent, item.title))
            }.onFailure {
                Toast.makeText(this, R.string.download_cannot_open, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.download_file_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePage(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            if (title.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, null))
    }

    private fun copyLink(url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.toast_link_copied, Toast.LENGTH_SHORT).show()
    }

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

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

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
        runCatching { unregisterReceiver(downloadReceiver) }
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
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val viewModel = vm
        val action = intent.action
        val command = intent.getStringExtra("command") ?: intent.getStringExtra("action")
        android.util.Log.i("ClearBrowser", "handleIntent: command=$command, action=$action, vm=$viewModel")
        if (viewModel == null) return

        when {
            command == "find" || action == "dev.clearbrowser.ACTION_FIND" -> {
                viewModel.openFindInPage()
                val q = intent.getStringExtra("query")
                if (!q.isNullOrBlank()) {
                    viewModel.setFindQuery(q)
                }
            }
            command == "find_next" || action == "dev.clearbrowser.ACTION_FIND_NEXT" -> {
                viewModel.findNext()
            }
            command == "find_prev" || action == "dev.clearbrowser.ACTION_FIND_PREV" -> {
                viewModel.findPrevious()
            }
            command == "find_close" || action == "dev.clearbrowser.ACTION_FIND_CLOSE" -> {
                viewModel.closeFindInPage()
            }
            command == "menu" || action == "dev.clearbrowser.ACTION_MENU" -> {
                viewModel.toggleMenuPopup()
            }
            command == "toggle_chrome" || command == "collapse" || action == "dev.clearbrowser.ACTION_TOGGLE_CHROME" -> {
                viewModel.toggleChrome()
            }
            command == "settings" || action == "dev.clearbrowser.ACTION_SETTINGS" -> {
                viewModel.openSettings()
            }
            command == "extensions" || action == "dev.clearbrowser.ACTION_EXTENSIONS" -> {
                viewModel.openExtensions()
            }
            command == "ubo" || command == "ublock" || command == "dashboard" || action == "dev.clearbrowser.ACTION_UBO" -> {
                viewModel.openUBlockDashboard()
            }
            command == "extension_popup" || command == "ubo_popup" || action == "dev.clearbrowser.ACTION_EXTENSION_POPUP" -> {
                viewModel.openExtensionPopup(dev.clearbrowser.core.engine.ClearGeckoRuntime.UBLOCK_ID)
            }
            command == "extension_store" || command == "amo" || action == "dev.clearbrowser.ACTION_EXTENSION_STORE" -> {
                viewModel.openExtensionStore()
            }
            command == "bookmarks" || action == "dev.clearbrowser.ACTION_BOOKMARKS" -> {
                viewModel.openLibrary(dev.clearbrowser.feature.reading.LibraryTab.BOOKMARKS)
            }
            command == "history" || action == "dev.clearbrowser.ACTION_HISTORY" -> {
                viewModel.openLibrary(dev.clearbrowser.feature.reading.LibraryTab.HISTORY)
            }
            command == "downloads" || action == "dev.clearbrowser.ACTION_DOWNLOADS" -> {
                viewModel.openLibrary(dev.clearbrowser.feature.reading.LibraryTab.DOWNLOADS)
            }
            command == "tabs" || action == "dev.clearbrowser.ACTION_TABS" -> {
                viewModel.openTabs()
            }
            command == "new_tab" || action == "dev.clearbrowser.ACTION_NEW_TAB" -> {
                viewModel.newTab(intent.getStringExtra("url").orEmpty())
            }
            command == "theme" || action == "dev.clearbrowser.ACTION_SET_THEME" -> {
                val themeStr = intent.getStringExtra("theme")?.uppercase()
                val theme = when (themeStr) {
                    "DARK" -> dev.clearbrowser.core.data.WebTheme.DARK
                    "LIGHT" -> dev.clearbrowser.core.data.WebTheme.LIGHT
                    else -> dev.clearbrowser.core.data.WebTheme.SYSTEM
                }
                settings.setWebTheme(theme)
            }
            command == "navigate" || action == "dev.clearbrowser.ACTION_NAVIGATE" -> {
                val url = intent.getStringExtra("url") ?: intent.dataString
                if (!url.isNullOrBlank()) viewModel.navigate(url)
            }
            command == "fullscreen" || action == "dev.clearbrowser.ACTION_FULLSCREEN" -> {
                val exit = intent.getBooleanExtra("exit", false)
                if (exit) viewModel.exitFullScreen()
            }
            command == "share" || action == "dev.clearbrowser.ACTION_SHARE" -> {
                val s = viewModel.state.value
                sharePage(s.url, s.title)
            }
            command == "copy_link" || action == "dev.clearbrowser.ACTION_COPY_LINK" -> {
                val s = viewModel.state.value
                copyLink(s.url)
            }
            command == "zoom" || action == "dev.clearbrowser.ACTION_SET_ZOOM" -> {
                val zoom = intent.getIntExtra("zoom", 100)
                viewModel.setTextZoom(zoom)
            }
            command == "clear_data" || action == "dev.clearbrowser.ACTION_CLEAR_DATA" -> {
                val clearHistory = intent.getBooleanExtra("history", true)
                val clearCookies = intent.getBooleanExtra("cookies", true)
                val clearCache = intent.getBooleanExtra("cache", true)
                viewModel.clearBrowsingData(clearHistory, clearCookies, clearCache)
            }
            command == "chrome_theme" || command == "theme_style" || action == "dev.clearbrowser.ACTION_CHROME_THEME" -> {
                val themeStr = intent.getStringExtra("theme")?.uppercase()
                val theme = when (themeStr) {
                    "ACRYLIC" -> dev.clearbrowser.core.data.ChromeTheme.ACRYLIC
                    "MATERIAL", "M3" -> dev.clearbrowser.core.data.ChromeTheme.MATERIAL
                    else -> dev.clearbrowser.core.data.ChromeTheme.CLEAR
                }
                settings.setChromeTheme(theme)
            }
            command == "position" || action == "dev.clearbrowser.ACTION_SET_POSITION" -> {
                val posStr = intent.getStringExtra("position")?.uppercase()
                val pos = when (posStr) {
                    "LEFT" -> dev.clearbrowser.core.data.ChromePosition.LEFT
                    "RIGHT" -> dev.clearbrowser.core.data.ChromePosition.RIGHT
                    else -> dev.clearbrowser.core.data.ChromePosition.CENTER
                }
                settings.setChromePosition(pos)
            }
            command == "hide_handle" || action == "dev.clearbrowser.ACTION_HIDE_HANDLE" -> {
                val enabled = intent.getBooleanExtra("enabled", true)
                settings.setHideHandle(enabled)
            }
            command == "m3_chrome" || command == "original_m3" || action == "dev.clearbrowser.ACTION_M3_CHROME" -> {
                val enabled = intent.getBooleanExtra("enabled", true)
                settings.setChromeTheme(if (enabled) dev.clearbrowser.core.data.ChromeTheme.MATERIAL else dev.clearbrowser.core.data.ChromeTheme.CLEAR)
            }
            intent.dataString != null -> {
                viewModel.navigate(intent.dataString!!)
            }
            intent.hasExtra("url") -> {
                intent.getStringExtra("url")?.let { viewModel.navigate(it) }
            }
        }
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
            factory = ReadingViewModel.Factory(settings, sessionStore, history, bookmarks, downloads),
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
        val downloadEntries by viewModel.downloads.collectAsStateWithLifecycle()
        val installedExtensions by viewModel.installedExtensions.collectAsStateWithLifecycle()

        // Recorded before the engine attaches, so the very first load is the
        // requested page rather than a detour through the start page.
        val startUrl = intent?.dataString
        LaunchedEffect(Unit) {
            if (!startUrl.isNullOrBlank()) {
                viewModel.setStartUrl(startUrl)
            }
            handleIntent(intent)
        }

        LaunchedEffect(state.isFullscreen, state.settings.immersiveMode) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (state.isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                applyImmersive(state.settings.immersiveMode)
            }
        }

        LaunchedEffect(state.settings.webTheme) {
            val scheme = when (state.settings.webTheme) {
                dev.clearbrowser.core.data.WebTheme.SYSTEM -> org.mozilla.geckoview.GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
                dev.clearbrowser.core.data.WebTheme.DARK -> org.mozilla.geckoview.GeckoRuntimeSettings.COLOR_SCHEME_DARK
                dev.clearbrowser.core.data.WebTheme.LIGHT -> org.mozilla.geckoview.GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            }
            dev.clearbrowser.core.engine.ClearGeckoRuntime.setPreferredColorScheme(scheme)
        }

        val isDark = isSystemInDarkTheme()
        LaunchedEffect(state.isTopBarLight, state.isStartPage, isDark, state.topBarColor) {
            val lightBackground = if (state.isStartPage || state.topBarColor == null) {
                !isDark
            } else {
                state.isTopBarLight
            }
            applyStatusBarIcons(isLight = lightBackground)
        }

        // Back is a browsing gesture before it is an app gesture. Priority order
        // matters: dismiss what the user summoned, then walk history, and only
        // then leave. Getting this backwards -- exiting the browser while ten
        // pages of history sit behind you -- is the single most common way
        // Android browsers annoy people.
        BackHandler(enabled = true) {
            val canBack = viewModel.canGoBack()
            when {
                // Fullscreen video / element is highest priority to exit on back press.
                state.isFullscreen -> viewModel.exitFullScreen()
                // Find in page is dismissed before sheet/history navigation.
                state.findInPageVisible -> viewModel.closeFindInPage()
                // Context menu
                state.contextMenu != null -> viewModel.closeContextMenu()
                // Popup menu from button
                state.menuPopupVisible -> viewModel.closeMenuPopup()
                // Dedicated sheets
                state.extensionPopup != null -> viewModel.closeExtensionPopup()
                state.settingsVisible -> viewModel.closeSettings()
                state.extensionsVisible -> viewModel.closeExtensions()
                // The sheets are the next thing to go: a back gesture while a
                // sheet is open means "close this", never "leave the page".
                state.libraryTab != null -> viewModel.closeLibrary()
                state.tabsVisible -> viewModel.closeTabs()
                state.chromeMode == ChromeMode.EXPANDED -> viewModel.collapseOmnibox()
                canBack && viewModel.goBack() -> Unit
                state.chromeMode == ChromeMode.READING -> viewModel.toggleChrome()
                else -> finish()
            }
        }

        ReadingScreen(
            state = state,
            bookmarks = bookmarkEntries,
            history = historyEntries,
            downloads = downloadEntries,
            installedExtensions = installedExtensions,
            attachEngine = { parent -> viewModel.attach(parent, this, host) },
            onSystemDarkChange = viewModel::setSystemDark,
            onBack = viewModel::goBack,
            onReload = viewModel::reloadOrStop,
            onStop = viewModel::reloadOrStop,
            onMenu = viewModel::toggleMenuPopup,
            onCloseMenuPopup = viewModel::closeMenuPopup,
            onOpenBookmarks = { viewModel.openLibrary(dev.clearbrowser.feature.reading.LibraryTab.BOOKMARKS) },
            onOpenHistory = { viewModel.openLibrary(dev.clearbrowser.feature.reading.LibraryTab.HISTORY) },
            onOpenDownloads = { viewModel.openLibrary(dev.clearbrowser.feature.reading.LibraryTab.DOWNLOADS) },
            onOpenDownload = ::openDownload,
            onRemoveDownload = viewModel::removeDownload,
            onOpenExtensions = viewModel::openExtensions,
            onCloseExtensions = viewModel::closeExtensions,
            onOpenExtensionPopup = viewModel::openExtensionPopup,
            onOpenExtensionSettings = viewModel::openExtensionSettings,
            onUninstallExtension = viewModel::uninstallExtension,
            onCloseExtensionPopup = viewModel::closeExtensionPopup,
            onOpenSettings = viewModel::openSettings,
            onCloseSettings = viewModel::closeSettings,
            onClearBrowsingData = { h, c, k ->
                viewModel.clearBrowsingData(h, c, k)
                Toast.makeText(this@MainActivity, R.string.toast_browsing_data_cleared, Toast.LENGTH_SHORT).show()
            },
            onTextZoomChange = viewModel::setTextZoom,
            onChromeThemeChange = settings::setChromeTheme,
            onSharePage = {
                val s = viewModel.state.value
                sharePage(s.url, s.title)
            },
            onCopyLink = {
                val s = viewModel.state.value
                copyLink(s.url)
            },
            onShareUrl = ::sharePage,
            onCopyUrl = ::copyLink,
            onCloseContextMenu = viewModel::closeContextMenu,
            onDownload = { url ->
                host.startDownload(
                    dev.clearbrowser.core.engine.DownloadRequest(
                        url = url,
                        userAgent = null,
                        contentDisposition = null,
                        mimeType = null,
                        contentLength = -1L,
                        suggestedFilename = dev.clearbrowser.core.engine.DownloadFilenameResolver.resolve(url, null, null),
                    ),
                )
            },
            onOpenUBlockDashboard = viewModel::openUBlockDashboard,
            onOpenExtensionStore = viewModel::openExtensionStore,
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
            onHideHandleChange = settings::setHideHandle,
            onDynamicColorChange = settings::setDynamicColor,
            onImmersiveChange = { viewModel.setImmersive(it) },
            onIncognitoChange = viewModel::setIncognito,
            onDismissJsDialog = viewModel::dismissJsDialog,
            onOmniboxQueryChange = viewModel::onOmniboxQueryChanged,
            onWebThemeChange = settings::setWebTheme,
            onOpenFindInPage = viewModel::openFindInPage,
            onCloseFindInPage = viewModel::closeFindInPage,
            onFindQueryChange = viewModel::setFindQuery,
            onFindNext = viewModel::findNext,
            onFindPrevious = viewModel::findPrevious,
        )
    }

    /**
     * Choose status bar icon colour to contrast with the system scheme.
     *
     * `isAppearanceLightStatusBars` means "what is behind the bar is light, so
     * draw dark icons". Cached so the window call only happens when the answer
     * actually changes.
     */
    private fun applyStatusBarIcons(isLight: Boolean) {
        if (lastIsLight == isLight) return
        lastIsLight = isLight
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = isLight
    }

    /** Null until the first pass, so the initial call is never skipped. */
    private var lastIsLight: Boolean? = null

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
