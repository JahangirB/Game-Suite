package com.pippop.ui

import androidx.compose.runtime.Composable
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.ships.Fleet
import com.pippop.ui.screens.DominoOptionsCard
import com.pippop.ui.screens.FleetCard
import com.pippop.ui.screens.LobbyFrame
import com.pippop.ui.screens.readyLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lobby: the game's picture and line, the code, the players, the game's rules and the Ready bar pinned below. */
class ScreenshotLobbyTest : ScreenshotBase() {
    private fun player(name: String, avatar: Avatar?, ready: Boolean, game: GameKind = GameKind.DOMINOES) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null,
        progress = null, finishMs = null, left = false, idleMs = 0, online = true, at = game,
    )

    private fun room(game: GameKind, meReady: Boolean, vararg others: PlayerInfo) = RoomState(
        code = "K7Q2NB", role = "host", game = game, status = RoomStatus.LOBBY, difficulty = null, round = 0, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam", Avatar.FOX, meReady, game),
        opponent = others.firstOrNull(), ships = null, turns = null, serverTime = 0, others = others.toList(),
        maxPlayers = game.maxPlayers, inSession = true,
    )

    @Composable
    private fun lobby(room: RoomState) = LobbyFrame(
        BattleUiState(room = room), Profile(name = "Sam", avatar = Avatar.FOX), onLeave = {}, onReady = if (room.game == GameKind.SUDOKU) null else ({}), onReact = {},
    ) {
        when (room.game) {
            GameKind.DOMINOES -> DominoOptionsCard(room, false) {}
            GameKind.SHIPS -> FleetCard(Fleet.random(), room.me.ready, room.opponent, false, onShuffle = {})
            else -> {}
        }
    }

    // Maya is ready, you are choosing: the Ready button is green.
    private fun joined() = room(GameKind.DOMINOES, false, player("Maya", Avatar.PANDA, true))
    private fun alone() = room(GameKind.DOMINOES, false)
    private fun waiting() = room(GameKind.DOMINOES, true, player("Maya", Avatar.PANDA, false))

    @Test fun lobbyNewLight() = shot("lobby-new", dark = false) { lobby(joined()) }

    @Test fun lobbyNewDark() = shot("lobby-new", dark = true) { lobby(joined()) }

    @Test fun lobbyAloneLight() = shot("lobby-alone", dark = false) { lobby(alone()) }

    @Test fun lobbyAloneDark() = shot("lobby-alone", dark = true) { lobby(alone()) }

    @Test fun lobbyWaitingLight() = shot("lobby-waiting", dark = false) { lobby(waiting()) }

    @Test fun lobbyWaitingDark() = shot("lobby-waiting", dark = true) { lobby(waiting()) }

    @Test fun lobbySudokuLight() = shot("lobby-sudoku", dark = false) { lobby(room(GameKind.SUDOKU, false, player("Maya", Avatar.PANDA, false, GameKind.SUDOKU))) }

    @Test fun lobbySudokuDark() = shot("lobby-sudoku", dark = true) { lobby(room(GameKind.SUDOKU, false, player("Maya", Avatar.PANDA, false, GameKind.SUDOKU))) }

    @Test fun lobbyShipsDark() = shot("lobby-ships", dark = true) { lobby(room(GameKind.SHIPS, false, player("Mia", Avatar.PANDA, true, GameKind.SHIPS))) }

    @Test fun lobbyShipsLight() = shot("lobby-ships", dark = false) { lobby(room(GameKind.SHIPS, false, player("Mia", Avatar.PANDA, true, GameKind.SHIPS))) }

    @Test fun readyWords() {
        val maya = player("Maya", Avatar.PANDA, true)
        val leo = player("Leo", null, false)
        val ana = player("Ana", null, false)
        val ben = player("Ben", null, false)
        // Alone: nothing to tap.
        assertFalse(readyLook(alone()).enabled)
        // Not ready: coral Ready.
        readyLook(room(GameKind.DOMINOES, false, leo)).let { assertEquals("Ready", it.title); assertFalse(it.green); assertNull(it.sub) }
        // The others are ready: green and says so.
        readyLook(joined()).let { assertEquals("Ready", it.title); assertEquals("Maya is ready: tap to start", it.sub); assertTrue(it.green) }
        // Ready and waiting: the names, then "N more", with Cancel.
        readyLook(room(GameKind.DOMINOES, true, maya, leo)).let { assertEquals("Waiting for Leo…", it.title); assertEquals("Cancel", it.sub) }
        assertEquals("Waiting for Leo & Ana…", readyLook(room(GameKind.DOMINOES, true, maya, leo, ana)).title)
        assertEquals("Waiting for 3 more…", readyLook(room(GameKind.DOMINOES, true, leo, ana, ben)).title)
        // Everyone ready.
        assertEquals("Starting…", readyLook(room(GameKind.DOMINOES, true, maya)).title)
    }
}
