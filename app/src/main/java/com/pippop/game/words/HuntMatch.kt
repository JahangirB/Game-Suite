package com.pippop.game.words

import com.pippop.game.turns.TurnMatch
import com.pippop.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Word Hunt setup: [level], [theme], [backwards], [timer] (minutes: 0 = none, 3, 5 or 8) and the grid
 * [seed] (null: the match picks one). Anything else becomes the default (as cleanOptions in wordhuntmatch.js).
 */
data class HuntOptions(
    val level: HuntLevel = HuntLevel.EASY,
    val theme: String = "Animals",
    val backwards: Boolean = false,
    val timer: Int = 0,
    val seed: Int? = null,
) {
    companion object {
        val TIMERS = listOf(0, 3, 5, 8)

        /** Backwards defaults to off on Easy and on from Medium up. */
        fun clean(level: String?, theme: String?, backwards: Boolean?, timer: Int?, seed: Int?): HuntOptions {
            val l = HuntLevel.entries.firstOrNull { it.key == level } ?: HuntLevel.EASY
            return HuntOptions(
                l, if (theme != null && theme in WordHuntThemes.themes) theme else "Animals",
                backwards ?: (l != HuntLevel.EASY), if (timer in TIMERS) timer!! else 0, seed,
            )
        }
    }
}

/** What a claim did: [mark] scored, now on [score]; [over] when that was the last word. */
data class HuntClaim(val word: String, val mark: Char, val score: Int, val result: TurnResult, val winners: List<Char>)

/** A hint: the first letter of an unfound word is at ([row], [col]). Only the player who asked sees it. */
data class HuntHint(val mark: Char, val row: Int, val col: Int, val score: Int, val hintsLeft: Int)

/** One player's place when the round is over; tied scores share a [rank]. */
data class HuntPlace(val mark: Char, val score: Int, val found: Int, val rank: Int)

/**
 * Word Hunt, the same rules as backend/cloudflare/src/wordhuntmatch.js (both run
 * src/test/resources/wordhuntmatch-cases.json). A race on one shared grid (made by [WordHunt]): the
 * first claim processed wins a word and scores its length. Each player has one hint (shows where one
 * unfound word starts, costs 2 points). The round ends when every word is found or the timer runs out.
 *
 * Choices: a score never goes below 0; a tie for the top score is a draw ([winner] null, [winners]
 * lists the tied marks). Times are milliseconds, given by the caller. Rule errors are
 * IllegalArgumentException with the reason to show: "Not a word here", "Already found by <mark>",
 * "Too late". Needs [WordHuntThemes] and [WordLadder]'s blocklist loaded.
 *
 * It is a [TurnMatch] so a nearby room can run it: a move is {r1, c1, r2, c2} or {hint: true}.
 */
