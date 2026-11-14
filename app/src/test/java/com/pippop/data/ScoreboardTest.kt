package com.pippop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---- Step 20: the window, the split, the story and the cups -----------------------------

    private fun m(month: Int, day: Int, game: GameKind, winner: String?, ms: Long? = null, mine: Int? = null, theirs: Int? = null, count: Int? = null) =
        MatchRecord(at(month, day), game, listOf("Alex"), winner, durationMs = ms, myScore = mine, theirScore = theirs, winnerCount = count)

    @Test
    fun theMonthAndAllTimeAreTwoWindowsOverTheSameGames() {
        val s = Scoreboard.of(records, at(11, 10), utc)
        // November: three of yours and one tie; all time adds September (Alex x2) and October.
        assertEquals(3, s.thisMonth.mine)
        assertEquals(0, s.thisMonth.theirs)
        assertEquals(1, s.thisMonth.ties)
        assertEquals(5, s.allTime.mine)
        assertEquals(3, s.allTime.theirs)
        assertEquals(1, s.allTime.ties)
        val ludoAll = s.allTime.games.first { it.game == GameKind.LUDO }
        assertEquals(listOf(1, 2, 1), listOf(ludoAll.mine, ludoAll.theirs, ludoAll.ties))
        val ludoMonth = s.thisMonth.games.first { it.game == GameKind.LUDO }
        assertEquals(listOf(1, 0, 1), listOf(ludoMonth.mine, ludoMonth.theirs, ludoMonth.ties))
        // Golf was played in October only: a game of all time but not of this month.
        assertTrue(s.allTime.games.any { it.game == GameKind.GOLF })
        assertFalse(s.thisMonth.games.any { it.game == GameKind.GOLF })
    }

    @Test
    fun theLastFiveAreOldestFirstAndInsideTheWindow() {
        val rs = listOf(
            r(10, 20, GameKind.LUDO, "Alex"), r(10, 21, GameKind.LUDO, "Alex"),
            r(11, 1, GameKind.LUDO, "You"), r(11, 2, GameKind.GOLF, "Alex"), r(11, 3, GameKind.GOLF, null),
            r(11, 4, GameKind.SHIPS, "You"), r(11, 5, GameKind.SHIPS, "You"), r(11, 6, GameKind.DOTS, "Alex"),
        )
        val s = Scoreboard.of(rs, at(11, 10), utc)
        assertEquals(listOf(false, null, true, true, false), s.form)
        assertEquals(s.form, s.thisMonth.form)
        // All time looks past the month's start: still five, ending at the same game.
        assertEquals(listOf(false, null, true, true, false), s.allTime.form)
        val fewer = Scoreboard.of(rs.take(3), at(11, 10), utc)
        assertEquals(listOf(true), fewer.form)
        assertEquals(listOf(false, false, true), fewer.allTime.form)
    }

    @Test
    fun someoneElsesWinIsNotADotOfEither() {
        val rs = listOf(m(11, 1, GameKind.DOMINOES, "Sam").copy(others = listOf("Alex", "Sam")), m(11, 2, GameKind.LUDO, "You"))
        assertEquals(listOf(true), Scoreboard.form(rs, "Alex"))
    }

    @Test
    fun theSplitIsEachSidesWinsAndTiesAreLeftOut() {
        val g = Scoreboard.of(records, at(11, 10), utc).allTime.games.first { it.game == GameKind.LUDO }
        assertEquals(1, g.mine); assertEquals(2, g.theirs); assertEquals(1, g.ties)
        // Nothing in a window: no bar, no games.
        val none = Scoreboard.of(records, at(12, 10), utc)
        assertEquals(0, none.thisMonth.mine + none.thisMonth.theirs)
        assertTrue(none.thisMonth.games.isEmpty())
    }

    @Test
    fun longestRunIsPerSideAndATieEndsIt() {
        val rs = listOf(
            m(11, 1, GameKind.LUDO, "You"), m(11, 2, GameKind.LUDO, "You"), m(11, 3, GameKind.LUDO, null), m(11, 4, GameKind.LUDO, "You"),
            m(11, 5, GameKind.LUDO, "Alex"), m(11, 6, GameKind.LUDO, "Alex"), m(11, 7, GameKind.LUDO, "Alex"), m(11, 8, GameKind.LUDO, "You"),
        )
        val story = Scoreboard.story(rs, GameKind.LUDO, "Alex")
        assertEquals(2, story.mine.longestRun)
        assertEquals(3, story.theirs.longestRun)
        assertEquals(4, story.mine.wins)
        assertEquals(3, story.theirs.wins)
        assertEquals(1, story.ties)
        // The story only reads this game and this partner.
        assertEquals(0, Scoreboard.story(rs, GameKind.GOLF, "Alex").mine.wins)
    }

    @Test
    fun quickestWinIsTheShortestTimeInGamesRaceAgainstTheClock() {
        val rs = listOf(
            m(11, 1, GameKind.SUDOKU, "You", ms = 500_000), m(11, 2, GameKind.SUDOKU, "You", ms = 420_000),
            m(11, 3, GameKind.SUDOKU, "Alex", ms = 380_000), m(11, 4, GameKind.SUDOKU, "Alex", ms = 610_000),
            m(11, 5, GameKind.SUDOKU, null, ms = 100_000),
        )
        val q = Scoreboard.story(rs, GameKind.SUDOKU, "Alex")
        assertEquals(420_000L, q.mine.quickestMs)
        assertEquals(380_000L, q.theirs.quickestMs)
        // A tie is nobody's quickest win, however fast it was.
        assertNull(q.mine.quickestCount)
    }

    @Test
    fun quickestWinIsTheFewestWhereFewestIsWhatCounts() {
        val rs = listOf(
            m(11, 1, GameKind.CONNECT4, "You", ms = 90_000, count = 12), m(11, 2, GameKind.CONNECT4, "You", ms = 60_000, count = 9),
            m(11, 3, GameKind.CONNECT4, "Alex", ms = 200_000, count = 14), m(11, 4, GameKind.CONNECT4, "Alex", ms = 70_000),
        )
        val q = Scoreboard.story(rs, GameKind.CONNECT4, "Alex")
        // The count is the answer even though a time was kept; a win with no count says nothing for them.
        assertEquals(9, q.mine.quickestCount); assertNull(q.mine.quickestMs)
        assertEquals(14, q.theirs.quickestCount); assertNull(q.theirs.quickestMs)
        assertTrue(GameKind.SHIPS in Scoreboard.FEWEST && GameKind.WORDS in Scoreboard.FEWEST && GameKind.CODE in Scoreboard.FEWEST)
    }

    @Test
    fun biggestWinIsTheWidestMarginAndTheWinnersScoreComesFirst() {
        val rs = listOf(
            m(11, 1, GameKind.DOTS, "You", mine = 10, theirs = 6), m(11, 2, GameKind.DOTS, "You", mine = 13, theirs = 3),
            m(11, 3, GameKind.DOTS, "Alex", mine = 5, theirs = 11), m(11, 4, GameKind.DOTS, null, mine = 8, theirs = 8),
            // Lower wins in golf: the margin is still the gap.
            m(11, 5, GameKind.GOLF, "You", mine = 31, theirs = 40), m(11, 6, GameKind.GOLF, "Alex", mine = 38, theirs = 33),
        )
        val dots = Scoreboard.story(rs, GameKind.DOTS, "Alex")
        assertEquals(BigWin(13, 3), dots.mine.biggestWin)
        assertEquals(BigWin(11, 5), dots.theirs.biggestWin)
        assertEquals(10, dots.mine.biggestWin!!.margin)
        val golf = Scoreboard.story(rs, GameKind.GOLF, "Alex")
        assertEquals(BigWin(31, 40), golf.mine.biggestWin)
        assertEquals(BigWin(33, 38), golf.theirs.biggestWin)
    }

    @Test
    fun olderResultsCountForWinsOnlyAndTheStoryLeavesLinesOut() {
        val rs = listOf(m(10, 1, GameKind.DOTS, "You"), m(10, 2, GameKind.DOTS, "Alex"), m(10, 3, GameKind.DOTS, "You"))
        val s = Scoreboard.story(rs, GameKind.DOTS, "Alex")
        assertEquals(2, s.mine.wins); assertEquals(1, s.theirs.wins)
        assertEquals(1, s.mine.longestRun)
        assertNull(s.mine.quickestMs); assertNull(s.mine.quickestCount); assertNull(s.mine.biggestWin)
        assertNull(s.theirs.quickestMs); assertNull(s.theirs.biggestWin)
        assertTrue(s.missingFacts)
        // A mix: one result with facts, one without.
        val mix = Scoreboard.story(rs + m(10, 4, GameKind.DOTS, "You", ms = 300_000, mine = 9, theirs = 4), GameKind.DOTS, "Alex")
        assertEquals(300_000L, mix.mine.quickestMs)
        assertEquals(BigWin(9, 4), mix.mine.biggestWin)
        assertTrue(mix.missingFacts)
        assertFalse(Scoreboard.story(listOf(m(10, 4, GameKind.DOTS, "You", ms = 1, mine = 9, theirs = 4)), GameKind.DOTS, "Alex").missingFacts)
    }

    @Test
    fun theLastTenAreNewestFirst() {
        val rs = (1..12).map { m(10, it, GameKind.LUDO, if (it % 2 == 0) "You" else "Alex") }
        val s = Scoreboard.story(rs, GameKind.LUDO, "Alex")
        assertEquals(10, s.last.size)
        assertEquals(at(10, 12), s.last.first().at)
        assertEquals(at(10, 3), s.last.last().at)
    }

    @Test
    fun aTieOnlyGameHasAnEmptySplitAndNoRecords() {
        val s = Scoreboard.story(listOf(m(11, 1, GameKind.TTT, null), m(11, 2, GameKind.TTT, null)), GameKind.TTT, "Alex")
        assertEquals(0, s.mine.wins + s.theirs.wins)
        assertEquals(2, s.ties)
        assertEquals(0, s.mine.longestRun + s.theirs.longestRun)
    }

    @Test
    fun cupsGoToEachMonthsWinnerAndATieIsNobodys() {
        val rs = listOf(
            // August: 2 to 1 to you. September: Alex takes it. October: level. November: in play.
            r(8, 1, GameKind.LUDO, "You"), r(8, 2, GameKind.LUDO, "You"), r(8, 3, GameKind.GOLF, "Alex"),
            r(9, 1, GameKind.LUDO, "Alex"), r(9, 2, GameKind.LUDO, "Alex"), r(9, 3, GameKind.GOLF, "You"),
            r(10, 1, GameKind.LUDO, "You"), r(10, 2, GameKind.LUDO, "Alex"), r(10, 3, GameKind.LUDO, null),
            r(11, 1, GameKind.LUDO, "You"),
        )
        val s = Scoreboard.of(rs, at(11, 10), utc)
        assertEquals(listOf(8, 9, 10), s.trophies.map { it.month.monthValue })
        assertEquals(listOf("You", "Alex", null), s.trophies.map { it.winner })
        assertEquals(listOf(2 to 1, 1 to 2, 1 to 1), s.trophies.map { it.mine to it.theirs })
    }

    @Test
    fun theShelfHasTheLatestCupsThisMonthAndEmptyPlacesForTheRestOfTheYear() {
        val rs = listOf(r(8, 1, GameKind.LUDO, "You"), r(9, 1, GameKind.LUDO, "Alex"), r(10, 1, GameKind.LUDO, null), r(11, 1, GameKind.LUDO, "You"))
        val nov = Scoreboard.of(rs, at(11, 10), utc).shelf
        assertEquals(listOf(8, 9, 10, 11, 12), nov.map { it.month.monthValue })
        assertEquals(listOf(true, true, true, false, false), nov.map { it.trophy != null })
        assertEquals(listOf(false, false, false, true, false), nov.map { it.current })
        // In December nothing is left to come: four cups, then the month in play.
        val dec = Scoreboard.of(rs, at(12, 10), utc).shelf
        assertEquals(listOf(8, 9, 10, 11, 12), dec.map { it.month.monthValue })
        assertTrue(dec.last().current && dec.dropLast(1).all { it.trophy != null })
        // In January there are no cups yet: this month and two places to come.
        val jan = Scoreboard.shelf(emptyList(), YearMonth.of(2027, 1))
        assertEquals(listOf(1, 2, 3), jan.map { it.month.monthValue })
        assertTrue(jan[0].current && jan.drop(1).all { it.trophy == null && !it.current })
    }

    @Test
    fun aMonthWithNoGamesGetsNoCup() {
        val s = Scoreboard.of(listOf(r(8, 1, GameKind.LUDO, "You"), r(10, 1, GameKind.LUDO, "Alex")), at(11, 10), utc)
        assertEquals(listOf(8, 10), s.trophies.map { it.month.monthValue })
    }

    @Test
    fun mutedTextReadsOnTheBackgroundItSitsOn() {
        // The shelf's months and scores are muted text straight on the screen's background.
        for (p in listOf(com.pippop.ui.theme.LightPalette, com.pippop.ui.theme.DarkPalette)) {
            assertTrue("muted on background in ${if (p.dark) "dark" else "light"}", com.pippop.ui.theme.contrast(p.muted, p.background) >= 4.5f)
            assertTrue(com.pippop.ui.theme.contrast(p.text, p.background) >= 4.5f)
        }
    }

    @Test
    fun aGameAtMidnightOnTheFirstBelongsToThatMonthInTheLocalZone() {
        val nz = java.time.ZoneId.of("Pacific/Auckland")
        // Nov 1st 00:00 in Auckland is still Oct 31st in UTC.
        val first = java.time.ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, nz).toInstant().toEpochMilli()
        val justBefore = first - 1
        val rs = listOf(
            MatchRecord(justBefore, GameKind.LUDO, listOf("Alex"), "Alex"),
            MatchRecord(first, GameKind.LUDO, listOf("Alex"), "You"),
        )
        val now = first + 3_600_000
        val local = Scoreboard.of(rs, now, nz)
        assertEquals(YearMonth.of(2026, 11), local.month)
        assertEquals(1, local.mine); assertEquals(0, local.theirs)
        assertEquals(listOf(YearMonth.of(2026, 10)), local.trophies.map { it.month })
        // The same instants read in UTC put both games in October.
        val utcBoard = Scoreboard.of(rs, now, utc)
        assertEquals(YearMonth.of(2026, 10), utcBoard.month)
        assertEquals(1, utcBoard.mine); assertEquals(1, utcBoard.theirs)
    }
}
