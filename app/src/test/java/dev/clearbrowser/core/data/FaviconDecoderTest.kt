package dev.clearbrowser.core.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaviconDecoderTest {

    @Test
    fun extractFaviconHost_validUrls() {
        assertEquals("github.com", extractFaviconHost("https://github.com/vylt-gh/clear-browser"))
        assertEquals("google.com", extractFaviconHost("https://www.google.com/search?q=clear"))
        assertEquals("en.wikipedia.org", extractFaviconHost("https://en.wikipedia.org/wiki/Kotlin"))
        assertEquals("reddit.com", extractFaviconHost("http://reddit.com:8080/r/android"))
        assertEquals("sub.domain.co.uk", extractFaviconHost("https://www.sub.domain.co.uk/path"))
    }

    @Test
    fun extractFaviconHost_invalidOrInternalUrls() {
        assertNull(extractFaviconHost("about:blank"))
        assertNull(extractFaviconHost("chrome://settings"))
        assertNull(extractFaviconHost("file:///sdcard/Download/test.html"))
        assertNull(extractFaviconHost("javascript:void(0)"))
        assertNull(extractFaviconHost(""))
        assertNull(extractFaviconHost("   "))
        assertNull(extractFaviconHost("not a url"))
        assertNull(extractFaviconHost("https://"))
    }

    @Test
    fun icoParser_rejectsCorruptOrTruncatedData() {
        assertNull(IcoParser.parse(byteArrayOf()))
        assertNull(IcoParser.parse(byteArrayOf(0, 0, 1)))
        assertNull(IcoParser.parse(ByteArray(22))) // Header saying 0 count
        // Invalid magic type (not 1)
        val badType = byteArrayOf(0, 0, 2, 0, 1, 0)
        assertNull(IcoParser.parse(badType))
    }

    @Test
    fun icoParser_parsesEmbeddedPng() {
        val fakePng = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            1, 2, 3, 4,
        )

        val buf = ByteBuffer.allocate(6 + 16 + fakePng.size).order(ByteOrder.LITTLE_ENDIAN)
        // ICO Header
        buf.putShort(0) // reserved
        buf.putShort(1) // type = icon
        buf.putShort(1) // count = 1
        // Entry
        buf.put(32.toByte()) // width
        buf.put(32.toByte()) // height
        buf.put(0) // colors
        buf.put(0) // reserved
        buf.putShort(1) // planes
        buf.putShort(32) // bpp
        buf.putInt(fakePng.size) // size
        buf.putInt(22) // offset
        // PNG Payload
        buf.put(fakePng)

        val result = IcoParser.parse(buf.array())
        assertNotNull(result)
        assertEquals(32, result!!.width)
        assertEquals(32, result.height)
        assertTrue(result.isPng)
        assertArrayEquals(fakePng, result.pngBytes)
        assertNull(result.dibPixels)
    }

    @Test
    fun icoParser_parses32BitDibWithAlpha() {
        val w = 2
        val h = 2
        val xorStride = ((w * 32 + 31) / 32) * 4 // 8 bytes
        val xorBytes = ByteArray(xorStride * h)

        // Bottom row in file (which is y = 1 in final bitmap): 2 blue pixels BGRA (255, 0, 0, 255)
        xorBytes[0] = 255.toByte(); xorBytes[1] = 0; xorBytes[2] = 0; xorBytes[3] = 255.toByte()
        xorBytes[4] = 255.toByte(); xorBytes[5] = 0; xorBytes[6] = 0; xorBytes[7] = 255.toByte()

        // Top row in file (which is y = 0 in final bitmap): 2 red pixels BGRA (0, 0, 255, 255)
        xorBytes[8] = 0; xorBytes[9] = 0; xorBytes[10] = 255.toByte(); xorBytes[11] = 255.toByte()
        xorBytes[12] = 0; xorBytes[13] = 0; xorBytes[14] = 255.toByte(); xorBytes[15] = 255.toByte()

        val andStride = ((w + 31) / 32) * 4 // 4 bytes
        val andBytes = ByteArray(andStride * h) // all 0 = opaque

        val dibHeaderSize = 40
        val resSize = dibHeaderSize + xorBytes.size + andBytes.size
        val totalSize = 6 + 16 + resSize

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        // ICO Header
        buf.putShort(0)
        buf.putShort(1)
        buf.putShort(1)
        // Entry
        buf.put(w.toByte())
        buf.put(h.toByte())
        buf.put(0)
        buf.put(0)
        buf.putShort(1)
        buf.putShort(32)
        buf.putInt(resSize)
        buf.putInt(22)
        // BITMAPINFOHEADER
        buf.putInt(40) // biSize
        buf.putInt(w) // biWidth
        buf.putInt(h * 2) // biHeight (XOR + AND mask height)
        buf.putShort(1) // biPlanes
        buf.putShort(32) // biBitCount
        buf.putInt(0) // biCompression
        buf.putInt(xorBytes.size) // biSizeImage
        buf.putInt(0)
        buf.putInt(0)
        buf.putInt(0)
        buf.putInt(0)
        // XOR mask
        buf.put(xorBytes)
        // AND mask
        buf.put(andBytes)

        val result = IcoParser.parse(buf.array())
        assertNotNull(result)
        assertEquals(w, result!!.width)
        assertEquals(h, result.height)
        org.junit.Assert.assertFalse(result.isPng)
        assertNotNull(result.dibPixels)

        val pixels = result.dibPixels!!
        assertEquals(4, pixels.size)
        // Top row (y=0) should be Red: 0xFFFF0000.toInt()
        assertEquals(0xFFFF0000.toInt(), pixels[0])
        assertEquals(0xFFFF0000.toInt(), pixels[1])
        // Bottom row (y=1) should be Blue: 0xFF0000FF.toInt()
        assertEquals(0xFF0000FF.toInt(), pixels[2])
        assertEquals(0xFF0000FF.toInt(), pixels[3])
    }

    @Test
    fun icoParser_parses4BitDibWithPaletteAndAndMask() {
        val w = 2
        val h = 2
        val numColors = 16
        val paletteBytes = ByteArray(numColors * 4)
        // Palette 0: Red (BGR: 0, 0, 255)
        paletteBytes[0] = 0; paletteBytes[1] = 0; paletteBytes[2] = 255.toByte(); paletteBytes[3] = 0
        // Palette 1: Green (BGR: 0, 255, 0)
        paletteBytes[4] = 0; paletteBytes[5] = 255.toByte(); paletteBytes[6] = 0; paletteBytes[7] = 0

        val xorStride = ((w * 4 + 31) / 32) * 4 // 4 bytes per row
        val xorBytes = ByteArray(xorStride * h)
        // 2 pixels per byte (high nibble = pixel 0, low nibble = pixel 1)
        // Row 0 in file (y=1): pixel 0 = color 1 (green), pixel 1 = color 0 (red) -> 0x10
        xorBytes[0] = 0x10.toByte()
        // Row 1 in file (y=0): pixel 0 = color 0 (red), pixel 1 = color 1 (green) -> 0x01
        xorBytes[xorStride] = 0x01.toByte()

        val andStride = ((w + 31) / 32) * 4 // 4 bytes per row
        val andBytes = ByteArray(andStride * h) // all 0 = opaque

        val dibHeaderSize = 40
        val resSize = dibHeaderSize + paletteBytes.size + xorBytes.size + andBytes.size
        val totalSize = 6 + 16 + resSize

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        // ICO Header
        buf.putShort(0)
        buf.putShort(1)
        buf.putShort(1)
        // Entry
        buf.put(w.toByte())
        buf.put(h.toByte())
        buf.put(0)
        buf.put(0)
        buf.putShort(1)
        buf.putShort(4)
        buf.putInt(resSize)
        buf.putInt(22)
        // BITMAPINFOHEADER
        buf.putInt(40)
        buf.putInt(w)
        buf.putInt(h * 2)
        buf.putShort(1)
        buf.putShort(4)
        buf.putInt(0)
        buf.putInt(xorBytes.size)
        buf.putInt(0)
        buf.putInt(0)
        buf.putInt(numColors)
        buf.putInt(0)
        // Palette
        buf.put(paletteBytes)
        // XOR
        buf.put(xorBytes)
        // AND
        buf.put(andBytes)

        val result = IcoParser.parse(buf.array())
        assertNotNull(result)
        assertEquals(w, result!!.width)
        assertEquals(h, result.height)
        assertNotNull(result.dibPixels)

        val pixels = result.dibPixels!!
        // Top row (y=0): red (0), green (1)
        assertEquals(0xFFFF0000.toInt(), pixels[0])
        assertEquals(0xFF00FF00.toInt(), pixels[1])
        // Bottom row (y=1): green (1), red (0)
        assertEquals(0xFF00FF00.toInt(), pixels[2])
        assertEquals(0xFFFF0000.toInt(), pixels[3])
    }
}
