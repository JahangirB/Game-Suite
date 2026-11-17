package com.pippop.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.data.Profile
import com.pippop.game.BoardState
import com.pippop.game.bingo.Bingo
import com.pippop.game.clash.ClashMove
import com.pippop.game.clash.ClashView
import com.pippop.game.ludo.LudoMove
import com.pippop.game.ludo.LudoView
import com.pippop.game.ships.Ship
import com.pippop.game.ships.Shot
import com.pippop.ui.screens.BingoCard
import com.pippop.ui.screens.ClashTable
import com.pippop.ui.screens.ConnectFourBoard
import com.pippop.ui.screens.DieFace
import com.pippop.ui.screens.LudoBoard
import com.pippop.ui.screens.RecordLine
import com.pippop.ui.screens.ShipsSea
import com.pippop.ui.screens.SudokuGrid
import com.pippop.ui.screens.TttBoard
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Step 32: the five new buzzes. The patterns are PLAN table 9.5 to the letter, phones without amplitude control keep the
 * rhythm, and each wired moment buzzes once (not on a recomposition or a rebuilt screen, and not when Vibration is off).
 * The screens run in Robolectric with a fake vibrator in place of the phone's (see [LocalHaptics]).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class BuzzFeelTest {
    @get:Rule
    val compose = createComposeRule()

    private class Fake : BuzzOut {
        var control = true
        val buzzed = mutableListOf<Buzz>()
        val waves = mutableListOf<Wave?>()
        override val amplitudeControl get() = control
        override fun play(buzz: Buzz, wave: Wave?) { buzzed += buzz; waves += wave }
    }

    private val out = Fake()
    private var profile = Profile()
    private val haptics = Haptics({ profile }, out)
    private val sfx = Sfx({ profile }, { _, _, _ -> })
    private var noise by mutableIntStateOf(0)
    private val tester by lazy { StateRestorationTester(compose) }

    @Before fun fresh() {
        resetSoundOnce()
        compose.mainClock.autoAdvance = false
    }

    private fun count(b: Buzz) = out.buzzed.count { it == b }

    private fun show(content: @Composable () -> Unit) {
        tester.setContent {
            CompositionLocalProvider(LocalSfx provides sfx, LocalHaptics provides haptics) {
                PipPopTheme(darkTheme = false) { Column { Text("$noise"); content() } }
            }
        }
        settle()
    }

    private fun settle(ms: Long = 2000) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun recomposeQuietly() {
        compose.runOnIdle { noise += 1 }
        settle()
    }

    private fun rebuilt(content: @Composable () -> Unit) {
        show(content)
        tester.emulateSavedInstanceStateRestore()
        settle()
    }

    // ---- the patterns ---------------------------------------------------------------------------

    private val table = mapOf(
        Buzz.HIT to listOf(60L to 1f),
        Buzz.LINE_DONE to listOf(14L to 0.5f, 60L to 0f, 14L to 0.5f),
        Buzz.DICE to listOf(10L to 0.35f, 26L to 0f, 10L to 0.35f, 26L to 0f, 10L to 0.35f, 26L to 0f, 10L to 0.35f, 34L to 0f, 24L to 0.8f),
        Buzz.ONE_AWAY to listOf(28L to 0.6f, 80L to 0f, 40L to 1f, 200L to 0f, 28L to 0.6f, 80L to 0f, 40L to 1f),
        Buzz.RECORD to listOf(22L to 0.45f, 50L to 0f, 44L to 1f),
    )

    @Test fun thePatternsAreTable95ToTheLetter() {
        for ((buzz, steps) in table) assertEquals("$buzz", steps, buzz.pattern)
        assertEquals("only the five new ones have a pattern", table.keys, Buzz.entries.filter { it.pattern != null }.toSet())
    }

    @Test fun aWaveformStartsWithNoDelayThenEachStepInOrderWithStrengthAs0To255() {
        val hit = Buzz.HIT.wave(true)!!
        assertArrayEquals(longArrayOf(0, 60), hit.timings)
        assertArrayEquals(intArrayOf(0, 255), hit.amplitudes)
        val line = Buzz.LINE_DONE.wave(true)!!
        assertArrayEquals(longArrayOf(0, 14, 60, 14), line.timings)
        assertArrayEquals(intArrayOf(0, 128, 0, 128), line.amplitudes)
        val dice = Buzz.DICE.wave(true)!!
        assertArrayEquals(longArrayOf(0, 10, 26, 10, 26, 10, 26, 10, 34, 24), dice.timings)
        assertArrayEquals(intArrayOf(0, 89, 0, 89, 0, 89, 0, 89, 0, 204), dice.amplitudes)
        val away = Buzz.ONE_AWAY.wave(true)!!
        assertArrayEquals(longArrayOf(0, 28, 80, 40, 200, 28, 80, 40), away.timings)
        assertArrayEquals(intArrayOf(0, 153, 0, 255, 0, 153, 0, 255), away.amplitudes)
        val record = Buzz.RECORD.wave(true)!!
        assertArrayEquals(longArrayOf(0, 22, 50, 44), record.timings)
        assertArrayEquals(intArrayOf(0, 115, 0, 255), record.amplitudes)
    }

    @Test fun everyPauseIsOffAndEveryOtherStepIsOn() {
        for ((buzz, steps) in table) {
            val w = buzz.wave(true)!!
            steps.forEachIndexed { i, (_, strength) ->
                assertEquals("$buzz step $i is a pause only where the table says 0", strength == 0f, w.amplitudes!![i + 1] == 0)
                assertEquals("$buzz alternates on and off", i % 2 == 1, strength == 0f)
            }
        }
    }

    @Test fun phonesWithoutAmplitudeControlKeepTheRhythmAndDropTheStrengths() {
        for (buzz in table.keys) {
            val full = buzz.wave(true)!!
            val plain = buzz.wave(false)!!
            assertArrayEquals("$buzz", full.timings, plain.timings)
            assertNull("$buzz", plain.amplitudes)
        }
    }

    @Test fun theOlderBuzzesHaveNoWaveformOfTheirOwn() {
        listOf(Buzz.MOVE, Buzz.YOUR_TURN, Buzz.NOT_ALLOWED, Buzz.COUNTDOWN, Buzz.WIN, Buzz.LOSE).forEach { assertNull(it.wave(true)) }
    }

    @Test fun hapticsHandsThePhoneTheFallbackWaveformWhenThereIsNoAmplitudeControl() {
        out.control = false
        haptics.play(Buzz.RECORD)
        assertNull(out.waves.single()!!.amplitudes)
        out.control = true
        haptics.play(Buzz.RECORD)
        assertNotNull(out.waves.last()!!.amplitudes)
    }

    @Test fun vibrationOffMeansNoBuzzAtAll() {
        profile = Profile(vibration = false)
        Buzz.entries.forEach { haptics.play(it) }
        assertTrue(out.buzzed.isEmpty())
        profile = Profile(vibration = true)
        haptics.play(Buzz.HIT)
        assertEquals(listOf(Buzz.HIT), out.buzzed)
    }

    // ---- the moments ----------------------------------------------------------------------------

    private val solvedRow = listOf(5, 3, 4, 6, 7, 8, 9, 1, 2)
    private fun sudoku(filled: Int) = BoardState(List(81) { if (it < filled) solvedRow[it] else 0 }, List(81) { false })

    @Test fun aFinishedSudokuUnitBuzzesOnce() {
        var board by mutableStateOf(sudoku(8))
        show { SudokuGrid(board, emptySet(), null, false, null, {}, celebrate = true) }
        assertEquals(0, count(Buzz.LINE_DONE))
        compose.runOnIdle { board = sudoku(9) }
        settle()
        assertEquals(1, count(Buzz.LINE_DONE))
        recomposeQuietly()
        assertEquals(1, count(Buzz.LINE_DONE))
    }

    @Test fun aSudokuUnitAlreadyFullWhenShownIsSilent() {
        rebuilt { SudokuGrid(sudoku(9), emptySet(), null, false, null, {}, celebrate = true) }
        assertEquals(0, count(Buzz.LINE_DONE))
    }

    @Test fun aUnitAnotherPlayerFinishesDoesNotBuzz() {
        var board by mutableStateOf(sudoku(8))
        show { SudokuGrid(board, emptySet(), null, false, null, {}, celebrate = true, placedByOther = { it == 8 }) }
        compose.runOnIdle { board = sudoku(9) }
        settle()
        assertEquals(0, count(Buzz.LINE_DONE))
    }

    private fun discs(mark: Char) = ".".repeat(35) + "$mark$mark$mark$mark" + "..."

    @Test fun fourInARowBuzzesOnceAndTheirsDoesNot() {
        var line by mutableStateOf(emptyList<Int>())
        show { ConnectFourBoard(discs('m'), line, 38, null) }
        compose.runOnIdle { line = listOf(35, 36, 37, 38) }
        settle()
        assertEquals(1, count(Buzz.LINE_DONE))
        recomposeQuietly()
        assertEquals(1, count(Buzz.LINE_DONE))
    }

    @Test fun theirFourInARowDoesNotBuzz() {
        var line by mutableStateOf(emptyList<Int>())
        show { ConnectFourBoard(discs('o'), line, 38, null) }
        compose.runOnIdle { line = listOf(35, 36, 37, 38) }
        settle()
        assertEquals(0, count(Buzz.LINE_DONE))
    }

    @Test fun aFinishedConnectFourShownAgainIsSilent() {
        rebuilt { ConnectFourBoard(discs('m'), listOf(35, 36, 37, 38), 38, null) }
        assertEquals(0, count(Buzz.LINE_DONE))
    }

    @Test fun aSmallBoardWonBuzzesOnceForYouAndNotForThem() {
        var boards by mutableStateOf(".........")
        var board by mutableStateOf(".".repeat(81))
        var last by mutableStateOf<Int?>(null)
        show { TttBoard(board, boards, null, emptyList(), last, true, null) }
        compose.runOnIdle { boards = "dm......."; last = 9; board = "..m......m" + ".".repeat(71) }
        settle()
        assertEquals(1, count(Buzz.LINE_DONE))
        recomposeQuietly()
        assertEquals(1, count(Buzz.LINE_DONE))
        compose.runOnIdle { boards = "dmo......"; last = 20; board = "..m......m.........o" + ".".repeat(61) }
        settle()
        assertEquals("theirs is seen and heard, not felt", 1, count(Buzz.LINE_DONE))
    }

    @Test fun aBingoLineBuzzesOnceAndAOneAwayCardBuzzesItsOwnBuzz() {
        val card = Bingo.dealCard(Random(1))
        var dabs by mutableStateOf(setOf(0, 1, 2))
        show { BingoCard(card, dabs, null, true, {}) }
        compose.runOnIdle { dabs = setOf(0, 1, 2, 3) }
        settle()
        assertEquals(1, count(Buzz.ONE_AWAY))
        assertEquals(0, count(Buzz.LINE_DONE))
        recomposeQuietly()
        assertEquals(1, count(Buzz.ONE_AWAY))
        compose.runOnIdle { dabs = setOf(0, 1, 2, 3, 4) }
        settle()
        assertEquals(1, count(Buzz.LINE_DONE))
        recomposeQuietly()
        assertEquals(1, count(Buzz.LINE_DONE))
        assertEquals(1, count(Buzz.ONE_AWAY))
    }

    @Test fun aBingoCardShownOneAwayIsSilent() {
        rebuilt { BingoCard(Bingo.dealCard(Random(1)), setOf(0, 1, 2, 3), null, true, {}) }
        assertEquals(0, count(Buzz.ONE_AWAY))
    }

    private fun clash(mine: Int, theirs: Int, move: ClashMove?) = ClashView(
        top = "r3", colour = 'r', hand = List(mine) { "b$it" }, counts = mapOf('m' to mine, 'o' to theirs), order = listOf('m', 'o'),
        direction = 1, deck = 40, drawn = false, lastMove = move,
    )

    @Test fun yourLastCardBuzzesOnceAndTheirsDoesNot() {
        var view by mutableStateOf(clash(5, 2, null))
        show { Column { ClashTable(view, true, true, "", { it.toString() }, { _, _ -> }, {}, {}) } }
        compose.runOnIdle { view = clash(5, 1, ClashMove('o', "r2", null, 0, false, null)) }
        settle()
        assertEquals(0, count(Buzz.ONE_AWAY))
        compose.runOnIdle { view = clash(1, 1, ClashMove('m', "r4", null, 0, false, null)) }
        settle()
        assertEquals(1, count(Buzz.ONE_AWAY))
        recomposeQuietly()
        assertEquals(1, count(Buzz.ONE_AWAY))
    }

    @Test fun aClashTableShownWithYouOnOneCardIsSilent() {
        rebuilt { Column { ClashTable(clash(1, 5, null), true, true, "", { it.toString() }, { _, _ -> }, {}, {}) } }
        assertEquals(0, count(Buzz.ONE_AWAY))
    }

    @Test fun theRecordLineBuzzesOncePerRecord() {
        var text by mutableStateOf("Your quickest win at Connect Four: 11 discs")
        show { RecordLine(text) }
        assertEquals(1, count(Buzz.RECORD))
        recomposeQuietly()
        tester.emulateSavedInstanceStateRestore()
        settle()
        assertEquals(1, count(Buzz.RECORD))
        compose.runOnIdle { text = "Your quickest win at Connect Four: 9 discs" }
        settle()
        assertEquals(2, count(Buzz.RECORD))
    }

    @Test fun aHitOnTheirSeaBuzzesOnceAndAMissOrTheirShotDoesNot() {
        var shot by mutableStateOf<Shot?>(null)
        show { ShipsSea(emptyList(), listOfNotNull(shot), lastShot = shot, enemy = true) }
        compose.runOnIdle { shot = Shot(3, 3, false) }
        settle()
        assertEquals(0, count(Buzz.HIT))
        compose.runOnIdle { shot = Shot(4, 3, true) }
        settle()
        assertEquals(1, count(Buzz.HIT))
        recomposeQuietly()
        assertEquals(1, count(Buzz.HIT))
    }

    @Test fun theHitThatSinksTheLastShipDoesNotBuzz() {
        val ship = Ship(0, 0, 2, true)
        val other = Ship(0, 5, 2, true)
        var sunk by mutableStateOf(emptyList<Ship>())
        var shot by mutableStateOf<Shot?>(null)
        show { ShipsSea(listOf(ship, other), listOfNotNull(shot), sunk = sunk, lastShot = shot, enemy = true) }
        compose.runOnIdle { sunk = listOf(ship); shot = Shot(1, 0, true) }
        settle()
        assertEquals("a ship sinks but the game goes on", 1, count(Buzz.HIT))
        compose.runOnIdle { sunk = listOf(ship, other); shot = Shot(1, 5, true) }
        settle()
        assertEquals("the last ship: the win buzz follows", 1, count(Buzz.HIT))
    }

    @Test fun theSmallBoardThatWinsTheGameDoesNotBuzz() {
        var boards by mutableStateOf(".........")
        var board by mutableStateOf(".".repeat(81))
        var last by mutableStateOf<Int?>(null)
        var win by mutableStateOf(emptyList<Int>())
        show { TttBoard(board, boards, null, win, last, true, null) }
        compose.runOnIdle { boards = "mmm......"; last = 9; win = listOf(0, 1, 2); board = "..m......m" + ".".repeat(71) }
        settle()
        assertEquals(0, count(Buzz.LINE_DONE))
    }

    @Test fun theirHitOnYourSeaDoesNotBuzz() {
        var shot by mutableStateOf<Shot?>(null)
        show { ShipsSea(emptyList(), listOfNotNull(shot), lastShot = shot) }
        compose.runOnIdle { shot = Shot(4, 3, true) }
        settle()
        assertEquals(0, count(Buzz.HIT))
    }

    private fun ludo(move: LudoMove?) = LudoView(
        colours = mapOf('m' to 0, 'o' to 2),
        tokens = mapOf('m' to listOf(8, -1, -1, -1), 'o' to listOf(34, -1, -1, -1)),
        roll = null, movable = emptyList(), lastMove = move,
    )

    @Test fun yourCaptureInLudoBuzzesOnceAndTheirsDoesNot() {
        var view by mutableStateOf(ludo(null))
        show { LudoBoard(view, onToken = null) }
        compose.runOnIdle { view = ludo(LudoMove('o', 3, 0, 5, 8, listOf('m'), false, false)) }
        settle()
        assertEquals(0, count(Buzz.HIT))
        compose.runOnIdle { view = ludo(LudoMove('m', 3, 0, 5, 8, listOf('o'), false, false)) }
        settle()
        assertEquals(1, count(Buzz.HIT))
        recomposeQuietly()
        assertEquals(1, count(Buzz.HIT))
    }

    @Test fun aLudoRollBuzzesOnceAndOnlyYours() {
        var n by mutableStateOf<Int?>(4)
        var rolled by mutableStateOf<Any?>("first")
        var mine by mutableStateOf(true)
        show { DieFace(n, rolled = rolled, buzzes = mine) }
        assertEquals("the die shown as it is is not a roll", 0, count(Buzz.DICE))
        compose.runOnIdle { n = 6; rolled = "second" }
        settle()
        assertEquals(1, count(Buzz.DICE))
        recomposeQuietly()
        assertEquals(1, count(Buzz.DICE))
        compose.runOnIdle { mine = false; n = 2; rolled = "third" }
        settle()
        assertEquals("another player's throw is seen, not felt", 1, count(Buzz.DICE))
    }

    @Test fun vibrationOffSilencesTheWiredMoments() {
        profile = Profile(vibration = false)
        var line by mutableStateOf(emptyList<Int>())
        show {
            RecordLine("Your quickest win at Connect Four: 11 discs")
            ConnectFourBoard(discs('m'), line, 38, null)
        }
        compose.runOnIdle { line = listOf(35, 36, 37, 38) }
        settle()
        assertTrue(out.buzzed.isEmpty())
    }
}
