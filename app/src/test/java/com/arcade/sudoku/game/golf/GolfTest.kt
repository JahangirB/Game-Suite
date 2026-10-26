package com.arcade.sudoku.game.golf

import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

class GolfTest {
    private fun shot(x: Double, y: Double, holed: Boolean = false) =
        JSONObject().put("angle", 1.0).put("power", 0.5).put("x", x).put("y", y).put("holed", holed)

    @Test
    fun turnsAndStrokesMatchTheServer() {
        val m = GolfMatch(listOf('h', 'g', '3'))
        assertEquals('g', m.play('h', shot(0.5, 0.5)).turn)
        assertEquals('3', m.play('g', shot(0.5, 0.2, true)).turn)
        assertEquals('h', m.play('3', shot(0.5, 0.4)).turn)
        assertThrows(IllegalArgumentException::class.java) { m.play('g', shot(0.5, 0.2)) }
        assertEquals('3', m.play('h', shot(0.5, 0.2, true)).turn)
        // The last one in starts hole 2, a seat on.
        assertEquals('g', m.play('3', shot(0.5, 0.2, true)).turn)
        assertEquals(1, m.hole)
        val v = GolfView.from(m.view('g', mapOf('h' to 'o', '3' to 'p')))
        assertEquals(listOf(1, 0), v.strokes['m'])
        assertEquals(listOf(2, 0), v.strokes['o'])
        assertNull(v.balls['m'])
        assertEquals('p', v.last!!.who)
        assertTrue(v.last!!.holed)
        assertThrows(IllegalArgumentException::class.java) { m.play('g', JSONObject().put("angle", 1.0).put("power", 0.0).put("x", 0.5).put("y", 0.5)) }
    }

    @Test
    fun aBallOutAfterSixIsPickedUpWithSeven() {
        val m = GolfMatch(listOf('h', 'g'))
        m.play('h', shot(0.5, 0.2, true))
        repeat(6) { m.play('g', shot(0.5, 0.5)) }
        assertEquals(listOf(7, 0), m.strokes['g'])
        assertEquals(1, m.hole)
    }

    @Test
    fun physicsIsRepeatableAndStaysOnTheCourse() {
        val hole = Golf.HOLES[1]
        val a = Golf.simulate(hole, hole.tee, -PI / 2, 0.8)
        val b = Golf.simulate(hole, hole.tee, -PI / 2, 0.8)
        assertEquals(a, b)
        // Straight up into the block: it bounces back, never through it.
        assertFalse(a.holed)
        assertTrue(a.path.none { it.x in 0.3..0.7 && it.y in 0.72..0.84 })
        for (k in 0 until 40) {
            val s = Golf.simulate(Golf.HOLES[k % 9], Golf.HOLES[k % 9].tee, k * 0.7, 1.0)
            assertTrue(s.path.all { it.x in 0.0..Golf.W && it.y in 0.0..Golf.H })
        }
        // A gentle straight putt on the first hole drops.
        val first = Golf.HOLES[0]
        assertTrue((1..40).any { Golf.simulate(first, first.tee, -PI / 2, it / 40.0).holed })
    }

    @Test
    fun theComputerFinishesEveryHole() {
        val random = Random(3)
        for (hole in Golf.HOLES) {
            val field = Golf.distanceField(hole)
            var at = hole.tee
            var strokes = 0
            var holed = false
            while (!holed && strokes < 7) {
                strokes++
                val (angle, power) = Golf.computerShot(hole, at, 0.9, field, random)
                val s = Golf.simulate(hole, at, angle, power)
                at = s.end
                holed = s.holed
            }
            assertTrue("hole par ${hole.par} took $strokes", holed)
        }
    }

    @Test
    fun nearbyRoundPlaysLikeTheServer() {
        val room = LocalRoom("GF01", "Alice", null, GameKind.GOLF)
        val guest = room.join("Bob", null, null, listOf("golf"))
        room.setTurnReady(LocalRoom.HOST, true, 0)
        room.setTurnReady(guest, true, 0)
        val host = parseRoom(room.view(LocalRoom.HOST, 1))
        assertEquals("me", host.turns!!.turn)
        room.move(LocalRoom.HOST, shot(0.5, 0.6), 2)
        val bob = parseRoom(room.view(guest, 3)).turns!!
        assertEquals("me", bob.turn)
        assertEquals(listOf(1), bob.golf!!.strokes['o'])
        assertEquals(Pt(0.5, 0.6), bob.golf!!.balls['o'])
        assertEquals(RoomStatus.PLAYING, parseRoom(room.view(guest, 3)).status)
    }
}
