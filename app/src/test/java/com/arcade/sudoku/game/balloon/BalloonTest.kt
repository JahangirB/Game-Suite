package com.arcade.sudoku.game.balloon

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalloonTest {
    @Test
    fun pickThenGuessInTurnSkippingThePicker() {
        val m = BalloonMatch(listOf('h', 'g', '3'))
        assertEquals('h', m.firstMark)
        assertEquals('g', m.play('h', JSONObject().put("word", "Hello")).turn)
        assertNull(BalloonView.from(m.view('g')).word)
        assertEquals("HELLO", BalloonView.from(m.view('h')).word)
        assertEquals('3', m.play('g', JSONObject().put("letter", "l")).turn)
        assertEquals("__LL_", BalloonView.from(m.view('3')).pattern)
        assertEquals('g', m.play('3', JSONObject().put("letter", "z")).turn)
        assertEquals(2, m.scores['g'])
    }

    @Test
    fun everyonePicksOnceThenTheMostPointsWins() {
        val m = BalloonMatch(listOf('h', 'g'))
        m.play('h', JSONObject().put("word", "zzz"))
        for (c in "abcdefg") m.play('g', JSONObject().put("letter", c.toString()))
        assertEquals(5, m.scores['h'])
        m.play('g', JSONObject().put("word", "abc"))
        m.play('h', JSONObject().put("letter", "a"))
        m.play('h', JSONObject().put("letter", "b"))
        val r = m.play('h', JSONObject().put("letter", "c"))
        assertTrue(r.over)
        assertEquals('h', r.winner)
    }

    @Test
    fun theComputerGuessesFromWordsThatFit() {
        val words = listOf("apple", "angle", "ample", "zebra")
        // A_PLE: P is known, so only A?PLE words fit; M and P... M is the best open letter.
        assertEquals('M', BalloonMatch.bestLetter("A_PLE", setOf('A', 'P', 'L', 'E'), words))
    }
}
