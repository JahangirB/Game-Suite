package com.pippop.ui

import androidx.compose.runtime.Composable
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RecentGame
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.SavedPartner
import com.pippop.game.BoardState
import com.pippop.game.SoloGame
import com.pippop.ui.screens.HomeScreen
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * The home while connected: the room bar (faces, names, the room, Leave, and what is happening), the
 * seats on every tile, games that cannot take everyone faded, and the face of someone in a game.
 * Two players with Maya waiting in Ludo (green ring, face, Join); three with nobody in a game
 * (Ship Battle, Connect Four and Tic-Tac-Toe fade as "2 players"). No scores on tiles.
 */
class ScreenshotHomeRoomTest : ScreenshotBase() {
    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    private fun saved(): SoloGame {
        val sol = solution.map { it.digitToInt() }
        var board = BoardState.fromPuzzle(puzzle)
        board.cells.indices.filter { !board.givens[it] }.take(14).forEach { board = board.place(it, sol[it]) }
        return SoloGame(Difficulty.MEDIUM, puzzle, sol, board, elapsedMs = 38 * 60_000L + 12_000L)
    }

    private fun player(name: String, avatar: Avatar, at: GameKind?) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = at,
    )

    private fun room(game: GameKind, vararg others: PlayerInfo) = RoomState(
        code = "K7Q2NB", role = "host", game = game, status = RoomStatus.LOBBY, difficulty = null, round = 0, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam", Avatar.FOX, null),
        opponent = others.firstOrNull(), ships = null, turns = null, serverTime = 0, others = others.toList(), inSession = true,
    )

    private val day = 24 * 60 * 60 * 1000L

    @Composable
    private fun home(room: RoomState) {
        val profile = Profile(
            name = "Sam", avatar = Avatar.FOX, playMode = PlayMode.ONLINE, gameOrder = HomeOrder + PutAway, putAway = PutAway.toSet(),
            recentGames = listOf(RecentGame(GameKind.LUDO, NOW - day), RecentGame(GameKind.WORDS, NOW - 4 * day)),
            partner = SavedPartner("https://example.com/x", "", "K7Q2NB", "Maya", Avatar.PANDA, "device", "me"),
        )
        HomeScreen(
            solo = SoloUiState(game = saved()), profile = profile, onContinue = {}, onNewGame = { _, _ -> }, onProfile = {}, onSolo = {},
            onArrange = {}, onScores = {}, partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {},
            connected = true, onConnect = {}, room = room, now = NOW,
        )
    }

    private fun two() = room(GameKind.LUDO, player("Maya", Avatar.PANDA, GameKind.LUDO))
    private fun three() = room(GameKind.LUDO, player("Maya", Avatar.PANDA, null), player("Leo", Avatar.OWL, null))

    @Test fun twoLight() = shot("home2-new", dark = false) { home(two()) }

    @Test fun twoDark() = shot("home2-new", dark = true) { home(two()) }

    @Test fun threeLight() = shot("home2-three", dark = false) { home(three()) }

    @Test fun threeDark() = shot("home2-three", dark = true) { home(three()) }

    private companion object {
        val NOW: Long = ZonedDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val HomeOrder = listOf(
            GameKind.SUDOKU, GameKind.SHIPS, GameKind.CONNECT4, GameKind.LUDO, GameKind.WORDS, GameKind.DOMINOES,
            GameKind.CLASH, GameKind.GOLF, GameKind.MEMORY, GameKind.DOTS, GameKind.DRAW, GameKind.TTT,
            GameKind.BINGO, GameKind.HOCKEY, GameKind.CODE, GameKind.BALLOON,
        )
        val PutAway = listOf(GameKind.NUMBERS, GameKind.GRID, GameKind.SLIDE, GameKind.BLOCKS)
    }
}
