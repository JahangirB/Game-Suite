package com.pippop.ui

import androidx.lifecycle.ViewModel
import com.pippop.data.CodeGuess
import com.pippop.game.code.CodeBreaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Code Breaker on your own: crack the computer's code in as few guesses as you can. */
data class SoloCodeUiState(
    val started: Boolean = false,
    val secret: String = "",
    val guesses: List<CodeGuess> = emptyList(),
    val best: Int? = null,
) {
    val solved get() = guesses.lastOrNull()?.black == CodeBreaker.PEGS
    val done get() = solved || guesses.size >= CodeBreaker.TRIES
}

class SoloCodeViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloCodeUiState())
    val state: StateFlow<SoloCodeUiState> = _state.asStateFlow()

    fun start() = _state.update { SoloCodeUiState(started = true, secret = CodeBreaker.randomCode(), best = it.best) }

    fun guess(code: String) {
        val s = _state.value
        if (s.done || !CodeBreaker.isCode(code)) return
        val (black, white) = CodeBreaker.score(code, s.secret)
        val guesses = s.guesses + CodeGuess(code, black, white)
        val solved = black == CodeBreaker.PEGS
        _state.update { it.copy(guesses = guesses, best = if (solved) minOf(it.best ?: guesses.size, guesses.size) else it.best) }
    }
}
