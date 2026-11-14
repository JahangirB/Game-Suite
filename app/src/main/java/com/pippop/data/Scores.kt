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
 * One game played with others: when it ended, which game, who else played (by name), and
 * who won it ([ME] for this phone, a name, "A & B" for a team, or null for a tie).
 *
 * The rest is optional, and left out of the saved line when a game has nothing to say: how long
 * it took ([durationMs]), each side's own score where the game has one ([myScore], and [theirScore]
 * for the best of the others: goals, points, boxes, pairs) and the winner's count where quickest
 * means fewest ([winnerCount]: discs, shots, tries). [sample] marks a made-up game from the
 * preview app's test tools, so clearing those never touches a real one.
 */
data class MatchRecord(
    val at: Long,
    val game: GameKind,
    val others: List<String>,
    val winner: String?,
    val durationMs: Long? = null,
    val myScore: Int? = null,
    val theirScore: Int? = null,
    val winnerCount: Int? = null,
    val sample: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("game", game.key).put("others", JSONArray(others)).put("winner", winner ?: JSONObject.NULL).apply {
        durationMs?.let { put("ms", it) }
        myScore?.let { put("mine", it) }
        theirScore?.let { put("theirs", it) }
        winnerCount?.let { put("count", it) }
        if (sample) put("sample", true)
    }

    /** Whether [name] won it (alone or in a team). */
    fun won(name: String) = winner != null && winner.split(" & ").contains(name)

    companion object {
        const val ME = "You"

        fun from(json: JSONObject): MatchRecord? {
            val game = GameKind.entries.firstOrNull { it.key == json.optString("game") } ?: return null
            val others = json.optJSONArray("others")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            fun long(key: String) = if (json.has(key) && !json.isNull(key)) json.optLong(key) else null
            fun int(key: String) = if (json.has(key) && !json.isNull(key)) json.optInt(key) else null
            return MatchRecord(
                json.optLong("at"), game, others, if (json.isNull("winner")) null else json.optString("winner"),
                durationMs = long("ms"), myScore = int("mine"), theirScore = int("theirs"), winnerCount = int("count"),
                sample = json.optBoolean("sample", false),
            )
        }
    }
}

/**
 * Every match you've finished with others, kept on this phone (no account: your partner's
 * phone keeps its own). The Scoreboard is worked out from it by [Scoreboard].
 */
class ScoreStore internal constructor(private val file: File) {
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
            if (all.size < MAX) {
                // A cut-off earlier append leaves a last line with no newline: end it first, so this record never joins it.
                if (file.exists() && file.length() > 0 && !endsWithNewline()) file.appendText("\n")
                file.appendText(record.toJson().toString() + "\n")
            }
            else writeWhole(file, all.joinToString("") { it.toJson().toString() + "\n" })
        }
    }

    private fun endsWithNewline(): Boolean = java.io.RandomAccessFile(file, "r").use { f ->
        f.seek(f.length() - 1)
        f.read() == '\n'.code
    }

    /** Several at once (the preview's sample games); real games are never dropped to fit the limit. */
    @Synchronized
    fun addAll(records: List<MatchRecord>) {
        if (records.isEmpty()) return
        var all = (_records.value + records).sortedBy { it.at }
        // Over the limit, only made-up games make room: a real one is never dropped here.
        var extra = all.size - MAX
        if (extra > 0) all = all.filter { !(it.sample && extra-- > 0) }
        _records.value = all
        rewrite(all)
    }

    /** Takes out the made-up games of the preview's test tools; real ones stay as they are. */
    @Synchronized
    fun removeSamples() {
        val all = _records.value.filter { !it.sample }
        if (all.size == _records.value.size) return
        _records.value = all
        rewrite(all)
    }

    private fun rewrite(all: List<MatchRecord>) {
        runCatching { writeWhole(file, all.joinToString("") { it.toJson().toString() + "\n" }) }
    }

    companion object {
        /** Records kept: years of evenings. */
        private const val MAX = 5000

        @Volatile
        private var instance: ScoreStore? = null

        fun get(context: Context): ScoreStore = instance ?: synchronized(this) {
            instance ?: ScoreStore(File(context.applicationContext.filesDir, "scores.jsonl")).also { instance = it }
        }
    }
}

