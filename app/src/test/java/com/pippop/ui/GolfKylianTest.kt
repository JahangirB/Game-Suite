package com.pippop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.Pt
import com.pippop.ui.screens.Golf3DShot
import com.pippop.ui.screens.Golf3DState
import com.pippop.ui.screens.GolfPlay
import com.pippop.ui.theme.PipPopTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mini Golf step 9: Kylian's statue is a picture. When a ball stops on him, nobody's shot is held
 * back, by the statue or by his song: the player who landed there is skipped by the rules, not by a lock.
 */
class GolfKylianTest : ScreenshotBase() {
    private var last: Golf3DState? = null
    private var events: ((String) -> Unit)? = null

    @After fun reset() { Takeover.until.value = 0L }

    private val view = GolfView(
        hole = 0, holes = 9,
        strokes = mapOf('m' to listOf(0), 'o' to listOf(1)),
        balls = mapOf('m' to null, 'o' to Pt(0.5, 2.0)),
        done = mapOf('m' to false, 'o' to false),
        last = null,
    )

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                // It is my turn: the other player's ball has just stopped on Kylian.
                GolfPlay(view, 'm', true, "game", { _, _, _ -> }, Modifier, course = { state, _: Golf3DShot?, _, onEvent, _ -> record(state, onEvent) })
            }
        }
        compose.mainClock.advanceTimeBy(16)
    }

    @Composable
    private fun record(state: Golf3DState, onEvent: (String) -> Unit) {
        last = state
        events = onEvent
    }

    private fun step(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test fun theNextPlayerShootsWhileTheStatueIsUpAndAfter() {
        start()
        assertEquals(true, last!!.canShoot)
        compose.runOnIdle { events!!("kylian") }
        for (ms in listOf(16L, 16L, 1000L, 5000L, 6000L, 12_000L)) {
            step(ms)
            assertEquals("can shoot after ${ms}ms more", true, last!!.canShoot)
        }
    }

    /** Only that this rule's canShoot ignores a Kylian reaction; the sticker's own 11 s touch freeze is its design and stays. */
    @Test fun theGolfRulesCanShootIgnoresAKylianReaction() {
        start()
        Takeover.takeOver(com.pippop.data.Reaction.KYLIAN, System.currentTimeMillis())
        step(16)
        assertEquals(true, last!!.canShoot)
    }

    @Test fun theRulesStillSayWhoMayShoot() {
        // Not my turn (the caller says so): the statue changes nothing about that either way.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                GolfPlay(view, 'o', false, "game", { _, _, _ -> }, Modifier, course = { state, _: Golf3DShot?, _, onEvent, _ -> record(state, onEvent) })
            }
        }
        compose.mainClock.advanceTimeBy(16)
        compose.runOnIdle { events!!("kylian") }
        step(20_000)
        assertEquals(false, last!!.canShoot)
    }
}
