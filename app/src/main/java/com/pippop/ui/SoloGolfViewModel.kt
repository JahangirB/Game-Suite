package com.pippop.ui

import com.pippop.data.GameKind
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecordStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.game.golf.Courses
import com.pippop.game.golf.Golf
import com.pippop.game.golf.GolfLevel
import com.pippop.game.golf.GolfMatch
import com.pippop.game.golf.GolfShot
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.ShotKind
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
    /** How many islands every hole has. */
    val level: GolfLevel = GolfLevel.EASY,
    val turn: Char = 'm',
    /** "me", "computer", "tie" or null while playing. */
    val result: String? = null,
    /** Your best nine-hole total on your own. */
    val best: Int? = null,
    /** Counts the games started, so the course knows a new game from the last. */
    val game: Int = 0,
)

class SoloGolfViewModel : ViewModel() {
    private var match: GolfMatch? = null
    private var thinking: Job? = null
    private val _state = MutableStateFlow(SoloGolfUiState())
    val state: StateFlow<SoloGolfUiState> = _state.asStateFlow()

    fun setComputers(n: Int) = _state.update { it.copy(computers = n.coerceIn(0, 3)) }

    fun setSkill(n: Int) = _state.update { it.copy(skill = n.coerceIn(0, 2)) }

    fun setLevel(level: GolfLevel) = _state.update { it.copy(level = level) }

    fun start() {
        thinking?.cancel()
        val m = GolfMatch("mopq".take(_state.value.computers + 1).toList(), Courses.pick(_state.value.level))
        match = m
        _state.update { it.copy(view = viewOf(m), turn = 'm', result = null, game = it.game + 1) }
    }

    /** Your [kind] shot at [angle] with [power]; the computers follow once it has played. */
    fun shoot(kind: ShotKind, power: Double, angle: Double) {
        val s = _state.value
        if (s.turn != 'm' || s.result != null || match == null) return
        val shot = shotFor('m', kind, angle, power) ?: return
        play('m', kind, angle, power, shot)
        if (_state.value.result == null && _state.value.turn != 'm') computersPlay(Golf.playMs(shot))
    }

    private fun shotFor(mark: Char, kind: ShotKind, angle: Double, power: Double): GolfShot? {
        val m = match ?: return null
        val hole = Golf.hole(m.course, m.hole)
        val from = _state.value.view?.balls?.get(mark) ?: hole.tee
        return Golf.simulate(hole, from, kind, angle, power)
    }

    private fun play(mark: Char, kind: ShotKind, angle: Double, power: Double, shot: GolfShot) {
        val m = match ?: return
        // Lost in the sea, a pond or the fire, the ball goes back on the tee.
        val rest = shot.rest(Golf.hole(m.course, m.hole))
        val move = JSONObject().put("kind", kind.key).put("angle", angle).put("power", power).put("x", rest.x).put("y", rest.y)
            .put("holed", shot.holed).put("skip", shot.skip)
        val result = m.play(mark, move)
        if (result.over) {
            // Strokes over the nine holes, when you play them on your own.
            val alone = _state.value.computers == 0
            SoloRecordStore.current?.record(GameKind.GOLF, _state.value.level.key, SoloMetric.STROKES, m.total('m').toLong().takeIf { alone })
        }
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
                // While Kylian has the game, everyone waits.
                while (Takeover.locked()) delay(250)
                val m = match ?: return@launch
                val mark = _state.value.turn
                val hole = Golf.hole(m.course, m.hole)
                val from = _state.value.view?.balls?.get(mark) ?: hole.tee
                val skill = listOf(0.3, 0.6, 0.9)[_state.value.skill]
                val plan = withContext(Dispatchers.Default) { Golf.computerShot(hole, from, skill) }
                val shot = Golf.simulate(hole, from, plan.kind, plan.angle, plan.power)
                play(mark, plan.kind, plan.angle, plan.power, shot)
                wait = Golf.playMs(shot)
            }
        }
    }

    private fun viewOf(m: GolfMatch) = GolfView.from(m.view('m', mapOf('o' to 'o', 'p' to 'p', 'q' to 'q')))
}
