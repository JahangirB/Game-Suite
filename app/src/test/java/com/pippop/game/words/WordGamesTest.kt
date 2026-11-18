package com.pippop.game.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WordGamesTest {
    private fun read(name: String) = File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }
    private val blocklist by lazy { read("blocklist").toSet() }
    private val valid by lazy { (read("allowed") + read("answers") + read("grid")).map { it.uppercase() }.toSet() }
    private val az = Regex("[A-Z]+")

    @Before
    fun load() {
        WordLadder.install(read("ladder"))
        WordHuntThemes.install(read("hunt"))
    }

    @Test
    fun ladderWordsAreRealRightLengthAndClean() {
        assertEquals(WordLadder.all.size, read("ladder").size)
        for (w in WordLadder.all) {
            assertTrue(w, w.length in WordLadder.LENGTHS && az.matches(w))
            assertTrue("$w is not a real word", w in valid)
            assertFalse("$w is blocked", w in blocklist)
        }
    }

    @Test
    fun enoughLadderWordsAreJoined() {
        val min = mapOf(4 to (600 to 600), 5 to (1400 to 700), 6 to (3000 to 1500)) // with a neighbour, largest group
        for (length in WordLadder.LENGTHS) {
            val list = WordLadder.all.filter { it.length == length }
            val withNeighbour = list.count { WordLadder.neighbours(it).isNotEmpty() }
            val seen = HashSet<String>()
            var largest = 0
            for (w in list) {
                if (!seen.add(w)) continue
                val queue = ArrayDeque(listOf(w))
                var size = 0
                while (queue.isNotEmpty()) {
                    size++
                    for (n in WordLadder.neighbours(queue.removeFirst())) if (seen.add(n)) queue.addLast(n)
                }
                largest = maxOf(largest, size)
            }
            println("ladder $length: ${list.size} words, $withNeighbour with a neighbour, largest group $largest")
            assertTrue("$length: $withNeighbour", withNeighbour >= min.getValue(length).first)
            assertTrue("$length: $largest", largest >= min.getValue(length).second)
        }
    }

    @Test
    fun neighboursAndDistances() {
        assertTrue("GOLD" in WordLadder.neighbours("COLD"))
        assertFalse("COLD" in WordLadder.neighbours("COLD"))
        assertTrue(WordLadder.neighbours("ZZZZ").isEmpty())
        for (w in WordLadder.all.take(300)) for (n in WordLadder.neighbours(w)) assertTrue(w in WordLadder.neighbours(n))
        assertEquals(0, WordLadder.distance("COLD", "COLD"))
        assertEquals(1, WordLadder.distance("COLD", "GOLD"))
        assertEquals(6, WordLadder.distance("COLD", "WARM"))
        assertEquals(6, WordLadder.distance("WARM", "COLD"))
        assertTrue(WordLadder.pathExists("COLD", "WARM"))
        assertEquals(-1, WordLadder.distance("COLD", "COLDER"))
        assertFalse(WordLadder.pathExists("COLD", "ZZZZ"))
    }

    @Test
    fun huntCategoriesAreRealAndClean() {
        val themes = WordHuntThemes.themes
        assertTrue("only ${themes.size} categories", themes.size >= 12)
        for (name in listOf("Animals", "Food", "Sport", "Nature", "Home", "Stories", "Colours", "Clothes", "Body", "Weather", "Music", "Travel")) assertTrue(name, name in themes)
        assertEquals(themes.values.flatten().distinct(), WordHuntThemes.pool)
        for ((name, list) in themes) {
            assertTrue("$name has ${list.size}", list.size >= 40)
            assertEquals("$name repeats a word", list.size, list.toSet().size)
            for (w in list) {
                assertTrue(w, w.length in 3..8 && az.matches(w))
                assertTrue("$name: $w is not a real word", w in valid)
                assertFalse("$name: $w is blocked", w in blocklist)
            }
        }
    }
}
