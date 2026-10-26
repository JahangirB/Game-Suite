package com.arcade.sudoku.game.dominoes

import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** A domino tile: its two halves, 0 to 6. */
data class Tile(val a: Int, val b: Int) {
    val pips get() = a + b
    val isDouble get() = a == b
    fun flipped() = Tile(b, a)
    fun toJson(): JSONArray = JSONArray().put(a).put(b)
}

/**
 * The host's choices for a Dominoes match, as on the server (backend/cloudflare/src/dominoes.js):
 * [teams] (four players, partners opposite), [draw] (draw from the boneyard, else pass), [target] points.
 */
data class DominoOptions(val teams: Boolean = false, val draw: Boolean = true, val target: Int = 100) {
    fun toJson(): JSONObject = JSONObject().put("teams", teams).put("draw", draw).put("target", target)

    companion object {
        val TARGETS = listOf(50, 100, 150)

        fun from(json: JSONObject?) = DominoOptions(
            teams = json?.optBoolean("teams") ?: false,
            draw = json?.optBoolean("draw", true) ?: true,
            target = json?.optInt("target", 100)?.takeIf { it in TARGETS } ?: 100,
        )
    }
}

object Dominoes {
    fun fullSet() = (0..6).flatMap { a -> (a..6).map { b -> Tile(a, b) } }

    /** The ends [tile] can go on: "left", "right" (just "right" on an empty line), or none. */
    fun fits(line: List<Tile>, tile: Tile): List<String> {
        if (line.isEmpty()) return listOf("right")
        val left = line.first().a
        val right = line.last().b
        return listOfNotNull(
            "left".takeIf { tile.a == left || tile.b == left },
            "right".takeIf { tile.a == right || tile.b == right },
        )
    }

    fun pips(hand: List<Tile>) = hand.sumOf { it.pips }

    /** [line] with [tile] put on [end], turned so its matching half touches the line. */
    fun place(line: List<Tile>, tile: Tile, end: String): List<Tile> = when {
        line.isEmpty() -> listOf(tile)
        end == "left" -> listOf(if (tile.b == line.first().a) tile else tile.flipped()) + line
        else -> line + (if (tile.a == line.last().b) tile else tile.flipped())
    }
}

