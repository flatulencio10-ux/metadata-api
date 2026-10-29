package com.customlauncher.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(12)

/**
 * Visual chrome around any widget; edited in Edit Mode's inspector.
 * While [useCardStyle] is true the background, border, radius and blur come from the global
 * [CardStyle] in the theme; the fields below are only used once the user customises them.
 */
@Serializable
data class WidgetStyle(
    val useCardStyle: Boolean = true,
    val opacity: Float = 1f,
    val cornerRadiusDp: Float = 24f,
    /** ARGB tint drawn behind the widget content. Alpha 0 = no background. */
    val backgroundColor: Long = 0x66000000,
    /** Blur radius applied to the background layer (real blur needs Android 12+). */
    val blurDp: Float = 0f,
    val paddingDp: Float = 8f,
)

/** Where a node draws relative to panels (docks/bars). */
@Serializable
enum class NodeLayer { NORMAL, ABOVE_PANELS }

/**
 * One element on the canvas ("node"): widgets, app icons, stickers and objects all share it.
 * Position/size are in dp relative to the page's content area; the transform (rotation,
 * scale, flip, pivot) is applied on top, so placement is completely free.
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
    /** User-visible name in the layers panel (empty = widget name). */
    val name: String = "",
    /** Degrees, clockwise, around the pivot. */
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    /** Pivot for rotation/scale as a fraction of the node size. */
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    /** Locked nodes can't be moved or resized in Edit Mode. */
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val layer: NodeLayer = NodeLayer.NORMAL,
    /** Nodes sharing a group id move together in Edit Mode. */
    val groupId: String? = null,
    /** Entrance / idle / tap animations and parallax (null = none). */
    val animation: NodeAnimations? = null,
)

/** Kept for readability in new code: every placed element is a node. */
typealias Node = WidgetInstance

@Serializable
sealed interface Wallpaper {
    /** Show the system wallpaper through the transparent launcher window. */
    @Serializable @SerialName("system")
    data object System : Wallpaper

    @Serializable @SerialName("color")
    data class SolidColor(val color: Long) : Wallpaper

    @Serializable @SerialName("gradient")
    data class Gradient(val top: Long, val bottom: Long) : Wallpaper

    /**
     * Image copied into app storage (absolute file path), cropped to fill the screen.
     * [focusX]/[focusY] (0..1) is the point kept visible when the crop cuts the image.
     */
    @Serializable @SerialName("image")
    data class Image(val path: String, val dim: Float = 0f, val focusX: Float = 0.5f, val focusY: Float = 0.5f) : Wallpaper
}

@Serializable
enum class PageTransition { SLIDE, CUBE, FADE, ZOOM, STACK }

@Serializable
data class Page(
    val id: String = newId(),
    /** Null = use the activity wallpaper. */
    val wallpaper: Wallpaper? = null,
    val transition: PageTransition = PageTransition.SLIDE,
    /** The page's nodes (widgets, icons, stickers, objects). */
    val widgets: List<WidgetInstance> = emptyList(),
    /** Desktop-like page: pan with one finger, pinch to zoom, no page bounds. */
    val infinite: Boolean = false,
    /** Optional per-page grid overlay; null = use the global grid settings. */
    val grid: GridConfig? = null,
    val name: String = "",
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
) {
    /** Spacers alone don't count: a panel with nothing else is hidden outside Edit Mode. */
    val hasContent: Boolean get() = items.any { it !is PanelItem.Spacer }
}

/** A KDE-style "Activity": a complete, independent home setup. */
@Serializable
data class ActivityProfile(
    val id: String = newId(),
    val name: String,
    val wallpaper: Wallpaper = Wallpaper.System,
    val pages: List<Page> = listOf(Page()),
    val panels: List<Panel> = emptyList(),
    val defaultPage: Int = 0,
    /** Nodes pinned across all pages: fixed on screen while pages swipe underneath. */
    val pinned: List<WidgetInstance> = emptyList(),
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

/** Optional placement aids. Placement is free by default; the grid is only an overlay. */
@Serializable
data class GridConfig(
    val snapEnabled: Boolean = false,
    /** Snap step in dp; small values give "fine" snapping. */
    val snapDp: Float = 8f,
    val showGrid: Boolean = true,
    /** Smart guides: snap to page centre/edges and other nodes while dragging. */
    val guides: Boolean = true,
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

/** Battery / performance safeguards for animations and physics. */
@Serializable
data class PerformanceConfig(
    /** Keep idle animations and physics running in battery saver / below 15% battery. */
    val ignoreBatterySaver: Boolean = false,
    /** Maximum simultaneously simulated dynamic physics bodies per page. */
    val maxDynamicBodies: Int = 30,
    /** Shows FPS, active objects and physics step time on the home screen. */
    val showOverlay: Boolean = false,
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
    val performance: PerformanceConfig = PerformanceConfig(),
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
            ?: activities.asSequence().flatMap { it.pinned }.firstOrNull { it.id == instanceId }
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
                pinned = act.pinned.map { if (it.id == instanceId) transform(it) else it },
            )
        })

    /** Removes a node from wherever it lives (pages, pinned, panels). */
    fun removeWidget(instanceId: String): LayoutProfile =
        copy(activities = activities.map { act ->
            act.copy(
                pages = act.pages.map { page -> page.copy(widgets = page.widgets.filterNot { it.id == instanceId }) },
                panels = act.panels.map { panel ->
                    panel.copy(items = panel.items.filterNot { it is PanelItem.Widget && it.widget.id == instanceId })
                },
                pinned = act.pinned.filterNot { it.id == instanceId },
            )
        })

    companion object {
        const val CURRENT_FORMAT = 1
    }
}
