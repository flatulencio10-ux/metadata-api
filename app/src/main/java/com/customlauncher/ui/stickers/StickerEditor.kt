package com.customlauncher.ui.stickers

import android.graphics.Bitmap
import android.graphics.Path
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.model.Node
import com.customlauncher.data.stickers.StickerKind
import com.customlauncher.ui.LauncherViewModel
import com.customlauncher.ui.common.ColorRow
import com.customlauncher.ui.common.LabeledSlider
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widgets.sticker.StickerBlend
import com.customlauncher.widgets.sticker.StickerEffects
import com.customlauncher.widgets.sticker.StickerRenderer
import com.customlauncher.widgets.sticker.StickerWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EditorBg = Color(0xFF0E1013)
private val TrayBg = Color(0xFF171A1F)
private val Accent = Color(0xFF7C5CFF)

private enum class EditorTab(val label: String, val icon: ImageVector) {
    CUT("Cut", Icons.Filled.ContentCut),
    SHAPE("Shape", Icons.Filled.Interests),
    EFFECTS("Effects", Icons.Filled.AutoAwesome),
    ANIMATE("Animate", Icons.Filled.Movie),
}

private enum class Brush { NONE, LASSO, ERASER, RESTORE }

/**
 * Undo/redo history over full bitmap snapshots (the image is capped at 1024 px, so 8 steps
 * stay around 32 MB).
 */
private class EditSession(val original: Bitmap, start: Bitmap) {
    var working by mutableStateOf(start)
    var version by mutableIntStateOf(0)
    var changed by mutableStateOf(false)
    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()
    val canUndo get() = version >= 0 && undo.isNotEmpty()
    val canRedo get() = version >= 0 && redo.isNotEmpty()

    fun snapshot() {
        undo.addLast(working.copy(Bitmap.Config.ARGB_8888, true))
        while (undo.size > 8) undo.removeFirst()
        redo.clear()
        changed = true
    }

    fun replace(b: Bitmap) {
        snapshot(); working = b; version++
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(working); working = prev; version++
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(working); working = next; version++
    }
}

/**
 * Full-screen sticker editor: background removal, lasso cut, eraser/restore brushes with
 * undo/redo, crop shapes, effects, and (Animate tab) the sticker's animations.
 */
