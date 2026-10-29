package com.customlauncher.core.anim

import com.customlauncher.core.model.Direction
import com.customlauncher.core.model.Easing
import com.customlauncher.core.model.EntranceEffect
import com.customlauncher.core.model.IdleEffect
import com.customlauncher.core.model.PathPoint
import com.customlauncher.core.model.TapEffect
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Extra transform an animation applies on top of a node's own transform. Translations in dp,
 * rotation in degrees. [reveal] < 1 clips the node from the left (wipe / typewriter).
 */
data class Motion(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
    val reveal: Float = 1f,
) {
    operator fun plus(o: Motion) = Motion(
        dx + o.dx, dy + o.dy, scaleX * o.scaleX, scaleY * o.scaleY, rotation + o.rotation, alpha * o.alpha, minOf(reveal, o.reveal),
    )

    companion object { val NONE = Motion() }
}

/** Pure animation math (no Android/Compose), shared by the renderer, editor preview and tests. */
object MotionMath {

    fun ease(easing: Easing, t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            Easing.LINEAR -> x
            Easing.EASE -> cubicBezier(0.4f, 0f, 0.2f, 1f, x)
            Easing.SPRING -> 1f - (kotlin.math.cos(x * PI * 3.5) * (1 - x).toDouble().pow(2.2)).toFloat()
            Easing.BOUNCE -> bounceOut(x)
            Easing.OVERSHOOT -> {
                val s = 1.70158f
                val y = x - 1f
                y * y * ((s + 1) * y + s) + 1f
            }
        }
    }

    fun bounceOut(x: Float): Float {
        val n = 7.5625f
        val d = 2.75f
        return when {
            x < 1 / d -> n * x * x
            x < 2 / d -> { val y = x - 1.5f / d; n * y * y + 0.75f }
            x < 2.5 / d -> { val y = x - 2.25f / d; n * y * y + 0.9375f }
            else -> { val y = x - 2.625f / d; n * y * y + 0.984375f }
        }
    }

    /** CSS-style cubic-bezier(x1, y1, x2, y2) evaluated at progress [t] (also used by keyframes). */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, t: Float): Float {
        // Solve x(u) = t for u with Newton iterations, then return y(u).
        var u = t
        repeat(8) {
            val x = bez(u, x1, x2) - t
            val dx = bezDerivative(u, x1, x2)
            if (kotlin.math.abs(dx) < 1e-6f) return@repeat
            u = (u - x / dx).coerceIn(0f, 1f)
        }
        return bez(u, y1, y2)
    }

    private fun bez(u: Float, p1: Float, p2: Float): Float {
        val v = 1 - u
        return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u
    }

    private fun bezDerivative(u: Float, p1: Float, p2: Float): Float {
        val v = 1 - u
        return 3 * v * v * p1 + 6 * v * u * (p2 - p1) + 3 * u * u * (1 - p2)
    }

    /** Entrance at eased progress [p] (0 = start, 1 = at rest). [distance] = fly-in distance in dp. */
    fun entrance(effect: EntranceEffect, p: Float, from: Direction, distance: Float = 420f): Motion {
        val q = 1f - p
        val (ux, uy) = when (from) {
            Direction.LEFT -> -1f to 0f
            Direction.RIGHT -> 1f to 0f
            Direction.TOP -> 0f to -1f
            Direction.BOTTOM -> 0f to 1f
        }
        return when (effect) {
            EntranceEffect.NONE -> Motion.NONE
            EntranceEffect.FADE_IN -> Motion(alpha = p)
            EntranceEffect.FLY_IN -> Motion(dx = ux * distance * q, dy = uy * distance * q)
            EntranceEffect.ZOOM_IN -> Motion(scaleX = p, scaleY = p, alpha = p.coerceIn(0f, 1f))
            EntranceEffect.POP -> Motion(scaleX = p, scaleY = p)
            EntranceEffect.SPIN_IN -> Motion(rotation = -360f * q, scaleX = p, scaleY = p)
            EntranceEffect.BOUNCE_DROP -> Motion(dy = -distance * q)
            EntranceEffect.WIPE -> Motion(reveal = p)
            EntranceEffect.SLIDE_FADE -> Motion(dx = ux * 48f * q, dy = uy * 48f * q, alpha = p)
            EntranceEffect.TYPEWRITER -> Motion(reveal = floor(p.coerceIn(0f, 1f) * 10f) / 10f)
        }
    }

    /** Idle loop at [seconds] since start, period [periodMs], strength [amount]. */
    fun idle(effect: IdleEffect, seconds: Float, periodMs: Int, amount: Float, path: List<PathPoint> = emptyList()): Motion {
        val period = periodMs.coerceAtLeast(100) / 1000f
        val phase = (seconds / period) % 1f
        val s = sin(2 * PI * phase).toFloat()
        return when (effect) {
            IdleEffect.NONE -> Motion.NONE
            IdleEffect.FLOAT -> Motion(dy = s * 8f * amount)
            IdleEffect.WIGGLE -> Motion(rotation = s * 6f * amount)
            IdleEffect.PULSE -> (1f + 0.06f * amount * s).let { Motion(scaleX = it, scaleY = it) }
            IdleEffect.ROTATE -> Motion(rotation = 360f * phase)
            IdleEffect.SWAY -> Motion(rotation = s * 15f * amount)
            IdleEffect.SHAKE -> Motion(dx = sin(2 * PI * phase * 9).toFloat() * 3f * amount, rotation = sin(2 * PI * phase * 7).toFloat() * 2f * amount)
            IdleEffect.BREATHE -> (1f + 0.04f * amount * s).let { Motion(scaleX = it, scaleY = it, alpha = 0.85f + 0.15f * (s + 1) / 2f) }
            IdleEffect.DRIFT -> pointAlong(path, phase).let { Motion(dx = it.x * amount, dy = it.y * amount) }
        }
    }

    /** Tap reaction at progress [p] (0..1, linear; the effect shapes its own curve). */
    fun tap(effect: TapEffect, p: Float): Motion {
        val x = p.coerceIn(0f, 1f)
        val hump = sin(PI * x).toFloat()
        return when (effect) {
            TapEffect.NONE, TapEffect.PLAY_GIF_ONCE -> Motion.NONE
            TapEffect.JUMP -> Motion(dy = -36f * hump, scaleY = 1f + 0.06f * hump)
            TapEffect.SPIN -> Motion(rotation = 360f * MotionMath.ease(Easing.EASE, x))
            TapEffect.SQUASH -> {
                val w = sin(2 * PI * x * 1.5).toFloat() * (1 - x)
                Motion(scaleX = 1f + 0.3f * w, scaleY = 1f - 0.3f * w)
            }
            TapEffect.SHAKE -> Motion(dx = sin(6 * PI * x).toFloat() * 10f * (1 - x))
            // The node hides while its particles fly out and back (see ParticleBurst).
            TapEffect.EXPLODE -> Motion(alpha = if (x in 0.02f..0.96f) 0f else 1f)
        }
    }

    /** Point at fraction [f] of a closed path's length (the path loops back to its start). */
    fun pointAlong(path: List<PathPoint>, f: Float): PathPoint {
        if (path.size < 2) return PathPoint(0f, 0f)
        val pts = path + path.first()
        val lengths = pts.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
        val total = lengths.sum().takeIf { it > 0f } ?: return path.first()
        var remaining = (f.coerceIn(0f, 1f)) * total
        for (i in lengths.indices) {
            if (remaining <= lengths[i] || i == lengths.lastIndex) {
                val t = if (lengths[i] > 0f) (remaining / lengths[i]).coerceIn(0f, 1f) else 0f
                val a = pts[i]; val b = pts[i + 1]
                return PathPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            remaining -= lengths[i]
        }
        return path.first()
    }
}
