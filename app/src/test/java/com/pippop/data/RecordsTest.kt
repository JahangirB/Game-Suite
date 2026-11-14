package com.pippop.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Whether a result is a record (PLAN step 22): against a partner, and alone. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class RecordsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun win(game: GameKind, who: String = "Maya", at: Long = 1, ms: Long? = null, mine: Int? = null, theirs: Int? = null, count: Int? = null, winner: String? = MatchRecord.ME) =
        MatchRecord(at, game, listOf(who), winner, durationMs = ms, myScore = mine, theirScore = theirs, winnerCount = count)

    // ---- Against a partner ----------------------------------------------------------------------

    @Test fun aFirstGameIsNotARecord() {
        assertNull(Records.againstPartner(emptyList(), win(GameKind.CONNECT4, count = 11)))
        assertNull(Records.againstPartner(emptyList(), win(GameKind.DOMINOES, mine = 100, theirs = 50, ms = 60_000)))
    }

    @Test fun beatingTheQuickestWinByCountIsARecord() {
        val earlier = listOf(win(GameKind.CONNECT4, count = 14, at = 1), win(GameKind.CONNECT4, count = 12, at = 2))
        assertEquals(BrokenRecord.Quickest(GameKind.CONNECT4, count = 11, ms = null), Records.againstPartner(earlier, win(GameKind.CONNECT4, count = 11)))
    }

    @Test fun beatingTheQuickestWinByDurationIsARecord() {
        val earlier = listOf(win(GameKind.SLIDE, ms = 90_000), win(GameKind.SLIDE, ms = 70_000))
        assertEquals(BrokenRecord.Quickest(GameKind.SLIDE, count = null, ms = 65_000), Records.againstPartner(earlier, win(GameKind.SLIDE, ms = 65_000)))
    }

    @Test fun aFewestGameIgnoresTheClockAndOtherGamesIgnoreTheCount() {
        // Connect Four counts discs: a faster clock is nothing.
        assertNull(Records.againstPartner(listOf(win(GameKind.CONNECT4, count = 10, ms = 90_000)), win(GameKind.CONNECT4, count = 12, ms = 30_000)))
        // Slide Race counts the clock: a smaller count is nothing.
        assertNull(Records.againstPartner(listOf(win(GameKind.SLIDE, ms = 60_000, count = 9)), win(GameKind.SLIDE, ms = 70_000, count = 3)))
    }

    @Test fun beatingTheBiggestWinIsARecord() {
        val earlier = listOf(win(GameKind.DOMINOES, mine = 100, theirs = 70), win(GameKind.DOMINOES, mine = 120, theirs = 90))
        assertEquals(BrokenRecord.Biggest(GameKind.DOMINOES, 42), Records.againstPartner(earlier, win(GameKind.DOMINOES, mine = 112, theirs = 70)))
    }

    @Test fun aLowScoreWinsAtGolfByItsMargin() {
        val earlier = listOf(win(GameKind.GOLF, mine = 30, theirs = 35))
        assertEquals(BrokenRecord.Biggest(GameKind.GOLF, 8), Records.againstPartner(earlier, win(GameKind.GOLF, mine = 28, theirs = 36)))
    }

    @Test fun aTieWithTheEarlierBestIsNotARecord() {
        assertNull(Records.againstPartner(listOf(win(GameKind.CONNECT4, count = 11)), win(GameKind.CONNECT4, count = 11)))
        assertNull(Records.againstPartner(listOf(win(GameKind.SLIDE, ms = 60_000)), win(GameKind.SLIDE, ms = 60_000)))
        assertNull(Records.againstPartner(listOf(win(GameKind.DOMINOES, mine = 100, theirs = 58)), win(GameKind.DOMINOES, mine = 90, theirs = 48)))
    }

    @Test fun aDifferentPartnerDoesNotCount() {
        val earlier = listOf(win(GameKind.CONNECT4, who = "Alex", count = 20))
        // Nothing earlier with Maya: a first result, not a record, even though 11 beats Alex's 20.
        assertNull(Records.againstPartner(earlier, win(GameKind.CONNECT4, who = "Maya", count = 11)))
        // And Alex's quicker win does not stop Maya's own record.
        val both = earlier + win(GameKind.CONNECT4, who = "Alex", count = 5) + win(GameKind.CONNECT4, who = "Maya", count = 15)
        assertEquals(BrokenRecord.Quickest(GameKind.CONNECT4, 11, null), Records.againstPartner(both, win(GameKind.CONNECT4, who = "Maya", count = 11)))
    }

    @Test fun aDifferentGameDoesNotCount() {
        assertNull(Records.againstPartner(listOf(win(GameKind.SHIPS, count = 30)), win(GameKind.CONNECT4, count = 11)))
    }

    @Test fun aLossIsNotARecord() {
        val earlier = listOf(win(GameKind.CONNECT4, count = 14))
        assertNull(Records.againstPartner(earlier, win(GameKind.CONNECT4, count = 8, winner = "Maya")))
        assertNull(Records.againstPartner(earlier, win(GameKind.CONNECT4, count = 8, winner = null)))
    }

    @Test fun onlyYourOwnEarlierWinsAreBeaten() {
        // Maya's quicker win is hers, not yours to beat.
        val earlier = listOf(win(GameKind.CONNECT4, count = 14), win(GameKind.CONNECT4, count = 6, winner = "Maya"))
        assertEquals(BrokenRecord.Quickest(GameKind.CONNECT4, 11, null), Records.againstPartner(earlier, win(GameKind.CONNECT4, count = 11)))
    }

    @Test fun earlierGamesWithoutTheValueLeaveNothingToBeat() {
        // Older results kept no times: a first timed game is not a record.
        val earlier = listOf(win(GameKind.SLIDE), win(GameKind.SLIDE))
        assertNull(Records.againstPartner(earlier, win(GameKind.SLIDE, ms = 50_000)))
        // A result without the value is no record either.
        assertNull(Records.againstPartner(listOf(win(GameKind.SLIDE, ms = 50_000)), win(GameKind.SLIDE)))
    }

    @Test fun whenQuickestAndBiggestFallTogetherTheQuickestIsSaid() {
        val earlier = listOf(win(GameKind.DOTS, ms = 120_000, mine = 10, theirs = 8))
        val both = win(GameKind.DOTS, ms = 90_000, mine = 14, theirs = 6)
        assertEquals(BrokenRecord.Quickest(GameKind.DOTS, count = null, ms = 90_000), Records.againstPartner(earlier, both))
        // With only the margin better, it is the biggest that is said.
        assertEquals(BrokenRecord.Biggest(GameKind.DOTS, 8), Records.againstPartner(earlier, both.copy(durationMs = 130_000)))
    }

    @Test fun theRecordIsJudgedBeforeTheResultIsKept() {
        // The order the app uses: ask against what was kept, then add. Asking after adding finds nothing to beat.
        val store = ScoreStore(File(tmp.root, "scores.jsonl"))
        store.add(win(GameKind.CONNECT4, count = 14))
        val result = win(GameKind.CONNECT4, count = 11, at = 2)
        val before = Records.againstPartner(store.records.value, result)
        store.add(result)
        val after = Records.againstPartner(store.records.value, result)
        assertEquals(BrokenRecord.Quickest(GameKind.CONNECT4, 11, null), before)
        assertNull(after)
    }

    // ---- Alone ----------------------------------------------------------------------------------

    private fun store() = SoloRecordStore(File(tmp.root, "solo_records.json"))

    @Test fun soloFirstPlayIsNotARecord() {
        val s = store()
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 72_000)
        assertNull(s.broke.value)
        s.recordRun(GameKind.TTT, "classic", SoloMetric.WINS_IN_ROW, true)
        assertNull(s.broke.value)
    }

    @Test fun soloBeatingALowerIsBetterBestIsARecord() {
        val s = store()
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 80_000)
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 72_000)
        assertEquals(BrokenRecord.Solo(SoloKey(GameKind.SLIDE, "easy", SoloMetric.TIME), 72_000), s.broke.value)
    }

    @Test fun soloBeatingAHigherIsBetterBestIsARecord() {
        val s = store()
        s.record(GameKind.DOMINOES, "1", SoloMetric.POINTS_AHEAD, 20)
        s.record(GameKind.DOMINOES, "1", SoloMetric.POINTS_AHEAD, 35)
        assertEquals(BrokenRecord.Solo(SoloKey(GameKind.DOMINOES, "1", SoloMetric.POINTS_AHEAD), 35), s.broke.value)
    }

    @Test fun soloATieOrAWorseResultOrAnotherLevelIsNotARecord() {
        val s = store()
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 72_000)
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 72_000)
        assertNull(s.broke.value)
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 90_000)
        assertNull(s.broke.value)
        // Another size has no earlier best of its own.
        s.record(GameKind.SLIDE, "hard", SoloMetric.TIME, 60_000)
        assertNull(s.broke.value)
        // A game with nothing to count (a loss) is no record.
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, null)
        assertNull(s.broke.value)
    }

    @Test fun soloTheNextGameReplacesTheLastRecord() {
        val s = store()
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 80_000)
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 72_000)
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 75_000)
        assertNull(s.broke.value)
    }

    @Test fun soloRunRecordsNeedALongerRun() {
        val s = store()
        s.recordRun(GameKind.TTT, "classic", SoloMetric.WINS_IN_ROW, true)
        s.recordRun(GameKind.TTT, "classic", SoloMetric.WINS_IN_ROW, true)
        assertEquals(BrokenRecord.Solo(SoloKey(GameKind.TTT, "classic", SoloMetric.WINS_IN_ROW), 2), s.broke.value)
        s.recordRun(GameKind.TTT, "classic", SoloMetric.WINS_IN_ROW, false)
        assertNull(s.broke.value)
        s.recordRun(GameKind.TTT, "classic", SoloMetric.WINS_IN_ROW, true)
        assertNull(s.broke.value)
    }

    @Test fun soloTwoRecordsAtOnceSaysTheMainOne() {
        val s = store()
        s.record(GameKind.GRID, "easy", SoloMetric.POINTS, 20)
        s.noteBest(GameKind.GRID, "easy", SoloMetric.LONGEST_WORD, 4)
        // Both beaten in one game: the points (the main best) are said, not the longest word.
        s.record(GameKind.GRID, "easy", SoloMetric.POINTS, 30)
        s.noteBest(GameKind.GRID, "easy", SoloMetric.LONGEST_WORD, 6)
        assertEquals(SoloMetric.POINTS, s.broke.value?.key?.metric)
        // Only the second beaten: it is said.
        s.record(GameKind.GRID, "easy", SoloMetric.POINTS, 10)
        s.noteBest(GameKind.GRID, "easy", SoloMetric.LONGEST_WORD, 8)
        assertEquals(SoloMetric.LONGEST_WORD, s.broke.value?.key?.metric)
    }

    @Test fun soloTheRecordIsJudgedBeforeTheBestIsStored() {
        val s = store()
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 80_000)
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 72_000)
        // The best already says 72 s now; the break was found against the 80 s before it.
        assertEquals(72_000L, s.get(SoloKey(GameKind.SLIDE, "easy", SoloMetric.TIME))?.best)
        assertEquals(72_000L, s.broke.value?.value)
    }

    @Test fun clearBrokeDropsTheLastRecord() {
        val s = store()
        s.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 400_000)
        s.record(GameKind.SUDOKU, "easy", SoloMetric.TIME, 350_000)
        assertEquals(350_000L, s.broke.value?.value)
        // A lost game or a new board calls this: the old record must not show on it.
        s.clearBroke()
        assertNull(s.broke.value)
    }

    @Test fun madeUpGamesNeverCountAgainstRealOnes() {
        val sample = win(GameKind.CONNECT4, count = 8).copy(sample = true)
        assertNull(Records.againstPartner(listOf(sample), win(GameKind.CONNECT4, count = 11)))
        val real = win(GameKind.CONNECT4, count = 14)
        assertEquals(BrokenRecord.Quickest(GameKind.CONNECT4, 11, null), Records.againstPartner(listOf(sample, real), win(GameKind.CONNECT4, count = 11)))
    }

    private fun room(reason: String?, together: Boolean = false) = RoomState(
        code = "ABCD", role = "host", game = GameKind.CONNECT4, status = RoomStatus.FINISHED, difficulty = null, round = 1, startAt = 1_000, puzzle = null, solution = null,
        winner = "me", endReason = reason, me = PlayerInfo(name = "Me", avatar = null, deviceId = null, emoji = null, ready = true, difficulty = null, progress = null, finishMs = null, left = false, idleMs = 0, online = true),
        opponent = null, ships = null, turns = null, serverTime = 301_000,
    ).let { if (together) it.copy(game = GameKind.SUDOKU, sudokuTogether = true) else it }

    @Test fun aForfeitOrTimeoutOrSharedWinCannotSetARecord() {
        assertEquals(true, Records.canSet(room("four")))
        assertEquals(false, Records.canSet(room("forfeit")))
        assertEquals(false, Records.canSet(room("timeout")))
        assertEquals(false, Records.canSet(room("solved", together = true)))
    }

    @Test fun forfeitAndTimeoutKeepNoFactsSoTheyCountAsAWinOnly() {
        val turns = TurnsView(turn = null, board = "mmmm....." + "o".repeat(5), winLine = emptyList(), lastMoveByMe = null, lastCell = null)
        assertEquals(4, GameFacts.of(room("four").copy(turns = turns)).count)
        for (why in listOf("forfeit", "timeout")) {
            assertEquals(GameFacts(), GameFacts.of(room(why).copy(turns = turns)))
        }
    }

    @Test fun aMadeUpBestIsNotBeaten() {
        val s = store()
        s.addSamples(mapOf(SoloKey(GameKind.SLIDE, "easy", SoloMetric.TIME) to SoloRecord(50_000, 1, 3)))
        s.record(GameKind.SLIDE, "easy", SoloMetric.TIME, 40_000)
        assertNull(s.broke.value)
    }
}
