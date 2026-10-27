package com.arcade.sudoku.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shapes that go with colours, so pieces and players can be told apart without seeing colour:
 * each piece colour always carries the same mark in every game, and each seat has its own.
 */
enum class Mark { Circle, Triangle, Square, Diamond, Star, Cross }

/** The mark of a piece colour: red ▲, yellow ★, green ✚, blue ■, purple ◆, orange ●. */
fun markOf(color: Color): Mark? = when (color) {
    Pieces.Red -> Mark.Triangle
    Pieces.Yellow -> Mark.Star
    Pieces.Green -> Mark.Cross
    Pieces.Blue -> Mark.Square
    Pieces.Purple -> Mark.Diamond
    Pieces.Orange -> Mark.Circle
    else -> null
}

/** A seat's mark: you (0) ●, then ▲, ■ and ◆ for the others in seat order. */
fun seatMark(seat: Int): Mark = when (seat) {
    0 -> Mark.Circle
    1 -> Mark.Triangle
    2 -> Mark.Square
    else -> Mark.Diamond
}

/** The colour marks are drawn in on top of a piece: dark, so they show on every piece colour. */
val MarkInk = Board.Ink.copy(alpha = 0.75f)

/** Draws [mark] filling a square of side [size] centred on [center]. */
fun DrawScope.drawMark(mark: Mark, center: Offset, size: Float, color: Color = MarkInk) {
    val r = size / 2
    when (mark) {
        Mark.Circle -> drawCircle(color, r * 0.8f, center)
        Mark.Square -> drawRect(color, Offset(center.x - r * 0.75f, center.y - r * 0.75f), Size(r * 1.5f, r * 1.5f))
        Mark.Triangle -> drawPath(polygon(center, r, 3, -90f), color)
        Mark.Diamond -> drawPath(polygon(center, r, 4, -90f), color)
        Mark.Star -> drawPath(star(center, r), color)
        Mark.Cross -> {
            val t = r * 0.36f
            drawRect(color, Offset(center.x - t, center.y - r), Size(t * 2, r * 2))
            drawRect(color, Offset(center.x - r, center.y - t), Size(r * 2, t * 2))
        }
    }
}

/** [mark] on its own, for a peg, a card corner or an avatar badge. */
@Composable
fun MarkIcon(mark: Mark, modifier: Modifier = Modifier, color: Color = MarkInk) {
    Canvas(modifier) { drawMark(mark, center, size.minDimension, color) }
}

private fun polygon(c: Offset, r: Float, sides: Int, startDeg: Float) = Path().apply {
    for (i in 0 until sides) {
        val a = Math.toRadians((startDeg + i * 360f / sides).toDouble())
        val x = c.x + r * cos(a).toFloat()
        val y = c.y + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun star(c: Offset, r: Float) = Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians((-90.0 + i * 36.0))
        val x = c.x + rr * cos(a).toFloat()
        val y = c.y + rr * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
