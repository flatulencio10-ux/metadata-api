package com.customlauncher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Easing { LINEAR, EASE, SPRING, BOUNCE, OVERSHOOT }

/** When an entrance animation plays. */
@Serializable
enum class EntranceTrigger(val label: String) {
    PAGE_SHOWN("Page shown"), UNLOCK("Screen unlock"), LAUNCHER_OPENED("Launcher opened")
}

@Serializable
enum class EntranceEffect(val label: String) {
    NONE("None"), FADE_IN("Fade in"), FLY_IN("Fly in"), ZOOM_IN("Zoom in"), POP("Pop"), SPIN_IN("Spin in"),
    BOUNCE_DROP("Bounce drop"), WIPE("Wipe"), SLIDE_FADE("Slide + fade"), TYPEWRITER("Typewriter reveal"),
}

@Serializable
enum class Direction { LEFT, RIGHT, TOP, BOTTOM }

@Serializable
enum class IdleEffect(val label: String) {
    NONE("None"), FLOAT("Float / bob"), WIGGLE("Wiggle"), PULSE("Pulse"), ROTATE("Rotate"), SWAY("Sway"),
    SHAKE("Shake"), BREATHE("Breathe"), DRIFT("Drift along path"),
}

@Serializable
enum class TapEffect(val label: String) {
    NONE("None"), JUMP("Jump"), SPIN("Spin"), SQUASH("Squash & stretch"), SHAKE("Shake"),
    EXPLODE("Explode & reform"), PLAY_GIF_ONCE("Play GIF once"),
}

/** Duration/delay/easing/repeat for one animation slot. [repeat] = extra plays (-1 = forever). */
@Serializable
data class AnimTiming(
    val durationMs: Int = 600,
    val delayMs: Int = 0,
    val easing: Easing = Easing.EASE,
    val repeat: Int = 0,
)

/** A point in dp, relative to the node's resting position (drift paths). */
@Serializable
data class PathPoint(val x: Float, val y: Float)

/**
 * PowerPoint/Canva-style animations for a node: entrance, idle loop and on-tap, plus
 * parallax. Everything defaults to off.
 */
@Serializable
data class NodeAnimations(
    val entrance: EntranceEffect = EntranceEffect.NONE,
    val entranceTrigger: EntranceTrigger = EntranceTrigger.PAGE_SHOWN,
    val entranceFrom: Direction = Direction.LEFT,
    val entranceTiming: AnimTiming = AnimTiming(),
    val idle: IdleEffect = IdleEffect.NONE,
    val idleTiming: AnimTiming = AnimTiming(durationMs = 2400, easing = Easing.LINEAR, repeat = -1),
    /** 0..2, scales how strong the idle motion is. */
    val idleAmount: Float = 1f,
    /** Sway pivot as a fraction of the node (0.5, 0 = hanging from the top centre). */
    val swayPivotX: Float = 0.5f,
    val swayPivotY: Float = 0f,
    val driftPath: List<PathPoint> = emptyList(),
    val tap: TapEffect = TapEffect.NONE,
    val tapTiming: AnimTiming = AnimTiming(durationMs = 500),
    /** Action run on tap in addition to the tap animation. */
    val tapAction: Action = Action.None,
    /** 0..1: how much the node shifts with page swipes / device tilt (depth effect). */
    val parallaxDepth: Float = 0f,
    val tiltParallax: Boolean = false,
) {
    val isEmpty: Boolean get() = this == NodeAnimations()
}
