package com.pippop.game.golf

import com.pippop.game.turns.TurnMatch
import com.pippop.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mini Golf's turns and strokes, matching the server (backend/cloudflare/src/golf.js). A move
 * is a shot the player's phone already worked out: { angle, power, x, y, holed }.
 */
class GolfMatch(val marks: List<Char>) : TurnMatch {
    var hole = 0
        private set
    val strokes = marks.associateWith { mutableListOf(0) }
    private val balls = marks.associateWith<Char, Pt?> { null }.toMutableMap()
    private val done = marks.associateWith { false }.toMutableMap()
    private var last: JSONObject? = null
    private var shots = 0

    override val firstMark: Char get() = marks[0]

    fun total(m: Char) = strokes.getValue(m).sum()

    private fun nextOut(mark: Char): Char? {
        var i = marks.indexOf(mark)
        repeat(marks.size) {
            i = (i + 1) % marks.size
            if (done[marks[i]] != true) return marks[i]
        }
        return null
    }

    override fun play(mark: Char, move: JSONObject): TurnResult {
        require(hole < HOLES) { "The game is over." }
        require(done[mark] != true) { "You're in the hole already." }
        val angle = move.optDouble("angle")
        val power = move.optDouble("power")
        val x = move.optDouble("x")
        val y = move.optDouble("y")
        require(angle.isFinite() && power > 0 && power <= 1 && x in 0.0..2.0 && y in 0.0..2.0) { "That shot is not valid." }
        val holed = move.optBoolean("holed")
        val from = balls[mark]
        val mine = strokes.getValue(mark)
        mine[hole] = mine[hole] + 1
        balls[mark] = Pt(x, y)
        if (holed) {
            done[mark] = true
        } else if (mine[hole] >= Golf.MAX_STROKES) {
            done[mark] = true
            mine[hole] = Golf.MAX_STROKES + 1
        }
        shots++
        last = JSONObject().put("mark", mark.toString()).put("hole", hole).put("from", from?.toJson() ?: JSONObject.NULL)
            .put("angle", angle).put("power", power).put("to", Pt(x, y).toJson()).put("holed", holed).put("seq", shots)
        nextOut(mark)?.let { return TurnResult(again = false, turn = it) }
        if (hole + 1 >= HOLES) {
            hole = HOLES
            val ranked = marks.sortedBy { total(it) }
            val tie = ranked.size > 1 && total(ranked[0]) == total(ranked[1])
            return TurnResult(again = false, over = true, winner = if (tie) null else ranked[0], reason = if (tie) "draw" else "strokes")
        }
        hole++
        for (m in marks) {
            strokes.getValue(m).add(0)
            balls[m] = null
            done[m] = false
        }
        return TurnResult(again = false, turn = marks[hole % marks.size])
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject {
        fun who(m: Char) = (if (m == mark) 'm' else seats[m] ?: 'o').toString()
        fun each(f: (Char) -> Any) = JSONObject().apply { for (m in marks) put(who(m), f(m)) }
        return JSONObject().put(
            "golf",
            JSONObject()
                .put("hole", minOf(hole, HOLES - 1))
                .put("holes", HOLES)
                .put("maxStrokes", Golf.MAX_STROKES)
                .put("strokes", each { JSONArray(strokes.getValue(it)) })
                .put("balls", each { balls[it]?.toJson() ?: JSONObject.NULL })
                .put("done", each { done.getValue(it) })
                .put(
                    "last",
                    last?.let { l ->
                        JSONObject(l.toString()).apply { remove("mark") }.put("who", who(l.getString("mark")[0]))
                    } ?: JSONObject.NULL,
                ),
        )
    }

    companion object {
        val HOLES = Golf.HOLES.size
    }
}

private fun Pt.toJson() = JSONArray().put(x).put(y)

/** The last shot played: by [who] on [hole], from [from] (null: the tee) to [to]. */
data class GolfLastShot(val who: Char, val hole: Int, val from: Pt?, val angle: Double, val power: Double, val to: Pt, val holed: Boolean, val seq: Int)

/** Mini Golf as one player sees it: everyone's strokes per hole, balls ('m' is you) and the last shot. */
data class GolfView(
    val hole: Int,
    val holes: Int,
    val strokes: Map<Char, List<Int>>,
    val balls: Map<Char, Pt?>,
    val done: Map<Char, Boolean>,
    val last: GolfLastShot?,
) {
    fun total(who: Char) = strokes[who].orEmpty().sum()

    companion object {
        private fun pt(a: JSONArray?) = a?.let { Pt(it.optDouble(0), it.optDouble(1)) }

        fun from(turns: JSONObject): GolfView {
            val g = turns.getJSONObject("golf")
            fun <T> each(key: String, f: (JSONObject, String) -> T): Map<Char, T> {
                val o = g.optJSONObject(key) ?: return emptyMap()
                return o.keys().asSequence().associate { it[0] to f(o, it) }
            }
            return GolfView(
                hole = g.optInt("hole"),
                holes = g.optInt("holes", GolfMatch.HOLES),
                strokes = each("strokes") { o, k -> o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty() },
                balls = each("balls") { o, k -> pt(o.optJSONArray(k)) },
                done = each("done") { o, k -> o.optBoolean(k) },
                last = g.optJSONObject("last")?.let { l ->
                    GolfLastShot(
                        who = l.optString("who", "o")[0], hole = l.optInt("hole"), from = pt(l.optJSONArray("from")),
                        angle = l.optDouble("angle"), power = l.optDouble("power"), to = pt(l.optJSONArray("to")) ?: Pt(0.0, 0.0),
                        holed = l.optBoolean("holed"), seq = l.optInt("seq"),
                    )
                },
            )
        }
    }
}
