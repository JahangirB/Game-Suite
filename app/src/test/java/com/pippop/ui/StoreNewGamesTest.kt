package com.pippop.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import com.pippop.ui.art.about
import com.pippop.ui.art.levelsFact
import com.pippop.ui.art.rules
import com.pippop.ui.screens.StoreFilter
import com.pippop.ui.screens.storeFacts
import com.pippop.ui.screens.storeList
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Words step 11: Word Ladder and Word Hunt in the Game Store, marked "New" until their page is opened or they are got. */
class StoreNewGamesTest : ScreenshotBase() {
    private val nine = ProfileStore.DEFAULT_SHELF

    private fun host(shelf: List<GameKind> = nine, page: GameKind? = null): StoreHost {
        val h = StoreHost(shelf, StoreFilter.ALL, page)
        compose.setContent { PipPopTheme(darkTheme = false) { h.Content() } }
        return h
    }

    private fun newMarks() = compose.onAllNodesWithText("New").fetchSemanticsNodes().size

    @Test fun bothGamesAreInTheStoreWithNewAndInTheRightChoices() {
        assertTrue(GameKind.LADDER in storeList(nine, StoreFilter.ALL) && GameKind.HUNT in storeList(nine, StoreFilter.ALL))
        assertTrue(GameKind.LADDER in storeList(nine, StoreFilter.WORDS) && GameKind.HUNT in storeList(nine, StoreFilter.WORDS))
        // Ladder is a quick game, Hunt (10 minutes) is not.
        assertTrue(GameKind.LADDER in storeList(nine, StoreFilter.QUICK) && GameKind.HUNT !in storeList(nine, StoreFilter.QUICK))
        // Hunt is for up to four, so it is a party game too.
        assertTrue(GameKind.HUNT in storeList(nine, StoreFilter.PARTY))
        host()
        compose.onNodeWithContentDescription("Get Word Ladder").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Get Word Hunt").performScrollTo().assertIsDisplayed()
        assertEquals("both rows say New", 2, newMarks())
    }

    private fun getAndCheck(g: GameKind) {
        val h = host()
        compose.onNodeWithContentDescription("Get ${g.label}").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(nine + g, h.shelf)
        compose.onNodeWithContentDescription("Open ${g.label}").assertExists()
        assertEquals("only the other game still says New", 1, newMarks())
    }

    @Test fun getPutsWordLadderOnTheShelfAndItsNewGoes() = getAndCheck(GameKind.LADDER)

    @Test fun getPutsWordHuntOnTheShelfAndItsNewGoes() = getAndCheck(GameKind.HUNT)

    @Test fun theNewGoesOnceThePageHasBeenSeenButThePageShowsItAsItOpened() {
        val h = host()
        compose.onNodeWithText("Word Hunt").performScrollTo().performClick()
        compose.waitForIdle()
        // The page: description, facts, how to play, and New as it was when the page opened.
        assertEquals(1, newMarks())
        compose.onNodeWithText(GameKind.HUNT.about!!).assertExists()
        compose.onNodeWithText("2–4 players", substring = true).assertExists()
        compose.onNodeWithText("4 sizes, hundreds of hidden words").assertExists()
        compose.onNodeWithText("How to play").assertExists()
        assertTrue(GameKind.HUNT in h.seen)
        h.page = null
        compose.waitForIdle()
        // Back in the list Hunt no longer says New; Ladder still does.
        assertEquals(1, newMarks())
        assertTrue(GameKind.LADDER !in h.seen)
    }

    @Test fun eachGameHasADescriptionFactsAndRules() {
        for (g in listOf(GameKind.LADDER, GameKind.HUNT)) {
            assertTrue(g.about!!.isNotBlank())
            assertTrue(g.levelsFact!!.isNotBlank())
            assertEquals("Goal", g.rules.first().title)
            assertTrue(storeFacts(g).contains("players") && storeFacts(g).contains("min"))
        }
        assertEquals("2 players · 5 min", storeFacts(GameKind.LADDER))
        assertEquals("2–4 players · 10 min", storeFacts(GameKind.HUNT))
    }
}
