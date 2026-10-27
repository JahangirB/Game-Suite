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

/** Whether [kind] has its key art yet. */
fun hasKeyArt(kind: GameKind) = kind in Scenes

/** [kind]'s key art, filling [modifier]'s size (cropped at the edges to keep its shape). */
@Composable
fun KeyArt(kind: GameKind, modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds()) { drawKeyArt(kind) }
}

fun DrawScope.drawKeyArt(kind: GameKind) {
    val scene = Scenes[kind] ?: return
    val s = max(size.width / W, size.height / H)
    translate((size.width - W * s) / 2, (size.height - H * s) / 2) {
        scale(s, Offset.Zero) { Pen(this).scene() }
    }
}

// ---- Helpers -------------------------------------------------------------------------------

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
)

// ---- The first ten -------------------------------------------------------------------------

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
