package com.arcade.sudoku.game.slide

import kotlin.random.Random

/** Slide Race levels: the puzzle's size. */
enum class SlideLevel(val key: String, val label: String, val size: Int) {
    EASY("easy", "Easy · 3 × 3", 3),
    MEDIUM("medium", "Medium · 4 × 4", 4),
    HARD("hard", "Hard · 5 × 5", 5);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: EASY
    }
}

/**
 * Sliding puzzle rules, matching the server (backend/cloudflare/src/slide.js): tiles row by
 * row, 0 the gap; solved is 1, 2, … then the gap last. A move is the tile slid into the gap.
 */
object Slide {
    fun solved(size: Int) = (1 until size * size).toList() + 0

    fun isSolved(tiles: List<Int>, size: Int) = tiles == solved(size)

    /** Whether [tile] is next to the gap. */
    fun canSlide(tiles: List<Int>, size: Int, tile: Int): Boolean {
        val at = tiles.indexOf(tile)
        val gap = tiles.indexOf(0)
        return tile != 0 && at >= 0 && kotlin.math.abs(at % size - gap % size) + kotlin.math.abs(at / size - gap / size) == 1
    }

    fun slide(tiles: List<Int>, size: Int, tile: Int): List<Int> {
        require(canSlide(tiles, size, tile)) { "That tile can't slide." }
        val at = tiles.indexOf(tile)
        val gap = tiles.indexOf(0)
        return tiles.toMutableList().also { it[gap] = tile; it[at] = 0 }
    }

    fun replay(start: List<Int>, size: Int, moves: List<Int>) = moves.fold(start) { t, m -> slide(t, size, m) }

    /**
     * The tiles to slide, nearest the gap first, when [tile] is tapped: every tile between it
     * and the gap along its row or column (none if it isn't in line with the gap).
     */
    fun slidesFor(tiles: List<Int>, size: Int, tile: Int): List<Int> {
        val at = tiles.indexOf(tile)
        val gap = tiles.indexOf(0)
        if (tile == 0 || at < 0) return emptyList()
        val (ar, ac) = at / size to at % size
        val (gr, gc) = gap / size to gap % size
        val cells = when {
            ar == gr -> (if (ac < gc) (gc - 1 downTo ac) else (gc + 1..ac)).map { ar * size + it }
            ac == gc -> (if (ar < gr) (gr - 1 downTo ar) else (gr + 1..ar)).map { it * size + ac }
            else -> emptyList()
        }
        return cells.map { tiles[it] }
    }

    /**
     * The tile a swipe slides into the gap: a swipe going ([dx], [dy]) (each -1, 0 or 1) moves
     * the tile on the other side of the gap, so a swipe to the left slides in the tile to the
     * gap's right. Null when the gap is at that edge.
     */
    fun tileForSwipe(tiles: List<Int>, size: Int, dx: Int, dy: Int): Int? {
        val gap = tiles.indexOf(0)
        val x = gap % size - dx
        val y = gap / size - dy
        if (gap < 0 || x !in 0 until size || y !in 0 until size) return null
        return tiles[y * size + x]
    }

    fun progress(tiles: List<Int>, size: Int): Int {
        val goal = solved(size)
        return tiles.indices.count { tiles[it] != 0 && tiles[it] == goal[it] } * 100 / (size * size - 1)
    }

    /** A shuffled start made of random slides from solved, so it can always be solved. */
    fun shuffle(size: Int, random: Random = Random.Default): List<Int> {
        var tiles = solved(size)
        var last = -1
        repeat(size * size * 40) {
            val options = tiles.filter { it != last && canSlide(tiles, size, it) }
            val t = options.random(random)
            tiles = slide(tiles, size, t)
            last = t
        }
        return if (isSolved(tiles, size)) shuffle(size, random) else tiles
    }
}
