package com.pippop.ui.screens

import com.pippop.data.GameKind
import com.pippop.data.RecentGame
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    private val zone = ZoneId.of("Europe/London")
    // Friday 13 November 2026, 09:30 in London.
    private val now = ZonedDateTime.of(2026, 11, 13, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
    private fun daysAgo(n: Long, hour: Int = 12) =
        ZonedDateTime.of(2026, 11, 13, hour, 0, 0, 0, zone).minusDays(n).toInstant().toEpochMilli()

    @Test fun tagsCountCalendarDaysInTheDeviceZone() {
        assertEquals("Today", recentTag(daysAgo(0, 0), now, zone))
        // Yesterday evening is "Yesterday" even though it was fewer than 24 hours ago.
        assertEquals("Yesterday", recentTag(daysAgo(1, 23), now, zone))
        assertEquals("Wednesday", recentTag(daysAgo(2), now, zone))
        assertEquals("Sunday", recentTag(daysAgo(5), now, zone))
        assertEquals("Saturday", recentTag(daysAgo(6), now, zone))
        assertEquals("6 Nov", recentTag(daysAgo(7), now, zone))
        assertEquals("13 Jan", recentTag(ZonedDateTime.of(2026, 1, 13, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), now, zone))
        assertEquals("30 Dec 2025", recentTag(ZonedDateTime.of(2025, 12, 30, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), now, zone))
    }

    @Test fun theZoneDecidesWhereMidnightFalls() {
        // 23:30 UTC on the 12th is already the 13th in Tokyo, but yesterday in London.
        val at = ZonedDateTime.of(2026, 11, 12, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("Yesterday", recentTag(at, now, zone))
        assertEquals("Today", recentTag(at, now, ZoneId.of("Asia/Tokyo")))
    }

    @Test fun aTimeInTheFutureIsToday() = assertEquals("Today", recentTag(now + 3_600_000, now, zone))

    @Test fun jumpBackInPutsTheSudokuFirstThenNewestFirst() {
        val recent = listOf(
            RecentGame(GameKind.WORDS, daysAgo(4)), RecentGame(GameKind.LUDO, daysAgo(1)), RecentGame(GameKind.SHIPS, daysAgo(8)),
        )
        assertEquals(
            listOf(JumpTile(GameKind.SUDOKU, "27% done"), JumpTile(GameKind.LUDO, "Yesterday"), JumpTile(GameKind.WORDS, "Monday")),
            jumpBackIn(27, recent, now, zone),
        )
    }

    @Test fun jumpBackInNeverShowsAGameTwice() {
        val recent = listOf(RecentGame(GameKind.SUDOKU, daysAgo(0)), RecentGame(GameKind.LUDO, daysAgo(1)))
        assertEquals(
            listOf(GameKind.SUDOKU to "27% done", GameKind.LUDO to "Yesterday"),
            jumpBackIn(27, recent, now, zone).map { it.game to it.tag },
        )
        // With no Sudoku in progress, a recent Sudoku is an ordinary recent game.
        assertEquals(listOf(JumpTile(GameKind.SUDOKU, "Today"), JumpTile(GameKind.LUDO, "Yesterday")), jumpBackIn(null, recent, now, zone))
    }

    @Test fun jumpBackInHoldsThreeAndCanBeEmpty() {
        val six = GameKind.entries.take(6).mapIndexed { i, g -> RecentGame(g, daysAgo(i.toLong())) }
        assertEquals(3, jumpBackIn(10, six, now, zone).size)
        assertTrue(jumpBackIn(null, emptyList(), now, zone).isEmpty())
    }

    @Test fun surpriseWithNoRoomNeedsSoloPlay() {
        val shelf = listOf(GameKind.DRAW, GameKind.LUDO, GameKind.TTT)
        assertEquals(listOf(GameKind.LUDO, GameKind.TTT), surpriseCandidates(shelf, 1))
        assertEquals(GameKind.TTT, surprisePick(listOf(GameKind.DRAW, GameKind.TTT), 1, Random(1)))
    }

    @Test fun surpriseInARoomFitsEveryone() {
        val shelf = listOf(GameKind.DRAW, GameKind.HOCKEY, GameKind.LUDO, GameKind.TTT)
        assertEquals(listOf(GameKind.DRAW, GameKind.HOCKEY, GameKind.LUDO, GameKind.TTT), surpriseCandidates(shelf, 2))
        assertEquals(listOf(GameKind.DRAW, GameKind.LUDO), surpriseCandidates(shelf, 3))
        assertEquals(listOf(GameKind.DRAW, GameKind.LUDO), surpriseCandidates(shelf, 4))
        repeat(20) { assertTrue(surprisePick(shelf, 3, Random(it)) in listOf(GameKind.DRAW, GameKind.LUDO)) }
    }

    @Test fun surpriseOnlyPicksFromTheGivenShelfAndNullWhenNothingFits() {
        assertNull(surprisePick(emptyList(), 1))
        assertNull(surprisePick(listOf(GameKind.TTT, GameKind.HOCKEY), 3))
        assertNull(surprisePick(listOf(GameKind.DRAW), 1))
    }
}
