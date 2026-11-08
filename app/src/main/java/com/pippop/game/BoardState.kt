package com.pippop.game

/**
 * What the player has on the board: digits, pencil notes and an undo history.
 * Notes are a bitmask per cell where bit (digit - 1) means "digit is a candidate".
 */
data class BoardState(
    val cells: List<Int>,
    val givens: List<Boolean>,
    val notes: List<Int> = List(cells.size) { 0 },
    private val history: List<Snapshot> = emptyList(),
    /** How the board's grids are joined; off-board cells count as givens holding 0. */
    val shape: SudokuShape = SudokuShape.CLASSIC,
) {
    data class Snapshot(val cells: List<Int>, val notes: List<Int>)

    val canUndo get() = history.isNotEmpty()

    fun noteDigits(index: Int): List<Int> = (1..9).filter { notes[index] and (1 shl (it - 1)) != 0 }

    /** Puts [digit] in the cell (0 erases it), clearing its notes and the digit from peer notes. */
    fun place(index: Int, digit: Int): BoardState {
        if (givens[index]) return this
        if (cells[index] == digit && (digit != 0 || notes[index] == 0)) return this
        val newCells = cells.toMutableList().also { it[index] = digit }
        val newNotes = notes.toMutableList().also { it[index] = 0 }
        if (digit != 0) {
            val bit = 1 shl (digit - 1)
            for (peer in shape.peers[index]) newNotes[peer] = newNotes[peer] and bit.inv()
        }
        return copy(cells = newCells, notes = newNotes, history = pushed())
    }

    /** Adds or removes [digit] as a note; only empty cells can hold notes. */
    fun toggleNote(index: Int, digit: Int): BoardState {
        if (givens[index] || cells[index] != 0) return this
        val newNotes = notes.toMutableList().also { it[index] = it[index] xor (1 shl (digit - 1)) }
        return copy(notes = newNotes, history = pushed())
    }

    /** The digits [index] could still be: none of its row, column or box has them. */
    fun candidates(index: Int): Int {
        if (cells[index] != 0) return 0
        if (givens[index]) return 0
        var mask = (1 shl 9) - 1
        for (peer in shape.peers[index]) {
            val d = cells[peer]
            if (d != 0) mask = mask and (1 shl (d - 1)).inv()
        }
        return mask
    }

    /** Pencils in every digit [index] could still be (the pencil-mark helper's long press). */
    fun fillNotes(index: Int): BoardState {
        if (givens[index] || cells[index] != 0) return this
        val mask = candidates(index)
        if (notes[index] == mask) return this
        return copy(notes = notes.toMutableList().also { it[index] = mask }, history = pushed())
    }

    /** Pencils in the possible digits of every empty square at once ("Fill all"). */
    fun fillAllNotes(): BoardState {
        val filled = cells.indices.map { if (cells[it] == 0) candidates(it) else 0 }
        if (filled == notes) return this
        return copy(notes = filled, history = pushed())
    }

    /**
     * Sudoku together: the shared grid's digits ([board], 81 characters) in place of this
     * board's, keeping your own notes where the squares are still empty. Not undoable.
     */
    fun shared(board: String): BoardState {
        val digits = shape.parse(board)
        if (digits == cells) return this
        val newNotes = notes.toMutableList()
        for (i in cells.indices) {
            val d = digits[i]
            if (d == 0 || d == cells[i]) continue
            newNotes[i] = 0
            val bit = 1 shl (d - 1)
            for (peer in shape.peers[i]) newNotes[peer] = newNotes[peer] and bit.inv()
        }
        return copy(cells = digits, notes = newNotes, history = emptyList())
    }

    fun undo(): BoardState {
        val last = history.lastOrNull() ?: return this
        return copy(cells = last.cells, notes = last.notes, history = history.dropLast(1))
    }

    /** Filled cells that repeat a digit within their row, column or box. */
    fun conflicts(): Set<Int> = if (shape.big) shape.conflicts(cells) else Sudoku.conflicts(cells)

    private fun pushed() = (history + Snapshot(cells, notes)).takeLast(MAX_HISTORY)

    companion object {
        private const val MAX_HISTORY = 200

        fun fromPuzzle(puzzle: String, shape: SudokuShape = SudokuShape.CLASSIC): BoardState {
            val cells = shape.parse(puzzle)
            return BoardState(cells, cells.mapIndexed { i, d -> d != 0 || !shape.onBoard(i) }, shape = shape)
        }
    }
}
