package com.pippop.game.clash

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ClashTest {
    @Test
    fun theDeckIsTheClassic108() {
        val deck = Clash.fullDeck()
        assertEquals(108, deck.size)
        assertEquals(8, deck.count { Clash.isWild(it) })
        assertTrue(Clash.fits("r7", "b7", 'b'))
        assertTrue(Clash.fits("bS", "r5", 'b'))
        assertFalse(Clash.fits("g2", "b7", 'b'))
        assertTrue(Clash.fits("F", "b7", 'b'))
    }

    @Test
    fun aDealGivesSevenEachAndANumberOnThePile() {
        val m = ClashMatch(listOf('h', 'g', '3'), Random(4))
        assertEquals(7, m.hands.getValue('3').size)
        assertTrue(m.top[1].isDigit())
        assertEquals(108 - 21 - 1, m.deck.size)
    }

    @Test
    fun computersPlayAWholeGameToTheEnd() {
        repeat(20) { seed ->
            val marks = listOf('h', 'g', '3', '4')
            val m = ClashMatch(marks, Random(seed))
            val computer = ClashComputer()
            var turn = 'h'
            var winner: Char? = null
            repeat(3000) {
                if (winner != null) return@repeat
                val r = m.play(turn, computer.move(m, turn))
                if (r.over) winner = r.winner else turn = r.turn!!
            }
            val w = winner
            assertNotNull("game $seed ends", w)
            assertTrue(m.hands.getValue(w!!).isEmpty())
            // The view counts every hand, each under its own letter.
            val view = ClashView.from(m.view('h', mapOf('g' to 'o', '3' to 'p', '4' to 'q')))
            assertEquals(m.hands.values.sumOf { it.size }, view.counts.values.sum())
            assertEquals(m.deck.size, view.deck)
        }
    }

    @Test
    fun aWildNeedsAColour() {
        val m = ClashMatch(listOf('h', 'g'), Random(1))
        val hand = m.hands.getValue('h')
        hand.clear()
        hand += listOf("W", "r1")
        val bad = runCatching { m.play('h', JSONObject().put("card", 0)) }
        assertTrue(bad.isFailure)
        m.play('h', JSONObject().put("card", 0).put("colour", "g"))
        assertEquals('g', m.colour)
    }
}
