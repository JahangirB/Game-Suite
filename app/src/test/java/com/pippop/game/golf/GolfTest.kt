package com.pippop.game.golf

import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.data.nearby.LocalRoom
import com.pippop.data.parseRoom
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
    fun nobodyIsPickedUpBeforeSomeoneIsInThenTheRestGetThreeMore() {
        val m = GolfMatch(listOf('h', 'g'))
        // Both keep missing (or falling back to the tee): the hole goes on, as the server's does.
        repeat(10) {
            m.play('h', shot(0.5, 0.5))
            m.play('g', shot(0.5, 0.5))
        }
        assertEquals(0, m.hole)
        assertTrue(GolfView.from(m.view('h', mapOf('g' to 'o'))).left.isEmpty())
        m.play('h', shot(0.5, 0.2, true))
        assertEquals(mapOf('m' to 3), GolfView.from(m.view('g', mapOf('h' to 'o'))).left)
        m.play('g', shot(0.5, 0.5))
        assertEquals(mapOf('m' to 2), GolfView.from(m.view('g', mapOf('h' to 'o'))).left)
        m.play('g', shot(0.5, 0.5))
        m.play('g', shot(0.5, 0.5))
        // Not in after those: picked up with one more, and on to the next hole.
        assertEquals(listOf(14, 0), m.strokes['g'])
        assertEquals(listOf(11, 0), m.strokes['h'])
        assertEquals(1, m.hole)
        assertTrue(GolfView.from(m.view('h', mapOf('g' to 'o'))).left.isEmpty())
    }

    @Test
    fun aHoleNobodyFinishesEndsAtTheSafetyNet() {
        val m = GolfMatch(listOf('h'))
        repeat(Golf.MAX_STROKES) { m.play('h', shot(0.5, 0.5)) }
        assertEquals(listOf(Golf.MAX_STROKES + 1, 0), m.strokes['h'])
    }

    @Test
    fun physicsIsRepeatableAndWallsTurnARollBack() {
        val hole = Golf.HOLES[0]
        val a = Golf.simulate(hole, Pt(0.4, 2.0), ShotKind.ROLL, -PI / 2, 0.8)
        val b = Golf.simulate(hole, Pt(0.4, 2.0), ShotKind.ROLL, -PI / 2, 0.8)
        assertEquals(a, b)
        // Straight up into the wall at y 1.6: it turns back, never through it.
        assertTrue(a.frames.none { it.x in 0.2..0.7 && it.y in 1.6..1.64 })
        assertTrue(a.end.y > 1.64)
        assertTrue(a.events.any { it.what == "wall" })
        // A high jump flies over the same wall.
        val over = Golf.simulate(hole, Pt(0.4, 1.9), ShotKind.JUMP, -PI / 2, 1.0)
        assertTrue(over.end.y < 1.6)
    }

    @Test
    fun aJumpClearsTheGapThatARollFallsInto() {
        val hole = Hole(
            4, Pt(0.5, 3.0), Pt(0.5, 0.3),
            islands = listOf(Box(0.0, 0.05, 1.0, 1.5), Box(0.0, 1.75, 1.0, 3.15)),
            bridges = listOf(Box(0.85, 1.48, 0.97, 1.77)),
        )
        val roll = Golf.simulate(hole, Pt(0.5, 1.9), ShotKind.ROLL, -PI / 2, 0.6)
        assertEquals(Hazard.DROP, roll.hazard)
        assertEquals(hole.tee, roll.rest(hole))
        assertTrue(roll.events.any { it.what == "drop" })
        val jump = Golf.simulate(hole, Pt(0.5, 1.9), ShotKind.JUMP, -PI / 2, 0.9)
        assertNull(jump.hazard)
        assertTrue(jump.end.y < 1.5)
        // Over the bridge a roll gets across.
        val bridge = Golf.simulate(hole, Pt(0.91, 1.9), ShotKind.ROLL, -PI / 2, 0.6)
        assertNull(bridge.hazard)
        assertTrue(bridge.end.y < 1.5)
    }

    @Test
    fun stickerSpotsDoWhatTheySay() {
        val hole = Golf.HOLES[2]
        // Rolling into the fire: back to the tee.
        val fire = Golf.simulate(hole, Pt(0.72, 2.095), ShotKind.ROLL, -PI / 2, 0.4)
        assertEquals(Hazard.FIRE, fire.hazard)
        // Onto Stonks: thrown up the course.
        val stonks = Golf.simulate(hole, Pt(0.21, 1.62), ShotKind.ROLL, -PI / 2, 0.35)
        assertTrue(stonks.events.any { it.what == "stonks" })
        assertTrue(stonks.frames.any { it.h > 0.5 })
        // Into a portal: out of the other.
        val portal = Golf.simulate(hole, Pt(0.15, 2.9), ShotKind.ROLL, PI / 2, 0.38)
        assertTrue(portal.events.any { it.what == "portal" })
        assertTrue(portal.end.y < 1.0)
        // Stopping on Kylian takes the next turn; on the eyes, the aim line.
        val kylian = Golf.simulate(hole, Pt(0.2, 1.0), ShotKind.ROLL, -PI / 2, 0.3)
        assertEquals(ZoneType.KYLIAN, kylian.restZone)
        assertTrue(kylian.skip)
        val eyes = Golf.simulate(hole, Pt(0.4, 0.4), ShotKind.ROLL, -PI / 2, 0.25)
        assertEquals(ZoneType.EYES, eyes.restZone)
        assertFalse(eyes.skip)
    }

    @Test
    fun kylianTakesTheNextTurnInAMatch() {
        val m = GolfMatch(listOf('h', 'g', '3'))
        assertEquals('g', m.play('h', shot(0.5, 2.0).put("skip", true).put("kind", "jump")).turn)
        assertEquals("jump", GolfView.from(m.view('g', mapOf('h' to 'o', '3' to 'p'))).last!!.kind.key)
        assertEquals('3', m.play('g', shot(0.5, 2.0)).turn)
        assertEquals('g', m.play('3', shot(0.5, 2.0)).turn)
        // Alone on the hole, the lost turn is a shot added.
        val solo = GolfMatch(listOf('h'))
        assertEquals('h', solo.play('h', shot(0.5, 2.0).put("skip", true)).turn)
        assertEquals(listOf(2), solo.strokes['h'])
    }

    @Test
    fun theComputerFinishesTheBuiltInHoles() {
        val random = Random(3)
        for (hole in Golf.HOLES) {
            val field = Golf.distanceField(hole)
            var at = hole.tee
            var strokes = 0
            var holed = false
            while (!holed && strokes < 12) {
                strokes++
                val plan = Golf.computerShot(hole, at, 0.9, field, random)
                val s = Golf.simulate(hole, at, plan.kind, plan.angle, plan.power)
                at = s.rest(hole)
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
        room.move(LocalRoom.HOST, shot(0.5, 2.6), 2)
        val bob = parseRoom(room.view(guest, 3)).turns!!
        assertEquals("me", bob.turn)
        assertEquals(listOf(1), bob.golf!!.strokes['o'])
        assertEquals(Pt(0.5, 2.6), bob.golf!!.balls['o'])
        assertEquals(RoomStatus.PLAYING, parseRoom(room.view(guest, 3)).status)
    }
}
