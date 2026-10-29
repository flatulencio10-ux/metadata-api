package com.customlauncher.ui.edit

import com.customlauncher.core.model.Node
import kotlin.math.abs
import kotlin.math.roundToInt

/** A guide line to draw while dragging: vertical (x = [position]) or horizontal (y = [position]), in dp. */
data class Guide(val vertical: Boolean, val position: Float)

/** Axis-aligned visual bounds of a node in dp (scale is applied around the pivot; rotation is ignored). */
data class NodeBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    companion object {
        fun of(n: Node): NodeBounds {
            val w = n.width * n.scale
            val h = n.height * n.scale
            val left = n.x + n.pivotX * n.width * (1 - n.scale)
            val top = n.y + n.pivotY * n.height * (1 - n.scale)
            return NodeBounds(left, top, left + w, top + h)
        }
    }
}

/**
 * Smart guides: snaps a moving node's edges/centre to the page centre lines, page edges and
 * other nodes' edges/centres when within [threshold] dp. Framework-free (unit tested).
 */
object SnapGuides {
    data class Result(val dx: Float, val dy: Float, val guides: List<Guide>)

    fun snap(moving: NodeBounds, others: List<NodeBounds>, pageWidth: Float, pageHeight: Float, threshold: Float = 6f): Result {
        val xs = mutableListOf(0f, pageWidth / 2f, pageWidth)
        val ys = mutableListOf(0f, pageHeight / 2f, pageHeight)
        others.forEach { o ->
            xs += listOf(o.left, o.centerX, o.right)
            ys += listOf(o.top, o.centerY, o.bottom)
        }
        val (dx, gx) = best(listOf(moving.left, moving.centerX, moving.right), xs, threshold)
        val (dy, gy) = best(listOf(moving.top, moving.centerY, moving.bottom), ys, threshold)
        return Result(dx, dy, listOfNotNull(gx?.let { Guide(true, it) }, gy?.let { Guide(false, it) }))
    }

    /** Smallest correction that aligns any of [own] with any of [targets]. */
    private fun best(own: List<Float>, targets: List<Float>, threshold: Float): Pair<Float, Float?> {
        var bestDelta = 0f
        var bestTarget: Float? = null
        var bestDist = threshold
        for (o in own) for (t in targets) {
            val d = abs(t - o)
            if (d <= bestDist) {
                bestDist = d; bestDelta = t - o; bestTarget = t
            }
        }
        return bestDelta to bestTarget
    }

    /** Snaps [degrees] to the nearest multiple of [step] when within [tolerance]; returns null if not snapped. */
    fun snapRotation(degrees: Float, step: Float = 15f, tolerance: Float = 4f): Float? {
        val nearest = (degrees / step).roundToInt() * step
        return if (abs(nearest - degrees) <= tolerance) nearest else null
    }
}
