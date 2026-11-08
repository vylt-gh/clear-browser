package dev.clearbrowser.feature.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import dev.clearbrowser.R
import dev.clearbrowser.core.data.ChromeTheme

/**
 * Speech-bubble shape with a triangular arrow/tail on the bottom edge pointing down.
 */
class BubbleShape(
    private val cornerRadius: Dp = 16.dp,
    private val arrowWidth: Dp = 16.dp,
    private val arrowHeight: Dp = 10.dp,
    private val arrowOffsetFromRight: Dp = 16.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cr = with(density) { cornerRadius.toPx() }
        val aw = with(density) { arrowWidth.toPx() }
        val ah = with(density) { arrowHeight.toPx() }
        val aOffset = with(density) { arrowOffsetFromRight.toPx() }

        val w = size.width
        val h = size.height - ah

        val path = Path().apply {
            moveTo(cr, 0f)
            lineTo(w - cr, 0f)
            quadraticTo(w, 0f, w, cr)

            lineTo(w, h - cr)
            quadraticTo(w, h, w - cr, h)

            val arrowRight = w - aOffset
            val arrowLeft = arrowRight - aw
            val arrowTipX = (arrowLeft + arrowRight) / 2f

            lineTo(arrowRight, h)
            lineTo(arrowTipX, size.height)
            lineTo(arrowLeft, h)
            lineTo(cr, h)
            quadraticTo(0f, h, 0f, h - cr)

            lineTo(0f, cr)
            quadraticTo(0f, 0f, cr, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Positions the popup menu directly above the anchor button so that the downward arrow tip
 * aligns with the center of the anchor.
 */
class ChromePopupPositionProvider(
    private val density: Density,
    private val arrowTipOffsetFromRight: Dp = 24.dp,
    private val gap: Dp = 6.dp,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val arrowTipPx = with(density) { arrowTipOffsetFromRight.roundToPx() }
        val gapPx = with(density) { gap.roundToPx() }
        val marginPx = with(density) { 12.dp.roundToPx() }

        val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
        val targetPopupX = anchorCenterX - (popupContentSize.width - arrowTipPx)
        val x = targetPopupX.coerceIn(marginPx, windowSize.width - popupContentSize.width - marginPx)

        val y = anchorBounds.top - popupContentSize.height - gapPx
        return IntOffset(x, y)
    }
}

/**
 * Floating popup menu anchored directly above the three-dots action button.
 */
@Composable
fun ChromePopupMenu(
    visible: Boolean,
    canBookmark: Boolean = false,
    isBookmarked: Boolean = false,
    canShare: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onDismiss: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit = {},
    onExtensions: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onSettings: () -> Unit,
    chromeTheme: ChromeTheme = ChromeTheme.CLEAR,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val shape = when (chromeTheme) {
        ChromeTheme.CLEAR -> BubbleShape(
            cornerRadius = 16.dp,
            arrowWidth = 16.dp,
            arrowHeight = 10.dp,
            arrowOffsetFromRight = 16.dp,
        )
        ChromeTheme.ACRYLIC, ChromeTheme.MATERIAL -> RoundedCornerShape(16.dp)
    }

    val elevation = when (chromeTheme) {
        ChromeTheme.CLEAR -> 16.dp
        ChromeTheme.ACRYLIC -> 8.dp
        ChromeTheme.MATERIAL -> 6.dp
    }

    val spotColor = when (chromeTheme) {
        ChromeTheme.CLEAR -> Color.Black.copy(alpha = 0.45f)
        ChromeTheme.ACRYLIC -> Color.Black.copy(alpha = 0.25f)
        ChromeTheme.MATERIAL -> Color.Black.copy(alpha = 0.20f)
    }

    val borderStroke = when (chromeTheme) {
        ChromeTheme.CLEAR, ChromeTheme.ACRYLIC ->
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ChromeTheme.MATERIAL ->
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }

    Surface(
        modifier = modifier
            .width(215.dp)
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = spotColor,
            )
            .border(borderStroke, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (chromeTheme == ChromeTheme.MATERIAL) 3.dp else 8.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    top = 8.dp,
                    bottom = if (chromeTheme == ChromeTheme.CLEAR) 18.dp else 8.dp,
                    start = 6.dp,
                    end = 6.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PopupMenuItem(
                icon = Icons.Rounded.Bookmarks,
                label = stringResource(R.string.menu_bookmarks),
                onClick = {
                    onDismiss()
                    onBookmarks()
                },
                trailing = if (canBookmark) {
                    {
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isBookmarked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                } else null,
            )
            PopupMenuItem(
                icon = Icons.Rounded.History,
                label = stringResource(R.string.menu_history),
                onClick = {
                    onDismiss()
                    onHistory()
                },
            )
            PopupMenuItem(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.menu_downloads),
                onClick = {
                    onDismiss()
                    onDownloads()
                },
            )
            PopupMenuItem(
                icon = Icons.Rounded.Extension,
                label = stringResource(R.string.menu_extensions),
                onClick = {
                    onDismiss()
                    onExtensions()
                },
            )
            PopupMenuItem(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.menu_find_in_page),
                onClick = {
                    onDismiss()
                    onFindInPage()
                },
            )
            if (canShare) {
                PopupMenuItem(
                    icon = Icons.Rounded.Share,
                    label = stringResource(R.string.menu_share),
                    onClick = {
                        onDismiss()
                        onShare()
                    },
                )
                PopupMenuItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.menu_copy_link),
                    onClick = {
                        onDismiss()
                        onCopyLink()
                    },
                )
            }
            PopupMenuItem(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.menu_settings),
                onClick = {
                    onDismiss()
                    onSettings()
                },
            )
        }
    }
}

@Composable
private fun PopupMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
