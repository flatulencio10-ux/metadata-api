package com.customlauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.GridConfig
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.NodeLayer
import com.customlauncher.core.model.Page
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.edit.Guide
import com.customlauncher.ui.edit.NodeBounds
import com.customlauncher.ui.edit.NodeEditEnv
import com.customlauncher.ui.edit.NodeView
import com.customlauncher.widget.WidgetHost

/**
 * One home page: its NORMAL-layer nodes ordered by z. Nodes on the ABOVE_PANELS layer are
 * drawn by [NodeList] in the overlay above the panels instead.
 * [isActive] is false for off-screen pages so their widgets pause updates.
 */
@Composable
fun PageView(
    page: Page,
    activityWallpaper: Wallpaper,
    isActive: Boolean,
    editMode: Boolean,
    grid: GridConfig,
    vm: LauncherViewModel,
) {
    val wallpaper = page.wallpaper ?: activityWallpaper
    Box(Modifier.fillMaxSize()) {
        if (editMode && grid.showGrid && grid.snapEnabled) GridDots(grid.snapDp)
        NodeList(
            nodes = page.widgets.filter { it.layer == NodeLayer.NORMAL },
            allOnPage = page.widgets,
            wallpaper = wallpaper,
            isActive = isActive,
            editMode = editMode,
            grid = grid,
            vm = vm,
            pinned = false,
        )
    }
}

/**
 * Renders a list of nodes (z-ordered) for a page, the above-panels overlay or the pinned
 * layer, with Edit Mode transform support. [allOnPage] feeds the smart guides.
 */
@Composable
fun NodeList(
    nodes: List<Node>,
    allOnPage: List<Node>,
    wallpaper: Wallpaper,
    isActive: Boolean,
    editMode: Boolean,
    grid: GridConfig,
    vm: LauncherViewModel,
    pinned: Boolean,
) {
    nodes.sortedBy { it.z }.forEach { stored ->
        key(stored.id) {
            // Inspector sliders preview style changes before committing them.
            val instance = vm.stylePreview?.takeIf { it.first == stored.id }?.let { stored.copy(style = it.second) } ?: stored
            val host = remember(instance.id, isActive, editMode) {
                WidgetHost(
                    instanceId = instance.id,
                    isActive = isActive,
                    isEditing = editMode,
                    updateConfig = { vm.updateWidgetConfig(instance.id, it) },
                    openFullscreen = { vm.fullscreenWidgetId = instance.id },
                    openConfig = { vm.configuringWidgetId = instance.id },
                    runAction = vm::run,
                )
            }
            val env = if (!editMode) null else NodeEditEnv(
                pageSize = vm.pageSizeDp,
                gridEnabled = grid.snapEnabled,
                gridSnap = vm::snap,
                guidesEnabled = grid.guides,
                others = allOnPage.filter { it.id != instance.id && !it.hidden }.map { NodeBounds.of(it) },
                onSelect = { vm.selectedWidgetId = it },
                onCommit = vm::commitNode,
                onGuides = { vm.guides = it },
                onToolbar = vm::onNodeToolbar,
                pinned = pinned,
            )
            NodeView(
                node = instance,
                wallpaper = wallpaper,
                host = host,
                editMode = editMode,
                selected = vm.selectedWidgetId == instance.id,
                env = env,
                modifier = if (isActive && !editMode) Modifier.reportBounds(vm, instance.id) else Modifier,
            )
        }
    }
}

private fun Modifier.reportBounds(vm: LauncherViewModel, id: String): Modifier =
    this.onGloballyPositioned { vm.widgetBounds[id] = it.boundsInRoot() }

/** Smart guide lines while dragging in Edit Mode. */
@Composable
fun GuideLines(guides: List<Guide>) {
    if (guides.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        guides.forEach { g ->
            val p = g.position.dp.toPx()
            if (g.vertical) drawLine(Color(0xFFFF4FA3), Offset(p, 0f), Offset(p, size.height), strokeWidth = 1.dp.toPx())
            else drawLine(Color(0xFFFF4FA3), Offset(0f, p), Offset(size.width, p), strokeWidth = 1.dp.toPx())
        }
    }
}

/** Snap grid preview shown in Edit Mode (one dot every 4 snap steps to keep it readable). */
@Composable
private fun GridDots(stepDp: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val step = (stepDp * 4).dp.toPx().coerceAtLeast(8f)
        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(Color.White.copy(alpha = 0.35f), radius = 1.5f.dp.toPx(), center = Offset(x, y))
                y += step
            }
            x += step
        }
    }
}
