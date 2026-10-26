package com.arcade.sudoku.game.balloon

import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Balloon Words, matching the server (backend/cloudflare/src/balloon.js): one player picks a
 * secret word, the others guess letters in turn before [BALLOONS] balloons pop. Everyone picks
 * once; the most points wins.
 */
class BalloonMatch(val marks: List<Char>) : TurnMatch {
    private var round = 1
    private var picker = 0
    var phase = "pick"
        private set
    private var word: String? = null
    private val guessed = mutableListOf<Char>()
    private var misses = 0
    val scores = marks.associateWith { 0 }.toMutableMap()
    private var last: JSONObject? = null
    private var lastRound: JSONObject? = null

    override val firstMark: Char get() = marks[0]

    private fun pickerMark() = marks[picker]

    private fun nextGuesser(mark: Char): Char {
        var i = marks.indexOf(mark)
        repeat(marks.size) {
            i = (i + 1) % marks.size
            if (i != picker) return marks[i]
        }
        return marks[(picker + 1) % marks.size]
    }

    private fun endRound(solvedBy: Char?): TurnResult {
        lastRound = JSONObject().put("word", word).put("picker", pickerMark().toString())
            .put("solvedBy", solvedBy?.toString() ?: JSONObject.NULL).put("popped", solvedBy == null)
        if (round >= marks.size) {
            val ranked = marks.sortedByDescending { scores.getValue(it) }
            val tie = ranked.size > 1 && scores.getValue(ranked[0]) == scores.getValue(ranked[1])
            phase = "over"
            return TurnResult(again = false, over = true, winner = if (tie) null else ranked[0], reason = if (tie) "draw" else "points")
        }
        round++
        picker = (picker + 1) % marks.size
        phase = "pick"
        word = null
        guessed.clear()
        misses = 0
        return TurnResult(again = false, turn = pickerMark())
    }

