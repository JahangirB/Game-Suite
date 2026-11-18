package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.center
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.nearby.LocalRoom
import com.pippop.data.parseRoom
import com.pippop.game.words.HuntDir
import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntMatch
import com.pippop.ui.screens.HuntOptionsCard
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.SetupSheetContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.PipPopTheme
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

/**
 * Word Hunt on a 411 x 891 dp phone, light and dark: the setup, the countdown over the covered grid, the grid with a drag in
 * progress and words from three players, the found-word moment, a big grid zoomed with its small map, and the result. Every position
 * is made by the nearby room's own rules.
 */
class ScreenshotHuntTest : HuntTestBase() {
    @Before fun words() = HuntTable.installWords()

    /** Shows [harness], does what [then] does (drags, other players' finds), then lets the motion settle and saves the picture. */
    private fun picture(name: String, dark: Boolean, harness: HuntHarness, then: () -> Unit = {}) {
        show(harness, dark)
        then()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    // ---- Setup --------------------------------------------------------------------------------------

    private fun lobby(): RoomState {
        val r = LocalRoom("HUNT", "Sam", Avatar.FOX, GameKind.HUNT)
        r.join("Maya", Avatar.PANDA, null, listOf("hunt"))
        r.setOptions(LocalRoom.HOST, JSONObject().put("level", "medium").put("backwards", true).put("timer", 5))
        return parseRoom(r.view(LocalRoom.HOST, 1_000_000L))
    }

    @Composable
    private fun setup(room: RoomState) {
        val controls = RoomControls(room, null, { HuntOptionsCard(room, false) {} }, rematch = {}, newGame = {}, switchTo = {}, games = emptyList(), setupOpen = mutableStateOf(true), busy = false)
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard { SetupSheetContent(room.game, controls, {}) }
        }
    }

    private fun setupShot(dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent { PipPopTheme(darkTheme = dark) { Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { setup(lobby()) } } }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/hunt-setup-${if (dark) "dark" else "light"}.png")
    }

    @Test fun setupLight() = setupShot(false)
    @Test fun setupDark() = setupShot(true)

    // ---- The countdown --------------------------------------------------------------------------------

    private fun countdown(dark: Boolean) {
        val t = HuntTable()
        t.now = t.t0 + 3500 // 1.5 s to go: "2"
        picture("hunt-countdown", dark, HuntHarness(t))
    }

    @Test fun countdownLight() = countdown(false)
    @Test fun countdownDark() = countdown(true)

    // ---- Playing --------------------------------------------------------------------------------------

    /** Sam has two words, Maya two, Priya one; 3:42 is left on the 5 minute clock. */
    private fun midGame(level: HuntLevel = HuntLevel.EASY, backwards: Boolean = false, finds: List<Pair<String, Int>>? = null): HuntHarness {
        val t = HuntTable(level = level, backwards = backwards)
        val w = t.words
        val who = listOf(LocalRoom.HOST, LocalRoom.GUEST, LocalRoom.P3)
        val plan = finds ?: listOf(w[0].word to 0, w[1].word to 1, w[2].word to 2, w[3].word to 0, w[5].word to 1).map { it.first to it.second }
        for ((word, i) in plan) t.claim(who[i], word)
        t.now = t.go + (5 * 60 - 222) * 1000L
        return HuntHarness(t)
    }

    /** A drag from the first letter of an unfound across word to part of the way along it, with the finger still down. */
    private fun dragging(h: HuntHarness) {
        val t = h.table
        val found = t.sam().turns!!.hunt!!.found.map { it.word }.toSet()
        val w = t.words.first { it.dir == HuntDir.RIGHT && it.word !in found }
        val (a, b) = HuntMatch.ends(w)
        val start = centre(compose, t, a.first, a.second)
        val end = centre(compose, t, b.first, b.second - 1)
        compose.onNodeWithTag("huntGrid").performTouchInput { down(start); moveTo((start + end) / 2f); moveTo(end + Offset(0f, 9f)) }
    }

