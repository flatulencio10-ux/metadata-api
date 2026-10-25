package com.customlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.ui.theme.toColor
import java.io.File

/** Draws a [Wallpaper]. [Wallpaper.System] draws nothing: the window shows the system wallpaper. */
@Composable
fun WallpaperView(wallpaper: Wallpaper, modifier: Modifier = Modifier.fillMaxSize()) {
    when (wallpaper) {
        Wallpaper.System -> Unit
        is Wallpaper.SolidColor -> Box(modifier.background(wallpaper.color.toColor()))
        is Wallpaper.Gradient -> Box(
            modifier.background(Brush.verticalGradient(listOf(wallpaper.top.toColor(), wallpaper.bottom.toColor())))
        )
        is Wallpaper.Image -> Box(modifier) {
            AsyncImage(
                model = File(wallpaper.path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (wallpaper.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = wallpaper.dim.coerceIn(0f, 1f))))
        }
    }
}
