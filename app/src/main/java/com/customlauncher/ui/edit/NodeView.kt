package com.customlauncher.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.NodeLayer
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.container
import com.customlauncher.physics.BodyState
import com.customlauncher.physics.LocalPhysics
import com.customlauncher.ui.anim.NodeAnimator
import com.customlauncher.ui.anim.clipPose
import com.customlauncher.objects.LocalRuntimes
import com.customlauncher.ui.objects.RuntimeDecorations
import com.customlauncher.ui.objects.TrailBuffer
import com.customlauncher.ui.objects.speedFx
import com.customlauncher.widgets.objects.LocalBodyState
import com.customlauncher.widgets.objects.LocalObjectState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import com.customlauncher.ui.objects.runtimeOffset
import com.customlauncher.ui.home.WidgetFrame
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.WidgetHost
import com.customlauncher.widgets.sticker.StickerBlend
import com.customlauncher.widgets.sticker.StickerWidget
import com.customlauncher.widget.WidgetRegistry
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Quick actions on the floating toolbar above a selected node. */
enum class NodeToolbarAction { FLIP_H, FLIP_V, DUPLICATE, FRONT, BACK, LOCK, LAYER, PIN, EDIT, DELETE }

/** Everything a node needs from its page while being edited. */
class NodeEditEnv(
    val pageSize: DpSize,
    /** Grid snap for widgets when [gridEnabled]. Stickers never use it. */
    val gridEnabled: Boolean,
    val gridSnap: (Float) -> Float,
    val guidesEnabled: Boolean,
    /** Bounds of the other nodes on the same page, for smart guides. */
    val others: List<NodeBounds>,
    val onSelect: (String) -> Unit,
    val onCommit: (old: Node, new: Node) -> Unit,
    val onGuides: (List<Guide>) -> Unit,
    val onToolbar: (NodeToolbarAction, Node) -> Unit,
    /** Zoom of an infinite canvas page (1 = normal page). */
    val canvasScale: Float = 1f,
    val pinned: Boolean = false,
    /** Timeline scrub / recording preview for this node (read per frame). */
    val scrub: () -> com.customlauncher.ui.anim.ActivePose? = { null },
)

/**
 * Same transform drawn on the parent's canvas instead of an own layer, so a sticker's blend
 * mode can mix with what is beneath it. Touch hit-testing ignores the rotation here, so it is
 * only used for stickers with a blend mode.
 */
fun Modifier.canvasTransform(n: Node): Modifier = drawWithContent {
    val pivot = Offset(size.width * n.pivotX, size.height * n.pivotY)
    withTransform({
        rotate(n.rotation, pivot)
        scale(n.scale * if (n.flipX) -1f else 1f, n.scale * if (n.flipY) -1f else 1f, pivot)
    }) { this@drawWithContent.drawContent() }
}

/** Pose from the physics simulation (read per frame in the layer, so no recomposition). */
fun Modifier.physicsTransform(n: Node, body: BodyState): Modifier = graphicsLayer {
    translationX = (body.x - n.x).dp.toPx()
    translationY = (body.y - n.y).dp.toPx()
    rotationZ = body.rotation
    scaleX = n.scale * if (n.flipX) -1f else 1f
    scaleY = n.scale * if (n.flipY) -1f else 1f
}

/** Applies a node's rotation, scale, flip and pivot. */
fun Modifier.nodeTransform(n: Node): Modifier = graphicsLayer {
    rotationZ = n.rotation
    scaleX = n.scale * if (n.flipX) -1f else 1f
    scaleY = n.scale * if (n.flipY) -1f else 1f
    transformOrigin = TransformOrigin(n.pivotX, n.pivotY)
}

/**
 * A node on a page. Outside Edit Mode it just draws the widget with its transform. In Edit
 * Mode: one-finger drag moves, two-finger pinch scales and twists rotate; the selected node
 * gets corner handles, a rotate handle and a floating toolbar. Changes preview live and are
 * committed when the gesture ends.
 */
