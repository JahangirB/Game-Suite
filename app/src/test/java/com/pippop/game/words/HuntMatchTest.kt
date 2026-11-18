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
        o.getString("level"), o.getString("theme"), if (o.has("backwards")) o.getBoolean("backwards") else null,
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

    private fun assertNoLeak(v: JSONObject, at: String) {
        val words = v.getJSONArray("words")
        for (i in 0 until words.length()) {
            val w = words.getJSONObject(i)
            if (w.isNull("foundBy")) assertEquals("$at: ${w.getString("word")} leaks", setOf("word", "length", "foundBy"), w.keys().asSequence().toSet())
        }
    }

    @Test
    fun sharedCases() {
        assertTrue(cases.length() >= 8)
        for (i in 0 until cases.length()) runCase(cases.getJSONObject(i))
    }

    private fun runCase(c: JSONObject) {
        val marksJson = c.getJSONArray("marks")
        val marks = List(marksJson.length()) { marksJson.getString(it)[0] }
        val m = HuntMatch(options(c.getJSONObject("options")), marks, now = c.getLong("now"))
        val steps = c.getJSONArray("steps")
        for (n in 0 until steps.length()) {
            val s = steps.getJSONObject(n)
            val at = "${c.getString("name")}, step ${n + 1} (${s.getString("do")})"
            val now = s.optLong("now", 0)
            val mark = s.optString("mark", " ").first()
            when (s.getString("do")) {
                "view" -> {
                    val v = m.view(mark)
                    assertNoLeak(v, at)
                    val expect = s.getJSONObject("expect")
                    for (k in expect.keys()) {
                        if (k == "foundBy") {
                            val got = (0 until v.getJSONArray("words").length()).associate { v.getJSONArray("words").getJSONObject(it).let { w -> w.getString("word") to norm(w.get("foundBy")) } }
                            assertEquals("$at: foundBy", norm(expect.get(k)), got)
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
                        assertEquals(at, e.getInt("score"), r.score)
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
        for (mk in marks) assertNoLeak(m.view(mk), "end")
    }

    @Test
    fun optionsAndPlayers() {
        assertEquals(HuntOptions(), HuntOptions.clean(null, null, null, null, null))
        assertTrue(HuntOptions.clean("medium", null, null, null, null).backwards)
        assertEquals(false, HuntOptions.clean("huge", null, false, null, null).backwards)
        assertEquals(HuntOptions(), HuntOptions.clean("x", "Nope", null, 4, null))
        assertEquals(8, HuntOptions.clean(null, null, null, 8, null).timer)
        assertThrows(IllegalArgumentException::class.java) { HuntMatch(marks = listOf('h')) }
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
}
