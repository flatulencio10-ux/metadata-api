package com.customlauncher.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.GestureConfig
import com.customlauncher.core.model.Trigger
import kotlin.math.abs
import kotlin.math.max

/** Callbacks from the home screen gesture engine. Pass stable lambdas (rememberUpdatedState). */
class GestureCallbacks(
    val onTrigger: (Trigger) -> Unit,
    val onLongPress: (Offset) -> Unit,
    val onTap: (Offset) -> Unit,
)

private enum class Corner(val tap: Trigger, val long: Trigger) {
    TOP_LEFT(Trigger.CORNER_TOP_LEFT_TAP, Trigger.CORNER_TOP_LEFT_LONG),
    TOP_RIGHT(Trigger.CORNER_TOP_RIGHT_TAP, Trigger.CORNER_TOP_RIGHT_LONG),
    BOTTOM_LEFT(Trigger.CORNER_BOTTOM_LEFT_TAP, Trigger.CORNER_BOTTOM_LEFT_LONG),
    BOTTOM_RIGHT(Trigger.CORNER_BOTTOM_RIGHT_TAP, Trigger.CORNER_BOTTOM_RIGHT_LONG),
}

/**
 * Root-level gesture recogniser for the home screen.
 *
 * It *observes* events (Final pass) so children such as the pager, scrolling widgets and
 * clickable icons keep working; a gesture only fires when no child consumed the movement.
 * Hot corners and edge swipes are the exception: when the touch starts in a bound zone the
 * engine claims the gesture in the Initial pass so nothing underneath reacts.
 *
 * Recognises: swipes (1 finger), two-finger swipes, pinch in/out, double tap, long press,
 * corner tap/long press and edge swipes.
 */
fun Modifier.launcherGestures(
    config: GestureConfig,
    editMode: Boolean,
    singlePage: Boolean,
    callbacks: GestureCallbacks,
): Modifier = pointerInput(config, editMode, singlePage) {
    val cornerPx = config.cornerSizeDp.dp.toPx()
    val edgePx = config.edgeSizeDp.dp.toPx()
    val swipeThreshold = 64.dp.toPx()
    val slop = viewConfiguration.touchSlop
    fun bound(t: Trigger) = !editMode && config.actionFor(t) != com.customlauncher.core.model.Action.None

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = down.position

        // ---- Hot corners -------------------------------------------------------------
        val corner = cornerAt(start, size, cornerPx)
        if (corner != null && (bound(corner.tap) || bound(corner.long))) {
            down.consume()
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitAllUp(PointerEventPass.Initial) }
            if (up == null) {
                if (bound(corner.long)) callbacks.onTrigger(corner.long)
                awaitAllUp(PointerEventPass.Initial)
            } else if (bound(corner.tap)) {
                callbacks.onTrigger(corner.tap)
            }
            return@awaitEachGesture
        }

        // ---- Edge swipes -------------------------------------------------------------
        val edge = edgeAt(start, size, edgePx)
        if (edge != null && bound(edge)) {
            down.consume()
            var last = start
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
                ev.changes.firstOrNull { it.id == down.id }?.let { last = it.position }
                if (ev.changes.none { it.pressed }) break
            }
            val d = last - start
            val inward = when (edge) {
                Trigger.EDGE_LEFT_SWIPE -> d.x
                Trigger.EDGE_RIGHT_SWIPE -> -d.x
                Trigger.EDGE_TOP_SWIPE -> d.y
                else -> -d.y
            }
            if (inward > swipeThreshold) callbacks.onTrigger(edge)
            return@awaitEachGesture
        }

        // ---- Normal gestures (observed, not claimed) --------------------------------
        var childScrolled = false
        var maxPointers = 1
        var lastPrimary = start
        var startSpan = 0f
        var endSpan = 0f
        var startCentroid = Offset.Zero
        var endCentroid = Offset.Zero

        fun track(changes: List<PointerInputChange>) {
            // A consumed *move* means a pager/scroller/draggable child handled it.
            if (changes.any { it.isConsumed && it.pressed && it.previousPressed }) childScrolled = true
            changes.firstOrNull { it.id == down.id }?.let { lastPrimary = it.position }
            val pressed = changes.filter { it.pressed }
            maxPointers = max(maxPointers, pressed.size)
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val centroid = (a + b) / 2f
                val span = (a - b).getDistance()
                if (startSpan == 0f) {
                    startSpan = span; startCentroid = centroid
                }
                endSpan = span; endCentroid = centroid
            }
        }

        // Phase 1: wait for release, movement or long-press timeout.
        var upConsumed = false
        val phase1 = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Final)
                track(ev.changes)
                if (ev.changes.none { it.pressed }) {
                    upConsumed = ev.changes.any { it.isConsumed }
                    return@withTimeoutOrNull "up"
                }
                if (maxPointers > 1 || (lastPrimary - start).getDistance() > slop) return@withTimeoutOrNull "moved"
            }
            @Suppress("UNREACHABLE_CODE") "moved"
        }

        when (phase1) {
            null -> {
                if (editMode) {
                    // In Edit Mode a press-and-hold usually precedes a widget drag: just observe.
                    awaitAllUp(PointerEventPass.Final)
                } else {
                    // Long press: fire, then swallow the rest so a child click doesn't also trigger.
                    callbacks.onLongPress(start)
                    awaitAllUp(PointerEventPass.Initial)
                }
            }
            "up" -> {
                if (upConsumed) return@awaitEachGesture // a child (icon, button) handled the tap
                if (bound(Trigger.DOUBLE_TAP)) {
                    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    }
                    if (second != null) {
                        awaitAllUp(PointerEventPass.Final)
                        callbacks.onTrigger(Trigger.DOUBLE_TAP)
                        return@awaitEachGesture
                    }
                }
                callbacks.onTap(start)
            }
            else -> {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Final)
                    track(ev.changes)
                    if (ev.changes.none { it.pressed }) break
                }
                if (childScrolled || editMode) return@awaitEachGesture
                val trigger = if (maxPointers >= 2) {
                    classifyMulti(startSpan, endSpan, endCentroid - startCentroid, swipeThreshold)
                } else {
                    classifySingle(lastPrimary - start, swipeThreshold, singlePage)
                }
                if (trigger != null && bound(trigger)) callbacks.onTrigger(trigger)
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitAllUp(pass: PointerEventPass) {
    while (true) {
        val ev = awaitPointerEvent(pass)
        if (pass == PointerEventPass.Initial) ev.changes.forEach { it.consume() }
        if (ev.changes.none { it.pressed }) return
    }
}

