package dev.clearbrowser.feature.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingUiStateTest {

    @Test
    fun defaultStateHasFullscreenAndFindInPageDisabled() {
        val state = ReadingUiState()
        assertFalse(state.isFullscreen)
        assertFalse(state.findInPageVisible)
        assertFalse(state.menuPopupVisible)
        assertFalse(state.settingsVisible)
        assertFalse(state.extensionsVisible)
        org.junit.Assert.assertNull(state.extensionPopup)
        assertEquals("", state.findQuery)
        assertEquals(0, state.findActiveMatch)
        assertEquals(0, state.findTotalMatches)
    }

    @Test
    fun menuPopupAndSheetsCanBeUpdated() {
        val popupState = ReadingUiState().copy(menuPopupVisible = true)
        assertTrue(popupState.menuPopupVisible)
        assertFalse(popupState.settingsVisible)
        assertFalse(popupState.extensionsVisible)

        val settingsState = ReadingUiState().copy(settingsVisible = true)
        assertTrue(settingsState.settingsVisible)
        assertFalse(settingsState.menuPopupVisible)

        val extensionsState = ReadingUiState().copy(extensionsVisible = true)
        assertTrue(extensionsState.extensionsVisible)
        assertFalse(extensionsState.menuPopupVisible)
    }

    @Test
    fun fullscreenCanBeUpdated() {
        val state = ReadingUiState().copy(isFullscreen = true)
        assertTrue(state.isFullscreen)
    }

    @Test
    fun findInPageStateCanBeUpdated() {
        val state = ReadingUiState().copy(
            findInPageVisible = true,
            findQuery = "clear",
            findActiveMatch = 2,
            findTotalMatches = 5,
        )
        assertTrue(state.findInPageVisible)
        assertEquals("clear", state.findQuery)
        assertEquals(2, state.findActiveMatch)
        assertEquals(5, state.findTotalMatches)
    }
}
