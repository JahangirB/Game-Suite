package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.ui.screens.BalloonRow
import com.pippop.ui.screens.LetterKeys
import com.pippop.ui.screens.WordTiles
import org.junit.Test

/** Balloon Words picture: a round of PLANET in progress, with right and wrong letters used and one balloon popped. */
class ScreenshotBalloonTest : ScreenshotBase() {
    // E, A and T are in PLANET and Z is not, so exactly one balloon of seven has gone (the game's own count of misses).
    @Composable
    private fun balloon() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val state = SoloBalloonUiState(started = true, word = "PLANET", guessed = listOf('E', 'A', 'T', 'Z'), misses = 1)
        BalloonRow(7, state.misses)
        WordTiles(state.pattern)
        LetterKeys(state.guessed, state.word, enabled = true, onLetter = {})
    }

    @Test fun balloonLight() = shot("balloon", dark = false) { balloon() }

    @Test fun balloonDark() = shot("balloon", dark = true) { balloon() }
}
