package com.customlauncher.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.ui.graphics.vector.ImageVector
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.LayoutProfile

/** Labels and icons for actions, shared by pickers, panels and settings. */
object ActionCatalog {

    /** Parameterless actions offered directly in the picker. */
    val simpleActions: List<Action> = listOf(
        Action.None,
        Action.OpenDrawer,
        Action.OpenSearch,
        Action.EnterEditMode,
        Action.OpenSettings,
        Action.DrawGesture,
        Action.NextActivity,
        Action.NextPage,
        Action.PreviousPage,
        Action.LockScreen,
        Action.Recents,
        Action.NotificationShade,
        Action.QuickSettings,
        Action.ToggleFlashlight,
        Action.MediaPlayPause,
        Action.MediaNext,
        Action.MediaPrevious,
    )

    fun label(action: Action, profile: LayoutProfile? = null, appLabel: (String) -> String = { it }): String = when (action) {
        Action.None -> "Do nothing"
        is Action.OpenApp -> "Open ${appLabel(action.component)}"
        is Action.OpenShortcut -> "Shortcut: ${action.label.ifBlank { action.shortcutId }}"
        is Action.OpenWidgetFullscreen -> "Open widget full screen" +
            (profile?.findWidget(action.instanceId)?.let { " (${it.type})" } ?: "")
        is Action.SwitchActivity -> "Switch to " +
            (profile?.activities?.firstOrNull { it.id == action.activityId }?.name ?: "activity")
        Action.NextActivity -> "Next activity"
        Action.OpenSearch -> "Open search"
        Action.OpenDrawer -> "Open app drawer"
        Action.EnterEditMode -> "Edit mode"
        Action.OpenSettings -> "Launcher settings"
        Action.DrawGesture -> "Draw a letter"
        Action.LockScreen -> "Lock screen"
        Action.Recents -> "Recent apps"
        Action.NotificationShade -> "Notifications"
        Action.QuickSettings -> "Quick settings"
        Action.ToggleFlashlight -> "Toggle flashlight"
        Action.MediaPlayPause -> "Play / pause"
        Action.MediaNext -> "Next track"
        Action.MediaPrevious -> "Previous track"
        Action.NextPage -> "Next page"
        Action.PreviousPage -> "Previous page"
        is Action.OpenUrl -> "Open ${action.url}"
        is Action.Macro -> "${action.name} (${action.actions.size} steps)"
    }

    fun icon(action: Action): ImageVector = when (action) {
        Action.None -> Icons.Filled.Block
        is Action.OpenApp -> Icons.AutoMirrored.Filled.Launch
        is Action.OpenShortcut -> Icons.Filled.Star
        is Action.OpenWidgetFullscreen -> Icons.Filled.Fullscreen
        is Action.SwitchActivity, Action.NextActivity -> Icons.Filled.SwapHoriz
        Action.OpenSearch -> Icons.Filled.Search
        Action.OpenDrawer -> Icons.Filled.Apps
        Action.EnterEditMode -> Icons.Filled.Edit
        Action.OpenSettings -> Icons.Filled.Settings
        Action.DrawGesture -> Icons.Filled.Gesture
        Action.LockScreen -> Icons.Filled.Lock
        Action.Recents -> Icons.Filled.ViewCarousel
        Action.NotificationShade -> Icons.Filled.Notifications
        Action.QuickSettings -> Icons.Filled.Tune
        Action.ToggleFlashlight -> Icons.Filled.FlashlightOn
        Action.MediaPlayPause -> Icons.Filled.PlayArrow
        Action.MediaNext -> Icons.Filled.SkipNext
        Action.MediaPrevious -> Icons.Filled.SkipPrevious
        Action.NextPage -> Icons.AutoMirrored.Filled.ArrowForward
        Action.PreviousPage -> Icons.AutoMirrored.Filled.ArrowBack
        is Action.OpenUrl -> Icons.Filled.Link
        is Action.Macro -> Icons.AutoMirrored.Filled.PlaylistPlay
    }
}
