package com.customlauncher

import com.customlauncher.data.stickers.StickerFormat
import com.customlauncher.data.stickers.StickerKind
import com.customlauncher.ui.edit.NodeBounds
import com.customlauncher.ui.edit.SnapGuides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickerAndGuidesTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun detectsFormats() {
        assertEquals(StickerKind.BITMAP, StickerFormat.detect(bytes(0x89, 'P'.code, 'N'.code, 'G'.code, 13, 10, 26, 10, 0, 0))!!.kind)
        assertEquals("jpg", StickerFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))!!.extension)
        assertEquals(StickerKind.ANIMATED, StickerFormat.detect("GIF89a....".encodeToByteArray())!!.kind)
        assertEquals(StickerKind.SVG, StickerFormat.detect("<?xml version=\"1.0\"?><svg viewBox=\"0 0 10 10\"/>".encodeToByteArray())!!.kind)
        assertEquals(StickerKind.LOTTIE, StickerFormat.detect("{\"v\":\"5.7\",\"w\":100,\"layers\":[]}".encodeToByteArray())!!.kind)
        assertNull(StickerFormat.detect("hello".encodeToByteArray()))
    }

    @Test fun detectsAnimatedPngAndWebp() {
        val apng = bytes(0x89, 'P'.code, 'N'.code, 'G'.code, 13, 10, 26, 10) + "....acTL....".encodeToByteArray()
        assertEquals(StickerKind.ANIMATED, StickerFormat.detect(apng)!!.kind)
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8X".encodeToByteArray() + bytes(0, 0, 0, 0, 0x02, 0, 0, 0)
        assertEquals(StickerKind.ANIMATED, StickerFormat.detect(webp)!!.kind)
    }

    @Test fun snapsToPageCentre() {
        // 100 dp wide node whose centre is 3 dp left of the page centre (180).
        val r = SnapGuides.snap(NodeBounds(127f, 400f, 227f, 500f), emptyList(), 360f, 800f)
        assertEquals(3f, r.dx, 0.001f)
        assertEquals(true, r.guides.any { it.vertical && it.position == 180f })
    }

    @Test fun rotationSnapsTo15Degrees() {
        assertEquals(45f, SnapGuides.snapRotation(43f)!!, 0.001f)
        assertNull(SnapGuides.snapRotation(37f))
    }
}
