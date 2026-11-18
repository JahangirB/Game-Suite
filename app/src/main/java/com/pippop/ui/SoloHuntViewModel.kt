package com.pippop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.data.GameKind
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecordStore
import com.pippop.game.words.HuntComputer
import com.pippop.game.words.HuntMatch
import com.pippop.game.words.HuntOptions
import com.pippop.game.words.HuntSkill
import com.pippop.game.words.HuntView
import com.pippop.game.words.WordHuntThemes
import com.pippop.game.words.WordLadder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The two ways to play Word Hunt alone. */
enum class HuntSoloMode { CLOCK, COMPUTERS }

/**
 * Word Hunt on your own. Marks: 'm' you, then 'a', 'b', 'c' for the computer finders. [view] is the round as you see it (the same
 * view a room sends); null until a game starts. The round's time is [SoloHuntViewModel.gameTime], which stands still while the
 * app is in the background or this game is not on screen.
 */
data class SoloHuntUiState(
    val mode: HuntSoloMode = HuntSoloMode.CLOCK,
    val options: HuntOptions = HuntOptions(),
    val computers: Int = 0,
    val skill: HuntSkill = HuntSkill.MEDIUM,
    val view: HuntView? = null,
    /** The room's words for the last refused drag ("Not a word here"), until the screen takes it. */
    val message: String? = null,
    /** Goes up with each game, so a screen can tell this game from the last (the countdown shows again). */
    val gameId: Int = 0,
    /** The time of a finished clock round with no timer: from the countdown's end to the last word, plus 10 s for a hint. */
    val finishedMs: Long? = null,
) {
    val over get() = view?.phase == "over"
}

/** Marks of the computer finders, in order. */
internal val HUNT_COMPUTER_MARKS = listOf('a', 'b', 'c')

