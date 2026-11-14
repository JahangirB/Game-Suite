package com.pippop.data

import java.time.YearMonth
import java.time.ZoneId

/**
 * A month that has ended with games against [partner], for the cup moment: who took it ([winner]
 * is [MatchRecord.ME], the partner's name, or null for a tie), the wins each ([mine], [theirs]),
 * how many games were played together that month ([games], wins, losses and ties alike) and
 * the game played most ([mostPlayed]).
 */
data class MonthEnd(
    val month: YearMonth,
    val partner: String,
    val winner: String?,
    val mine: Int,
    val theirs: Int,
    val games: Int,
    val mostPlayed: GameKind?,
) {
    companion object {
        /**
         * The most recent month before [now]'s own that has games against the partner you play most,
         * or null when there is none yet. Months are in [zone] (the phone's), as the Scoreboard's are.
         * Made-up sample games count, so the preview's test tools can show it.
         */
        fun latest(records: List<MatchRecord>, now: Long, zone: ZoneId = ZoneId.systemDefault()): MonthEnd? {
            val board = Scoreboard.of(records, now, zone)
            val partner = board.partner ?: return null
            val trophy = board.trophies.lastOrNull() ?: return null
            val played = records.filter {
                partner in it.others && YearMonth.from(java.time.Instant.ofEpochMilli(it.at).atZone(zone)) == trophy.month
            }
            if (trophy.mine + trophy.theirs == 0) return null // someone else won every game: the shelf keeps it, no moment
            // Named only when it clearly played most: one game with more results than every other.
            val counts = played.groupingBy { it.game }.eachCount().entries.sortedByDescending { it.value }
            val most = counts.firstOrNull()?.takeIf { counts.size == 1 || it.value > counts[1].value }?.key
            return MonthEnd(trophy.month, partner, trophy.winner, trophy.mine, trophy.theirs, played.size, most)
        }

        /**
         * What to show when the app opens: [latest], unless its cup was already shown ([lastShown] is
         * that month or a later one; a remembered month that is not before the current one is a clock
         * or data oddity and blocks nothing). Only the latest finished month is ever offered, so months missed
         * (or old history on a first run) never pile up as several moments.
         */
        fun toShow(records: List<MatchRecord>, now: Long, lastShown: YearMonth?, zone: ZoneId = ZoneId.systemDefault()): MonthEnd? =
            latest(records, now, zone)?.takeIf { end ->
                val current = YearMonth.from(java.time.Instant.ofEpochMilli(now).atZone(zone))
                lastShown == null || lastShown >= current || end.month > lastShown
            }
    }
}
