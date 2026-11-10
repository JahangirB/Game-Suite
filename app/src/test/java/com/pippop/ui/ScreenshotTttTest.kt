package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.ui.screens.TttBoard
import org.junit.Test

/**
 * Tic-Tac-Toe: an Ultimate board where the next move must go in the middle small board (the
 * other open boards are dimmed), and a normal 3 x 3 board with a few marks.
 */
class ScreenshotTttTest : ScreenshotBase() {
    @Composable
    private fun ttt() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // A real game, X first: X board 4 cell 1, O board 1 cell 4, X board 4 cell 8, O board 8 cell 0,
        // X board 0 cell 0, O board 0 cell 4. Each move sends the next player to the board of the
        // cell just taken, so now X must play in board 4.
        val cells = CharArray(81) { '.' }
        fun put(board: Int, vararg marks: Pair<Int, Char>) = marks.forEach { cells[board * 9 + it.first] = it.second }
        put(4, 1 to 'm', 8 to 'm')
        put(1, 4 to 'o')
        put(8, 0 to 'o')
        put(0, 0 to 'm', 4 to 'o')
        TttBoard(
            board = String(cells), boards = ".........", next = 4, winLine = emptyList(), lastCell = 4,
            playing = true, onMark = null, modifier = Modifier.size(320.dp),
        )
        TttBoard(
            board = "m.o.mo...", boards = "", next = null, winLine = emptyList(), lastCell = null,
            playing = true, onMark = null, modifier = Modifier.size(220.dp),
        )
    }

    @Test fun tttLight() = shot("ttt", dark = false) { ttt() }

    @Test fun tttDark() = shot("ttt", dark = true) { ttt() }
}
