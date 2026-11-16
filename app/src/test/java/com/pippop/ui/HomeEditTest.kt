package com.pippop.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.pippop.data.GameKind
import com.pippop.ui.screens.EMPTY_SHELF
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Game Store step 4: holding a game card edits the shelf in place (no other screen), × removes with an Undo that
 * puts the game back where it was, a card dragged to a new place saves the order, a screen reader has move and
 * remove actions, tapping a card opens nothing, and an empty shelf says so.
 */
class HomeEditTest : ScreenshotBase() {
    private val nine = listOf(
        GameKind.SUDOKU, GameKind.SHIPS, GameKind.MEMORY, GameKind.WORDS, GameKind.TTT,
        GameKind.DOMINOES, GameKind.CLASH, GameKind.LUDO, GameKind.GOLF,
    )
    private val bar = "Drag to move. Tap × to remove."

    private fun host(shelf: List<GameKind> = nine): HomeEditHost {
        val h = HomeEditHost(shelf)
        compose.setContent { PipPopTheme(darkTheme = false) { h.Content() } }
        return h
    }

    private fun edit(game: GameKind) {
        compose.onNodeWithText(game.label).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    private fun card(index: Int, n: Int = nine.size, shelf: List<GameKind> = nine) =
        compose.onNodeWithContentDescription("${shelf[index].label}, ${index + 1} of $n")

    private fun actionOf(node: SemanticsNodeInteraction, label: String) =
        node.fetchSemanticsNode().config[SemanticsActions.CustomActions].firstOrNull { it.label == label }

    @Test fun holdingACardEntersEditModeAndOpensNothing() {
        val h = host()
        compose.onNodeWithText(bar).assertDoesNotExistCompat()
        edit(GameKind.LUDO)
        compose.onNodeWithText(bar).assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        // Every card has its ×.
        nine.forEach { compose.onNodeWithContentDescription("Remove ${it.label}").assertExists() }
        // No sheet, no game, no profile, no scores.
        compose.onNodeWithText("How to play").assertDoesNotExistCompat()
        assertTrue(h.opened.isEmpty())
        assertEquals(0, h.profileOpened + h.scoresOpened)
    }

    @Test fun aTapOnACardWithoutEditingStillOpensItsSheet() {
        host()
        compose.onNodeWithText(GameKind.LUDO.label).performClick()
        compose.onNodeWithText("How to play").assertExists()
    }

    @Test fun tappingACardInEditModeDoesNotOpenTheGame() {
        val h = host()
        edit(GameKind.LUDO)
        card(7).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("How to play").assertDoesNotExistCompat()
        assertTrue(h.opened.isEmpty())
        // And it is still editing.
        compose.onNodeWithText(bar).assertIsDisplayed()
    }

    @Test fun doneLeavesEditMode() {
        host()
        edit(GameKind.LUDO)
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(bar).assertDoesNotExistCompat()
        compose.onNodeWithContentDescription("Remove Ludo").assertDoesNotExistCompat()
    }

    @Test fun tappingEmptySpaceLeavesEditMode() {
        host(nine.take(3))
        edit(GameKind.SUDOKU)
        compose.onNodeWithText(bar).assertIsDisplayed()
        // Under the one row of cards there is nothing: tap there.
        compose.onRoot().performTouchInput { click(Offset(width / 2f, height - 40f)) }
        compose.waitForIdle()
        compose.onNodeWithText(bar).assertDoesNotExistCompat()
    }

    @Test fun removingAGameTakesItOffTheShelfAndUndoPutsItBackInTheSamePlace() {
        val h = host()
        edit(GameKind.LUDO)
        compose.onNodeWithContentDescription("Remove Memory Pairs").performClick()
        compose.waitForIdle()
        assertEquals(nine - GameKind.MEMORY, h.shelf)
        assertEquals("Memory Pairs is back in the Store.", h.toast?.first)
        // Still editing, and the others are where they were.
        compose.onNodeWithText(bar).assertIsDisplayed()
        compose.onNodeWithText("Memory Pairs is back in the Store.").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(nine, h.shelf)
        assertNull(h.toast)
    }

    @Test fun undoAfterRemovingTheLastGameOnTheShelfPutsItBackAtTheEnd() {
        val h = host()
        edit(GameKind.LUDO)
        compose.onNodeWithContentDescription("Remove ${GameKind.GOLF.label}").performClick()
        compose.waitForIdle()
        compose.runOnIdle { h.lastUndo!!() }
        compose.waitForIdle()
        assertEquals(nine, h.shelf)
    }

    // Moves arrive one frame (16 ms) at a time, as a finger sends them, with the clock stepped by hand.
    private fun frameHost(shelf: List<GameKind> = nine): HomeEditHost {
        val h = HomeEditHost(shelf)
        compose.mainClock.autoAdvance = false
        compose.setContent { PipPopTheme(darkTheme = false) { h.Content() } }
        compose.mainClock.advanceTimeBy(500)
        return h
    }

    private fun glide(from: Offset, to: Offset, frames: Int = 20, hold: Boolean = true) {
        compose.onRoot().performTouchInput { down(from) }
        // Held still until the long press has happened.
        compose.mainClock.advanceTimeBy(700)
        for (i in 1..frames) {
            compose.onRoot().performTouchInput { moveTo(from + (to - from) * (i / frames.toFloat())) }
            compose.mainClock.advanceTimeBy(16)
        }
        if (hold) {
            compose.onRoot().performTouchInput { up() }
            compose.mainClock.advanceTimeBy(600)
        }
    }

    private fun center(index: Int, shelf: List<GameKind>) =
        card(index, shelf.size, shelf).fetchSemanticsNode().boundsInRoot.center

    private fun enterEditByHolding(game: GameKind, shelf: List<GameKind> = nine) {
        val at = shelf.indexOf(game)
        val node = compose.onNodeWithText(game.label).fetchSemanticsNode().boundsInRoot
        val other = shelf[(at + 1) % shelf.size]
        val otherTop = compose.onNodeWithText(other.label).fetchSemanticsNode().boundsInRoot.top
        compose.onRoot().performTouchInput { down(node.center) }
        compose.mainClock.advanceTimeBy(700)
        // Nothing shifts when editing starts: a card that is not held is exactly where it was, and the held card only
        // grows a little about its centre (it is lifted), so its top has moved by no more than half of that growth.
        val held = card(at, shelf.size, shelf).fetchSemanticsNode().boundsInRoot
        assertEquals(node.top, held.top, node.height * 0.04f + 2f)
        assertEquals(otherTop, card((at + 1) % shelf.size, shelf.size, shelf).fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        // A card's bounds are read fresh only on a second look after its layer changed (the tree is refreshed by the first).
        card(at, shelf.size, shelf).fetchSemanticsNode()
        compose.mainClock.advanceTimeBy(500)
        // Let go, and the held card is back on its place to the pixel.
        assertEquals(node.top, card(at, shelf.size, shelf).fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    private fun dragInEditMode(from: Int, to: Int) {
        val h = frameHost()
        enterEditByHolding(GameKind.LUDO)
        compose.onNodeWithText(bar).assertIsDisplayed()
        glide(center(from, nine), center(to, nine))
        val expected = nine.toMutableList().apply { add(to, removeAt(from)) }
        assertEquals("drag $from to $to", listOf(from to to), h.moves)
        assertEquals(expected, h.shelf)
        assertTrue(h.opened.isEmpty())
        compose.onNodeWithText(bar).assertIsDisplayed()
    }

    @Test fun drag0to1() = dragInEditMode(0, 1)
    @Test fun drag0to2() = dragInEditMode(0, 2)
    @Test fun drag0to3() = dragInEditMode(0, 3)
    @Test fun drag0to4() = dragInEditMode(0, 4)
    @Test fun drag0toLast() = dragInEditMode(0, 8)
    @Test fun drag1to0() = dragInEditMode(1, 0)
    @Test fun drag3to0() = dragInEditMode(3, 0)
    @Test fun drag4to0() = dragInEditMode(4, 0)
    @Test fun drag5to8() = dragInEditMode(5, 8)
    @Test fun drag6to8() = dragInEditMode(6, 8)
    @Test fun drag7to5() = dragInEditMode(7, 5)
    @Test fun drag8to6() = dragInEditMode(8, 6)
    @Test fun dragLastTo0() = dragInEditMode(8, 0)

    @Test fun holdingThenDraggingInOneGoFromTheNormalHomeSavesTheMove() {
        val h = frameHost()
        val from = compose.onNodeWithText(GameKind.SUDOKU.label).fetchSemanticsNode().boundsInRoot.center
        val to = compose.onNodeWithText(GameKind.DOMINOES.label).fetchSemanticsNode().boundsInRoot.center
        glide(from, to)
        assertEquals(listOf(0 to 5), h.moves)
        assertEquals(listOf(GameKind.SHIPS, GameKind.MEMORY, GameKind.WORDS, GameKind.TTT, GameKind.DOMINOES, GameKind.SUDOKU), h.shelf.take(6))
        assertTrue(h.opened.isEmpty())
        compose.onNodeWithText("How to play").assertDoesNotExistCompat()
        // Editing carried on after the drop.
        compose.onNodeWithText(bar).assertIsDisplayed()
    }

    @Test fun dropOnTheEmptyPlaceAtTheEndOfAPartFilledLastRowGoesLast() {
        val shelf = nine.take(7)
        val h = frameHost(shelf)
        enterEditByHolding(GameKind.SUDOKU, shelf)
        glide(center(0, shelf), center(6, shelf) + Offset(300f, 0f))
        assertEquals(listOf(0 to 6), h.moves)
    }

    @Test fun aCancelledTouchSnapsTheCardBackAndSavesNothing() {
        val h = frameHost()
        enterEditByHolding(GameKind.LUDO)
        val from = center(0, nine)
        val to = center(4, nine)
        compose.onRoot().performTouchInput { down(from) }
        compose.mainClock.advanceTimeBy(700)
        for (i in 1..10) {
            compose.onRoot().performTouchInput { moveTo(from + (to - from) * (i / 10f)) }
            compose.mainClock.advanceTimeBy(16)
        }
        compose.onRoot().performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(600)
        assertTrue(h.moves.isEmpty())
        assertEquals(nine, h.shelf)
        // Sudoku is back on the first place (the semantics tree needs a second look to catch up with the layers).
        compose.onRoot().fetchSemanticsNode()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Sudoku, 1 of 9").assertExists()
        compose.onNodeWithText(bar).assertIsDisplayed()
    }

    @Test fun aQuickSwipeIsNotTakenAsAHold() {
        val h = frameHost()
        val from = compose.onNodeWithText(GameKind.SUDOKU.label).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(from) }
        for (i in 1..5) {
            compose.onRoot().performTouchInput { moveTo(from + Offset(0f, -20f * i)) }
            compose.mainClock.advanceTimeBy(16)
        }
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(bar).assertDoesNotExistCompat()
        assertTrue(h.moves.isEmpty())
    }

    @Test fun droppingACardOnItsOwnPlaceSavesNothing() {
        val h = host()
        edit(GameKind.SUDOKU)
        val from = card(0).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(from + Offset(6f, 6f))
            up()
        }
        compose.waitForIdle()
        assertTrue(h.moves.isEmpty())
        assertEquals(nine, h.shelf)
    }

    @Test fun aScreenReaderCanMoveAndRemoveCards() {
        val h = host()
        edit(GameKind.LUDO)
        // The first card cannot move earlier, the last cannot move later.
        assertNull(actionOf(card(0), "Move earlier"))
        assertNull(actionOf(card(8), "Move later"))
        compose.runOnIdle { actionOf(card(0), "Move later")!!.action() }
        compose.waitForIdle()
        assertEquals(listOf(0 to 1), h.moves)
        assertEquals(listOf(GameKind.SHIPS, GameKind.SUDOKU), h.shelf.take(2))
        // Sudoku is now second: move it earlier again.
        compose.runOnIdle { actionOf(compose.onNodeWithContentDescription("Sudoku, 2 of 9"), "Move earlier")!!.action() }
        compose.waitForIdle()
        assertEquals(nine, h.shelf)
        compose.runOnIdle { actionOf(card(2), "Remove")!!.action() }
        compose.waitForIdle()
        assertEquals(nine - GameKind.MEMORY, h.shelf)
        assertEquals("Memory Pairs is back in the Store.", h.toast?.first)
    }

    @Test fun anEmptyShelfSaysSoAndSurpriseUsSaysTheSame() {
        val h = host(emptyList())
        compose.onNodeWithText(EMPTY_SHELF).assertIsDisplayed()
        assertEquals("Your shelf is empty. Get games from the Game Store.", EMPTY_SHELF)
        compose.onNodeWithContentDescription("Surprise us").performClick()
        compose.waitForIdle()
        assertEquals(listOf(EMPTY_SHELF), h.messages)
    }

    @Test fun surpriseUsWithGamesStillOnTheShelfDoesNotSayItIsEmpty() {
        val h = host()
        compose.onNodeWithText(EMPTY_SHELF).assertDoesNotExistCompat()
        assertTrue(h.messages.isEmpty())
    }

    @Test fun removingTheLastGameLeavesEditModeAndShowsTheEmptyShelf() {
        val h = host(listOf(GameKind.SUDOKU))
        edit(GameKind.SUDOKU)
        compose.onNodeWithContentDescription("Remove Sudoku").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(EMPTY_SHELF).assertIsDisplayed()
        compose.onNodeWithText(bar).assertDoesNotExistCompat()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(listOf(GameKind.SUDOKU), h.shelf)
    }

    private fun SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
}
