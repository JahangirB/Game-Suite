package com.pippop.ui

import androidx.compose.runtime.Composable
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.Profile
import com.pippop.data.RecentGame
import com.pippop.data.SavedPartner
import com.pippop.game.BoardState
import com.pippop.game.SoloGame
import com.pippop.ui.screens.HomeScreen
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * The home before you connect: the header, one line to connect, "Jump back in" (the Sudoku 27% done
 * and the last two games played), the Games row and the key-art tiles on their shelves. Four games
 * are put away, so the shelf ends in "More games · 4". No month strip, no scores on tiles.
 */
class ScreenshotHomeTest : ScreenshotBase() {
    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    /** The classic puzzle with its first 14 empty squares filled in with the right digits (27% of the squares). */
    private fun saved(): SoloGame {
        val sol = solution.map { it.digitToInt() }
        var board = BoardState.fromPuzzle(puzzle)
        board.cells.indices.filter { !board.givens[it] }.take(14).forEach { board = board.place(it, sol[it]) }
        return SoloGame(Difficulty.MEDIUM, puzzle, sol, board, elapsedMs = 38 * 60_000L + 12_000L)
    }

    private val day = 24 * 60 * 60 * 1000L

    @Composable
    private fun home() {
        // A fixed clock, so the tags read the same every day: Friday 13 November 2026, midday.
        val now = NOW
        val profile = Profile(
            name = "Sam",
            avatar = Avatar.FOX,
            partner = SavedPartner("https://example.com/x", "", "K7Q2NB", "Maya", Avatar.PANDA, "device", "me"),
            playMode = PlayMode.ONLINE,
            gameOrder = HomeOrder + PutAway,
            putAway = PutAway.toSet(),
            recentGames = listOf(RecentGame(GameKind.LUDO, now - day), RecentGame(GameKind.WORDS, now - 4 * day)),
        )
        HomeScreen(
            solo = SoloUiState(game = saved()), profile = profile, onContinue = {}, onNewGame = { _, _ -> }, onProfile = {}, onSolo = {},
            onArrange = {}, onScores = {}, partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {},
            connected = false, onConnect = {}, now = NOW,
        )
    }

    @Test fun homeLight() = shot("home-new", dark = false) { home() }

    @Test fun homeDark() = shot("home-new", dark = true) { home() }

    private companion object {
        val NOW: Long = ZonedDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val HomeOrder = listOf(
            GameKind.SUDOKU, GameKind.SHIPS, GameKind.CONNECT4, GameKind.LUDO, GameKind.WORDS, GameKind.DOMINOES,
            GameKind.CLASH, GameKind.GOLF, GameKind.MEMORY, GameKind.DOTS, GameKind.DRAW, GameKind.TTT,
            GameKind.BINGO, GameKind.HOCKEY, GameKind.CODE, GameKind.BALLOON,
        )
        val PutAway = listOf(GameKind.NUMBERS, GameKind.GRID, GameKind.SLIDE, GameKind.BLOCKS)
    }
}
