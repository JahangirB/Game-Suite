package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.Reaction
import com.pippop.data.RoomStatus
import com.pippop.data.parseRoom
import com.pippop.game.Generator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDrawTest {
    private val t0 = 1_000_000L

    @org.junit.Before
    fun words() {
        fun read(name: String) = java.io.File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }
        com.pippop.game.words.WordList.install(read("answers"), read("allowed"), read("draw"))
    }

    private fun drawRoom(players: Int, race: Boolean): LocalRoom {
        val r = LocalRoom("DRAW", "Alice", Avatar.FOX, com.pippop.data.GameKind.DRAW)
        listOf("Bob", "Cy", "Di").take(players - 1).forEach { r.join(it, Avatar.OWL, null, listOf("draw")) }
        if (race) r.setOptions(LocalRoom.HOST, org.json.JSONObject().put("mode", "race"))
        r.seated().forEach { r.setDrawReady(it, true, t0) }
        return r
    }

    private fun LocalRoom.drawerRole() = seated().first { parseRoom(view(it, t0)).draw?.drawing == true }

    @Test
    fun undoTakesBackAWholeLineAndATurnKeepsItsDrawing() {
        val r = drawRoom(2, race = false)
        val go = t0 + 10_000
        val drawer = r.drawerRole()
        val line = org.json.JSONObject().put("points", org.json.JSONArray().put(org.json.JSONArray().put(0.1).put(0.1)).put(org.json.JSONArray().put(0.2).put(0.2))).put("color", 7).put("width", 3)
        r.addStroke(drawer, org.json.JSONObject(line.toString()).put("start", true), go)
        r.addStroke(drawer, org.json.JSONObject(line.toString()).put("start", true), go)
        r.addStroke(drawer, line, go)
        r.undoStroke(drawer, go)
        val guesser = r.seated().first { it != drawer }
        assertEquals(1, parseRoom(r.view(guesser, go)).draw!!.strokes.size)
        val word = parseRoom(r.view(drawer, go)).draw!!.word!!
        r.drawGuess(guesser, word, go + 1000)
        val last = parseRoom(r.view(guesser, go + 1000)).draw!!.lastTurn!!
        assertEquals(1, last.strokes.size)
        assertEquals(7, last.strokes[0].color)
    }

    @Test
    fun theGuessRaceScoresRightGuessesInOrder() {
        val r = drawRoom(4, race = true)
        val go = t0 + 10_000
        val drawer = r.drawerRole()
        val word = parseRoom(r.view(drawer, go)).draw!!.word!!
        val guessers = r.seated().filter { it != drawer }
        r.drawGuess(guessers[0], word, go)
        assertTrue(parseRoom(r.view(guessers[0], go)).draw!!.gotIt)
        assertNull(parseRoom(r.view(guessers[1], go)).draw!!.word)
        assertThrows(com.pippop.data.ApiException::class.java) { r.drawGuess(guessers[0], word, go) }
        r.drawGuess(guessers[1], word, go)
        r.drawGuess(guessers[2], word, go)
        val draw = parseRoom(r.view(drawer, go)).draw!!
        assertTrue(draw.race)
        assertEquals(listOf(80, 60, 40, 60), (guessers + drawer).map { g -> draw.scores.first { it.name == r.player(g).name }.points })
        assertEquals(3, draw.lastTurn!!.rights.size)
        assertEquals(2, draw.turn)
    }
}
