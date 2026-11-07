package dev.clearbrowser.feature.reading

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R
import dev.clearbrowser.core.data.Bookmark
import dev.clearbrowser.core.data.HistoryEntry

/** Which half of the library a sheet is showing. */
enum class LibraryTab {
    BOOKMARKS,
    HISTORY,
}

/**
 * Bookmarks and history, in one sheet.
 *
 * ## Why one sheet and not two
 *
 * They answer the same question -- "where is that page" -- and the difference is
 * only whether the user chose it. Two full sheets with near-identical rows would
 * make the menu ask the user to classify their own memory before they could look
 * at it. A segmented control keeps both one tap away and lets the sheet say
 * which list is in force while it is open.
 *
 * Rows are the same shape as the tab switcher's: title, host underneath, and a
 * small explicit remove at the trailing edge. The whole row opens the page, so
 * the destructive target is never the large one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheet(
    tab: LibraryTab,
    bookmarks: List<Bookmark>,
    history: List<HistoryEntry>,
    onSelectTab: (LibraryTab) -> Unit,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmClear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    LibraryTab.entries.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = tab == value,
                            onClick = { onSelectTab(value) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = LibraryTab.entries.size,
                            ),
                            icon = {},
                            label = { Text(stringResource(value.labelRes())) },
                        )
                    }
                }
                // Only history can be cleared wholesale. A "clear all bookmarks"
                // control next to "clear history" would be one mis-tap away from
                // deleting the list the user deliberately built, and removing a
                // bookmark is already one tap in its row.
                if (tab == LibraryTab.HISTORY && history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.library_clear))
                    }
                }
            }

            when (tab) {
                LibraryTab.BOOKMARKS -> {
                    if (bookmarks.isEmpty()) {
                        EmptyLibrary(
                            icon = Icons.Rounded.Bookmarks,
                            title = stringResource(R.string.library_empty_bookmarks),
                            body = stringResource(R.string.library_empty_bookmarks_hint),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(bookmarks, key = { it.url }) { bookmark ->
                                LibraryRow(
                                    title = bookmark.title.ifBlank { hostOf(bookmark.url) },
                                    subtitle = hostOf(bookmark.url),
                                    removeDescription = stringResource(R.string.library_remove_bookmark),
                                    onOpen = { onOpen(bookmark.url) },
                                    onRemove = { onRemoveBookmark(bookmark.url) },
                                )
                            }
                        }
                    }
                }

                LibraryTab.HISTORY -> {
                    if (history.isEmpty()) {
                        EmptyLibrary(
                            icon = Icons.Rounded.History,
                            title = stringResource(R.string.library_empty_history),
                            body = stringResource(R.string.library_empty_history_hint),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(history, key = { it.url }) { entry ->
                                LibraryRow(
                                    title = entry.title.ifBlank { hostOf(entry.url) },
                                    subtitle = subtitleFor(entry),
                                    removeDescription = stringResource(R.string.library_remove_history),
                                    onOpen = { onOpen(entry.url) },
                                    onRemove = { onRemoveHistory(entry.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.library_clear_history_title)) },
            text = { Text(stringResource(R.string.library_clear_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearHistory()
                    },
                ) {
                    Text(stringResource(R.string.library_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    removeDescription: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 4.dp)
            .heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank() && subtitle != title) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = removeDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LibraryTab.labelRes(): Int = when (this) {
    LibraryTab.BOOKMARKS -> R.string.library_bookmarks
    LibraryTab.HISTORY -> R.string.library_history
}

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').removePrefix("www.")

/**
 * The host, plus when the page was visited when that is known.
 *
 * `DateUtils` is used rather than a hand-rolled "5m ago": it is localised, it
 * knows the user's 12/24-hour setting, and it collapses weeks into dates without
 * a calendar of edge cases to maintain.
 */
private fun subtitleFor(entry: HistoryEntry): String {
    val host = hostOf(entry.url)
    if (entry.visitedAtMillis <= 0L) return host
    val when_ = DateUtils.getRelativeTimeSpanString(
        entry.visitedAtMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return if (host.isBlank()) when_ else "$host · $when_"
}
