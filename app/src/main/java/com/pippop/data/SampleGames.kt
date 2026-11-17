package com.pippop.data

import android.content.Context
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random

/**
 * Made-up games for the preview app's test tools (never reached from the real app): about sixty
 * results against [PARTNER] over this month and the three before, with times and scores, and solo
 * records for eight games. Every one is marked `sample`, so clearing them leaves real ones alone.
 */
object SampleGames {
    const val PARTNER = "Maya"
    private const val COUNT = 60
    private const val MINUTE = 60_000L

    /** About [COUNT] results over this month and the three before it, none later than [now]. */
    fun matches(now: Long, zone: ZoneId = ZoneId.systemDefault(), seed: Long = 7): List<MatchRecord> {
        val rnd = Random(seed)
        val month = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        // More this month, so its scoreboard has something to show.
        val shares = listOf(18, 14, 14, 14)
        return shares.flatMapIndexed { back, n ->
            val m = month.minusMonths(back.toLong())
            val from = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = if (back == 0) now else m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            List(n) {
                val game = GameKind.entries[rnd.nextInt(GameKind.entries.size)]
                val roll = rnd.nextInt(10)
                val winner = when { roll < 5 -> MatchRecord.ME; roll < 9 -> PARTNER; else -> null }
                val facts = factsFor(game, winner, rnd)
                MatchRecord(
                    at = from + (rnd.nextDouble() * (to - from)).toLong(), game = game, others = listOf(PARTNER), winner = winner,
                    durationMs = facts.durationMs, myScore = facts.mine, theirScore = facts.theirs, winnerCount = facts.count, sample = true,
                )
            }
        }.sortedBy { it.at }
    }

    /** What a game of [game] might have said, shaped like [GameFacts.of] gives it. */
    private fun factsFor(game: GameKind, winner: String?, rnd: Random): GameFacts {
        val meWon = winner == MatchRecord.ME
        val tie = winner == null
        fun minutes(from: Int, to: Int) = (from + rnd.nextInt(to - from + 1)) * MINUTE + rnd.nextInt(60) * 1000L
        /** A score pair where the winner has [win] and the other [lose]; a tie gives both [lose]. */
        fun pair(win: Int, lose: Int) = if (tie) lose to lose else if (meWon) win to lose else lose to win
        return when (game) {
            GameKind.SUDOKU -> GameFacts(durationMs = minutes(4, 14))
            GameKind.SHIPS -> GameFacts(durationMs = minutes(6, 14), count = if (tie) null else 28 + rnd.nextInt(30))
            GameKind.CONNECT4 -> GameFacts(durationMs = minutes(2, 6), count = if (tie) null else 7 + rnd.nextInt(10))
            GameKind.DOTS -> pair(9 + rnd.nextInt(4), 4 + rnd.nextInt(3)).let { (a, b) -> GameFacts(durationMs = minutes(5, 12), mine = a, theirs = b) }
            GameKind.MEMORY -> pair(5 + rnd.nextInt(3), 2 + rnd.nextInt(2)).let { (a, b) -> GameFacts(durationMs = minutes(3, 7), mine = a, theirs = b) }
            GameKind.WORDS -> GameFacts(durationMs = minutes(2, 6), count = if (tie) null else 2 + rnd.nextInt(5))
            GameKind.DRAW -> GameFacts(durationMs = minutes(6, 12), mine = 3 + rnd.nextInt(6))
            GameKind.TTT -> GameFacts(durationMs = minutes(1, 4))
            GameKind.DOMINOES -> pair(100, 40 + rnd.nextInt(55)).let { (a, b) -> GameFacts(durationMs = minutes(10, 20), mine = a, theirs = b) }
            GameKind.NUMBERS -> GameFacts(durationMs = minutes(2, 9))
            GameKind.HOCKEY -> pair(7, rnd.nextInt(7)).let { (a, b) -> GameFacts(durationMs = minutes(3, 8), mine = a, theirs = b) }
            GameKind.CLASH -> GameFacts(durationMs = minutes(5, 12))
            GameKind.LUDO -> GameFacts(durationMs = minutes(12, 28))
            GameKind.GRID -> pair(30 + rnd.nextInt(30), 15 + rnd.nextInt(14)).let { (a, b) -> GameFacts(durationMs = 2 * MINUTE, mine = a, theirs = b) }
            GameKind.BALLOON -> pair(8 + rnd.nextInt(8), 3 + rnd.nextInt(4)).let { (a, b) -> GameFacts(durationMs = minutes(6, 12), mine = a, theirs = b) }
            GameKind.CODE -> GameFacts(durationMs = minutes(3, 9), count = if (tie) null else 3 + rnd.nextInt(6))
            GameKind.SLIDE -> GameFacts(durationMs = minutes(1, 4))
            GameKind.BINGO -> GameFacts(durationMs = minutes(2, 6))
            // Fewer strokes win.
            GameKind.GOLF -> pair(31 + rnd.nextInt(5), 36 + rnd.nextInt(8)).let { (a, b) -> GameFacts(durationMs = minutes(10, 20), mine = a, theirs = b) }
            GameKind.BLOCKS -> pair(52 + rnd.nextInt(18), 40 + rnd.nextInt(10)).let { (a, b) -> GameFacts(durationMs = minutes(8, 16), mine = a, theirs = b) }
        }
    }

