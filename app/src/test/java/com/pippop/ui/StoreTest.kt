package com.pippop.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import com.pippop.ui.screens.StoreFilter
import com.pippop.ui.theme.PipPopTheme
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Game Store step 5: Get moves a row to "On your shelf", the Choices filter, a game's page (Add, then Play and Remove), the empty Store. */
class StoreTest : ScreenshotBase() {
    private val nine = ProfileStore.DEFAULT_SHELF

    private fun host(shelf: List<GameKind> = nine, page: GameKind? = null, filter: StoreFilter = StoreFilter.ALL, seats: Int? = null, current: GameKind? = null): StoreHost {
        val h = StoreHost(shelf, filter, page, roomSeats = seats, roomGame = current)
        compose.setContent { PipPopTheme(darkTheme = false) { h.Content() } }
        return h
    }

    @Test fun getPutsTheGameOnTheShelfAtOnce() {
        val h = host()
        compose.onNodeWithContentDescription("Get Bingo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Bingo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Get Bingo").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(nine + GameKind.BINGO, h.shelf)
        // The row moved: no Get any more, an Open in "On your shelf".
        compose.onNodeWithContentDescription("Get Bingo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open Bingo").assertExists()
        compose.onNodeWithText("Bingo is on your shelf").assertExists()
    }

    @Test fun theButtonsAreAtLeast48dpAndSayWhatTheyDo() {
        host()
        compose.onNodeWithContentDescription("Get Connect Four").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Open Ludo").assertHeightIsAtLeast(48.dp)
    }

    @Test fun theChoicesReallyFilter() {
        host()
        compose.onNodeWithContentDescription("Get Connect Four").assertExists()
        compose.onNodeWithText("Words").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Get Letter Grid").assertExists()
        compose.onNodeWithContentDescription("Get Connect Four").assertDoesNotExist()
        compose.onNodeWithText("All").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Get Connect Four").assertExists()
    }

    @Test fun anEmptyStoreSaysSo() {
        host(shelf = GameKind.entries)
        compose.onNodeWithText("Every game is on your shelf.").assertIsDisplayed()
    }

    @Test fun gettingTheLastGameEmptiesTheStore() {
        val h = host(shelf = GameKind.entries - GameKind.BINGO)
        compose.onNodeWithContentDescription("Get Bingo").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Every game is on your shelf.").assertIsDisplayed()
        assertTrue(GameKind.BINGO in h.shelf)
    }

    @Test fun tappingARowOpensTheGamesPage() {
        host()
        compose.onNodeWithText("Bingo").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("How to play").assertExists()
        compose.onNodeWithText("Add to my games").assertExists()
    }

    @Test fun openOnTheShelfStartsTheGame() {
        val h = host()
        compose.onNodeWithContentDescription("Open Ludo").performScrollTo().performClick()
        assertEquals(listOf(GameKind.LUDO), h.played)
    }

    @Test fun thePageAddsThenOffersPlayAndRemove() {
        val h = host(page = GameKind.BINGO)
        compose.onNodeWithText("Plays nearby and online").assertExists()
        compose.onNodeWithText("See all rules").assertExists()
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("Add to my games").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(nine + GameKind.BINGO, h.shelf)
        compose.onNodeWithText("Bingo is on your shelf").assertExists()
        compose.onNodeWithText("Add to my games").assertDoesNotExist()
        compose.onNodeWithText("Play").performScrollTo().performClick()
        assertEquals(listOf(GameKind.BINGO), h.played)
        compose.onNodeWithText("Remove from my games").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(nine, h.shelf)
        compose.onNodeWithText("Add to my games").assertExists()
    }

    @Test fun thePageSaysNothingAboutAPartnersShelf() {
        host(page = GameKind.BINGO)
        compose.onNodeWithText("has this game", substring = true).assertDoesNotExist()
    }

    @Test fun seeAllRulesOpensTheRulesSheet() {
        host(page = GameKind.BINGO)
        compose.onNodeWithText("See all rules").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("How to play Bingo").assertExists()
    }

    @Test fun inARoomAGameThatDoesNotFitOrIsOpenCannotBeOpened() {
        // Three in the room: Ship Battle (2) and Tic-Tac-Toe (2) don't fit; Ludo is the game open now.
        val h = host(seats = 3, current = GameKind.LUDO)
        compose.onNodeWithContentDescription("Open Ship Battle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open Ludo").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("Too few seats").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Playing now").assertExists()
        compose.onNodeWithContentDescription("Open Memory Pairs").performScrollTo().performClick()
        assertEquals(listOf(GameKind.MEMORY), h.played)
    }

    @Test fun inARoomThePageHidesPlayForAGameThatDoesNotFit() {
        host(shelf = nine + GameKind.CONNECT4, page = GameKind.CONNECT4, seats = 3)
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("Connect Four is for 2.").assertExists()
    }
}
