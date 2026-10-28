package com.pippop.game.dominoes

import kotlin.math.abs

/**
 * A tile on the table in half-tile units (a tile is 2 × 1): its box, and the pips on its
 * left (or top) half [first] and right (or bottom) half [second].
 */
data class PlacedTile(val x: Float, val y: Float, val w: Float, val h: Float, val first: Int, val second: Int)

/**
 * Lays the line out like a table: the round's first tile in the middle, the line growing out
 * both ways from it, doubles across the line, and a quarter turn near the edge, so the right
 * arm snakes downwards and the left arm upwards and they never meet.
 */
object DominoLayout {
    /** How far either way from the middle a row may run, in half tiles. */
    const val REACH = 7f

    private data class Dir(val x: Int, val y: Int)

    private val RIGHT = Dir(1, 0)
    private val LEFT = Dir(-1, 0)
    private val DOWN = Dir(0, 1)
    private val UP = Dir(0, -1)

    /** The tiles of [line] (in line order) placed round [start], the round's first tile. */
    fun layout(line: List<Tile>, start: Int, reach: Float = REACH): List<PlacedTile> {
        if (line.isEmpty()) return emptyList()
        val s = start.coerceIn(0, line.lastIndex)
        val out = arrayOfNulls<PlacedTile>(line.size)
        val first = line[s]
        val len0 = if (first.isDouble) 1f else 2f
        out[s] = box(0f, 0f, RIGHT, len0, first.isDouble, first.a, first.b)
        // Walking right from the first tile, each tile's a touches; walking left, its b does.
        arm((s + 1 until line.size).map { it to line[it] }, len0 / 2, RIGHT, DOWN, first.isDouble, reach, out) { it.a to it.b }
        arm((s - 1 downTo 0).map { it to line[it] }, -len0 / 2, LEFT, UP, first.isDouble, reach, out) { it.b to it.a }
        return out.map { it!! }
    }

    private fun arm(
        tiles: List<Pair<Int, Tile>>,
        startX: Float,
        outward: Dir,
        turn: Dir,
        startDouble: Boolean,
        reach: Float,
        out: Array<PlacedTile?>,
        ends: (Tile) -> Pair<Int, Int>,
    ) {
        var px = startX
        var py = 0f
        var d = outward
        var across = outward
        var prevDouble = startDouble
        // How far the line has gone down (or up) since it turned.
        var down = 0f
        for ((index, t) in tiles) {
            val double = t.isDouble
            val len = if (double) 1f else 2f
            if (d.y == 0 && abs(px + d.x * len) > reach) {
                // Near the edge: turn down (or up).
                val step = if (prevDouble) 1f else 0.5f
                px += -d.x * 0.5f + turn.x * step
                py += -d.y * 0.5f + turn.y * step
                across = d
                d = turn
                down = 0f
            } else if (d.y != 0 && down >= 2f) {
                // A tile's length down (so rows don't touch), then back the other way.
                val back = Dir(-across.x, 0)
                val step = if (prevDouble) 1f else 0.5f
                px += -d.x * 0.5f + back.x * step
                py += -d.y * 0.5f + back.y * step
                d = back
            }
            val (near, far) = ends(t)
            out[index] = box(px + d.x * len / 2, py + d.y * len / 2, d, len, double, near, far)
            px += d.x * len
            py += d.y * len
            if (d.y != 0) down += len
            prevDouble = double
        }
    }

    /** A tile centred on ([cx], [cy]) running along [d]; [near] touches the line behind it. */
    private fun box(cx: Float, cy: Float, d: Dir, len: Float, double: Boolean, near: Int, far: Int): PlacedTile {
        val wide = if (double) 2f else 1f
        val (w, h) = if (d.y == 0) len to wide else wide to len
        // Left or top half first: walking right or down, the near half is first.
        val forward = d.x > 0 || d.y > 0
        return PlacedTile(cx - w / 2, cy - h / 2, w, h, if (forward) near else far, if (forward) far else near)
    }
}
