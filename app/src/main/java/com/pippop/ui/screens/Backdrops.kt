package com.pippop.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Backdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A soft tint and a faint pattern from the game's world behind a game screen (tiles for
 * Dominoes, clouds for Balloon Words, discs for Connect Four). The marks and shapes are built once
 * and kept in the cache (the pattern is redrawn only when the size or theme changes), it never
 * animates and takes no touches. The game is found from its colour, which is unique
 * per game; an unknown colour gets no backdrop.
 */
@Composable
fun Modifier.gameBackdrop(glow: Color): Modifier {
    val kind = GameKind.entries.firstOrNull { it.color == glow } ?: return this
    val p = AppTheme.palette
    val tint = Backdrop.tint(p.background, glow, p.dark)
    val mark = Backdrop.mark(glow, p.dark)
    return drawWithCache {
        val cell = 46.dp.toPx()
        val w = 1.5.dp.toPx()
        val line = Stroke(w)
        val shape = shapeOf(kind, cell * 0.5f)
        onDrawBehind {
            drawRect(tint)
            var row = 0
            var y = cell / 2
            while (y < size.height + cell) {
                // Every other row shifts half a cell, so the pattern doesn't read as a table.
                var x = (if (row % 2 == 0) cell / 2 else cell)
                while (x < size.width + cell) {
                    motif(kind, Offset(x, y), cell * 0.5f, mark, w, line, shape, row, (x / cell).toInt())
                    x += cell
                }
                y += cell
                row++
            }
        }
    }
}

