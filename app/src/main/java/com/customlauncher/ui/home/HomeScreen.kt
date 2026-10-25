package com.customlauncher.ui.home

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.Edge
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.gestures.GestureCallbacks
import com.customlauncher.gestures.launcherGestures
import com.customlauncher.ui.LauncherViewModel

/**
 * The home screen of the current activity: wallpaper, paged widgets, edge panels, and the
 * gesture engine wrapped around all of it.
 */
@Composable
fun HomeScreen(profile: LayoutProfile, vm: LauncherViewModel, pagerState: PagerState, resumed: Boolean) {
    val activity = profile.currentActivity
    val panels = activity.panels
    val currentProfile by rememberUpdatedState(profile)

    val callbacks = remember(vm) {
        GestureCallbacks(
            onTrigger = { trigger -> vm.run(currentProfile.gestures.actionFor(trigger)) },
            onLongPress = { pos -> if (!vm.editMode) vm.enterEditMode(pos) },
            onTap = { if (vm.editMode) vm.selectedWidgetId = null },
        )
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.currentPage = it }
    }

    // Space reserved by non-auto-hiding panels on each edge.
    val reserved: Map<Edge, Dp> = Edge.entries.associateWith { edge ->
        panels.filter { it.edge == edge && it.reserveSpace && !it.autoHide }
            .fold(0.dp) { acc, p -> acc + p.footprint() }
    }
    val overlayOpen = vm.drawerOpen || vm.searchOpen || vm.fullscreenWidgetId != null

    Box(
        Modifier
            .fillMaxSize()
            .launcherGestures(profile.gestures, vm.editMode, activity.pages.size == 1, callbacks)
    ) {
        WallpaperView(activity.wallpaper)
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

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = activity.pages.size > 1,
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

                if (activity.pages.size > 1) {
                    PageIndicator(
                        count = activity.pages.size,
                        current = pagerState.currentPage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                    )
                }
            }

            // Panels, stacked outward-in per edge.
            Edge.entries.forEach { edge ->
                var offset = 0.dp
                panels.filter { it.edge == edge }.forEach { panel ->
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
                    )
                    if (!panel.autoHide) offset += panel.footprint()
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 8.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (i == current) 0.9f else 0.4f), CircleShape)
            )
        }
    }
}
