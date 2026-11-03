package com.pippop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class ScoreboardTest {
    private val utc = ZoneOffset.UTC
    private fun at(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 20, 0).toInstant(utc).toEpochMilli()
    private fun r(month: Int, day: Int, game: GameKind, winner: String?, others: List<String> = listOf("Alex")) = MatchRecord(at(month, day), game, others, winner)

    private val records = listOf(
        r(9, 3, GameKind.LUDO, "Alex"),
        r(9, 4, GameKind.LUDO, "Alex"),
        r(10, 1, GameKind.GOLF, "You"),
        r(10, 2, GameKind.GOLF, "You"),
        r(10, 2, GameKind.SUDOKU, "Alex"),
        r(11, 1, GameKind.LUDO, "You"),
        r(11, 2, GameKind.LUDO, null),
        r(11, 2, GameKind.SHIPS, "You"),
        r(11, 3, GameKind.SHIPS, "You"),
        // Someone else once: not part of the scoreboard with Alex.
        r(11, 3, GameKind.SHIPS, "Sam", listOf("Sam")),
    )

    @Test
    fun theMonthCountsWinsWithThePartner() {
        val s = Scoreboard.of(records, at(11, 10), utc)
        assertEquals("Alex", s.partner)
        assertEquals(YearMonth.of(2026, 11), s.month)
        assertEquals(20, s.daysLeft)
        assertEquals(3, s.mine)
        assertEquals(0, s.theirs)
        val ludo = s.games.first { it.game == GameKind.LUDO }
        assertEquals(1, ludo.mine)
        assertEquals(1, ludo.ties)
        assertEquals(4, ludo.last.size)
        assertEquals(GameKind.SHIPS, s.games.first().game)
    }

    @Test
    fun streaksAndTrophies() {
        val s = Scoreboard.of(records, at(11, 10), utc)
        // A tie on Nov 2 ends the run before it; two Ship Battle wins since.
        assertEquals(2, s.streak)
        assertEquals(2, s.bestStreak)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), s.trophies.map { it.month })
        assertEquals("Alex", s.trophies[0].winner)
        assertEquals("You", s.trophies[1].winner)
    }

    @Test
    fun theirStreakIsNegativeAndATeamWinCounts() {
        val rs = listOf(r(11, 1, GameKind.DOMINOES, "Alex & Sam", listOf("Alex", "Sam", "Kim")), r(11, 2, GameKind.LUDO, "Alex"))
        assertEquals(-2, Scoreboard.currentStreak(rs, "Alex"))
        assertNull(Scoreboard.of(emptyList(), at(11, 1), utc).partner)
    }
}
