package com.arcade.sudoku.game.ludo

import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Ludo rules, matching the server (backend/cloudflare/src/ludo.js). A token's progress: -1 in
 * the yard, 0-50 round the 52-square ring (a colour starts on ring square colour × 13), 51-55
 * up its home column, 56 home.
 */
object Ludo {
    const val HOME = 56
    const val RING = 52
    val SAFE = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    /** Colours for [n] players: opposite corners for two. */
    fun coloursFor(n: Int) = if (n == 2) listOf(0, 2) else listOf(0, 1, 2, 3).take(n)

    /** The ring square a token of [colour] at [progress] stands on, or null off the ring. */
    fun square(colour: Int, progress: Int) = if (progress in 0..50) (colour * 13 + progress) % RING else null

    fun movable(tokens: List<Int>, roll: Int) = tokens.indices.filter {
        val p = tokens[it]
        if (p == -1) roll == 6 else p != HOME && p + roll <= HOME
    }

    /**
     * The board's 15 × 15 squares: the ring from colour 0's start going round clockwise, each
     * colour's home column, its yard corner and the centre.
     */
    val ringCells: List<Pair<Int, Int>> = buildList {
        for (x in 1..5) add(x to 6)
        for (y in 5 downTo 0) add(6 to y)
        add(7 to 0)
        for (y in 0..5) add(8 to y)
        for (x in 9..14) add(x to 6)
        add(14 to 7)
        for (x in 14 downTo 9) add(x to 8)
        for (y in 9..14) add(8 to y)
        add(7 to 14)
        for (y in 14 downTo 9) add(6 to y)
        for (x in 5 downTo 0) add(x to 8)
        add(0 to 7)
        add(0 to 6)
    }

    /** Home column square [k] (0-4) of [colour], from the ring towards the centre. */
    fun homeCell(colour: Int, k: Int): Pair<Int, Int> = when (colour) {
        0 -> (1 + k) to 7
        1 -> 7 to (1 + k)
        2 -> (13 - k) to 7
        else -> 7 to (13 - k)
    }

    /** The top-left square of [colour]'s 6 × 6 yard. */
    fun yardCorner(colour: Int): Pair<Int, Int> = when (colour) {
        0 -> 0 to 0
        1 -> 9 to 0
        2 -> 9 to 9
        else -> 0 to 9
    }

    /** Where token [index] of [colour] at [progress] is drawn, in board squares (centres). */
    fun cellOf(colour: Int, progress: Int, index: Int): Pair<Float, Float> = when {
        progress < 0 -> {
            val (x, y) = yardCorner(colour)
            (x + 1.75f + (index % 2) * 2.5f) to (y + 1.75f + (index / 2) * 2.5f)
        }
        progress <= 50 -> ringCells[square(colour, progress)!!].let { (x, y) -> x + 0.5f to y + 0.5f }
        progress < HOME -> homeCell(colour, progress - 51).let { (x, y) -> x + 0.5f to y + 0.5f }
        // Home: the four tokens sit round the centre, on their colour's side.
        else -> when (colour) {
            0 -> 6.6f to 7.5f
            1 -> 7.5f to 6.6f
            2 -> 8.4f to 7.5f
            else -> 7.5f to 8.4f
        }
    }
}

/** One Ludo game. Each result says whose turn is next. */
class LudoMatch(val marks: List<Char>, private val random: Random = Random.Default) : TurnMatch {
    val colours: Map<Char, Int> = marks.zip(Ludo.coloursFor(marks.size)).toMap()
    val tokens: Map<Char, MutableList<Int>> = marks.associateWith { MutableList(4) { -1 } }
    var roll: Int? = null
        private set
    private var sixes = 0
    private var last: JSONObject? = null
    private var lastMover: Char? = null

    private fun next(mark: Char) = marks[(marks.indexOf(mark) + 1) % marks.size]

