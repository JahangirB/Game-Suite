package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.bingo.Bingo
import com.pippop.ui.screens.BingoCard
import com.pippop.ui.screens.CallStrip
import org.junit.Test
import kotlin.random.Random

/**
 * Bingo mid-game: a card from the game's own dealer with eight called numbers dabbed. The top
 * row has four dabbed squares, so its last square glows gold (one away); the ball rail shows
 * call 12; the newest big, five older on the rail.
 */
class ScreenshotBingoTest : ScreenshotBase() {
    private val card = Bingo.dealCard(Random(5))
    // Squares dabbed: the top row except its last square, plus a few others (none of them one dab from another line).
    private val dabbed = listOf(0, 1, 2, 3, 6, 9, 15, 19)
    private val dabs = dabbed.toSet()
    // The calls: the dabbed numbers first, then four more, then the rest, so every dab is on a called number.
    private val calls = run {
        val first = dabbed.map { card[it] }
        val rest = (1..Bingo.NUMBERS).filter { it !in first }.shuffled(Random(5))
        first + rest
    }

    @Composable
    private fun bingo() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CallStrip(calls, 12)
        BingoCard(card, dabs, null, true, {}, Modifier.fillMaxWidth().weight(1f))
    }

    @Test fun bingoLight() = shot("bingo", dark = false) { bingo() }

    @Test fun bingoDark() = shot("bingo", dark = true) { bingo() }
}
