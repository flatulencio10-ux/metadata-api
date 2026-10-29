package com.customlauncher.objects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.customlauncher.core.model.NodeAnimations
import com.customlauncher.core.model.TapEffect
import kotlinx.coroutines.Job

/** A particle burst started at [startMs] (uptime millis). */
data class Burst(val count: Int, val color: Long, val spread: Float, val startMs: Long, val seed: Int)

/** A sprite sheet playing over the object. */
data class SpriteRun(val file: String, val cols: Int, val rows: Int, val fps: Float, val loops: Int, val startMs: Long)

/**
 * Runtime (non-persisted) state of one node, driven by behaviours and scripts and read by the
 * renderer: extra offset, current state and image, speech bubble, particles, sprites.
 */
class NodeRuntime(val id: String) {
    /** Extra offset in dp on top of the node's position (move-to, paths, orbits, finger). */
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
    var extraRotation by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var image by mutableStateOf<String?>(null)
    var state by mutableStateOf("idle")
    var transitionMs by mutableIntStateOf(200)
    var stateAnimation by mutableStateOf<NodeAnimations?>(null)
    var bubble by mutableStateOf<String?>(null)
    val bursts = mutableStateListOf<Burst>()
    var sprite by mutableStateOf<SpriteRun?>(null)
    var followFinger by mutableStateOf(false)
    /** Built-in reaction to play; the Int is a counter so the same effect can replay. */
    var effect by mutableStateOf<Pair<TapEffect, Int>?>(null)
    /** Keyframe clip to play (name, counter). */
    var clip by mutableStateOf<Pair<String, Int>?>(null)
    /** Increments on taps routed through the object layer (plays the node's tap animation). */
    var tapKey by mutableIntStateOf(0)
    var physicsEnabled = true
    var motionJob: Job? = null
    var lastRpm = 0f
    var inZone = HashMap<String, Boolean>()
}

/** All node runtimes of the process, by node id. */
class RuntimeRegistry {
    private val map = HashMap<String, NodeRuntime>()
    operator fun get(id: String): NodeRuntime = map.getOrPut(id) { NodeRuntime(id) }
    fun peek(id: String): NodeRuntime? = map[id]
}

val LocalRuntimes = staticCompositionLocalOf<RuntimeRegistry?> { null }
