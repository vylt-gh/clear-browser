package dev.clearbrowser.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.clearbrowser.R
import dev.clearbrowser.core.data.AppTheme
import dev.clearbrowser.core.data.BrowserSettings
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.data.ChromeTheme
import dev.clearbrowser.core.data.SearchEngine
import dev.clearbrowser.core.theme.chromeMorphSpring
import dev.clearbrowser.core.theme.lerpDp
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
    onChromePositionChange: (ChromePosition) -> Unit = {},
    onChromeThemeChange: (ChromeTheme) -> Unit,
    onHideHandleChange: (Boolean) -> Unit = {},
    onRequestDefaultBrowser: () -> Unit,
    onImportBookmarks: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
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
                    repeat(4) { index ->
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
                        hideHandle = settings.hideHandle,
                        onHideHandleChange = onHideHandleChange,
                    )
                    2 -> PermissionsPage()
                    3 -> ReadyPage(
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

                if (pagerState.currentPage < 3) {
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
            hideHandle = hideHandle,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))

        // 4. Draggable handle tip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_draggable_handle_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
    hideHandle: Boolean,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }
    val isDark = resolveIsDark(appTheme, isSystemInDarkTheme())
    val colorScheme = MaterialTheme.colorScheme

    val progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = chromeMorphSpring(),
        label = "dummy_chrome_morph",
    )

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
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    isExpanded = !isExpanded
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.onboarding_preview_title),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    isExpanded -> stringResource(R.string.onboarding_preview_hint)
                    hideHandle -> "Hidden while reading (Tap to expand)"
                    else -> "Tap handle to expand"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mock Web View Container with real-world proportion
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF13141C) else Color(0xFFF4F6FB))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    isExpanded = !isExpanded
                }
                .padding(10.dp),
        ) {
            val available = maxWidth
            val handleSize = 44.dp
            val expandedWidth = available

            val handleOffset: Dp = (available - handleSize) / 2f

            val animatedHandleOffset by animateDpAsState(
                targetValue = handleOffset,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f),
                label = "dummy_handle_offset",
            )

            val currentWidth = lerpDp(handleSize, expandedWidth, progress)
            val currentOffset = lerpDp(animatedHandleOffset, 0.dp, progress)

            val targetOffsetY = if (hideHandle) lerpDp(handleSize + 24.dp, 0.dp, progress) else 0.dp
            val animatedOffsetY by animateDpAsState(
                targetValue = targetOffsetY,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f),
                label = "dummy_hide_offset",
            )
            val hideAlpha = if (hideHandle) (progress * 2f).coerceIn(0f, 1f) else 1f

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

            // Single unified morphing bar surface (matches BrowserChrome architecture)
            val basePillModifier = when (chromeTheme) {
                ChromeTheme.CLEAR -> Modifier
                    .shadow(
                        elevation = if (progress > 0.5f) 8.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0x30000000),
                        ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x15000000),
                    )
                    .clip(CircleShape)
                    .background(frostedBackground)
                    .border(BorderStroke(1.dp, frostedBorder), CircleShape)
                ChromeTheme.ACRYLIC -> Modifier
                    .shadow(
                        elevation = if (progress > 0.5f) 6.dp else 2.dp,
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .background(acrylicBackground)
                    .border(BorderStroke(1.dp, acrylicBorder), CircleShape)
                ChromeTheme.MATERIAL -> Modifier
                    .shadow(
                        elevation = if (progress > 0.5f) 3.dp else 1.dp,
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainer)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(currentWidth)
                    .height(handleSize)
                    .graphicsLayer {
                        translationX = currentOffset.toPx()
                        translationY = animatedOffsetY.toPx()
                        alpha = hideAlpha
                    }
                    .then(basePillModifier),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (progress > 0.5f) {
                        // Expanded Bar Content
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = ((progress - 0.5f) * 2f).coerceIn(0f, 1f) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp),
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
                                .height(30.dp)
                                .clip(CircleShape)
                                .background(addressPillBg)
                            if (addressPillBorder != null) {
                                pillMod = pillMod.border(addressPillBorder, CircleShape)
                            }

                            Row(
                                modifier = pillMod.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = "clearbrowser.dev",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Tab counter preview
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
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
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = colorScheme.onSurface,
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        // Minimized handle content
                        Box(
                            modifier = Modifier
                                .size(handleSize)
                                .graphicsLayer { alpha = (1f - progress * 2f).coerceIn(0f, 1f) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsPage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun refreshPermissions() {
        hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissions()
    }

    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissions()
    }

    val allGranted = hasNotifications && hasCamera && hasMic && hasLocation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.onboarding_permissions_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        // Grant All button or All Granted indicator
        if (allGranted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_all_granted),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Button(
                onClick = {
                    val missing = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications) {
                        missing.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!hasCamera) {
                        missing.add(Manifest.permission.CAMERA)
                    }
                    if (!hasMic) {
                        missing.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (!hasLocation) {
                        missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    if (missing.isNotEmpty()) {
                        multipleLauncher.launch(missing.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_permissions_grant_all),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications
        PermissionCard(
            icon = Icons.Rounded.Notifications,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            title = stringResource(R.string.onboarding_permission_notifications_title),
            description = stringResource(R.string.onboarding_permission_notifications_desc),
            isGranted = hasNotifications,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Camera
        PermissionCard(
            icon = Icons.Rounded.PhotoCamera,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            title = stringResource(R.string.onboarding_permission_camera_title),
            description = stringResource(R.string.onboarding_permission_camera_desc),
            isGranted = hasCamera,
            onRequest = {
                singleLauncher.launch(Manifest.permission.CAMERA)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Microphone
        PermissionCard(
            icon = Icons.Rounded.Mic,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            title = stringResource(R.string.onboarding_permission_mic_title),
            description = stringResource(R.string.onboarding_permission_mic_desc),
            isGranted = hasMic,
            onRequest = {
                singleLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Location
        PermissionCard(
            icon = Icons.Rounded.LocationOn,
            iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconContentColor = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.onboarding_permission_location_title),
            description = stringResource(R.string.onboarding_permission_location_desc),
            isGranted = hasLocation,
            onRequest = {
                multipleLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy note card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_permissions_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = if (isGranted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
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
                .background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconContentColor,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.onboarding_permission_granted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            FilledTonalButton(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.permission_allow),
                    style = MaterialTheme.typography.labelMedium,
                )
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
