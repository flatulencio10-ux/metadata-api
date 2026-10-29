package com.customlauncher.ui.home

import com.customlauncher.ui.desktop.StartMenu
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.Edge
import com.customlauncher.core.model.NodeLayer
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.gestures.GestureCallbacks
import com.customlauncher.gestures.launcherGestures
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.anim.LocalPageOffset
import kotlin.math.abs

/**
 * The home screen of the current activity: wallpaper, paged widgets, edge panels, and the
 * gesture engine wrapped around all of it.
 */
@Composable
fun HomeScreen(profile: LayoutProfile, vm: LauncherViewModel, pagerState: PagerState, resumed: Boolean) {
    val activity = profile.currentActivity
    val panels = activity.panels
    val scope = rememberCoroutineScope()
    val currentProfile by rememberUpdatedState(profile)

    val callbacks = remember(vm) {
        GestureCallbacks(
            onTrigger = { trigger -> vm.run(currentProfile.gestures.actionFor(trigger)) },
            onLongPress = { pos -> vm.onHomeLongPress(pos) },
            onTap = { if (vm.editMode) vm.selectedWidgetId = null },
        )
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.currentPage = it }
    }

    // Empty panels (only spacers) are hidden outside Edit Mode and take no space.
    val visiblePanels = if (vm.editMode) panels else panels.filter { it.hasContent }
    val reserved = reservedSpace(visiblePanels)
    val overlayOpen = vm.drawerOpen || vm.searchOpen || vm.fullscreenWidgetId != null

    CompositionLocalProvider(com.customlauncher.ui.desktop.LocalDesktop provides vm) {
    Box(
        Modifier
            .fillMaxSize()
            .launcherGestures(profile.gestures, vm.editMode, activity.pages.size == 1, callbacks)
    ) {
        // Wallpapers fill the whole screen (behind status and navigation bars). Pages with
        // their own wallpaper cross-fade over the activity wallpaper while swiping.
        WallpaperView(activity.wallpaper)
        val current = pagerState.currentPage
        for (i in (current - 1)..(current + 1)) {
            val own = activity.pages.getOrNull(i)?.wallpaper ?: continue
            key("wallpaper-${activity.pages[i].id}") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val distance = (pagerState.currentPage - i) + pagerState.currentPageOffsetFraction
                            alpha = (1f - abs(distance)).coerceIn(0f, 1f)
                        }
                ) { WallpaperView(own) }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = reserved.getValue(Edge.LEFT),
                        top = reserved.getValue(Edge.TOP),
                        end = reserved.getValue(Edge.RIGHT),
                        bottom = reserved.getValue(Edge.BOTTOM),
                    )
            ) {
                val pageSize = DpSize(maxWidth, maxHeight)
                LaunchedEffect(pageSize) { vm.pageSizeDp = pageSize }
                Box(Modifier.fillMaxSize().onGloballyPositioned { vm.pageOriginPx = it.positionInRoot() })

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // On an infinite canvas page one-finger drags pan the canvas instead of paging;
                    // use the page dots to change pages.
                    userScrollEnabled = activity.pages.size > 1 &&
                        activity.pages.getOrNull(pagerState.currentPage)?.infinite != true,
                    beyondViewportPageCount = 1,
                    key = { index -> activity.pages.getOrNull(index)?.id ?: index },
                ) { index ->
                    val page = activity.pages.getOrNull(index) ?: return@HorizontalPager
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pageTransition(page.transition) {
                                (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                            }
                    ) {
                        CompositionLocalProvider(
                            LocalPageOffset provides { (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction }
                        ) {
                        PageView(
                            page = page,
                            activityWallpaper = activity.wallpaper,
                            isActive = resumed && !overlayOpen && pagerState.settledPage == index,
                            editMode = vm.editMode,
                            grid = profile.grid,
                            vm = vm,
                        )
                        }
                    }
                }

                // Pinned nodes stay fixed on screen while pages swipe underneath.
                NodeList(
                    nodes = activity.pinned.filter { it.layer == NodeLayer.NORMAL },
                    allOnPage = activity.pinned + (activity.pages.getOrNull(pagerState.currentPage)?.widgets.orEmpty()),
                    wallpaper = activity.wallpaper,
                    isActive = resumed && !overlayOpen,
                    editMode = vm.editMode,
                    grid = profile.grid,
                    vm = vm,
                    pinned = true,
                )

                // Desktop-style widget windows float over the pages (hidden while editing).
                if (!vm.editMode && activity.windows.isNotEmpty()) {
                    com.customlauncher.ui.desktop.FloatingWindows(activity.windows, activity.wallpaper, pageSize, resumed && !overlayOpen, vm)
                }

                if (activity.pages.size > 1) {
                    PageIndicator(
                        count = activity.pages.size,
                        current = pagerState.currentPage,
                        onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                    )
                }
            }

            // Panels, stacked outward-in per edge.
            Edge.entries.forEach { edge ->
                var offset = 0.dp
                visiblePanels.filter { it.edge == edge }.forEach { panel ->
                    PanelView(
                        panel = panel,
                        wallpaper = activity.wallpaper,
                        isActive = resumed && !overlayOpen,
                        offsetAlongNormal = offset,
                        crossStart = if (edge.isHorizontal) 0.dp else reserved.getValue(Edge.TOP),
                        crossEnd = if (edge.isHorizontal) 0.dp else reserved.getValue(Edge.BOTTOM),
                        onAction = vm::run,
                        onOpenWidget = { vm.fullscreenWidgetId = it },
                        onUpdateConfig = { id, config -> vm.updateWidgetConfig(id, config) },
                        editMode = vm.editMode,
                        onAddToPanel = { vm.panelAddTargetId = panel.id },
                    )
                    if (!panel.autoHide) offset += panel.footprint()
                }
            }

            // Layer above the panels: page nodes marked "above panels" (they slide with their
            // page) and pinned nodes on that layer. Also draws the smart guides.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = reserved.getValue(Edge.LEFT),
                        top = reserved.getValue(Edge.TOP),
                        end = reserved.getValue(Edge.RIGHT),
                        bottom = reserved.getValue(Edge.BOTTOM),
                    )
            ) {
                val cur = pagerState.currentPage
                for (i in (cur - 1)..(cur + 1)) {
                    val page = activity.pages.getOrNull(i) ?: continue
                    val above = page.widgets.filter { it.layer == NodeLayer.ABOVE_PANELS }
                    if (above.isEmpty()) continue
                    key("above-${page.id}") {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = ((i - pagerState.currentPage) - pagerState.currentPageOffsetFraction) * size.width
                                }
                        ) {
                            NodeList(
                                nodes = above,
                                allOnPage = page.widgets,
                                wallpaper = page.wallpaper ?: activity.wallpaper,
                                isActive = resumed && !overlayOpen && pagerState.settledPage == i,
                                editMode = vm.editMode,
                                grid = profile.grid,
                                vm = vm,
                                pinned = false,
                            )
                        }
                    }
                }
                NodeList(
                    nodes = activity.pinned.filter { it.layer == NodeLayer.ABOVE_PANELS },
                    allOnPage = activity.pinned,
                    wallpaper = activity.wallpaper,
                    isActive = resumed && !overlayOpen,
                    editMode = vm.editMode,
                    grid = profile.grid,
                    vm = vm,
                    pinned = true,
                )
                if (vm.editMode) GuideLines(vm.guides)
            }

            StartMenu(vm, reserved.getValue(Edge.BOTTOM))
            if (profile.performance.showOverlay) {
                com.customlauncher.ui.desktop.PerformanceOverlay(
                    activity.pages.getOrNull(pagerState.currentPage)?.widgets.orEmpty() + activity.pinned,
                    Modifier.align(Alignment.TopEnd).padding(top = reserved.getValue(Edge.TOP) + 4.dp, end = reserved.getValue(Edge.RIGHT) + 4.dp),
                )
            }
        }
        com.customlauncher.ui.desktop.HomeContextMenu(vm)
    }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .clickable { onSelect(i) }
                    .padding(4.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (i == current) 0.9f else 0.4f), CircleShape)
            )
        }
    }
}

/** Space reserved on each edge by panels that are shown, not auto-hidden and reserve space. */
fun reservedSpace(panels: List<com.customlauncher.core.model.Panel>): Map<Edge, Dp> = Edge.entries.associateWith { edge ->
    panels.filter { it.edge == edge && it.reserveSpace && !it.autoHide }
        .fold(0.dp) { acc, p -> acc + p.footprint() }
}
