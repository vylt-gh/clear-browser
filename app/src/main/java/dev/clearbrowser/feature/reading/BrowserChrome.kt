package dev.clearbrowser.feature.reading

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.clearbrowser.R
import dev.clearbrowser.core.data.AppTheme
import dev.clearbrowser.core.data.BrowserSettings
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.data.ChromeTheme
import dev.clearbrowser.core.data.SearchSuggestion
import dev.clearbrowser.core.theme.chromeFadeSpec
import dev.clearbrowser.core.theme.chromeMorphSpring
import dev.clearbrowser.core.theme.lerpDp

internal fun resolveIsDark(appTheme: AppTheme, systemDark: Boolean): Boolean = when (appTheme) {
    AppTheme.SYSTEM -> systemDark
    AppTheme.DARK -> true
    AppTheme.LIGHT -> false
}

/**
 * The chrome, as one continuous surface.
 *
 * ## Why this is a single component and not two
 *
 * There used to be two: a full-width pill that slid away, and a small circle
 * that appeared somewhere else. They were independent, so hiding the chrome
 * looked like one thing leaving and another arriving -- two objects in two places,
 * which reads as a glitch no matter how good the easing is. The user has to
 * *learn* that the circle is the bar, and that is a tax on every single
 * interaction.
 *
 * Now there is one surface with two sizes. Collapsing stretches the bar down to
 * the circle's diameter and slides it into the circle's corner; expanding does
 * the reverse. Because it is literally the same view moving and resizing, the
 * relationship teaches itself, and the handle can never be somewhere the bar
 * was not.
 *
 * ## Why the collapsed state is a corner, not the centre
 *
 * The bar is where the URL lives, so it keeps the full width and stays centred.
 * The collapsed handle is the thing that sits over a page, so it is the thing
 * that moves out of the way. Docking the bar to dodge a page's floating
 * "contact support" bubble would mean truncating the address to `example....` to
 * uncover something the user was not using at that moment.
 *
 * ## Keyboard
 *
 * [imePadding] is what makes the expanded field usable. Without it the
 * omnibox stays where it is and the keyboard covers it, so the user is typing
 * into a field they cannot see. It is paired with [navigationBarsPadding]
 * because the IME inset collapses to zero when the keyboard is down, and the bar
 * would otherwise sit under the gesture bar.
 */
