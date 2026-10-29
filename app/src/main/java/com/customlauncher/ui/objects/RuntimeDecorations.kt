package com.customlauncher.ui.objects

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.anim.MotionMath
import com.customlauncher.core.model.Node
import com.customlauncher.objects.NodeRuntime
import com.customlauncher.objects.SpriteRun
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widgets.sticker.StickerRenderer
import com.customlauncher.widgets.sticker.StickerWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Extra offset from behaviours (move-to, paths, orbits, follow finger), applied outside the node transform. */
fun Modifier.runtimeOffset(rt: NodeRuntime?): Modifier =
    if (rt == null) this else this.graphicsLayer {
        translationX = rt.dx.dp.toPx()
        translationY = rt.dy.dp.toPx()
    }

/**
 * Draws a node's behaviour-driven extras around [content]: state image (cross-faded with the
 * state's transition time), triggered reactions, speech bubble, particle bursts and sprites.
 * [content] receives the node to draw (with the current state's image swapped in).
 */
@Composable
fun RuntimeDecorations(node: Node, rt: NodeRuntime?, content: @Composable (Node) -> Unit) {
    if (rt == null) {
        content(node)
        return
    }
    val effect = remember { Animatable(1f) }
    LaunchedEffect(rt.effect) {
        val e = rt.effect ?: return@LaunchedEffect
        effect.snapTo(0f)
        effect.animateTo(1f, tween(500, easing = LinearEasing))
        if (rt.effect == e) Unit
    }
    val spriteActive = rt.sprite != null

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val m = rt.effect?.let { (kind, _) -> if (effect.value < 1f) MotionMath.tap(kind, effect.value) else null }
                translationX = (m?.dx ?: 0f).dp.toPx()
                translationY = (m?.dy ?: 0f).dp.toPx()
                scaleX = (m?.scaleX ?: 1f) * rt.scale
                scaleY = (m?.scaleY ?: 1f) * rt.scale
                rotationZ = (m?.rotation ?: 0f) + rt.extraRotation
                alpha = if (spriteActive) 0f else (m?.alpha ?: 1f)
            }
    ) {
        Crossfade(targetState = rt.image, animationSpec = tween(rt.transitionMs), label = "state") { image ->
            val shown = if (image != null && node.type == StickerWidget.id) {
                node.copy(config = WidgetConfig(node.config).with(StickerWidget.FILE, image).json)
            } else node
            content(shown)
        }
    }
    rt.sprite?.let { SpriteOverlay(it) }
    if (rt.bursts.isNotEmpty()) BurstOverlay(rt)
    rt.bubble?.let { SpeechBubble(it) }
}

@Composable
private fun SpeechBubble(text: String) {
    Box(Modifier.fillMaxSize().wrapContentSize(Alignment.TopCenter)) {
        Text(
            text,
            color = Color(0xFF15171A),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .layout { measurable, constraints ->
                    // Sit above the object, unconstrained by its size.
                    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = 260.dp.roundToPx()))
                    layout(0, 0) { p.place(-p.width / 2 + constraints.maxWidth / 2, -p.height - 8.dp.roundToPx()) }
                }
                .background(Color.White, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Particle bursts: dots flying outward with gravity, fading over ~1.2 s. */
@Composable
private fun BurstOverlay(rt: NodeRuntime) {
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(rt.bursts.size) {
        while (rt.bursts.isNotEmpty()) withFrameMillis { now = SystemClock.uptimeMillis() }
    }
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        rt.bursts.forEach { b ->
            val t = ((now - b.startMs) / 1200f).coerceIn(0f, 1f)
            val rnd = Random(b.seed)
            repeat(b.count) {
                val angle = rnd.nextFloat() * 6.283f
                val speed = (0.4f + rnd.nextFloat()) * size.minDimension * b.spread
                val p = center + Offset(cos(angle) * speed * t, sin(angle) * speed * t + 300f * t * t)
                drawCircle(Color(b.color.toInt()).copy(alpha = 1f - t), (2.5f + rnd.nextFloat() * 2f).dp.toPx(), p)
            }
        }
    }
}

/** Draws the current frame of a sprite sheet from the sticker library. */
@Composable
private fun SpriteOverlay(sprite: SpriteRun) {
    val context = LocalContext.current
    val sheet by produceState<ImageBitmap?>(null, sprite.file) {
        val file = context.container.stickerRepository.fileOf(sprite.file)
        value = withContext(Dispatchers.IO) { StickerRenderer.decodeAtSize(file, 1024, 1024)?.asImageBitmap() }
    }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(sprite) { while (true) withFrameMillis { now = SystemClock.uptimeMillis() } }
    val image = sheet ?: return
    Canvas(Modifier.fillMaxSize()) {
        val frames = (sprite.cols * sprite.rows).coerceAtLeast(1)
        val frame = (((now - sprite.startMs) / 1000f * sprite.fps).toInt()) % frames
        drawSpriteFrame(image, frame, sprite.cols, sprite.rows)
    }
}

/** Draws [frame] of a cols×rows sheet fitted into the canvas. Shared with the zoetrope mode. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpriteFrame(image: ImageBitmap, frame: Int, cols: Int, rows: Int) {
    val fw = image.width / cols.coerceAtLeast(1)
    val fh = image.height / rows.coerceAtLeast(1)
    val col = frame % cols.coerceAtLeast(1)
    val row = frame / cols.coerceAtLeast(1)
    val scale = minOf(size.width / fw, size.height / fh)
    val dw = (fw * scale).toInt()
    val dh = (fh * scale).toInt()
    drawImage(
        image,
        srcOffset = IntOffset(col * fw, row * fh),
        srcSize = IntSize(fw, fh),
        dstOffset = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
        dstSize = IntSize(dw, dh),
    )
}
