package com.customlauncher.widget

import com.customlauncher.widgets.apps.AppGridWidget
import com.customlauncher.widgets.apps.AppIconWidget
import com.customlauncher.widgets.calendar.AgendaWidget
import com.customlauncher.widgets.clock.ClockWidget
import com.customlauncher.widgets.football.FootballWidget
import com.customlauncher.widgets.media.MediaWidget
import com.customlauncher.widgets.notes.NotesWidget
import com.customlauncher.widgets.stats.SystemStatsWidget
import com.customlauncher.widgets.sticker.StickerWidget

/**
 * Every available widget. Adding a widget = one new file + one line here.
 * Order is the order shown in the "Add widget" sheet.
 */
object WidgetRegistry {
    val all: List<LauncherWidget> = listOf(
        ClockWidget,
        SystemStatsWidget,
        AgendaWidget,
        NotesWidget,
        MediaWidget,
        AppGridWidget,
        AppIconWidget,
        FootballWidget,
        StickerWidget,
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): LauncherWidget? = byId[id]
}
