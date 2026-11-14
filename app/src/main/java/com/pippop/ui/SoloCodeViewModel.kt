package com.pippop.ui

import com.pippop.data.GameKind
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecordStore
import androidx.lifecycle.ViewModel
import com.pippop.data.CodeGuess
import com.pippop.game.code.CodeBreaker
import com.pippop.game.code.CodeRules
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
    /** The settings: harder codes have five pegs, a seventh colour or no repeats. */
    val rules: CodeRules = CodeRules(),
) {
    val solved get() = guesses.lastOrNull()?.black == rules.pegs
    val done get() = solved || guesses.size >= CodeBreaker.TRIES
}

class SoloCodeViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloCodeUiState())
    val state: StateFlow<SoloCodeUiState> = _state.asStateFlow()

    fun start() = _state.update { SoloCodeUiState(started = true, secret = it.rules.random(), best = it.best, rules = it.rules) }

    /** A fresh code under [rules]; the best score is kept per setting only while they stay. */
    fun start(rules: CodeRules) = _state.update { SoloCodeUiState(started = true, secret = rules.random(), best = it.best.takeIf { _ -> rules == it.rules }, rules = rules) }

    fun guess(code: String) {
        val s = _state.value
        if (s.done || !s.rules.isCode(code)) return
        val (black, white) = CodeBreaker.score(code, s.secret)
        val guesses = s.guesses + CodeGuess(code, black, white)
        val solved = black == s.rules.pegs
        _state.update { it.copy(guesses = guesses, best = if (solved) minOf(it.best ?: guesses.size, guesses.size) else it.best) }
        if (solved || guesses.size >= CodeBreaker.TRIES) {
            val level = "${s.rules.pegs}-${s.rules.colours}-${if (s.rules.repeats) "repeats" else "none"}"
            SoloRecordStore.current?.record(GameKind.CODE, level, SoloMetric.GUESSES, if (solved) guesses.size.toLong() else null)
        }
    }
}
