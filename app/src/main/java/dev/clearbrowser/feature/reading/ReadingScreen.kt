package dev.clearbrowser.feature.reading

import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.clearbrowser.JsDialogHost
import dev.clearbrowser.R
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.engine.LoadState
import dev.clearbrowser.core.engine.WebContainer
import dev.clearbrowser.core.theme.ClearBrowserTheme
import dev.clearbrowser.core.theme.chromeFadeSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The reading surface: web content edge to edge, with chrome that leaves.
 *
 * `ClearBrowserTheme` wraps the whole screen rather than just the chrome, which
 * is intentional. The sampled page colour should also reach sheets and menus,
 * so a menu feels like it belongs to the page you are on rather than to the app
 * you happen to be inside.
 */
@Composable
fun ReadingScreen(
    state: ReadingUiState,
    bookmarks: List<dev.clearbrowser.core.data.Bookmark>,
    history: List<dev.clearbrowser.core.data.HistoryEntry>,
    downloads: List<dev.clearbrowser.core.data.DownloadItem> = emptyList(),
    installedExtensions: List<dev.clearbrowser.core.engine.ExtensionInfo> = emptyList(),
    sitePermissions: List<dev.clearbrowser.core.data.SitePermission> = emptyList(),
    attachEngine: (ViewGroup) -> Unit,
    onSystemDarkChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNavigate: (String) -> Unit,
    onCollapseOmnibox: () -> Unit,
    onMenu: () -> Unit,
    onOpenTabs: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    onCloseTabs: () -> Unit,
    onToggleChrome: () -> Unit,
    onExpandOmnibox: () -> Unit,
    onChromeBounds: (android.graphics.RectF?) -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSelectLibraryTab: (LibraryTab) -> Unit,
    onOpenLibraryEntry: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onCloseLibrary: () -> Unit,
    onDesktopSiteChange: (Boolean) -> Unit,
    onChromePositionChange: (dev.clearbrowser.core.data.ChromePosition) -> Unit,
    onSearchEngineChange: (dev.clearbrowser.core.data.SearchEngine) -> Unit,
    onAutoHideChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    onIncognitoChange: (Boolean) -> Unit,
    onDismissJsDialog: () -> Unit,
    onOmniboxQueryChange: (String) -> Unit = {},
    onWebThemeChange: (dev.clearbrowser.core.data.WebTheme) -> Unit = {},
    onOpenFindInPage: () -> Unit = {},
    onCloseFindInPage: () -> Unit = {},
    onFindQueryChange: (String) -> Unit = {},
    onFindNext: () -> Unit = {},
    onFindPrevious: () -> Unit = {},
    onCloseMenuPopup: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenDownload: (dev.clearbrowser.core.data.DownloadItem) -> Unit = {},
    onRemoveDownload: (Long) -> Unit = {},
    onExportBookmarks: () -> Unit = {},
    onImportBookmarks: () -> Unit = {},
    onOpenExtensions: () -> Unit = {},
    onCloseExtensions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCloseSettings: () -> Unit = {},
    onClearBrowsingData: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onRevokeSitePermission: (String, String) -> Unit = { _, _ -> },
    onClearAllSitePermissions: () -> Unit = {},
    onTextZoomChange: (Int) -> Unit = {},
    onChromeThemeChange: (dev.clearbrowser.core.data.ChromeTheme) -> Unit = {},
    onHideHandleChange: (Boolean) -> Unit = {},
    onPullToRefreshChange: (Boolean) -> Unit = {},
    onSharePage: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onShareUrl: (String, String) -> Unit = { _, _ -> },
    onCopyUrl: (String) -> Unit = {},
    onCloseContextMenu: () -> Unit = {},
    onDownload: (String) -> Unit = {},
    onOpenUBlockDashboard: () -> Unit = {},
    onOpenExtensionStore: () -> Unit = {},
    onOpenExtensionPopup: (String) -> Unit = {},
    onOpenExtensionSettings: (String) -> Unit = {},
    onUninstallExtension: (String) -> Unit = {},
    onCloseExtensionPopup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { onSystemDarkChange(isDark) }

    // The status bar height, used to place web content below the status bar.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val coroutineScope = rememberCoroutineScope()
    val pullOffsetAnim = remember { Animatable(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasHapticTriggered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current
    var refreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val triggerThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val restingLoadingHeightPx = remember(density) { with(density) { 48.dp.toPx() } }

    val handlePullProgress: (Float, Float) -> Unit = { offsetPx, thresholdPx ->
        if (!isRefreshing) {
            coroutineScope.launch {
                pullOffsetAnim.snapTo(offsetPx)
            }
            if (offsetPx >= thresholdPx) {
                if (!hasHapticTriggered) {
                    hasHapticTriggered = true
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            } else {
                hasHapticTriggered = false
            }
        }
    }

    val handlePullRelease: (Boolean) -> Unit = { triggered ->
        hasHapticTriggered = false
        refreshJob?.cancel()
        if (triggered && !isRefreshing) {
            isRefreshing = true
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            refreshJob = coroutineScope.launch {
                pullOffsetAnim.animateTo(
                    targetValue = restingLoadingHeightPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
                val start = SystemClock.uptimeMillis()
                onReload()

                // Wait for the reload to start and complete, ensuring a minimum display
                // duration of 400ms and a 3.5s safety timeout so it never hangs.
                while (state.loadState == LoadState.LOADING || (SystemClock.uptimeMillis() - start < 400L)) {
                    delay(50L)
                    if (SystemClock.uptimeMillis() - start > 3500L) break
                }

                // Smoothly retract the canopy back to the status bar
                pullOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
                isRefreshing = false
            }
        } else if (!isRefreshing) {
            refreshJob = coroutineScope.launch {
                pullOffsetAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }
    }

    LaunchedEffect(state.activeTabId) {
        refreshJob?.cancel()
        isRefreshing = false
        pullOffsetAnim.snapTo(0f)
    }

    val currentPullOffset = pullOffsetAnim.value
    val pullOffsetDp = with(density) { currentPullOffset.toDp() }
    val canopyHeight = statusBarTop + pullOffsetDp

    ClearBrowserTheme(
        isDark = isDark,
        dynamicColor = state.settings.dynamicColor,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Web content starts directly below the status bar, placed safely out of the
            // phone's notch and status bar area (or full screen in HTML5 fullscreen video mode).
            // When pulling down to refresh, the content shifts down in tandem with the canopy.
            AndroidView(
                factory = { context ->
                    WebContainer(context).apply {
                        pullToRefreshEnabled = state.settings.pullToRefresh && !state.isStartPage && !state.isFullscreen
                        isAtTopProvider = { state.scrollAtTop && !isRefreshing }
                        onPullProgress = handlePullProgress
                        onPullRelease = handlePullRelease
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (state.isFullscreen) 0.dp else statusBarTop)
                    .offset { IntOffset(0, currentPullOffset.roundToInt()) }
                    .testTag("web-content"),
                update = { holder ->
                    attachEngine(holder)
                    holder.pullToRefreshEnabled = state.settings.pullToRefresh && !state.isStartPage && !state.isFullscreen
                    holder.isAtTopProvider = { state.scrollAtTop && !isRefreshing }
                    holder.onPullProgress = handlePullProgress
                    holder.onPullRelease = handlePullRelease
                },
            )

            // Top status bar strip & expanding pull-to-refresh canopy:
            // Fills the status bar area with the website's sampled top bar color, and expands
            // downward during a pull gesture with the tactile contrast-matched reload indicator.
            if (!state.isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canopyHeight)
                        .background(
                            if (!state.isStartPage && state.topBarColor != null) {
                                Color(state.topBarColor)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                        .align(Alignment.TopCenter),
                ) {
                    if (currentPullOffset > 4f) {
                        val contentColor = if (!state.isStartPage && state.topBarColor != null) {
                            if (state.isTopBarLight) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pullOffsetDp)
                                .align(Alignment.BottomCenter),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            alpha = (currentPullOffset / restingLoadingHeightPx).coerceIn(0f, 1f)
                                        },
                                    color = contentColor,
                                    strokeWidth = 2.5.dp,
                                )
                            } else {
                                val progress = (currentPullOffset / triggerThresholdPx).coerceIn(0f, 1f)
                                val rotation = progress * 180f
                                val scale = 0.6f + (progress * 0.4f)
                                val alpha = (progress * 1.5f).coerceIn(0.2f, 1f)

                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.chrome_reload),
                                    tint = contentColor.copy(alpha = alpha),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            rotationZ = rotation
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // No scrim over the top of the page any more. The engine insets its
            // content below the status bar, so the strip is the page's own flat
            // colour rather than a live page header, and the status bar icons are
            // chosen to contrast with it. A gradient on top of that would darken
            // a colour that is already correct and reintroduce the "band" the
            // inset exists to remove.

            // The start page is an overlay rather than a replacement, because
            // the WebView has to stay in the tree: detaching it would take the
            // engine's view out of the hierarchy it was handed, and this
            // composition would have to rebuild the whole engine to get it back.
            // Nothing is hidden by covering it -- the engine has no page loaded
            // at this point, which is exactly what makes this the start page.
            if (!state.isFullscreen) {
                AnimatedVisibility(
                    visible = state.isStartPage,
                    enter = fadeIn(chromeFadeSpec()),
                    exit = fadeOut(chromeFadeSpec()),
                ) {
                    StartPage()
                }
            }

            if (state.chromeMode == ChromeMode.EXPANDED && !state.isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapseOmnibox,
                        ),
                )
            }

            if (!state.isFullscreen) {
                ChromeSurface(
                    state = state,
                    onBack = onBack,
                    onReload = onReload,
                    onStop = onStop,
                    onMenu = onMenu,
                    onOpenTabs = onOpenTabs,
                    onExpandOmnibox = onExpandOmnibox,
                    onNavigate = onNavigate,
                    onCollapseOmnibox = onCollapseOmnibox,
                    onReveal = onToggleChrome,
                    onBoundsChanged = onChromeBounds,
                    onQueryChange = onOmniboxQueryChange,
                    onDismissMenuPopup = onCloseMenuPopup,
                    onBookmarks = onOpenBookmarks,
                    onHistory = onOpenHistory,
                    onDownloads = onOpenDownloads,
                    onExtensions = onOpenExtensions,
                    onFindInPage = onOpenFindInPage,
                    onSharePage = onSharePage,
                    onCopyLink = onCopyLink,
                    onSettings = onOpenSettings,
                    onToggleBookmark = onToggleBookmark,
                    // The bottom edge is the whole design: the thumb should not have
                    // to reach past the content to find the controls.
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (state.findInPageVisible && !state.isFullscreen) {
                FindInPageBar(
                    query = state.findQuery,
                    activeMatch = state.findActiveMatch,
                    totalMatches = state.findTotalMatches,
                    onQueryChange = onFindQueryChange,
                    onNext = onFindNext,
                    onPrevious = onFindPrevious,
                    onClose = onCloseFindInPage,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusBarTop),
                )
            }

            // Inside the theme on purpose. Composed outside it, a dialog would
            // silently fall back to the neutral palette and appear to belong to
            // a different app than the page behind it.
            state.jsDialog?.let { dialog ->
                JsDialogHost(
                    dialog = dialog,
                    onDismiss = onDismissJsDialog,
                )
            }

            // Closing the sheet is a state change, not a dismissal, because
            // `tabsVisible` also gates the auto-hide rules and the back gesture.
            // The sheet is only ever composed while that flag is set, so it
            // cannot reappear over a page once dismissed.
            if (state.tabsVisible) {
                TabsSheet(
                    tabs = state.tabs,
                    activeTabId = state.activeTabId,
                    onSelect = onSelectTab,
                    onClose = onCloseTab,
                    onNewTab = onNewTab,
                    onDismiss = onCloseTabs,
                    favicons = state.favicons,
                )
            }

            if (state.settingsVisible) {
                SettingsSheet(
                    state = state,
                    desktopSiteEnabled = state.desktopSite,
                    onDismiss = onCloseSettings,
                    onChromePositionChange = onChromePositionChange,
                    onSearchEngineChange = onSearchEngineChange,
                    onAutoHideChange = onAutoHideChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onDesktopSiteChange = onDesktopSiteChange,
                    onImmersiveChange = onImmersiveChange,
                    onIncognitoChange = onIncognitoChange,
                    onWebThemeChange = onWebThemeChange,
                    onTextZoomChange = onTextZoomChange,
                    onChromeThemeChange = onChromeThemeChange,
                    onHideHandleChange = onHideHandleChange,
                    onPullToRefreshChange = onPullToRefreshChange,
                    onClearBrowsingData = onClearBrowsingData,
                    sitePermissions = sitePermissions,
                    onRevokeSitePermission = onRevokeSitePermission,
                    onClearAllSitePermissions = onClearAllSitePermissions,
                )
            }

            if (state.extensionsVisible) {
                ExtensionsSheet(
                    extensions = installedExtensions,
                    onDismiss = onCloseExtensions,
                    onOpenPopup = onOpenExtensionPopup,
                    onOpenSettings = onOpenExtensionSettings,
                    onUninstall = onUninstallExtension,
                    onOpenExtensionStore = onOpenExtensionStore,
                )
            }

            state.extensionPopup?.let { popup ->
                ExtensionPopupSheet(
                    popup = popup,
                    onDismiss = onCloseExtensionPopup,
                )
            }

            state.contextMenu?.let { menuInfo ->
                ContextMenuSheet(
                    info = menuInfo,
                    onDismiss = onCloseContextMenu,
                    onOpenNewTab = { url ->
                        onNewTab()
                        onNavigate(url)
                    },
                    onCopyLink = { url ->
                        onCopyUrl(url)
                    },
                    onShareLink = { url ->
                        onShareUrl(url, menuInfo.title.orEmpty())
                    },
                    onDownload = onDownload,
                )
            }

            // Same rule as the switcher and the menu: the sheet is composed only
            // while its flag is set, so it cannot reappear over a page once the
            // user has moved on. The menu and the library are mutually exclusive:
            // [onOpenLibrary] closes the menu as it opens this one.
            state.libraryTab?.let { tab ->
                LibrarySheet(
                    tab = tab,
                    bookmarks = bookmarks,
                    history = history,
                    downloads = downloads,
                    onSelectTab = onSelectLibraryTab,
                    onOpen = onOpenLibraryEntry,
                    onRemoveBookmark = onRemoveBookmark,
                    onRemoveHistory = onRemoveHistory,
                    onClearHistory = onClearHistory,
                    onOpenDownload = onOpenDownload,
                    onRemoveDownload = onRemoveDownload,
                    onExportBookmarks = onExportBookmarks,
                    onImportBookmarks = onImportBookmarks,
                    onDismiss = onCloseLibrary,
                )
            }
        }
    }
}
