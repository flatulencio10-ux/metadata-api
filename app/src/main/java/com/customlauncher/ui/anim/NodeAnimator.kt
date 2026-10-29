package com.customlauncher.ui.anim

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.customlauncher.container
import com.customlauncher.core.anim.Motion
import com.customlauncher.core.anim.MotionMath
import com.customlauncher.core.model.EntranceEffect
import com.customlauncher.core.model.EntranceTrigger
import com.customlauncher.core.model.IdleEffect
import com.customlauncher.core.model.NodeAnimations
import com.customlauncher.core.model.TapEffect
import com.customlauncher.runtime.Tilt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Lets node content (e.g. a sticker) trigger the node's tap animation. */
class NodeAnimController {
    /** Increments on every tap; stickers use it to replay a GIF once. */
    var tapCount by mutableIntStateOf(0)
    var onTap: () -> Unit = {}
    /** True when tapping the node does something (animation or action). */
    var tapEnabled by mutableStateOf(false)
    /** Animated stickers stay on their first frame and play once per tap. */
    var playGifOnce by mutableStateOf(false)
}

val LocalNodeAnim = compositionLocalOf<NodeAnimController?> { null }

/** Distance of this node's page from the settled position (parallax); 0 for pinned layers. */
val LocalPageOffset = compositionLocalOf<() -> Float> { { 0f } }

/**
 * Plays a node's entrance / idle / tap animations and parallax around [content].
 * All per-frame values are read inside graphicsLayer, so animating never recomposes.
 * Idle loops stop when [active] is false (off screen, launcher paused) or in battery saver.
 */