@Composable
fun StickerEditor(node: Node, vm: LauncherViewModel) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val repo = context.container.stickerRepository
    val config = remember(node.id) { WidgetConfig(node.config) }
    val kind = StickerWidget.kindOf(config)
    val file = remember(node.id) { repo.fileOf(config.string(StickerWidget.FILE)) }
    val pixelEditable = kind == StickerKind.BITMAP || kind == StickerKind.ANIMATED

    var session by remember { mutableStateOf<EditSession?>(null) }
    LaunchedEffect(node.id) {
        if (pixelEditable) {
            session = withContext(Dispatchers.IO) {
                ImageOps.loadEditable(file)?.let { EditSession(it.copy(Bitmap.Config.ARGB_8888, false), it) }
            }
        }
    }

    var tab by remember { mutableStateOf(if (pixelEditable) EditorTab.CUT else EditorTab.EFFECTS) }
    var brush by remember { mutableStateOf(Brush.NONE) }
    var brushDp by remember { mutableFloatStateOf(28f) }
    var showBefore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fx by remember { mutableStateOf(StickerWidget.effects(config)) }
    var opacity by remember { mutableFloatStateOf(node.style.opacity) }

    val close = { vm.stickerEditorTarget = null }
    BackHandler(onBack = close)

    fun save() {
        val s = session
        busy = "Saving…"
        scope.launch {
            val png = if (s != null && s.changed) withContext(Dispatchers.Default) { ImageOps.toPng(ImageOps.trimTransparent(s.working)) } else null
            vm.saveStickerEdit(node.id, png, fx, opacity)
            busy = null
            close()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(EditorBg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // ---- Top bar --------------------------------------------------------------
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            Text("Sticker", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val s = session
            IconButton(onClick = { s?.undo() }, enabled = s?.canUndo == true) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (s?.canUndo == true) Color.White else Color.White.copy(alpha = 0.3f))
            }
            IconButton(onClick = { s?.redo() }, enabled = s?.canRedo == true) {
                Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (s?.canRedo == true) Color.White else Color.White.copy(alpha = 0.3f))
            }
            Button(onClick = { save() }, shape = RoundedCornerShape(50), enabled = busy == null) { Text("Save") }
        }

        // ---- Canvas ---------------------------------------------------------------
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp))) {
            val s = session
            if (s != null) {
                EditCanvas(s, brush, brushDp, showBefore, fx)
            } else {
                Box(Modifier.fillMaxSize().checkerboard(), contentAlignment = Alignment.Center) {
                    StickerWidget.StickerImage(
                        file, kind, androidx.compose.ui.unit.DpSize(280.dp, 280.dp), playing = true,
                        modifier = Modifier.size(280.dp).padding(16.dp), effects = fx,
                    )
                }
            }
            if (s != null) {
                // Hold to compare with the original.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                showBefore = true
                                tryAwaitRelease()
                                showBefore = false
                            })
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Compare, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(if (showBefore) " Before" else " Hold: before/after", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            busy?.let {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Text(it, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            error?.let {
                Text(
                    it, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .background(Color(0xCCB3261E), RoundedCornerShape(12.dp)).padding(10.dp)
                        .clickable { error = null },
                )
            }
        }

        // ---- Contextual options ---------------------------------------------------
        AnimatedContent(
            targetState = tab to brush,
            transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut() },
            label = "options",
            modifier = Modifier.fillMaxWidth(),
        ) { (t, b) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightLimit(t)
                    .verticalScroll(rememberScrollState())
            ) {
                when (t) {
                    EditorTab.CUT -> CutOptions(
                        session = session,
                        brush = b,
                        brushDp = brushDp,
                        onBrush = { brush = it; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        onBrushSize = { brushDp = it },
                        onRemoveBackground = {
                            val s = session ?: return@CutOptions
                            busy = "Removing background…"
                            scope.launch {
                                runCatching { BackgroundRemover.removeBackground(s.working) }
                                    .onSuccess { s.replace(it); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                                    .onFailure { error = it.message }
                                busy = null
                            }
                        },
                    )
                    EditorTab.SHAPE -> ShapeOptions(session) { shape ->
                        val s = session ?: return@ShapeOptions
                        s.replace(ImageOps.applyShape(s.working, shape))
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    EditorTab.EFFECTS -> EffectsOptions(fx, opacity, kind, onFx = { fx = it }, onOpacity = { opacity = it })
                    EditorTab.ANIMATE -> Hint("Entrance, idle and tap animations are set here.")
                }
            }
        }

        // ---- Tab tray -------------------------------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(TrayBg)
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EditorTab.entries.forEach { t ->
                val enabled = pixelEditable || (t != EditorTab.CUT && t != EditorTab.SHAPE)
                TrayButton(t.icon, t.label, selected = tab == t, enabled = enabled) {
                    tab = t
                    if (t != EditorTab.CUT) brush = Brush.NONE
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }
}

private fun Modifier.heightLimit(tab: EditorTab): Modifier =
    height(if (tab == EditorTab.ANIMATE || tab == EditorTab.EFFECTS) 280.dp else 150.dp)

/** Checkerboard backdrop that makes transparency visible. */
private fun Modifier.checkerboard(): Modifier = drawBehind {
    val cell = 12.dp.toPx()
    drawRect(Color(0xFF2A2E35))
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(Color(0xFF3A3F47), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(cell, cell))
            x += cell * 2
        }
        y += cell; row++
    }
}

@Composable
private fun EditCanvas(s: EditSession, brush: Brush, brushDp: Float, showBefore: Boolean, fx: StickerEffects) {
    val density = LocalDensity.current
    var fit by remember { mutableStateOf(Rect.Zero) }
    val lasso = remember { mutableStateListOf<Offset>() }
    var strokePath by remember { mutableStateOf<Path?>(null) }
    var last by remember { mutableStateOf(Offset.Zero) }
    // Effects preview uses the same renderer as the home screen.
    val preview = remember(s.version, fx, showBefore) {
        val src = if (showBefore) s.original else s.working
        if (fx.hasPixelEffects && !showBefore) StickerRenderer.applyEffects(src, src.width, src.height, fx) else src
    }

    fun toBitmap(p: Offset): Offset {
        val scale = fit.width / s.working.width
        return Offset((p.x - fit.left) / scale, (p.y - fit.top) / scale)
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .checkerboard()
            .pointerInput(brush, brushDp) {
                if (brush == Brush.NONE) return@pointerInput
                val widthPx = brushDp.dp.toPx()
                detectDragGestures(
                    onDragStart = { p ->
                        if (brush == Brush.LASSO) {
                            lasso.clear(); lasso += p
                        } else {
                            s.snapshot()
                            val b = toBitmap(p)
                            strokePath = Path().apply { moveTo(b.x, b.y); lineTo(b.x + 0.1f, b.y) }
                            last = b
                        }
                    },
                    onDragEnd = {
                        if (brush == Brush.LASSO && lasso.size > 3) {
                            val path = Path().apply {
                                lasso.forEachIndexed { i, p -> toBitmap(p).let { b -> if (i == 0) moveTo(b.x, b.y) else lineTo(b.x, b.y) } }
                                close()
                            }
                            s.replace(ImageOps.keepInside(s.working, path))
                        }
                        lasso.clear()
                        strokePath = null
                    },
                ) { change, _ ->
                    change.consume()
                    if (brush == Brush.LASSO) {
                        lasso += change.position
                    } else {
                        val b = toBitmap(change.position)
                        val seg = Path().apply { moveTo(last.x, last.y); lineTo(b.x, b.y) }
                        val w = widthPx / (fit.width / s.working.width)
                        if (brush == Brush.ERASER) ImageOps.erase(s.working, seg, w) else ImageOps.restore(s.working, s.original, seg, w)
                        last = b
                        s.version++
                    }
                }
            }
    ) {
        s.version // read so brush strokes on the mutable bitmap redraw
        val img = preview
        val scale = minOf(size.width / img.width, size.height / img.height) * 0.92f
        val dw = img.width * scale
        val dh = img.height * scale
        val left = (size.width - dw) / 2f
        val top = (size.height - dh) / 2f
        fit = Rect(left, top, left + dw, top + dh)
        drawImage(
            img.asImageBitmap(),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dw.toInt(), dh.toInt()),
        )
        if (lasso.size > 1) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(lasso.first().x, lasso.first().y)
                lasso.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, Color.White, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            drawPath(path, Accent, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun CutOptions(
    session: EditSession?,
    brush: Brush,
    brushDp: Float,
    onBrush: (Brush) -> Unit,
    onBrushSize: (Float) -> Unit,
    onRemoveBackground: () -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolPill(Icons.Filled.AutoFixHigh, "Remove BG", selected = false, highlight = true, onClick = onRemoveBackground)
        ToolPill(Icons.Filled.Gesture, "Lasso", selected = brush == Brush.LASSO) { onBrush(if (brush == Brush.LASSO) Brush.NONE else Brush.LASSO) }
        ToolPill(Icons.Filled.Brush, "Eraser", selected = brush == Brush.ERASER) { onBrush(if (brush == Brush.ERASER) Brush.NONE else Brush.ERASER) }
        ToolPill(Icons.Filled.Healing, "Restore", selected = brush == Brush.RESTORE) { onBrush(if (brush == Brush.RESTORE) Brush.NONE else Brush.RESTORE) }
    }
    when (brush) {
        Brush.ERASER, Brush.RESTORE -> DarkSlider("Brush size", brushDp, 4f..96f, onBrushSize) { "${it.toInt()} dp" }
        Brush.LASSO -> Hint("Draw around the part to keep. Everything outside is removed.")
        Brush.NONE -> Hint(
            if (session == null) "Loading…"
            else "Remove BG cuts the subject out on-device. Pick a brush to refine; hold the compare chip to see the original."
        )
    }
}

@Composable
private fun ShapeOptions(session: EditSession?, onShape: (CropShape) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CropShape.entries.filter { it != CropShape.NONE }.forEach { shape ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(enabled = session != null) { onShape(shape) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val preview = remember(shape) {
                    val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(bmp).drawPath(
                        ImageOps.shapePath(shape, 96f, 96f),
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C5CFF.toInt() },
                    )
                    bmp.asImageBitmap()
                }
                androidx.compose.foundation.Image(preview, shape.label, Modifier.size(40.dp))
                Text(shape.label, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    Hint("Crop to a shape; for a custom shape use Cut → Lasso. Undo restores the previous cut.")
}

@Composable
private fun EffectsOptions(fx: StickerEffects, opacity: Float, kind: StickerKind, onFx: (StickerEffects) -> Unit, onOpacity: (Float) -> Unit) {
    if (kind != StickerKind.BITMAP) {
        Hint("Outline, shadow and glow apply to still images; tint and opacity work for every format.")
    }
    DarkSlider("Outline", fx.outlineWidth, 0f..8f, { onFx(fx.copy(outlineWidth = it)) }) { "%.1f".format(it) }
    if (fx.outlineWidth > 0f) DarkColor("Outline color", fx.outlineColor) { onFx(fx.copy(outlineColor = it)) }
    DarkSlider("Shadow blur", fx.shadowBlur, 0f..12f, { onFx(fx.copy(shadowBlur = it)) }) { "%.1f".format(it) }
    if (fx.shadowBlur > 0f) {
        DarkSlider("Shadow X", fx.shadowDx, -10f..10f, { onFx(fx.copy(shadowDx = it)) }) { "%.1f".format(it) }
        DarkSlider("Shadow Y", fx.shadowDy, -10f..10f, { onFx(fx.copy(shadowDy = it)) }) { "%.1f".format(it) }
        DarkColor("Shadow color", fx.shadowColor) { onFx(fx.copy(shadowColor = it)) }
    }
    DarkSlider("Glow", fx.glowRadius, 0f..12f, { onFx(fx.copy(glowRadius = it)) }) { "%.1f".format(it) }
    if (fx.glowRadius > 0f) DarkColor("Glow color", fx.glowColor) { onFx(fx.copy(glowColor = it)) }
    DarkSlider("Tint", fx.tintAmount, 0f..1f, { onFx(fx.copy(tintAmount = it)) }) { "%.0f%%".format(it * 100) }
    if (fx.tintAmount > 0f) DarkColor("Tint color", fx.tintColor) { onFx(fx.copy(tintColor = it)) }
    DarkSlider("Opacity", opacity, 0.05f..1f, onOpacity) { "%.0f%%".format(it * 100) }
    Text("Blend mode", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StickerBlend.entries.forEach { b ->
            FilterChip(
                selected = fx.blend == b,
                onClick = { onFx(fx.copy(blend = b)) },
                label = { Text(b.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Accent),
            )
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
internal fun DarkSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, format: (Float) -> String) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = Color.White)) {
            LabeledSlider(label, value, range, onChange, format = format)
        }
    }
}

@Composable
private fun DarkColor(label: String, color: Long, onChange: (Long) -> Unit) {
    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = Color.White)) {
        ColorRow(label, color, onChange)
    }
}

/** Round tray button with a springy selection bump. */
@Composable
private fun TrayButton(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tray")
    Column(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f))
        Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ToolPill(icon: ImageVector, label: String, selected: Boolean, highlight: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "pill")
    Row(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    selected -> Accent
                    highlight -> Accent.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
