package com.arcade.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcade.sudoku.game.bingo.Bingo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Bingo against one to three computer cards. The computers dab every call and shout a little
 * after their line fills, so a quick eye on your own card wins.
 */
data class SoloBingoUiState(
    /** 0 before the first game. */
    val computers: Int = 0,
    val card: List<Int> = emptyList(),
    val computerCards: List<List<Int>> = emptyList(),
    val calls: List<Int> = emptyList(),
    val called: Int = 0,
    val dabs: Set<Int> = emptySet(),
    /** "me", the computer's number (1 to 3) as text, or "none" when the calls ran out. */
    val winner: String? = null,
    val line: List<Int>? = null,
    val wins: Int = 0,
) {
    val calledSet get() = calls.take(called).toSet()
    /** A full line of dabbed squares on your card. */
    val myLine get() = Bingo.fullLine(card, dabs.map { card[it] }.toSet())
}

class SoloBingoViewModel : ViewModel() {
    private val _state = MutableStateFlow(SoloBingoUiState())
    val state: StateFlow<SoloBingoUiState> = _state.asStateFlow()
    private var caller: Job? = null

    fun start(computers: Int) {
        caller?.cancel()
        val random = Random.Default
        _state.update {
            SoloBingoUiState(
                computers = computers,
                card = Bingo.dealCard(random),
                computerCards = List(computers) { Bingo.dealCard(random) },
                calls = Bingo.shuffledCalls(random),
                wins = it.wins,
            )
        }
        caller = viewModelScope.launch {
            // A computer shouts this many calls after its line fills.
            val shoutAfter = IntArray(computers) { -1 }
            while (true) {
                delay(if (_state.value.called == 0) 1500 else Bingo.CALL_MS)
                if (_state.value.winner != null) return@launch
                _state.update { it.copy(called = it.called + 1) }
                val s = _state.value
                for (k in 0 until computers) {
                    if (shoutAfter[k] < 0 && Bingo.fullLine(s.computerCards[k], s.calledSet) != null) shoutAfter[k] = s.called + 1 + random.nextInt(2)
                }
                val shouter = (0 until computers).firstOrNull { shoutAfter[it] in 1..s.called }
                if (shouter != null) {
                    _state.update { it.copy(winner = "${shouter + 1}", line = Bingo.fullLine(it.computerCards[shouter], it.calledSet)) }
                    return@launch
                }
                if (s.called >= Bingo.NUMBERS) {
                    delay(Bingo.LAST_CALL_GRACE_MS)
                    _state.update { if (it.winner == null) it.copy(winner = "none") else it }
                    return@launch
                }
            }
        }
    }

    fun again() = start(_state.value.computers.coerceAtLeast(1))

    fun changePlayers() {
        caller?.cancel()
        _state.update { SoloBingoUiState(wins = it.wins) }
    }

    /** Dab (or un-dab) a called number on your card. */
    fun dab(index: Int) {
        val s = _state.value
        if (s.winner != null || index == Bingo.FREE) return
        if (s.card.getOrNull(index) !in s.calledSet) return
        _state.update { it.copy(dabs = if (index in it.dabs) it.dabs - index else it.dabs + index) }
    }

    fun claim() {
        val s = _state.value
        if (s.winner != null) return
        val line = s.myLine ?: return
        caller?.cancel()
        _state.update { it.copy(winner = "me", line = line, wins = it.wins + 1) }
    }
}
