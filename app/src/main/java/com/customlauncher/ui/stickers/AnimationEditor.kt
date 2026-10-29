package com.customlauncher.ui.stickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.customlauncher.actions.ActionCatalog
import com.customlauncher.core.model.AnimTiming
import com.customlauncher.core.model.Direction
import com.customlauncher.core.model.Easing
import com.customlauncher.core.model.EntranceEffect
import com.customlauncher.core.model.EntranceTrigger
import com.customlauncher.core.model.IdleEffect
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.NodeAnimations
import com.customlauncher.core.model.PathPoint
import com.customlauncher.core.model.TapEffect
import com.customlauncher.ui.common.ActionPickerDialog

private val Accent = Color(0xFF7C5CFF)

/** Entrance / idle loop / on-tap animation slots plus parallax, for any node. */
@Composable
fun AnimationEditor(anim: NodeAnimations, profile: LayoutProfile, onPreview: (() -> Unit)?, onChange: (NodeAnimations) -> Unit) {
    var pickingAction by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Animations", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (onPreview != null) ToolPill(Icons.Filled.TouchApp, "Preview", selected = false, highlight = true, onClick = onPreview)
        }

        Section("Entrance")
        Chips(EntranceEffect.entries, anim.entrance, { it.label }) { onChange(anim.copy(entrance = it)) }
        if (anim.entrance != EntranceEffect.NONE) {
            Chips(EntranceTrigger.entries, anim.entranceTrigger, { "On: " + it.label }) { onChange(anim.copy(entranceTrigger = it)) }
            if (anim.entrance == EntranceEffect.FLY_IN || anim.entrance == EntranceEffect.SLIDE_FADE) {
                Chips(Direction.entries, anim.entranceFrom, { "From " + it.name.lowercase() }) { onChange(anim.copy(entranceFrom = it)) }
            }
            TimingEditor(anim.entranceTiming, allowInfinite = false) { onChange(anim.copy(entranceTiming = it)) }
        }

        Section("Idle loop")
        Chips(IdleEffect.entries, anim.idle, { it.label }) { onChange(anim.copy(idle = it)) }
        if (anim.idle != IdleEffect.NONE) {
            DarkSlider("Loop length", anim.idleTiming.durationMs.toFloat(), 300f..10_000f, {
                onChange(anim.copy(idleTiming = anim.idleTiming.copy(durationMs = it.toInt())))
            }) { "%.1f s".format(it / 1000f) }
            DarkSlider("Strength", anim.idleAmount, 0.1f..2f, { onChange(anim.copy(idleAmount = it)) }) { "%.1f×".format(it) }
            if (anim.idle == IdleEffect.SWAY) {
                DarkSlider("Pivot X", anim.swayPivotX, 0f..1f, { onChange(anim.copy(swayPivotX = it)) }) { "%.0f%%".format(it * 100) }
                DarkSlider("Pivot Y", anim.swayPivotY, 0f..1f, { onChange(anim.copy(swayPivotY = it)) }) { "%.0f%%".format(it * 100) }
            }
            if (anim.idle == IdleEffect.DRIFT) DriftPathRecorder(anim.driftPath) { onChange(anim.copy(driftPath = it)) }
            Hint("Idle loops pause off screen, with the screen off, and in battery saver / below 15% battery.")
        }

        Section("On tap")
        Chips(TapEffect.entries, anim.tap, { it.label }) { onChange(anim.copy(tap = it)) }
        if (anim.tap != TapEffect.NONE && anim.tap != TapEffect.PLAY_GIF_ONCE) {
            TimingEditor(anim.tapTiming, allowInfinite = false) { onChange(anim.copy(tapTiming = it)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Also run: " + ActionCatalog.label(anim.tapAction, profile),
                color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            ToolPill(Icons.Filled.Gesture, "Choose", selected = false) { pickingAction = true }
        }

        Section("Parallax")
        DarkSlider("Depth", anim.parallaxDepth, 0f..1f, { onChange(anim.copy(parallaxDepth = it)) }) { "%.0f%%".format(it * 100) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Follow device tilt", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = anim.tiltParallax, onCheckedChange = { onChange(anim.copy(tiltParallax = it)) })
        }
    }
    if (pickingAction) {
        ActionPickerDialog(profile, anim.tapAction, onDismiss = { pickingAction = false }) {
            onChange(anim.copy(tapAction = it)); pickingAction = false
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun <T> Chips(values: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            FilterChip(
                selected = v == selected,
                onClick = { onPick(v) },
                label = { Text(label(v)) },
                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Accent, selectedLabelColor = Color.White),
            )
        }
    }
}

@Composable
private fun TimingEditor(t: AnimTiming, allowInfinite: Boolean, onChange: (AnimTiming) -> Unit) {
    DarkSlider("Duration", t.durationMs.toFloat(), 100f..4000f, { onChange(t.copy(durationMs = it.toInt())) }) { "${it.toInt()} ms" }
    DarkSlider("Delay", t.delayMs.toFloat(), 0f..3000f, { onChange(t.copy(delayMs = it.toInt())) }) { "${it.toInt()} ms" }
    Chips(Easing.entries, t.easing, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { onChange(t.copy(easing = it)) }
    DarkSlider("Repeat", t.repeat.coerceAtLeast(0).toFloat(), 0f..10f, { onChange(t.copy(repeat = it.toInt())) }) {
        if (allowInfinite && it < 0) "∞" else "${it.toInt()}×"
    }
}

/** Draw a loop with a finger; the node drifts along it (dp relative to its rest position). */
@Composable
internal fun DriftPathRecorder(path: List<PathPoint>, onPath: (List<PathPoint>) -> Unit) {
    val drawing = remember { mutableStateListOf<Offset>() }
    Text("Draw the drift path", color = Color.White, style = MaterialTheme.typography.bodySmall)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { drawing.clear(); drawing += it },
                    onDragEnd = {
                        if (drawing.size > 3) {
                            val start = drawing.first()
                            // Keep at most ~40 points, relative to the start, in dp.
                            val step = maxOf(1, drawing.size / 40)
                            onPath(drawing.filterIndexed { i, _ -> i % step == 0 }.map {
                                PathPoint((it.x - start.x) / density, (it.y - start.y) / density)
                            })
                        }
                    },
                ) { change, _ -> drawing += change.position }
            }
    ) {
        val pts = if (drawing.isNotEmpty()) drawing.toList()
        else path.map { Offset(size.width / 2 + it.x.dp.toPx(), size.height / 2 + it.y.dp.toPx()) }
        if (pts.size > 1) {
            val p = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(p, Accent, style = Stroke(3.dp.toPx()))
        }
    }
}
