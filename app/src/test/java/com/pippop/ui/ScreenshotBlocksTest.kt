package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.game.blocks.Blocks
import com.pippop.game.blocks.BlocksMatch
import com.pippop.game.blocks.BlocksView
import com.pippop.ui.screens.BlocksPlayBody
import org.json.JSONObject
import org.junit.Test

/** Corner Blocks mid-game: a piece previewed in green on a legal spot, and the tray grouped by size. */
class ScreenshotBlocksTest : ScreenshotBase() {
    // Both sides play four pieces by the game's own rules (the computer's choice, no noise), then
    // we pick one of your legal moves to preview.
    private val match = BlocksMatch(listOf('m', 'o')).also { m ->
        repeat(4) {
            for (who in listOf('m', 'o')) {
                val pl = m.computerMove(who, 1.0)!!
                m.play(who, JSONObject().put("piece", pl.piece).put("orient", pl.orient).put("x", pl.x).put("y", pl.y))
            }
        }
    }
    private val view = BlocksView.from(match.view('m', mapOf('o' to 'o')))
    private val move = match.movesFor('m').first { it.orient == 0 && Blocks.PIECES[it.piece].size == 4 && view.allows(it) }
    private val shape = Blocks.shape(move.piece, 0)
    private val spot = (move.x + shape.maxOf { it.first } / 2) to (move.y + shape.maxOf { it.second } / 2)

    @Composable
    private fun blocks() = Column(Modifier.fillMaxSize().padding(16.dp)) {
        BlocksPlayBody(view, canPlay = true, hintsOn = true, onPlace = {}, startPiece = move.piece, startTarget = spot)
    }

    @Test fun blocksLight() = shot("blocks", dark = false) { blocks() }

    @Test fun blocksDark() = shot("blocks", dark = true) { blocks() }
}
