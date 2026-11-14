package com.pippop.ui.screens

import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnotherGameTest {
    private fun player(name: String) = PlayerInfo(
        name = name, avatar = Avatar.FOX, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true,
    )

    private fun room(
        winner: String?, winnerName: String? = null, together: Boolean = false, endReason: String? = null,
        others: List<PlayerInfo> = listOf(player("Maya")), game: GameKind = GameKind.CONNECT4,
    ) = RoomState(
        code = "K7Q2NB", role = "host", game = game, status = RoomStatus.FINISHED, difficulty = null, round = 2, startAt = null,
        puzzle = null, solution = null, winner = winner, endReason = endReason, me = player("Sam"), opponent = others.firstOrNull(), ships = null, turns = null,
        serverTime = 0, others = others, winnerName = winnerName, sudokuTogether = together,
    )

    @Test fun theyLostSoTheyPickButYouMay() {
        val w = whoPicks(room("me"))
        assertEquals("Maya lost that one, so Maya picks. You can still pick if they'd rather.", w.line)
        assertEquals("Maya", w.picker?.name)
        assertFalse(w.mine)
    }

    @Test fun youLostSoYouPick() {
        val w = whoPicks(room("opponent", "Maya"))
        assertEquals("You lost that one, so you pick.", w.line)
        assertTrue(w.mine)
    }

    @Test fun aDrawIsEitherOfYours() {
        val w = whoPicks(room(null))
        assertEquals("A draw, so either of you picks.", w.line)
        assertNull(w.picker)
    }

    @Test fun sudokuTogetherHasNoLoser() {
        assertEquals("A draw, so either of you picks.", whoPicks(room("opponent", null, together = true, game = GameKind.SUDOKU)).line)
        assertEquals("A draw, so either of you picks.", whoPicks(room("me", together = true, game = GameKind.SUDOKU)).line)
        assertEquals("A draw, so either of you picks.", whoPicks(room("opponent", "Maya", together = true, game = GameKind.SUDOKU)).line)
    }

    @Test fun aLossWithNoNameStillCounts() {
        // Apps Script names no winner: a two-player loss is still yours.
        assertEquals("You lost that one, so you pick.", whoPicks(room("opponent", null)).line)
    }

    @Test fun putAwayGamesAreNotTilesButAreInAllGames() {
        val a = AnotherGame(GameKind.CONNECT4, listOf(GameKind.LUDO, GameKind.WORDS), setOf(GameKind.LUDO), 2, whoPicks(room(null))) {}
        assertEquals(listOf(GameKind.WORDS), a.tiles())
        assertEquals(listOf(GameKind.LUDO, GameKind.WORDS), a.everything())
    }

    @Test fun threeOrFourPlayersSayAnyoneCanPick() {
        val three = listOf(player("Maya"), player("Leo"))
        assertEquals("Anyone can pick.", whoPicks(room("me", others = three)).line)
        assertEquals("Anyone can pick.", whoPicks(room("opponent", "Leo", others = three)).line)
        assertEquals("Anyone can pick.", whoPicks(room(null, others = three + player("Kim"))).line)
    }

    @Test fun aRestartHasNoLoser() {
        assertEquals("A draw, so either of you picks.", whoPicks(room("opponent", "Maya", endReason = "restart")).line)
    }

    @Test fun tilesLeaveOutTheGameJustPlayed() {
        val tiles = anotherGameTiles(GameKind.entries, GameKind.CONNECT4, 2)
        assertFalse(GameKind.CONNECT4 in tiles)
    }

    @Test fun tilesOnlyHoldGamesThatFitTheRoom() {
        val three = anotherGameTiles(GameKind.entries, GameKind.LUDO, 3, max = 100)
        assertTrue(three.isNotEmpty())
        assertTrue(three.all { it.maxPlayers >= 3 })
        assertTrue(GameKind.entries.any { it.maxPlayers == 2 })
        assertEquals(GameKind.entries.filter { it != GameKind.LUDO && it.maxPlayers >= 4 }, anotherGameTiles(GameKind.entries, GameKind.LUDO, 4, max = 100))
    }

    @Test fun tilesAreAtMostSixInTheShelfsOrder() {
        val order = GameKind.entries.reversed()
        val tiles = anotherGameTiles(order, GameKind.CONNECT4, 2)
        assertEquals(ANOTHER_TILES, tiles.size)
        assertEquals(order.filter { it != GameKind.CONNECT4 }.take(6), tiles)
        assertEquals(2, anotherGameTiles(listOf(GameKind.LUDO, GameKind.WORDS), GameKind.CONNECT4, 2).size)
    }
}
