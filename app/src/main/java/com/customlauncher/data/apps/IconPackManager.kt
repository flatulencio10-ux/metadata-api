package com.customlauncher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(val packageName: String, val label: String)

/**
 * Supports the de-facto standard ADW / Nova icon pack format: the pack app ships an
 * `appfilter.xml` (in res/xml or assets) mapping `ComponentInfo{pkg/cls}` to drawable names.
 */
class IconPackManager(private val context: Context) {

    private val packs = ConcurrentHashMap<String, IconPack>()

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        return THEME_ACTIONS.flatMap { action ->
            pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
        }.map { it.activityInfo.packageName }
            .distinct()
            .map { pkg ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                IconPackInfo(pkg, label)
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Loads (and caches) a pack; returns null if it is not installed or unreadable. */
    fun pack(packageName: String): IconPack? = packs[packageName] ?: runCatching {
        IconPack.load(context, packageName)
    }.onFailure { Log.w("IconPackManager", "Failed loading $packageName", it) }
        .getOrNull()?.also { packs[packageName] = it }

    fun invalidate() = packs.clear()

    companion object {
        private val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
        )
    }
}

class IconPack private constructor(
    private val packageName: String,
    private val resources: Resources,
    private val mapping: Map<String, String>,
) {
    fun drawableFor(component: ComponentName): Drawable? {
        val name = mapping["ComponentInfo{${component.packageName}/${component.className}}"] ?: return null
        @Suppress("DiscouragedApi")
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { resources.getDrawable(id, null) }.getOrNull()
    }

    companion object {
        fun load(context: Context, packageName: String): IconPack {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val mapping = HashMap<String, String>()

            @Suppress("DiscouragedApi")
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "UTF-8")
                }
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null) mapping[comp] = drawable
                }
                event = parser.next()
            }
            return IconPack(packageName, res, mapping)
        }
    }
}
