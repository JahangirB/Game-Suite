package com.pippop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.pippop.data.GameKind
import com.pippop.data.Profile
import com.pippop.data.ProfileStore
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** Game Store step 5 on the home: the Store card, the empty-shelf button and the one-time note. */
class HomeStoreTest : ScreenshotBase() {
    private var store = 0
    private var seen = 0
    private var played by mutableStateOf<GameKind?>(null)

    @Composable
    private fun Home(profile: Profile) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                solo = SoloUiState(), profile = profile, onContinue = {}, onProfile = {}, onSolo = { _, _ -> }, onScores = {},
                partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {}, connected = false, onConnect = {},
                onOpenStore = { store++ }, onShelfNoteSeen = { seen++ },
                playRequest = played, onPlayRequestHandled = { played = null },
                now = 0L,
            )
        }
    }

    private fun show(profile: Profile) = compose.setContent { PipPopTheme(darkTheme = false) { Home(profile) } }

    private val seenAll = GameKind.entries.toSet()

    @Test fun theCardCountsTheStoreGamesAndOpensTheStore() {
        show(Profile(name = "Sam", tutorialsSeen = seenAll))
        compose.onNodeWithText("Game Store · 12 more games").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, store)
        // The old row is gone.
        compose.onNodeWithText("More games", substring = true).assertDoesNotExist()
    }

    @Test fun anEmptyStoreStillOpensTheStore() {
        show(Profile(name = "Sam", shelf = GameKind.entries, tutorialsSeen = seenAll))
        compose.onNodeWithText("Game Store · every game is on your shelf").performScrollTo().performClick()
        assertEquals(1, store)
    }

    @Test fun theCardRestsWhileTheShelfIsEdited() {
        show(Profile(name = "Sam", tutorialsSeen = seenAll))
        compose.onNodeWithText("Game Store · 12 more games").performScrollTo().assertIsEnabled()
        compose.onNodeWithText(GameKind.LUDO.label).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Game Store · 12 more games").assertIsNotEnabled()
    }

    @Test fun theEmptyShelfHasAButtonToTheStore() {
        show(Profile(name = "Sam", shelf = emptyList(), tutorialsSeen = seenAll))
        compose.onNodeWithText("Open the Game Store").performScrollTo().performClick()
        assertEquals(1, store)
    }

    @Test fun anEmptyShelfHasOnlyTheButtonNotTheCard() {
        show(Profile(name = "Sam", shelf = emptyList(), tutorialsSeen = seenAll))
        compose.onNodeWithText("Open the Game Store").assertExists()
        compose.onNodeWithText("Game Store · 20 more games").assertDoesNotExist()
    }

    @Test fun theNoteShowsOnlyWhenDueAndNotYetSeen() {
        show(Profile(name = "Sam", shelfNoteDue = false))
        compose.onNodeWithText("The other games are in the Game Store").assertDoesNotExist()
    }

    @Test fun aSeenNoteStaysGone() {
        show(Profile(name = "Sam", shelfNoteDue = true, shelfNoteShown = true))
        compose.onNodeWithText("The other games are in the Game Store").assertDoesNotExist()
    }

    @Test fun theNoteCanBeDismissed() {
        show(Profile(name = "Sam", shelfNoteDue = true))
        compose.onNodeWithText("The other games are in the Game Store").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(1, seen)
        assertEquals(0, store)
    }

    @Test fun theNotesButtonOpensTheStoreAndMarksItSeen() {
        show(Profile(name = "Sam", shelfNoteDue = true))
        compose.onNodeWithText("Open the Game Store").performClick()
        assertEquals(1, store)
        assertEquals(1, seen)
    }

    @Test fun aPlayRequestOpensTheGamesStartSheet() {
        show(Profile(name = "Sam", tutorialsSeen = seenAll))
        compose.runOnIdle { played = GameKind.LUDO }
        compose.waitForIdle()
        compose.onNodeWithText("How to play").assertExists()
        assertEquals(null, played)
    }
}
