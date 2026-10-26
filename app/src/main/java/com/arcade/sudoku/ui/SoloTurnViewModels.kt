package com.arcade.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.game.clash.ClashComputer
import com.arcade.sudoku.game.clash.ClashMatch
import com.arcade.sudoku.game.clash.ClashView
import com.arcade.sudoku.game.dominoes.DominoComputer
import com.arcade.sudoku.game.dominoes.DominoMatch
import com.arcade.sudoku.game.dominoes.DominoOptions
import com.arcade.sudoku.game.dominoes.DominoView
import com.arcade.sudoku.game.dots.Dots
import com.arcade.sudoku.game.dots.DotsComputer
import com.arcade.sudoku.game.dots.DotsMatch
import com.arcade.sudoku.game.memory.MemoryMatch
import com.arcade.sudoku.game.ttt.Ttt
import com.arcade.sudoku.game.ttt.TttComputer
import com.arcade.sudoku.game.ttt.TttMatch
import com.arcade.sudoku.game.ttt.TttPosition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Dots & Boxes against the computer. Owners: 'm' yours, 'o' the computer's. */
data class SoloDotsUiState(
    val lines: String = ".".repeat(Dots.LINES),
    val boxes: String = ".".repeat(Dots.BOXES * Dots.BOXES),
    val lastLine: Int? = null,
    val myTurn: Boolean = true,
    /** "me", "computer", "draw" or null while playing. */
    val result: String? = null,
)

class SoloDotsViewModel : ViewModel() {
    private var match = DotsMatch()
    private val computer = DotsComputer()
    private var playerStarts = true
    private val _state = MutableStateFlow(SoloDotsUiState())
    val state: StateFlow<SoloDotsUiState> = _state.asStateFlow()

    fun newGame() {
        match = DotsMatch()
        _state.value = SoloDotsUiState(myTurn = playerStarts)
        if (!playerStarts) computerMoves()
        playerStarts = !playerStarts
    }

    fun drawLine(line: Int) {
        val s = _state.value
        if (!s.myTurn || s.result != null || match.lines[line] != '.') return
        val again = play('m', line)
        if (!again && _state.value.result == null) computerMoves()
    }

    /** Plays one line; returns true if the same player goes again. */
    private fun play(mark: Char, line: Int): Boolean {
        val result = match.play(mark, JSONObject().put("line", line))
        _state.value = SoloDotsUiState(
            lines = match.lines,
            boxes = match.boxes,
            lastLine = line,
            myTurn = if (result.again) mark == 'm' else mark != 'm',
            result = if (!result.over) null else when (result.winner) {
                'm' -> "me"
                'o' -> "computer"
                else -> "draw"
            },
        )
        return result.again
    }

    private fun computerMoves() {
        viewModelScope.launch {
            do {
                delay(500)
                val again = play('o', computer.move(match.lines))
            } while (again && _state.value.result == null)
        }
    }
}

/** Ultimate Tic-Tac-Toe against the computer. Marks: 'm' your X, 'o' the computer's O. */
data class SoloTttUiState(
    val position: TttPosition = TttPosition(),
    val lastCell: Int? = null,
    val winLine: List<Int> = emptyList(),
    val myTurn: Boolean = true,
    /** "me", "computer", "draw" or null while playing. */
    val result: String? = null,
)

class SoloTttViewModel : ViewModel() {
    private var match = TttMatch()
    private val computer = TttComputer()
    private var playerStarts = true
    private val _state = MutableStateFlow(SoloTttUiState())
    val state: StateFlow<SoloTttUiState> = _state.asStateFlow()

    fun newGame() {
        playerStarts = !playerStarts
        match = TttMatch()
        _state.value = SoloTttUiState(myTurn = playerStarts)
        if (!playerStarts) computerMoves()
    }

    fun mark(cell: Int) {
        val s = _state.value
        if (!s.myTurn || s.result != null || !Ttt.isLegal(match.position, cell)) return
        play('m', cell)
        if (_state.value.result == null) computerMoves()
    }

