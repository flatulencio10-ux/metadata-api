package com.customlauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.CompositionLocalProvider
import com.customlauncher.physics.LocalPhysics
import com.customlauncher.physics.physicsTouch
import com.customlauncher.physics.rememberPhysicsWorld
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
    val effectiveGrid = page.grid ?: grid
    if (page.infinite) {
        InfiniteCanvas(vm.viewport(page.id)) { zoom ->
            if (editMode && effectiveGrid.showGrid) GridDots(effectiveGrid.snapDp)
            NodeList(
                nodes = page.widgets.filter { it.layer == NodeLayer.NORMAL },
                allOnPage = page.widgets,
                wallpaper = wallpaper,
                isActive = isActive,
                editMode = editMode,
                grid = effectiveGrid,
                vm = vm,
                pinned = false,
                canvasScale = zoom,
            )
        }
        return
    }
    // Physics runs outside Edit Mode only; nodes read their live pose from LocalPhysics.
    val physics = if (editMode) null else rememberPhysicsWorld(
        nodes = page.widgets,
        world = page.world,
        pageSize = vm.pageSizeDp,
        active = isActive,
        maxDynamic = vm.profile.value?.performance?.maxDynamicBodies ?: 30,
        events = vm.objectEvents,
        onSettled = { vm.commitPhysicsPoses(it) },
    )
    CompositionLocalProvider(LocalPhysics provides physics) {
    Box(Modifier.fillMaxSize().physicsTouch(physics, vm.objectEvents)) {
        if (editMode && effectiveGrid.showGrid && effectiveGrid.snapEnabled) GridDots(effectiveGrid.snapDp)
        NodeList(
            nodes = page.widgets.filter { it.layer == NodeLayer.NORMAL },
            allOnPage = page.widgets,
            wallpaper = wallpaper,
            isActive = isActive,
            editMode = editMode,
            grid = effectiveGrid,
            vm = vm,
            pinned = false,
        )
    }
    }
}

/** Pan/zoom state of an infinite canvas page (kept per page while the launcher runs). */
class CanvasViewport {
    var offset by mutableStateOf(Offset.Zero)
    var zoom by mutableFloatStateOf(1f)
    val isHome get() = offset == Offset.Zero && zoom == 1f
}

/**
 * Desktop-like page: one finger pans, two fingers pinch-zoom around the fingers. Nodes keep
 * their dp coordinates in "canvas space"; Edit Mode drags are divided by the zoom.
 */
@Composable
private fun InfiniteCanvas(vp: CanvasViewport, content: @Composable (zoom: Float) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(vp) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newZoom = (vp.zoom * zoom).coerceIn(0.25f, 4f)
                    // Keep the content point under the fingers fixed while zooming.
                    val contentPoint = (centroid - vp.offset) / vp.zoom
                    vp.offset = centroid - contentPoint * newZoom + pan
                    vp.zoom = newZoom
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = vp.offset.x
                    translationY = vp.offset.y
                    scaleX = vp.zoom
                    scaleY = vp.zoom
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) { content(vp.zoom) }
        if (!vp.isHome) {
            Text(
                "⌂  Recenter",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable { vp.offset = Offset.Zero; vp.zoom = 1f }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
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
    canvasScale: Float = 1f,
) {
    nodes.sortedBy { it.z }.forEach { stored ->
        key(stored.id) {
            // Inspector sliders preview style changes before committing them.
            val instance = vm.nodePreview?.takeIf { it.id == stored.id } ?: stored
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
                gridSnap = { v -> if (grid.snapDp > 0f) kotlin.math.round(v / grid.snapDp) * grid.snapDp else v },
                guidesEnabled = grid.guides,
                others = allOnPage.filter { it.id != instance.id && !it.hidden }.map { NodeBounds.of(it) },
                onSelect = { vm.selectedWidgetId = it },
                onCommit = vm::commitNode,
                onGuides = { vm.guides = it },
                onToolbar = vm::onNodeToolbar,
                pinned = pinned,
                canvasScale = canvasScale,
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
