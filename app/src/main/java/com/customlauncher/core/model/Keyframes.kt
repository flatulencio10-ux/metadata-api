package com.customlauncher.core.model

import kotlinx.serialization.Serializable

/** Animatable node properties for keyframe clips. Offsets are relative to the node's rest pose. */
@Serializable
enum class KProp(val label: String, val default: Float) {
    X("Move X (dp)", 0f), Y("Move Y (dp)", 0f), ROTATION("Rotation (°)", 0f), SCALE("Scale", 1f),
    OPACITY("Opacity", 1f), TINT("Tint amount", 0f), SKEW_X("Skew X", 0f), BLUR("Blur (dp)", 0f),
}

/**
 * One keyframe. The segment from this key to the next uses the cubic-bezier easing
 * (x1, y1, x2, y2) like CSS; (0, 0, 1, 1) is linear.
 */
@Serializable
data class Keyframe(
    val timeMs: Int,
    val value: Float,
    val x1: Float = 0.42f,
    val y1: Float = 0f,
    val x2: Float = 0.58f,
    val y2: Float = 1f,
)

@Serializable
data class Track(val property: KProp, val keys: List<Keyframe> = emptyList())

/** A named, reusable animation of one object (played by behaviours and scripts). */
@Serializable
data class KeyframeClip(
    val name: String,
    val durationMs: Int = 1000,
    val tracks: List<Track> = emptyList(),
    val tintColor: Long = 0xFFFF4FA3,
    /** Extra plays after the first (-1 = loop forever). */
    val repeat: Int = 0,
)
