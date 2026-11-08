package dev.clearbrowser.feature.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.data.ChromeTheme
import dev.clearbrowser.core.data.SearchEngine
import dev.clearbrowser.core.data.WebTheme

/**
 * Dedicated settings bottom sheet containing full browser configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: ReadingUiState,
    desktopSiteEnabled: Boolean,
    onDismiss: () -> Unit,
    onChromePositionChange: (ChromePosition) -> Unit,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onAutoHideChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDesktopSiteChange: (Boolean) -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    onIncognitoChange: (Boolean) -> Unit,
    onWebThemeChange: (WebTheme) -> Unit,
    onTextZoomChange: (Int) -> Unit = {},
    onChromeThemeChange: (ChromeTheme) -> Unit = {},
    onClearBrowsingData: (Boolean, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            SearchEngineControl(
                selected = settings.searchEngine,
                onSelect = onSearchEngineChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            ChromePositionControl(
                selected = settings.chromePosition,
                onSelect = onChromePositionChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            ChromeThemeControl(
                selected = settings.chromeTheme,
                onSelect = onChromeThemeChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            WebThemeControl(
                selected = settings.webTheme,
                onSelect = onWebThemeChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            TextScalingControl(
                selected = settings.textZoom,
                onSelect = onTextZoomChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SwitchRow(
                title = stringResource(R.string.menu_auto_hide),
                subtitle = stringResource(R.string.menu_auto_hide_subtitle),
                checked = settings.autoHideChrome,
                onCheckedChange = onAutoHideChange,
            )
            SwitchRow(
                title = stringResource(R.string.menu_incognito),
                subtitle = stringResource(R.string.menu_incognito_subtitle),
                checked = state.incognito,
                onCheckedChange = onIncognitoChange,
            )
            SwitchRow(
                title = stringResource(R.string.menu_immersive),
                subtitle = stringResource(R.string.menu_immersive_subtitle),
                checked = settings.immersiveMode,
                onCheckedChange = onImmersiveChange,
            )
            SwitchRow(
                title = stringResource(R.string.menu_desktop_site),
                subtitle = stringResource(R.string.menu_desktop_site_subtitle),
                checked = desktopSiteEnabled,
                onCheckedChange = onDesktopSiteChange,
            )
            SwitchRow(
                title = stringResource(R.string.menu_dynamic_color),
                subtitle = stringResource(R.string.menu_dynamic_color_subtitle),
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            ClearDataRow(
                onClick = { showClearConfirm = true },
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(text = stringResource(R.string.settings_clear_data_confirm_title))
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_data_confirm_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clearHistory = !clearHistory }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = clearHistory,
                            onCheckedChange = { clearHistory = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_clear_history),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clearCookies = !clearCookies }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = clearCookies,
                            onCheckedChange = { clearCookies = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_clear_cookies),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clearCache = !clearCache }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = clearCache,
                            onCheckedChange = { clearCache = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_clear_cache),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearBrowsingData(clearHistory, clearCookies, clearCache)
                        onDismiss()
                    },
                    enabled = clearHistory || clearCookies || clearCache,
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_data_confirm_action),
                        color = if (clearHistory || clearCookies || clearCache) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchEngineControl(
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.menu_search_engine)) },
            supportingText = { Text(stringResource(R.string.menu_search_engine_subtitle)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SearchEngine.entries.forEach { engine ->
                DropdownMenuItem(
                    text = { Text(engine.label) },
                    onClick = {
                        onSelect(engine)
                        expanded = false
                    },
                    trailingIcon = {
                        if (engine == selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChromePositionControl(
    selected: ChromePosition,
    onSelect: (ChromePosition) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.menu_chrome_position),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.menu_chrome_position_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            ChromePosition.entries.forEachIndexed { index, position ->
                SegmentedButton(
                    selected = selected == position,
                    onClick = { onSelect(position) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ChromePosition.entries.size,
                    ),
                    icon = {},
                    label = {
                        Icon(
                            imageVector = when (position) {
                                ChromePosition.LEFT -> positionIconLeft
                                ChromePosition.CENTER -> positionIconCenter
                                ChromePosition.RIGHT -> positionIconRight
                            },
                            contentDescription = stringResource(position.labelRes()),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
    }
}

private fun ChromePosition.labelRes(): Int = when (this) {
    ChromePosition.CENTER -> R.string.chrome_position_center
    ChromePosition.LEFT -> R.string.chrome_position_left
    ChromePosition.RIGHT -> R.string.chrome_position_right
}

private val positionIconLeft: ImageVector =
    Icons.AutoMirrored.Rounded.AlignHorizontalLeft

private val positionIconCenter: ImageVector = Icons.Rounded.AlignHorizontalCenter

private val positionIconRight: ImageVector =
    Icons.AutoMirrored.Rounded.AlignHorizontalRight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChromeThemeControl(
    selected: ChromeTheme,
    onSelect: (ChromeTheme) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.menu_chrome_theme),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.menu_chrome_theme_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            ChromeTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = selected == theme,
                    onClick = { onSelect(theme) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ChromeTheme.entries.size,
                    ),
                    icon = {},
                    label = {
                        Text(
                            text = when (theme) {
                                ChromeTheme.CLEAR -> stringResource(R.string.chrome_theme_clear)
                                ChromeTheme.ACRYLIC -> stringResource(R.string.chrome_theme_acrylic)
                                ChromeTheme.MATERIAL -> stringResource(R.string.chrome_theme_material)
                            },
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebThemeControl(
    selected: WebTheme,
    onSelect: (WebTheme) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.menu_web_theme),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.menu_web_theme_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            WebTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = selected == theme,
                    onClick = { onSelect(theme) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = WebTheme.entries.size,
                    ),
                    icon = {},
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when (theme) {
                                    WebTheme.SYSTEM -> Icons.Rounded.BrightnessAuto
                                    WebTheme.DARK -> Icons.Rounded.DarkMode
                                    WebTheme.LIGHT -> Icons.Rounded.LightMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = when (theme) {
                                    WebTheme.SYSTEM -> stringResource(R.string.web_theme_system)
                                    WebTheme.DARK -> stringResource(R.string.web_theme_dark)
                                    WebTheme.LIGHT -> stringResource(R.string.web_theme_light)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ClearDataRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_clear_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.settings_clear_data_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextScalingControl(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(80, 100, 125, 150)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_text_zoom),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_text_zoom_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, zoom ->
                SegmentedButton(
                    selected = selected == zoom,
                    onClick = { onSelect(zoom) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = {
                        Text(
                            text = "$zoom%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
    }
}
