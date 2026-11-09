package dev.clearbrowser.core.data

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.clearbrowser.MainActivity
import dev.clearbrowser.R
import java.util.Collections

/**
 * Manages live-updating status notifications for downloads.
 */
class DownloadNotificationManager(private val context: Context) {

    private val notificationManager: NotificationManager? by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    private val notifiedCompletedIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private val notifiedFailedIds = Collections.synchronizedSet(mutableSetOf<Long>())

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_downloads_desc)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun updateNotifications(items: List<DownloadItem>) {
        if (!canPostNotifications()) return
        val manager = notificationManager ?: return

        for (item in items) {
            when (item.status) {
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    val cancelIntent = Intent(ACTION_CANCEL_DOWNLOAD).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_DOWNLOAD_ID, item.id)
                    }
                    val cancelPendingIntent = PendingIntent.getBroadcast(
                        context,
                        item.id.toInt(),
                        cancelIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle(item.title)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .addAction(
                            android.R.drawable.ic_menu_close_clear_cancel,
                            context.getString(R.string.download_cancel),
                            cancelPendingIntent,
                        )

                    if (item.totalSizeBytes > 0) {
                        val progressPercent = ((item.downloadedBytes * 100) / item.totalSizeBytes).toInt().coerceIn(0, 100)
                        val currentStr = formatDownloadFileSize(item.downloadedBytes)
                        val totalStr = formatDownloadFileSize(item.totalSizeBytes)
                        builder.setContentText("${context.getString(R.string.download_status_downloading)} $currentStr / $totalStr ($progressPercent%)")
                        builder.setProgress(100, progressPercent, false)
                    } else {
                        val currentStr = formatDownloadFileSize(item.downloadedBytes)
                        builder.setContentText("${context.getString(R.string.download_status_downloading)} $currentStr")
                        builder.setProgress(100, 0, true)
                    }

                    manager.notify(item.id.toInt(), builder.build())
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (notifiedCompletedIds.add(item.id)) {
                        val openIntent = Intent(context, MainActivity::class.java).apply {
                            action = ACTION_OPEN_DOWNLOAD
                            putExtra(EXTRA_DOWNLOAD_ID, item.id)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        val openPendingIntent = PendingIntent.getActivity(
                            context,
                            item.id.toInt(),
                            openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )

                        val sizeStr = if (item.totalSizeBytes > 0) formatDownloadFileSize(item.totalSizeBytes) else ""
                        val contentText = if (sizeStr.isNotBlank()) {
                            "${context.getString(R.string.download_status_complete)} · $sizeStr"
                        } else {
                            context.getString(R.string.download_status_complete)
                        }

                        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle(item.title)
                            .setContentText(contentText)
                            .setProgress(0, 0, false)
                            .setOngoing(false)
                            .setAutoCancel(true)
                            .setContentIntent(openPendingIntent)

                        manager.notify(item.id.toInt(), builder.build())
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    if (notifiedFailedIds.add(item.id)) {
                        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_notify_error)
                            .setContentTitle(item.title)
                            .setContentText(context.getString(R.string.download_status_failed))
                            .setProgress(0, 0, false)
                            .setOngoing(false)
                            .setAutoCancel(true)

                        manager.notify(item.id.toInt(), builder.build())
                    }
                }
            }
        }
    }

    fun cancel(id: Long) {
        notificationManager?.cancel(id.toInt())
        notifiedCompletedIds.remove(id)
        notifiedFailedIds.remove(id)
    }

    companion object {
        const val CHANNEL_ID = "clear_downloads"
        const val ACTION_CANCEL_DOWNLOAD = "dev.clearbrowser.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_OPEN_DOWNLOAD = "dev.clearbrowser.ACTION_OPEN_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}

/** Format file size in bytes to human-readable string (B, KB, MB, GB). */
fun formatDownloadFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val b = bytes.toDouble()
    return when {
        b >= gb -> String.format(java.util.Locale.US, "%.1f GB", b / gb)
        b >= mb -> String.format(java.util.Locale.US, "%.1f MB", b / mb)
        b >= kb -> String.format(java.util.Locale.US, "%.1f KB", b / kb)
        else -> "$bytes B"
    }
}
