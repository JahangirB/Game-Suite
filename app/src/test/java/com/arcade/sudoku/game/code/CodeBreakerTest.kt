package com.arcade.sudoku.game.code

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBreakerTest {
    @Test
    fun scoringMatchesTheServer() {
        assertEquals(4 to 0, CodeBreaker.score("rygb", "rygb"))
        assertEquals(0 to 4, CodeBreaker.score("bgyr", "rygb"))
        assertEquals(1 to 0, CodeBreaker.score("rrrr", "rygb"))
        assertEquals(1 to 2, CodeBreaker.score("rryy", "yrrp"))
        assertTrue(CodeBreaker.isCode(CodeBreaker.randomCode()))
    }

    @Test
    fun nearbyRoundSetsThenCracks() {
        val room = LocalRoom("CB01", "Alice", null, GameKind.CODE)
        val guest = room.join("Bob", null, null, listOf("code"))
        room.setCodeReady(LocalRoom.HOST, true, 0)
        room.setCodeReady(guest, true, 0)
        room.codeMove(LocalRoom.HOST, "rrgg", 0)
        assertThrows(ApiException::class.java) { room.codeMove(LocalRoom.HOST, "bbbb", 0) }
        room.codeMove(guest, "pobb", 0)
        val go = LocalRoom.COUNTDOWN_MS
        room.codeMove(guest, "rgrg", go)
        val host = parseRoom(room.view(LocalRoom.HOST, go)).codeGame!!
        assertEquals("guess", host.phase)
        assertEquals(2, host.theirs[0].black)
        assertEquals(null, host.theirs[0].code)
        room.codeMove(LocalRoom.HOST, "pobb", go + 1)
        val end = parseRoom(room.view(guest, go + 2))
        assertEquals(RoomStatus.FINISHED, end.status)
        assertEquals("opponent", end.winner)
        assertEquals("rrgg", end.codeGame!!.theirCode)
    }
}
