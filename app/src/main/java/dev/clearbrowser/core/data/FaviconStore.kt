package dev.clearbrowser.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.HashSet

/**
 * Extracts a normalized hostname for favicon lookups from a URL.
 * Returns null if the URL has no host, is blank, or is an internal browser page.
 */
fun extractFaviconHost(url: String): String? {
    if (url.isBlank()) return null
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        return null
    }
    val host = url.substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore(':')
        .removePrefix("www.")
        .trim()
        .lowercase()
    return if (host.isBlank() || host.contains(' ') || !host.contains('.')) null else host
}

/**
 * Privacy-first, local favicon cache and direct fetcher.
 *
 * Favicons are fetched directly from the visited host at `https://${host}/favicon.ico`.
 * No third-party proxy, telemetry, or lookup services are ever contacted.
 *
 * - In-memory [LruCache] keeps decoded bitmaps ready for instant tab switcher rendering.
 * - Disk cache stores PNGs under `cacheDir/favicons/`.
 * - Private browsing tabs never read from or write to disk.
 */
class FaviconStore(
    context: Context,
    private val diskCacheDir: File = File(context.cacheDir, "favicons"),
) {
    private val memoryCache = object : LruCache<String, Bitmap>(60) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val failedHosts = Collections.synchronizedSet(HashSet<String>())

    init {
        diskCacheDir.mkdirs()
    }

    /**
     * Synchronously returns cached favicon from memory if already available.
     */
    fun getFromMemory(host: String): Bitmap? = memoryCache.get(host)

    /**
     * Loads a favicon for the given URL:
     * 1. Check memory cache.
     * 2. If not private, check disk cache.
     * 3. Fetch direct from https://${host}/favicon.ico.
     * 4. Decode PNG or ICO (DIB/PNG).
     * 5. Save to memory cache, and if not private, save to disk.
     */
    suspend fun loadFavicon(url: String, isPrivate: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        val host = extractFaviconHost(url) ?: return@withContext null

        // 1. Memory cache
        memoryCache.get(host)?.let { return@withContext it }

        // Negative cache check
        if (failedHosts.contains(host)) return@withContext null

        // 2. Disk cache (non-private only)
        if (!isPrivate) {
            val diskFile = File(diskCacheDir, "$host.png")
            if (diskFile.exists() && diskFile.length() > 0) {
                val cachedBmp = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
                if (cachedBmp != null) {
                    memoryCache.put(host, cachedBmp)
                    return@withContext cachedBmp
                } else {
                    diskFile.delete()
                }
            }
        }

        // 3. Direct network fetch
        val fetchedBytes = fetchFaviconBytes(host)
        if (fetchedBytes == null || fetchedBytes.isEmpty()) {
            failedHosts.add(host)
            return@withContext null
        }

        // 4. Decode
        val bitmap = FaviconDecoder.decode(fetchedBytes)
        if (bitmap == null) {
            failedHosts.add(host)
            return@withContext null
        }

        // 5. Cache
        memoryCache.put(host, bitmap)
        if (!isPrivate) {
            saveToDisk(host, bitmap)
        }

        return@withContext bitmap
    }

    private fun saveToDisk(host: String, bitmap: Bitmap) {
        runCatching {
            val tempFile = File(diskCacheDir, "$host.tmp")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val destFile = File(diskCacheDir, "$host.png")
            tempFile.renameTo(destFile)
        }
    }

    private fun fetchFaviconBytes(host: String): ByteArray? {
        var currentUrl = "https://$host/favicon.ico"
        var redirects = 0
        val maxRedirects = 3

        while (redirects <= maxRedirects) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:130.0) Gecko/130.0 Clear/0.2")
                conn.setRequestProperty("Accept", "image/png,image/x-icon,image/*,*/*;q=0.8")

                val code = conn.responseCode
                if (code in 301..308) {
                    val location = conn.getHeaderField("Location") ?: return null
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else if (location.startsWith("/")) {
                        val base = URL(currentUrl)
                        "${base.protocol}://${base.host}$location"
                    } else {
                        return null
                    }
                    redirects++
                    continue
                }

                if (code !in 200..299) {
                    return null
                }

                val contentLength = conn.contentLength
                if (contentLength > 512 * 1024) {
                    return null
                }

                conn.inputStream.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val data = ByteArray(4096)
                    var bytesRead: Int
                    var total = 0
                    while (input.read(data).also { bytesRead = it } != -1) {
                        total += bytesRead
                        if (total > 512 * 1024) return null
                        buffer.write(data, 0, bytesRead)
                    }
                    return buffer.toByteArray()
                }
            } catch (e: Exception) {
                return null
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    fun clear() {
        memoryCache.evictAll()
        failedHosts.clear()
        runCatching {
            diskCacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}
