package com.customlauncher.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.customlauncher.core.model.ActivityProfile
import com.customlauncher.core.model.AutoSwitchRule
import com.customlauncher.core.model.Page
import com.customlauncher.core.model.newId
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.common.WallpaperEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** KDE-style Activities: independent home setups plus automatic switching rules. */
@Composable
fun ActivitiesScreen(s: SettingsScope) {
    var editing by remember { mutableStateOf<ActivityProfile?>(null) }
    var addingRule by remember { mutableStateOf(false) }
    val p = s.profile

    SettingsPage {
        item { SectionTitle("Activities") }
        items(p.activities, key = { it.id }) { act ->
            ListItem(
                headlineContent = { Text(act.name) },
                supportingContent = { Text("${act.pages.size} pages · ${act.panels.size} panels") },
                leadingContent = {
                    if (act.id == p.currentActivityId) Icon(Icons.Filled.CheckCircle, "Current", tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { IconButton(onClick = { editing = act }) { Icon(Icons.Filled.Edit, "Edit") } },
                modifier = Modifier.clickable { s.edit { it.copy(currentActivityId = act.id) } },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    s.edit { it.copy(activities = it.activities + ActivityProfile(name = "New activity", pages = listOf(Page()))) }
                }) { Text("New empty") }
                OutlinedButton(onClick = {
                    s.edit { prof ->
                        val cur = prof.currentActivity
                        prof.copy(activities = prof.activities + cur.copy(id = newId(), name = "${cur.name} copy"))
                    }
                }) { Text("Duplicate current") }
            }
        }

        item { SectionTitle("Automatic switching") }
        item {
            Text(
                "Rules are checked top to bottom; the first match wins. A manual switch stays until conditions change.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(p.autoSwitch.indices.toList()) { index ->
            val rule = p.autoSwitch[index]
            val target = p.activities.firstOrNull { it.id == rule.activityId }?.name ?: "(deleted activity)"
            ListItem(
                headlineContent = { Text(describe(rule)) },
                supportingContent = { Text("→ $target") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = rule.enabled, onCheckedChange = { on ->
                            s.edit { it.copy(autoSwitch = it.autoSwitch.mapIndexed { i, r -> if (i == index) r.withEnabled(on) else r }) }
                        })
                        IconButton(onClick = {
                            s.edit { it.copy(autoSwitch = it.autoSwitch.filterIndexed { i, _ -> i != index }) }
                        }) { Icon(Icons.Filled.Delete, "Delete rule") }
                    }
                },
            )
        }
        item { OutlinedButton(onClick = { addingRule = true }) { Text("Add rule") } }
    }

    editing?.let { act ->
        ActivityEditor(
            activity = p.activities.firstOrNull { it.id == act.id } ?: act,
            canDelete = p.activities.size > 1,
            onDismiss = { editing = null },
            onDelete = {
                s.edit { prof -> prof.copy(activities = prof.activities.filterNot { it.id == act.id }) }
                editing = null
            },
            onChange = { updated -> s.edit { it.updateActivity(act.id) { updated } } },
        )
    }
    if (addingRule) RuleEditor(s, onDismiss = { addingRule = false })
}

private fun AutoSwitchRule.withEnabled(on: Boolean): AutoSwitchRule = when (this) {
    is AutoSwitchRule.TimeOfDay -> copy(enabled = on)
    is AutoSwitchRule.Wifi -> copy(enabled = on)
    is AutoSwitchRule.Charging -> copy(enabled = on)
}

private fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun describe(rule: AutoSwitchRule): String = when (rule) {
    is AutoSwitchRule.TimeOfDay -> "Between ${hhmm(rule.startMinute)} and ${hhmm(rule.endMinute)}"
    is AutoSwitchRule.Wifi -> "Connected to WiFi \"${rule.ssid}\""
    is AutoSwitchRule.Charging -> if (rule.charging) "While charging" else "While on battery"
}

@Composable
private fun ActivityEditor(
    activity: ActivityProfile,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onChange: (ActivityProfile) -> Unit,
) {
    val context = LocalContext.current
    // Name is edited locally and saved on close, so typing isn't affected by async saves.
    var name by remember(activity.id) { mutableStateOf(activity.name) }
    val close = {
        if (name.isNotBlank() && name != activity.name) onChange(activity.copy(name = name.trim()))
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Edit activity") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                WallpaperEditor(
                    current = activity.wallpaper,
                    allowInherit = false,
                    copyImage = { uri -> copyImage(context, uri) },
                ) { w -> if (w != null) onChange(activity.copy(wallpaper = w)) }
                if (activity.pages.size > 1) {
                    LabeledSlider(
                        "Default page", (activity.defaultPage + 1).toFloat(), 1f..activity.pages.size.toFloat(),
                        { onChange(activity.copy(defaultPage = it.toInt() - 1)) },
                        steps = (activity.pages.size - 2).coerceAtLeast(0),
                    )
                }
                Text("Pages and widgets are edited on the home screen (long-press → Edit Mode).", style = MaterialTheme.typography.bodySmall)
                if (canDelete) TextButton(onClick = onDelete) { Text("Delete activity") }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Done") } },
    )
}

/** Copies a picked image into app storage; used for wallpapers from settings. */
suspend fun copyImage(context: android.content.Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
        file.absolutePath
    }.getOrNull()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(s: SettingsScope, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf("time") }
    var target by remember { mutableStateOf(s.profile.activities.first().id) }
    var start by remember { mutableStateOf(22f * 60) }
    var end by remember { mutableStateOf(6f * 60) }
    var ssid by remember { mutableStateOf("") }
    var charging by remember { mutableStateOf(true) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == "time", { type = "time" }, { Text("Time of day") })
                    FilterChip(type == "wifi", { type = "wifi" }, { Text("WiFi network") })
                    FilterChip(type == "charging", { type = "charging" }, { Text("Charging") })
                }
                when (type) {
                    "time" -> {
                        LabeledSlider("From", start, 0f..1425f, { start = it }, steps = 94, format = { hhmm(it.toInt()) })
                        LabeledSlider("Until", end, 0f..1425f, { end = it }, steps = 94, format = { hhmm(it.toInt()) })
                    }
                    "wifi" -> {
                        OutlinedTextField(ssid, { ssid = it }, label = { Text("Network name (SSID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Reading the WiFi name requires location permission and location turned on.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Grant location permission") }
                    }
                    else -> com.customlauncher.ui.common.SwitchRow("Plugged in (off = on battery)", charging, { charging = it })
                }
                Text("Switch to", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.profile.activities.forEach { a -> FilterChip(target == a.id, { target = a.id }, { Text(a.name) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule: AutoSwitchRule = when (type) {
                    "time" -> AutoSwitchRule.TimeOfDay(target, start.toInt(), end.toInt())
                    "wifi" -> AutoSwitchRule.Wifi(target, ssid.trim())
                    else -> AutoSwitchRule.Charging(target, charging)
                }
                s.edit { it.copy(autoSwitch = it.autoSwitch + rule) }
                onDismiss()
            }, enabled = type != "wifi" || ssid.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
