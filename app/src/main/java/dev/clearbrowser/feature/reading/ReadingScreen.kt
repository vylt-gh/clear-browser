package dev.clearbrowser.feature.reading

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.clearbrowser.JsDialogHost
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.engine.WebContainer
import dev.clearbrowser.core.theme.ClearBrowserTheme
import dev.clearbrowser.core.theme.chromeFadeSpec

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
    onStatusBarHeightChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    LaunchedEffect(isDark) { onSystemDarkChange(isDark) }

    val density = LocalDensity.current
    // The status bar height, used to inset the page while it is at the top.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusBarTopPx = with(density) { statusBarTop.roundToPx() }

    LaunchedEffect(statusBarTopPx) {
        onStatusBarHeightChange(statusBarTopPx)
    }

    val topInsetDp = with(density) { state.topInsetPx.toDp() }

    ClearBrowserTheme(
        isDark = isDark,
        dynamicColor = state.settings.dynamicColor,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Extruded top bar strip: extends the website's top row of pixels
            // seamlessly into the Android status bar area at the top of the page,
            // shortening as the user scrolls down so it feels like part of the document.
            if (!state.isStartPage && state.topInsetPx > 0) {
                state.topBarBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topInsetDp)
                            .align(Alignment.TopCenter),
                    )
                }
            }

            // Web content starts directly below the extruded strip at the top of the page,
            // and moves up smoothly as the user scrolls down.
            AndroidView(
                factory = { context -> WebContainer(context) },
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, state.topInsetPx) }
                    .testTag("web-content"),
                update = { holder -> attachEngine(holder) },
            )

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
            AnimatedVisibility(
                visible = state.isStartPage,
                enter = fadeIn(chromeFadeSpec()),
                exit = fadeOut(chromeFadeSpec()),
            ) {
                StartPage()
            }

            if (state.chromeMode == ChromeMode.EXPANDED) {
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
                // The bottom edge is the whole design: the thumb should not have
                // to reach past the content to find the controls.
                modifier = Modifier.align(Alignment.BottomCenter),
            )

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
                )
            }

            if (state.chromeMode == ChromeMode.OVERLAID) {
                ChromeMenuSheet(
                    state = state,
                    // Read from the state rather than a local `remember`, which
                    // a rotation would reset to false while the engines kept
                    // the desktop user agent.
                    desktopSiteEnabled = state.desktopSite,
                    onDismiss = onCollapseOmnibox,
                    onChromePositionChange = onChromePositionChange,
                    onSearchEngineChange = onSearchEngineChange,
                    onAutoHideChange = onAutoHideChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onDesktopSiteChange = onDesktopSiteChange,
                    onImmersiveChange = onImmersiveChange,
                    onIncognitoChange = onIncognitoChange,
                    canBookmark = state.canBookmark,
                    isBookmarked = state.isBookmarked,
                    onToggleBookmark = onToggleBookmark,
                    onOpenLibrary = onOpenLibrary,
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
                    onSelectTab = onSelectLibraryTab,
                    onOpen = onOpenLibraryEntry,
                    onRemoveBookmark = onRemoveBookmark,
                    onRemoveHistory = onRemoveHistory,
                    onClearHistory = onClearHistory,
                    onDismiss = onCloseLibrary,
                )
            }
        }
    }
}
