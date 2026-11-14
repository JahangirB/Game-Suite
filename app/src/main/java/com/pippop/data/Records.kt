package com.pippop.data

/**
 * A personal record that a result has just beaten (PLAN step 22), what the result card says in
 * its gold line. Three kinds: against a partner, your quickest win ([Quickest]: fewest [count] where
 * a game counts fewest, else the [ms] it took) and your biggest win ([Biggest], by [margin]); and
 * alone, your own best for a game and level ([Solo]).
 */
sealed class BrokenRecord {
    abstract val game: GameKind

    data class Quickest(override val game: GameKind, val count: Int?, val ms: Long?) : BrokenRecord()
    data class Biggest(override val game: GameKind, val margin: Int) : BrokenRecord()
    data class Solo(val key: SoloKey, val value: Long) : BrokenRecord() {
        override val game get() = key.game
    }
}

/**
 * Works out whether a result is a record. Every rule here compares with the state BEFORE the
 * result was stored: call it first, then store (the stores do this themselves).
 *
 * A record needs something earlier to beat: a first result in a game is not one, and a tie with
 * the earlier best is not one.
 *
 * This is the one place a record is detected. PLAN steps 31-32 (the `record` sound and buzz)
 * hook in where the card shows the line ([com.pippop.ui.screens.RecordLine]), which only appears
 * when this has found one.
 */
object Records {
    /**
     * Whether [value] beats [oldBest]: strictly better, and only when there is an earlier best and a value.
     */
    fun beats(lowerIsBetter: Boolean, oldBest: Long?, value: Long?): Boolean =
        value != null && oldBest != null && if (lowerIsBetter) value < oldBest else value > oldBest

    /**
     * The record [result] sets against its partner, from [earlier] (every result kept BEFORE it).
     * Only your own wins of the same game against the same players count, and only those that kept the
     * value in question. When both a quickest and a biggest win fall at once, the quickest is said
     * (the order the game's story has them in).
     */
    fun againstPartner(earlier: List<MatchRecord>, result: MatchRecord): BrokenRecord? {
        if (!result.won(MatchRecord.ME) || result.others.isEmpty()) return null
        val group = result.others.toSet()
        // Made-up games from the preview's test tools never count against real ones.
        val wins = earlier.filter { !it.sample && it.game == result.game && it.others.toSet() == group && it.won(MatchRecord.ME) }
        val game = result.game
        // Quickest: fewest where the game counts fewest, else the clock (the story sheet's rule).
        if (game in Scoreboard.FEWEST) {
            val now = result.winnerCount
            if (now != null && beats(true, wins.mapNotNull { it.winnerCount }.minOrNull()?.toLong(), now.toLong())) {
                return BrokenRecord.Quickest(game, count = now, ms = null)
            }
        } else {
            val now = result.durationMs
            if (now != null && beats(true, wins.mapNotNull { it.durationMs }.minOrNull(), now)) {
                return BrokenRecord.Quickest(game, count = null, ms = now)
            }
        }
        val margin = margin(result)
        if (margin != null && margin > 0 && beats(false, wins.mapNotNull { margin(it) }.maxOrNull()?.toLong(), margin.toLong())) {
            return BrokenRecord.Biggest(game, margin)
        }
        return null
    }

    /**
     * Whether a game that ended in [room] can set a record at all: not a win by forfeit or timeout (nobody
     * can beat such a time or count), and not a shared win in Sudoku together (it is not yours alone).
     */
    fun canSet(room: RoomState) = !room.together && room.endReason != "forfeit" && room.endReason != "timeout"

    /** How far ahead a result was by the game's own score, or null when it kept no scores. */
    private fun margin(r: MatchRecord): Int? {
        val a = r.myScore ?: return null
        val b = r.theirScore ?: return null
        return kotlin.math.abs(a - b)
    }
}
