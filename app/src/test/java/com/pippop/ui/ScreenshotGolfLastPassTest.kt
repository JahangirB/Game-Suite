package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.Pt
import com.pippop.ui.screens.GolfHeader
import com.pippop.ui.screens.GolfScorecard
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mini Golf step 13: the result's scorecard starts as par and totals (the buttons stay in view) with
 * "Show scorecard" one tap away; the solo header with three computers wraps whole entries at 360 dp
 * and font scale 1.3. Light and dark.
 */
class ScreenshotGolfLastPassTest : ScreenshotBase() {
    private val names = listOf('m' to "You", 'o' to "CPU 1", 'p' to "CPU 2", 'q' to "CPU 3")
    private val view = GolfView(
        hole = 4, holes = 9,
        strokes = mapOf(
            'm' to listOf(3, 5, 8, 4, 2), 'o' to listOf(4, 6, 8, 3, 1),
            'p' to listOf(4, 4, 3, 3, 4), 'q' to listOf(5, 4, 3, 3, 4),
        ),
        balls = mapOf('m' to Pt(0.5, 2.5), 'o' to Pt(0.4, 1.0), 'p' to Pt(0.4, 1.0), 'q' to Pt(0.4, 1.0)),
        done = mapOf('m' to false, 'o' to false, 'p' to false, 'q' to false),
        last = null,
        unfinished = mapOf('o' to setOf(2)),
    )

    @Composable
    private fun result() = Column(Modifier.fillMaxWidth().padding(16.dp)) { GolfScorecard(view, names, compact = true) }

    @Composable
    private fun header() = CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
        Column(Modifier.width(360.dp).padding(16.dp)) { GolfHeader(view, names, totals = true) }
    }

    @Test fun resultLight() = shot("golf-result-card", dark = false) { result() }
    @Test fun resultDark() = shot("golf-result-card", dark = true) { result() }
    @Test fun headerLight() = shot("golf-solo-header", dark = false) { header() }
    @Test fun headerDark() = shot("golf-solo-header", dark = true) { header() }

    @Test fun resultStartsShortAndOpensInOneTap() {
        compose.setContent { PipPopTheme(darkTheme = false) { result() } }
        compose.onNodeWithText("Not in (+5): hole 3").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasContentDescription("Pars", substring = true)).fetchSemanticsNodes().size)
        compose.onNodeWithText("Show scorecard").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hide scorecard").assertIsDisplayed()
        assertEquals(1, compose.onAllNodes(hasContentDescription("Pars", substring = true)).fetchSemanticsNodes().size)
    }
}
