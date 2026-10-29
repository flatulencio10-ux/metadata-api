package com.customlauncher.widgets.objects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Toys
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customlauncher.physics.BodyState
import com.customlauncher.ui.theme.toColor
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.WidgetConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Physics pose of the node being drawn (null when not simulated). */
val LocalBodyState = compositionLocalOf<BodyState?> { null }

/** Current behaviour state name of the node being drawn ("on", "off", "idle"…). */
val LocalObjectState = compositionLocalOf { "idle" }

/**
 * Original procedurally drawn objects (no image assets): spinner, ball, cloud, pendulum,
 * blob, lever, switch, knob and spawn button. They read their physics pose and behaviour
 * state to animate (spinner zoetrope, lever position…).
 */
object ObjectArtWidget : LauncherWidget {
    override val id = "object_art"
    override val name = "Object"
    override val description = "A built-in interactive object"
    override val icon = Icons.Filled.Toys
    override val defaultSize = DpSize(120.dp, 120.dp)
    override val chromeless = true

    const val ART = "art"
    const val COLOR = "color"
    private val showRpm = ConfigField.Toggle("rpm", "Show rpm counter (spinner)", false)
    private val color = ConfigField.ColorPick(COLOR, "Colour", 0xFF7C5CFF)

    override val configSchema = listOf<ConfigField>(color, showRpm)

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val body = LocalBodyState.current
        val state = LocalObjectState.current
        val tint = config.long(color).toColor()
        val rpmOn = config.bool(showRpm)
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize()) {
            when (config.string(ART)) {
                "spinner" -> {
                    val rpm = abs(body?.rpm ?: 0f)
                    if (rpm > 700f) zoetrope(tint, body!!) else spinner(tint)
                    if (rpmOn && body != null) {
                        // Counter-rotated so the number stays readable while spinning.
                        rotate(-body.rotation) {
                            val text = measurer.measure("${rpm.toInt()}", TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
                        }
                    }
                }
                "ball" -> ball(tint)
                "cloud" -> cloud(tint)
                "pendulum" -> pendulum(tint)
                "blob" -> blob(tint)
                "lever" -> lever(tint, state == "on")
                "switch" -> switch(tint, state == "on")
                "knob" -> knob(tint)
                "spawner" -> spawner(tint)
                else -> ball(tint)
            }
        }
    }

    private fun DrawScope.spinner(c: Color) {
        val r = size.minDimension / 2f
        val lobe = r * 0.34f
        for (i in 0 until 3) {
            val a = -PI / 2 + i * 2 * PI / 3
            val p = center + Offset((cos(a) * r * 0.6f).toFloat(), (sin(a) * r * 0.6f).toFloat())
            drawLine(c.copy(alpha = 0.9f), center, p, strokeWidth = lobe * 1.1f, cap = StrokeCap.Round)
            drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0.7f)), p, lobe), lobe, p)
            drawCircle(Color(0xFF2B2F36), lobe * 0.55f, p)
            drawCircle(Color.White.copy(alpha = 0.35f), lobe * 0.2f, p - Offset(lobe * 0.18f, lobe * 0.18f))
        }
        drawCircle(Color(0xFFDDE3EA), r * 0.22f, center)
        drawCircle(c, r * 0.12f, center)
    }

    /** At high speed the lobes melt into a disc with a 12-frame bouncing figure: a zoetrope. */
    private fun DrawScope.zoetrope(c: Color, body: BodyState) {
        val r = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(listOf(c.copy(alpha = 0.25f), c.copy(alpha = 0.7f), c.copy(alpha = 0.25f))), r * 0.95f, center)
        rotate(-body.rotation) {
            val frame = floor(abs(body.spin) / 30f).toInt() % 12
            val t = frame / 12f
            val h = abs(sin(t * 2 * PI)).toFloat()
            val ground = center.y + r * 0.45f
            val ball = Offset(center.x, ground - r * 0.15f - h * r * 0.55f)
            // Squash on landing, stretch in the air.
            val squash = if (h < 0.15f) 1.35f else 1f
            drawOval(Color.White, topLeft = ball - Offset(r * 0.13f * squash, r * 0.13f / squash), size = Size(r * 0.26f * squash, r * 0.26f / squash))
            drawLine(Color.White.copy(alpha = 0.5f), Offset(center.x - r * 0.4f, ground), Offset(center.x + r * 0.4f, ground), 2.dp.toPx())
        }
        drawCircle(Color(0xFFDDE3EA), r * 0.12f, center)
    }

    private fun DrawScope.ball(c: Color) {
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(c.lighten(), c, c.darken()), center - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r, center)
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.18f, center - Offset(r * 0.35f, r * 0.35f))
    }

    private fun DrawScope.cloud(c: Color) {
        val w = size.width
        val h = size.height
        val shadow = Color.Black.copy(alpha = 0.12f)
        listOf(Offset(0.3f, 0.62f) to 0.26f, Offset(0.52f, 0.45f) to 0.34f, Offset(0.74f, 0.6f) to 0.25f, Offset(0.5f, 0.68f) to 0.27f).forEach { (p, r) ->
            drawCircle(shadow, r * h, Offset(p.x * w, p.y * h + h * 0.06f))
        }
        listOf(Offset(0.3f, 0.6f) to 0.26f, Offset(0.52f, 0.42f) to 0.34f, Offset(0.74f, 0.58f) to 0.25f, Offset(0.5f, 0.66f) to 0.27f).forEach { (p, r) ->
            drawCircle(c, r * h, Offset(p.x * w, p.y * h))
        }
    }

    private fun DrawScope.pendulum(c: Color) {
        val r = size.minDimension / 2f
        // Rod up to the pin (2 heights above the centre), drawn in local space so it swings with the body.
        drawLine(Color(0xFFB0B8C2), center, center - Offset(0f, size.height * 2.5f), 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(c.lighten(), c, c.darken()), center - Offset(r * 0.3f, r * 0.3f), r * 1.3f), r, center)
    }

    private fun DrawScope.blob(c: Color) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            cubicTo(w * 0.95f, h * 0.0f, w * 1.02f, h * 0.55f, w * 0.88f, h * 0.8f)
            cubicTo(w * 0.75f, h * 1.02f, w * 0.25f, h * 1.02f, w * 0.12f, h * 0.8f)
            cubicTo(-w * 0.02f, h * 0.55f, w * 0.05f, h * 0.0f, w * 0.5f, h * 0.04f)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(c.lighten(), c)))
        val eye = size.minDimension * 0.07f
        drawCircle(Color(0xFF2B2F36), eye, Offset(w * 0.38f, h * 0.45f))
        drawCircle(Color(0xFF2B2F36), eye, Offset(w * 0.62f, h * 0.45f))
        drawArc(Color(0xFF2B2F36), 20f, 140f, false, topLeft = Offset(w * 0.42f, h * 0.5f), size = Size(w * 0.16f, h * 0.12f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }

    private fun DrawScope.lever(c: Color, on: Boolean) {
        val w = size.width
        val h = size.height
        val base = Offset(w / 2f, h * 0.82f)
        drawRoundRect(Color(0xFF3A3F47), topLeft = Offset(w * 0.15f, h * 0.75f), size = Size(w * 0.7f, h * 0.2f), cornerRadius = CornerRadius(h * 0.08f))
        val angle = if (on) -30f else 30f
        rotate(angle, base) {
            drawLine(Color(0xFFB0B8C2), base, Offset(base.x, h * 0.15f), w * 0.1f, cap = StrokeCap.Round)
            drawCircle(if (on) c else Color(0xFF7F8C8D), w * 0.16f, Offset(base.x, h * 0.15f))
        }
        drawCircle(Color(0xFF2B2F36), w * 0.08f, base)
    }

    private fun DrawScope.switch(c: Color, on: Boolean) {
        val r = size.height / 2f
        drawRoundRect(if (on) c else Color(0xFF4A4F57), cornerRadius = CornerRadius(r))
        val x = if (on) size.width - r else r
        drawCircle(Color.Black.copy(alpha = 0.2f), r * 0.82f, Offset(x, r + 3f))
        drawCircle(Color.White, r * 0.82f, Offset(x, r))
    }

    private fun DrawScope.knob(c: Color) {
        val r = size.minDimension / 2f
        for (i in 0 until 24) {
            val a = 2 * PI * i / 24
            drawLine(Color.White.copy(alpha = 0.35f), center + Offset((cos(a) * r * 0.86f).toFloat(), (sin(a) * r * 0.86f).toFloat()),
                center + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()), 1.5.dp.toPx())
        }
        drawCircle(Brush.radialGradient(listOf(c.lighten(), c.darken()), center - Offset(r * 0.2f, r * 0.2f), r), r * 0.78f, center)
        drawLine(Color.White, center, center - Offset(0f, r * 0.62f), 3.dp.toPx(), cap = StrokeCap.Round)
    }

    private fun DrawScope.spawner(c: Color) {
        drawRoundRect(c, cornerRadius = CornerRadius(size.minDimension * 0.28f))
        val s = size.minDimension * 0.22f
        drawLine(Color.White, center - Offset(s, 0f), center + Offset(s, 0f), 5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, center - Offset(0f, s), center + Offset(0f, s), 5.dp.toPx(), cap = StrokeCap.Round)
    }

    private fun Color.lighten() = Color(red + (1 - red) * 0.35f, green + (1 - green) * 0.35f, blue + (1 - blue) * 0.35f, alpha)
    private fun Color.darken() = Color(red * 0.6f, green * 0.6f, blue * 0.6f, alpha)
}