/** One Dominoes match (rounds until a side reaches the target), matching dominoes.js. */
class DominoMatch(
    val marks: List<Char>,
    options: DominoOptions,
    private val random: Random = Random.Default,
) : TurnMatch {
    val teams = options.teams && marks.size == 4
    val draw = options.draw
    val target = options.target
    var hands: MutableMap<Char, MutableList<Tile>> = mutableMapOf()
        private set
    var boneyard: MutableList<Tile> = mutableListOf()
        private set
    var line: MutableList<Tile> = mutableListOf()
        private set
    private var passes = 0
    private var last: JSONObject? = null
    private var lastMover: Char = marks.first()
    var roundNo = 0
        private set
    val scores = mutableMapOf<String, Int>()
    var lastRound: JSONObject? = null
        private set

    /** Who plays first: the holder of the highest double. */
    override val firstMark: Char

    init {
        for (m in marks) scores[sideOf(m)] = 0
        firstMark = deal(null)
    }

    /** The side [mark] scores for: its team ("A" or "B"), or the mark itself without teams. */
    fun sideOf(mark: Char): String = if (teams) "AB"[marks.indexOf(mark) % 2].toString() else mark.toString()

    fun sameSide(a: Char, b: Char) = sideOf(a) == sideOf(b)

    private fun deal(starter: Char?): Char {
        val tiles = Dominoes.fullSet().shuffled(random).toMutableList()
        val each = if (draw && marks.size > 2) 5 else 7
        hands = marks.associateWith { m -> MutableList(each) { tiles.removeAt(0) } }.toMutableMap()
        boneyard = if (draw) tiles else mutableListOf()
        line = mutableListOf()
        passes = 0
        last = null
        roundNo++
        if (starter != null) return starter
        return marks.maxBy { m -> hands.getValue(m).maxOf { (if (it.isDouble) 100 else 0) + it.pips } }
    }

    fun canPlay(mark: Char) = hands.getValue(mark).any { Dominoes.fits(line, it).isNotEmpty() }

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val hand = hands.getValue(mark)
        if (move.optBoolean("draw")) {
            require(draw) { "This game has no drawing: pass instead." }
            require(!canPlay(mark)) { "You have a tile you can play." }
            require(boneyard.isNotEmpty()) { "The boneyard is empty: pass instead." }
            hand += boneyard.removeAt(boneyard.lastIndex)
            last = JSONObject().put("mark", mark.toString()).put("drew", true)
            return TurnResult(again = true)
        }
        if (move.optBoolean("pass")) {
            require(!canPlay(mark)) { "You have a tile you can play." }
            require(!draw || boneyard.isEmpty()) { "Draw from the boneyard first." }
            passes++
            lastMover = mark
            last = JSONObject().put("mark", mark.toString()).put("passed", true)
            if (passes >= marks.size) return endRound(null)
            return TurnResult(again = false)
        }
        val index = move.optInt("tile", -1)
        require(index in hand.indices) { "Pick one of your tiles." }
        val tile = hand[index]
        val ends = Dominoes.fits(line, tile)
        require(ends.isNotEmpty()) { "That tile doesn't match either end." }
        val end = move.optString("end").takeIf { it in ends } ?: ends.first()
        line = Dominoes.place(line, tile, end).toMutableList()
        hand.removeAt(index)
        passes = 0
        lastMover = mark
        last = JSONObject().put("mark", mark.toString()).put("tile", tile.toJson()).put("end", end)
        if (hand.isEmpty()) return endRound(mark)
        return TurnResult(again = false)
    }

    private fun endRound(outWinner: Char?): TurnResult {
        var winner = outWinner
        val blocked = winner == null
        if (blocked) {
            val totals = marks.groupBy { sideOf(it) }.mapValues { (_, ms) -> ms.sumOf { Dominoes.pips(hands.getValue(it)) } }
            val ranked = totals.entries.sortedBy { it.value }
            if (ranked.size < 2 || ranked[0].value != ranked[1].value) {
                winner = marks.filter { sideOf(it) == ranked[0].key }.minBy { Dominoes.pips(hands.getValue(it)) }
            }
        }
        var points = 0
        if (winner != null) {
            points = marks.filter { !sameSide(it, winner) }.sumOf { Dominoes.pips(hands.getValue(it)) }
            scores[sideOf(winner)] = scores.getValue(sideOf(winner)) + points
        }
        lastRound = JSONObject().put("winner", winner?.toString() ?: JSONObject.NULL).put("points", points).put("blocked", blocked)
            .put("hands", JSONObject().apply { for (m in marks) put(m.toString(), JSONArray(hands.getValue(m).map { it.toJson() })) })
        if (winner != null && scores.getValue(sideOf(winner)) >= target) {
            return TurnResult(again = false, over = true, winner = winner, reason = "points")
        }
        val next = winner ?: marks[(marks.indexOf(lastMover) + 1) % marks.size]
        return TurnResult(again = false, turn = deal(next))
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject {
        fun who(m: Char) = if (m == mark) 'm' else seats[m] ?: 'o'
        fun tiles(list: List<Tile>) = JSONArray(list.map { it.toJson() })
        val round = lastRound
        return JSONObject()
            .put("line", tiles(line))
            .put("hand", tiles(hands[mark].orEmpty()))
            .put("counts", JSONObject().apply { for (m in marks) put(who(m).toString(), hands.getValue(m).size) })
            .put("scores", JSONObject().apply { for (m in marks) put(who(m).toString(), scores.getValue(sideOf(m))) })
            .put("partners", JSONArray(if (teams) marks.filter { it != mark && sameSide(it, mark) }.map { who(it).toString() } else emptyList()))
            .put("boneyard", boneyard.size)
            .put("teams", teams)
            .put("draw", draw)
            .put("target", target)
            .put("roundNo", roundNo)
            .put(
                "lastMove",
                last?.let {
                    val by = it.getString("mark")[0]
                    JSONObject().put("by", if (by == mark) "me" else "opponent").put("who", who(by).toString())
                        .put("tile", it.opt("tile") ?: JSONObject.NULL).put("end", it.opt("end") ?: JSONObject.NULL)
                        .put("drew", it.optBoolean("drew")).put("passed", it.optBoolean("passed"))
                } ?: JSONObject.NULL,
            )
            .put(
                "lastRound",
                round?.let { r ->
                    val hs = r.getJSONObject("hands")
                    JSONObject()
                        .put("winner", if (r.isNull("winner")) JSONObject.NULL else who(r.getString("winner")[0]).toString())
                        .put("points", r.getInt("points")).put("blocked", r.getBoolean("blocked"))
                        .put("hands", JSONObject().apply { for (m in marks) put(who(m).toString(), hs.getJSONArray(m.toString())) })
                } ?: JSONObject.NULL,
            )
    }
}

