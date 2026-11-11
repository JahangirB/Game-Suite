package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.clash.ClashMove
import com.pippop.game.clash.ClashView
import com.pippop.ui.screens.ClashTable
import kotlinx.coroutines.delay
import org.junit.Test

/** Colour Clash pictures: a game mid-play on your turn, with a mixed hand where some cards fit. */
class ScreenshotClashTest : ScreenshotBase() {
    private fun view(top: String, colour: Char, last: ClashMove?) = ClashView(
        top = top, colour = colour,
        hand = listOf("g2", "r5", "g9", "y3", "b8", "W", "rD"),
        counts = mapOf('m' to 7, 'o' to 4, 'p' to 6),
        order = listOf('m', 'o', 'p'),
        direction = 1, deck = 61, drawn = false, lastMove = last,
    )

    // Two plays happen after the first picture, so the pile shows the two cards under the top one.
    // On green with 8 on top: g2, g9, b8 (the same number) and the wild fit; r5, y3 and rD don't.
    @Composable
    private fun clash() {
        var game by remember { mutableStateOf(view("b4", 'b', null)) }
        LaunchedEffect(Unit) {
            delay(100)
            game = view("g4", 'g', ClashMove('o', "g4", 'g', 0, false, null))
            delay(100)
            game = view("g8", 'g', ClashMove('p', "g8", 'g', 0, false, null))
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ClashTable(game, myTurn = true, enabled = true, waitingText = "", name = { "You" }, onPlay = { _, _ -> }, onDraw = {}, onPass = {})
        }
    }

    @Test fun clashLight() = shot("clash", dark = false) { clash() }

    @Test fun clashDark() = shot("clash", dark = true) { clash() }
}
