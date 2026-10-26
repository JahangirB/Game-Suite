package com.arcade.sudoku.ui.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.arcade.sudoku.data.GameKind
import kotlin.math.PI
import kotlin.math.cos

/** How a match ended for this player. Draw & Guess, a team game, uses WIN for a good score. */
enum class Outcome { WIN, LOSE, TIE }

/**
 * The looping picture in the result popup: one for winning and one for losing per game, and
 * one shared tie. Drawn in a 300 × 170 space, the size of the designs they come from.
 */
@Composable
fun ResultAnimation(game: GameKind, outcome: Outcome, modifier: Modifier = Modifier) {
    // One 12-second clock; every loop's period (3, 2, 1.5, 1 or 0.8 s) divides it evenly.
    val time by rememberInfiniteTransition(label = "result").animateFloat(
        0f, 12f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "resultTime",
    )
    Canvas(modifier.fillMaxWidth().aspectRatio(W / H).clip(RoundedCornerShape(16.dp))) {
        drawRect(background(game, outcome))
        scale(size.width / W, size.height / H, Offset.Zero) {
            val a = ResultScene(this, time)
            if (outcome == Outcome.TIE) a.tie() else {
                val win = outcome == Outcome.WIN
                when (game) {
                    GameKind.SUDOKU -> if (win) a.sudokuWin() else a.sudokuLose()
                    GameKind.SHIPS -> if (win) a.shipsWin() else a.shipsLose()
                    GameKind.CONNECT4 -> if (win) a.c4Win() else a.c4Lose()
                    GameKind.DOTS -> if (win) a.dotsWin() else a.dotsLose()
                    GameKind.MEMORY -> if (win) a.memoryWin() else a.memoryLose()
                    GameKind.WORDS -> if (win) a.wordsWin() else a.wordsLose()
                    GameKind.DRAW -> if (win) a.drawWin() else a.drawLose()
                    GameKind.TTT -> if (win) a.tttWin() else a.tttLose()
                }
            }
        }
    }
}

private const val W = 300f
private const val H = 170f

private val Ink = Color(0xFF2B2440)
private val White = Color.White
private val Gold = Color(0xFFFFD23F)
private val Red = Color(0xFFE8505B)
private val Yellow = Color(0xFFFFC93C)
private val Coral = Color(0xFFFF6B6B)
private val Lilac = Color(0xFFEFEAFF)
private val BoardBlue = Color(0xFF2F6FD1)
private val Grey = Color(0xFF8A8499)
private val Green = Color(0xFF20A47B)

private fun background(game: GameKind, outcome: Outcome) = when {
    outcome == Outcome.TIE -> Color(0xFFA49CC4)
    game == GameKind.WORDS -> Color(0xFFDDF3EA)
    else -> game.color
}

/**
 * A value along keyframes, like a CSS animation: [keys] are (fraction of the loop, value)
 * pairs in order; between two keys the value moves in a straight line.
 */
private fun kf(f: Float, vararg keys: Pair<Float, Float>): Float {
    if (f <= keys.first().first) return keys.first().second
    for (i in 1 until keys.size) {
        val (t1, v1) = keys[i]
        if (f <= t1) {
            val (t0, v0) = keys[i - 1]
            return if (t1 == t0) v1 else v0 + (v1 - v0) * (f - t0) / (t1 - t0)
        }
    }
    return keys.last().second
}

/** A smooth 0 → 1 → 0 wave over one loop, like ease-in-out back and forth. */
private fun wave(f: Float) = ((1 - cos(2 * PI * f)) / 2).toFloat()

private fun rad(deg: Float) = deg * PI.toFloat() / 180f

private class ResultScene(val d: DrawScope, val t: Float) {
    val p = Pen(d)

    /** Where a loop of [period] seconds, started [delay] seconds late, is now: 0 to 1. */
    fun f(period: Float, delay: Float = 0f): Float = (((t - delay) % period) + period) % period / period

