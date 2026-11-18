package com.pippop.game.words

import com.pippop.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Word Ladder setup: [length] is 4, 5 or 6 letters; [timer] is 0 (off), 30 or 60 seconds a turn. */
data class LadderOptions(val length: Int = 5, val timer: Int = 0) {
    companion object {
        val LENGTHS = listOf(4, 5, 6)
        val TIMERS = listOf(0, 30, 60)

        /** Anything else becomes the default (as cleanOptions in wordladder.js). */
        fun clean(length: Int?, timer: Int?) =
            LadderOptions(if (length in LENGTHS) length!! else 5, if (timer in TIMERS) timer!! else 0)
    }
}

/**
 * Word Ladder, the same rules as backend/cloudflare/src/wordladder.js (both run the cases in
 * app/src/test/resources/wordladder-cases.json). Each player secretly picks a word; the coin
 * ([random] below 0.5: the first mark's word is the START) decides the start, the other word is the
 * TARGET, and the owner of the TARGET moves first. A move changes exactly one letter into a real,
 * unused word. Whoever's move spells the TARGET wins; a player with no legal move loses; a turn
 * that runs out of time passes to the other player. Times are milliseconds, given by the caller.
 *
 * Rule errors are IllegalArgumentException with the reason to show ("Change one letter",
 * "Not a word", "Already used", ...). Needs [WordLadder] (and the Word Race and Letter Grid lists)
 * to be loaded.
 */
class LadderMatch(
    options: LadderOptions = LadderOptions(),
    val marks: List<Char> = listOf('h', 'g'),
    private val random: () -> Double = { Random.nextDouble() },
) {
    val length = options.length
    val timer = options.timer
    private val picks = HashMap<Char, String?>().apply { marks.forEach { put(it, null) } }
    private val chain = mutableListOf<String>()
    private var startOwner: Char? = null
    private var targetOwner: Char? = null

    var turn: Char? = null
        private set
    var turnEndsAt = 0L
        private set
    var over = false
        private set
    var winner: Char? = null
        private set
    var reason: String? = null
        private set

    val revealed get() = chain.isNotEmpty()
    val words: List<String> get() = chain.toList()

    private fun other(mark: Char) = marks.first { it != mark }
    private fun turnEnd(now: Long) = if (timer > 0) now + timer * 1000L else 0L

    /** The real, unused words one letter away from the last word in the chain. */
    fun legalMoves(): List<String> {
        val last = chain.last()
        return last.indices.flatMap { i ->
            ('A'..'Z').map { last.substring(0, i) + it + last.substring(i + 1) }
        }.filter { it != last && it !in chain && WordLadder.isRealWord(it) }
    }

    private fun endIfStuck(): TurnResult? {
        if (legalMoves().isNotEmpty()) return null
        over = true
        winner = other(turn!!)
        reason = "stuck"
        turnEndsAt = 0
        return TurnResult(again = false, over = true, winner = winner, reason = "stuck")
    }

    /**
     * [mark] secretly picks [word]. Once both picks are in, [revealed] turns true and the first
     * turn starts at [now]. Returns the result of that (over when the first mover has no move).
     */
    fun setPick(mark: Char, word: String, now: Long = 0): TurnResult {
        require(!revealed) { "The game has started." }
        val w = word.uppercase()
        require(w.length == length) { "Pick a $length-letter word." }
        require(WordLadder.isWord(w) && w !in WordLadder.blocklist) { "Not a word we can use. Pick another." }
        val rival = picks[other(mark)]
        if (rival != null) {
            require(rival != w) { "Pick a different word." }
            // Names only the sender's own word, so it never gives the other pick away.
            require(WordLadder.pathExists(w, rival)) { "No way from $w to the other word yet. Pick another." }
        }
        picks[mark] = w
        if (rival == null) return TurnResult(again = false)
        startOwner = if (random() < 0.5) marks[0] else marks[1]
        targetOwner = other(startOwner!!)
        chain.add(picks.getValue(startOwner!!)!!)
        turn = targetOwner
        turnEndsAt = turnEnd(now)
        return endIfStuck() ?: TurnResult(again = false)
    }

    /** [mark] changes one letter into [word]. */
    fun play(mark: Char, word: String, now: Long = 0): TurnResult {
        require(revealed) { "Waiting for both words." }
        require(!over) { "The game is over." }
        require(turn == mark) { "Not your turn." }
        val w = word.uppercase()
        val last = chain.last()
        require(w.length == last.length && w.indices.count { w[it] != last[it] } == 1) { "Change one letter" }
        require(WordLadder.isRealWord(w)) { "Not a word" }
        require(w !in chain) { "Already used" }
        chain.add(w)
        if (w == picks[targetOwner]) {
            over = true
            winner = mark
            reason = "target"
            turnEndsAt = 0
            return TurnResult(again = false, over = true, winner = mark, reason = "target")
        }
        turn = other(mark)
        turnEndsAt = turnEnd(now)
        return endIfStuck() ?: TurnResult(again = false)
    }

    /** Call when the clock reaches [turnEndsAt]: the turn passes. True if it did. */
    fun tick(now: Long): Boolean {
        if (over || turnEndsAt == 0L || now < turnEndsAt) return false
        turn = other(turn!!)
        turnEndsAt = turnEnd(now)
        return true
    }

    /** The game as [mark] sees it: the same JSON as view() in wordladder.js. Only [mark]'s own pick before the reveal. */
    fun view(mark: Char): JSONObject {
        fun me(m: Char?) = if (m == null) JSONObject.NULL else if (m == mark) "me" else "opponent"
        return JSONObject()
            .put("length", length)
            .put("timer", timer)
            .put("phase", if (over) "over" else if (revealed) "playing" else "picking")
            .put("myPick", picks[mark] ?: JSONObject.NULL)
            .put("opponentPicked", picks[other(mark)] != null)
            .put("start", if (revealed) chain[0] else JSONObject.NULL)
            .put("target", if (revealed) picks[targetOwner] else JSONObject.NULL)
            .put("startOwner", if (revealed) me(startOwner) else JSONObject.NULL)
            .put("targetOwner", if (revealed) me(targetOwner) else JSONObject.NULL)
            .put("chain", JSONArray(chain))
            .put("turn", if (revealed && !over) me(turn) else JSONObject.NULL)
            .put("turnEndsAt", turnEndsAt)
            .put("winner", if (over) me(winner) else JSONObject.NULL)
            .put("reason", reason ?: JSONObject.NULL)
    }
}
