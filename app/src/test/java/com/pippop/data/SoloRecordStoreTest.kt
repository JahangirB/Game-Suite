package com.pippop.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The solo best of each game: what is best (PLAN table 9.3), when, and how many were played. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SoloRecordStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun file(): File = File(tmp.root, "solo_records.json")
    private fun store() = SoloRecordStore(file())
    private fun key(g: GameKind, level: String, m: SoloMetric) = SoloKey(g, level, m)

    @Test fun lowerIsBetterKeepsTheSmallestAndWhen() {
        val s = store()
        s.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 400_000, at = 10)
        s.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 500_000, at = 20)
        s.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 350_000, at = 30)
        val r = s.get(key(GameKind.SUDOKU, "easy", SoloMetric.TIME))!!
        assertEquals(350_000L, r.best); assertEquals(30L, r.at); assertEquals(3, r.played)
    }

    @Test fun fewestIsBestForShotsDiscsFlipsTriesGuessesBallsStrokes() {
        val s = store()
        val cases = listOf(
            Triple(GameKind.SHIPS, "", SoloMetric.SHOTS), Triple(GameKind.CONNECT4, "hard", SoloMetric.DISCS), Triple(GameKind.MEMORY, "16", SoloMetric.FLIPS),
            Triple(GameKind.WORDS, "daily", SoloMetric.TRIES), Triple(GameKind.CODE, "4-6-repeats", SoloMetric.GUESSES), Triple(GameKind.BINGO, "1", SoloMetric.BALLS),
            Triple(GameKind.GOLF, "easy", SoloMetric.STROKES),
        )
        cases.forEach { (g, l, m) ->
            s.record(g, l, m, 30, at = 1); s.record(g, l, m, 22, at = 2); s.record(g, l, m, 25, at = 3)
            val r = s.get(SoloKey(g, l, m))!!
            assertEquals("$g", 22L, r.best); assertEquals("$g", 2L, r.at); assertEquals("$g", 3, r.played)
        }
    }

    @Test fun highestIsBestForAheadPointsAndSquares() {
        val s = store()
        listOf(
            Triple(GameKind.DOTS, "4x4", SoloMetric.BOXES_AHEAD), Triple(GameKind.HOCKEY, "easy", SoloMetric.GOALS_AHEAD), Triple(GameKind.DOMINOES, "1", SoloMetric.POINTS_AHEAD),
            Triple(GameKind.GRID, "timed", SoloMetric.POINTS), Triple(GameKind.GRID, "timed", SoloMetric.LONGEST_WORD), Triple(GameKind.BLOCKS, "1", SoloMetric.SQUARES),
        ).forEach { (g, l, m) ->
            s.record(g, l, m, 4, at = 1); s.record(g, l, m, 9, at = 2); s.record(g, l, m, 6, at = 3)
            assertEquals("$m", 9L, s.get(SoloKey(g, l, m))!!.best)
        }
    }

    @Test fun aLossCountsAsPlayedButNeverSetsABest() {
        val s = store()
        s.record(GameKind.DOTS, "4x4", SoloMetric.BOXES_AHEAD, null, at = 1)
        assertNull(s.get(key(GameKind.DOTS, "4x4", SoloMetric.BOXES_AHEAD))!!.best)
        s.record(GameKind.DOTS, "4x4", SoloMetric.BOXES_AHEAD, 3, at = 2)
        s.record(GameKind.DOTS, "4x4", SoloMetric.BOXES_AHEAD, null, at = 3)
        val r = s.get(key(GameKind.DOTS, "4x4", SoloMetric.BOXES_AHEAD))!!
        assertEquals(3L, r.best); assertEquals(2L, r.at); assertEquals(3, r.played)
    }

    @Test fun runsGoUpWithWinsAndBackToNothingOnALoss() {
        val s = store()
        val k = key(GameKind.TTT, "ultimate", SoloMetric.WINS_IN_ROW)
        s.recordRun(GameKind.TTT, "ultimate", SoloMetric.WINS_IN_ROW, true, at = 1)
        s.recordRun(GameKind.TTT, "ultimate", SoloMetric.WINS_IN_ROW, true, at = 2)
        s.recordRun(GameKind.TTT, "ultimate", SoloMetric.WINS_IN_ROW, true, at = 3)
        s.recordRun(GameKind.TTT, "ultimate", SoloMetric.WINS_IN_ROW, false, at = 4)
        s.recordRun(GameKind.TTT, "ultimate", SoloMetric.WINS_IN_ROW, true, at = 5)
        val r = s.get(k)!!
        assertEquals(3L, r.best); assertEquals(3L, r.at); assertEquals(1, r.run); assertEquals(5, r.played)
    }

    @Test fun levelsAreKeptApart() {
        val s = store()
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 20_000)
        s.record(GameKind.SLIDE, "hard", SoloMetric.TIME, 90_000)
        assertEquals(20_000L, s.get(key(GameKind.SLIDE, "easy", SoloMetric.TIME))!!.best)
        assertEquals(90_000L, s.get(key(GameKind.SLIDE, "hard", SoloMetric.TIME))!!.best)
    }

    @Test fun recordsSurviveAReload() {
        val s = store()
        s.record(GameKind.NUMBERS, "medium", SoloMetric.TIME, 123_000, at = 9)
        s.recordRun(GameKind.CLASH, "2", SoloMetric.WINS_IN_ROW, true, at = 10)
        val again = store()
        assertEquals(SoloRecord(123_000, 9, 1), again.get(key(GameKind.NUMBERS, "medium", SoloMetric.TIME)))
        assertEquals(SoloRecord(1, 10, 1, run = 1), again.get(key(GameKind.CLASH, "2", SoloMetric.WINS_IN_ROW)))
    }

    @Test fun aWrongKindOfCallIsRefused() {
        val s = store()
        val failed = runCatching { s.record(GameKind.TTT, "", SoloMetric.WINS_IN_ROW, 1) }.isFailure
        assertTrue(failed)
    }

    // --- Sudoku's old bests ---

    private val old = mapOf(
        Difficulty.EASY to LevelStats(wins = 7, bestMs = 240_000),
        Difficulty.HARD to LevelStats(wins = 2, bestMs = 900_000),
        Difficulty.MASTER to LevelStats(),
    )

    @Test fun sudokuBestsMoveAcrossWithTheirCounts() {
        val s = store()
        s.migrateSudoku(old)
        assertEquals(SoloRecord(240_000, 0, 7), s.get(key(GameKind.SUDOKU, "easy", SoloMetric.TIME)))
        assertEquals(SoloRecord(900_000, 0, 2), s.get(key(GameKind.SUDOKU, "hard", SoloMetric.TIME)))
        // A level never played leaves nothing.
        assertNull(s.get(key(GameKind.SUDOKU, "master", SoloMetric.TIME)))
    }

    @Test fun runningTheMigrationTwiceChangesNothing() {
        val s = store()
        s.migrateSudoku(old)
        val once = s.records.value
        s.migrateSudoku(old)
        assertEquals(once, s.records.value)
        assertEquals(once, store().also { it.migrateSudoku(old) }.records.value)
    }

    @Test fun aBetterNewTimeIsNeverLostToTheOldOneOrTheOtherWayRound() {
        val s = store()
        s.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 200_000, at = 50)
        s.migrateSudoku(old)
        val r = s.get(key(GameKind.SUDOKU, "easy", SoloMetric.TIME))!!
        assertEquals(200_000L, r.best); assertEquals(50L, r.at); assertEquals(7, r.played)
        // And an old best that beats the new one wins.
        val s2 = SoloRecordStore(File(tmp.root, "two.json"))
        s2.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 300_000, at = 60)
        s2.migrateSudoku(old)
        assertEquals(240_000L, s2.get(key(GameKind.SUDOKU, "easy", SoloMetric.TIME))!!.best)
    }

    @Test fun theOldProfileKeysAreReadAndLeftInPlace() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("wins_medium", 5).putLong("best_medium", 410_000).commit()
        val read = ProfileStore.oldSudokuBests(prefs)
        assertEquals(LevelStats(5, 410_000), read[Difficulty.MEDIUM])
        assertEquals(LevelStats(0, null), read[Difficulty.EASY])
        val s = store()
        s.migrateSudoku(read)
        assertEquals(410_000L, s.get(key(GameKind.SUDOKU, "medium", SoloMetric.TIME))!!.best)
        // Nothing was taken away.
        assertEquals(410_000L, prefs.getLong("best_medium", 0)); assertEquals(5, prefs.getInt("wins_medium", 0))
    }

    @Test fun aTruncatedFileIsKeptNotSilentlyWiped() {
        val good = store().also { it.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 20_000, at = 1) }
        val text = file().readText()
        file().writeText(text.take(text.length / 2))
        val broken = store()
        assertTrue(broken.records.value.isEmpty())
        // The unreadable file is kept beside it, whole, and a new result does not erase that copy.
        assertEquals(text.take(text.length / 2), File(file().path + ".bad").readText())
        broken.record(GameKind.SLIDE, "hard", SoloMetric.TIME, 90_000, at = 2)
        assertEquals(text.take(text.length / 2), File(file().path + ".bad").readText())
        assertEquals(1, good.records.value.size)
    }

    @Test fun saveLeavesNoHalfWrittenFileBehind() {
        store().record(GameKind.SLIDE, "easy", SoloMetric.TIME, 20_000)
        assertTrue(!File(file().path + ".tmp").exists())
        assertEquals(1, store().records.value.size)
    }
}
