package com.customlauncher.physics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import com.customlauncher.container
import com.customlauncher.core.model.BodyType
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.PathPoint
import com.customlauncher.core.model.WorldConfig
import com.customlauncher.objects.ObjectEvent
import com.customlauncher.objects.ObjectEventSink
import com.customlauncher.objects.RuntimeRegistry
import com.customlauncher.ui.edit.NodeBounds
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widgets.sticker.AlphaMask
import com.customlauncher.widgets.sticker.StickerWidget
import java.io.File

/** The current page's simulation, if any (nodes read their live pose from it). */
val LocalPhysics = staticCompositionLocalOf<PhysicsWorld?> { null }

/** Live performance numbers for the overlay. */
object PhysicsStats {
    @Volatile var stepMs = 0f
    @Volatile var bodies = 0
}

/**
 * Creates and runs the page's physics world while the page is active and not in Edit Mode.
 * The loop is driven by withFrameNanos (Choreographer), sleeps when every body is still, and
 * writes final positions back through [onSettled].
 */
@Composable
fun rememberPhysicsWorld(
    nodes: List<Node>,
    world: WorldConfig,
    pageSize: DpSize,
    active: Boolean,
    maxDynamic: Int,
    events: ObjectEventSink,
    onSettled: (Map<String, Triple<Float, Float, Float>>) -> Unit,
): PhysicsWorld? {
    if (nodes.none { it.physics != null && it.physics.type != BodyType.NONE }) return null
    val context = LocalContext.current
    val c = context.container
    val stickerDir = c.stickerRepository.dir
    val latestEvents by rememberUpdatedState(events)
    val latestSettled by rememberUpdatedState(onSettled)

    // Rebuild only when something physics-relevant changes.
    val key = nodes.map { listOf(it.id, it.physics, it.x, it.y, it.width, it.height, it.rotation, it.scale, it.flipX, it.hidden, it.z) }
    val pw = remember(key, world, pageSize) {
        PhysicsWorld(
            nodes, world, pageSize.width.value, pageSize.height.value, maxDynamic,
            hullOf = { n -> stickerHull(n, stickerDir) },
            onCollision = { col ->
                col.a?.let { latestEvents.onEvent(ObjectEvent.Collided(it, col.b, col.impulse)) }
                col.b?.let { latestEvents.onEvent(ObjectEvent.Collided(it, col.a, col.impulse)) }
            },
        )
    }
    val allowed by c.powerGovernor.continuousAllowed.collectAsState()

    LaunchedEffect(pw, active, allowed) {
        if (!active || !allowed) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            var moving = true
            withFrameNanos { now ->
                moving = pw.step((now - last) / 1e9f)
                last = now
            }
            PhysicsStats.stepMs = pw.lastStepMs
            PhysicsStats.bodies = pw.bodyCount
            if (!moving) {
                // Everything is asleep: zero CPU until a touch, impulse or tilt wakes it.
                latestSettled(pw.poses())
                pw.wakeups.receive()
                last = withFrameNanos { it }
            }
        }
    }
    LaunchedEffect(pw, active, world.followTilt) {
        if (!active || !world.followTilt) return@LaunchedEffect
        c.tiltSensor.tilt.collect { pw.setTilt(it.x, it.y) }
    }
    return pw
}

/** Convex collider from a sticker's alpha channel (null for other node types). */
private fun stickerHull(n: Node, dir: File): List<PathPoint>? = stickerMaskOf(n, dir)?.convexHull()

/** The alpha mask of a sticker node, or null for other nodes. */
fun stickerMaskOf(n: Node, dir: File): AlphaMask? {
    if (n.type != StickerWidget.id) return null
    return AlphaMask.load(File(dir, WidgetConfig(n.config).string(StickerWidget.FILE)))
}

/**
 * Touch handling for interactive objects, claimed in the Initial pass so nothing underneath
 * reacts. Physics bodies: drag with a spring joint once the finger moves, release to throw,
 * tangential speed spins. Other objects ([touchNodes]): taps go to behaviours, and drags move
 * them when "follow finger" is on. Taps / double taps / long presses become [ObjectEvent]s.
 */
fun Modifier.objectTouch(
    world: PhysicsWorld?,
    touchNodes: List<Node>,
    runtimes: RuntimeRegistry,
    events: ObjectEventSink,
    onFollowDragEnd: (String) -> Unit,
    /** Alpha mask of a sticker node (taps on transparent pixels pass through). */
    maskOf: (Node) -> AlphaMask? = { null },
): Modifier =
    if (world == null && touchNodes.isEmpty()) this else this.pointerInput(world, touchNodes) {
        var lastTapId: String? = null
        var lastTapAt = 0L
        val masks = HashMap<String, AlphaMask?>()
        fun nodeAt(x: Float, y: Float): String? = touchNodes.sortedByDescending { it.z }.firstOrNull { n ->
            val rt = runtimes.peek(n.id)
            val b = NodeBounds.of(n)
            val lx = x - (rt?.dx ?: 0f)
            val ly = y - (rt?.dy ?: 0f)
            if (lx < b.left || lx > b.right || ly < b.top || ly > b.bottom) return@firstOrNull false
            val mask = masks.getOrPut(n.id) { maskOf(n) }
            mask == null || mask.isOpaque(
                androidx.compose.ui.geometry.Offset(lx - b.left, ly - b.top),
                androidx.compose.ui.unit.IntSize((b.right - b.left).toInt().coerceAtLeast(1), (b.bottom - b.top).toInt().coerceAtLeast(1)),
            )
        }?.id

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun dp(o: androidx.compose.ui.geometry.Offset) = o / density
            val start = dp(down.position)
            val physical = world?.bodyAt(start.x, start.y)
            val id = physical ?: nodeAt(start.x, start.y) ?: return@awaitEachGesture
            down.consume()
            val rt = runtimes[id]
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            var longPressed = false
            var last = start
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                tracker.addPosition(ch.uptimeMillis, ch.position)
                val now = dp(ch.position)
                if (!dragging && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    dragging = true
                    if (physical != null) world.startDrag(id, start.x, start.y)
                    events.onEvent(ObjectEvent.DragStart(id))
                }
                if (dragging) {
                    if (physical != null) world.drag(now.x, now.y)
                    else if (rt.followFinger) { rt.dx += now.x - last.x; rt.dy += now.y - last.y }
                }
                last = now
                if (!dragging && !longPressed && ch.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                    longPressed = true
                    events.onEvent(ObjectEvent.LongPress(id))
                }
                if (!ch.pressed) break
            }
            val v = tracker.calculateVelocity()
            val vx = v.x / density
            val vy = v.y / density
            if (dragging) {
                if (physical != null) world.endDrag(vx, vy, last.x, last.y)
                else if (rt.followFinger) onFollowDragEnd(id)
                events.onEvent(ObjectEvent.DragEnd(id, kotlin.math.hypot(vx, vy)))
            } else if (!longPressed) {
                val now = down.uptimeMillis
                if (lastTapId == id && now - lastTapAt < viewConfiguration.doubleTapTimeoutMillis) {
                    events.onEvent(ObjectEvent.DoubleTap(id)); lastTapId = null
                } else {
                    events.onEvent(ObjectEvent.Tap(id)); lastTapId = id; lastTapAt = now
                }
            }
        }
    }