    private fun play(mark: Char, cell: Int) {
        val result = match.play(mark, JSONObject().put("cell", cell))
        _state.value = SoloTttUiState(
            position = match.position,
            lastCell = cell,
            winLine = match.winLine.orEmpty(),
            myTurn = mark != 'm',
            result = if (!result.over) null else when (result.winner) {
                'm' -> "me"
                'o' -> "computer"
                else -> "draw"
            },
        )
    }

    private fun computerMoves() {
        viewModelScope.launch {
            delay(600)
            play('o', computer.move(match.position, 'o', 'm'))
        }
    }
}

/** Memory Pairs on your own: find all pairs in as few flips as you can. */
data class SoloMemoryUiState(
    val faces: List<Avatar?> = List(MemoryMatch.CARDS) { null },
    val found: String = ".".repeat(MemoryMatch.CARDS),
    val flips: Int = 0,
    val startedAt: Long? = null,
    val finishedMs: Long? = null,
    val bestFlips: Int? = null,
)

class SoloMemoryViewModel : ViewModel() {
    private var match = MemoryMatch()
    private var hideJob: Job? = null
    private val _state = MutableStateFlow(SoloMemoryUiState())
    val state: StateFlow<SoloMemoryUiState> = _state.asStateFlow()

    fun newGame() {
        hideJob?.cancel()
        match = MemoryMatch()
        _state.value = SoloMemoryUiState(bestFlips = _state.value.bestFlips)
    }

    fun flip(card: Int) {
        val s = _state.value
        if (s.finishedMs != null || match.owners[card] != '.' || card in match.up && match.up.size == 1) return
        hideJob?.cancel()
        val result = match.play('m', JSONObject().put("card", card))
        val now = System.currentTimeMillis()
        val start = s.startedAt ?: now
        val flips = s.flips + 1
        val done = result.over
        _state.value = s.copy(
            faces = shown(),
            found = match.owners,
            flips = flips,
            startedAt = start,
            finishedMs = if (done) now - start else null,
            bestFlips = if (done) minOf(s.bestFlips ?: flips, flips) else s.bestFlips,
        )
        // A missed pair turns face down again after a moment.
        if (match.up.size == 2) {
            hideJob = viewModelScope.launch {
                delay(900)
                _state.value = _state.value.copy(faces = shown(hideMissed = true))
            }
        }
    }

    private fun shown(hideMissed: Boolean = false) = match.deck.mapIndexed { i, face ->
        val up = i in match.up && !(hideMissed && match.up.size == 2)
        if (match.owners[i] != '.' || up) face else null
    }
}

/**
 * Dominoes against one to three computer players. Marks: 'm' you, then 'o', 'p', 'q' for the
 * computers in seat order (with teams, 'p' is your partner).
 */
data class SoloDominoesUiState(
    /** Null while choosing the rules. */
    val view: DominoView? = null,
    val opponents: Int = 1,
    val options: DominoOptions = DominoOptions(),
    /** Whose turn: 'm' or a computer's mark. */
    val turn: Char = 'm',
    /** "me", "computer" or null while playing. */
    val result: String? = null,
)

class SoloDominoesViewModel : ViewModel() {
    private var match: DominoMatch? = null
    private val computer = DominoComputer()
    private var thinking: Job? = null
    private val _state = MutableStateFlow(SoloDominoesUiState())
    val state: StateFlow<SoloDominoesUiState> = _state.asStateFlow()

    fun setOpponents(n: Int) = _state.update { it.copy(opponents = n.coerceIn(1, 3)) }

    fun setOptions(options: DominoOptions) = _state.update { it.copy(options = options) }

    /** Back to choosing the rules. */
    fun changeRules() {
        thinking?.cancel()
        match = null
        _state.update { it.copy(view = null, result = null, turn = 'm') }
    }

    fun start() {
        thinking?.cancel()
        val s = _state.value
        val m = DominoMatch("mopq".take(s.opponents + 1).toList(), s.options)
        match = m
        _state.value = s.copy(view = viewOf(m), turn = m.firstMark, result = null)
        if (m.firstMark != 'm') computersMove()
    }

    fun playTile(index: Int, end: String) = humanMove(JSONObject().put("tile", index).put("end", end))

    fun drawTile() = humanMove(JSONObject().put("draw", true))

