package com.customlauncher.ui.anim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.customlauncher.core.anim.KeyframeMath
import com.customlauncher.core.anim.MotionMath
import com.customlauncher.core.model.KProp
import com.customlauncher.core.model.Keyframe
import com.customlauncher.core.model.KeyframeClip
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.Track
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.edit.inspector.DarkChips
import com.customlauncher.ui.stickers.DarkSlider
import com.customlauncher.ui.stickers.Hint
import com.customlauncher.ui.stickers.ToolPill
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Accent = Color(0xFF7C9CFF)
private val KeyColor = Color(0xFFFFC94D)

/** Slider range per animatable property. */
private fun rangeOf(p: KProp): ClosedFloatingPointRange<Float> = when (p) {
    KProp.X, KProp.Y -> -400f..400f
    KProp.ROTATION -> -720f..720f
    KProp.SCALE -> 0f..4f
    KProp.OPACITY, KProp.TINT -> 0f..1f
    KProp.SKEW_X -> -1f..1f
    KProp.BLUR -> 0f..40f
}

/** Named easing presets as cubic-bezier control points. */
private val EasingPresets = listOf(
    "Linear" to floatArrayOf(0f, 0f, 1f, 1f),
    "Ease" to floatArrayOf(0.25f, 0.1f, 0.25f, 1f),
    "In" to floatArrayOf(0.42f, 0f, 1f, 1f),
    "Out" to floatArrayOf(0f, 0f, 0.58f, 1f),
    "In-out" to floatArrayOf(0.42f, 0f, 0.58f, 1f),
    "Overshoot" to floatArrayOf(0.34f, 1.56f, 0.64f, 1f),
    "Anticipate" to floatArrayOf(0.36f, -0.6f, 0.7f, 1f),
)

/**
 * Keyframe timeline for one node: named clips, one lane per property with keyframe diamonds,
 * a scrubber that previews on the canvas, a bezier easing editor for the selected segment and
 * record mode (drag the object on the page to capture a motion path).
 */