    private fun playPicture(dark: Boolean) {
        val h = midGame()
        picture("hunt-play", dark, h) { dragging(h) }
    }

    @Test fun playLight() = playPicture(false)
    @Test fun playDark() = playPicture(true)

    // ---- A player's found words ------------------------------------------------------------------------------

    /** The play screen with the sheet a chip opens over it: [who] is the seat (0 is you, 1 Maya, 2 Priya). The sheet's own window is not in the root, so its card is drawn here. */
    private fun wordsSheet(name: String, dark: Boolean, who: Int) {
        val h = midGame()
        val vm = vm()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                androidx.compose.runtime.CompositionLocalProvider(com.pippop.ui.theme.LocalReduceMotion provides true) {
                    Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) {
                        h.Screen(vm)
                        val room = h.state.value.room!!
                        val view = room.turns!!.hunt!!
                        val seats = com.pippop.ui.screens.huntSeats(room, view, AppTheme.palette.me)
                        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
                            ToySheetCard { com.pippop.ui.screens.HuntWordsBody(view, seats[who]) }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    @Test fun yourWordsLight() = wordsSheet("hunt-words", false, 0)
    @Test fun yourWordsDark() = wordsSheet("hunt-words", true, 0)
    @Test fun mayasWordsLight() = wordsSheet("hunt-words-maya", false, 1)
    @Test fun mayasWordsDark() = wordsSheet("hunt-words-maya", true, 1)

    // ---- The found-word moment ---------------------------------------------------------------------------

    private fun found(dark: Boolean) {
        val h = midGame()
        picture("hunt-found", dark, h) {
            // Maya finishes a drag on a word: the bar glows in her colour and the line says so.
            compose.runOnIdle { h.table.claim(LocalRoom.GUEST, h.table.words[7].word); h.refresh() }
        }
    }

    @Test fun foundLight() = found(false)
    @Test fun foundDark() = found(true)

    // ---- A big grid, zoomed --------------------------------------------------------------------------------

    private fun zoomed(dark: Boolean) {
        val h = midGame(HuntLevel.HARD, true, null)
        picture("hunt-zoom", dark, h) {
            // The + button zooms to cells of 48 dp; two fingers then move the window to the right and down.
            compose.onNodeWithContentDescription("Zoom in").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("huntGrid").performTouchInput {
                val a = center + Offset(-60f, -60f)
                val b = center + Offset(60f, 60f)
                val d = Offset(-200f, -260f)
                pinch(a, b, a + d, b + d)
            }
        }
    }

    @Test fun zoomLight() = zoomed(false)
    @Test fun zoomDark() = zoomed(true)

    // ---- The result ------------------------------------------------------------------------------------------

    /** Every word found: Sam has the most, then Maya, then Priya. */
    private fun result(dark: Boolean) {
        val t = HuntTable()
        val who = listOf(LocalRoom.HOST, LocalRoom.HOST, LocalRoom.GUEST, LocalRoom.HOST, LocalRoom.GUEST, LocalRoom.HOST, LocalRoom.P3, LocalRoom.GUEST, LocalRoom.HOST, LocalRoom.P3)
        t.words.forEachIndexed { i, w -> t.claim(who[i % who.size], w.word) }
        t.now = t.go + 150_000L
        picture("hunt-result", dark, HuntHarness(t))
    }

    @Test fun resultLight() = result(false)
    @Test fun resultDark() = result(true)

    /** The round ends level: Sam and Maya have the same points and the card says it is a draw. */
    private fun draw(dark: Boolean) {
        val t = HuntTable(timer = 3, players = 2)
        // One word each of the same length (Sam the first, Maya the second), the rest not found.
        val pair = t.words.groupBy { it.length }.values.first { it.size >= 2 }
        t.claim(LocalRoom.HOST, pair[0].word)
        t.claim(LocalRoom.GUEST, pair[1].word)
        t.now = t.go + 3 * 60_000L + 1
        t.room.checkTimers(t.now)
        picture("hunt-draw", dark, HuntHarness(t))
    }

    @Test fun drawLight() = draw(false)
    @Test fun drawDark() = draw(true)
}
