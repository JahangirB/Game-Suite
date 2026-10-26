package com.arcade.sudoku.game.bingo

import kotlin.random.Random

/**
 * Bingo, as on the server (bingo.js): a 5 × 5 card each, column B 1–15 to O 61–75, the centre
 * free (0). The numbers 1 to 75 are called in a shuffled order, one every [CALL_MS] from the
 * start, so every phone works out the calls from the clock. The first full line of called
 * numbers (row, column or diagonal) wins.
 */
object Bingo {
    const val NUMBERS = 75
    const val SIZE = 5
    const val FREE = 12
    const val CALL_MS = 3500L
    const val LAST_CALL_GRACE_MS = 10_000L

    fun shuffledCalls(random: Random = Random.Default): List<Int> = (1..NUMBERS).shuffled(random)

    /** A card, row by row; 0 is the free centre. */
    fun dealCard(random: Random = Random.Default): List<Int> {
        val columns = (0 until SIZE).map { c -> (c * 15 + 1..c * 15 + 15).shuffled(random).take(SIZE) }
        return List(SIZE * SIZE) { i -> if (i == FREE) 0 else columns[i % SIZE][i / SIZE] }
    }

    /** How many numbers have been called [elapsed] ms after the start. */
    fun calledCount(elapsed: Long): Int = if (elapsed < 0) 0 else minOf(NUMBERS, (elapsed / CALL_MS).toInt() + 1)

    /** How long after the start the round ends if nobody claims. */
    const val ROUND_MS = (NUMBERS - 1) * CALL_MS + LAST_CALL_GRACE_MS

    /** Five rows, five columns and the two diagonals, as lists of squares. */
    val LINES: List<List<Int>> =
        (0 until SIZE).map { r -> (0 until SIZE).map { c -> r * SIZE + c } } +
            (0 until SIZE).map { c -> (0 until SIZE).map { r -> r * SIZE + c } } +
            listOf((0 until SIZE).map { it * SIZE + it }, (0 until SIZE).map { it * SIZE + SIZE - 1 - it })

    /** The first line on [card] whose squares are all [marked] (the centre always is), or null. */
    fun fullLine(card: List<Int>, marked: Set<Int>): List<Int>? =
        LINES.firstOrNull { line -> line.all { card[it] == 0 || card[it] in marked } }

    /** The letter over a number's column. */
    fun letter(n: Int) = "BINGO"[((n - 1) / 15).coerceIn(0, 4)]
}