@Composable
fun NodeAnimator(
    anim: NodeAnimations?,
    active: Boolean,
    modifier: Modifier = Modifier,
    /** Changing this replays the entrance (editor preview). */
    replayKey: Int = 0,
    particleSource: File? = null,
    /** Taps that arrive through the object layer (behaviours) instead of the sticker itself. */
    externalTapKey: Int = 0,
    content: @Composable () -> Unit,
) {
    val controller = remember { NodeAnimController() }
    LaunchedEffect(externalTapKey) { if (externalTapKey > 0) controller.onTap() }
    if (anim == null || anim.isEmpty) {
        CompositionLocalProvider(LocalNodeAnim provides controller) { Box(modifier) { content() } }
        return
    }
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val entrance = remember { Animatable(1f) }
    val tap = remember { Animatable(1f) }
    val idleSeconds = remember { mutableFloatStateOf(0f) }
    val allowed by c.powerGovernor.continuousAllowed.collectAsState()
    val pageOffset = LocalPageOffset.current
    val tilt: State<Tilt?> = if (anim.tiltParallax && active) c.tiltSensor.tilt.collectAsState(null) else remember { mutableStateOf(null) }

    suspend fun playEntrance() {
        if (anim.entrance == EntranceEffect.NONE) return
        val t = anim.entranceTiming
        repeat(if (t.repeat < 0) 50 else t.repeat + 1) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(t.durationMs, t.delayMs, LinearEasing))
        }
    }

    LaunchedEffect(active, anim.entrance, anim.entranceTrigger, replayKey) {
        if (active && (anim.entranceTrigger == EntranceTrigger.PAGE_SHOWN || replayKey > 0)) playEntrance()
    }
    LaunchedEffect(anim.entrance, anim.entranceTrigger, active) {
        if (!active) return@LaunchedEffect
        c.animationSignals.events.collect { if (it == anim.entranceTrigger) playEntrance() }
    }
    LaunchedEffect(active, allowed, anim.idle, anim.idleTiming) {
        if (!active || !allowed || anim.idle == IdleEffect.NONE) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { idleSeconds.floatValue = (it - start) / 1e9f }
    }
    controller.tapEnabled = anim.tap != TapEffect.NONE || anim.tapAction != com.customlauncher.core.model.Action.None
    controller.playGifOnce = anim.tap == TapEffect.PLAY_GIF_ONCE
    controller.onTap = {
        controller.tapCount++
        if (anim.tapAction != com.customlauncher.core.model.Action.None) c.actionExecutor.run(anim.tapAction)
        if (anim.tap != TapEffect.NONE && anim.tap != TapEffect.PLAY_GIF_ONCE) scope.launch {
            val t = anim.tapTiming
            repeat((t.repeat.coerceAtLeast(0)) + 1) {
                tap.snapTo(0f)
                tap.animateTo(1f, tween(t.durationMs, t.delayMs, LinearEasing))
            }
        }
    }

    // Explode: particles start at the sticker's own pixels, fly out and come back.
    val particles by produceState<List<Particle>>(emptyList(), anim.tap, particleSource?.path) {
        value = if (anim.tap == TapEffect.EXPLODE) withContext(Dispatchers.IO) { Particle.sample(particleSource) } else emptyList()
    }

    fun motion(): Motion {
        var m = Motion.NONE
        if (anim.entrance != EntranceEffect.NONE) {
            m += MotionMath.entrance(anim.entrance, MotionMath.ease(anim.entranceTiming.easing, entrance.value), anim.entranceFrom)
        }
        if (anim.idle != IdleEffect.NONE) {
            m += MotionMath.idle(anim.idle, idleSeconds.floatValue, anim.idleTiming.durationMs, anim.idleAmount, anim.driftPath)
        }
        if (anim.tap != TapEffect.NONE && tap.value < 1f) m += MotionMath.tap(anim.tap, tap.value)
        if (anim.parallaxDepth > 0f) {
            val t = tilt.value
            m += Motion(
                dx = pageOffset() * anim.parallaxDepth * 90f + (t?.x ?: 0f) * anim.parallaxDepth * 28f,
                dy = (t?.y ?: 0f) * anim.parallaxDepth * 28f,
            )
        }
        return m
    }

    CompositionLocalProvider(LocalNodeAnim provides controller) {
        Box(
            modifier
                .graphicsLayer {
                    val m = motion()
                    translationX = m.dx.dp.toPx()
                    translationY = m.dy.dp.toPx()
                    scaleX = m.scaleX
                    scaleY = m.scaleY
                    rotationZ = m.rotation
                    alpha = m.alpha.coerceIn(0f, 1f)
                    transformOrigin = if (anim.idle == IdleEffect.SWAY) TransformOrigin(anim.swayPivotX, anim.swayPivotY) else TransformOrigin.Center
                }
                .drawWithContent {
                    val reveal = if (anim.entrance == EntranceEffect.WIPE || anim.entrance == EntranceEffect.TYPEWRITER) motion().reveal else 1f
                    if (reveal >= 1f) drawContent() else clipRect(right = size.width * reveal) { this@drawWithContent.drawContent() }
                }
        ) {
            content()
        }
        if (anim.tap == TapEffect.EXPLODE && particles.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val p = tap.value
                if (p <= 0f || p >= 1f) return@Canvas
                // Out for the first half, back for the second.
                val travel = if (p < 0.5f) MotionMath.ease(com.customlauncher.core.model.Easing.EASE, p * 2f) else MotionMath.ease(com.customlauncher.core.model.Easing.EASE, (1f - p) * 2f)
                val r = 2.2.dp.toPx()
                particles.forEach { q ->
                    val home = Offset(q.u * size.width, q.v * size.height)
                    val d = q.speed * travel * size.minDimension
                    drawCircle(q.color, r, home + Offset(cos(q.angle) * d, sin(q.angle) * d))
                }
            }
        }
    }
}

/** One particle of the explode effect: home position (0..1), colour and flight direction. */
data class Particle(val u: Float, val v: Float, val color: Color, val angle: Float, val speed: Float) {
    companion object {
        fun sample(file: File?): List<Particle> {
            val rnd = Random(42)
            val bmp = file?.let { f ->
                val o = BitmapFactory.Options().apply { inSampleSize = 8 }
                runCatching { BitmapFactory.decodeFile(f.path, o) }.getOrNull()
            }
            if (bmp == null) {
                return List(80) {
                    Particle(rnd.nextFloat(), rnd.nextFloat(), Color.hsv(rnd.nextFloat() * 360f, 0.6f, 1f), rnd.nextFloat() * 6.283f, 0.3f + rnd.nextFloat() * 0.7f)
                }
            }
            val out = ArrayList<Particle>()
            val step = maxOf(1, (bmp.width * bmp.height / 260))
            var i = 0
            while (i < bmp.width * bmp.height) {
                val x = i % bmp.width
                val y = i / bmp.width
                val px = bmp.getPixel(x, y)
                if ((px ushr 24) > 40) {
                    out += Particle(x / bmp.width.toFloat(), y / bmp.height.toFloat(), Color(px), rnd.nextFloat() * 6.283f, 0.25f + rnd.nextFloat() * 0.75f)
                }
                i += step
            }
            bmp.recycle()
            return out
        }
    }
}
