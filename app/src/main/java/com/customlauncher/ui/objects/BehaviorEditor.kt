package com.customlauncher.ui.objects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.model.BAction
import com.customlauncher.core.model.BCondition
import com.customlauncher.core.model.BTrigger
import com.customlauncher.core.model.Behavior
import com.customlauncher.core.model.CompareOp
import com.customlauncher.core.model.Easing
import com.customlauncher.core.model.HapticPattern
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.NodeAnimations
import com.customlauncher.core.model.NodeState
import com.customlauncher.core.model.ObjectLogic
import com.customlauncher.core.model.TapEffect
import com.customlauncher.core.model.TiltDirection
import com.customlauncher.core.model.VarOp
import com.customlauncher.core.model.VarScope
import com.customlauncher.core.model.Zone
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.ActionPickerDialog
import com.customlauncher.ui.common.AppPickerDialog
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.stickers.AnimationEditor
import com.customlauncher.ui.stickers.DriftPathRecorder
import com.customlauncher.widget.WidgetRegistry
import com.customlauncher.widgets.sticker.StickerWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Accent = Color(0xFF7C5CFF)
private val CardBg = Color.White.copy(alpha = 0.06f)

/** What forms need to offer choices (other objects, states, stickers). */
class FormContext(val profile: LayoutProfile?, val self: Node, val others: List<Node>) {
    val states: List<String> get() = self.logic?.states?.map { it.name }.orEmpty()
    fun nodeName(id: String?): String = others.firstOrNull { it.id == id }?.let { n ->
        n.name.ifBlank { WidgetRegistry.find(n.type)?.name ?: n.type }
    } ?: "?"
}