@Composable
fun ChromeSurface(
    state: ReadingUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onMenu: () -> Unit,
    onOpenTabs: () -> Unit,
    onExpandOmnibox: () -> Unit,
    onNavigate: (String) -> Unit,
    onCollapseOmnibox: () -> Unit,
    onReveal: () -> Unit,
    /**
     * Reports the chrome's occupied area in window coordinates so the engine can
     * stop the page from stealing touches from it. See `WebContainer`.
     */
    onBoundsChanged: (android.graphics.RectF?) -> Unit,
    onQueryChange: (String) -> Unit = {},
    onDismissMenuPopup: () -> Unit = {},
    onToggleReloadPopup: () -> Unit = {},
    onDismissReloadPopup: () -> Unit = {},
    onHardReload: () -> Unit = {},
    onEmptyCacheAndReload: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onHistory: () -> Unit = {},
    onDownloads: () -> Unit = {},
    onExtensions: () -> Unit = {},
    onFindInPage: () -> Unit = {},
    onSharePage: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onAddToHomeScreen: () -> Unit = {},
    onSettings: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onToggleChrome: () -> Unit = {},
    onHandlePositionRatioChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val collapsed = state.chromeMode == ChromeMode.READING
    val expanded = state.chromeMode == ChromeMode.EXPANDED
    val handlePresentWhenCollapsed = !state.settings.hideHandle || state.temporaryHandle

    // Remember whether a handle was visible when the chrome was collapsed, so that during
    // the reveal transition (progress < 1f) we know whether to expand from the handle's
    // exact position or to slide up from the middle.
    var hadHandleWhenCollapsed by remember { mutableStateOf(handlePresentWhenCollapsed) }
    if (collapsed) {
        hadHandleWhenCollapsed = handlePresentWhenCollapsed
    }

    val handleExisted = if (collapsed) handlePresentWhenCollapsed else hadHandleWhenCollapsed

    val initialText = remember(state.url, state.chromeMode) {
        if (state.isStartPage) "" else state.url
    }
    var omniboxText by remember(state.url, state.chromeMode) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }

    // One number drives the whole morph: 1 is the full bar, 0 is the handle.
    // Deriving width, offset and both fades from a single animated value is what
    // makes them move together instead of each running its own transition and
    // arriving at different times.
    val progress by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = chromeMorphSpring(),
        label = "chrome-surface",
    )

    val density = LocalDensity.current
    val bounds = remember { android.graphics.RectF() }

    // Track the animated width, not the target: the page should not be able to
    // be tapped through a bar that is on its way somewhere.
    LaunchedEffect(progress, handleExisted) {
        if (progress <= 0.01f && !handleExisted) onBoundsChanged(null)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = HANDLE_MARGIN, vertical = HANDLE_MARGIN),
    ) {
        val available = maxWidth
        val handleWidth = HANDLE_SIZE
        val expandedWidth = available

        val maxOffsetDp = (available - handleWidth).coerceAtLeast(0.dp)
        val maxOffsetPx = with(density) { maxOffsetDp.toPx() }

        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        val baseRatio = state.settings.handlePositionRatio.coerceIn(0f, 1f)
        val baseOffsetPx = maxOffsetPx * baseRatio
        val currentHandleOffsetPx = (baseOffsetPx + dragOffsetPx).coerceIn(0f, maxOffsetPx)

        // Where the handle sits, in the same coordinate space as the bar.
        // If there was no handle, it always appears/disappears to the center middle.
        val targetHandleOffset: Dp = if (!handleExisted) {
            maxOffsetDp / 2f
        } else {
            with(density) { currentHandleOffsetPx.toDp() }
        }

        val currentWidth = lerpDp(handleWidth, expandedWidth, progress)
        val currentOffset = lerpDp(targetHandleOffset, 0.dp, progress)

        val isDark = resolveIsDark(state.settings.appTheme, isSystemInDarkTheme())
        val chromeTheme = state.settings.chromeTheme
        val hideOffsetY = if (!handleExisted) lerpDp(HANDLE_SIZE + HANDLE_MARGIN * 3, 0.dp, progress) else 0.dp
        val expandedWidthPx = with(density) { expandedWidth.toPx() }
        val currentOffsetPx = with(density) { currentOffset.toPx() }
        val hideOffsetYPx = with(density) { hideOffsetY.toPx() }
        val hasBackdrop = state.backdropBlur != null && (chromeTheme == ChromeTheme.CLEAR || chromeTheme == ChromeTheme.ACRYLIC)
        val (frostedBackground, frostedBorder) = rememberFrostedGlassBrushes(isDark, hasBackdrop)

        val colorScheme = MaterialTheme.colorScheme
        val acrylicBackground = remember(colorScheme, hasBackdrop) {
            if (hasBackdrop) {
                Brush.verticalGradient(
                    listOf(
                        colorScheme.surfaceContainer.copy(alpha = 0.65f),
                        colorScheme.surfaceContainer.copy(alpha = 0.55f),
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        colorScheme.surfaceContainer.copy(alpha = 0.88f),
                        colorScheme.surfaceContainer.copy(alpha = 0.78f),
                    )
                )
            }
        }
        val acrylicBorder = remember(colorScheme) {
            Brush.verticalGradient(
                listOf(
                    colorScheme.outline.copy(alpha = 0.35f),
                    colorScheme.outlineVariant.copy(alpha = 0.18f),
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            AnimatedVisibility(
                visible = expanded && state.searchSuggestions.isNotEmpty(),
                enter = fadeIn(chromeFadeSpec()) + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut(chromeFadeSpec()) + shrinkVertically(shrinkTowards = Alignment.Bottom),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SuggestionsCard(
                    suggestions = state.searchSuggestions,
                    onSelect = { suggestion ->
                        onNavigate(suggestion.destination)
                        onCollapseOmnibox()
                    },
                    onInsert = { text ->
                        omniboxText = TextFieldValue(text, TextRange(text.length))
                        onQueryChange(text)
                    },
                    chromeTheme = chromeTheme,
                    isDark = isDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            val chromeBarModifier = when (chromeTheme) {
                ChromeTheme.CLEAR -> {
                    Modifier
                        .width(currentWidth)
                        .height(HANDLE_SIZE)
                        .graphicsLayer {
                            translationX = currentOffset.toPx()
                            translationY = hideOffsetY.toPx()
                            if (!handleExisted) {
                                alpha = (progress * 2f).coerceIn(0f, 1f)
                            }
                        }
                        .shadow(
                            elevation = if (progress > 0.5f) 14.dp else 4.dp,
                            shape = CircleShape,
                            spotColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0x30000000),
                            ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x15000000),
                        )
                        .clip(CircleShape)
                        .drawBehind {
                            val blur = state.backdropBlur
                            if (blur != null) {
                                drawImage(
                                    image = blur,
                                    dstOffset = IntOffset(-currentOffsetPx.roundToInt(), -hideOffsetYPx.roundToInt()),
                                    dstSize = IntSize(expandedWidthPx.roundToInt(), size.height.roundToInt()),
                                    filterQuality = FilterQuality.Low,
                                )
                            }
                        }
                        .background(frostedBackground)
                        .border(BorderStroke(1.dp, frostedBorder), CircleShape)
                }
                ChromeTheme.ACRYLIC -> {
                    Modifier
                        .width(currentWidth)
                        .height(HANDLE_SIZE)
                        .graphicsLayer {
                            translationX = currentOffset.toPx()
                            translationY = hideOffsetY.toPx()
                            if (!handleExisted) {
                                alpha = (progress * 2f).coerceIn(0f, 1f)
                            }
                        }
                        .shadow(
                            elevation = if (progress > 0.5f) 8.dp else 2.dp,
                            shape = CircleShape,
                        )
                        .clip(CircleShape)
                        .drawBehind {
                            val blur = state.backdropBlur
                            if (blur != null) {
                                drawImage(
                                    image = blur,
                                    dstOffset = IntOffset(-currentOffsetPx.roundToInt(), -hideOffsetYPx.roundToInt()),
                                    dstSize = IntSize(expandedWidthPx.roundToInt(), size.height.roundToInt()),
                                    filterQuality = FilterQuality.Low,
                                )
                            }
                        }
                        .background(acrylicBackground)
                        .border(BorderStroke(1.dp, acrylicBorder), CircleShape)
                }
                ChromeTheme.MATERIAL -> {
                    Modifier
                        .width(currentWidth)
                        .height(HANDLE_SIZE)
                        .graphicsLayer {
                            translationX = currentOffset.toPx()
                            translationY = hideOffsetY.toPx()
                            if (!handleExisted) {
                                alpha = (progress * 2f).coerceIn(0f, 1f)
                            }
                        }
                        .shadow(
                            elevation = if (progress > 0.5f) 3.dp else 1.dp,
                            shape = CircleShape,
                        )
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainer)
                }
            }

            val currentRatio = if (maxOffsetPx > 0f) currentHandleOffsetPx / maxOffsetPx else 0.5f
            Row(
                modifier = chromeBarModifier
                    .onGloballyPositioned { coords ->
                        val window = coords.boundsInWindow()
                        bounds.set(window.left, window.top, window.right, window.bottom)
                        onBoundsChanged(bounds)
                    },
                horizontalArrangement = when {
                    !handleExisted -> Arrangement.Center
                    currentRatio < 0.33f -> Arrangement.Start
                    currentRatio > 0.67f -> Arrangement.End
                    else -> Arrangement.Center
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cross-fade rather than swap: the handle's icon grows out of the
                // menu button it shares a glyph with, so the two states read as one
                // object changing size.
                if (progress > 0.5f) {
                    CompactBar(
                        state = state,
                        progress = progress,
                        expanded = expanded,
                        omniboxText = omniboxText,
                        onOmniboxTextChange = {
                            omniboxText = it
                            onQueryChange(it.text)
                        },
                        onBack = onBack,
                        onReload = onReload,
                        onStop = onStop,
                        onMenu = onMenu,
                        onOpenTabs = onOpenTabs,
                        onExpandOmnibox = onExpandOmnibox,
                        onNavigate = onNavigate,
                        onCollapseOmnibox = onCollapseOmnibox,
                        onDismissMenuPopup = onDismissMenuPopup,
                        onToggleReloadPopup = onToggleReloadPopup,
                        onDismissReloadPopup = onDismissReloadPopup,
                        onHardReload = onHardReload,
                        onEmptyCacheAndReload = onEmptyCacheAndReload,
                        onBookmarks = onBookmarks,
                        onHistory = onHistory,
                        onDownloads = onDownloads,
                        onExtensions = onExtensions,
                        onFindInPage = onFindInPage,
                        onSharePage = onSharePage,
                        onCopyLink = onCopyLink,
                        onAddToHomeScreen = onAddToHomeScreen,
                        onSettings = onSettings,
                        onToggleBookmark = onToggleBookmark,
                        onToggleChrome = onToggleChrome,
                        modifier = Modifier.graphicsLayer { alpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f) },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(HANDLE_SIZE)
                            .graphicsLayer { alpha = (1f - progress * 2f).coerceIn(0f, 1f) }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    dragOffsetPx += delta
                                },
                                onDragStopped = {
                                    val finalOffsetPx = (baseOffsetPx + dragOffsetPx).coerceIn(0f, maxOffsetPx)
                                    val newRatio = if (maxOffsetPx > 0f) (finalOffsetPx / maxOffsetPx).coerceIn(0f, 1f) else 0.5f
                                    dragOffsetPx = 0f
                                    onHandlePositionRatioChange(newRatio)
                                },
                            )
                            .clickable(onClick = onReveal),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.chrome_show_chrome),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bar's contents, faded by [progress].
 *
 * Kept out of [ChromeSurface] so the handle can be laid out without composing
 * the address field, the text field and four buttons that are about to be
 * invisible. On a low-end device that difference is visible as a hitch at the
 * start of the collapse.
 */
@Composable
private fun CompactBar(
    state: ReadingUiState,
    progress: Float,
    expanded: Boolean,
    omniboxText: TextFieldValue,
    onOmniboxTextChange: (TextFieldValue) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onMenu: () -> Unit,
    onOpenTabs: () -> Unit,
    onExpandOmnibox: () -> Unit,
    onNavigate: (String) -> Unit,
    onCollapseOmnibox: () -> Unit,
    onDismissMenuPopup: () -> Unit = {},
    onToggleReloadPopup: () -> Unit = {},
    onDismissReloadPopup: () -> Unit = {},
    onHardReload: () -> Unit = {},
    onEmptyCacheAndReload: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onHistory: () -> Unit = {},
    onDownloads: () -> Unit = {},
    onExtensions: () -> Unit = {},
    onFindInPage: () -> Unit = {},
    onSharePage: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onAddToHomeScreen: () -> Unit = {},
    onSettings: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onToggleChrome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded) {
            ExpandedOmnibox(
                state = state,
                value = omniboxText,
                onValueChange = onOmniboxTextChange,
                onNavigate = onNavigate,
                onCollapse = onCollapseOmnibox,
            )
        } else {
            val canBack = !state.isStartPage
            IconButton(
                onClick = onBack,
                enabled = canBack,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.chrome_back),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val isDark = resolveIsDark(state.settings.appTheme, isSystemInDarkTheme())
            val chromeTheme = state.settings.chromeTheme
            val addressPillBackground = when (chromeTheme) {
                ChromeTheme.CLEAR -> if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                ChromeTheme.ACRYLIC -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                ChromeTheme.MATERIAL -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val addressPillBorder = when (chromeTheme) {
                ChromeTheme.CLEAR -> BorderStroke(
                    0.75.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                )
                ChromeTheme.ACRYLIC -> BorderStroke(
                    0.75.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                )
                ChromeTheme.MATERIAL -> null
            }
            val view = LocalView.current
            var pillModifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(addressPillBackground)
            if (addressPillBorder != null) {
                pillModifier = pillModifier.border(addressPillBorder, CircleShape)
            }
            @OptIn(ExperimentalFoundationApi::class)
            pillModifier = pillModifier
                .height(44.dp)
                // Tapping the address is the primary way into the
                // browser's own UI. Long pressing hides the chrome.
                .combinedClickable(
                    onClick = onExpandOmnibox,
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onToggleChrome()
                    },
                )
                .padding(horizontal = 14.dp)

            Row(
                modifier = pillModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.incognito) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(R.string.chrome_private),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 7.dp)
                            .size(17.dp),
                    )
                }
                if (state.isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = state.displayHost,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = if (state.isLoading) 8.dp else 0.dp)
                        .weight(1f, fill = false),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right-side controls (Tabs, Reload, Menu) with uniform 38dp slot sizes and consistent spacing
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TabCounter(
                        count = state.tabs.size,
                        onClick = onOpenTabs,
                        chromeTheme = chromeTheme,
                        isDark = isDark,
                    )
                }

                // The reload button, and the menu that holding it opens.
                //
                // A tap still reloads, because that is what the button is for
                // and a browser that made the user hold to reload would be
                // unusable. The hold is for the two things reload is not:
                // fetching the page past the cache, and throwing the cache
                // away first.
                //
                // The shape is the menu button's, deliberately: the same slot,
                // the same control inside it, so the two icons sit on the
                // same line and take the same kind of touch. `IconButton`
                // cannot be used here because it has no long press, and a
                // second gesture handler on top of its own is not something to
                // rely on.
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { if (state.isLoading) onStop() else onReload() },
                                onLongClick = {
                                    // While the page is loading the button is a
                                    // stop button, and there is nothing to
                                    // reload yet.
                                    if (state.isLoading) return@combinedClickable
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onToggleReloadPopup()
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (state.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                            contentDescription = stringResource(
                                if (state.isLoading) R.string.chrome_reload_stop else R.string.chrome_reload,
                            ),
                            modifier = Modifier.size(20.dp),
                        )

                        ReloadPopup(
                            visible = state.reloadPopupVisible,
                            onHardReload = onHardReload,
                            onEmptyCacheAndReload = onEmptyCacheAndReload,
                            onDismiss = onDismissReloadPopup,
                            chromeTheme = chromeTheme,
                        )
                    }
                }

                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onMenu,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.chrome_menu),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (state.menuPopupVisible) {
                        Popup(
                            popupPositionProvider = ChromePopupPositionProvider(LocalDensity.current),
                            onDismissRequest = onDismissMenuPopup,
                            properties = PopupProperties(focusable = true),
                        ) {
                            ChromePopupMenu(
                                visible = true,
                                canBookmark = state.canBookmark,
                                isBookmarked = state.isBookmarked,
                                canShare = state.url.isNotBlank() && !state.isStartPage,
                                onToggleBookmark = onToggleBookmark,
                                onDismiss = onDismissMenuPopup,
                                onBookmarks = onBookmarks,
                                onHistory = onHistory,
                                onDownloads = onDownloads,
                                onExtensions = onExtensions,
                                onFindInPage = onFindInPage,
                                onShare = onSharePage,
                                onCopyLink = onCopyLink,
                                onAddToHomeScreen = onAddToHomeScreen,
                                onHideChrome = onToggleChrome,
                                onSettings = onSettings,
                                chromeTheme = chromeTheme,
                            )
                        }
                    }
                }
            }
        }

        // An unmistakable, always-present marker.
        //
        // This is not decoration. A private window that is only subtly
        // different is worse than no private window, because the user has no
        // way to tell which one they are in. So it is present in every state of
        // the bar, including while the address is being typed into.
        //
        // It lives inside the address field rather than at the end of the bar,
        // because the question it answers is "which window is this address in".
        // As a word it cost the bar a third of its width for a state that is
        // simply *on*; as a glyph in the accent colour it says the same thing in
        // 14dp. The word is kept as the description, which is where it belongs:
        // screen readers have no width to worry about.
    }
}

