package com.customlauncher

import com.customlauncher.core.model.PathPoint
import com.customlauncher.physics.ColliderMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ColliderMathTest {
    @Test fun hullOfSquareHasFourCorners() {
        val pts = listOf(PathPoint(0f, 0f), PathPoint(1f, 0f), PathPoint(1f, 1f), PathPoint(0f, 1f), PathPoint(0.5f, 0.5f))
        assertEquals(4, ColliderMath.convexHull(pts).size)
    }

    @Test fun circleMaskSimplifiesToAtMostEightVertices() {
        val n = 64
        val mask = BooleanArray(n * n) { i -> hypot(i % n - 31.5, i / n - 31.5) < 30 }
        val hull = ColliderMath.hullFromMask(mask, n, n)!!
        assertTrue(hull.size in 3..8)
        // A circle of radius ~0.47 has area ~0.69; an 8-gon keeps most of it.
        assertTrue(ColliderMath.area(hull) > 0.55f)
    }

    @Test fun emptyMaskHasNoCollider() = assertEquals(null, ColliderMath.hullFromMask(BooleanArray(16), 4, 4))
}
