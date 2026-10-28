package com.pippop.game.ttt

import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.data.nearby.LocalRoom
import com.pippop.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ClassicTttTest {
    private fun cell(c: Int) = JSONObject().put("cell", c)

    @Test
    fun threeInARowWinsAndAFullBoardIsADraw() {
        val m = TttMatch(classic = true)
        m.play('h', cell(0))
        m.play('g', cell(3))
        m.play('h', cell(1))
        assertThrows(IllegalArgumentException::class.java) { m.play('g', cell(1)) }
        assertThrows(IllegalArgumentException::class.java) { m.play('g', cell(9)) }
        m.play('g', cell(4))
        val r = m.play('h', cell(2))
        assertTrue(r.over)
        assertEquals('h', r.winner)
        assertEquals(listOf(0, 1, 2), m.winLine)

        val d = TttMatch(classic = true)
        var last = d.play('h', cell(0))
        for ((c, mark) in listOf(1 to 'g', 2 to 'h', 4 to 'g', 3 to 'h', 5 to 'g', 7 to 'h', 6 to 'g', 8 to 'h')) last = d.play(mark, cell(c))
        assertTrue(last.over)
        assertNull(last.winner)
    }

    @Test
    fun theComputerTakesAWinAndBlocks() {
        val computer = TttComputer(Random(1))
        // X X . / O O . / . . .  — the computer (O) wins on 5 rather than blocking 2.
        assertEquals(5, computer.move(TttPosition("mm.oo....", ".", -1), 'o', 'm'))
        // X X . / O . . / . . .  — it must block on 2.
        assertEquals(2, computer.move(TttPosition("mm.o.....", ".", -1), 'o', 'm'))
    }

    @Test
    fun theHostCanPickNormalForANearbyRoom() {
        val room = LocalRoom("TT01", "Alice", null, GameKind.TTT)
        val guest = room.join("Bob", null, null, listOf("ttt"))
        room.setOptions(LocalRoom.HOST, JSONObject().put("mode", "classic"))
        assertTrue(parseRoom(room.view(guest, 0)).tttClassic)
        room.setTurnReady(LocalRoom.HOST, true, 0, Random(2))
        room.setTurnReady(guest, true, 0, Random(2))
        val view = parseRoom(room.view(guest, 1))
        assertEquals(RoomStatus.PLAYING, view.status)
        assertEquals(9, view.turns!!.board!!.length)
    }
}
