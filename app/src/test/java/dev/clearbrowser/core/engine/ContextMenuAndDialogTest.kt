package dev.clearbrowser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMenuAndDialogTest {

    @Test
    fun jsDialogDefaultProperties() {
        val dialog = JsDialog(
            message = "test message",
            onAccept = {},
            onDismiss = {},
        )
        assertEquals("test message", dialog.message)
        assertEquals("OK", dialog.confirmText)
        assertEquals("Cancel", dialog.dismissText)
        assertNull(dialog.title)
        assertFalse(dialog.hasInput)
        assertFalse(dialog.withCancel)
    }

    @Test
    fun jsDialogCustomPropertiesForPermissions() {
        var accepted = false
        val dialog = JsDialog(
            title = "Permission Request",
            message = "example.com wants to access Location",
            confirmText = "Allow",
            dismissText = "Block",
            withCancel = true,
            onAccept = { accepted = true },
            onDismiss = {},
        )
        assertEquals("Permission Request", dialog.title)
        assertEquals("Allow", dialog.confirmText)
        assertEquals("Block", dialog.dismissText)
        assertTrue(dialog.withCancel)
        dialog.onAccept(null)
        assertTrue(accepted)
    }

    @Test
    fun contextMenuInfoProperties() {
        val linkInfo = ContextMenuInfo(
            linkUrl = "https://example.com/article",
            title = "Article Title",
        )
        assertEquals("https://example.com/article", linkInfo.linkUrl)
        assertEquals("Article Title", linkInfo.title)
        assertNull(linkInfo.srcUrl)
        assertFalse(linkInfo.isImage)
        assertFalse(linkInfo.isVideo)

        val imageInfo = ContextMenuInfo(
            srcUrl = "https://example.com/image.png",
            isImage = true,
        )
        assertEquals("https://example.com/image.png", imageInfo.srcUrl)
        assertTrue(imageInfo.isImage)
        assertFalse(imageInfo.isVideo)
    }
}
