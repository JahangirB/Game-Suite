package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.Pt
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.GameMenuRows
import com.pippop.ui.screens.GolfScorecard
import com.pippop.ui.screens.GolfScorecardSheet
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.screens.golfTotals
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mini Golf step 11: no score strip; the chips carry totals, the game menu's "Scorecard" opens the
 * full card, and the two agree on every number, a "Not in" +5 hole included.
 */
class GolfScorecardTest : ScreenshotBase() {
    private val names = listOf('m' to "You", 'o' to "Ben")

    // Ben was picked up on hole 3 (3 shots + 5 = 8), already in his 22.
    private val view = GolfView(
        hole = 4, holes = 9,
        strokes = mapOf('m' to listOf(3, 5, 8, 4, 2), 'o' to listOf(4, 6, 8, 3, 1)),
        balls = mapOf('m' to Pt(0.5, 2.5), 'o' to Pt(0.4, 1.0)),
        done = mapOf('m' to false, 'o' to false),
        last = null,
        unfinished = mapOf('o' to setOf(2)),
    )

    @Composable
    private fun menu(onScorecard: (() -> Unit)?) {
        val menu = GameMenu(GameKind.GOLF, onRules = {}, onLeave = {}, onScorecard = onScorecard)
        ToySheetCard { GameMenuRows(menu, close = {}, then = { a -> { a() } }) }
    }

    @Test fun chipTotalsEqualTheScorecardTotals() {
        compose.setContent { PipPopTheme(darkTheme = false) { Column(Modifier.fillMaxWidth().padding(16.dp)) { GolfScorecard(view, names) } } }
        for ((mark, name) in names) {
            val total = view.total(mark)
            assertEquals("$total · ${view.strokes[mark]!![4]} on this hole", golfTotals(view, mark))
            compose.onNodeWithText("$name · $total").assertIsDisplayed()
        }
        assertEquals(22, view.total('o'))
        compose.onNodeWithText("Not in (+5): hole 3").assertIsDisplayed()
    }

    @Test fun theMenuEntryOpensTheScorecard() {
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                var open by remember { mutableStateOf(false) }
                if (open) GolfScorecardSheet(view, names) { open = false }
                menu { open = true }
            }
        }
        compose.onNodeWithText("Scorecard").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Par 45 · under par is green with a minus, over par is red with a plus.").assertIsDisplayed()
        // The sheet scrolls, so the note may sit below what is shown; it is in the card.
        assertEquals(1, compose.onAllNodes(androidx.compose.ui.test.hasText("Not in (+5): hole 3")).fetchSemanticsNodes().size)
    }

    @Test fun otherGamesMenusHaveNoScorecardRow() {
        compose.setContent { PipPopTheme(darkTheme = false) { menu(null) } }
        compose.onAllNodesWithTextCount("Scorecard", 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, n: Int) =
        assertEquals(n, onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size)

    @Test fun menuLight() = shot("golf-menu", dark = false) { menu {} }

    @Test fun menuDark() = shot("golf-menu", dark = true) { menu {} }

    @Test fun scorecardSheetLight() = shot("golf-scorecard", dark = false) { ToySheetCard { Column(Modifier.padding(16.dp)) { GolfScorecard(view, names) } } }

    @Test fun scorecardSheetDark() = shot("golf-scorecard", dark = true) { ToySheetCard { Column(Modifier.padding(16.dp)) { GolfScorecard(view, names) } } }
}
