package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.slide.Slide
import com.pippop.ui.screens.SlideBar
import com.pippop.ui.screens.SlideBoard
import com.pippop.ui.screens.SlideProgressBars
import com.pippop.ui.screens.slideHome
import com.pippop.ui.screens.slideHomeFromPercent
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.SeatColors
import org.junit.Test

/** Slide Race picture: a 4 x 4 race part way through, with tiles home (ticks) and both players' bars. */
class ScreenshotSlideTest : ScreenshotBase() {
    // Reached from the solved board by real slides (Slide.slide), so it is a legal, solvable position.
    private fun position(): List<Int> {
        var tiles = Slide.solved(4)
        for (tile in listOf(12, 8, 7, 11, 8, 7, 4, 3, 11, 8, 10, 6, 8, 4)) tiles = Slide.slide(tiles, 4, tile)
        return tiles
    }

    @Composable
    private fun slide() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val tiles = position()
        val p = AppTheme.palette
        SlideBoard(tiles, 4, true, {}, Modifier.size(320.dp))
        SlideProgressBars(
            listOf(
                SlideBar("You", p.primary, slideHome(tiles, 4), 15),
                SlideBar("Maya", SeatColors[0], slideHomeFromPercent(33, 4), 15),
            ),
        )
    }

    @Test fun slideLight() = shot("slide", dark = false) { slide() }

    @Test fun slideDark() = shot("slide", dark = true) { slide() }
}
