package com.arcade.sudoku.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.ui.theme.Board
import kotlin.math.max

/*
 * Key art: one small scene per game, a clear moment from it in three or four colours with ink
 * outlines, so games read apart at a glance and not only by colour. Drawn on a 280 × 170
 * design, the same as the Game moments catalogue, and scaled to cover whatever size it gets.
 */

private const val W = 280f
private const val H = 170f
private val Ink = Board.Ink
private const val LINE = 2.5

/** [kind]'s key art, filling [modifier]'s size (cropped at the edges to keep its shape). */
@Composable
fun KeyArt(kind: GameKind, modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds()) { drawKeyArt(kind) }
}

fun DrawScope.drawKeyArt(kind: GameKind) {
    val scene = Scenes.getValue(kind)
    val s = max(size.width / W, size.height / H)
    translate((size.width - W * s) / 2, (size.height - H * s) / 2) {
        scale(s, Offset.Zero) { Pen(this).scene() }
    }
}

// ---- Helpers --------------------------------------------------------------------------------

private fun Pen.ground(color: Long) = rect(-40, -40, W + 80, H + 80, rgb(color))

/** A disc with an ink outline. */
private fun Pen.disc(x: Number, y: Number, r: Number, fill: Color, width: Number = LINE) {
    circle(x, y, r, fill)
    ring(x, y, r, Ink, width)
}

/** A rounded tile with an ink outline and an optional letter or number on it. */
private fun Pen.tile(x: Number, y: Number, w: Number, h: Number, fill: Color, label: String? = null, color: Color = Ink, size: Number = 26, r: Number = 8) {
    rr(x, y, w, h, r, fill, Ink, LINE)
    if (label != null) text(label, x.f + w.f / 2, y.f + h.f / 2, size, color)
}

private val White = Color.White

private val Scenes: Map<GameKind, Pen.() -> Unit> = mapOf(
    GameKind.SUDOKU to Pen::sudoku,
    GameKind.SHIPS to Pen::ships,
    GameKind.CONNECT4 to Pen::connectFour,
    GameKind.DOTS to Pen::dots,
    GameKind.MEMORY to Pen::memory,
    GameKind.WORDS to Pen::words,
    GameKind.DRAW to Pen::draw,
    GameKind.TTT to Pen::ticTacToe,
    GameKind.DOMINOES to Pen::dominoes,
    GameKind.NUMBERS to Pen::numbers,
    GameKind.HOCKEY to Pen::hockey,
    GameKind.CLASH to Pen::clash,
    GameKind.LUDO to Pen::ludo,
    GameKind.GRID to Pen::grid,
    GameKind.BALLOON to Pen::balloon,
    GameKind.CODE to Pen::code,
    GameKind.SLIDE to Pen::slide,
    GameKind.BINGO to Pen::bingo,
    GameKind.GOLF to Pen::golf,
    GameKind.BLOCKS to Pen::blocks,
)

// ---- The first ten games --------------------------------------------------------------------

/** The last number going in. */
private fun Pen.sudoku() {
    ground(0xDCE8FF)
    rr(80, 25, 120, 120, 8, White)
    rect(160, 105, 40, 40, rgb(0xBBDEFB))
    stroke(rgb(0x8A95A8), 1.5) { moveTo(120, 25); lineTo(120, 145); moveTo(160, 25); lineTo(160, 145); moveTo(80, 65); lineTo(200, 65); moveTo(80, 105); lineTo(200, 105) }
    text("5", 100, 45, 24, Ink); text("3", 180, 45, 24, Ink); text("7", 140, 85, 24, Ink); text("9", 100, 125, 24, Ink)
    text("1", 180, 125, 24, rgb(0x0072E3))
    rr(80, 25, 120, 120, 8, null, Ink, 3)
}

