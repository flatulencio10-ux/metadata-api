package com.customlauncher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.customlauncher.actions.ActionCatalog
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.Edge
import com.customlauncher.core.model.Panel
import com.customlauncher.core.model.PanelItem
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.theme.toColor
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.WidgetHost
import kotlinx.coroutines.delay

/** Space a panel takes along its edge's normal (thickness + margins when floating). */
fun Panel.footprint(): Dp = (thicknessDp + if (floating) marginDp * 2 else 0f).dp

/**
 * A KDE-style panel on any screen edge. Holds app icons, widgets, action buttons and
 * spacers; can float (inset + rounded) or attach to the edge, and auto-hide.
 *
 * @param offsetAlongNormal distance from the screen edge (stacking multiple panels per edge).
 * @param crossStart/crossEnd insets along the edge (vertical panels avoid top/bottom panels).
 */
@Composable
fun PanelView(
    panel: Panel,
    wallpaper: Wallpaper,
    isActive: Boolean,
    offsetAlongNormal: Dp,
    crossStart: Dp,
    crossEnd: Dp,
    onAction: (Action) -> Unit,
    onOpenWidget: (String) -> Unit,
    onUpdateConfig: (String, WidgetConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(!panel.autoHide) }
    var interactionTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(panel.autoHide, interactionTick, revealed) {
        if (panel.autoHide && revealed) {
            delay(4_000)
            revealed = false
        } else if (!panel.autoHide) revealed = true
    }

    val margin = if (panel.floating) panel.marginDp.dp else 0.dp
    val thickness = panel.thicknessDp.dp
    val shape = if (panel.floating) RoundedCornerShape(panel.cornerRadiusDp.dp) else RectangleShape
    val horizontal = panel.edge.isHorizontal

    val placement = when (panel.edge) {
        Edge.BOTTOM -> Modifier.padding(start = crossStart, end = crossEnd, bottom = offsetAlongNormal)
        Edge.TOP -> Modifier.padding(start = crossStart, end = crossEnd, top = offsetAlongNormal)
        Edge.LEFT -> Modifier.padding(top = crossStart, bottom = crossEnd, start = offsetAlongNormal)
        Edge.RIGHT -> Modifier.padding(top = crossStart, bottom = crossEnd, end = offsetAlongNormal)
    }
    val alignment = when (panel.edge) {
        Edge.BOTTOM -> Alignment.BottomCenter
        Edge.TOP -> Alignment.TopCenter
        Edge.LEFT -> Alignment.CenterStart
        Edge.RIGHT -> Alignment.CenterEnd
    }

    Box(modifier.fillMaxSize().then(placement), contentAlignment = alignment) {
        // Auto-hidden: a thin strip at the edge reveals the panel on tap.
        if (panel.autoHide && !revealed) {
            Box(
                (if (horizontal) Modifier.fillMaxWidth().height(10.dp) else Modifier.fillMaxHeight().width(10.dp))
                    .clickable { revealed = true; interactionTick++ }
            )
        }
        AnimatedVisibility(visible = revealed, enter = fadeIn(), exit = fadeOut()) {
            val size = if (horizontal) Modifier.fillMaxWidth().height(thickness) else Modifier.fillMaxHeight().width(thickness)
            Box(
                size
                    .padding(margin)
                    .graphicsLayer { alpha = panel.opacity.coerceIn(0.05f, 1f) }
                    .clip(shape)
                    .background(panel.backgroundColor.toColor())
                    .clickable(enabled = panel.autoHide, indication = null, interactionSource = null) { interactionTick++ }
            ) {
                val itemSize = thickness - margin * 2
                if (horizontal) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        panel.items.forEach { item -> RowItem(item, itemSize, wallpaper, isActive, onAction, onOpenWidget, onUpdateConfig) }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        panel.items.forEach { item -> ColumnItem(item, itemSize, wallpaper, isActive, onAction, onOpenWidget, onUpdateConfig) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.RowItem(item: PanelItem, cross: Dp, wallpaper: Wallpaper, active: Boolean, onAction: (Action) -> Unit, onOpenWidget: (String) -> Unit, onUpdateConfig: (String, WidgetConfig) -> Unit) {
    when (item) {
        is PanelItem.Spacer -> if (item.flexible) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(item.sizeDp.dp))
        is PanelItem.Widget -> Box(Modifier.width(item.lengthDp.dp).fillMaxHeight()) {
            PanelWidget(item, wallpaper, active, onAction, onOpenWidget, onUpdateConfig)
        }
        else -> Box(Modifier.size(cross), contentAlignment = Alignment.Center) { IconItem(item, cross, onAction) }
    }
}

@Composable
private fun ColumnScope.ColumnItem(item: PanelItem, cross: Dp, wallpaper: Wallpaper, active: Boolean, onAction: (Action) -> Unit, onOpenWidget: (String) -> Unit, onUpdateConfig: (String, WidgetConfig) -> Unit) {
    when (item) {
        is PanelItem.Spacer -> if (item.flexible) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(item.sizeDp.dp))
        is PanelItem.Widget -> Box(Modifier.height(item.lengthDp.dp).fillMaxWidth()) {
            PanelWidget(item, wallpaper, active, onAction, onOpenWidget, onUpdateConfig)
        }
        else -> Box(Modifier.size(cross), contentAlignment = Alignment.Center) { IconItem(item, cross, onAction) }
    }
}

@Composable
private fun IconItem(item: PanelItem, cross: Dp, onAction: (Action) -> Unit) {
    val iconSize = (cross * 0.72f).coerceAtLeast(16.dp)
    when (item) {
        is PanelItem.App -> AppIcon(item.component, iconSize, Modifier.clickable { onAction(Action.OpenApp(item.component)) })
        is PanelItem.ActionButton -> Box(
            Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { onAction(item.action) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(ActionCatalog.icon(item.action), contentDescription = item.label, tint = Color.White)
        }
        else -> Unit
    }
}

@Composable
private fun PanelWidget(item: PanelItem.Widget, wallpaper: Wallpaper, active: Boolean, onAction: (Action) -> Unit, onOpenWidget: (String) -> Unit, onUpdateConfig: (String, WidgetConfig) -> Unit) {
    val host = remember(item.widget.id, active) {
        WidgetHost(
            instanceId = item.widget.id,
            isActive = active,
            inPanel = true,
            openFullscreen = { onOpenWidget(item.widget.id) },
            updateConfig = { onUpdateConfig(item.widget.id, it) },
            runAction = onAction,
        )
    }
    WidgetFrame(item.widget, wallpaper, host, Modifier.fillMaxSize().padding(2.dp))
}
