package com.arcade.sudoku.game.dominoes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DominoesTest {
    @Test
    fun fullSetHasTwentyEightTiles() {
        assertEquals(28, Dominoes.fullSet().size)
        assertEquals(168, Dominoes.pips(Dominoes.fullSet()))
    }

    @Test
    fun tilesTurnToMatchTheEnd() {
        val line = listOf(Tile(2, 5))
        assertEquals(listOf("left", "right"), Dominoes.fits(line, Tile(5, 2)))
        assertEquals(listOf(Tile(3, 2), Tile(2, 5)), Dominoes.place(line, Tile(2, 3), "left"))
        assertEquals(listOf(Tile(2, 5), Tile(5, 6)), Dominoes.place(line, Tile(6, 5), "right"))
        assertEquals(emptyList<String>(), Dominoes.fits(line, Tile(1, 1)))
    }

    @Test
    fun dealsSevenToTwoAndFiveToFourInTheDrawGame() {
        val two = DominoMatch(listOf('h', 'g'), DominoOptions(), Random(1))
        assertEquals(7, two.hands.getValue('h').size)
        assertEquals(14, two.boneyard.size)
        val four = DominoMatch(listOf('h', 'g', '3', '4'), DominoOptions(), Random(1))
        assertEquals(5, four.hands.getValue('4').size)
        assertEquals(8, four.boneyard.size)
        val block = DominoMatch(listOf('h', 'g', '3'), DominoOptions(draw = false), Random(1))
        assertEquals(7, block.hands.getValue('3').size)
        assertTrue(block.boneyard.isEmpty())
    }

    @Test
    fun highestDoubleStarts() {
        val m = DominoMatch(listOf('h', 'g'), DominoOptions(), Random(3))
        val best = m.marks.maxBy { k -> m.hands.getValue(k).maxOf { (if (it.isDouble) 100 else 0) + it.pips } }
        assertEquals(best, m.firstMark)
    }

    @Test
    fun cannotDrawOrPassWithAPlayableTile() {
        val m = DominoMatch(listOf('h', 'g'), DominoOptions(), Random(5))
        val first = m.firstMark
        // The line is empty, so every tile fits.
        assertThrows(IllegalArgumentException::class.java) { m.play(first, JSONObject().put("draw", true)) }
        assertThrows(IllegalArgumentException::class.java) { m.play(first, JSONObject().put("pass", true)) }
        val result = m.play(first, JSONObject().put("tile", 0))
        assertFalse(result.again)
        assertEquals(1, m.line.size)
    }

    @Test
    fun teamsShareAScoreAndAMatchEndsAtTheTarget() {
        val marks = listOf('h', 'g', '3', '4')
        val m = DominoMatch(marks, DominoOptions(teams = true, target = 50), Random(7))
        assertTrue(m.sameSide('h', '3'))
        assertFalse(m.sameSide('h', 'g'))
        // Computers play the whole match out; it must end with a side at or past 50.
        val computer = DominoComputer()
        var turn = m.firstMark
        var over = false
        repeat(5000) {
            if (over) return@repeat
            val r = m.play(turn, computer.move(m, turn))
            over = r.over
            if (r.over) {
                assertTrue(m.scores.getValue(m.sideOf(r.winner!!)) >= 50)
                assertEquals(setOf("A", "B"), m.scores.keys)
            }
            turn = r.turn ?: if (r.again) turn else marks[(marks.indexOf(turn) + 1) % marks.size]
        }
        assertTrue(over)
    }

    @Test
    fun viewsParseBack() {
        val m = DominoMatch(listOf('h', 'g', '3'), DominoOptions(target = 150), Random(2))
        val v = DominoView.from(m.view('g', mapOf('h' to 'q', '3' to 'o')))
        assertEquals(5, v.hand.size)
        assertEquals(setOf('m', 'o', 'q'), v.counts.keys)
        assertEquals(150, v.target)
        assertNull(v.lastMove)
        assertTrue(v.canPlay)
    }
}
