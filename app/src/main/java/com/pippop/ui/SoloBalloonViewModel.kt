package com.pippop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.game.balloon.BalloonMatch
import com.pippop.game.grid.GridWords
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Balloon Words on your own: guess the computer's word, or give it one of yours to guess.
 * [youGuess] says which way round this round is.
 */
data class SoloBalloonUiState(
    val started: Boolean = false,
    val youGuess: Boolean = true,
    /** The secret word (shown to you only when you picked it or the round is over). */
    val word: String = "",
    val guessed: List<Char> = emptyList(),
    val misses: Int = 0,
    /** "found", "popped" or null while playing. */
    val result: String? = null,
    val streak: Int = 0,
    val message: String? = null,
) {
    val pattern get() = word.map { if (it in guessed) it else '_' }.joinToString("")
}

class SoloBalloonViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloBalloonUiState())
    val state: StateFlow<SoloBalloonUiState> = _state.asStateFlow()
    private var thinking: Job? = null

    /** You guess a word the computer picked. */
    fun guessTheirs() {
        thinking?.cancel()
        _state.update { SoloBalloonUiState(started = true, youGuess = true, word = GridWords.randomWord().uppercase(), streak = it.streak) }
    }

    /** You pick a word for the computer to guess. */
    fun giveYours(word: String) {
        val w = word.uppercase().replace(Regex("\\s+"), "")
        if (!Regex("[A-Z]{3,12}").matches(w)) {
            _state.update { it.copy(message = "Pick a word of 3 to 12 letters (A to Z only).") }
            return
        }
        thinking?.cancel()
        _state.update { SoloBalloonUiState(started = true, youGuess = false, word = w, streak = it.streak) }
        thinking = viewModelScope.launch {
            while (_state.value.result == null) {
                delay(900)
                val s = _state.value
                guess(BalloonMatch.bestLetter(s.pattern, s.guessed.toSet(), GridWords.all))
            }
        }
    }

    fun setup() {
        thinking?.cancel()
        _state.update { SoloBalloonUiState(streak = it.streak) }
    }

    fun guess(letter: Char) {
        val s = _state.value
        if (s.result != null || letter in s.guessed) return
        val guessed = s.guessed + letter
        val misses = s.misses + if (letter in s.word) 0 else 1
        val found = s.word.all { it in guessed }
        val result = when {
            found -> "found"
            misses >= BalloonMatch.BALLOONS -> "popped"
            else -> null
        }
        // The streak counts words you find in a row, or words the computer couldn't find.
        val won = (result == "found" && s.youGuess) || (result == "popped" && !s.youGuess)
        _state.update {
            it.copy(
                guessed = guessed, misses = misses, result = result, message = null,
                streak = if (result == null) it.streak else if (won) it.streak + 1 else 0,
            )
        }
    }
}
