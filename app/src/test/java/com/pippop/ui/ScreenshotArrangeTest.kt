package com.pippop.ui

import androidx.compose.runtime.Composable
import com.pippop.data.GameKind
import com.pippop.ui.screens.ArrangeGamesScreen
import org.junit.Test

/** The arrange screen with two games put away, under the "Put away" line. */
class ScreenshotArrangeTest : ScreenshotBase() {
    @Composable
    private fun screen() = ArrangeGamesScreen(
        // A short list so the line shows on one screen; the real list holds every game.
        order = GameKind.entries.take(7),
        putAway = GameKind.entries.take(7).takeLast(2).toSet(),
        onChange = { _, _ -> },
        onBack = {},
    )

    @Test fun arrangeLight() = shot("arrange-putaway", dark = false) { screen() }

    @Test fun arrangeDark() = shot("arrange-putaway", dark = true) { screen() }
}
