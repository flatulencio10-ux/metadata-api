package com.customlauncher.ui.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.CardStyle
import com.customlauncher.core.model.ThemeConfig
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.core.model.WidgetStyle
import com.customlauncher.ui.theme.LocalLauncherTheme
import com.customlauncher.ui.theme.readableOn
import com.customlauncher.ui.theme.toColor
import com.customlauncher.widget.LocalWidgetHost
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.WidgetHost
import com.customlauncher.widget.WidgetRegistry
import kotlin.math.roundToInt

/** Root size in px, needed to align the frosted-glass wallpaper copy behind widgets. */
val LocalRootSize = compositionLocalOf { IntSize.Zero }

/** Resolved card chrome for one widget: either the global card style or its own overrides. */
private data class CardLook(val background: Color, val border: Color, val radius: Dp, val blur: Float)

private fun cardLook(style: WidgetStyle, theme: ThemeConfig, surface: Color): CardLook {
    if (!style.useCardStyle) {
        return CardLook(style.backgroundColor.toColor(), Color.Transparent, style.cornerRadiusDp.dp, style.blurDp)
    }
    val radius = theme.cardRadiusDp.dp
    return when (theme.cardStyle) {
        // 12% white surface, 15% hairline, subtle blur (Android 12+ over launcher wallpapers).
        CardStyle.GLASS -> CardLook(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.15f), radius, 16f)
        CardStyle.SOLID -> CardLook(surface.copy(alpha = 0.92f), Color.Transparent, radius, 0f)
        CardStyle.OUTLINE -> CardLook(Color.Transparent, Color.White.copy(alpha = 0.4f), radius, 0f)
        CardStyle.NONE -> CardLook(Color.Transparent, Color.Transparent, 0.dp, 0f)
    }
}

/**
 * Draws a widget instance with its style (opacity, card background, border, radius, blur,
 * padding) and provides [LocalWidgetHost] to its content. Chromeless widgets (stickers) get
 * none of the card chrome. Used on pages, in panels and full screen.
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
    val config = remember(instance.config) { WidgetConfig(instance.config) }

    if (def != null && def.chromeless && !fullscreen) {
        // No own layer at full opacity, so blend modes can mix with what is beneath.
        val faded = if (style.opacity < 0.999f) Modifier.graphicsLayer { alpha = style.opacity.coerceIn(0f, 1f) } else Modifier
        Box(modifier.then(faded)) {
            CompositionLocalProvider(LocalWidgetHost provides host) {
                BoxWithConstraints(Modifier.fillMaxSize()) { def.Content(config, DpSize(maxWidth, maxHeight)) }
            }
        }
        return
    }

    val theme = LocalLauncherTheme.current
    val look = cardLook(style, theme, MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(look.radius)
    val contentColor = if (look.background.alpha > 0.35f) readableOn(look.background) else MaterialTheme.colorScheme.onBackground

    Box(
        modifier
            .graphicsLayer { alpha = style.opacity.coerceIn(0.05f, 1f) }
            .clip(shape)
            .then(if (look.border.alpha > 0f) Modifier.border(1.dp, look.border, shape) else Modifier)
    ) {
        WidgetBackground(look.blur, wallpaper)
        Box(Modifier.fillMaxSize().background(look.background))
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
