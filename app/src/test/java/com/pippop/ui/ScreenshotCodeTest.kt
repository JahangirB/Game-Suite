package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.CodeGuess
import com.pippop.game.code.CodeBreaker
import com.pippop.game.code.CodeRules
import com.pippop.ui.screens.CodeEntry
import com.pippop.ui.screens.CodeSafe
import com.pippop.ui.screens.GuessRows
import org.junit.Test

/** Code Breaker picture: a safe, four scored guesses in wooden trays with marks in the marbles, and the colour buttons. */
class ScreenshotCodeTest : ScreenshotBase() {
    // Scored by the game's own rules against the secret "rgbo", so the pins are what a real game shows.
    @Composable
    private fun code() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val secret = "rgbo"
        val guesses = listOf("rrgb", "ygbo", "pcbo", "rgbo".replace('o', 'y')).map {
            val (black, white) = CodeBreaker.score(it, secret)
            CodeGuess(it, black, white)
        }
        CodeSafe(guesses, 4, Modifier.fillMaxWidth().height(108.dp))
        GuessRows(guesses, Modifier.fillMaxWidth())
        CodeEntry(enabled = true, sendLabel = "Guess", onSend = {}, rules = CodeRules(colours = 7))
    }

    @Test fun codeLight() = shot("code", dark = false) { code() }

    @Test fun codeDark() = shot("code", dark = true) { code() }
}
