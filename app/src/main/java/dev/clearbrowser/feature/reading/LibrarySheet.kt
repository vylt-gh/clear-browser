package dev.clearbrowser.feature.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R
import dev.clearbrowser.core.data.Bookmark
import dev.clearbrowser.core.data.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which half of the library a sheet is showing. */
enum class LibraryTab {
    BOOKMARKS,
    HISTORY,
}

private data class DisplayLibraryItem(
    val url: String,
    val title: String,
    val subtitle: String,
)

/**
 * Bookmarks and history, in one sheet.
 *
 * Highly optimized for 120Hz smooth scrolling:
 * - Subtitles and hosts are pre-computed once per list update rather than on every frame.
 * - Relative time uses integer math instead of heavyweight ICU/DateUtils calls.
 * - Fixed row height eliminates expensive 2-pass intrinsic layout measurements.
 * - Nested scroll interception prevents gesture conflict with ModalBottomSheet drag gestures.
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
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmClear by remember { mutableStateOf(false) }

    // Intercept overscroll so LazyColumn doesn't trigger sheet dragging or jitter during fast flings.
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return available.copy(x = 0f)
            }
        }
    }

    val removeBookmarkDesc = stringResource(R.string.library_remove_bookmark)
    val removeHistoryDesc = stringResource(R.string.library_remove_history)

    // Pre-format display items once when list changes so scrolling performs ZERO string or date parsing.
    val displayBookmarks = remember(bookmarks) {
        bookmarks.map { bookmark ->
            val host = hostOf(bookmark.url)
            DisplayLibraryItem(
                url = bookmark.url,
                title = bookmark.title.ifBlank { host },
                subtitle = host,
            )
        }
    }

    val displayHistory = remember(history) {
        val now = System.currentTimeMillis()
        history.map { entry ->
            val host = hostOf(entry.url)
            val time = formatRelativeTime(entry.visitedAtMillis, now)
            val subtitle = when {
                host.isBlank() -> time
                time.isBlank() -> host
                else -> "$host · $time"
            }
            DisplayLibraryItem(
                url = entry.url,
                title = entry.title.ifBlank { host },
                subtitle = subtitle,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.nestedScroll(scrollConnection),
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
                if (tab == LibraryTab.HISTORY && history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.library_clear))
                    }
                }
            }

            when (tab) {
                LibraryTab.BOOKMARKS -> {
                    if (displayBookmarks.isEmpty()) {
                        EmptyLibrary(
                            icon = Icons.Rounded.Bookmarks,
                            title = stringResource(R.string.library_empty_bookmarks),
                            body = stringResource(R.string.library_empty_bookmarks_hint),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        ) {
                            items(
                                items = displayBookmarks,
                                key = { it.url },
                                contentType = { "bookmark_row" },
                            ) { item ->
                                LibraryRow(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    removeDescription = removeBookmarkDesc,
                                    onOpen = { onOpen(item.url) },
                                    onRemove = { onRemoveBookmark(item.url) },
                                )
                            }
                        }
                    }
                }

                LibraryTab.HISTORY -> {
                    if (displayHistory.isEmpty()) {
                        EmptyLibrary(
                            icon = Icons.Rounded.History,
                            title = stringResource(R.string.library_empty_history),
                            body = stringResource(R.string.library_empty_history_hint),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        ) {
                            items(
                                items = displayHistory,
                                key = { it.url },
                                contentType = { "history_row" },
                            ) { item ->
                                LibraryRow(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    removeDescription = removeHistoryDesc,
                                    onOpen = { onOpen(item.url) },
                                    onRemove = { onRemoveHistory(item.url) },
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 4.dp),
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

private fun hostOf(url: String): String {
    val afterScheme = url.substringAfter("://", "")
    if (afterScheme.isEmpty()) return url
    val host = afterScheme.substringBefore('/')
    return if (host.startsWith("www.")) host.substring(4) else host
}

private val dateFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("MMM d", Locale.getDefault())
}

/**
 * High-performance relative time formatter using pure integer arithmetic.
 * Avoids heavyweight ICU JNI calls and DateFormat allocations during scrolling.
 */
internal fun formatRelativeTime(visitedAtMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (visitedAtMillis <= 0L) return ""
    val diffSeconds = (now - visitedAtMillis) / 1000
    if (diffSeconds < 0) return "Just now"
    return when {
        diffSeconds < 60 -> "Just now"
        diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
        diffSeconds < 86400 -> "${diffSeconds / 3600}h ago"
        diffSeconds < 604800 -> "${diffSeconds / 86400}d ago"
        else -> {
            dateFormatter.get()?.format(Date(visitedAtMillis)).orEmpty()
        }
    }
}
