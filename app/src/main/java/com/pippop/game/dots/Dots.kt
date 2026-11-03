package com.pippop.game.dots

import com.pippop.game.turns.TurnMatch
import com.pippop.game.turns.TurnResult
import org.json.JSONObject
import kotlin.random.Random

/**
 * A Dots & Boxes board: [w] × [h] boxes, [mask] rows of '#' (a box) and ' ' (a gap). Lines are
 * numbered as on the server (backend/cloudflare/src/dots.js): horizontal ones first (row r
 * 0..h, column c: r * w + c), then vertical ones ([horizontal] + r * (w + 1) + c). Boxes are
 * r * w + c. A line next to no box doesn't exist.
 */
data class DotsShape(val key: String, val w: Int, val h: Int, val mask: List<String>) {
    val horizontal get() = (h + 1) * w
    val lines get() = horizontal + h * (w + 1)
    val boxes get() = w * h

    fun exists(box: Int) = mask[box / w][box % w] == '#'

    fun boxLines(box: Int): List<Int> {
        val r = box / w
        val c = box % w
        return listOf(r * w + c, (r + 1) * w + c, horizontal + r * (w + 1) + c, horizontal + r * (w + 1) + c + 1)
    }

    fun lineBoxes(line: Int): List<Int> =
        if (line < horizontal) {
            val r = line / w
            listOf(r - 1, r).filter { it in 0 until h }.map { it * w + line % w }.filter(::exists)
        } else {
            val r = (line - horizontal) / (w + 1)
            val c = (line - horizontal) % (w + 1)
            listOf(c - 1, c).filter { it in 0 until w }.map { r * w + it }.filter(::exists)
        }

    /** Every line, '.' where it can be drawn and ' ' next to no box; every box, '.' or ' '. */
    fun emptyLines() = (0 until lines).joinToString("") { if (lineBoxes(it).isEmpty()) " " else "." }
    fun emptyBoxes() = (0 until boxes).joinToString("") { if (exists(it)) "." else " " }

    /** How many sides of [box] are drawn in [lines]. */
    fun sides(lines: String, box: Int) = boxLines(box).count { lines[it] != '.' }

    companion object {
        private fun rect(n: Int) = DotsShape("${n}x$n", n, n, List(n) { "#".repeat(n) })

        /** The boards to pick from: the usual 4 × 4, 5 × 5, 6 × 6 and the heart. */
        val ALL = listOf(
            rect(4), rect(5), rect(6),
            DotsShape("heart", 7, 6, listOf(" ## ## ", "#######", "#######", " ##### ", "  ###  ", "   #   ")),
        )

        fun from(key: String?) = ALL.firstOrNull { it.key == key } ?: ALL[0]

        fun label(key: String) = when (key) {
            "heart" -> "Heart"
            else -> key.replace("x", " × ")
        }
    }
}

/** The usual 4 × 4 board, as before boards could change. */
object Dots {
    private val G = DotsShape.from("4x4")
    const val BOXES = 4
    const val LINES = 40

    fun boxLines(box: Int) = G.boxLines(box)
    fun lineBoxes(line: Int) = G.lineBoxes(line)
    fun sides(lines: String, box: Int) = G.sides(lines, box)
}

/** One Dots & Boxes round on [shape]: moves are {"line": n}. */
class DotsMatch(val shape: DotsShape = DotsShape.from("4x4")) : TurnMatch {
    var lines = shape.emptyLines()
        private set
    var boxes = shape.emptyBoxes()
        private set
    var last: Pair<Char, Int>? = null
        private set

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val line = move.optInt("line", -1)
        require(line in 0 until shape.lines && lines[line] != ' ') { "Pick a line between two dots." }
        require(lines[line] == '.') { "That line is already drawn." }
        lines = lines.replaceAt(line, mark)
        last = mark to line
        var closed = 0
        for (box in shape.lineBoxes(line)) {
            if (boxes[box] == '.' && shape.sides(lines, box) == 4) {
                boxes = boxes.replaceAt(box, mark)
                closed++
            }
        }
        if ('.' in boxes) return TurnResult(again = closed > 0)
        return TurnResult(again = false, over = true, winner = TurnMatch.winnerOf(boxes), reason = "most")
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject = JSONObject()
        .put("dotsBoard", shape.key)
        .put("lines", TurnMatch.side(lines, mark, seats))
        .put("boxes", TurnMatch.side(boxes, mark, seats))
        .put("lastMove", last?.let { (by, line) -> TurnMatch.lastMove(by, mark, "line", line) } ?: JSONObject.NULL)
}

/**
 * The computer opponent: takes every box it can, avoids drawing a box's third side, and when
 * it must give boxes away it picks the line that gives away the fewest.
 */
class DotsComputer(private val random: Random = Random.Default, private val shape: DotsShape = DotsShape.from("4x4")) {
    fun move(lines: String): Int {
        val free = (0 until shape.lines).filter { lines[it] == '.' }
        free.firstOrNull { line -> shape.lineBoxes(line).any { shape.sides(lines, it) == 3 } }?.let { return it }
        val safe = free.filter { line -> shape.lineBoxes(line).none { shape.sides(lines, it) == 2 } }
        if (safe.isNotEmpty()) return safe.random(random)
        return free.minBy { line -> givenAway(lines.replaceAt(line, 'x')) }
    }

    /** Boxes the opponent could take in a row after [lines]. */
    private fun givenAway(start: String): Int {
        var lines = start
        var taken = 0
        while (true) {
            val line = (0 until shape.lines).firstOrNull { l ->
                lines[l] == '.' && shape.lineBoxes(l).any { shape.sides(lines, it) == 3 }
            } ?: return taken
            taken += shape.lineBoxes(line).count { shape.sides(lines, it) == 3 }
            lines = lines.replaceAt(line, 'x')
        }
    }
}

internal fun String.replaceAt(index: Int, c: Char) = substring(0, index) + c + substring(index + 1)
