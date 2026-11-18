package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.pippop.data.GameKind
import com.pippop.data.SoloSetups
import com.pippop.game.words.LadderLevel
import com.pippop.game.words.LadderMatch
import com.pippop.game.words.LadderOptions
import com.pippop.game.words.LadderView
import com.pippop.ui.screens.SoloLadderScreen
import com.pippop.ui.screens.SoloSetupContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.PipPopTheme
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

/** Word Ladder against the computer, light and dark: the setup, the secret pick, the ladder on your turn, and the result. Positions come from the game's own rules. */
class ScreenshotLadderSoloTest : LadderTestBase() {
    @Before fun words() = LadderTable.installWords()

    private fun match(vararg plays: String, mine: String? = "WARM", theirs: String? = "COLD"): SoloLadderUiState {
        val m = LadderMatch(LadderOptions(4, 0), listOf('m', 'o')) { 0.99 }
        mine?.let { m.setPick('m', it) }
        theirs?.let { m.setPick('o', it) }
        plays.forEachIndexed { i, w -> m.play(if (i % 2 == 0) 'm' else 'o', JSONObject().put("word", w)) }
        return SoloLadderUiState(LadderLevel.MEDIUM, 4, LadderView.from(m.view('m', mapOf('o' to 'o'))))
    }

    private fun screen(name: String, dark: Boolean, state: SoloLadderUiState) {
        val vm = SoloLadderViewModel()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { SoloLadderScreen(state, vm, onHome = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    private fun setup(dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
                    ToySheetCard {
                        SoloSetupContent(
                            GameKind.LADDER, SoloSetups.resolve(GameKind.LADDER, mapOf("level" to "hard", "length" to "5")),
                            onPick = { _, _ -> }, startLabel = "Play solo · Hard", onStart = {}, onBack = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/ladder-solo-setup-${if (dark) "dark" else "light"}.png")
    }

    @Test fun setupLight() = setup(false)
    @Test fun setupDark() = setup(true)

    /** You have locked WARM in; the computer is picking. */
    @Test fun pickLight() = screen("ladder-solo-pick", false, match(theirs = null))
    @Test fun pickDark() = screen("ladder-solo-pick", true, match(theirs = null))

    /** COLD (the computer's word) to WARM (yours): you played CORD, it played CARD, and it is your turn. */
    @Test fun playLight() = screen("ladder-solo-play", false, match("CORD", "CARD"))
    @Test fun playDark() = screen("ladder-solo-play", true, match("CORD", "CARD"))

    /** You spelled WARM. */
    @Test fun resultLight() = screen("ladder-solo-result", false, match("CORD", "CARD", "CART", "WART", "WARM"))
    @Test fun resultDark() = screen("ladder-solo-result", true, match("CORD", "CARD", "CART", "WART", "WARM"))
}
