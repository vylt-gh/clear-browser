package dev.clearbrowser.feature.reading

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.clearbrowser.R
import dev.clearbrowser.core.data.ChromeTheme

/**
 * The reload menu, opened by holding the reload button.
 *
 * ## Why a hold and not a tap
 *
 * Reloading is a button people press without thinking, so the button has to
 * keep doing exactly that. The two things on this menu are rare and slightly
 * destructive -- one throws the cache away, the other ignores it -- and a
 * mis-tap on either is a page the user did not want to fetch again. Holding
 * is the gesture that says "I meant this", and it costs the everyday tap
 * nothing.
 *
 * This is also where every other browser puts these two, which is worth more
 * than any argument about where else they could live.
 */
@Composable
fun ReloadPopupMenu(
    onHardReload: () -> Unit,
    onEmptyCacheAndReload: () -> Unit,
    chromeTheme: ChromeTheme = ChromeTheme.CLEAR,
    modifier: Modifier = Modifier,
) {
    PopupSurface(chromeTheme = chromeTheme, modifier = modifier) {
        PopupMenuItem(
            icon = Icons.Rounded.Bolt,
            label = stringResource(R.string.reload_hard),
            onClick = onHardReload,
        )
        PopupMenuItem(
            icon = Icons.Rounded.DeleteSweep,
            label = stringResource(R.string.reload_empty_cache),
            onClick = onEmptyCacheAndReload,
        )
    }
}

/**
 * Shows the reload menu above the reload button.
 *
 * The button is the anchor, so the menu belongs to the control it was opened
 * from: the bubble's arrow points at it, and the menu moves with it when the
 * chrome is dragged to the other side of the screen.
 */
@Composable
fun ReloadPopup(
    visible: Boolean,
    onHardReload: () -> Unit,
    onEmptyCacheAndReload: () -> Unit,
    onDismiss: () -> Unit,
    chromeTheme: ChromeTheme = ChromeTheme.CLEAR,
) {
    if (!visible) return
    Popup(
        popupPositionProvider = ReloadPopupPositionProvider(LocalDensity.current),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ReloadPopupMenu(
            onHardReload = onHardReload,
            onEmptyCacheAndReload = onEmptyCacheAndReload,
            chromeTheme = chromeTheme,
        )
    }
}

/**
 * Places the reload menu above the button that opened it.
 *
 * The action menu anchors by a fixed distance from its own right edge, which
 * works because it always hangs off the same button at the end of the bar.
 * This one cannot: the reload button sits wherever the chrome is docked, so
 * the arrow is placed on the button's centre instead, and the bubble is
 * pushed back inside the window if that would put it off the edge.
 */
class ReloadPopupPositionProvider(
    private val density: Density,
    private val gap: Dp = 6.dp,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gapPx = with(density) { gap.roundToPx() }
        val marginPx = with(density) { 12.dp.roundToPx() }
        // How far the bubble's right edge has to be from the button's centre
        // for the arrow's tip to land on it: the arrow sits that far in from
        // the right edge, and its tip is half its width further in still.
        val arrowInsetPx = with(density) {
            (BUBBLE_ARROW_OFFSET_FROM_RIGHT + BUBBLE_ARROW_WIDTH / 2).roundToPx()
        }

        val anchorCentre = anchorBounds.left + anchorBounds.width / 2
        val bubbleRight = anchorCentre + arrowInsetPx
        val left = bubbleRight - popupContentSize.width
        val maxLeft = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = left.coerceIn(marginPx, maxLeft)

        // Above the button, and never off the top of the screen: a menu that
        // cannot be seen is worse than one that is in the wrong place.
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = above.coerceAtLeast(marginPx)
        return IntOffset(x, y)
    }
}
