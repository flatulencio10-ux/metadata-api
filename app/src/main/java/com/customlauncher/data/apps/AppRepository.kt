package com.customlauncher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.customlauncher.core.model.AppOverride
import com.customlauncher.core.model.DrawerSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Source of truth for installed launchable apps. Listens to package changes via
 * [LauncherApps.Callback] so the drawer stays current without polling.
 */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val pm = context.packageManager

    /** Raw app list (no overrides applied). */
    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val rawApps: StateFlow<List<AppInfo>> = _rawApps.asStateFlow()

    private var refreshJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            val list = launcherApps.getActivityList(null, Process.myUserHandle())
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    val appInfo = info.applicationInfo
                    val updateTime = runCatching { pm.getPackageInfo(appInfo.packageName, 0).lastUpdateTime }
                        .getOrDefault(info.firstInstallTime)
                    val label = info.label?.toString() ?: appInfo.packageName
                    AppInfo(
                        key = info.componentName.flattenToString(),
                        component = info.componentName,
                        label = label,
                        originalLabel = label,
                        packageName = appInfo.packageName,
                        category = categoryTitle(appInfo),
                        installTime = info.firstInstallTime,
                        updateTime = updateTime,
                        user = info.user,
                    )
                }
            _rawApps.value = list
        }
    }

    private fun categoryTitle(info: ApplicationInfo): String {
        if (info.category == ApplicationInfo.CATEGORY_UNDEFINED) return "Other"
        return ApplicationInfo.getCategoryTitle(context, info.category)?.toString() ?: "Other"
    }

    fun find(key: String): AppInfo? = _rawApps.value.firstOrNull { it.key == key }

    /** Launches an app by flattened ComponentName. Returns false if it no longer exists. */
    fun launch(key: String, sourceBounds: Rect? = null): Boolean {
        val component = ComponentName.unflattenFromString(key) ?: return false
        val user = find(key)?.user ?: Process.myUserHandle()
        return try {
            launcherApps.startMainActivity(component, user, sourceBounds, null)
            true
        } catch (e: Exception) {
            Log.w("AppRepository", "Cannot launch $key", e)
            // Fall back to the package's default launch intent (activity may have been renamed).
            pm.getLaunchIntentForPackage(component.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            } ?: false
        }
    }

    fun openAppInfo(key: String) {
        val pkg = ComponentName.unflattenFromString(key)?.packageName ?: return
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun uninstall(key: String) {
        val pkg = ComponentName.unflattenFromString(key)?.packageName ?: return
        @Suppress("DEPRECATION")
        context.startActivity(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** Applies user overrides (rename/hide/icon/category) to the raw list. */
        fun applyOverrides(apps: List<AppInfo>, overrides: Map<String, AppOverride>): List<AppInfo> =
            apps.map { app ->
                val o = overrides[app.key] ?: return@map app
                app.copy(
                    label = o.label?.takeIf { it.isNotBlank() } ?: app.originalLabel,
                    hidden = o.hidden,
                    customIconPath = o.iconPath,
                    category = o.category ?: app.category,
                )
            }

        fun sort(apps: List<AppInfo>, sort: DrawerSort): List<AppInfo> = when (sort) {
            DrawerSort.NAME -> apps.sortedBy { it.label.lowercase() }
            DrawerSort.NAME_DESC -> apps.sortedByDescending { it.label.lowercase() }
            DrawerSort.INSTALL_DATE -> apps.sortedByDescending { it.installTime }
            DrawerSort.UPDATE_DATE -> apps.sortedByDescending { it.updateTime }
        }
    }
}
