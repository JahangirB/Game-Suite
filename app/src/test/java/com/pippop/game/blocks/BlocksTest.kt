package com.pippop.game.blocks

import com.pippop.data.GameKind
import com.pippop.data.nearby.LocalRoom
import com.pippop.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlocksTest {
    private fun move(piece: Int, orient: Int, x: Int, y: Int) = JSONObject().put("piece", piece).put("orient", orient).put("x", x).put("y", y)

    @Test
    fun piecesMatchTheServer() {
        assertEquals(21, Blocks.PIECES.size)
        assertEquals(89, Blocks.PIECES.sumOf { it.size })
        assertEquals(91, Blocks.ORIENTS.sumOf { it.size })
        // Same turn as blocks.js: (x, y) -> (-y, x), then back to the top-left.
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), Blocks.shape(2, 1))
    }

    @Test
    fun startThenCornersOnly() {
        val m = BlocksMatch(listOf('h', 'g'))
        assertThrows(IllegalArgumentException::class.java) { m.play('h', move(0, 0, 0, 0)) }
        assertEquals('g', m.play('h', move(7, 0, 3, 3)).turn)
        m.play('g', move(7, 0, 9, 9))
        assertThrows(IllegalArgumentException::class.java) { m.play('h', move(0, 0, 4, 4)) }
        assertThrows(IllegalArgumentException::class.java) { m.play('h', move(0, 0, 5, 4)) }
        assertThrows(IllegalArgumentException::class.java) { m.play('h', move(0, 0, 8, 8)) }
        m.play('h', move(0, 0, 5, 5))
        val v = BlocksView.from(m.view('g', mapOf('h' to 'o')))
        assertEquals(5, v.placed['o'])
        assertEquals('o', v.at(5, 5))
        assertTrue(v.allows(Placement(0, 0, 8, 8)))
        assertFalse(v.allows(Placement(0, 0, 9, 8)))
    }

    @Test
    fun computersPlayAGameToTheEnd() {
        val m = BlocksMatch(listOf('h', 'g', '3'))
        var turn: Char? = 'h'
        var moves = 0
        while (turn != null) {
            val p = m.computerMove(turn, 0.6, Random(moves))!!
            val r = m.play(turn, move(p.piece, p.orient, p.x, p.y))
            turn = if (r.over) null else r.turn
            moves++
        }
        assertTrue(moves > 20)
        assertTrue(listOf('h', 'g', '3').none { m.canMove(it) })
        assertEquals(m.board.count { it != '.' }, m.placed.values.sum())
    }

    @Test
    fun nearbyRoundPlaysLikeTheServer() {
        val room = LocalRoom("BK01", "Alice", null, GameKind.BLOCKS)
        val guest = room.join("Bob", null, null, listOf("blocks"))
        room.setTurnReady(LocalRoom.HOST, true, 0)
        room.setTurnReady(guest, true, 0)
        room.move(LocalRoom.HOST, move(0, 0, 4, 4), 1)
        val bob = parseRoom(room.view(guest, 2)).turns!!
        assertEquals("me", bob.turn)
        assertEquals(14, bob.blocks!!.size)
        assertEquals('o', bob.blocks!!.at(4, 4))
        assertEquals(20, bob.blocks!!.left['o']!!.size)
    }
}
