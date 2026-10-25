package com.customlauncher.ui.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.ui.theme.readableOn
import com.customlauncher.ui.theme.toColor
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.WidgetHost
import com.customlauncher.widget.WidgetRegistry
import kotlin.math.roundToInt

/** Root size in px, needed to align the frosted-glass wallpaper copy behind widgets. */
val LocalRootSize = compositionLocalOf { IntSize.Zero }

/**
 * Draws a widget instance with its style (opacity, corner radius, tint, blur, padding) and
 * provides [LocalWidgetHost] to its content. Used on pages, in panels and full screen.
 */
@Composable
fun WidgetFrame(
    instance: WidgetInstance,
    wallpaper: Wallpaper,
    host: WidgetHost,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
) {
    val def = WidgetRegistry.find(instance.type)
    val style = instance.style
    val shape = RoundedCornerShape(style.cornerRadiusDp.dp)
    val tint = style.backgroundColor.toColor()
    val contentColor = if (tint.alpha > 0.35f) readableOn(tint) else MaterialTheme.colorScheme.onBackground
    val config = remember(instance.config) { WidgetConfig(instance.config) }

    Box(
        modifier
            .graphicsLayer { alpha = style.opacity.coerceIn(0.05f, 1f) }
            .clip(shape)
    ) {
        WidgetBackground(style.blurDp, wallpaper)
        Box(Modifier.fillMaxSize().background(tint))
        CompositionLocalProvider(LocalWidgetHost provides host, LocalContentColor provides contentColor) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(style.paddingDp.coerceAtLeast(0f).dp)) {
                val size = DpSize(maxWidth, maxHeight)
                when {
                    def == null -> Text("Unknown widget: ${instance.type}")
                    fullscreen -> def.FullscreenContent(config, size)
                    else -> def.Content(config, size)
                }
            }
        }
    }
}

/**
 * Frosted glass: draws a blurred copy of the wallpaper aligned with the real one, clipped to
 * the widget. Compose cannot blur what is behind a node, so this only works for launcher
 * wallpapers (color/gradient/image) on Android 12+; with the system wallpaper only the tint
 * is drawn.
 */
@Composable
private fun WidgetBackground(blurDp: Float, wallpaper: Wallpaper) {
    if (blurDp <= 0f || wallpaper == Wallpaper.System || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val root = LocalRootSize.current
    if (root == IntSize.Zero) return
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { position = it.positionInRoot() }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(root.width, root.height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(-position.x.roundToInt(), -position.y.roundToInt())
                }
            }
            .blur(blurDp.dp)
    ) {
        WallpaperView(wallpaper)
    }
}
