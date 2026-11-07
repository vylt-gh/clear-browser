package dev.clearbrowser.feature.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import dev.clearbrowser.R
import dev.clearbrowser.core.data.BrowserSettings
import dev.clearbrowser.core.data.ChromePosition
import dev.clearbrowser.core.data.SearchEngine
import kotlin.math.roundToInt

/**
 * The overflow menu.
 *
 * This is the "summoned" end of the browser's two-mode personality: the user
 * opened it, so it is fully Material and unapologetically expressive. A sheet
 * rather than a dropdown because the tint control needs room, and because a
 * sheet can carry the live preview that makes the slider legible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromeMenuSheet(
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
    canBookmark: Boolean,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                // Scrollable because this sheet is now taller than a small phone.
                // A settings list that runs off the bottom edge has no way to
                // reach its last row, and the last row here is a switch someone
                // came to the sheet to flip.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.chrome_url_field),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.displayHost,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Bookmarks and history live at the top of the menu, before the
            // settings, on purpose. They are what the user came here *for*; the
            // switches are configuration. The star row also answers "is this
            // page saved" at a glance, which is the same job the address bar's
            // private glyph does for the profile.
            BookmarkRow(
                canBookmark = canBookmark,
                bookmarked = isBookmarked,
                incognito = state.incognito,
                onToggle = onToggleBookmark,
            )
            LibraryNavigationRow(onOpenLibrary = onOpenLibrary)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // There used to be a "match page colour" strength slider here. It is
            // gone: the chrome now always uses the page's colour outright, so the
            // control could only take that away. Its only remaining honest
            // settings are the ones below.
            SearchEngineControl(
                selected = settings.searchEngine,
                onSelect = onSearchEngineChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Placed next to the tint control rather than down with the
            // switches, because it is the same kind of thing: a visual
            // preference about the bar itself, with an effect you can see the
            // moment you change it.
            ChromePositionControl(
                selected = settings.chromePosition,
                onSelect = onChromePositionChange,
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
        }
    }
}

/**
 * Which engine answers a typed question, as a dropdown.
 *
 * ## Why a dropdown rather than the chips this started as
 *
 * Eight chips look like a catalogue: they take three rows of the sheet to say
 * one thing, and they make a setting that is almost never changed the loudest
 * object on the screen. A single field that reads "Kagi" answers "which one is
 * in force" at a glance, and opening it is one tap for a change made rarely.
 *
 * ## Why the value is a read-only field and not a row with a trailing arrow
 *
 * The field keeps its label when the menu is open, so the list cannot be
 * mistaken for a list of pages or of recent searches -- and a dropdown that is
 * visibly a *field* is the one form people already know how to operate in
 * Android, so nothing has to be learned from scratch.
 */
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
        modifier = Modifier.padding(top = 20.dp),
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
                    // A tick rather than a highlighted row: the row that is
                    // already chosen has to be identifiable by more than colour,
                    // and the menu closes on tap, so the choice has to be visible
                    // *before* the tap, not confirmed after it.
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

/**
 * Where the bar sits, as a segmented control.
 *
 * A segmented row rather than a switch or a list, because the three options are
 * mutually exclusive, adjacent, and each is better recognised by its shape than
 * by its name. "Left" as text is ambiguous on a screen whose direction follows
 * the locale; a bar visibly hugging the left edge is not.
 *
 * The icons are mirrored so they follow reading direction. On a right-to-left
 * locale "leading" is the right edge, and a hard-coded left-pointing icon would
 * then be a lie about what tapping it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChromePositionControl(
    selected: ChromePosition,
    onSelect: (ChromePosition) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
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
            // The whole row toggles, not just the switch. "Private browsing" is
            // a label for a setting, and a row that answers a tap only on a 30dp
            // control at its far edge reads as broken. The switch's own callback
            // is null so the row is the single owner of the state change.
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

/**
 * The star for the page being read.
 *
 * A filled star says "saved" without the user having to remember the word, and
 * the subtitle says what a tap will do next rather than only what just happened.
 * On the start page there is no page to save, and the row says so rather than
 * silently doing nothing: an action that only sometimes answers reads as broken
 * in exactly the same way a switch that only answers on the switch does.
 */
@Composable
private fun BookmarkRow(
    canBookmark: Boolean,
    bookmarked: Boolean,
    incognito: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (canBookmark) {
                    Modifier.clickable(onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .alpha(if (canBookmark) 1f else 0.45f)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (bookmarked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(
                    if (bookmarked) {
                        R.string.menu_bookmarked_page
                    } else {
                        R.string.menu_bookmark_page
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    when {
                        canBookmark -> R.string.menu_bookmark_subtitle
                        // Two different reasons for the same disabled row, each
                        // said out loud: "open a page" is something the user can
                        // act on, while a private window is the point of the
                        // window and not a mistake to correct.
                        incognito -> R.string.menu_bookmark_private
                        else -> R.string.menu_bookmark_no_page
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One row that opens the library sheet, rather than two menu items.
 *
 * Bookmarks and history share the sheet and its segmented control, so two menu
 * entries would only ever be two names for the same destination. The chevron is
 * what makes the row read as "go there" instead of "turn this on".
 */
@Composable
private fun LibraryNavigationRow(onOpenLibrary: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenLibrary)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Bookmarks,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(R.string.library_menu_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.library_menu_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
