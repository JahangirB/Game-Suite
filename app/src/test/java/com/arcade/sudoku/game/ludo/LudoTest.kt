package com.arcade.sudoku.game.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LudoTest {
    @Test
    fun theRingIs52NeighbouringSquares() {
        val ring = Ludo.ringCells
        assertEquals(52, ring.size)
        assertEquals(52, ring.toSet().size)
        // Each square touches the next, all the way round.
        for (i in ring.indices) {
            val (x1, y1) = ring[i]
            val (x2, y2) = ring[(i + 1) % ring.size]
            assertEquals("squares $i and ${i + 1}", 1, kotlin.math.abs(x1 - x2) + kotlin.math.abs(y1 - y2))
        }
        // Each colour's start is next to its yard, and its home column leads off the ring.
        assertEquals(1 to 6, ring[0])
        assertEquals(8 to 1, ring[13])
        for (c in 0..3) {
            val entry = ring[Ludo.square(c, 50)!!]
            val (hx, hy) = Ludo.homeCell(c, 0)
            assertEquals(1, kotlin.math.abs(entry.first - hx) + kotlin.math.abs(entry.second - hy))
        }
    }

    @Test
    fun twoPlayersSitOpposite() {
        assertEquals(mapOf('h' to 0, 'g' to 2), LudoMatch(listOf('h', 'g')).colours)
    }

    @Test
    fun computersPlayWholeGames() {
        repeat(10) { seed ->
            val marks = listOf('h', 'g', '3', '4')
            val m = LudoMatch(marks, Random(seed))
            val computer = LudoComputer()
            var turn = 'h'
            var winner: Char? = null
            repeat(30000) {
                if (winner != null) return@repeat
                val r = m.play(turn, computer.move(m, turn))
                if (r.over) winner = r.winner else turn = r.turn!!
            }
            assertNotNull("game $seed ends", winner)
            assertTrue(m.tokens.getValue(winner!!).all { it == Ludo.HOME })
        }
    }
}
