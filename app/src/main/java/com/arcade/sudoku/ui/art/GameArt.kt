package com.arcade.sudoku.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.arcade.sudoku.data.GameKind

/** Each game's own colour, used for its tile, icon and turn highlights everywhere in the app. */
val GameKind.color: Color
    get() = when (this) {
        GameKind.SUDOKU -> Color(0xFF3D7BF7)
        GameKind.SHIPS -> Color(0xFF10A2B6)
        GameKind.CONNECT4 -> Color(0xFFE8505B)
        GameKind.DOTS -> Color(0xFF7B6CF0)
        GameKind.MEMORY -> Color(0xFFF2A33A)
        GameKind.WORDS -> Color(0xFF20A47B)
        GameKind.DRAW -> Color(0xFFE0569B)
        GameKind.TTT -> Color(0xFF4F46E5)
        GameKind.DOMINOES -> Color(0xFF8B5E34)
        GameKind.NUMBERS -> Color(0xFF0E9AA7)
        GameKind.HOCKEY -> Color(0xFF1E88E5)
        GameKind.CLASH -> Color(0xFFE8505B)
        GameKind.LUDO -> Color(0xFF20A47B)
        GameKind.GRID -> Color(0xFF7B6CF0)
        GameKind.BALLOON -> Color(0xFFEC6FA9)
        GameKind.CODE -> Color(0xFF5B5F97)
        GameKind.SLIDE -> Color(0xFFFF8A3D)
        GameKind.BINGO -> Color(0xFFD9467A)
    }

/** One line describing each game, for its tile and play sheet. */
val GameKind.tagline: String
    get() = when (this) {
        GameKind.SUDOKU -> "Fill the grid, race your partner"
        GameKind.SHIPS -> "Hide your fleet, sink theirs"
        GameKind.CONNECT4 -> "Four in a row wins"
        GameKind.DOTS -> "Close a box, go again"
        GameKind.MEMORY -> "Find the pairs"
        GameKind.WORDS -> "Same word, who finds it first?"
        GameKind.DRAW -> "One draws, one guesses, as a team"
        GameKind.TTT -> "Nine boards, one big game"
        GameKind.DOMINOES -> "Match the ends, play out your hand"
        GameKind.NUMBERS -> "A crossword made of sums"
        GameKind.HOCKEY -> "Fast paddles, one puck"
        GameKind.CLASH -> "Match the colour, empty your hand"
        GameKind.LUDO -> "Roll, race home, knock them back"
        GameKind.GRID -> "Trace words in two minutes"
        GameKind.BALLOON -> "Guess the letters before they pop"
        GameKind.CODE -> "Crack the hidden colour code"
        GameKind.SLIDE -> "Same shuffle, first in order wins"
        GameKind.BINGO -> "Dab the calls, shout first"
    }

