package com.pippop.ui.screens

import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultCardTest {
    private fun player(name: String) = PlayerInfo(
        name = name, avatar = Avatar.FOX, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true,
    )

    private fun room(game: GameKind, winner: String?, winnerName: String? = null, together: Boolean = false, host: Boolean = true, opener: Boolean = false) = RoomState(
        code = "K7Q2NB", role = if (host) "host" else "guest", game = game, status = RoomStatus.FINISHED, difficulty = null, round = 2, startAt = null,
        puzzle = null, solution = null, winner = winner, endReason = null, me = player("Sam"), opponent = player("Maya"), ships = null, turns = null,
        serverTime = 0, winnerName = winnerName, sudokuTogether = together, sudokuOpener = opener, caps = if (opener) listOf("sudoku-opener") else emptyList(),
        setsOptions = host,
    )

    @Test fun cardUsesTheCardsWordsForTheSheetsButtons() {
        assertEquals("Another game", cardFinishLabel("Games"))
        assertEquals("Back to games", cardFinishLabel("Home"))
        assertEquals("Change level", cardFinishLabel("Change level"))
        assertEquals("Done", cardFinishLabel("Done"))
    }

    @Test fun onlyWhatTheTallyCountedGetsAPlusOne() {
        assertEquals(TallyBump.MINE, tallyBump(room(GameKind.CONNECT4, "me")))
        assertEquals(TallyBump.THEIRS, tallyBump(room(GameKind.CONNECT4, "opponent", "Maya")))
        // Draw & Guess's team game: nobody won, nothing counted.
        assertEquals(TallyBump.NONE, tallyBump(room(GameKind.DRAW, null)))
        // Sudoku together out of lives: "opponent" with no name, and a loss is everyone's.
        assertEquals(TallyBump.NONE, tallyBump(room(GameKind.SUDOKU, "opponent", null, together = true)))
        assertEquals(TallyBump.NONE, tallyBump(room(GameKind.SUDOKU, "opponent", "Maya", together = true)))
        assertEquals(TallyBump.NONE, tallyBump(room(GameKind.CONNECT4, "opponent", null)))
    }

    @Test fun changeIsForTheOpenerAndForSudokuOnOlderServers() {
        assertTrue(cardOffersChange(room(GameKind.CONNECT4, "me", host = true)))
        assertFalse(cardOffersChange(room(GameKind.CONNECT4, "me", host = false)))
        // Older server: each player's own level picker is in the Change sheet.
        assertTrue(cardOffersChange(room(GameKind.SUDOKU, "me", host = false)))
        // Opener mode: only the opener.
        assertFalse(cardOffersChange(room(GameKind.SUDOKU, "me", host = false, opener = true)))
        assertTrue(cardOffersChange(room(GameKind.SUDOKU, "me", host = true, opener = true)))
    }
}