/** A ship, and a near miss splashing up beside it. */
private fun Pen.ships() {
    ground(0x9FD3DC)
    val sea = shape {
        moveTo(0, 120)
        var x = 0
        var up = true
        while (x < W) {
            quadTo(x + 20, if (up) 110 else 130, x + 40, 120)
            x += 40
            up = !up
        }
        lineTo(W + 40, 120); lineTo(W + 40, H + 40); lineTo(0, H + 40); close()
    }
    fill(sea, rgb(0x6FB7C4)); outline(sea, Ink, LINE)
    val hull = shape { moveTo(60, 112); lineTo(170, 112); lineTo(156, 134); lineTo(74, 134); close() }
    fill(hull, rgb(0x8A8F9E)); outline(hull, Ink, LINE)
    rr(95, 90, 34, 22, 4, rgb(0xC9CCD6), Ink, LINE)
    stroke(Ink, LINE) { moveTo(112, 90); lineTo(112, 68) }
    val flag = shape { moveTo(112, 68); lineTo(130, 75); lineTo(112, 81); close() }
    fill(flag, rgb(0xE8505B)); outline(flag, Ink, 2)
    val splash = shape { moveTo(215, 118); quadTo(209, 88, 221, 72); quadTo(225, 94, 233, 78); quadTo(239, 102, 231, 118); close() }
    fill(splash, White); outline(splash, Ink, LINE)
    disc(222, 60, 5, White, 2)
}

/** A red disc dropping into the board. */
private fun Pen.connectFour() {
    ground(0xFDE2E2)
    rr(40, 40, 200, 150, 16, rgb(0x3D7BF7), Ink, 3)
    val red = rgb(0xE8505B)
    val yellow = rgb(0xFFC93C)
    val empty = rgb(0xEFEAFF)
    val rows = listOf(listOf(empty, empty, empty, yellow), listOf(empty, yellow, red, red), listOf(red, red, yellow, yellow))
    rows.forEachIndexed { r, row -> row.forEachIndexed { c, color -> disc(70 + c * 40, 70 + r * 40, 13, color) } }
    disc(150, 20, 13, red)
    stroke(Ink, LINE) { moveTo(150, 38); lineTo(150, 46) }
}

/** A box just closed, and the next line going in. */
private fun Pen.dots() {
    ground(0xECE8FF)
    val xs = listOf(65, 115, 165, 215)
    val ys = listOf(35, 85, 135)
    rect(65, 35, 50, 50, rgb(0xB8AEF7))
    text("A", 90, 60, 24, Ink)
    stroke(Ink, 4) {
        moveTo(65, 35); lineTo(165, 35); lineTo(165, 85)
        moveTo(65, 35); lineTo(65, 85); lineTo(115, 85); lineTo(115, 35)
        moveTo(115, 85); lineTo(115, 135)
        moveTo(215, 35); lineTo(215, 85)
    }
    stroke(rgb(0xE8505B), 5) { moveTo(165, 85); lineTo(215, 85) }
    for (y in ys) for (x in xs) disc(x, y, 7, White)
}

/** A matching pair turned over. */
private fun Pen.memory() {
    ground(0xFFF0D9)
    val face = rgb(0xFFD7C2)
    at(68, 80, -0.14, 1) {
        rr(40, 40, 56, 80, 10, rgb(0xF2A33A), Ink, LINE)
        ring(68, 80, 12, White, 2.5)
    }
    rr(112, 36, 56, 80, 10, White, Ink, LINE)
    disc(140, 76, 14, face)
    circle(135, 73, 2.5, Ink); circle(145, 73, 2.5, Ink)
    at(212, 80, 0.14, 1) {
        rr(184, 40, 56, 80, 10, White, Ink, LINE)
        disc(212, 80, 14, face)
        circle(207, 77, 2.5, Ink); circle(217, 77, 2.5, Ink)
    }
    stroke(rgb(0x047857), 3) { moveTo(118, 140); quadTo(140, 152, 162, 140); moveTo(188, 140); quadTo(210, 152, 232, 140) }
}

