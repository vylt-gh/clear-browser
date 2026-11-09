package dev.clearbrowser.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationManagerTest {

    @Test
    fun formatsZeroAndNegativeSizes() {
        assertEquals("0 B", formatDownloadFileSize(0L))
        assertEquals("0 B", formatDownloadFileSize(-50L))
    }

    @Test
    fun formatsBytesCorrectly() {
        assertEquals("500 B", formatDownloadFileSize(500L))
        assertEquals("1023 B", formatDownloadFileSize(1023L))
    }

    @Test
    fun formatsKilobytesCorrectly() {
        assertEquals("1.0 KB", formatDownloadFileSize(1024L))
        assertEquals("1.5 KB", formatDownloadFileSize(1536L))
    }

    @Test
    fun formatsMegabytesAndGigabytesCorrectly() {
        assertEquals("1.0 MB", formatDownloadFileSize(1024L * 1024L))
        assertEquals("45.5 MB", formatDownloadFileSize((45.5 * 1024 * 1024).toLong()))
        assertEquals("1.8 GB", formatDownloadFileSize((1.8 * 1024 * 1024 * 1024).toLong()))
    }
}
