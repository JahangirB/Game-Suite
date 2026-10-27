package com.arcade.sudoku.game.bingo

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BingoTest {
    @Test
    fun cardsAndCallsMatchTheServer() {
        repeat(50) {
            val card = Bingo.dealCard()
            assertEquals(0, card[Bingo.FREE])
            card.forEachIndexed { i, n -> if (i != Bingo.FREE) assertEquals(i % 5, (n - 1) / 15) }
            assertEquals(25, card.toSet().size)
        }
        assertEquals((1..75).toList(), Bingo.shuffledCalls().sorted())
        assertEquals(12, Bingo.LINES.size)
        assertEquals(0, Bingo.calledCount(-1))
        assertEquals(1, Bingo.calledCount(0))
        assertEquals(2, Bingo.calledCount(Bingo.CALL_MS))
        assertEquals(75, Bingo.calledCount(1_000_000_000))
        val card = Bingo.dealCard()
        assertNull(Bingo.fullLine(card, setOf(card[10], card[11], card[13])))
        assertEquals(listOf(10, 11, 12, 13, 14), Bingo.fullLine(card, setOf(card[10], card[11], card[13], card[14])))
    }

    @Test
    fun nearbyRoundOnlyALineOfCallsWins() {
        val room = LocalRoom("BG01", "Alice", null, GameKind.BINGO)
        val guest = room.join("Bob", null, null, listOf("bingo"))
        room.setBingoReady(LocalRoom.HOST, true, 0, Random(7))
        room.setBingoReady(guest, true, 0, Random(7))
        val go = LocalRoom.COUNTDOWN_MS
        assertThrows(ApiException::class.java) { room.claimBingo(guest, 0) }
        val view = parseRoom(room.view(guest, go)).bingo!!
        val card = view.card!!
        val calls = view.calls!!
        assertEquals(75, calls.size)
        // The first moment one of Bob's lines is all called.
        val callsNeeded = (1..75).first { k -> Bingo.fullLine(card, calls.take(k).toSet()) != null }
        assertThrows(ApiException::class.java) { room.claimBingo(guest, go + (callsNeeded - 2) * Bingo.CALL_MS) }
        room.claimBingo(guest, go + (callsNeeded - 1) * Bingo.CALL_MS)
        val end = parseRoom(room.view(LocalRoom.HOST, go + callsNeeded * Bingo.CALL_MS))
        assertEquals(RoomStatus.FINISHED, end.status)
        assertEquals("opponent", end.winner)
        assertEquals(card, end.bingo!!.winCard)
        assertTrue(end.bingo!!.line!!.all { it == Bingo.FREE || card[it] in calls.take(callsNeeded) })
    }

    @Test
    fun theCallsRunningOutIsADraw() {
        val room = LocalRoom("BG02", "Alice", null, GameKind.BINGO)
        val guest = room.join("Bob", null, null, listOf("bingo"))
        room.setBingoReady(LocalRoom.HOST, true, 0)
        room.setBingoReady(guest, true, 0)
        val go = LocalRoom.COUNTDOWN_MS
        // The countdown's end is pushed once, so everyone gets their card.
        assertEquals(true, room.checkTimers(go))
        assertEquals(false, room.checkTimers(go + Bingo.ROUND_MS - 1))
        assertEquals(true, room.checkTimers(go + Bingo.ROUND_MS))
        assertEquals(RoomStatus.FINISHED, parseRoom(room.view(guest, go + Bingo.ROUND_MS)).status)
    }
}
