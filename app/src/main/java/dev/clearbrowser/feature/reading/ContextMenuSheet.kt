package dev.clearbrowser.feature.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clearbrowser.R
import dev.clearbrowser.core.engine.ContextMenuInfo

/**
 * Material 3 bottom sheet for long-pressed web elements (links, images, videos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(
    info: ContextMenuInfo,
    onDismiss: () -> Unit,
    onOpenNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Header: Preview of element
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (info.isImage) Icons.Rounded.Image else Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        val titleText = info.title ?: info.linkUrl ?: info.srcUrl.orEmpty()
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val subText = info.linkUrl ?: info.srcUrl.orEmpty()
                        if (subText.isNotBlank() && subText != titleText) {
                            Text(
                                text = subText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            info.linkUrl?.let { link ->
                ContextMenuItem(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = stringResource(R.string.context_menu_open_new_tab),
                    onClick = {
                        onDismiss()
                        onOpenNewTab(link)
                    },
                )
                ContextMenuItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.context_menu_copy_link),
                    onClick = {
                        onDismiss()
                        onCopyLink(link)
                    },
                )
                ContextMenuItem(
                    icon = Icons.Rounded.Share,
                    label = stringResource(R.string.context_menu_share_link),
                    onClick = {
                        onDismiss()
                        onShareLink(link)
                    },
                )
            }

            if (info.isImage && info.srcUrl != null) {
                if (info.linkUrl != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                ContextMenuItem(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = stringResource(R.string.context_menu_open_image_new_tab),
                    onClick = {
                        onDismiss()
                        onOpenNewTab(info.srcUrl)
                    },
                )
                ContextMenuItem(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.context_menu_download_image),
                    onClick = {
                        onDismiss()
                        onDownload(info.srcUrl)
                    },
                )
                ContextMenuItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.context_menu_copy_image_link),
                    onClick = {
                        onDismiss()
                        onCopyLink(info.srcUrl)
                    },
                )
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