/** The one path a mark needs (waves, star, scribble), built at the origin once; null for the other games. */
private fun shapeOf(kind: GameKind, r: Float): Path? = when (kind) {
    GameKind.SHIPS -> Path().apply {
        moveTo(-r * 0.8f, 0f)
        quadraticTo(-r * 0.4f, -r * 0.6f, 0f, 0f)
        quadraticTo(r * 0.4f, r * 0.6f, r * 0.8f, 0f)
    }
    GameKind.LUDO -> Path().apply {
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val d = if (i % 2 == 0) r * 0.7f else r * 0.3f
            val x = (d * cos(a)).toFloat(); val y = (d * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    GameKind.DRAW -> Path().apply {
        moveTo(-r * 0.8f, r * 0.3f)
        lineTo(-r * 0.4f, -r * 0.4f); lineTo(0f, r * 0.3f)
        lineTo(r * 0.4f, -r * 0.4f); lineTo(r * 0.8f, r * 0.3f)
    }
    else -> null
}

/** One mark of [kind]'s pattern, [r] is half its width, centred on [c]; [shape] is from [shapeOf]. */
private fun DrawScope.motif(kind: GameKind, c: Offset, r: Float, m: Color, w: Float, line: Stroke, shape: Path?, row: Int, col: Int) {
    val tl = Offset(c.x - r * 0.6f, c.y - r * 0.6f)
    val box = Size(r * 1.2f, r * 1.2f)
    val corner = CornerRadius(r * 0.25f)
    fun seg(ax: Float, ay: Float, bx: Float, by: Float) = drawLine(m, Offset(c.x + ax * r, c.y + ay * r), Offset(c.x + bx * r, c.y + by * r), w)
    when (kind) {
        GameKind.SUDOKU -> {
            drawRect(m, tl, box, style = line)
            seg(-0.2f, -0.6f, -0.2f, 0.6f); seg(0.2f, -0.6f, 0.2f, 0.6f)
            seg(-0.6f, -0.2f, 0.6f, -0.2f); seg(-0.6f, 0.2f, 0.6f, 0.2f)
        }
        GameKind.CONNECT4 -> {
            drawCircle(m, r * 0.6f, c, style = line)
            drawCircle(m, r * 0.3f, c)
        }
        GameKind.DOMINOES -> {
            drawRoundRect(m, Offset(c.x - r * 0.9f, c.y - r * 0.5f), Size(r * 1.8f, r), corner, style = line)
            seg(0f, -0.5f, 0f, 0.5f)
            drawCircle(m, w, Offset(c.x - r * 0.45f, c.y)); drawCircle(m, w, Offset(c.x + r * 0.45f, c.y))
        }
        GameKind.BALLOON -> {
            // A cloud: three round humps on a flat base.
            drawCircle(m, r * 0.35f, Offset(c.x - r * 0.4f, c.y + r * 0.1f))
            drawCircle(m, r * 0.45f, Offset(c.x, c.y - r * 0.1f))
            drawCircle(m, r * 0.35f, Offset(c.x + r * 0.45f, c.y + r * 0.1f))
            drawRect(m, Offset(c.x - r * 0.4f, c.y + r * 0.1f), Size(r * 0.85f, r * 0.35f))
        }
        GameKind.SHIPS -> translate(c.x, c.y) { drawPath(shape!!, m, style = line) }
        GameKind.LUDO -> translate(c.x, c.y) { drawPath(shape!!, m) }
        GameKind.WORDS -> {
            // A letter tile with an A on it.
            drawRoundRect(m, tl, box, corner, style = line)
            seg(-0.3f, 0.35f, 0f, -0.35f); seg(0f, -0.35f, 0.3f, 0.35f); seg(-0.15f, 0.1f, 0.15f, 0.1f)
        }
        GameKind.GRID -> {
            // A small grid of letter squares.
            for (i in 0..1) for (j in 0..1) drawRoundRect(m, Offset(c.x - r * 0.6f + i * r * 0.7f, c.y - r * 0.6f + j * r * 0.7f), Size(r * 0.5f, r * 0.5f), CornerRadius(r * 0.1f), style = line)
        }
        GameKind.GOLF -> {
            // Blades of grass, as short slanted strokes.
            seg(-0.5f, 0.4f, -0.3f, -0.3f); seg(0f, 0.4f, 0f, -0.5f); seg(0.5f, 0.4f, 0.3f, -0.3f)
        }
        GameKind.HOCKEY -> {
            // Ice markings: a face-off circle cut by a line.
            drawCircle(m, r * 0.5f, c, style = line)
            seg(-0.8f, 0f, 0.8f, 0f)
        }
        GameKind.BINGO -> {
            drawCircle(m, r * 0.6f, c, style = line)
            drawCircle(m, r * 0.25f, c, style = line)
        }
        GameKind.CODE -> {
            for (i in 0..1) for (j in 0..1) drawCircle(m, r * 0.18f, Offset(c.x + (i - 0.5f) * r * 0.7f, c.y + (j - 0.5f) * r * 0.7f))
        }
        GameKind.SLIDE -> {
            // Three tiles and a gap.
            val t = Size(r * 0.55f, r * 0.55f)
            drawRoundRect(m, Offset(c.x - r * 0.6f, c.y - r * 0.6f), t, CornerRadius(r * 0.1f), style = line)
            drawRoundRect(m, Offset(c.x + r * 0.05f, c.y - r * 0.6f), t, CornerRadius(r * 0.1f), style = line)
            drawRoundRect(m, Offset(c.x - r * 0.6f, c.y + r * 0.05f), t, CornerRadius(r * 0.1f), style = line)
        }
        GameKind.BLOCKS -> {
            val t = Size(r * 0.55f, r * 0.55f)
            drawRect(m, Offset(c.x - r * 0.6f, c.y - r * 0.6f), t, style = line)
            drawRect(m, Offset(c.x + r * 0.05f, c.y + r * 0.05f), t, style = line)
            drawRect(m, Offset(c.x - r * 0.6f, c.y + r * 0.05f), t, style = line)
        }
        GameKind.DOTS -> {
            for (i in 0..1) for (j in 0..1) drawCircle(m, w * 1.4f, Offset(c.x + (i - 0.5f) * r * 1.1f, c.y + (j - 0.5f) * r * 1.1f))
        }
        GameKind.MEMORY -> {
            // The back of a card.
            drawRoundRect(m, Offset(c.x - r * 0.5f, c.y - r * 0.7f), Size(r, r * 1.4f), corner, style = line)
            drawCircle(m, r * 0.18f, c)
        }
        GameKind.TTT -> {
            if ((row + col) % 2 == 0) { seg(-0.45f, -0.45f, 0.45f, 0.45f); seg(0.45f, -0.45f, -0.45f, 0.45f) }
            else drawCircle(m, r * 0.45f, c, style = line)
        }
        GameKind.DRAW -> translate(c.x, c.y) { drawPath(shape!!, m, style = line) }
        GameKind.CLASH -> {
            // The corner of a card with a pip.
            seg(-0.6f, 0.6f, -0.6f, -0.2f); seg(-0.6f, -0.2f, -0.2f, -0.6f); seg(-0.2f, -0.6f, 0.6f, -0.6f)
            drawCircle(m, r * 0.15f, Offset(c.x - r * 0.25f, c.y - r * 0.2f))
        }
        GameKind.NUMBERS -> {
            seg(-0.4f, 0f, 0.4f, 0f); seg(0f, -0.4f, 0f, 0.4f)
        }
    }
}
