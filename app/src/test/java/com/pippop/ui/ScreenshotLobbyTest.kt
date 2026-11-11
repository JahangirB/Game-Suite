package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.LobbyBanner
import com.pippop.ui.screens.PlayerRow
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.SeatColors
import org.junit.Test

/** The lobby: a game's banner above the players. */
class ScreenshotLobbyTest : ScreenshotBase() {
    private fun player(name: String, avatar: Avatar?, ready: Boolean, game: GameKind) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null,
        progress = null, finishMs = null, left = false, idleMs = 0, online = true, at = game,
    )

    @Composable
    private fun lobby(game: GameKind, others: List<PlayerInfo>) = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LobbyBanner(game)
        AppCard("Players (up to 4)") {
            PlayerRow(player("Sam", Avatar.FOX, true, game), isMe = true, color = AppTheme.palette.primary, reaction = null, seat = 0, game = game)
            others.forEachIndexed { i, o -> PlayerRow(o, isMe = false, color = SeatColors[i % SeatColors.size], reaction = null, seat = i + 1, game = game) }
        }
        LobbyBanner(if (game == GameKind.SHIPS) GameKind.CONNECT4 else GameKind.SHIPS)
    }

    @Composable
    private fun ships() = lobby(GameKind.SHIPS, listOf(player("Mia", Avatar.PANDA, true, GameKind.SHIPS)))

    @Composable
    private fun sudoku() = lobby(
        GameKind.SUDOKU,
        listOf(player("Mia", Avatar.PANDA, true, GameKind.SUDOKU), player("Leo", null, false, GameKind.SUDOKU)),
    )

    @Test fun lobbyShipsLight() = shot("lobby-ships", dark = false) { ships() }

    @Test fun lobbyShipsDark() = shot("lobby-ships", dark = true) { ships() }

    @Test fun lobbySudokuLight() = shot("lobby-sudoku", dark = false) { sudoku() }

    @Test fun lobbySudokuDark() = shot("lobby-sudoku", dark = true) { sudoku() }
}
