package com.pippop.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.pippop.data.GameKind

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
        GameKind.CLASH -> Color(0xFF00796B)
        GameKind.LUDO -> Color(0xFF7CB342)
        GameKind.GRID -> Color(0xFF9E9D24)
        GameKind.BALLOON -> Color(0xFFEC6FA9)
        GameKind.CODE -> Color(0xFF5B5F97)
        GameKind.SLIDE -> Color(0xFFFF8A3D)
        GameKind.BINGO -> Color(0xFFD9467A)
        GameKind.GOLF -> Color(0xFF3FA34D)
        GameKind.BLOCKS -> Color(0xFF546E7A)
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
        GameKind.TTT -> "Three in a row, or nine boards"
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
        GameKind.GOLF -> "3D islands, jump shots, sticker spots"
        GameKind.BLOCKS -> "Corner to corner, fill the board"
    }

/** How many play, as the sheet says it: "2 players" or "2–4 players". */
val GameKind.playersFact: String
    get() = if (maxPlayers > 2) "2–$maxPlayers players" else "$maxPlayers players"

/** About how many minutes a game takes, from its own rules (a Bingo game is under five minutes of calls). */
val GameKind.minutes: Int
    get() = when (this) {
        GameKind.CONNECT4, GameKind.MEMORY, GameKind.WORDS, GameKind.TTT, GameKind.NUMBERS, GameKind.HOCKEY,
        GameKind.SLIDE, GameKind.BALLOON, GameKind.CODE -> 5
        GameKind.GRID, GameKind.BINGO -> 3
        GameKind.SUDOKU, GameKind.SHIPS, GameKind.DOTS, GameKind.CLASH, GameKind.DRAW -> 10
        GameKind.DOMINOES, GameKind.GOLF, GameKind.BLOCKS -> 15
        GameKind.LUDO -> 20
    }

/** How a game is played together: you "Take turns", "Race" the same puzzle, or play "Live". */
val GameKind.how: String
    get() = when {
        this == GameKind.HOCKEY || this == GameKind.DRAW || this == GameKind.BINGO -> "Live"
        turnBased || this == GameKind.SHIPS -> "Take turns"
        else -> "Race"
    }

/** The three facts under a game's name on its sheet. */
val GameKind.facts: List<String>
    get() = listOf(playersFact, "About $minutes min", how)

/** One short point of a game's rules: a title of up to three words and a body of up to thirty. */
data class RulePoint(val title: String, val body: String)

private fun pts(vararg p: Pair<String, String>) = p.map { RulePoint(it.first, it.second) }