/** One game's line on the scoreboard: wins each in the window (this month or all time), and its last results (newest first). */
data class GameScore(val game: GameKind, val mine: Int, val theirs: Int, val ties: Int, val last: List<MatchRecord>, val bestStreak: Int)

/**
 * Who stands where over a window of time: wins each, ties, each game's line, and the last five
 * results ([form], oldest first: true yours, false your partner's, null a tie).
 */
data class Standing(val mine: Int, val theirs: Int, val ties: Int, val games: List<GameScore>, val form: List<Boolean?>)

/** A month that has ended: who won it (you, the partner, or nobody for a tie), and the score. */
data class Trophy(val month: YearMonth, val winner: String?, val mine: Int, val theirs: Int)

/**
 * One place on the trophy shelf: a month that has ended ([trophy] set), the month in play
 * ([current]), or a month still to come this year (both empty).
 */
data class ShelfSlot(val month: YearMonth, val trophy: Trophy?, val current: Boolean)

/** A win by a score: the winner's own and the other side's. */
data class BigWin(val winnerScore: Int, val otherScore: Int) {
    val margin get() = kotlin.math.abs(winnerScore - otherScore)
}

/** One side's records in one game. A value is null when no game kept it. */
data class SideStory(val wins: Int, val longestRun: Int, val quickestMs: Long?, val quickestCount: Int?, val biggestWin: BigWin?)

/**
 * A game's story between you: each side's records, side by side, and the last ten results
 * (newest first). [missingFacts] is true when some results are from before times and scores were kept.
 */
data class GameStory(val game: GameKind, val mine: SideStory, val theirs: SideStory, val ties: Int, val last: List<MatchRecord>, val missingFacts: Boolean)

