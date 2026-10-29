package com.customlauncher.desktop

import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Recently used apps for the taskbar, from UsageStatsManager. Needs the special "usage
 * access" permission, which the user grants in system settings ([settingsIntent]).
 */
class RecentApps(private val context: Context, private val scope: CoroutineScope) {
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    /** Package names, most recent first. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Re-reads usage (cheap; called when the home screen resumes). */
    fun refresh() {
        if (!hasPermission()) { _recent.value = emptyList(); return }
        scope.launch {
            _recent.value = withContext(Dispatchers.IO) {
                val usm = context.getSystemService(UsageStatsManager::class.java)
                val now = System.currentTimeMillis()
                val pm = context.packageManager
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - TimeUnit.DAYS.toMillis(2), now)
                    .filter { it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 && it.packageName != context.packageName }
                    .sortedByDescending { it.lastTimeUsed }
                    .map { it.packageName }
                    .distinct()
                    .filter { pm.getLaunchIntentForPackage(it) != null }
                    .take(12)
            }
        }
    }
}

/** Live values for the system tray: Wi-Fi connectivity and notification count. */
class TrayState(context: Context) {
    private val _wifi = MutableStateFlow(false)
    val wifi: StateFlow<Boolean> = _wifi.asStateFlow()
    private val _notifications = MutableStateFlow(0)
    /** Clearable notifications (needs notification access; 0 otherwise). */
    val notifications: StateFlow<Int> = _notifications.asStateFlow()

    init {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun update() {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            _wifi.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        update()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
        })
    }

    fun setNotificationCount(count: Int) { _notifications.value = count }
}

/**
 * Opens apps in a freeform window where the device supports it (desktop mode, foldables,
 * "force desktop mode" developer option); otherwise opens normally.
 */
object FreeformLauncher {
    fun supported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) != 0

    /** [component] is "pkg/cls" or a bare package name. Returns true when shown in a window. */
    fun launch(context: Context, component: String, bounds: Rect): Boolean {
        val intent = (ComponentName.unflattenFromString(component)?.let { Intent(Intent.ACTION_MAIN).setComponent(it).addCategory(Intent.CATEGORY_LAUNCHER) }
            ?: context.packageManager.getLaunchIntentForPackage(component) ?: return false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        if (supported(context)) {
            val ok = runCatching { context.startActivity(intent, ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle()) }.isSuccess
            if (ok) return true
        }
        context.startActivity(intent.removeFlagsCompat(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        return false
    }

    private fun Intent.removeFlagsCompat(flags: Int): Intent = setFlags(this.flags and flags.inv())
}
