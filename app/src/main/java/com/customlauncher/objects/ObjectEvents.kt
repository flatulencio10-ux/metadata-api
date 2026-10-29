package com.customlauncher.objects

import androidx.compose.runtime.staticCompositionLocalOf

/** Gestures and physics events on interactive objects, consumed by behaviours and scripts. */
sealed interface ObjectEvent {
    val nodeId: String

    data class Tap(override val nodeId: String) : ObjectEvent
    data class DoubleTap(override val nodeId: String) : ObjectEvent
    data class LongPress(override val nodeId: String) : ObjectEvent
    data class DragStart(override val nodeId: String) : ObjectEvent
    /** [speed] = release speed in dp/s. */
    data class DragEnd(override val nodeId: String, val speed: Float) : ObjectEvent
    data class Collided(override val nodeId: String, val other: String?, val impulse: Float) : ObjectEvent
}

fun interface ObjectEventSink {
    fun onEvent(event: ObjectEvent)
}

/** Where object gestures go on the current page (no-op outside a page). */
val LocalObjectEvents = staticCompositionLocalOf { ObjectEventSink { } }
