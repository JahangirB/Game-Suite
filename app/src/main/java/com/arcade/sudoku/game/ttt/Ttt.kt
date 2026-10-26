package com.arcade.sudoku.game.ttt

import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Ultimate Tic-Tac-Toe, the same rules as backend/cloudflare/src/ttt.js: nine small boards in a
 * 3 × 3 big board. The cell you mark sends your opponent to the small board in the same position,
 * or anywhere if that board is decided. Cells are board * 9 + cell; [boards] holds each small
 * board's owner, or 'd' when it filled up with no winner. [next] is the board to play in, -1 for any.
 */
data class TttPosition(
    val cells: String = ".".repeat(81),
    val boards: String = ".".repeat(9),
    val next: Int = -1,
)

object Ttt {
    val LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6),
        listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6),
    )

    /** The line of three [mark]s in a 9-character grid, or null. */
    fun lineOf(grid: CharSequence, mark: Char): List<Int>? = LINES.firstOrNull { line -> line.all { grid[it] == mark } }

    fun isLegal(pos: TttPosition, cell: Int): Boolean {
        if (cell !in 0 until 81) return false
        val board = cell / 9
        return pos.boards[board] == '.' && (pos.next < 0 || pos.next == board) && pos.cells[cell] == '.'
    }

    fun legalMoves(pos: TttPosition) = (0 until 81).filter { isLegal(pos, it) }

    /** [pos] after [mark] marks [cell], which must be legal. */
    fun play(pos: TttPosition, mark: Char, cell: Int): TttPosition {
        val board = cell / 9
        val cells = pos.cells.replaceAt(cell, mark)
        val small = cells.substring(board * 9, board * 9 + 9)
        val boards = when {
            lineOf(small, mark) != null -> pos.boards.replaceAt(board, mark)
            '.' !in small -> pos.boards.replaceAt(board, 'd')
            else -> pos.boards
        }
        val target = cell % 9
        return TttPosition(cells, boards, if (boards[target] == '.') target else -1)
    }

    private fun String.replaceAt(i: Int, c: Char) = substring(0, i) + c + substring(i + 1)
}

/** One round, matching ttt.js for battles. */
class TttMatch : TurnMatch {
    var position = TttPosition()
        private set
    var winLine: List<Int>? = null
        private set
    private var last: Pair<Char, Int>? = null

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val cell = move.optInt("cell", -1)
        require(cell in 0 until 81) { "Pick a square on the board." }
        require(position.boards[cell / 9] == '.') { "That small board is already decided." }
        require(position.next < 0 || position.next == cell / 9) { "Play in the highlighted board." }
        require(position.cells[cell] == '.') { "That square is taken." }
        position = Ttt.play(position, mark, cell)
        last = mark to cell
        val line = Ttt.lineOf(position.boards, mark)
        if (line != null) {
            winLine = line
            return TurnResult(again = false, over = true, winner = mark, reason = "three")
        }
        return TurnResult(again = false, over = '.' !in position.boards, reason = "draw")
    }

    override fun view(mark: Char): JSONObject = JSONObject()
        .put("board", TurnMatch.side(position.cells, mark))
        .put("boards", position.boards.map { if (it == '.' || it == 'd') it else if (it == mark) 'm' else 'o' }.joinToString(""))
        .put("next", position.next)
        .put("winLine", winLine?.let { JSONArray(it) } ?: JSONObject.NULL)
        .put("lastMove", last?.let { (by, cell) -> TurnMatch.lastMove(by, mark, "cell", cell) } ?: JSONObject.NULL)
}

/**
 * A friendly computer opponent: it takes a win when it sees one, blocks yours, and tries not to
 * send you to a board you can win at once. It looks one move ahead, so it can be beaten.
 */
class TttComputer(private val random: Random = Random.Default) {
    fun move(pos: TttPosition, me: Char, them: Char): Int =
        Ttt.legalMoves(pos).maxBy { score(pos, it, me, them) + random.nextDouble() }

    private fun score(pos: TttPosition, cell: Int, me: Char, them: Char): Double {
        val after = Ttt.play(pos, me, cell)
        if (Ttt.lineOf(after.boards, me) != null) return 10_000.0
        var score = 0.0
        val board = cell / 9
        if (after.boards[board] == me) score += 100
        // Taking the square they need to win this small board.
        if (Ttt.lineOf(pos.cells.substring(board * 9, board * 9 + 9).replaceRange(cell % 9, cell % 9 + 1, them.toString()), them) != null) {
            score += 60
        }
        // What the opponent can do from where this move sends them.
        val replies = Ttt.legalMoves(after)
        if (replies.any { Ttt.lineOf(Ttt.play(after, them, it).boards, them) != null }) score -= 5_000
        else if (replies.any { Ttt.play(after, them, it).boards[it / 9] == them }) score -= 80
        if (after.next < 0) score -= 25
        score += when (cell % 9) {
            4 -> 6.0
            0, 2, 6, 8 -> 3.0
            else -> 0.0
        }
        if (board == 4) score += 2
        return score
    }
}
