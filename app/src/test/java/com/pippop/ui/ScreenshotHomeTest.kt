package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.game.BoardState
import com.pippop.game.SoloGame
import com.pippop.ui.screens.ContinueCard
import com.pippop.ui.screens.GameTile
import com.pippop.ui.screens.ShelfRow
import org.junit.Test

/**
 * The home shelf: the Continue card with a picture of the saved board and a "% done" bar, and
 * rows of shaded tiles standing on a shelf. The Last tag and icon loop are not built: the app
 * does not record which game was played last.
 */
class ScreenshotHomeTest : ScreenshotBase() {
    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    /** The classic puzzle with its first 14 empty squares filled in with the right digits. */
    private fun saved(): SoloGame {
        val sol = solution.map { it.digitToInt() }
        var board = BoardState.fromPuzzle(puzzle)
        board.cells.indices.filter { !board.givens[it] }.take(14).forEach { board = board.place(it, sol[it]) }
        return SoloGame(Difficulty.MEDIUM, puzzle, sol, board, elapsedMs = 38 * 60_000L + 12_000L)
    }

    @Composable
    private fun home() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ContinueCard(saved(), onPlay = {})
        listOf(
            listOf(GameKind.SUDOKU, GameKind.SHIPS),
            listOf(GameKind.CONNECT4, GameKind.DOTS),
        ).forEach { pair ->
            ShelfRow {
                pair.forEach { kind ->
                    GameTile(kind, Modifier.weight(1f), badge = if (kind == GameKind.SUDOKU) "Continue" else null, onLongClick = {}, onClick = {})
                }
            }
        }
    }

    @Test fun homeLight() = shot("home", dark = false) { home() }

    @Test fun homeDark() = shot("home", dark = true) { home() }
}