    fun passTurn() = humanMove(JSONObject().put("pass", true))

    private fun humanMove(move: JSONObject) {
        val s = _state.value
        if (s.turn != 'm' || s.result != null || match == null) return
        try {
            play('m', move)
        } catch (e: IllegalArgumentException) {
            return
        }
        if (_state.value.result == null && _state.value.turn != 'm') computersMove()
    }

    private fun play(mark: Char, move: JSONObject) {
        val m = match ?: return
        val result = m.play(mark, move)
        val next = when {
            result.turn != null -> result.turn
            result.again -> mark
            else -> m.marks[(m.marks.indexOf(mark) + 1) % m.marks.size]
        }
        _state.update {
            it.copy(
                view = viewOf(m),
                turn = next,
                result = if (!result.over) null else if (result.winner != null && m.sameSide(result.winner, 'm')) "me" else "computer",
            )
        }
    }

    private fun computersMove() {
        thinking = viewModelScope.launch {
            while (_state.value.result == null && _state.value.turn != 'm') {
                delay(800)
                val m = match ?: return@launch
                val mark = _state.value.turn
                play(mark, computer.move(m, mark))
            }
        }
    }

    private fun viewOf(m: DominoMatch) = DominoView.from(m.view('m', mapOf('o' to 'o', 'p' to 'p', 'q' to 'q')))
}

/**
 * Colour Clash against one to three computer players. Marks: 'm' you, then 'o', 'p', 'q' for
 * the computers in seat order.
 */
data class SoloClashUiState(
    /** Null while choosing how many computers to play. */
    val view: ClashView? = null,
    val opponents: Int = 1,
    /** Whose turn: 'm' or a computer's mark. */
    val turn: Char = 'm',
    /** "me", "computer" or null while playing. */
    val result: String? = null,
)

class SoloClashViewModel : ViewModel() {
    private var match: ClashMatch? = null
    private val computer = ClashComputer()
    private var thinking: Job? = null
    private var starter = 0
    private val _state = MutableStateFlow(SoloClashUiState())
    val state: StateFlow<SoloClashUiState> = _state.asStateFlow()

    fun setOpponents(n: Int) = _state.update { it.copy(opponents = n.coerceIn(1, 3)) }

    fun changeSetup() {
        thinking?.cancel()
        match = null
        _state.update { it.copy(view = null, result = null, turn = 'm') }
    }

    fun start() {
        thinking?.cancel()
        val marks = "mopq".take(_state.value.opponents + 1).toList()
        val m = ClashMatch(marks)
        match = m
        // Who starts moves one seat on each game.
        val first = marks[starter++ % marks.size]
        _state.update { it.copy(view = viewOf(m), turn = first, result = null) }
        if (first != 'm') computersMove()
    }

    fun playCard(index: Int, colour: Char? = null) = humanMove(JSONObject().put("card", index).apply { if (colour != null) put("colour", colour.toString()) })

    fun drawCard() = humanMove(JSONObject().put("draw", true))

    fun passCard() = humanMove(JSONObject().put("pass", true))

    private fun humanMove(move: JSONObject) {
        val s = _state.value
        if (s.turn != 'm' || s.result != null || match == null) return
        try {
            play('m', move)
        } catch (e: IllegalArgumentException) {
            return
        }
        if (_state.value.result == null && _state.value.turn != 'm') computersMove()
    }

    private fun play(mark: Char, move: JSONObject) {
        val m = match ?: return
        val result = m.play(mark, move)
        _state.update {
            it.copy(
                view = viewOf(m),
                turn = result.turn ?: mark,
                result = if (!result.over) null else if (result.winner == 'm') "me" else "computer",
            )
        }
    }

    private fun computersMove() {
        thinking = viewModelScope.launch {
            while (_state.value.result == null && _state.value.turn != 'm') {
                delay(900)
                val m = match ?: return@launch
                val mark = _state.value.turn
                play(mark, computer.move(m, mark))
            }
        }
    }

    private fun viewOf(m: ClashMatch) = ClashView.from(m.view('m', mapOf('o' to 'o', 'p' to 'p', 'q' to 'q')))
}