/** Letters landing in the row, coloured by the guess. */
private fun Pen.words() {
    ground(0xD9F2E6)
    val green = rgb(0x20A47B)
    val fills = listOf(green, White, green, rgb(0xFFC93C), White)
    "CRANE".forEachIndexed { i, c -> tile(28 + i * 46, if (i == 2) 54 else 62, 40, 44, fills[i], c.toString(), size = 28) }
}

/** A house on the pad, and someone wondering what it is. */
private fun Pen.draw() {
    ground(0xFCE4F0)
    rr(40, 22, 160, 126, 10, White, Ink, 3)
    stroke(Ink, 3.5) {
        moveTo(80, 128); lineTo(80, 84); lineTo(160, 84); lineTo(160, 128); lineTo(80, 128)
        moveTo(72, 88); lineTo(120, 46); lineTo(168, 88)
        moveTo(113, 128); lineTo(113, 104); lineTo(128, 104); lineTo(128, 128)
    }
    at(214, 112, 0.55, 1) {
        rr(207, 62, 14, 52, 3, rgb(0xFFC93C), Ink, LINE)
        val tip = shape { moveTo(207, 114); lineTo(221, 114); lineTo(214, 130); close() }
        fill(tip, rgb(0xFFD7C2)); outline(tip, Ink, 2)
    }
    disc(240, 42, 20, White)
    text("?", 240, 42, 26, rgb(0xE0569B))
}

/** Ultimate: one small board won, and the glowing board you're sent to. */
private fun Pen.ticTacToe() {
    ground(0xE4E3FF)
    rr(80, 22, 120, 120, 10, White)
    rect(120, 62, 40, 40, rgb(0xC7C4FF))
    stroke(rgb(0x8A95A8), 1.2) { moveTo(133, 62); lineTo(133, 102); moveTo(147, 62); lineTo(147, 102); moveTo(120, 75); lineTo(160, 75); moveTo(120, 89); lineTo(160, 89) }
    ring(140, 82, 4.5, rgb(0x3D7BF7), 2)
    stroke(rgb(0xE8505B), 4.5) { moveTo(88, 30); lineTo(112, 54); moveTo(112, 30); lineTo(88, 54) }
    ring(180, 122, 12, rgb(0x3D7BF7), 4.5)
    stroke(rgb(0xE8505B), 2.5) { moveTo(174, 36); lineTo(186, 48); moveTo(186, 36); lineTo(174, 48) }
    stroke(Ink, 3) { moveTo(120, 22); lineTo(120, 142); moveTo(160, 22); lineTo(160, 142); moveTo(80, 62); lineTo(200, 62); moveTo(80, 102); lineTo(200, 102) }
    rr(80, 22, 120, 120, 10, null, Ink, 3)
}

/** The line snaking across the table. */
private fun Pen.dominoes() {
    ground(0x2E7D5B)
    val bone = rgb(0xFFF8EC)
    for ((x, y, w, h) in listOf(listOf(30, 70, 60, 30), listOf(92, 70, 60, 30), listOf(154, 55, 30, 60), listOf(186, 70, 60, 30), listOf(216, 102, 30, 50))) {
        rr(x, y, w, h, 6, bone, Ink, LINE)
    }
    stroke(Ink, 1.5) { moveTo(60, 70); lineTo(60, 100); moveTo(122, 70); lineTo(122, 100); moveTo(154, 85); lineTo(184, 85); moveTo(216, 70); lineTo(216, 100); moveTo(216, 127); lineTo(246, 127) }
    for ((x, y) in listOf(45 to 85, 75 to 78, 75 to 92, 107 to 78, 107 to 92, 137 to 85, 169 to 68, 169 to 100, 201 to 85, 231 to 78, 231 to 92, 231 to 114, 231 to 140)) circle(x, y, 3.5, Ink)
}

