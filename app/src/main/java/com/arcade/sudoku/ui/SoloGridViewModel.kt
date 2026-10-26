package com.arcade.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.game.grid.LetterGrid
import com.arcade.sudoku.game.grid.LetterGridGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Letter Grid on your own: two minutes, as many words as you can. */
data class SoloGridUiState(
    /** Null before the first round. */
    val letters: String? = null,
    val found: List<String> = emptyList(),
    val endsAt: Long = 0,
    /** At the end: your score and the longest words you missed. */
    val over: Boolean = false,
    val missed: List<String> = emptyList(),
    val total: Int = 0,
    val best: Int? = null,
    val message: String? = null,
) {
    val score get() = found.sumOf { LetterGrid.points(it) }
}

class SoloGridViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloGridUiState())
    val state: StateFlow<SoloGridUiState> = _state.asStateFlow()
    private var clock: Job? = null

    fun start() {
        clock?.cancel()
        viewModelScope.launch {
            val letters = withContext(Dispatchers.Default) { LetterGridGenerator().generate() }
            val endsAt = System.currentTimeMillis() + LetterGrid.ROUND_MS
            _state.update { SoloGridUiState(letters = letters, endsAt = endsAt, best = it.best) }
            clock = launch {
                delay(LetterGrid.ROUND_MS)
                finish()
            }
        }
    }

    fun find(word: String) {
        val s = _state.value
        val letters = s.letters ?: return
        if (s.over) return
        val w = word.lowercase()
        if (w.length < 3) return
        val problem = if (w in s.found) "You already found ${w.uppercase()}." else LetterGrid.problem(letters, w)
        _state.update { if (problem != null) it.copy(message = problem) else it.copy(found = it.found + w, message = null) }
    }

    fun finish() {
        clock?.cancel()
        val s = _state.value
        val letters = s.letters ?: return
        viewModelScope.launch {
            val all = withContext(Dispatchers.Default) { LetterGrid.allWords(letters) }
            val missed = (all - s.found.toSet()).sortedWith(compareByDescending<String> { it.length }.thenBy { it }).take(12)
            _state.update { it.copy(over = true, missed = missed, total = all.size, best = maxOf(it.best ?: 0, it.score)) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
