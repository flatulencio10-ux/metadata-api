package com.customlauncher.physics

import com.customlauncher.core.model.PathPoint
import kotlin.math.abs

/**
 * Builds physics colliders from image alpha: convex hull of opaque pixels, simplified to the
 * 8 vertices Box2D allows. Framework-free (unit tested).
 */
object ColliderMath {
    const val MAX_VERTICES = 8

    /** [opaque] = row-major w×h flags. Returns a convex polygon in 0..1 fractions, or null if empty. */
    fun hullFromMask(opaque: BooleanArray, w: Int, h: Int): List<PathPoint>? {
        val pts = ArrayList<PathPoint>()
        for (y in 0 until h) {
            // Only the leftmost and rightmost opaque pixel of each row can be on the hull.
            var first = -1
            var last = -1
            for (x in 0 until w) if (opaque[y * w + x]) { if (first < 0) first = x; last = x }
            if (first >= 0) {
                pts += PathPoint(first / (w - 1f).coerceAtLeast(1f), y / (h - 1f).coerceAtLeast(1f))
                pts += PathPoint(last / (w - 1f).coerceAtLeast(1f), y / (h - 1f).coerceAtLeast(1f))
            }
        }
        if (pts.size < 3) return null
        return simplify(convexHull(pts), MAX_VERTICES)
    }

    /** Andrew's monotone chain; counter-clockwise in a y-up frame (clockwise on screen). */
    fun convexHull(points: List<PathPoint>): List<PathPoint> {
        val p = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size < 3) return p
        fun cross(o: PathPoint, a: PathPoint, b: PathPoint) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<PathPoint>()
        for (pt in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), pt) <= 0) lower.removeAt(lower.lastIndex)
            lower += pt
        }
        val upper = ArrayList<PathPoint>()
        for (pt in p.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), pt) <= 0) upper.removeAt(upper.lastIndex)
            upper += pt
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Visvalingam: repeatedly drop the vertex whose triangle with its neighbours is smallest. */
    fun simplify(polygon: List<PathPoint>, max: Int): List<PathPoint> {
        val p = polygon.toMutableList()
        while (p.size > max) {
            var minIndex = 0
            var minArea = Float.MAX_VALUE
            for (i in p.indices) {
                val a = p[(i - 1 + p.size) % p.size]
                val b = p[i]
                val c = p[(i + 1) % p.size]
                val area = abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y))
                if (area < minArea) { minArea = area; minIndex = i }
            }
            p.removeAt(minIndex)
        }
        return p
    }

    fun area(polygon: List<PathPoint>): Float {
        var a = 0f
        for (i in polygon.indices) {
            val p = polygon[i]
            val q = polygon[(i + 1) % polygon.size]
            a += p.x * q.y - q.x * p.y
        }
        return abs(a) / 2f
    }
}
