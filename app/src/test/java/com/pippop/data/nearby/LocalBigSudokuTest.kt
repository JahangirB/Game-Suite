package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.RoomStatus
import com.pippop.data.parseRoom
import com.pippop.game.BigGenerator
import com.pippop.game.BoardState
import com.pippop.game.SoloGame
import com.pippop.game.SoloSave
import com.pippop.game.SudokuShape
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Sudoku on joined grids over Nearby: the same rules as the server's. */
class LocalBigSudokuTest {
    private val t0 = 1_000_000L
    private val NEW = listOf("sudoku", LocalRoom.BIG_SUDOKU)

    private fun room(games: List<String> = NEW) = LocalRoom("BIG1", "Alice", Avatar.FOX).apply { join("Bob", Avatar.OWL, null, games) }

    @Test
    fun aBigRaceStartsWithTheOpenersPuzzle() {
        val r = room()
        r.setOptions(LocalRoom.HOST, JSONObject().put("mode", "race").put("shape", "cross"))
        val made = BigGenerator.generate(SudokuShape.CROSS, 38, Random(3))
        r.setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
        assertEquals("lobby", r.status)
        assertThrows(ApiException::class.java) { r.setReady(LocalRoom.HOST, true, Difficulty.EASY, t0) }
        assertThrows(ApiException::class.java) { r.setReady(LocalRoom.HOST, true, Difficulty.EASY, t0, made.solution to made.solution) }
        r.setReady(LocalRoom.HOST, true, Difficulty.EASY, t0, made.puzzle to made.solution)
        assertEquals("started", r.status)
        val v = parseRoom(r.view(LocalRoom.GUEST, r.startAt!!))
        assertEquals(SudokuShape.CROSS, v.shape)
        assertEquals(SudokuShape.CROSS, v.sudokuShape)
        assertTrue(v.sudokuShapes)
        assertEquals(made.puzzle, v.puzzle)
        val board = BoardState.fromPuzzle(v.puzzle!!, v.shape)
        assertEquals(SudokuShape.CROSS.cells, board.cells.size)
        assertThrows(ApiException::class.java) { r.submitBoard(LocalRoom.GUEST, "1".repeat(81), r.startAt!!) }
        r.submitBoard(LocalRoom.HOST, made.solution, r.startAt!! + 500)
        val end = parseRoom(r.view(LocalRoom.HOST, r.startAt!! + 500))
        assertEquals(RoomStatus.FINISHED, end.status)
        assertEquals("me", end.winner)
    }

    @Test
    fun everyoneNeedsTheLatestAppForBigBoards() {
        val r = room(listOf("sudoku"))
        r.setOptions(LocalRoom.HOST, JSONObject().put("mode", "race").put("shape", "twin"))
        val made = BigGenerator.generate(SudokuShape.TWIN, 38, Random(4))
        val e = assertThrows(ApiException::class.java) { r.setReady(LocalRoom.HOST, true, Difficulty.EASY, t0, made.puzzle to made.solution) }
        assertTrue(e.message!!.contains("Bob needs the latest"))
    }

    @Test
    fun togetherOnABigBoardIsATeamGame() {
        val r = room()
        r.setOptions(LocalRoom.HOST, JSONObject().put("mode", "together").put("shape", "twin"))
        val made = BigGenerator.generate(SudokuShape.TWIN, 38, Random(5))
        r.setReady(LocalRoom.HOST, true, Difficulty.EASY, t0, made.puzzle to made.solution)
        r.setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
        val now = r.startAt!!
        val coop = parseRoom(r.view(LocalRoom.GUEST, now)).coop!!
        assertEquals(LocalRoom.COOP_LIVES_BIG, coop.lives)
        assertEquals(LocalRoom.COOP_LIVES_BIG, coop.maxLives)
        // Off the board: nothing to fill. Anywhere on it: anyone.
        assertFalse(r.coopPlace(LocalRoom.GUEST, 14, 1, now))
        val gaps = made.puzzle.indices.filter { made.puzzle[it] == '0' }
        gaps.forEachIndexed { i, cell -> r.coopPlace(if (i % 2 == 0) LocalRoom.GUEST else LocalRoom.HOST, cell, made.solution[cell] - '0', now + i) }
        val end = parseRoom(r.view(LocalRoom.HOST, now + 1000))
        assertEquals("solvedTogether", end.endReason)
        assertEquals("me", end.winner)
    }

    @Test
    fun aBigSoloGameSavesAndComesBack() {
        val made = BigGenerator.generate(SudokuShape.WINDMILL, 46, Random(6))
        var game = SoloGame.new(Difficulty.MEDIUM, made)
        assertEquals(SoloGame.BIG_MISTAKES, game.maxMistakes)
        assertEquals(SoloGame.BIG_HINTS, game.hintsLeft)
        val cell = made.puzzle.indexOf('0')
        game = game.select(cell).enter(made.solution[cell] - '0').toggleNotesMode()
        val back = assertNotNull(SoloSave.decode(SoloSave.encode(game))).let { SoloSave.decode(SoloSave.encode(game))!! }
        assertEquals(SudokuShape.WINDMILL, back.shape)
        assertEquals(game.board.cells, back.board.cells)
        assertEquals(game.solution, back.solution)
        assertEquals(made.solution[cell] - '0', back.board.cells[cell])
        // Solving it all wins.
        var won = back.resume()
        for (i in made.puzzle.indices) if (made.puzzle[i] == '0') won = won.select(i).let { g -> if (g.selected == i) g else g.select(i) }.enter(made.solution[i] - '0')
        assertTrue(won.isSolved)
    }
}
