package com.pippop.ui

import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.MatchRecord
import com.pippop.data.MonthEnd
import com.pippop.ui.screens.TrophyMoment
import com.pippop.ui.screens.showsConfetti
import com.pippop.ui.screens.startsLevel
import com.pippop.ui.screens.trophyLine
import com.pippop.ui.screens.trophyTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The month-end cup: you win, your partner wins, a tie, and the words and the confetti rule. */
class ScreenshotTrophyMomentTest : ScreenshotBase() {
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val youWin = MonthEnd(oct, "Maya", MatchRecord.ME, 15, 10, 25, GameKind.WORDS)
    private val mayaWins = MonthEnd(oct, "Maya", "Maya", 10, 15, 25, GameKind.WORDS)
    private val tie = MonthEnd(oct, "Maya", null, 12, 12, 26, GameKind.CONNECT4)

    @Test fun partnerWinsLight() = shot("scores-trophy", dark = false) { TrophyMoment(mayaWins, nov, Avatar.FOX, Avatar.PANDA, {}) }
    @Test fun partnerWinsDark() = shot("scores-trophy", dark = true) { TrophyMoment(mayaWins, nov, Avatar.FOX, Avatar.PANDA, {}) }

    @Test fun youWinLight() = shot("scores-trophy-you", dark = false) { TrophyMoment(youWin, nov, Avatar.FOX, Avatar.PANDA, {}) }
    @Test fun youWinDark() = shot("scores-trophy-you", dark = true) { TrophyMoment(youWin, nov, Avatar.FOX, Avatar.PANDA, {}) }

    @Test fun tieLight() = shot("scores-trophy-tie", dark = false) { TrophyMoment(tie, nov, Avatar.FOX, Avatar.PANDA, {}) }
    @Test fun tieDark() = shot("scores-trophy-tie", dark = true) { TrophyMoment(tie, nov, Avatar.FOX, Avatar.PANDA, {}) }

    // No pictures: the winner's first letter is on the cup.
    @Test fun noPicturesLight() = shot("scores-trophy-letters", dark = false) { TrophyMoment(mayaWins, nov, null, null, {}) }
    @Test fun noPicturesDark() = shot("scores-trophy-letters", dark = true) { TrophyMoment(mayaWins, nov, null, null, {}) }

    @Test fun titles() {
        assertEquals("Maya takes October", trophyTitle(mayaWins))
        assertEquals("You take October", trophyTitle(youWin))
        assertEquals("October is a tie", trophyTitle(tie))
    }

    @Test fun theLineIsTrueToTheData() {
        assertEquals("25 games together. You played Word Race the most.", trophyLine(youWin))
        assertEquals("One game together.", trophyLine(youWin.copy(games = 1)))
        assertEquals("3 games together.", trophyLine(youWin.copy(games = 3, mostPlayed = null)))
    }

    @Test fun startsLevelOnlyWhenTheNextMonthIsNow() {
        assertTrue(startsLevel(youWin, nov))
        assertFalse(startsLevel(youWin, YearMonth.of(2026, 12)))
    }

    @Test fun reducedMotionHasNoConfetti() {
        assertTrue(showsConfetti(reduceMotion = false))
        assertFalse(showsConfetti(reduceMotion = true))
    }
}
