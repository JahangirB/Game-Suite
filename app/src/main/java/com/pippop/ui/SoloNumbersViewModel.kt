package com.pippop.ui

import androidx.lifecycle.ViewModel
import com.pippop.game.numbers.NumberCross
import com.pippop.game.numbers.NumberCrossGenerator
import com.pippop.game.numbers.NumberLevel
import com.pippop.game.numbers.NumberPuzzle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Number Cross on your own: a fresh puzzle from the generator, against the clock. */
data class SoloNumbersUiState(
    /** Null while choosing the level. */
    val puzzle: NumberPuzzle? = null,
    val level: NumberLevel = NumberLevel.EASY,
    val entries: List<String> = emptyList(),
    val selected: Int? = null,
    val startedAt: Long = 0,
    val finishedMs: Long? = null,
    /** Fastest solve per level this session. */
    val best: Map<NumberLevel, Long> = emptyMap(),
)

class SoloNumbersViewModel : ViewModel() {
    private val generator = NumberCrossGenerator()
    private val _state = MutableStateFlow(SoloNumbersUiState())
    val state: StateFlow<SoloNumbersUiState> = _state.asStateFlow()

    fun start(level: NumberLevel) {
        val puzzle = generator.generate(level)
        _state.update {
            it.copy(
                puzzle = puzzle, level = level, entries = List(puzzle.blanks.size) { "" }, selected = 0,
                startedAt = System.currentTimeMillis(), finishedMs = null,
            )
        }
    }

    fun again() = start(_state.value.level)

    fun changeLevel() = _state.update { it.copy(puzzle = null, finishedMs = null) }

    fun select(k: Int) = _state.update { it.copy(selected = if (it.selected == k) null else k) }

    /** Puts [value] in the picked blank ("" empties it), then picks the next empty one. */
    fun place(value: String) {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        val k = s.selected ?: return
        if (s.finishedMs != null || k !in s.entries.indices) return
        val entries = s.entries.toMutableList().also { it[k] = value }
        val next = if (value == "") k else (1..entries.size).map { (k + it) % entries.size }.firstOrNull { entries[it] == "" }
        val done = NumberCross.isSolved(puzzle, entries)
        val time = System.currentTimeMillis() - s.startedAt
        _state.update {
            it.copy(
                entries = entries, selected = if (done) null else next,
                finishedMs = if (done) time else null,
                best = if (done) it.best + (s.level to minOf(it.best[s.level] ?: time, time)) else it.best,
            )
        }
    }
}
