package com.arcade.sudoku.ui.screens

import android.view.Gravity
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.CodeGuess
import com.arcade.sudoku.data.DrawStroke
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.NumberBoard
import com.arcade.sudoku.data.WordGuess
import com.arcade.sudoku.game.BoardState
import com.arcade.sudoku.game.blocks.Blocks
import com.arcade.sudoku.game.blocks.BlocksView
import com.arcade.sudoku.game.dominoes.Tile
import com.arcade.sudoku.game.golf.Golf
import com.arcade.sudoku.game.golf.GolfView
import com.arcade.sudoku.game.golf.Pt
import com.arcade.sudoku.game.hockey.Body
import com.arcade.sudoku.game.hockey.Hockey
import com.arcade.sudoku.game.hockey.HockeyFrame
import com.arcade.sudoku.game.ludo.Ludo
import com.arcade.sudoku.game.ludo.LudoMove
import com.arcade.sudoku.game.ludo.LudoView
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import com.arcade.sudoku.game.slide.Slide
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.LineHeight
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Space
import com.arcade.sudoku.ui.theme.TextSize

/** One step of a game's tutorial: what to know, over the real board set up to show it ([aspect] wide for each 1 tall). */
class TutorialStep(val title: String, val body: String, val board: @Composable (Modifier) -> Unit, val aspect: Float = 1f)

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
    // Full screen on every phone. A dialog's window otherwise takes whatever height the phone
    // gives it next to the status and gesture bars, which differs from maker to maker, so Back
    // and Next ran off the bottom on some phones. The window is set to exactly the screen and
    // drawn under the bars; the steps keep inside the safe area (see TutorialScreen).
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window?.setGravity(Gravity.FILL)
        }
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
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Space.L, vertical = Space.M),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = { at = steps.size - 1 }) { Text("Skip", color = p.primaryText, fontWeight = FontWeight.Bold) }
        }
        BoardArea(step.aspect) { size -> step.board(size) }
        Text(step.title, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
        // Room for three lines, so the board doesn't jump between steps; more with a large font setting.
        Text(step.body, color = p.muted, fontSize = TextSize.BodyL, modifier = Modifier.heightIn(min = 72.dp))
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

/** The tutorial for [kind], or null for a game without one. */
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
    GameKind.HOCKEY -> hockey()
    GameKind.CLASH -> clash()
    GameKind.LUDO -> ludo()
    GameKind.GRID -> grid()
    GameKind.BALLOON -> balloon()
    GameKind.CODE -> code()
    GameKind.SLIDE -> slide()
    GameKind.BINGO -> bingo()
    GameKind.GOLF -> golf()
    GameKind.BLOCKS -> blocks()
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
        TutorialStep("Seven columns", "You're red, they're yellow. Take turns dropping a disc into a column.", board(c4(), null)),
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

// ---- The other ten -----------------------------------------------------------------------

private fun hockey(): List<TutorialStep> {
    fun rink(f: HockeyFrame) = @Composable { m: Modifier -> HockeyRink(f, MyDisc, TheirDisc, null, m) }
    val aspect = Hockey.W / Hockey.H
    val start = HockeyFrame.start()
    val hit = HockeyFrame(puck = Body(0.56f, 1.0f, -0.2f, -1.4f), bottom = Body(0.52f, 1.13f), top = Body(0.5f, 0.24f))
    val goal = HockeyFrame(puck = Body(0.5f, 0.04f), bottom = Body(0.5f, 1.2f), top = Body(0.28f, 0.2f), bottomScore = 1)
    return listOf(
        TutorialStep("Your paddle is red", "It's at the bottom and stays in your half. Theirs is at the top.", rink(start), aspect),
        TutorialStep("Drag to hit the puck", "The puck takes your paddle's speed: swing through it to shoot hard.", rink(hit), aspect),
        TutorialStep("Score at the far end", "The puck bounces off the sides. Get it into their goal.", rink(goal), aspect),
        TutorialStep("First to ${Hockey.GOALS} wins", "Against your partner it's live, so play nearby where the phones talk directly.", rink(start.copy(bottomScore = 6, topScore = 4)), aspect),
    )
}

/** Colour Clash's table: the pile and the card on top, and a hand under it. */
@Composable
private fun ClashScene(top: String, hand: List<String>, named: Char? = null, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth / 6, maxHeight / 4.2f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(w * 0.4f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(w * 0.4f), verticalAlignment = Alignment.CenterVertically) {
                ClashBack(w)
                ClashCard(top, w * 1.5f, named = named)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(w * 0.15f)) { hand.forEach { ClashCard(it, w) } }
        }
    }
}