private fun cornerAt(p: Offset, size: IntSize, zone: Float): Corner? {
    val left = p.x < zone
    val right = p.x > size.width - zone
    val top = p.y < zone
    val bottom = p.y > size.height - zone
    return when {
        top && left -> Corner.TOP_LEFT
        top && right -> Corner.TOP_RIGHT
        bottom && left -> Corner.BOTTOM_LEFT
        bottom && right -> Corner.BOTTOM_RIGHT
        else -> null
    }
}

private fun edgeAt(p: Offset, size: IntSize, zone: Float): Trigger? = when {
    p.x < zone -> Trigger.EDGE_LEFT_SWIPE
    p.x > size.width - zone -> Trigger.EDGE_RIGHT_SWIPE
    p.y < zone -> Trigger.EDGE_TOP_SWIPE
    p.y > size.height - zone -> Trigger.EDGE_BOTTOM_SWIPE
    else -> null
}

/** Exposed for unit tests. */
internal fun classifySingle(d: Offset, threshold: Float, allowHorizontal: Boolean): Trigger? {
    val ax = abs(d.x)
    val ay = abs(d.y)
    return when {
        ay > threshold && ay > ax * 1.5f -> if (d.y < 0) Trigger.SWIPE_UP else Trigger.SWIPE_DOWN
        allowHorizontal && ax > threshold && ax > ay * 1.5f -> if (d.x < 0) Trigger.SWIPE_LEFT else Trigger.SWIPE_RIGHT
        else -> null
    }
}

internal fun classifyMulti(startSpan: Float, endSpan: Float, centroidDelta: Offset, threshold: Float): Trigger? {
    if (startSpan > 0f) {
        val ratio = endSpan / startSpan
        if (ratio < 0.7f) return Trigger.PINCH_IN
        if (ratio > 1.4f) return Trigger.PINCH_OUT
    }
    val ax = abs(centroidDelta.x)
    val ay = abs(centroidDelta.y)
    return when {
        ay > threshold && ay > ax -> if (centroidDelta.y < 0) Trigger.TWO_FINGER_UP else Trigger.TWO_FINGER_DOWN
        ax > threshold -> if (centroidDelta.x < 0) Trigger.TWO_FINGER_LEFT else Trigger.TWO_FINGER_RIGHT
        else -> null
    }
}
