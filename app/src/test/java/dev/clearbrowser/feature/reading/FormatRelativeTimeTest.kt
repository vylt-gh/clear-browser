package dev.clearbrowser.feature.reading

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRelativeTimeTest {

    @Test
    fun formatsRelativeTimeCorrectly() {
        val now = 1_000_000_000L
        assertEquals("Just now", formatRelativeTime(now - 30_000L, now))
        assertEquals("5m ago", formatRelativeTime(now - 300_000L, now))
        assertEquals("2h ago", formatRelativeTime(now - 7_200_000L, now))
        assertEquals("3d ago", formatRelativeTime(now - 259_200_000L, now))
        assertEquals("", formatRelativeTime(0L, now))
    }
}
