package com.customlauncher.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.customlauncher.core.model.PageTransition
import kotlin.math.abs

/**
 * Per-page transition effect. [offset] returns the page's distance from the settled position:
 * 0 = fully visible, +1 = one page to the left (scrolled past), -1 = one page to the right.
 * The pager already translates pages horizontally; each effect adds to or cancels that.
 * [offset] is read inside the graphics layer, so scrolling only redraws (no recomposition).
 */
fun Modifier.pageTransition(effect: PageTransition, offset: () -> Float): Modifier = graphicsLayer {
    val o = offset().coerceIn(-1f, 1f)
    val a = abs(o)
    when (effect) {
        PageTransition.SLIDE -> Unit
        PageTransition.FADE -> {
            translationX = o * size.width
            alpha = 1f - a
        }
        PageTransition.ZOOM -> {
            val scale = 1f - 0.3f * a
            scaleX = scale
            scaleY = scale
            alpha = 1f - a * 0.7f
        }
        PageTransition.CUBE -> {
            cameraDistance = 12f * density
            transformOrigin = TransformOrigin(if (o < 0f) 0f else 1f, 0.5f)
            rotationY = -90f * o
        }
        PageTransition.STACK -> if (o > 0f) {
            // Outgoing page stays in place, shrinks and fades; the incoming one slides over it.
            translationX = o * size.width
            val scale = 1f - 0.2f * a
            scaleX = scale
            scaleY = scale
            alpha = 1f - a
        }
    }
}
