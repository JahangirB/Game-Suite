package com.pippop.ui

import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.tonightScore
import com.pippop.ui.screens.turnWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnHintsTest {
    @Test
    fun hintShowsOnTheFirstTwoTurnsOnly() {
        val h = TurnHints()
        val shown = (1..4).map { i ->
            h.note(false)
            TurnHints.showsHint(h.note(true)).also { assertTrue(i > 0) }
        }
        assertEquals(listOf(true, true, false, false), shown)
    }

    @Test
    fun aSecondGoInARowIsTheSameTurn() {
        val h = TurnHints()
        assertEquals(1, h.note(true))
        assertEquals(1, h.note(true))
        h.note(false)
        assertEquals(2, h.note(true))
    }

    @Test
    fun recomposingDoesNotCount() {
        val h = TurnHints()
        repeat(5) { h.note(false) }
        assertEquals(1, h.note(true))
        repeat(5) { assertEquals(1, h.note(true)) }
    }

    @Test
    fun notYourTurnIsZeroAndShowsNothing() {
        assertEquals(0, TurnHints().note(false))
        assertFalse(TurnHints.showsHint(0))
    }

    @Test
    fun aNewGameStartsAgain() {
        val old = TurnHints(turns = 5)
        assertFalse(TurnHints.showsHint(old.note(true)))
        assertTrue(TurnHints.showsHint(TurnHints().note(true)))
    }

    private fun player(name: String, left: Boolean = false) = PlayerInfo(
        name = name, avatar = Avatar.PANDA, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = left, idleMs = 0, online = true, at = GameKind.CONNECT4,
    )

    private fun room(vararg others: PlayerInfo) = RoomState(
        code = "K7Q2NB", role = "host", game = GameKind.CONNECT4, status = RoomStatus.PLAYING, difficulty = null, round = 1, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam"),
        opponent = others.firstOrNull(), ships = null, turns = null, serverTime = 0, others = others.toList(),
    )

    @Test
    fun tonightIsForExactlyOneOtherPlayer() {
        val tally = mapOf(TALLY_ME to 3, "Maya" to 2)
        val s = tonightScore(room(player("Maya")), tally)!!
        assertEquals(3, s.mine)
        assertEquals(2, s.theirs)
        assertEquals("Tonight: you 3, Maya 2", s.description)
        assertNotNull(tonightScore(room(player("Maya")), emptyMap()))
        assertNull(tonightScore(room(player("Maya"), player("Leo")), tally))
        assertNull(tonightScore(room(player("Maya"), player("Leo"), player("Ann")), tally))
        assertNull(tonightScore(room(player("Maya", left = true)), tally))
        assertNull(tonightScore(room(), tally))
    }

    /** Runs a game's real banner lines (text, whose turn) through [turnWords]; returns each hint. */
    private fun hints(vararg lines: Pair<String, Boolean>): List<String?> {
        val h = TurnHints()
        return lines.map { (text, mine) -> turnWords(text, mine, h).second }
    }

    @Test
    fun memorysGoAgainIsNotANewTurn() {
        val flip = "Your turn: flip two cards"
        val got = hints(
            flip to true, "A pair! Go again" to true, "A pair! Go again" to true, // turn 1, matched twice
            "Maya is thinking…" to false,
            flip to true, "A pair! Go again" to true,                             // turn 2
            "Maya is thinking…" to false,
            flip to true,                                                         // turn 3
        )
        assertEquals(listOf("Flip two cards", null, null, null, "Flip two cards", null, null, null), got)
    }

    @Test
    fun ludosBonusRollIsNotANewTurn() {
        val roll = "Your turn: roll the die"
        val got = hints(
            roll to true, "You rolled 6: tap a token to move" to true, roll to true, // a six: roll again, same turn
            "Maya is playing…" to false,
            roll to true, "Maya is playing…" to false,
            roll to true,                                                           // turn 3
        )
        assertEquals(listOf("Roll the die", null, "Roll the die", null, "Roll the die", null, null), got)
    }

    @Test
    fun onlyYourTurnWithAColonSplits() {
        val h = TurnHints()
        assertEquals("Your turn" to null, turnWords("Your turn", true, h))
        assertEquals("Your turn to pick a word" to null, turnWords("Your turn to pick a word", true, h))
        assertEquals("Maya is thinking…" to null, turnWords("Maya is thinking…", false, h))
    }
}
