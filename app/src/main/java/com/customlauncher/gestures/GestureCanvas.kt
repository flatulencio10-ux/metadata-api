package com.customlauncher.gestures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.customlauncher.actions.ActionCatalog
import com.customlauncher.core.model.Action
import com.customlauncher.core.model.GestureConfig
import kotlinx.coroutines.delay

/**
 * Full-screen canvas for "draw a letter" gestures. The recognised letter runs the action
 * bound in [GestureConfig.letters].
 */
@Composable
fun GestureCanvas(config: GestureConfig, onAction: (Action) -> Unit, onClose: () -> Unit) {
    val points = remember { mutableStateListOf<Offset>() }
    var status by remember { mutableStateOf("Draw a letter: ${config.letters.keys.sorted().joinToString(" ")}") }
    var finished by remember { mutableStateOf<Action?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(finished) {
        val action = finished ?: return@LaunchedEffect
        delay(250)
        onClose()
        onAction(action)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { points.clear(); points += it },
                    onDragEnd = {
                        val letter = LetterRecognizer.recognize(points.map { Pt(it.x, it.y) })
                        val action = letter?.let { config.letters[it] }
                        status = when {
                            letter == null -> "Not recognised, try again (tap outside to close)"
                            action == null || action == Action.None -> "\"$letter\" has no action bound"
                            else -> "\"$letter\" → ${ActionCatalog.label(action)}"
                        }
                        if (action != null && action != Action.None) finished = action
                    },
                ) { change, _ -> points += change.position }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, accent, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 96.dp, start = 24.dp, end = 24.dp)) {
            Text(status, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
