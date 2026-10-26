package com.arcade.sudoku.game.numbers

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Number Cross levels: how many sums the grid holds, the share of numbers left blank, and the
 * operators used.
 */
enum class NumberLevel(val key: String, val label: String, val sums: Int, val blankShare: Double, val ops: String) {
    EASY("easy", "Easy", 5, 0.4, "+-"),
    MEDIUM("medium", "Medium", 7, 0.55, "+-*"),
    HARD("hard", "Hard", 9, 0.7, "+-*/");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: EASY
    }
}

/**
 * A Number Cross puzzle: a [w] × [h] grid, row by row, of '' (empty), whole numbers and the
 * tokens + - * / =. Every run across or down is one sum (number, operator, number, =, number).
 * [grid] is solved; [blanks] are the number cells players fill from the bank.
 */
data class NumberPuzzle(val w: Int, val h: Int, val grid: List<String>, val blanks: List<Int>) {
    /** The missing numbers, smallest first. */
    val bank get() = blanks.map { grid[it].toInt() }.sorted()

    fun toJson(): JSONObject = JSONObject().put("w", w).put("h", h).put("grid", JSONArray(grid)).put("blanks", JSONArray(blanks))

    companion object {
        fun from(json: JSONObject?): NumberPuzzle? {
            json ?: return null
            val g = json.optJSONArray("grid") ?: return null
            val b = json.optJSONArray("blanks") ?: return null
            return NumberPuzzle(json.optInt("w"), json.optInt("h"), (0 until g.length()).map { g.optString(it) }, (0 until b.length()).map { b.optInt(it) })
        }
    }
}

/** The rules, matching the server (backend/cloudflare/src/numbers.js). */
object NumberCross {
    const val SIZE = 11
    private const val OPS = "+-*/"

    fun isNumber(t: String) = t.length in 1..3 && t.all { it.isDigit() }

    /** Whether a sum [a, op, b, =, c] holds. */
    fun holds(t: List<String>): Boolean {
        if (t.size != 5 || t[3] != "=" || !isNumber(t[0]) || !isNumber(t[2]) || !isNumber(t[4])) return false
        val (x, y, z) = Triple(t[0].toInt(), t[2].toInt(), t[4].toInt())
        return when (t[1]) {
            "+" -> x + y == z
            "-" -> x - y == z
            "*" -> x * y == z
            "/" -> y != 0 && x == y * z
            else -> false
        }
    }

    /** The sums in a grid, as their five cell indexes; throws if a run isn't shaped like a sum. */
    fun sums(grid: List<String>, w: Int, h: Int): List<List<Int>> {
        val runs = mutableListOf<List<Int>>()
        fun scan(start: Int, step: Int, length: Int) {
            var run = mutableListOf<Int>()
            for (k in 0..length) {
                val i = start + k * step
                if (k < length && grid[i] != "") run += i else {
                    if (run.size > 1) runs += run
                    run = mutableListOf()
                }
            }
        }
        for (r in 0 until h) scan(r * w, 1, w)
        for (c in 0 until w) scan(c, w, h)
        for (run in runs) {
            val t = run.map { grid[it] }
            require(run.size == 5 && isNumber(t[0]) && t[1].length == 1 && t[1][0] in OPS && isNumber(t[2]) && t[3] == "=" && isNumber(t[4])) {
                "Every line in the grid must be one sum."
            }
        }
        return runs
    }

