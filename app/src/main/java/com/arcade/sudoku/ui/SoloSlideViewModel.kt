package com.arcade.sudoku.ui

import androidx.lifecycle.ViewModel
import com.arcade.sudoku.game.slide.Slide
import com.arcade.sudoku.game.slide.SlideLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Slide Race on your own: a fresh shuffle against the clock. */
data class SoloSlideUiState(
    val level: SlideLevel? = null,
    val tiles: List<Int> = emptyList(),
    val moves: Int = 0,
    val startedAt: Long = 0,
    val finishedMs: Long? = null,
    val best: Map<SlideLevel, Long> = emptyMap(),
)

class SoloSlideViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloSlideUiState())
    val state: StateFlow<SoloSlideUiState> = _state.asStateFlow()

    fun start(level: SlideLevel) = _state.update {
        it.copy(level = level, tiles = Slide.shuffle(level.size), moves = 0, startedAt = System.currentTimeMillis(), finishedMs = null)
    }

    fun again() = _state.value.level?.let { start(it) } ?: Unit

    fun changeLevel() = _state.update { it.copy(level = null, finishedMs = null) }

    /** A swipe anywhere: slides the tile next to the gap that way. */
    fun swipe(dx: Int, dy: Int) {
        val s = _state.value
        val level = s.level ?: return
        Slide.tileForSwipe(s.tiles, level.size, dx, dy)?.let(::tap)
    }

    fun tap(tile: Int) {
        val s = _state.value
        val level = s.level ?: return
        if (s.finishedMs != null) return
        val slides = Slide.slidesFor(s.tiles, level.size, tile)
        if (slides.isEmpty()) return
        val tiles = Slide.replay(s.tiles, level.size, slides)
        val done = Slide.isSolved(tiles, level.size)
        val time = System.currentTimeMillis() - s.startedAt
        _state.update {
            it.copy(
                tiles = tiles, moves = it.moves + slides.size, finishedMs = if (done) time else null,
                best = if (done) it.best + (level to minOf(it.best[level] ?: time, time)) else it.best,
            )
        }
    }
}
