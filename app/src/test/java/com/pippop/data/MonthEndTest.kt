package com.pippop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

/** Which month's cup the app shows when it opens. */
class MonthEndTest {
    private val utc = ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).toInstant(utc).toEpochMilli()
    private fun game(at: Long, winner: String?, kind: GameKind = GameKind.WORDS, others: List<String> = listOf("Maya")) =
        MatchRecord(at, kind, others, winner)

    private val oct = YearMonth.of(2026, 10)

    /** [me] wins for you and [them] for Maya, all in [y]-[m]. */
    private fun month(y: Int, m: Int, me: Int, them: Int, ties: Int = 0): List<MatchRecord> =
        List(me) { game(at(y, m, 2 + it), MatchRecord.ME) } + List(them) { game(at(y, m, 12 + it), "Maya") } + List(ties) { game(at(y, m, 25 + it), null) }

    private fun show(records: List<MatchRecord>, now: Long, last: YearMonth? = null) = MonthEnd.toShow(records, now, last, utc)

    @Test fun nothingYet() {
        assertNull(show(emptyList(), at(2026, 11, 3)))
    }

    @Test fun onlyThisMonthsGamesIsNothingYet() {
        assertNull(show(month(2026, 11, 3, 1), at(2026, 11, 20)))
    }

    @Test fun gamesWithSomeoneElseAreNotThePartners() {
        // "Maya" is the partner (most games); the single game with Sam in September is not hers.
        val rs = month(2026, 11, 3, 1) + listOf(game(at(2026, 9, 5), MatchRecord.ME, others = listOf("Sam")))
        assertNull(show(rs, at(2026, 11, 20)))
    }

    @Test fun aNewMonthShowsItsCup() {
        val end = show(month(2026, 10, 15, 10), at(2026, 11, 1, 8))!!
        assertEquals(oct, end.month)
        assertEquals(15, end.mine)
        assertEquals(10, end.theirs)
        assertEquals(MatchRecord.ME, end.winner)
        assertEquals("Maya", end.partner)
        assertEquals(25, end.games)
    }

    @Test fun theSameMonthIsShownOnce() {
        val rs = month(2026, 10, 3, 1)
        assertNull(show(rs, at(2026, 11, 5), last = oct))
    }

    @Test fun theNextMonthShowsAgain() {
        val rs = month(2026, 10, 3, 1) + month(2026, 11, 1, 4)
        val end = show(rs, at(2026, 12, 2), last = oct)!!
        assertEquals(YearMonth.of(2026, 11), end.month)
        assertEquals("Maya", end.winner)
    }

    @Test fun aMonthWithoutGamesShowsNothing() {
        // October had games and was shown; November had none, so December's opening shows nothing.
        assertNull(show(month(2026, 10, 3, 1), at(2026, 12, 2), last = oct))
    }

    @Test fun oldHistoryShowsOnlyTheLatestMonth() {
        val rs = month(2026, 6, 2, 1) + month(2026, 7, 1, 2) + month(2026, 9, 4, 0)
        val end = show(rs, at(2026, 11, 14))!!
        assertEquals(YearMonth.of(2026, 9), end.month)
        // Once shown, the older ones never come.
        assertNull(show(rs, at(2026, 11, 14), last = end.month))
    }

    @Test fun decemberIsShownInJanuary() {
        val rs = month(2026, 12, 2, 5)
        val end = show(rs, at(2027, 1, 1, 0))!!
        assertEquals(YearMonth.of(2026, 12), end.month)
        assertEquals("Maya", end.winner)
        assertNull(show(rs, at(2027, 1, 20), last = YearMonth.of(2026, 12)))
        // And January shows in February, after December was shown.
        val both = rs + month(2027, 1, 3, 1)
        assertEquals(YearMonth.of(2027, 1), show(both, at(2027, 2, 1), last = YearMonth.of(2026, 12))!!.month)
    }

    @Test fun aTieHasNoWinner() {
        val end = show(month(2026, 10, 4, 4, ties = 2), at(2026, 11, 2))!!
        assertNull(end.winner)
        assertEquals(4, end.mine)
        assertEquals(4, end.theirs)
        assertEquals(10, end.games)
    }

    @Test fun theMonthIsTheOneInThePhonesZone() {
        // 23:00 on 31 October in UTC is 1 November in Auckland: there it belongs to the month still in play.
        val rs = listOf(game(at(2026, 10, 31, 23), MatchRecord.ME), game(at(2026, 9, 10), "Maya"))
        val auckland = java.time.ZoneId.of("Pacific/Auckland")
        assertEquals(YearMonth.of(2026, 10), MonthEnd.toShow(rs, at(2026, 11, 20), null, utc)!!.month)
        assertEquals(YearMonth.of(2026, 9), MonthEnd.toShow(rs, at(2026, 11, 20), null, auckland)!!.month)
    }

    @Test fun theGamePlayedMostIsNamed() {
        val rs = listOf(GameKind.WORDS, GameKind.WORDS, GameKind.CONNECT4).mapIndexed { i, g -> game(at(2026, 10, 3 + i), MatchRecord.ME, g) }
        val end = show(rs, at(2026, 11, 2))!!
        assertEquals(GameKind.WORDS, end.mostPlayed)
        assertEquals(3, end.games)
    }

    @Test fun noGameClearlyPlayedMostIsNotNamed() {
        val rs = listOf(GameKind.WORDS, GameKind.CONNECT4).mapIndexed { i, g -> game(at(2026, 10, 3 + i), MatchRecord.ME, g) }
        assertNull(show(rs, at(2026, 11, 2))!!.mostPlayed)
    }

    @Test fun aMonthSomeoneElseWonEveryGameOfShowsNothing() {
        val rs = List(3) { game(at(2026, 10, 3 + it), "Sam & Kit") }
        assertNull(show(rs, at(2026, 11, 2)))
    }

    @Test fun aRememberedMonthInTheFutureBlocksNothing() {
        val rs = month(2026, 10, 3, 1)
        assertEquals(oct, show(rs, at(2026, 11, 2), last = YearMonth.of(2027, 3))!!.month)
        assertEquals(oct, show(rs, at(2026, 11, 2), last = YearMonth.of(2026, 11))!!.month)
    }

    @Test fun sampleGamesCount() {
        val now = at(2026, 11, 12)
        val end = MonthEnd.latest(SampleGames.matches(now, utc), now, utc)!!
        assertEquals(YearMonth.of(2026, 10), end.month)
        assertEquals(SampleGames.PARTNER, end.partner)
    }
}