@Composable
fun NodeView(
    node: Node,
    wallpaper: Wallpaper,
    host: WidgetHost,
    editMode: Boolean,
    selected: Boolean,
    env: NodeEditEnv?,
    modifier: Modifier = Modifier,
) {
    if (!editMode || env == null) {
        if (node.hidden) return
        val body = LocalPhysics.current?.states?.get(node.id)
        val transform = when {
            body != null -> Modifier.physicsTransform(node, body)
            usesBlend(node) -> Modifier.canvasTransform(node)
            else -> Modifier.nodeTransform(node)
        }
        val rt = if (node.logic != null || node.clips.isNotEmpty() || node.speedVisuals?.sparksOnCollision == true) LocalRuntimes.current?.get(node.id) else null
        val clipPose = com.customlauncher.ui.anim.rememberClipPlayback(node, rt)
        val visuals = node.speedVisuals
        val frames = if (visuals?.frames != null) rememberSpriteSheet(visuals.frames) else null
        val trail = remember(node.id) { TrailBuffer() }
        if (body != null && visuals?.tickPerTurn == true) TurnTicks(body)
        Box(
            Modifier
                .offset(node.x.dp, node.y.dp)
                .runtimeOffset(rt)
                .size(node.width.dp, node.height.dp)
                .then(modifier)
                .then(transform)
                .then(if (node.clips.isNotEmpty()) Modifier.clipPose { clipPose.value } else Modifier)
                .then(if (body != null && visuals != null) Modifier.speedFx(body, visuals, frames, trail) else Modifier)
        ) {
            NodeAnimator(
                rt?.stateAnimation ?: node.animation, host.isActive, Modifier.fillMaxSize(),
                particleSource = stickerFile(node), externalTapKey = rt?.tapKey ?: 0,
            ) {
                CompositionLocalProvider(LocalBodyState provides body, LocalObjectState provides (rt?.state ?: node.logic?.initialState ?: "idle")) {
                    RuntimeDecorations(node, rt) { shown -> WidgetFrame(shown, wallpaper, host, Modifier.fillMaxSize()) }
                }
            }
        }
        return
    }

    val def = WidgetRegistry.find(node.type)
    val freeScale = def?.chromeless == true
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    var draft by remember(node) { mutableStateOf(node) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pxToDp = 1f / (density * env.canvasScale)

    fun root(local: Offset): Offset = coords?.localToRoot(local) ?: local
    fun centerRoot(): Offset = root(Offset(draft.width * density * 0.5f, draft.height * density * 0.5f))

    /** Moves to raw position (x, y), applying grid snap (widgets) or smart guides. */
    fun placeAt(base: Node, x: Float, y: Float): Node {
        if (!freeScale && env.gridEnabled) {
            env.onGuides(emptyList())
            return base.copy(x = env.gridSnap(x), y = env.gridSnap(y))
        }
        val candidate = base.copy(x = x, y = y)
        if (!env.guidesEnabled) return candidate
        val r = SnapGuides.snap(NodeBounds.of(candidate), env.others, env.pageSize.width.value, env.pageSize.height.value)
        env.onGuides(r.guides)
        return candidate.copy(x = x + r.dx, y = y + r.dy)
    }

    var lastRotationSnap by remember { mutableStateOf<Float?>(null) }
    fun rotateTo(base: Node, degrees: Float): Node {
        val snapped = SnapGuides.snapRotation(degrees)
        if (snapped != null && snapped != lastRotationSnap) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastRotationSnap = snapped
        return base.copy(rotation = snapped ?: degrees)
    }

    fun finish() {
        env.onGuides(emptyList())
        lastRotationSnap = null
        if (draft != node) env.onCommit(node, draft)
    }

    val alpha = if (node.hidden) 0.35f else 1f
    Box(
        Modifier
            .offset(draft.x.dp, draft.y.dp)
            .size(draft.width.dp, draft.height.dp)
            .nodeTransform(draft)
            .clipPose(env.scrub)
            .graphicsLayer { this.alpha = alpha }
            .onGloballyPositioned { coords = it }
    ) {
        WidgetFrame(draft, wallpaper, host, Modifier.fillMaxSize())
        val outline = when {
            selected -> MaterialTheme.colorScheme.primary
            node.locked -> Color.White.copy(alpha = 0.25f)
            else -> Color.White.copy(alpha = 0.55f)
        }
        // Gesture layer above the content: content is not interactive in Edit Mode.
        Box(
            Modifier
                .fillMaxSize()
                .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(if (freeScale) 4.dp else 24.dp))
                .pointerInput(node) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        env.onSelect(node.id)
                        if (node.locked) return@awaitEachGesture
                        down.consume()
                        var raw = Offset(node.x, node.y)
                        var current = node
                        var prev: Map<PointerId, Offset> = mapOf(down.id to root(down.position))
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val now = pressed.associate { it.id to root(it.position) }
                            val common = now.keys.filter { it in prev }
                            if (common.size >= 2) {
                                // Two fingers: pinch to scale, twist to rotate, centroid moves.
                                val (a, b) = common.take(2)
                                val c0 = (prev.getValue(a) + prev.getValue(b)) / 2f
                                val c1 = (now.getValue(a) + now.getValue(b)) / 2f
                                val span0 = (prev.getValue(a) - prev.getValue(b)).getDistance()
                                val span1 = (now.getValue(a) - now.getValue(b)).getDistance()
                                val ang0 = angle(prev.getValue(a), prev.getValue(b))
                                val ang1 = angle(now.getValue(a), now.getValue(b))
                                raw += (c1 - c0) * pxToDp
                                val zoom = if (span0 > 1f) span1 / span0 else 1f
                                current = current.copy(scale = (current.scale * zoom).coerceIn(0.05f, 20f))
                                current = rotateTo(current, current.rotation + (ang1 - ang0))
                                current = placeAt(current, raw.x, raw.y)
                            } else if (common.size == 1) {
                                val id = common.first()
                                raw += (now.getValue(id) - prev.getValue(id)) * pxToDp
                                current = placeAt(current, raw.x, raw.y)
                            }
                            draft = current
                            ev.changes.forEach { it.consume() }
                            prev = now
                        }
                        finish()
                    }
                }
        )
        if (selected && !node.locked) {
            Corner.entries.forEach { corner ->
                Handle(corner.alignment, node, onEnd = { finish() }) { start, p0, p ->
                    draft = if (freeScale) {
                        // Stickers scale uniformly around their centre.
                        val c = centerRoot()
                        val d0 = (p0 - c).getDistance().coerceAtLeast(1f)
                        start.copy(scale = (start.scale * (p - c).getDistance() / d0).coerceIn(0.05f, 20f))
                    } else {
                        resize(start, corner, toLocal(p - p0, start) * pxToDp, env)
                    }
                }
            }
            // Rotate handle above the top edge.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-40).dp)
                    .size(32.dp)
                    .pointerInput(node) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            val start = draft
                            val c = centerRoot()
                            val a0 = angle(c, root(down.position))
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                draft = rotateTo(start, start.rotation + angle(c, root(ch.position)) - a0)
                            }
                            finish()
                        }
                    }
                    .padding(6.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }

    if (selected) {
        NodeToolbar(draft, env)
    }
}

