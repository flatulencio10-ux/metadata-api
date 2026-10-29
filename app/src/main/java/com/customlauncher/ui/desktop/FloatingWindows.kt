package com.customlauncher.ui.desktop

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.customlauncher.core.model.FloatingWindow
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.desktop.WindowMath
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.home.WidgetFrame
import com.customlauncher.widget.WidgetHost
import com.customlauncher.widget.WidgetRegistry

/**
 * Widgets in desktop-style windows: drag the title bar to move, the corner to resize;
 * minimize sends them to the taskbar, maximize fills the page area, close removes them.
 * The last touched window comes to the front.
 */
@Composable
fun FloatingWindows(windows: List<FloatingWindow>, wallpaper: Wallpaper, area: DpSize, active: Boolean, vm: LauncherViewModel) {
    var order by remember { mutableStateOf(listOf<String>()) }
    windows.filter { !it.minimized }.forEach { w ->
        key(w.id) {
            val z = order.indexOf(w.id).let { if (it < 0) 0f else it + 1f }
            WindowFrame(w, wallpaper, area, active, vm, Modifier.zIndex(z)) { order = order - w.id + w.id }
        }
    }
}

@Composable
private fun WindowFrame(w: FloatingWindow, wallpaper: Wallpaper, area: DpSize, active: Boolean, vm: LauncherViewModel, modifier: Modifier, onFocus: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val def = WidgetRegistry.find(w.widget.type)
    // Drag state lives locally and is committed on release, so moving never rewrites the profile per frame.
    var draft by remember(w) { mutableStateOf(w) }
    val current by rememberUpdatedState(draft)
    val spring = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val x by animateDpAsState(if (draft.maximized) 0.dp else draft.x.dp, spring, label = "wx")
    val y by animateDpAsState(if (draft.maximized) 0.dp else draft.y.dp, spring, label = "wy")
    val width by animateDpAsState(if (draft.maximized) area.width else draft.width.dp, spring, label = "ww")
    val height by animateDpAsState(if (draft.maximized) area.height else draft.height.dp, spring, label = "wh")
    val dragging = remember { mutableStateOf(false) }

    Column(
        modifier
            .offset(if (dragging.value) draft.x.dp else x, if (dragging.value) draft.y.dp else y)
            .size(if (dragging.value) draft.width.dp else width, if (dragging.value) draft.height.dp else height)
            .shadow(18.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF0181A20))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { onFocus() }) }
            .onGloballyPositioned { vm.chromeBounds["window:${w.id}"] = it.boundsInRoot() }
    ) {
        androidx.compose.runtime.DisposableEffect(w.id) { onDispose { vm.chromeBounds.remove("window:${w.id}") } }
        Row(
            Modifier
                .fillMaxWidth()
                .height(WindowMath.TITLE_H.dp)
                .background(Color.White.copy(alpha = 0.05f))
                .pointerInput(w.id, area) {
                    detectDragGestures(
                        onDragStart = { onFocus(); dragging.value = true },
                        onDragEnd = { dragging.value = false; vm.updateWindow(w.id) { current } },
                        onDragCancel = { dragging.value = false; draft = w },
                    ) { change, d ->
                        change.consume()
                        draft = WindowMath.move(draft, d.x / density, d.y / density, area.width.value, area.height.value)
                    }
                }
                .pointerInput(w.id) {
                    detectTapGestures(onDoubleTap = { vm.updateWindow(w.id) { it.copy(maximized = !it.maximized) } })
                }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            def?.icon?.let { Icon(it, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(8.dp))
            Text(w.widget.name.ifBlank { def?.name ?: "Window" }, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TitleButton(Icons.Filled.Minimize, "Minimize") { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.updateWindow(w.id) { it.copy(minimized = true) } }
            TitleButton(if (draft.maximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare, if (draft.maximized) "Restore" else "Maximize") {
                vm.updateWindow(w.id) { it.copy(maximized = !it.maximized) }
            }
            TitleButton(Icons.Filled.Close, "Close") { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.closeWindow(w.id) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val host = remember(w.widget.id, active) {
                WidgetHost(
                    instanceId = w.widget.id,
                    isActive = active,
                    updateConfig = { vm.updateWidgetConfig(w.widget.id, it) },
                    openFullscreen = { vm.fullscreenWidgetId = w.widget.id },
                    openConfig = { vm.configuringWidgetId = w.widget.id },
                    runAction = vm::run,
                )
            }
            WidgetFrame(w.widget, wallpaper, host, Modifier.fillMaxSize().padding(4.dp))
            if (!draft.maximized) Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .pointerInput(w.id, area) {
                        detectDragGestures(
                            onDragStart = { onFocus(); dragging.value = true },
                            onDragEnd = { dragging.value = false; vm.updateWindow(w.id) { current } },
                            onDragCancel = { dragging.value = false; draft = w },
                        ) { change, d ->
                            change.consume()
                            draft = WindowMath.resize(draft, d.x / density, d.y / density, area.width.value, area.height.value)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.OpenInFull, "Resize", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
private fun TitleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(16.dp)) }
}
