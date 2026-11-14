package com.pippop.data

import com.pippop.ui.screens.SoloLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the Solo tab says: units, levels in words, the "Also" line and the order of the lines. */
class SoloLinesTest {
    private fun rec(best: Long, at: Long = 1000, played: Int = 3, run: Int = 0) = SoloRecord(best, at, played, run)
    private fun key(g: GameKind, level: String, m: SoloMetric) = SoloKey(g, level, m)
    private fun line(g: GameKind, level: String, m: SoloMetric, best: Long, played: Int = 3) =
        SoloLines.of(mapOf(key(g, level, m) to rec(best, played = played))).single()

    @Test fun bestTextUnitsAndPlurals() {
        assertEquals("6:42", SoloLines.bestText(SoloMetric.TIME, 402_000))
        assertEquals("1:02:05", SoloLines.bestText(SoloMetric.TIME, 3_725_000))
        assertEquals("1 shot", SoloLines.bestText(SoloMetric.SHOTS, 1)); assertEquals("31 shots", SoloLines.bestText(SoloMetric.SHOTS, 31))
        assertEquals("1 disc", SoloLines.bestText(SoloMetric.DISCS, 1)); assertEquals("9 discs", SoloLines.bestText(SoloMetric.DISCS, 9))
        assertEquals("1 box ahead", SoloLines.bestText(SoloMetric.BOXES_AHEAD, 1)); assertEquals("5 boxes ahead", SoloLines.bestText(SoloMetric.BOXES_AHEAD, 5))
        assertEquals("1 try", SoloLines.bestText(SoloMetric.TRIES, 1)); assertEquals("3 tries", SoloLines.bestText(SoloMetric.TRIES, 3))
        assertEquals("1 guess", SoloLines.bestText(SoloMetric.GUESSES, 1)); assertEquals("4 guesses", SoloLines.bestText(SoloMetric.GUESSES, 4))
        assertEquals("1 goal ahead", SoloLines.bestText(SoloMetric.GOALS_AHEAD, 1)); assertEquals("3 goals ahead", SoloLines.bestText(SoloMetric.GOALS_AHEAD, 3))
        assertEquals("40 points ahead", SoloLines.bestText(SoloMetric.POINTS_AHEAD, 40))
        assertEquals("1 point", SoloLines.bestText(SoloMetric.POINTS, 1)); assertEquals("31 points", SoloLines.bestText(SoloMetric.POINTS, 31))
        assertEquals("7 letters", SoloLines.bestText(SoloMetric.LONGEST_WORD, 7))
        assertEquals("52 squares", SoloLines.bestText(SoloMetric.SQUARES, 52))
        assertEquals("52 flips", SoloLines.bestText(SoloMetric.FLIPS, 52)); assertEquals("12 balls", SoloLines.bestText(SoloMetric.BALLS, 12))
        assertEquals("1 win", SoloLines.bestText(SoloMetric.WINS_IN_ROW, 1)); assertEquals("5 in a row", SoloLines.bestText(SoloMetric.WINS_IN_ROW, 5))
        assertEquals("7 in a row", SoloLines.bestText(SoloMetric.SOLVED_IN_ROW, 7))
    }

