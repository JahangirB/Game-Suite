package com.pippop.ui

import androidx.compose.runtime.Composable
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.parseRoom
import com.pippop.data.nearby.LocalRoom
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.LevelPicker
import com.pippop.ui.screens.LobbyFrame
import com.pippop.ui.screens.SudokuModeCard
import com.pippop.ui.screens.SudokuOpenerCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.SetupSheetContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sudoku lobby. Where everyone in the room has the "sudoku-opener" token the opener picks Race or
 * Together, the level and the board and the others tap Ready; otherwise today's level buttons stay.
 */
class ScreenshotLobbySudokuTest : ScreenshotBase() {
    private fun player(name: String, avatar: Avatar?, ready: Boolean) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null,
        progress = null, finishMs = null, left = false, idleMs = 0, online = true, at = GameKind.SUDOKU,
    )

    /** The room as the opener (Sam) sees it, or as Maya, who only taps Ready. */
    private fun room(opener: Boolean, picks: Boolean = true) = RoomState(
        code = "K7Q2NB", role = if (opener) "host" else "guest", game = GameKind.SUDOKU, status = RoomStatus.LOBBY, difficulty = null, round = 0,
        startAt = null, puzzle = null, solution = null, winner = null, endReason = null,
        me = if (opener) player("Sam", Avatar.FOX, false) else player("Maya", Avatar.PANDA, true),
        opponent = if (opener) player("Maya", Avatar.PANDA, true) else player("Sam", Avatar.FOX, false),
        ships = null, turns = null, serverTime = 0,
        others = listOf(if (opener) player("Maya", Avatar.PANDA, true) else player("Sam", Avatar.FOX, false)),
        maxPlayers = 4, setsOptions = opener, inSession = true, sudokuModes = true, sudokuShapes = true,
        caps = listOf("sudoku-opener"), sudokuOpener = picks, sudokuLevel = Difficulty.HARD,
    )

    @Composable
    private fun lobby(room: RoomState) = LobbyFrame(
        BattleUiState(room = room), Profile(name = room.me.name, avatar = room.me.avatar), onLeave = {},
        onReady = if (room.picksByOpener) ({}) else null, onReact = {},
    ) {
        if (room.picksByOpener) SudokuOpenerCard(room, false, {}, {}, {})
        else {
            SudokuModeCard(room, false, {})
            AppCard("Choose the level") { LevelPicker(room, false) {} }
        }
    }

    // The Change sheet a result opens, where the opener picks: the same rows.
    @Composable
    private fun changeSheet(room: RoomState) {
        val controls = RoomControls(
            room, null, { SudokuOpenerCard(room, false, {}, {}, {}) }, rematch = {}, newGame = {}, switchTo = {}, games = emptyList(),
            setupOpen = mutableStateOf(true), busy = false,
        )
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard { SetupSheetContent(room.game, controls, {}) }
        }
    }

    @Test fun changeLight() = shot("lobby-sudoku-change", dark = false) { changeSheet(room(opener = true).copy(status = RoomStatus.FINISHED)) }

    @Test fun changeDark() = shot("lobby-sudoku-change", dark = true) { changeSheet(room(opener = true).copy(status = RoomStatus.FINISHED)) }

    @Test fun openerLight() = shot("lobby-sudoku-new", dark = false) { lobby(room(opener = true)) }

    @Test fun openerDark() = shot("lobby-sudoku-new", dark = true) { lobby(room(opener = true)) }

    @Test fun othersLight() = shot("lobby-sudoku-others", dark = false) { lobby(room(opener = false)) }

    @Test fun othersDark() = shot("lobby-sudoku-others", dark = true) { lobby(room(opener = false)) }

    // Someone is on an older app: today's level buttons.
    @Test fun fallbackLight() = shot("lobby-sudoku-taps", dark = false) { lobby(room(opener = true, picks = false)) }

    @Test fun fallbackDark() = shot("lobby-sudoku-taps", dark = true) { lobby(room(opener = true, picks = false)) }

    @Test fun theRoomSaysWhichLobbyToShow() {
        val old = JSONObject().put("ok", true).put("game", "sudoku").put("role", "host")
        // A server that sends nothing: no caps, today's lobby.
        val none = parseRoom(roomJson(old))
        assertEquals(emptyList<String>(), none.caps)
        assertFalse(none.picksByOpener)
        // The cap alone is not enough: the room must be in opener mode.
        val capOnly = parseRoom(roomJson(old).put("caps", org.json.JSONArray(listOf("sudoku-opener"))))
        assertEquals(listOf("sudoku-opener"), capOnly.caps)
        assertFalse(capOnly.picksByOpener)
        val on = parseRoom(roomJson(old).put("caps", org.json.JSONArray(listOf("sudoku-opener"))).put("sudokuOpener", true)
            .put("options", JSONObject().put("mode", "race").put("shape", "classic").put("level", "hard")))
        assertTrue(on.picksByOpener)
        assertEquals(Difficulty.HARD, on.sudokuLevel)
    }

    @Test fun nearbyRoomsAnswerTheSameWay() {
        val r = LocalRoom("NEAR1", "Sam", Avatar.FOX)
        r.join("Maya", Avatar.PANDA, null, listOf("sudoku", LocalRoom.SUDOKU_OPENER))
        assertTrue(parseRoom(r.view(LocalRoom.GUEST, 0)).picksByOpener)
        val old = LocalRoom("NEAR2", "Sam", Avatar.FOX)
        old.join("Maya", Avatar.PANDA, null, listOf("sudoku"))
        val view = parseRoom(old.view(LocalRoom.GUEST, 0))
        assertFalse(view.picksByOpener)
        assertEquals(listOf("sudoku-opener"), view.caps)
        assertEquals(null, view.sudokuLevel)
    }

    private fun roomJson(base: JSONObject): JSONObject = base
        .put("code", "K7Q2NB").put("version", 1).put("status", "lobby").put("round", 0).put("serverTime", 0)
        .put("me", JSONObject().put("name", "Sam")).put("opponent", JSONObject().put("name", "Maya"))
}
