package com.pippop.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.game.words.LadderLevel
import com.pippop.game.words.WordLadder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

/** A whole game against the computer, run by the view model with the game's clock sped up. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SoloLadderViewModelTest {
    private val scheduler = TestCoroutineScheduler()

    @Before fun main() {
        LadderTable.installWords()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After fun reset() = Dispatchers.resetMain()

    private fun vm() = SoloLadderViewModel(Random(3)) { 900L }

    /** Lets the game's clock run until [done] (the computer's work happens on another thread, so it also waits). */
    private fun until(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!done() && System.currentTimeMillis() < end) {
            scheduler.advanceTimeBy(2_000)
            scheduler.runCurrent()
            if (!done()) Thread.sleep(5)
        }
        assertTrue(what, done())
    }

    @Test fun aSoloGameFromThePickToAResult() {
        for (level in LadderLevel.entries) {
            val vm = vm()
            vm.startWith(mapOf("level" to level.key, "length" to "4"))
            assertEquals("picking", vm.state.value.view!!.phase)
            vm.pick("WARM")
            // The computer picks after a moment; the pick is revealed with the coin flip.
            until("revealed") { vm.state.value.view!!.phase != "picking" }
            var guard = 0
            while (vm.state.value.view!!.phase != "over" && guard++ < 3000) {
                vm.flipDone()
                val v = vm.state.value.view!!
                if (v.turn == "me") {
                    // You play straight at the target: the legal move nearest it.
                    val near = WordLadder.distancesFrom(v.target!!)
                    vm.move(WordLadder.legalMoves(v.chain).minByOrNull { near[it] ?: 999 }!!)
                    until("moved") { vm.state.value.view!!.turn != "me" || vm.state.value.view!!.phase == "over" }
                } else {
                    until("computer moved") { vm.state.value.view!!.turn == "me" || vm.state.value.view!!.phase == "over" }
                }
            }
            val end = vm.state.value.view!!
            assertEquals("$level", "over", end.phase)
            assertNotNull(end.winner)
            assertTrue(end.reason == "target" || end.reason == "stuck")
            assertEquals("WARM".length, end.chain.first().length)
        }
    }

    @Test fun theComputerPicksAJoinedWordAndYourPickCannotBeChangedAfterLockingIn() {
        val vm = vm()
        vm.start(LadderLevel.MEDIUM, 4)
        vm.pick("WARM")
        vm.pick("COLD")
        until("revealed") { vm.state.value.view!!.phase != "picking" }
        val v = vm.state.value.view!!
        assertEquals("WARM", v.myPick)
        assertTrue(WordLadder.pathExists(v.start!!, v.target!!))
    }

    @Test fun aRefusedMoveIsReportedAndTheGameGoesOn() {
        val vm = vm()
        vm.start(LadderLevel.EASY, 4)
        vm.pick("WARM")
        until("revealed") { vm.state.value.view!!.phase != "picking" }
        vm.flipDone()
        until("my turn") { vm.state.value.view!!.turn == "me" }
        vm.move("ZZZZ")
        assertEquals("Change one letter", vm.state.value.problem)
        vm.dismissProblem()
        assertNull(vm.state.value.problem)
        assertEquals("me", vm.state.value.view!!.turn)
    }

    @Test fun badSetupValuesPlayTheUsualGame() {
        val vm = vm()
        vm.startWith(mapOf("level" to "nonsense", "length" to "99"))
        assertEquals(LadderLevel.MEDIUM, vm.state.value.level)
        assertEquals(5, vm.state.value.length)
    }

    private fun revealed(vm: SoloLadderViewModel) {
        vm.pick("WARM")
        until("revealed") { vm.state.value.view!!.phase != "picking" }
    }

    @Test fun aRematchWhereTheComputerMovesFirstGetsPastTheFlipAndTheComputerMoves() {
        val vm = vm()
        vm.start(LadderLevel.MEDIUM, 4)
        var tries = 0
        // Rematch until the coin gives the computer the first move.
        do {
            if (tries++ > 0) vm.rematch()
            revealed(vm)
        } while (vm.state.value.view!!.turn != "opponent" && tries < 40)
        assertEquals("opponent", vm.state.value.view!!.turn)
        val game = vm.state.value.gameId
        assertEquals(tries, game)
        assertEquals(1, vm.state.value.view!!.chain.size)
        // Nothing moves until the coin flip has been seen; then the computer moves.
        scheduler.advanceTimeBy(10_000); scheduler.runCurrent()
        assertEquals(1, vm.state.value.view!!.chain.size)
        vm.flipDone()
        until("the computer moved") { vm.state.value.view!!.chain.size > 1 }
    }

    @Test fun aRematchWhileTheComputerIsThinkingCancelsIt() {
        val vm = vm()
        vm.start(LadderLevel.EASY, 4)
        vm.pick("WARM")
        // The computer is still picking when the player starts again.
        vm.rematch()
        scheduler.advanceTimeBy(5_000); scheduler.runCurrent(); Thread.sleep(50); scheduler.runCurrent()
        val v = vm.state.value.view!!
        assertEquals("picking", v.phase)
        assertNull(v.myPick)
        assertEquals(false, v.opponentPicked)
    }

    @Test fun eachGameIsRecordedOnceAndARematchStartsAfresh() {
        val file = java.io.File.createTempFile("ladder", ".json")
        val store = com.pippop.data.SoloRecordStore(file)
        com.pippop.data.SoloRecordStore.installForTest(store)
        try {
            val key = com.pippop.data.SoloKey(com.pippop.data.GameKind.LADDER, "easy", com.pippop.data.SoloMetric.WINS_IN_ROW)
            val vm = vm()
            vm.start(LadderLevel.EASY, 4)
            repeat(2) { n ->
                if (n > 0) vm.rematch()
                revealed(vm)
                var guard = 0
                while (vm.state.value.view!!.phase != "over" && guard++ < 3000) {
                    vm.flipDone()
                    val v = vm.state.value.view!!
                    if (v.turn == "me") {
                        val near = WordLadder.distancesFrom(v.target!!)
                        vm.move(WordLadder.legalMoves(v.chain).minByOrNull { near[it] ?: 999 }!!)
                        until("moved") { vm.state.value.view!!.turn != "me" || vm.state.value.view!!.phase == "over" }
                    } else until("computer moved") { vm.state.value.view!!.turn == "me" || vm.state.value.view!!.phase == "over" }
                }
                assertEquals("over", vm.state.value.view!!.phase)
                assertEquals(n + 1, store.get(key)!!.played)
            }
        } finally {
            com.pippop.data.SoloRecordStore.installForTest(null)
            file.delete()
        }
    }
}
