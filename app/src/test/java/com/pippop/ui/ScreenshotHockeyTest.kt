package com.pippop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.hockey.Body
import com.pippop.game.hockey.Hockey
import com.pippop.game.hockey.HockeyFrame
import com.pippop.ui.screens.HockeyRink
import com.pippop.ui.screens.MyDisc
import com.pippop.ui.screens.TheirDisc
import org.junit.Test

/**
 * Air Hockey mid-game at 4 - 2: the puck is fast across the table, so it drags a streak. The
 * table is fed a short run of frames, as the game does, so the streak is built from them.
 */
class ScreenshotHockeyTest : ScreenshotBase() {
    @Composable
    private fun hockey() {
        // The puck moves 0.03 a frame (1.8 per second, under the 2.8 maximum), up and to the left,
        // in your half. Paddles stay in their own halves (you below 0.8 + 0.075, them above 0.8 - 0.075).
        val frames = remember {
            List(14) { i ->
                HockeyFrame(
                    puck = Body(0.72f - 0.012f * i, 1.2f - 0.028f * i, -0.72f, -1.68f),
                    bottom = Body(0.45f, 1.3f),
                    top = Body(0.4f, 0.3f),
                    bottomScore = 4,
                    topScore = 2,
                )
            }
        }
        var frame by remember { mutableStateOf(frames[0]) }
        LaunchedEffect(Unit) {
            for (f in frames) {
                frame = f
                withFrameNanos { }
            }
        }
        Box(Modifier.padding(16.dp).fillMaxWidth().aspectRatio(Hockey.W / Hockey.H)) {
            HockeyRink(frame, MyDisc, TheirDisc, onMove = null, modifier = Modifier.fillMaxWidth().aspectRatio(Hockey.W / Hockey.H))
        }
    }

    @Test fun hockeyLight() = shot("hockey", dark = false) { hockey() }

    @Test fun hockeyDark() = shot("hockey", dark = true) { hockey() }
}
