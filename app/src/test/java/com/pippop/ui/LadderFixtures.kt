package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.nearby.LocalRoom
import com.pippop.data.parseRoom
import com.pippop.game.words.WordLadder
import com.pippop.game.words.WordList
import org.json.JSONObject
import kotlin.random.Random

/**
 * A Word Ladder game between Sam (this phone, the host) and Maya, run by the nearby room's own rules, so a
 * screen is shown positions the real game makes. The coin lands at [coin]: below one half Sam's word is the
 * start (Maya owns the target and moves first); from one half up Maya's word is the start and Sam moves first.
 */
class LadderTable(length: Int = 4, timer: Int = 0, coin: Double = 0.99, val now: Long = 1_000_000L) {
    val room = LocalRoom("LADR", "Sam", Avatar.FOX, GameKind.LADDER)

    init {
        installWords()
        room.join("Maya", Avatar.PANDA, null, listOf("ladder"))
        room.setOptions(LocalRoom.HOST, JSONObject().put("length", length).put("timer", timer))
        room.setTurnReady(LocalRoom.HOST, true, now, fixed(coin))
        room.setTurnReady(LocalRoom.GUEST, true, now, fixed(coin))
    }

    private fun fixed(x: Double) = object : Random() {
        override fun nextBits(bitCount: Int) = if (x < 0.5) 0 else -1 ushr (32 - bitCount)
    }

    /** The room as Sam sees it now. */
    fun sam(): RoomState = parseRoom(room.view(LocalRoom.HOST, now))

    fun samPicks(word: String) = room.pickWord(LocalRoom.HOST, word, now)
    fun mayaPicks(word: String) = room.pickWord(LocalRoom.GUEST, word, now)
    fun samPlays(word: String) = room.move(LocalRoom.HOST, JSONObject().put("word", word), now)
    fun mayaPlays(word: String) = room.move(LocalRoom.GUEST, JSONObject().put("word", word), now)

    companion object {
        /** The word lists, as the app loads them at start. */
        fun installWords() {
            fun read(name: String) = java.io.File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }
            WordList.install(read("answers"), read("allowed"))
            WordLadder.install(read("ladder"), read("blocklist"))
        }
    }
}

/** What the app puts round a game against Maya (tonight's score, the Change sheet, the Another-game strip), for a ladder screen under test. */
@androidx.compose.runtime.Composable
fun LadderSession(room: RoomState, content: @androidx.compose.runtime.Composable () -> Unit) {
    val tally = mapOf(com.pippop.ui.screens.TALLY_ME to 2, "Maya" to 1)
    val controls = com.pippop.ui.screens.RoomControls(
        room = room, mode = null, setup = if (com.pippop.ui.screens.hasOptions(room)) ({}) else null, rematch = {}, newGame = {}, switchTo = {},
        games = emptyList(), setupOpen = androidx.compose.runtime.mutableStateOf(false), busy = false,
    )
    androidx.compose.runtime.CompositionLocalProvider(
        com.pippop.ui.screens.LocalMatchSession provides com.pippop.ui.screens.MatchSession(tally, listOf(GameKind.WORDS, GameKind.GRID), {}),
        com.pippop.ui.screens.LocalRoomControls provides controls,
        com.pippop.ui.screens.LocalTonight provides com.pippop.ui.screens.tonightScore(room, tally),
        com.pippop.ui.screens.LocalTurnHints provides com.pippop.ui.screens.TurnHints(),
    ) { content() }
}

/**
 * The ladder screen standing on a [LadderTable]: Sam's taps go to the table (its refusals come back as the toast message, as
 * the app's would), and [refresh] shows the table as it now is, after Maya has moved.
 */
class LadderHarness(val table: LadderTable) {
    val state = androidx.compose.runtime.mutableStateOf(com.pippop.ui.BattleUiState(room = table.sam()))
    val picks = mutableListOf<String>()
    val moves = mutableListOf<String>()

    fun refresh() { state.value = com.pippop.ui.BattleUiState(room = table.sam()) }

    private fun act(block: () -> Unit) {
        try {
            block()
            refresh()
        } catch (e: com.pippop.data.ApiException) {
            state.value = state.value.copy(message = e.message, messageTone = com.pippop.ui.screens.Tone.Bad)
        }
    }

    val actions = com.pippop.ui.screens.LadderActions(
        pick = { w -> picks += w; act { table.samPicks(w) } },
        move = { w -> moves += w; act { table.samPlays(w) } },
        rematch = {},
        dismissMessage = { state.value = state.value.copy(message = null) },
    )

    @androidx.compose.runtime.Composable
    fun Screen(vm: com.pippop.ui.BattleViewModel) {
        val s = state.value
        LadderSession(s.room!!) { com.pippop.ui.screens.LadderGameScreen(s, vm, onLeave = {}, actions = actions) }
    }
}

/** Shared taps for the ladder tests: the Word Race keyboard's keys and the tiles of the word in play. */
abstract class LadderTestBase : ScreenshotBase() {
    protected fun vm() = BattleViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>())

    /** Shows [harness]'s screen in [dark] or light, with motion off so nothing loops while the test waits. */
    protected fun show(harness: LadderHarness, dark: Boolean = false) {
        val vm = vm()
        compose.setContent {
            com.pippop.ui.theme.PipPopTheme(darkTheme = dark) {
                androidx.compose.runtime.CompositionLocalProvider(com.pippop.ui.theme.LocalReduceMotion provides true) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(com.pippop.ui.theme.AppTheme.palette.background),
                    ) { harness.Screen(vm) }
                }
            }
        }
    }

    protected fun key(c: Char) = compose.onAllNodes(hasText(c.toString()) and hasClickAction()).onLast()

    protected fun type(word: String) = word.forEach { key(it).performClick() }

    /** Taps letter [place] (0 for the first) of the word in play, then [letter] on the keyboard. */
    protected fun change(place: Int, current: Char, letter: Char) {
        compose.onNodeWithContentDescription("Letter ${place + 1}, $current", substring = true).performClick()
        key(letter).performClick()
    }

    protected fun refreshOnIdle(harness: LadderHarness, block: () -> Unit = {}) = compose.runOnIdle { block(); harness.refresh() }
}