    /** Solo records for eight games, set over the last weeks. */
    fun soloRecords(now: Long, seed: Long = 11): Map<SoloKey, SoloRecord> {
        val rnd = Random(seed)
        val day = 24 * 60 * MINUTE
        fun rec(best: Long, daysAgo: Int, played: Int, run: Int = 0) = SoloRecord(best, now - daysAgo * day, played, run, sample = true)
        return mapOf(
            SoloKey(GameKind.SUDOKU, "easy", SoloMetric.TIME) to rec(minutesMs(4, rnd), 21, 9),
            SoloKey(GameKind.SUDOKU, "medium", SoloMetric.TIME) to rec(minutesMs(8, rnd), 6, 14),
            SoloKey(GameKind.SUDOKU, "hard", SoloMetric.TIME) to rec(minutesMs(15, rnd), 2, 5),
            SoloKey(GameKind.SHIPS, "", SoloMetric.SHOTS) to rec(31 + rnd.nextLong(6), 10, 8),
            SoloKey(GameKind.CONNECT4, "medium", SoloMetric.DISCS) to rec(9 + rnd.nextLong(4), 4, 12),
            SoloKey(GameKind.MEMORY, "16", SoloMetric.FLIPS) to rec(16 + rnd.nextLong(5), 14, 11),
            SoloKey(GameKind.WORDS, "daily", SoloMetric.TRIES) to rec(3, 1, 23),
            SoloKey(GameKind.WORDS, "daily", SoloMetric.SOLVED_IN_ROW) to rec(7, 9, 23, run = 2),
            SoloKey(GameKind.NUMBERS, "medium", SoloMetric.TIME) to rec(minutesMs(3, rnd), 8, 7),
            SoloKey(GameKind.SLIDE, "medium", SoloMetric.TIME) to rec(minutesMs(2, rnd), 3, 10),
            SoloKey(GameKind.GOLF, "easy", SoloMetric.STROKES) to rec(33 + rnd.nextLong(5), 12, 6),
        )
    }

    private fun minutesMs(minutes: Int, rnd: Random) = minutes * MINUTE + rnd.nextInt(50) * 1000L

    /** Puts the sample games and records in (replacing the last ones), and a few recent games if there are none. */
    fun fill(scores: ScoreStore, solo: SoloRecordStore, now: Long = System.currentTimeMillis()) {
        clear(scores, solo)
        scores.addAll(matches(now))
        solo.addSamples(soloRecords(now))
    }

    /** Takes out only what [fill] put in. */
    fun clear(scores: ScoreStore, solo: SoloRecordStore) {
        scores.removeSamples()
        solo.removeSamples()
    }

    /** [fill] on this phone's own stores, and a few recent games for the home if it has none yet. True when recent games were added. */
    fun fill(context: Context): Boolean {
        val profile = ProfileStore.get(context)
        fill(ScoreStore.get(context), SoloRecordStore.get(context))
        if (profile.profile.value.recentGames.isNotEmpty()) return false
        val now = System.currentTimeMillis()
        val added = listOf(GameKind.GOLF, GameKind.CODE, GameKind.LUDO, GameKind.SHIPS).mapIndexed { i, g -> RecentGame(g, now - (4 - i) * 3_600_000L) }
        added.forEach { profile.noteGamePlayed(it.game, it.at) }
        profile.setSampleRecent(added)
        return true
    }

    /** [clear] on this phone's own stores, and the recent games Fill added (only those, matched exactly). */
    fun clear(context: Context) {
        clear(ScoreStore.get(context), SoloRecordStore.get(context))
        val profile = ProfileStore.get(context)
        profile.removeRecent(profile.sampleRecent())
        profile.setSampleRecent(emptyList())
    }
}