    /** Checks a puzzle as the server does; throws IllegalArgumentException if it's not valid. */
    fun check(p: NumberPuzzle): NumberPuzzle {
        require(p.w in 5..13 && p.h in 5..13) { "The puzzle is the wrong size." }
        require(p.grid.size == p.w * p.h && p.grid.all { it == "" || it == "=" || (it.length == 1 && it[0] in OPS) || isNumber(it) }) {
            "The puzzle grid is not valid."
        }
        val list = sums(p.grid, p.w, p.h)
        require(list.isNotEmpty() && list.all { s -> holds(s.map { p.grid[it] }) }) { "A sum in the puzzle is wrong." }
        val used = list.flatten().toSet()
        require(p.grid.indices.all { p.grid[it] == "" || it in used }) { "The puzzle grid is not valid." }
        val blanks = p.blanks.distinct().sorted()
        require(blanks.isNotEmpty() && blanks.all { it in p.grid.indices && isNumber(p.grid[it]) }) { "The puzzle blanks are not valid." }
        return p.copy(blanks = blanks)
    }

    private fun filled(p: NumberPuzzle, entries: List<String>) =
        p.grid.toMutableList().also { g -> p.blanks.forEachIndexed { k, i -> g[i] = entries.getOrElse(k) { "" } } }

    /** Percentage of the sums with a blank that hold with [entries] (one per blank). */
    fun progressOf(p: NumberPuzzle, entries: List<String>): Int {
        val grid = filled(p, entries)
        val blank = p.blanks.toSet()
        val open = sums(p.grid, p.w, p.h).filter { s -> s.any { it in blank } }
        if (open.isEmpty()) return 100
        return open.count { s -> holds(s.map { grid[it] }) } * 100 / open.size
    }

    /** Solved: every blank filled from the bank, each number as often as it appears, and every sum holds. */
    fun isSolved(p: NumberPuzzle, entries: List<String>): Boolean {
        if (entries.size != p.blanks.size || entries.any { !isNumber(it) }) return false
        if (entries.map { it.toInt() }.sorted() != p.bank) return false
        val grid = filled(p, entries)
        return sums(p.grid, p.w, p.h).all { s -> holds(s.map { grid[it] }) }
    }
}

/**
 * Builds puzzles on the host's phone: sums are laid like a crossword, each new one crossing an
 * old one at a shared number, then some numbers are blanked (every sum keeps at least one shown).
 */
class NumberCrossGenerator(private val random: Random = Random.Default) {
    private val size = NumberCross.SIZE

    fun generate(level: NumberLevel): NumberPuzzle {
        var best: Pair<List<String>, Int>? = null
        repeat(40) {
            val (grid, count) = layout(level)
            if (best == null || count > best!!.second) best = grid to count
            if (count >= level.sums) return blank(grid, level)
        }
        return blank(best!!.first, level)
    }

    /** Lays out up to [NumberLevel.sums] sums; returns the grid and how many fit. */
    private fun layout(level: NumberLevel): Pair<List<String>, Int> {
        val grid = MutableList(size * size) { "" }
        val across = BooleanArray(size * size)
        val down = BooleanArray(size * size)
        // The first sum runs across the middle.
        val first = sum(level, null, 0)!!
        val row = size / 2
        val col = random.nextInt(0, size - 4)
        put(grid, across, (0 until 5).map { row * size + col + it }, first)
        var count = 1
        repeat(400) {
            if (count >= level.sums) return grid to count
            // A number in only one sum, to cross with a new one going the other way.
            val open = grid.indices.filter { NumberCross.isNumber(grid[it]) && across[it] != down[it] }
            if (open.isEmpty()) return grid to count
            val at = open.random(random)
            val goingDown = across[at]
            val step = if (goingDown) size else 1
            val k = listOf(0, 2, 4).random(random)
            val cells = (0 until 5).map { at + (it - k) * step }
            if (!fits(grid, cells, at, goingDown)) return@repeat
            val tokens = sum(level, grid[at].toInt(), k) ?: return@repeat
            put(grid, if (goingDown) down else across, cells, tokens)
            count++
        }
        return grid to count
    }

    private fun put(grid: MutableList<String>, dir: BooleanArray, cells: List<Int>, tokens: List<String>) {
        cells.forEachIndexed { n, i -> grid[i] = tokens[n]; dir[i] = true }
    }

