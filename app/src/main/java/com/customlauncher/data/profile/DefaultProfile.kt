package com.customlauncher.data.profile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.ActivityProfile
import com.customlauncher.core.model.AutoSwitchRule
import com.customlauncher.core.model.Edge
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Page
import com.customlauncher.core.model.PageTransition
import com.customlauncher.core.model.Panel
import com.customlauncher.core.model.PanelItem
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.core.model.WidgetStyle
import com.customlauncher.core.model.newId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** First-run setup: two activities, a few widgets and a floating dock with default apps. */
object DefaultProfile {

    fun create(context: Context): LayoutProfile {
        val dock = defaultDockApps(context).map { PanelItem.App(component = it) }
        val dockPanel = Panel(
            edge = Edge.BOTTOM,
            thicknessDp = 68f,
            floating = true,
            items = listOf(PanelItem.Spacer(flexible = true)) + dock + listOf(
                PanelItem.ActionButton(label = "Apps", action = Action.OpenDrawer),
                PanelItem.Spacer(flexible = true),
            ),
        )

        val home = ActivityProfile(
            name = "Home",
            pages = listOf(
                Page(
                    transition = PageTransition.CUBE,
                    widgets = listOf(
                        WidgetInstance(type = "clock", x = 16f, y = 48f, width = 328f, height = 140f,
                            style = WidgetStyle(backgroundColor = 0x00000000)),
                        WidgetInstance(type = "system_stats", x = 16f, y = 204f, width = 160f, height = 160f),
                        WidgetInstance(type = "media", x = 184f, y = 204f, width = 160f, height = 160f),
                        WidgetInstance(type = "agenda", x = 16f, y = 380f, width = 328f, height = 180f),
                    ),
                ),
                Page(
                    transition = PageTransition.CUBE,
                    widgets = listOf(
                        WidgetInstance(type = "football", x = 16f, y = 48f, width = 328f, height = 220f),
                        WidgetInstance(type = "notes", x = 16f, y = 284f, width = 328f, height = 180f,
                            style = WidgetStyle(backgroundColor = 0xCCFFF59D)),
                    ),
                ),
            ),
            panels = listOf(dockPanel),
        )

        val night = ActivityProfile(
            name = "Night",
            wallpaper = Wallpaper.Gradient(top = 0xFF0B1026, bottom = 0xFF000000),
            pages = listOf(
                Page(
                    transition = PageTransition.FADE,
                    widgets = listOf(
                        WidgetInstance(
                            type = "clock", x = 16f, y = 120f, width = 328f, height = 260f,
                            style = WidgetStyle(backgroundColor = 0x00000000),
                            config = JsonObject(mapOf("style" to JsonPrimitive("word"))),
                        ),
                    ),
                ),
            ),
            panels = listOf(dockPanel.copy(id = newId(), backgroundColor = 0x66000000)),
        )

        return LayoutProfile(
            activities = listOf(home, night),
            currentActivityId = home.id,
            // Example rule, disabled by default: switch to "Night" from 22:00 to 06:00.
            autoSwitch = listOf(
                AutoSwitchRule.TimeOfDay(activityId = night.id, startMinute = 22 * 60, endMinute = 6 * 60, enabled = false),
            ),
        )
    }

    /** Resolves the user's default phone, messaging, browser and camera apps. */
    private fun defaultDockApps(context: Context): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull { intent ->
            val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?.takeIf { it != "android" } ?: return@mapNotNull null
            pm.getLaunchIntentForPackage(pkg)?.component?.flattenToString()
        }.distinct()
    }
}
