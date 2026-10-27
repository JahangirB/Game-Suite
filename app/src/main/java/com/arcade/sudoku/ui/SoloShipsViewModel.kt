package com.arcade.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.game.ships.ComputerCaptain
import com.arcade.sudoku.game.ships.Fleet
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.SoloShips
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SoloShipsUiState(
    val draft: List<Ship> = Fleet.random(),
    val game: SoloShips? = null,
)

/** Ship Battle against the computer. */
class SoloShipsViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloShipsUiState())
    val state: StateFlow<SoloShipsUiState> = _state.asStateFlow()

    private val captain = ComputerCaptain()
    private var computer: Job? = null

    fun shuffle() = _state.update { it.copy(draft = Fleet.random()) }

    /** The fleet as arranged by hand. */
    fun setDraft(fleet: List<Ship>) = _state.update { it.copy(draft = fleet) }

    fun start() {
        computer?.cancel()
        _state.update { it.copy(game = SoloShips.new(it.draft)) }
        keepPlaying()
    }

    fun fire(x: Int, y: Int) {
        val game = _state.value.game ?: return
        val next = game.fire(x, y)
        if (next === game) return
        _state.update { it.copy(game = next) }
        keepPlaying()
    }

    /**
     * Starts the computer's shots when it's its turn and they aren't already coming. The screen
     * calls this whenever the game changes, so the computer's turn is never left waiting, even
     * if its shots were stopped part way.
     */
    fun keepPlaying() {
        val game = _state.value.game ?: return
        if (!game.myTurn && game.winner == null && computer?.isActive != true) computerTurn()
    }

    /** Back to placing a new fleet. */
    fun newBattle() {
        computer?.cancel()
        _state.value = SoloShipsUiState()
    }

    /** The computer takes its shots one by one, so you can follow them. */
    private fun computerTurn() {
        computer = viewModelScope.launch {
            while (true) {
                val game = _state.value.game ?: break
                if (game.myTurn || game.winner != null) break
                delay(COMPUTER_DELAY_MS)
                _state.update { it.copy(game = it.game?.computerFires(captain)) }
            }
        }
    }

    private companion object {
        const val COMPUTER_DELAY_MS = 750L
    }
}
