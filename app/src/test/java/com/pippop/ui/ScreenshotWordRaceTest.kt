package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.WordGuess
import com.pippop.game.words.Words
import com.pippop.ui.screens.WordGrid
import org.junit.Test

/** Word Race picture: four guesses at CRANE showing all three results with their marks, the last one correct. */
class ScreenshotWordRaceTest : ScreenshotBase() {
    // Scored by the game's own rules, so the colours and marks are exactly what a real round shows
    // (ERROR against CRANE: the E is in the word elsewhere, the first R is in place, the later Rs are repeats and are out).
    @Composable
    private fun wordRace() = Column(Modifier.padding(16.dp)) {
        val answer = "crane"
        val guesses = listOf("slate", "error", "grape", "crane").map { WordGuess(it, Words.score(it, answer)) }
        WordGrid(guesses, input = "", tries = 6, length = 5, modifier = Modifier.fillMaxWidth())
    }

    @Test fun wordRaceLight() = shot("word-race", dark = false) { wordRace() }

    @Test fun wordRaceDark() = shot("word-race", dark = true) { wordRace() }
}
