package dev.clearbrowser.feature.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R
import dev.clearbrowser.core.data.AppTheme
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
    onChromePositionChange: (ChromePosition) -> Unit = {},
    onSearchEngineChange: (SearchEngine) -> Unit,
    onAutoHideChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDesktopSiteChange: (Boolean) -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    onIncognitoChange: (Boolean) -> Unit,
    onAppThemeChange: (AppTheme) -> Unit = {},
    onWebThemeChange: (WebTheme) -> Unit,
    onTextZoomChange: (Int) -> Unit = {},
    onChromeThemeChange: (ChromeTheme) -> Unit = {},
    onHideHandleChange: (Boolean) -> Unit = {},
    onPullToRefreshChange: (Boolean) -> Unit = {},
    onCanopyDownloadsChange: (Boolean) -> Unit = {},
    onClearBrowsingData: (Boolean, Boolean, Boolean) -> Unit,
    sitePermissions: List<dev.clearbrowser.core.data.SitePermission> = emptyList(),
    onRevokeSitePermission: (String, String) -> Unit = { _, _ -> },
    onClearAllSitePermissions: () -> Unit = {},
    onAutoAcceptAllPermissionsChange: (Boolean) -> Unit = {},
    onToggleAutoAcceptPermission: (String, Boolean) -> Unit = { _, _ -> },
    isDefaultBrowser: Boolean = false,
    onRequestDefaultBrowser: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings
    var showClearConfirm by remember { mutableStateOf(false) }
    var showSitePermissions by remember { mutableStateOf(false) }
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

            DefaultBrowserRow(
                isDefault = isDefaultBrowser,
                onClick = onRequestDefaultBrowser,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SearchEngineControl(
                selected = settings.searchEngine,
                onSelect = onSearchEngineChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            ChromeThemeControl(
                selected = settings.chromeTheme,
                onSelect = onChromeThemeChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            AppThemeControl(
                selected = settings.appTheme,
                onSelect = onAppThemeChange,
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
                title = stringResource(R.string.menu_hide_handle),
                subtitle = stringResource(R.string.menu_hide_handle_subtitle),
                checked = settings.hideHandle,
                onCheckedChange = onHideHandleChange,
            )
            SwitchRow(
                title = stringResource(R.string.menu_pull_to_refresh),
                subtitle = stringResource(R.string.menu_pull_to_refresh_subtitle),
                checked = settings.pullToRefresh,
                onCheckedChange = onPullToRefreshChange,
            )
            SwitchRow(
                title = stringResource(R.string.settings_canopy_downloads),
                subtitle = stringResource(R.string.settings_canopy_downloads_subtitle),
                checked = settings.canopyDownloads,
                onCheckedChange = onCanopyDownloadsChange,
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

            SitePermissionsRow(
                permissionsCount = sitePermissions.size,
                onClick = { showSitePermissions = true },
            )

            ClearDataRow(
                onClick = { showClearConfirm = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            WelcomeTourRow(
                onClick = onOpenOnboarding,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            AboutSection(
                appVersion = "0.10.0",
                engineVersion = dev.clearbrowser.core.engine.ClearGeckoRuntime.geckoViewVersion,
            )
        }
    }

    if (showSitePermissions) {
        AlertDialog(
            onDismissRequest = { showSitePermissions = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text(text = stringResource(R.string.settings_site_permissions))
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Auto-accept all switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onAutoAcceptAllPermissionsChange(!settings.autoAcceptAllPermissions)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_site_permissions_auto_accept_all),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_site_permissions_auto_accept_all_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.autoAcceptAllPermissions,
                            onCheckedChange = onAutoAcceptAllPermissionsChange,
                        )
                    }

                    // Checkbox list for specific permissions when auto-accept all is OFF
                    AnimatedVisibility(visible = !settings.autoAcceptAllPermissions) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.settings_site_permissions_specific_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )

                            val autoPerms = settings.autoAcceptPermissions
                            val permOptions = listOf(
                                "location" to stringResource(R.string.permission_location_label),
                                "notifications" to stringResource(R.string.permission_notifications_label),
                                "media" to stringResource(R.string.permission_camera_microphone_label),
                                "storage" to stringResource(R.string.permission_storage_label),
                                "features" to stringResource(R.string.permission_features_label),
                                "xr" to stringResource(R.string.permission_xr_label),
                                "autoplay" to stringResource(R.string.permission_autoplay_label),
                                "protected_media" to stringResource(R.string.permission_protected_media_label),
                                "storage_access" to stringResource(R.string.permission_storage_access_label),
                                "local_devices" to stringResource(R.string.permission_local_devices_label),
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                ) {
                                    permOptions.forEach { (key, label) ->
                                        val checked = autoPerms.contains(key)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    onToggleAutoAcceptPermission(key, !checked)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { isChecked ->
                                                    onToggleAutoAcceptPermission(key, isChecked)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = stringResource(R.string.settings_site_permissions_saved_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (sitePermissions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_site_permissions_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                sitePermissions.forEach { perm ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = perm.origin,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = perm.permissionType,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                onRevokeSitePermission(perm.origin, perm.permissionType)
                                            },
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_site_permissions_revoke),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (sitePermissions.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onClearAllSitePermissions()
                            showSitePermissions = false
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_site_permissions_revoke_all),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSitePermissions = false }) {
                    Text(text = stringResource(R.string.library_cancel))
                }
            },
        )
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
private fun AppThemeControl(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.menu_app_theme),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.menu_app_theme_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            AppTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = selected == theme,
                    onClick = { onSelect(theme) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AppTheme.entries.size,
                    ),
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

@Composable
private fun DefaultBrowserRow(
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isDefault, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = null,
            tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_default_browser),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    if (isDefault) R.string.settings_default_browser_current
                    else R.string.settings_default_browser_set
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isDefault) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        } else {
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_default_browser_action),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SitePermissionsRow(
    permissionsCount: Int,
    onClick: () -> Unit,
) {
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
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_site_permissions),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (permissionsCount > 0) {
                    "$permissionsCount saved site permission" + if (permissionsCount > 1) "s" else ""
                } else {
                    stringResource(R.string.settings_site_permissions_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WelcomeTourRow(
    onClick: () -> Unit,
) {
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
            imageVector = Icons.Rounded.Explore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_welcome_tour),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_welcome_tour_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutSection(
    appVersion: String,
    engineVersion: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_about_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_clear_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Clear Browser v$appVersion",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_engine_version, engineVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Engine updates track Mozilla GeckoView release milestones.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
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
