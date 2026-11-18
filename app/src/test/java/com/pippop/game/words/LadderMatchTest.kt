package com.pippop.game.words

import com.pippop.game.grid.GridWords
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Runs the cases shared with the server (src/test/resources/wordladder-cases.json). */
class LadderMatchTest {
    private fun read(name: String) = File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }

    @Before
    fun load() {
        WordList.install(read("answers"), read("allowed"))
        GridWords.install(read("grid"))
        WordLadder.install(read("ladder"), read("blocklist"))
    }

    private val cases = JSONObject(File("src/test/resources/wordladder-cases.json").readText()).getJSONArray("cases")

    @Test
    fun sharedCases() {
        assertTrue(cases.length() >= 7)
        for (i in 0 until cases.length()) runCase(cases.getJSONObject(i))
    }

    private fun runCase(c: JSONObject) {
        val opts = c.getJSONObject("options")
        val coin = c.getDouble("random")
        val match = LadderMatch(LadderOptions.clean(opts.getInt("length"), opts.getInt("timer")), random = { coin })
        val steps = c.getJSONArray("steps")
        for (n in 0 until steps.length()) {
            val s = steps.getJSONObject(n)
            val at = "${c.getString("name")}, step ${n + 1} (${s.getString("do")})"
            val now = s.optLong("now", 0)
            when (s.getString("do")) {
                "view" -> {
                    val v = match.view(s.getString("mark")[0])
                    val expect = s.getJSONObject("expect")
                    for (k in expect.keys()) assertEquals("$at: $k", norm(expect.get(k)), norm(v.get(k)))
                }
                "tick" -> {
                    assertEquals(at, s.getBoolean("passed"), match.tick(now))
                    assertEquals(at, s.getString("turn")[0], match.turn)
                }
                else -> {
                    val mark = s.getString("mark")[0]
                    val run = { if (s.getString("do") == "pick") match.setPick(mark, s.getString("word"), now) else match.play(mark, s.getString("word"), now) }
                    if (s.has("error")) {
                        assertEquals(at, s.getString("error"), assertThrows(at, IllegalArgumentException::class.java) { run() }.message)
                    } else {
                        val r = run()
                        if (s.getString("do") == "pick") assertEquals(at, s.optBoolean("revealed", false), match.revealed)
                        else {
                            assertEquals(at, s.optBoolean("over", false), r.over)
                            assertEquals(at, if (s.has("winner")) s.getString("winner")[0] else null, r.winner)
                            assertEquals(at, s.optString("reason", "").ifEmpty { null }, r.reason)
                        }
                    }
                }
            }
        }
    }

    /** JSON values as plain Kotlin ones, so a view and a fixture compare equal. */
    private fun norm(v: Any?): Any? = when (v) {
        JSONObject.NULL -> null
        is JSONArray -> (0 until v.length()).map { norm(v.get(it)) }
        is Int -> v.toLong()
        else -> v
    }

    @Test
    fun optionsAndMoves() {
        assertEquals(LadderOptions(6, 60), LadderOptions.clean(6, 60))
        assertEquals(LadderOptions(5, 0), LadderOptions.clean(9, 5))
        assertTrue(WordLadder.isRealWord("CORD"))
        assertFalse(WordLadder.isRealWord("DAMN"))
        assertFalse(WordLadder.isRealWord("CXLD"))
        val m = LadderMatch(LadderOptions(4, 0), random = { 0.1 })
        m.setPick('h', "MOON")
        m.setPick('g', "MOOD")
        val moves = m.legalMoves()
        assertTrue("MUON" in moves && "SOON" in moves && "MOON" !in moves)
    }
}
