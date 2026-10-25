package com.customlauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.customlauncher.actions.ActionCatalog
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.GestureConfig
import com.customlauncher.core.model.Trigger
import com.customlauncher.core.model.TriggerGroup
import com.customlauncher.gestures.LetterRecognizer
import com.customlauncher.ui.common.ActionPickerDialog
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.common.LocalAppCatalog

/** Every trigger (gesture, hot corner, edge swipe, volume key, drawn letter) → any action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GesturesScreen(s: SettingsScope) {
    val g = s.profile.gestures
    val catalog = LocalAppCatalog.current
    fun set(transform: (GestureConfig) -> GestureConfig) = s.edit { it.copy(gestures = transform(it.gestures)) }

    var pickingTrigger by remember { mutableStateOf<Trigger?>(null) }
    var pickingLetter by remember { mutableStateOf<String?>(null) }
    var choosingLetter by remember { mutableStateOf(false) }

    SettingsPage {
        TriggerGroup.entries.forEach { group ->
            item { SectionTitle(group.label) }
            if (group == TriggerGroup.KEY) {
                item {
                    Text("Volume-key actions need the accessibility service. A short press still changes the volume.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (group == TriggerGroup.CORNER || group == TriggerGroup.EDGE) {
                item {
                    Text("Unbound corners and edges stay fully touchable.", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(Trigger.entries.filter { it.group == group }) { trigger ->
                val action = g.actionFor(trigger)
                ListItem(
                    headlineContent = { Text(trigger.label) },
                    supportingContent = { Text(ActionCatalog.label(action, s.profile, catalog::label)) },
                    leadingContent = { Icon(ActionCatalog.icon(action), null) },
                    modifier = Modifier.clickable { pickingTrigger = trigger },
                )
            }
        }
        item {
            LabeledSlider("Hot corner size", g.cornerSizeDp, 16f..96f, { v -> set { it.copy(cornerSizeDp = v) } }, format = { "${it.toInt()} dp" })
            LabeledSlider("Edge swipe zone", g.edgeSizeDp, 8f..48f, { v -> set { it.copy(edgeSizeDp = v) } }, format = { "${it.toInt()} dp" })
        }

        item { SectionTitle("Draw a letter") }
        item {
            Text("Bind \"Draw a letter\" to any gesture, then draw one of: ${LetterRecognizer.letters.sorted().joinToString(" ")}",
                style = MaterialTheme.typography.bodySmall)
        }
        items(g.letters.keys.sorted()) { letter ->
            val action = g.letters.getValue(letter)
            ListItem(
                headlineContent = { Text("Letter $letter") },
                supportingContent = { Text(ActionCatalog.label(action, s.profile, catalog::label)) },
                trailingContent = {
                    IconButton(onClick = { set { it.copy(letters = it.letters - letter) } }) { Icon(Icons.Filled.Delete, "Remove") }
                },
                modifier = Modifier.clickable { pickingLetter = letter },
            )
        }
        item { OutlinedButton(onClick = { choosingLetter = true }) { Text("Add letter") } }
        item {
            TextButton(onClick = { set { GestureConfig() } }) { Text("Reset all gestures to defaults") }
        }
    }

    pickingTrigger?.let { trigger ->
        ActionPickerDialog(s.profile, g.actionFor(trigger), onDismiss = { pickingTrigger = null }) { action ->
            set { it.copy(bindings = if (action == Action.None) it.bindings - trigger else it.bindings + (trigger to action)) }
            pickingTrigger = null
        }
    }
    pickingLetter?.let { letter ->
        ActionPickerDialog(s.profile, g.letters[letter] ?: Action.None, onDismiss = { pickingLetter = null }) { action ->
            set { it.copy(letters = it.letters + (letter to action)) }
            pickingLetter = null
        }
    }
    if (choosingLetter) {
        AlertDialog(
            onDismissRequest = { choosingLetter = false },
            title = { Text("Choose letter") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LetterRecognizer.letters.sorted().forEach { l ->
                        FilterChip(l in g.letters, { choosingLetter = false; pickingLetter = l }, { Text(l) })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingLetter = false }) { Text("Cancel") } },
        )
    }
}
