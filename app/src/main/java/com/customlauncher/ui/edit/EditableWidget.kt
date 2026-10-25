package com.customlauncher.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.customlauncher.core.model.Wallpaper
import com.customlauncher.core.model.WidgetInstance
import com.customlauncher.ui.home.WidgetFrame
import com.customlauncher.widget.WidgetHost
import com.customlauncher.widget.WidgetRegistry

/** Position/size in dp while a drag or resize is in progress. */
private data class EditRect(val x: Float, val y: Float, val w: Float, val h: Float)

private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * A widget in Edit Mode: drag anywhere to move, drag the corner handles to resize. Changes
 * are previewed locally (snapped live) and committed to the profile when the finger lifts.
 */
@Composable
fun EditableWidget(
    instance: WidgetInstance,
    wallpaper: Wallpaper,
    host: WidgetHost,
    selected: Boolean,
    pageSize: DpSize,
    snap: (Float) -> Float,
    onSelect: () -> Unit,
    onCommit: (x: Float, y: Float, w: Float, h: Float) -> Unit,
) {
    val keys = arrayOf<Any>(instance.id, instance.x, instance.y, instance.width, instance.height)
    var rect by remember(*keys) { mutableStateOf(EditRect(instance.x, instance.y, instance.width, instance.height)) }
    val min = WidgetRegistry.find(instance.type)?.minSize ?: DpSize(48.dp, 48.dp)
    val maxW = pageSize.width.value
    val maxH = pageSize.height.value

    fun snapped(r: EditRect): EditRect {
        val w = snap(r.w).coerceIn(min.width.value, maxW)
        val h = snap(r.h).coerceIn(min.height.value, maxH)
        return EditRect(
            x = snap(r.x).coerceIn(0f, (maxW - w).coerceAtLeast(0f)),
            y = snap(r.y).coerceIn(0f, (maxH - h).coerceAtLeast(0f)),
            w = w,
            h = h,
        )
    }

    fun commit() {
        val s = snapped(rect)
        if (s == EditRect(instance.x, instance.y, instance.width, instance.height)) rect = s
        onCommit(s.x, s.y, s.w, s.h)
    }

    val shown = snapped(rect)
    val outline = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)

    Box(
        Modifier
            .offset(shown.x.dp, shown.y.dp)
            .size(shown.w.dp, shown.h.dp)
    ) {
        WidgetFrame(instance, wallpaper, host, Modifier.fillMaxSize())
        // Gesture layer above the content: widget content is not interactive in Edit Mode.
        Box(
            Modifier
                .fillMaxSize()
                .border(if (selected) 2.dp else 1.dp, outline, RoundedCornerShape(instance.style.cornerRadiusDp.dp))
                .pointerInput(*keys) { detectTapGestures(onTap = { onSelect() }) }
                .pointerInput(*keys) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDragEnd = { commit() },
                        onDragCancel = { commit() },
                    ) { change, drag ->
                        change.consume()
                        rect = rect.copy(x = rect.x + drag.x.toDp().value, y = rect.y + drag.y.toDp().value)
                    }
                }
        )
        if (selected) {
            Handle.entries.forEach { handle ->
                ResizeHandle(handle, keys, onEnd = { commit() }) { dx, dy ->
                    rect = when (handle) {
                        Handle.TOP_LEFT -> rect.copy(x = rect.x + dx, y = rect.y + dy, w = rect.w - dx, h = rect.h - dy)
                        Handle.TOP_RIGHT -> rect.copy(y = rect.y + dy, w = rect.w + dx, h = rect.h - dy)
                        Handle.BOTTOM_LEFT -> rect.copy(x = rect.x + dx, w = rect.w - dx, h = rect.h + dy)
                        Handle.BOTTOM_RIGHT -> rect.copy(w = rect.w + dx, h = rect.h + dy)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandle(
    handle: Handle,
    keys: Array<Any>,
    onEnd: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    val alignment = when (handle) {
        Handle.TOP_LEFT -> Alignment.TopStart
        Handle.TOP_RIGHT -> Alignment.TopEnd
        Handle.BOTTOM_LEFT -> Alignment.BottomStart
        Handle.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
    Box(
        Modifier
            .align(alignment)
            .size(28.dp)
            .pointerInput(*keys) {
                detectDragGestures(
                    onDragEnd = onEnd,
                    onDragCancel = onEnd,
                ) { change, drag ->
                    change.consume()
                    onDrag(drag.x.toDp().value, drag.y.toDp().value)
                }
            }
            .padding(5.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .border(2.dp, Color.White, CircleShape)
    )
}
