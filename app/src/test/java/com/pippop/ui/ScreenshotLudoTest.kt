package com.pippop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.ludo.LudoView
import com.pippop.ui.screens.LudoBoard
import org.junit.Test

/** Ludo mid-game after a roll of 6: pawns, landing squares with dotted trails, stacked pawns. */
class ScreenshotLudoTest : ScreenshotBase() {
    // Red (progress 8) and two yellow (progress 34 = ring (26 + 34) % 52 = 8) share ring square 8,
    // a safe star square, so no one is knocked out and the pawns sit side by side.
    private val view = LudoView(
        colours = mapOf('m' to 0, 'o' to 2),
        tokens = mapOf('m' to listOf(-1, 8, 20, 56), 'o' to listOf(-1, 34, 34, 30)),
        roll = 6,
        movable = listOf(0, 1, 2),
        lastMove = null,
    )

    @Composable
    private fun ludoPawns() = Box(Modifier.padding(16.dp)) {
        LudoBoard(view, onToken = null, modifier = Modifier.size(360.dp))
    }

    @Test fun ludoPawnsLight() = shot("ludo-pawns", dark = false) { ludoPawns() }

    @Test fun ludoPawnsDark() = shot("ludo-pawns", dark = true) { ludoPawns() }
}
