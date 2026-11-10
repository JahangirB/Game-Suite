package com.pippop.ui

import com.pippop.ui.screens.tutorialFor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.data.GameKind
import com.pippop.data.Profile
import com.pippop.game.BoardState
import com.pippop.game.ludo.LudoView
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.KeyArt
import com.pippop.data.Avatar
import com.pippop.game.dominoes.DominoView
import com.pippop.game.dots.DotsShape
import com.pippop.game.dominoes.Tile
import com.pippop.game.memory.CardFace
import com.pippop.ui.screens.ConnectFourBoard
import com.pippop.ui.screens.DominoTable
import com.pippop.ui.screens.DotsBoard
import com.pippop.ui.screens.DieFace
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.LudoBoard
import com.pippop.ui.screens.MemoryBoard
import com.pippop.ui.screens.NumberPad
import com.pippop.ui.screens.PopupAction
import com.pippop.ui.screens.ResultPopup
import com.pippop.ui.screens.SudokuGrid
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.PipPopTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of screens in light and dark, saved under app/build/outputs/roborazzi and uploaded
 * by CI, so a change to how something looks can be seen without a phone. They are not
 * compared against stored pictures.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one starts Firebase and loads word lists.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** Renders [content] on the app background and saves it as `<name>-light|dark.png`. */
    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        // Animations would never settle (the result loops, bobbing tokens); step the clock instead.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    @Composable
    private fun home() = HomeScreen(
        solo = SoloUiState(),
        profile = Profile(name = "Sam"),
        onContinue = {},
        onNewGame = { _, _ -> },
        onProfile = {},
        onSolo = {},
        onArrange = {},
        partnerBusy = false,
        onPlayWithPartner = {},
        onInvitePartner = {},
        onPlayMode = {},
        connected = false,
        onConnectNearby = {},
        onStartRoom = {},
        onJoinRoom = {},
    )

    @Test fun homeLight() = shot("home", dark = false) { home() }

    @Test fun homeDark() = shot("home", dark = true) { home() }

    private val ludo = LudoView(
        colours = mapOf('m' to 0, 'o' to 2),
        tokens = mapOf('m' to listOf(-1, 5, 20, 56), 'o' to listOf(-1, -1, 12, 30)),
        roll = 6,
        movable = listOf(0, 1),
        lastMove = null,
    )

    @Composable
    private fun ludoTable() = Box(Modifier.padding(16.dp)) {
        LudoBoard(ludo, onToken = null, modifier = Modifier.size(360.dp))
        DieFace(5, Modifier.padding(top = 372.dp))
    }

    @Test fun ludoLight() = shot("ludo", dark = false) { ludoTable() }

    @Test fun ludoDark() = shot("ludo", dark = true) { ludoTable() }

    // Connect Four: a game in play above, and four in a row (the line drawn through) below.
    @Composable
    private fun connectFour() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val rows = listOf(".......", ".......", "...o...", "..mo...", ".mooo..", "mmomo.o")
        ConnectFourBoard(rows.joinToString(""), winLine = emptyList(), lastCell = null, onDrop = null, modifier = Modifier.size(300.dp, 348.dp))
        val won = listOf(".......", ".......", "...o...", "..mo...", ".moom..", "mmoooo.")
        ConnectFourBoard(won.joinToString(""), winLine = listOf(37, 38, 39, 40), lastCell = null, onDrop = null, modifier = Modifier.size(300.dp, 348.dp))
    }

    @Test fun connectFourLight() = shot("connect-four", dark = false) { connectFour() }

    @Test fun connectFourDark() = shot("connect-four", dark = true) { connectFour() }

    // Dots & Boxes mid-game on 4 x 4: lines by both players in their own colours, three boxes
    // taken (two yours, one theirs). Lines and boxes present at the start show whole, no animation.
    @Composable
    private fun dots() = Column(Modifier.padding(16.dp)) {
        val shape = DotsShape.from("4x4")
        val lines = CharArray(shape.lines) { '.' }
        // Box 0 (top left) yours, box 1 theirs, box 4 yours; plus a few loose lines.
        for (box in listOf(0 to 'm', 1 to 'o', 4 to 'm')) for (l in shape.boxLines(box.first)) lines[l] = box.second
        lines[3] = 'o'
        lines[9] = 'm'
        lines[shape.horizontal + 12] = 'm'
        val boxes = CharArray(shape.boxes) { '.' }
        boxes[0] = 'm'
        boxes[1] = 'o'
        boxes[4] = 'm'
        DotsBoard(
            String(lines), String(boxes), lastLine = null,
            avatars = mapOf('m' to Avatar.FOX, 'o' to Avatar.CAT),
            onLine = null, modifier = Modifier.size(320.dp), shape = shape,
        )
    }

    @Test fun dotsLight() = shot("dots", dark = false) { dots() }

    @Test fun dotsDark() = shot("dots", dark = true) { dots() }

    // Sudoku mid-game: givens, your digits, the 6 you picked, a clashing 9 in the third row; the
    // pad below with 2 finished, and a big board's pad (more than nine left) under it.
    private val sudokuBoard = BoardState.fromPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079")
        .place(2, 4).place(3, 6).place(5, 8).place(6, 9).place(10, 7).place(11, 2).place(21, 9)
        .place(30, 7).place(32, 1).place(57, 5).place(58, 3)

    // Memory Pairs: mostly face down, one card turned up, a pair taken by you and one by the other player.
    @Composable
    private fun memory() = Column(Modifier.padding(16.dp)) {
        val animals = listOf(Avatar.FOX, Avatar.CAT)
        val faces = List(16) { i ->
            when (i) {
                0, 9 -> CardFace(animals[0])
                5, 14 -> CardFace(animals[1])
                3 -> CardFace(Avatar.entries[2])
                else -> null
            }
        }
        MemoryBoard(faces, "m....o...m....o.", onFlip = null, modifier = Modifier.size(300.dp, 400.dp))
    }

    @Test fun memoryLight() = shot("memory", dark = false) { memory() }

    @Test fun memoryDark() = shot("memory", dark = true) { memory() }

    @Composable
    private fun sudoku() = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SudokuGrid(board = sudokuBoard, errors = sudokuBoard.conflicts(), selected = 3, hidden = false, solution = null, onSelect = {})
        val left = listOf(3, 0, 5, 1, 9, 2, 4, 7, 6)
        NumberPad(remaining = { left[it - 1] }, enabled = true, onDigit = {})
        val bigLeft = listOf(17, 0, 41, 9, 23, 12, 30, 5, 1)
        NumberPad(remaining = { bigLeft[it - 1] }, enabled = true, onDigit = {})
    }

    @Test fun sudokuLight() = shot("sudoku", dark = false) { sudoku() }

    @Test fun sudokuDark() = shot("sudoku", dark = true) { sudoku() }

    // Dominoes mid-game on your turn: the line with a ringed number at each open end, and a hand
    // where three tiles fit (they stand up with a gold edge) and three fade back.
    @Composable
    private fun dominoes() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val view = DominoView(
            line = listOf(Tile(3, 4), Tile(4, 4), Tile(4, 1), Tile(1, 5)),
            start = 1,
            hand = listOf(Tile(3, 6), Tile(5, 5), Tile(2, 2), Tile(0, 5), Tile(1, 6), Tile(2, 4)),
            counts = mapOf('m' to 6, 'o' to 5),
            scores = mapOf('m' to 0, 'o' to 0),
            partners = emptyList(),
            boneyard = 8,
            teams = false,
            draw = true,
            target = 100,
            roundNo = 1,
            lastMove = null,
            lastRound = null,
        )
        DominoTable(view, myTurn = true, enabled = true, waitingText = "", name = { "You" }, onPlay = { _, _ -> }, onDraw = {}, onPass = {})
    }

    @Test fun dominoesLight() = shot("dominoes", dark = false) { dominoes() }

    @Test fun dominoesDark() = shot("dominoes", dark = true) { dominoes() }

    @Composable
    private fun result() = Box(Modifier.fillMaxSize()) {
        ResultPopup(
            game = GameKind.CONNECT4,
            outcome = Outcome.WIN,
            title = "You win!",
            detail = "Four in a row",
            finish = PopupAction("Finish", {}),
            again = PopupAction("Rematch", {}),
            scores = listOf("You 3", "Alex 2"),
            status = "Alex wants a rematch",
            statusGood = true,
        )
    }

    @Test fun resultLight() = shot("result", dark = false) { result() }

    @Test fun resultDark() = shot("result", dark = true) { result() }

    // Every step of every tutorial on its board: one row per game, in one picture.
    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun tutorials() = shot("tutorials", dark = false) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (kind in GameKind.entries) {
                val steps = tutorialFor(kind) ?: continue
                Text(kind.label, color = AppTheme.palette.text)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEach { step -> Box(Modifier.size(72.dp * step.aspect, 72.dp)) { step.board(Modifier.fillMaxSize()) } }
                }
            }
        }
    }

    // Every game's key art, about the game sheet's size.
    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun keyArt() = shot("key-art", dark = false) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GameKind.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { KeyArt(it, Modifier.size(196.dp, 119.dp)) }
                }
            }
        }
    }

    // The design-system gallery is long: a tall screen fits it all in one picture.
    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun galleryLight() = shot("gallery", dark = false) { DesignSystemGallery() }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun galleryDark() = shot("gallery", dark = true) { DesignSystemGallery() }

    /** The phone's largest text setting: nothing should be cut off or overlap. */
    @Test @Config(qualifiers = "w411dp-h3200dp-xxhdpi")
    fun galleryLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        shot("gallery-large-text", dark = false) { DesignSystemGallery() }
    }
}
