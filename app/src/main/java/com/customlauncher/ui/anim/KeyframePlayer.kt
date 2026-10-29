package com.customlauncher.ui.anim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.customlauncher.core.anim.KeyframeMath
import com.customlauncher.core.model.KProp
import com.customlauncher.core.model.KeyframeClip
import com.customlauncher.core.model.Node
import com.customlauncher.objects.NodeRuntime

/** A sampled clip pose (property → value); null means "rest pose". */
typealias ClipPose = Map<KProp, Float>

/** A pose plus the tint colour of the clip it came from. */
data class ActivePose(val tint: Long, val pose: ClipPose)

/**
 * Plays the clip named in [NodeRuntime.clip] (set by behaviours / scripts) and returns the
 * current pose holder. The holder is read inside graphics layers, so playback never
 * recomposes the node.
 */
@Composable
fun rememberClipPlayback(node: Node, rt: NodeRuntime?): MutableState<ActivePose?> {
    val pose = remember(node.id) { mutableStateOf<ActivePose?>(null) }
    val request = rt?.clip
    LaunchedEffect(request, node.clips) {
        val clip = request?.let { r -> node.clips.firstOrNull { it.name == r.first } } ?: run { pose.value = null; return@LaunchedEffect }
        play(clip) { pose.value = ActivePose(clip.tintColor, it) }
        pose.value = null
    }
    return pose
}

/** Runs [clip] to completion (honouring repeat; -1 loops until cancelled). */
suspend fun play(clip: KeyframeClip, onPose: (ClipPose) -> Unit) {
    val duration = clip.durationMs.coerceAtLeast(1)
    var plays = 0
    while (clip.repeat < 0 || plays <= clip.repeat) {
        val start = withFrameNanos { it }
        var t = 0f
        while (t < duration) {
            t = (withFrameNanos { it } - start) / 1_000_000f
            onPose(KeyframeMath.sample(clip, t.coerceAtMost(duration.toFloat())))
        }
        plays++
    }
}

/**
 * Applies a clip pose on top of the node: move, rotate, scale, opacity, skew, colour tint
 * and blur (blur needs Android 12+). [pose] is read per frame inside the layer.
 */
fun Modifier.clipPose(active: () -> ActivePose?): Modifier = this
    .graphicsLayer {
        val p = active()?.pose ?: return@graphicsLayer
        translationX = p.getValue(KProp.X).dp.toPx()
        translationY = p.getValue(KProp.Y).dp.toPx()
        rotationZ = p.getValue(KProp.ROTATION)
        scaleX = p.getValue(KProp.SCALE)
        scaleY = p.getValue(KProp.SCALE)
        alpha = p.getValue(KProp.OPACITY).coerceIn(0f, 1f)
        val blur = p.getValue(KProp.BLUR).dp.toPx()
        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        // Tint needs its own layer so SrcAtop only colours the node's own pixels.
        compositingStrategy = if (p.getValue(KProp.TINT) > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        val a = active()
        val p = a?.pose
        if (p == null) {
            drawContent()
            return@drawWithContent
        }
        val skew = p.getValue(KProp.SKEW_X)
        if (skew != 0f) {
            withTransform({
                // x' = x + skew·(y − h/2): leans the node around its horizontal centre line.
                transform(androidx.compose.ui.graphics.Matrix().apply {
                    values[androidx.compose.ui.graphics.Matrix.SkewX] = skew
                    values[androidx.compose.ui.graphics.Matrix.TranslateX] = -size.height / 2f * skew
                })
            }) { this@drawWithContent.drawContent() }
        } else {
            drawContent()
        }
        val tint = p.getValue(KProp.TINT).coerceIn(0f, 1f)
        if (tint > 0f) drawRect(Color(a.tint).copy(alpha = tint), blendMode = BlendMode.SrcAtop)
    }
