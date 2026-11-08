package com.pippop.game

/**
 * How Sudoku grids are joined into one board. Each grid is a 9 x 9 Sudoku placed at a (row,
 * column) in cells; where grids overlap, the shared 3 x 3 boxes belong to every grid covering
 * them, so a digit there counts in each. CLASSIC is the one grid of normal Sudoku.
 *
 * A board is [size] x [size] cells, row-major; cells outside every grid are off the board
 * ('-' in text, 0 in a list). The server has the same shapes (backend/cloudflare/src/shapes.js).
 */
enum class SudokuShape(val key: String, val label: String, val grids: List<Pair<Int, Int>>) {
    CLASSIC("classic", "Classic", listOf(0 to 0)),
    TWIN("twin", "Twin", listOf(0 to 0, 6 to 6)),
    STAIRS("stairs", "Staircase", listOf(0 to 0, 6 to 6, 12 to 12)),
    CROSS("cross", "Big Cross", listOf(0 to 0, 0 to 12, 6 to 6, 12 to 0, 12 to 12)),
    WINDMILL("windmill", "Windmill", listOf(0 to 3, 3 to 12, 6 to 6, 12 to 9, 9 to 0));

    val size = grids.maxOf { maxOf(it.first, it.second) + 9 }
    val cells = size * size
    val big get() = this != CLASSIC

    /** Which grids cover each cell (empty: off the board). */
    val gridsAt: Array<IntArray> = Array(cells) { i ->
        val r = i / size
        val c = i % size
        grids.indices.filter { g -> r - grids[g].first in 0..8 && c - grids[g].second in 0..8 }.toIntArray()
    }

    fun onBoard(i: Int) = gridsAt[i].isNotEmpty()

    /** Cells on the board. */
    val playable = gridsAt.count { it.isNotEmpty() }

    /** How many times each digit appears in a solved board: 9 for every grid, less the shared boxes. */
    val perDigit get() = playable / 9

    /** Cells in more than one grid. */
    fun shared(i: Int) = gridsAt[i].size > 1

    /** Every row, column and box of every grid, as cell lists (shared boxes once). */
    val units: List<IntArray> = buildList {
        // (Inside buildList, plain `size` would be the list's.)
        val size = this@SudokuShape.size
        val boxes = HashSet<Int>()
        for ((gr, gc) in grids) {
            for (k in 0 until 9) {
                add(IntArray(9) { (gr + k) * size + gc + it })
                add(IntArray(9) { (gr + it) * size + gc + k })
                val br = gr + (k / 3) * 3
                val bc = gc + (k % 3) * 3
                if (boxes.add(br * size + bc)) add(IntArray(9) { (br + it / 3) * size + bc + it % 3 })
            }
        }
    }

    /** The units each cell is in. */
    val unitsOf: Array<IntArray> = run {
        val lists = Array(cells) { ArrayList<Int>() }
        units.forEachIndexed { u, unit -> unit.forEach { lists[it].add(u) } }
        Array(cells) { lists[it].toIntArray() }
    }

    /** The cells that may not hold the same digit as each cell: its rows, columns and boxes in every grid. */
    val peers: Array<IntArray> = Array(cells) { i -> unitsOf[i].flatMap { units[it].asIterable() }.filter { it != i }.distinct().toIntArray() }

    /** The board as text: digits, '0' for empty and '-' off the board. */
    fun encode(cells: List<Int>): String = String(CharArray(this.cells) { if (onBoard(it)) '0' + cells[it] else '-' })

    /** Text to digits; off-board cells read 0. */
    fun parse(text: String): List<Int> {
        require(text.length == cells) { "A ${label} board has $cells cells" }
        return text.mapIndexed { i, ch -> if (ch == '-' || !onBoard(i)) 0 else ch.digitToInt() }
    }

    /** Whether [text] is a board of this shape: '-' exactly off the board, digits on it. */
    fun fits(text: String) = text.length == cells && text.indices.all { i -> if (onBoard(i)) text[i] in '0'..'9' else text[i] == '-' }

    /** Filled cells whose digit repeats in one of their rows, columns or boxes. */
    fun conflicts(cells: List<Int>): Set<Int> {
        val out = HashSet<Int>()
        for (unit in units) {
            val seen = IntArray(10) { -1 }
            for (i in unit) {
                val d = cells[i]
                if (d == 0) continue
                if (seen[d] >= 0) {
                    out += i
                    out += seen[d]
                } else {
                    seen[d] = i
                }
            }
        }
        return out
    }

    /**
     * Checks a puzzle another phone made (the same checks as the server's): both boards fit, the
     * answer is full and breaks no rule, the givens are the answer's, and there's something to do.
     * Returns what's wrong, or null.
     */
    fun check(puzzle: String, solution: String): String? {
        if (!big) return "Unknown board shape."
        if (!fits(puzzle) || !fits(solution)) return "That puzzle doesn\u2019t fit the board."
        if ('0' in solution) return "The puzzle\u2019s answer isn\u2019t complete."
        if (units.any { u -> u.map { solution[it] }.toSet().size != 9 }) return "The puzzle\u2019s answer breaks the rules."
        if (puzzle.indices.any { puzzle[it] in '1'..'9' && puzzle[it] != solution[it] }) return "The puzzle doesn\u2019t match its answer."
        if (puzzle.count { it == '0' } < playable / 4) return "The puzzle has too few empty squares."
        return null
    }

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: CLASSIC

        /** The shapes a game can be played on, the classic one first. */
        val offered = listOf(CLASSIC, TWIN, STAIRS, CROSS, WINDMILL)
    }
}
