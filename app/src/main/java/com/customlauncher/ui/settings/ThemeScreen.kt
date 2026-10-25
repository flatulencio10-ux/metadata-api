package com.customlauncher.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.model.DarkMode
import com.customlauncher.core.model.FontChoice
import com.customlauncher.core.model.IconShape
import com.customlauncher.core.model.ThemeConfig
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.common.SwitchRow
import com.customlauncher.ui.theme.fontFamilyFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Theme engine: colors, Material You, fonts, icon shapes, icon packs, labels. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen(s: SettingsScope) {
    val theme = s.profile.theme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun set(transform: (ThemeConfig) -> ThemeConfig) = s.edit { it.copy(theme = transform(it.theme)) }
    val packs = remember { context.container.iconPackManager.installedPacks() }
    val sampleApps = LocalAppCatalog.current.apps.take(5)
    var fontError by remember { mutableStateOf<String?>(null) }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.filesDir, "fonts/custom_${System.currentTimeMillis()}.ttf").apply { parentFile?.mkdirs() }
                    context.contentResolver.openInputStream(uri)?.use { i -> file.outputStream().use { i.copyTo(it) } }
                    // Validate before switching so a bad file can't break the UI.
                    android.graphics.Typeface.createFromFile(file)
                    file.absolutePath
                }.getOrNull()
            }
            if (path == null) fontError = "Could not read that font file" else set { it.copy(font = FontChoice.CUSTOM, customFontPath = path) }
        }
    }

    SettingsPage {
        item { SectionTitle("Colors") }
        item {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Material You (dynamic color)", theme.dynamicColor, { on -> set { it.copy(dynamicColor = on) } },
                    supporting = "Derives colors from your wallpaper; overrides the custom scheme below")
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkMode.entries.forEach { m ->
                    FilterChip(theme.darkMode == m, { set { it.copy(darkMode = m) } }, { Text(m.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                }
            }
        }
        item { ColorRow("Primary", theme.colors.primary) { c -> set { it.copy(colors = it.colors.copy(primary = c)) } } }
        item { ColorRow("Accent", theme.colors.accent) { c -> set { it.copy(colors = it.colors.copy(accent = c)) } } }
        item { ColorRow("Surface (panels, dialogs)", theme.colors.surface) { c -> set { it.copy(colors = it.colors.copy(surface = c)) } } }
        item { ColorRow("Background", theme.colors.background) { c -> set { it.copy(colors = it.colors.copy(background = c)) } } }
        item { ColorRow("Text", theme.colors.text) { c -> set { it.copy(colors = it.colors.copy(text = c)) } } }
        item {
            OutlinedButton(onClick = { set { it.copy(colors = com.customlauncher.core.model.ColorScheme()) } }) { Text("Reset colors (Breeze Dark)") }
        }

        item { SectionTitle("Font") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FontChoice.entries.filter { it != FontChoice.CUSTOM || theme.customFontPath != null }.forEach { f ->
                    FilterChip(
                        theme.font == f,
                        { set { it.copy(font = f) } },
                        { Text(f.label, fontFamily = fontFamilyFor(theme.copy(font = f))) },
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) }) {
                Text("Import .ttf / .otf font")
            }
            fontError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        item { SectionTitle("Icons") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconShape.entries.forEach { shape ->
                    FilterChip(theme.iconShape == shape, { set { it.copy(iconShape = shape) } },
                        { Text(shape.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                sampleApps.forEach { AppIcon(it.key, theme.iconSizeDp.dp) }
            }
        }
        item {
            LabeledSlider("Icon size", theme.iconSizeDp, 32f..80f, { v -> set { it.copy(iconSizeDp = v) } }, format = { "${it.toInt()} dp" })
        }
        item { SwitchRow("Show labels", theme.showLabels, { on -> set { it.copy(showLabels = on) } }) }
        item {
            LabeledSlider("Label size", theme.labelSizeSp, 8f..18f, { v -> set { it.copy(labelSizeSp = v) } }, format = { "${it.toInt()} sp" })
        }

        item { SectionTitle("Icon pack") }
        item {
            Text("Any ADW / Nova compatible icon pack (appfilter.xml) works.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(theme.iconPack == null, {
                    context.container.iconLoader.clear(); set { it.copy(iconPack = null) }
                }, { Text("System icons") })
                packs.forEach { pack ->
                    FilterChip(theme.iconPack == pack.packageName, {
                        context.container.iconLoader.clear(); set { it.copy(iconPack = pack.packageName) }
                    }, { Text(pack.label) })
                }
            }
            if (packs.isEmpty()) Text("No icon packs installed.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Per-app icons: long-press an app in the drawer → Change icon.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
