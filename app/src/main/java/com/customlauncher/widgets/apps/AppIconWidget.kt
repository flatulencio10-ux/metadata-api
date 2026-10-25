package com.customlauncher.widgets.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
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

/** A single app icon placed freely on a page (what "Add to home" creates). */
object AppIconWidget : LauncherWidget {
    override val id = "app_icon"
    override val name = "App icon"
    override val description = "A single app shortcut"
    override val icon = Icons.Filled.Apps
    override val defaultSize = DpSize(80.dp, 96.dp)

    val app = ConfigField.Apps("app", "App (first selected is used)")

    override val configSchema = listOf<ConfigField>(app)

    fun configFor(component: String) = WidgetConfig().with(app.key, listOf(component))

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val host = LocalWidgetHost.current
        val theme = LocalLauncherTheme.current
        val key = config.stringList(app).firstOrNull()
        val label = key?.let { LocalAppCatalog.current.label(it) } ?: "Choose an app"
        val iconSize = minOf(theme.iconSizeDp.dp, size.width, size.height - if (theme.showLabels) 20.dp else 0.dp)
        Column(
            Modifier
                .fillMaxSize()
                .clickable(enabled = !host.isEditing) { key?.let { host.runAction(Action.OpenApp(it)) } },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (key != null) AppIcon(key, iconSize.coerceAtLeast(16.dp))
            if (theme.showLabels) {
                Text(label, fontSize = theme.labelSizeSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}