    // CSS-like keyframe curves from the designs.
    fun pop(f: Float) = kf(f, 0f to 0f, .08f to 0f, .18f to 1.2f, .26f to 1f, .84f to 1f, .94f to 0f, 1f to 0f)
    fun bob(f: Float) = -10f * wave(f)
    fun glow(f: Float) = .25f + .75f * wave(f)
    fun shakeX(f: Float) = kf(f, 0f to 0f, .2f to -5f, .4f to 5f, .6f to -3f, .8f to 3f, 1f to 0f)
    fun shakeRot(f: Float) = kf(f, 0f to 0f, .2f to -4f, .4f to 4f, .6f to 0f, 1f to 0f)
    fun sway(f: Float) = -7f * cos(2 * PI * f).toFloat()

    fun scaled(cx: Number, cy: Number, sx: Float, sy: Float, block: () -> Unit) =
        d.withTransform({ scale(sx, sy, Offset(cx.f, cy.f)) }) { block() }

    /** A sparkle dot that grows and fades. */
    fun spark(x: Number, y: Number, r: Number, color: Color, period: Float, delay: Float) {
        val ph = f(period, delay)
        val s = kf(ph, 0f to 0f, .2f to 0f, .4f to 1f, .7f to 1.7f, 1f to 1.7f)
        val a = kf(ph, 0f to 0f, .2f to 0f, .4f to 1f, .7f to 0f, 1f to 0f)
        if (s > 0f && a > 0f) p.withAlpha(a) { p.circle(x, y, r.f * s, color) }
    }