class SoloHuntViewModel(
    private val random: Random = Random.Default,
    /** The real clock (milliseconds); a test gives its own so time can be sped up. */
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private var match: HuntMatch? = null
    private var ticker: Job? = null
    private var finders = emptyList<Job>()
    private val _state = MutableStateFlow(SoloHuntUiState())
    val state: StateFlow<SoloHuntUiState> = _state.asStateFlow()

    // The round's own time: real time since it began, less the time spent away.
    private var base = clock()
    private var pausedAt: Long? = null
    private var pausedTotal = 0L
    private var inForeground = true
    private var onScreen = true
    private var recorded: HuntMatch? = null
    private var lastFindAt = 0L

    /** Milliseconds into the round, counting the countdown. It stands still while paused. */
    fun gameTime(): Long = (pausedAt ?: clock()) - base - pausedTotal

    fun setForeground(on: Boolean) { inForeground = on; syncPause() }

    /** Whether this game is the screen being shown (a game left unfinished while you are at home waits). */
    fun setOnScreen(on: Boolean) { onScreen = on; syncPause() }

    private fun syncPause() {
        val pause = !(inForeground && onScreen)
        if (pause && pausedAt == null) pausedAt = clock()
        if (!pause) pausedAt?.let { pausedTotal += clock() - it; pausedAt = null }
    }

    /**
     * Starts a game. [computers] is 0 against the clock; against computer finders 1 to 3 of [skill]. A new game, or a rematch, cancels
     * whatever the last one still had going (a computer waiting to claim), and shows the countdown again.
     */
    fun start(mode: HuntSoloMode, options: HuntOptions, computers: Int = 0, skill: HuntSkill = HuntSkill.MEDIUM) {
        ticker?.cancel()
        finders.forEach { it.cancel() }
        finders = emptyList()
        if (WordHuntThemes.pool.isEmpty() || !WordLadder.loaded) {
            _state.update { it.copy(message = "Still getting the words ready. Try again in a moment.") }
            return
        }
        val n = if (mode == HuntSoloMode.COMPUTERS) computers.coerceIn(1, 3) else 0
        val marks = listOf('m') + HUNT_COMPUTER_MARKS.take(n)
        val m = HuntMatch(options.copy(seed = null), marks, { random.nextDouble() }, startAt = COUNTDOWN_MS)
        match = m
        recorded = null
        lastFindAt = 0
        base = clock()
        pausedTotal = 0
        pausedAt = if (inForeground && onScreen) null else base
        _state.value = SoloHuntUiState(mode, m.options, n, skill, viewOf(m), gameId = _state.value.gameId + 1)
        // The clock ends a timed round when its time is up, and reveals nothing early: the screen covers the grid until the countdown ends.
        ticker = viewModelScope.launch {
            while (!m.over) {
                delay(TICK_MS)
                if (m.tick(gameTime())) afterEach(m)
            }
        }
        finders = marks.drop(1).map { mark -> viewModelScope.launch { find(m, mark, skill) } }
    }

    /** For tests: the computer finders' jobs. */
    internal fun finderJobs(): List<Job> = finders

    fun rematch() {
        val s = _state.value
        if (s.view != null) start(s.mode, s.options.copy(seed = null), s.computers, s.skill)
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Your drag between two cells, from either end. The rule's own words come back as [SoloHuntUiState.message] when it is refused. */
    fun claim(r1: Int, c1: Int, r2: Int, c2: Int) {
        val m = match ?: return
        try {
            val result = m.claim('m', r1, c1, r2, c2, gameTime())
            if (m.foundBy(result.word) == 'm') lastFindAt = gameTime()
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(message = e.message) }
        }
        afterEach(m)
    }

    /** Spends your one hint. */
    fun hint() {
        val m = match ?: return
        try {
            m.hint('m', gameTime())
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(message = e.message) }
        }
        afterEach(m)
    }

    /** One computer finder: waits out the countdown, then claims a word at its pace until the round is over. */
    private suspend fun find(m: HuntMatch, mark: Char, skill: HuntSkill) {
        waitUntil(COUNTDOWN_MS)
        while (!m.over) {
            waitUntil(gameTime() + HuntComputer.gapMs(skill, random))
            if (m.over) return
            val word = HuntComputer.pick(skill, m.grid.words.filter { m.foundBy(it.word) == null }, random) ?: return
            val (a, b) = HuntMatch.ends(word)
            try {
                m.claim(mark, a.first, a.second, b.first, b.second, gameTime())
            } catch (e: IllegalArgumentException) {
                // Someone found it first, or the time ran out: the next round of the loop sees which.
            }
            afterEach(m)
        }
    }

    /** Waits until the round's own time reaches [target] (it does not run while the game is paused). */
    private suspend fun waitUntil(target: Long) {
        while (gameTime() < target) delay(minOf(TICK_MS, maxOf(1L, target - gameTime())))
    }

    private fun afterEach(m: HuntMatch) {
        if (m !== match) return
        val view = viewOf(m)
        val finished = if (m.over && state.value.mode == HuntSoloMode.CLOCK && m.options.timer == 0) lastFindAt - COUNTDOWN_MS + HINT_PENALTY_MS * (1 - (view.hintsLeft['m'] ?: 1)) else null
        _state.update { it.copy(view = view, finishedMs = finished ?: it.finishedMs) }
        if (m.over && recorded !== m) {
            recorded = m
            record(m, finished)
        }
    }

    /**
     * A finished game is kept once. Against the clock with no timer the best is the fastest time to find every word; with a timer it
     * is the most points inside that time (a level of its own for each timer). Against computers it is the run of wins at that skill.
     */
    private fun record(m: HuntMatch, finishedMs: Long?) {
        val store = SoloRecordStore.current ?: return
        val s = _state.value
        val level = m.options.level.key
        when (s.mode) {
            HuntSoloMode.CLOCK ->
                if (m.options.timer == 0) store.record(GameKind.HUNT, level, SoloMetric.TIME, finishedMs)
                else store.record(GameKind.HUNT, "$level-${m.options.timer}", SoloMetric.POINTS, m.score('m').toLong())
            HuntSoloMode.COMPUTERS -> store.recordRun(GameKind.HUNT, "vs-${s.skill.key}", SoloMetric.WINS_IN_ROW, m.winner == 'm')
        }
    }

    private fun viewOf(m: HuntMatch) = HuntView.from(m.view('m', emptyMap()))

    companion object {
        /** The countdown before the round starts. */
        const val COUNTDOWN_MS = 5_000L
        private const val TICK_MS = 250L
        /** A hint adds this to a time with no timer, so it cannot be used to be quicker. */
        const val HINT_PENALTY_MS = 10_000L
    }
}
