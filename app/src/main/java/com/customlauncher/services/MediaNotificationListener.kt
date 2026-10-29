package com.customlauncher.services

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.customlauncher.container

/**
 * Exists so the app may call MediaSessionManager.getActiveSessions(); notifications themselves
 * are not read or stored.
 */
class MediaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        container.mediaRepository.onListenerConnected()
        updateCount()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) = updateCount()

    /** Only the number of clearable notifications is kept (taskbar tray badge). */
    private fun updateCount() {
        val count = runCatching { activeNotifications.count { it.isClearable && !it.isOngoing } }.getOrDefault(0)
        container.trayState.setNotificationCount(count)
    }

    /** Feeds "notification received" behaviour triggers (only the package name is used). */
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg != packageName) container.triggerSources.notificationPosted(pkg)
        updateCount()
    }

    companion object {
        fun component(context: Context) = ComponentName(context, MediaNotificationListener::class.java)

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(':').any { ComponentName.unflattenFromString(it) == component(context) }
        }
    }
}
