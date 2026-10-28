package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.GameKind
import com.pippop.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** One room playing game after game, as on the server (backend/cloudflare/test/session.test.js). */
class SessionTest {
    private val all = GameKind.entries.map { it.key }

    private fun pair(game: GameKind = GameKind.CONNECT4) = LocalRoom("PAIR1", "Alice", null, game).also {
        it.join("Bob", null, null, all)
    }

    private fun LocalRoom.status(role: String) = view(role, 0).getString("status")
    private fun LocalRoom.at(role: String): Any = view(role, 0).getJSONObject("me").get("at")

    /** Plays Connect Four to a win for whoever moves first. */
    private fun LocalRoom.playToWin() {
        val starter = if (view(LocalRoom.HOST, 0).getJSONObject("turns").getString("turn") == "me") LocalRoom.HOST else LocalRoom.GUEST
        val other = LocalRoom.other(starter)
        for (col in listOf(0, 1, 0, 1, 0, 1, 0)) move(if (col == 0) starter else other, JSONObject().put("col", col), 0)
        assertEquals("finished", status(LocalRoom.HOST))
    }

    @Test
    fun everyoneStartsAtTheGameTheRoomWasMadeFor() {
        val room = pair()
        assertEquals("connect4", room.at(LocalRoom.HOST))
        assertEquals("connect4", room.at(LocalRoom.GUEST))
        assertTrue(room.view(LocalRoom.HOST, 0).getBoolean("setsOptions"))
        assertFalse(room.view(LocalRoom.GUEST, 0).getBoolean("setsOptions"))
    }

    @Test
    fun openingAnotherGameKeepsSeatsAndTheOthersStayPut() {
        val room = pair()
        room.setTurnReady(LocalRoom.HOST, true, 0)
        room.setTurnReady(LocalRoom.GUEST, true, 0, Random(1))
        room.playToWin()
        val ids = listOf(room.playerId(LocalRoom.HOST), room.playerId(LocalRoom.GUEST))
        val round = room.view(LocalRoom.HOST, 0).getInt("round")

        val dots = room.openGame(LocalRoom.GUEST, GameKind.DOTS)
        assertNotSame(room, dots)
        assertEquals(GameKind.DOTS, dots.game)
        assertEquals("PAIR1", dots.code)
        assertEquals("lobby", dots.status(LocalRoom.HOST))
        assertEquals(ids, listOf(dots.playerId(LocalRoom.HOST), dots.playerId(LocalRoom.GUEST)))
        assertEquals(round, dots.view(LocalRoom.HOST, 0).getInt("round"))
        assertTrue(dots.view(LocalRoom.HOST, 0).getInt("version") > room.view(LocalRoom.HOST, 0).getInt("version"))

        // Alice is still on the Connect Four screen; her phone can see Bob went to Dots.
        val alice = dots.view(LocalRoom.HOST, 0)
        assertEquals("dots", alice.getString("game"))
        assertEquals("connect4", alice.getJSONObject("me").get("at"))
        assertEquals("dots", alice.getJSONObject("opponent").get("at"))
        assertFalse(alice.getBoolean("setsOptions"))
        assertTrue(dots.view(LocalRoom.GUEST, 0).getBoolean("setsOptions"))
    }

    @Test
    fun aMatchStartsOnlyOnceTheOthersComeToTheGame() {
        val dots = pair().openGame(LocalRoom.HOST, GameKind.DOTS)
        val e = assertThrows(ApiException::class.java) { dots.setTurnReady(LocalRoom.HOST, true, 0) }
        assertTrue(e.message!!.contains("come to this game"))
        assertSame(dots, dots.openGame(LocalRoom.GUEST, GameKind.DOTS))
        dots.setTurnReady(LocalRoom.HOST, true, 0)
        dots.setTurnReady(LocalRoom.GUEST, true, 0)
        assertEquals("playing", dots.status(LocalRoom.HOST))
    }

    @Test
    fun onlyThePlayerWhoOpenedTheGameChangesItsOptions() {
        val ttt = pair().openGame(LocalRoom.GUEST, GameKind.TTT)
        val e = assertThrows(ApiException::class.java) { ttt.setOptions(LocalRoom.HOST, JSONObject().put("mode", "classic")) }
        assertTrue(e.message!!.contains("Bob can change the rules"))
        ttt.setOptions(LocalRoom.GUEST, JSONObject().put("mode", "classic"))
        assertEquals("classic", ttt.view(LocalRoom.GUEST, 0).getJSONObject("options").getString("mode"))
    }

    @Test
    fun theMenuKeepsYouInTheRoomAndLeavingAMatchForItGivesItUp() {
        val room = pair()
        room.setTurnReady(LocalRoom.HOST, true, 0)
        room.setTurnReady(LocalRoom.GUEST, true, 0)
        room.toMenu(LocalRoom.GUEST, 5)
        assertEquals("finished", room.status(LocalRoom.HOST))
        assertEquals("me", room.view(LocalRoom.HOST, 5).getString("winner"))
        assertEquals(JSONObject.NULL, room.at(LocalRoom.GUEST))
        assertFalse(room.view(LocalRoom.GUEST, 5).getJSONObject("me").getBoolean("left"))
        // Back from the menu to the same game, for a rematch.
        assertSame(room, room.openGame(LocalRoom.GUEST, GameKind.CONNECT4))
        room.setTurnReady(LocalRoom.HOST, true, 6)
        room.setTurnReady(LocalRoom.GUEST, true, 6)
        assertEquals("playing", room.view(LocalRoom.HOST, 6).getString("status"))
    }

    @Test
    fun anotherGameCannotOpenDuringAMatch() {
        val room = pair()
        room.setTurnReady(LocalRoom.HOST, true, 0)
        room.setTurnReady(LocalRoom.GUEST, true, 0)
        val e = assertThrows(ApiException::class.java) { room.openGame(LocalRoom.HOST, GameKind.DOTS) }
        assertEquals("busy", e.reason)
    }

    @Test
    fun aTwoPlayerGameIsForTheFirstTwoSeats() {
        val room = LocalRoom("FOUR4", "Alice", null, GameKind.CLASH)
        room.join("Bob", null, null, all)
        room.join("Cat", null, null, all)
        val e = assertThrows(ApiException::class.java) { room.openGame(LocalRoom.P3, GameKind.CONNECT4) }
        assertEquals("full", e.reason)
        val c4 = room.openGame(LocalRoom.HOST, GameKind.CONNECT4)
        c4.openGame(LocalRoom.GUEST, GameKind.CONNECT4)
        c4.setTurnReady(LocalRoom.HOST, true, 0)
        c4.setTurnReady(LocalRoom.GUEST, true, 0)
        assertEquals("playing", c4.status(LocalRoom.HOST))
        // Cat stayed on the Colour Clash screen and isn't part of the match.
        assertEquals("clash", c4.at(LocalRoom.P3))
    }

    @Test
    fun theAppReadsWhereEveryoneIs() {
        val dots = pair().openGame(LocalRoom.GUEST, GameKind.DOTS)
        val alice = parseRoom(dots.view(LocalRoom.HOST, 0))
        assertTrue(alice.inSession)
        assertFalse(alice.atGame)
        assertEquals(GameKind.CONNECT4, alice.me.at)
        assertEquals(listOf("Bob"), alice.othersAtGame.map { it.name })
        assertFalse(alice.setsOptions)
        dots.toMenu(LocalRoom.HOST, 0)
        assertEquals(null, parseRoom(dots.view(LocalRoom.HOST, 0)).me.at)
        assertTrue(parseRoom(dots.view(LocalRoom.GUEST, 0)).atGame)
    }
}