    /** Whether a sum over [cells] fits, touching the grid only where it crosses at [cross]. */
    private fun fits(grid: List<String>, cells: List<Int>, cross: Int, goingDown: Boolean): Boolean {
        val r0 = cross / size
        val c0 = cross % size
        for (i in cells) {
            if (i < 0 || i >= grid.size) return false
            if (goingDown && i % size != c0) return false
            if (!goingDown && i / size != r0) return false
        }
        val step = if (goingDown) size else 1
        // Nothing just before or after the sum, so it doesn't run into another.
        for (end in listOf(cells.first() - step, cells.last() + step)) {
            val inLine = end in grid.indices && (if (goingDown) end % size == c0 else end / size == r0)
            if (inLine && grid[end] != "") return false
        }
        // Every other cell empty, with empty neighbours on both sides.
        for (i in cells) {
            if (i == cross) continue
            if (grid[i] != "") return false
            val r = i / size
            val c = i % size
            val sides = if (goingDown) listOf(r to c - 1, r to c + 1) else listOf(r - 1 to c, r + 1 to c)
            if (sides.any { (sr, sc) -> sr in 0 until size && sc in 0 until size && grid[sr * size + sc] != "" }) return false
        }
        return true
    }

    /** A random sum with [fixed] at token [k] (0, 2 or 4), or any when [fixed] is null. */
    private fun sum(level: NumberLevel, fixed: Int?, k: Int): List<String>? {
        repeat(30) {
            val op = level.ops.random(random)
            val v = fixed ?: random.nextInt(2, 13)
            val (a, b, c) = when (op) {
                '+' -> when (k) {
                    0 -> random.nextInt(1, 13).let { Triple(v, it, v + it) }
                    2 -> random.nextInt(1, 13).let { Triple(it, v, it + v) }
                    else -> if (v < 2) return@repeat else random.nextInt(1, v).let { Triple(it, v - it, v) }
                }
                '-' -> when (k) {
                    0 -> if (v < 2) return@repeat else random.nextInt(1, v).let { Triple(v, it, v - it) }
                    2 -> random.nextInt(1, 13).let { Triple(it + v, v, it) }
                    else -> random.nextInt(1, 13).let { Triple(v + it, it, v) }
                }
                '*' -> when (k) {
                    0 -> random.nextInt(2, 10).let { Triple(v, it, v * it) }
                    2 -> random.nextInt(2, 10).let { Triple(it, v, it * v) }
                    else -> (2..9).filter { v % it == 0 && v / it >= 2 }.randomOrNull(random)?.let { Triple(it, v / it, v) } ?: return@repeat
                }
                else -> when (k) {
                    0 -> (2..9).filter { v % it == 0 && v / it >= 2 }.randomOrNull(random)?.let { Triple(v, it, v / it) } ?: return@repeat
                    2 -> random.nextInt(2, 10).let { Triple(v * it, v, it) }
                    else -> random.nextInt(2, 10).let { Triple(v * it, it, v) }
                }
            }
            if (listOf(a, b, c).all { it in 1..99 }) return listOf(a.toString(), op.toString(), b.toString(), "=", c.toString())
        }
        return null
    }

    private fun blank(grid: List<String>, level: NumberLevel): NumberPuzzle {
        val sums = NumberCross.sums(grid, size, size)
        val numbers = grid.indices.filter { NumberCross.isNumber(grid[it]) }
        val target = (numbers.size * level.blankShare).toInt().coerceAtLeast(1)
        val blanks = mutableSetOf<Int>()
        for (i in numbers.shuffled(random)) {
            if (blanks.size >= target) break
            // Each sum keeps at least one of its numbers showing.
            val ok = sums.filter { i in it }.all { s -> s.count { it !in blanks && it != i && NumberCross.isNumber(grid[it]) } >= 1 }
            if (ok) blanks += i
        }
        return NumberPuzzle(size, size, grid, blanks.sorted())
    }
}
