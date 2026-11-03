package com.pippop.game.memory

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MemoryDeckTest {
    @Test
    fun everyDeckIsPairsAndOnlyTheBigOneHasGoldCards() {
        for (cards in MemoryMatch.DECKS) {
            val m = MemoryMatch(Random(cards), cards)
            assertEquals(cards, m.deck.size)
            assertTrue(m.deck.groupingBy { it }.eachCount().values.all { it == 2 })
            assertEquals(cards == 36, m.deck.any { it.gold })
        }
        // An odd size falls back to the usual 16.
        assertEquals(16, MemoryMatch(Random(1), 20).deck.size)
    }

    @Test
    fun goldCardsTravelAsAStarredKey() {
        val gold = CardFace.from("fox*")!!
        assertTrue(gold.gold)
        assertEquals("fox*", gold.key)
        val m = MemoryMatch(Random(2), 36)
        assertThrows(IllegalArgumentException::class.java) { m.play('h', JSONObject().put("card", 36)) }
        m.play('h', JSONObject().put("card", 35))
    }
}
