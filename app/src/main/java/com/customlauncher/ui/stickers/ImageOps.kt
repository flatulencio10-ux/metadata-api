package com.customlauncher.ui.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Crop shapes offered by the sticker editor. CUSTOM is drawn with the lasso tool. */
enum class CropShape(val label: String) { NONE("Original"), CIRCLE("Circle"), ROUNDED("Rounded"), STAR("Star"), HEART("Heart"), BLOB("Blob") }

/** Destructive pixel operations used by the sticker editor (all on ARGB_8888 bitmaps). */
object ImageOps {

    /** Loads an editable copy, at most [maxSide] px on the long side to keep undo memory sane. */
    fun loadEditable(file: File, maxSide: Int = 1024): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        val scaled = if (longest > maxSide) {
            val f = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(decoded, (decoded.width * f).toInt(), (decoded.height * f).toInt(), true)
        } else decoded
        return scaled.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun strokePaint(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Eraser: clears pixels under the stroke. */
    fun erase(target: Bitmap, path: Path, width: Float) {
        Canvas(target).drawPath(path, strokePaint(width).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    /** Restore brush: paints the original pixels back under the stroke. */
    fun restore(target: Bitmap, original: Bitmap, path: Path, width: Float) {
        val paint = strokePaint(width).apply {
            shader = BitmapShader(original, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        Canvas(target).drawPath(path, paint)
    }

    /** Keeps only what is inside [path] (lasso cut / crop shapes), anti-aliased. */
    fun keepInside(src: Bitmap, path: Path): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(src, 0f, 0f, null)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        canvas.drawPath(path, mask)
        return out
    }

    fun applyShape(src: Bitmap, shape: CropShape): Bitmap =
        if (shape == CropShape.NONE) src else keepInside(src, shapePath(shape, src.width.toFloat(), src.height.toFloat()))

    /** Shape outlines fitted to a w×h box. */
    fun shapePath(shape: CropShape, w: Float, h: Float): Path = Path().apply {
        val s = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        when (shape) {
            CropShape.NONE -> addRect(0f, 0f, w, h, Path.Direction.CW)
            CropShape.CIRCLE -> addCircle(cx, cy, s / 2f, Path.Direction.CW)
            CropShape.ROUNDED -> addRoundRect(RectF(0f, 0f, w, h), s * 0.2f, s * 0.2f, Path.Direction.CW)
            CropShape.STAR -> {
                val outer = s / 2f
                val inner = outer * 0.45f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val a = -PI / 2 + i * PI / 5
                    val x = cx + (r * cos(a)).toFloat()
                    val y = cy + (r * sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            CropShape.HEART -> {
                val l = cx - s / 2f
                val t = cy - s / 2f
                moveTo(l + s * 0.5f, t + s * 0.92f)
                cubicTo(l + s * 0.05f, t + s * 0.6f, l - s * 0.05f, t + s * 0.18f, l + s * 0.27f, t + s * 0.1f)
                cubicTo(l + s * 0.4f, t + s * 0.07f, l + s * 0.48f, t + s * 0.16f, l + s * 0.5f, t + s * 0.24f)
                cubicTo(l + s * 0.52f, t + s * 0.16f, l + s * 0.6f, t + s * 0.07f, l + s * 0.73f, t + s * 0.1f)
                cubicTo(l + s * 1.05f, t + s * 0.18f, l + s * 0.95f, t + s * 0.6f, l + s * 0.5f, t + s * 0.92f)
                close()
            }
            CropShape.BLOB -> {
                // Smooth organic outline through 8 jittered radii (fixed pattern, so repeatable).
                val radii = floatArrayOf(1f, 0.86f, 0.97f, 0.8f, 0.95f, 0.84f, 1f, 0.9f)
                val pts = radii.indices.map { i ->
                    val a = 2 * PI * i / radii.size
                    val r = s / 2f * radii[i]
                    android.graphics.PointF(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat())
                }
                val mid = { a: android.graphics.PointF, b: android.graphics.PointF -> android.graphics.PointF((a.x + b.x) / 2, (a.y + b.y) / 2) }
                val start = mid(pts.last(), pts.first())
                moveTo(start.x, start.y)
                for (i in pts.indices) {
                    val m = mid(pts[i], pts[(i + 1) % pts.size])
                    quadTo(pts[i].x, pts[i].y, m.x, m.y)
                }
                close()
            }
        }
    }

    /** Crops away fully transparent borders so the sticker's box hugs its content. */
    fun trimTransparent(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (Color.alpha(px[y * w + x]) > 8) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return src
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) return src
        return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    fun toPng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }
}
