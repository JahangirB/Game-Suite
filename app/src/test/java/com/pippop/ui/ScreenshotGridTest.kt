package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.grid.LetterGrid
import com.pippop.ui.screens.FoundWords
import com.pippop.ui.screens.LetterGridBoard
import org.junit.Test

/** Letter Grid picture: a drag through S-T-A-L-E in progress, the fuse frame about 60% left, two words already found. */
class ScreenshotGridTest : ScreenshotBase() {
    // Rows: S T A R / E L O N / B I D E / K M P S. Squares 0, 1, 2, 5, 4 spell STALE and each step touches the last.
    @Composable
    private fun grid() = Column(Modifier.padding(16.dp)) {
        val letters = "starelonbidekmps"
        val path = listOf(0, 1, 2, 5, 4)
        require(path.zipWithNext().all { (a, b) -> LetterGrid.touching(a, b) } && path.distinct().size == path.size)
        LetterGridBoard(
            letters, enabled = true, onWord = {}, modifier = Modifier.fillMaxWidth(),
            found = listOf("star", "told"), fuseLeftMs = { 72_000L }, startPath = path,
        )
        FoundWords(listOf("star", "told"))
    }

    @Test fun gridLight() = shot("grid", dark = false) { grid() }

    @Test fun gridDark() = shot("grid", dark = true) { grid() }
}
