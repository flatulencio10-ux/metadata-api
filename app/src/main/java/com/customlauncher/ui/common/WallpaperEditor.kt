package com.customlauncher.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.Wallpaper
import kotlinx.coroutines.launch

/**
 * Wallpaper chooser used for activities and pages.
 * @param allowInherit pages may inherit (null) the activity wallpaper.
 * @param copyImage copies a picked image into app storage and returns its path.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperEditor(
    current: Wallpaper?,
    allowInherit: Boolean,
    copyImage: suspend (android.net.Uri) -> String?,
    onChange: (Wallpaper?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { copyImage(uri)?.let { onChange(Wallpaper.Image(it)) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Wallpaper", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (allowInherit) FilterChip(current == null, { onChange(null) }, { Text("Same as activity") })
            FilterChip(current == Wallpaper.System, { onChange(Wallpaper.System) }, { Text("System") })
            FilterChip(current is Wallpaper.SolidColor, { onChange(Wallpaper.SolidColor(0xFF1B1E20)) }, { Text("Color") })
            FilterChip(current is Wallpaper.Gradient, { onChange(Wallpaper.Gradient(0xFF1D99F3, 0xFF0B1026)) }, { Text("Gradient") })
            FilterChip(current is Wallpaper.Image, {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, { Text("Image…") })
        }
        when (current) {
            is Wallpaper.SolidColor -> ColorRow("Color", current.color) { onChange(current.copy(color = it)) }
            is Wallpaper.Gradient -> {
                ColorRow("Top", current.top) { onChange(current.copy(top = it)) }
                ColorRow("Bottom", current.bottom) { onChange(current.copy(bottom = it)) }
            }
            is Wallpaper.Image -> {
                FocalPointPicker(current) { fx, fy -> onChange(current.copy(focusX = fx, focusY = fy)) }
                LabeledSlider("Dim", current.dim, 0f..0.9f, { onChange(current.copy(dim = it)) }, format = { "%.0f%%".format(it * 100) })
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose another image") }
            }
            else -> Unit
        }
    }
}

/** Phone-shaped preview of the cropped wallpaper; tap to choose the point kept in view. */
@Composable
private fun FocalPointPicker(wallpaper: Wallpaper.Image, onPick: (Float, Float) -> Unit) {
    Column {
        Text("Focal point: tap the part that must stay visible", style = MaterialTheme.typography.bodySmall)
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(width = 120.dp, height = 240.dp)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        onPick((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
                    }
                }
        ) {
            AsyncImage(
                model = File(wallpaper.path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(wallpaper.focusX * 2f - 1f, wallpaper.focusY * 2f - 1f),
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                // The focus marker sits where the focal point lands inside the crop.
                val c = Offset(size.width * wallpaper.focusX, size.height * wallpaper.focusY)
                drawCircle(Color.White, radius = 10.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = c)
            }
        }
    }
}
