package dev.clearbrowser.core.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

/**
 * Samples the top region of the webpage on page load using [PixelCopy],
 * analyzes the dominant vs. average color, and computes the top bar theme color.
 *
 * - Overwhelming majority: If >= 50% of the sampled top region is one color,
 *   that dominant color is chosen (e.g. Hacker News orange `#ff6600`).
 * - Mixed / Diverse: If no single color dominates, computes the average RGB color.
 *
 * Sampled on page load and kept static for the duration of the page.
 */
class TopBarSampler(
    private val onColorSampled: (Int, Boolean) -> Unit, // colorInt, isLight
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isSampling = false
    private var lastSampledUrl: String? = null

    fun reset() {
        lastSampledUrl = null
    }

    /**
     * Request a top bar color sample for [url].
     * If already sampled for this exact [url], skips to prevent redundant work.
     */
    fun sample(root: View, url: String?, force: Boolean = false) {
        if (url.isNullOrBlank() || url == ABOUT_BLANK) return
        if (!force && url == lastSampledUrl) return
        if (isSampling) return

        val surfaceView = findSurfaceView(root) ?: return
        val surface = surfaceView.holder.surface
        if (surface == null || !surface.isValid) return
        val width = surfaceView.width
        val height = surfaceView.height
        if (width <= 0 || height <= 0) return

        isSampling = true

        // Sample top 24 pixels of the webpage
        val sampleHeight = 24.coerceAtMost(height)
        val bmp = Bitmap.createBitmap(width, sampleHeight, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(0, 0, width, sampleHeight)

        try {
            PixelCopy.request(
                surfaceView,
                srcRect,
                bmp,
                { result ->
                    isSampling = false
                    try {
                        if (result == PixelCopy.SUCCESS) {
                            val analysis = analyzeTopBarColor(bmp)
                            if (analysis != null) {
                                lastSampledUrl = url
                                onColorSampled(analysis.color, analysis.isLight)
                            }
                        }
                    } finally {
                        bmp.recycle()
                    }
                },
                mainHandler,
            )
        } catch (_: Throwable) {
            isSampling = false
        }
    }

    private fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findSurfaceView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    data class ColorResult(val color: Int, val isLight: Boolean)

    /**
     * Analyze top bar region:
     * - Clusters pixels into 5-bit color buckets.
     * - If the top cluster has >= 50% majority, returns that cluster's exact color.
     * - Otherwise returns the average color across all valid pixels.
     */
    internal fun analyzeTopBarColor(bitmap: Bitmap): ColorResult? {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 50).coerceAtLeast(1)
        val stepY = 2

        val pixels = ArrayList<Int>((w / stepX + 1) * (h / stepY + 1))
        for (x in 0 until w step stepX) {
            for (y in 0 until h step stepY) {
                pixels.add(bitmap.getPixel(x, y))
            }
        }
        return analyzePixels(pixels)
    }

    internal fun analyzePixels(pixels: List<Int>): ColorResult? {
        val clusterCounts = HashMap<Int, Int>()
        val clusterSumR = HashMap<Int, Long>()
        val clusterSumG = HashMap<Int, Long>()
        val clusterSumB = HashMap<Int, Long>()

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var validCount = 0

        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < 128) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            // 5-bit quantization key (groups close anti-aliased shades)
            val qKey = ((r / 8) shl 10) or ((g / 8) shl 5) or (b / 8)

            clusterCounts[qKey] = (clusterCounts[qKey] ?: 0) + 1
            clusterSumR[qKey] = (clusterSumR[qKey] ?: 0L) + r
            clusterSumG[qKey] = (clusterSumG[qKey] ?: 0L) + g
            clusterSumB[qKey] = (clusterSumB[qKey] ?: 0L) + b

            totalR += r
            totalG += g
            totalB += b
            validCount++
        }

        if (validCount == 0) return null

        val topCluster = clusterCounts.maxByOrNull { it.value } ?: return null
        val majorityThreshold = 0.50f

        val finalR: Int
        val finalG: Int
        val finalB: Int

        if (topCluster.value.toFloat() / validCount >= majorityThreshold) {
            // Overwhelming majority color
            val key = topCluster.key
            finalR = (clusterSumR[key]!! / topCluster.value).toInt()
            finalG = (clusterSumG[key]!! / topCluster.value).toInt()
            finalB = (clusterSumB[key]!! / topCluster.value).toInt()
        } else {
            // Mixed / diverse color: compute average
            finalR = (totalR / validCount).toInt()
            finalG = (totalG / validCount).toInt()
            finalB = (totalB / validCount).toInt()
        }

        val color = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        val lum = 0.2126 * (finalR / 255.0) + 0.7152 * (finalG / 255.0) + 0.0722 * (finalB / 255.0)
        val isLight = lum > 0.45

        return ColorResult(color, isLight)
    }
}
