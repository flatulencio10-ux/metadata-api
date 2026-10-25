package com.customlauncher.widgets.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customlauncher.core.model.Action
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.ui.theme.LocalLauncherTheme
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig

/** A folder shown as a widget: a titled grid of app icons. */
object AppGridWidget : LauncherWidget {
    override val id = "app_grid"
    override val name = "App folder grid"
    override val description = "Several apps in a grid, like an open folder"
    override val icon = Icons.Filled.GridView
    override val defaultSize = DpSize(320.dp, 200.dp)

    private val title = ConfigField.Text("title", "Title", default = "Folder")
    private val apps = ConfigField.Apps("apps", "Apps")
    private val columns = ConfigField.Slider("columns", "Columns", 2f, 8f, 4f, steps = 5)
    private val labels = ConfigField.Toggle("labels", "Show labels", true)

    override val configSchema = listOf(title, apps, columns, labels)

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val host = LocalWidgetHost.current
        val theme = LocalLauncherTheme.current
        val catalog = LocalAppCatalog.current
        val keys = config.stringList(apps).filter { it in catalog.byKey }
        val cols = config.float(columns).toInt().coerceAtLeast(1)
        val showLabels = config.bool(labels)
        val cell = size.width / cols
        val iconSize = minOf(theme.iconSizeDp.dp, cell - 12.dp).coerceAtLeast(20.dp)

        Column(Modifier.fillMaxSize()) {
            if (config.string(title).isNotBlank()) {
                Text(config.string(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (keys.isEmpty()) {
                Text("Tap the widget's settings to choose apps", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable(enabled = !host.isEditing) { host.runAction(Action.OpenApp(key)) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(key, iconSize)
                                if (showLabels) {
                                    Text(
                                        catalog.label(key), fontSize = theme.labelSizeSp.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