    @Test fun levelsAreSaidInWords() {
        assertEquals("36 cards", line(GameKind.MEMORY, "36", SoloMetric.FLIPS, 52).detail)
        assertEquals("1 computer", line(GameKind.DOMINOES, "1", SoloMetric.POINTS_AHEAD, 40).detail)
        assertEquals("3 computers", line(GameKind.BINGO, "3", SoloMetric.BALLS, 12).detail)
        assertEquals("On your own", line(GameKind.BINGO, "0", SoloMetric.BALLS, 12).detail)
        assertEquals("4 pegs · 6 colours · repeats", line(GameKind.CODE, "4-6-repeats", SoloMetric.GUESSES, 4).detail)
        assertEquals("5 pegs · 7 colours", line(GameKind.CODE, "5-7-none", SoloMetric.GUESSES, 4).detail)
        assertEquals("Daily word", line(GameKind.WORDS, "daily", SoloMetric.TRIES, 3).detail)
        assertEquals("5 letters", line(GameKind.WORDS, "medium", SoloMetric.TRIES, 3).detail)
        assertEquals("7–8 letters", line(GameKind.WORDS, "expert", SoloMetric.TRIES, 3).detail)
        assertEquals("Hard", line(GameKind.GOLF, "hard", SoloMetric.STROKES, 29).detail)
        assertEquals("4 × 4", line(GameKind.SLIDE, "medium", SoloMetric.TIME, 84_000).detail)
        assertEquals("Heart board", line(GameKind.DOTS, "heart", SoloMetric.BOXES_AHEAD, 5).detail)
        assertEquals("Quickest win against Hard", line(GameKind.CONNECT4, "hard", SoloMetric.DISCS, 9).detail)
    }

    @Test fun sudokuSaysHowManyWereSolved() {
        assertEquals("Hard · best of 14 solved", line(GameKind.SUDOKU, "hard", SoloMetric.TIME, 402_000, played = 14).detail)
        // Other games don't say it.
        assertEquals("Medium", line(GameKind.NUMBERS, "medium", SoloMetric.TIME, 138_000, played = 14).detail)
    }

    @Test fun otherLevelsOfTheSameGameAreAlsoListed() {
        val l = SoloLines.of(mapOf(
            key(GameKind.SUDOKU, "easy", SoloMetric.TIME) to rec(245_000, at = 100),
            key(GameKind.SUDOKU, "medium", SoloMetric.TIME) to rec(500_000, at = 200),
            key(GameKind.SUDOKU, "hard", SoloMetric.TIME) to rec(930_000, at = 300),
        )).single()
        assertEquals("15:30", l.best)
        assertEquals("Easy: 4:05 · Medium: 8:20", l.others)
        assertNull(SoloLines.of(mapOf(key(GameKind.SUDOKU, "easy", SoloMetric.TIME) to rec(245_000))).single().others)
    }

    @Test fun aSecondBestJoinsTheDetailOfTheSameLevel() {
        val l = SoloLines.of(mapOf(
            key(GameKind.WORDS, "medium", SoloMetric.TRIES) to rec(3),
            key(GameKind.WORDS, "medium", SoloMetric.SOLVED_IN_ROW) to rec(9, run = 2),
        )).single()
        assertEquals("3 tries", l.best)
        assertEquals("5 letters · 9 solved in a row", l.detail)
        val g = SoloLines.of(mapOf(
            key(GameKind.GRID, "timed", SoloMetric.POINTS) to rec(31),
            key(GameKind.GRID, "timed", SoloMetric.LONGEST_WORD) to rec(7),
        )).single()
        assertEquals("Timed · Longest word: 7 letters", g.detail)
    }

    @Test fun lineWithoutABestIsLeftOutAndAMigratedRecordSortsLast() {
        val lines = SoloLines.of(mapOf(
            // Migrated from the old Sudoku bests: no date.
            key(GameKind.SUDOKU, "easy", SoloMetric.TIME) to rec(245_000, at = 0, played = 9),
            key(GameKind.SHIPS, "", SoloMetric.SHOTS) to rec(31, at = 500),
            key(GameKind.GOLF, "easy", SoloMetric.STROKES) to rec(33, at = 900),
            key(GameKind.LUDO, "1", SoloMetric.WINS_IN_ROW) to SoloRecord(null, 0, 2),
        ))
        assertEquals(listOf(GameKind.GOLF, GameKind.SHIPS, GameKind.SUDOKU), lines.map { it.game })
    }

    @Test fun theSpokenLineStatesTheNumbers() {
        assertEquals("Sudoku, best 6:42, Hard · best of 14 solved", line(GameKind.SUDOKU, "hard", SoloMetric.TIME, 402_000, played = 14).spoken)
    }
}
