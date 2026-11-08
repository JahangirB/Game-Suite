package com.pippop.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BigSudokuTest {
    @Test
    fun shapesHaveTheRightCells() {
        assertEquals(81, SudokuShape.CLASSIC.playable)
        assertEquals(2 * 81 - 9, SudokuShape.TWIN.playable)
        assertEquals(3 * 81 - 18, SudokuShape.STAIRS.playable)
        assertEquals(5 * 81 - 36, SudokuShape.CROSS.playable)
        assertEquals(5 * 81 - 72, SudokuShape.WINDMILL.playable)
        assertEquals(21, SudokuShape.CROSS.size)
        // A cell in the shared box of the Twin is in both grids, and its peers reach both.
        val shared = 7 * 15 + 7
        assertTrue(SudokuShape.TWIN.shared(shared))
        assertTrue(7 * 15 + 0 in SudokuShape.TWIN.peers[shared])
        assertTrue(7 * 15 + 14 in SudokuShape.TWIN.peers[shared])
        // Same row of the board but different grids, never in one grid together: not peers.
        assertFalse(SudokuShape.TWIN.onBoard(14))
        assertEquals(SudokuShape.CLASSIC.peers[40].size, 20)
    }

    @Test
    fun puzzlesHaveOneSolutionOverTheWholeBoard() {
        for (shape in listOf(SudokuShape.TWIN, SudokuShape.STAIRS, SudokuShape.CROSS, SudokuShape.WINDMILL)) {
            for (holes in listOf(38, 50)) {
                val t0 = System.currentTimeMillis()
                val r = BigGenerator.generate(shape, holes, Random(holes * 31 + shape.ordinal))
                val ms = System.currentTimeMillis() - t0
                println("${shape.key} $holes: ${ms} ms, ${r.puzzle.count { it == '0' }} of ${shape.playable} empty")
                assertTrue(shape.fits(r.puzzle))
                assertTrue(shape.fits(r.solution))
                assertTrue(shape.conflicts(shape.parse(r.solution)).isEmpty())
                assertTrue(r.solution.none { it == '0' })
                for (i in r.puzzle.indices) if (r.puzzle[i] in '1'..'9') assertEquals(r.solution[i], r.puzzle[i])
                assertEquals(1, BigGenerator.countSolutions(shape, r.puzzle))
                assertTrue("${shape.key} took $ms ms", ms < 8000)
            }
        }
    }

    @Test
    fun boardsWorkForEveryShape() {
        val r = BigGenerator.generate(SudokuShape.TWIN, 38, Random(5))
        val board = BoardState.fromPuzzle(r.puzzle, SudokuShape.TWIN)
        // Off-board squares can't be filled.
        assertTrue(board.givens[14])
        assertEquals(board, board.place(14, 3))
        val empty = r.puzzle.indexOf('0')
        val right = r.solution[empty].digitToInt()
        val placed = board.place(empty, right)
        assertTrue(placed.conflicts().isEmpty())
        val peer = SudokuShape.TWIN.peers[empty].first { placed.cells[it] == 0 }
        assertTrue(placed.place(peer, right).conflicts().containsAll(listOf(empty, peer)))
        // Notes: what each empty square could be, never the digit of a peer.
        val notes = board.fillAllNotes()
        assertEquals(0, notes.notes[14])
        assertTrue(notes.notes[empty] and (1 shl (right - 1)) != 0)
        assertEquals(r.puzzle, SudokuShape.TWIN.encode(board.cells))
    }
}