/** Loads a sprite sheet from the sticker library (zoetrope frames). */
@Composable
private fun rememberSpriteSheet(file: String): androidx.compose.ui.graphics.ImageBitmap? {
    val repo = androidx.compose.ui.platform.LocalContext.current.container.stickerRepository
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.customlauncher.widgets.sticker.StickerRenderer.decodeAtSize(repo.fileOf(file), 1024, 1024)?.asImageBitmap()
        }
    }
    return bmp
}

/** One haptic tick per full turn while spinning slowly (fidget spinner feel). */
@Composable
private fun TurnTicks(body: BodyState) {
    val haptics = LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(body) {
        var lastTurn = kotlin.math.floor(body.spin / 360f)
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val turn = kotlin.math.floor(body.spin / 360f)
            if (turn != lastTurn && kotlin.math.abs(body.rpm) < 200f) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastTurn = turn
        }
    }
}

@Composable
private fun stickerFile(n: Node): java.io.File? {
    if (n.type != StickerWidget.id) return null
    val repo = androidx.compose.ui.platform.LocalContext.current.container.stickerRepository
    return remember(n.config) { repo.fileOf(WidgetConfig(n.config).string(StickerWidget.FILE)) }
}

private fun usesBlend(n: Node): Boolean =
    n.type == StickerWidget.id && StickerWidget.effects(WidgetConfig(n.config)).blend != StickerBlend.NORMAL

private enum class Corner(val alignment: Alignment, val left: Boolean, val top: Boolean) {
    TOP_LEFT(Alignment.TopStart, true, true),
    TOP_RIGHT(Alignment.TopEnd, false, true),
    BOTTOM_LEFT(Alignment.BottomStart, true, false),
    BOTTOM_RIGHT(Alignment.BottomEnd, false, false),
}

/** Angle in degrees of the line from [a] to [b] (screen coordinates, clockwise). */
private fun angle(a: Offset, b: Offset): Float = (atan2(b.y - a.y, b.x - a.x) * 180.0 / PI).toFloat()