@Composable
fun ClipTimeline(node: Node, vm: LauncherViewModel, onClips: (List<KeyframeClip>) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var selectedName by remember(node.id) { mutableStateOf(node.clips.firstOrNull()?.name) }
    val clip = node.clips.firstOrNull { it.name == selectedName } ?: node.clips.firstOrNull()
    var playhead by remember(node.id) { mutableStateOf(0f) }
    var selectedKey by remember(node.id) { mutableStateOf<Pair<KProp, Int>?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }

    fun replace(c: KeyframeClip) = onClips(node.clips.map { if (it.name == clip?.name) c else it })
    fun scrubTo(t: Float) {
        val c = clip ?: return
        playhead = t.coerceIn(0f, c.durationMs.toFloat())
        vm.clipScrub = node.id to ActivePose(c.tintColor, KeyframeMath.sample(c, playhead))
    }
    androidx.compose.runtime.DisposableEffect(node.id) { onDispose { vm.clipScrub = null } }

    // Clip picker.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        node.clips.forEach { c ->
            ToolPill(Icons.Filled.PlayArrow, c.name, selected = c.name == clip?.name) { selectedName = c.name; selectedKey = null; playhead = 0f }
        }
        ToolPill(Icons.Filled.Add, "New clip", selected = false, highlight = node.clips.isEmpty()) {
            var i = node.clips.size + 1
            while (node.clips.any { it.name == "clip$i" }) i++
            val c = KeyframeClip("clip$i")
            onClips(node.clips + c)
            selectedName = c.name
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    if (clip == null) {
        Hint("Clips are named keyframe animations. Make one, then play it from a behaviour (\"Play clip\") or a script (this.play(\"name\")).")
        return
    }

    var nameDraft by remember(clip.name) { mutableStateOf(clip.name) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            nameDraft, { nameDraft = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
            label = { Text("Clip name") }, singleLine = true, modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
        )
        IconButton(onClick = {
            if (nameDraft.isNotBlank() && node.clips.none { it.name == nameDraft }) { replace(clip.copy(name = nameDraft)); selectedName = nameDraft }
        }) { Text("✓", color = Color.White) }
        IconButton(onClick = { onClips(node.clips.filter { it.name != clip.name }); selectedName = null; vm.clipScrub = null }) {
            Icon(Icons.Filled.Delete, "Delete clip", tint = Color.White)
        }
    }

    // Transport: play / stop, record.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val playing = playJob?.isActive == true
        ToolPill(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing) "Stop" else "Play", selected = playing) {
            if (playing) { playJob?.cancel(); vm.clipScrub = null } else {
                playJob = scope.launch {
                    play(clip.copy(repeat = 0)) { pose -> vm.clipScrub = node.id to ActivePose(clip.tintColor, pose) }
                    vm.clipScrub = null
                }
            }
        }
        ToolPill(Icons.Filled.FiberManualRecord, "Record path", selected = vm.clipRecording != null, highlight = true) {
            vm.clipRecording = node.id to clip.name
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        Text("%.2fs".format(playhead / 1000f), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }

    // Ruler + lanes.
    TimeRuler(clip.durationMs, playhead) { scrubTo(it) }
    KProp.entries.forEach { prop ->
        val track = clip.tracks.firstOrNull { it.property == prop }
        Lane(
            prop, track, clip.durationMs, playhead,
            selectedIndex = selectedKey?.takeIf { it.first == prop }?.second,
            onSelect = { i -> selectedKey = prop to i; track?.keys?.getOrNull(i)?.let { scrubTo(it.timeMs.toFloat()) }; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onAdd = {
                val value = track?.let { KeyframeMath.sample(it, playhead) } ?: prop.default
                val updated = KeyframeMath.setKey(clip, prop, playhead.toInt(), value)
                replace(updated)
                selectedKey = prop to updated.tracks.first { it.property == prop }.keys.indexOfFirst { it.timeMs == playhead.toInt() }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    // Selected key: value, time, easing to the next key.
    val sel = selectedKey
    val selTrack = sel?.let { s -> clip.tracks.firstOrNull { it.property == s.first } }
    val key = sel?.let { selTrack?.keys?.getOrNull(it.second) }
    if (sel != null && selTrack != null && key != null) {
        fun setKey(k: Keyframe) {
            val keys = selTrack.keys.toMutableList().also { it[sel.second] = k }.sortedBy { it.timeMs }
            val updated = clip.copy(tracks = clip.tracks.map { if (it.property == sel.first) Track(sel.first, keys) else it })
            replace(updated)
            selectedKey = sel.first to keys.indexOf(k)
            vm.clipScrub = node.id to ActivePose(clip.tintColor, KeyframeMath.sample(updated, playhead))
        }
        Text("${sel.first.label} key", color = Accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        DarkSlider("Value", key.value, rangeOf(sel.first), { setKey(key.copy(value = it)) }) { "%.2f".format(it) }
        DarkSlider("Time", key.timeMs.toFloat(), 0f..clip.durationMs.toFloat(), { setKey(key.copy(timeMs = it.toInt())) }) { "%.2fs".format(it / 1000f) }
        if (sel.second < selTrack.keys.lastIndex) {
            Text("Easing to next key", color = Color.White)
            DarkChips(EasingPresets.map { it.first }, EasingPresets.firstOrNull { it.second.contentEquals(floatArrayOf(key.x1, key.y1, key.x2, key.y2)) }?.first ?: "", { it }) { name ->
                val c = EasingPresets.first { it.first == name }.second
                setKey(key.copy(x1 = c[0], y1 = c[1], x2 = c[2], y2 = c[3]))
            }
            BezierEditor(key) { x1, y1, x2, y2 -> setKey(key.copy(x1 = x1, y1 = y1, x2 = x2, y2 = y2)) }
        }
        ToolPill(Icons.Filled.Delete, "Delete key", selected = false) {
            val keys = selTrack.keys.filterIndexed { i, _ -> i != sel.second }
            replace(clip.copy(tracks = clip.tracks.map { if (it.property == sel.first) Track(sel.first, keys) else it }.filter { it.keys.isNotEmpty() }))
            selectedKey = null
        }
    }

    // Clip settings.
    Text("Clip", color = Accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    DarkSlider("Duration", clip.durationMs.toFloat(), 100f..10_000f, { replace(clip.copy(durationMs = it.toInt())) }) { "%.1fs".format(it / 1000f) }
    DarkChips(listOf(0, 1, 2, -1), clip.repeat, { if (it < 0) "Loop" else if (it == 0) "Once" else "${it + 1}×" }) { replace(clip.copy(repeat = it)) }
    if (clip.tracks.any { it.property == KProp.TINT }) {
        DarkChips(listOf(0xFFFF4FA3, 0xFFFFC94D, 0xFF4DD9FF, 0xFF7CFF8A, 0xFFFFFFFF, 0xFF000000), clip.tintColor, { "●" }) { replace(clip.copy(tintColor = it)) }
    }
    Hint("Tap a lane to add a key at the playhead. Drag the ruler to scrub — the object previews on the page.")
}

@Composable
private fun TimeRuler(durationMs: Int, playhead: Float, onScrub: (Float) -> Unit) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(start = 84.dp, top = 6.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(durationMs) {
                detectTapGestures { onScrub(it.x / size.width * durationMs) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures { change, _ -> onScrub(change.position.x / size.width * durationMs) }
            }
    ) {
        val step = when { durationMs <= 1000 -> 100; durationMs <= 4000 -> 250; else -> 1000 }
        var t = 0
        while (t <= durationMs) {
            val x = t.toFloat() / durationMs * size.width
            val major = t % (step * 4) == 0
            drawLine(Color.White.copy(alpha = if (major) 0.6f else 0.25f), Offset(x, size.height * if (major) 0.3f else 0.6f), Offset(x, size.height), 1.dp.toPx())
            t += step
        }
        val px = playhead / durationMs * size.width
        drawLine(Accent, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
        drawCircle(Accent, 4.dp.toPx(), Offset(px, 4.dp.toPx()))
    }
}

@Composable
private fun Lane(
    prop: KProp, track: Track?, durationMs: Int, playhead: Float,
    selectedIndex: Int?, onSelect: (Int) -> Unit, onAdd: () -> Unit,
) {
    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(prop.label.substringBefore(" ("), color = Color.White.copy(alpha = if (track != null) 1f else 0.5f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(84.dp))
        val keys = track?.keys.orEmpty()
        Canvas(
            Modifier
                .weight(1f)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .pointerInput(keys, durationMs) {
                    detectTapGestures { p ->
                        val hit = keys.indexOfFirst { kotlin.math.abs(it.timeMs.toFloat() / durationMs * size.width - p.x) < 14.dp.toPx() }
                        if (hit >= 0) onSelect(hit) else onAdd()
                    }
                }
        ) {
            if (keys.size > 1) {
                val y = size.height / 2
                drawLine(KeyColor.copy(alpha = 0.35f), Offset(keys.first().timeMs.toFloat() / durationMs * size.width, y), Offset(keys.last().timeMs.toFloat() / durationMs * size.width, y), 2.dp.toPx())
            }
            keys.forEachIndexed { i, k ->
                val c = Offset(k.timeMs.toFloat() / durationMs * size.width, size.height / 2)
                val r = (if (i == selectedIndex) 7 else 5).dp.toPx()
                val diamond = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
                drawPath(diamond, if (i == selectedIndex) Color.White else KeyColor)
            }
            val px = playhead / durationMs * size.width
            drawLine(Accent.copy(alpha = 0.6f), Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
        }
    }
}

/** Drag the two handles to shape the segment's cubic-bezier easing (like CSS). */
@Composable
private fun BezierEditor(key: Keyframe, onChange: (Float, Float, Float, Float) -> Unit) {
    var dragging by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .pointerInput(key) {
                    // The unit square is inset so overshooting handles (y outside 0..1) stay reachable.
                    val pad = size.height * 0.25f
                    val h = size.height - 2 * pad
                    fun toCurve(p: Offset) = Offset((p.x / size.width).coerceIn(0f, 1f), ((size.height - pad - p.y) / h).coerceIn(-1f, 2f))
                    var x1 = key.x1; var y1 = key.y1; var x2 = key.x2; var y2 = key.y2
                    detectDragGestures(
                        onDragStart = { p ->
                            val c = toCurve(p)
                            dragging = if (kotlin.math.hypot(c.x - x1, c.y - y1) < kotlin.math.hypot(c.x - x2, c.y - y2)) 1 else 2
                        },
                        onDragEnd = { dragging = 0 },
                    ) { change, _ ->
                        val c = toCurve(change.position)
                        if (dragging == 1) { x1 = c.x; y1 = c.y } else { x2 = c.x; y2 = c.y }
                        onChange(x1, y1, x2, y2)
                    }
                }
        ) {
            val pad = size.height * 0.25f
            val h = size.height - 2 * pad
            fun pt(x: Float, y: Float) = Offset(x * size.width, size.height - pad - y * h)
            drawLine(Color.White.copy(alpha = 0.15f), pt(0f, 0f), pt(1f, 0f), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.15f), pt(0f, 1f), pt(1f, 1f), 1.dp.toPx())
            val curve = Path().apply {
                moveTo(pt(0f, 0f).x, pt(0f, 0f).y)
                for (i in 1..48) {
                    val t = i / 48f
                    val p = pt(t, MotionMath.cubicBezier(key.x1, key.y1, key.x2, key.y2, t))
                    lineTo(p.x, p.y)
                }
            }
            drawPath(curve, Color.White, style = Stroke(2.5.dp.toPx()))
            val h1 = pt(key.x1, key.y1)
            val h2 = pt(key.x2, key.y2)
            drawLine(Accent, pt(0f, 0f), h1, 1.5.dp.toPx())
            drawLine(Accent, pt(1f, 1f), h2, 1.5.dp.toPx())
            drawCircle(KeyColor, 7.dp.toPx(), h1)
            drawCircle(KeyColor, 7.dp.toPx(), h2)
        }
    }
    Spacer(Modifier.height(2.dp))
}

/**
 * Full-screen capture while a clip is being recorded: drag anywhere and the object follows
 * the finger on the canvas; releasing turns the path into X/Y keyframe tracks.
 */
@Composable
fun ClipRecorderOverlay(vm: LauncherViewModel, node: Node, clipName: String, onClips: (List<KeyframeClip>) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val samples = remember { mutableListOf<Triple<Int, Float, Float>>() }
    val current by rememberUpdatedState(node)
    var recording by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.15f))
            .pointerInput(node.id, clipName) {
                var start = 0L
                detectDragGestures(
                    onDragStart = { p ->
                        samples.clear(); start = System.currentTimeMillis(); recording = true
                        samples += Triple(0, p.x / density, p.y / density)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        recording = false
                        val clip = current.clips.firstOrNull { it.name == clipName }
                        if (clip != null && samples.size > 2) onClips(current.clips.map { if (it.name == clipName) KeyframeMath.fromRecording(clip, samples.toList()) else it })
                        vm.clipScrub = null
                        vm.clipRecording = null
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = { recording = false; vm.clipScrub = null; vm.clipRecording = null },
                ) { change, _ ->
                    val t = (System.currentTimeMillis() - start).toInt()
                    val p = change.position
                    samples += Triple(t, p.x / density, p.y / density)
                    val first = samples.first()
                    val pose = KProp.entries.associateWith { it.default } + mapOf(KProp.X to p.x / density - first.second, KProp.Y to p.y / density - first.third)
                    vm.clipScrub = node.id to ActivePose(0, pose)
                }
            }
    ) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xE61A1C24))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.FiberManualRecord, null, tint = if (recording) Color(0xFFFF4D4D) else Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (recording) "Recording “$clipName”…" else "Drag anywhere to record a motion path", color = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("Cancel", color = Accent, modifier = Modifier.clickable { vm.clipRecording = null; vm.clipScrub = null })
        }
    }
}

private fun Modifier.fillMaxHeight(): Modifier = this.then(Modifier.fillMaxHeight())
