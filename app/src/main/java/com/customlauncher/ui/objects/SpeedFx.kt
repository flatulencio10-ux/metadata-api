package com.customlauncher.ui.objects

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.SpeedVisuals
import com.customlauncher.physics.BodyState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin

/** Recent world positions of a body, for motion trails (plain arrays: written while drawing). */
class TrailBuffer(size: Int = 6) {
    val xs = FloatArray(size)
    val ys = FloatArray(size)
    var count = 0
    var head = 0
    fun push(x: Float, y: Float) {
        xs[head] = x; ys[head] = y
        head = (head + 1) % xs.size
        if (count < xs.size) count++
    }
}

/**
 * Speed-reactive drawing for a simulated node (applied inside its rotated layer):
 * radial motion blur by spin speed, zoetrope frames above a threshold, stretch along a fast
 * throw, ghost trails and speed lines. All state is read at draw time: no recomposition.
 */
fun Modifier.speedFx(body: BodyState, v: SpeedVisuals, frames: ImageBitmap?, trail: TrailBuffer): Modifier = drawWithContent {
    val rpm = abs(body.rpm)
    val rotationRad = body.rotation * PI.toFloat() / 180f

    // Zoetrope: frames advance with the rotation angle and the drawing is counter-rotated,
    // so a fast spin looks like a stable, living animation.
    if (frames != null && rpm >= v.framesFromRpm) {
        val count = (v.frameCols * v.frameRows).coerceAtLeast(1)
        val frame = (floor(abs(body.spin) / (360f / count)).toInt()) % count
        rotate(-body.rotation) { drawSpriteFrame(frames, frame, v.frameCols, v.frameRows) }
        return@drawWithContent
    }

    // World velocity rotated into the node's local (rotated) frame.
    fun toLocal(dx: Float, dy: Float): Offset {
        val c = cos(-rotationRad)
        val s = sin(-rotationRad)
        return Offset(dx * c - dy * s, dx * s + dy * c)
    }

    if (v.trails && body.speed > 250f) {
        trail.push(body.x, body.y)
        for (i in 1 until trail.count) {
            val idx = (trail.head - 1 - i + trail.xs.size * 2) % trail.xs.size
            val d = toLocal((trail.xs[idx] - body.x).dp.toPx(), (trail.ys[idx] - body.y).dp.toPx())
            withAlpha(0.35f * (1f - i / trail.count.toFloat())) { translate(d.x, d.y) { this@drawWithContent.drawContent() } }
        }
    } else trail.count = 0

    if (v.speedLines && body.speed > 600f) {
        val dir = toLocal(-body.vx, -body.vy)
        val len = dir.getDistance().coerceAtLeast(1f)
        val n = Offset(dir.x / len, dir.y / len)
        val perp = Offset(-n.y, n.x)
        for (k in -1..1) {
            val start = center + n * (size.minDimension * 0.55f) + perp * (k * size.minDimension * 0.25f)
            drawLine(Color.White.copy(alpha = 0.5f), start, start + n * (body.speed / 20f).coerceAtMost(size.minDimension), 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }

    when {
        v.motionBlur && rpm >= v.blurFromRpm -> {
            // Radial blur: ghost copies rotated back along the spin direction.
            val strength = ((rpm - v.blurFromRpm) / (v.framesFromRpm - v.blurFromRpm).coerceAtLeast(1f)).coerceIn(0f, 1f)
            val spread = 10f + 50f * strength
            val ghosts = 6
            for (i in ghosts downTo 1) {
                withAlpha(0.28f * (1f - i / (ghosts + 1f))) {
                    rotate(-sign(body.rpm) * spread * i / ghosts) { this@drawWithContent.drawContent() }
                }
            }
            drawContent()
        }
        v.stretchWhenFast && body.speed > 700f -> {
            val s = ((body.speed - 700f) / 5000f).coerceIn(0f, 0.35f)
            val dir = toLocal(body.vx, body.vy)
            val angle = atan2(dir.y, dir.x) * 180f / PI.toFloat()
            rotate(angle) { scale(1f + s, 1f - s * 0.5f) { rotate(-angle) { this@drawWithContent.drawContent() } } }
        }
        else -> drawContent()
    }
}

private inline fun ContentDrawScope.withAlpha(alpha: Float, block: ContentDrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-size.width, -size.height, size.width * 2, size.height * 2), Paint().apply { this.alpha = alpha })
        block()
        canvas.restore()
    }
}
