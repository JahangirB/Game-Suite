package com.arcade.sudoku.game.slide

import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.data.parseRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlideTest {
    private val near = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8)

    @Test
    fun tappingSlidesEverythingBetweenTheTileAndTheGap() {
        // 1 2 3 / 4 5 6 / 7 0 8: tapping 7 slides 7; tapping 2 slides 5 then 2.
        assertEquals(listOf(7), Slide.slidesFor(near, 3, 7))
        assertEquals(listOf(5, 2), Slide.slidesFor(near, 3, 2))
        assertEquals(emptyList<Int>(), Slide.slidesFor(near, 3, 1))
        assertTrue(Slide.isSolved(Slide.replay(near, 3, listOf(8)), 3))
    }

    @Test
    fun aSwipeSlidesTheTileOnTheFarSideOfTheGap() {
        // 1 2 3 / 4 5 6 / 7 0 8: left brings 8 in, right brings 7, down brings 5; nothing is below.
        assertEquals(8, Slide.tileForSwipe(near, 3, -1, 0))
        assertEquals(7, Slide.tileForSwipe(near, 3, 1, 0))
        assertEquals(5, Slide.tileForSwipe(near, 3, 0, 1))
        assertEquals(null, Slide.tileForSwipe(near, 3, 0, -1))
    }

    @Test
    fun shufflesAreSolvableAndNotSolved() {
        for (size in 3..5) repeat(5) {
            val t = Slide.shuffle(size, Random(it))
            assertFalse(Slide.isSolved(t, size))
            assertEquals((0 until size * size).toList(), t.sorted())
        }
    }

    @Test
    fun nearbyRaceReplaysMoves() {
        val room = LocalRoom("SL01", "Alice", null, GameKind.SLIDE)
        val guest = room.join("Bob", null, null, listOf("slide"))
        room.setSlideReady(guest, true, null, 0)
        room.setSlideReady(LocalRoom.HOST, true, near, 0)
        val go = LocalRoom.COUNTDOWN_MS
        assertEquals(near, parseRoom(room.view(guest, go)).slide!!.start)
        room.slideMoves(LocalRoom.HOST, "8", go)
        val end = parseRoom(room.view(guest, go))
        assertEquals(RoomStatus.FINISHED, end.status)
        assertEquals("opponent", end.winner)
    }
}
