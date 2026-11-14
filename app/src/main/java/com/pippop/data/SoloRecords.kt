package com.pippop.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * What a solo best counts, and which way is better (PLAN table 9.3). A "run" metric is a streak:
 * its best is the longest run, and the run now is kept beside it.
 */
enum class SoloMetric(val key: String, val lowerIsBetter: Boolean, val run: Boolean = false) {
    /** Milliseconds to finish (Sudoku, Number Cross, Slide Race). */
    TIME("time", true),
    /** Shots to sink the fleet (Ship Battle). */
    SHOTS("shots", true),
    /** Your discs in a win (Connect Four). */
    DISCS("discs", true),
    /** Boxes ahead at the end, in a win (Dots & Boxes). */
    BOXES_AHEAD("boxes_ahead", false),
    /** Flips to clear the deck (Memory Pairs). */
    FLIPS("flips", true),
    /** Tries to find the word (Word Race). */
    TRIES("tries", true),
    /** Guesses to crack the code (Code Breaker). */
    GUESSES("guesses", true),
    /** Strokes over the nine holes (Mini Golf). */
    STROKES("strokes", true),
    /** Numbers called before your bingo. */
    BALLS("balls", true),
    /** Goals ahead at the end, in a win (Air Hockey). */
    GOALS_AHEAD("goals_ahead", false),
    /** Points ahead at the end, in a win (Dominoes). */
    POINTS_AHEAD("points_ahead", false),
    /** Points in a round (Letter Grid). */
    POINTS("points", false),
    /** Letters in the longest word found (Letter Grid). */
    LONGEST_WORD("longest_word", false),
    /** Squares placed (Corner Blocks). */
    SQUARES("squares", false),
    /** Wins in a row (Tic-Tac-Toe, Colour Clash, Ludo). */
    WINS_IN_ROW("wins_in_row", false, run = true),
    /** Words solved in a row (Word Race, Balloon Words). */
    SOLVED_IN_ROW("solved_in_row", false, run = true);

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** One thing a solo game keeps a best of: a game, its level (or size, deck, board; "" for none) and what is counted. */
data class SoloKey(val game: GameKind, val level: String, val metric: SoloMetric) {
    val text get() = "${game.key}|$level|${metric.key}"

    companion object {
        fun parse(text: String): SoloKey? {
            val parts = text.split("|", limit = 3)
            if (parts.size != 3) return null
            val game = GameKind.entries.firstOrNull { it.key == parts[0] } ?: return null
            return SoloKey(game, parts[1], SoloMetric.from(parts[2]) ?: return null)
        }
    }
}

/**
 * The best result so far ([best], null until a game has one to count), [at] when it was set
 * (epoch milliseconds; 0 when not known, as for a best kept before this store), how many games
 * were [played], and for a run metric the [run] now. [sample] marks a made-up record from the
 * preview's test tools.
 */
data class SoloRecord(val best: Long?, val at: Long, val played: Int, val run: Int = 0, val sample: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("n", played).apply {
        best?.let { put("best", it) }
        if (run != 0) put("run", run)
        if (sample) put("sample", true)
    }

    companion object {
        fun from(json: JSONObject) = SoloRecord(
            best = if (json.has("best") && !json.isNull("best")) json.optLong("best") else null,
            at = json.optLong("at"),
            played = json.optInt("n"),
            run = json.optInt("run"),
            sample = json.optBoolean("sample", false),
        )
    }
}

/**
 * The best of each solo game, kept on this phone in `solo_records.json`: for each game and level,
 * the best result, when it was set and how many were played. What a best is, per game, is
 * [SoloMetric]. A game is recorded once when it ends (never when it is restarted).
 */
class SoloRecordStore internal constructor(private val file: File) {
    private val _records = MutableStateFlow(load())
    val records: StateFlow<Map<SoloKey, SoloRecord>> = _records.asStateFlow()