    /** A dashed ring that bursts outward, for fireworks. */
    fun burst(x: Number, y: Number, r: Number, color: Color, period: Float, delay: Float, on: Float = 3f, off: Float = 6f) {
        val ph = f(period, delay)
        val s = kf(ph, 0f to 0f, .2f to 0f, .4f to 1f, .7f to 1.7f, 1f to 1.7f)
        val a = kf(ph, 0f to 0f, .2f to 0f, .4f to 1f, .7f to 0f, 1f to 0f)
        if (s > 0f && a > 0f) {
            d.drawCircle(color, r.f * s, Offset(x.f, y.f), a, Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on * s, off * s))))
        }
    }

    /** The first [fraction] of [path], as a line: a stroke drawing itself. */
    fun partial(path: Path, fraction: Float, color: Color, width: Float, alpha: Float = 1f) {
        if (fraction <= 0f || alpha <= 0f) return
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * fraction.coerceAtMost(1f), part, true)
        d.drawPath(part, color, alpha, Stroke(width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }

    /** How far a "draw on" line has drawn and how visible it is, delayed by [delay]. */
    fun drawOn(delay: Float = 0f): Pair<Float, Float> {
        val ph = f(3f, delay)
        return kf(ph, 0f to 0f, .05f to 0f, .55f to 1f, 1f to 1f) to kf(ph, 0f to 1f, .88f to 1f, 1f to 0f)
    }

    fun outlinedText(s: String, x: Number, y: Number, size: Number, fill: Color, outline: Float = 2.5f) {
        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to 1, -1 to 1, 1 to -1)) {
            p.text(s, x.f + dx * outline, y.f + dy * outline, size, Ink)
        }
        p.text(s, x, y, size, fill)
    }

    fun star(cx: Number, cy: Number, ro: Float, ri: Float) = p.shape {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) ro else ri
            val a = -PI / 2 + i * PI / 5
            val x = cx.f + r * cos(a).toFloat()
            val y = cy.f + r * kotlin.math.sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun heart(cx: Number, cy: Number) = p.shape {
        val x = cx.f
        val y = cy.f
        moveTo(x, y + 16)
        cubicTo(x - 14, y + 6, x - 18, y - 2, x - 12, y - 9)
        cubicTo(x - 7, y - 14, x - 1, y - 12, x, y - 6)
        cubicTo(x + 1, y - 12, x + 7, y - 14, x + 12, y - 9)
        cubicTo(x + 18, y - 2, x + 14, y + 6, x, y + 16)
        close()
    }

    fun filled(path: Path, fill: Color, width: Number = 3) {
        p.fill(path, fill)
        p.outline(path, Ink, width)
    }

    // ---- Sudoku ---------------------------------------------------------------------------

    fun sudokuGrid() {
        p.rr(45, 25, 120, 120, 10, White, Ink, 3)
        p.withAlpha(0.3) { p.stroke(Ink, 2) { moveTo(85, 25); lineTo(85, 145); moveTo(125, 25); lineTo(125, 145); moveTo(45, 65); lineTo(165, 65); moveTo(45, 105); lineTo(165, 105) } }
    }

    fun sudokuWin() {
        sudokuGrid()
        "538194726".forEachIndexed { i, digit ->
            val cx = 65 + 40 * (i % 3)
            val cy = 45 + 40 * (i / 3)
            val s = pop(f(3f, i * .1f))
            if (s > 0f) p.at(cx, cy, 0, s) { p.text(digit.toString(), cx, cy, 26, GameKind.SUDOKU.color) }
        }
        p.translate(0, bob(f(1.5f))) { filled(star(235, 80, 30f, 13f), Gold) }
        spark(200, 40, 5, White, 2f, 0f); spark(272, 46, 5, White, 2f, .5f)
        spark(270, 122, 5, White, 2f, 1f); spark(204, 124, 5, White, 2f, 1.3f)
    }

    fun sudokuLose() {
        p.rr(45, 25, 120, 120, 10, White, Ink, 3)
        p.rect(86, 66, 38, 38, Color(0xFFFFD9DC))
        p.withAlpha(0.3) { p.stroke(Ink, 2) { moveTo(85, 25); lineTo(85, 145); moveTo(125, 25); lineTo(125, 145); moveTo(45, 65); lineTo(165, 65); moveTo(45, 105); lineTo(165, 105) } }
        val muted = Color(0xFFA9B8E8)
        listOf(Triple("5", 65, 45), Triple("8", 145, 45), Triple("1", 65, 85), Triple("6", 145, 125), Triple("2", 105, 125))
            .forEach { (s, x, y) -> p.text(s, x, y, 26, muted) }
        val ph = f(.8f)
        p.at(105, 85, rad(shakeRot(ph)), 1) { p.text("7", 105 + shakeX(ph), 85, 26, Red) }
        p.at(235, 118, rad(sway(f(2f))), 1) { outlinedText("?", 235, 86, 86, White) }
    }

    // ---- Ship Battle ----------------------------------------------------------------------

    fun ship(flagColor: Color, flagAngle: Float) {
        p.stroke(Ink, 4) { moveTo(150, 100); lineTo(150, 52) }
        p.at(152, 63, rad(flagAngle), 1) { filled(p.shape { moveTo(152, 54); lineTo(184, 62); lineTo(152, 72); close() }, flagColor, 2.5) }
        p.rr(125, 100, 40, 18, 4, Coral, Ink, 3)
        filled(p.shape { moveTo(95, 118); lineTo(205, 118); lineTo(190, 142); lineTo(110, 142); close() }, White)
    }

    fun waves() {
        p.translate(-60 * f(2f), 0) {
            p.fill(Color(0xFF0B7F90)) {
                moveTo(0, 134)
                var k = 0
                var x = 0
                while (x < 390) {
                    quadTo(x + 15, if (k % 2 == 0) 124 else 144, x + 30, 134)
                    x += 30
                    k++
                }
                lineTo(390, 170); lineTo(0, 170); close()
            }
        }
    }

    fun shipsWin() {
        burst(70, 42, 16, Gold, 2f, 0f); burst(240, 36, 16, White, 2f, .7f); burst(205, 62, 10, Coral, 2f, 1.3f, 3f, 5f)
        p.translate(0, bob(f(2f))) { ship(Gold, sway(f(1f))) }
        waves()
    }

    fun shipsLose() {
        val ph = f(3f)
        val ty = kf(ph, 0f to 0f, .1f to 0f, .7f to 46f, .85f to 56f, 1f to 56f)
        val rot = kf(ph, 0f to 0f, .1f to 0f, .7f to -22f, .85f to -24f, 1f to -24f)
        val a = kf(ph, 0f to 1f, .7f to 1f, .85f to 0f, 1f to 0f)
        p.withAlpha(a) { p.translate(0, ty) { p.at(150, 97, rad(rot), 1) { ship(White, 0f) } } }
        waves()
        listOf(Triple(138, 5, 0f), Triple(156, 4, .6f), Triple(170, 6, 1.2f)).forEach { (x, r, delay) ->
            val b = f(2f, delay)
            val alpha = if (b < .15f) b / .15f else 1f - (b - .15f) / .85f
            p.withAlpha(alpha) { p.ring(x, 150 - 80 * b, r, White, 2) }
        }
    }

    // ---- Connect Four ---------------------------------------------------------------------

    fun c4Board() {
        p.rr(60, 40, 180, 120, 14, BoardBlue, Ink, 3)
    }

    fun hole(x: Number, y: Number) = p.circle(x, y, 15, Lilac)
    fun disc(x: Number, y: Number, color: Color) {
        p.circle(x, y, 15, color)
        p.ring(x, y, 15, Ink, 2.5)
    }

    fun c4Win() {
        c4Board()
        for (i in 0 until 4) hole(82.5 + 45 * i, 60)
        hole(82.5, 100); disc(127.5, 100, Yellow); disc(172.5, 100, Yellow); hole(217.5, 100)
        for (i in 0 until 4) {
            val ph = f(3f, i * .15f)
            val dy = kf(ph, 0f to -150f, .06f to -150f, .22f to 0f, .27f to -10f, .32f to 0f, 1f to 0f)
            val a = kf(ph, 0f to 1f, .86f to 1f, .96f to 0f, 1f to 0f)
            p.withAlpha(a) { disc(82.5 + 45 * i, 140 + dy, Red) }
        }
        p.withAlpha(glow(f(1f))) { p.rr(64, 122, 172, 36, 18, null, Gold, 4) }
    }

    fun c4Lose() {
        c4Board()
        for (i in 0 until 4) hole(82.5 + 45 * i, 60)
        for (i in 0 until 4) disc(82.5 + 45 * i, 100, Yellow)
        p.withAlpha(glow(f(1f))) { p.rr(64, 82, 172, 36, 18, null, Gold, 4) }
        for (i in 0 until 4) hole(82.5 + 45 * i, 140)
        for (i in 0 until 3) {
            val ph = f(3f, i * .25f)
            val ty = kf(ph, 0f to 0f, .25f to 0f, .75f to 130f, 1f to 130f)
            val a = kf(ph, 0f to 1f, .25f to 1f, .75f to 0f, 1f to 0f)
            val x = 82.5 + 45 * i
            p.withAlpha(a) { p.at(x, 140 + ty, rad(40f * ty / 130f), 1) { disc(x, 140 + ty, Red) } }
        }
    }

    // ---- Dots & Boxes ---------------------------------------------------------------------

    fun dotsGrid(lines: Shape.() -> Unit) {
        p.stroke(White, 5, lines)
        for (y in listOf(25, 85, 145)) for (x in listOf(60, 120, 180)) p.circle(x, y, 6, Ink)
    }

    fun box(x: Int, y: Int, color: Color, delay: Float) {
        val s = pop(f(3f, delay))
        if (s > 0f) p.at(x + 28, y + 28, 0, s) { p.rr(x, y, 56, 56, 4, color) }
    }

    fun dotsWin() {
        box(62, 27, Red, 0f); box(122, 27, Red, .2f); box(62, 87, Red, .4f); box(122, 87, Red, .6f)
        dotsGrid { moveTo(60, 25); lineTo(180, 25); moveTo(60, 85); lineTo(180, 85); moveTo(60, 145); lineTo(180, 145); moveTo(60, 25); lineTo(60, 145); moveTo(120, 25); lineTo(120, 145); moveTo(180, 25); lineTo(180, 145) }
        p.translate(0, bob(f(1.5f))) {
            filled(p.shape { moveTo(215, 100); lineTo(220, 64); lineTo(232, 82); lineTo(240, 58); lineTo(248, 82); lineTo(260, 64); lineTo(265, 100); close() }, Gold)
            p.rr(213, 100, 54, 10, 3, Gold, Ink, 3)
        }
    }

    fun dotsLose() {
        box(62, 27, Yellow, 0f); box(122, 27, Yellow, .2f); box(62, 87, Yellow, .4f)
        p.stroke(White, 5) { moveTo(60, 25); lineTo(180, 25); moveTo(60, 85); lineTo(180, 85); moveTo(60, 145); lineTo(120, 145); moveTo(60, 25); lineTo(60, 145); moveTo(120, 25); lineTo(120, 145); moveTo(180, 25); lineTo(180, 85) }
        val ph = f(3f, .4f)
        val ty = kf(ph, 0f to 0f, .25f to 0f, .75f to 130f, 1f to 130f)
        p.withAlpha(kf(ph, 0f to 1f, .25f to 1f, .75f to 0f, 1f to 0f)) {
            p.at(180, 116 + ty, rad(40f * ty / 130f), 1) { p.stroke(Red, 6) { moveTo(180, 92 + ty); lineTo(180, 140 + ty) } }
        }
        for (y in listOf(25, 85, 145)) for (x in listOf(60, 120, 180)) p.circle(x, y, 6, Ink)
        p.at(240, 110, rad(sway(f(3f)) * 0.6f), 1) {
            p.circle(240, 84, 26, White); p.ring(240, 84, 26, Ink, 3)
            p.circle(231, 78, 3, Ink); p.circle(249, 78, 3, Ink)
            p.stroke(Ink, 3) { moveTo(230, 96); quadTo(240, 88, 250, 96) }
        }
    }

    // ---- Memory Pairs ---------------------------------------------------------------------

    fun card(x: Int, delay: Float, face: () -> Unit) {
        val ph = f(3f, delay)
        val back = kf(ph, 0f to 1f, .15f to 1f, .25f to 0f, .8f to 0f, .9f to 1f, 1f to 1f)
        val front = kf(ph, 0f to 0f, .25f to 0f, .35f to 1f, .78f to 1f, .88f to 0f, 1f to 0f)
        val cx = x + 35
        if (back > 0f) scaled(cx, 85, back, 1f) {
            p.rr(x, 37, 70, 96, 12, Color(0xFFFFE2B3), Ink, 3)
            p.withAlpha(0.4) { p.rr(x + 20, 65, 30, 40, 6, null, Ink, 2.5) }
        }
        if (front > 0f) scaled(cx, 85, front, 1f) {
            p.rr(x, 37, 70, 96, 12, White, Ink, 3)
            face()
        }
    }

    fun memoryWin() {
        val ph = f(3f)
        val dy = kf(ph, 0f to 0f, .36f to 0f, .46f to -16f, .56f to 0f, .63f to -8f, .7f to 0f, 1f to 0f)
        p.translate(0, dy) {
            card(65, 0f) { filled(heart(100, 82), Red, 2.5) }
            card(165, .15f) { filled(heart(200, 82), Red, 2.5) }
        }
        spark(150, 30, 6, White, 2f, .6f); spark(150, 140, 5, White, 2f, 1.1f)
    }

    fun memoryLose() {
        val ph = f(3f)
        val dx = kf(ph, 0f to 0f, .4f to 0f, .45f to -7f, .5f to 7f, .55f to -5f, .6f to 5f, .65f to 0f, 1f to 0f)
        p.translate(dx, 0) {
            card(65, 0f) { filled(heart(100, 82), Red, 2.5) }
            card(165, .15f) { filled(star(200, 84, 18f, 7.6f), Color(0xFF3D7BF7), 2.5) }
        }
    }

    // ---- Word Race ------------------------------------------------------------------------

    fun tiles(word: String, colour: Color, shakeLast: Boolean) {
        word.forEachIndexed { i, letter ->
            val ph = f(3f, i * .15f)
            val sy = kf(ph, 0f to 1f, .1f to 1f, .2f to 0f, .21f to 0f, .3f to 1f, 1f to 1f)
            val turned = ph in .21f..0.91f
            val x = 24 + i * 52
            val dx = if (shakeLast && i == word.lastIndex) shakeX(f(.8f)) else 0f
            p.translate(dx, 0) {
                scaled(x + 22, 85, 1f, sy) {
                    p.rr(x, 63, 44, 44, 8, if (turned) colour else White, Ink, 3)
                    if (turned) p.text(letter.toString(), x + 22, 85, 28, White)
                }
            }
        }
    }

    fun wordsWin() {
        tiles("GREAT", Green, false)
        spark(60, 36, 5, Gold, 2f, .9f); spark(150, 30, 6, Green, 2f, 1.2f); spark(240, 38, 5, Coral, 2f, 1.5f)
        spark(100, 136, 5, Color(0xFF7B6CF0), 2f, 1.1f); spark(210, 138, 5, Gold, 2f, 1.4f)
    }

    fun wordsLose() = tiles("UH-OH", Grey, true)

    // ---- Draw & Guess ---------------------------------------------------------------------

    fun paper() = p.rr(50, 20, 200, 130, 12, White, Ink, 3)

    fun drawWin() {
        paper()
        val (drawn, a) = drawOn()
        partial(star(150, 76, 38f, 16f), drawn, GameKind.DRAW.color, 5f, a)
        val s = pop(f(3f, 1.4f))
        if (s > 0f) p.at(228, 128, 0, s) {
            p.rr(196, 112, 64, 32, 16, Gold, Ink, 3)
            p.text("YAY", 228, 128, 18, Ink)
        }
        spark(70, 40, 5, Gold, 2f, 1.6f); spark(230, 40, 5, Color(0xFF7B6CF0), 2f, 1.9f)
    }

    fun drawLose() {
        paper()
        val scribble = p.shape {
            moveTo(80, 100)
            cubicTo(95, 50, 110, 120, 125, 70)
            cubicTo(140, 20, 155, 110, 170, 60)
            cubicTo(185, 10, 200, 100, 220, 70)
        }
        val ph = f(3f)
        val (drawn, a) = drawOn()
        partial(scribble, drawn, Grey, 5f, a * kf(ph, 0f to 1f, .5f to 1f, .9f to 0f, 1f to 0f))
        val e = kf(ph, 0f to 0f, .4f to 0f, .9f to 170f, 1f to 170f)
        p.translate(e, 0) {
            p.rr(40, 60, 44, 28, 6, Coral, Ink, 3)
            p.rr(40, 76, 44, 12, 4, White, Ink, 3)
        }
    }

    // ---- Ultimate Tic-Tac-Toe -------------------------------------------------------------

    fun tttGrid() = p.stroke(White, 5) { moveTo(130, 25); lineTo(130, 145); moveTo(170, 25); lineTo(170, 145); moveTo(90, 65); lineTo(210, 65); moveTo(90, 105); lineTo(210, 105) }

    fun cross(cx: Number, cy: Number, half: Float = 12f, w: Float = 6f) {
        for ((color, width) in listOf(Ink to w + 5, Red to w)) {
            p.stroke(color, width) { moveTo(cx.f - half, cy.f - half); lineTo(cx.f + half, cy.f + half); moveTo(cx.f + half, cy.f - half); lineTo(cx.f - half, cy.f + half) }
        }
    }

    fun nought(cx: Number, cy: Number) {
        p.ring(cx, cy, 13, Ink, 10)
        p.ring(cx, cy, 13, Yellow, 5)
    }

    fun tttWin() {
        tttGrid()
        listOf(110 to 45, 150 to 85, 190 to 125).forEachIndexed { i, (x, y) ->
            val s = pop(f(3f, i * .25f))
            if (s > 0f) p.at(x, y, 0, s) { cross(x, y) }
        }
        val (drawn, a) = drawOn(.9f)
        partial(p.shape { moveTo(95, 30); lineTo(205, 140) }, drawn, Gold, 7f, a)
    }

    fun tttLose() {
        tttGrid()
        listOf(110, 150, 190).forEachIndexed { i, x ->
            val s = pop(f(3f, i * .25f))
            if (s > 0f) p.at(x, 85, 0, s) { nought(x, 85) }
        }
        val (drawn, a) = drawOn(.9f)
        partial(p.shape { moveTo(92, 85); lineTo(208, 85) }, drawn, White, 6f, a)
        val ph = f(3f, .6f)
        val ty = kf(ph, 0f to 0f, .25f to 0f, .75f to 130f, 1f to 130f)
        p.withAlpha(kf(ph, 0f to 1f, .25f to 1f, .75f to 0f, 1f to 0f)) {
            p.at(248, 40 + ty, rad(40f * ty / 130f), 1) { cross(248, 40 + ty, 10f, 5f) }
        }
    }

    // ---- A tie, any game ------------------------------------------------------------------

    fun tie() {
        val ph = f(2f)
        val dx = kf(ph, 0f to 0f, .45f to 26f, .52f to 20f, 1f to 0f)
        p.translate(dx, 0) { p.circle(100, 85, 32, Red); p.ring(100, 85, 32, Ink, 3); p.withAlpha(0.4) { p.circle(90, 75, 8, White) } }
        p.translate(-dx, 0) { p.circle(200, 85, 32, Yellow); p.ring(200, 85, 32, Ink, 3); p.withAlpha(0.5) { p.circle(190, 75, 8, White) } }
        burst(150, 44, 8, White, 2f, .5f, 3f, 4f)
    }
}
