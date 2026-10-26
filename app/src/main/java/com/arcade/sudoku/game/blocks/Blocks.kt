package com.arcade.sudoku.game.blocks

import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** One way to lay a piece: [orient] 0–7 and its squares from its top-left corner. */
data class Placement(val piece: Int, val orient: Int, val x: Int, val y: Int)

/**
 * Corner Blocks, matching the server (backend/cloudflare/src/blocks.js): 21 pieces each on a
 * shared board (14 × 14 for two, 20 × 20 for three or four). The first piece covers your
 * start square; after that pieces touch yours only corner to corner. A player who can't move
 * is skipped; when nobody can, the most squares placed wins.
 */
object Blocks {
    val PIECES: List<List<Pair<Int, Int>>> = listOf(
        listOf(0 to 0),
        listOf(0 to 0, 1 to 0),
        listOf(0 to 0, 1 to 0, 2 to 0),
        listOf(0 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
        listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 1 to 3),
        listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2, 1 to 3),
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, 0 to 2),
        listOf(0 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1),
        listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2),
        listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2, 2 to 2),
        listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2, 2 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1, 1 to 2),
        listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1, 1 to 2),
        listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1, 1 to 2),
    )

    /** [piece] turned to [orient] (flip for 4–7, then quarter turns), top-left at (0, 0), sorted. */
    fun shape(piece: Int, orient: Int): List<Pair<Int, Int>> {
        var cells = PIECES[piece].map { (x, y) -> (if (orient >= 4) -x else x) to y }
        repeat(orient % 4) { cells = cells.map { (x, y) -> -y to x } }
        val mx = cells.minOf { it.first }
        val my = cells.minOf { it.second }
        return cells.map { (x, y) -> (x - mx) to (y - my) }.sortedWith(compareBy({ it.second }, { it.first }))
    }

    /** The different ways to lay each piece (mirror pairs and turns that look the same count once). */
    val ORIENTS: List<List<Pair<Int, List<Pair<Int, Int>>>>> = PIECES.indices.map { p ->
        (0 until 8).map { it to shape(p, it) }.distinctBy { it.second }
    }

    fun sizeFor(players: Int) = if (players <= 2) 14 else 20

    fun startsFor(players: Int): List<Pair<Int, Int>> =
        if (players <= 2) listOf(4 to 4, 9 to 9) else listOf(0 to 0, 19 to 0, 19 to 19, 0 to 19).take(players)
}

class BlocksMatch(val marks: List<Char>) : TurnMatch {
    val size = Blocks.sizeFor(marks.size)
    val board = CharArray(size * size) { '.' }
    val starts = marks.zip(Blocks.startsFor(marks.size)).toMap()
    val left = marks.associateWith { Blocks.PIECES.indices.toMutableList() }
    val placed = marks.associateWith { 0 }.toMutableMap()
    private var last: Pair<Char, List<Pair<Int, Int>>>? = null
    var turn: Char? = marks[0]
        private set

    override val firstMark: Char get() = marks[0]

    private fun at(x: Int, y: Int) = if (x < 0 || y < 0 || x >= size || y >= size) null else board[y * size + x]

    /** The squares [p] covers for [mark], or why it isn't allowed. */
    fun check(mark: Char, p: Placement): Result<List<Pair<Int, Int>>> {
        if (p.piece !in left.getValue(mark)) return Result.failure(IllegalArgumentException("You have played that piece."))
        val cells = Blocks.shape(p.piece, p.orient).map { (cx, cy) -> (p.x + cx) to (p.y + cy) }
        val first = left.getValue(mark).size == Blocks.PIECES.size
        var corner = false
        for ((cx, cy) in cells) {
            if (at(cx, cy) != '.') return Result.failure(IllegalArgumentException("That spot is taken or off the board."))
            if (EDGES.any { (dx, dy) -> at(cx + dx, cy + dy) == mark }) return Result.failure(IllegalArgumentException("Your pieces may only touch at the corners."))
            if (CORNERS.any { (dx, dy) -> at(cx + dx, cy + dy) == mark }) corner = true
        }
        if (first) {
            if (starts.getValue(mark) !in cells) return Result.failure(IllegalArgumentException("Your first piece must cover your start square."))
        } else if (!corner) {
            return Result.failure(IllegalArgumentException("Touch one of your pieces corner to corner."))
        }
        return Result.success(cells)
    }

    /** Every legal move for [mark] (or just the first, with [any]). */
    fun movesFor(mark: Char, any: Boolean = false): List<Placement> {
        val first = left.getValue(mark).size == Blocks.PIECES.size
        val n = size
        // (Inside buildList, size would be the list's own.)
        val targets = if (first) listOf(starts.getValue(mark)) else buildList {
            for (y in 0 until n) for (x in 0 until n) {
                if (at(x, y) != '.') continue
                if (CORNERS.any { (dx, dy) -> at(x + dx, y + dy) == mark } && EDGES.none { (dx, dy) -> at(x + dx, y + dy) == mark }) add(x to y)
            }
        }
        val out = mutableListOf<Placement>()
        val seen = HashSet<Placement>()
        for (piece in left.getValue(mark)) for ((orient, cells) in Blocks.ORIENTS[piece]) for ((tx, ty) in targets) for ((cx, cy) in cells) {
            val p = Placement(piece, orient, tx - cx, ty - cy)
            if (!seen.add(p)) continue
            if (check(mark, p).isSuccess) {
                out += p
                if (any) return out
            }
        }
        return out
    }

