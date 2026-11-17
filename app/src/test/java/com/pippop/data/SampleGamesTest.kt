package com.pippop.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

/** The preview app's sample games: what they are, and that taking them out leaves real games alone. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SampleGamesTest {
    @get:Rule val tmp = TemporaryFolder()
    private val utc = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 14, 21, 0).toInstant(utc).toEpochMilli()

    private fun stores() = ScoreStore(File(tmp.root, "scores.jsonl")) to SoloRecordStore(File(tmp.root, "solo.json"))

    @Test fun aboutSixtyGamesWithMayaOverFourMonthsAllMarked() {
        val m = SampleGames.matches(now, utc)
        assertEquals(60, m.size)
        assertTrue(m.all { it.sample && it.others == listOf("Maya") && it.at <= now })
        val months = m.map { YearMonth.from(Instant.ofEpochMilli(it.at).atZone(utc)) }.toSet()
        assertEquals(setOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 8)), months)
        assertTrue(m.count { it.durationMs != null } > 50)
        assertTrue(m.any { it.myScore != null && it.theirScore != null })
        assertTrue(m.any { it.winnerCount != null })
        assertTrue(m.any { it.winner == null } && m.any { it.won("You") } && m.any { it.won("Maya") })
    }

    @Test fun soloRecordsForEightGames() {
        val games = SampleGames.soloRecords(now).keys.map { it.game }.toSet()
        assertEquals(8, games.size)
        assertTrue(SampleGames.soloRecords(now).values.all { it.sample })
    }

    @Test fun fillThenClearLeavesRealEntriesIntact() {
        val (scores, solo) = stores()
        val real = MatchRecord(now - 1000, GameKind.LUDO, listOf("Alex"), "You", durationMs = 700_000)
        scores.add(real)
        solo.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 301_000, at = 5)
        solo.record(GameKind.CODE, "4-6-repeats", SoloMetric.GUESSES, 4, at = 6)

        SampleGames.fill(scores, solo, now)
        assertEquals(61, scores.records.value.size)
        // The real Sudoku best is not replaced by a sample, and is still real.
        assertEquals(SoloRecord(301_000, 5, 1), solo.get(SoloKey(GameKind.SUDOKU, "easy", SoloMetric.TIME)))
        assertTrue(solo.records.value.values.any { it.sample })

        SampleGames.clear(scores, solo)
        assertEquals(listOf(real), scores.records.value)
        assertEquals(SoloRecord(301_000, 5, 1), solo.get(SoloKey(GameKind.SUDOKU, "easy", SoloMetric.TIME)))
        assertEquals(SoloRecord(4, 6, 1), solo.get(SoloKey(GameKind.CODE, "4-6-repeats", SoloMetric.GUESSES)))
        assertFalse(solo.records.value.values.any { it.sample })
        // What is on disk agrees.
        assertEquals(listOf(real), ScoreStore(File(tmp.root, "scores.jsonl")).records.value)
        assertFalse(SoloRecordStore(File(tmp.root, "solo.json")).records.value.values.any { it.sample })
    }

    @Test fun fillingTwiceDoesNotDoubleThem() {
        val (scores, solo) = stores()
        SampleGames.fill(scores, solo, now)
        SampleGames.fill(scores, solo, now)
        assertEquals(60, scores.records.value.size)
    }

    @Test fun aRealGameOnASampleRecordStartsAfresh() {
        val (scores, solo) = stores()
        SampleGames.fill(scores, solo, now)
        val k = SoloKey(GameKind.SHIPS, "", SoloMetric.SHOTS)
        assertTrue(solo.get(k)!!.sample)
        solo.record(GameKind.SHIPS, "", SoloMetric.SHOTS, 50, at = 9)
        assertEquals(SoloRecord(50, 9, 1), solo.get(k))
        SampleGames.clear(scores, solo)
        assertEquals(SoloRecord(50, 9, 1), solo.get(k))
    }

    @Test fun thePreviewScoreboardHasSomethingToShow() {
        val sb = Scoreboard.of(SampleGames.matches(now, utc), now, utc)
        assertEquals("Maya", sb.partner)
        assertEquals(3, sb.trophies.size)
    }

    @Test fun addAllNeverDropsRealGamesToMakeRoom() {
        val (scores, _) = stores()
        // Fill the store's limit with real games, then add samples: only samples may be dropped.
        scores.addAll(List(5000) { MatchRecord(it.toLong(), GameKind.LUDO, listOf("Alex"), "You") })
        scores.addAll(SampleGames.matches(now, utc))
        assertEquals(5000, scores.records.value.count { !it.sample })
    }
}
