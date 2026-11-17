package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pippop.game.dominoes.DominoView
import com.pippop.game.dominoes.Tile
import com.pippop.ui.screens.DominoHand
import com.pippop.ui.screens.DominoLine
import com.pippop.ui.screens.DominoTable
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Store step 12: the line of dominoes has no number circles at its ends (the end tiles show the open numbers), and while a tile
 * that fits both ends is picked, the open end of each end tile is the target for choosing where it goes.
 */
class ScreenshotDominoEndsTest : ScreenshotBase() {
    // Ends 3 and 5.
    private val line = listOf(Tile(3, 4), Tile(4, 4), Tile(4, 1), Tile(1, 5))

    private fun view(hand: List<Tile>) = DominoView(
        line = line, start = 1, hand = hand, counts = mapOf('m' to hand.size, 'o' to 5), scores = mapOf('m' to 0, 'o' to 0),
        partners = emptyList(), boneyard = 0, teams = false, draw = false, target = 100, roundNo = 1, lastMove = null, lastRound = null,
    )

    @Composable
    private fun picked() = Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        val hand = listOf(Tile(0, 6), Tile(3, 5), Tile(2, 2))
        DominoLine(line, start = 1, highlight = null, modifier = Modifier.size(360.dp, 288.dp), myTurn = true, pickEnds = setOf("left", "right"), onEnd = {})
        DominoHand(hand, line, enabled = true, selected = 1, onTap = {})
    }

    @Test fun endsLight() = shot("domino-ends", false) { picked() }
    @Test fun endsDark() = shot("domino-ends", true) { picked() }

    // ---- The new targets ----

    private val plays = mutableListOf<Pair<Int, String>>()

    private fun table(hand: List<Tile>) {
        plays.clear()
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize()) {
                    DominoTable(view(hand), myTurn = true, enabled = true, waitingText = "", name = { "You" }, onPlay = { i, end -> plays += i to end }, onDraw = {}, onPass = {})
                }
            }
        }
    }

    @Test fun aTileThatFitsBothEndsIsPlayedOnEachThroughItsTarget() {
        table(listOf(Tile(3, 5)))
        compose.onAllNodesWithContentDescription("Play on the 3 end").assertCountEquals(0)
        compose.onAllNodes(hasClickAction()).onFirst().performClick()
        compose.onNodeWithContentDescription("Play on the 3 end").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Play on the 5 end").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(0 to "left", 0 to "right"), plays)
    }

    @Test fun theButtonsUnderTheLineStillPlayOnEachEnd() {
        table(listOf(Tile(3, 5)))
        compose.onAllNodes(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("◀ On the 3").performClick()
        compose.onNodeWithText("On the 5 ▶").performClick()
        assertEquals(listOf(0 to "left", 0 to "right"), plays)
    }

    @Test fun aTileThatFitsOneEndIsPlayedAtOnceAndNoTargetsShow() {
        table(listOf(Tile(3, 0)))
        compose.onAllNodes(hasClickAction()).onFirst().performClick()
        assertEquals(listOf(0 to "left"), plays)
        compose.onAllNodesWithContentDescription("Play on the 3 end").assertCountEquals(0)
    }

    // ---- What a screen reader hears ----

    @Test fun theOpenEndsAreStillDescribed() {
        table(listOf(Tile(3, 5)))
        compose.onNodeWithContentDescription("Open ends: 3 and 5").assertExists()
    }
}
