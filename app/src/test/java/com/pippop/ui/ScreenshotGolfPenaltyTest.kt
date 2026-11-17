package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.Pt
import com.pippop.ui.screens.GolfScorecard
import org.junit.Test

/** Mini Golf step 10: the scorecard marks a hole a ball was still out on ("3 +5" in its cell and a note), light and dark. */
class ScreenshotGolfPenaltyTest : ScreenshotBase() {
    @Composable
    private fun card() {
        // Ben was picked up on hole 3: 3 shots plus 5 is the 8 on his card, already in his 15.
        val view = GolfView(
            hole = 4, holes = 9,
            strokes = mapOf('m' to listOf(3, 5, 8, 4, 2), 'o' to listOf(4, 6, 8, 3, 1)),
            balls = mapOf('m' to Pt(0.5, 2.5), 'o' to Pt(0.4, 1.0)),
            done = mapOf('m' to false, 'o' to false),
            last = null,
            unfinished = mapOf('o' to setOf(2)),
        )
        Column(Modifier.fillMaxWidth().padding(16.dp)) { GolfScorecard(view, listOf('m' to "You", 'o' to "Ben")) }
    }

    @Test fun penaltyLight() = shot("golf-penalty", dark = false) { card() }

    @Test fun penaltyDark() = shot("golf-penalty", dark = true) { card() }

    @Test fun theNoteIsInWords() {
        compose.setContent { com.pippop.ui.theme.PipPopTheme(darkTheme = false) { card() } }
        compose.onNodeWithText("Not in (+5): hole 3").assertIsDisplayed()
    }
}
