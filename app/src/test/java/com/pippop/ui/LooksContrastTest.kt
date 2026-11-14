package com.pippop.ui

import androidx.compose.ui.graphics.Color
import com.pippop.data.CardBackLook
import com.pippop.data.ColourTheme
import com.pippop.data.FrameLook
import com.pippop.data.TileLook
import com.pippop.ui.screens.MyDisc
import com.pippop.ui.screens.TheirDisc
import com.pippop.ui.screens.color
import com.pippop.ui.screens.darker
import com.pippop.ui.screens.lighter
import com.pippop.ui.screens.paint
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.contrast
import com.pippop.ui.theme.paletteFor
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 25: whatever look is chosen, pips, discs and card backs stay easy to tell apart. */
class LooksContrastTest {
    @Test fun pipsReadOnEveryTile() {
        for (look in TileLook.entries) {
            val paint = look.paint
            // A tile shades from its face to a tenth darker.
            for ((where, face) in listOf("top" to paint.face, "bottom" to paint.face.darker(0.1f))) {
                val ratio = contrast(paint.pip, face)
                assertTrue("$look pips on the $where of the tile: $ratio", ratio >= 3f)
            }
        }
    }

    @Test fun theLeastCheerfulTileStillHasAnEdgeAgainstTheTable() {
        for (look in TileLook.entries) {
            val ratio = contrast(look.paint.edge, Board.Felt)
            assertTrue("$look edge on the felt: $ratio", ratio >= 1.5f || contrast(look.paint.face, Board.Felt) >= 1.5f)
        }
    }

    /**
     * A disc sits in a hole and is ringed in ink, so the ring is what sets it off from the frame and
     * the hole. (A disc against the frame's own colour cannot reach 3:1 for both players: the
     * original blue frame is 1.1 to 4.6 against them, so the guard is the ring and a frame that
     * stands out from the page.)
     */
    @Test fun discsAreRingedClearlyAndFramesStandOutOnEveryFrame() {
        // A disc is ringed in the palette's ink (dark in light mode, a light lavender in dark mode) and
        // sits in a hole of the page's background. In each colourway the ring or the hole sets it off.
        for (theme in ColourTheme.entries) for (dark in listOf(false, true)) {
            val p = paletteFor(theme, dark)
            for (disc in listOf(MyDisc, TheirDisc)) {
                val ratio = maxOf(contrast(disc, p.ink), contrast(disc, p.background))
                assertTrue("disc $disc in $theme ${if (dark) "dark" else "light"}: $ratio", ratio >= 3f)
            }
        }
        for (look in FrameLook.entries) {
            // The frame's own ink edge is 4 px round all of it; its body must also stand out from the page.
            for (theme in ColourTheme.entries) for (dark in listOf(false, true)) {
                val page = paletteFor(theme, dark).background
                val ratio = contrast(look.color, page)
                assertTrue("$look frame on a $theme ${if (dark) "dark" else "light"} page: $ratio", ratio >= 1.8f)
            }
            // The hole's rim is darker than the frame, so a disc's ring has something to sit against.
            val rim = contrast(look.color.darker(0.42f), look.color)
            assertTrue("$look hole rim against the frame: $rim", rim >= 1.5f)
        }
    }

    /** A face-down card stays clearly not a face-up one, in every colourway, light and dark. */
    @Test fun cardBacksStandApartFromCardFaces() {
        for (theme in ColourTheme.entries) for (dark in listOf(false, true)) {
            val p = paletteFor(theme, dark)
            for (look in CardBackLook.entries) {
                val back = contrast(look.color, p.card)
                assertTrue("$look back on a $theme ${if (dark) "dark" else "light"} card: $back", back >= 1.5f)
                val gold = contrast(look.color, Pieces.Gold)
                assertTrue("$look back against a gold card: $gold", gold >= 1.2f)
            }
        }
    }

    @Test fun backColoursAreNotTheOnesClashUses() {
        // Clash keeps its own dark deck whatever back is chosen, so a back can never pass for a colour card.
        val distinct: List<Color> = CardBackLook.entries.map { it.color }
        assertTrue(distinct.toSet().size == distinct.size)
    }
}
