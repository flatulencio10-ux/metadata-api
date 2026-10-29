package com.customlauncher.ui.edit.inspector

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.BodyType
import com.customlauncher.core.model.ColliderShape
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.NodeAnimations
import com.customlauncher.core.model.NodeLayer
import com.customlauncher.core.model.PathPoint
import com.customlauncher.core.model.PhysicsSpec
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.stickers.AnimationEditor
import com.customlauncher.ui.stickers.DarkSlider
import com.customlauncher.ui.stickers.Hint
import com.customlauncher.widget.WidgetRegistry
import com.customlauncher.widgets.sticker.StickerWidget

internal val InspectorBg = Color(0xF0141619)
internal val InspectorAccent = Color(0xFF7C5CFF)

enum class InspectorTab(val label: String, val icon: ImageVector) {
    TRANSFORM("Transform", Icons.Filled.OpenWith),
    LOOK("Look", Icons.Filled.Palette),
    PHYSICS("Physics", Icons.Filled.SportsBaseball),
    BEHAVIORS("Behaviors", Icons.Filled.Rule),
    ANIMATION("Animation", Icons.Filled.Animation),
    SCRIPT("Script", Icons.Filled.Code),
}

/**
 * Pro-tool inspector for the selected node (Figma + Unity style): glassy dark sheet with
 * Transform / Look / Physics / Behaviors / Animation / Script tabs. Slider drags preview live
 * and commit when released.
 */
