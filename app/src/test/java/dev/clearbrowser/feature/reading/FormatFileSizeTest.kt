package dev.clearbrowser.feature.reading

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatFileSizeTest {

    @Test
    fun formatsZeroAndNegativeSizes() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("0 B", formatFileSize(-100L))
    }

    @Test
    fun formatsBytesCorrectly() {
        assertEquals("500 B", formatFileSize(500L))
        assertEquals("1023 B", formatFileSize(1023L))
    }

    @Test
    fun formatsKilobytesCorrectly() {
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 KB", formatFileSize(1536L))
        assertEquals("500.0 KB", formatFileSize(512_000L))
    }

    @Test
    fun formatsMegabytesAndGigabytesCorrectly() {
        assertEquals("1.0 MB", formatFileSize(1024L * 1024L))
        assertEquals("12.5 MB", formatFileSize((12.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", formatFileSize(2L * 1024L * 1024L * 1024L))
    }
}