/**
 * The expanded address field.
 *
 * The whole shape-morph moment of the design thesis lives in the transition
 * between this and the compact pill, so it is kept deliberately plain: one
 * field, no suggestions yet, but genuinely usable.
 */
@Composable
private fun ExpandedOmnibox(
    state: ReadingUiState,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onNavigate: (String) -> Unit,
    onCollapse: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    text = stringResource(R.string.chrome_enter_url, state.settings.searchEngine.label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onGo = {
                    onNavigate(value.text)
                    onCollapse()
                },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        IconButton(
            onClick = {
                if (value.text.isNotEmpty()) {
                    onValueChange(TextFieldValue("", TextRange.Zero))
                } else {
                    onCollapse()
                }
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.chrome_hide_chrome),
            )
        }
    }
}

@Composable
internal fun rememberFrostedGlassBrushes(isDark: Boolean, hasBackdrop: Boolean = false): Pair<Brush, Brush> {
    return remember(isDark, hasBackdrop) {
        val background = if (hasBackdrop) {
            if (isDark) {
                Brush.verticalGradient(
                    listOf(
                        Color(0x88272938), // Translucent acrylic top highlight
                        Color(0x661A1C29), // Translucent body
                        Color(0x5512131F), // Deeper base
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0x99FFFFFF), // Bright specular white catch
                        Color(0x77F0F4FA), // Frosted translucent body
                        Color(0x66DFE5F0), // Cool tinted base
                    )
                )
            }
        } else {
            if (isDark) {
                Brush.verticalGradient(
                    listOf(
                        Color(0xD8272938), // Translucent acrylic top highlight
                        Color(0xB51A1C29), // Translucent body
                        Color(0x9E12131F), // Deeper base
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0xF2FFFFFF), // Bright specular white catch
                        Color(0xD4F0F4FA), // Frosted translucent body
                        Color(0xBCDFE5F0), // Cool tinted base
                    )
                )
            }
        }

        val border = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.36f), // Specular rim reflection
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.04f), // Soft base rim
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f), // Specular highlight
                    Color(0x35000000),               // Crisp edge definition
                    Color(0x12000000),
                )
            )
        }

        background to border
    }
}

