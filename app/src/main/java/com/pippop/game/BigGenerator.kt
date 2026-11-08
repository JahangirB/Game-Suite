package com.pippop.game

import kotlin.random.Random

/**
 * Puzzles for boards of joined grids ([SudokuShape]), with exactly one solution over the whole
 * board. Made on the phone that opens the game (and for solo), as Number Cross puzzles are.
 */
object BigGenerator {
    class Result(val shape: SudokuShape, val puzzle: String, val solution: String)

    /**
     * A random full board, then cells taken out in random order wherever the board keeps one
     * solution, until the share of [holes] in 81 is reached (or no more can go).
     */
    fun generate(shape: SudokuShape, holes: Int, random: Random = Random.Default): Result {
        val solver = Solver(shape)
        while (!solver.fill(random)) solver.clear()
        val solution = shape.encode(solver.grid.toList())
        val target = shape.playable * holes / 81
        var removed = 0
        for (i in (0 until shape.cells).filter { shape.onBoard(it) }.shuffled(random)) {
            if (removed >= target) break
            val digit = solver.grid[i]
            solver.unset(i)
            if (solver.count(2, BUDGET) == 1) removed++ else solver.set(i, digit)
        }
        return Result(shape, shape.encode(solver.grid.toList()), solution)
    }

    /** Number of solutions of [board] (text of [shape]), up to [limit]. */
    fun countSolutions(shape: SudokuShape, board: String, limit: Int = 2): Int {
        val s = Solver(shape)
        shape.parse(board).forEachIndexed { i, d -> if (d != 0) s.set(i, d) }
        return s.count(limit, Int.MAX_VALUE)
    }

    /** How many steps one uniqueness check may take; past it the removal is simply not kept. */
    private const val BUDGET = 200_000

    private class Solver(val shape: SudokuShape) {
        val grid = IntArray(shape.cells)
        private val used = IntArray(shape.units.size)
        private val open = (0 until shape.cells).filter { shape.onBoard(it) }.toIntArray()
        private var steps = 0

        fun clear() {
            grid.fill(0)
            used.fill(0)
        }

        fun set(i: Int, d: Int) {
            grid[i] = d
            for (u in shape.unitsOf[i]) used[u] = used[u] or (1 shl d)
        }

        fun unset(i: Int) {
            val bit = (1 shl grid[i]).inv()
            for (u in shape.unitsOf[i]) used[u] = used[u] and bit
            grid[i] = 0
        }

        private fun candidates(i: Int): Int {
            var u = 0
            for (k in shape.unitsOf[i]) u = u or used[k]
            return ALL and u.inv()
        }

        /** The empty cell with the fewest candidates (-1: none left), and its candidates. */
        private fun pick(): Long {
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in open) {
                if (grid[i] != 0) continue
                val m = candidates(i)
                val n = Integer.bitCount(m)
                if (n < bestCount) {
                    best = i
                    bestMask = m
                    bestCount = n
                    if (n <= 1) break
                }
            }
            return (best.toLong() shl 32) or (bestMask.toLong() and 0xffffffffL)
        }

        /** Fills the board at random; false after too much backtracking (start again). */
        fun fill(random: Random): Boolean {
            steps = 0
            return fillFrom(random)
        }

        private fun fillFrom(random: Random): Boolean {
            if (++steps > 20_000) return false
            val p = pick()
            val i = (p shr 32).toInt()
            if (i < 0) return true
            val mask = p.toInt()
            for (d in (1..9).filter { mask and (1 shl it) != 0 }.shuffled(random)) {
                set(i, d)
                if (fillFrom(random)) return true
                unset(i)
                if (steps > 20_000) return false
            }
            return false
        }

        /** Solutions up to [limit]; a check that runs past [budget] steps counts as "more than one". */
        fun count(limit: Int, budget: Int): Int {
            steps = 0
            val n = countFrom(limit, budget)
            return if (steps > budget) limit else n
        }

        private fun countFrom(limit: Int, budget: Int): Int {
            if (++steps > budget) return limit
            val p = pick()
            val i = (p shr 32).toInt()
            if (i < 0) return 1
            var mask = p.toInt()
            var found = 0
            while (mask != 0) {
                val d = Integer.numberOfTrailingZeros(mask)
                mask = mask and (mask - 1)
                set(i, d)
                found += countFrom(limit - found, budget)
                unset(i)
                if (found >= limit) break
            }
            return found
        }
    }

    private const val ALL = 0b1111111110
}
