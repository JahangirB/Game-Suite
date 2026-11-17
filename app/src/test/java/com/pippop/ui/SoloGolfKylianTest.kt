package com.pippop.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.game.golf.ShotKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Mini Golf on your own against a computer: a Kylian takeover on this phone never holds the computer back. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SoloGolfKylianTest {
    @After fun reset() {
        Takeover.until.value = 0L
        Dispatchers.resetMain()
    }

    @Test fun theComputerShootsWithoutWaitingForKylian() {
        val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val vm = SoloGolfViewModel()
        vm.setComputers(1)
        vm.start()
        // Kylian's takeover is on (a reaction, or his statue): it must not stop the computer.
        Takeover.until.value = System.currentTimeMillis() + 11_000
        vm.shoot(ShotKind.ROLL, 0.4, 0.0)
        var computerPlayed = false
        val end = System.currentTimeMillis() + 5_000
        // Ten seconds of the game's clock go by at once (the computer thinks, then shoots).
        scheduler.advanceTimeBy(10_000)
        while (!computerPlayed && System.currentTimeMillis() < end) {
            scheduler.runCurrent()
            computerPlayed = vm.state.value.view?.last?.who == 'o'
            if (!computerPlayed) Thread.sleep(20)
        }
        assertEquals("the computer shot within 10 s of game time", true, computerPlayed)
    }
}
