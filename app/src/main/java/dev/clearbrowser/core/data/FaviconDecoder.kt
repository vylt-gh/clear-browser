package dev.clearbrowser.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Parsed representation of an ICO image entry or raw image data.
 * Pure Kotlin data structure so header/DIB unpacking can be unit tested without Android SDK stubs.
 */
data class ParsedIco(
    val width: Int,
    val height: Int,
    val isPng: Boolean,
    val pngBytes: ByteArray? = null,
    val dibPixels: IntArray? = null,
)

/**
 * Pure Kotlin parser for Windows .ico container files (containing PNG or DIB entries).
 */
object IcoParser {

    fun parse(data: ByteArray): ParsedIco? {
        if (data.size < 6) return null
        // Header: reserved (0), type (1 = icon), count
        val reserved = readShortLe(data, 0).toInt()
        val type = readShortLe(data, 2).toInt()
        val count = readShortLe(data, 4).toInt() and 0xFFFF
        if (reserved != 0 || type != 1 || count <= 0) return null
        if (data.size < 6 + count * 16) return null

        data class IcoEntry(
            val width: Int,
            val height: Int,
            val bpp: Int,
            val size: Int,
            val offset: Int,
        )

        val entries = ArrayList<IcoEntry>(count)
        for (i in 0 until count) {
            val entryOff = 6 + i * 16
            val rawW = data[entryOff].toInt() and 0xFF
            val rawH = data[entryOff + 1].toInt() and 0xFF
            val w = if (rawW == 0) 256 else rawW
            val h = if (rawH == 0) 256 else rawH
            val bpp = readShortLe(data, entryOff + 6).toInt() and 0xFFFF
            val size = readIntLe(data, entryOff + 8)
            val offset = readIntLe(data, entryOff + 12)
            if (offset >= 0 && size > 0 && offset.toLong() + size.toLong() <= data.size.toLong()) {
                entries.add(IcoEntry(w, h, bpp, size, offset))
            }
        }
        if (entries.isEmpty()) return null

        // Pick best entry: prefer 32px, then 48px, then 64px, then 16px, else closest to 32px
        fun score(e: IcoEntry): Int = when (e.width) {
            32 -> 100
            48 -> 90
            64 -> 80
            16 -> 70
            else -> 60 - kotlin.math.abs(e.width - 32)
        }

        val best = entries.maxByOrNull { score(it) } ?: return null
        val resOff = best.offset
        val resSize = best.size

        // Check if embedded PNG (magic: 0x89, 'P', 'N', 'G')
        if (resSize >= 8 &&
            data[resOff] == 0x89.toByte() &&
            data[resOff + 1] == 0x50.toByte() &&
            data[resOff + 2] == 0x4E.toByte() &&
            data[resOff + 3] == 0x47.toByte()
        ) {
            val pngBytes = data.copyOfRange(resOff, resOff + resSize)
            return ParsedIco(best.width, best.height, isPng = true, pngBytes = pngBytes)
        }

        // Parse DIB
        if (resSize < 40) return null
        val headerSize = readIntLe(data, resOff)
        if (headerSize < 40 || resOff + headerSize > data.size) return null

        val biWidth = readIntLe(data, resOff + 4)
        val rawBiHeight = readIntLe(data, resOff + 8)
        val biHeight = kotlin.math.abs(rawBiHeight) / 2
        val biBitCount = readShortLe(data, resOff + 14).toInt() and 0xFFFF

        if (biWidth <= 0 || biWidth > 512 || biHeight <= 0 || biHeight > 512) return null

        val numColors = if (biBitCount <= 8) {
            val clrUsed = readIntLe(data, resOff + 32)
            if (clrUsed in 1..(1 shl biBitCount)) clrUsed else (1 shl biBitCount)
        } else {
            0
        }

        val palOff = resOff + headerSize
        val xorOff = palOff + numColors * 4
        if (xorOff > data.size) return null

        val xorStride = ((biWidth * biBitCount + 31) / 32) * 4
        val andOff = xorOff + xorStride * biHeight
        val andStride = ((biWidth + 31) / 32) * 4

        if (xorOff + xorStride * biHeight > data.size) return null

        val palette = IntArray(numColors)
        for (c in 0 until numColors) {
            val p = palOff + c * 4
            val b = data[p].toInt() and 0xFF
            val g = data[p + 1].toInt() and 0xFF
            val r = data[p + 2].toInt() and 0xFF
            palette[c] = (r shl 16) or (g shl 8) or b
        }

        val pixels = IntArray(biWidth * biHeight)
        var hasAlpha = false

        if (biBitCount == 32) {
            for (y in 0 until biHeight) {
                val rowY = biHeight - 1 - y
                val rowStart = xorOff + y * xorStride
                for (x in 0 until biWidth) {
                    val px = rowStart + x * 4
                    val b = data[px].toInt() and 0xFF
                    val g = data[px + 1].toInt() and 0xFF
                    val r = data[px + 2].toInt() and 0xFF
                    val a = data[px + 3].toInt() and 0xFF
                    if (a > 0) hasAlpha = true
                    pixels[rowY * biWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            if (!hasAlpha) {
                val hasAndMask = andOff + andStride * biHeight <= data.size
                for (y in 0 until biHeight) {
                    val rowY = biHeight - 1 - y
                    val andRow = andOff + y * andStride
                    for (x in 0 until biWidth) {
                        val a = if (hasAndMask) {
                            val bit = (data[andRow + (x / 8)].toInt() shr (7 - (x % 8))) and 1
                            if (bit != 0) 0 else 255
                        } else {
                            255
                        }
                        val rgb = pixels[rowY * biWidth + x] and 0x00FFFFFF
                        pixels[rowY * biWidth + x] = (a shl 24) or rgb
                    }
                }
            }
        } else if (biBitCount in listOf(1, 4, 8, 24)) {
            val hasAndMask = andOff + andStride * biHeight <= data.size
            for (y in 0 until biHeight) {
                val rowY = biHeight - 1 - y
                val rowStart = xorOff + y * xorStride
                val andRow = andOff + y * andStride
                for (x in 0 until biWidth) {
                    val rgb = when (biBitCount) {
                        24 -> {
                            val px = rowStart + x * 3
                            val b = data[px].toInt() and 0xFF
                            val g = data[px + 1].toInt() and 0xFF
                            val r = data[px + 2].toInt() and 0xFF
                            (r shl 16) or (g shl 8) or b
                        }
                        8 -> {
                            val idx = data[rowStart + x].toInt() and 0xFF
                            if (idx < palette.size) palette[idx] else 0
                        }
                        4 -> {
                            val byteVal = data[rowStart + (x / 2)].toInt() and 0xFF
                            val idx = if (x % 2 == 0) (byteVal shr 4) and 0x0F else byteVal and 0x0F
                            if (idx < palette.size) palette[idx] else 0
                        }
                        1 -> {
                            val bit = (data[rowStart + (x / 8)].toInt() shr (7 - (x % 8))) and 1
                            if (bit < palette.size) palette[bit] else 0
                        }
                        else -> 0
                    }

                    val a = if (hasAndMask) {
                        val bit = (data[andRow + (x / 8)].toInt() shr (7 - (x % 8))) and 1
                        if (bit != 0) 0 else 255
                    } else {
                        255
                    }
                    pixels[rowY * biWidth + x] = (a shl 24) or (rgb and 0x00FFFFFF)
                }
            }
        } else {
            return null
        }

        return ParsedIco(biWidth, biHeight, isPng = false, dibPixels = pixels)
    }

    private fun readShortLe(b: ByteArray, offset: Int): Short {
        return ((b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun readIntLe(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
    }
}

/**
 * Decodes downloaded favicon byte arrays (direct PNG/WEBP/JPEG or ICO with PNG/DIB)
 * into Android [Bitmap]s.
 */
object FaviconDecoder {

    fun decode(data: ByteArray): Bitmap? {
        if (data.isEmpty()) return null

        // If direct PNG (or WEBP/JPEG), try BitmapFactory first
        val isPngMagic = data.size >= 4 &&
            data[0] == 0x89.toByte() &&
            data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() &&
            data[3] == 0x47.toByte()

        if (isPngMagic) {
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bmp != null) return bmp
        }

        // Try parsing as ICO container
        val parsed = runCatching { IcoParser.parse(data) }.getOrNull()
        if (parsed != null) {
            if (parsed.isPng && parsed.pngBytes != null) {
                val bmp = BitmapFactory.decodeByteArray(parsed.pngBytes, 0, parsed.pngBytes.size)
                if (bmp != null) return bmp
            } else if (parsed.dibPixels != null) {
                return runCatching {
                    Bitmap.createBitmap(parsed.dibPixels, parsed.width, parsed.height, Bitmap.Config.ARGB_8888)
                }.getOrNull()
            }
        }

        // Fallback: let BitmapFactory attempt to decode directly
        return runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
    }
}
