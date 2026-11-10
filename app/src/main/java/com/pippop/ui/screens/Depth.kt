package com.pippop.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Depth and materials: the few shapes every board uses to look like something you could pick
// up. Light comes from the top left; shadows fall straight down.

/** [this] mixed towards white by [amount] (0 to 1). */
fun Color.lighter(amount: Float) = lerp(this, Color.White, amount)

/** [this] mixed towards black by [amount] (0 to 1). */
fun Color.darker(amount: Float) = lerp(this, Color.Black, amount)

/** The colour of a soft shadow on any surface. */
private val Shade = Color.Black

/** A soft shadow under a round thing of [radius] at [center], dropped by [drop]. */
fun DrawScope.roundShadow(center: Offset, radius: Float, drop: Float = radius * 0.16f, strength: Float = 0.22f) {
    drawCircle(Shade.copy(alpha = strength), radius * 1.04f, center + Offset(0f, drop))
}

/** A soft shadow under a rounded box. */
fun DrawScope.boxShadow(topLeft: Offset, size: Size, corner: Float, drop: Float, strength: Float = 0.2f) {
    drawRoundRect(Shade.copy(alpha = strength), topLeft + Offset(0f, drop), size, CornerRadius(corner))
}

/**
 * A glossy disc or ball in [color]: lit from the top left, darker towards its lower edge.
 * [alpha] fades the whole disc.
 */
fun DrawScope.glossyDisc(color: Color, radius: Float, center: Offset, alpha: Float = 1f) {
    val light = center + Offset(-radius * 0.35f, -radius * 0.4f)
    drawCircle(
        Brush.radialGradient(
            0f to color.lighter(0.55f), 0.5f to color, 1f to color.darker(0.26f),
            center = light, radius = radius * 1.45f,
        ),
        radius, center, alpha = alpha,
    )
}

/**
 * A chunky tile: a darker edge showing under it by [lift], and a face a little lighter at the
 * top. Returns nothing; draw the tile's content over it.
 */
fun DrawScope.raisedTile(color: Color, topLeft: Offset, size: Size, corner: Float, lift: Float) {
    drawRoundRect(color.darker(0.3f), topLeft + Offset(0f, lift), size, CornerRadius(corner))
    drawRoundRect(
        Brush.verticalGradient(listOf(color.lighter(0.18f), color), startY = topLeft.y, endY = topLeft.y + size.height),
        topLeft, size, CornerRadius(corner),
    )
}

/** A small burst of rays round [center]: [progress] 0 (just begun) to 1 (gone). */
fun DrawScope.burst(center: Offset, progress: Float, color: Color, reach: Float, rays: Int = 10, width: Float = reach * 0.09f) {
    if (progress <= 0f || progress >= 1f) return
    for (i in 0 until rays) {
        val a = i * 2 * PI.toFloat() / rays
        val dir = Offset(cos(a), sin(a))
        drawLine(color.copy(alpha = 1f - progress), center + dir * (reach * progress), center + dir * (reach * (progress + 0.35f)), width, StrokeCap.Round)
    }
}
