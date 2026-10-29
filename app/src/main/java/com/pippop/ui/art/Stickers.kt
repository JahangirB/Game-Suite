package com.pippop.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/** One shape of a sticker drawing (see StickerArt): SVG path data and how it's painted. */
internal class Part(
    d: String,
    fill: Long?,
    stroke: Long?,
    val width: Float,
    dash: FloatArray?,
    roundCap: Boolean,
    roundJoin: Boolean,
) {
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
    val fill = fill?.let { Color(it) }
    val stroke = stroke?.let { Color(it) }
    val style = Stroke(
        width = width,
        cap = if (roundCap) StrokeCap.Round else StrokeCap.Butt,
        join = if (roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
        pathEffect = dash?.let { PathEffect.dashPathEffect(it) },
    )
}

private const val GRID = 120f
private val FRAME = Color(0xFF14121C)

/** Draws a sticker's [parts] on its grey card, filling this draw area; stickers don't move. */
internal fun DrawScope.drawSticker(parts: List<Part>, backdrop: Pair<Color, Color>) {
    scale(size.minDimension / GRID, Offset.Zero) {
        val card = Path().apply {
            addRoundRect(RoundRect(0f, 0f, GRID, GRID, CornerRadius(12f)))
        }
        clipPath(card) {
            drawRect(Brush.radialGradient(listOf(backdrop.first, backdrop.second), Offset(GRID / 2, GRID * 0.4f), GRID * 0.75f), size = Size(GRID, GRID))
            parts.forEach { part ->
                part.fill?.let { drawPath(part.path, it) }
                part.stroke?.let { drawPath(part.path, it, style = part.style) }
            }
        }
        drawRoundRect(FRAME, Offset(1.5f, 1.5f), Size(GRID - 3f, GRID - 3f), CornerRadius(11f), style = Stroke(3f))
    }
}
