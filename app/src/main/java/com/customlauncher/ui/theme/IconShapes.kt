package com.customlauncher.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.customlauncher.core.model.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Superellipse (|x|^n + |y|^n = 1) with n = 4: the classic "squircle". */
val SquircleShape: Shape = GenericShape { size, _ ->
    val n = 4.0
    val steps = 64
    val rx = size.width / 2f
    val ry = size.height / 2f
    for (i in 0..steps) {
        val t = 2 * Math.PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = (abs(c).pow(2 / n) * sign(c)).toFloat() * rx + rx
        val y = (abs(s).pow(2 / n) * sign(s)).toFloat() * ry + ry
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val TeardropShape: Shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 12)

fun IconShape.toShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.SQUARE -> RoundedCornerShape(12)
    IconShape.TEARDROP -> TeardropShape
    IconShape.NONE -> RectangleShape
}
