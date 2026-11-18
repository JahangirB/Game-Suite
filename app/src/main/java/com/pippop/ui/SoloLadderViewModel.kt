package com.pippop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.data.GameKind
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecordStore
import com.pippop.game.words.LadderComputer
import com.pippop.game.words.LadderLevel
import com.pippop.game.words.LadderMatch
import com.pippop.game.words.LadderOptions
import com.pippop.game.words.LadderView
import com.pippop.game.words.WordLadder
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
import kotlin.random.Random

/** Word Ladder against the computer. Marks: 'm' you, 'o' the computer. [view] is the game as you see it; null until one starts. */
data class SoloLadderUiState(
    val level: LadderLevel = LadderLevel.MEDIUM,
    val length: Int = 5,
    val view: LadderView? = null,
    /** Why the last pick or move was refused, in the rule's words, until it is dismissed. */
    val problem: String? = null,
    /** Goes up with each game, so a screen can tell this game from the last (its coin flip shows again). */
    val gameId: Int = 0,
)

class SoloLadderViewModel(private val random: Random = Random.Default, private val thinkMs: () -> Long = { 800L + random.nextLong(700) }) : ViewModel() {
    private var match: LadderMatch? = null
    private var job: Job? = null
    private val _state = MutableStateFlow(SoloLadderUiState())
    val state: StateFlow<SoloLadderUiState> = _state.asStateFlow()

    fun start(level: LadderLevel, length: Int) {
        job?.cancel()
        val m = LadderMatch(LadderOptions.clean(length, 0), listOf('m', 'o')) { random.nextDouble() }
        match = m
        recorded = null
        _state.value = SoloLadderUiState(level, m.length, viewOf(m), gameId = _state.value.gameId + 1)
    }

    fun rematch() = start(_state.value.level, _state.value.length)

    fun dismissProblem() = _state.update { it.copy(problem = null) }

    /** Locks in your secret word; the computer then picks its own and the coin flip follows. */
    fun pick(word: String) {
        val m = match ?: return
        if (m.revealed || job?.isActive == true) return
        // The computer needs a word to join yours to: a word no other word changes into leaves it nothing to pick.
        if (WordLadder.distancesFrom(word.uppercase()).size < 2 && WordLadder.isWord(word.uppercase())) {
            _state.update { it.copy(problem = "No way from ${word.uppercase()} to another word. Pick another.") }
            return
        }
        try {
            m.setPick('m', word)
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(problem = e.message) }
            return
        }
        _state.update { it.copy(view = viewOf(m), problem = null) }
        job = viewModelScope.launch {
            delay(thinkMs())
            val mine = word.uppercase()
            val theirs = withContext(Dispatchers.Default) { LadderComputer.pick(_state.value.level, mine, random) }
            m.setPick('o', theirs)
            afterEach(m)
        }
    }

    /** Plays your move: [word] is the last word with one letter changed. */
    fun move(word: String) {
        val m = match ?: return
        if (!m.revealed || m.over || m.turn != 'm') return
        try {
            m.play('m', JSONObject().put("word", word))
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(problem = e.message) }
            return
        }
        afterEach(m)
        if (!m.over) computerMoves(m)
    }

    /** The coin flip has been seen: if the computer moves first it starts thinking now. */
    fun flipDone() {
        val m = match ?: return
        if (m.revealed && !m.over && m.turn == 'o' && job?.isActive != true) computerMoves(m)
    }

    private fun computerMoves(m: LadderMatch) {
        job = viewModelScope.launch {
            while (!m.over && m.turn == 'o') {
                delay(thinkMs())
                val chain = m.words
                val word = withContext(Dispatchers.Default) { LadderComputer.move(_state.value.level, chain, m.target.orEmpty(), random) }
                // A computer with no move has lost: the game says so when its turn comes round (it ended the game already).
                if (word == null) return@launch
                m.play('o', JSONObject().put("word", word))
                afterEach(m)
            }
        }
    }

    private var recorded: LadderMatch? = null

    /** Shows the game as it now is; a game that has just ended is recorded once. */
    private fun afterEach(m: LadderMatch) {
        _state.update { it.copy(view = viewOf(m)) }
        if (m.over && recorded !== m) {
            recorded = m
            SoloRecordStore.current?.recordRun(GameKind.LADDER, _state.value.level.key, SoloMetric.WINS_IN_ROW, m.winner == 'm')
        }
    }

    private fun viewOf(m: LadderMatch) = LadderView.from(m.view('m', mapOf('o' to 'o')))
}