/** Converts a root-space delta into the node's unrotated, unscaled local axes. */
private fun toLocal(d: Offset, n: Node): Offset {
    val r = -n.rotation * PI / 180.0
    val x = d.x * cos(r) - d.y * sin(r)
    val y = d.x * sin(r) + d.y * cos(r)
    val sx = n.scale * if (n.flipX) -1f else 1f
    val sy = n.scale * if (n.flipY) -1f else 1f
    return Offset((x / sx).toFloat(), (y / sy).toFloat())
}

/** Widget resize from a corner (local dp delta), with grid snap and minimum size. */
private fun resize(start: Node, corner: Corner, d: Offset, env: NodeEditEnv): Node {
    val def = WidgetRegistry.find(start.type)
    val minW = def?.minSize?.width?.value ?: 48f
    val minH = def?.minSize?.height?.value ?: 48f
    var x = start.x; var y = start.y; var w = start.width; var h = start.height
    if (corner.left) { x += d.x; w -= d.x } else w += d.x
    if (corner.top) { y += d.y; h -= d.y } else h += d.y
    w = env.gridSnap(w).coerceAtLeast(minW)
    h = env.gridSnap(h).coerceAtLeast(minH)
    if (corner.left) x = start.x + start.width - w
    if (corner.top) y = start.y + start.height - h
    return start.copy(x = x, y = y, width = w, height = h)
}

/**
 * A corner handle. [onEnd] runs when the finger lifts; [onDrag] receives the node at drag start, the pointer start and current
 * position in root coordinates.
 */
@Composable
private fun BoxScope.Handle(alignment: Alignment, key: Node, onEnd: () -> Unit, onDrag: (start: Node, p0: Offset, p: Offset) -> Unit) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        Modifier
            .align(alignment)
            .size(28.dp)
            .onGloballyPositioned { coords = it }
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun root(o: Offset) = coords?.localToRoot(o) ?: o
                    val p0 = root(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        onDrag(key, p0, root(ch.position))
                    }
                    onEnd()
                }
            }
            .padding(6.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .border(2.dp, Color.White, CircleShape)
    )
}

/** Floating quick-action bar centred above the node. */
@Composable
private fun NodeToolbar(node: Node, env: NodeEditEnv) {
    val b = NodeBounds.of(node)
    val density = LocalDensity.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xEE1E2226),
        shadowElevation = 8.dp,
        modifier = Modifier
            .zIndex(1000f)
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = 10_000))
                layout(0, 0) {
                    val x = with(density) { b.centerX.dp.roundToPx() } - p.width / 2
                    val y = with(density) { (b.top - 52f).coerceAtLeast(-8f).dp.roundToPx() }
                    p.place(x.coerceAtLeast(0), y)
                }
            },
    ) {
        Row {
            ToolButton(Icons.Filled.Flip, "Flip horizontally") { env.onToolbar(NodeToolbarAction.FLIP_H, node) }
            ToolButton(Icons.Filled.Flip, "Flip vertically", rotated = true) { env.onToolbar(NodeToolbarAction.FLIP_V, node) }
            ToolButton(Icons.Filled.ContentCopy, "Duplicate") { env.onToolbar(NodeToolbarAction.DUPLICATE, node) }
            ToolButton(Icons.Filled.FlipToFront, "Bring to front") { env.onToolbar(NodeToolbarAction.FRONT, node) }
            ToolButton(Icons.Filled.FlipToBack, "Send to back") { env.onToolbar(NodeToolbarAction.BACK, node) }
            ToolButton(
                Icons.Filled.Layers, if (node.layer == NodeLayer.ABOVE_PANELS) "Above panels (tap: below)" else "Below panels (tap: above)",
                active = node.layer == NodeLayer.ABOVE_PANELS,
            ) { env.onToolbar(NodeToolbarAction.LAYER, node) }
            ToolButton(Icons.Filled.PushPin, "Pin across all pages", active = env.pinned) { env.onToolbar(NodeToolbarAction.PIN, node) }
            ToolButton(if (node.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, "Lock position", active = node.locked) {
                env.onToolbar(NodeToolbarAction.LOCK, node)
            }
            ToolButton(Icons.Filled.Edit, "Edit") { env.onToolbar(NodeToolbarAction.EDIT, node) }
            ToolButton(Icons.Filled.Delete, "Delete") { env.onToolbar(NodeToolbarAction.DELETE, node) }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, rotated: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon, label,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(20.dp).then(if (rotated) Modifier.rotate(90f) else Modifier),
        )
    }
}

