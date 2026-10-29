package com.customlauncher.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Current time, updated every [periodMs] aligned to the period boundary, and only while the
 * widget is active (on screen, launcher resumed). Off-screen widgets therefore cost nothing.
 */
@Composable
fun rememberNow(periodMs: Long): Long {
    val active = LocalWidgetHost.current.isActive
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, periodMs) {
        if (!active) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

/**
 * Runs [block] immediately and then every [periodMs] while the widget is active.
 * Use for polling data sources (stats, calendar).
 */
@Composable
fun PollWhileActive(periodMs: Long, vararg keys: Any?, block: suspend () -> Unit) {
    val active = LocalWidgetHost.current.isActive
    LaunchedEffect(active, periodMs, *keys) {
        if (!active) return@LaunchedEffect
        while (true) {
            block()
            delay(periodMs)
        }
    }
}

/** Font size that fits [chars] characters on one line inside [size], capped by height. */
fun fitTextSize(size: DpSize, chars: Int, heightFraction: Float = 0.5f, widthFactor: Float = 1.7f): TextUnit {
    val byHeight = size.height.value * heightFraction
    val byWidth = size.width.value / chars.coerceAtLeast(1) * widthFactor
    return minOf(byHeight, byWidth).coerceAtLeast(8f).sp
}
