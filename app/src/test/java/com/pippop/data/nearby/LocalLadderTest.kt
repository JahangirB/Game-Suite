package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import com.pippop.data.RoomStatus
import com.pippop.data.parseRoom
import com.pippop.game.words.WordLadder
import com.pippop.game.words.WordList
import com.pippop.ui.screens.StoreFilter
import com.pippop.ui.screens.storeList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** Word Ladder in a nearby room: a full game, the turn timer, and what the guest's phone is told. */
class LocalLadderTest {
    private val t0 = 1_000_000L

    @Before
    fun words() {
        fun read(name: String) = java.io.File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }
        WordList.install(read("answers"), read("allowed"))
        WordLadder.install(read("ladder"), read("blocklist"))
    }

    /** A random source whose doubles are about [x] (0.0 for the host's word as the start, 0.99 for the guest's). */
    private fun fixed(x: Double) = object : Random() {
        override fun nextBits(bitCount: Int) = if (x < 0.5) 0 else -1 ushr (32 - bitCount)
    }

    private fun ladder(length: Int = 4, timer: Int = 0, coin: Double = 0.0): LocalRoom {
        val r = LocalRoom("LADR", "Alice", Avatar.FOX, GameKind.LADDER)
        r.join("Bob", Avatar.OWL, null, listOf("ladder"))
        r.setOptions(LocalRoom.HOST, JSONObject().put("length", length).put("timer", timer))
        r.setTurnReady(LocalRoom.HOST, true, t0, fixed(coin))
        r.setTurnReady(LocalRoom.GUEST, true, t0, fixed(coin))
        return r
    }

    private fun LocalRoom.turns(role: String, now: Long = t0) = parseRoom(view(role, now)).turns!!.ladder!!
    private fun LocalRoom.everything(role: String) = view(role, t0).toString()

    @Test
    fun picksStaySecretThenTheTargetOwnerMovesFirst() {
        val r = ladder()
        assertEquals("started", r.view(LocalRoom.HOST, t0).getString("status").let { if (it == "playing") "started" else it })
        assertEquals("picking", r.turns(LocalRoom.HOST).phase)
        assertThrows(ApiException::class.java) { r.move(LocalRoom.GUEST, JSONObject().put("word", "BOLD"), t0) }
        r.pickWord(LocalRoom.HOST, "cold", t0)
        assertFalse(r.everything(LocalRoom.GUEST).contains("COLD"))
        assertEquals("COLD", r.turns(LocalRoom.HOST).myPick)
        assertTrue(r.turns(LocalRoom.GUEST).opponentPicked)
        assertNull(r.turns(LocalRoom.GUEST).myPick)
        assertEquals("Pick another word.", assertThrows(ApiException::class.java) { r.pickWord(LocalRoom.GUEST, "COLD", t0) }.message)
        assertEquals("No way from ABLE to the other word yet. Pick another.", assertThrows(ApiException::class.java) { r.pickWord(LocalRoom.GUEST, "ABLE", t0) }.message)
        assertFalse(r.everything(LocalRoom.GUEST).contains("COLD") || r.everything(LocalRoom.HOST).contains("ABLE"))
        r.pickWord(LocalRoom.GUEST, "TOLL", t0)
        val guest = r.turns(LocalRoom.GUEST)
        assertEquals(listOf("COLD", "TOLL", "me", "opponent"), listOf(guest.start, guest.target, guest.turn, guest.startOwner))
        assertEquals("opponent", r.turns(LocalRoom.HOST).turn)
        assertEquals(listOf("COLD"), guest.chain)
        // The turn's name follows the reveal: Bob owns the target and moves first.
        assertEquals("Bob", parseRoom(r.view(LocalRoom.HOST, t0)).turns!!.turnName)
        assertEquals("Bob", parseRoom(r.view(LocalRoom.GUEST, t0)).turns!!.turnName)
    }

    @Test
    fun aFullGameWithAWinAndARematch() {
        val r = ladder()
        r.pickWord(LocalRoom.HOST, "COLD", t0)
        r.pickWord(LocalRoom.GUEST, "TOLL", t0)
        assertThrows(ApiException::class.java) { r.move(LocalRoom.HOST, JSONObject().put("word", "CORD"), t0) }
        assertEquals("Change one letter", assertThrows(ApiException::class.java) { r.move(LocalRoom.GUEST, JSONObject().put("word", "WARM"), t0) }.message)
        assertEquals("Not a word", assertThrows(ApiException::class.java) { r.move(LocalRoom.GUEST, JSONObject().put("word", "CXLD"), t0) }.message)
        r.move(LocalRoom.GUEST, JSONObject().put("word", "TOLD"), t0)
        assertEquals("me", r.turns(LocalRoom.HOST).turn)
        assertEquals("Already used", assertThrows(ApiException::class.java) { r.move(LocalRoom.HOST, JSONObject().put("word", "COLD"), t0) }.message)
        r.move(LocalRoom.HOST, JSONObject().put("word", "TOLL"), t0)
        val room = parseRoom(r.view(LocalRoom.GUEST, t0))
        assertEquals(RoomStatus.FINISHED, room.status)
        assertEquals("opponent", room.winner)
        assertEquals("target", room.turns!!.ladder!!.reason)
        assertEquals(listOf("COLD", "TOLD", "TOLL"), room.turns!!.ladder!!.chain)
        // Rematch: fresh picks, and the coin can go the other way.
        r.setTurnReady(LocalRoom.HOST, true, t0 + 1, fixed(0.99))
        r.setTurnReady(LocalRoom.GUEST, true, t0 + 1, fixed(0.99))
        assertEquals("picking", r.turns(LocalRoom.HOST).phase)
        assertNull(r.turns(LocalRoom.HOST).myPick)
        r.pickWord(LocalRoom.HOST, "MOON", t0)
        r.pickWord(LocalRoom.GUEST, "MOOD", t0)
        // The guest's word (MOOD) is the start; the host owns MOON, the target, and moves first.
        assertEquals("me", r.turns(LocalRoom.HOST).turn)
        r.move(LocalRoom.HOST, JSONObject().put("word", "MOON"), t0)
        assertEquals("me", parseRoom(r.view(LocalRoom.HOST, t0)).winner)
    }

    @Test
    fun noMoveLeftLoses() {
        val r = ladder()
        r.pickWord(LocalRoom.HOST, "MOON", t0)
        r.pickWord(LocalRoom.GUEST, "MOOD", t0)
        r.move(LocalRoom.GUEST, JSONObject().put("word", "MUON"), t0)
        assertEquals(RoomStatus.FINISHED, parseRoom(r.view(LocalRoom.HOST, t0)).status)
        assertEquals("me", parseRoom(r.view(LocalRoom.GUEST, t0)).winner)
        assertEquals("stuck", r.turns(LocalRoom.HOST).reason)
    }

    @Test
    fun theHostsClockPassesTheTurn() {
        val r = ladder(timer = 30)
        r.pickWord(LocalRoom.HOST, "COLD", t0 + 1000)
        r.pickWord(LocalRoom.GUEST, "TOLL", t0 + 2000)
        assertEquals(t0 + 32_000, r.turns(LocalRoom.GUEST).turnEndsAt)
        assertFalse(r.checkTimers(t0 + 31_999))
        assertTrue(r.checkTimers(t0 + 32_000))
        assertEquals("me", r.turns(LocalRoom.HOST).turn)
        assertEquals("opponent", r.turns(LocalRoom.GUEST).turn)
        assertEquals("Alice", parseRoom(r.view(LocalRoom.GUEST, t0 + 32_000)).turns!!.turnName)
        assertThrows(ApiException::class.java) { r.move(LocalRoom.GUEST, JSONObject().put("word", "TOLD"), t0 + 33_000) }
        r.move(LocalRoom.HOST, JSONObject().put("word", "TOLD"), t0 + 40_000)
        assertEquals(t0 + 70_000, r.turns(LocalRoom.GUEST, t0 + 40_000).turnEndsAt)
        assertEquals(30, parseRoom(r.view(LocalRoom.HOST, t0)).ladderOptions.timer)
    }

    @Test
    fun aSeatThatIsNotInTheGameCannotPickAndTheGameGoesOn() {
        val r = ladder()
        r.pickWord(LocalRoom.HOST, "COLD", t0)
        assertEquals("You're not in this game.", assertThrows(ApiException::class.java) { r.pickWord("p3", "TOLL", t0) }.message)
        val match = com.pippop.game.words.LadderMatch(com.pippop.game.words.LadderOptions(4, 0), random = { 0.1 })
        assertEquals("You're not in this game.", assertThrows(IllegalArgumentException::class.java) { match.setPick('3', "COLD") }.message)
        r.pickWord(LocalRoom.GUEST, "TOLL", t0)
        assertEquals(listOf("COLD"), r.turns(LocalRoom.GUEST).chain)
        assertEquals("me", r.turns(LocalRoom.GUEST).turn)
    }

    @Test
    fun wordLadderIsInTheStoreNotOnTheShelf() {
        assertFalse(GameKind.LADDER in ProfileStore.DEFAULT_SHELF)
        assertTrue(GameKind.LADDER in ProfileStore.storeGames(ProfileStore.DEFAULT_SHELF))
        assertTrue(GameKind.LADDER in storeList(ProfileStore.DEFAULT_SHELF, StoreFilter.ALL))
        assertEquals("Word Ladder", GameKind.LADDER.label)
        assertEquals("ladder", GameKind.LADDER.key)
    }
}
