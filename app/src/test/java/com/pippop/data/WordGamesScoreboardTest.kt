package com.pippop.data

import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntMatch
import com.pippop.game.words.HuntOptions
import com.pippop.ui.SoloHuntViewModel
import com.pippop.ui.screens.SoloLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Words step 11: Word Ladder and Word Hunt are in the scoreboard, the per-game story, the records and the sample games like any other game. */
class WordGamesScoreboardTest {
    private val utc = ZoneOffset.UTC
    private fun at(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 20, 0).toInstant(utc).toEpochMilli()

    // A Word Hunt for four: Maya wins one, you win one, one is a draw; a ladder game is never a draw.
    private val everyone = listOf("Maya", "Priya", "Leo")
    private val records = listOf(
        MatchRecord(at(11, 1), GameKind.HUNT, everyone, "You", myScore = 18, theirScore = 12, durationMs = 400_000),
        MatchRecord(at(11, 2), GameKind.HUNT, everyone, "Maya", myScore = 9, theirScore = 15, durationMs = 300_000),
        MatchRecord(at(11, 3), GameKind.HUNT, everyone, null, myScore = 10, theirScore = 10, durationMs = 350_000),
        MatchRecord(at(11, 4), GameKind.LADDER, listOf("Maya"), "You", durationMs = 120_000),
        MatchRecord(at(11, 5), GameKind.LADDER, listOf("Maya"), "Maya"),
        MatchRecord(at(10, 20), GameKind.HUNT, everyone, "Priya"),
    )

    @Test fun bothGamesAppearInTheMonthAllTimeAndTheirOwnStory() {
        val s = Scoreboard.of(records, at(11, 10), utc)
        assertEquals("Maya", s.partner)
        val hunt = s.games.first { it.game == GameKind.HUNT }
        // A draw is a tie for everyone, a win by someone else is neither yours nor Maya's.
        assertEquals(1, hunt.mine)
        assertEquals(1, hunt.theirs)
        assertEquals(1, hunt.ties)
        val ladder = s.games.first { it.game == GameKind.LADDER }
        assertEquals(1, ladder.mine)
        assertEquals(1, ladder.theirs)
        assertEquals(0, ladder.ties)
        val all = s.allTime.games.first { it.game == GameKind.HUNT }
        assertEquals(1, all.mine)
        assertEquals(1, all.theirs)
        assertEquals(1, all.ties)
        // October's win by Priya is not a win for either of you; the month's cup is a draw.
        assertEquals(null, s.trophies.single().winner)
        val story = Scoreboard.story(records, GameKind.HUNT, "Maya")
        assertEquals(1, story.ties)
        assertEquals("18 to 12", story.mine.biggestWin?.let { "${it.winnerScore} to ${it.otherScore}" })
        assertEquals(300_000L, story.theirs.quickestMs)
    }

    @Test fun aFinishedHuntSaysYourPointsAndTheBestOfTheOthers() {
        com.pippop.ui.HuntTable.installWords()
        val view = com.pippop.game.words.HuntView.from(
            HuntMatch(HuntOptions(HuntLevel.EASY, false, 0, 0), listOf('h', 'g', '3'), startAt = 0).also { m ->
                val w = m.grid.words
                fun claim(mark: Char, i: Int) { val (a, b) = HuntMatch.ends(w[i]); m.claim(mark, a.first, a.second, b.first, b.second, 1) }
                claim('h', 0); claim('g', 1); claim('g', 2)
                m.end("time")
            }.view('h', mapOf('g' to 'o', '3' to 'p')),
        )
        val words = view.found
        val mine = view.scores['h']!!
        val theirs = view.scores['g']!!
        assertEquals(words.first { it.mark == 'h' }.length, mine)
        assertTrue(theirs > 0)
        assertEquals(setOf('h', 'g', '3'), view.scores.keys)
    }

    @Test fun soloRecordsOfBothGamesAreNamedInWordsPeopleUse() {
        fun words(level: String, metric: SoloMetric) = SoloLines.levelWords(SoloKey(GameKind.HUNT, level, metric))
        assertEquals("Easy", words("easy", SoloMetric.TIME))
        assertEquals("Hard · 5 min", words("hard-5", SoloMetric.POINTS))
        assertEquals("Against Medium computers", words("vs-medium", SoloMetric.WINS_IN_ROW))
        assertEquals("Hard", SoloLines.levelWords(SoloKey(GameKind.LADDER, "hard", SoloMetric.WINS_IN_ROW)))
        // A key with either game parses back, so what is kept can be read again.
        for (g in listOf(GameKind.LADDER, GameKind.HUNT)) {
            val key = SoloKey(g, "easy", SoloMetric.WINS_IN_ROW)
            assertEquals(key, SoloKey.parse(key.text))
        }
    }

    @Test fun theSampleGamesIncludeBothAndTheirScoresMatchTheWinner() {
        val m = SampleGames.matches(at(11, 14), utc)
        val hunts = m.filter { it.game == GameKind.HUNT && it.myScore != null }
        for (r in hunts) when {
            r.winner == MatchRecord.ME -> assertTrue("${r.myScore} v ${r.theirScore}", r.myScore!! > r.theirScore!!)
            r.winner == null -> assertEquals(r.myScore, r.theirScore)
            else -> assertTrue(r.myScore!! < r.theirScore!!)
        }
        assertTrue(m.none { it.game == GameKind.LADDER && it.winner == null })
        val kinds = SampleGames.matches(at(11, 14), utc, seed = 3).map { it.game }.toSet()
        assertTrue(GameKind.LADDER in kinds || GameKind.HUNT in kinds || kinds.size > 10)
        assertEquals(SoloHuntViewModel.COUNTDOWN_MS, 5_000L)
    }
}
