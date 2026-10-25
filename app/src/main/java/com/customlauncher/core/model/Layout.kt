package com.customlauncher.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(12)

/** Visual chrome around any widget; edited in Edit Mode's inspector. */
@Serializable
data class WidgetStyle(
    val opacity: Float = 1f,
    val cornerRadiusDp: Float = 18f,
    /** ARGB tint drawn behind the widget content. Alpha 0 = no background. */
    val backgroundColor: Long = 0x66000000,
    /** Blur radius applied to the background layer (real blur needs Android 12+). */
    val blurDp: Float = 0f,
    val paddingDp: Float = 8f,
)

/**
 * One placed widget. Position and size are in dp relative to the page's content area,
 * which keeps placement free-form (KDE style) while staying density independent.
 */
@Serializable
data class WidgetInstance(
    val id: String = newId(),
    /** Matches [com.customlauncher.widget.LauncherWidget.id]. */
    val type: String,
    val x: Float = 16f,
    val y: Float = 16f,
    val width: Float = 160f,
    val height: Float = 160f,
    /** Higher z draws on top. */
    val z: Int = 0,
    val style: WidgetStyle = WidgetStyle(),
    /** Widget-specific settings, read via WidgetConfig. */
    val config: JsonObject = JsonObject(emptyMap()),
)

@Serializable
sealed interface Wallpaper {
    /** Show the system wallpaper through the transparent launcher window. */
    @Serializable @SerialName("system")
    data object System : Wallpaper

    @Serializable @SerialName("color")
    data class SolidColor(val color: Long) : Wallpaper

    @Serializable @SerialName("gradient")
    data class Gradient(val top: Long, val bottom: Long) : Wallpaper

    /** Image copied into app storage (absolute file path). */
    @Serializable @SerialName("image")
    data class Image(val path: String, val dim: Float = 0f) : Wallpaper
}

@Serializable
enum class PageTransition { SLIDE, CUBE, FADE, ZOOM, STACK }

@Serializable
data class Page(
    val id: String = newId(),
    /** Null = use the activity wallpaper. */
    val wallpaper: Wallpaper? = null,
    val transition: PageTransition = PageTransition.SLIDE,
    val widgets: List<WidgetInstance> = emptyList(),
)

@Serializable
enum class Edge { TOP, BOTTOM, LEFT, RIGHT;
    val isHorizontal: Boolean get() = this == TOP || this == BOTTOM
}

@Serializable
sealed interface PanelItem {
    val id: String

    @Serializable @SerialName("app")
    data class App(override val id: String = newId(), val component: String) : PanelItem

    /** A widget rendered inside the panel; [lengthDp] is its size along the panel. */
    @Serializable @SerialName("widget")
    data class Widget(override val id: String = newId(), val widget: WidgetInstance, val lengthDp: Float = 120f) : PanelItem

    /** Runs any action, e.g. a "lock" button. */
    @Serializable @SerialName("action")
    data class ActionButton(override val id: String = newId(), val label: String, val action: Action) : PanelItem

    /** Fixed gap, or flexible (fills remaining space) when [flexible] is true. */
    @Serializable @SerialName("spacer")
    data class Spacer(override val id: String = newId(), val sizeDp: Float = 16f, val flexible: Boolean = false) : PanelItem
}

@Serializable
data class Panel(
    val id: String = newId(),
    val edge: Edge = Edge.BOTTOM,
    val thicknessDp: Float = 64f,
    /** Floating panels are inset from the edge and fully rounded, like Plasma 6. */
    val floating: Boolean = true,
    val marginDp: Float = 8f,
    val autoHide: Boolean = false,
    val backgroundColor: Long = 0x99000000,
    val cornerRadiusDp: Float = 20f,
    val opacity: Float = 1f,
    /** Whether the page content area is shrunk so widgets never sit under this panel. */
    val reserveSpace: Boolean = true,
    val items: List<PanelItem> = emptyList(),
)

/** A KDE-style "Activity": a complete, independent home setup. */
@Serializable
data class ActivityProfile(
    val id: String = newId(),
    val name: String,
    val wallpaper: Wallpaper = Wallpaper.System,
    val pages: List<Page> = listOf(Page()),
    val panels: List<Panel> = emptyList(),
    val defaultPage: Int = 0,
)

