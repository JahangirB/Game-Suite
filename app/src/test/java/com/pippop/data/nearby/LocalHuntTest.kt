package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import com.pippop.data.parseRoom
import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntMatch
import com.pippop.game.words.HuntOptions
import com.pippop.game.words.HuntView
import com.pippop.game.words.WordHuntThemes
import com.pippop.game.words.WordLadder
import com.pippop.ui.screens.StoreFilter
import com.pippop.ui.screens.storeList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** Word Hunt in a nearby room: a full 3-player race, the clock, and what the guests' phones are told. */
class LocalHuntTest {
    private val t0 = 1_000_000L
    private val go = t0 + LocalRoom.COUNTDOWN_MS
    private val roles = listOf(LocalRoom.HOST, LocalRoom.GUEST, LocalRoom.P3)

    @Before
    fun words() {
        fun read(name: String) = java.io.File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }
        WordLadder.install(read("ladder"), read("blocklist"))
        WordHuntThemes.install(read("hunt"))
    }

    /** A random source whose doubles are all about [x]: 0.0 makes seed 0, 0.9 another one. */
    private fun fixed(x: Double) = object : Random() {
        override fun nextBits(bitCount: Int) = if (x < 0.5) 0 else -1 ushr (32 - bitCount)
    }

    private fun hunt(options: JSONObject = JSONObject().put("level", "easy").put("theme", "Food").put("backwards", false).put("timer", 3), random: Random = fixed(0.0)): LocalRoom {
        val r = LocalRoom("HUNT", "Alice", Avatar.FOX, GameKind.HUNT)
        r.join("Bob", Avatar.OWL, null, listOf("hunt"))
        r.join("Cara", Avatar.FOX, null, listOf("hunt"))
        roles.forEach { r.setConnected(it, true, t0) }
        r.setOptions(LocalRoom.HOST, options)
        roles.forEach { r.setTurnReady(it, true, t0, random) }
        return r
    }

    /** The same grid the room made: seed 0 with these rules (the room's random gives 0 there). */
    private fun twin(seed: Int = 0) = HuntMatch(HuntOptions(HuntLevel.EASY, "Food", false, 3, seed), listOf('h', 'g', '3'), startAt = go)

    private fun LocalRoom.hunt(role: String, now: Long = go + 1000): HuntView = parseRoom(view(role, now)).turns!!.hunt!!
    private fun LocalRoom.claim(role: String, m: HuntMatch, word: String, now: Long, reverse: Boolean = false) {
        val w = m.grid.words.first { it.word == word }
        val (a, b) = HuntMatch.ends(w)
        val (s, e) = if (reverse) b to a else a to b
        move(role, JSONObject().put("r1", s.first).put("c1", s.second).put("r2", e.first).put("c2", e.second), now)
    }

    @Test
    fun aFullThreePlayerRace() {
        val r = hunt()
        val m = twin()
        // Countdown: nobody sees the grid and nobody can claim yet.
        assertEquals("countdown", r.view(LocalRoom.GUEST, t0 + 1).getString("status"))
        assertTrue(r.hunt(LocalRoom.GUEST, t0 + 1).rows.isEmpty())
        assertEquals("Wait for the countdown.", assertThrows(ApiException::class.java) { r.claim(LocalRoom.GUEST, m, m.grid.words[0].word, t0 + 1) }.message)
        assertEquals(10, r.hunt(LocalRoom.GUEST, go).rows.size)
        assertNull(r.view(LocalRoom.HOST, go).getJSONObject("turns").let { if (it.isNull("turn")) null else it.get("turn") })

        val words = m.grid.words.map { it.word }
        // Two phones race for the same word: the first the host sees wins; the drag may be made backwards.
        r.claim(LocalRoom.GUEST, m, words[0], go + 100, reverse = true)
        assertEquals("Already found by g", assertThrows(ApiException::class.java) { r.claim(LocalRoom.HOST, m, words[0], go + 100) }.message)
        assertEquals("Already found by g", assertThrows(ApiException::class.java) { r.claim(LocalRoom.P3, m, words[0], go + 101, reverse = true) }.message)
        assertEquals("Not a word here", assertThrows(ApiException::class.java) { r.move(LocalRoom.HOST, JSONObject().put("r1", 0).put("c1", 0).put("r2", 1).put("c2", 1), go + 102) }.message)
        assertEquals(m.grid.words[0].length, r.hunt(LocalRoom.HOST).scores['g'])
        // A hint costs 2 points, once, and only its player sees it.
        r.claim(LocalRoom.P3, m, words[1], go + 200)
        val before = r.hunt(LocalRoom.P3).scores.getValue('3')
        r.move(LocalRoom.P3, JSONObject().put("hint", true), go + 300)
        assertEquals(maxOf(0, before - 2), r.hunt(LocalRoom.P3).scores['3'])
        assertEquals(1, r.hunt(LocalRoom.P3).hints.size)
        assertTrue(r.hunt(LocalRoom.HOST).hints.isEmpty())
        assertEquals("No hints left", assertThrows(ApiException::class.java) { r.move(LocalRoom.P3, JSONObject().put("hint", true), go + 301) }.message)
        // The host finds the rest: the round ends and the host wins.
        for (w in words.drop(2)) r.claim(LocalRoom.HOST, m, w, go + 400)
        val over = r.hunt(LocalRoom.GUEST, go + 500)
        assertEquals("over", over.phase)
        assertEquals('h', over.winner)
        assertEquals(listOf('h', 'g', '3'), over.results.map { it.mark })
        assertEquals("finished", r.view(LocalRoom.GUEST, go + 500).getString("status"))
        // A rematch makes a new grid.
        roles.forEach { r.setTurnReady(it, true, go + 600, fixed(0.9)) }
        val next = r.hunt(LocalRoom.GUEST, go + 600 + LocalRoom.COUNTDOWN_MS + 1)
        assertEquals("playing", next.phase)
        assertNotEquals(m.grid.rows, next.rows)
        assertEquals(0, next.scores['h'])
    }

    @Test
    fun guestsNeverGetTheWhereaboutsOfAnUnfoundWordOrTheSeed() {
        val r = hunt(JSONObject().put("level", "easy").put("theme", "Food").put("backwards", false).put("timer", 3).put("seed", 12345))
        val m = twin()
        r.claim(LocalRoom.HOST, m, m.grid.words[0].word, go + 10)
        for (role in roles) {
            val text = r.view(role, go + 20).toString()
            assertFalse(role, text.contains("\"seed\"") || text.contains("12345"))
            val v = r.hunt(role, go + 20)
            assertEquals(m.grid.words.size, v.words.size)
            for (w in v.words) if (w.foundBy == null) assertTrue(w.word, w.row == null && w.col == null && w.dir == null)
            assertEquals('h', v.words.first { it.word == m.grid.words[0].word }.foundBy)
        }
        assertEquals(HuntOptions(HuntLevel.EASY, "Food", false, 3), parseRoom(r.view(LocalRoom.GUEST, go)).huntOptions)
        // The seed the phone sent was not used: the grid is the one seed 0 makes.
        assertEquals(twin().grid.rows, r.hunt(LocalRoom.GUEST, go).rows)
    }

    @Test
    fun theClockEndsTheRoundAndALateClaimDoesToo() {
        val r = hunt()
        val m = twin()
        val ends = go + 3 * 60_000L
        r.claim(LocalRoom.GUEST, m, m.grid.words[0].word, go + 5000)
        assertTrue(r.checkTimers(go)) // the push that shows the grid when the countdown ends
        assertFalse(r.checkTimers(ends - 1))
        assertTrue(r.checkTimers(ends))
        assertEquals("finished", r.view(LocalRoom.HOST, ends).getString("status"))
        assertEquals("time", r.hunt(LocalRoom.HOST, ends).reason)
        assertEquals('g', r.hunt(LocalRoom.HOST, ends).winner)
        // Again: a draw, and a claim after the end time (no timer call before it) ends the round.
        val late = hunt()
        late.claim(LocalRoom.HOST, m, m.grid.words[0].word, go + 10)
        late.claim(LocalRoom.GUEST, m, m.grid.words[1].word, ends + 1)
        assertEquals("finished", late.view(LocalRoom.HOST, ends + 1).getString("status"))
        assertEquals(0, late.hunt(LocalRoom.HOST, ends + 1).scores['g'])
        assertEquals('h', late.hunt(LocalRoom.HOST, ends + 1).winner)
        val draw = hunt()
        assertTrue(draw.checkTimers(ends))
        assertNull(draw.hunt(LocalRoom.HOST, ends).winner)
        assertEquals(listOf('h', 'g', '3'), draw.hunt(LocalRoom.HOST, ends).winners)
    }

    @Test
    fun whenOneOfTwoLeavesTheRoundEndsAsLeftAndKeepsTheResults() {
        val r = LocalRoom("HUNT", "Alice", Avatar.FOX, GameKind.HUNT)
        r.join("Bob", Avatar.OWL, null, listOf("hunt"))
        listOf(LocalRoom.HOST, LocalRoom.GUEST).forEach { r.setConnected(it, true, t0) }
        r.setOptions(LocalRoom.HOST, JSONObject().put("level", "easy").put("theme", "Food").put("backwards", false).put("timer", 0))
        listOf(LocalRoom.HOST, LocalRoom.GUEST).forEach { r.setTurnReady(it, true, t0, fixed(0.0)) }
        val m = HuntMatch(HuntOptions(HuntLevel.EASY, "Food", false, 0, 0), listOf('h', 'g'), startAt = go)
        r.claim(LocalRoom.GUEST, m, m.grid.words[0].word, go + 10)
        r.claim(LocalRoom.HOST, m, m.grid.words[1].word, go + 20)
        r.leave(LocalRoom.GUEST, go + 30)
        assertEquals("finished", r.view(LocalRoom.HOST, go + 40).getString("status"))
        val v = r.hunt(LocalRoom.HOST, go + 40)
        assertEquals("over", v.phase)
        assertEquals("left", v.reason)
        assertEquals(m.grid.words[0].length, v.scores['g'])
        assertEquals(setOf('h', 'g'), v.results.map { it.mark }.toSet())
        assertEquals(1, v.results.first { it.mark == 'g' }.found)
    }

    @Test
    fun wordHuntIsInTheStoreNotOnTheShelf() {
        assertFalse(GameKind.HUNT in ProfileStore.DEFAULT_SHELF)
        assertTrue(GameKind.HUNT in ProfileStore.storeGames(ProfileStore.DEFAULT_SHELF))
        assertTrue(GameKind.HUNT in storeList(ProfileStore.DEFAULT_SHELF, StoreFilter.ALL))
        assertTrue(GameKind.HUNT in storeList(ProfileStore.DEFAULT_SHELF, StoreFilter.WORDS))
        assertEquals("Word Hunt", GameKind.HUNT.label)
        assertEquals("hunt", GameKind.HUNT.key)
        assertEquals(4, GameKind.HUNT.maxPlayers)
    }
}
