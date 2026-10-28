package com.pippop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.game.golf.Golf
import com.pippop.game.golf.GolfMatch
import com.pippop.game.golf.GolfShot
import com.pippop.game.golf.GolfView
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

/** Mini Golf on your own or against one to three computers. */
data class SoloGolfUiState(
    val view: GolfView? = null,
    val computers: Int = 1,
    /** 0 easy to 2 hard. */
    val skill: Int = 1,
    val turn: Char = 'm',
    /** "me", "computer", "tie" or null while playing. */
    val result: String? = null,
    /** Your best nine-hole total on your own. */
    val best: Int? = null,
)

class SoloGolfViewModel : ViewModel() {
    private var match: GolfMatch? = null
    private var thinking: Job? = null
    private val _state = MutableStateFlow(SoloGolfUiState())
    val state: StateFlow<SoloGolfUiState> = _state.asStateFlow()

    fun setComputers(n: Int) = _state.update { it.copy(computers = n.coerceIn(0, 3)) }

    fun setSkill(n: Int) = _state.update { it.copy(skill = n.coerceIn(0, 2)) }

    fun changeSetup() {
        thinking?.cancel()
        match = null
        _state.update { it.copy(view = null, result = null, turn = 'm') }
    }

    fun start() {
        thinking?.cancel()
        val m = GolfMatch("mopq".take(_state.value.computers + 1).toList())
        match = m
        _state.update { it.copy(view = viewOf(m), turn = 'm', result = null) }
    }

    /** Your shot at [angle] with [power]; the computers follow once it has rolled. */
    fun shoot(angle: Double, power: Double) {
        val s = _state.value
        if (s.turn != 'm' || s.result != null || match == null) return
        val shot = shotFor('m', angle, power) ?: return
        play('m', angle, power, shot)
        if (_state.value.result == null && _state.value.turn != 'm') computersPlay(rollMs(shot.path.size))
    }

    private fun shotFor(mark: Char, angle: Double, power: Double): GolfShot? {
        val m = match ?: return null
        val hole = Golf.hole(m.course, m.hole)
        val from = _state.value.view?.balls?.get(mark) ?: hole.tee
        return Golf.simulate(hole, from, angle, power)
    }

    private fun play(mark: Char, angle: Double, power: Double, shot: GolfShot) {
        val m = match ?: return
        // In the water or over the edge, the ball goes back on the tee.
        val rest = shot.rest(Golf.hole(m.course, m.hole))
        val move = JSONObject().put("angle", angle).put("power", power).put("x", rest.x).put("y", rest.y).put("holed", shot.holed)
        val result = m.play(mark, move)
        _state.update {
            val over = result.over
            val total = m.total('m')
            it.copy(
                view = viewOf(m),
                turn = result.turn ?: mark,
                result = if (!over) null else when (result.winner) { 'm' -> "me"; null -> "tie"; else -> "computer" },
                best = if (over && it.computers == 0) minOf(it.best ?: total, total) else it.best,
            )
        }
    }

    private fun computersPlay(firstWait: Long) {
        thinking = viewModelScope.launch {
            var wait = firstWait
            while (_state.value.result == null && _state.value.turn != 'm') {
                delay(wait + 500)
                val m = match ?: return@launch
                val mark = _state.value.turn
                val hole = Golf.hole(m.course, m.hole)
                val from = _state.value.view?.balls?.get(mark) ?: hole.tee
                val skill = listOf(0.3, 0.6, 0.9)[_state.value.skill]
                val (angle, power) = withContext(Dispatchers.Default) { Golf.computerShot(hole, from, skill) }
                val shot = Golf.simulate(hole, from, angle, power)
                play(mark, angle, power, shot)
                wait = rollMs(shot.path.size)
            }
        }
    }

    private fun viewOf(m: GolfMatch) = GolfView.from(m.view('m', mapOf('o' to 'o', 'p' to 'p', 'q' to 'q')))

    companion object {
        /** How long a shot's roll plays on screen: the path has about 60 points a second. */
        fun rollMs(points: Int) = points * 1000L / 60
    }
}
