package com.pippop.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.data.Profile
import com.pippop.game.clash.ClashMove
import com.pippop.game.clash.ClashView
import com.pippop.ui.screens.ClashTable
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Step 8: Kylian Dictador's card is just +8. The screen takes no take-over and plays no song: nothing locks the
 * controls, so the very next card is accepted at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ClashDictatorTest {
    @get:Rule
    val compose = createComposeRule()

    private val sounds = mutableListOf<Sound>()
    private val sfx = Sfx({ Profile() }, { s, _, _ -> sounds += s })

    private fun view(last: ClashMove?) = ClashView(
        top = "rK", colour = 'r', hand = listOf("r1", "r2"), counts = mapOf('m' to 2, 'o' to 9), order = listOf('m', 'o'),
        direction = 1, deck = 40, drawn = false, lastMove = last,
    )

    @Test fun theNextCardIsAcceptedAtOnceAfterTheDictatorIsPlayed() {
        Takeover.until.value = 0L
        compose.mainClock.autoAdvance = false
        var shown by mutableStateOf(view(null))
        val played = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalSfx provides sfx) {
                PipPopTheme(darkTheme = false) { Column { ClashTable(shown, true, true, "", { it.toString() }, { i, _ -> played += i }, {}, {}, buttons = false) } }
            }
        }
        compose.waitForIdle()
        // My Dictador lands: the other player draws eight and, as with any draw card, misses their turn; it is my move again.
        compose.runOnIdle { shown = view(ClashMove('m', "rK", null, 0, false, 'o' to 8)) }
        compose.waitForIdle()
        // Only the card's flight (a picture, not a lock) is let to land, so the tap hits the card where it sits.
        compose.mainClock.advanceTimeBy(1500)
        // No take-over: nothing is locked, no song plays, and the screen takes the tap with no waiting state.
        assertFalse(Takeover.locked())
        assertEquals(0L, Takeover.until.value)
        assertTrue(Sound.CARD_SLIDE in sounds)
        assertFalse(Sound.KYLIAN_DICTADOR in sounds)
        compose.onAllNodes(hasClickAction() and isEnabled()).onFirst().performClick()
        assertEquals(listOf(0), played)
        assertTrue(compose.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty())
    }
}
