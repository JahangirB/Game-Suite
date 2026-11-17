package com.pippop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a finished online or nearby game says besides who won. */
class GameFactsTest {
    private fun player(name: String, finishMs: Long? = null) = PlayerInfo(
        name = name, avatar = null, deviceId = null, emoji = null, ready = true, difficulty = null, progress = null,
        finishMs = finishMs, left = false, idleMs = 0, online = true,
    )

    private fun room(game: GameKind, winner: String?, turns: TurnsView? = null, ships: ShipsView? = null, hockey: HockeyScore? = null, startAt: Long? = 1_000, serverTime: Long = 301_000, me: PlayerInfo = player("Me"), opp: PlayerInfo = player("Maya", 200_000)) = RoomState(
        code = "ABCD", role = "host", game = game, status = RoomStatus.FINISHED, difficulty = null, round = 1, startAt = startAt, puzzle = null, solution = null,
        winner = winner, endReason = null, me = me, opponent = opp, ships = ships, turns = turns, hockey = hockey, serverTime = serverTime, winnerName = if (winner == "opponent") "Maya" else null,
    )

    private fun turns(board: String? = null, boxes: String? = null) = TurnsView(turn = null, board = board, winLine = emptyList(), lastMoveByMe = null, lastCell = null, boxes = boxes)

    @Test fun connectFourCountsTheWinnersDiscs() {
        val board = "mmmm....." + "o".repeat(5) + ".".repeat(28)
        assertEquals(4, GameFacts.of(room(GameKind.CONNECT4, "me", turns(board))).count)
        assertEquals(5, GameFacts.of(room(GameKind.CONNECT4, "opponent", turns(board))).count)
        assertNull(GameFacts.of(room(GameKind.CONNECT4, null, turns(board))).count)
    }

    @Test fun dotsGivesBoxesEachAndTheTimeOnTheClock() {
        val f = GameFacts.of(room(GameKind.DOTS, "me", turns(boxes = "mmmmmmmoooo........")))
        assertEquals(7, f.mine); assertEquals(4, f.theirs); assertEquals(300_000L, f.durationMs)
    }

    @Test fun hockeyGivesGoals() {
        val f = GameFacts.of(room(GameKind.HOCKEY, "opponent", hockey = HockeyScore(3, 7, 7)))
        assertEquals(3, f.mine); assertEquals(7, f.theirs)
    }

    @Test fun sudokuUsesTheWinnersOwnTime() {
        val f = GameFacts.of(room(GameKind.SUDOKU, "opponent"))
        assertEquals(200_000L, f.durationMs)
    }

    @Test fun anImpossibleClockSaysNothing() {
        assertNull(GameFacts.of(room(GameKind.LUDO, "me", serverTime = 1_000 + 20 * 60 * 60 * 1000L)).durationMs)
        assertNull(GameFacts.of(room(GameKind.LUDO, "me", startAt = null)).durationMs)
    }

    @Test fun aGameWithNothingToCountStillGetsATime() {
        val f = GameFacts.of(room(GameKind.TTT, "me"))
        assertNull(f.mine); assertNull(f.count); assertEquals(300_000L, f.durationMs)
    }
}
