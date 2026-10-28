package com.pippop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.game.blocks.BlocksMatch
import com.pippop.game.blocks.BlocksView
import com.pippop.game.blocks.Placement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Corner Blocks against one to three computers. */
data class SoloBlocksUiState(
    val view: BlocksView? = null,
    val opponents: Int = 1,
    /** 0 easy to 2 hard. */
    val skill: Int = 1,
    val turn: Char? = 'm',
    /** "me", "computer", "tie" or null while playing. */
    val result: String? = null,
)

class SoloBlocksViewModel : ViewModel() {
    private var match: BlocksMatch? = null
    private var thinking: Job? = null
    private val _state = MutableStateFlow(SoloBlocksUiState())
    val state: StateFlow<SoloBlocksUiState> = _state.asStateFlow()

    fun setOpponents(n: Int) = _state.update { it.copy(opponents = n.coerceIn(1, 3)) }

    fun setSkill(n: Int) = _state.update { it.copy(skill = n.coerceIn(0, 2)) }

    fun changeSetup() {
        thinking?.cancel()
        match = null
        _state.update { it.copy(view = null, result = null, turn = 'm') }
    }

    fun start() {
        thinking?.cancel()
        val m = BlocksMatch("mopq".take(_state.value.opponents + 1).toList())
        match = m
        _state.update { it.copy(view = viewOf(m), turn = 'm', result = null) }
    }

    fun place(p: Placement) {
        val s = _state.value
        if (s.turn != 'm' || s.result != null || match == null) return
        try {
            play('m', p)
        } catch (e: IllegalArgumentException) {
            return
        }
        if (_state.value.result == null && _state.value.turn != 'm') computersMove()
    }

    private fun play(mark: Char, p: Placement) {
        val m = match ?: return
        val result = m.play(mark, JSONObject().put("piece", p.piece).put("orient", p.orient).put("x", p.x).put("y", p.y))
        _state.update {
            it.copy(
                view = viewOf(m),
                turn = result.turn,
                result = if (!result.over) null else when (result.winner) { 'm' -> "me"; null -> "tie"; else -> "computer" },
            )
        }
    }

    private fun computersMove() {
        thinking = viewModelScope.launch {
            while (_state.value.result == null && _state.value.turn.let { it != null && it != 'm' }) {
                delay(700)
                val m = match ?: return@launch
                val mark = _state.value.turn ?: return@launch
                val skill = listOf(0.2, 0.6, 1.0)[_state.value.skill]
                val p = withContext(Dispatchers.Default) { m.computerMove(mark, skill) } ?: return@launch
                play(mark, p)
            }
        }
    }

    private fun viewOf(m: BlocksMatch) = BlocksView.from(m.view('m', mapOf('o' to 'o', 'p' to 'p', 'q' to 'q')))
}
