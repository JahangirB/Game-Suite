package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.pippop.data.GameKind
import com.pippop.data.SoloSetups
import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntMatch
import com.pippop.game.words.HuntOptions
import com.pippop.game.words.HuntSkill
import com.pippop.game.words.HuntView
import com.pippop.ui.screens.SoloHuntScreen
import com.pippop.ui.screens.SoloSetupContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.PipPopTheme
import org.junit.Before
import org.junit.Test

/** Word Hunt on your own, light and dark: the setup, and a round against two computer finders with finds from all three. */
class ScreenshotHuntSoloTest : ScreenshotBase() {
    @Before fun words() = HuntTable.installWords()

    private fun setup(name: String, values: Map<String, String>, dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
                    ToySheetCard {
                        SoloSetupContent(
                            GameKind.HUNT, SoloSetups.resolve(GameKind.HUNT, values),
                            onPick = { _, _ -> }, startLabel = "Play solo · " + SoloSetups.spec(GameKind.HUNT)!!.buttonText(SoloSetups.resolve(GameKind.HUNT, values)), onStart = {}, onBack = {},
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    private val computersSetup = mapOf("mode" to "computers", "level" to "medium", "theme" to "Food", "backwards" to "auto", "timer" to "5", "computers" to "2", "skill" to "medium")

    @Test fun setupComputersLight() = setup("hunt-solo-setup", computersSetup, false)
    @Test fun setupComputersDark() = setup("hunt-solo-setup", computersSetup, true)
    @Test fun setupClockLight() = setup("hunt-solo-setup-clock", mapOf("mode" to "clock", "level" to "hard", "timer" to "0"), false)
    @Test fun setupClockDark() = setup("hunt-solo-setup-clock", mapOf("mode" to "clock", "level" to "hard", "timer" to "0"), true)

    /** Two minutes into a 5 minute round: you have found two words, Computer A two and Computer B one. */
    private fun round(dark: Boolean) {
        val options = HuntOptions(HuntLevel.EASY, "Animals", false, 5, 0)
        val m = HuntMatch(options, listOf('m', 'a', 'b'), startAt = 5_000)
        val w = m.grid.words
        fun claim(mark: Char, i: Int) { val (s, e) = HuntMatch.ends(w[i]); m.claim(mark, s.first, s.second, e.first, e.second, 30_000) }
        claim('m', 0); claim('a', 1); claim('b', 2); claim('m', 3); claim('a', 5)
        val state = SoloHuntUiState(HuntSoloMode.COMPUTERS, m.options, 2, HuntSkill.MEDIUM, HuntView.from(m.view('m', emptyMap())), gameId = 1)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { SoloHuntScreen(state, SoloHuntViewModel(), onHome = {}, clock = { 125_000L }) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/hunt-solo-round-${if (dark) "dark" else "light"}.png")
    }

    @Test fun roundLight() = round(false)
    @Test fun roundDark() = round(true)
}