/** How to play, shown from the play sheet and the in-game Rules button. */
val GameKind.rules: String
    get() = when (this) {
        GameKind.SUDOKU -> "Fill every row, column and 3 × 3 box with the digits 1 to 9, each exactly once. " +
            "In a battle you both get the same puzzle at the same moment; the first to solve it wins. " +
            "Three mistakes end a solo game."
        GameKind.SHIPS -> "Each player hides five ships (5, 4, 3, 3 and 2 squares) on a 10 × 10 sea. Take turns " +
            "firing at the other sea. A hit lets you fire again. Sink the whole enemy fleet to win."
        GameKind.CONNECT4 -> "Take turns dropping a disc into one of seven columns; it falls to the lowest free space. " +
            "Line up four of your discs across, down or diagonally to win. A full board is a draw."
        GameKind.DOTS -> "Take turns drawing a line between two neighbouring dots. Draw the fourth side of a box " +
            "to claim it, then draw again. When all 16 boxes are taken, the most boxes wins."
        GameKind.MEMORY -> "16 cards lie face down: 8 pairs of pictures. Flip two cards on your turn. A pair is yours " +
            "and you go again; otherwise they turn back and the next player goes. Most pairs wins."
        GameKind.WORDS -> "Find the secret word in six tries. Every guess must be a real word of the same length. After " +
            "each guess the letters turn green (right letter, right place), yellow (in the word, elsewhere) or grey (not in " +
            "the word). The level sets the length: easy 4 letters, medium 5, hard 6, expert 7 or 8. In a race everyone gets " +
            "the same word and sees the others' colours, never their letters. Fewest tries wins; on a tie, the faster."
        GameKind.DRAW -> "You play as a team, four turns, taking turns to draw. The drawer gets a secret word and draws it; " +
            "the other watches the drawing appear and types guesses. A right guess adds the seconds left (out of 80) " +
            "to your team score. No letters or words in the drawing!"
        GameKind.TTT -> "Nine small tic-tac-toe boards make one big board. The square you mark sends your partner to " +
            "the small board in the same spot: mark a top-right square and they must play in the top-right board. " +
            "Three in a row wins a small board; three small boards in a row wins the game. If you are sent to a " +
            "board that is already won or full, play in any open board."
        GameKind.DOMINOES -> "Two to four players, each with a hand of dominoes. On your turn put a tile on either end of " +
            "the line so its number matches that end. Can't play? In the draw game you take tiles from the boneyard " +
            "until you can; in the block game you pass. The first to play out their hand (or, if nobody can play, the " +
            "lightest hand) wins the round and scores the pips left in the others' hands. First to the target wins. " +
            "With four players you can play two against two, partners opposite."
        GameKind.NUMBERS -> "A crossword of sums such as 4 + 5 = 9, crossing at shared numbers. Some numbers are " +
            "missing: tap a blank, then tap a number from the bank below. Every sum, across and down, must work, and " +
            "each bank number is used once. A sum turns green when it's right. In a race everyone gets the same new " +
            "puzzle, built on the host's phone; the first to solve it wins."
        GameKind.HOCKEY -> "Drag your paddle (it stays in your half) to knock the puck into the goal at the far end. " +
            "The puck bounces off the sides and takes the speed of your paddle, so swing through it to shoot hard. " +
            "First to 7 goals wins. Against your partner it's live: best nearby, where the phones talk directly."
        GameKind.CLASH -> "Everyone starts with seven cards. On your turn play a card that matches the top of the pile " +
            "by colour or by number or symbol. Skip (⦸) skips the next player, reverse (⇄) turns the order round and +2 " +
            "makes the next player draw two and miss their turn. Wilds (★) go on anything and you name the colour; +4 " +
            "also makes the next player draw four. Can't play? Draw a card: play it if it fits, or keep it. First to " +
            "play their last card wins."
        GameKind.LUDO -> "Each player has four tokens in their corner. Roll the die on your turn: a 6 brings a token " +
            "out onto its start square and gives you another roll (but a third 6 in a row ends your turn). Move a token " +
            "the number rolled, clockwise round the board and up your coloured column to the centre. Land on someone " +
            "else's token to send it back to their corner, except on the start squares and stars, which are safe. You " +
            "need the exact roll to get home. First with all four tokens home wins."
        GameKind.GRID -> "Sixteen letters, two minutes. Spell words by dragging through letters that touch, sideways or " +
            "corner to corner, each letter once per word, three letters or more. Words of 3-4 letters score 1, 5 letters 2, " +
            "6 letters 3, 7 letters 5 and 8 letters 11. When time is up, a word nobody else found scores double. In a " +
            "race everyone gets the same grid, rolled on the host's phone."
        GameKind.BALLOON -> "Each round one player types a secret word (any word: names and in-jokes work). The others " +
            "take turns guessing letters. A right letter scores 1 for each time it's in the word, and whoever fills in the " +
            "last letter scores 3 more. Each wrong letter pops one of the seven balloons; if they all pop, the picker scores " +
            "5. Everyone picks once, and the most points wins."
        GameKind.CODE -> "Each of you hides a code of four coloured pegs (colours can repeat) for the other to crack. " +
            "After each guess you get a black pin for every right colour in the right place and a white pin for every " +
            "right colour in the wrong place. You see each other's pins but never the guesses. Fewest guesses wins; " +
            "on a tie, the faster. Ten guesses each."
        GameKind.SLIDE -> "Put the numbered tiles back in order, 1 to the last, with the gap at the end. Tap a tile in line " +
            "with the gap and it slides over (with any tiles between). In a race everyone gets the same shuffle, made on the " +
            "host's phone with real slides so it can always be solved; the first to finish wins. Tiles in their place turn orange."
        GameKind.BINGO -> "Everyone gets a card of 24 numbers around a free centre: B is 1–15, I 16–30, N 31–45, G 46–60 and O 61–75. " +
            "A number from 1 to 75 is called every few seconds. Tap it on your card to dab it. Fill a whole row, column or " +
            "diagonal (the star counts) and the Bingo button lights up: the first to press it wins."
    }

