package com.customlauncher.data.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppShortcut(
    val packageName: String,
    val id: String,
    val label: String,
    val info: ShortcutInfo,
)

/**
 * App shortcuts (long-press menu entries) via LauncherApps. Only available while this app is
 * the default launcher, so every call degrades to an empty list otherwise.
 */
class ShortcutRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    val isAvailable: Boolean
        get() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    suspend fun shortcutsFor(packageName: String?): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery().apply {
            if (packageName != null) setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        try {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty().map {
                AppShortcut(it.`package`, it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString(), it)
            }
        } catch (e: Exception) {
            Log.w("ShortcutRepository", "Shortcut query failed", e)
            emptyList()
        }
    }

    suspend fun icon(shortcut: AppShortcut, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val d = launcherApps.getShortcutIconDrawable(shortcut.info, context.resources.displayMetrics.densityDpi)
            d?.let { IconLoader.render(it, sizePx, masked = true).bitmap }
        }.getOrNull()
    }

    fun start(packageName: String, shortcutId: String): Boolean = try {
        launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
        true
    } catch (e: Exception) {
        Log.w("ShortcutRepository", "Cannot start shortcut $packageName/$shortcutId", e)
        false
    }
}
