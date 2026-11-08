package dev.clearbrowser.feature.reading

import android.app.DownloadManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import dev.clearbrowser.core.data.DownloadItem
import dev.clearbrowser.core.data.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which section of the library a sheet is showing. */
enum class LibraryTab {
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
}

private data class DisplayLibraryItem(
    val url: String,
    val title: String,
    val subtitle: String,
)

private data class DisplayDownloadItem(
    val item: DownloadItem,
    val title: String,
    val subtitle: String,
    val isError: Boolean,
)

/**
 * Bookmarks, history, and downloads in one sheet.
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
    downloads: List<DownloadItem> = emptyList(),
    onSelectTab: (LibraryTab) -> Unit,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenDownload: (DownloadItem) -> Unit = {},
    onRemoveDownload: (Long) -> Unit = {},
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
    val removeDownloadDesc = stringResource(R.string.library_remove_download)

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

    val downloadingLabel = stringResource(R.string.download_status_downloading)
    val downloadFailedLabel = stringResource(R.string.download_status_failed)
    val downloadPausedLabel = stringResource(R.string.download_status_paused)

    val displayDownloads = remember(downloads) {
        val now = System.currentTimeMillis()
        downloads.map { item ->
            val time = formatRelativeTime(item.timestamp, now)
            val size = formatFileSize(item.totalSizeBytes)
            val subtitle = when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (time.isBlank()) size else "$size · $time"
                }
                DownloadManager.STATUS_RUNNING -> {
                    val downloaded = formatFileSize(item.downloadedBytes)
                    if (item.totalSizeBytes > 0) {
                        "$downloadingLabel $downloaded / $size"
                    } else {
                        "$downloadingLabel $downloaded"
                    }
                }
                DownloadManager.STATUS_PAUSED -> downloadPausedLabel
                DownloadManager.STATUS_FAILED -> {
                    if (time.isBlank()) downloadFailedLabel else "$downloadFailedLabel · $time"
                }
                else -> if (time.isBlank()) size else "$size · $time"
            }
            DisplayDownloadItem(
                item = item,
                title = item.title,
                subtitle = subtitle,
                isError = item.status == DownloadManager.STATUS_FAILED,
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

                LibraryTab.DOWNLOADS -> {
                    if (displayDownloads.isEmpty()) {
                        EmptyLibrary(
                            icon = Icons.Rounded.Download,
                            title = stringResource(R.string.library_empty_downloads),
                            body = stringResource(R.string.library_empty_downloads_hint),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        ) {
                            items(
                                items = displayDownloads,
                                key = { it.item.id },
                                contentType = { "download_row" },
                            ) { item ->
                                LibraryRow(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    removeDescription = removeDownloadDesc,
                                    onOpen = { onOpenDownload(item.item) },
                                    onRemove = { onRemoveDownload(item.item.id) },
                                    leadingIcon = {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        ) {
                                            Icon(
                                                imageVector = iconForMime(item.item.mimeType),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .size(20.dp),
                                            )
                                        }
                                    },
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
    leadingIcon: @Composable (() -> Unit)? = null,
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
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(12.dp))
        }
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
    LibraryTab.DOWNLOADS -> R.string.library_downloads
}

private fun iconForMime(mimeType: String?): androidx.compose.ui.graphics.vector.ImageVector {
    val mime = mimeType?.lowercase() ?: ""
    return when {
        mime.startsWith("image/") -> Icons.Rounded.Image
        mime.startsWith("audio/") -> Icons.Rounded.AudioFile
        mime.startsWith("video/") -> Icons.Rounded.VideoFile
        mime == "application/pdf" -> Icons.Rounded.PictureAsPdf
        else -> Icons.Rounded.Description
    }
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

/** Formats byte counts into human-readable units (B, KB, MB, GB). */
internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val b = bytes.toDouble()
    return when {
        b >= gb -> String.format(Locale.US, "%.1f GB", b / gb)
        b >= mb -> String.format(Locale.US, "%.1f MB", b / mb)
        b >= kb -> String.format(Locale.US, "%.1f KB", b / kb)
        else -> "$bytes B"
    }
}
