package dev.clearbrowser.core.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SitePermissionStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun hasPermissionMatchesExactAndNormalizedOrigins() {
        val file = File(tempFolder.root, "site_permissions.json")
        val store = SitePermissionStore(file)

        store.recordPermission("github.com", "Location")
        store.recordPermission("https://www.example.org", "Camera and Microphone")

        assertTrue(store.hasPermission("github.com", "Location"))
        assertTrue(store.hasPermission("https://github.com", "Location"))
        assertTrue(store.hasPermission("http://www.github.com", "location"))
        assertTrue(store.hasPermission("example.org", "Camera and Microphone"))
        assertTrue(store.hasPermission("example.org", "camera"))

        assertFalse(store.hasPermission("github.com", "Notifications"))
        assertFalse(store.hasPermission("other.com", "Location"))
    }
}
