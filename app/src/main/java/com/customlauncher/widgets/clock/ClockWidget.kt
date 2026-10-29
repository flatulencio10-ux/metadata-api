package com.customlauncher.widgets.clock

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customlauncher.ui.theme.toColor
import com.customlauncher.widget.ConfigField
import com.customlauncher.widget.LauncherWidget
import com.customlauncher.widget.WidgetConfig
import com.customlauncher.widget.fitTextSize
import com.customlauncher.widget.rememberNow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Clock with digital, analog and word styles and user-defined format strings. */
object ClockWidget : LauncherWidget {
    override val id = "clock"
    override val name = "Clock"
    override val description = "Digital, analog or word clock with custom format"
    override val icon = Icons.Filled.Schedule
    override val defaultSize = DpSize(320.dp, 140.dp)

    private val style = ConfigField.Choice("style", "Style", listOf("digital" to "Digital", "analog" to "Analog", "word" to "Word clock"))
    private val timeFormat = ConfigField.Text("format", "Time format", default = "HH:mm", hint = "e.g. HH:mm, h:mm a, HH:mm:ss")
    private val dateFormat = ConfigField.Text("dateFormat", "Date format", default = "EEEE, d MMMM", hint = "empty = hide date")
    private val bold = ConfigField.Toggle("bold", "Bold time", default = false)
    private val accent = ConfigField.Toggle("useAccent", "Use custom color", default = false)
    private val color = ConfigField.ColorPick("color", "Custom color", default = 0xFFFFFFFF)
    private val tapOpensAlarms = ConfigField.Toggle("tapAlarms", "Tap opens alarms", default = true)

    override val configSchema = listOf(
        style, timeFormat, dateFormat, bold, accent, color, tapOpensAlarms,
        ConfigField.Info("help", "Formats use java.time patterns: HH hour, mm minutes, ss seconds, a am/pm, EEEE weekday, MMMM month."),
    )

    @Composable
    override fun Content(config: WidgetConfig, size: DpSize) {
        val context = LocalContext.current
        val pattern = config.string(timeFormat).ifBlank { "HH:mm" }
        val showsSeconds = config.string(style) == "analog" || pattern.contains('s')
        val now = rememberNow(if (showsSeconds) 1_000 else 10_000)
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
        val tint = if (config.bool(accent)) config.long(color).toColor() else LocalContentColor.current

        val modifier = Modifier
            .fillMaxSize()
            .then(
                if (config.bool(tapOpensAlarms)) Modifier.clickable {
                    runCatching {
                        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else Modifier
            )

        Box(modifier, contentAlignment = Alignment.Center) {
            when (config.string(style)) {
                "analog" -> AnalogClock(time, tint)
                "word" -> Text(
                    wordTime(time.hour, time.minute),
                    color = tint,
                    textAlign = TextAlign.Center,
                    fontWeight = if (config.bool(bold)) FontWeight.Bold else FontWeight.Light,
                    fontSize = fitTextSize(size, 11, heightFraction = 0.2f),
                    lineHeight = fitTextSize(size, 11, heightFraction = 0.24f),
                )
                else -> DigitalClock(time, pattern, config.string(dateFormat), config.bool(bold), tint, size)
            }
        }
    }

    @Composable
    private fun DigitalClock(time: LocalDateTime, pattern: String, datePattern: String, bold: Boolean, tint: Color, size: DpSize) {
        val text = format(time, pattern)
        val date = if (datePattern.isBlank()) "" else format(time, datePattern)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text,
                color = tint,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Light,
                fontSize = fitTextSize(size, text.length, heightFraction = if (date.isEmpty()) 0.6f else 0.45f),
                maxLines = 1,
            )
            if (date.isNotEmpty()) {
                Text(date, color = tint.copy(alpha = 0.85f), fontSize = fitTextSize(size, date.length, 0.13f, 1.9f).coerceAtMost16(), maxLines = 1)
            }
        }
    }

    private fun androidx.compose.ui.unit.TextUnit.coerceAtMost16() = if (value > 22f) 22.sp else this

    private fun format(time: LocalDateTime, pattern: String): String = try {
        time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    } catch (e: IllegalArgumentException) {
        "Bad format"
    }

    @Composable
    private fun AnalogClock(time: LocalDateTime, tint: Color) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val r = min(size.width, size.height) / 2f
            val c = center
            drawCircle(tint.copy(alpha = 0.25f), r, c, style = Stroke(width = r * 0.04f))
            for (i in 0 until 12) {
                val a = i * PI / 6
                val inner = if (i % 3 == 0) 0.78f else 0.86f
                drawLine(tint.copy(alpha = 0.7f), c + polar(a, r * inner), c + polar(a, r * 0.95f), strokeWidth = r * 0.03f, cap = StrokeCap.Round)
            }
            val sec = time.second
            val min = time.minute + sec / 60f
            val hour = time.hour % 12 + min / 60f
            hand(tint, c, hour / 12.0 * 2 * PI, r * 0.5f, r * 0.07f)
            hand(tint, c, min / 60.0 * 2 * PI, r * 0.75f, r * 0.045f)
            hand(Color(0xFFDA4453), c, sec / 60.0 * 2 * PI, r * 0.85f, r * 0.02f)
            drawCircle(tint, r * 0.05f, c)
        }
    }

    /** Angle 0 = 12 o'clock, clockwise. */
    private fun polar(angle: Double, length: Float) =
        Offset((sin(angle) * length).toFloat(), (-cos(angle) * length).toFloat())

    private fun DrawScope.hand(color: Color, c: Offset, angle: Double, length: Float, width: Float) {
        drawLine(color, c, c + polar(angle, length), strokeWidth = width, cap = StrokeCap.Round)
    }

    private val HOURS = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")

    /** Fuzzy English word clock rounded to five minutes, e.g. "it is quarter past ten". */
    fun wordTime(hour: Int, minute: Int): String {
        val rounded = ((minute + 2) / 5) * 5
        val h = if (rounded > 30) hour + 1 else hour
        val name = HOURS[h % 12]
        val phrase = when (rounded) {
            0, 60 -> "$name o'clock"
            5 -> "five past $name"
            10 -> "ten past $name"
            15 -> "quarter past $name"
            20 -> "twenty past $name"
            25 -> "twenty-five past $name"
            30 -> "half past $name"
            35 -> "twenty-five to $name"
            40 -> "twenty to $name"
            45 -> "quarter to $name"
            50 -> "ten to $name"
            else -> "five to $name"
        }
        return "It is\n$phrase"
    }
}