/**
 * The Scoreboard against [partner]: this month's wins each, every game's record this month,
 * your current win streak and best ever, a trophy for each month that has ended, and the same
 * standing over everything kept ([allTime]).
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
    /** This month's last five results, oldest first. */
    val form: List<Boolean?> = emptyList(),
    val allTime: Standing = Standing(0, 0, 0, emptyList(), emptyList()),
    val shelf: List<ShelfSlot> = emptyList(),
) {
    /** This month, as the same shape [allTime] has. */
    val thisMonth: Standing get() = Standing(mine, theirs, games.sumOf { it.ties }, games, form)

    companion object {
        /** Shelf places: the month in play, up to two months still to come this year, and the latest cups after them. */
        private const val SHELF = 5
        private const val AHEAD = 2

        /** The partner you play most (over everything kept), or null before any game. */
        fun partnerOf(records: List<MatchRecord>): String? =
            records.flatMap { it.others }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        /** Wins each, ties and per-game lines over [rs] (already only games with [partner]). */
        internal fun standing(rs: List<MatchRecord>, all: List<MatchRecord>, partner: String?): Standing {
            val games = rs.groupBy { it.game }.map { (g, played) ->
                val ofGame = all.filter { it.game == g }
                GameScore(
                    g, played.count { it.won(MatchRecord.ME) }, played.count { partner != null && it.won(partner) }, played.count { it.winner == null },
                    ofGame.takeLast(10).reversed(), longestStreak(ofGame),
                )
            }.sortedWith(compareByDescending<GameScore> { it.mine + it.theirs + it.ties }.thenByDescending { it.last.firstOrNull()?.at ?: 0 })
            return Standing(
                rs.count { it.won(MatchRecord.ME) }, rs.count { partner != null && it.won(partner) }, rs.count { it.winner == null },
                games, form(rs, partner),
            )
        }

        /** The last five results of [rs] (oldest first): yours true, [partner]'s false, a tie null. Someone else's win is not either of yours. */
        fun form(rs: List<MatchRecord>, partner: String?): List<Boolean?> {
            val out = ArrayList<Boolean?>()
            for (r in rs) when {
                r.won(MatchRecord.ME) -> out += true
                partner != null && r.won(partner) -> out += false
                r.winner == null -> out += null
            }
            return out.takeLast(5)
        }

        fun of(records: List<MatchRecord>, now: Long, zone: ZoneId = ZoneId.systemDefault(), partner: String? = partnerOf(records)): Scoreboard {
            val month = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
            fun monthOf(r: MatchRecord) = YearMonth.from(Instant.ofEpochMilli(r.at).atZone(zone))
            // Only matches with the partner count, newest last.
            val ours = records.filter { partner != null && partner in it.others }.sortedBy { it.at }
            val thisMonth = ours.filter { monthOf(it) == month }
            val month0 = standing(thisMonth, ours, partner)
            val allTime = standing(ours, ours, partner)
            val trophies = ours.groupBy(::monthOf).filterKeys { it < month }.toSortedMap().map { (m, rs) ->
                val a = rs.count { it.won(MatchRecord.ME) }
                val b = rs.count { partner != null && it.won(partner) }
                Trophy(m, if (a > b) MatchRecord.ME else if (b > a) partner else null, a, b)
            }
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return Scoreboard(
                partner, month, month.lengthOfMonth() - today.dayOfMonth, month0.mine, month0.theirs, month0.games,
                currentStreak(ours, partner), longestStreak(ours), trophies, ours.size, month0.form, allTime, shelf(trophies, month),
            )
        }

        /** The shelf: the latest cups, the month in play, and empty places for the months still to come this year. */
        fun shelf(trophies: List<Trophy>, month: YearMonth): List<ShelfSlot> {
            val ahead = (month.monthValue + 1..12).take(AHEAD).map { YearMonth.of(month.year, it) }
            val cups = trophies.takeLast(SHELF - 1 - ahead.size).map { ShelfSlot(it.month, it, false) }
            return cups + ShelfSlot(month, null, true) + ahead.map { ShelfSlot(it, null, false) }
        }

        /** Games where "quickest" means fewest (discs, shots, tries, guesses): the winner's count, not the clock. */
        val FEWEST = setOf(GameKind.SHIPS, GameKind.CONNECT4, GameKind.WORDS, GameKind.CODE)

        /** What [game]'s story between you says, from every result kept with [partner]. */
        fun story(records: List<MatchRecord>, game: GameKind, partner: String?): GameStory {
            val rs = records.filter { it.game == game && partner != null && partner in it.others }.sortedBy { it.at }
            fun side(name: String, me: Boolean): SideStory {
                val won = rs.filter { it.won(name) }
                val quick = if (game in FEWEST) null else won.mapNotNull { it.durationMs }.minOrNull()
                val fewest = if (game in FEWEST) won.mapNotNull { it.winnerCount }.minOrNull() else null
                val big = won.mapNotNull { r ->
                    val a = r.myScore ?: return@mapNotNull null
                    val b = r.theirScore ?: return@mapNotNull null
                    if (a == b) null else if (me) BigWin(a, b) else BigWin(b, a)
                }.maxByOrNull { it.margin }
                return SideStory(won.size, longestRun(rs, name), quick, fewest, big)
            }
            val hasFacts = { r: MatchRecord -> r.durationMs != null || r.myScore != null || r.winnerCount != null }
            return GameStory(
                game, side(MatchRecord.ME, true), side(partner ?: "", false), rs.count { it.winner == null },
                rs.takeLast(10).reversed(), rs.any { !hasFacts(it) },
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
        fun longestStreak(rs: List<MatchRecord>): Int = longestRun(rs, MatchRecord.ME)

        /** [name]'s most wins in a row in [rs] (oldest first); a tie or the other's win ends a run. */
        fun longestRun(rs: List<MatchRecord>, name: String): Int {
            var best = 0
            var run = 0
            for (r in rs) {
                run = if (r.won(name)) run + 1 else 0
                best = maxOf(best, run)
            }
            return best
        }
    }
}
