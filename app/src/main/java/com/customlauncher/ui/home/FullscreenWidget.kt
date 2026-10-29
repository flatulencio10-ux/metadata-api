package com.customlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.customlauncher.core.model.LayoutProfile
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.widget.WidgetHost

/** Shows one widget instance full screen (Action.OpenWidgetFullscreen or a widget tap). */
@Composable
fun FullscreenWidget(profile: LayoutProfile, instanceId: String, vm: LauncherViewModel) {
    val instance = profile.findWidget(instanceId) ?: run { vm.fullscreenWidgetId = null; return }
    val host = remember(instanceId) {
        WidgetHost(
            instanceId = instanceId,
            isActive = true,
            updateConfig = { vm.updateWidgetConfig(instanceId, it) },
            openFullscreen = {},
            runAction = vm::run,
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        WidgetFrame(
            // Full screen ignores the widget's own chrome; the theme background is used.
            instance = instance.copy(style = instance.style.copy(opacity = 1f, cornerRadiusDp = 0f, backgroundColor = 0x00000000, blurDp = 0f)),
            wallpaper = Wallpaper.System,
            host = host,
            modifier = Modifier.fillMaxSize(),
            fullscreen = true,
        )
        IconButton(onClick = { vm.fullscreenWidgetId = null }, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Filled.Close, "Close")
        }
    }
}
