package com.customlauncher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads app icons with this priority: user override image → icon pack → system icon.
 * Adaptive icons are rendered *unmasked* so the theme's icon shape can clip them; legacy
 * icons are returned as-is. Results are cached in memory.
 */
class IconLoader(private val context: Context, private val iconPacks: IconPackManager) {

    data class LoadedIcon(val bitmap: ImageBitmap, val shapeable: Boolean)

    private val cache = object : LruCache<String, LoadedIcon>(400) {}
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun cached(key: String, pack: String?, overridePath: String?, sizePx: Int, masked: Boolean): LoadedIcon? =
        cache.get(cacheKey(key, pack, overridePath, sizePx, masked))

    suspend fun load(key: String, pack: String?, overridePath: String?, sizePx: Int, masked: Boolean): LoadedIcon? {
        val ck = cacheKey(key, pack, overridePath, sizePx, masked)
        cache.get(ck)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { loadUncached(key, pack, overridePath, sizePx, masked) }.getOrNull()
        }?.also { cache.put(ck, it) }
    }

    fun clear() = cache.evictAll()

    private fun cacheKey(key: String, pack: String?, path: String?, size: Int, masked: Boolean) =
        "$key|$pack|$path|$size|$masked"

    private fun loadUncached(key: String, pack: String?, overridePath: String?, sizePx: Int, masked: Boolean): LoadedIcon? {
        overridePath?.let { path ->
            BitmapFactory.decodeFile(path)?.let { bmp ->
                return LoadedIcon(Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true).asImageBitmap(), shapeable = true)
            }
        }
        val component = ComponentName.unflattenFromString(key) ?: return null
        pack?.let { iconPacks.pack(it)?.drawableFor(component) }?.let { d ->
            return LoadedIcon(d.toBitmap(sizePx, sizePx).asImageBitmap(), shapeable = false)
        }
        val drawable = systemIcon(component) ?: return null
        return render(drawable, sizePx, masked)
    }

    private fun systemIcon(component: ComponentName): Drawable? {
        val density = context.resources.displayMetrics.densityDpi
        val info = launcherApps.getActivityList(component.packageName, Process.myUserHandle())
            .firstOrNull { it.componentName == component }
            ?: launcherApps.getActivityList(component.packageName, Process.myUserHandle()).firstOrNull()
        return info?.getIcon(density) ?: runCatching { context.packageManager.getApplicationIcon(component.packageName) }.getOrNull()
    }

    companion object {
        /**
         * Renders a drawable to a square bitmap. For adaptive icons with [masked] = false the
         * layers are drawn without the system mask so Compose can clip with any shape.
         */
        fun render(drawable: Drawable, sizePx: Int, masked: Boolean): LoadedIcon {
            if (!masked && drawable is AdaptiveIconDrawable) {
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                // Layers are 108dp with the visible area being the inner 72dp: extend by 1/4 on each side.
                val inset = sizePx / 4
                listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                    layer.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                    layer.draw(canvas)
                }
                return LoadedIcon(bmp.asImageBitmap(), shapeable = true)
            }
            return LoadedIcon(drawable.toBitmap(sizePx, sizePx).asImageBitmap(), shapeable = false)
        }
    }
}