/**
 * Visual rule editor: "When ___ · if ___ · do ___, ___". Behaviours can belong to the
 * object or to one of its states; plus the state machine and variables.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BehaviorEditor(node: Node, profile: LayoutProfile, vm: LauncherViewModel) {
    val logic = node.logic ?: ObjectLogic()
    val page = profile.currentActivity.pages.getOrNull(vm.currentPage)
    val ctx = FormContext(profile, node, page?.widgets.orEmpty().filter { it.id != node.id } + profile.currentActivity.pinned.filter { it.id != node.id })
    fun save(l: ObjectLogic) = vm.updateWidget(node.id) { it.copy(logic = l.takeIf { !it.isEmpty }) }
    var scope by remember { mutableStateOf<String?>(null) }
    var editingState by remember { mutableStateOf<NodeState?>(null) }

    val behaviors = if (scope == null) logic.behaviors else logic.states.firstOrNull { it.name == scope }?.behaviors.orEmpty()
    fun setBehaviors(list: List<Behavior>) = save(
        if (scope == null) logic.copy(behaviors = list)
        else logic.copy(states = logic.states.map { if (it.name == scope) it.copy(behaviors = list) else it })
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Apply a template (copies its physics, behaviours and animation)", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.customlauncher.objects.Presets.all.filter { it.logic != null || it.physics != null }.forEach { p ->
                DarkChip(p.name, false) { vm.applyTemplate(node.id, p) }
            }
        }
        Text("Behaviours of", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkChip("Object", scope == null) { scope = null }
            logic.states.forEach { s -> DarkChip("State: ${s.name}", scope == s.name) { scope = s.name } }
        }
        behaviors.forEach { b ->
            BehaviorCard(b, ctx,
                onChange = { nb -> setBehaviors(behaviors.map { if (it.id == b.id) nb else it }) },
                onDelete = { setBehaviors(behaviors.filterNot { it.id == b.id }) })
        }
        AddButton("Add behaviour") { setBehaviors(behaviors + Behavior()) }

        SectionLabel("States")
        Text(
            "\"idle\", \"tapped\", \"spinning\" and \"dragging\" are entered automatically when they exist. Each state can change the image, animation and sound, and has its own behaviours.",
            color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            logic.states.forEach { s -> DarkChip(s.name + if (s.name == logic.initialState) " ★" else "", false) { editingState = s } }
            listOf("idle", "tapped", "spinning", "dragging").filter { n -> logic.states.none { it.name == n } }.forEach { n ->
                DarkChip("+ $n", false) { save(logic.copy(states = logic.states + NodeState(n))) }
            }
            var naming by remember { mutableStateOf(false) }
            DarkChip("+ custom…", false) { naming = true }
            if (naming) com.customlauncher.ui.common.TextInputDialog("State name", onDismiss = { naming = false }) { name ->
                if (name.isNotBlank() && logic.states.none { it.name == name.trim() }) save(logic.copy(states = logic.states + NodeState(name.trim())))
                naming = false
            }
        }

        SectionLabel("Variables")
        VariablesEditor("This object", logic.variables) { save(logic.copy(variables = it)) }
        VariablesEditor("Global", profile.globalVariables) { g -> vm.edit { it.copy(globalVariables = g) } }
    }

    editingState?.let { s ->
        StateDialog(s, logic, ctx,
            onDismiss = { editingState = null },
            onDelete = { save(logic.copy(states = logic.states.filterNot { it.name == s.name })); editingState = null; if (scope == s.name) scope = null },
            onInitial = { save(logic.copy(initialState = s.name)) },
        ) { ns -> save(logic.copy(states = logic.states.map { if (it.name == s.name) ns else it })); editingState = ns }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BehaviorCard(b: Behavior, ctx: FormContext, onChange: (Behavior) -> Unit, onDelete: () -> Unit) {
    var pickTrigger by remember { mutableStateOf(false) }
    var addCondition by remember { mutableStateOf(false) }
    var addAction by remember { mutableStateOf(false) }
    var editAction by remember { mutableStateOf<Int?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(18.dp))
            .border(1.dp, if (b.enabled) Accent.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("When", color = Accent, style = MaterialTheme.typography.labelLarge)
            Pill(BehaviorLabels.trigger(b.trigger), Modifier.padding(start = 8.dp).weight(1f, fill = false)) { pickTrigger = true }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Switch(checked = b.enabled, onCheckedChange = { onChange(b.copy(enabled = it)) })
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete behaviour", tint = Color.White) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("if", color = Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            b.conditions.forEachIndexed { i, c ->
                Pill(BehaviorLabels.condition(c), removable = true) { onChange(b.copy(conditions = b.conditions.filterIndexed { j, _ -> j != i })) }
            }
            Pill("+") { addCondition = true }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("do", color = Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            b.actions.forEachIndexed { i, a -> Pill("${i + 1}. " + BehaviorLabels.action(a, ctx.profile)) { editAction = i } }
            Pill("+ action") { addAction = true }
        }
    }
    if (pickTrigger) TriggerDialog(b.trigger, ctx, onDismiss = { pickTrigger = false }) { onChange(b.copy(trigger = it)); pickTrigger = false }
    if (addCondition) ConditionDialog(ctx, onDismiss = { addCondition = false }) { onChange(b.copy(conditions = b.conditions + it)); addCondition = false }
    if (addAction) ActionDialog(null, ctx, onDismiss = { addAction = false }, onDelete = null) { onChange(b.copy(actions = b.actions + it)); addAction = false }
    editAction?.let { i ->
        ActionDialog(b.actions[i], ctx, onDismiss = { editAction = null },
            onDelete = { onChange(b.copy(actions = b.actions.filterIndexed { j, _ -> j != i })); editAction = null },
            onMove = { d ->
                val j = (i + d).coerceIn(0, b.actions.lastIndex)
                onChange(b.copy(actions = b.actions.toMutableList().also { l -> l.add(j, l.removeAt(i)) })); editAction = j
            },
        ) { a -> onChange(b.copy(actions = b.actions.mapIndexed { j, old -> if (j == i) a else old })); editAction = null }
    }
}

// ---- Triggers --------------------------------------------------------------------

private fun triggerKinds(): List<Pair<String, BTrigger>> = listOf(
    "Tap" to BTrigger.Tap, "Double tap" to BTrigger.DoubleTap, "Long press" to BTrigger.LongPress,
    "Drag starts" to BTrigger.DragStart, "Drag ends" to BTrigger.DragEnd, "Thrown" to BTrigger.Thrown(),
    "Spin crosses a speed" to BTrigger.SpinCrosses(), "Collides" to BTrigger.Collides(),
    "Enters a zone" to BTrigger.ZoneEnter(Zone(0f, 0f, 180f, 300f)), "Leaves a zone" to BTrigger.ZoneLeave(Zone(0f, 0f, 180f, 300f)),
    "Page shown" to BTrigger.PageShown, "Screen unlock" to BTrigger.Unlock, "Time of day" to BTrigger.TimeOfDay(),
    "Battery level" to BTrigger.BatteryBelow(), "Notification received" to BTrigger.NotificationFrom(),
    "Music starts" to BTrigger.MusicStarted, "Music pauses" to BTrigger.MusicPaused, "Phone shaken" to BTrigger.Shaken,
    "Phone tilted" to BTrigger.Tilted(), "Every N seconds" to BTrigger.Every(), "Random chance" to BTrigger.RandomChance(),
)

@Composable
private fun TriggerDialog(current: BTrigger, ctx: FormContext, onDismiss: () -> Unit, onPick: (BTrigger) -> Unit) {
    var draft by remember { mutableStateOf(current) }
    var choosing by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (choosing) "When…" else BehaviorLabels.trigger(draft)) },
        text = {
            if (choosing) {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(triggerKinds()) { (label, t) ->
                        ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable {
                            draft = if (current::class == t::class) current else t
                            if (hasTriggerParams(t)) choosing = false else onPick(draft)
                        })
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) { TriggerForm(draft, ctx) { draft = it } }
            }
        },
        confirmButton = { if (!choosing) TextButton(onClick = { onPick(draft) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { if (choosing) onDismiss() else choosing = true }) { Text(if (choosing) "Cancel" else "Back") } },
    )
}

private fun hasTriggerParams(t: BTrigger) = when (t) {
    is BTrigger.Thrown, is BTrigger.SpinCrosses, is BTrigger.Collides, is BTrigger.ZoneEnter, is BTrigger.ZoneLeave,
    is BTrigger.TimeOfDay, is BTrigger.BatteryBelow, is BTrigger.NotificationFrom, is BTrigger.Tilted, is BTrigger.Every,
    is BTrigger.RandomChance -> true
    else -> false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerForm(t: BTrigger, ctx: FormContext, onChange: (BTrigger) -> Unit) {
    when (t) {
        is BTrigger.Thrown -> LabeledSlider("Minimum speed", t.minSpeed, 200f..5000f, { onChange(t.copy(minSpeed = it)) }, format = { "${it.toInt()} dp/s" })
        is BTrigger.SpinCrosses -> {
            LabeledSlider("Speed", t.rpm, 10f..3000f, { onChange(t.copy(rpm = it)) }, format = { "${it.toInt()} rpm" })
            com.customlauncher.ui.common.SwitchRow("Going up (off = slowing down below it)", t.upward, { onChange(t.copy(upward = it)) })
        }
        is BTrigger.Collides -> {
            LabeledSlider("Minimum hit strength", t.minImpulse, 0f..20f, { onChange(t.copy(minImpulse = it)) }, format = { "%.1f".format(it) })
            NodeChooser("With", t.withNodeId, ctx, allowAny = true) { onChange(t.copy(withNodeId = it)) }
        }
        is BTrigger.ZoneEnter -> ZoneForm(t.zone) { onChange(t.copy(zone = it)) }
        is BTrigger.ZoneLeave -> ZoneForm(t.zone) { onChange(t.copy(zone = it)) }
        is BTrigger.TimeOfDay -> {
            LabeledSlider("Hour", (t.minute / 60).toFloat(), 0f..23f, { onChange(t.copy(minute = it.toInt() * 60 + t.minute % 60)) }, steps = 22)
            LabeledSlider("Minute", (t.minute % 60).toFloat(), 0f..59f, { onChange(t.copy(minute = t.minute / 60 * 60 + it.toInt())) })
        }
        is BTrigger.BatteryBelow -> LabeledSlider("Below", t.percent.toFloat(), 1f..99f, { onChange(t.copy(percent = it.toInt())) }, format = { "${it.toInt()}%" })
        is BTrigger.NotificationFrom -> {
            var picking by remember { mutableStateOf(false) }
            Text(if (t.packageName.isBlank()) "Any app" else t.packageName)
            Row {
                TextButton(onClick = { picking = true }) { Text("Choose app") }
                TextButton(onClick = { onChange(t.copy(packageName = "")) }) { Text("Any app") }
            }
            Text("Needs notification access (Settings → Permissions).", style = MaterialTheme.typography.bodySmall)
            if (picking) AppPickerDialog("Notifications from", multi = false, onDismiss = { picking = false }) {
                onChange(t.copy(packageName = it.first().substringBefore('/'))); picking = false
            }
        }
        is BTrigger.Tilted -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TiltDirection.entries.forEach { d -> FilterChip(t.direction == d, { onChange(t.copy(direction = d)) }, { Text(d.name.lowercase()) }) }
        }
        is BTrigger.Every -> LabeledSlider("Every", t.seconds, 0.5f..600f, { onChange(t.copy(seconds = it)) }, format = { "%.1f s".format(it) })
        is BTrigger.RandomChance -> LabeledSlider("Chance per second", t.percentPerSecond, 0.5f..100f, { onChange(t.copy(percentPerSecond = it)) }, format = { "%.1f%%".format(it) })
        else -> Text("No settings.")
    }
}

@Composable
private fun ZoneForm(z: Zone, onChange: (Zone) -> Unit) {
    Text("Zone on the page (dp). The object's centre is tested.", style = MaterialTheme.typography.bodySmall)
    LabeledSlider("Left", z.left, 0f..1200f, { onChange(z.copy(left = it)) })
    LabeledSlider("Top", z.top, 0f..2400f, { onChange(z.copy(top = it)) })
    LabeledSlider("Right", z.right, 0f..1200f, { onChange(z.copy(right = it)) })
    LabeledSlider("Bottom", z.bottom, 0f..2400f, { onChange(z.copy(bottom = it)) })
}

// ---- Conditions ------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionDialog(ctx: FormContext, onDismiss: () -> Unit, onPick: (BCondition) -> Unit) {
    var kind by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("taps") }
    var scope by remember { mutableStateOf(VarScope.OBJECT) }
    var op by remember { mutableStateOf(CompareOp.GE) }
    var value by remember { mutableStateOf("10") }
    var state by remember { mutableStateOf(ctx.states.firstOrNull() ?: "idle") }
    var chance by remember { mutableStateOf(50f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Only if…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Variable", "State", "Chance").forEachIndexed { i, l -> FilterChip(kind == i, { kind = i }, { Text(l) }) }
                }
                when (kind) {
                    0 -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VarScope.entries.forEach { s -> FilterChip(scope == s, { scope = s }, { Text(if (s == VarScope.GLOBAL) "Global" else "This object") }) }
                        }
                        OutlinedTextField(name, { name = it }, label = { Text("Variable") }, singleLine = true)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompareOp.entries.forEach { o -> FilterChip(op == o, { op = o }, { Text(o.symbol) }) }
                        }
                        OutlinedTextField(value, { value = it }, label = { Text("Value") }, singleLine = true)
                    }
                    1 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (ctx.states.ifEmpty { listOf("idle") }).forEach { s -> FilterChip(state == s, { state = s }, { Text(s) }) }
                    }
                    else -> LabeledSlider("Chance", chance, 1f..99f, { chance = it }, format = { "${it.toInt()}%" })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(
                    when (kind) {
                        0 -> BCondition.Variable(scope, name.trim(), op, value.toDoubleOrNull() ?: 0.0)
                        1 -> BCondition.StateIs(state)
                        else -> BCondition.Chance(chance)
                    }
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- Actions ---------------------------------------------------------------------

private fun actionKinds(ctx: FormContext): List<Pair<String, BAction>> = listOf(
    "Play reaction (jump, spin…)" to BAction.PlayEffect(TapEffect.JUMP),
    "Play keyframe clip" to BAction.PlayClip(ctx.self.clips.firstOrNull()?.name ?: "clip"),
    "Swap image" to BAction.SwapImage(""),
    "Go to state" to BAction.SetState(ctx.states.firstOrNull() ?: "idle"),
    "Play sprite sheet" to BAction.PlaySprite(""),
    "Push (impulse)" to BAction.Impulse(),
    "Spin (torque)" to BAction.Torque(),
    "Move to point" to BAction.MoveTo(ctx.self.x, ctx.self.y - 120f),
    "Fly to offset (relative)" to BAction.OffsetTo(0f, -120f),
    "Spawn an object" to BAction.Spawn("ball"),
    "Follow finger" to BAction.FollowFinger(true),
    "Follow a path" to BAction.FollowPath(emptyList()),
    "Orbit another object" to BAction.Orbit(ctx.others.firstOrNull()?.id ?: ""),
    "Emit particles" to BAction.EmitParticles(),
    "Play sound" to BAction.PlaySound(),
    "Haptic pattern" to BAction.Haptic(),
    "Show text bubble" to BAction.Say("Hi!"),
    "Change another object" to BAction.ChangeOther(ctx.others.firstOrNull()?.id ?: "", BAction.PlayEffect(TapEffect.JUMP)),
    "Set variable" to BAction.SetVariable(VarScope.OBJECT, "taps"),
    "Launcher action (app, macro…)" to BAction.Launcher(com.customlauncher.core.model.Action.None),
    "Physics on/off" to BAction.TogglePhysics(false),
    "Wait" to BAction.Wait(),
    "Random choice" to BAction.Randomize(emptyList()),
)

@Composable
internal fun ActionDialog(
    current: BAction?,
    ctx: FormContext,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onMove: ((Int) -> Unit)? = null,
    onPick: (BAction) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft?.let { BehaviorLabels.action(it, ctx.profile) } ?: "Do…") },
        text = {
            val d = draft
            if (d == null) {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(actionKinds(ctx)) { (label, a) -> ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { draft = a }) }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionForm(d, ctx) { draft = it }
                    if (onMove != null) Row {
                        TextButton(onClick = { onMove(-1) }) { Text("Move earlier") }
                        TextButton(onClick = { onMove(+1) }) { Text("Move later") }
                    }
                    if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove action") }
                }
            }
        },
        confirmButton = { draft?.let { a -> TextButton(onClick = { onPick(a) }) { Text("OK") } } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionForm(a: BAction, ctx: FormContext, onChange: (BAction) -> Unit) {
    when (a) {
        is BAction.PlayEffect -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TapEffect.entries.filter { it != TapEffect.NONE && it != TapEffect.PLAY_GIF_ONCE }.forEach { e ->
                FilterChip(a.effect == e, { onChange(a.copy(effect = e)) }, { Text(e.label) })
            }
        }
        is BAction.PlayClip -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ctx.self.clips.forEach { c -> FilterChip(a.clip == c.name, { onChange(a.copy(clip = c.name)) }, { Text(c.name) }) }
            }
            OutlinedTextField(a.clip, { onChange(a.copy(clip = it)) }, label = { Text("Clip name") }, singleLine = true)
            Text("Clips are made in the Animation tab → Keyframes.", style = MaterialTheme.typography.bodySmall)
        }
        is BAction.SwapImage -> StickerChooser(a.file) { onChange(a.copy(file = it)) }
        is BAction.SetState -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ctx.states.forEach { s -> FilterChip(a.state == s, { onChange(a.copy(state = s)) }, { Text(s) }) }
            }
            OutlinedTextField(a.state, { onChange(a.copy(state = it)) }, label = { Text("State") }, singleLine = true)
        }
        is BAction.PlaySprite -> {
            StickerChooser(a.file) { onChange(a.copy(file = it)) }
            LabeledSlider("Columns", a.cols.toFloat(), 1f..16f, { onChange(a.copy(cols = it.toInt())) }, steps = 14)
            LabeledSlider("Rows", a.rows.toFloat(), 1f..16f, { onChange(a.copy(rows = it.toInt())) }, steps = 14)
            LabeledSlider("Frames per second", a.fps, 1f..60f, { onChange(a.copy(fps = it)) })
            LabeledSlider("Loops", a.loops.toFloat(), 1f..20f, { onChange(a.copy(loops = it.toInt())) })
        }
        is BAction.Impulse -> {
            LabeledSlider("Horizontal", a.dx, -3000f..3000f, { onChange(a.copy(dx = it)) }, format = { "${it.toInt()} dp/s" })
            LabeledSlider("Vertical (negative = up)", a.dy, -3000f..3000f, { onChange(a.copy(dy = it)) }, format = { "${it.toInt()} dp/s" })
        }
        is BAction.Torque -> LabeledSlider("Spin", a.rpm, -3000f..3000f, { onChange(a.copy(rpm = it)) }, format = { "${it.toInt()} rpm" })
        is BAction.MoveTo -> {
            LabeledSlider("X", a.x, -200f..1200f, { onChange(a.copy(x = it)) })
            LabeledSlider("Y", a.y, -200f..2400f, { onChange(a.copy(y = it)) })
            LabeledSlider("Duration", a.durationMs.toFloat(), 0f..5000f, { onChange(a.copy(durationMs = it.toInt())) }, format = { "${it.toInt()} ms" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Easing.entries.forEach { e -> FilterChip(a.easing == e, { onChange(a.copy(easing = e)) }, { Text(e.name.lowercase()) }) }
            }
        }
        is BAction.OffsetTo -> {
            LabeledSlider("Offset X", a.dx, -1200f..1200f, { onChange(a.copy(dx = it)) }, format = { "${it.toInt()} dp" })
            LabeledSlider("Offset Y", a.dy, -2000f..2000f, { onChange(a.copy(dy = it)) }, format = { "${it.toInt()} dp" })
            LabeledSlider("Duration (0 = jump)", a.durationMs.toFloat(), 0f..5000f, { onChange(a.copy(durationMs = it.toInt())) }, format = { "${it.toInt()} ms" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Easing.entries.forEach { e -> FilterChip(a.easing == e, { onChange(a.copy(easing = e)) }, { Text(e.name.lowercase()) }) }
            }
        }
        is BAction.Spawn -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.customlauncher.objects.Presets.all.filter { it.art != null }.forEach { p ->
                FilterChip(a.preset == p.id, { onChange(a.copy(preset = p.id)) }, { Text(p.name) })
            }
        }
        is BAction.FollowFinger -> com.customlauncher.ui.common.SwitchRow("Follow the finger while dragging", a.enabled, { onChange(a.copy(enabled = it)) })
        is BAction.FollowPath -> {
            DriftPathRecorder(a.path) { onChange(a.copy(path = it)) }
            LabeledSlider("Duration", a.durationMs.toFloat(), 200f..20_000f, { onChange(a.copy(durationMs = it.toInt())) }, format = { "${it.toInt()} ms" })
            LabeledSlider("Loops", a.loops.toFloat(), 1f..20f, { onChange(a.copy(loops = it.toInt())) })
        }
        is BAction.Orbit -> {
            NodeChooser("Around", a.targetId, ctx, allowAny = false) { onChange(a.copy(targetId = it ?: "")) }
            LabeledSlider("Radius", a.radius, 10f..600f, { onChange(a.copy(radius = it)) }, format = { "${it.toInt()} dp" })
            LabeledSlider("Speed", a.rpm, -120f..120f, { onChange(a.copy(rpm = it)) }, format = { "${it.toInt()} rpm" })
            LabeledSlider("Duration", a.durationMs.toFloat(), 500f..60_000f, { onChange(a.copy(durationMs = it.toInt())) }, format = { "${it.toInt() / 1000} s" })
        }
        is BAction.EmitParticles -> {
            LabeledSlider("Count", a.count.toFloat(), 5f..200f, { onChange(a.copy(count = it.toInt())) })
            LabeledSlider("Spread", a.spread, 0.2f..3f, { onChange(a.copy(spread = it)) }, format = { "%.1f×".format(it) })
            ColorRow("Colour", a.color) { onChange(a.copy(color = it)) }
        }
        is BAction.PlaySound -> SoundChooser(a.source) { onChange(a.copy(source = it)) }
        is BAction.Haptic -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HapticPattern.entries.forEach { h -> FilterChip(a.pattern == h, { onChange(a.copy(pattern = h)) }, { Text(h.name.lowercase()) }) }
        }
        is BAction.Say -> {
            OutlinedTextField(a.text, { onChange(a.copy(text = it)) }, label = { Text("Text") })
            LabeledSlider("Show for", a.durationMs.toFloat(), 500f..10_000f, { onChange(a.copy(durationMs = it.toInt())) }, format = { "%.1f s".format(it / 1000) })
        }
        is BAction.ChangeOther -> {
            NodeChooser("Object", a.targetId, ctx, allowAny = false) { onChange(a.copy(targetId = it ?: "")) }
            var editing by remember { mutableStateOf(false) }
            Text("Action: " + BehaviorLabels.action(a.action, ctx.profile))
            TextButton(onClick = { editing = true }) { Text("Choose action") }
            if (editing) ActionDialog(a.action, ctx, onDismiss = { editing = false }, onDelete = null) { onChange(a.copy(action = it)); editing = false }
        }
        is BAction.SetVariable -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VarScope.entries.forEach { s -> FilterChip(a.scope == s, { onChange(a.copy(scope = s)) }, { Text(if (s == VarScope.GLOBAL) "Global" else "This object") }) }
                VarOp.entries.forEach { o -> FilterChip(a.op == o, { onChange(a.copy(op = o)) }, { Text(if (o == VarOp.ADD) "add" else "set to") }) }
            }
            OutlinedTextField(a.name, { onChange(a.copy(name = it)) }, label = { Text("Variable") }, singleLine = true)
            var text by remember { mutableStateOf(a.value.toString()) }
            OutlinedTextField(text, { text = it; it.toDoubleOrNull()?.let { v -> onChange(a.copy(value = v)) } }, label = { Text("Value") }, singleLine = true)
        }
        is BAction.Launcher -> {
            var picking by remember { mutableStateOf(false) }
            Text(com.customlauncher.actions.ActionCatalog.label(a.action, ctx.profile))
            TextButton(onClick = { picking = true }) { Text("Choose launcher action") }
            if (picking && ctx.profile != null) ActionPickerDialog(ctx.profile, a.action, onDismiss = { picking = false }) { onChange(a.copy(action = it)); picking = false }
        }
        is BAction.TogglePhysics -> com.customlauncher.ui.common.SwitchRow("Physics enabled", a.enabled, { onChange(a.copy(enabled = it)) })
        is BAction.Wait -> LabeledSlider("Wait", a.ms.toFloat(), 50f..10_000f, { onChange(a.copy(ms = it.toInt())) }, format = { "${it.toInt()} ms" })
        is BAction.Randomize -> {
            var adding by remember { mutableStateOf(false) }
            a.actions.forEachIndexed { i, sub ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(BehaviorLabels.action(sub, ctx.profile), Modifier.weight(1f))
                    IconButton(onClick = { onChange(a.copy(actions = a.actions.filterIndexed { j, _ -> j != i })) }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
            TextButton(onClick = { adding = true }) { Text("Add option") }
            if (adding) ActionDialog(null, ctx, onDismiss = { adding = false }, onDelete = null) { onChange(a.copy(actions = a.actions + it)); adding = false }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeChooser(label: String, selected: String?, ctx: FormContext, allowAny: Boolean, onPick: (String?) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allowAny) FilterChip(selected == null, { onPick(null) }, { Text("Anything") })
        ctx.others.forEach { n -> FilterChip(selected == n.id, { onPick(n.id) }, { Text(ctx.nodeName(n.id)) }) }
    }
}

/** Picks an image from the sticker library. */
@Composable
internal fun StickerChooser(selected: String, onPick: (String) -> Unit) {
    val repo = LocalContext.current.container.stickerRepository
    val library by repo.library.collectAsState()
    if (library.isEmpty()) {
        Text("Import stickers first (Edit Mode → Widget → Sticker).", style = MaterialTheme.typography.bodySmall)
        return
    }
    LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.heightIn(max = 200.dp)) {
        items(library, key = { it.id }) { asset ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(3.dp)
                    .size(56.dp)
                    .border(if (asset.file == selected) 2.dp else 0.dp, Accent, RoundedCornerShape(10.dp))
                    .clickable { onPick(asset.file) }
                    .padding(4.dp)
            ) {
                StickerWidget.StickerImage(repo.fileOf(asset.file), asset.kind, DpSize(56.dp, 56.dp), playing = false, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundChooser(source: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "sounds").apply { mkdirs() }
                    val f = File(dir, "sound_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
                    f.name
                }.getOrNull()
            }
            if (name != null) onPick("file:$name")
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("PROP_ACK", "PROP_BEEP", "PROP_BEEP2", "PROP_PROMPT", "CDMA_PIP", "SUP_PIP").forEach { t ->
            FilterChip(source == "tone:$t", { onPick("tone:$t"); context.container.soundAndHaptics.play("tone:$t") }, { Text(t.lowercase()) })
        }
    }
    TextButton(onClick = { importer.launch(arrayOf("audio/*")) }) { Text(if (source.startsWith("file:")) "Imported sound ✓ (replace…)" else "Import sound file…") }
}

