package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.RoomStatus
import com.pippop.data.parseRoom
import com.pippop.game.BoardState
import com.pippop.game.Generator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sudoku together on the Nearby room: the same rules as the server's. */
class LocalCoopTest {
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
    // Six gaps: the first three squares and the last three.
    private val puzzle = "000" + solution.substring(3, 78) + "000"
    private val t0 = 1_000_000L

    private fun together(): LocalRoom = LocalRoom("ABCD", "Alice", Avatar.FOX, generate = { Generator.Result(puzzle, solution) }).apply {
        join("Bob", Avatar.OWL, null)
        setOptions(LocalRoom.HOST, JSONObject().put("mode", "together"))
        setReady(LocalRoom.HOST, true, Difficulty.EASY, t0)
        setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
    }

    private fun LocalRoom.start() = startAt!!
    private fun digit(cell: Int) = solution[cell] - '0'
    private fun wrong(cell: Int) = digit(cell) % 9 + 1

    @Test
    fun theModeIsAnOption() {
        val room = LocalRoom("ABCD", "Alice", null)
        assertEquals("race", room.view(LocalRoom.HOST, t0).getJSONObject("options").getString("mode"))
        val parsed = parseRoom(together().view(LocalRoom.HOST, t0))
        assertTrue(parsed.sudokuModes)
        assertTrue(parsed.together)
        // During the countdown there is no shared grid yet.
        assertNull(parsed.coop)
    }

    @Test
    fun rightDigitsShowInTheirColourOnBothPhones() {
        val room = together()
        val now = room.start()
        assertThrows(ApiException::class.java) { room.coopPlace(LocalRoom.HOST, 0, digit(0), now - 1) }
        assertTrue(room.coopPlace(LocalRoom.HOST, 0, digit(0), now))
        assertTrue(room.coopPlace(LocalRoom.GUEST, 1, digit(1), now))
        assertFalse(room.coopPlace(LocalRoom.GUEST, 0, digit(0), now))
        val mine = parseRoom(room.view(LocalRoom.HOST, now))
        val theirs = parseRoom(room.view(LocalRoom.GUEST, now))
        assertEquals("m1", mine.coop!!.by.take(2))
        assertEquals("1m", theirs.coop!!.by.take(2))
        assertEquals(solution.take(2), mine.coop!!.board.take(2))
        assertEquals(1, mine.me.placed)
        assertEquals(1, mine.opponent!!.placed)
        // Boards sent the race way are ignored; givens can't be filled.
        assertFalse(room.submitBoard(LocalRoom.HOST, solution, now))
        assertFalse(room.coopPlace(LocalRoom.HOST, 10, 1, now))
        assertThrows(ApiException::class.java) { room.coopPlace(LocalRoom.HOST, 81, 1, now) }
        // The phone's board takes the shared digits, keeping notes where squares are still empty.
        val board = BoardState.fromPuzzle(puzzle).toggleNote(2, digit(2)).toggleNote(2, digit(1))
        val merged = board.shared(mine.coop!!.board)
        assertEquals(digit(0), merged.cells[0])
        assertEquals(listOf(digit(2)), merged.noteDigits(2))
        assertFalse(merged.canUndo)
    }

    @Test
    fun threeWrongDigitsLoseForEveryone() {
        val room = together()
        val now = room.start()
        room.coopPlace(LocalRoom.GUEST, 0, wrong(0), now)
        var v = parseRoom(room.view(LocalRoom.HOST, now)).coop!!
        assertEquals(2, v.lives)
        assertEquals('0', v.board[0])
        assertEquals(wrong(0), v.miss!!.digit)
        assertFalse(v.miss!!.mine)
        assertTrue(parseRoom(room.view(LocalRoom.GUEST, now)).coop!!.miss!!.mine)
        room.coopPlace(LocalRoom.HOST, 0, wrong(0), now)
        room.coopPlace(LocalRoom.HOST, 0, wrong(0), now + 5)
        val end = parseRoom(room.view(LocalRoom.HOST, now + 5))
        assertEquals(RoomStatus.FINISHED, end.status)
        assertEquals("outOfLives", end.endReason)
        assertEquals("opponent", end.winner)
        assertEquals("opponent", parseRoom(room.view(LocalRoom.GUEST, now + 5)).winner)
        assertNull(end.winnerName)
        assertEquals("lost", end.coop!!.result)
        assertEquals(5L, end.me.finishMs)
    }

    @Test
    fun fillingTheGridWinsForEveryone() {
        val room = together()
        val now = room.start()
        val gaps = puzzle.indices.filter { puzzle[it] == '0' }
        gaps.forEachIndexed { i, cell -> room.coopPlace(if (i % 2 == 0) LocalRoom.HOST else LocalRoom.GUEST, cell, digit(cell), now + i) }
        for (role in listOf(LocalRoom.HOST, LocalRoom.GUEST)) {
            val v = parseRoom(room.view(role, now + 100))
            assertEquals("me", v.winner)
            assertEquals("solvedTogether", v.endReason)
            assertEquals("Alice & Bob", v.winnerName)
            assertEquals(3, v.me.placed)
            assertEquals(100, v.me.progress)
        }
    }

    @Test
    fun everyoneSeesWhereTheOthersAre() {
        val room = together()
        val now = room.start()
        assertEquals(listOf(-1), parseRoom(room.view(LocalRoom.HOST, now)).coop!!.cursors)
        assertTrue(room.coopCursor(LocalRoom.GUEST, 40, now))
        assertFalse(room.coopCursor(LocalRoom.GUEST, 40, now))
        assertEquals(listOf(40), parseRoom(room.view(LocalRoom.HOST, now)).coop!!.cursors)
        assertThrows(ApiException::class.java) { room.coopCursor(LocalRoom.HOST, 99, now) }
    }
}