class HuntMatch(
    options: HuntOptions = HuntOptions(),
    val marks: List<Char> = listOf('h', 'g'),
    random: () -> Double = { Random.nextDouble() },
    now: Long = 0,
) : TurnMatch {
    val options: HuntOptions
    val grid: HuntGrid
    private val claims = LinkedHashMap<String, Pair<Char, Long>>()
    private val scores = LinkedHashMap<Char, Int>()
    private val hintsUsed = LinkedHashMap<Char, Int>()
    private class Given(val mark: Char, val word: HuntWord)
    private val hints = mutableListOf<Given>()

    val endsAt: Long
    var over = false
        private set
    var winner: Char? = null
        private set
    var winners: List<Char> = emptyList()
        private set
    var reason: String? = null
        private set

    init {
        require(marks.size in 2..4) { "Word Hunt is for 2 to 4 players." }
        val seed = options.seed ?: (random() * 0x7fffffff).toInt()
        this.options = options.copy(seed = seed)
        grid = WordHunt.make(options.theme, options.level, options.backwards, seed)
        marks.forEach { scores[it] = 0; hintsUsed[it] = 0 }
        endsAt = if (options.timer > 0) now + options.timer * 60000L else 0L
    }

    fun score(mark: Char) = scores.getValue(mark)
    fun foundBy(word: String): Char? = claims[word]?.first

    private fun finish(why: String): TurnResult {
        over = true
        reason = why
        val top = scores.values.max()
        winners = marks.filter { scores.getValue(it) == top }
        winner = winners.singleOrNull()
        return TurnResult(again = false, over = true, winner = winner, reason = why)
    }

    private fun check(mark: Char, now: Long) {
        require(mark in marks) { "You're not in this game." }
        if (!over && endsAt != 0L && now >= endsAt) finish("time")
        require(!over) { "Too late" }
    }

    /** [mark] claims the word between two cells, dragged from either end. */
    fun claim(mark: Char, r1: Int?, c1: Int?, r2: Int?, c2: Int?, now: Long = 0): HuntClaim {
        check(mark, now)
        val w = if (r1 == null || c1 == null || r2 == null || c2 == null) null else grid.words.firstOrNull {
            val (a, b) = ends(it)
            (a == r1 to c1 && b == r2 to c2) || (a == r2 to c2 && b == r1 to c1)
        }
        require(w != null) { "Not a word here" }
        claims[w.word]?.let { throw IllegalArgumentException("Already found by ${it.first}") }
        claims[w.word] = mark to now
        scores[mark] = scores.getValue(mark) + w.length
        val result = if (claims.size == grid.words.size) finish("all") else TurnResult(again = false)
        return HuntClaim(w.word, mark, scores.getValue(mark), result, winners)
    }

    /**
     * [mark] spends its one hint: the first letter's cell of one unfound word, chosen by the seed
     * (words nobody was hinted about come first). Costs 2 points, never below 0.
     */
    fun hint(mark: Char, now: Long = 0): HuntHint {
        check(mark, now)
        require(hintsUsed.getValue(mark) < 1) { "No hints left" }
        val unfound = grid.words.filter { it.word !in claims }
        val fresh = unfound.filter { u -> hints.none { it.word.word == u.word } }
        val pool = fresh.ifEmpty { unfound }
        val w = pool[WordHunt.Mulberry32(options.seed!! + hints.size * 40503).below(pool.size)]
        hintsUsed[mark] = hintsUsed.getValue(mark) + 1
        scores[mark] = maxOf(0, scores.getValue(mark) - HINT_COST)
        hints.add(Given(mark, w))
        return HuntHint(mark, w.row, w.col, scores.getValue(mark), 1 - hintsUsed.getValue(mark))
    }

    /** Call when the clock reaches [endsAt]. True if that ended the round. */
    fun tick(now: Long): Boolean {
        if (over || endsAt == 0L || now < endsAt) return false
        finish("time")
        return true
    }

    override fun play(mark: Char, move: JSONObject): TurnResult = play(mark, move, 0)

    override fun play(mark: Char, move: JSONObject, now: Long): TurnResult {
        if (move.optBoolean("hint")) {
            hint(mark, now)
            return TurnResult(again = false)
        }
        fun int(k: String) = if (move.has(k) && move.get(k) is Int) move.getInt(k) else null
        return claim(mark, int("r1"), int("c1"), int("r2"), int("c2"), now).result
    }

    /** Everyone's place once the round is over, best first. */
    fun results(): List<HuntPlace> {
        if (!over) return emptyList()
        val found = { m: Char -> claims.values.count { it.first == m } }
        val order = marks.withIndex().sortedWith(compareBy({ -scores.getValue(it.value) }, { it.index })).map { it.value }
        val out = ArrayList<HuntPlace>()
        order.forEachIndexed { k, m ->
            val s = scores.getValue(m)
            out.add(HuntPlace(m, s, found(m), if (k > 0 && out[k - 1].score == s) out[k - 1].rank else k + 1))
        }
        return out
    }

    /**
     * The round as [mark] sees it: the same JSON as view() in wordhuntmatch.js. Unfound words are
     * listed by text and length only (no cells); found ones say who found them and where. "hints"
     * holds only this player's own hint cells for words still unfound.
     */
    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject {
        val words = JSONArray()
        for (w in grid.words) {
            val o = JSONObject().put("word", w.word).put("length", w.length)
            val by = claims[w.word]?.first
            if (by == null) o.put("foundBy", JSONObject.NULL)
            else o.put("foundBy", by.toString()).put("row", w.row).put("col", w.col).put("dir", w.dir.key)
            words.put(o)
        }
        fun counts(f: (Char) -> Int) = JSONObject().also { j -> marks.forEach { j.put(it.toString(), f(it)) } }
        return JSONObject()
            .put("me", mark.toString())
            .put("level", options.level.key).put("theme", options.theme).put("backwards", options.backwards)
            .put("timer", options.timer).put("width", grid.width).put("height", grid.height)
            .put("rows", JSONArray(grid.rows))
            .put("phase", if (over) "over" else "playing")
            .put("words", words)
            .put("marks", JSONArray(marks.map { it.toString() }))
            .put("scores", counts { scores.getValue(it) })
            .put("hintsLeft", counts { 1 - hintsUsed.getValue(it) })
            .put("hints", JSONArray(hints.filter { it.mark == mark && it.word.word !in claims }.map { JSONObject().put("row", it.word.row).put("col", it.word.col) }))
            .put("endsAt", endsAt)
            .put("winner", if (over && winner != null) winner.toString() else JSONObject.NULL)
            .put("winners", JSONArray(if (over) winners.map { it.toString() } else emptyList()))
            .put("reason", reason ?: JSONObject.NULL)
            .put("results", JSONArray(results().map {
                JSONObject().put("mark", it.mark.toString()).put("score", it.score).put("found", it.found).put("rank", it.rank)
            }))
    }

    companion object {
        const val HINT_COST = 2

        /** The two end cells of a placed word. */
        fun ends(w: HuntWord): Pair<Pair<Int, Int>, Pair<Int, Int>> =
            (w.row to w.col) to ((w.row + w.dir.dr * (w.length - 1)) to (w.col + w.dir.dc * (w.length - 1)))
    }
}
