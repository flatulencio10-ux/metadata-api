package com.customlauncher.core.anim

import com.customlauncher.core.model.KProp
import com.customlauncher.core.model.Keyframe
import com.customlauncher.core.model.KeyframeClip
import com.customlauncher.core.model.Track

/** Samples keyframe tracks. Framework-free (unit tested). */
object KeyframeMath {

    /** Value of [track] at [timeMs]; holds the first/last value outside the keyed range. */
    fun sample(track: Track, timeMs: Float): Float {
        val keys = track.keys.sortedBy { it.timeMs }
        if (keys.isEmpty()) return track.property.default
        if (timeMs <= keys.first().timeMs) return keys.first().value
        if (timeMs >= keys.last().timeMs) return keys.last().value
        val i = keys.indexOfLast { it.timeMs <= timeMs }
        val a = keys[i]
        val b = keys[i + 1]
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1)
        val t = (timeMs - a.timeMs) / span
        val eased = MotionMath.cubicBezier(a.x1, a.y1, a.x2, a.y2, t)
        return a.value + (b.value - a.value) * eased
    }

    /** Every property's value at [timeMs] (defaults for properties without a track). */
    fun sample(clip: KeyframeClip, timeMs: Float): Map<KProp, Float> =
        KProp.entries.associateWith { p -> clip.tracks.firstOrNull { it.property == p }?.let { sample(it, timeMs) } ?: p.default }

    /** Adds or replaces the key at [timeMs] on [property]'s track. */
    fun setKey(clip: KeyframeClip, property: KProp, timeMs: Int, value: Float): KeyframeClip {
        val existing = clip.tracks.firstOrNull { it.property == property }
        val keys = (existing?.keys.orEmpty().filterNot { it.timeMs == timeMs } + Keyframe(timeMs, value)).sortedBy { it.timeMs }
        val track = Track(property, keys)
        return clip.copy(tracks = if (existing == null) clip.tracks + track else clip.tracks.map { if (it.property == property) track else it })
    }

    /**
     * Turns a recorded finger path (time ms, x dp, y dp) into X/Y tracks relative to the first
     * sample, keeping a key every [stepMs] so the result stays editable.
     */
    fun fromRecording(clip: KeyframeClip, samples: List<Triple<Int, Float, Float>>, stepMs: Int = 100): KeyframeClip {
        if (samples.size < 2) return clip
        val (t0, x0, y0) = samples.first()
        val kept = samples.filterIndexed { i, s -> i == 0 || i == samples.lastIndex || (s.first - t0) / stepMs != (samples[i - 1].first - t0) / stepMs }
        val linear = { t: Int, v: Float -> Keyframe(t - t0, v, 0f, 0f, 1f, 1f) }
        val xs = Track(KProp.X, kept.map { linear(it.first, it.second - x0) })
        val ys = Track(KProp.Y, kept.map { linear(it.first, it.third - y0) })
        val duration = maxOf(clip.durationMs, samples.last().first - t0)
        return clip.copy(durationMs = duration, tracks = clip.tracks.filter { it.property != KProp.X && it.property != KProp.Y } + xs + ys)
    }
}
