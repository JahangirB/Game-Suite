package com.pippop.game.numbers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NumberCrossTest {
    // The same hand-made puzzle as the server tests: four sums round a square.
    private val grid = listOf(
        "3", "+", "4", "=", "7",
        "*", "", "", "", "-",
        "2", "", "", "", "5",
        "=", "", "", "", "=",
        "6", "-", "4", "=", "2",
    )
    private val puzzle = NumberPuzzle(5, 5, grid, listOf(2, 14, 20))

    @Test
    fun rulesMatchTheServer() {
        val p = NumberCross.check(puzzle)
        assertEquals(4, NumberCross.sums(p.grid, 5, 5).size)
        assertEquals(listOf(4, 5, 6), p.bank)
        assertTrue(NumberCross.isSolved(p, listOf("4", "5", "6")))
        assertFalse(NumberCross.isSolved(p, listOf("4", "6", "5")))
        assertEquals(25, NumberCross.progressOf(p, listOf("4", "", "")))
        assertTrue(NumberCross.holds(listOf("8", "/", "2", "=", "4")))
        assertThrows(IllegalArgumentException::class.java) { NumberCross.check(puzzle.copy(grid = grid.toMutableList().also { it[4] = "8" })) }
        assertThrows(IllegalArgumentException::class.java) { NumberCross.check(puzzle.copy(blanks = listOf(1))) }
    }

    @Test
    fun generatedPuzzlesAreValidAndSolvable() {
        for (level in NumberLevel.entries) {
            repeat(30) { seed ->
                val p = NumberCrossGenerator(Random(seed * 7 + level.ordinal)).generate(level)
                NumberCross.check(p)
                val sums = NumberCross.sums(p.grid, p.w, p.h)
                assertTrue("$level $seed has ${sums.size} sums", sums.size >= 3)
                assertTrue(NumberCross.isSolved(p, p.blanks.map { p.grid[it] }))
                // Every sum keeps at least one number showing.
                for (s in sums) assertTrue(s.any { NumberCross.isNumber(p.grid[it]) && it !in p.blanks })
                // Operators only from the level.
                assertTrue(p.grid.filter { it.length == 1 && it[0] in "+-*/" }.all { it[0] in level.ops })
            }
        }
    }
}
