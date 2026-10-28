package com.pippop.game.grid

import android.content.Context
import kotlin.random.Random

/** The Letter Grid word list (3 to 8 letters, words/grid.txt), the same as the server's. */
object GridWords {
    @Volatile private var words: Set<String> = emptySet()
    @Volatile private var prefixes: Set<String> = emptySet()
    @Volatile private var list: List<String> = emptyList()

    fun load(context: Context) {
        if (words.isNotEmpty()) return
        install(context.assets.open("words/grid.txt").bufferedReader().readLines().filter { it.isNotBlank() })
    }

    /** For tests, and [load]. */
    fun install(list: List<String>) {
        prefixes = list.flatMap { w -> (1 until w.length).map { w.substring(0, it) } }.toSet()
        words = list.toSet()
        this.list = list
    }

    /** Every word, for the Balloon Words computer. */
    val all: List<String> get() = list

    /** A random word of 5 to 8 letters (Balloon Words on your own). */
    fun randomWord(random: Random = Random.Default): String =
        list.filter { it.length in 5..8 }.randomOrNull(random) ?: "balloon"


    val loaded get() = words.isNotEmpty()

    fun isWord(w: String) = w in words

    fun isPrefix(p: String) = p in prefixes
}

/** Letter Grid rules, matching the server (backend/cloudflare/src/grid.js). */
object LetterGrid {
    const val SIZE = 4
    const val ROUND_MS = 120_000L

    /** Whether squares [a] and [b] touch, sideways or corner to corner. */
    fun touching(a: Int, b: Int) = a != b && kotlin.math.abs(a / SIZE - b / SIZE) <= 1 && kotlin.math.abs(a % SIZE - b % SIZE) <= 1

    /** Whether [word] can be traced on [letters] through touching squares, each used once. */
    fun traceable(letters: String, word: String): Boolean {
        val used = BooleanArray(SIZE * SIZE)
        fun from(i: Int, k: Int): Boolean {
            if (letters[i] != word[k]) return false
            if (k == word.lastIndex) return true
            used[i] = true
            val ok = (0 until SIZE * SIZE).any { j -> !used[j] && touching(i, j) && from(j, k + 1) }
            used[i] = false
            return ok
        }
        return word.isNotEmpty() && (0 until SIZE * SIZE).any { from(it, 0) }
    }

    /** A word's points by length: 3-4 letters 1, 5 → 2, 6 → 3, 7 → 5, 8 → 11. */
    fun points(word: String) = when (word.length) {
        in 0..4 -> 1
        5 -> 2
        6 -> 3
        7 -> 5
        else -> 11
    }

    /** Why [word] doesn't count, or null when it does. */
    fun problem(letters: String, word: String): String? = when {
        word.length < 3 -> "Words need three letters or more."
        !traceable(letters, word) -> "That word isn't on the grid."
        !GridWords.isWord(word) -> "\"${word.uppercase()}\" isn't in the word list."
        else -> null
    }

    /** Every listed word on the grid (for the generator and the words you missed). */
    fun allWords(letters: String): Set<String> {
        val found = mutableSetOf<String>()
        val used = BooleanArray(SIZE * SIZE)
        fun walk(i: Int, sofar: String) {
            val w = sofar + letters[i]
            if (w.length >= 3 && GridWords.isWord(w)) found += w
            if (!GridWords.isPrefix(w)) return
            used[i] = true
            for (j in 0 until SIZE * SIZE) if (!used[j] && touching(i, j)) walk(j, w)
            used[i] = false
        }
        for (i in 0 until SIZE * SIZE) walk(i, "")
        return found
    }
}

/** Rolls grids from the classic sixteen letter dice, keeping one with plenty of words. */
class LetterGridGenerator(private val random: Random = Random.Default) {
    private val dice = listOf(
        "aaeegn", "abbjoo", "achops", "affkps", "aoottw", "cimotu", "deilrx", "delrvy",
        "distty", "eeghnw", "eeinsu", "ehrtvw", "eiosst", "elrtty", "himnuu", "hlnnrz",
    )

    fun roll(): String = dice.shuffled(random).map { it[random.nextInt(6)] }.joinToString("")

    /** A grid with at least [minWords] words in the list (the richest of a few tries otherwise). */
    fun generate(minWords: Int = 40): String {
        if (!GridWords.loaded) return roll()
        var best = roll()
        var bestCount = LetterGrid.allWords(best).size
        repeat(30) {
            if (bestCount >= minWords) return best
            val g = roll()
            val n = LetterGrid.allWords(g).size
            if (n > bestCount) {
                best = g
                bestCount = n
            }
        }
        return best
    }
}
