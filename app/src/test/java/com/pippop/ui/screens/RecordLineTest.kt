package com.pippop.ui.screens

import com.pippop.data.BrokenRecord
import com.pippop.data.GameKind
import com.pippop.data.SoloKey
import com.pippop.data.SoloMetric
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the gold line says, and that it reads. */
class RecordLineTest {
    private fun solo(g: GameKind, level: String, m: SoloMetric, v: Long) = RecordLines.text(BrokenRecord.Solo(SoloKey(g, level, m), v))

    @Test fun againstAPartner() {
        assertEquals("Your quickest win at Connect Four: 11 discs", RecordLines.text(BrokenRecord.Quickest(GameKind.CONNECT4, 11, null)))
        assertEquals("Your quickest win at Ship Battle: 1 shot", RecordLines.text(BrokenRecord.Quickest(GameKind.SHIPS, 1, null)))
        assertEquals("Your quickest win at Slide Race: 1:12", RecordLines.text(BrokenRecord.Quickest(GameKind.SLIDE, null, 72_000)))
        assertEquals("Your biggest win at Dominoes: by 42 points", RecordLines.text(BrokenRecord.Biggest(GameKind.DOMINOES, 42)))
        assertEquals("Your biggest win at Dots & Boxes: by 1 box", RecordLines.text(BrokenRecord.Biggest(GameKind.DOTS, 1)))
        assertEquals("Your biggest win at Air Hockey: by 5 goals", RecordLines.text(BrokenRecord.Biggest(GameKind.HOCKEY, 5)))
    }

    @Test fun alone() {
        assertEquals("Your best at Slide Race (4 × 4): 1:12", solo(GameKind.SLIDE, "medium", SoloMetric.TIME, 72_000))
        assertEquals("Your best at Ship Battle: 31 shots", solo(GameKind.SHIPS, "", SoloMetric.SHOTS, 31))
        assertEquals("Your best at Sudoku (Medium): 6:42", solo(GameKind.SUDOKU, "medium", SoloMetric.TIME, 402_000))
        assertEquals("Your best at Memory Pairs (16 cards): 21 flips", solo(GameKind.MEMORY, "16", SoloMetric.FLIPS, 21))
        assertEquals("Your best at Dominoes (2 computers): 35 points ahead", solo(GameKind.DOMINOES, "2", SoloMetric.POINTS_AHEAD, 35))
        assertEquals("Your best at Connect Four (against Hard): 9 discs", solo(GameKind.CONNECT4, "hard", SoloMetric.DISCS, 9))
    }

    @Test fun letterGridLongestWordIsSaidAsAWord() {
        assertEquals("Your longest word at Letter Grid (Timed): 7 letters", solo(GameKind.GRID, "timed", SoloMetric.LONGEST_WORD, 7))
        assertEquals("Your best at Letter Grid (Timed): 40 points", solo(GameKind.GRID, "timed", SoloMetric.POINTS, 40))
    }

    @Test fun theGoldReadsInLightAndDark() {
        // Board colours do not change with the theme: the card is light-on-dark either way, the line is the same.
        assertTrue(contrast(Board.Ink, Board.GlowSoft) >= 4.5f)
    }
}
