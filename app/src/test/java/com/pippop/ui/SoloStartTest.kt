package com.pippop.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.game.code.CodeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the sheet's choices mean to each game when it starts. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SoloStartTest {
    @Test fun dominoesTeamsOnlyWithThreeComputers() {
        val two = SoloDominoesViewModel().apply { startWith(mapOf("opponents" to "2", "teams" to "teams", "draw" to "block", "target" to "150")) }
        assertEquals(2, two.state.value.opponents)
        assertFalse(two.state.value.options.teams)
        assertFalse(two.state.value.options.draw)
        assertEquals(150, two.state.value.options.target)
        val three = SoloDominoesViewModel().apply { startWith(mapOf("opponents" to "3", "teams" to "teams")) }
        assertTrue(three.state.value.options.teams)
        assertTrue(three.state.value.view != null)
    }

    @Test fun golfAloneHasNoComputersAndKeepsTheLevel() {
        val vm = SoloGolfViewModel().apply { startWith(mapOf("level" to "hard", "computers" to "0", "skill" to "hard")) }
        assertEquals(0, vm.state.value.computers)
        assertEquals(com.pippop.game.golf.GolfLevel.HARD, vm.state.value.level)
        assertTrue(vm.state.value.view != null)
    }

    @Test fun bingoPaceAndComputers() {
        val vm = SoloBingoViewModel().apply { startWith(mapOf("computers" to "3", "pace" to "fast")) }
        assertEquals(3, vm.state.value.computers)
        assertEquals(BingoPace.FAST, vm.state.value.pace)
    }

    @Test fun codeRules() {
        val vm = SoloCodeViewModel().apply { startWith(mapOf("pegs" to "5", "colours" to "7", "repeats" to "no")) }
        assertEquals(CodeRules(pegs = 5, colours = 7, repeats = false), vm.state.value.rules)
        assertTrue(vm.state.value.started)
    }

    @Test fun unknownValuesPlayTheUsualGame() {
        val vm = SoloClashViewModel().apply { startWith(mapOf("opponents" to "99")) }
        assertEquals(1, vm.state.value.opponents)
        val blocks = SoloBlocksViewModel().apply { startWith(mapOf("skill" to "nonsense")) }
        assertEquals(1, blocks.state.value.skill)
    }

    @Test fun theFirstTicTacToeGameIsOpenedByYou() {
        val vm = SoloTttViewModel().apply { startWith(mapOf("mode" to "classic")) }
        assertTrue(vm.state.value.myTurn)
    }
}
