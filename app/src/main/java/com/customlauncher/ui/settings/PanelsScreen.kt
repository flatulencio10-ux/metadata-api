package com.customlauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.customlauncher.actions.ActionCatalog
import com.customlauncher.core.model.Edge
import com.customlauncher.core.model.Panel
import com.customlauncher.core.model.PanelItem
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.ui.common.ActionPickerDialog
import com.customlauncher.ui.common.AppPickerDialog
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.common.SwitchRow
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.WidgetRegistry

/** Panels per activity: any edge, any number, holding apps / widgets / action buttons / spacers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelsScreen(s: SettingsScope) {
    var activityId by remember { mutableStateOf(s.profile.currentActivityId) }
    val activity = s.profile.activities.firstOrNull { it.id == activityId } ?: s.profile.currentActivity
    var editingId by remember { mutableStateOf<String?>(null) }

    fun editPanels(transform: (List<Panel>) -> List<Panel>) =
        s.edit { p -> p.updateActivity(activity.id) { it.copy(panels = transform(it.panels)) } }

    SettingsPage {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.profile.activities.forEach { a -> FilterChip(a.id == activity.id, { activityId = a.id }, { Text(a.name) }) }
            }
        }
        item { SectionTitle("Panels in ${activity.name}") }
        if (activity.panels.isEmpty()) item { Text("No panels yet.") }
        items(activity.panels, key = { it.id }) { panel ->
            ListItem(
                headlineContent = { Text("${panel.edge.name.lowercase().replaceFirstChar { it.uppercase() }} panel") },
                supportingContent = { Text("${panel.items.size} items · ${panel.thicknessDp.toInt()} dp" + if (panel.autoHide) " · auto-hide" else "") },
                trailingContent = {
                    IconButton(onClick = { editPanels { list -> list.filterNot { it.id == panel.id } } }) { Icon(Icons.Filled.Delete, "Delete") }
                },
                leadingContent = { Icon(Icons.Filled.Edit, null) },
                modifier = Modifier.clickable { editingId = panel.id },
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Edge.entries.forEach { edge ->
                    OutlinedButton(onClick = {
                        val panel = Panel(edge = edge, thicknessDp = if (edge.isHorizontal) 64f else 72f, items = listOf(PanelItem.Spacer(flexible = true)))
                        editPanels { it + panel }
                        editingId = panel.id
                    }) { Text("Add ${edge.name.lowercase()}") }
                }
                // One tap to a desktop: start button, taskbar and tray along the bottom.
                OutlinedButton(onClick = {
                    val panel = Panel(
                        edge = Edge.BOTTOM, thicknessDp = 56f, floating = false, marginDp = 0f, cornerRadiusDp = 0f, backgroundColor = 0xD9101218,
                        items = listOf(PanelItem.StartButton(), PanelItem.Spacer(sizeDp = 6f), PanelItem.Taskbar(), PanelItem.SystemTray()),
                    )
                    editPanels { it + panel }
                }) { Text("Add desktop taskbar") }
            }
        }
    }

    editingId?.let { id ->
        val panel = activity.panels.firstOrNull { it.id == id }
        if (panel != null) {
            PanelEditor(panel, s, onDismiss = { editingId = null }) { updated ->
                editPanels { list -> list.map { if (it.id == id) updated else it } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelEditor(panel: Panel, s: SettingsScope, onDismiss: () -> Unit, onChange: (Panel) -> Unit) {
    var adding by remember { mutableStateOf<String?>(null) }
    var configuringWidget by remember { mutableStateOf<PanelItem.Widget?>(null) }
    val catalog = LocalAppCatalog.current

    fun moveItem(index: Int, delta: Int) {
        val target = index + delta
        if (target !in panel.items.indices) return
        onChange(panel.copy(items = panel.items.toMutableList().also { val x = it.removeAt(index); it.add(target, x) }))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Panel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Edge.entries.forEach { e -> FilterChip(panel.edge == e, { onChange(panel.copy(edge = e)) }, { Text(e.name.lowercase()) }) }
                }
                LabeledSlider("Thickness", panel.thicknessDp, 32f..160f, { onChange(panel.copy(thicknessDp = it)) }, format = { "${it.toInt()} dp" })
                SwitchRow("Floating", panel.floating, { onChange(panel.copy(floating = it)) }, supporting = "Inset and rounded instead of edge-attached")
                if (panel.floating) {
                    LabeledSlider("Margin", panel.marginDp, 0f..32f, { onChange(panel.copy(marginDp = it)) }, format = { "${it.toInt()} dp" })
                    LabeledSlider("Corner radius", panel.cornerRadiusDp, 0f..48f, { onChange(panel.copy(cornerRadiusDp = it)) }, format = { "${it.toInt()} dp" })
                }
                SwitchRow("Auto-hide", panel.autoHide, { onChange(panel.copy(autoHide = it)) }, supporting = "Tap the screen edge to reveal")
                SwitchRow("Reserve space", panel.reserveSpace, { onChange(panel.copy(reserveSpace = it)) }, supporting = "Keep widgets from going under the panel")
                LabeledSlider("Opacity", panel.opacity, 0.1f..1f, { onChange(panel.copy(opacity = it)) }, format = { "%.0f%%".format(it * 100) })
                ColorRow("Background", panel.backgroundColor) { onChange(panel.copy(backgroundColor = it)) }

                SectionTitle("Items")
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(panel.items.indices.toList()) { i ->
                        val item = panel.items[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (item) {
                                    is PanelItem.App -> catalog.label(item.component)
                                    is PanelItem.Widget -> "Widget: " + (WidgetRegistry.find(item.widget.type)?.name ?: item.widget.type)
                                    is PanelItem.ActionButton -> "Button: " + ActionCatalog.label(item.action, s.profile, catalog::label)
                                    is PanelItem.Spacer -> if (item.flexible) "Flexible spacer" else "Spacer ${item.sizeDp.toInt()} dp"
                                    is PanelItem.Taskbar -> "Taskbar (${item.pinned.size} pinned${if (item.showRecents) " + recent" else ""})"
                                    is PanelItem.SystemTray -> "System tray"
                                    is PanelItem.StartButton -> "Start menu button"
                                },
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (item is PanelItem.Widget) IconButton(onClick = { configuringWidget = item }) { Icon(Icons.Filled.Tune, "Configure") }
                            IconButton(onClick = { moveItem(i, -1) }) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                            IconButton(onClick = { moveItem(i, +1) }) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                            IconButton(onClick = { onChange(panel.copy(items = panel.items.filterIndexed { j, _ -> j != i })) }) {
                                Icon(Icons.Filled.Delete, "Remove")
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { adding = "app" }) { Text("+ App") }
                    OutlinedButton(onClick = { adding = "widget" }) { Text("+ Widget") }
                    OutlinedButton(onClick = { adding = "action" }) { Text("+ Button") }
                    OutlinedButton(onClick = { onChange(panel.copy(items = panel.items + PanelItem.Spacer(sizeDp = 16f))) }) { Text("+ Gap") }
                    OutlinedButton(onClick = { onChange(panel.copy(items = panel.items + PanelItem.Spacer(flexible = true))) }) { Text("+ Flexible") }
                    OutlinedButton(onClick = { onChange(panel.copy(items = panel.items + PanelItem.StartButton())) }) { Text("+ Start") }
                    OutlinedButton(onClick = { onChange(panel.copy(items = panel.items + PanelItem.Taskbar())) }) { Text("+ Taskbar") }
                    OutlinedButton(onClick = { onChange(panel.copy(items = panel.items + PanelItem.SystemTray())) }) { Text("+ Tray") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    when (adding) {
        "app" -> AppPickerDialog("Add apps", multi = true, onDismiss = { adding = null }) { keys ->
            onChange(panel.copy(items = panel.items + keys.map { PanelItem.App(component = it) }))
            adding = null
        }
        "action" -> ActionPickerDialog(s.profile, onDismiss = { adding = null }) { action ->
            onChange(panel.copy(items = panel.items + PanelItem.ActionButton(label = ActionCatalog.label(action), action = action)))
            adding = null
        }
        "widget" -> AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Add widget") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(WidgetRegistry.all, key = { it.id }) { w ->
                        ListItem(
                            headlineContent = { Text(w.name) },
                            leadingContent = { Icon(w.icon, null) },
                            modifier = Modifier.clickable {
                                val instance = WidgetInstance(type = w.id, width = w.defaultSize.width.value, height = panel.thicknessDp)
                                val length = if (panel.edge.isHorizontal) w.defaultSize.width.value.coerceAtMost(200f) else w.defaultSize.height.value.coerceAtMost(200f)
                                onChange(panel.copy(items = panel.items + PanelItem.Widget(widget = instance, lengthDp = length)))
                                adding = null
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }

    configuringWidget?.let { item ->
        val def = WidgetRegistry.find(item.widget.type)
        var draft by remember(item.id) { mutableStateOf(WidgetConfig(item.widget.config)) }
        var length by remember(item.id) { mutableStateOf(item.lengthDp) }
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { configuringWidget = null },
            title = { Text(def?.name ?: "Widget") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LabeledSlider("Length along panel", length, 40f..400f, { length = it }, format = { "${it.toInt()} dp" })
                    def?.ConfigScreen(draft) { draft = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(panel.copy(items = panel.items.map {
                        if (it.id == item.id) item.copy(lengthDp = length, widget = item.widget.copy(config = draft.json)) else it
                    }))
                    def?.onAdded(context, draft)
                    configuringWidget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { configuringWidget = null }) { Text("Cancel") } },
        )
    }
}
