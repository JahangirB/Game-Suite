package com.pippop.game.words

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Runs the cases shared with the server (src/test/resources/wordhunt-cases.json) and checks 500 seeds per size. */
class WordHuntTest {
    private fun read(name: String) = File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }

    @Before
    fun load() {
        WordLadder.install(read("ladder"), read("blocklist"))
        WordHuntThemes.install(read("hunt"))
    }

    private fun level(key: String) = HuntLevel.entries.first { it.key == key }

    @Test
    fun randomNumbersMatchTheServer() {
        val r = WordHunt.Mulberry32(1)
        assertEquals(listOf(2693262067L, 11749833L, 2265367787L), listOf(r.next(), r.next(), r.next()))
        assertEquals(WordHunt.Mulberry32(-1).next(), 4294967295L.toInt().let { WordHunt.Mulberry32(it).next() })
    }

    @Test
    fun sizesAreColumnsByRows() {
        assertEquals(listOf(10 to 10, 12 to 12, 12 to 16, 16 to 20), HuntLevel.entries.map { it.width to it.height })
        assertEquals(listOf(10, 14, 18, 24), HuntLevel.entries.map { it.words })
    }

    @Test
    fun sharedCases() {
        val cases = JSONObject(File("src/test/resources/wordhunt-cases.json").readText()).getJSONArray("cases")
        assertTrue(cases.length() >= 8)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val g = WordHunt.make(c.getString("theme"), level(c.getString("level")), c.getBoolean("backwards"), c.getInt("seed"))
            val at = "case ${i + 1}"
            val rows = c.getJSONArray("rows")
            assertEquals(at, List(rows.length()) { rows.getString(it) }, g.rows)
            val words = c.getJSONArray("words")
            assertEquals(at, words.length(), g.words.size)
            for (k in 0 until words.length()) {
                val w = words.getJSONObject(k)
                val got = g.words[k]
                assertEquals("$at word $k", listOf(w.getString("word"), w.getInt("row"), w.getInt("col"), w.getString("dir"), w.getInt("length")),
                    listOf(got.word, got.row, got.col, got.dir.key, got.length))
            }
        }
    }

    /** Every place [word] reads in [g], with the directions allowed. */
    private fun occurrences(g: HuntGrid, word: String, backwards: Boolean): List<HuntWord> {
        val out = ArrayList<HuntWord>()
        val dirs = if (backwards) HuntDir.entries else HuntDir.entries.take(2)
        for (r in 0 until g.height) for (c in 0 until g.width) for (d in dirs) {
            val w = HuntWord(word, r, c, d)
            val endR = r + d.dr * (word.length - 1)
            val endC = c + d.dc * (word.length - 1)
            if (endR !in 0 until g.height || endC !in 0 until g.width) continue
            if (word.indices.all { k -> g.rows[r + d.dr * k][c + d.dc * k] == word[k] }) out.add(w)
        }
        return out
    }

    @Test
    fun fiveHundredSeedsPerSizeAreValid() {
        val themes = WordHuntThemes.themes
        val names = themes.keys.toList()
        for (lv in HuntLevel.entries) for (backwards in listOf(false, true)) {
            var worst = 0L
            var total = 0L
            for (seed in 0 until 500) {
                val theme = names[seed % names.size]
                val t0 = System.nanoTime()
                val g = WordHunt.make(theme, lv, backwards, seed)
                val ns = System.nanoTime() - t0
                total += ns; worst = maxOf(worst, ns)
                val at = "${lv.key} $backwards seed $seed"
                assertEquals(at, lv.height, g.rows.size)
                assertTrue(at, g.rows.all { it.length == lv.width && it.all { ch -> ch in 'A'..'Z' } })
                assertEquals(at, lv.words, g.words.size)
                assertEquals(at, lv.words, g.words.map { it.word }.toSet().size)
                for (w in g.words) {
                    assertTrue(at, w.word in themes.getValue(theme))
                    assertTrue(at, backwards || w.dir == HuntDir.RIGHT || w.dir == HuntDir.DOWN)
                    assertEquals("$at ${w.word}", listOf(w), occurrences(g, w.word, backwards))
                    for (o in g.words) if (o != w) {
                        assertFalse("$at ${w.word} inside ${o.word}", o.word.contains(w.word))
                        if (backwards) assertFalse("$at ${w.word} reversed inside ${o.word}", o.word.contains(w.word.reversed()))
                    }
                }
                for (b in WordLadder.blocklist) assertTrue("$at blocked $b", occurrences(g, b, backwards).isEmpty())
                if (seed % 50 == 0) assertEquals(at, g, WordHunt.make(theme, lv, backwards, seed))
            }
            println("word hunt ${lv.key} ${if (backwards) "back" else "fwd "}: mean ${total / 500 / 1000} us, worst ${worst / 1000} us per grid")
        }
    }
}
