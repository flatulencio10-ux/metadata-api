package com.customlauncher.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A rectangle in page dp (zones for enter/leave triggers). */
@Serializable
data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

@Serializable
enum class TiltDirection { LEFT, RIGHT, FORWARD, BACK }

/** "When ___": what starts a behaviour. */
@Serializable
sealed interface BTrigger {
    @Serializable @SerialName("tap") data object Tap : BTrigger
    @Serializable @SerialName("double_tap") data object DoubleTap : BTrigger
    @Serializable @SerialName("long_press") data object LongPress : BTrigger
    @Serializable @SerialName("drag_start") data object DragStart : BTrigger
    @Serializable @SerialName("drag_end") data object DragEnd : BTrigger
    /** Released while moving faster than [minSpeed] dp/s. */
    @Serializable @SerialName("thrown") data class Thrown(val minSpeed: Float = 1200f) : BTrigger
    /** Spin speed crosses [rpm] going up (or down when [upward] is false). */
    @Serializable @SerialName("spin") data class SpinCrosses(val rpm: Float = 300f, val upward: Boolean = true) : BTrigger
    /** Hits another object ([withNodeId] = null means anything, including walls). */
    @Serializable @SerialName("collides") data class Collides(val withNodeId: String? = null, val minImpulse: Float = 0.5f) : BTrigger
    @Serializable @SerialName("zone_enter") data class ZoneEnter(val zone: Zone) : BTrigger
    @Serializable @SerialName("zone_leave") data class ZoneLeave(val zone: Zone) : BTrigger
    @Serializable @SerialName("page_shown") data object PageShown : BTrigger
    @Serializable @SerialName("unlock") data object Unlock : BTrigger
    /** Minutes since midnight. */
    @Serializable @SerialName("time") data class TimeOfDay(val minute: Int = 8 * 60) : BTrigger
    @Serializable @SerialName("battery") data class BatteryBelow(val percent: Int = 20) : BTrigger
    /** A notification from [packageName] arrives (empty = any app). Needs notification access. */
    @Serializable @SerialName("notification") data class NotificationFrom(val packageName: String = "") : BTrigger
    @Serializable @SerialName("music_playing") data object MusicStarted : BTrigger
    @Serializable @SerialName("music_paused") data object MusicPaused : BTrigger
    @Serializable @SerialName("shaken") data object Shaken : BTrigger
    @Serializable @SerialName("tilt") data class Tilted(val direction: TiltDirection = TiltDirection.LEFT) : BTrigger
    @Serializable @SerialName("every") data class Every(val seconds: Float = 5f) : BTrigger
    /** Each second, fires with this probability. */
    @Serializable @SerialName("chance") data class RandomChance(val percentPerSecond: Float = 5f) : BTrigger
}

@Serializable
enum class VarScope { OBJECT, GLOBAL }

@Serializable
enum class CompareOp(val symbol: String) { EQ("="), NE("≠"), LT("<"), LE("≤"), GT(">"), GE("≥") }

/** "if ___": extra conditions; all must hold. */
@Serializable
sealed interface BCondition {
    @Serializable @SerialName("var") data class Variable(val scope: VarScope, val name: String, val op: CompareOp, val value: Double) : BCondition
    @Serializable @SerialName("state") data class StateIs(val state: String) : BCondition
    @Serializable @SerialName("random") data class Chance(val percent: Float = 50f) : BCondition
}

@Serializable
enum class HapticPattern { TICK, CLICK, HEAVY, DOUBLE, LONG }

@Serializable
enum class VarOp { SET, ADD }

