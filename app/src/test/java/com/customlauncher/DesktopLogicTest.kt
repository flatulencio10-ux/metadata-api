package com.customlauncher

import com.customlauncher.core.model.FloatingWindow
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.desktop.NodeClipboard
import com.customlauncher.desktop.StartMenuLogic
import com.customlauncher.desktop.WindowMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DesktopLogicTest {
    private val win = FloatingWindow(widget = WidgetInstance(type = "clock"), x = 10f, y = 10f, width = 200f, height = 150f)

    @Test fun moveKeepsTitleBarReachable() {
        val moved = WindowMath.move(win, 5000f, 5000f, 400f, 800f)
        assertEquals(336f, moved.x, 0f)
        assertEquals(800f - WindowMath.TITLE_H, moved.y, 0f)
        assertEquals(0f, WindowMath.move(win, 0f, -500f, 400f, 800f).y, 0f)
    }

    @Test fun resizeHasMinimum() {
        val r = WindowMath.resize(win, -1000f, -1000f, 400f, 800f)
        assertEquals(WindowMath.MIN_W, r.width, 0f)
        assertEquals(WindowMath.MIN_H, r.height, 0f)
    }

    @Test fun searchRanksPrefixThenInitials() {
        val apps = listOf("Maps", "Google Maps", "Gmail", "Messages", "Calendar")
        assertEquals(listOf("Maps", "Google Maps", "Gmail"), StartMenuLogic.search(apps, "ma") { it })
        assertEquals(listOf("Gmail", "Google Maps"), StartMenuLogic.search(apps, "gm") { it })
        assertEquals(emptyList<String>(), StartMenuLogic.search(apps, "zzz") { it })
    }

    @Test fun categoriesOtherLast() = assertEquals(listOf("Games", "Social", "Other"), StartMenuLogic.categories(listOf("Other", "Social", "Games", "Social")))

    @Test fun pasteReIdsAndKeepsGroups() {
        val a = WidgetInstance(id = "a", type = "t", x = 100f, y = 100f, z = 1, groupId = "g")
        val b = WidgetInstance(id = "b", type = "t", x = 150f, y = 120f, z = 2, groupId = "g")
        var n = 0
        val pasted = NodeClipboard.paste(listOf(a, b), 10f, 20f, maxZ = 5) { "id${n++}" }
        assertEquals(listOf(10f, 60f), pasted.map { it.x })
        assertEquals(listOf(20f, 40f), pasted.map { it.y })
        assertEquals(listOf(6, 7), pasted.map { it.z })
        assertEquals(pasted[0].groupId, pasted[1].groupId)
        assertNotEquals("g", pasted[0].groupId)
        assertNotEquals("a", pasted[0].id)
    }
}