@Composable
private fun SuggestionsCard(
    suggestions: List<SearchSuggestion>,
    onSelect: (SearchSuggestion) -> Unit,
    onInsert: (String) -> Unit,
    chromeTheme: ChromeTheme = ChromeTheme.CLEAR,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val (frostedBackground, frostedBorder) = rememberFrostedGlassBrushes(isDark)
    val colorScheme = MaterialTheme.colorScheme

    val cardModifier = when (chromeTheme) {
        ChromeTheme.CLEAR -> {
            modifier
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color(0x35000000),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.40f) else Color(0x18000000),
                )
                .clip(shape)
                .background(frostedBackground)
                .border(BorderStroke(1.dp, frostedBorder), shape)
        }
        ChromeTheme.ACRYLIC -> {
            val acrylicBorder = remember(colorScheme) {
                Brush.verticalGradient(
                    listOf(
                        colorScheme.outline.copy(alpha = 0.35f),
                        colorScheme.outlineVariant.copy(alpha = 0.15f),
                    )
                )
            }
            modifier
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                .background(colorScheme.surfaceContainerHigh.copy(alpha = 0.90f))
                .border(BorderStroke(1.dp, acrylicBorder), shape)
        }
        ChromeTheme.MATERIAL -> {
            modifier
                .shadow(elevation = 6.dp, shape = shape)
                .clip(shape)
                .background(colorScheme.surfaceContainerHigh)
                .border(BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)), shape)
        }
    }

    Box(modifier = cardModifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
        ) {
            itemsIndexed(suggestions) { index, suggestion ->
                if (index > 0) {
                    HorizontalDivider(
                        color = if (chromeTheme == ChromeTheme.CLEAR) {
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                        } else {
                            colorScheme.outlineVariant.copy(alpha = 0.25f)
                        },
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                SuggestionItem(
                    suggestion = suggestion,
                    onClick = { onSelect(suggestion) },
                    onInsert = { onInsert(suggestion.destination) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: SearchSuggestion,
    onClick: () -> Unit,
    onInsert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (suggestion) {
            is SearchSuggestion.Query -> Icons.Rounded.Search
            is SearchSuggestion.Bookmark -> Icons.Rounded.Bookmark
            is SearchSuggestion.History -> Icons.Rounded.History
        }
        val tint = when (suggestion) {
            is SearchSuggestion.Query -> MaterialTheme.colorScheme.onSurfaceVariant
            is SearchSuggestion.Bookmark -> MaterialTheme.colorScheme.primary
            is SearchSuggestion.History -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            suggestion.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(
            onClick = onInsert,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.NorthWest,
                contentDescription = stringResource(R.string.chrome_insert_suggestion),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Diameter of the collapsed handle. Also the bar's height, so the morph is square. */
private val HANDLE_SIZE: Dp = 56.dp

/** Inset from the screen edge, applied in both states. */
private val HANDLE_MARGIN: Dp = 12.dp
