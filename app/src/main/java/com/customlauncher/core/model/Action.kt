package com.customlauncher.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything a gesture, hot corner, panel button or volume key can do.
 *
 * Actions are plain serializable data so they live inside the layout profile JSON and can be
 * shared. [com.customlauncher.actions.ActionExecutor] turns them into side effects.
 * To add a new action: add a subclass here, handle it in ActionExecutor, and give it a
 * label in ActionCatalog.
 */
@Serializable
sealed interface Action {
    @Serializable @SerialName("none")
    data object None : Action

    /** [component] is a flattened ComponentName ("pkg/cls"). */
    @Serializable @SerialName("open_app")
    data class OpenApp(val component: String) : Action

    /** A static or dynamic app shortcut published through LauncherApps. */
    @Serializable @SerialName("open_shortcut")
    data class OpenShortcut(val packageName: String, val shortcutId: String, val label: String = "") : Action

    /** Shows a placed widget instance full screen (e.g. the football standings). */
    @Serializable @SerialName("open_widget")
    data class OpenWidgetFullscreen(val instanceId: String) : Action

    @Serializable @SerialName("switch_activity")
    data class SwitchActivity(val activityId: String) : Action

    @Serializable @SerialName("next_activity")
    data object NextActivity : Action

    @Serializable @SerialName("open_search")
    data object OpenSearch : Action

    @Serializable @SerialName("open_drawer")
    data object OpenDrawer : Action

    @Serializable @SerialName("edit_mode")
    data object EnterEditMode : Action

    @Serializable @SerialName("open_settings")
    data object OpenSettings : Action

    @Serializable @SerialName("gesture_canvas")
    data object DrawGesture : Action

    @Serializable @SerialName("lock")
    data object LockScreen : Action

    @Serializable @SerialName("recents")
    data object Recents : Action

    @Serializable @SerialName("notifications")
    data object NotificationShade : Action

    @Serializable @SerialName("quick_settings")
    data object QuickSettings : Action

    @Serializable @SerialName("flashlight")
    data object ToggleFlashlight : Action

    @Serializable @SerialName("media_play_pause")
    data object MediaPlayPause : Action

    @Serializable @SerialName("media_next")
    data object MediaNext : Action

    @Serializable @SerialName("media_previous")
    data object MediaPrevious : Action

    @Serializable @SerialName("next_page")
    data object NextPage : Action

    @Serializable @SerialName("previous_page")
    data object PreviousPage : Action

    @Serializable @SerialName("open_url")
    data class OpenUrl(val url: String) : Action

    /** Runs [actions] in order, waiting [delayMs] between each one. */
    @Serializable @SerialName("macro")
    data class Macro(val name: String = "Macro", val actions: List<Action> = emptyList(), val delayMs: Long = 300) : Action
}