/** How to play, as four to six points (the first is the goal), shown from the play sheet and the in-game Rules button. */
val GameKind.rules: List<RulePoint>
    get() = when (this) {
        GameKind.SUDOKU -> pts(
            "Goal" to "Fill every row, column and 3 × 3 box with the digits 1 to 9, each exactly once.",
            "Race" to "Everyone gets the same puzzle at the same moment. The first to solve it wins.",
            "Together" to "One board for the team. Anyone fills any square; a wrong digit costs a shared life.",
            "Big boards" to "Two to five grids are joined. The gold boxes are shared, so a digit there counts in every grid covering it.",
            "Zoom" to "Pinch or double-tap to zoom.",
            "Mistakes" to "Three mistakes end a solo game; five on a big board.",
        )
        GameKind.SHIPS -> pts(
            "Goal" to "Sink the whole enemy fleet to win.",
            "Your fleet" to "Each player hides five ships (5, 4, 3, 3 and 2 squares) on a 10 × 10 sea.",
            "Firing" to "Take turns firing at the other sea.",
            "Hits" to "A hit lets you fire again.",
        )
        GameKind.CONNECT4 -> pts(
            "Goal" to "Line up four of your discs across, down or diagonally to win.",
            "Your turn" to "Take turns dropping a disc into one of seven columns.",
            "Falling" to "A disc falls to the lowest free space.",
            "Draw" to "A full board is a draw.",
        )
        GameKind.DOTS -> pts(
            "Goal" to "When all 16 boxes are taken, the most boxes wins.",
            "Your turn" to "Take turns drawing a line between two neighbouring dots.",
            "Boxes" to "Draw the fourth side of a box to claim it.",
            "Go again" to "Claiming a box lets you draw again.",
        )
        GameKind.MEMORY -> pts(
            "Goal" to "Most pairs wins.",
            "Cards" to "16 cards lie face down: 8 pairs of pictures.",
            "Your turn" to "Flip two cards on your turn.",
            "A pair" to "A pair is yours and you go again.",
            "No pair" to "Otherwise they turn back and the next player goes.",
        )
        GameKind.WORDS -> pts(
            "Goal" to "Find the secret word in six tries. Every guess must be a real word of the same length.",
            "Colours" to "After each guess the letters turn green (right letter, right place), yellow (in the word, elsewhere) or grey (not in the word).",
            "Length" to "The level sets the length: easy 4 letters, medium 5, hard 6, expert 7 or 8.",
            "Race" to "Everyone gets the same word and sees the others' colours, never their letters.",
            "Winning" to "Fewest tries wins; on a tie, the faster.",
        )
        GameKind.DRAW -> pts(
            "Goal" to "Play as a team over four turns, taking turns to draw. A right guess adds the seconds left, out of 80, to your team score.",
            "Drawing" to "The drawer gets a secret word and draws it. No letters or words in the drawing!",
            "Guessing" to "The other player watches the drawing appear and types guesses.",
            "Tray" to "Eight colours, an eraser, three brush sizes and a fill bucket. Undo in the dock takes back your last line.",
            "Replay" to "Each drawing replays when its turn ends. Keep it for the Sketchbook.",
            "Guess race" to "With three or four, one draws and everyone else guesses at once: the first right guesses score 80, 60 and 40, and the drawer 20 for each. Most points wins.",
        )
        GameKind.TTT -> pts(
            "Goal" to "Three in a row wins. Pick Normal or Ultimate before you start.",
            "Normal" to "One 3 × 3 board.",
            "Ultimate" to "Nine small boards make one big board. Three small boards in a row win the game.",
            "Where to play" to "The square you mark sends the other player to the small board in the same spot: mark a top-right square and they must play in the top-right board.",
            "Won or full" to "Three in a row wins a small board. Sent to a board that is already won or full? Play in any open board.",
        )
        GameKind.DOMINOES -> pts(
            "Goal" to "The first to play out their hand wins the round and scores the pips left in the others' hands. First to the target wins.",
            "Players" to "Two to four players, each with a hand of dominoes. With four you can play two against two, partners opposite.",
            "Your turn" to "Put a tile on either end of the line so its number matches that end.",
            "Can't play" to "In the draw game you take tiles from the boneyard until you can. In the block game you pass.",
            "Blocked" to "If nobody can play, the lightest hand wins the round and scores the same way.",
        )
        GameKind.NUMBERS -> pts(
            "Goal" to "Make every sum work, across and down. Each bank number is used once.",
            "The puzzle" to "A crossword of sums such as 4 + 5 = 9, crossing at shared numbers. Some numbers are missing.",
            "Filling in" to "Tap a blank, then tap a number from the bank below.",
            "Checking" to "A sum turns green when it's right.",
            "Race" to "Everyone gets the same new puzzle, built on the host's phone. The first to solve it wins.",
        )
        GameKind.HOCKEY -> pts(
            "Goal" to "Knock the puck into the goal at the far end. First to 7 goals wins.",
            "Paddle" to "Drag your paddle. It stays in your half.",
            "Puck" to "The puck bounces off the sides and takes the speed of your paddle, so swing through it to shoot hard.",
            "Live" to "Against your partner it's live: best nearby, where the phones talk directly.",
        )
        GameKind.CLASH -> pts(
            "Goal" to "Everyone starts with seven cards. First to play their last card wins.",
            "Your turn" to "Play a card that matches the top of the pile by colour or by number or symbol.",
            "Action cards" to "Skip (⦸) skips the next player, reverse (⇄) turns the order round and +2 makes the next player draw two and miss their turn.",
            "Wilds" to "Wilds (★) go on anything and you name the colour. +4 also makes the next player draw four.",
            "Kylian Dictador" to "One in each colour. The next player draws eight and misses their turn, and his song takes over every phone.",
            "Can't play" to "Draw a card: play it if it fits, or keep it.",
        )
        GameKind.LUDO -> pts(
            "Goal" to "First with all four tokens home wins.",
            "Rolling" to "Roll the die on your turn. A 6 brings a token onto its start square and gives you another roll, but a third 6 in a row ends your turn.",
            "Moving" to "Four tokens each, starting in your corner. Move one the number rolled, clockwise round and up your coloured column to the centre.",
            "Sending back" to "Land on someone else's token to send it back to their corner, except on the start squares and stars, which are safe.",
            "Getting home" to "You need the exact roll to get home.",
            "Fair die" to "The die is fair: a secure random number that never looks at the board. Dice history in the dock counts every roll.",
        )
        GameKind.GRID -> pts(
            "Goal" to "Sixteen letters, two minutes. Spell as many words as you can. Most points wins.",
            "Spelling" to "Drag through letters that touch, sideways or corner to corner, each letter once per word, three letters or more.",
            "Scoring" to "Words of 3-4 letters score 1, 5 letters 2, 6 letters 3, 7 letters 5 and 8 letters 11.",
            "Bonus" to "When time is up, a word nobody else found scores double.",
            "Race" to "Everyone gets the same grid, rolled on the host's phone.",
        )
        GameKind.BALLOON -> pts(
            "Goal" to "Most points wins. Everyone picks once.",
            "Picking" to "Each round one player types a secret word of 3 to 12 letters. Names and in-jokes work.",
            "Guessing" to "The others take turns guessing letters.",
            "Scoring" to "A right letter scores 1 for each time it's in the word, and whoever fills in the last letter scores 3 more.",
            "Balloons" to "Each wrong letter pops one of the seven balloons. If they all pop, the picker scores 5.",
        )
        GameKind.CODE -> pts(
            "Goal" to "Crack the other player's code. Each of you hides a code of four coloured pegs; colours can repeat.",
            "Pins" to "After each guess you get a red pin for every right colour in the right place and a white pin for every right colour in the wrong place.",
            "Seeing" to "You see each other's pins but never the guesses.",
            "Winning" to "Fewest guesses wins; on a tie, the faster. Ten guesses each.",
        )
        GameKind.SLIDE -> pts(
            "Goal" to "Put the numbered tiles back in order, 1 to the last, with the gap at the end.",
            "Tap" to "Tap a tile in line with the gap and it slides over, with any tiles between.",
            "Swipe" to "Or swipe anywhere on the screen to slide the tile next to the gap into it.",
            "Race" to "Everyone gets the same shuffle, made on the host's phone with real slides so it can always be solved. The first to finish wins.",
            "Ticks" to "Tiles in their place get a green edge and a tick.",
        )
        GameKind.BINGO -> pts(
            "Goal" to "Be the first to fill a line and press Bingo.",
            "Your card" to "Everyone gets a card of 24 numbers around a free centre: B is 1–15, I 16–30, N 31–45, G 46–60 and O 61–75.",
            "Calls" to "A number from 1 to 75 is called every few seconds. Tap it on your card to dab it.",
            "A line" to "Fill a whole row, column or diagonal (the star counts) and the Bingo button lights up.",
        )
        GameKind.GOLF -> pts(
            "Goal" to "Nine holes. The fewest strokes wins.",
            "Aim" to "Swipe to turn round your ball. Pinch to see the whole hole; double-tap faces the flag.",
            "Shoot" to "Slide up the bar on the right and let go. Low is a roll; high is a jump, over gaps and walls.",
            "Trouble" to "Off an island, or stopped on a pond: back to the tee. Sand slows a lot, grass a little.",
            "Sticker spots" to "Kylian takes your next turn. Fire sends you to the tee. Stonks throws you up the course. Portals swap you over. Hmm. Sure. hides your aim line.",
            "Ending a hole" to "Once someone is in, the rest get three more shots. A ball still out scores one more.",
        )
        GameKind.BLOCKS -> pts(
            "Goal" to "When nobody can place another piece, the most squares placed wins. Stuck players are skipped.",
            "Pieces" to "Everyone has the same 21 pieces in their colour.",
            "First piece" to "Your first piece must cover your ringed start square.",
            "Corners" to "Each new piece must touch one of your own pieces corner to corner and never along an edge. Other colours can touch you anywhere.",
            "Placing" to "Pick a piece, turn or flip it, tap the board to put it there and Place.",
        )
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
        GameKind.BLOCKS -> {
            // Two pieces meeting at a corner.
            p.rr(3, 3, 6, 6, 1.2, tint); p.rr(3, 9.5, 6, 6, 1.2, tint); p.rr(9.5, 9.5, 6, 6, 1.2, null, tint, 1.4)
            p.rr(15.5, 15.5, 5.5, 5.5, 1.2, null, tint, 1.4)
        }
        GameKind.GOLF -> {
            // A flag in the cup and a ball.
            p.stroke(tint, 1.6) { moveTo(9, 19); lineTo(9, 4) }
            p.fill(tint) { moveTo(9, 4); lineTo(17, 7); lineTo(9, 10); close() }
            p.ell(9, 19.5, 5, 1.6, 0, tint)
            p.circle(18, 18, 2.4, tint)
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
