package com.customlauncher.actions

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.customlauncher.core.model.Action
import com.customlauncher.data.apps.AppRepository
import com.customlauncher.data.apps.ShortcutRepository
import com.customlauncher.data.profile.ProfileRepository
import com.customlauncher.services.LauncherAccessibilityService
import com.customlauncher.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Executes [Action]s. System-level actions are done here; UI-level ones (open search,
 * drawer, …) are forwarded to the home screen through [LauncherBus].
 */
class ActionExecutor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val bus: LauncherBus,
    private val apps: AppRepository,
    private val shortcuts: ShortcutRepository,
    private val profiles: ProfileRepository,
) {
    private val torch by lazy { TorchController(context) }
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }

    fun run(action: Action) {
        scope.launch { execute(action) }
    }

    suspend fun execute(action: Action) {
        try {
            executeInternal(action)
        } catch (e: Exception) {
            Log.w(TAG, "Action $action failed", e)
            bus.send(UiCommand.Message("Action failed: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private suspend fun executeInternal(action: Action) {
        when (action) {
            Action.None -> Unit
            is Action.OpenApp -> if (!apps.launch(action.component)) bus.send(UiCommand.Message("App not installed"))
            is Action.OpenShortcut -> if (!shortcuts.start(action.packageName, action.shortcutId)) {
                bus.send(UiCommand.Message("Shortcut unavailable (is this the default launcher?)"))
            }
            is Action.OpenWidgetFullscreen -> bus.send(UiCommand.OpenWidget(action.instanceId))
            is Action.SwitchActivity -> profiles.update { it.copy(currentActivityId = action.activityId) }
            Action.NextActivity -> profiles.update { p ->
                val i = p.activities.indexOfFirst { it.id == p.currentActivityId }
                p.copy(currentActivityId = p.activities[(i + 1).mod(p.activities.size)].id)
            }
            Action.OpenSearch -> bus.send(UiCommand.OpenSearch)
            Action.OpenDrawer -> bus.send(UiCommand.OpenDrawer)
            Action.EnterEditMode -> bus.send(UiCommand.EnterEditMode)
            Action.DrawGesture -> bus.send(UiCommand.OpenGestureCanvas)
            Action.NextPage -> bus.send(UiCommand.NextPage)
            Action.PreviousPage -> bus.send(UiCommand.PreviousPage)
            Action.OpenSettings -> context.startActivity(
                Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Action.LockScreen -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            Action.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            Action.QuickSettings -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            Action.NotificationShade -> if (!globalOrFalse(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) expandStatusBar()
            Action.ToggleFlashlight -> if (!torch.toggle()) bus.send(UiCommand.Message("No flashlight available"))
            Action.MediaPlayPause -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Action.MediaNext -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            Action.MediaPrevious -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            is Action.OpenUrl -> context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            is Action.Macro -> action.actions.forEachIndexed { i, step ->
                if (i > 0) delay(action.delayMs.coerceAtLeast(0))
                executeInternal(step)
            }
        }
    }

    private fun globalOrFalse(globalAction: Int): Boolean =
        LauncherAccessibilityService.instance?.performGlobalAction(globalAction) == true

    private fun global(globalAction: Int) {
        if (!globalOrFalse(globalAction)) {
            bus.send(UiCommand.Message("Enable the launcher accessibility service in Settings for this action"))
        }
    }

    /** Fallback when the accessibility service is off; uses the (greylisted) StatusBarManager API. */
    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandStatusBar() {
        runCatching {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }.onFailure { bus.send(UiCommand.Message("Enable the accessibility service to open notifications")) }
    }

    private fun mediaKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    companion object {
        private const val TAG = "ActionExecutor"
    }
}
