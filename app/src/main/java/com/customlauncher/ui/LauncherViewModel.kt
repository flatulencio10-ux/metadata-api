package com.customlauncher.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.customlauncher.container
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.ActivityProfile
import com.customlauncher.core.model.AppFolder
import com.customlauncher.core.model.AppOverride
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Page
import com.customlauncher.core.model.PanelItem
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.core.model.WidgetStyle
import com.customlauncher.data.apps.AppInfo
import com.customlauncher.data.apps.AppRepository
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.WidgetRegistry
import com.customlauncher.widgets.apps.AppIconWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Home screen state holder. Persistent state lives in the layout profile (via
 * ProfileRepository); this class only adds transient UI state (open overlays, selection).
 */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val profiles = c.profileRepository

    val profile: StateFlow<LayoutProfile?> = profiles.profile

    /** Installed apps with renames / hidden flags / custom icons applied. */
    val apps: StateFlow<List<AppInfo>> = combine(
        c.appRepository.rawApps,
        profiles.profile.filterNotNull().map { it.appOverrides }.distinctUntilChanged(),
    ) { raw, overrides -> AppRepository.applyOverrides(raw, overrides) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Null while loading, so the onboarding screen doesn't flash. */
    val onboardingDone: StateFlow<Boolean?> = c.prefs.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Transient UI state --------------------------------------------------------
    var editMode by mutableStateOf(false)
    var selectedWidgetId by mutableStateOf<String?>(null)
    var drawerOpen by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var gestureCanvasOpen by mutableStateOf(false)
    var fullscreenWidgetId by mutableStateOf<String?>(null)
    var configuringWidgetId by mutableStateOf<String?>(null)
    var addWidgetSheetOpen by mutableStateOf(false)
    var pageSettingsOpen by mutableStateOf(false)
    var currentPage by mutableStateOf(0)

    /** Live style preview while an inspector slider is dragged (committed on release). */
    var stylePreview by mutableStateOf<Pair<String, WidgetStyle>?>(null)

    /** Panel that the "+ add to panel" hint (Edit Mode) is adding apps to. */
    var panelAddTargetId by mutableStateOf<String?>(null)

    /** Incremented when Home is pressed while the launcher is already showing. */
    var homePressTick by mutableStateOf(0)

    /** Size of the page content area, used to place new widgets and clamp drags. */
    var pageSizeDp by mutableStateOf(DpSize(360.dp, 640.dp))

    /** Bounds (in root px) of widgets on the visible page; used to pick a widget on long press. */
    val widgetBounds = HashMap<String, Rect>()

    fun run(action: Action) = c.actionExecutor.run(action)

    fun edit(transform: (LayoutProfile) -> LayoutProfile) = profiles.edit(transform)

    fun closeOverlays(): Boolean {
        val anyOpen = drawerOpen || searchOpen || gestureCanvasOpen || fullscreenWidgetId != null ||
            configuringWidgetId != null || addWidgetSheetOpen || pageSettingsOpen
        drawerOpen = false; searchOpen = false; gestureCanvasOpen = false
        fullscreenWidgetId = null; configuringWidgetId = null; addWidgetSheetOpen = false; pageSettingsOpen = false
        return anyOpen
    }

    fun enterEditMode(rootPosition: Offset? = null) {
        closeOverlays()
        editMode = true
        val pageWidgets = profile.value?.currentActivity?.pages?.getOrNull(currentPage)
            ?.widgets?.sortedByDescending { it.z }?.map { it.id }.orEmpty()
        selectedWidgetId = rootPosition?.let { p -> pageWidgets.firstOrNull { widgetBounds[it]?.contains(p) == true } }
    }

    /** Home pressed while visible: close what is open, otherwise go to the default page. */
    fun onHomePressed() {
        val closed = closeOverlays()
        if (editMode) {
            exitEditMode()
        } else if (!closed) {
            homePressTick++
        }
    }

    fun commitStylePreview() {
        val (id, style) = stylePreview ?: return
        updateWidget(id) { it.copy(style = style) }
        stylePreview = null
    }

    fun exitEditMode() {
        editMode = false
        selectedWidgetId = null
    }

    fun completeOnboarding() = viewModelScope.launch { c.prefs.setOnboardingDone(true) }

    // ---- Edit Mode: widgets --------------------------------------------------------

    /** Rounds to the snap grid when snapping is enabled. */
    fun snap(value: Float): Float {
        val grid = profile.value?.grid ?: return value
        if (!grid.snapEnabled || grid.snapDp <= 0f) return value
        return (value / grid.snapDp).roundToInt() * grid.snapDp
    }

    private fun editPages(transform: (List<Page>) -> List<Page>) =
        edit { p -> p.updateCurrentActivity { a -> a.copy(pages = transform(a.pages)) } }

    private fun editPage(index: Int, transform: (Page) -> Page) =
        editPages { pages -> pages.mapIndexed { i, page -> if (i == index) transform(page) else page } }

    fun addWidget(type: String, pageIndex: Int = currentPage, config: WidgetConfig = WidgetConfig()) {
        val def = WidgetRegistry.find(type) ?: return
        val w = def.defaultSize.width.value.coerceAtMost(pageSizeDp.width.value)
        val h = def.defaultSize.height.value.coerceAtMost(pageSizeDp.height.value)
        val page = profile.value?.currentActivity?.pages?.getOrNull(pageIndex)
        val instance = WidgetInstance(
            type = type,
            x = snap(((pageSizeDp.width.value - w) / 2f).coerceAtLeast(0f)),
            y = snap(pageSizeDp.height.value * 0.25f),
            width = w,
            height = h,
            z = (page?.widgets?.maxOfOrNull { it.z } ?: 0) + 1,
            style = if (type == AppIconWidget.id) WidgetStyle(useCardStyle = false, backgroundColor = 0x00000000, paddingDp = 2f) else WidgetStyle(),
            config = config.json,
        )
        editPage(pageIndex) { it.copy(widgets = it.widgets + instance) }
        def.onAdded(getApplication(), config)
        selectedWidgetId = instance.id
    }

    fun addAppToHome(appKey: String) {
        addWidget(AppIconWidget.id, currentPage, AppIconWidget.configFor(appKey))
        drawerOpen = false
        editMode = true
    }

    fun updateWidget(id: String, transform: (WidgetInstance) -> WidgetInstance) =
        edit { it.updateWidget(id, transform) }

    fun updateWidgetConfig(id: String, config: WidgetConfig) = updateWidget(id) { it.copy(config = config.json) }

    fun moveResizeWidget(id: String, x: Float, y: Float, w: Float, h: Float) = updateWidget(id) {
        it.copy(x = x, y = y, width = w, height = h)
    }

    fun bringToFront(id: String) = editPages { pages ->
        pages.map { page ->
            if (page.widgets.none { it.id == id }) page
            else page.copy(widgets = page.widgets.map { if (it.id == id) it.copy(z = page.widgets.maxOf { w -> w.z } + 1) else it })
        }
    }

    fun sendToBack(id: String) = editPages { pages ->
        pages.map { page ->
            if (page.widgets.none { it.id == id }) page
            else page.copy(widgets = page.widgets.map { if (it.id == id) it.copy(z = page.widgets.minOf { w -> w.z } - 1) else it })
        }
    }

    fun duplicateWidget(id: String) = editPages { pages ->
        pages.map { page ->
            val src = page.widgets.firstOrNull { it.id == id } ?: return@map page
            page.copy(widgets = page.widgets + src.copy(id = com.customlauncher.core.model.newId(), x = src.x + 16, y = src.y + 16, z = src.z + 1))
        }
    }

    fun deleteWidget(id: String) {
        edit { p ->
            p.copy(activities = p.activities.map { act ->
                act.copy(
                    pages = act.pages.map { page -> page.copy(widgets = page.widgets.filterNot { it.id == id }) },
                    panels = act.panels.map { panel ->
                        panel.copy(items = panel.items.filterNot { it is PanelItem.Widget && it.widget.id == id })
                    },
                )
            })
        }
        if (selectedWidgetId == id) selectedWidgetId = null
    }

    /** Moves a widget to the page [delta] away, creating a new page past the last one. */
    fun moveWidgetToPage(id: String, delta: Int) = editPages { pages ->
        val from = pages.indexOfFirst { p -> p.widgets.any { it.id == id } }
        if (from < 0) return@editPages pages
        val to = (from + delta).coerceAtLeast(0)
        val widget = pages[from].widgets.first { it.id == id }
        val extended = if (to >= pages.size) pages + Page(transition = pages.last().transition) else pages
        extended.mapIndexed { i, page ->
            when (i) {
                from -> page.copy(widgets = page.widgets.filterNot { it.id == id })
                to -> page.copy(widgets = page.widgets + widget)
                else -> page
            }
        }
    }

    // ---- Edit Mode: pages ----------------------------------------------------------

    fun addPage() = editPages { it + Page(transition = it.lastOrNull()?.transition ?: com.customlauncher.core.model.PageTransition.SLIDE) }

    fun removePage(index: Int) = editPages { pages -> if (pages.size <= 1) pages else pages.filterIndexed { i, _ -> i != index } }

    fun updatePage(index: Int, transform: (Page) -> Page) = editPage(index, transform)

    fun movePage(index: Int, delta: Int) = editPages { pages ->
        val target = index + delta
        if (target !in pages.indices) pages
        else pages.toMutableList().also { val p = it.removeAt(index); it.add(target, p) }
    }

    fun updateActivity(transform: (ActivityProfile) -> ActivityProfile) = edit { it.updateCurrentActivity(transform) }

    // ---- Drawer customisation ------------------------------------------------------

    fun updateOverride(key: String, transform: (AppOverride) -> AppOverride) = edit { p ->
        p.copy(appOverrides = p.appOverrides + (key to transform(p.appOverrides[key] ?: AppOverride())))
    }

    fun upsertFolder(folder: AppFolder) = edit { p ->
        p.copy(folders = if (p.folders.any { it.id == folder.id }) p.folders.map { if (it.id == folder.id) folder else it } else p.folders + folder)
    }

    fun deleteFolder(id: String) = edit { p -> p.copy(folders = p.folders.filterNot { it.id == id }) }

    /** Adds an app to the first panel of the current activity (creating a dock if none). */
    fun addAppToPanel(appKey: String) = updateActivity { act ->
        val item = PanelItem.App(component = appKey)
        if (act.panels.isEmpty()) act.copy(panels = listOf(com.customlauncher.core.model.Panel(items = listOf(item))))
        else act.copy(panels = act.panels.mapIndexed { i, p -> if (i == 0) p.copy(items = p.items + item) else p })
    }

    fun addAppsToPanel(panelId: String, appKeys: List<String>) = updateActivity { act ->
        act.copy(panels = act.panels.map { p ->
            if (p.id != panelId) p else p.copy(items = p.items + appKeys.map { PanelItem.App(component = it) })
        })
    }

    /** Copies a picked image into app storage and uses it as the app's icon. */
    fun setCustomIcon(appKey: String, uri: Uri) = viewModelScope.launch {
        val path = copyToStorage(uri, "icons") ?: return@launch
        c.iconLoader.clear()
        updateOverride(appKey) { it.copy(iconPath = path) }
    }

    /** Copies a picked image into app storage (wallpapers, icons) and returns its path. */
    suspend fun copyToStorage(uri: Uri, dir: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(getApplication<Application>().filesDir, dir).apply { mkdirs() }
            val file = File(folder, "${System.currentTimeMillis()}.img")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }
}
