package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.game.dominoes.DominoOptions
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.SlideView
import com.pippop.data.WordsView
import com.pippop.game.slide.SlideLevel
import com.pippop.ui.screens.SlideLevelCard
import com.pippop.ui.screens.DotsBoardCard
import com.pippop.ui.screens.NumbersLevelCard
import com.pippop.game.golf.GolfLevel
import com.pippop.game.words.WordLevel
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.Choices
import com.pippop.ui.screens.DominoOptionsCard
import com.pippop.ui.screens.GolfLevelCard
import com.pippop.ui.screens.LobbyFrame
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.RoomOptions
import com.pippop.ui.screens.SetupSheetContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.screens.WordLevelCard
import com.pippop.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 14: every game's options as [com.pippop.ui.screens.OptionRow]s in the lobby, greyed for whoever did not open the game, and in the Change sheet. */
class ScreenshotLobbyRowsTest : ScreenshotBase() {
    private fun player(name: String, avatar: Avatar?, ready: Boolean, game: GameKind) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null,
        progress = null, finishMs = null, left = false, idleMs = 0, online = true, at = game,
    )

    private fun room(game: GameKind, opener: Boolean = true, status: RoomStatus = RoomStatus.LOBBY) = RoomState(
        code = "K7Q2NB", role = if (opener) "host" else "joiner", game = game, status = status, difficulty = null, round = 0, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam", Avatar.FOX, false, game),
        opponent = player("Maya", Avatar.PANDA, false, game), ships = null, turns = null, serverTime = 0,
        others = listOf(player("Maya", Avatar.PANDA, false, game)), maxPlayers = game.maxPlayers, inSession = true,
        options = DominoOptions().takeIf { game == GameKind.DOMINOES },
        golfLevel = GolfLevel.MEDIUM.takeIf { game == GameKind.GOLF },
        slide = SlideView(SlideLevel.MEDIUM, 4, null, emptyList()).takeIf { game == GameKind.SLIDE },
        words = WordsView(tries = 6, level = WordLevel.MEDIUM, mine = emptyList(), theirs = emptyList(), meDone = false, theyDone = false, answer = null).takeIf { game == GameKind.WORDS },
    )

    @Composable
    private fun lobby(room: RoomState) = LobbyFrame(
        BattleUiState(room = room), Profile(name = "Sam", avatar = Avatar.FOX), onLeave = {}, onReady = {}, onReact = {},
    ) {
        when (room.game) {
            GameKind.DOMINOES -> DominoOptionsCard(room, false) {}
            GameKind.WORDS -> WordLevelCard(room, false) {}
            GameKind.GOLF -> GolfLevelCard(room, false) {}
            GameKind.SLIDE -> SlideLevelCard(room, false) {}
            GameKind.DOTS -> DotsBoardCard(room, false) {}
            else -> {}
        }
    }

    @Test fun dominoesLight() = shot("lobby-rows-dominoes", dark = false) { lobby(room(GameKind.DOMINOES)) }
    @Test fun dominoesDark() = shot("lobby-rows-dominoes", dark = true) { lobby(room(GameKind.DOMINOES)) }
    @Test fun golfLight() = shot("lobby-rows-golf", dark = false) { lobby(room(GameKind.GOLF)) }
    @Test fun golfDark() = shot("lobby-rows-golf", dark = true) { lobby(room(GameKind.GOLF)) }
    @Test fun slideLight() = shot("lobby-rows-slide", dark = false) { lobby(room(GameKind.SLIDE)) }
    @Test fun slideDark() = shot("lobby-rows-slide", dark = true) { lobby(room(GameKind.SLIDE)) }
    @Test fun dotsLight() = shot("lobby-rows-dots", dark = false) { lobby(room(GameKind.DOTS)) }
    @Test fun dotsDark() = shot("lobby-rows-dots", dark = true) { lobby(room(GameKind.DOTS)) }

    // A narrow phone: three level choices must not break "Medium".
    @Composable
    private fun narrow() = Box(Modifier.width(360.dp).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GolfLevelCard(room(GameKind.GOLF), false) {}
            NumbersLevelCard(room(GameKind.GOLF), false) {}
        }
    }
    @Test fun narrowLight() = shot("lobby-rows-narrow", dark = false) { narrow() }
    @Test fun narrowDark() = shot("lobby-rows-narrow", dark = true) { narrow() }

    @Test fun wordsLight() = shot("lobby-rows-words", dark = false) { lobby(room(GameKind.WORDS)) }
    @Test fun wordsDark() = shot("lobby-rows-words", dark = true) { lobby(room(GameKind.WORDS)) }

    // Maya opened the game: Sam's rows are greyed under "Maya is setting up".
    @Test fun greyedLight() = shot("lobby-rows-greyed", dark = false) { lobby(room(GameKind.DOMINOES, opener = false)) }
    @Test fun greyedDark() = shot("lobby-rows-greyed", dark = true) { lobby(room(GameKind.DOMINOES, opener = false)) }

    @Composable
    private fun changeSheet(room: RoomState, setup: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        val controls = RoomControls(
            room, null, setup, rematch = {}, newGame = {}, switchTo = {}, games = emptyList(), setupOpen = mutableStateOf(true), busy = false,
        )
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard { SetupSheetContent(room.game, controls, {}) }
        }
    }

    private fun changing() = room(GameKind.DOMINOES, status = RoomStatus.FINISHED)

    @Test fun changeLight() = shot("lobby-rows-change", dark = false) { changeSheet(changing()) { DominoOptionsCard(changing(), false) {} } }
    @Test fun changeDark() = shot("lobby-rows-change", dark = true) { changeSheet(changing()) { DominoOptionsCard(changing(), false) {} } }

    /** The Rules card against a reconstruction of the same settings as labels over full-width choices (the layout other cards used before this step). */
    @Test fun dominoesRulesAreShorter() {
        var now = 0
        var before = 0
        shot("lobby-rows-height", dark = false) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.onGloballyPositioned { now = it.size.height }) { DominoOptionsCard(room(GameKind.DOMINOES), false) {} }
                Box(Modifier.onGloballyPositioned { before = it.size.height }) {
                    // How the card was laid out before: a label, then the choices across the whole width, one setting under another.
                    AppCard("Rules") {
                        listOf("When stuck" to listOf("Draw", "Block"), "Play to" to DominoOptions.TARGETS.map { "$it" }, "Sides" to listOf("Each alone", "2 v 2")).forEach { (label, options) ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label)
                                Choices(options.map { it to it }, options.first(), {})
                            }
                        }
                        Text("Stuck? Take tiles from the boneyard until one fits.")
                    }
                }
            }
        }
        println("DOMINOES_RULES_HEIGHT_PX new=$now reconstructedLabelAbove=$before")
        assertTrue("$now vs $before", now > 0 && now < before)
    }
}
