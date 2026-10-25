package com.customlauncher.gestures

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A 2D point; kept framework-free so the recogniser is unit-testable on the JVM. */
data class Pt(val x: Float, val y: Float)

/**
 * Simple single-stroke letter recogniser (a rotation-sensitive variant of the "$1"
 * recogniser): strokes are resampled to [N] points, scaled uniformly into a unit box,
 * centred, and compared point-by-point with letter templates. Each template is also
 * matched reversed, so the drawing direction doesn't matter.
 *
 * Add a letter by adding a polyline to [templates] (coordinates in 0..1, y pointing down).
 */
object LetterRecognizer {
    private const val N = 48
    /** Average point distance (in unit-box units) above which nothing is recognised. */
    private const val MAX_SCORE = 0.2f

    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, fromDeg: Float, toDeg: Float, steps: Int = 24): List<Pt> =
        (0..steps).map { i ->
            val a = (fromDeg + (toDeg - fromDeg) * i / steps) * PI / 180.0
            Pt(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }

    /** Angles: 0° = right, 90° = down (screen coordinates). */
    private val templates: Map<String, List<List<Pt>>> = mapOf(
        "C" to listOf(arc(0.5f, 0.5f, 0.5f, 0.5f, -45f, -315f)),
        "O" to listOf(arc(0.5f, 0.5f, 0.5f, 0.5f, -90f, -450f)),
        "L" to listOf(listOf(Pt(0f, 0f), Pt(0f, 1f), Pt(0.7f, 1f))),
        "M" to listOf(listOf(Pt(0f, 1f), Pt(0f, 0f), Pt(0.5f, 0.6f), Pt(1f, 0f), Pt(1f, 1f))),
        "N" to listOf(listOf(Pt(0f, 1f), Pt(0f, 0f), Pt(1f, 1f), Pt(1f, 0f))),
        "W" to listOf(listOf(Pt(0f, 0f), Pt(0.25f, 1f), Pt(0.5f, 0.35f), Pt(0.75f, 1f), Pt(1f, 0f))),
        "V" to listOf(listOf(Pt(0f, 0f), Pt(0.5f, 1f), Pt(1f, 0f))),
        "Z" to listOf(listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(0f, 1f), Pt(1f, 1f))),
        "U" to listOf(listOf(Pt(0f, 0f), Pt(0f, 0.6f)) + arc(0.5f, 0.6f, 0.5f, 0.4f, 180f, 0f) + listOf(Pt(1f, 0f))),
        "S" to listOf(arc(0.5f, 0.25f, 0.45f, 0.25f, -30f, -270f) + arc(0.5f, 0.75f, 0.45f, 0.25f, -90f, 150f)),
        "E" to listOf(arc(0.5f, 0.3f, 0.4f, 0.3f, -40f, -300f, 12) + arc(0.5f, 0.7f, 0.45f, 0.3f, -110f, -320f, 12)),
        "G" to listOf(arc(0.5f, 0.5f, 0.5f, 0.5f, -45f, -360f) + listOf(Pt(0.55f, 0.5f))),
    ).mapValues { (_, variants) -> variants.flatMap { listOf(normalize(it), normalize(it.reversed())) } }

    val letters: Set<String> get() = templates.keys

    /** @return the best matching letter, or null when nothing is close enough. */
    fun recognize(stroke: List<Pt>): String? {
        if (stroke.size < 5) return null
        val candidate = normalize(stroke)
        var best: String? = null
        var bestScore = Float.MAX_VALUE
        for ((letter, variants) in templates) {
            for (t in variants) {
                val score = distance(candidate, t)
                if (score < bestScore) {
                    bestScore = score; best = letter
                }
            }
        }
        return if (bestScore <= MAX_SCORE) best else null
    }

    private fun distance(a: List<Pt>, b: List<Pt>): Float =
        a.indices.sumOf { i -> hypot(a[i].x - b[i].x, a[i].y - b[i].y).toDouble() }.toFloat() / a.size

    private fun normalize(points: List<Pt>): List<Pt> {
        val resampled = resample(points, N)
        val minX = resampled.minOf { it.x }
        val minY = resampled.minOf { it.y }
        val scale = max(resampled.maxOf { it.x } - minX, resampled.maxOf { it.y } - minY).takeIf { it > 0f } ?: 1f
        val scaled = resampled.map { Pt((it.x - minX) / scale, (it.y - minY) / scale) }
        val cx = scaled.sumOf { it.x.toDouble() }.toFloat() / scaled.size
        val cy = scaled.sumOf { it.y.toDouble() }.toFloat() / scaled.size
        return scaled.map { Pt(it.x - cx, it.y - cy) }
    }

    /** Resamples a polyline into [n] equidistant points. */
    private fun resample(points: List<Pt>, n: Int): List<Pt> {
        val length = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
        if (length == 0f) return List(n) { points.first() }
        val interval = length / (n - 1)
        val src = points.toMutableList()
        val out = mutableListOf(src.first())
        var acc = 0f
        var i = 1
        while (i < src.size) {
            val a = src[i - 1]
            val b = src[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            if (acc + d >= interval && d > 0f) {
                val t = (interval - acc) / d
                val q = Pt(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                out += q
                src.add(i, q)
                acc = 0f
            } else {
                acc += d
            }
            i++
        }
        while (out.size < n) out += src.last()
        return out.take(n)
    }
}
