package com.customlauncher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.model.DrawerConfig
import com.customlauncher.core.model.DrawerMode
import com.customlauncher.core.model.DrawerSort
import com.customlauncher.core.model.SearchConfig
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.common.SwitchRow
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerSettingsScreen(s: SettingsScope) {
    val d = s.profile.drawer
    fun set(t: (DrawerConfig) -> DrawerConfig) = s.edit { it.copy(drawer = t(it.drawer)) }
    val hidden = LocalAppCatalog.current.apps.filter { it.hidden }

    SettingsPage {
        item { SectionTitle("Layout") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DrawerMode.entries.forEach { m ->
                    FilterChip(d.mode == m, { set { it.copy(mode = m) } }, { Text(m.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                }
            }
        }
        item { LabeledSlider("Grid columns", d.columns.toFloat(), 3f..8f, { v -> set { it.copy(columns = v.toInt()) } }, steps = 4) }
        item { SwitchRow("Search bar", d.showSearchBar, { on -> set { it.copy(showSearchBar = on) } }) }
        item { ColorRow("Background", d.backgroundColor) { c -> set { it.copy(backgroundColor = c) } } }
        item { SectionTitle("Sort") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DrawerSort.entries.forEach { o -> FilterChip(d.sort == o, { set { it.copy(sort = o) } }, { Text(o.label) }) }
            }
        }
        item { SectionTitle("Hidden apps") }
        if (hidden.isEmpty()) item { Text("No hidden apps. Long-press an app in the drawer to hide it.", style = MaterialTheme.typography.bodySmall) }
        items(hidden, key = { it.key }) { app ->
            ListItem(
                headlineContent = { Text(app.label) },
                leadingContent = { AppIcon(app.key, 36.dp) },
                trailingContent = {
                    TextButton(onClick = {
                        s.edit { p ->
                            val o = p.appOverrides[app.key]
                            if (o == null) p else p.copy(appOverrides = p.appOverrides + (app.key to o.copy(hidden = false)))
                        }
                    }) { Text("Unhide") }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSettingsScreen(s: SettingsScope) {
    val c = s.profile.search
    fun set(t: (SearchConfig) -> SearchConfig) = s.edit { it.copy(search = t(it.search)) }
    var url by remember { mutableStateOf(c.webSearchUrl) }

    SettingsPage {
        item { SectionTitle("Providers") }
        item { SwitchRow("Calculator (2+2*3)", c.calculator, { on -> set { it.copy(calculator = on) } }) }
        item { SwitchRow("Unit conversion (10 km to mi)", c.unitConversion, { on -> set { it.copy(unitConversion = on) } }) }
        item { SwitchRow("Contacts", c.searchContacts, { on -> set { it.copy(searchContacts = on) } }) }
        item { SwitchRow("App shortcuts", c.searchShortcuts, { on -> set { it.copy(searchShortcuts = on) } }) }
        item { SwitchRow("System settings", c.searchSettings, { on -> set { it.copy(searchSettings = on) } }) }
        item { SectionTitle("Web search fallback") }
        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL template (%s = query)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
                    "Google" to "https://www.google.com/search?q=%s",
                    "Startpage" to "https://www.startpage.com/do/search?q=%s",
                    "Wikipedia" to "https://en.wikipedia.org/w/index.php?search=%s",
                ).forEach { (name, template) -> OutlinedButton(onClick = { url = template; set { it.copy(webSearchUrl = template) } }) { Text(name) } }
            }
        }
        item { Button(onClick = { set { it.copy(webSearchUrl = url.trim()) } }, enabled = url.contains("%s")) { Text("Save URL") } }
    }
}

/** Export / import the whole setup (like sharing a KDE global theme + layout). */
@Composable
fun BackupScreen(s: SettingsScope) {
    val context = LocalContext.current
    val repo = context.container.profileRepository
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(s.profile.name) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { repo.exportTo(uri); "Exported" }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { "Imported \"${repo.importFrom(uri).name}\"" }.getOrElse { it.message ?: "Import failed" }
        }
    }

    SettingsPage {
        item { SectionTitle("Layout profile") }
        item {
            Text(
                "A profile holds everything: activities, pages, widgets, panels, theme, gestures and drawer settings. " +
                    "API keys are never exported. Images (wallpapers, custom icons) stay on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { s.edit { it.copy(name = name.trim().ifBlank { "My setup" }) } }) { Text("Save name") }
        }
        item {
            Button(onClick = {
                val safe = s.profile.name.replace(Regex("[^A-Za-z0-9_-]+"), "_")
                exporter.launch("launcher-$safe-${LocalDate.now()}.json")
            }) { Text("Export to file…") }
        }
        item { OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Import from file…") } }
        item { TextButton(onClick = { confirmReset = true }) { Text("Reset to default setup") } }
        message?.let { m -> item { Text(m, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset everything?") },
            text = { Text("This replaces the current profile. Export it first if you want to keep it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { repo.resetToDefault(); message = "Reset to defaults" }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
