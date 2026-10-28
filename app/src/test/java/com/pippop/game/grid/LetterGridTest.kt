package com.pippop.game.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

class LetterGridTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun words() {
            // The real list, as the app ships it.
            GridWords.install(File("src/main/assets/words/grid.txt").readLines().filter { it.isNotBlank() })
        }
    }

    // c a t s
    // o r e d
    private val letters = "catsoredxxxxxxxx"

    @Test
    fun wordsAreTracedThroughTouchingSquares() {
        assertTrue(LetterGrid.traceable(letters, "cats"))
        assertTrue(LetterGrid.traceable(letters, "core"))
        assertFalse(LetterGrid.traceable(letters, "catt"))
        assertNull(LetterGrid.problem(letters, "red"))
        assertEquals("That word isn't on the grid.", LetterGrid.problem(letters, "dog"))
    }

    @Test
    fun theSolverFindsWhatCanBeTraced() {
        val all = LetterGrid.allWords(letters)
        assertTrue("cats" in all && "red" in all && "core" in all)
        assertTrue(all.all { LetterGrid.traceable(letters, it) && GridWords.isWord(it) })
    }

    @Test
    fun generatedGridsHavePlentyOfWords() {
        val gen = LetterGridGenerator(Random(3))
        repeat(5) {
            val g = gen.generate()
            assertEquals(16, g.length)
            assertTrue("${LetterGrid.allWords(g).size} words in $g", LetterGrid.allWords(g).size >= 20)
        }
    }

    @Test
    fun longerWordsScoreMore() {
        assertEquals(listOf(1, 1, 2, 3, 5, 11), listOf("cat", "cats", "cater", "carted", "scarted", "starters").map { LetterGrid.points(it) })
    }
}
