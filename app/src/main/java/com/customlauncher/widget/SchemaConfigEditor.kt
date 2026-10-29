package com.customlauncher.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.ui.common.AppPickerDialog
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.common.SwitchRow

/**
 * Auto-generated settings UI for a widget's [ConfigField] schema. Widgets get a working
 * config screen for free just by declaring their schema.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchemaConfigEditor(schema: List<ConfigField>, config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (schema.isEmpty()) Text("This widget has no settings.", style = MaterialTheme.typography.bodyMedium)
        schema.forEach { field ->
            when (field) {
                is ConfigField.Text -> OutlinedTextField(
                    value = config.string(field),
                    onValueChange = { onChange(config.with(field.key, it)) },
                    label = { Text(field.label) },
                    placeholder = if (field.hint.isNotEmpty()) ({ Text(field.hint) }) else null,
                    singleLine = !field.multiline,
                    modifier = Modifier.fillMaxWidth(),
                )

                is ConfigField.Toggle -> SwitchRow(field.label, config.bool(field), { onChange(config.with(field.key, it)) })

                is ConfigField.Choice -> Column {
                    Text(field.label, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        field.options.forEach { (value, label) ->
                            FilterChip(
                                selected = config.string(field) == value,
                                onClick = { onChange(config.with(field.key, value)) },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                is ConfigField.Slider -> LabeledSlider(
                    label = field.label,
                    value = config.float(field),
                    range = field.min..field.max,
                    steps = field.steps,
                    onChange = { onChange(config.with(field.key, it)) },
                    format = { if (field.max - field.min > 10) "%.0f".format(it) else "%.2f".format(it) },
                )

                is ConfigField.ColorPick -> ColorRow(field.label, config.long(field)) { onChange(config.with(field.key, it)) }

                is ConfigField.Apps -> AppsField(field, config, onChange)

                is ConfigField.Secret -> SecretField(field)

                is ConfigField.Info -> Text(field.label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AppsField(field: ConfigField.Apps, config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val catalog = LocalAppCatalog.current
    val selected = config.stringList(field)
    Column {
        Text(field.label, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (selected.isEmpty()) "No apps selected" else selected.joinToString { catalog.label(it) },
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { picking = true }) { Text("Choose apps") }
    }
    if (picking) {
        AppPickerDialog(field.label, multi = true, initiallySelected = selected, onDismiss = { picking = false }) {
            onChange(config.with(field.key, it))
            picking = false
        }
    }
}

/** Secret values go to EncryptedSharedPreferences, never into the layout JSON. */
@Composable
private fun SecretField(field: ConfigField.Secret) {
    val store = LocalContext.current.container.secureStore
    val version by store.version.collectAsState()
    var text by remember(version) { mutableStateOf(store.get(field.secureKey) ?: "") }
    var saved by remember(version) { mutableStateOf(true) }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false },
            label = { Text(field.label) },
            placeholder = if (field.hint.isNotEmpty()) ({ Text(field.hint) }) else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (saved) "Stored encrypted on this device" else "Not saved yet",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            TextButton(onClick = { store.put(field.secureKey, text) }) { Text("Save") }
        }
    }
}
