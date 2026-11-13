package com.pippop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.ui.art.color
import com.pippop.ui.screens.GameIcons
import com.pippop.ui.screens.BoardArea
import com.pippop.ui.screens.ChipPlayer
import com.pippop.ui.screens.ConnectFourBoard
import com.pippop.ui.screens.DockTool
import com.pippop.ui.screens.GameFrame
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.LocalTonight
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MyDisc
import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.TheirDisc
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.TurnLine
import com.pippop.ui.screens.tonightScore
import com.pippop.ui.theme.seatMark
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pippop.ui.screens.TurnBanner
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.TextSize
import org.junit.Test

/**
 * A game against Maya: tonight's score in the title row (read by GameFrame, not passed by the
 * screen) and "Your turn" on one line. Early on the what-to-do line sits under it; from the third
 * turn it is gone. With three players there is no score.
 */
class ScreenshotGameTonightTest : ScreenshotBase() {
    private val c4 = listOf(".......", ".......", "...o...", "..mo...", ".mooo..", "mmomo.o").joinToString("")

    private fun player(name: String, avatar: Avatar) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = GameKind.CONNECT4,
    )

    private fun room(vararg others: PlayerInfo) = RoomState(
        code = "K7Q2NB", role = "host", game = GameKind.CONNECT4, status = RoomStatus.PLAYING, difficulty = null, round = 6, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam", Avatar.FOX),
        opponent = others.firstOrNull(), ships = null, turns = null, serverTime = 0, others = others.toList(), inSession = true,
    )

    private val maya = player("Maya", Avatar.PANDA)
    private val leo = player("Leo", Avatar.OWL)

    /** [priorTurns] of yours already played, so this is turn priorTurns + 1. */
    @Composable
    private fun game(room: RoomState, priorTurns: Int) {
        val tally = mapOf(TALLY_ME to 2, "Maya" to 2)
        val players = listOf(ChipPlayer("You", Avatar.FOX, MyDisc, "Red", active = true, mark = seatMark(0))) +
            room.others.mapIndexed { i, o -> ChipPlayer(o.name, o.avatar, Color.Unspecified, "Yellow", mark = seatMark(i + 1)) }
        CompositionLocalProvider(
            LocalTonight provides tonightScore(room, tally),
            LocalTurnHints provides TurnHints(turns = priorTurns),
        ) {
            GameFrame(
                title = "Connect Four",
                subtitle = "Game ${room.round}",
                players = players,
                glow = GameKind.CONNECT4.color,
                menu = GameMenu(GameKind.CONNECT4, onRules = {}, onLeave = {}),
                tools = listOf(DockTool("Last move", GameIcons.Eye, enabled = false) {}),
                onReact = {},
            ) {
                TurnLine("Your turn: slide to a column and let go", true, GameKind.CONNECT4.color)
                BoardArea(1f) { size -> ConnectFourBoard(c4, winLine = emptyList(), lastCell = null, onDrop = null, modifier = size) }
            }
        }
    }

    // Target game-new: first turn, hint shown.
    @Test fun earlyLight() = shot("game-new", dark = false) { game(room(maya), priorTurns = 0) }

    @Test fun earlyDark() = shot("game-new", dark = true) { game(room(maya), priorTurns = 0) }

    // Target game-later: third turn, hint gone.
    @Test fun laterLight() = shot("game-later", dark = false) { game(room(maya), priorTurns = 2) }

    @Test fun laterDark() = shot("game-later", dark = true) { game(room(maya), priorTurns = 2) }

    // Three players: no score in the title row.
    @Test fun threeLight() = shot("game-three", dark = false) { game(room(maya, leo), priorTurns = 0) }

    @Test fun threeDark() = shot("game-three", dark = true) { game(room(maya, leo), priorTurns = 0) }

    // A narrow phone (360 dp) at 1.3 x text: Number Cross with its timer and the pill. The title
    // stays on one line and so does the subtitle, which gives way with an ellipsis.
    @Composable
    private fun narrow() {
        val p = com.pippop.ui.theme.AppTheme.palette
        val d = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(d.density, fontScale = 1.3f),
            LocalTonight provides tonightScore(room(maya), mapOf(TALLY_ME to 12, "Maya" to 10)),
        ) {
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                GameFrame(
                    title = "Number Cross",
                    subtitle = "Game 16 · Race to the end",
                    players = listOf(ChipPlayer("You", Avatar.FOX, MyDisc, "12 cells", active = true), ChipPlayer("Maya", Avatar.PANDA, TheirDisc, "10 cells")),
                    glow = GameKind.NUMBERS.color,
                    menu = GameMenu(GameKind.NUMBERS, onRules = {}, onLeave = {}),
                    onReact = {},
                    trailing = { Text("12:34", style = Digits, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
                ) {}
            }
        }
    }

    @Test fun narrowLight() = shot("game-narrow", dark = false) { narrow() }

    @Test fun narrowDark() = shot("game-narrow", dark = true) { narrow() }

    // Ship Battle's own banner, on your turn, first turn (hint shown).
    @Composable
    private fun ships() = CompositionLocalProvider(LocalTurnHints provides TurnHints()) {
        Box(Modifier.padding(16.dp)) { TurnBanner(myTurn = true, opponentName = "Maya") }
    }

    @Test fun shipsLight() = shot("game-ships-turn", dark = false) { ships() }

    @Test fun shipsDark() = shot("game-ships-turn", dark = true) { ships() }
}