private fun clash(): List<TutorialStep> {
    fun table(top: String, hand: List<String>, named: Char? = null) = @Composable { m: Modifier -> ClashScene(top, hand, named, modifier = m) }
    return listOf(
        TutorialStep("Match the top card", "Play a card of the same colour, or the same number or symbol.", table("r5", listOf("r2", "b5", "g7", "y1"))),
        TutorialStep("Action cards", "Skip misses the next player, reverse turns the order round, +2 makes them draw two.", table("rS", listOf("rR", "rD", "b5"))),
        TutorialStep("Wilds go on anything", "You name the colour. +4 also makes the next player draw four.", table("W", listOf("b2", "F"), named = 'b')),
        TutorialStep("Can't play? Draw", "Tap the pile. Play the card if it fits, or keep it.", table("g3", listOf("r8", "y6"))),
        TutorialStep("Play your last card", "Everyone starts with seven. The first to empty their hand wins.", table("b2", listOf("b9"))),
    )
}

private fun ludo(): List<TutorialStep> {
    val yard = listOf(-1, -1, -1, -1)
    fun board(mine: List<Int>, theirs: List<Int> = yard, roll: Int? = null, movable: List<Int> = emptyList(), move: LudoMove? = null) = @Composable { m: Modifier ->
        LudoBoard(LudoView(mapOf('m' to 0, 'o' to 2), mapOf('m' to mine, 'o' to theirs), roll, movable, move), null, m)
    }
    val out = LudoMove('m', 6, 0, -1, 0, emptyList(), stuck = false, thirdSix = false)
    return listOf(
        TutorialStep("Four tokens each", "Yours wait in your corner. Roll the die on your turn.", board(yard)),
        TutorialStep("A 6 brings one out", "It goes onto your start square, and a 6 rolls again.", board(listOf(0, -1, -1, -1), move = out)),
        TutorialStep("Tap a token to move it", "The tokens that can move bob up and down. Move round the board clockwise.", board(listOf(5, 0, -1, -1), roll = 4, movable = listOf(0, 1))),
        TutorialStep("Land on them to send them back", "Their token goes back to their corner. Start squares and stars are safe.", board(listOf(27, 5, -1, -1), listOf(4, -1, -1, -1), roll = 3, movable = listOf(0, 1))),
        TutorialStep("Get all four home", "Up your coloured column to the centre. You need the exact roll.", board(listOf(Ludo.HOME, Ludo.HOME, Ludo.HOME, 53), listOf(30, 12, -1, -1))),
    )
}

private fun grid(): List<TutorialStep> {
    fun board(letters: String) = @Composable { m: Modifier -> LetterGridBoard(letters, false, {}, m) }
    val letters = "STARPEONLIDECKUM"
    return listOf(
        TutorialStep("Sixteen letters, two minutes", "Everyone gets the same grid. Find as many words as you can.", board(letters)),
        TutorialStep("Drag through the letters", "Slide across, up and down or corner to corner, each letter once. Lift to send it.", board(letters)),
        TutorialStep("Longer words score more", "Short words score 1, an 8-letter word 11. A word nobody else found scores double.", board(letters)),
    )
}

private fun balloon(): List<TutorialStep> {
    fun round(pattern: String, popped: Int) = @Composable { m: Modifier ->
        Column(m, verticalArrangement = Arrangement.SpaceEvenly) {
            BalloonRow(7, popped)
            WordTiles(pattern)
        }
    }
    return listOf(
        TutorialStep("One picks a secret word", "Any word works: names and in-jokes too. The others guess it.", round("_____", 0)),
        TutorialStep("Guess a letter", "A right letter fills in every place it's in, and scores a point for each.", round("_A___", 0)),
        TutorialStep("A wrong letter pops a balloon", "If all seven pop, the picker scores 5.", round("_A_T_", 3)),
        TutorialStep("Fill in the last letter", "That scores 3 more. Everyone picks once; most points wins.", round("PARTY", 3)),
    )
}

private fun code(): List<TutorialStep> {
    fun rows(guesses: List<CodeGuess>) = @Composable { m: Modifier -> Box(m, contentAlignment = Alignment.Center) { GuessRows(guesses) } }
    val one = listOf(CodeGuess("rrgg", 1, 1))
    val two = one + CodeGuess("rgoo", 2, 0)
    val three = two + CodeGuess("rgyb", 2, 2)
    return listOf(
        TutorialStep("Hide a code, crack theirs", "Four coloured pegs; colours can repeat. You each crack the other's.", rows(one)),
        TutorialStep("Read the pins", "Red: right colour, right place. White: right colour, wrong place.", rows(two)),
        TutorialStep("Close in on it", "Two red, two white: all four colours are right, two are swapped.", rows(three)),
        TutorialStep("Fewest guesses wins", "Four red cracks it. On a tie the faster wins. Ten guesses each.", rows(three + CodeGuess("rgby", 4, 0))),
    )
}

