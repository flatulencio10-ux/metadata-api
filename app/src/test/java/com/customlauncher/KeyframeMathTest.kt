package com.customlauncher

import com.customlauncher.core.anim.KeyframeMath
import com.customlauncher.core.model.KProp
import com.customlauncher.core.model.Keyframe
import com.customlauncher.core.model.KeyframeClip
import com.customlauncher.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyframeMathTest {
    private val linear = Track(KProp.X, listOf(Keyframe(0, 0f, 0f, 0f, 1f, 1f), Keyframe(1000, 100f, 0f, 0f, 1f, 1f)))

    @Test fun linearMidpoint() = assertEquals(50f, KeyframeMath.sample(linear, 500f), 0.5f)
    @Test fun holdsOutsideRange() {
        assertEquals(0f, KeyframeMath.sample(linear, -100f), 0f)
        assertEquals(100f, KeyframeMath.sample(linear, 5000f), 0f)
    }
    @Test fun missingTrackUsesDefault() = assertEquals(1f, KeyframeMath.sample(KeyframeClip("c"), 10f).getValue(KProp.SCALE), 0f)

    @Test fun setKeyReplacesAtSameTime() {
        var clip = KeyframeClip("c")
        clip = KeyframeMath.setKey(clip, KProp.ROTATION, 200, 45f)
        clip = KeyframeMath.setKey(clip, KProp.ROTATION, 200, 90f)
        assertEquals(1, clip.tracks.single().keys.size)
        assertEquals(90f, clip.tracks.single().keys.single().value, 0f)
    }

    @Test fun recordingBecomesRelativeTracks() {
        val samples = (0..20).map { Triple(it * 25, 100f + it * 5f, 300f) }
        val clip = KeyframeMath.fromRecording(KeyframeClip("r", durationMs = 100), samples)
        assertEquals(500, clip.durationMs)
        assertEquals(100f, KeyframeMath.sample(clip.tracks.first { it.property == KProp.X }, 500f), 0.01f)
        assertEquals(0f, KeyframeMath.sample(clip.tracks.first { it.property == KProp.Y }, 250f), 0.01f)
    }
}
