package com.pippop.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.TurnsView
import com.pippop.ui.screens.AnotherGame
import com.pippop.ui.screens.AnotherGameContent
import com.pippop.ui.screens.LocalMatchSession
import com.pippop.ui.screens.LocalRoomControls
import com.pippop.ui.screens.LocalTonight
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MatchSession
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.screens.TurnGameScreen
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.tonightScore
import com.pippop.ui.screens.whoPicks
import com.pippop.ui.theme.AppTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * "Another game" over a finished Connect Four, as the screen shows it. The sheet's own window is not in
 * the test's root, so its card is drawn on a dim layer, as the other sheet screenshots do.
 */
class ScreenshotAnotherGameTest : ScreenshotBase() {
    private fun player(name: String, avatar: Avatar) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = true, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = GameKind.CONNECT4,
    )

    private val board = listOf(".......", ".......", "...o...", "..mo...", ".moom..", "mmmmooo").joinToString("")

    private fun room(winner: String?) = RoomState(
        code = "K7Q2NB", role = "host", game = GameKind.CONNECT4, status = RoomStatus.FINISHED, difficulty = null, round = 6, startAt = null,
        puzzle = null, solution = null, winner = winner, endReason = if (winner == null) "draw" else "four", me = player("Sam", Avatar.FOX),
        opponent = player("Maya", Avatar.PANDA), ships = null,
        turns = TurnsView(turn = null, board = board, winLine = listOf(35, 36, 37, 38), lastMoveByMe = winner == "me", lastCell = 38),
        serverTime = 0, setsOptions = true, others = listOf(player("Maya", Avatar.PANDA)), inSession = true,
        winnerName = if (winner == "opponent") "Maya" else null,
    )

    private val shelf = listOf(GameKind.LUDO, GameKind.WORDS, GameKind.SHIPS, GameKind.DOMINOES, GameKind.CLASH, GameKind.GOLF) +
        GameKind.entries.filter { it != GameKind.CONNECT4 && it.maxPlayers >= 2 && it !in listOf(GameKind.LUDO, GameKind.WORDS, GameKind.SHIPS, GameKind.DOMINOES, GameKind.CLASH, GameKind.GOLF) }

    @Composable
    private fun Over(r: RoomState, allOpen: Boolean, sheet: Boolean = true) {
        val tally = when (r.winner) {
            "me" -> mapOf(TALLY_ME to 3, "Maya" to 2)
            "opponent" -> mapOf(TALLY_ME to 2, "Maya" to 3)
            else -> mapOf(TALLY_ME to 2, "Maya" to 2)
        }
        val controls = RoomControls(
            room = r, mode = null, setup = {}, rematch = {}, newGame = {}, switchTo = {},
            games = emptyList(), setupOpen = mutableStateOf(false), busy = false,
        )
        val vm = BattleViewModel(ApplicationProvider.getApplicationContext<Application>())
        CompositionLocalProvider(
            LocalMatchSession provides MatchSession(tally, shelf, {}),
            LocalRoomControls provides controls,
            LocalTonight provides tonightScore(r, tally),
            LocalTurnHints provides TurnHints(),
        ) {
            TurnGameScreen(BattleUiState(room = r, tally = tally), vm, onLeave = {})
            if (sheet) Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
                ToySheetCard {
                    AnotherGameContent(AnotherGame(GameKind.CONNECT4, shelf, emptySet(), 2, whoPicks(r), {}), allOpen) {}
                }
            }
        }
    }

    @Test fun theyLostLight() = shot("another-game-they-lost", dark = false) { Over(room("me"), false) }
    @Test fun theyLostDark() = shot("another-game-they-lost", dark = true) { Over(room("me"), false) }
    @Test fun youLostLight() = shot("another-game-you-lost", dark = false) { Over(room("opponent"), false) }
    @Test fun youLostDark() = shot("another-game-you-lost", dark = true) { Over(room("opponent"), false) }
    @Test fun drawLight() = shot("another-game-draw", dark = false) { Over(room(null), false) }
    @Test fun drawDark() = shot("another-game-draw", dark = true) { Over(room(null), false) }
    @Test fun allGamesLight() = shot("another-game-all", dark = false) { Over(room("me"), true) }
    @Test fun allGamesDark() = shot("another-game-all", dark = true) { Over(room("me"), true) }

    /** The card with Change at 360 dp and text 1.3 times larger: every label on one line (no word breaks) and 48 dp targets. */
    @Test @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    fun cardAtLargeText() {
        shot("another-game-card-360-large", dark = false) {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) { Over(room("me"), false, sheet = false) }
        }
        for (label in listOf("Change", "Another game")) {
            val n = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue("$label is ${n.height} tall", n.height >= 48.dp)
        }
        // A word that broke across lines would make the button taller than one line of text plus its padding.
        val another = compose.onNodeWithText("Another game").getUnclippedBoundsInRoot()
        assertTrue("Another game wraps: ${another.height}", another.height < 64.dp)
    }
}
