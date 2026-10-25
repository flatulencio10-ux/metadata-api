package com.customlauncher

import com.customlauncher.gestures.LetterRecognizer
import com.customlauncher.gestures.Pt
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LetterRecognizerTest {
    /** Builds a stroke through [corners] in screen pixels (y down), with some wobble. */
    private fun stroke(vararg corners: Pair<Float, Float>): List<Pt> =
        corners.toList().zipWithNext().flatMap { (a, b) ->
            (0 until 10).map { i ->
                val t = i / 10f
                Pt(a.first + (b.first - a.first) * t + (i % 3), a.second + (b.second - a.second) * t)
            }
        } + Pt(corners.last().first, corners.last().second)

    @Test fun recognisesL() = assertEquals("L", LetterRecognizer.recognize(stroke(100f to 100f, 100f to 400f, 300f to 400f)))
    @Test fun recognisesM() = assertEquals("M", LetterRecognizer.recognize(stroke(100f to 400f, 100f to 100f, 250f to 280f, 400f to 100f, 400f to 400f)))
    @Test fun recognisesZ() = assertEquals("Z", LetterRecognizer.recognize(stroke(100f to 100f, 400f to 100f, 100f to 400f, 400f to 400f)))
    @Test fun recognisesReversedV() = assertEquals("V", LetterRecognizer.recognize(stroke(400f to 100f, 250f to 400f, 100f to 100f)))
    @Test fun recognisesO() {
        val pts = (0..40).map { i ->
            val a = -Math.PI / 2 - i * 2 * Math.PI / 40
            Pt((200 + 150 * cos(a)).toFloat(), (200 + 150 * sin(a)).toFloat())
        }
        assertEquals("O", LetterRecognizer.recognize(pts))
    }
}
