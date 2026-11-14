package com.pippop.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.ColourTheme
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.Profile
import com.pippop.data.SavedPartner
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.ColourThemePicker
import com.pippop.ui.screens.ConnectFourBoard
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.OptionRow
import com.pippop.ui.screens.ShipsSea
import com.pippop.game.ships.Shot
import org.junit.Test

/** Step 24: the home and the profile's Look card in Mint, Peach and Sky (light), one dark one, and a board in two colourways. */
class ScreenshotColourThemesTest : ScreenshotBase() {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Composable
    private fun home(theme: ColourTheme) {
        val profile = Profile(
            name = "Sam", avatar = Avatar.FOX, colourTheme = theme,
            partner = SavedPartner("https://example.com/x", "", "K7Q2NB", "Maya", Avatar.PANDA, "device", "me"),
            playMode = PlayMode.ONLINE,
            gameOrder = listOf(
                GameKind.SUDOKU, GameKind.SHIPS, GameKind.CONNECT4, GameKind.LUDO, GameKind.WORDS, GameKind.DOMINOES,
                GameKind.CLASH, GameKind.GOLF, GameKind.MEMORY, GameKind.DOTS, GameKind.DRAW, GameKind.TTT,
            ),
        )
        HomeScreen(
            solo = SoloUiState(), profile = profile, onContinue = {}, onProfile = {}, onSolo = { _, _ -> },
            onArrange = {}, onScores = {}, partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {},
            connected = false, onConnect = {}, now = 0L,
        )
    }

    @Composable
    private fun look(theme: ColourTheme) = Column(Modifier.padding(16.dp)) {
        AppCard("Look") {
            OptionRow("Theme", listOf(0 to "System", 1 to "Light", 2 to "Dark"), 0, {})
            ColourThemePicker(theme) {}
        }
    }

    @Composable
    private fun board() = Column(Modifier.padding(16.dp)) {
        val rows = listOf(".......", ".......", "...o...", "..mo...", ".mooo..", "mmomo.o")
        ConnectFourBoard(rows.joinToString(""), winLine = emptyList(), lastCell = null, onDrop = null, modifier = Modifier.size(300.dp, 348.dp))
    }

    @Test fun homeMint() = shot("theme-home-mint", false, ColourTheme.MINT) { home(ColourTheme.MINT) }
    @Test fun homePeach() = shot("theme-home-peach", false, ColourTheme.PEACH) { home(ColourTheme.PEACH) }
    @Test fun homeSky() = shot("theme-home-sky", false, ColourTheme.SKY) { home(ColourTheme.SKY) }
    @Test fun homeMintDark() = shot("theme-home-mint", true, ColourTheme.MINT) { home(ColourTheme.MINT) }
    @Test fun homeSkyDark() = shot("theme-home-sky", true, ColourTheme.SKY) { home(ColourTheme.SKY) }

    @Test fun lookMint() = shot("theme-look-mint", false, ColourTheme.MINT) { look(ColourTheme.MINT) }
    @Test fun lookPeach() = shot("theme-look-peach", false, ColourTheme.PEACH) { look(ColourTheme.PEACH) }
    @Test fun lookSky() = shot("theme-look-sky", false, ColourTheme.SKY) { look(ColourTheme.SKY) }
    @Test fun lookPeachDark() = shot("theme-look-peach", true, ColourTheme.PEACH) { look(ColourTheme.PEACH) }

    @Composable
    private fun sudoku() = Column(Modifier.padding(16.dp)) {
        val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        var b = com.pippop.game.BoardState.fromPuzzle(puzzle)
        b = b.place(2, 4)
        com.pippop.ui.screens.SudokuGrid(board = b, errors = b.conflicts(), selected = 2, hidden = false, solution = null, onSelect = {}, modifier = Modifier.size(360.dp))
    }

    @Composable
    private fun ships() = Column(Modifier.padding(16.dp)) {
        val shots = listOf(Shot(0, 0, false), Shot(4, 3, false), Shot(9, 5, false), Shot(7, 1, true), Shot(2, 6, true))
        ShipsSea(ships = emptyList(), shots = shots, lastShot = shots.last(), pending = 5 to 5, enemy = true, modifier = Modifier.padding(horizontal = 24.dp))
    }

    @Test fun shipsSky() = shot("theme-ships-sky", false, ColourTheme.SKY) { ships() }
    @Test fun shipsLavender() = shot("theme-ships-lavender", false, ColourTheme.LAVENDER) { ships() }

    @Test fun homePeachDark() = shot("theme-home-peach", true, ColourTheme.PEACH) { home(ColourTheme.PEACH) }
    @Test fun lookSkyDark() = shot("theme-look-sky", true, ColourTheme.SKY) { look(ColourTheme.SKY) }
    @Test fun sudokuMintDark() = shot("theme-sudoku-mint", true, ColourTheme.MINT) { sudoku() }

    @Test fun boardLavender() = shot("theme-board-lavender", false, ColourTheme.LAVENDER) { board() }
    @Test fun boardSky() = shot("theme-board-sky", false, ColourTheme.SKY) { board() }
}