    fun canMove(mark: Char) = left.getValue(mark).isNotEmpty() && movesFor(mark, any = true).isNotEmpty()

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val p = Placement(move.optInt("piece", -1), move.optInt("orient", -1), move.optInt("x", -99), move.optInt("y", -99))
        require(p.piece in Blocks.PIECES.indices && p.orient in 0..7) { "That move is not valid." }
        val cells = check(mark, p).getOrElse { throw it }
        for ((x, y) in cells) board[y * size + x] = mark
        left.getValue(mark).remove(p.piece)
        placed[mark] = placed.getValue(mark) + cells.size
        last = mark to cells
        var i = marks.indexOf(mark)
        repeat(marks.size) {
            i = (i + 1) % marks.size
            if (canMove(marks[i])) {
                turn = marks[i]
                return TurnResult(again = false, turn = marks[i])
            }
        }
        turn = null
        val ranked = marks.sortedByDescending { placed.getValue(it) }
        val tie = ranked.size > 1 && placed.getValue(ranked[0]) == placed.getValue(ranked[1])
        return TurnResult(again = false, over = true, winner = if (tie) null else ranked[0], reason = if (tie) "draw" else "squares")
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject {
        fun who(m: Char) = if (m == mark) 'm' else seats[m] ?: 'o'
        fun each(f: (Char) -> Any) = JSONObject().apply { for (m in marks) put(who(m).toString(), f(m)) }
        fun cell(c: Pair<Int, Int>) = JSONArray().put(c.first).put(c.second)
        return JSONObject().put(
            "blocks",
            JSONObject()
                .put("size", size)
                .put("board", board.map { if (it == '.') '.' else who(it) }.joinToString(""))
                .put("starts", each { cell(starts.getValue(it)) })
                .put("left", each { JSONArray(left.getValue(it)) })
                .put("placed", each { placed.getValue(it) })
                .put("last", last?.let { (m, cells) -> JSONObject().put("who", who(m).toString()).put("cells", JSONArray(cells.map(::cell))) } ?: JSONObject.NULL),
        )
    }

    /**
     * The computer's move: a big piece first, reaching towards the middle, leaving itself
     * corners to grow from; [skill] (0 to 1) lowers the noise in that judgement.
     */
    fun computerMove(mark: Char, skill: Double, random: Random = Random.Default): Placement? {
        val moves = movesFor(mark)
        if (moves.isEmpty()) return null
        val mid = (size - 1) / 2.0
        return moves.maxBy { p ->
            val cells = Blocks.shape(p.piece, p.orient).map { (cx, cy) -> (p.x + cx) to (p.y + cy) }
            val centre = cells.minOf { (x, y) -> abs(x - mid) + abs(y - mid) }
            cells.size * 10.0 - centre * 0.6 + random.nextDouble() * (1 - skill) * 25
        }
    }

    companion object {
        private val EDGES = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val CORNERS = listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1)
    }
}

/** Corner Blocks as one player sees it ('m' is you, then 'o', 'p', 'q'). */
data class BlocksView(
    val size: Int,
    val board: String,
    val starts: Map<Char, Pair<Int, Int>>,
    val left: Map<Char, List<Int>>,
    val placed: Map<Char, Int>,
    val lastWho: Char?,
    val lastCells: List<Pair<Int, Int>>,
) {
    fun at(x: Int, y: Int) = if (x < 0 || y < 0 || x >= size || y >= size) null else board[y * size + x]

    /** Whether [p] is a legal move for you on this board, worked out like the server does. */
    fun allows(p: Placement): Boolean {
        val mine = left['m'] ?: return false
        if (p.piece !in mine) return false
        val cells = Blocks.shape(p.piece, p.orient).map { (cx, cy) -> (p.x + cx) to (p.y + cy) }
        var corner = false
        for ((cx, cy) in cells) {
            if (at(cx, cy) != '.') return false
            if (listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> at(cx + dx, cy + dy) == 'm' }) return false
            if (listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1).any { (dx, dy) -> at(cx + dx, cy + dy) == 'm' }) corner = true
        }
        return if (mine.size == Blocks.PIECES.size) starts['m'] in cells else corner
    }

    companion object {
        fun from(turns: JSONObject): BlocksView {
            val b = turns.getJSONObject("blocks")
            fun cell(a: JSONArray?) = a?.let { it.optInt(0) to it.optInt(1) }
            fun <T> each(key: String, f: (JSONObject, String) -> T): Map<Char, T> {
                val o = b.optJSONObject(key) ?: return emptyMap()
                return o.keys().asSequence().associate { it[0] to f(o, it) }
            }
            val last = b.optJSONObject("last")
            return BlocksView(
                size = b.optInt("size", 14),
                board = b.optString("board"),
                starts = each("starts") { o, k -> cell(o.optJSONArray(k)) ?: (0 to 0) },
                left = each("left") { o, k -> o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty() },
                placed = each("placed") { o, k -> o.optInt(k) },
                lastWho = last?.optString("who")?.firstOrNull(),
                lastCells = last?.optJSONArray("cells")?.let { a -> (0 until a.length()).mapNotNull { cell(a.optJSONArray(it)) } }.orEmpty(),
            )
        }
    }
}
