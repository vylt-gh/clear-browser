package dev.clearbrowser.core.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One site permission granted by the user. */
data class SitePermission(
    val origin: String,
    val permissionType: String,
    val grantedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * Tracks granted site permissions so the user can review and revoke them.
 */
class SitePermissionStore internal constructor(private val file: File) {

    private val _permissions = MutableStateFlow<List<SitePermission>>(emptyList())
    val permissions: StateFlow<List<SitePermission>> = _permissions.asStateFlow()

    init {
        _permissions.value = readFromDisk()
    }

    fun recordPermission(origin: String, permissionType: String, atMillis: Long = System.currentTimeMillis()) {
        val current = _permissions.value.toMutableList()
        current.removeAll { it.origin == origin && it.permissionType == permissionType }
        current.add(0, SitePermission(origin, permissionType, atMillis))
        _permissions.value = current
        writeToDisk(current)
    }

    fun revoke(origin: String, permissionType: String) {
        val updated = _permissions.value.filterNot { it.origin == origin && it.permissionType == permissionType }
        _permissions.value = updated
        writeToDisk(updated)
    }

    fun revokeOrigin(origin: String) {
        val updated = _permissions.value.filterNot { it.origin == origin }
        _permissions.value = updated
        writeToDisk(updated)
    }

    fun clearAll() {
        _permissions.value = emptyList()
        writeToDisk(emptyList())
    }

    private fun readFromDisk(): List<SitePermission> {
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val origin = obj.optString(KEY_ORIGIN)
                val type = obj.optString(KEY_TYPE)
                if (origin.isBlank() || type.isBlank()) null
                else SitePermission(origin, type, obj.optLong(KEY_GRANTED_AT, 0L))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeToDisk(entries: List<SitePermission>) {
        runCatching {
            if (entries.isEmpty()) {
                AtomicJsonFile.delete(file)
            } else {
                val array = JSONArray()
                for (entry in entries) {
                    array.put(
                        JSONObject().apply {
                            put(KEY_ORIGIN, entry.origin)
                            put(KEY_TYPE, entry.permissionType)
                            put(KEY_GRANTED_AT, entry.grantedAtMillis)
                        },
                    )
                }
                AtomicJsonFile.write(file, array.toString())
            }
        }
    }

    companion object {
        private const val FILE_NAME = "library/site_permissions.json"
        private const val KEY_ORIGIN = "origin"
        private const val KEY_TYPE = "type"
        private const val KEY_GRANTED_AT = "grantedAt"

        fun forContext(context: Context): SitePermissionStore =
            SitePermissionStore(File(context.filesDir, FILE_NAME))
    }
}
