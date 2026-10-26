package com.arcade.sudoku.game.ttt

import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TttTest {
    @Test
    fun aMoveSendsTheOpponentToTheMatchingBoard() {
        val match = TttMatch()
        match.play('h', JSONObject().put("cell", 4))
        assertEquals(4, match.position.next)
        assertThrows(IllegalArgumentException::class.java) { match.play('g', JSONObject().put("cell", 9)) }
        match.play('g', JSONObject().put("cell", 36))
        assertEquals(0, match.position.next)
    }

    @Test
    fun aDecidedBoardFreesTheNextMove() {
        val pos = Ttt.play(TttPosition(cells = "hh" + ".".repeat(79), next = 0), 'h', 2)
        assertEquals('h', pos.boards[0])
        val after = Ttt.play(pos, 'g', 18)
        assertEquals(-1, after.next)
        assertFalse(Ttt.isLegal(after, 5))
        assertTrue(Ttt.isLegal(after, 40))
    }

    @Test
    fun aFullBoardWithNoLineIsDrawn() {
        val pos = Ttt.play(TttPosition(cells = "hghhgggh." + ".".repeat(72), boards = ".ghhggghh", next = 0), 'h', 8)
        assertEquals('d', pos.boards[0])
    }

    @Test
    fun theComputerTakesAWinningBoardAndAvoidsGivingOneAway() {
        // The computer ('o') owns boards 0 and 1 and can take board 2 with cell 20.
        val win = TttPosition(cells = ".".repeat(18) + "oo" + ".".repeat(61), boards = "oo.......", next = 2)
        assertEquals(20, TttComputer(Random(1)).move(win, 'o', 'h'))
        // Board 3 is one move from the player's; every other move keeps them out of it.
        val danger = TttPosition(cells = ".".repeat(27) + "hh" + ".".repeat(52), next = -1)
        repeat(20) { seed ->
            val cell = TttComputer(Random(seed)).move(danger, 'o', 'h')
            assertTrue("cell $cell sends the player to board 3", cell % 9 != 3 || cell == 29)
        }
    }

    @Test
    fun gamesAgainstTheComputerEnd() {
        val random = Random(3)
        repeat(20) {
            val match = TttMatch()
            var mark = 'h'
            var over = false
            var moves = 0
            while (!over) {
                val cell = if (mark == 'o') TttComputer(random).move(match.position, 'o', 'h') else Ttt.legalMoves(match.position).random(random)
                over = match.play(mark, JSONObject().put("cell", cell)).over
                mark = if (mark == 'h') 'o' else 'h'
                moves++
            }
            assertTrue(moves <= 81)
        }
    }

    @Test
    fun nearbyRoomPlaysLikeTheServer() {
        val room = LocalRoom("TTT1", "Alice", null, GameKind.TTT)
        room.join("Bob", null, null, listOf("ttt"))
        room.setTurnReady(LocalRoom.HOST, true, 0)
        room.setTurnReady(LocalRoom.GUEST, true, 0, Random(0))
        val first = parseRoom(room.view(LocalRoom.HOST, 0))
        assertEquals(RoomStatus.PLAYING, first.status)
        val starter = if (first.turns!!.turn == "me") LocalRoom.HOST else LocalRoom.GUEST
        room.move(starter, JSONObject().put("cell", 40), 0)
        val view = parseRoom(room.view(LocalRoom.other(starter), 0)).turns!!
        assertEquals("me", view.turn)
        assertEquals('o', view.board!![40])
        assertEquals(4, view.nextBoard)
        assertEquals(".........", view.smallBoards)
        assertEquals(40, view.lastCell)
    }
}