    override fun play(mark: Char, move: JSONObject): TurnResult {
        if (phase == "pick") {
            require(mark == pickerMark()) { "It's not your turn to pick." }
            val w = move.optString("word").uppercase().replace(Regex("\\s+"), "")
            require(Regex("[A-Z]{3,12}").matches(w)) { "Pick a word of 3 to 12 letters (A to Z only)." }
            word = w
            phase = "guess"
            guessed.clear()
            misses = 0
            last = JSONObject().put("mark", mark.toString()).put("picked", true)
            return TurnResult(again = false, turn = nextGuesser(mark))
        }
        require(phase == "guess") { "The game is over." }
        require(mark != pickerMark()) { "You picked this word." }
        val s = move.optString("letter").uppercase()
        require(s.length == 1 && s[0] in 'A'..'Z') { "Pick a letter." }
        val letter = s[0]
        require(letter !in guessed) { "That letter was already guessed." }
        guessed += letter
        val w = word!!
        val hits = w.count { it == letter }
        last = JSONObject().put("mark", mark.toString()).put("letter", letter.toString()).put("hits", hits)
        if (hits > 0) {
            scores[mark] = scores.getValue(mark) + hits
            if (w.all { it in guessed }) {
                scores[mark] = scores.getValue(mark) + 3
                return endRound(mark)
            }
        } else {
            misses++
            if (misses >= BALLOONS) {
                scores[pickerMark()] = scores.getValue(pickerMark()) + 5
                return endRound(null)
            }
        }
        return TurnResult(again = false, turn = nextGuesser(mark))
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject {
        fun who(m: Char) = if (m == mark) 'm' else seats[m] ?: 'o'
        val w = word.orEmpty()
        return JSONObject()
            .put("phase", phase)
            .put("picker", if (phase == "over") JSONObject.NULL else who(pickerMark()).toString())
            .put("pattern", w.map { if (it in guessed) it else '_' }.joinToString(""))
            .put("word", if (pickerMark() == mark && phase == "guess") w else JSONObject.NULL)
            .put("guessed", JSONArray(guessed.map { it.toString() }))
            .put("misses", misses)
            .put("balloons", BALLOONS)
            .put("scores", JSONObject().apply { for (m in marks) put(who(m).toString(), scores.getValue(m)) })
            .put("roundNo", round)
            .put("rounds", marks.size)
            .put(
                "lastMove",
                last?.let {
                    JSONObject().put("who", who(it.getString("mark")[0]).toString())
                        .put("letter", it.opt("letter") ?: JSONObject.NULL)
                        .put("hits", if (it.has("hits")) it.getInt("hits") else JSONObject.NULL)
                        .put("picked", it.optBoolean("picked"))
                } ?: JSONObject.NULL,
            )
            .put(
                "lastRound",
                lastRound?.let {
                    JSONObject().put("word", it.getString("word")).put("picker", who(it.getString("picker")[0]).toString())
                        .put("solvedBy", if (it.isNull("solvedBy")) JSONObject.NULL else who(it.getString("solvedBy")[0]).toString())
                        .put("popped", it.getBoolean("popped"))
                } ?: JSONObject.NULL,
            )
    }

    companion object {
        const val BALLOONS = 7

        /**
         * The computer's guess: the letter most common among [words] that still fit [pattern]
         * (given [guessed]), or the most common letter in English when none fit.
         */
        fun bestLetter(pattern: String, guessed: Set<Char>, words: Collection<String>): Char {
            val wrong = guessed.filter { it !in pattern }.toSet()
            val fits = words.asSequence().map { it.uppercase() }.filter { w ->
                w.length == pattern.length && w.indices.all { i ->
                    val p = pattern[i]
                    if (p == '_') w[i] !in guessed else w[i] == p
                } && w.none { it in wrong }
            }.take(2000).toList()
            val counts = mutableMapOf<Char, Int>()
            for (w in fits) for (c in w.toSet()) if (c !in guessed) counts[c] = (counts[c] ?: 0) + 1
            return counts.maxByOrNull { it.value }?.key ?: "EARIOTNSLCUDPMHGBFYWKVXZJQ".first { it !in guessed }
        }
    }
}

/** Balloon Words as one player sees it: the "turns" JSON from [BalloonMatch.view] or the server. */
data class BalloonView(
    /** "pick", "guess" or "over". */
    val phase: String,
    /** Who picks this round ('m' you). */
    val picker: Char?,
    /** The word with '_' for letters not found yet. */
    val pattern: String,
    /** The word itself, for its picker only. */
    val word: String?,
    val guessed: List<Char>,
    val misses: Int,
    val balloons: Int,
    val scores: Map<Char, Int>,
    val roundNo: Int,
    val rounds: Int,
    val lastLetter: Pair<Char, Int>?,
    val lastWho: Char?,
    /** The last round: its word, who picked it, who finished it (null when the balloons popped). */
    val lastRound: Triple<String, Char, Char?>?,
) {
    companion object {
        fun from(json: JSONObject): BalloonView {
            val g = json.optJSONArray("guessed")
            val sc = json.optJSONObject("scores")
            val lm = json.optJSONObject("lastMove")
            val lr = json.optJSONObject("lastRound")
            return BalloonView(
                phase = json.optString("phase", "pick"),
                picker = if (json.isNull("picker")) null else json.optString("picker").firstOrNull(),
                pattern = json.optString("pattern"),
                word = if (json.isNull("word")) null else json.optString("word"),
                guessed = g?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).firstOrNull() } }.orEmpty(),
                misses = json.optInt("misses"),
                balloons = json.optInt("balloons", BalloonMatch.BALLOONS),
                scores = sc?.keys()?.asSequence()?.associate { it[0] to sc.optInt(it) }.orEmpty(),
                roundNo = json.optInt("roundNo", 1),
                rounds = json.optInt("rounds", 2),
                lastLetter = lm?.takeIf { !it.isNull("letter") }?.let { it.optString("letter")[0] to it.optInt("hits") },
                lastWho = lm?.optString("who")?.firstOrNull(),
                lastRound = lr?.let { Triple(it.optString("word"), it.optString("picker", "o")[0], if (it.isNull("solvedBy")) null else it.optString("solvedBy")[0]) },
            )
        }
    }
}
