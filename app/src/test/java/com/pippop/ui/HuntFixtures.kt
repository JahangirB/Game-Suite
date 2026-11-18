package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.nearby.LocalRoom
import com.pippop.data.parseRoom
import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntMatch
import com.pippop.game.words.HuntOptions
import com.pippop.game.words.HuntWord
import com.pippop.game.words.WordHuntThemes
import com.pippop.game.words.WordLadder
import com.pippop.ui.screens.HuntActions
import com.pippop.ui.screens.HuntGameScreen
import com.pippop.ui.screens.Tone
import org.json.JSONObject
import kotlin.random.Random

/**
 * A Word Hunt round between Sam (this phone, the host), Maya and Priya, run by the nearby room's own rules, so a screen is shown
 * positions the real game makes. The grid is the one seed 0 makes (the room's random gives 0), and [match] is its twin, to look
 * words up in. [now] is the time the screens are shown at.
 */
class HuntTable(
    val level: HuntLevel = HuntLevel.EASY, val backwards: Boolean = false, val timer: Int = 5,
    players: Int = 3, val t0: Long = 1_000_000L,
) {
    val room = LocalRoom("HUNT", "Sam", Avatar.FOX, GameKind.HUNT)
    val go = t0 + LocalRoom.COUNTDOWN_MS
    private val roles = listOf(LocalRoom.HOST, LocalRoom.GUEST, LocalRoom.P3).take(players)

    /** The time Sam's screen is shown at; claims made through [claim] go in a little after the countdown, each later than the one before. */
    var now: Long = go + 1000
    private var at = go + 10

    init {
        installWords()
        if (players >= 2) room.join("Maya", Avatar.PANDA, null, listOf("hunt"))
        if (players >= 3) room.join("Priya", Avatar.OWL, null, listOf("hunt"))
        roles.forEach { room.setConnected(it, true, t0) }
        room.setOptions(LocalRoom.HOST, JSONObject().put("level", level.key).put("backwards", backwards).put("timer", timer))
        val zero = object : Random() { override fun nextBits(bitCount: Int) = 0 }
        roles.forEach { room.setTurnReady(it, true, t0, zero) }
    }

    val marks = roles.map { LocalRoom.MARKS.getValue(it) }
    val match = HuntMatch(HuntOptions(level, backwards, timer, 0), marks, startAt = go)

    /** The hidden words of the grid, in the room's own order. */
    val words: List<HuntWord> get() = match.grid.words
    fun word(text: String) = words.first { it.word == text }

    /** The room as Sam sees it now. */
    fun sam(): RoomState = parseRoom(room.view(LocalRoom.HOST, now))

    /** [role] drags [text] from its first letter to its last (or the other way round). */
    fun claim(role: String, text: String, reverse: Boolean = false) {
        val (a, b) = HuntMatch.ends(word(text))
        val (s, e) = if (reverse) b to a else a to b
        room.move(role, JSONObject().put("r1", s.first).put("c1", s.second).put("r2", e.first).put("c2", e.second), ++at)
    }

    fun hint(role: String) = room.move(role, JSONObject().put("hint", true), ++at)

    companion object {
        /** The word lists, as the app loads them at start. */
        fun installWords() {
            fun read(name: String) = java.io.File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }
            WordLadder.install(read("ladder"), read("blocklist"))
            WordHuntThemes.install(read("hunt"))
        }
    }
}

/** What the app puts round a game against Maya and Priya (tonight's score, the Change sheet, the Another-game strip). */
@Composable
fun HuntSession(room: RoomState, content: @Composable () -> Unit) {
    val tally = mapOf(com.pippop.ui.screens.TALLY_ME to 2, "Maya" to 1)
    val controls = com.pippop.ui.screens.RoomControls(
        room = room, mode = null, setup = if (com.pippop.ui.screens.hasOptions(room)) ({}) else null, rematch = {}, newGame = {}, switchTo = {},
        games = emptyList(), setupOpen = mutableStateOf(false), busy = false,
    )
    CompositionLocalProvider(
        com.pippop.ui.screens.LocalMatchSession provides com.pippop.ui.screens.MatchSession(tally, listOf(GameKind.WORDS, GameKind.GRID), {}),
        com.pippop.ui.screens.LocalRoomControls provides controls,
        com.pippop.ui.screens.LocalTonight provides com.pippop.ui.screens.tonightScore(room, tally),
        com.pippop.ui.screens.LocalTurnHints provides com.pippop.ui.screens.TurnHints(),
    ) { content() }
}