    override fun play(mark: Char, move: JSONObject): TurnResult {
        if (move.optBoolean("roll")) {
            require(roll == null) { "Move a token first." }
            val r = random.nextInt(1, 7)
            sixes = if (r == 6) sixes + 1 else 0
            lastMover = mark
            val l = JSONObject().put("mark", mark.toString()).put("roll", r)
            last = l
            if (sixes == 3) {
                sixes = 0
                l.put("thirdSix", true)
                return TurnResult(again = false, turn = next(mark))
            }
            if (Ludo.movable(tokens.getValue(mark), r).isEmpty()) {
                sixes = 0
                l.put("stuck", true)
                return TurnResult(again = r == 6, turn = if (r == 6) mark else next(mark))
            }
            roll = r
            return TurnResult(again = true, turn = mark)
        }
        val r = roll ?: throw IllegalArgumentException("Roll the die first.")
        val i = move.optInt("token", -1)
        val mine = tokens.getValue(mark)
        require(i in Ludo.movable(mine, r)) { "That token can't move." }
        val from = mine[i]
        val to = if (from == -1) 0 else from + r
        mine[i] = to
        roll = null
        val captured = JSONArray()
        val sq = Ludo.square(colours.getValue(mark), to)
        if (sq != null && sq !in Ludo.SAFE) {
            for (m in marks) {
                if (m == mark) continue
                val theirs = tokens.getValue(m)
                for (j in theirs.indices) {
                    if (Ludo.square(colours.getValue(m), theirs[j]) == sq) {
                        theirs[j] = -1
                        captured.put(m.toString())
                    }
                }
            }
        }
        last = JSONObject().put("mark", mark.toString()).put("roll", r).put("token", i).put("from", from).put("to", to).put("captured", captured)
        if (mine.all { it == Ludo.HOME }) return TurnResult(again = false, over = true, winner = mark, reason = "home")
        if (r == 6) return TurnResult(again = true, turn = mark)
        sixes = 0
        return TurnResult(again = false, turn = next(mark))
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject {
        fun who(m: Char) = if (m == mark) 'm' else seats[m] ?: 'o'
        val r = roll
        return JSONObject()
            .put("colours", JSONObject().apply { for (m in marks) put(who(m).toString(), colours.getValue(m)) })
            .put("tokens", JSONObject().apply { for (m in marks) put(who(m).toString(), JSONArray(tokens.getValue(m))) })
            .put("roll", r ?: JSONObject.NULL)
            .put("movable", JSONArray(if (r != null && lastMover == mark) Ludo.movable(tokens.getValue(mark), r) else emptyList()))
            .put(
                "lastMove",
                last?.let {
                    val by = it.getString("mark")[0]
                    val cap = it.optJSONArray("captured")
                    JSONObject().put("by", if (by == mark) "me" else "opponent").put("who", who(by).toString())
                        .put("roll", it.optInt("roll"))
                        .put("token", if (it.has("token")) it.optInt("token") else JSONObject.NULL)
                        .put("from", if (it.has("from")) it.optInt("from") else JSONObject.NULL)
                        .put("to", if (it.has("to")) it.optInt("to") else JSONObject.NULL)
                        .put("captured", JSONArray(cap?.let { a -> (0 until a.length()).map { k -> who(a.optString(k)[0]).toString() } }.orEmpty()))
                        .put("stuck", it.optBoolean("stuck")).put("thirdSix", it.optBoolean("thirdSix"))
                } ?: JSONObject.NULL,
            )
    }
}

/**
 * The computer: rolls, then prefers a capture, then getting home, then bringing a token out,
 * then landing safe, else moves the token furthest along.
 */
class LudoComputer {
    fun move(match: LudoMatch, mark: Char): JSONObject {
        val r = match.roll ?: return JSONObject().put("roll", true)
        val mine = match.tokens.getValue(mark)
        val colour = match.colours.getValue(mark)
        val options = Ludo.movable(mine, r)
        val best = options.maxBy { i ->
            val from = mine[i]
            val to = if (from == -1) 0 else from + r
            val sq = Ludo.square(colour, to)
            val captures = sq != null && sq !in Ludo.SAFE && match.marks.any { m ->
                m != mark && match.tokens.getValue(m).any { Ludo.square(match.colours.getValue(m), it) == sq }
            }
            when {
                captures -> 1000
                to == Ludo.HOME -> 900
                from == -1 -> 800
                sq != null && sq in Ludo.SAFE -> 500 + to
                else -> to
            }
        }
        return JSONObject().put("token", best)
    }
}

/** The last move: who ('m' you), the roll, and the token moved (or why none moved). */
data class LudoMove(val who: Char, val roll: Int, val token: Int?, val from: Int?, val to: Int?, val captured: List<Char>, val stuck: Boolean, val thirdSix: Boolean)

/** A Ludo game as one player sees it: the "turns" JSON from [LudoMatch.view] or the server. */
data class LudoView(
    /** Each player's colour (0-3) and tokens, by letter ('m' you). */
    val colours: Map<Char, Int>,
    val tokens: Map<Char, List<Int>>,
    /** Your roll waiting for a token to move, and which tokens can. */
    val roll: Int?,
    val movable: List<Int>,
    val lastMove: LudoMove?,
) {
    companion object {
        private fun ints(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.optInt(i) } }.orEmpty()

        fun from(json: JSONObject): LudoView {
            val c = json.optJSONObject("colours")
            val t = json.optJSONObject("tokens")
            val last = json.optJSONObject("lastMove")
            return LudoView(
                colours = c?.keys()?.asSequence()?.associate { it[0] to c.optInt(it) }.orEmpty(),
                tokens = t?.keys()?.asSequence()?.associate { it[0] to ints(t.optJSONArray(it)) }.orEmpty(),
                roll = if (json.isNull("roll")) null else json.optInt("roll"),
                movable = ints(json.optJSONArray("movable")),
                lastMove = last?.let {
                    LudoMove(
                        it.optString("who", "o").ifEmpty { "o" }[0], it.optInt("roll"),
                        if (it.isNull("token")) null else it.optInt("token"),
                        if (it.isNull("from")) null else it.optInt("from"),
                        if (it.isNull("to")) null else it.optInt("to"),
                        it.optJSONArray("captured")?.let { a -> (0 until a.length()).map { k -> a.optString(k)[0] } }.orEmpty(),
                        it.optBoolean("stuck"), it.optBoolean("thirdSix"),
                    )
                },
            )
        }
    }
}
