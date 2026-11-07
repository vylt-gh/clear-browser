package dev.clearbrowser.core.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One tab, as it is written to disk.
 *
 * Deliberately small and all primitives. This is written on every `onStop`, so
 * it has to be cheap and it has to survive the process being killed moments
 * later.
 */
data class SavedTab(
    val url: String,
    val title: String = "",
    val scrollY: Int = 0,
)

/**
 * What is needed to put a person back where they were, tabs and all.
 */
data class SessionState(
    val tabs: List<SavedTab> = emptyList(),
    val activeIndex: Int = 0,
    val savedAtMillis: Long = 0L,
) {
    /** False when every tab was blank, so nothing is worth restoring. */
    val hasPage: Boolean
        get() = tabs.any { it.url.isNotBlank() }

    companion object {
        val EMPTY = SessionState()
    }
}

/**
 * Durable session storage, for a session with more than one tab.
 *
 * ## Why a file and not `onSaveInstanceState` alone
 *
 * `onSaveInstanceState` is the fast path and it is the good one: the system
 * persists the Bundle, and it comes back for free after a process death. But it
 * is only written if the process is still alive when the Activity is stopped.
 * A browser is the app most likely to be killed while backgrounded -- the user
 * switches to their authenticator app -- and that is exactly the case where
 * losing the tabs is most annoying.
 *
 * So the tab list is also written to a file on `onStop`. The Bundle gives
 * fidelity (per-tab back history, zoom, scroll) when it survives; the file gives
 * *the tabs back* when it does not.
 *
 * ## Why the Bundle carries the tab list too
 *
 * It could have stored only the WebView states and let the file supply the URLs.
 * It does not, because the two are written at different moments and a mismatch
 * would silently attach one tab's back history to another tab's page. The Bundle
 * is self-contained: if it is present at all, it is the whole session.
 */
class SessionStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    fun save(state: SessionState) {
        if (!state.hasPage) {
            clear()
            return
        }
        val tabs = JSONArray()
        state.tabs.forEach { tab ->
            tabs.put(
                JSONObject().apply {
                    put(KEY_URL, tab.url)
                    put(KEY_TITLE, tab.title)
                    put(KEY_SCROLL, tab.scrollY)
                },
            )
        }
        val json = JSONObject().apply {
            put(KEY_TABS, tabs)
            put(KEY_ACTIVE, state.activeIndex)
            put(KEY_SAVED_AT, state.savedAtMillis)
        }
        // Write to a sibling and rename, so a kill mid-write cannot leave a
        // half-written file that fails to parse on next launch.
        //
        // The temp name is derived from the *full* path, not from FILE_NAME
        // again: FILE_NAME is relative ("session/last.json"), so re-joining it
        // onto the parent directory produces "session/session/last.json.tmp",
        // which does not exist. That write throws, and a bare runCatching
        // swallows it, so the session silently never persists.
        val temp = File(file.absolutePath + ".tmp")
        runCatching {
            file.parentFile?.mkdirs()
            temp.writeText(json.toString())
            if (!temp.renameTo(file)) {
                file.writeText(json.toString())
                temp.delete()
            }
        }.onFailure {
            // Never let session persistence fail silently. If this is empty,
            // session restore is quietly broken and nothing else says so.
            Log.w(TAG, "Could not persist session", it)
        }
    }

    fun load(): SessionState {
        val text = runCatching { file.readText() }.getOrNull() ?: return SessionState.EMPTY
        return runCatching { parse(text) }.getOrElse {
            // A corrupt file must not stop the browser starting.
            runCatching { file.delete() }
            SessionState.EMPTY
        }
    }

    private fun parse(text: String): SessionState {
        val json = JSONObject(text)
        val tabs = json.optJSONArray(KEY_TABS)?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val tab = array.optJSONObject(i) ?: return@mapNotNull null
                SavedTab(
                    // A blank URL is a real tab -- a fresh one the user had open.
                    // Dropping it would renumber the session and point
                    // `activeIndex` at the wrong tab.
                    url = tab.optString(KEY_URL),
                    title = tab.optString(KEY_TITLE),
                    scrollY = tab.optInt(KEY_SCROLL, 0),
                )
            }
        }
        // Sessions written before tabs existed have the URL at the top level.
        // Read them as a one-tab session instead of discarding them: the file is
        // the user's only record of where they were.
        val legacyUrl = json.optString(KEY_URL).takeIf { it.isNotBlank() }
        val resolved = when {
            tabs != null -> tabs
            legacyUrl != null -> listOf(SavedTab(legacyUrl, scrollY = json.optInt(KEY_SCROLL, 0)))
            else -> emptyList()
        }
        return SessionState(
            tabs = resolved,
            activeIndex = json.optInt(KEY_ACTIVE, 0).coerceIn(0, maxOf(0, resolved.lastIndex)),
            savedAtMillis = json.optLong(KEY_SAVED_AT, 0L),
        )
    }

    fun clear() {
        runCatching { file.delete() }
        // Same path derivation as the write, for the same reason: FILE_NAME is
        // relative, so joining it onto the parent directory again would point
        // at a file that does not exist and leave the temp file behind.
        runCatching { File(file.absolutePath + ".tmp").delete() }
    }

    companion object {
        private const val TAG = "ClearBrowser"

        /** Key under which [writeBundle]'s output lives in `onSaveInstanceState`. */
        const val BUNDLE_KEY = "session.tabs"

        private const val FILE_NAME = "session/last.json"
        private const val KEY_TABS = "tabs"
        private const val KEY_ACTIVE = "active"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_SCROLL = "scroll"
        private const val KEY_SAVED_AT = "savedAt"

        private const val BUNDLE_COUNT = "count"
        private const val BUNDLE_ACTIVE = "active"
        private const val BUNDLE_STATE = "state"

        /**
         * Pack the whole session -- tabs plus each engine's own saved state --
         * into one Bundle.
         *
         * [engineStates] is parallel to [tabs] and may contain nulls, which is
         * the normal case for a tab that has never been shown and so has no
         * engine to ask.
         */
        fun writeBundle(
            out: Bundle,
            tabs: List<SavedTab>,
            activeIndex: Int,
            engineStates: List<Bundle?>,
        ) {
            out.putInt(BUNDLE_COUNT, tabs.size)
            out.putInt(BUNDLE_ACTIVE, activeIndex)
            tabs.forEachIndexed { index, tab ->
                out.putString("$KEY_URL.$index", tab.url)
                out.putString("$KEY_TITLE.$index", tab.title)
                out.putInt("$KEY_SCROLL.$index", tab.scrollY)
                engineStates.getOrNull(index)?.let { out.putBundle("$BUNDLE_STATE.$index", it) }
            }
        }

        /** The inverse of [writeBundle]; null when the Bundle holds no session. */
        fun readBundle(bundle: Bundle): Restored? {
            val count = bundle.getInt(BUNDLE_COUNT, 0)
            if (count <= 0) return null
            val tabs = (0 until count).mapNotNull { index ->
                SavedTab(
                    url = bundle.getString("$KEY_URL.$index").orEmpty(),
                    title = bundle.getString("$KEY_TITLE.$index").orEmpty(),
                    scrollY = bundle.getInt("$KEY_SCROLL.$index", 0),
                )
            }
            if (tabs.isEmpty()) return null
            return Restored(
                tabs = tabs,
                activeIndex = bundle.getInt(BUNDLE_ACTIVE, 0).coerceIn(0, tabs.lastIndex),
                engineStates = (0 until count).map { bundle.getBundle("$BUNDLE_STATE.$it") },
            )
        }
    }

    /** A session read back out of a Bundle, with its engines' own state. */
    data class Restored(
        val tabs: List<SavedTab>,
        val activeIndex: Int,
        val engineStates: List<Bundle?>,
    )
}
