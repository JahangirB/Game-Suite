package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.DrawStroke
import com.pippop.data.DrawView
import com.pippop.ui.screens.PadWithStamp
import com.pippop.ui.screens.WordClock
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.TextSize
import org.junit.Test

/**
 * Draw & Guess: the clock ring part-way round the word, and a right guess's stamp on a page
 * with a few strokes (a race round where Alex has just got it).
 */
class ScreenshotDrawGuessTest : ScreenshotBase() {
    @Composable
    private fun drawGuess() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val p = AppTheme.palette
        // A little house: walls, roof, door.
        val strokes = listOf(
            DrawStroke(floatArrayOf(0.25f, 0.5f, 0.25f, 0.8f, 0.75f, 0.8f, 0.75f, 0.5f, 0.25f, 0.5f), 0, 2, start = true),
            DrawStroke(floatArrayOf(0.2f, 0.5f, 0.5f, 0.25f, 0.8f, 0.5f), 3, 2, start = true),
            DrawStroke(floatArrayOf(0.45f, 0.8f, 0.45f, 0.62f, 0.55f, 0.62f, 0.55f, 0.8f), 1, 1, start = true),
        )
        val draw = DrawView(
            turn = 2, turns = 4, drawing = false, word = null, hint = "_ _ _ _ _", endsAt = null,
            strokes = strokes,
            guesses = listOf(false to "barn", false to "house"),
            score = 0, lastTurn = null,
            guessers = listOf("Sam", "Alex"), mode = "race",
            scores = emptyList(),
            rightGuesses = listOf(false, true),
        )
        // About half the 80 seconds left (44 s), and the last seconds in red.
        WordClock(0.55f, 44) {
            Text("_ _ _ _ _", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        }
        WordClock(0.1f, 8) {
            Text("Draw: HOUSE", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        }
        PadWithStamp(draw, strokes, 0, 1, null, Modifier)
    }

    @Test fun drawGuessLight() = shot("draw-guess", dark = false) { drawGuess() }

    @Test fun drawGuessDark() = shot("draw-guess", dark = true) { drawGuess() }
}
