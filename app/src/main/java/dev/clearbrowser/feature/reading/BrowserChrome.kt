package dev.clearbrowser.feature.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.clearbrowser.R
import dev.clearbrowser.core.data.BrowserSettings
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.data.SearchSuggestion
import dev.clearbrowser.core.theme.chromeFadeSpec
import dev.clearbrowser.core.theme.chromeMorphSpring

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
    onBookmarks: () -> Unit = {},
    onHistory: () -> Unit = {},
    onExtensions: () -> Unit = {},
    onFindInPage: () -> Unit = {},
    onSharePage: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onSettings: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val collapsed = state.chromeMode == ChromeMode.READING
    val expanded = state.chromeMode == ChromeMode.EXPANDED
    val position = state.settings.chromePosition

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
    LaunchedEffect(progress) {
        if (progress <= 0.01f) onBoundsChanged(null)
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

        // Where the handle sits, in the same coordinate space as the bar.
        val handleOffset: Dp = when (position) {
            ChromePosition.LEFT -> 0.dp
            ChromePosition.CENTER -> (available - handleWidth) / 2f
            ChromePosition.RIGHT -> available - handleWidth
        }

        val currentWidth = lerpDp(handleWidth, expandedWidth, progress)
        val currentOffset = lerpDp(handleOffset, 0.dp, progress)

        val isDark = isSystemInDarkTheme()
        val isOriginalM3 = state.settings.originalMaterial3Chrome
        val (frostedBackground, frostedBorder) = rememberFrostedGlassBrushes(isDark)

        val colorScheme = MaterialTheme.colorScheme
        val m3Background = remember(colorScheme) {
            Brush.verticalGradient(
                listOf(
                    colorScheme.surfaceContainer.copy(alpha = 0.88f),
                    colorScheme.surfaceContainer.copy(alpha = 0.78f),
                )
            )
        }
        val m3Border = remember(colorScheme) {
            Brush.verticalGradient(
                listOf(
                    colorScheme.outline.copy(alpha = 0.35f),
                    colorScheme.outlineVariant.copy(alpha = 0.18f),
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = expanded && state.searchSuggestions.isNotEmpty(),
                enter = fadeIn(chromeFadeSpec()) + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut(chromeFadeSpec()) + shrinkVertically(shrinkTowards = Alignment.Bottom),
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
                    isOriginalM3 = isOriginalM3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            val chromeBarModifier = if (isOriginalM3) {
                Modifier
                    .width(currentWidth)
                    .height(HANDLE_SIZE)
                    .graphicsLayer { translationX = currentOffset.toPx() }
                    .shadow(
                        elevation = if (progress > 0.5f) 8.dp else 2.dp,
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .background(m3Background)
                    .border(BorderStroke(1.dp, m3Border), CircleShape)
            } else {
                Modifier
                    .width(currentWidth)
                    .height(HANDLE_SIZE)
                    .graphicsLayer { translationX = currentOffset.toPx() }
                    .shadow(
                        elevation = if (progress > 0.5f) 14.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0x30000000),
                        ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x15000000),
                    )
                    .clip(CircleShape)
                    .background(frostedBackground)
                    .border(BorderStroke(1.dp, frostedBorder), CircleShape)
            }

            Row(
                modifier = chromeBarModifier
                    .onGloballyPositioned { coords ->
                        val window = coords.boundsInWindow()
                        bounds.set(window.left, window.top, window.right, window.bottom)
                        onBoundsChanged(bounds)
                    },
                horizontalArrangement = when (position) {
                    ChromePosition.RIGHT -> Arrangement.End
                    ChromePosition.CENTER -> Arrangement.Center
                    ChromePosition.LEFT -> Arrangement.Start
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
                        onBookmarks = onBookmarks,
                        onHistory = onHistory,
                        onExtensions = onExtensions,
                        onFindInPage = onFindInPage,
                        onSharePage = onSharePage,
                        onCopyLink = onCopyLink,
                        onSettings = onSettings,
                        onToggleBookmark = onToggleBookmark,
                        modifier = Modifier.graphicsLayer { alpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f) },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(HANDLE_SIZE)
                            .graphicsLayer { alpha = (1f - progress * 2f).coerceIn(0f, 1f) }
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
    onBookmarks: () -> Unit = {},
    onHistory: () -> Unit = {},
    onExtensions: () -> Unit = {},
    onFindInPage: () -> Unit = {},
    onSharePage: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onSettings: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
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
            IconButton(onClick = onBack, enabled = state.canGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.chrome_back),
                )
            }

            val isDark = isSystemInDarkTheme()
            val isOriginalM3 = state.settings.originalMaterial3Chrome
            val addressPillBackground = if (isOriginalM3) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            } else {
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            }
            val addressPillBorder = if (isOriginalM3) {
                BorderStroke(
                    0.75.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                )
            } else {
                BorderStroke(
                    0.75.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(addressPillBackground)
                    .border(addressPillBorder, CircleShape)
                    .height(44.dp)
                    // Tapping the address is the primary way into the
                    // browser's own UI, so it has to be the most
                    // tappable thing in the pill.
                    .clickable(onClick = onExpandOmnibox)
                    .padding(horizontal = 14.dp),
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

            // Between the address and reload: it belongs to the row of
            // controls on the right, and the address pill keeps all the width
            // that is left. On a narrow phone the count is two characters at
            // most, so this costs the URL almost nothing.
            TabCounter(
                count = state.tabs.size,
                onClick = onOpenTabs,
                modifier = Modifier.padding(horizontal = 2.dp),
            )

            IconButton(onClick = { if (state.isLoading) onStop() else onReload() }) {
                Icon(
                    imageVector = if (state.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                    contentDescription = stringResource(
                        if (state.isLoading) R.string.chrome_reload_stop else R.string.chrome_reload,
                    ),
                )
            }

            Box {
                IconButton(onClick = onMenu) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.chrome_menu),
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
                            onExtensions = onExtensions,
                            onFindInPage = onFindInPage,
                            onShare = onSharePage,
                            onCopyLink = onCopyLink,
                            onSettings = onSettings,
                            isOriginalM3 = isOriginalM3,
                        )
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
private fun rememberFrostedGlassBrushes(isDark: Boolean): Pair<Brush, Brush> {
    return remember(isDark) {
        val background = if (isDark) {
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
    isOriginalM3: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(24.dp)
    val (frostedBackground, frostedBorder) = rememberFrostedGlassBrushes(isDark)
    val colorScheme = MaterialTheme.colorScheme

    val cardModifier = if (isOriginalM3) {
        val m3Border = remember(colorScheme) {
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
            .border(BorderStroke(1.dp, m3Border), shape)
    } else {
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

    Box(modifier = cardModifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp),
        ) {
            itemsIndexed(suggestions) { index, suggestion ->
                if (index > 0) {
                    HorizontalDivider(
                        color = if (isOriginalM3) {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
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

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp =
    start + (end - start) * fraction.coerceIn(0f, 1f)