/** "do ___": what a behaviour does. Actions run in order; [Wait] pauses. */
@Serializable
sealed interface BAction {
    /** Plays a named keyframe clip of this object. */
    @Serializable @SerialName("play_clip") data class PlayClip(val clip: String) : BAction
    /** Plays one of the built-in reactions (jump, spin, squash…). */
    @Serializable @SerialName("play_effect") data class PlayEffect(val effect: TapEffect) : BAction
    /** Swaps the sticker image (file in the sticker library). */
    @Serializable @SerialName("swap_image") data class SwapImage(val file: String) : BAction
    @Serializable @SerialName("set_state") data class SetState(val state: String) : BAction
    /** Plays a sprite sheet (cols × rows frames) over the object. */
    @Serializable @SerialName("sprite") data class PlaySprite(val file: String, val cols: Int = 4, val rows: Int = 1, val fps: Float = 12f, val loops: Int = 1) : BAction
    /** dp/s velocity change (physics objects). */
    @Serializable @SerialName("impulse") data class Impulse(val dx: Float = 0f, val dy: Float = -600f) : BAction
    @Serializable @SerialName("torque") data class Torque(val rpm: Float = 300f) : BAction
    /** Moves to (x, y) in page dp; with physics the body is teleported/pushed, otherwise animated. */
    @Serializable @SerialName("move_to") data class MoveTo(val x: Float, val y: Float, val durationMs: Int = 600, val easing: Easing = Easing.EASE) : BAction
    /** While dragging, the object follows the finger (non-physics objects). */
    @Serializable @SerialName("follow_finger") data class FollowFinger(val enabled: Boolean = true) : BAction
    @Serializable @SerialName("follow_path") data class FollowPath(val path: List<PathPoint>, val durationMs: Int = 2000, val loops: Int = 1) : BAction
    @Serializable @SerialName("orbit") data class Orbit(val targetId: String, val radius: Float = 80f, val rpm: Float = 20f, val durationMs: Int = 5000) : BAction
    @Serializable @SerialName("particles") data class EmitParticles(val count: Int = 30, val color: Long = 0xFFFFD54F, val spread: Float = 1f) : BAction
    /** "tone:NAME" (built-in beep) or "file:NAME" (in files/sounds). */
    @Serializable @SerialName("sound") data class PlaySound(val source: String = "tone:PROP_ACK") : BAction
    @Serializable @SerialName("haptic") data class Haptic(val pattern: HapticPattern = HapticPattern.CLICK) : BAction
    @Serializable @SerialName("say") data class Say(val text: String, val durationMs: Int = 2500) : BAction
    /** Runs [action] as if it were [targetId]'s. */
    @Serializable @SerialName("change_other") data class ChangeOther(val targetId: String, val action: BAction) : BAction
    @Serializable @SerialName("set_var") data class SetVariable(val scope: VarScope, val name: String, val op: VarOp = VarOp.ADD, val value: Double = 1.0) : BAction
    /** Any launcher action: open app, macro, flashlight… */
    @Serializable @SerialName("launcher") data class Launcher(val action: Action) : BAction
    @Serializable @SerialName("physics") data class TogglePhysics(val enabled: Boolean) : BAction
    @Serializable @SerialName("wait") data class Wait(val ms: Int = 500) : BAction
    /** Runs one of [actions] at random. */
    @Serializable @SerialName("random") data class Randomize(val actions: List<BAction>) : BAction
}

@Serializable
data class Behavior(
    val id: String = newId(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: BTrigger = BTrigger.Tap,
    val conditions: List<BCondition> = emptyList(),
    val actions: List<BAction> = emptyList(),
)

/**
 * One state of an object's state machine. Built-in names "idle", "tapped", "spinning" and
 * "dragging" are entered automatically when defined; custom states via SetState.
 */
@Serializable
data class NodeState(
    val name: String,
    /** Sticker image while in this state (null = keep the current image). */
    val image: String? = null,
    val animation: NodeAnimations? = null,
    /** Sound played when entering the state. */
    val sound: String? = null,
    val behaviors: List<Behavior> = emptyList(),
    val transitionMs: Int = 200,
    val transitionEasing: Easing = Easing.EASE,
)

/** Behaviour data of one node. */
@Serializable
data class ObjectLogic(
    val behaviors: List<Behavior> = emptyList(),
    val states: List<NodeState> = emptyList(),
    val initialState: String = "idle",
    /** Initial values of this object's variables. */
    val variables: Map<String, Double> = emptyMap(),
) {
    val isEmpty get() = behaviors.isEmpty() && states.isEmpty() && variables.isEmpty()
}