/** A sum across meeting a sum going down, one blank still to fill. */
private fun Pen.numbers() {
    ground(0xD7F1F3)
    val s = 26
    val step = 30
    val x0 = 66
    val y0 = 12
    val stone = rgb(0xB9C6CC)
    val cells = listOf("3", "+", "4", "=", "7", "−", null, null, null, null, "", null, null, null, null, "=", null, null, null, null, "2", null, null, null, null)
    cells.forEachIndexed { i, c ->
        val x = x0 + (i % 5) * step
        val y = y0 + (i / 5) * step
        when (c) {
            null -> rr(x, y, s, s, 6, stone)
            "" -> {
                tile(x, y, s, s, White, r = 6)
                ring(x + s / 2, y + s / 2, 17, rgb(0x0E9AA7), 3)
            }
            "4" -> tile(x, y, s, s, rgb(0x9BE3B0), c, size = 18, r = 6)
            else -> tile(x, y, s, s, White, c, size = 18, r = 6)
        }
    }
    at(232, 98, 0.18, 1) { tile(214, 80, 36, 36, rgb(0xFFC93C), "1", size = 22, r = 8) }
}

// ---- The other ten games --------------------------------------------------------------------

/** A shot flying off the red paddle towards the far goal. */
private fun Pen.hockey() {
    ground(0xDDEEFF)
    rr(24, 20, 232, 130, 24, White, Ink, 3)
    stroke(rgb(0xE8505B), 2) { moveTo(140, 20); lineTo(140, 150) }
    ring(140, 85, 22, rgb(0xE8505B), 2)
    stroke(Ink, 6) { moveTo(24, 62); lineTo(24, 108); moveTo(256, 62); lineTo(256, 108) }
    stroke(rgb(0x8A95A8), 3) { moveTo(150, 74); lineTo(168, 72); moveTo(146, 86); lineTo(166, 82); moveTo(152, 98); lineTo(168, 92) }
    disc(88, 96, 20, rgb(0xE8505B), 3); disc(88, 96, 9, rgb(0xF58A92))
    disc(222, 74, 20, rgb(0xFFC93C), 3); disc(222, 74, 9, rgb(0xFFE08A))
    disc(186, 78, 10, Ink)
}

/** A hand fanned out over the pile, a wild on top. */
private fun Pen.clash() {
    ground(0xD6EFEA)
    fun card(x: Number, y: Number, color: Color, label: String?) {
        rr(x, y, 52, 76, 10, color, Ink, LINE)
        disc(x.f + 26, y.f + 38, 15, White, 2)
        if (label != null) text(label, x.f + 26, y.f + 38, 18, Ink)
    }
    rr(28, 52, 52, 76, 10, Ink); ring(54, 90, 14, rgb(0xFFC93C), 3)
    card(92, 46, rgb(0x3D7BF7), "5")
    for ((rot, color, label) in listOf(Triple(-0.3, rgb(0xE8505B), "7"), Triple(0.0, rgb(0xFFC93C), "+2"), Triple(0.3, rgb(0x20A47B), "⇄"))) {
        at(210, 150, rot, 1) { card(184, 52, color, label) }
    }
}

/** Tokens in their corners and a die just rolled. */
private fun Pen.ludo() {
    ground(0xF4F0FF)
    rr(30, 25, 70, 70, 10, rgb(0xE8505B), Ink, LINE); rr(30, 95, 70, 55, 10, rgb(0x20A47B), Ink, LINE)
    rr(100, 25, 70, 70, 10, rgb(0xFFC93C), Ink, LINE); rr(100, 95, 70, 55, 10, rgb(0x3D7BF7), Ink, LINE)
    disc(65, 60, 12, rgb(0xE8505B)); disc(135, 60, 12, rgb(0xFFC93C)); disc(135, 122, 12, rgb(0x3D7BF7))
    ring(65, 60, 6, White, 2); ring(135, 60, 6, White, 2); ring(135, 122, 6, White, 2)
    at(221, 96, -0.21, 1) {
        rr(195, 70, 52, 52, 12, rgb(0xFFFDF7), Ink, LINE)
        circle(208, 83, 4.5, Ink); circle(221, 96, 4.5, Ink); circle(234, 109, 4.5, Ink)
    }
}

