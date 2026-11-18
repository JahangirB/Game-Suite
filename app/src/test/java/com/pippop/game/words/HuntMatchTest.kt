package com.pippop.game.words

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Runs the cases shared with the server (src/test/resources/wordhuntmatch-cases.json). */
class HuntMatchTest {
    private fun read(name: String) = File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }

    @Before
    fun load() {
        WordLadder.install(read("ladder"), read("blocklist"))
        WordHuntThemes.install(read("hunt"))
    }

    private val cases = JSONObject(File("src/test/resources/wordhuntmatch-cases.json").readText()).getJSONArray("cases")

    private fun options(o: JSONObject) = HuntOptions.clean(
        o.getString("level"), if (o.has("backwards")) o.getBoolean("backwards") else null,
        if (o.has("timer")) o.getInt("timer") else null, if (o.has("seed")) o.getInt("seed") else null,
    )

    /** The selection a step describes: a placed word's cells (first to last letter) with the step's changes. */
    private fun selection(m: HuntMatch, s: JSONObject): IntArray {
        s.optJSONObject("raw")?.let { return intArrayOf(it.getInt("r1"), it.getInt("c1"), it.getInt("r2"), it.getInt("c2")) }
        val w = m.grid.words.first { it.word == s.getString("word") }
        val (a, b) = HuntMatch.ends(w)
        var (r1, c1) = a
        var (r2, c2) = b
        val trim = s.optInt("trim", 0)
        r2 -= w.dir.dr * trim; c2 -= w.dir.dc * trim
        s.optJSONArray("endDelta")?.let { r2 += it.getInt(0); c2 += it.getInt(1) }
        if (s.optBoolean("diagonal")) { r2 = r1 + w.length - 1; c2 = c1 + w.length - 1 }
        return if (s.optBoolean("reverse")) intArrayOf(r2, c2, r1, c1) else intArrayOf(r1, c1, r2, c2)
    }

    private fun norm(v: Any?): Any? = when (v) {
        JSONObject.NULL -> null
        is JSONArray -> (0 until v.length()).map { norm(v.get(it)) }
        is JSONObject -> v.keys().asSequence().associateWith { norm(v.get(it)) }
        is Int -> v.toLong()
        else -> v
    }

    /** Every key a view has: the words are hidden, so there is no list of them. */
    private val viewKeys = setOf("me", "level", "backwards", "timer", "width", "height", "rows", "phase", "total", "foundCount", "found", "marks", "scores", "hintsLeft", "hints", "endsAt", "winner", "winners", "reason", "results")
    private val foundKeys = setOf("at", "dir", "col", "foundBy", "length", "row", "word")

    /** No view carries the text or length of a word nobody has found (the grid's own letters, `rows`, aside). */
    private fun assertNoUnfoundText(m: HuntMatch, v: JSONObject, at: String) {
        assertEquals("$at: the view's keys", viewKeys, v.keys().asSequence().toSet())
        val found = v.getJSONArray("found")
        val foundWords = (0 until found.length()).map { found.getJSONObject(it).also { f -> assertEquals("$at: found entry", foundKeys, f.keys().asSequence().toSet()) }.getString("word") }.toSet()
        val rest = JSONObject(v.toString()).also { it.remove("rows") }.toString()
        for (w in m.grid.words) {
            if (w.word in foundWords) continue
            assertTrue("$at: ${w.word} leaks", !rest.contains(w.word))
            assertTrue("$at: ${w.word} reversed leaks", !rest.contains(w.word.reversed()))
        }
        assertEquals("$at: total", m.grid.words.size, v.getInt("total"))
        assertEquals("$at: foundCount", found.length(), v.getInt("foundCount"))
        val hints = v.getJSONArray("hints")
        for (i in 0 until hints.length()) assertEquals("$at: hint", setOf("row", "col"), hints.getJSONObject(i).keys().asSequence().toSet())
        assertTrue("$at: no theme", !v.has("theme"))
    }

    @Test
    fun sharedCases() {
        assertTrue(cases.length() >= 8)
        for (i in 0 until cases.length()) runCase(cases.getJSONObject(i))
    }

    private fun runCase(c: JSONObject) {
        val marksJson = c.getJSONArray("marks")
        val marks = List(marksJson.length()) { marksJson.getString(it)[0] }
        val m = HuntMatch(options(c.getJSONObject("options")), marks, startAt = c.getLong("now"))
        val steps = c.getJSONArray("steps")
        for (n in 0 until steps.length()) {
            val s = steps.getJSONObject(n)
            val at = "${c.getString("name")}, step ${n + 1} (${s.getString("do")})"
            val now = s.optLong("now", 0)
            val mark = s.optString("mark", " ").first()
            when (s.getString("do")) {
                "view" -> {
                    val v = m.view(mark)
                    assertNoUnfoundText(m, v, at)
                    val expect = s.getJSONObject("expect")
                    for (k in expect.keys()) {
                        if (k == "found") {
                            val f = v.getJSONArray("found")
                            val got = (0 until f.length()).map { f.getJSONObject(it).let { w -> mapOf("word" to w.get("word"), "foundBy" to w.get("foundBy"), "at" to norm(w.get("at"))) } }
                            assertEquals("$at: found", norm(expect.get(k)), got)
                        } else assertEquals("$at: $k", norm(expect.get(k)), norm(v.get(k)))
                    }
                }
                "tick" -> {
                    assertEquals(at, s.getBoolean("ended"), m.tick(now))
                    if (s.has("over")) {
                        assertEquals(at, s.getBoolean("over"), m.over)
                        assertEquals(at, if (s.isNull("winner")) null else s.getString("winner")[0], m.winner)
                        assertEquals(at, norm(s.get("winners")), m.winners.map { it.toString() })
                        assertEquals(at, s.getString("reason"), m.reason)
                    }
                }
                "hint" -> {
                    if (s.has("error")) assertEquals(at, s.getString("error"), assertThrows(at, IllegalArgumentException::class.java) { m.hint(mark, now) }.message)
                    else {
                        val h = m.hint(mark, now)
                        val e = s.getJSONObject("expect")
                        if (e.has("row")) assertEquals(at, listOf(e.getInt("row"), e.getInt("col")), listOf(h.row, h.col))
                        assertEquals(at, e.getInt("score"), h.score)
                        assertEquals(at, e.getInt("hintsLeft"), h.hintsLeft)
                    }
                }
                else -> {
                    val sel = selection(m, s)
                    val run = { m.claim(mark, sel[0], sel[1], sel[2], sel[3], now) }
                    if (s.has("error")) assertEquals(at, s.getString("error"), assertThrows(at, IllegalArgumentException::class.java) { run() }.message)
                    else {
                        val r = run()
                        val e = s.getJSONObject("expect")
                        if (e.has("score")) assertEquals(at, e.getInt("score"), r.score)
                        assertEquals(at, e.getBoolean("over"), r.result.over)
                        if (e.getBoolean("over")) {
                            assertEquals(at, if (e.isNull("winner")) null else e.getString("winner")[0], r.result.winner)
                            assertEquals(at, norm(e.get("winners")), r.winners.map { it.toString() })
                            assertEquals(at, e.getString("reason"), r.result.reason)
                        }
                    }
                }
            }
        }
        for (mk in marks) assertNoUnfoundText(m, m.view(mk), "end")
    }

    @Test
    fun optionsAndPlayers() {
        assertEquals(HuntOptions(), HuntOptions.clean(null, null, null, null))
        assertTrue(HuntOptions.clean("medium", null, null, null).backwards)
        assertEquals(false, HuntOptions.clean("huge", false, null, null).backwards)
        assertEquals(HuntOptions(), HuntOptions.clean("x", null, 4, null))
        assertEquals(8, HuntOptions.clean(null, null, 8, null).timer)
        assertThrows(IllegalArgumentException::class.java) { HuntMatch(marks = emptyList()) }
        assertEquals(1, HuntMatch(marks = listOf('h')).marks.size)
        assertEquals(HuntOptions.clean("medium", null, null, null), HuntOptions(level = HuntLevel.MEDIUM))
        assertEquals("Wait for the countdown.", assertThrows(IllegalArgumentException::class.java) { HuntMatch(HuntOptions(seed = 3), startAt = 1000).claim('h', 0, 0, 0, 2, 999) }.message)
        assertThrows(IllegalArgumentException::class.java) { HuntMatch(marks = listOf('h', 'g', 'o', 'p', 'q')) }
        assertEquals((0.25 * 0x7fffffff).toInt(), HuntMatch(random = { 0.25 }).options.seed)
    }

    @Test
    fun theSameWordTwiceInARowGoesToTheFirst() {
        val m = HuntMatch(HuntOptions(seed = 3), listOf('h', 'g', 'o'))
        val w = m.grid.words[0]
        val (a, b) = HuntMatch.ends(w)
        assertEquals('g', m.claim('g', a.first, a.second, b.first, b.second, 5).mark)
        assertEquals("Already found by g", assertThrows(IllegalArgumentException::class.java) { m.claim('h', b.first, b.second, a.first, a.second, 5) }.message)
        assertEquals("Already found by g", assertThrows(IllegalArgumentException::class.java) { m.claim('o', a.first, a.second, b.first, b.second, 6) }.message)
        assertEquals(w.length, m.score('g'))
        assertEquals(0, m.score('h') + m.score('o'))
        assertNull(m.winner)
    }

    @Test
    fun movesGoThroughPlay() {
        val m = HuntMatch(HuntOptions(seed = 3))
        assertEquals(false, m.play('h', JSONObject().put("hint", true)).over)
        assertEquals("No hints left", assertThrows(IllegalArgumentException::class.java) { m.play('h', JSONObject().put("hint", true)) }.message)
        assertEquals("Not a word here", assertThrows(IllegalArgumentException::class.java) { m.play('g', JSONObject()) }.message)
    }

    @Test
    fun anOlderAppsThemeIsIgnored() {
        for (theme in listOf("Animals", "Food", "Nope")) {
            val o = HuntOptions.from(JSONObject().put("level", "medium").put("theme", theme).put("timer", 5))
            assertEquals(HuntOptions(HuntLevel.MEDIUM, true, 5), o)
        }
        val plain = HuntMatch(HuntOptions(seed = 9)).grid
        assertEquals(plain, HuntMatch(HuntOptions.from(JSONObject().put("level", "easy")).copy(seed = 9)).grid)
    }

    @Test
    fun noViewEverCarriesAnUnfoundWord() {
        val m = HuntMatch(HuntOptions(HuntLevel.MEDIUM, seed = 21, timer = 5), listOf('h', 'g', '3', '4'))
        val check = { at: String -> for (mk in m.marks + 'z') assertNoUnfoundText(m, m.view(mk), "$at ($mk)") }
        check("start")
        m.hint('h', 1); m.hint('g', 1)
        check("after hints")
        m.grid.words.dropLast(1).forEachIndexed { i, w ->
            val (a, b) = HuntMatch.ends(w)
            m.claim(m.marks[i % 4], a.first, a.second, b.first, b.second, 10L + i)
            check("after ${i + 1} claims")
        }
        m.tick(300000)
        check("over")
    }

    @Test
    fun theViewCountsAndListsFoundWordsInOrder() {
        val m = HuntMatch(HuntOptions(seed = 3))
        assertEquals(0, m.view('h').getJSONArray("found").length())
        assertEquals(10, m.view('h').getInt("total"))
        val (x, y) = m.grid.words
        val (xa, xb) = HuntMatch.ends(x)
        val (ya, yb) = HuntMatch.ends(y)
        m.claim('g', ya.first, ya.second, yb.first, yb.second, 50)
        m.claim('h', xa.first, xa.second, xb.first, xb.second, 20)
        val v = HuntView.from(m.view('h'))
        assertEquals(2, JSONObject(m.view('h').toString()).getInt("foundCount"))
        assertEquals(listOf(HuntFound(y.word, 'g', y.row, y.col, y.dir.key, 50), HuntFound(x.word, 'h', x.row, x.col, x.dir.key, 20)), v.found)
        assertEquals(10, v.total)
    }

    @Test
    fun aViewFromAnOlderServerStillShowsTheGame() {
        val old = JSONObject(HuntMatch(HuntOptions(seed = 3)).view('h').toString()).also { it.remove("total"); it.remove("found"); it.remove("foundCount") }
        old.put("theme", "Animals")
        old.put(
            "words",
            JSONArray()
                .put(JSONObject().put("word", "CAT").put("length", 3).put("foundBy", "g").put("row", 2).put("col", 1).put("dir", "right"))
                .put(JSONObject().put("word", "DEER").put("length", 4).put("foundBy", JSONObject.NULL)),
        )
        val v = HuntView.from(old)
        assertEquals(2, v.total)
        assertEquals(listOf(HuntFound("CAT", 'g', 2, 1, "right", 0)), v.found)
    }

    @Test
    fun aRealWordThatIsNotHiddenHasItsOwnRefusal() {
        // Find a grid where the fill letters happen to spell a 3 letter word of the vocabulary (4 or more never do).
        for (seed in 0 until 200) {
            val m = HuntMatch(HuntOptions(seed = seed))
            val hidden = m.grid.words.map { it.word }.toSet()
            for (r in 0 until m.grid.height) for (c in 0..m.grid.width - 3) {
                val t = m.grid.rows[r].substring(c, c + 3)
                if (t in WordHuntThemes.poolSet && t !in hidden) {
                    assertEquals("Not one of the hidden words", assertThrows(IllegalArgumentException::class.java) { m.claim('h', r, c, r, c + 2) }.message)
                    assertEquals("Not one of the hidden words", assertThrows(IllegalArgumentException::class.java) { m.claim('h', r, c + 2, r, c) }.message)
                    assertEquals("Not a word here", assertThrows(IllegalArgumentException::class.java) { m.claim('h', 0, 0, 1, 1) }.message)
                    return
                }
            }
        }
        throw AssertionError("no grid in 200 seeds has a spelled-by-chance word")
    }
}
