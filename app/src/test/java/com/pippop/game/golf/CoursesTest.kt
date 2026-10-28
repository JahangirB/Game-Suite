package com.pippop.game.golf

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

class CoursesTest {
    @Test
    fun everyHoleInTheLibraryIsBuiltTheSameAndCanBePlayed() {
        for (id in 0 until Courses.SIZE) {
            val hole = Courses.hole(id)
            assertTrue("hole $id can't be played", Courses.playable(hole))
            assertEquals(Courses.islands(id), hole.islands?.size ?: 1)
            assertEquals(Courses.islands(id) - 1, hole.bridges.size)
            assertEquals(Courses.islands(id) + 1, hole.par)
        }
        // Built from the number alone: the same on every phone.
        assertEquals(Courses.hole(512), Courses.hole(512 + Courses.SIZE))
    }

    @Test
    fun aGameIsThreeEasyThreeMediumThreeHard() {
        repeat(50) { seed ->
            val course = Courses.pick(Random(seed))
            assertEquals(9, course.toSet().size)
            assertEquals(listOf(1, 1, 1, 2, 2, 2, 3, 3, 3), course.map { Courses.islands(it) })
        }
        val match = GolfMatch(listOf('h', 'g'), listOf(1, 2, 3, 400, 401, 402, 700, 701, 702))
        val view = GolfView.from(match.view('h', mapOf('g' to 'o')))
        assertEquals(match.course, view.course)
        assertEquals(Courses.hole(400), view.holeAt(3))
    }

    @Test
    fun anOldServerWithoutACoursePlaysTheClassicNine() {
        val turns = JSONObject().put("golf", JSONObject().put("hole", 2).put("holes", 9))
        val view = GolfView.from(turns)
        assertNull(view.course)
        assertEquals(Golf.HOLES[2], view.holeAt())
    }

    @Test
    fun aBallOverTheEdgeFallsAndGoesBackToTheTee() {
        val hole = Hole(
            3, Pt(0.5, 1.4), Pt(0.5, 0.2),
            islands = listOf(Box(0.0, 0.05, 1.0, 0.6), Box(0.0, 0.9, 1.0, 1.55)),
            bridges = listOf(Box(0.45, 0.58, 0.55, 0.92)),
        )
        // Straight up the bridge: still on the course past the gap.
        val across = Golf.simulate(hole, hole.tee, -PI / 2, 0.75)
        assertNull(across.hazard)
        // Up the left side, where there's no bridge: into the drop.
        val off = Golf.simulate(hole, Pt(0.15, 1.4), -PI / 2, 0.75)
        assertEquals(Hazard.DROP, off.hazard)
        assertTrue(off.end.y in 0.6..0.9)
        assertEquals(hole.tee, off.rest(hole))
    }

    @Test
    fun aBallThatSlowsOnAPondSinksButAFastOneRollsAcross() {
        val hole = Hole(2, Pt(0.5, 1.4), Pt(0.5, 0.2), water = listOf(Pond(0.5, 1.0, 0.2, 0.12)))
        val sinks = Golf.simulate(hole, hole.tee, -PI / 2, 0.42)
        assertEquals(Hazard.WATER, sinks.hazard)
        assertEquals(hole.tee, sinks.rest(hole))
        val across = Golf.simulate(hole, hole.tee, -PI / 2, 0.8)
        assertNull(across.hazard)
        assertTrue(across.end.y < 0.85)
    }

    @Test
    fun theComputerGetsRoundTheLibrary() {
        // A sample of every kind of hole, at the computer's best: it gets the ball in.
        for (id in listOf(5, 150, 300, 340, 500, 650, 670, 820, 990)) {
            val hole = Courses.hole(id)
            val field = Golf.distanceField(hole)
            var at = hole.tee
            var holed = false
            for (stroke in 1..Golf.MAX_STROKES) {
                val (angle, power) = Golf.computerShot(hole, at, 1.0, field, Random(id + stroke))
                val shot = Golf.simulate(hole, at, angle, power)
                if (shot.holed) {
                    holed = true
                    break
                }
                at = shot.rest(hole)
            }
            assertTrue("the computer couldn't finish hole $id", holed)
        }
    }
}
