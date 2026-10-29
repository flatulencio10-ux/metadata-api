package com.customlauncher.widgets.sticker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * Low-resolution alpha channel of a sticker, used to decide whether a touch hits an opaque
 * pixel. 96 px on the long side is plenty for hit testing and costs ~9 KB.
 */
class AlphaMask(private val width: Int, private val height: Int, private val alpha: ByteArray, val aspect: Float) {

    /** [p] is in a box of [box] size where the image is drawn with ContentScale.Fit. */
    fun isOpaque(p: Offset, box: IntSize, threshold: Int = 24): Boolean {
        if (box.width == 0 || box.height == 0) return false
        // Fit: the image occupies a centered rect with the image's aspect ratio.
        val boxAspect = box.width.toFloat() / box.height
        val (w, h) = if (aspect > boxAspect) box.width.toFloat() to box.width / aspect else box.height * aspect to box.height.toFloat()
        val left = (box.width - w) / 2f
        val top = (box.height - h) / 2f
        val u = (p.x - left) / w
        val v = (p.y - top) / h
        if (u !in 0f..1f || v !in 0f..1f) return false
        val x = (u * (width - 1)).toInt()
        val y = (v * (height - 1)).toInt()
        return (alpha[y * width + x].toInt() and 0xFF) > threshold
    }

    companion object {
        private val cache = LruCache<String, AlphaMask>(64)

        /** Returns null for formats without a decodable bitmap (SVG, Lottie): those use their bounds. */
        fun load(file: File): AlphaMask? {
            cache.get(file.path)?.let { return it }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val mask = fromBitmap(bmp, bounds.outWidth.toFloat() / bounds.outHeight)
            bmp.recycle()
            cache.put(file.path, mask)
            return mask
        }

        fun fromBitmap(bmp: Bitmap, aspect: Float = bmp.width.toFloat() / bmp.height): AlphaMask {
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val alpha = ByteArray(pixels.size) { (pixels[it] ushr 24).toByte() }
            return AlphaMask(bmp.width, bmp.height, alpha, aspect)
        }

        fun invalidate(file: File) = cache.remove(file.path)
    }
}

/**
 * Taps on opaque pixels call [onTap]; taps on transparent pixels are ignored and, thanks to
 * [shareWithSiblings], also reach whatever is drawn underneath the sticker.
 */
fun Modifier.alphaTappable(mask: AlphaMask?, enabled: Boolean, onTap: (Offset) -> Unit): Modifier =
    this
        .pointerInput(mask, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                if (mask != null && !mask.isOpaque(down.position, size)) return@awaitEachGesture
                down.consume()
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                up.consume()
                onTap(down.position)
            }
        }
        .shareWithSiblings()

/** Lets nodes drawn below this one receive the same touches (needed for click-through). */
fun Modifier.shareWithSiblings(): Modifier = this.then(ShareWithSiblingsElement)

private object ShareWithSiblingsElement : ModifierNodeElement<ShareWithSiblingsNode>() {
    override fun create() = ShareWithSiblingsNode()
    override fun update(node: ShareWithSiblingsNode) = Unit
    override fun hashCode() = 7
    override fun equals(other: Any?) = other === this
}

private class ShareWithSiblingsNode : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit
    override fun onCancelPointerInput() = Unit
    override fun sharePointerInputWithSiblings() = true
}
