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
 * Samples the top row of pixels from the webpage's rendering surface
 * using [PixelCopy], and determines background brightness.
 *
 * This allows Clear Browser to extend the website's top style into the
 * Android status bar while placing the interactive document safely below it.
 */
class TopBarSampler(
    private val onSample: (Bitmap, Boolean) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isSampling = false
    private var lastSampleTime = 0L

    /**
     * Request a sample of the top pixels of [root].
     */
    fun sample(root: View) {
        val now = System.currentTimeMillis()
        if (isSampling || now - lastSampleTime < 100) return
        val surfaceView = findSurfaceView(root) ?: return
        val surface = surfaceView.holder.surface
        if (surface == null || !surface.isValid) return
        val width = surfaceView.width
        val height = surfaceView.height
        if (width <= 0 || height <= 0) return

        isSampling = true
        lastSampleTime = now

        // Sample the top 2 rows of pixels
        val sampleHeight = 2
        val bmp = Bitmap.createBitmap(width, sampleHeight, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(0, 0, width, sampleHeight)

        try {
            PixelCopy.request(
                surfaceView,
                srcRect,
                bmp,
                { result ->
                    isSampling = false
                    if (result == PixelCopy.SUCCESS) {
                        val centerPixel = bmp.getPixel(width / 2, 0)
                        if (Color.alpha(centerPixel) > 0) {
                            val isLight = isBitmapLight(bmp)
                            onSample(bmp, isLight)
                        }
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

    /**
     * Calculate relative luminance across horizontal sample points:
     * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
     */
    private fun isBitmapLight(bitmap: Bitmap): Boolean {
        var totalLuminance = 0.0
        val w = bitmap.width
        val h = bitmap.height
        val step = (w / 30).coerceAtLeast(1)
        var count = 0
        for (x in 0 until w step step) {
            for (y in 0 until h) {
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color) / 255.0
                val g = Color.green(color) / 255.0
                val b = Color.blue(color) / 255.0
                val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                totalLuminance += lum
                count++
            }
        }
        return if (count > 0) (totalLuminance / count) > 0.55 else true
    }
}
