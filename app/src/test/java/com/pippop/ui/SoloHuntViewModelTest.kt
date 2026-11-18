package com.pippop.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.data.GameKind
import com.pippop.data.SoloKey
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecordStore
import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntMatch
import com.pippop.game.words.HuntOptions
import com.pippop.game.words.HuntSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** Word Hunt alone, run by the view model on a virtual clock: the clock rounds and their records, and the computer finders' pace. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SoloHuntViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val file = File.createTempFile("hunt", ".json")
    private val store = SoloRecordStore(file)

    @Before fun main() {
        HuntTable.installWords()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        SoloRecordStore.installForTest(store)
    }

    @After fun reset() {
        Dispatchers.resetMain()
        SoloRecordStore.installForTest(null)
        file.delete()
    }

    private fun vm(seed: Int = 5) = SoloHuntViewModel(Random(seed)) { scheduler.currentTime }

    /** Moves the virtual clock to [ms] since the start of the test and lets what is due run. */
    private fun at(ms: Long) {
        scheduler.advanceTimeBy(ms - scheduler.currentTime)
        scheduler.runCurrent()
    }

    /** The grid a view model made with Random([seed]): its first random number is the grid's seed. */
    private fun twin(options: HuntOptions, seed: Int = 5, marks: List<Char> = listOf('m')) =
        HuntMatch(options.copy(seed = (Random(seed).nextDouble() * 0x7fffffff).toInt()), marks, startAt = SoloHuntViewModel.COUNTDOWN_MS)

    private fun SoloHuntViewModel.find(m: HuntMatch, word: String) {
        val (a, b) = HuntMatch.ends(m.grid.words.first { it.word == word })
        claim(a.first, a.second, b.first, b.second)
    }

    private fun found(vm: SoloHuntViewModel, by: Char? = null) = vm.state.value.view!!.words.count { it.foundBy != null && (by == null || it.foundBy == by) }

    @Test fun aClockRoundWithNoTimerEndsWhenEveryWordIsFoundAndKeepsTheFastestTime() {
        val options = HuntOptions(HuntLevel.EASY, "Animals", false, 0)
        val vm = vm()
        vm.start(HuntSoloMode.CLOCK, options)
        val grid = twin(options)
        assertEquals(grid.grid.rows, vm.state.value.view!!.rows)
        // The countdown: a claim is refused until it ends.
        at(2_000)
        vm.find(grid, grid.grid.words[0].word)
        assertEquals("Wait for the countdown.", vm.state.value.message)
        assertEquals(0, found(vm))
        vm.dismissMessage()
        // Words found a second and a half apart after the countdown; the time is that of the last one.
        at(6_000)
        grid.grid.words.forEachIndexed { i, w -> at(6_000L + i * 1_500); vm.find(grid, w.word) }
        assertTrue(vm.state.value.over)
        val last = 6_000L + (grid.grid.words.size - 1) * 1_500 - SoloHuntViewModel.COUNTDOWN_MS
        assertEquals(last, vm.state.value.finishedMs)
        val record = store.get(SoloKey(GameKind.HUNT, "easy", SoloMetric.TIME))!!
        assertEquals(last, record.best)
        assertEquals(1, record.played)
    }

    @Test fun aTimedRoundEndsWhenTimeIsUpAndKeepsTheMostPointsForThatTimer() {
        val options = HuntOptions(HuntLevel.EASY, "Animals", false, 3)
        val vm = vm()
        vm.start(HuntSoloMode.CLOCK, options)
        val grid = twin(options)
        at(10_000)
        val two = grid.grid.words.take(2)
        two.forEach { vm.find(grid, it.word) }
        assertEquals(two.sumOf { it.length }, vm.state.value.view!!.scores['m'])
        // Not over a moment before the three minutes (counted from the end of the countdown) are up, over a moment after.
        at(5_000L + 3 * 60_000 - 300)
        assertTrue(!vm.state.value.over)
        at(5_000L + 3 * 60_000 + 300)
        assertTrue(vm.state.value.over)
        assertEquals("time", vm.state.value.view!!.reason)
        val key = SoloKey(GameKind.HUNT, "easy-3", SoloMetric.POINTS)
        assertEquals(two.sumOf { it.length }.toLong(), store.get(key)!!.best)
        assertEquals(1, store.get(key)!!.played)
        // A claim after the end is refused and records nothing more.
        vm.find(grid, grid.grid.words[5].word)
        assertEquals(1, store.get(key)!!.played)
    }

    @Test fun aHintAddsTenSecondsToATimeWithNoTimer() {
        val options = HuntOptions(HuntLevel.EASY, "Animals", false, 0)
        val vm = vm()
        vm.start(HuntSoloMode.CLOCK, options)
        val grid = twin(options)
        at(6_000)
        vm.hint()
        grid.grid.words.forEach { vm.find(grid, it.word) }
        assertEquals(1_000L + SoloHuntViewModel.HINT_PENALTY_MS, vm.state.value.finishedMs)
    }

    @Test fun computerFindersClaimAtTheirPaceNeverDuringTheCountdown() {
        val options = HuntOptions(HuntLevel.EASY, "Animals", false, 0)
        val vm = vm()
        vm.start(HuntSoloMode.COMPUTERS, options, 1, HuntSkill.MEDIUM)
        // Nothing in the countdown, nor in the first 15 seconds after it (Medium waits 15 to 20 s between finds).
        at(4_999)
        assertEquals(0, found(vm))
        at(5_000 + 14_900)
        assertEquals(0, found(vm))
        // The first find is in by 20 s after the countdown, the second not before 30 s.
        at(5_000 + 20_000)
        assertEquals(1, found(vm, 'a'))
        at(5_000 + 29_900)
        assertEquals(1, found(vm, 'a'))
        at(5_000 + 40_000)
        assertEquals(2, found(vm, 'a'))
        assertEquals(vm.state.value.view!!.words.filter { it.foundBy == 'a' }.sumOf { it.length }, vm.state.value.view!!.scores['a'])
    }

    @Test fun eachSkillKeepsToItsPaceOverAWholeRound() {
        for ((skill, range) in mapOf(HuntSkill.EASY to 25_000L..35_000L, HuntSkill.MEDIUM to 15_000L..20_000L, HuntSkill.HARD to 8_000L..12_000L)) {
            val vm = vm(7)
            vm.start(HuntSoloMode.COMPUTERS, HuntOptions(HuntLevel.EASY, "Animals", false, 0), 1, skill)
            val t0 = scheduler.currentTime
            val times = mutableListOf<Long>()
            var seen = 0
            var t = 5_000L
            while (!vm.state.value.over && t < 5_000L + 10 * 36_000L) {
                t += 100
                at(t0 + t)
                val n = found(vm)
                if (n > seen) { times += t; seen = n }
            }
            assertTrue("$skill finishes", vm.state.value.over)
            val gaps = (listOf(5_000L) + times).zipWithNext { a, b -> b - a }
            // Each gap is within the skill's range, to the 100 ms the loop looks in.
            assertTrue("$skill $gaps", gaps.all { it in (range.first - 100)..(range.last + 100) })
        }
    }

    @Test fun finishersStopWhenTheRoundIsOverAndEasyGoesForShortWordsFirst() {
        val options = HuntOptions(HuntLevel.EASY, "Animals", false, 3)
        val vm = vm()
        vm.start(HuntSoloMode.COMPUTERS, options, 2, HuntSkill.EASY)
        at(5_000 + 3 * 60_000 + 500)
        assertTrue(vm.state.value.over)
        val after = found(vm)
        // Easy takes the shorter half of what is left each time: the first finds are among the five shortest words.
        val view = vm.state.value.view!!
        val shortest = twin(options, 5, listOf('m', 'a', 'b')).grid.words.sortedBy { it.length }.take(5).map { it.word }.toSet()
        assertTrue("found ${view.words.filter { it.foundBy != null }.map { it.word }}", view.words.filter { it.foundBy != null }.take(2).all { it.word in shortest } || after <= 2)
        // Long after the end nothing more is claimed and nothing is recorded twice.
        at(5_000 + 3 * 60_000 + 200_000)
        assertEquals(after, found(vm))
        assertEquals(1, store.get(SoloKey(GameKind.HUNT, "vs-easy", SoloMetric.WINS_IN_ROW))!!.played)
        assertTrue(vm.finderJobs().none { it.isActive })
    }

    @Test fun aRematchWhileAComputerIsWaitingCancelsItsJobAndShowsTheCountdownAgain() {
        val vm = vm()
        vm.start(HuntSoloMode.COMPUTERS, HuntOptions(HuntLevel.EASY, "Animals", false, 0), 2, HuntSkill.HARD)
        at(5_000 + 5_000)
        val old = vm.finderJobs()
        assertTrue(old.all { it.isActive })
        val id = vm.state.value.gameId
        vm.rematch()
        assertTrue("the old finders were cancelled", old.all { it.isCancelled })
        assertEquals(id + 1, vm.state.value.gameId)
        assertEquals(2, vm.finderJobs().count { it.isActive })
        // The round starts again from its countdown: nothing found, and the time is back at the start.
        assertEquals(0, found(vm))
        assertEquals(0L, vm.gameTime())
        at(scheduler.currentTime + 4_000)
        assertEquals(0, found(vm))
        assertEquals(4_000L, vm.gameTime())
    }

    @Test fun theRoundWaitsWhileTheAppIsInTheBackgroundOrNotOnScreen() {
        val vm = vm()
        vm.start(HuntSoloMode.COMPUTERS, HuntOptions(HuntLevel.EASY, "Animals", false, 5), 1, HuntSkill.HARD)
        at(5_000 + 3_000)
        vm.setForeground(false)
        val held = vm.gameTime()
        at(5_000 + 3_000 + 120_000)
        assertEquals(held, vm.gameTime())
        assertEquals(0, found(vm))
        vm.setForeground(true)
        vm.setOnScreen(false)
        at(scheduler.currentTime + 60_000)
        assertEquals(held, vm.gameTime())
        vm.setOnScreen(true)
        // It goes on from where it was: a Hard finder claims within 12 s of game time.
        at(scheduler.currentTime + 13_000)
        assertEquals(held + 13_000, vm.gameTime())
        assertTrue(found(vm) >= 1)
    }

    @Test fun winsAgainstComputersAreARunPerSkill() {
        val options = HuntOptions(HuntLevel.EASY, "Animals", false, 0)
        val vm = vm()
        vm.start(HuntSoloMode.COMPUTERS, options, 1, HuntSkill.EASY)
        val grid = twin(options, 5, listOf('m', 'a'))
        at(5_100)
        grid.grid.words.forEach { vm.find(grid, it.word) }
        assertTrue(vm.state.value.over)
        assertEquals('m', vm.state.value.view!!.winner)
        val key = SoloKey(GameKind.HUNT, "vs-easy", SoloMetric.WINS_IN_ROW)
        assertEquals(1, store.get(key)!!.run)
        // A game the computer wins takes the run back to nothing.
        vm.rematch()
        at(scheduler.currentTime + 5_000 + 36_000 * 11)
        assertEquals(0, store.get(key)!!.run)
        assertEquals(2, store.get(key)!!.played)
    }
}
