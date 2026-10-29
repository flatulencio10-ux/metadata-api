package com.customlauncher

import com.customlauncher.core.anim.MotionMath
import com.customlauncher.core.model.Direction
import com.customlauncher.core.model.Easing
import com.customlauncher.core.model.EntranceEffect
import com.customlauncher.core.model.IdleEffect
import com.customlauncher.core.model.PathPoint
import com.customlauncher.core.model.TapEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {
    @Test fun easingsStartAndEndInPlace() {
        Easing.entries.forEach {
            assertEquals("$it start", 0f, MotionMath.ease(it, 0f), 0.02f)
            assertEquals("$it end", 1f, MotionMath.ease(it, 1f), 0.02f)
        }
    }

    @Test fun overshootGoesPastTheTarget() = assertTrue((0..100).any { MotionMath.ease(Easing.OVERSHOOT, it / 100f) > 1.05f })

    @Test fun bezierLinearIsIdentity() = assertEquals(0.3f, MotionMath.cubicBezier(0.3f, 0.3f, 0.7f, 0.7f, 0.3f), 0.01f)

    @Test fun flyInStartsOffscreenAndEndsAtRest() {
        assertEquals(-420f, MotionMath.entrance(EntranceEffect.FLY_IN, 0f, Direction.LEFT).dx, 0.01f)
        assertEquals(0f, MotionMath.entrance(EntranceEffect.FLY_IN, 1f, Direction.LEFT).dx, 0.01f)
    }

    @Test fun idleRotateCompletesATurnPerPeriod() =
        assertEquals(180f, MotionMath.idle(IdleEffect.ROTATE, 1f, 2000, 1f).rotation, 0.01f)

    @Test fun tapEffectsReturnToRest() {
        TapEffect.entries.forEach {
            val m = MotionMath.tap(it, 1f)
            assertEquals("$it dx", 0f, m.dx, 0.5f)
            assertEquals("$it dy", 0f, m.dy, 0.5f)
        }
    }

    @Test fun pathSamplingLoops() {
        val square = listOf(PathPoint(0f, 0f), PathPoint(10f, 0f), PathPoint(10f, 10f), PathPoint(0f, 10f))
        assertEquals(PathPoint(10f, 0f), MotionMath.pointAlong(square, 0.25f))
        assertEquals(PathPoint(0f, 10f), MotionMath.pointAlong(square, 0.75f))
    }
}
