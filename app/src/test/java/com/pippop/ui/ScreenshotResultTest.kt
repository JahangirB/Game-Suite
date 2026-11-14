package com.pippop.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.TurnsView
import com.pippop.ui.screens.SoloClashScreen
import com.pippop.ui.screens.SoloConnectFourScreen
import com.pippop.ui.screens.SoloDominoesScreen
import com.pippop.ui.screens.SoloDotsScreen
import com.pippop.ui.screens.SoloLudoScreen
import com.pippop.ui.screens.SoloMemoryScreen
import com.pippop.ui.screens.SoloNumbersScreen
import com.pippop.ui.screens.SoloSlideScreen
import com.pippop.ui.screens.SoloTttScreen
import com.pippop.ui.screens.SoloWordsScreen
import com.pippop.data.HockeyScore
import com.pippop.data.ShipsView
import com.pippop.game.golf.GolfView
import com.pippop.game.ships.Ship
import com.pippop.game.ships.Shot
import com.pippop.ui.screens.GolfGameScreen
import com.pippop.ui.screens.HockeyScreen
import com.pippop.ui.screens.ShipsGameScreen
import com.pippop.data.BingoView
import com.pippop.data.CodeGuess
import com.pippop.data.CodeView
import com.pippop.data.DrawScore
import com.pippop.data.DrawStroke
import com.pippop.data.DrawTurnResult
import com.pippop.data.DrawView
import com.pippop.data.GridResult
import com.pippop.data.GridView
import com.pippop.data.GridWord
import com.pippop.data.NumberBoard
import com.pippop.data.NumbersView
import com.pippop.data.OtherWords
import com.pippop.data.SlideView
import com.pippop.data.WordGuess
import com.pippop.data.WordsView
import com.pippop.game.numbers.NumberCross
import com.pippop.game.numbers.NumberCrossGenerator
import com.pippop.game.numbers.NumberLevel
import com.pippop.game.slide.Slide
import com.pippop.game.slide.SlideLevel
import com.pippop.ui.screens.BingoGameScreen
import com.pippop.ui.screens.CodeGameScreen
import com.pippop.ui.screens.DrawGuessScreen
import com.pippop.ui.screens.GameScreen
import com.pippop.ui.screens.GridGameScreen
import com.pippop.ui.screens.NumberCrossScreen
import com.pippop.ui.screens.SlideGameScreen
import com.pippop.ui.screens.WordRaceScreen
import kotlin.random.Random
import com.pippop.game.balloon.BalloonView
import com.pippop.game.blocks.BlocksView
import com.pippop.game.clash.ClashMove
import com.pippop.game.clash.ClashView
import com.pippop.game.dominoes.DominoMove
import com.pippop.game.dominoes.DominoView
import com.pippop.game.dominoes.Tile
import com.pippop.game.ludo.LudoView
import com.pippop.ui.screens.BalloonGameScreen
import com.pippop.ui.screens.BlocksGameScreen
import com.pippop.ui.screens.ClashGameScreen
import com.pippop.ui.screens.DominoesGameScreen
import com.pippop.ui.screens.LudoGameScreen
import com.pippop.ui.screens.LocalMatchSession
import com.pippop.ui.screens.LocalRoomControls
import com.pippop.ui.screens.LocalTonight
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MatchSession
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.TurnGameScreen
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.hasOptions
import com.pippop.ui.screens.tonightScore
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every game's result, won and lost: the card under the board where it fits a 411 x 891 dp
 * screen (and a check that it does), the board's last position fully visible. Games whose
 * board is the whole screen keep the sheet over the game (see the notes on each).
 */
