package com.customlauncher.widgets.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customlauncher.ui.theme.toColor
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig
import kotlinx.coroutines.delay

/**
 * Sticky note, editable in place. The text lives in the widget's config, so it is persisted
 * with the profile (and exported with it).
 */
object NotesWidget : LauncherWidget {
    override val id = "notes"
    override val name = "Sticky note"
    override val description = "Quick note you can type into directly"
    override val icon = Icons.AutoMirrored.Filled.StickyNote2
    override val defaultSize = DpSize(200.dp, 180.dp)

    private val text = ConfigField.Text("text", "Text", multiline = true)
    private val fontSize = ConfigField.Slider("fontSize", "Font size", 10f, 32f, 16f)
    private val customColor = ConfigField.Toggle("customColor", "Custom text color", true)
    private val textColor = ConfigField.ColorPick("textColor", "Text color", 0xFF3A3000)
    private val placeholder = ConfigField.Text("placeholder", "Placeholder", default = "Write something…")

    override val configSchema = listOf(text, fontSize, customColor, textColor, placeholder)

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val host = LocalWidgetHost.current
        // Local copy keyed by instance so typing is not interrupted by profile round-trips.
        var value by remember(host.instanceId) { mutableStateOf(config.string(text)) }
        val latestConfig by rememberUpdatedState(config)

        // Debounced save.
        LaunchedEffect(value) {
            delay(600)
            if (value != latestConfig.string(text)) host.updateConfig(latestConfig.with(text.key, value))
        }

        val color = if (config.bool(customColor)) config.long(textColor).toColor() else LocalContentColor.current
        val style = LocalTextStyle.current.copy(color = color, fontSize = config.float(fontSize).sp)
        Box(Modifier.fillMaxSize()) {
            if (value.isEmpty()) Text(config.string(placeholder), style = style.copy(color = color.copy(alpha = 0.5f)))
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                enabled = !host.isEditing,
                textStyle = style,
                cursorBrush = SolidColor(color),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
