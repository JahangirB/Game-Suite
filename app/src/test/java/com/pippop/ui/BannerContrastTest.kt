package com.pippop.ui

import androidx.compose.ui.graphics.Color
import com.pippop.data.GameKind
import com.pippop.ui.art.color
import com.pippop.ui.screens.ludoColor
import com.pippop.ui.screens.clashColor
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.DarkPalette
import com.pippop.ui.theme.LightPalette
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.contrast
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.textFill
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small hint under "Your turn" reads at 4.5:1 or better on every colour a turn banner can get. */
class BannerContrastTest {
    private val fills: List<Pair<String, Color>> =
        GameKind.entries.map { "game ${it.key}" to it.color } +
            listOf(Pieces.Red, Pieces.Yellow, Pieces.Green, Pieces.Blue, Pieces.Purple, Pieces.Orange, Pieces.Pink, Pieces.Teal).mapIndexed { i, c -> "piece $i" to c } +
            (0..3).map { "ludo $it" to ludoColor(it) } +
            "rgby?".map { "clash $it" to clashColor(it) } +
            listOf(Board.Ink).map { "ink" to it } +
            listOf(LightPalette, DarkPalette).flatMapIndexed { i, p ->
                listOf("primary $i" to p.primary, "good $i" to p.good, "bad $i" to p.bad)
            }

    @Test
    fun hintReadsOnEveryBannerFill() {
        for ((name, c) in fills) {
            val fill = textFill(c)
            val ratio = contrast(readableOn(fill), fill)
            assertTrue("$name: $ratio", ratio >= 4.5f)
        }
    }
}