/** A word traced through the letters. */
private fun Pen.grid() {
    ground(0xEEF0C8)
    val letters = "STARPEONLIDECKUM"
    val s = 32
    val step = 36
    val x0 = 70
    val y0 = 14
    letters.forEachIndexed { i, c ->
        tile(x0 + (i % 4) * step, y0 + (i / 4) * step, s, s, if (i < 4) rgb(0xFFE27A) else White, c.toString(), size = 18, r = 7)
    }
    withAlpha(0.55) { stroke(rgb(0x9E9D24), 9) { moveTo(x0 + 16, y0 + 16); lineTo(x0 + 3 * step + 16, y0 + 16) } }
    disc(x0 + 3 * step + 16, y0 + 16, 5, rgb(0x9E9D24), 2)
    tile(226, 112, 44, 26, rgb(0x9E9D24), "+1", White, 16, 13)
}

/** Balloons over the word, one of them popping. */
private fun Pen.balloon() {
    ground(0xFDE3EE)
    val colors = listOf(rgb(0xE8505B), rgb(0xFFC93C), rgb(0x3D7BF7), rgb(0x20A47B))
    colors.forEachIndexed { i, c ->
        val x = 62 + i * 52
        val y = if (i % 2 == 0) 46 else 56
        stroke(Ink, 1.5) { moveTo(x, y + 24); quadTo(x - 6, y + 40, x + 2, y + 58) }
        if (i == 3) {
            for (k in 0 until 8) {
                val a = k * TAU / 8
                stroke(c, 3) { moveTo(x + 14 * kotlin.math.cos(a), y + 14 * kotlin.math.sin(a)); lineTo(x + 26 * kotlin.math.cos(a), y + 26 * kotlin.math.sin(a)) }
            }
        } else {
            ell(x, y, 18, 23, 0, c)
            stroke(Ink, LINE) { oval(x, y, 18, 23) }
            ell(x - 6, y - 8, 4, 6, 0.5, White.copy(alpha = 0.7f))
        }
    }
    "_A_T_".forEachIndexed { i, c -> tile(70 + i * 30, 122, 26, 32, White, if (c == '_') null else c.toString(), size = 18, r = 6) }
}

/** The hidden code, and guesses closing in on it. */
private fun Pen.code() {
    ground(0xE3E4F3)
    rr(52, 12, 176, 146, 14, White, Ink, 3)
    rr(64, 22, 120, 30, 8, Ink)
    text("?  ?  ?  ?", 124, 37, 18, White)
    val pegs = mapOf('r' to rgb(0xE8505B), 'y' to rgb(0xFFC93C), 'g' to rgb(0x20A47B), 'b' to rgb(0x3D7BF7), 'o' to rgb(0xFF8A3D))
    val rows = listOf("rrgg" to (1 to 1), "rgoo" to (2 to 0), "rgyb" to (2 to 2))
    rows.forEachIndexed { r, (guess, score) ->
        val y = 74 + r * 30
        guess.forEachIndexed { i, c -> disc(79 + i * 30, y, 10, pegs.getValue(c), 2) }
        val pins = List(score.first) { rgb(0xE8505B) } + List(score.second) { White }
        pins.forEachIndexed { k, color -> disc(198 + (k % 2) * 12, y - 6 + (k / 2) * 12, 4.5, color, 1.5) }
    }
}

