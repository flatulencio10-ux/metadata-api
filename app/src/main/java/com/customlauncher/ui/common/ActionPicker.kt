package com.customlauncher.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.customlauncher.actions.ActionCatalog
import com.customlauncher.container
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.data.apps.AppShortcut
import com.customlauncher.widget.WidgetRegistry

private enum class PickerStep { ROOT, APP, SHORTCUT_APP, SHORTCUT, ACTIVITY, WIDGET, URL, MACRO }

/**
 * Lets the user choose any [Action], including parameterised ones (app, shortcut, activity,
 * widget, URL) and macros. Used for gestures, hot corners, panel buttons and volume keys.
 */
@Composable
fun ActionPickerDialog(
    profile: LayoutProfile,
    current: Action = Action.None,
    onDismiss: () -> Unit,
    onPick: (Action) -> Unit,
) {
    var step by remember { mutableStateOf(PickerStep.ROOT) }
    var shortcutPackage by remember { mutableStateOf<String?>(null) }
    val catalog = LocalAppCatalog.current

    when (step) {
        PickerStep.ROOT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Choose action") },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    item {
                        Text("Current: ${ActionCatalog.label(current, profile, catalog::label)}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    val parameterised = listOf(
                        "Open app…" to PickerStep.APP,
                        "Open app shortcut…" to PickerStep.SHORTCUT_APP,
                        "Switch activity…" to PickerStep.ACTIVITY,
                        "Open widget full screen…" to PickerStep.WIDGET,
                        "Open URL…" to PickerStep.URL,
                        "Macro (sequence of actions)…" to PickerStep.MACRO,
                    )
                    items(parameterised) { (label, target) ->
                        ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable { step = target })
                    }
                    item { HorizontalDivider() }
                    items(ActionCatalog.simpleActions) { action ->
                        ListItem(
                            headlineContent = { Text(ActionCatalog.label(action)) },
                            leadingContent = { Icon(ActionCatalog.icon(action), null) },
                            modifier = Modifier.clickable { onPick(action) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )

        PickerStep.APP -> AppPickerDialog("Open app", multi = false, onDismiss = { step = PickerStep.ROOT }) {
            onPick(Action.OpenApp(it.first()))
        }

        PickerStep.SHORTCUT_APP -> AppPickerDialog("Shortcut from app", multi = false, onDismiss = { step = PickerStep.ROOT }) {
            shortcutPackage = it.first().substringBefore('/')
            step = PickerStep.SHORTCUT
        }

        PickerStep.SHORTCUT -> {
            val repo = LocalContext.current.container.shortcutRepository
            val shortcuts by produceState<List<AppShortcut>?>(null, shortcutPackage) {
                value = repo.shortcutsFor(shortcutPackage)
            }
            AlertDialog(
                onDismissRequest = { step = PickerStep.ROOT },
                title = { Text("Choose shortcut") },
                text = {
                    val list = shortcuts
                    when {
                        list == null -> Text("Loading…")
                        list.isEmpty() -> Text(
                            if (repo.isAvailable) "This app publishes no shortcuts."
                            else "Shortcuts are only available while this is the default launcher."
                        )
                        else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(list) { s ->
                                ListItem(
                                    headlineContent = { Text(s.label) },
                                    modifier = Modifier.clickable { onPick(Action.OpenShortcut(s.packageName, s.id, s.label)) },
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { step = PickerStep.ROOT }) { Text("Back") } },
            )
        }

        PickerStep.ACTIVITY -> AlertDialog(
            onDismissRequest = { step = PickerStep.ROOT },
            title = { Text("Switch to activity") },
            text = {
                Column {
                    profile.activities.forEach { act ->
                        ListItem(headlineContent = { Text(act.name) }, modifier = Modifier.clickable { onPick(Action.SwitchActivity(act.id)) })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { step = PickerStep.ROOT }) { Text("Back") } },
        )

        PickerStep.WIDGET -> AlertDialog(
            onDismissRequest = { step = PickerStep.ROOT },
            title = { Text("Widget to open") },
            text = {
                val widgets = profile.activities.flatMap { act ->
                    act.pages.flatMapIndexed { i, page -> page.widgets.map { Triple(act.name, i, it) } }
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(widgets) { (actName, pageIndex, w) ->
                        val def = WidgetRegistry.find(w.type)
                        ListItem(
                            headlineContent = { Text(def?.name ?: w.type) },
                            supportingContent = { Text("$actName · page ${pageIndex + 1}") },
                            leadingContent = { def?.let { Icon(it.icon, null) } },
                            modifier = Modifier.clickable { onPick(Action.OpenWidgetFullscreen(w.id)) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { step = PickerStep.ROOT }) { Text("Back") } },
        )

        PickerStep.URL -> TextInputDialog(
            title = "Open URL",
            initial = (current as? Action.OpenUrl)?.url ?: "https://",
            onDismiss = { step = PickerStep.ROOT },
        ) { url -> if (url.isNotBlank()) onPick(Action.OpenUrl(url.trim())) }

        PickerStep.MACRO -> MacroEditorDialog(
            profile = profile,
            initial = current as? Action.Macro ?: Action.Macro(),
            onDismiss = { step = PickerStep.ROOT },
            onSave = onPick,
        )
    }
}

/** Builds a macro: an ordered list of actions run with a delay between steps. */
@Composable
fun MacroEditorDialog(profile: LayoutProfile, initial: Action.Macro, onDismiss: () -> Unit, onSave: (Action.Macro) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var steps by remember { mutableStateOf(initial.actions) }
    var delay by remember { mutableStateOf(initial.delayMs.toFloat()) }
    var adding by remember { mutableStateOf(false) }
    val catalog = LocalAppCatalog.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macro") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LabeledSlider("Delay between steps", delay, 0f..3000f, { delay = it }, format = { "${it.toInt()} ms" })
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    itemsIndexed(steps) { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${ActionCatalog.label(a, profile, catalog::label)}", Modifier.weight(1f))
                            IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(i) } }) {
                                Icon(Icons.Filled.Delete, "Remove step")
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { adding = true }) {
                            Icon(Icons.Filled.Add, null); Text("Add step")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Action.Macro(name.ifBlank { "Macro" }, steps, delay.toLong())) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (adding) {
        ActionPickerDialog(profile, onDismiss = { adding = false }) { a ->
            steps = steps + a
            adding = false
        }
    }
}
