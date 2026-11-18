package com.pippop.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pippop.ui.screens.Tone
import com.pippop.ui.screens.ladderMoveProblem
import com.pippop.ui.screens.ladderPickCheck
import com.pippop.ui.screens.ladderProblemHelp
import com.pippop.ui.screens.ladderResultDetail
import com.pippop.ui.screens.ladderRungs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Word Ladder's screens played against a nearby room's own rules (LocalRoom): pick, the coin flip, the ladder,
 * a refused move and a win, all by tapping what a player taps. Sam is this phone; Maya is moved from the test.
 */
class LadderScreenTest : LadderTestBase() {
    @Before fun words() = LadderTable.installWords()

    private fun shown(text: String) = compose.onNode(hasText(text, substring = true)).assertIsDisplayed()

    @Test fun aShortGameThroughTheScreens() {
        // Maya's word (CARD) is the start; Sam owns the target (WARM) and moves first.
        val h = LadderHarness(LadderTable())
        show(h)

        // The pick: typing WARM shows it is usable; Lock in sends it; then Sam waits for Maya.
        shown("Pick your secret word")
        compose.onNodeWithText("Lock in").assertIsNotEnabled()
        type("WARM")
        shown("WARM is in the word list")
        compose.onNodeWithText("Lock in WARM").assertIsEnabled().performClick()
        assertEquals(listOf("WARM"), h.picks)
        shown("Locked in. Waiting for Maya")

        // Maya picks CARD: the reveal. The coin flip says whose word starts and who moves first.
        refreshOnIdle(h) { h.table.mayaPicks("CARD") }
        shown("Maya's word starts")
        shown("CARD → your word: WARM")
        shown("You move first.")
        compose.onNodeWithText("Start climbing").performClick()

        // Sam's first move: CARD to CART.
        shown("Your turn")
        change(3, 'D', 'T')
        shown("CARD → CART")
        compose.onNodeWithText("Play").assertIsEnabled().performClick()
        assertEquals(listOf("CART"), h.moves)
        shown("Maya is thinking")

        // Maya answers WART (one step from the target); Sam spells WARM and wins.
        refreshOnIdle(h) { h.table.mayaPlays("WART") }
        shown("Your turn")
        change(3, 'T', 'M')
        compose.onNodeWithText("Play").performClick()
        assertEquals(listOf("CART", "WARM"), h.moves)
        shown("You win!")
        shown("Your move spelled WARM.")
        assertEquals(listOf("CARD", "CART", "WART", "WARM"), h.state.value.room!!.turns!!.ladder!!.chain)
    }

    @Test fun aRefusedMoveSaysWhyInPlace() {
        val h = LadderHarness(LadderTable())
        h.table.samPicks("WARM"); h.table.mayaPicks("COLD")
        h.table.samPlays("CORD"); h.table.mayaPlays("CARD")
        h.refresh()
        show(h)
        // Not a real word: nothing is sent, the reason is on the screen.
        change(0, 'C', 'X')
        compose.onNodeWithText("Play").performClick()
        shown("Not a word")
        shown("XARD isn't in the word list")
        assertTrue(h.moves.isEmpty())
        // A word already on the ladder.
        change(1, 'A', 'O')
        compose.onNodeWithText("Play").performClick()
        shown("Already used")
        assertTrue(h.moves.isEmpty())
        // The same letter back is no change at all.
        change(0, 'C', 'C')
        shown("✕  Change one letter")
        compose.onNodeWithText("Play").assertIsNotEnabled()
        // A good word goes through.
        change(0, 'C', 'W')
        compose.onNodeWithText("Play").performClick()
        assertEquals(listOf("WARD"), h.moves)
    }

    @Test fun theServersRefusalOfAPickIsShownInPlaceToo() {
        val h = LadderHarness(LadderTable())
        h.table.mayaPicks("CARD")
        h.refresh()
        show(h)
        shown("Maya has picked")
        type("CARD")
        compose.onNodeWithText("Lock in CARD").performClick()
        shown("Pick another word.")
        // The toast is taken: the message is cleared.
        assertNull(h.state.value.message)
        assertEquals(listOf("CARD"), h.picks)
    }

