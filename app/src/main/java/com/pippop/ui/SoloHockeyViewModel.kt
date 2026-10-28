package com.pippop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.game.hockey.Hockey
import com.pippop.game.hockey.HockeyComputer
import com.pippop.game.hockey.HockeyFrame
import com.pippop.game.hockey.HockeyTable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Air Hockey against the computer: you at the bottom, first to [Hockey.GOALS]. */
data class SoloHockeyUiState(
    /** 0 easy, 1 medium, 2 hard; null while choosing. */
    val level: Int? = null,
    val frame: HockeyFrame = HockeyFrame.start(),
    /** "me" or "computer" once someone reaches the goals. */
    val result: String? = null,
)

class SoloHockeyViewModel : ViewModel() {
    private var table = HockeyTable()
    private var loop: Job? = null
    private val _state = MutableStateFlow(SoloHockeyUiState())
    val state: StateFlow<SoloHockeyUiState> = _state.asStateFlow()

    fun start(level: Int) {
        loop?.cancel()
        table = HockeyTable()
        val computer = HockeyComputer(HockeyComputer.SPEEDS[level.coerceIn(0, 2)])
        _state.value = SoloHockeyUiState(level = level)
        loop = viewModelScope.launch {
            var last = System.nanoTime()
            while (true) {
                delay(16)
                val now = System.nanoTime()
                val dt = (now - last) / 1e9f
                last = now
                val (x, y) = computer.move(table.frame, dt)
                table.moveTop(x, y)
                table.step(dt)
                val f = table.frame
                val result = when {
                    f.bottomScore >= Hockey.GOALS -> "me"
                    f.topScore >= Hockey.GOALS -> "computer"
                    else -> null
                }
                _state.update { it.copy(frame = f, result = result) }
                if (result != null) break
            }
        }
    }

    fun again() = _state.value.level?.let { start(it) } ?: Unit

    fun changeLevel() {
        loop?.cancel()
        _state.value = SoloHockeyUiState()
    }

    fun move(x: Float, y: Float) = table.moveBottom(x, y)
}
