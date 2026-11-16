package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.pippop.ui.theme.AppTheme
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.pippop.data.GameKind
import com.pippop.ui.theme.PipPopTheme
import org.junit.Test

/**
 * Game Store step 4, the home while the shelf is edited: cards tilted and each with its ×, one card lifted
 * mid-drag over a dashed gap, the "<Game> is back in the Store. Undo" toast, and the empty shelf. The clock is
 * stepped by hand, so the wiggle is caught at one fixed moment.
 */
class ScreenshotHomeEditTest : ScreenshotBase() {
    private val nine = listOf(
        GameKind.SUDOKU, GameKind.SHIPS, GameKind.MEMORY, GameKind.WORDS, GameKind.TTT,
        GameKind.DOMINOES, GameKind.CLASH, GameKind.LUDO, GameKind.GOLF,
    )

    private fun snap(name: String, dark: Boolean, shelf: List<GameKind> = nine, reduceMotion: Boolean = false, steps: HomeEditHost.() -> Unit = {}) {
        val host = HomeEditHost(shelf)
        compose.mainClock.autoAdvance = false
        compose.setContent { PipPopTheme(darkTheme = dark) { Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { host.Content(reduceMotion) } } }
        compose.mainClock.advanceTimeBy(500)
        host.steps()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    private fun holdToEdit(game: GameKind) {
        val tile = compose.onNodeWithText(game.label)
        tile.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        // Let the card come down for real: the hand-stepped clock leaves the lifted card up.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun lift(index: Int, toward: Int) {
        val shelf = nine
        val from = compose.onNodeWithContentDescription("${shelf[index].label}, ${index + 1} of 9").fetchSemanticsNode().boundsInRoot.center
        val to = compose.onNodeWithContentDescription("${shelf[toward].label}, ${toward + 1} of 9").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(from)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performTouchInput {
            moveTo(from + Offset(6f, 6f))
            moveTo(from + (to - from) * 0.6f)
        }
        compose.mainClock.advanceTimeBy(300)
    }

    @Test fun editLight() = snap("home-edit", false) { holdToEdit(GameKind.LUDO) }
    @Test fun editDark() = snap("home-edit", true) { holdToEdit(GameKind.LUDO) }

    @Test fun editStillLight() = snap("home-edit-still", false, reduceMotion = true) { holdToEdit(GameKind.LUDO) }
    @Test fun editStillDark() = snap("home-edit-still", true, reduceMotion = true) { holdToEdit(GameKind.LUDO) }

    @Test fun dragLight() = snap("home-edit-drag", false) { holdToEdit(GameKind.LUDO); lift(1, 4) }
    @Test fun dragDark() = snap("home-edit-drag", true) { holdToEdit(GameKind.LUDO); lift(1, 4) }

    @Test fun toastLight() = snap("home-edit-toast", false) { holdToEdit(GameKind.LUDO); removeLudo() }
    @Test fun toastDark() = snap("home-edit-toast", true) { holdToEdit(GameKind.LUDO); removeLudo() }

    @Test fun emptyLight() = snap("home-empty-shelf", false, shelf = emptyList()) {}
    @Test fun emptyDark() = snap("home-empty-shelf", true, shelf = emptyList()) {}

    private fun removeLudo() {
        compose.onNodeWithContentDescription("Remove Ludo").performClick()
        compose.mainClock.advanceTimeBy(500)
    }
}
