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
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.NodeLayer
import com.customlauncher.data.stickers.StickerAsset
import com.customlauncher.ui.edit.Guide
import com.customlauncher.ui.edit.NodeToolbarAction
import com.customlauncher.widgets.sticker.StickerEffects
import com.customlauncher.widgets.sticker.StickerWidget
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

    /** Live node preview while an inspector slider is dragged (committed on release). */
    var nodePreview by mutableStateOf<Node?>(null)

    /** Keyframe timeline preview: node id → pose shown on the canvas while scrubbing / recording. */
    var clipScrub by mutableStateOf<Pair<String, com.customlauncher.ui.anim.ActivePose>?>(null)

    /** Node id + clip name being recorded by dragging on the page. */
    var clipRecording by mutableStateOf<Pair<String, String>?>(null)

    /** Panel that the "+ add to panel" hint (Edit Mode) is adding apps to. */
    var panelAddTargetId by mutableStateOf<String?>(null)

    /** Incremented when Home is pressed while the launcher is already showing. */
    var homePressTick by mutableStateOf(0)

    /** Size of the page content area, used to place new widgets and clamp drags. */
    var pageSizeDp by mutableStateOf(DpSize(360.dp, 640.dp))

    private val viewports = HashMap<String, com.customlauncher.ui.home.CanvasViewport>()

    /** Pan/zoom of an infinite canvas page (survives recomposition, not restarts). */
    fun viewport(pageId: String) = viewports.getOrPut(pageId) { com.customlauncher.ui.home.CanvasViewport() }

    var layersPanelOpen by mutableStateOf(false)

    /** Smart guide lines currently shown while dragging in Edit Mode. */
    var guides by mutableStateOf<List<Guide>>(emptyList())

    /** Bounds (in root px) of widgets on the visible page; used to pick a widget on long press. */
    val widgetBounds = HashMap<String, Rect>()

    fun run(action: Action) = c.actionExecutor.run(action)

    fun edit(transform: (LayoutProfile) -> LayoutProfile) = profiles.edit(transform)

    fun closeOverlays(): Boolean {
        val anyOpen = drawerOpen || searchOpen || gestureCanvasOpen || fullscreenWidgetId != null ||
            configuringWidgetId != null || addWidgetSheetOpen || pageSettingsOpen || startMenuOpen || contextMenuAt != null
        drawerOpen = false; searchOpen = false; gestureCanvasOpen = false; startMenuOpen = false; contextMenuAt = null
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

    fun commitNodePreview() {
        val n = nodePreview ?: return
        updateWidget(n.id) { n }
        nodePreview = null
    }

    fun exitEditMode() {
        editMode = false
        selectedWidgetId = null
        clipScrub = null
        clipRecording = null
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

    fun bringToFront(id: String) = editNodeLists { list ->
        if (list.none { it.id == id }) list else list.map { if (it.id == id) it.copy(z = list.maxOf { w -> w.z } + 1) else it }
    }

    fun sendToBack(id: String) = editNodeLists { list ->
        if (list.none { it.id == id }) list else list.map { if (it.id == id) it.copy(z = list.minOf { w -> w.z } - 1) else it }
    }

    /** Duplicates with everything (config, transform, behaviours), offset a little. */
    fun duplicateWidget(id: String) = editNodeLists { list ->
        val src = list.firstOrNull { it.id == id } ?: return@editNodeLists list
        list + src.copy(id = com.customlauncher.core.model.newId(), x = src.x + 16, y = src.y + 16, z = list.maxOf { it.z } + 1, groupId = null)
    }

    fun deleteWidget(id: String) {
        edit { it.removeWidget(id) }
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

    // ---- Edit Mode: free transform ---------------------------------------------------

    /**
     * Applies [transform] to every node list of every activity: each page's nodes and the
     * pinned list. Node ops below use it so they work wherever the node lives.
     */
    private fun editNodeLists(transform: (List<Node>) -> List<Node>) = edit { p ->
        p.copy(activities = p.activities.map { a ->
            a.copy(pages = a.pages.map { it.copy(widgets = transform(it.widgets)) }, pinned = transform(a.pinned))
        })
    }

    /** Commits a transform from Edit Mode; group members move by the same amount. */
    fun commitNode(old: Node, new: Node) {
        val dx = new.x - old.x
        val dy = new.y - old.y
        val group = old.groupId
        editNodeLists { list ->
            list.map {
                when {
                    it.id == new.id -> new
                    group != null && it.groupId == group && !it.locked -> it.copy(x = it.x + dx, y = it.y + dy)
                    else -> it
                }
            }
        }
    }

    fun onNodeToolbar(action: NodeToolbarAction, node: Node) {
        when (action) {
            NodeToolbarAction.FLIP_H -> updateWidget(node.id) { it.copy(flipX = !it.flipX) }
            NodeToolbarAction.FLIP_V -> updateWidget(node.id) { it.copy(flipY = !it.flipY) }
            NodeToolbarAction.DUPLICATE -> duplicateWidget(node.id)
            NodeToolbarAction.COPY -> copyNode(node.id)
            NodeToolbarAction.FRONT -> bringToFront(node.id)
            NodeToolbarAction.BACK -> sendToBack(node.id)
            NodeToolbarAction.LOCK -> updateWidget(node.id) { it.copy(locked = !it.locked) }
            NodeToolbarAction.LAYER -> updateWidget(node.id) {
                it.copy(layer = if (it.layer == NodeLayer.ABOVE_PANELS) NodeLayer.NORMAL else NodeLayer.ABOVE_PANELS)
            }
            NodeToolbarAction.PIN -> togglePinned(node.id)
            NodeToolbarAction.EDIT -> if (node.type == StickerWidget.id) stickerEditorTarget = node.id else configuringWidgetId = node.id
            NodeToolbarAction.DELETE -> deleteWidget(node.id)
        }
    }

    /** Moves a node between the current page and the activity's "pinned across all pages" layer. */
    fun togglePinned(id: String) = updateActivity { act ->
        val pinned = act.pinned.firstOrNull { it.id == id }
        if (pinned != null) {
            val page = currentPage.coerceIn(0, act.pages.lastIndex)
            act.copy(
                pinned = act.pinned - pinned,
                pages = act.pages.mapIndexed { i, p -> if (i == page) p.copy(widgets = p.widgets + pinned) else p },
            )
        } else {
            val node = act.pages.asSequence().flatMap { it.widgets }.firstOrNull { it.id == id } ?: return@updateActivity act
            act.copy(
                pinned = act.pinned + node,
                pages = act.pages.map { p -> p.copy(widgets = p.widgets.filterNot { it.id == id }) },
            )
        }
    }

    // ---- Interactive objects ---------------------------------------------------------

    /** Receives object gestures and physics events (behaviours hook in here). */
    val objectEvents = com.customlauncher.objects.ObjectEventSink { event -> onObjectEvent(event) }

    private fun onObjectEvent(event: com.customlauncher.objects.ObjectEvent) {
        objectEventListener?.invoke(event)
    }

    /** Set by the behaviour engine of the visible page. */
    var objectEventListener: ((com.customlauncher.objects.ObjectEvent) -> Unit)? = null

    /** Extensions (scripting) attached to each page's behaviour session. */
    val sessionExtensions = listOf<com.customlauncher.objects.SessionExtension>(com.customlauncher.scripting.ScriptExtension(c))

    /** Bumped on "Save & reload" so page sessions restart even if the text is unchanged. */
    var scriptGeneration by androidx.compose.runtime.mutableIntStateOf(0)

    /** Scripted nodes take touches in the object layer so their hooks fire. */
    fun isScripted(n: Node): Boolean = !n.script.isNullOrBlank()

    fun pinnedNodes(): List<Node> = profile.value?.currentActivity?.pinned.orEmpty()

    /** A follow-finger drag ended: keep the object where it was dropped. */
    fun commitRuntimeOffset(id: String) {
        val rt = c.runtimes.peek(id) ?: return
        val dx = rt.dx
        val dy = rt.dy
        if (dx == 0f && dy == 0f) return
        rt.dx = 0f; rt.dy = 0f
        updateWidget(id) { it.copy(x = it.x + dx, y = it.y + dy) }
    }

    /** Writes resting physics poses back into the layout (only nodes that actually moved). */
    fun commitPhysicsPoses(poses: Map<String, Triple<Float, Float, Float>>) {
        val p = profile.value ?: return
        val moved = poses.filter { (id, pose) ->
            val n = p.findWidget(id) ?: return@filter false
            kotlin.math.abs(n.x - pose.first) > 0.5f || kotlin.math.abs(n.y - pose.second) > 0.5f ||
                kotlin.math.abs(n.rotation - pose.third) > 0.5f
        }
        if (moved.isEmpty()) return
        editNodeLists { list ->
            list.map { n -> moved[n.id]?.let { (x, y, r) -> n.copy(x = x, y = y, rotation = r) } ?: n }
        }
    }

    // ---- Object presets ---------------------------------------------------------------

    /** Adds a preset object to the centre of the current page. */
    fun addPreset(preset: com.customlauncher.objects.ObjectPreset, pageIndex: Int = currentPage) {
        val page = profile.value?.currentActivity?.pages?.getOrNull(pageIndex)
        val node = com.customlauncher.objects.Presets.node(
            preset, pageSizeDp.width.value / 2f, pageSizeDp.height.value * 0.4f, (page?.widgets?.maxOfOrNull { it.z } ?: 0) + 1,
        )
        editPage(pageIndex) { it.copy(widgets = it.widgets + node) }
        selectedWidgetId = node.id
    }

    /** "Spawn" behaviour action: drops a preset object just below [nearId] on the same page. */
    fun spawnPreset(presetId: String, nearId: String) {
        val preset = com.customlauncher.objects.Presets.find(presetId) ?: return
        editPages { pages ->
            pages.map { page ->
                val near = page.widgets.firstOrNull { it.id == nearId } ?: return@map page
                val count = page.widgets.count { it.physics?.type == com.customlauncher.core.model.BodyType.DYNAMIC }
                if (count >= (profile.value?.performance?.maxDynamicBodies ?: 30)) return@map page
                page.copy(widgets = page.widgets + com.customlauncher.objects.Presets.node(
                    preset.copy(color = listOf(0xFFF67400, 0xFFDA4453, 0xFF27AE60, 0xFF1D99F3, 0xFFFDBC4B).random()),
                    near.x + near.width / 2f, near.y + near.height + preset.height, page.widgets.maxOf { it.z } + 1,
                ))
            }
        }
    }

    fun applyTemplate(id: String, preset: com.customlauncher.objects.ObjectPreset) =
        updateWidget(id) { com.customlauncher.objects.Presets.applyTo(it, preset) }

    /** Adds a physics sandbox page (tilt gravity, balls, a spinner and a spawn button). */
    fun addSandboxPage() = editPages {
        it + com.customlauncher.objects.Presets.sandboxPage(pageSizeDp.width.value, pageSizeDp.height.value)
    }

    // ---- Layers panel ---------------------------------------------------------------

    fun renameNode(id: String, name: String) = updateWidget(id) { it.copy(name = name.trim()) }
    fun toggleHidden(id: String) = updateWidget(id) { it.copy(hidden = !it.hidden) }
    fun toggleLocked(id: String) = updateWidget(id) { it.copy(locked = !it.locked) }

    /** Moves a node one step up (+1) or down (-1) in its list's stacking order. */
    fun moveZ(id: String, delta: Int) = editNodeLists { list ->
        if (list.none { it.id == id }) return@editNodeLists list
        val ordered = list.sortedBy { it.z }.toMutableList()
        val i = ordered.indexOfFirst { it.id == id }
        val j = (i + delta).coerceIn(0, ordered.lastIndex)
        if (i == j) return@editNodeLists list
        ordered.add(j, ordered.removeAt(i))
        // Re-number z densely so later moves stay predictable.
        val newZ = ordered.mapIndexed { index, n -> n.id to index }.toMap()
        list.map { it.copy(z = newZ.getValue(it.id)) }
    }

    /** Groups nodes: they then move together in Edit Mode. */
    fun group(ids: Set<String>) {
        val gid = com.customlauncher.core.model.newId()
        editNodeLists { list -> list.map { if (it.id in ids) it.copy(groupId = gid) else it } }
    }

    fun ungroup(ids: Set<String>) {
        val groups = profile.value?.let { p -> ids.mapNotNull { p.findWidget(it)?.groupId }.toSet() }.orEmpty()
        editNodeLists { list -> list.map { if (it.id in ids || it.groupId in groups) it.copy(groupId = null) else it } }
    }

    // ---- Stickers ------------------------------------------------------------------

    /** Node whose sticker is open in the sticker editor. */
    var stickerEditorTarget by mutableStateOf<String?>(null)
    var stickerSourceSheetOpen by mutableStateOf(false)

    /** Imports a file (gallery, files, clipboard, share) and places it on the current page. */
    fun importSticker(uri: Uri) = viewModelScope.launch {
        runCatching { c.stickerRepository.import(uri) }
            .onSuccess { addSticker(it) }
            .onFailure { c.bus.send(com.customlauncher.actions.UiCommand.Message(it.message ?: "Import failed")) }
    }

    /** Places a library sticker centred on the current page, longest side 160 dp. */
    fun addSticker(asset: StickerAsset, pageIndex: Int = currentPage) {
        val aspect = if (asset.height > 0) asset.width.toFloat() / asset.height else 1f
        val (w, h) = if (aspect >= 1f) 160f to 160f / aspect else 160f * aspect to 160f
        val page = profile.value?.currentActivity?.pages?.getOrNull(pageIndex)
        val node = WidgetInstance(
            type = StickerWidget.id,
            x = (pageSizeDp.width.value - w) / 2f,
            y = (pageSizeDp.height.value - h) / 2f,
            width = w, height = h,
            z = (page?.widgets?.maxOfOrNull { it.z } ?: 0) + 1,
            style = WidgetStyle(useCardStyle = false, backgroundColor = 0x00000000, paddingDp = 0f),
            config = StickerWidget.configFor(asset).json,
            name = asset.name,
        )
        editPage(pageIndex) { it.copy(widgets = it.widgets + node) }
        editMode = true
        selectedWidgetId = node.id
    }

    /**
     * Saves the sticker editor's result: new pixels (if edited) become a new library asset
     * and the node keeps its height while adopting the new aspect ratio.
     */
    fun saveStickerEdit(nodeId: String, png: ByteArray?, fx: StickerEffects, opacity: Float, animations: com.customlauncher.core.model.NodeAnimations) = viewModelScope.launch {
        val asset = png?.let { runCatching { c.stickerRepository.saveEdited(it, null) }.getOrNull() }
        updateWidget(nodeId) { n ->
            var config = WidgetConfig(n.config)
            var width = n.width
            if (asset != null) {
                config = config.with(StickerWidget.FILE, asset.file).with(StickerWidget.KIND, asset.kind.name)
                if (asset.height > 0) width = n.height * asset.width / asset.height
            }
            config = StickerWidget.withEffects(config, fx)
            n.copy(config = config.json, width = width, style = n.style.copy(opacity = opacity), animation = animations.takeIf { !it.isEmpty })
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

    // ---- Desktop: start menu, context menu, clipboard, windows, taskbar ---------------

    var startMenuOpen by mutableStateOf(false)

    /** Page area origin in root pixels (to place pasted nodes under the finger). */
    var pageOriginPx by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)

    /** Tab the add sheet opens on (0 widgets, 1 stickers, 2 objects). */
    var addWidgetSheetTab by mutableStateOf(0)

    /** Root position of the home long-press context menu (null = closed). */
    var contextMenuAt by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)

    /** Copied nodes (with their group), pasteable on any page. */
    var nodeClipboard by mutableStateOf<List<Node>>(emptyList())

    /** When true the add-widget sheet opens the widget in a floating window. */
    var addWidgetAsWindow by mutableStateOf(false)

    /** Long-press on the home screen: on an object → Edit Mode with it selected; elsewhere → context menu. */
    fun onHomeLongPress(rootPosition: androidx.compose.ui.geometry.Offset) {
        if (editMode) return
        val pageWidgets = profile.value?.currentActivity?.pages?.getOrNull(currentPage)?.widgets.orEmpty()
        if (pageWidgets.any { widgetBounds[it.id]?.contains(rootPosition) == true }) enterEditMode(rootPosition)
        else contextMenuAt = rootPosition
    }

    fun copyNode(id: String) {
        val act = profile.value?.currentActivity ?: return
        val all = act.pages.flatMap { it.widgets } + act.pinned
        val node = all.firstOrNull { it.id == id } ?: return
        nodeClipboard = node.groupId?.let { g -> all.filter { it.groupId == g } } ?: listOf(node)
        c.bus.send(com.customlauncher.actions.UiCommand.Message(if (nodeClipboard.size > 1) "Copied ${nodeClipboard.size} objects" else "Copied — paste from the long-press menu on any page"))
    }

    /** Pastes the clipboard on the current page at [atDp] (page coordinates), or offset from the originals. */
    fun pasteNodes(atDp: androidx.compose.ui.geometry.Offset? = null) {
        val clip = nodeClipboard.ifEmpty { return }
        val page = profile.value?.currentActivity?.pages?.getOrNull(currentPage) ?: return
        val x = atDp?.x ?: (clip.minOf { it.x } + 24f)
        val y = atDp?.y ?: (clip.minOf { it.y } + 24f)
        val pasted = com.customlauncher.desktop.NodeClipboard.paste(clip, x, y, page.widgets.maxOfOrNull { it.z } ?: 0)
        editPage(currentPage) { it.copy(widgets = it.widgets + pasted) }
        selectedWidgetId = pasted.last().id
    }

    fun openWidgetWindow(type: String) {
        val def = WidgetRegistry.find(type) ?: return
        val area = pageSizeDp
        updateActivity { act ->
            val base = com.customlauncher.desktop.WindowMath.cascade(act.windows, def.defaultSize.width.value.coerceAtLeast(220f), def.defaultSize.height.value.coerceAtLeast(160f) + com.customlauncher.desktop.WindowMath.TITLE_H, area.width.value, area.height.value)
            act.copy(windows = act.windows + base.copy(widget = WidgetInstance(type = type, width = base.width, height = base.height)))
        }
        def.onAdded(getApplication(), WidgetConfig())
    }

    fun updateWindow(id: String, transform: (com.customlauncher.core.model.FloatingWindow) -> com.customlauncher.core.model.FloatingWindow) =
        updateActivity { act -> act.copy(windows = act.windows.map { if (it.id == id) transform(it) else it }) }

    fun closeWindow(id: String) = updateActivity { act -> act.copy(windows = act.windows.filterNot { it.id == id }) }

    /** Pins / unpins an app on every taskbar of the current activity. */
    fun toggleTaskbarPin(component: String) = updateActivity { act ->
        act.copy(panels = act.panels.map { p ->
            p.copy(items = p.items.map { item ->
                if (item is PanelItem.Taskbar) item.copy(pinned = if (component in item.pinned) item.pinned - component else item.pinned + component) else item
            })
        })
    }

    /** Opens an app in a freeform window when supported, else normally. */
    fun openAppInWindow(component: String) {
        val ctx = getApplication<android.app.Application>()
        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels * 0.8f).toInt()
        val h = (dm.heightPixels * 0.55f).toInt()
        val left = (dm.widthPixels - w) / 2
        val top = (dm.heightPixels - h) / 3
        val windowed = com.customlauncher.desktop.FreeformLauncher.launch(ctx, component, android.graphics.Rect(left, top, left + w, top + h))
        if (!windowed) c.bus.send(com.customlauncher.actions.UiCommand.Message("Free-form windows aren't enabled on this device — opened normally"))
    }

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