/** Tiles in their place glowing, one sliding into the gap. */
private fun Pen.slide() {
    ground(0xFFE6D4)
    rr(78, 16, 124, 124, 12, rgb(0xFFF8EC), Ink, 3)
    val orange = rgb(0xFF8A3D)
    val tiles = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8)
    tiles.forEachIndexed { i, n ->
        if (n == 0) return@forEachIndexed
        val x = 84 + (i % 3) * 38 + if (n == 8) -14 else 0
        val y = 22 + (i / 3) * 38
        tile(x, y, 36, 36, if (n == i + 1) orange else White, n.toString(), size = 18, r = 7)
    }
    stroke(Ink, 2.5) { moveTo(206, 112); lineTo(224, 112); moveTo(210, 124); lineTo(232, 124) }
}

/** A card one number from a line, and the ball being called. */
private fun Pen.bingo() {
    ground(0xFBE0EA)
    rr(36, 18, 124, 134, 12, White, Ink, 3)
    val head = listOf(rgb(0x3D7BF7), rgb(0xE8505B), rgb(0xFFC93C), rgb(0x20A47B), rgb(0xFF8A3D))
    "BINGO".forEachIndexed { i, c ->
        rr(42 + i * 23, 24, 21, 18, 4, head[i])
        text(c.toString(), 52.5 + i * 23, 33, 13, Ink)
    }
    val dabs = setOf(10, 11, 12, 13, 6, 0, 18)
    for (i in 0 until 25) {
        val x = 53.5 + (i % 5) * 23
        val y = 56 + (i / 5) * 21
        if (i in dabs) circle(x, y, 8.5, rgb(0xD9467A).copy(alpha = 0.55f))
        ring(x, y, 9, rgb(0x8A95A8), 1)
    }
    text("★", 99.5, 98, 14, Ink)
    disc(214, 82, 36, White, 3)
    ring(214, 82, 26, rgb(0x20A47B), 6)
    text("G 54", 214, 82, 18, Ink)
}

/** The ball's path to the flag. */
private fun Pen.golf() {
    ground(0xBFE3A8)
    val green = shape { moveTo(20, 130); quadTo(80, 70, 160, 90); quadTo(240, 110, 270, 100); lineTo(270, H + 40); lineTo(20, H + 40); close() }
    fill(green, rgb(0x3FA34D)); outline(green, Ink, LINE)
    ell(190, 112, 12, 5, 0, Ink)
    stroke(Ink, 3) { moveTo(190, 112); lineTo(190, 40) }
    val flag = shape { moveTo(190, 40); lineTo(224, 52); lineTo(190, 64); close() }
    fill(flag, rgb(0xE8505B)); outline(flag, Ink, LINE)
    for (k in 0 until 9) {
        val t = k / 9.0
        val x = 92 + t * 86
        val y = 124 - 40 * t + 30 * t * t
        circle(x, y, 1.8, Ink)
    }
    disc(80, 128, 9, White)
}

/** Pieces of two colours meeting only at their corners. */
private fun Pen.blocks() {
    ground(0xE2E8EB)
    val c = 18
    val x0 = 50
    val y0 = 13
    rr(x0 - 6, y0 - 6, 10 * c + 12, 8 * c + 12, 10, rgb(0x546E7A), Ink, 3)
    for (y in 0 until 8) for (x in 0 until 10) rr(x0 + x * c + 1, y0 + y * c + 1, c - 2, c - 2, 3, rgb(0xF4F6F7))
    val blue = listOf(1 to 1, 2 to 1, 3 to 1, 1 to 2, 4 to 2, 4 to 3, 5 to 3, 4 to 4)
    val yellow = listOf(8 to 1, 8 to 2, 7 to 3, 8 to 3, 6 to 4, 6 to 5, 7 to 5, 6 to 6)
    for ((cells, color) in listOf(blue to rgb(0x3D7BF7), yellow to rgb(0xFFC93C))) {
        for ((x, y) in cells) rr(x0 + x * c + 1, y0 + y * c + 1, c - 2, c - 2, 3, color, Ink, 1.5)
    }
}
