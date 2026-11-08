package dev.clearbrowser.feature.reading

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.clearbrowser.core.data.ChromeTheme
import dev.clearbrowser.core.data.extractFaviconHost
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R

/**
 * The tab switcher.
 *
 * ## Why a list and not a grid of previews
 *
 * A grid of page thumbnails is the nicer picture, and it needs a screenshot of
 * every tab: a `WebView.draw` into a bitmap the size of the screen, held in
 * memory for every tab, refreshed whenever a page paints. That is a large amount
 * of work and memory to answer "which of these is the article I was reading",
 * which a title and a hostname already answer.
 *
 * So this is a list, and the money is spent on the parts that make a list fast
 * to use with one hand: the whole row is the target, the close button is a
 * separate small target at the trailing edge, and the tab you are on is the only
 * one with a filled background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(
    tabs: List<Tab>,
    activeTabId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
    favicons: Map<String, Bitmap> = emptyMap(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    .padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tabs_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onNewTab) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.tabs_new),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(tabs, key = { it.id }) { tab ->
                    val host = extractFaviconHost(tab.url) ?: ""
                    val favicon = if (host.isNotEmpty()) favicons[host] else null
                    TabRow(
                        tab = tab,
                        active = tab.id == activeTabId,
                        favicon = favicon,
                        onSelect = { onSelect(tab.id) },
                        onClose = { onClose(tab.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabRow(
    tab: Tab,
    active: Boolean,
    favicon: Bitmap?,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val title = tab.title.ifBlank {
        tab.url.substringAfter("://").substringBefore('/').ifBlank {
            stringResource(R.string.tabs_blank)
        }
    }
    val host = tab.url.substringAfter("://").substringBefore('/').removePrefix("www.")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            // Filled only for the current tab: a list where every row is a card
            // has no way left to say which one you are looking at.
            .background(
                if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
            )
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp)
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading slot: Favicon or Monogram badge (32dp slot for aligned text guides)
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (favicon != null) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            } else {
                val isBlank = tab.url.isBlank() || tab.url == "about:blank"
                val initial = (host.firstOrNull() ?: title.firstOrNull() ?: '•')
                    .uppercaseChar()
                    .toString()
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBlank) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (host.isNotBlank() && host != title) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.tabs_close),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The tab count, as it appears in the bar.
 *
 * A number in a rounded square rather than a stack icon, because the count is
 * the one piece of information here that the icon cannot carry: "how many pages
 * have I left open" is exactly what makes someone tap it. Two digits fit; beyond
 * that the count stops being a count and becomes "a lot", which is what the
 * cap is for.
 */
@Composable
fun TabCounter(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chromeTheme: ChromeTheme = ChromeTheme.CLEAR,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val description = pluralStringResource(R.plurals.tabs_count, count, count)

    val background = when (chromeTheme) {
        ChromeTheme.CLEAR -> if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
        ChromeTheme.ACRYLIC -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        ChromeTheme.MATERIAL -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val border = when (chromeTheme) {
        ChromeTheme.CLEAR -> BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f),
        )
        ChromeTheme.ACRYLIC -> BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )
        ChromeTheme.MATERIAL -> null
    }

    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            // The visible content is a bare number, which a screen reader would
            // read as "three" with no hint of what tapping it does.
            .clickable(onClick = onClick)
            // Merged, because the label has to end up on the same node as the
            // click action. Two sibling nodes read as a number that happens to
            // be next to a button, and screen readers announce nothing useful.
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99" else count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