/**
 * The computer opponent: plays the heaviest tile it can (doubles first, to get rid of them),
 * else draws or passes. Returns the move to send.
 */
class DominoComputer {
    fun move(match: DominoMatch, mark: Char): JSONObject {
        val hand = match.hands.getValue(mark)
        val best = hand.indices.filter { Dominoes.fits(match.line, hand[it]).isNotEmpty() }
            .maxByOrNull { (if (hand[it].isDouble) 20 else 0) + hand[it].pips }
        return when {
            best != null -> JSONObject().put("tile", best).put("end", Dominoes.fits(match.line, hand[best]).last())
            match.draw && match.boneyard.isNotEmpty() -> JSONObject().put("draw", true)
            else -> JSONObject().put("pass", true)
        }
    }
}

/** The last move: who made it ('m' you, else 'o', 'p', 'q'), and the tile played or a draw or pass. */
data class DominoMove(val who: Char, val tile: Tile?, val end: String?, val drew: Boolean, val passed: Boolean)

/** The round just finished: its winner (null on a drawn block), the points and everyone's leftover tiles. */
data class DominoRound(val winner: Char?, val points: Int, val blocked: Boolean, val hands: Map<Char, List<Tile>>)

/** A Dominoes match as one player sees it: the "turns" JSON from [DominoMatch.view] or the server. */
data class DominoView(
    val line: List<Tile>,
    val hand: List<Tile>,
    /** Tiles in each hand and each player's (or team's) score, by letter ('m' you). */
    val counts: Map<Char, Int>,
    val scores: Map<Char, Int>,
    /** With teams: your partner's letter. */
    val partners: List<Char>,
    val boneyard: Int,
    val teams: Boolean,
    val draw: Boolean,
    val target: Int,
    val roundNo: Int,
    val lastMove: DominoMove?,
    val lastRound: DominoRound?,
) {
    /** Whether any tile in your hand fits the line. */
    val canPlay get() = hand.any { Dominoes.fits(line, it).isNotEmpty() }

    companion object {
        private fun tile(a: JSONArray?) = a?.let { Tile(it.optInt(0), it.optInt(1)) }
        private fun tiles(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { tile(a.optJSONArray(it)) }
        private fun ints(o: JSONObject?) = o?.keys()?.asSequence()?.associate { it[0] to o.optInt(it) }.orEmpty()

        fun from(json: JSONObject): DominoView {
            val last = json.optJSONObject("lastMove")
            val round = json.optJSONObject("lastRound")
            return DominoView(
                line = tiles(json.optJSONArray("line")),
                hand = tiles(json.optJSONArray("hand")),
                counts = ints(json.optJSONObject("counts")),
                scores = ints(json.optJSONObject("scores")),
                partners = json.optJSONArray("partners")?.let { a -> (0 until a.length()).map { a.optString(it)[0] } }.orEmpty(),
                boneyard = json.optInt("boneyard"),
                teams = json.optBoolean("teams"),
                draw = json.optBoolean("draw", true),
                target = json.optInt("target", 100),
                roundNo = json.optInt("roundNo", 1),
                lastMove = last?.let {
                    DominoMove(it.optString("who", "o").ifEmpty { "o" }[0],
                        tile(it.optJSONArray("tile")), if (it.isNull("end")) null else it.optString("end"),
                        it.optBoolean("drew"), it.optBoolean("passed"))
                },
                lastRound = round?.let { r ->
                    val hs = r.optJSONObject("hands")
                    DominoRound(
                        winner = if (r.isNull("winner")) null else r.optString("winner")[0],
                        points = r.optInt("points"),
                        blocked = r.optBoolean("blocked"),
                        hands = hs?.keys()?.asSequence()?.associate { it[0] to tiles(hs.optJSONArray(it)) }.orEmpty(),
                    )
                },
            )
        }
    }
}