@Composable
fun NodeInspector(stored: Node, profile: LayoutProfile, vm: LauncherViewModel, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(InspectorTab.TRANSFORM) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val haptics = LocalHapticFeedback.current
    val node = vm.nodePreview?.takeIf { it.id == stored.id } ?: stored
    val def = WidgetRegistry.find(node.type)

    /** Live preview while dragging; [commit] on release. */
    fun preview(n: Node) { vm.nodePreview = n }
    fun commit() = vm.commitNodePreview()
    fun set(n: Node) = vm.updateWidget(n.id) { n }

    Surface(modifier, color = InspectorBg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 12.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                def?.icon?.let { Icon(it, null, tint = InspectorAccent, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(node.name.ifBlank { def?.name ?: node.type }, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, "Collapse", tint = Color.White)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InspectorTab.entries.forEach { t ->
                    TabPill(t, t == tab) { tab = t; expanded = true; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
            }
            if (expanded) {
                Column(
                    Modifier
                        .padding(top = 8.dp)
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (tab) {
                        InspectorTab.TRANSFORM -> TransformTab(node, ::preview, ::commit, ::set)
                        InspectorTab.LOOK -> LookTab(node, vm, ::preview, ::commit)
                        InspectorTab.PHYSICS -> PhysicsTab(node, vm, ::set)
                        InspectorTab.BEHAVIORS -> BehaviorsTab(node, profile, vm)
                        InspectorTab.ANIMATION -> AnimationTab(node, profile, vm, ::set)
                        InspectorTab.SCRIPT -> ScriptTab(node, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(tab: InspectorTab, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.05f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tab")
    Row(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(if (selected) InspectorAccent else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(tab.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun <T> DarkChips(values: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            FilterChip(
                selected = v == selected, onClick = { onPick(v) }, label = { Text(label(v)) },
                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = InspectorAccent, selectedLabelColor = Color.White),
            )
        }
    }
}

@Composable
internal fun DarkSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Slider that previews while dragging and commits on release. */
@Composable
internal fun LiveSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onPreview: (Float) -> Unit, onCommit: () -> Unit, format: (Float) -> String) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = Color.White)) {
            com.customlauncher.ui.common.LabeledSlider(label, value, range, onPreview, format = format, onChangeFinished = onCommit)
        }
    }
}

@Composable
private fun TransformTab(n: Node, preview: (Node) -> Unit, commit: () -> Unit, set: (Node) -> Unit) {
    LiveSlider("X", n.x, -400f..1200f, { preview(n.copy(x = it)) }, commit) { "${it.toInt()} dp" }
    LiveSlider("Y", n.y, -400f..2000f, { preview(n.copy(y = it)) }, commit) { "${it.toInt()} dp" }
    LiveSlider("Width", n.width, 16f..1000f, { preview(n.copy(width = it)) }, commit) { "${it.toInt()} dp" }
    LiveSlider("Height", n.height, 16f..1600f, { preview(n.copy(height = it)) }, commit) { "${it.toInt()} dp" }
    LiveSlider("Rotation", n.rotation, -180f..180f, { preview(n.copy(rotation = it)) }, commit) { "${it.toInt()}°" }
    LiveSlider("Scale", n.scale, 0.1f..5f, { preview(n.copy(scale = it)) }, commit) { "%.2f×".format(it) }
    LiveSlider("Pivot X", n.pivotX, 0f..1f, { preview(n.copy(pivotX = it)) }, commit) { "%.0f%%".format(it * 100) }
    LiveSlider("Pivot Y", n.pivotY, 0f..1f, { preview(n.copy(pivotY = it)) }, commit) { "%.0f%%".format(it * 100) }
    DarkSwitch("Flip horizontally", n.flipX) { set(n.copy(flipX = it)) }
    DarkSwitch("Flip vertically", n.flipY) { set(n.copy(flipY = it)) }
    DarkSwitch("Lock position", n.locked) { set(n.copy(locked = it)) }
    Text("Layer", color = Color.White, modifier = Modifier.padding(top = 6.dp))
    DarkChips(NodeLayer.entries, n.layer, { if (it == NodeLayer.NORMAL) "Below panels" else "Above panels" }) { set(n.copy(layer = it)) }
}

@Composable
private fun LookTab(n: Node, vm: LauncherViewModel, preview: (Node) -> Unit, commit: () -> Unit) {
    val s = n.style
    if (n.type == StickerWidget.id) {
        com.customlauncher.ui.stickers.ToolPill(Icons.Filled.Palette, "Open sticker editor", selected = false, highlight = true) {
            vm.stickerEditorTarget = n.id
        }
    } else {
        com.customlauncher.ui.stickers.ToolPill(Icons.Filled.Palette, "Widget settings", selected = false, highlight = true) {
            vm.configuringWidgetId = n.id
        }
    }
    LiveSlider("Opacity", s.opacity, 0.05f..1f, { preview(n.copy(style = s.copy(opacity = it))) }, commit) { "%.0f%%".format(it * 100) }
    if (n.type != StickerWidget.id) {
        DarkSwitch("Use global card style", s.useCardStyle) { vm.updateWidget(n.id) { w -> w.copy(style = w.style.copy(useCardStyle = it)) } }
        if (!s.useCardStyle) {
            LiveSlider("Corner radius", s.cornerRadiusDp, 0f..64f, { preview(n.copy(style = s.copy(cornerRadiusDp = it))) }, commit) { "${it.toInt()} dp" }
            LiveSlider("Background blur", s.blurDp, 0f..40f, { preview(n.copy(style = s.copy(blurDp = it))) }, commit) { "${it.toInt()} dp" }
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = Color.White)) {
                ColorRow("Background tint", s.backgroundColor) { c -> vm.updateWidget(n.id) { w -> w.copy(style = w.style.copy(backgroundColor = c)) } }
            }
        }
        LiveSlider("Padding", s.paddingDp, 0f..32f, { preview(n.copy(style = s.copy(paddingDp = it))) }, commit) { "${it.toInt()} dp" }
    }
}

@Composable
private fun PhysicsTab(n: Node, vm: LauncherViewModel, set: (Node) -> Unit) {
    val spec = n.physics ?: PhysicsSpec(type = BodyType.NONE)
    fun update(p: PhysicsSpec) = set(n.copy(physics = p.takeIf { it.type != BodyType.NONE }))
    Text("Body", color = Color.White)
    DarkChips(BodyType.entries, spec.type, { it.label }) { update(spec.copy(type = it)) }
    if (spec.type == BodyType.NONE) {
        Hint("Dynamic objects fall, bounce and can be thrown or spun. Static ones are solid obstacles; kinematic ones move only when told to.")
        return
    }
    DarkSlider("Mass", spec.mass, 0.1f..20f, { update(spec.copy(mass = it)) }) { "%.1f kg".format(it) }
    DarkSlider("Friction", spec.friction, 0f..1f, { update(spec.copy(friction = it)) }) { "%.2f".format(it) }
    DarkSlider("Bounciness", spec.restitution, 0f..1f, { update(spec.copy(restitution = it)) }) { "%.2f".format(it) }
    DarkSlider("Linear damping", spec.linearDamping, 0f..5f, { update(spec.copy(linearDamping = it)) }) { "%.2f".format(it) }
    DarkSlider("Angular damping", spec.angularDamping, 0f..5f, { update(spec.copy(angularDamping = it)) }) { "%.2f".format(it) }
    DarkSlider("Gravity scale", spec.gravityScale, -2f..3f, { update(spec.copy(gravityScale = it)) }) { "%.1f×".format(it) }
    DarkSwitch("Keep upright (no rotation)", spec.fixedRotation) { update(spec.copy(fixedRotation = it)) }
    Text("Collider", color = Color.White, modifier = Modifier.padding(top = 6.dp))
    DarkChips(ColliderShape.entries, spec.collider, { it.label }) { update(spec.copy(collider = it)) }
    if (spec.collider == ColliderShape.POLYGON) PolygonEditor(spec.polygon) { update(spec.copy(polygon = it)) }
    DarkSwitch("Pin to the page (spinner / pendulum)", spec.pinned) { update(spec.copy(pinned = it)) }
    if (spec.pinned) {
        DarkSlider("Pin X", spec.pinX, -1f..2f, { update(spec.copy(pinX = it)) }) { "%.0f%%".format(it * 100) }
        DarkSlider("Pin Y", spec.pinY, -1f..2f, { update(spec.copy(pinY = it)) }) { "%.0f%%".format(it * 100) }
    }
    com.customlauncher.ui.stickers.ToolPill(Icons.Filled.SportsBaseball, "World settings (gravity, walls…)", selected = false) {
        vm.pageSettingsOpen = true
    }
    SpeedVisualsEditor(n.speedVisuals) { set(n.copy(speedVisuals = it)) }
}

/** Speed-reactive visuals: motion blur, zoetrope frames, stretch, trails, sparks. */
@Composable
private fun SpeedVisualsEditor(v: com.customlauncher.core.model.SpeedVisuals?, onChange: (com.customlauncher.core.model.SpeedVisuals?) -> Unit) {
    Text("Speed-reactive visuals", color = InspectorAccent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
    DarkSwitch("Enabled", v != null) { onChange(if (it) com.customlauncher.core.model.SpeedVisuals() else null) }
    if (v == null) return
    DarkSwitch("Motion blur when spinning", v.motionBlur) { onChange(v.copy(motionBlur = it)) }
    DarkSlider("Blur from", v.blurFromRpm, 10f..2000f, { onChange(v.copy(blurFromRpm = it)) }) { "${it.toInt()} rpm" }
    DarkSlider("Zoetrope frames from", v.framesFromRpm, 50f..4000f, { onChange(v.copy(framesFromRpm = it)) }) { "${it.toInt()} rpm" }
    Text("Zoetrope sprite sheet (frames play with the rotation)", color = Color.White)
    com.customlauncher.ui.objects.StickerChooser(v.frames ?: "") { onChange(v.copy(frames = it)) }
    if (v.frames != null) {
        DarkSlider("Columns", v.frameCols.toFloat(), 1f..16f, { onChange(v.copy(frameCols = it.toInt())) }) { "${it.toInt()}" }
        DarkSlider("Rows", v.frameRows.toFloat(), 1f..16f, { onChange(v.copy(frameRows = it.toInt())) }) { "${it.toInt()}" }
    }
    DarkSwitch("Stretch when thrown fast", v.stretchWhenFast) { onChange(v.copy(stretchWhenFast = it)) }
    DarkSwitch("Motion trails", v.trails) { onChange(v.copy(trails = it)) }
    DarkSwitch("Speed lines", v.speedLines) { onChange(v.copy(speedLines = it)) }
    DarkSwitch("Sparks on collision", v.sparksOnCollision) { onChange(v.copy(sparksOnCollision = it)) }
    DarkSwitch("Haptic tick per turn (slow spins)", v.tickPerTurn) { onChange(v.copy(tickPerTurn = it)) }
}

/** Tap up to 8 points inside the node's box to define a custom convex collider. */
@Composable
private fun PolygonEditor(points: List<PathPoint>, onChange: (List<PathPoint>) -> Unit) {
    Hint("Tap to add up to 8 points (convex outline). Tap here again to reset when full.")
    Box(
        Modifier
            .size(160.dp)
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .pointerInput(points) {
                detectTapGestures { p ->
                    val pt = PathPoint(p.x / size.width, p.y / size.height)
                    onChange(if (points.size >= 8) listOf(pt) else points + pt)
                }
            }
    ) {
        Canvas(Modifier.size(160.dp)) {
            if (points.size > 1) {
                val path = Path().apply {
                    points.forEachIndexed { i, q -> if (i == 0) moveTo(q.x * size.width, q.y * size.height) else lineTo(q.x * size.width, q.y * size.height) }
                    close()
                }
                drawPath(path, InspectorAccent, style = Stroke(2.dp.toPx()))
            }
            points.forEach { drawCircle(Color.White, 4.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
        }
    }
}

@Composable
private fun AnimationTab(n: Node, profile: LayoutProfile, vm: LauncherViewModel, set: (Node) -> Unit) {
    var timeline by rememberSaveable { mutableStateOf(n.clips.isNotEmpty()) }
    DarkChips(listOf(false, true), timeline, { if (it) "Keyframe timeline" else "Effects" }) { timeline = it }
    if (timeline) {
        com.customlauncher.ui.anim.ClipTimeline(n, vm) { clips -> vm.updateWidget(n.id) { it.copy(clips = clips) } }
        return
    }
    AnimationEditor(
        anim = n.animation ?: NodeAnimations(), profile = profile, onPreview = null,
        onChange = { a -> set(n.copy(animation = a.takeIf { !it.isEmpty })) },
    )
}

@Composable
internal fun BehaviorsTab(n: Node, profile: LayoutProfile, vm: LauncherViewModel) {
    com.customlauncher.ui.objects.BehaviorEditor(n, profile, vm)
}

@Composable
internal fun ScriptTab(n: Node, vm: LauncherViewModel) {
    Hint("Scripts: JavaScript hooks for this object.")
}