/** The game's icon in white (or [tint]), drawn to fill this area. */
@Composable
fun GameIcon(kind: GameKind, modifier: Modifier, tint: Color = Color.White) {
    Canvas(modifier) { drawGameIcon(kind, tint) }
}

fun DrawScope.drawGameIcon(kind: GameKind, tint: Color) = scale(size.minDimension / 24f, Offset.Zero) {
    val p = Pen(this)
    when (kind) {
        GameKind.SUDOKU -> {
            p.rr(3, 3, 18, 18, 3, null, tint, 2)
            p.stroke(tint, 1.4) { moveTo(9, 3); lineTo(9, 21); moveTo(15, 3); lineTo(15, 21); moveTo(3, 9); lineTo(21, 9); moveTo(3, 15); lineTo(21, 15) }
            p.rect(9.5, 9.5, 5, 5, tint)
        }
        GameKind.SHIPS -> {
            p.fill(tint) { moveTo(3, 14); lineTo(21, 14); lineTo(18, 19); lineTo(6, 19); close() }
            p.stroke(tint, 2) { moveTo(8, 14); lineTo(8, 9); lineTo(14, 9); lineTo(14, 14); moveTo(11, 9); lineTo(11, 5); lineTo(13, 5) }
            p.stroke(tint, 1.5) { moveTo(2, 21.5); quadTo(4, 20.5, 6, 21.5); quadTo(8, 22.5, 10, 21.5); quadTo(12, 20.5, 14, 21.5); quadTo(16, 22.5, 18, 21.5); quadTo(20, 20.5, 22, 21.5) }
        }
        GameKind.CONNECT4 -> {
            p.rr(2, 4, 20, 17, 3, null, tint, 2)
            p.circle(8, 10, 2.6, tint); p.circle(16, 15, 2.6, tint)
            p.ring(8, 15, 2.6, tint, 1.5); p.ring(16, 10, 2.6, tint, 1.5)
        }
        GameKind.DOTS -> {
            p.withAlpha(0.45) { p.rect(5, 5, 7, 7, tint) }
            p.stroke(tint, 2) { moveTo(5, 5); lineTo(19, 5); moveTo(5, 5); lineTo(5, 19); moveTo(12, 5); lineTo(12, 12); lineTo(5, 12) }
            for (y in listOf(5, 12, 19)) for (x in listOf(5, 12, 19)) p.circle(x, y, 2, tint)
        }
        GameKind.MEMORY -> {
            p.at(8.5, 12.5, -0.17, 1) { p.withAlpha(0.5) { p.rr(3, 5, 11, 15, 2.5, tint) } }
            p.at(15.5, 11.5, 0.14, 1) { p.rr(10, 4, 11, 15, 2.5, null, tint, 2) }
            p.circle(15.5, 11.5, 2.3, tint)
        }
        GameKind.DRAW -> {
            // A pencil drawing a squiggle.
            p.stroke(tint, 2) { moveTo(3, 19); quadTo(6, 13, 9, 17); quadTo(12, 21, 14, 15) }
            p.at(17, 9, 0.785, 1) { p.rr(14, 2, 6, 15, 1.5, tint) }
            p.fill(tint) { moveTo(13.2, 15.6); lineTo(16.5, 18.9); lineTo(12, 20.5); close() }
        }
        GameKind.TTT -> {
            // The big grid, with an X and an O in two of its boards.
            p.stroke(tint, 2) { moveTo(9, 3); lineTo(9, 21); moveTo(15, 3); lineTo(15, 21); moveTo(3, 9); lineTo(21, 9); moveTo(3, 15); lineTo(21, 15) }
            p.stroke(tint, 1.8) { moveTo(3.8, 3.8); lineTo(7.2, 7.2); moveTo(7.2, 3.8); lineTo(3.8, 7.2) }
            p.ring(18, 18, 1.9, tint, 1.6)
            p.rr(10.2, 10.2, 3.6, 3.6, 0.8, tint)
        }
        GameKind.DOMINOES -> {
            // An upright tile: three pips over two.
            p.rr(7, 2.5, 10, 19, 2.5, null, tint, 1.8)
            p.stroke(tint, 1.4) { moveTo(8.5, 12); lineTo(15.5, 12) }
            p.circle(9.8, 5.6, 1.15, tint); p.circle(12, 7.4, 1.15, tint); p.circle(14.2, 9.2, 1.15, tint)
            p.circle(9.8, 15.4, 1.15, tint); p.circle(14.2, 18.6, 1.15, tint)
        }
        GameKind.NUMBERS -> {
            // A plus crossing an equals: a sum across meeting one going down.
            p.rr(3, 3, 18, 18, 4, null, tint, 1.8)
            p.stroke(tint, 2) { moveTo(7, 9); lineTo(13, 9); moveTo(10, 6); lineTo(10, 12) }
            p.stroke(tint, 2) { moveTo(11, 15); lineTo(17, 15); moveTo(11, 18); lineTo(17, 18) }
        }
        GameKind.HOCKEY -> {
            // A paddle about to strike the puck.
            p.ring(9, 15, 5.5, tint, 2)
            p.circle(9, 15, 2, tint)
            p.circle(17, 7, 3, tint)
            p.stroke(tint, 1.6) { moveTo(13.5, 10.5); lineTo(15, 9) }
        }
        GameKind.CLASH -> {
            // Two cards fanned out.
            p.rr(4, 6, 10, 14, 2, null, tint, 1.6)
            p.rr(10, 4, 10, 14, 2, tint, tint, 1.6)
            p.circle(15, 11, 2.2, Color.White)
        }
        GameKind.LUDO -> {
            // A die with five pips.
            p.rr(4, 4, 16, 16, 3.5, null, tint, 1.8)
            p.circle(8.5, 8.5, 1.4, tint); p.circle(15.5, 8.5, 1.4, tint); p.circle(12, 12, 1.4, tint)
            p.circle(8.5, 15.5, 1.4, tint); p.circle(15.5, 15.5, 1.4, tint)
        }
        GameKind.GRID -> {
            // Four letter tiles, two of them traced.
            p.rr(3, 3, 8, 8, 2, tint, tint, 1.4); p.rr(13, 3, 8, 8, 2, null, tint, 1.4)
            p.rr(3, 13, 8, 8, 2, null, tint, 1.4); p.rr(13, 13, 8, 8, 2, tint, tint, 1.4)
            p.stroke(tint, 1.6) { moveTo(7, 7); lineTo(17, 17) }
        }
        GameKind.BALLOON -> {
            // A balloon on a string.
            p.ell(12, 9.5, 6, 7, 0, tint)
            p.stroke(tint, 1.4) { moveTo(12, 16.5); quadTo(10, 19, 13, 22) }
        }
        GameKind.CODE -> {
            // Four pegs and the score pins.
            p.circle(5, 9, 2.6, tint); p.circle(11, 9, 2.6, tint); p.ring(17, 9, 2.4, tint, 1.4)
            p.circle(5, 17, 1.4, tint); p.circle(9, 17, 1.4, tint); p.ring(13, 17, 1.3, tint, 1.2)
        }
        GameKind.SLIDE -> {
            // A 2 × 2 of tiles with one gap and an arrow.
            p.rr(3, 3, 8, 8, 2, tint, tint, 1.4); p.rr(13, 3, 8, 8, 2, tint, tint, 1.4); p.rr(3, 13, 8, 8, 2, tint, tint, 1.4)
            p.stroke(tint, 1.6) { moveTo(14, 17); lineTo(20, 17); moveTo(17.5, 14.5); lineTo(20, 17); lineTo(17.5, 19.5) }
        }
        GameKind.BINGO -> {
            // A ball with a dabbed card corner.
            p.ring(9, 9, 6, tint, 1.6); p.circle(9, 9, 2.6, tint)
            p.rr(12, 12, 9, 9, 1.5, null, tint, 1.6); p.circle(16.5, 16.5, 2.2, tint)
        }
        GameKind.WORDS -> {
            // Two rows of letter tiles: the top guess part right, the bottom one solved.
            for (i in 0 until 3) p.rr(2.5 + i * 7, 4, 5.5, 6.5, 1.5, if (i == 1) tint else null, tint, 1.6)
            for (i in 0 until 3) p.rr(2.5 + i * 7, 13, 5.5, 6.5, 1.5, tint)
        }
    }
}
