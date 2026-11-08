package com.pippop.game

import com.pippop.data.Difficulty

enum class SoloStatus { PLAYING, PAUSED, WON, LOST }

/** A standalone game against the clock. The solution is known locally, so mistakes show at once. */
data class SoloGame(
    val difficulty: Difficulty,
    val puzzle: String,
    val solution: List<Int>,
    val board: BoardState,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    val mistakes: Int = 0,
    val hintsLeft: Int = MAX_HINTS,
    val elapsedMs: Long = 0,
    val status: SoloStatus = SoloStatus.PLAYING,
    /** One grid, or several joined: a big board allows more mistakes and hints. */
    val shape: SudokuShape = SudokuShape.CLASSIC,
) {
    val maxMistakes get() = if (shape.big) BIG_MISTAKES else MAX_MISTAKES

    /** Filled cells whose digit differs from the solution. */
    val wrongCells: Set<Int>
        get() = board.cells.indices.filterTo(HashSet()) {
            !board.givens[it] && board.cells[it] != 0 && board.cells[it] != solution[it]
        }

    val isSolved get() = board.cells == solution
    val isActive get() = status == SoloStatus.PLAYING || status == SoloStatus.PAUSED

    fun select(index: Int) = copy(selected = if (selected == index) null else index)

    fun toggleNotesMode() = copy(notesMode = !notesMode)

    /** Enters [digit] in the selected cell, or as a note in notes mode. Wrong digits cost a mistake. */
    fun enter(digit: Int): SoloGame {
        val index = selected ?: return this
        if (status != SoloStatus.PLAYING || board.givens[index]) return this
        if (notesMode && digit != 0) return copy(board = board.toggleNote(index, digit))
        if (digit == 0) return copy(board = board.place(index, 0))
        // Replacing a correct digit with itself or a note-less repeat changes nothing.
        if (board.cells[index] == digit) return this

        val placed = board.place(index, digit)
        val mistake = digit != solution[index]
        return finish(copy(board = placed, mistakes = mistakes + if (mistake) 1 else 0))
    }

    fun undo() = if (status == SoloStatus.PLAYING) copy(board = board.undo()) else this

    /** The pencil-mark helper: every digit square [index] could be (a long press), or every empty square's. */
    fun fillNotes(index: Int?) = if (status != SoloStatus.PLAYING) this
    else copy(board = if (index == null) board.fillAllNotes() else board.fillNotes(index), selected = index ?: selected)

    /** Reveals the right digit in the selected cell, or in the first empty or wrong cell. */
    fun hint(): SoloGame {
        if (status != SoloStatus.PLAYING || hintsLeft == 0) return this
        val target = selected?.takeIf { !board.givens[it] && board.cells[it] != solution[it] }
            ?: board.cells.indices.firstOrNull { board.cells[it] != solution[it] }
            ?: return this
        val placed = board.place(target, solution[target])
        return finish(copy(board = placed, selected = target, hintsLeft = hintsLeft - 1))
    }

    fun pause() = if (status == SoloStatus.PLAYING) copy(status = SoloStatus.PAUSED) else this
    fun resume() = if (status == SoloStatus.PAUSED) copy(status = SoloStatus.PLAYING) else this

    fun tick(ms: Long) = if (status == SoloStatus.PLAYING) copy(elapsedMs = elapsedMs + ms) else this

    /** The same puzzle from scratch. */
    fun restart() = copy(
        board = BoardState.fromPuzzle(puzzle, shape), selected = null, notesMode = false,
        mistakes = 0, hintsLeft = hintsFor(shape), elapsedMs = 0, status = SoloStatus.PLAYING,
    )

    private fun finish(game: SoloGame) = when {
        game.isSolved -> game.copy(status = SoloStatus.WON)
        game.mistakes >= game.maxMistakes -> game.copy(status = SoloStatus.LOST)
        else -> game
    }

    companion object {
        const val MAX_MISTAKES = 3
        const val MAX_HINTS = 3
        /** A board of joined grids is a long puzzle: five mistakes and five hints. */
        const val BIG_MISTAKES = 5
        const val BIG_HINTS = 5

        fun hintsFor(shape: SudokuShape) = if (shape.big) BIG_HINTS else MAX_HINTS

        fun new(difficulty: Difficulty, generated: Generator.Result) = SoloGame(
            difficulty = difficulty,
            puzzle = generated.puzzle,
            solution = Sudoku.parse(generated.solution),
            board = BoardState.fromPuzzle(generated.puzzle),
        )

        fun new(difficulty: Difficulty, generated: BigGenerator.Result) = SoloGame(
            difficulty = difficulty,
            puzzle = generated.puzzle,
            solution = generated.shape.parse(generated.solution),
            board = BoardState.fromPuzzle(generated.puzzle, generated.shape),
            hintsLeft = hintsFor(generated.shape),
            shape = generated.shape,
        )
    }
}

/**
 * Text form of an unfinished game, so it can be continued after the app is closed. Version 2
 * adds the board's shape at the end; version 1 saves (one grid) still read.
 */
object SoloSave {
    private const val VERSION = "v2"

    fun encode(game: SoloGame): String = listOf(
        VERSION,
        game.difficulty.key,
        game.puzzle,
        game.shape.encode(game.solution),
        game.shape.encode(game.board.cells),
        game.board.notes.joinToString(","),
        game.elapsedMs,
        game.mistakes,
        game.hintsLeft,
        game.shape.key,
    ).joinToString("|")

    fun decode(text: String?): SoloGame? = runCatching {
        val parts = text!!.split("|")
        require((parts.size == 9 && parts[0] == "v1") || (parts.size == 10 && parts[0] == VERSION))
        val shape = if (parts.size == 10) SudokuShape.from(parts[9]) else SudokuShape.CLASSIC
        val puzzle = parts[2]
        val cells = shape.parse(parts[4])
        val notes = parts[5].split(",").map { it.toInt() }
        require(notes.size == shape.cells)
        val start = BoardState.fromPuzzle(puzzle, shape)
        SoloGame(
            difficulty = Difficulty.entries.first { it.key == parts[1] },
            puzzle = puzzle,
            solution = shape.parse(parts[3]),
            board = BoardState(cells, start.givens, notes, shape = shape),
            mistakes = parts[7].toInt(),
            hintsLeft = parts[8].toInt(),
            elapsedMs = parts[6].toLong(),
            status = SoloStatus.PAUSED,
            shape = shape,
        )
    }.getOrNull()
}