    private fun load(): Map<SoloKey, SoloRecord> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val json = JSONObject(file.readText())
            json.keys().asSequence().mapNotNull { k -> SoloKey.parse(k)?.let { it to SoloRecord.from(json.getJSONObject(k)) } }.toMap()
        }.getOrElse { e ->
            // A file that cannot be read is kept as it is, so the next save cannot silently wipe the bests.
            runCatching { file.copyTo(File(file.path + ".bad"), overwrite = true) }
            Log.w("SoloRecordStore", "solo_records.json could not be read; kept a copy as solo_records.json.bad", e)
            emptyMap()
        }
    }

    private fun save(all: Map<SoloKey, SoloRecord>) {
        runCatching {
            val json = JSONObject()
            all.forEach { (k, v) -> json.put(k.text, v.toJson()) }
            writeWhole(file, json.toString())
        }
    }

    /** The record for [key], or null when none (a made-up one counts when [real] is false). */
    fun get(key: SoloKey, real: Boolean = false): SoloRecord? = _records.value[key]?.takeIf { !real || !it.sample }

    /** A game of [game] at [level] has ended. [value] is the result to compare with the best, null when this game has none to count (a loss, say). */
    @Synchronized
    fun record(game: GameKind, level: String, metric: SoloMetric, value: Long?, at: Long = System.currentTimeMillis()) {
        require(!metric.run) { "${metric.key} is a run: use recordRun" }
        val key = SoloKey(game, level, metric)
        // A made-up record never survives a real game: the real one starts afresh.
        val old = get(key, real = true)
        val better = value != null && (old?.best == null || if (metric.lowerIsBetter) value < old.best else value > old.best)
        put(key, SoloRecord(if (better) value else old?.best, if (better) at else old?.at ?: 0L, (old?.played ?: 0) + 1))
    }

    /**
     * A second best from a game already counted by [record] (Letter Grid's longest word beside its
     * points): it can set the best and counts the game for its own key.
     */
    @Synchronized
    fun noteBest(game: GameKind, level: String, metric: SoloMetric, value: Long, at: Long = System.currentTimeMillis()) {
        val key = SoloKey(game, level, metric)
        val old = get(key, real = true)
        val better = old?.best == null || if (metric.lowerIsBetter) value < old.best else value > old.best
        put(key, SoloRecord(if (better) value else old?.best, if (better) at else old?.at ?: 0L, (old?.played ?: 0) + 1))
    }

    /** A game ended that [won] or didn't: the run goes up or back to nothing; the best is the longest run. */
    @Synchronized
    fun recordRun(game: GameKind, level: String, metric: SoloMetric, won: Boolean, at: Long = System.currentTimeMillis()) {
        require(metric.run) { "${metric.key} is not a run: use record" }
        val key = SoloKey(game, level, metric)
        val old = get(key, real = true)
        val run = if (won) (old?.run ?: 0) + 1 else 0
        val better = won && run > (old?.best ?: 0L)
        put(key, SoloRecord(if (better) run.toLong() else old?.best, if (better) at else old?.at ?: 0L, (old?.played ?: 0) + 1, run))
    }

    private fun put(key: SoloKey, record: SoloRecord) {
        val all = _records.value + (key to record)
        _records.value = all
        save(all)
    }

    /**
     * Brings Sudoku's bests from before this store (the profile's `wins_<level>` and `best_<level>`)
     * in. Safe to run any number of times: the better best and the larger count win, so nothing is
     * ever lost and nothing is counted twice. The old values are left where they are.
     */
    @Synchronized
    fun migrateSudoku(old: Map<Difficulty, LevelStats>) {
        var all = _records.value
        for ((level, stats) in old) {
            if (stats.wins == 0 && stats.bestMs == null) continue
            val key = SoloKey(GameKind.SUDOKU, level.key, SoloMetric.TIME)
            val now = all[key]?.takeIf { !it.sample }
            val best = listOfNotNull(now?.best, stats.bestMs).minOrNull()
            val merged = SoloRecord(best, now?.at ?: 0L, maxOf(now?.played ?: 0, stats.wins))
            if (merged != now) all = all + (key to merged)
        }
        if (all !== _records.value) {
            _records.value = all
            save(all)
        }
    }

    /** Puts made-up records in, except where a real one is. */
    @Synchronized
    fun addSamples(samples: Map<SoloKey, SoloRecord>) {
        val all = _records.value + samples.filter { (k, _) -> get(k, real = true) == null }.mapValues { it.value.copy(sample = true) }
        _records.value = all
        save(all)
    }

    /** Takes out the made-up records of the preview's test tools; real ones stay as they are. */
    @Synchronized
    fun removeSamples() {
        val all = _records.value.filterValues { !it.sample }
        if (all.size == _records.value.size) return
        _records.value = all
        save(all)
    }

    companion object {
        @Volatile
        private var instance: SoloRecordStore? = null

        /** The store once the app has started it ([init]); null before, as in a test that doesn't need it. */
        val current: SoloRecordStore? get() = instance

        /** Opens the store and brings in Sudoku's old bests. Called when the app starts. */
        fun init(context: Context): SoloRecordStore = get(context)

        fun get(context: Context): SoloRecordStore = instance ?: synchronized(this) {
            instance ?: SoloRecordStore(File(context.applicationContext.filesDir, "solo_records.json")).also {
                it.migrateSudoku(ProfileStore.oldSudokuBests(context.applicationContext))
                instance = it
            }
        }
    }
}

/** Writes [text] as the whole of [file] so that a cut-off write leaves the old file, not half a new one. */
internal fun writeWhole(file: File, text: String) {
    val tmp = File(file.path + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) {
        file.delete()
        check(tmp.renameTo(file)) { "could not replace ${file.name}" }
    }
}
