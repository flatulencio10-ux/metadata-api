package com.customlauncher.widgets.sticker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.LruCache
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Blend modes offered for stickers (mix with what is drawn beneath on the same page). */
@Serializable
enum class StickerBlend { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, DIFFERENCE }

/**
 * Non-destructive sticker effects, stored in the sticker's config. Sizes are in percent of
 * the sticker's longest side so they look the same at any scale.
 */
@Serializable
data class StickerEffects(
    val outlineWidth: Float = 0f,
    val outlineColor: Long = 0xFFFFFFFF,
    val shadowBlur: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 2f,
    val shadowColor: Long = 0x99000000,
    val glowRadius: Float = 0f,
    val glowColor: Long = 0xFF7FD3FF,
    val tintAmount: Float = 0f,
    val tintColor: Long = 0xFFFF4FA3,
    val blend: StickerBlend = StickerBlend.NORMAL,
) {
    val hasPixelEffects: Boolean get() = outlineWidth > 0f || shadowBlur > 0f || glowRadius > 0f || tintAmount > 0f
}

/** Renders a sticker bitmap with outline / shadow / glow / tint into a box of the requested size. */
object StickerRenderer {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Decodes [file] at roughly [boxW] x [boxH] (never full resolution) and draws the effects.
     * The image is inset so outlines/shadows fit inside the box. Cached by all inputs.
     */
    fun render(file: File, boxW: Int, boxH: Int, fx: StickerEffects): Bitmap? {
        if (boxW <= 0 || boxH <= 0) return null
        val key = "${file.path}|${file.lastModified()}|$boxW|$boxH|$fx"
        cache.get(key)?.let { return it }
        val src = decodeAtSize(file, boxW, boxH) ?: return null
        val out = applyEffects(src, boxW, boxH, fx)
        if (out !== src) src.recycle()
        cache.put(key, out)
        return out
    }

    fun decodeAtSize(file: File, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun applyEffects(src: Bitmap, boxW: Int, boxH: Int, fx: StickerEffects): Bitmap {
        val unit = max(boxW, boxH) / 100f
        val outline = fx.outlineWidth * unit
        val shadow = if (fx.shadowBlur > 0f) fx.shadowBlur * unit + max(kotlin.math.abs(fx.shadowDx), kotlin.math.abs(fx.shadowDy)) * unit else 0f
        val glow = fx.glowRadius * unit
        val margin = max(outline, max(shadow, glow))

        // Fit the image (aspect preserved) into the box minus the margin.
        val availW = (boxW - 2 * margin).coerceAtLeast(1f)
        val availH = (boxH - 2 * margin).coerceAtLeast(1f)
        val scale = minOf(availW / src.width, availH / src.height)
        val dw = (src.width * scale).toInt().coerceAtLeast(1)
        val dh = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (dw == src.width && dh == src.height) src else Bitmap.createScaledBitmap(src, dw, dh, true)
        if (!fx.hasPixelEffects && margin == 0f) return scaled

        val out = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val left = (boxW - dw) / 2f
        val top = (boxH - dh) / 2f
        val alpha = scaled.extractAlpha()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (glow > 0f) {
            paint.color = fx.glowColor.toInt()
            paint.maskFilter = BlurMaskFilter(glow, BlurMaskFilter.Blur.OUTER)
            canvas.drawBitmap(alpha, left, top, paint)
            canvas.drawBitmap(alpha, left, top, paint)
        }
        if (fx.shadowBlur > 0f) {
            paint.color = fx.shadowColor.toInt()
            paint.maskFilter = BlurMaskFilter(fx.shadowBlur * unit, BlurMaskFilter.Blur.NORMAL)
            canvas.drawBitmap(alpha, left + fx.shadowDx * unit, top + fx.shadowDy * unit, paint)
        }
        paint.maskFilter = null
        if (outline > 0f) {
            // Sticker-style border: the silhouette stamped in a ring around the image.
            paint.color = fx.outlineColor.toInt()
            val steps = 24
            for (i in 0 until steps) {
                val a = 2 * PI * i / steps
                canvas.drawBitmap(alpha, left + (cos(a) * outline).toFloat(), top + (sin(a) * outline).toFloat(), paint)
            }
        }
        canvas.drawBitmap(scaled, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        if (fx.tintAmount > 0f) {
            val tint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(fx.tintColor.toInt(), PorterDuff.Mode.SRC_ATOP)
                this.alpha = (fx.tintAmount.coerceIn(0f, 1f) * 255).toInt()
            }
            canvas.drawBitmap(scaled, left, top, tint)
        }
        alpha.recycle()
        if (scaled !== src) scaled.recycle()
        return out
    }

    fun invalidate(file: File) {
        cache.snapshot().keys.filter { it.startsWith(file.path + "|") }.forEach { cache.remove(it) }
    }
}
