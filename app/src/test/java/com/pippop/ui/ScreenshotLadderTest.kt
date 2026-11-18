package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.nearby.LocalRoom
import com.pippop.data.parseRoom
import com.pippop.ui.screens.LadderOptionsCard
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.SetupSheetContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.PipPopTheme
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

/**
 * Word Ladder on a 411 x 891 dp phone, light and dark: the setup, the secret pick (typing, then locked in), the coin
 * flip, the ladder on your turn, a refused move, and the result. Every position is made by the nearby room's own rules.
 */
class ScreenshotLadderTest : LadderTestBase() {
    @Before fun words() = LadderTable.installWords()

    /** Shows [harness], does what [then] does (taps, Maya's moves), then lets the motion settle and saves the picture. */
    private fun picture(name: String, dark: Boolean, harness: LadderHarness, then: () -> Unit = {}) {
        show(harness, dark)
        then()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    // ---- Setup --------------------------------------------------------------------------------------

    private fun lobby(): RoomState {
        val r = LocalRoom("LADR", "Sam", Avatar.FOX, GameKind.LADDER)
        r.join("Maya", Avatar.PANDA, null, listOf("ladder"))
        r.setOptions(LocalRoom.HOST, JSONObject().put("length", 5).put("timer", 30))
        return parseRoom(r.view(LocalRoom.HOST, 1_000_000L))
    }

    @Composable
    private fun setup(room: RoomState) {
        val controls = RoomControls(room, null, { LadderOptionsCard(room, false) {} }, rematch = {}, newGame = {}, switchTo = {}, games = emptyList(), setupOpen = mutableStateOf(true), busy = false)
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard { SetupSheetContent(room.game, controls, {}) }
        }
    }

    private fun setupShot(dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent { PipPopTheme(darkTheme = dark) { Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { setup(lobby()) } } }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/ladder-setup-${if (dark) "dark" else "light"}.png")
    }

    @Test fun setupLight() = setupShot(false)
    @Test fun setupDark() = setupShot(true)

    // ---- Pick ---------------------------------------------------------------------------------------

    /** Maya has picked; Sam has typed WARM and can lock it in. */
    private fun pick(dark: Boolean) {
        val h = LadderHarness(LadderTable())
        h.table.mayaPicks("CARD"); h.refresh()
        picture("ladder-pick", dark, h) { type("WARM") }
    }

    /** Sam has locked WARM in and waits for Maya. */
    private fun waiting(dark: Boolean) {
        val h = LadderHarness(LadderTable())
        picture("ladder-pick-waiting", dark, h) { type("WARM"); compose.onNodeWithText("Lock in WARM").performClick() }
    }

    @Test fun pickLight() = pick(false)
    @Test fun pickDark() = pick(true)
    @Test fun waitingLight() = waiting(false)
    @Test fun waitingDark() = waiting(true)

    // ---- The coin flip --------------------------------------------------------------------------------

    private fun flip(dark: Boolean) {
        val t = LadderTable()
        t.samPicks("WARM"); t.mayaPicks("COLD")
        picture("ladder-flip", dark, LadderHarness(t))
    }

    @Test fun flipLight() = flip(false)
    @Test fun flipDark() = flip(true)

    // ---- The ladder -----------------------------------------------------------------------------------

    /** COLD (Maya's word) to WARM (Sam's): Sam played CORD, Maya CARD, and it is Sam's turn on a 30-second clock. */
    private fun midGame(): LadderHarness {
        val t = LadderTable(timer = 30, now = System.currentTimeMillis())
        t.samPicks("WARM"); t.mayaPicks("COLD")
        t.samPlays("CORD"); t.mayaPlays("CARD")
        return LadderHarness(t)
    }

    private fun play(dark: Boolean) = picture("ladder-play", dark, midGame()) { change(0, 'C', 'W') }

    /** Sam picks X for the first letter: XARD is no word. */
    private fun invalid(dark: Boolean) = picture("ladder-invalid", dark, midGame()) {
        change(0, 'C', 'X')
        compose.onNodeWithText("Play").performClick()
    }

    @Test fun playLight() = play(false)
    @Test fun playDark() = play(true)
    @Test fun invalidLight() = invalid(false)
    @Test fun invalidDark() = invalid(true)

    // ---- The result -----------------------------------------------------------------------------------

    /** Sam's move spelled WARM after six words: the result card holds the whole ladder. */
    private fun result(dark: Boolean) {
        val t = LadderTable()
        t.samPicks("WARM"); t.mayaPicks("CARD")
        t.samPlays("CART"); t.mayaPlays("WART"); t.samPlays("WARM")
        picture("ladder-result", dark, LadderHarness(t))
    }

    @Test fun resultLight() = result(false)
    @Test fun resultDark() = result(true)
}
