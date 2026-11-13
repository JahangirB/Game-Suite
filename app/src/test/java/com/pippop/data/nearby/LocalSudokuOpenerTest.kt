package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sudoku where whoever opened the game picks the level: the same rules as the servers'. */
class LocalSudokuOpenerTest {
    private val t0 = 1_000_000L
    private val NEW = listOf("sudoku", LocalRoom.BIG_SUDOKU, LocalRoom.SUDOKU_OPENER)
    private val OLD = listOf("sudoku", LocalRoom.BIG_SUDOKU)

    private fun room(guest: List<String>) = LocalRoom("OPN1", "Alice", Avatar.FOX).apply { join("Bob", Avatar.OWL, null, guest) }

    @Test
    fun everyRoomStateListsTheCap() {
        val r = room(NEW)
        assertEquals(listOf(LocalRoom.SUDOKU_OPENER), parseRoom(r.view(LocalRoom.HOST, t0)).caps)
    }

    @Test
    fun withTheTokenEverywhereTheOpenersLevelIsUsedAndTheOthersTapReady() {
        val r = room(NEW)
        val seen = parseRoom(r.view(LocalRoom.GUEST, t0))
        assertTrue(seen.picksByOpener)
        r.setOptions(LocalRoom.HOST, JSONObject().put("mode", "race").put("shape", "classic").put("level", "hard"))
        assertEquals(Difficulty.HARD, parseRoom(r.view(LocalRoom.GUEST, t0)).sudokuLevel)
        // The guest's Ready carries no level, and a level they tapped would not count either.
        r.setReady(LocalRoom.GUEST, true, null, t0)
        assertEquals("lobby", r.status)
        r.setReady(LocalRoom.HOST, true, Difficulty.HARD, t0)
        assertEquals("started", r.status)
        assertEquals(Difficulty.HARD, parseRoom(r.view(LocalRoom.HOST, r.startAt!!)).difficulty)
    }

    @Test
    fun aGuestsOwnTapIsIgnoredAndARuleCallKeepsTheLevel() {
        val r = room(NEW)
        r.setOptions(LocalRoom.HOST, JSONObject().put("level", "expert"))
        r.setOptions(LocalRoom.HOST, JSONObject().put("mode", "together").put("shape", "classic"))
        assertEquals(Difficulty.EXPERT, r.sudokuLevel)
        r.setReady(LocalRoom.HOST, true, Difficulty.EXPERT, t0)
        r.setReady(LocalRoom.GUEST, true, Difficulty.BEGINNER, t0)
        assertEquals(Difficulty.EXPERT, parseRoom(r.view(LocalRoom.HOST, r.startAt!!)).difficulty)
    }

    @Test
    fun aNewLevelTakesEveryonesReadyBack() {
        val r = room(NEW)
        r.setReady(LocalRoom.GUEST, true, null, t0)
        r.setOptions(LocalRoom.HOST, JSONObject().put("level", "medium"))
        assertFalse(r.player(LocalRoom.GUEST).ready)
    }

    @Test
    fun withOneAppLackingTheTokenTapsMustMatchAsBefore() {
        val r = room(OLD)
        assertFalse(parseRoom(r.view(LocalRoom.HOST, t0)).picksByOpener)
        assertThrows(ApiException::class.java) { r.setReady(LocalRoom.GUEST, true, null, t0) }
        r.setReady(LocalRoom.HOST, true, Difficulty.EASY, t0)
        r.setReady(LocalRoom.GUEST, true, Difficulty.MEDIUM, t0)
        assertEquals("lobby", r.status)
        r.setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
        assertEquals("started", r.status)
        assertEquals(Difficulty.EASY, parseRoom(r.view(LocalRoom.HOST, r.startAt!!)).difficulty)
    }

    @Test
    fun theLevelIsOnlySentWhereTheOpenerPicks() {
        assertTrue(room(NEW).view(LocalRoom.HOST, t0).getJSONObject("options").has("level"))
        assertFalse(room(OLD).view(LocalRoom.HOST, t0).getJSONObject("options").has("level"))
    }

    @Test
    fun theDefaultLevelIsMedium() {
        assertEquals(Difficulty.MEDIUM, parseRoom(room(NEW).view(LocalRoom.HOST, t0)).sudokuLevel)
    }

    @Test
    fun openingAnotherGameAndComingBackKeepsOpenerMode() {
        val r = room(NEW)
        val ttt = r.openGame(LocalRoom.HOST, com.pippop.data.GameKind.TTT)
        val back = ttt.openGame(LocalRoom.HOST, com.pippop.data.GameKind.SUDOKU)
        assertTrue(parseRoom(back.view(LocalRoom.GUEST, t0)).picksByOpener)
        assertTrue(back.player(LocalRoom.GUEST).bigSudoku)
    }

    @Test
    fun aPlayerWithoutTheTokenJoiningClearsReadyAndLeavingBringsOpenerModeBack() {
        val r = LocalRoom("OPN2", "Alice", Avatar.FOX)
        r.join("Bob", Avatar.OWL, null, NEW)
        r.setOptions(LocalRoom.HOST, JSONObject().put("level", "expert"))
        r.setReady(LocalRoom.GUEST, true, null, t0)
        val cat = r.join("Cat", Avatar.CAT, null, OLD)
        assertFalse(r.player(LocalRoom.GUEST).ready)
        val old = parseRoom(r.view(LocalRoom.HOST, t0))
        assertFalse(old.picksByOpener)
        assertEquals(null, old.sudokuLevel)
        r.leave(cat, t0)
        val back = parseRoom(r.view(LocalRoom.HOST, t0))
        assertTrue(back.picksByOpener)
        assertEquals(Difficulty.EXPERT, back.sudokuLevel)
    }

    @Test
    fun aRejoinFromAnAppWithoutTheTokenDropsOpenerMode() {
        val r = LocalRoom("OPN3", "Alice", Avatar.FOX)
        r.join("Bob", Avatar.OWL, null, NEW)
        val id = r.playerId(LocalRoom.GUEST)
        r.join("Bob", Avatar.OWL, id, OLD)
        assertFalse(parseRoom(r.view(LocalRoom.HOST, t0)).picksByOpener)
    }

    @Test
    fun aGuestWhoOpenedTheGameIsTheOpener() {
        val r = room(NEW)
        val ttt = r.openGame(LocalRoom.GUEST, com.pippop.data.GameKind.TTT)
        val sudoku = ttt.openGame(LocalRoom.GUEST, com.pippop.data.GameKind.SUDOKU)
        assertThrows(ApiException::class.java) { sudoku.setOptions(LocalRoom.HOST, JSONObject().put("level", "hard")) }
        sudoku.setOptions(LocalRoom.GUEST, JSONObject().put("level", "hard"))
        sudoku.setReady(LocalRoom.HOST, true, null, t0)
        sudoku.setReady(LocalRoom.GUEST, true, Difficulty.HARD, t0)
        assertEquals("started", sudoku.status)
        assertEquals(Difficulty.HARD, parseRoom(sudoku.view(LocalRoom.HOST, sudoku.startAt!!)).difficulty)
    }
}