@Composable
private fun StateDialog(
    s: NodeState,
    logic: ObjectLogic,
    ctx: FormContext,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onInitial: () -> Unit,
    onChange: (NodeState) -> Unit,
) {
    var editAnim by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("State: ${s.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Image while in this state", style = MaterialTheme.typography.labelLarge)
                StickerChooser(s.image ?: "") { onChange(s.copy(image = it)) }
                if (s.image != null) TextButton(onClick = { onChange(s.copy(image = null)) }) { Text("Keep the normal image") }
                LabeledSlider("Transition", s.transitionMs.toFloat(), 0f..2000f, { onChange(s.copy(transitionMs = it.toInt())) }, format = { "${it.toInt()} ms" })
                Text("Sound on enter: " + (s.sound?.substringAfter(':') ?: "none"))
                SoundChooser(s.sound ?: "") { onChange(s.copy(sound = it)) }
                if (s.sound != null) TextButton(onClick = { onChange(s.copy(sound = null)) }) { Text("No sound") }
                TextButton(onClick = { editAnim = !editAnim }) { Text(if (editAnim) "Hide animation" else "Animation in this state…") }
                if (editAnim) {
                    androidx.compose.foundation.layout.Box(Modifier.background(Color(0xFF16181C), RoundedCornerShape(12.dp)).padding(8.dp)) {
                        AnimationEditor(s.animation ?: NodeAnimations(), ctx.profile ?: return@Box, onPreview = null) {
                            onChange(s.copy(animation = it.takeIf { a -> !a.isEmpty }))
                        }
                    }
                }
                if (logic.initialState != s.name) TextButton(onClick = onInitial) { Text("Start in this state") }
                TextButton(onClick = onDelete) { Text("Delete state") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun VariablesEditor(title: String, vars: Map<String, Double>, onChange: (Map<String, Double>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Text(title, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
    vars.forEach { (name, v) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$name = $v (start value)", color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(vars - name) }) { Icon(Icons.Filled.Close, "Remove", tint = Color.White) }
        }
    }
    AddButton("Add variable") { adding = true }
    if (adding) com.customlauncher.ui.common.TextInputDialog("name = start value", initial = "counter = 0", onDismiss = { adding = false }) { text ->
        val name = text.substringBefore('=').trim()
        val value = text.substringAfter('=', "0").trim().toDoubleOrNull() ?: 0.0
        if (name.isNotEmpty()) onChange(vars + (name to value))
        adding = false
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Accent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun DarkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(if (selected) Accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, removable: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        if (removable) Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(14.dp))
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
        Text(" $label", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