/**
 * The Word Hunt screen standing on a [HuntTable]: Sam's drags and taps go to the table (its refusals come back as the toast message,
 * as the app's would), and [refresh] shows the table as it now is, after the others have moved.
 */
class HuntHarness(val table: HuntTable) {
    val state = mutableStateOf(BattleUiState(room = table.sam()))
    val claims = mutableListOf<List<Int>>()
    var hints = 0

    fun refresh() { state.value = BattleUiState(room = table.sam()) }

    private fun act(block: () -> Unit) {
        try {
            block()
            refresh()
        } catch (e: ApiException) {
            state.value = state.value.copy(message = e.message, messageTone = Tone.Bad)
        }
    }

    val actions = HuntActions(
        claim = { r1, c1, r2, c2 ->
            claims += listOf(r1, c1, r2, c2)
            act { table.room.move(LocalRoom.HOST, JSONObject().put("r1", r1).put("c1", c1).put("r2", r2).put("c2", c2), table.now) }
        },
        hint = { hints++; act { table.room.move(LocalRoom.HOST, JSONObject().put("hint", true), table.now) } },
        rematch = {},
        dismissMessage = { state.value = state.value.copy(message = null) },
    )

    @Composable
    fun Screen(vm: BattleViewModel) {
        val s = state.value
        HuntSession(s.room!!) { HuntGameScreen(s, vm, onLeave = {}, actions = actions, clock = { table.now }) }
    }
}

/** Shared set-up for the Word Hunt tests: the screen in light or dark with motion off, and the hand's drags and taps on its grid. */
abstract class HuntTestBase : ScreenshotBase() {
    protected fun vm() = BattleViewModel(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>())

    protected fun show(harness: HuntHarness, dark: Boolean = false) {
        val vm = vm()
        compose.setContent {
            com.pippop.ui.theme.PipPopTheme(darkTheme = dark) {
                CompositionLocalProvider(com.pippop.ui.theme.LocalReduceMotion provides true) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(com.pippop.ui.theme.AppTheme.palette.background),
                    ) { harness.Screen(vm) }
                }
            }
        }
    }

    /** Where the middle of [row], [col] is on the grid as it is shown whole (not zoomed), in the grid node's own pixels. */
    protected fun centre(rule: ComposeContentTestRule, table: HuntTable, row: Int, col: Int, view: com.pippop.game.words.HuntView? = null): Offset {
        val size = rule.onNodeWithTag("huntGrid").fetchSemanticsNode().size
        val w = table.level.width
        val h = table.level.height
        val cell = minOf(size.width.toFloat() / w, size.height.toFloat() / h)
        return Offset((size.width - cell * w) / 2f + (col + 0.5f) * cell, (size.height - cell * h) / 2f + (row + 0.5f) * cell)
    }

    protected fun dragWord(table: HuntTable, text: String, reverse: Boolean = false) {
        val (a, b) = HuntMatch.ends(table.word(text))
        val from = if (reverse) b else a
        val to = if (reverse) a else b
        val start = centre(compose, table, from.first, from.second)
        val end = centre(compose, table, to.first, to.second)
        compose.onNodeWithTag("huntGrid").performTouchInput { down(start); moveTo((start + end) / 2f); moveTo(end); up() }
        compose.waitForIdle()
    }

    protected fun tapCell(table: HuntTable, row: Int, col: Int) {
        val at = centre(compose, table, row, col)
        compose.onNodeWithTag("huntGrid").performTouchInput { click(at) }
        compose.waitForIdle()
    }
}