class ScreenshotResultTest : ScreenshotBase() {
    private fun player(name: String, avatar: Avatar, game: GameKind, ready: Boolean = false) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = game,
    )

    /** A finished game in a room with Maya; [winner] is "me", "opponent" or null (a tie). */
    private fun room(
        game: GameKind, winner: String?, endReason: String? = null, turns: TurnsView? = null, ships: ShipsView? = null, ready: Boolean = true,
        others: List<PlayerInfo> = listOf(player("Maya", Avatar.PANDA, game, ready)),
        change: (RoomState) -> RoomState = { it },
    ) = change(
        RoomState(
            code = "K7Q2NB", role = "host", game = game, status = RoomStatus.FINISHED, difficulty = null, round = 6, startAt = null,
            puzzle = null, solution = null, winner = winner, endReason = endReason, me = player("Sam", Avatar.FOX, game),
            opponent = others.firstOrNull(), ships = ships, turns = turns, serverTime = 0, others = others, inSession = true,
            winnerName = if (winner == "opponent") others.firstOrNull()?.name else null,
        ),
    )

    private fun vm() = BattleViewModel(ApplicationProvider.getApplicationContext<Application>())

    /** What the app provides round a game against Maya, with this game already counted in tonight's score. */
    @Composable
    private fun Session(room: RoomState, content: @Composable () -> Unit) {
        val tally = when (room.winner) {
            "me" -> mapOf(TALLY_ME to 3, "Maya" to 2)
            "opponent" -> mapOf(TALLY_ME to 2, "Maya" to 3)
            else -> mapOf(TALLY_ME to 2, "Maya" to 2)
        }
        val controls = RoomControls(
            room = room, mode = null, setup = if (hasOptions(room)) ({}) else null, rematch = {}, newGame = {}, switchTo = {},
            games = emptyList(), setupOpen = mutableStateOf(false), busy = false,
        )
        CompositionLocalProvider(
            LocalMatchSession provides MatchSession(tally, listOf(GameKind.LUDO, GameKind.WORDS, GameKind.SHIPS), {}),
            LocalRoomControls provides controls,
            LocalTonight provides tonightScore(room, tally),
            LocalTurnHints provides TurnHints(),
        ) { content() }
    }

    /** The card sits wholly above the dock, and the board keeps at least [minBoard] of height. */
    private fun assertFits(minBoard: Int = 220, board: Boolean = true) {
        val card = compose.onNodeWithTag("resultCard").getUnclippedBoundsInRoot()
        val dock = compose.onNodeWithTag("dock").getUnclippedBoundsInRoot()
        assertTrue("card ends at ${card.bottom}, the dock starts at ${dock.top}", card.bottom <= dock.top)
        if (board) {
            val area = compose.onNodeWithTag("boardArea").getUnclippedBoundsInRoot()
            assertTrue("the board has only ${area.height} of height", area.height >= minBoard.dp)
            assertTrue("the board ends at ${area.bottom}, the card starts at ${card.top}", area.bottom <= card.top)
        }
    }

    private val c4Won = listOf(".......", ".......", "...o...", "..mo...", ".moom..", "mmmmooo").joinToString("")
    private val c4Lost = listOf(".......", ".......", "...m...", "..om...", ".omm.o.", "ooooMmm".lowercase()).joinToString("")

    private fun c4(winner: String?) = room(
        GameKind.CONNECT4, winner, "four",
        TurnsView(
            turn = null, board = if (winner == "me") c4Won else c4Lost, winLine = if (winner == "me") listOf(35, 36, 37, 38) else listOf(35, 36, 37, 38),
            lastMoveByMe = winner == "me", lastCell = 38,
        ),
    )

    private fun tallyFor(winner: String?) = when (winner) {
        "me" -> mapOf(TALLY_ME to 3, "Maya" to 2)
        "opponent" -> mapOf(TALLY_ME to 2, "Maya" to 3)
        else -> mapOf(TALLY_ME to 2, "Maya" to 2)
    }

    /**
     * Draws [screen] for a finished [r] and, for a game that uses the card, checks it fits: the card
     * wholly above the dock and the game's board area at least [minBoard] dp tall (when [board]).
     */
    private fun result(
        name: String, r: RoomState, dark: Boolean = false, minBoard: Int = 220, board: Boolean = true,
        state: (BattleUiState) -> BattleUiState = { it },
        screen: @Composable (BattleUiState, BattleViewModel) -> Unit,
    ) {
        val s = state(BattleUiState(room = r, tally = tallyFor(r.winner)))
        shot("result-$name-${if (r.winner == "me") "won" else if (r.winner == null) "tied" else "lost"}", dark) {
            Session(r) { screen(s, vm()) }
        }
        assertFits(minBoard, board)
    }

    private fun c4Screen(winner: String?, dark: Boolean) = result("c4", c4(winner), dark) { s, v -> TurnGameScreen(s, v, onLeave = {}) }

    @Test fun c4WonLight() = c4Screen("me", false)
    @Test fun c4LostLight() = c4Screen("opponent", false)
    @Test fun c4WonDark() = c4Screen("me", true)
    @Test fun c4LostDark() = c4Screen("opponent", true)

    // ---- Turn games ---------------------------------------------------------------------------

    private fun turns(winner: String?, change: (TurnsView) -> TurnsView = { it }) = change(
        TurnsView(turn = null, board = null, winLine = emptyList(), lastMoveByMe = winner == "me", lastCell = null),
    )

    private fun dominoes(winner: String?) = room(
        GameKind.DOMINOES, winner, "points",
        turns(winner) {
            it.copy(
                dominoes = DominoView(
                    line = listOf(Tile(3, 4), Tile(4, 4), Tile(4, 1), Tile(1, 5), Tile(5, 2), Tile(2, 2), Tile(2, 0)), start = 3, hand = emptyList(),
                    counts = mapOf('m' to if (winner == "me") 0 else 3, 'o' to if (winner == "me") 3 else 0),
                    scores = mapOf('m' to if (winner == "me") 100 else 60, 'o' to if (winner == "me") 60 else 100),
                    partners = emptyList(), boneyard = 6, teams = false, draw = true, target = 100, roundNo = 4,
                    lastMove = DominoMove('m', Tile(2, 0), "right", false, false), lastRound = null,
                ),
            )
        },
    )

    @Test fun dominoesWon() = result("dominoes", dominoes("me"), minBoard = 100) { s, v -> DominoesGameScreen(s, v, onLeave = {}) }
    @Test fun dominoesLost() = result("dominoes", dominoes("opponent"), minBoard = 100) { s, v -> DominoesGameScreen(s, v, onLeave = {}) }

    private fun clash(winner: String?) = room(
        GameKind.CLASH, winner, "out",
        turns(winner) {
            it.copy(
                clash = ClashView(
                    top = "g4", colour = 'g', hand = emptyList(), counts = mapOf('m' to if (winner == "me") 0 else 3, 'o' to if (winner == "me") 3 else 0),
                    order = listOf('m', 'o'), direction = 1, deck = 41, drawn = false, lastMove = ClashMove(if (winner == "me") 'm' else 'o', "g4", 'g', 0, false, null),
                ),
            )
        },
    )

    @Test fun clashWon() = result("clash", clash("me"), board = false) { s, v -> ClashGameScreen(s, v, onLeave = {}) }
    @Test fun clashLost() = result("clash", clash("opponent"), board = false) { s, v -> ClashGameScreen(s, v, onLeave = {}) }

    private fun ludo(winner: String?) = room(
        GameKind.LUDO, winner, "home",
        turns(winner) {
            it.copy(
                ludo = LudoView(
                    colours = mapOf('m' to 0, 'o' to 2),
                    tokens = if (winner == "me") mapOf('m' to listOf(56, 56, 56, 56), 'o' to listOf(56, 20, 12, 30)) else mapOf('m' to listOf(56, 5, 20, 33), 'o' to listOf(56, 56, 56, 56)),
                    roll = null, movable = emptyList(), lastMove = null,
                ),
            )
        },
    )

    @Test fun ludoWon() = result("ludo", ludo("me")) { s, v -> LudoGameScreen(s, v, onLeave = {}) }
    @Test fun ludoLost() = result("ludo", ludo("opponent")) { s, v -> LudoGameScreen(s, v, onLeave = {}) }

    private fun balloon(winner: String?) = room(
        GameKind.BALLOON, winner, "points",
        turns(winner) {
            it.copy(
                balloon = BalloonView(
                    phase = "over", picker = 'o', pattern = "BALLOON", word = "BALLOON", guessed = listOf('A', 'B', 'E', 'L', 'O', 'N', 'S'), misses = 2, balloons = 7,
                    scores = mapOf('m' to if (winner == "me") 25 else 10, 'o' to if (winner == "me") 10 else 25), roundNo = 3, rounds = 3,
                    lastLetter = null, lastWho = null, lastRound = Triple("BALLOON", 'o', 'm'),
                ),
            )
        },
    )

    @Test fun balloonWon() = result("balloon", balloon("me"), board = false) { s, v -> BalloonGameScreen(s, v, onLeave = {}) }
    @Test fun balloonLost() = result("balloon", balloon("opponent"), board = false) { s, v -> BalloonGameScreen(s, v, onLeave = {}) }

    private fun blocks(winner: String?): RoomState {
        val size = 14
        val cells = CharArray(size * size) { '.' }
        fun put(mark: Char, vararg at: Pair<Int, Int>) = at.forEach { (x, y) -> cells[y * size + x] = mark }
        put('m', 4 to 4, 5 to 4, 6 to 4, 6 to 5, 7 to 5, 7 to 6, 8 to 6, 8 to 7, 9 to 7, 9 to 8, 3 to 5, 3 to 6, 2 to 6)
        put('o', 9 to 9, 10 to 9, 10 to 10, 11 to 10, 11 to 11, 12 to 11, 9 to 10, 8 to 10, 8 to 11, 7 to 11, 12 to 9)
        return room(
            GameKind.BLOCKS, winner, "squares",
            turns(winner) {
                it.copy(
                    blocks = BlocksView(
                        size = size, board = String(cells), starts = mapOf('m' to (4 to 4), 'o' to (9 to 9)),
                        left = mapOf('m' to emptyList(), 'o' to emptyList()),
                        placed = mapOf('m' to if (winner == "me") 40 else 31, 'o' to if (winner == "me") 31 else 40), lastWho = 'm', lastCells = emptyList(),
                    ),
                )
            },
        )
    }

    @Test fun blocksWon() = result("blocks", blocks("me")) { s, v -> BlocksGameScreen(s, v, onLeave = {}) }
    @Test fun blocksLost() = result("blocks", blocks("opponent")) { s, v -> BlocksGameScreen(s, v, onLeave = {}) }

    private fun memory(winner: String?): RoomState {
        val avatars = Avatar.entries.take(8)
        val faces = (avatars + avatars).mapIndexed { i, a -> com.pippop.game.memory.CardFace(a) }
        val owners = if (winner == "me") "mmmmmmmmmmoooooo".toList().shuffled(kotlin.random.Random(3)).joinToString("") else "oooooooooooommmm"
        return room(GameKind.MEMORY, winner, "pairs", turns(winner) { it.copy(faces = faces, owners = owners) })
    }

    @Test fun memoryWon() = result("memory", memory("me")) { s, v -> TurnGameScreen(s, v, onLeave = {}) }
    @Test fun memoryLost() = result("memory", memory("opponent")) { s, v -> TurnGameScreen(s, v, onLeave = {}) }

    private fun dots(winner: String?): RoomState {
        val shape = com.pippop.game.dots.DotsShape.from("4x4")
        val lines = shape.emptyLines().map { if (it == ' ') ' ' else if (it.hashCode() % 2 == 0) 'm' else 'o' }.joinToString("")
        val boxes = (0 until shape.boxes).joinToString("") { if ((it < 10) == (winner == "me")) "m" else "o" }
        return room(GameKind.DOTS, winner, "boxes", turns(winner) { it.copy(lines = lines.replace('.', 'm'), boxes = boxes, dotsBoard = "4x4") })
    }

    @Test fun dotsWon() = result("dots", dots("me")) { s, v -> TurnGameScreen(s, v, onLeave = {}) }
    @Test fun dotsLost() = result("dots", dots("opponent")) { s, v -> TurnGameScreen(s, v, onLeave = {}) }

    private fun ttt(winner: String?) = room(
        GameKind.TTT, winner, "three",
        turns(winner) {
            it.copy(
                board = if (winner == "me") "mmmoo.o.." else "ommmo.o..".let { b -> "oomm.m.o." }, winLine = if (winner == "me") listOf(0, 1, 2) else listOf(2, 5, 8),
                lastCell = if (winner == "me") 2 else 8, smallBoards = ".........", nextBoard = null,
            )
        },
    ) { it.copy(tttClassic = true) }

    @Test fun tttWon() = result("ttt", ttt("me")) { s, v -> TurnGameScreen(s, v, onLeave = {}) }
    @Test fun tttLost() = result("ttt", ttt("opponent")) { s, v -> TurnGameScreen(s, v, onLeave = {}) }

    // ---- Race games ---------------------------------------------------------------------------

    private fun sudoku(winner: String?): RoomState = room(
        GameKind.SUDOKU, winner, "solved",
        change = { it.copy(difficulty = com.pippop.data.Difficulty.EASY, me = it.me.copy(finishMs = 312_000, progress = if (winner == "me") 100 else 86)) },
    )

    private val sudokuBoard = com.pippop.game.BoardState(
        cells = List(81) { i -> (i / 9 * 3 + i / 27 + i % 9) % 9 + 1 }, givens = List(81) { it % 3 != 0 },
    )

    @Test fun sudokuWon() = result("sudoku", sudoku("me"), state = { it.copy(board = sudokuBoard) }) { s, v -> GameScreen(s, v, onLeave = {}) }
    @Test fun sudokuLost() = result("sudoku", sudoku("opponent"), state = { it.copy(board = sudokuBoard) }) { s, v -> GameScreen(s, v, onLeave = {}) }

    private fun words(winner: String?) = room(
        GameKind.WORDS, winner, "solved",
        change = {
            it.copy(
                words = WordsView(
                    tries = 6, mine = listOf(WordGuess("crane", "..y.g"), WordGuess("plant", if (winner == "me") "ggggg" else "gy..g")), theirs = emptyList(),
                    meDone = true, theyDone = true, answer = "plant",
                    others = listOf(OtherWords(listOf(WordGuess("slate", ".y..g"), WordGuess("plant", if (winner == "me") "gy..g" else "ggggg")), true)),
                ),
            )
        },
    )

    @Test fun wordsWon() = result("words", words("me")) { s, v -> WordRaceScreen(s, v, onLeave = {}) }
    @Test fun wordsLost() = result("words", words("opponent")) { s, v -> WordRaceScreen(s, v, onLeave = {}) }

    private val stroke = DrawStroke(floatArrayOf(0.2f, 0.7f, 0.4f, 0.3f, 0.6f, 0.7f, 0.8f, 0.3f), color = 0, width = 2)

    private fun draw(winner: String?) = room(
        GameKind.DRAW, winner, "score",
        change = {
            it.copy(
                drawRace = true,
                draw = DrawView(
                    turn = 6, turns = 6, drawing = null, word = null, hint = null, endsAt = null, strokes = listOf(stroke), guesses = emptyList(), score = 0,
                    lastTurn = DrawTurnResult("kite", true, 80, false, guesserName = "Maya", rights = listOf("Maya")), mode = "race",
                    scores = listOf(DrawScore("You", true, if (winner == "me") 340 else 180), DrawScore("Maya", false, if (winner == "me") 180 else 340)),
                ),
            )
        },
    )

    @Test fun drawWon() = result("draw", draw("me"), state = { it.copy(drawStrokes = listOf(stroke)) }) { s, v -> DrawGuessScreen(s, v, onLeave = {}) }
    @Test fun drawLost() = result("draw", draw("opponent"), state = { it.copy(drawStrokes = listOf(stroke)) }) { s, v -> DrawGuessScreen(s, v, onLeave = {}) }

    private val numberPuzzle = NumberCross.check(NumberCrossGenerator(Random(11)).generate(NumberLevel.MEDIUM))
    private val numberBoard = numberPuzzle.let { pz ->
        val blank = pz.blanks.toSet()
        NumberBoard(pz.w, pz.h, pz.grid.mapIndexed { i, t -> if (i in blank) "?" else t }, pz.blanks, pz.bank)
    }
    private val numberEntries = numberPuzzle.blanks.map { numberPuzzle.grid[it] }

    private fun numbers(winner: String?) = room(
        GameKind.NUMBERS, winner, "solved",
        change = { it.copy(numbers = NumbersView(NumberLevel.MEDIUM, numberBoard, numberEntries, null), me = it.me.copy(finishMs = 188_000, progress = 100)) },
    )

    @Test fun numbersWon() = result("numbers", numbers("me"), state = { it.copy(numberEntries = numberEntries) }) { s, v -> NumberCrossScreen(s, v, onLeave = {}) }
    @Test fun numbersLost() = result("numbers", numbers("opponent"), state = { it.copy(numberEntries = numberEntries.take(numberEntries.size - 3) + listOf("", "", "")) }) { s, v -> NumberCrossScreen(s, v, onLeave = {}) }

    private fun slide(winner: String?) = room(
        GameKind.SLIDE, winner, "solved",
        change = { it.copy(slide = SlideView(SlideLevel.EASY, 3, Slide.solved(3), emptyList()), me = it.me.copy(finishMs = 41_000)) },
    )

    @Test fun slideWon() = result("slide", slide("me")) { s, v -> SlideGameScreen(s, v, onLeave = {}) }
    @Test fun slideLost() = result("slide", slide("opponent")) { s, v -> SlideGameScreen(s, v, onLeave = {}) }

    private fun bingo(winner: String?) = room(
        GameKind.BINGO, winner, "bingo",
        change = {
            it.copy(
                bingo = BingoView(
                    card = (1..25).map { n -> n * 3 }, calls = (1..75).toList(), callMs = 3500, endsAt = null,
                    winCard = if (winner == "opponent") (1..25).map { n -> n * 3 + 1 } else null, line = if (winner == "me") listOf(0, 1, 2, 3, 4) else listOf(0, 6, 12, 18, 24),
                ),
                me = it.me.copy(finishMs = 52_000),
            )
        },
    )

    @Test fun bingoWon() = result("bingo", bingo("me"), board = false, state = { it.copy(bingoDabs = setOf(0, 1, 2, 3, 4, 7, 12, 20), bingoRound = 6) }) { s, v -> BingoGameScreen(s, v, onLeave = {}) }
    @Test fun bingoLost() = result("bingo", bingo("opponent"), board = false, state = { it.copy(bingoDabs = setOf(0, 1, 2, 7, 12, 20), bingoRound = 6) }) { s, v -> BingoGameScreen(s, v, onLeave = {}) }

    private fun code(winner: String?) = room(
        GameKind.CODE, winner, "solved",
        change = {
            it.copy(
                codeGame = CodeView(
                    phase = "guess", tries = 8, myCode = "rygb", theySet = true,
                    mine = listOf(CodeGuess("rpoc", 1, 1), CodeGuess("rygp", 3, 0)) + if (winner == "me") listOf(CodeGuess("rygb", 4, 0)) else emptyList(),
                    theirs = listOf(CodeGuess(null, 0, 2), CodeGuess(null, 2, 1)) + if (winner == "opponent") listOf(CodeGuess(null, 4, 0)) else emptyList(),
                    meDone = true, theyDone = true, theirCode = "pocr",
                ),
            )
        },
    )

    @Test fun codeWon() = result("code", code("me"), board = false) { s, v -> CodeGameScreen(s, v, onLeave = {}) }
    @Test fun codeLost() = result("code", code("opponent"), board = false) { s, v -> CodeGameScreen(s, v, onLeave = {}) }

    private fun grid(winner: String?) = room(
        GameKind.GRID, winner, "score",
        change = {
            it.copy(
                grid = GridView(
                    letters = "PLANTSEATMOREBIKE", endsAt = 1L, mine = listOf("plant", "seat", "more"), counts = listOf(4),
                    results = listOf(
                        GridResult(true, "You", if (winner == "me") 42 else 20, listOf(GridWord("plant", true, 10), GridWord("seat", false, 4), GridWord("more", false, 4))),
                        GridResult(false, "Maya", if (winner == "me") 20 else 42, listOf(GridWord("seat", false, 4), GridWord("bike", true, 10))),
                    ),
                ),
            )
        },
    )

    @Test fun gridWon() = result("grid", grid("me"), minBoard = 180) { s, v -> GridGameScreen(s, v, onLeave = {}) }
    @Test fun gridLost() = result("grid", grid("opponent"), minBoard = 180) { s, v -> GridGameScreen(s, v, onLeave = {}) }

    // ---- Games that keep the sheet (the board is the whole screen) ---------------------------------

    /** Draws a result that stays a sheet over the game: no card under the board. */
    private fun sheet(name: String, r: RoomState, screen: @Composable (BattleUiState, BattleViewModel) -> Unit) {
        val s = BattleUiState(room = r, tally = tallyFor(r.winner))
        shot("result-$name-${if (r.winner == "me") "won" else "lost"}", dark = false) { Session(r) { screen(s, vm()) } }
        compose.onNodeWithTag("resultCard").assertDoesNotExist()
    }

    private fun shipsRoom(winner: String?): RoomState {
        val fleet = listOf(Ship(1, 1, 5, true), Ship(0, 4, 4, false), Ship(3, 5, 3, true), Ship(6, 8, 3, true), Ship(8, 2, 2, false))
        val enemy = listOf(Ship(0, 0, 5, true), Ship(9, 2, 4, false), Ship(3, 3, 3, true), Ship(5, 7, 3, false), Ship(7, 1, 2, true))
        val hits = enemy.flatMap { sh -> sh.cells.map { Shot(it % 10, it / 10, true) } }
        return room(
            GameKind.SHIPS, winner, "sunk",
            ships = ShipsView(
                turn = null, myFleet = fleet, myShots = if (winner == "me") hits else hits.take(8), theirShots = if (winner == "me") listOf(Shot(0, 0, false)) else fleet.flatMap { sh -> sh.cells.map { Shot(it % 10, it / 10, true) } },
                sunkEnemy = if (winner == "me") enemy else emptyList(), enemyFleet = enemy, lastShotByMe = winner == "me",
            ),
        )
    }

    @Test fun shipsWon() = sheet("ships", shipsRoom("me")) { s, v -> ShipsGameScreen(s, v, onLeave = {}) }
    @Test fun shipsLost() = sheet("ships", shipsRoom("opponent")) { s, v -> ShipsGameScreen(s, v, onLeave = {}) }

    private fun hockey(winner: String?) = room(
        GameKind.HOCKEY, winner, "goals", change = { it.copy(hockey = HockeyScore(if (winner == "me") 7 else 4, if (winner == "me") 4 else 7, 7)) },
    )

    @Test fun hockeyWon() = sheet("hockey", hockey("me")) { s, v -> HockeyScreen(s, v, onLeave = {}) }
    @Test fun hockeyLost() = sheet("hockey", hockey("opponent")) { s, v -> HockeyScreen(s, v, onLeave = {}) }

    private fun golf(winner: String?) = room(
        GameKind.GOLF, winner, "strokes",
        turns(winner) {
            it.copy(
                golf = GolfView(
                    hole = 8, holes = 9,
                    strokes = mapOf('m' to listOf(3, 5, 8, 4, 2, 4, 3, 5, 4), 'o' to listOf(4, 6, 6, 3, 3, 4, 5, 4, 3)),
                    balls = mapOf('m' to null, 'o' to null), done = mapOf('m' to true, 'o' to true), last = null,
                ),
            )
        },
    ) { it.copy(golfLevel = com.pippop.game.golf.GolfLevel.entries.first()) }

    @Test fun golfWon() = sheet("golf", golf("me")) { s, v -> GolfGameScreen(s, v, onLeave = {}) }
    @Test fun golfLost() = sheet("golf", golf("opponent")) { s, v -> GolfGameScreen(s, v, onLeave = {}) }

    // ---- On your own: the same card without the score row -----------------------------------------

    private fun solo(name: String, board: Boolean = true, minBoard: Int = 220, screen: @Composable () -> Unit) {
        shot("result-solo-$name", dark = false) { screen() }
        assertFits(minBoard, board)
        compose.onNodeWithTag("resultCard").assertExists()
    }

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun soloC4Won() = solo("c4-won") {
        SoloConnectFourScreen(SoloC4UiState(com.pippop.game.connect4.C4Level.entries.first(), c4Won, false, "me", listOf(35, 36, 37, 38), 38), SoloConnectFourViewModel(), onHome = {})
    }

    @Test fun soloC4Lost() = solo("c4-lost") {
        SoloConnectFourScreen(SoloC4UiState(com.pippop.game.connect4.C4Level.entries.first(), c4Lost, false, "computer", listOf(35, 36, 37, 38), 38), SoloConnectFourViewModel(), onHome = {})
    }

    @Test fun soloDotsWon() = solo("dots-won") {
        val shape = com.pippop.game.dots.DotsShape.from("4x4")
        SoloDotsScreen(
            SoloDotsUiState(shape.emptyLines().replace('.', 'm'), (0 until shape.boxes).joinToString("") { if (it < 10) "m" else "o" }, "4x4", null, false, "me"),
            SoloDotsViewModel(), onHome = {},
        )
    }

    @Test fun soloTttLost() = solo("ttt-lost") {
        SoloTttScreen(
            SoloTttUiState(com.pippop.game.ttt.TttPosition("oomm.m.o.", ".", -1), 8, listOf(2, 5, 8), false, "computer"), SoloTttViewModel(), onHome = {},
        )
    }

    @Test fun soloMemoryWon() = solo("memory-won") {
        val avatars = Avatar.entries.take(8)
        SoloMemoryScreen(
            SoloMemoryUiState(
                faces = (avatars + avatars).map { com.pippop.game.memory.CardFace(it) }, found = "m".repeat(16), cards = 16, flips = 23, startedAt = 0, finishedMs = 95_000, bestFlips = 21,
            ),
            SoloMemoryViewModel(), onHome = {},
        )
    }

    @Test fun soloSlideWon() = solo("slide-won") {
        SoloSlideScreen(SoloSlideUiState(SlideLevel.EASY, Slide.solved(3), 31, 0, 41_000, mapOf(SlideLevel.EASY to 38_000L)), SoloSlideViewModel(), onHome = {})
    }

    @Test fun soloNumbersWon() = solo("numbers-won") {
        SoloNumbersScreen(
            SoloNumbersUiState(numberPuzzle, NumberLevel.MEDIUM, numberEntries, null, 0, 188_000, emptyMap()), SoloNumbersViewModel(), onHome = {},
        )
    }

    @Test fun soloWordsWon() = solo("words-won") {
        SoloWordsScreen(
            SoloWordsUiState(daily = false, answer = "plant", guesses = listOf(WordGuess("crane", "..y.g"), WordGuess("plant", "ggggg"))), SoloWordsViewModel(app), onHome = {},
        )
    }

    @Test fun soloWordsLost() = solo("words-lost") {
        SoloWordsScreen(
            SoloWordsUiState(daily = true, answer = "plant", guesses = List(6) { WordGuess("crane", "..y.g") }), SoloWordsViewModel(app), onHome = {},
        )
    }

    private val soloDomino = DominoView(
        line = listOf(Tile(3, 4), Tile(4, 4), Tile(4, 1), Tile(1, 5), Tile(5, 2), Tile(2, 2)), start = 2, hand = emptyList(),
        counts = mapOf('m' to 0, 'o' to 3), scores = mapOf('m' to 100, 'o' to 40), partners = emptyList(), boneyard = 6, teams = false, draw = true, target = 100, roundNo = 3,
        lastMove = null, lastRound = null,
    )

    @Test fun soloDominoesWon() = solo("dominoes-won", minBoard = 100) {
        SoloDominoesScreen(SoloDominoesUiState(soloDomino, 1, com.pippop.game.dominoes.DominoOptions(), 'm', "me"), SoloDominoesViewModel(), onHome = {})
    }

    @Test fun soloClashLost() = solo("clash-lost", board = false) {
        SoloClashScreen(
            SoloClashUiState(ClashView("g4", 'g', emptyList(), mapOf('m' to 3, 'o' to 0), listOf('m', 'o'), 1, 41, false, null), 1, 'o', "computer"), SoloClashViewModel(), onHome = {},
        )
    }

    @Test fun soloLudoWon() = solo("ludo-won") {
        SoloLudoScreen(
            SoloLudoUiState(LudoView(mapOf('m' to 0, 'o' to 2), mapOf('m' to listOf(56, 56, 56, 56), 'o' to listOf(56, 20, 12, 30)), null, emptyList(), null), 1, 'm', "me"),
            SoloLudoViewModel(), onHome = {},
        )
    }

    // ---- Dark, faces and the open "Another game" ---------------------------------------------------

    @Test fun dominoesLostDark() = result("dominoes", dominoes("opponent"), dark = true, minBoard = 100) { s, v -> DominoesGameScreen(s, v, onLeave = {}) }

    @Test fun bingoLostDark() = result("bingo", bingo("opponent"), dark = true, board = false, state = { it.copy(bingoDabs = setOf(0, 1, 2, 7, 12, 20), bingoRound = 6) }) { s, v -> BingoGameScreen(s, v, onLeave = {}) }

    @Test fun soloC4LostDark() {
        shot("result-solo-c4-lost", dark = true) {
            SoloConnectFourScreen(SoloC4UiState(com.pippop.game.connect4.C4Level.entries.first(), c4Lost, false, "computer", listOf(35, 36, 37, 38), 38), SoloConnectFourViewModel(), onHome = {})
        }
        assertFits()
    }

    /** A drawn Connect Four: no crown (the game's tile instead), and no "+1". */
    @Test fun c4Tied() {
        val full = "mooomoo" + "ommmoom" + "mooomoo" + "ommmoom" + "mooomoo" + "ommmoom"
        val r = room(GameKind.CONNECT4, null, "draw", TurnsView(turn = null, board = full, winLine = emptyList(), lastMoveByMe = true, lastCell = 41))
        result("c4", r) { s, v -> TurnGameScreen(s, v, onLeave = {}) }
        compose.onNodeWithText("+1").assertDoesNotExist()
    }

    /** Three players: the winner's face, and no tonight row. */
    @Test fun ludoThreeLost() {
        val others = listOf(player("Maya", Avatar.PANDA, GameKind.LUDO, true), player("Leo", Avatar.OWL, GameKind.LUDO, true))
        val r = room(
            GameKind.LUDO, "opponent", "home",
            turns("opponent") {
                it.copy(
                    ludo = LudoView(
                        colours = mapOf('m' to 0, 'o' to 2, 'p' to 1),
                        tokens = mapOf('m' to listOf(56, 5, 20, 33), 'o' to listOf(56, 40, 12, 30), 'p' to listOf(56, 56, 56, 56)), roll = null, movable = emptyList(), lastMove = null,
                    ),
                )
            },
            others = others, change = { it.copy(winnerName = "Leo") },
        )
        result("ludo3", r) { s, v -> LudoGameScreen(s, v, onLeave = {}) }
        compose.onNodeWithText("TONIGHT").assertDoesNotExist() // neither the title pill nor the card's row
    }

    private fun sudokuTogether(won: Boolean) = room(
        GameKind.SUDOKU, if (won) "me" else "opponent", if (won) "solvedTogether" else "outOfLives",
        change = { it.copy(sudokuTogether = true, difficulty = com.pippop.data.Difficulty.EASY, me = it.me.copy(finishMs = 412_000), winnerName = null) },
    )

    @Test fun sudokuTogetherWon() = result("sudoku-together", sudokuTogether(true), state = { it.copy(board = sudokuBoard) }) { s, v -> GameScreen(s, v, onLeave = {}) }
    @Test fun sudokuTogetherLost() = result("sudoku-together", sudokuTogether(false), state = { it.copy(board = sudokuBoard) }) { s, v -> GameScreen(s, v, onLeave = {}) }

    /** The card keeps "Another game" (which opens the sheet, see ScreenshotAnotherGameTest), above the dock with the board complete. */
    @Test fun anotherGameOnTheCard() {
        result("c4-another", c4("me")) { s, v -> TurnGameScreen(s, v, onLeave = {}) }
        compose.onNodeWithText("Another game").assertExists()
        // "Back to games" is at the bottom of the sheet, not on the card.
        compose.onNodeWithText("Back to games").assertDoesNotExist()
    }
}