/** Automatic activity switching. Rules are checked top to bottom; the first match wins. */
@Serializable
sealed interface AutoSwitchRule {
    val activityId: String
    val enabled: Boolean

    /** Minutes since midnight. Ranges may wrap past midnight (e.g. 22:00–06:00). */
    @Serializable @SerialName("time")
    data class TimeOfDay(
        override val activityId: String,
        val startMinute: Int,
        val endMinute: Int,
        override val enabled: Boolean = true,
    ) : AutoSwitchRule

    @Serializable @SerialName("wifi")
    data class Wifi(override val activityId: String, val ssid: String, override val enabled: Boolean = true) : AutoSwitchRule

    @Serializable @SerialName("charging")
    data class Charging(override val activityId: String, val charging: Boolean = true, override val enabled: Boolean = true) : AutoSwitchRule
}

@Serializable
data class GridConfig(
    val snapEnabled: Boolean = true,
    /** Snap step in dp; small values give "fine" snapping. */
    val snapDp: Float = 8f,
    val showGrid: Boolean = true,
)

@Serializable
data class SearchConfig(
    /** `%s` is replaced with the URL-encoded query. */
    val webSearchUrl: String = "https://duckduckgo.com/?q=%s",
    val searchContacts: Boolean = true,
    val searchShortcuts: Boolean = true,
    val searchSettings: Boolean = true,
    val calculator: Boolean = true,
    val unitConversion: Boolean = true,
)

/**
 * The whole launcher setup in one serializable object. This is what is persisted, exported
 * and imported (like a KDE global theme + layout). Secrets such as API keys are deliberately
 * NOT stored here so exported files are safe to share.
 */
@Serializable
data class LayoutProfile(
    val formatVersion: Int = CURRENT_FORMAT,
    val name: String = "My setup",
    val theme: ThemeConfig = ThemeConfig(),
    val activities: List<ActivityProfile> = emptyList(),
    val currentActivityId: String = "",
    val autoSwitch: List<AutoSwitchRule> = emptyList(),
    val gestures: GestureConfig = GestureConfig(),
    val drawer: DrawerConfig = DrawerConfig(),
    val grid: GridConfig = GridConfig(),
    val search: SearchConfig = SearchConfig(),
    val appOverrides: Map<String, AppOverride> = emptyMap(),
    val folders: List<AppFolder> = emptyList(),
) {
    val currentActivity: ActivityProfile
        get() = activities.firstOrNull { it.id == currentActivityId } ?: activities.first()

    /** Replaces the activity with the same id. */
    fun updateActivity(id: String, transform: (ActivityProfile) -> ActivityProfile): LayoutProfile =
        copy(activities = activities.map { if (it.id == id) transform(it) else it })

    fun updateCurrentActivity(transform: (ActivityProfile) -> ActivityProfile): LayoutProfile =
        updateActivity(currentActivity.id, transform)

    fun findWidget(instanceId: String): WidgetInstance? =
        activities.asSequence().flatMap { it.pages }.flatMap { it.widgets }.firstOrNull { it.id == instanceId }
            ?: activities.asSequence().flatMap { it.panels }.flatMap { it.items }
                .filterIsInstance<PanelItem.Widget>().map { it.widget }.firstOrNull { it.id == instanceId }

    /** Applies [transform] to the widget with [instanceId] wherever it lives (page or panel). */
    fun updateWidget(instanceId: String, transform: (WidgetInstance) -> WidgetInstance): LayoutProfile =
        copy(activities = activities.map { act ->
            act.copy(
                pages = act.pages.map { page ->
                    page.copy(widgets = page.widgets.map { if (it.id == instanceId) transform(it) else it })
                },
                panels = act.panels.map { panel ->
                    panel.copy(items = panel.items.map { item ->
                        if (item is PanelItem.Widget && item.widget.id == instanceId) item.copy(widget = transform(item.widget)) else item
                    })
                },
            )
        })

    companion object {
        const val CURRENT_FORMAT = 1
    }
}
