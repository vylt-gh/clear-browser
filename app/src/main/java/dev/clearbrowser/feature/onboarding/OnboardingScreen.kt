package dev.clearbrowser.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R
import dev.clearbrowser.core.data.AppTheme
import dev.clearbrowser.core.data.BrowserSettings
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.data.ChromeTheme
import dev.clearbrowser.core.data.SearchEngine
import dev.clearbrowser.feature.reading.rememberFrostedGlassBrushes
import dev.clearbrowser.feature.reading.resolveIsDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settings: BrowserSettings,
    isDefaultBrowser: Boolean,
    importedBookmarksCount: Int?,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onAppThemeChange: (AppTheme) -> Unit,
    onChromePositionChange: (ChromePosition) -> Unit,
    onChromeThemeChange: (ChromeTheme) -> Unit,
    onHideHandleChange: (Boolean) -> Unit = {},
    onRequestDefaultBrowser: () -> Unit,
    onImportBookmarks: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onFinish()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Page indicator pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                        )
                    }
                }

                TextButton(
                    onClick = onFinish,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomePage(
                        selectedEngine = settings.searchEngine,
                        onSelectEngine = onSearchEngineChange,
                    )
                    1 -> AppearancePage(
                        appTheme = settings.appTheme,
                        onAppThemeChange = onAppThemeChange,
                        chromeTheme = settings.chromeTheme,
                        onChromeThemeChange = onChromeThemeChange,
                        chromePosition = settings.chromePosition,
                        onChromePositionChange = onChromePositionChange,
                        hideHandle = settings.hideHandle,
                        onHideHandleChange = onHideHandleChange,
                    )
                    2 -> ReadyPage(
                        isDefaultBrowser = isDefaultBrowser,
                        importedBookmarksCount = importedBookmarksCount,
                        onRequestDefaultBrowser = onRequestDefaultBrowser,
                        onImportBookmarks = onImportBookmarks,
                    )
                }
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (pagerState.currentPage < 2) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.onboarding_next))
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = onFinish,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_get_started))
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(
    selectedEngine: SearchEngine,
    onSelectEngine: (SearchEngine) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Image(
            painter = painterResource(R.drawable.ic_clear_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        FeatureHighlightCard(
            icon = Icons.Rounded.Shield,
            title = stringResource(R.string.onboarding_feature_adblock_title),
            description = stringResource(R.string.onboarding_feature_adblock_desc),
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureHighlightCard(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.onboarding_feature_privacy_title),
            description = stringResource(R.string.onboarding_feature_privacy_desc),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_search_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.onboarding_search_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        SearchEngine.entries.forEach { engine ->
            val isSelected = engine == selectedEngine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onSelectEngine(engine) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = engine.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePage(
    appTheme: AppTheme,
    onAppThemeChange: (AppTheme) -> Unit,
    chromeTheme: ChromeTheme,
    onChromeThemeChange: (ChromeTheme) -> Unit,
    chromePosition: ChromePosition,
    onChromePositionChange: (ChromePosition) -> Unit,
    hideHandle: Boolean,
    onHideHandleChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_customize_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.onboarding_customize_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        // 1. App Theme
        Text(
            text = stringResource(R.string.menu_app_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.menu_app_theme_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AppTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = appTheme == theme,
                    onClick = { onAppThemeChange(theme) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AppTheme.entries.size),
                    icon = {},
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when (theme) {
                                    AppTheme.SYSTEM -> Icons.Rounded.BrightnessAuto
                                    AppTheme.DARK -> Icons.Rounded.DarkMode
                                    AppTheme.LIGHT -> Icons.Rounded.LightMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = when (theme) {
                                    AppTheme.SYSTEM -> stringResource(R.string.app_theme_system)
                                    AppTheme.DARK -> stringResource(R.string.app_theme_dark)
                                    AppTheme.LIGHT -> stringResource(R.string.app_theme_light)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))

        // 2. Chrome Theme
        Text(
            text = stringResource(R.string.menu_chrome_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.menu_chrome_theme_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChromeTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = chromeTheme == theme,
                    onClick = { onChromeThemeChange(theme) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ChromeTheme.entries.size),
                    icon = {},
                    label = {
                        Text(
                            text = when (theme) {
                                ChromeTheme.CLEAR -> stringResource(R.string.chrome_theme_clear)
                                ChromeTheme.ACRYLIC -> stringResource(R.string.chrome_theme_acrylic)
                                ChromeTheme.MATERIAL -> stringResource(R.string.chrome_theme_material)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Live Dummy Chrome Preview
        DummyChromePreview(
            chromeTheme = chromeTheme,
            appTheme = appTheme,
            chromePosition = chromePosition,
            hideHandle = hideHandle,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))

        // 4. Minimized Bar Position (formerly Handle Position)
        Text(
            text = stringResource(R.string.menu_chrome_position),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.menu_chrome_position_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChromePosition.entries.forEachIndexed { index, pos ->
                SegmentedButton(
                    selected = chromePosition == pos,
                    onClick = { onChromePositionChange(pos) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ChromePosition.entries.size),
                    icon = {},
                    label = {
                        Icon(
                            imageVector = when (pos) {
                                ChromePosition.LEFT -> Icons.AutoMirrored.Rounded.AlignHorizontalLeft
                                ChromePosition.CENTER -> Icons.Rounded.AlignHorizontalCenter
                                ChromePosition.RIGHT -> Icons.AutoMirrored.Rounded.AlignHorizontalRight
                            },
                            contentDescription = pos.name,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Hide Minimized Button Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onHideHandleChange(!hideHandle) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.menu_hide_handle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.menu_hide_handle_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = hideHandle,
                onCheckedChange = onHideHandleChange,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DummyChromePreview(
    chromeTheme: ChromeTheme,
    appTheme: AppTheme,
    chromePosition: ChromePosition,
    hideHandle: Boolean,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }
    val isDark = resolveIsDark(appTheme, isSystemInDarkTheme())
    val colorScheme = MaterialTheme.colorScheme

    val (frostedBackground, frostedBorder) = rememberFrostedGlassBrushes(isDark)
    val acrylicBackground = remember(colorScheme) {
        Brush.verticalGradient(
            listOf(
                colorScheme.surfaceContainer.copy(alpha = 0.88f),
                colorScheme.surfaceContainer.copy(alpha = 0.78f),
            )
        )
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                colorScheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.onboarding_preview_title),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isExpanded) stringResource(R.string.onboarding_preview_hint) else "Tap handle to expand",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mock Web View Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF13141C) else Color(0xFFF4F6FB))
                .padding(10.dp),
        ) {
            // Mock web page text lines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.40f)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                )
            }

            // The floating bar / handle
            val barAlignment = when (chromePosition) {
                ChromePosition.LEFT -> Alignment.BottomStart
                ChromePosition.CENTER -> Alignment.BottomCenter
                ChromePosition.RIGHT -> Alignment.BottomEnd
            }

            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200, delayMillis = 40)) +
                            scaleIn(initialScale = 0.92f, animationSpec = tween(200)))
                        .togetherWith(fadeOut(animationSpec = tween(100)))
                },
                modifier = Modifier.align(if (isExpanded) Alignment.BottomCenter else barAlignment),
                label = "dummy_chrome_anim",
            ) { expanded ->
                if (expanded) {
                    // Full Bar Preview
                    val barModifier = when (chromeTheme) {
                        ChromeTheme.CLEAR -> Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(8.dp, CircleShape, spotColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0x30000000))
                            .clip(CircleShape)
                            .background(frostedBackground)
                            .border(BorderStroke(1.dp, frostedBorder), CircleShape)
                        ChromeTheme.ACRYLIC -> Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(acrylicBackground)
                            .border(BorderStroke(1.dp, acrylicBorder), CircleShape)
                        ChromeTheme.MATERIAL -> Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceContainer)
                    }

                    Row(
                        modifier = barModifier
                            .clickable { isExpanded = false }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Address pill
                        val addressPillBg = when (chromeTheme) {
                            ChromeTheme.CLEAR -> if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                            ChromeTheme.ACRYLIC -> colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                            ChromeTheme.MATERIAL -> colorScheme.surfaceContainerHigh
                        }
                        val addressPillBorder = when (chromeTheme) {
                            ChromeTheme.CLEAR -> BorderStroke(0.75.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
                            ChromeTheme.ACRYLIC -> BorderStroke(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.25f))
                            ChromeTheme.MATERIAL -> null
                        }

                        var pillMod = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(addressPillBg)
                        if (addressPillBorder != null) {
                            pillMod = pillMod.border(addressPillBorder, CircleShape)
                        }

                        Row(
                            modifier = pillMod.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "clearbrowser.dev",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Tab counter preview
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    when (chromeTheme) {
                                        ChromeTheme.CLEAR -> if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                                        ChromeTheme.ACRYLIC -> colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                                        ChromeTheme.MATERIAL -> colorScheme.surfaceContainerHighest
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "3",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurface,
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    // Minimized handle
                    if (hideHandle) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.primaryContainer.copy(alpha = 0.85f))
                                .clickable { isExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Hidden while reading (Tap to expand)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer,
                            )
                        }
                    } else {
                        val handleModifier = when (chromeTheme) {
                            ChromeTheme.CLEAR -> Modifier
                                .size(44.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(frostedBackground)
                                .border(BorderStroke(1.dp, frostedBorder), CircleShape)
                            ChromeTheme.ACRYLIC -> Modifier
                                .size(44.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(acrylicBackground)
                                .border(BorderStroke(1.dp, acrylicBorder), CircleShape)
                            ChromeTheme.MATERIAL -> Modifier
                                .size(44.dp)
                                .shadow(2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceContainer)
                        }

                        Box(
                            modifier = handleModifier.clickable { isExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyPage(
    isDefaultBrowser: Boolean,
    importedBookmarksCount: Int?,
    onRequestDefaultBrowser: () -> Unit,
    onImportBookmarks: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_finish_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.onboarding_finish_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        // Bookmarks Import Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_import_bookmarks_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (importedBookmarksCount != null && importedBookmarksCount > 0) {
                        stringResource(R.string.onboarding_import_bookmarks_success, importedBookmarksCount)
                    } else {
                        stringResource(R.string.onboarding_import_bookmarks_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (importedBookmarksCount != null && importedBookmarksCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (importedBookmarksCount != null && importedBookmarksCount > 0) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                FilledTonalButton(
                    onClick = onImportBookmarks,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_import_bookmarks_action),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Default Browser Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_default_browser),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (isDefaultBrowser) R.string.settings_default_browser_current
                        else R.string.settings_default_browser_set
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDefaultBrowser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isDefaultBrowser) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                FilledTonalButton(
                    onClick = onRequestDefaultBrowser,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_default_browser_action),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureHighlightCard(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
