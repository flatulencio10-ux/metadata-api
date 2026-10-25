package com.customlauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import com.customlauncher.core.model.Page
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.edit.EditableWidget
import com.customlauncher.widget.WidgetHost

/**
 * One home page: optional page wallpaper plus freely placed widgets ordered by z.
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
        page.wallpaper?.let { WallpaperView(it) }
        if (editMode && grid.showGrid && grid.snapEnabled) GridDots(grid.snapDp)

        page.widgets.sortedBy { it.z }.forEach { stored ->
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
                        runAction = vm::run,
                    )
                }
                if (editMode) {
                    EditableWidget(
                        instance = instance,
                        wallpaper = wallpaper,
                        host = host,
                        selected = vm.selectedWidgetId == instance.id,
                        pageSize = vm.pageSizeDp,
                        snap = vm::snap,
                        onSelect = { vm.selectedWidgetId = instance.id },
                        onCommit = { x, y, w, h -> vm.moveResizeWidget(instance.id, x, y, w, h) },
                    )
                } else {
                    WidgetFrame(
                        instance = instance,
                        wallpaper = wallpaper,
                        host = host,
                        modifier = Modifier
                            .offset(instance.x.dp, instance.y.dp)
                            .size(instance.width.dp, instance.height.dp)
                            .then(
                                if (isActive) Modifier.onGloballyPositioned { vm.widgetBounds[instance.id] = it.boundsInRoot() }
                                else Modifier
                            ),
                    )
                }
            }
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
