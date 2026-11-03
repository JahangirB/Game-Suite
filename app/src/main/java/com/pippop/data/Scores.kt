package com.pippop.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * One match played with others: when it ended, which game, who else played (by name), and
 * who won it ([ME] for this phone, a name, "A & B" for a team, or null for a tie).
 */
data class MatchRecord(val at: Long, val game: GameKind, val others: List<String>, val winner: String?) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("game", game.key).put("others", JSONArray(others)).put("winner", winner ?: JSONObject.NULL)

    /** Whether [name] won it (alone or in a team). */
    fun won(name: String) = winner != null && winner.split(" & ").contains(name)

    companion object {
        const val ME = "You"

        fun from(json: JSONObject): MatchRecord? {
            val game = GameKind.entries.firstOrNull { it.key == json.optString("game") } ?: return null
            val others = json.optJSONArray("others")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            return MatchRecord(json.optLong("at"), game, others, if (json.isNull("winner")) null else json.optString("winner"))
        }
    }
}

/**
 * Every match you've finished with others, kept on this phone (no account: your partner's
 * phone keeps its own). The Scoreboard is worked out from it by [Scoreboard].
 */
class ScoreStore private constructor(context: Context) {
    private val file = File(context.filesDir, "scores.jsonl")
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<MatchRecord>> = _records.asStateFlow()

    private fun load(): List<MatchRecord> = runCatching {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { line -> runCatching { MatchRecord.from(JSONObject(line)) }.getOrNull() }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(record: MatchRecord) {
        val all = (_records.value + record).takeLast(MAX)
        _records.value = all
        runCatching {
            if (all.size < MAX) file.appendText(record.toJson().toString() + "\n")
            else file.writeText(all.joinToString("") { it.toJson().toString() + "\n" })
        }
    }

    companion object {
        /** Records kept: years of evenings. */
        private const val MAX = 5000

        @Volatile
        private var instance: ScoreStore? = null

        fun get(context: Context): ScoreStore = instance ?: synchronized(this) {
            instance ?: ScoreStore(context.applicationContext).also { instance = it }
        }
    }
}

/** One game's line on the scoreboard: wins each this month, and its last results (newest first). */
data class GameScore(val game: GameKind, val mine: Int, val theirs: Int, val ties: Int, val last: List<MatchRecord>, val bestStreak: Int)

/** A month that has ended: who won it (you, the partner, or nobody for a tie), and the score. */
data class Trophy(val month: YearMonth, val winner: String?, val mine: Int, val theirs: Int)

/**
 * The Scoreboard against [partner]: this month's wins each, every game's record this month,
 * your current win streak and best ever, and a trophy for each month that has ended.
 */
data class Scoreboard(
    val partner: String?,
    val month: YearMonth,
    val daysLeft: Int,
    val mine: Int,
    val theirs: Int,
    val games: List<GameScore>,
    /** Wins in a row now: positive yours, negative your partner's. */
    val streak: Int,
    val bestStreak: Int,
    val trophies: List<Trophy>,
    val total: Int,
) {
    companion object {
        /** The partner you play most (over everything kept), or null before any game. */
        fun partnerOf(records: List<MatchRecord>): String? =
            records.flatMap { it.others }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        fun of(records: List<MatchRecord>, now: Long, zone: ZoneId = ZoneId.systemDefault(), partner: String? = partnerOf(records)): Scoreboard {
            val month = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
            fun monthOf(r: MatchRecord) = YearMonth.from(Instant.ofEpochMilli(r.at).atZone(zone))
            // Only matches with the partner count, newest last.
            val ours = records.filter { partner != null && partner in it.others }.sortedBy { it.at }
            val thisMonth = ours.filter { monthOf(it) == month }
            val mine = thisMonth.count { it.won(MatchRecord.ME) }
            val theirs = thisMonth.count { partner != null && it.won(partner) }
            val games = thisMonth.groupBy { it.game }.map { (g, rs) ->
                val all = ours.filter { it.game == g }
                GameScore(
                    g, rs.count { it.won(MatchRecord.ME) }, rs.count { partner != null && it.won(partner) }, rs.count { it.winner == null },
                    all.takeLast(10).reversed(), longestStreak(all),
                )
            }.sortedWith(compareByDescending<GameScore> { it.mine + it.theirs + it.ties }.thenByDescending { it.last.firstOrNull()?.at ?: 0 })
            val trophies = ours.groupBy(::monthOf).filterKeys { it < month }.toSortedMap().map { (m, rs) ->
                val a = rs.count { it.won(MatchRecord.ME) }
                val b = rs.count { partner != null && it.won(partner) }
                Trophy(m, if (a > b) MatchRecord.ME else if (b > a) partner else null, a, b)
            }
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return Scoreboard(
                partner, month, month.lengthOfMonth() - today.dayOfMonth, mine, theirs, games,
                currentStreak(ours, partner), longestStreak(ours), trophies, ours.size,
            )
        }

        /** Wins in a row at the end of [rs]: yours positive, [partner]'s negative; a tie ends it. */
        fun currentStreak(rs: List<MatchRecord>, partner: String?): Int {
            var n = 0
            for (r in rs.asReversed()) {
                val me = r.won(MatchRecord.ME)
                val them = partner != null && r.won(partner)
                when {
                    me && n >= 0 -> n++
                    them && n <= 0 -> n--
                    else -> break
                }
            }
            return n
        }

        /** Your most wins in a row in [rs]. */
        fun longestStreak(rs: List<MatchRecord>): Int {
            var best = 0
            var run = 0
            for (r in rs) {
                run = if (r.won(MatchRecord.ME)) run + 1 else 0
                best = maxOf(best, run)
            }
            return best
        }
    }
}
