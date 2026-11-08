package dev.clearbrowser.core.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A single file download recorded by Android's [DownloadManager]. */
data class DownloadItem(
    val id: Long,
    val title: String,
    val mimeType: String?,
    val totalSizeBytes: Long,
    val downloadedBytes: Long,
    val status: Int,
    val localUri: String?,
    val timestamp: Long,
)

/**
 * Manages completed and in-flight downloads by querying Android's system [DownloadManager].
 */
class DownloadsStore(private val context: Context) {

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val downloadManager: DownloadManager? by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val manager = downloadManager ?: return@withContext
        val items = mutableListOf<DownloadItem>()
        runCatching {
            val query = DownloadManager.Query()
            manager.query(query)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val mimeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val currentIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val timeIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) else null
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L
                    val current = if (currentIdx >= 0) cursor.getLong(currentIdx) else 0L
                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_SUCCESSFUL
                    val uri = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                    val timestamp = if (timeIdx >= 0) cursor.getLong(timeIdx) else 0L

                    if (id != -1L) {
                        val cleanedTitle = title?.ifBlank { null }
                            ?: uri?.substringAfterLast('/')?.ifBlank { null }
                            ?: "Download $id"
                        items.add(
                            DownloadItem(
                                id = id,
                                title = cleanedTitle,
                                mimeType = mime,
                                totalSizeBytes = total,
                                downloadedBytes = current,
                                status = status,
                                localUri = uri,
                                timestamp = timestamp,
                            ),
                        )
                    }
                }
            }
        }
        // Most recent first
        _downloads.value = items.sortedByDescending { it.timestamp }
    }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        runCatching {
            downloadManager?.remove(id)
        }
        refresh()
    }

    fun getUriForDownloadedFile(id: Long): Uri? {
        return runCatching { downloadManager?.getUriForDownloadedFile(id) }.getOrNull()
    }

    fun getMimeTypeForDownloadedFile(id: Long): String? {
        return runCatching { downloadManager?.getMimeTypeForDownloadedFile(id) }.getOrNull()
    }
}
