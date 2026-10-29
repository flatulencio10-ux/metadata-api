package com.customlauncher.ui.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.PanelItem
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.AppIcon
import com.customlauncher.ui.common.LocalAppCatalog
import com.customlauncher.widget.WidgetRegistry
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The launcher view model, for panel items that need more than actions (taskbar, start). */
val LocalDesktop = staticCompositionLocalOf<LauncherViewModel?> { null }

/** Start button: opens the start menu. */
@Composable
fun StartButtonItem(size: Dp) {
    val vm = LocalDesktop.current
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(30))
            .background(if (vm?.startMenuOpen == true) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                vm?.let { it.startMenuOpen = !it.startMenuOpen }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Apps, "Start", tint = Color.White) }
}

/**
 * Taskbar: pinned apps, then recent apps (UsageStats), then minimized widget windows.
 * Long-press an app for "Open in window" and pin/unpin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskbarItem(item: PanelItem.Taskbar, iconSize: Dp, horizontal: Boolean, onAction: (Action) -> Unit) {
    val vm = LocalDesktop.current ?: return
    val c = LocalContext.current.container
    val catalog = LocalAppCatalog.current
    val recentPackages by c.recentApps.recent.collectAsState()
    val recents = if (!item.showRecents) emptyList() else recentPackages
        .mapNotNull { pkg -> catalog.apps.firstOrNull { it.packageName == pkg }?.key }
        .filter { it !in item.pinned }
        .take(item.maxRecents)
    val minimized = vm.profile.collectAsState().value?.currentActivity?.windows.orEmpty().filter { it.minimized }
    val needsPermission = item.showRecents && !remember(recentPackages) { c.recentApps.hasPermission() }

    @Composable
    fun content() {
        item.pinned.forEach { key -> TaskbarApp(key, iconSize, pinned = true, running = false, vm, onAction) }
        if (item.pinned.isNotEmpty() && recents.isNotEmpty()) Divider(horizontal)
        recents.forEach { key -> TaskbarApp(key, iconSize, pinned = false, running = true, vm, onAction) }
        minimized.forEach { w ->
            val def = WidgetRegistry.find(w.widget.type)
            Box(
                Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(30))
                    .background(Color.White.copy(alpha = 0.12f))
                    .combinedClickable(onClick = { vm.updateWindow(w.id) { it.copy(minimized = false) } }),
                contentAlignment = Alignment.Center,
            ) { Icon(def?.icon ?: Icons.Filled.Widgets, def?.name, tint = Color.White) }
        }
        if (needsPermission) {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(Color(0x337C9CFF))
                    .combinedClickable(onClick = {
                        c.bus.send(com.customlauncher.actions.UiCommand.Message("Allow usage access so the taskbar can show recent apps"))
                        runCatching { c.app.startActivity(c.recentApps.settingsIntent()) }
                    }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.QueryStats, "Show recent apps", tint = Color.White) }
        }
    }
    if (horizontal) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun Divider(horizontal: Boolean) {
    Box(
        (if (horizontal) Modifier.width(1.dp).size(1.dp, 24.dp) else Modifier.size(24.dp, 1.dp))
            .background(Color.White.copy(alpha = 0.3f))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskbarApp(key: String, size: Dp, pinned: Boolean, running: Boolean, vm: LauncherViewModel, onAction: (Action) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(contentAlignment = Alignment.BottomCenter) {
        AppIcon(
            key, size * 0.86f,
            Modifier
                .padding(bottom = 4.dp)
                .combinedClickable(
                    onClick = { onAction(Action.OpenApp(key)) },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                ),
        )
        if (running) Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Open in window") }, onClick = { menu = false; vm.openAppInWindow(key) })
            DropdownMenuItem(text = { Text(if (pinned) "Unpin from taskbar" else "Pin to taskbar") }, onClick = { menu = false; vm.toggleTaskbarPin(key) })
        }
    }
}

/** Clock, battery, Wi-Fi and notification count. */
@Composable
fun SystemTrayItem(item: PanelItem.SystemTray, horizontal: Boolean) {
    val c = LocalContext.current.container
    val battery by c.powerGovernor.battery.collectAsState()
    val wifi by c.trayState.wifi.collectAsState()
    val notifications by c.trayState.notifications.collectAsState()
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
    @Composable
    fun content() {
        if (item.notifications && notifications > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(16.dp))
            Text("$notifications", style = style)
        }
        if (item.wifi) Icon(if (wifi) Icons.Filled.Wifi else Icons.Filled.WifiOff, if (wifi) "Wi-Fi connected" else "No Wi-Fi", tint = Color.White, modifier = Modifier.size(16.dp))
        if (item.battery) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (battery > 90) Icons.Filled.BatteryFull else Icons.Filled.BatteryStd, "Battery", tint = if (battery < 15) Color(0xFFFF7A7A) else Color.White, modifier = Modifier.size(16.dp))
            Text("$battery%", style = style)
        }
        if (item.clock) Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleSmall.copy(color = Color.White))
    }
    if (horizontal) Row(Modifier.fillMaxHeight().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    else Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    Spacer(Modifier.size(0.dp))
}
