package com.arcade.sudoku.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.DrawStroke
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.NumberBoard
import com.arcade.sudoku.data.WordGuess
import com.arcade.sudoku.game.BoardState
import com.arcade.sudoku.game.dominoes.Tile
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.LineHeight
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Space
import com.arcade.sudoku.ui.theme.TextSize

/** One step of a game's tutorial: what to know, over the real board set up to show it. */
class TutorialStep(val title: String, val body: String, val board: @Composable (Modifier) -> Unit)

/**
 * A game's tutorial as a full-screen dialog: a few steps on the game's own board, with Skip,
 * Back and Next. It plays the first time a game is opened; Rules → "Show me how" plays it again.
 */
@Composable
fun TutorialDialog(kind: GameKind, onDone: () -> Unit) {
    val steps = tutorialFor(kind)
    if (steps == null) {
        LaunchedEffect(kind) { onDone() }
        return
    }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppTheme.palette.background, modifier = Modifier.fillMaxSize()) {
            TutorialScreen(kind, steps, onDone)
        }
    }
}

/** Whether [kind] has a tutorial yet. */
fun hasTutorial(kind: GameKind) = tutorialFor(kind) != null

@Composable
private fun TutorialScreen(kind: GameKind, steps: List<TutorialStep>, onDone: () -> Unit) {
    val p = AppTheme.palette
    var at by rememberSaveable(kind) { mutableIntStateOf(0) }
    val step = steps[at.coerceIn(0, steps.size - 1)]
    val last = at == steps.size - 1
    Column(Modifier.fillMaxSize().padding(horizontal = Space.L, vertical = Space.M), verticalArrangement = Arrangement.spacedBy(Space.M)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = { at = steps.size - 1 }) { Text("Skip", color = p.primaryText, fontWeight = FontWeight.Bold) }
        }
        BoardArea(1f) { size -> step.board(size) }
        Text(step.title, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
        Text(step.body, color = p.muted, fontSize = TextSize.BodyL, modifier = Modifier.height(72.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
            steps.indices.forEach { i ->
                Box(Modifier.size(if (i == at) 22.dp else 8.dp, 8.dp).clip(Shapes.Full).background(if (i == at) p.ink else p.hairline))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            SecondaryButton("Back", { at = (at - 1).coerceAtLeast(0) }, Modifier.weight(1f), enabled = at > 0)
            PrimaryButton(if (last) "Start the game" else "Next", { if (last) onDone() else at++ }, Modifier.weight(1.4f))
        }
    }
}

/** The tutorial for [kind], or null for games that don't have one yet. */
fun tutorialFor(kind: GameKind): List<TutorialStep>? = when (kind) {
    GameKind.SUDOKU -> sudoku()
    GameKind.SHIPS -> ships()
    GameKind.CONNECT4 -> connectFour()
    GameKind.DOTS -> dots()
    GameKind.MEMORY -> memory()
    GameKind.WORDS -> words()
    GameKind.DRAW -> draw()
    GameKind.TTT -> ttt()
    GameKind.DOMINOES -> dominoes()
    GameKind.NUMBERS -> numbers()
    else -> null
}

// ---- The first ten games -----------------------------------------------------------------

private const val SUDOKU_PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

private fun sudoku(): List<TutorialStep> {
    val start = BoardState.fromPuzzle(SUDOKU_PUZZLE)
    val placed = start.place(2, 4)
    val clash = placed.place(3, 5)
    fun grid(b: BoardState, selected: Int?) = @Composable { m: Modifier ->
        SudokuGrid(b, b.conflicts(), selected, hidden = false, solution = null, onSelect = {}, modifier = m)
    }
    return listOf(
        TutorialStep("Fill the grid", "Every row, column and 3 × 3 box gets the digits 1 to 9, once each.", grid(start, null)),
        TutorialStep("Tap a square", "Then tap a digit under the grid to put it there.", grid(start, 2)),
        TutorialStep("The 4 goes in", "Notes mode lets you pencil in the digits a square could be.", grid(placed, 2)),
        TutorialStep("Clashes turn red", "This row has a 5 already, so a second 5 shows red. Change it.", grid(clash, 3)),
        TutorialStep("Race your partner", "You both get the same puzzle at the same moment. The first correct grid wins.", grid(placed, null)),
    )
}

private val TutorialFleet = listOf(Ship(1, 1, 5, true), Ship(7, 3, 4, false), Ship(1, 4, 3, true), Ship(4, 7, 3, false), Ship(1, 8, 2, true))

private fun ships(): List<TutorialStep> {
    val miss = listOf(Shot(3, 6, false))
    val hits = miss + Shot(7, 3, true) + Shot(7, 4, true)
    val sunk = hits + Shot(7, 5, true) + Shot(7, 6, true)
    fun sea(ships: List<Ship>, shots: List<Shot>, down: List<Ship> = emptyList()) = @Composable { m: Modifier ->
        ShipsSea(ships = ships, shots = shots, sunk = down, lastShot = shots.lastOrNull(), modifier = m)
    }
    return listOf(
        TutorialStep("Hide your fleet", "Five ships on your sea. Tap a ship, then the square it goes to; Turn rotates it.", sea(TutorialFleet, emptyList())),
        TutorialStep("Fire at their sea", "Tap a square of the enemy sea. A splash is a miss, and their turn comes.", sea(emptyList(), miss)),
        TutorialStep("A hit fires again", "Keep going while you hit. Ships lie in a straight line, so try next to a hit.", sea(emptyList(), hits)),
        TutorialStep("Sink them all", "Every square of a ship hit sinks it. Sink the whole fleet before they sink yours.", sea(emptyList(), sunk, listOf(TutorialFleet[1]))),
    )
}

private fun c4(vararg discs: Pair<Int, Char>): String {
    val b = CharArray(42) { '.' }
    for ((cell, mark) in discs) b[cell] = mark
    return String(b)
}

private fun connectFour(): List<TutorialStep> {
    fun board(b: String, last: Int?, win: List<Int> = emptyList()) = @Composable { m: Modifier -> ConnectFourBoard(b, win, last, null, m) }
    val first = c4(38 to 'm')
    val second = c4(38 to 'm', 39 to 'o')
    val four = c4(35 to 'm', 36 to 'm', 37 to 'm', 38 to 'm', 39 to 'o', 32 to 'o', 31 to 'o')
    return listOf(
        TutorialStep("You're red", "Take turns dropping discs into the seven columns.", board(c4(), null)),
        TutorialStep("Slide to a column", "Let go and your disc falls to the lowest free space.", board(first, 38)),
        TutorialStep("Then they play", "You'll feel a buzz and see \"Your turn\" when it's you again.", board(second, 39)),
        TutorialStep("Four in a row wins", "Across, up and down, or on a slant.", board(four, 38, listOf(35, 36, 37, 38))),
    )
}

private fun dots(): List<TutorialStep> {
    fun lines(vararg drawn: Pair<Int, Char>): String {
        val l = CharArray(40) { '.' }
        for ((i, mark) in drawn) l[i] = mark
        return String(l)
    }
    fun board(l: String, boxes: String, last: Int?) = @Composable { m: Modifier -> DotsBoard(l, boxes, last, emptyMap(), null, m) }
    val empty = ".".repeat(16)
    val three = lines(0 to 'o', 4 to 'm', 20 to 'o')
    val closed = lines(0 to 'o', 4 to 'm', 20 to 'o', 21 to 'm')
    return listOf(
        TutorialStep("Join the dots", "Take turns drawing one line between two dots next to each other.", board(lines(), empty, null)),
        TutorialStep("Tap, then tap again", "A tap lights up the nearest line; a second tap on it draws it.", board(three, empty, 4)),
        TutorialStep("Close a box, go again", "Draw a box's fourth side and it's yours, and you draw again.", board(closed, "m" + ".".repeat(15), 21)),
        TutorialStep("Most boxes wins", "Try not to draw a box's third side: that hands it to the next player.", board(closed, "m" + ".".repeat(15), null)),
    )
}

private fun memory(): List<TutorialStep> {
    val none = List<Avatar?>(16) { null }
    fun table(faces: List<Avatar?>, owners: String) = @Composable { m: Modifier -> MemoryBoard(faces, owners, null, modifier = m) }
    val two = none.toMutableList().also { it[0] = Avatar.FOX; it[5] = Avatar.CAT }
    val pair = none.toMutableList().also { it[0] = Avatar.FOX; it[9] = Avatar.FOX }
    return listOf(
        TutorialStep("Find the pairs", "Sixteen cards, face down: eight pairs of pictures.", table(none, ".".repeat(16))),
        TutorialStep("Turn two cards", "Not a pair? They turn back and it's the next player's go. Remember where they were.", table(two, ".".repeat(16))),
        TutorialStep("A pair is yours", "Keep it and turn two more. Most pairs wins.", table(pair, "m" + ".".repeat(8) + "m" + ".".repeat(6))),
    )
}

private fun words(): List<TutorialStep> {
    fun grid(guesses: List<WordGuess>, input: String = "") = @Composable { m: Modifier -> WordGrid(guesses, input, 6, 5, m) }
    val one = listOf(WordGuess("crane", ".yy.."))
    val two = one + WordGuess("north", "..gg.")
    val solved = two + WordGuess("party", "ggggg")
    return listOf(
        TutorialStep("Guess the word", "Everyone gets the same word. Type a guess and tap enter.", grid(emptyList(), "cra")),
        TutorialStep("Read the colours", "Green: right letter, right place. Yellow: in the word, somewhere else. Grey: not in it.", grid(one)),
        TutorialStep("Narrow it down", "Each guess must be a real word. Use what the colours told you.", grid(two)),
        TutorialStep("First to solve wins", "All green. Fewer guesses beats more, and faster beats slower.", grid(solved)),
    )
}

private fun draw(): List<TutorialStep> {
    fun stroke(vararg xy: Float) = DrawStroke(xy, 0, 1)
    val walls = listOf(stroke(0.25f, 0.5f, 0.25f, 0.85f, 0.75f, 0.85f, 0.75f, 0.5f, 0.25f, 0.5f))
    val house = walls + stroke(0.2f, 0.52f, 0.5f, 0.2f, 0.8f, 0.52f) + stroke(0.45f, 0.85f, 0.45f, 0.66f, 0.56f, 0.66f, 0.56f, 0.85f)
    fun pad(strokes: List<DrawStroke>) = @Composable { m: Modifier -> DrawingPad(strokes, 0, 1, null, m) }
    return listOf(
        TutorialStep("One draws, the others guess", "Turns go round: everyone draws a word and guesses the others'.", pad(emptyList())),
        TutorialStep("Draw your word", "Only you see it. No letters or numbers: just a picture.", pad(walls)),
        TutorialStep("Guess it quickly", "Type a guess. The sooner someone gets it, the more points for the team.", pad(house)),
    )
}

private fun ttt(): List<TutorialStep> {
    fun classic(b: String, last: Int?, win: List<Int> = emptyList()) = @Composable { m: Modifier -> TttBoard(b, ".", null, win, last, false, null, m) }
    fun ultimate(b: String, boards: String, next: Int?, last: Int?) = @Composable { m: Modifier -> TttBoard(b, boards, next, emptyList(), last, true, null, m) }
    val big = CharArray(81) { '.' }.also { it[4] = 'm' }
    return listOf(
        TutorialStep("Three in a row", "Normal Tic-Tac-Toe: take turns, and three in a row wins.", classic("m.o.m.o.m", 8, listOf(0, 4, 8))),
        TutorialStep("Ultimate: nine boards", "Each square of the big board is a small board. Win three small boards in a row.", ultimate(".".repeat(81), ".".repeat(9), null, null)),
        TutorialStep("Your square sends them", "Playing the middle of a small board sends them to the middle board: it glows.", ultimate(String(big), ".".repeat(9), 4, 4)),
        TutorialStep("Pick when you like", "Choose Normal or Ultimate in the lobby before you start.", classic(".".repeat(9), null)),
    )
}

private fun dominoes(): List<TutorialStep> {
    fun line(tiles: List<Tile>, start: Int, highlight: Int?) = @Composable { m: Modifier -> DominoLine(tiles, start, highlight, m) }
    val one = listOf(Tile(6, 6))
    val three = listOf(Tile(3, 6), Tile(6, 6), Tile(6, 1))
    return listOf(
        TutorialStep("The highest double starts", "Seven tiles each. Whoever holds the highest double puts it down first, crosswise.", line(one, 0, 0)),
        TutorialStep("Match an end", "Play a tile whose number matches one end of the line. Tap a tile in your hand to play it.", line(three, 1, 2)),
        TutorialStep("Can't play?", "Draw from the pile until you can, or pass when it's empty.", line(three, 1, null)),
        TutorialStep("Play out your hand", "Empty your hand first and score the pips left in the others' hands.", line(three, 1, null)),
    )
}

private val TutorialNumbers = NumberBoard(
    w = 5, h = 5,
    cells = listOf(
        "3", "+", "?", "=", "7",
        "-", "", "", "", "",
        "?", "", "", "", "",
        "=", "", "", "", "",
        "2", "", "", "", "",
    ),
    blanks = listOf(2, 10),
    bank = listOf(4, 1),
)

private fun numbers(): List<TutorialStep> {
    fun grid(entries: List<String>, selected: Int?) = @Composable { m: Modifier -> NumberGrid(TutorialNumbers, entries, selected, false, {}, m) }
    return listOf(
        TutorialStep("Fill the blanks", "A crossword of sums: across and down, each line is one sum.", grid(listOf("", ""), null)),
        TutorialStep("Tap a blank, then a number", "The numbers to use are under the grid, each once.", grid(listOf("", ""), 0)),
        TutorialStep("Right sums turn green", "3 + 4 = 7 holds. A wrong one turns red.", grid(listOf("4", ""), 1)),
        TutorialStep("Every sum must hold", "3 − 1 = 2 too. Fill the grid with every sum right.", grid(listOf("4", "1"), null)),
    )
}
