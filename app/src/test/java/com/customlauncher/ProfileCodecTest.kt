package com.customlauncher

import com.customlauncher.core.model.Action
import com.customlauncher.core.model.ActivityProfile
import com.customlauncher.core.model.AutoSwitchRule
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Page
import com.customlauncher.core.model.Panel
import com.customlauncher.core.model.PanelItem
import com.customlauncher.core.model.Trigger
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.data.profile.ProfileCodec
import com.customlauncher.widget.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {
    private val sample = LayoutProfile(
        activities = listOf(
            ActivityProfile(
                id = "a1", name = "School",
                wallpaper = Wallpaper.Gradient(0xFF000000, 0xFFFFFFFF),
                pages = listOf(Page(widgets = listOf(WidgetInstance(type = "clock", config = WidgetConfig().with("style", "analog").json)))),
                panels = listOf(Panel(items = listOf(PanelItem.Spacer(flexible = true), PanelItem.ActionButton(label = "x", action = Action.LockScreen)))),
            ),
        ),
        currentActivityId = "a1",
        autoSwitch = listOf(AutoSwitchRule.Wifi("a1", "HomeWifi")),
    ).let { it.copy(gestures = it.gestures.copy(bindings = it.gestures.bindings + (Trigger.CORNER_TOP_LEFT_TAP to Action.Macro("m", listOf(Action.ToggleFlashlight, Action.OpenUrl("https://x"))))) ) }

    @Test fun roundTrip() {
        val json = ProfileCodec.encode(sample)
        assertEquals(sample, ProfileCodec.decode(json))
    }

    @Test fun repairsEmptyProfile() {
        val p = ProfileCodec.decode("{}")
        assertEquals(1, p.activities.size)
        assertEquals(p.activities[0].id, p.currentActivityId)
        assertTrue(p.activities[0].pages.isNotEmpty())
    }

    @Test fun ignoresUnknownFieldsAndBadCurrentId() {
        val json = ProfileCodec.encode(sample).replaceFirst("{", "{\"futureField\": 1,").replace("\"currentActivityId\": \"a1\"", "\"currentActivityId\": \"zzz\"")
        assertEquals("a1", ProfileCodec.decode(json).currentActivityId)
    }
}
