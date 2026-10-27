package com.arcade.sudoku.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's named design values. Screens use these (and [SudokuPalette]) instead of typing
 * colours, corner radii and gaps inline, so a change is made once and shows everywhere.
 * `scripts/check-tokens.sh` (run by CI) rejects raw values outside this package and `ui/art`.
 */

/** Corner radii: XS for tiny marks, S for small tiles, M for chips and small buttons, L for buttons, cards and sheets. */
object Radius {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 20.dp
}

/** The shapes made from [Radius], plus [Full] for pills and round chips. */
object Shapes {
    val XS = RoundedCornerShape(Radius.XS)
    val S = RoundedCornerShape(Radius.S)
    val M = RoundedCornerShape(Radius.M)
    val L = RoundedCornerShape(Radius.L)
    val Full = RoundedCornerShape(50)
    /** A sheet or tray rising from the bottom: only its top corners round. */
    val Top = RoundedCornerShape(topStart = Radius.L, topEnd = Radius.L)
}

/** Gaps and padding. */
object Space {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 24.dp
    val XXXL = 32.dp
}

/**
 * The colours of playing pieces, shared by the games that colour pieces by player or suit
 * (Ludo, Colour Clash, Code Breaker, Balloon Words, Bingo, Connect Four, Dots & Boxes).
 */
object Pieces {
    val Red = Color(0xFFE8505B)
    val Yellow = Color(0xFFFFC93C)
    val Green = Color(0xFF20A47B)
    val Blue = Color(0xFF3D7BF7)
    val Purple = Color(0xFF7B6CF0)
    val Orange = Color(0xFFFF8A3D)
    val Pink = Color(0xFFEC6FA9)
}

/**
 * Colours of boards and table things that keep their look in both themes: a Ludo board or a
 * domino is light even in dark mode, so it is drawn with this fixed [Ink], not the theme's.
 */
object Board {
    /** Outlines and pips on light boards. */
    val Ink = Color(0xFF2B2440)
    /** A die's face. */
    val DieWhite = Color(0xFFFFFDF7)
    /** Dominoes: the table and the tiles. */
    val Felt = Color(0xFF2E7D5B)
    val Bone = Color(0xFFFFFBF2)
    /** A soft gold for "this one" highlights: a playable tile, a target, a winning line. */
    val Glow = Color(0xFFFFD23F)
    val GlowSoft = Color(0xFFFFE08A)
    /** Connect Four's and Ultimate Tic-Tac-Toe's frames. */
    val ConnectBlue = Color(0xFF2F6FD1)
    val TttBlue = Color(0xFF4F46E5)
    /** Air Hockey's rink. */
    val Ice = Color(0xFFEAF6FF)
    val IceLine = Color(0xFFB9D9F2)
}

/** The other players, in seat order after you: the first is the classic opponent orange. */
val SeatColors = listOf(OpponentColor, Pieces.Purple, Pieces.Green)

/** Lines that need attention without being errors (levels that don't match). */
val Warning = Color(0xFFE08A00)

/** Colours only one game uses: its course, stones, letters or effects. */
object GameColors {
    // Draw & Guess pens (the others are Board.Ink, Pieces.Red and Pieces.Blue).
    val PenGreen = Color(0xFF35BD88)
    val PenOrange = Color(0xFFF2A33A)
    // Ship Battle hits: sparks, smoke and spray.
    val Spark = Color(0xFFFFB13D)
    val Smoke = Color(0xFF6B6B7A)
    val Spray = Color(0xFFBFE6FF)
    // Dots & Boxes: the other player's lines.
    val TheirLine = Color(0xFFE0A21C)
    // Word Race letters: right place, in the word, not in it.
    val LetterRight = Color(0xFF3BAA5C)
    val LetterClose = Color(0xFFE2B021)
    val LetterOut = Color(0xFF8C87A3)
    // Mini Golf course.
    val Grass = Color(0xFF8BCF5A)
    val GrassDark = Color(0xFF79BE4B)
    val Rim = Color(0xFFB5E27A)
    val RimTop = Color(0xFFC9EC94)
    val CourseSide = Color(0xFF4E9A3E)
    val Sand = Color(0xFFF2D68A)
    val SandEdge = Color(0xFFD9B865)
    val SkyTop = Color(0xFF7CCBF2)
    val SkyBottom = Color(0xFFC6EBFB)
    val Flag = Color(0xFFE23B4B)
    val Cup = Color(0xFF1B1B1B)
    // Number Cross grid and stone blocks.
    val GridFrame = Color(0xFF6E6A7C)
    val OperatorTile = Color(0xFFEDE8F7)
    val Stone = Color(0xFF9C98A8)
    val StoneLight = Color(0xFFB9B5C4)
    val StoneDark = Color(0xFF6F6B7D)
}
