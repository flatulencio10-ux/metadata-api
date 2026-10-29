package com.customlauncher.services

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.customlauncher.core.model.Trigger
import com.customlauncher.container

/**
 * Provides global actions (lock, recents, notification shade, quick settings) and
 * volume-key long-press bindings. It never reads window content.
 */
class LauncherAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    /** Key code of a volume key currently held, waiting to become a long press. */
    private var pendingKey: Int? = null
    private var longPressFired = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /**
     * Volume keys: only intercepted when a long-press binding exists. A short press is
     * re-applied manually as a normal volume step because the original event was consumed.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val trigger = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> Trigger.VOLUME_UP_LONG
            KeyEvent.KEYCODE_VOLUME_DOWN -> Trigger.VOLUME_DOWN_LONG
            else -> return false
        }
        val profile = container.profileRepository.profile.value ?: return false
        val action = profile.gestures.actionFor(trigger)
        if (action == com.customlauncher.core.model.Action.None) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                pendingKey = event.keyCode
                longPressFired = false
                handler.postDelayed({
                    if (pendingKey == event.keyCode) {
                        longPressFired = true
                        container.actionExecutor.run(action)
                    }
                }, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacksAndMessages(null)
                if (!longPressFired) {
                    val direction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                    getSystemService(AudioManager::class.java)
                        .adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
                }
                pendingKey = null
            }
        }
        return true
    }

    companion object {
        private const val LONG_PRESS_MS = 600L

        /** Set while the service is connected; used by ActionExecutor for global actions. */
        @Volatile
        var instance: LauncherAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = ComponentName(context, LauncherAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabled?.split(':')?.any { it.equals(flat, ignoreCase = true) } == true
        }
    }
}
