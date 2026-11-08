package dev.clearbrowser

import android.app.Application
import dev.clearbrowser.core.data.BookmarkStore
import dev.clearbrowser.core.data.FaviconStore
import dev.clearbrowser.core.data.HistoryStore
import dev.clearbrowser.core.data.SettingsStore

class ClearBrowserApp : Application() {

    /**
     * A process-wide singleton. Small, immutable, and read on nearly every
     * screen, which is the case SharedPreferences-with-a-StateFlow exists for.
     */
    val settings: SettingsStore by lazy { SettingsStore(this) }

    /**
     * The library files. Lazy for the same reason `SettingsStore` is: eagerly
     * touching `filesDir` from `onCreate` would race `attachBaseContext`.
     */
    val history: HistoryStore by lazy { HistoryStore.forContext(this) }
    val bookmarks: BookmarkStore by lazy { BookmarkStore.forContext(this) }
    val faviconStore: FaviconStore by lazy { FaviconStore(this) }
}
