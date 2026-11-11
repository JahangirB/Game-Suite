package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.Pt
import com.pippop.ui.screens.GolfScoreStrip
import org.junit.Test

/**
 * Mini Golf mid-round: the fifth hole of nine, the scorecard strip across the top. Only the 2D
 * strip is drawn (the 3D course can't render here). Without a course list the game plays the
 * three built-in holes in turn, pars 4, 5, 6, 4, 5, 6..., so your 3, 5 and 8 are one under,
 * level and two over on the first three, 4 is level on the fourth, and 2 is under way on the fifth.
 */
class ScreenshotGolfTest : ScreenshotBase() {
    @Composable
    private fun golf() {
        val view = GolfView(
            hole = 4, holes = 9,
            strokes = mapOf('m' to listOf(3, 5, 8, 4, 2), 'o' to listOf(4, 6, 6, 3, 1)),
            balls = mapOf('m' to Pt(0.5, 2.5), 'o' to Pt(0.4, 1.0)),
            done = mapOf('m' to false, 'o' to false),
            last = null,
        )
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            GolfScoreStrip(view, listOf('m' to "You", 'o' to "Ben"))
        }
    }

    @Test fun golfLight() = shot("golf", dark = false) { golf() }

    @Test fun golfDark() = shot("golf", dark = true) { golf() }
}
