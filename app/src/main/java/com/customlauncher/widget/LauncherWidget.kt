package com.customlauncher.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The widget plugin contract. To add a widget:
 *  1. Create one file implementing this interface (usually an `object`).
 *  2. Add it to [WidgetRegistry.all].
 * That's it: the widget shows up in the "Add widget" sheet, can be placed on pages and
 * panels, styled, configured (via [configSchema]) and exported with profiles.
 */
interface LauncherWidget {
    /** Stable id stored in layouts. Never change it once released. */
    val id: String
    val name: String
    val description: String get() = ""
    val icon: ImageVector
    /** Initial size in dp when added to a page. */
    val defaultSize: DpSize
    /** Minimum size the resize handles allow. */
    val minSize: DpSize get() = DpSize(48.dp, 48.dp)
    /** Settings this widget understands; drives the auto-generated config screen. */
    val configSchema: List<ConfigField>

    /**
     * Renders the widget. Read [LocalWidgetHost] for the instance id, whether the widget is
     * on screen (pause timers/updates when not), and to persist config changes.
     */
    @Composable
    fun Content(config: WidgetConfig, size: DpSize)

    /**
     * Settings UI. The default renders [configSchema]; override for custom editors.
     * Call [onChange] with the new config; the host persists it.
     */
    @Composable
    fun ConfigScreen(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
        SchemaConfigEditor(configSchema, config, onChange)
    }

    /** Full-screen view used by "open widget fullscreen". Defaults to [Content] at full size. */
    @Composable
    fun FullscreenContent(config: WidgetConfig, size: DpSize) {
        Content(config, size)
    }

    /** Called once when the widget is placed, e.g. to schedule background refresh work. */
    fun onAdded(context: android.content.Context, config: WidgetConfig) {}
}