private fun slide(): List<TutorialStep> {
    fun board(tiles: List<Int>) = @Composable { m: Modifier -> SlideBoard(tiles, 3, false, {}, m) }
    return listOf(
        TutorialStep("Put the tiles in order", "1 to 8, with the gap last. Tiles in their place turn orange.", board(listOf(0, 2, 3, 1, 4, 6, 7, 5, 8))),
        TutorialStep("Tap or swipe", "Tap a tile in line with the gap, or swipe anywhere: the tile next to the gap slides in.", board(listOf(1, 2, 3, 0, 4, 6, 7, 5, 8))),
        TutorialStep("Nearly there", "Work row by row from the top.", board(listOf(1, 2, 3, 4, 5, 6, 7, 0, 8))),
        TutorialStep("First to finish wins", "Everyone gets the same shuffle, and it can always be solved.", board(Slide.solved(3))),
    )
}

private fun bingo(): List<TutorialStep> {
    val card = List(25) { i -> if (i == 12) 0 else (i % 5) * 15 + listOf(3, 7, 10, 12, 14)[i / 5] }
    fun card(dabs: Set<Int>, line: List<Int>? = null) = @Composable { m: Modifier -> BingoCard(card, dabs, line, false, {}, m) }
    val row = listOf(10, 11, 12, 13, 14)
    return listOf(
        TutorialStep("Your card", "24 numbers round a free star. B is 1 to 15, I 16 to 30, and so on up to O 75.", card(emptySet())),
        TutorialStep("Dab the called numbers", "A number is called every few seconds. Tap it on your card if you have it.", card(setOf(0, 6, 11))),
        TutorialStep("Fill a line", "A row, column or diagonal; the star counts. The Bingo button lights up.", card(setOf(0, 6, 10, 11, 13, 14), row)),
        TutorialStep("Press Bingo first", "The first to press it wins.", card(setOf(0, 6, 10, 11, 13, 14), row)),
    )
}

private fun golf(): List<TutorialStep> {
    fun course(hole: Int, me: Pt?, them: Pt?) = @Composable { m: Modifier ->
        val view = GolfView(hole, Golf.HOLES.size, mapOf('m' to emptyList(), 'o' to emptyList()), mapOf('m' to me, 'o' to them), emptyMap(), null)
        GolfCourse(view, 'm', false, { _, _ -> }, m)
    }
    val aspect = (Golf.W / Golf.H).toFloat()
    return listOf(
        TutorialStep("Nine holes", "Everyone tees off in turn. Get the ball into the cup at the flag.", course(0, null, null), aspect),
        TutorialStep("Pull back and let go", "Pull from anywhere, like a slingshot. The further you pull, the harder the shot.", course(1, null, Pt(0.62, 1.0)), aspect),
        TutorialStep("Walls bounce, sand slows", "Play off the walls to get round corners.", course(4, Pt(0.4, 1.05), Pt(0.56, 0.5)), aspect),
        TutorialStep("Fewest strokes wins", "A ball still out after six strokes is picked up and scores seven.", course(0, Pt(0.52, 0.34), null), aspect),
    )
}

private fun blocks(): List<TutorialStep> {
    val n = Blocks.sizeFor(2)
    val first = listOf(3 to 4, 4 to 4, 5 to 4, 4 to 5)
    val second = listOf(6 to 5, 6 to 6, 6 to 7)
    val theirs = listOf(9 to 9, 10 to 9, 9 to 10, 10 to 10)
    fun board(mine: List<Pair<Int, Int>>, others: List<Pair<Int, Int>>, preview: List<Pair<Int, Int>>? = null, ok: Boolean = true) = @Composable { m: Modifier ->
        val b = CharArray(n * n) { '.' }
        mine.forEach { (x, y) -> b[y * n + x] = 'm' }
        others.forEach { (x, y) -> b[y * n + x] = 'o' }
        val view = BlocksView(n, String(b), mapOf('m' to (4 to 4), 'o' to (9 to 9)), emptyMap(), emptyMap(), null, emptyList())
        BlocksBoard(view, preview, ok, null, m)
    }
    return listOf(
        TutorialStep("Start on your ring", "Everyone has the same 21 pieces. Your first piece covers your ringed square.", board(emptyList(), emptyList())),
        TutorialStep("Pick, turn, place", "Choose a piece, turn or flip it, tap the board, then Place.", board(first, theirs)),
        TutorialStep("Corners only", "Each new piece touches your own corner to corner, never along an edge.", board(first + second, theirs)),
        TutorialStep("Red means no", "This one would touch your piece along an edge. Other colours can touch you anywhere.", board(first + second, theirs, listOf(7 to 6, 8 to 6), ok = false)),
        TutorialStep("Most squares wins", "Stuck players are skipped. When nobody can place, count the squares.", board(first + second + listOf(7 to 8, 8 to 8, 8 to 9), theirs + listOf(11 to 8, 12 to 8))),
    )
}