    @Test fun aWordNotInTheListCannotBeLocked() {
        val h = LadderHarness(LadderTable())
        show(h)
        type("ZZZZ")
        shown("Not a word we can use. Pick another.")
        compose.onNodeWithText("Lock in ZZZZ").assertIsNotEnabled()
    }

    @Test fun yourPickCanChangeUntilTheReveal() {
        val h = LadderHarness(LadderTable())
        show(h)
        type("WARM")
        compose.onNodeWithText("Lock in WARM").performClick()
        // The locked word stays on the screen; typing changes only what would be sent next.
        compose.onNodeWithText("⌫").performClick()
        type("D")
        shown("Change to WARD")
        compose.onNodeWithText("Change to WARD").performClick()
        assertEquals(listOf("WARM", "WARD"), h.picks)
        assertEquals("WARD", h.state.value.room!!.turns!!.ladder!!.myPick)
    }

    // ---- The rules the screens quote ---------------------------------------------------------------

    @Test fun theMovesReasonsAreTheRulesOwnWords() {
        val chain = listOf("COLD", "CORD", "CARD")
        assertNull(ladderMoveProblem(chain, "CART"))
        assertEquals("Change one letter", ladderMoveProblem(chain, "CARD"))
        assertEquals("Change one letter", ladderMoveProblem(chain, "WART"))
        assertEquals("Change one letter", ladderMoveProblem(chain, "CARDS"))
        assertEquals("Not a word", ladderMoveProblem(chain, "XARD"))
        assertEquals("Already used", ladderMoveProblem(chain, "CORD"))
        assertEquals("Already used", ladderMoveProblem(listOf("CORD", "COLD", "CARD"), "cord"))
        assertNull(ladderProblemHelp("Something else", "CARD"))
        assertEquals("XARD isn't in the word list. Pick another letter.", ladderProblemHelp("Not a word", "XARD"))
    }

    @Test fun thePickCheckSaysWhatIsMissingOrWhy() {
        assertEquals("Type a 4-letter word", ladderPickCheck("", 4).text)
        assertEquals("1 more letter", ladderPickCheck("war", 4).text)
        assertEquals("2 more letters", ladderPickCheck("wa", 4).text)
        assertFalse(ladderPickCheck("wa", 4).canLock)
        val good = ladderPickCheck("warm", 4)
        assertEquals(Tone.Good, good.tone)
        assertTrue(good.canLock)
        val bad = ladderPickCheck("zzzz", 4)
        assertEquals(Tone.Bad, bad.tone)
        assertEquals("Not a word we can use. Pick another.", bad.text)
        assertFalse(bad.canLock)
    }

    @Test fun rungsNameWhoMadeEachStepOnlyWhenTurnsAlternate() {
        val chain = listOf("COLD", "CORD", "CARD", "CART")
        // No timer, Sam owns the target and moves first: Sam, Maya, Sam.
        assertEquals(listOf("Start", "You", "Maya", "You"), ladderRungs(chain, true, 0, "Maya").map { it.label })
        assertEquals(listOf("Start", "Maya", "You", "Maya"), ladderRungs(chain, false, 0, "Maya").map { it.label })
        // With a timer a turn may have passed, so the steps are only numbered.
        assertEquals(listOf("Start", "Step 1", "Step 2", "Step 3"), ladderRungs(chain, true, 30, "Maya").map { it.label })
        // The letter that changed in each step.
        assertEquals(listOf(null, 2, 1, 3), ladderRungs(chain, true, 0, "Maya").map { it.changed })
    }

    @Test fun eachWayTheGameEndsHasItsOwnMessage() {
        assertEquals("Your move spelled WARM.", ladderResultDetail("me", "target", "Maya", "WARM"))
        assertEquals("Maya's move spelled WARM.", ladderResultDetail("opponent", "target", "Maya", "WARM"))
        assertEquals("Maya had no move left.", ladderResultDetail("me", "stuck", "Maya", "WARM"))
        assertEquals("You had no move left.", ladderResultDetail("opponent", "stuck", "Maya", "WARM"))
        assertEquals("Maya left the game.", ladderResultDetail("me", "forfeit", "Maya", null))
        assertEquals("", ladderResultDetail("me", null, "Maya", null))
    }
}
