package com.customlauncher.ui.desktop

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customlauncher.core.model.Node
import com.customlauncher.physics.PhysicsStats
import com.customlauncher.ui.LauncherViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Long-press on empty home space: a glassy menu at the finger with add widget / object,
 * widget window, paste, page settings and wallpaper. Springs open from the touch point.
 */
@Composable
fun HomeContextMenu(vm: LauncherViewModel) {
    val at = vm.contextMenuAt
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(at) { if (at != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var screen by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { screen = it.size }) {
        if (at != null) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { vm.contextMenuAt = null })
        AnimatedVisibility(
            visible = at != null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 0f)),
            exit = fadeOut(),
            modifier = Modifier.offset {
                val p = at ?: Offset.Zero
                IntOffset(
                    p.x.roundToInt().coerceAtMost((screen.width - size.width).coerceAtLeast(0)),
                    p.y.roundToInt().coerceAtMost((screen.height - size.height).coerceAtLeast(0)),
                )
            },
        ) {
            val pos = at ?: Offset.Zero
            fun close() { vm.contextMenuAt = null }
            Column(
                Modifier
                    .onGloballyPositioned { size = it.size }
                    .width(232.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xEB181A20))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                    .padding(vertical = 6.dp)
            ) {
                MenuRow(Icons.Filled.Extension, "Add widget") { close(); vm.enterEditMode(); vm.addWidgetSheetTab = 0; vm.addWidgetSheetOpen = true }
                MenuRow(Icons.Filled.SportsBaseball, "Add object") { close(); vm.enterEditMode(); vm.addWidgetSheetTab = 2; vm.addWidgetSheetOpen = true }
                MenuRow(Icons.Filled.Web, "Widget in a window") { close(); vm.enterEditMode(); vm.addWidgetAsWindow = true; vm.addWidgetSheetTab = 0; vm.addWidgetSheetOpen = true }
                if (vm.nodeClipboard.isNotEmpty()) MenuRow(Icons.Filled.ContentPaste, "Paste (${vm.nodeClipboard.size})") {
                    close()
                    val local = (pos - vm.pageOriginPx) / density.density
                    vm.pasteNodes(local)
                }
                MenuRow(Icons.Filled.Tune, "Page settings") { close(); vm.enterEditMode(); vm.pageSettingsOpen = true }
                MenuRow(Icons.Filled.Wallpaper, "Wallpaper") {
                    close()
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                MenuRow(Icons.Filled.Edit, "Edit home") { close(); vm.enterEditMode() }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp).let { it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFF7C9CFF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
    }
}

/** Settings → Performance overlay: FPS, active objects and physics step time. */
@Composable
fun PerformanceOverlay(pageNodes: List<Node>, modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    var stepMs by remember { mutableStateOf(0f) }
    var bodies by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var windowStart = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            frames++
            if (now - windowStart >= 500_000_000L) {
                fps = (frames * 1e9 / (now - windowStart)).roundToInt()
                frames = 0
                windowStart = now
                stepMs = PhysicsStats.stepMs
                bodies = PhysicsStats.bodies
            }
        }
    }
    val live = pageNodes.count { it.physics != null || it.logic != null || it.script != null || it.animation != null || it.clips.isNotEmpty() }
    val mono = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("FPS      $fps", color = when { fps >= 55 -> Color(0xFF8BE28B); fps >= 30 -> Color(0xFFFFD54D); else -> Color(0xFFFF7A7A) }, style = mono)
        Text("objects  $live", color = Color.White, style = mono)
        Text("bodies   $bodies", color = Color.White, style = mono)
        Text("physics  ${"%.2f".format(stepMs)} ms", color = Color.White, style = mono)
    }
}
