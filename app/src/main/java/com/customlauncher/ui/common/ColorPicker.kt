package com.customlauncher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.customlauncher.ui.theme.toColor

private val PRESETS = listOf<Long>(
    0xFF1D99F3, 0xFF3DAEE9, 0xFF27AE60, 0xFFF67400, 0xFFDA4453, 0xFF8E44AD, 0xFFFDBC4B,
    0xFFEFF0F1, 0xFF7F8C8D, 0xFF31363B, 0xFF232629, 0xFF000000, 0x00000000, 0x66000000, 0xCCFFFFFF,
)

/** A row that shows a color swatch and opens [ColorPickerDialog] on tap. */
@Composable
fun ColorRow(label: String, color: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Swatch(color, 32)
    }
    if (open) ColorPickerDialog(label, color, onDismiss = { open = false }) { onChange(it); open = false }
}

@Composable
private fun Swatch(color: Long, sizeDp: Int, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.3f))
            .background(color.toColor())
            .border(if (selected) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/** HSV + alpha picker with presets and hex input. Value is ARGB as Long. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) }
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var a by remember { mutableFloatStateOf(((initial shr 24) and 0xFF) / 255f) }
    fun argb(): Long = android.graphics.Color.HSVToColor((a * 255).toInt(), floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFFL
    val current = argb()
    var hex by remember { mutableStateOf("%08X".format(initial and 0xFFFFFFFFL)) }
    fun syncHex() { hex = "%08X".format(argb()) }

    fun set(c: Long) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toInt(), arr)
        h = arr[0]; s = arr[1]; v = arr[2]; a = ((c shr 24) and 0xFF) / 255f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.3f))
                        .background(current.toColor())
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESETS.forEach { p -> Swatch(p, 28, selected = p == current) { set(p); syncHex() } }
                }
                LabeledSlider("Hue", h, 0f..360f, { h = it; syncHex() })
                LabeledSlider("Saturation", s, 0f..1f, { s = it; syncHex() }, format = { "%.2f".format(it) })
                LabeledSlider("Brightness", v, 0f..1f, { v = it; syncHex() }, format = { "%.2f".format(it) })
                LabeledSlider("Opacity", a, 0f..1f, { a = it; syncHex() }, format = { "%.0f%%".format(it * 100) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = {
                            hex = it.uppercase().filter { ch -> ch in "0123456789ABCDEF" }.take(8)
                            val parsed = hex.toLongOrNull(16)
                            if (parsed != null && (hex.length == 6 || hex.length == 8)) {
                                set(if (hex.length == 6) parsed or 0xFF000000 else parsed)
                            }
                        },
                        label = { Text("AARRGGBB") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
