package com.customlauncher.widget

import androidx.compose.runtime.compositionLocalOf
import com.customlauncher.core.model.Action

/**
 * Services the launcher gives each rendered widget instance, provided via [LocalWidgetHost].
 */
data class WidgetHost(
    val instanceId: String,
    /**
     * False when the widget's page is off screen, the launcher is paused, or Edit Mode is
     * active. Widgets should stop tickers/polling while inactive (performance).
     */
    val isActive: Boolean,
    /** True when rendered inside a panel (usually small); widgets can show a compact layout. */
    val inPanel: Boolean = false,
    val isEditing: Boolean = false,
    /** Persists a new config for this instance (e.g. the notes widget saving its text). */
    val updateConfig: (WidgetConfig) -> Unit = {},
    /** Opens this instance full screen. */
    val openFullscreen: () -> Unit = {},
    val runAction: (Action) -> Unit = {},
)

val LocalWidgetHost = compositionLocalOf {
    WidgetHost(instanceId = "", isActive = true)
}
