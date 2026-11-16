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
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.Loudness
import com.pippop.data.MatchRecord
import com.pippop.data.MonthEnd
import com.pippop.data.Profile
import com.pippop.game.BoardState
import com.pippop.game.bingo.Bingo
import com.pippop.game.clash.ClashMove
import com.pippop.game.clash.ClashView
import com.pippop.game.dominoes.DominoMove
import com.pippop.game.dominoes.DominoView
import com.pippop.game.dominoes.Tile
import com.pippop.game.memory.CardFace
import com.pippop.game.slide.Slide
import com.pippop.ui.screens.BingoCard
import com.pippop.ui.screens.ClashTable
import com.pippop.ui.screens.ConnectFourBoard
import com.pippop.ui.screens.DominoTable
import com.pippop.ui.screens.MemoryBoard
import com.pippop.ui.screens.RecordLine
import com.pippop.ui.screens.SlideBoard
import com.pippop.ui.screens.SudokuGrid
import com.pippop.ui.screens.TallyBump
import com.pippop.ui.screens.TallyRow
import com.pippop.ui.screens.TonightScore
import com.pippop.ui.screens.TrophyMoment
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Step 31: each new sound plays once when its moment happens, and not when the screen is shown again (a recomposition,
 * or the screen rebuilt after a rotation). The screens run in Robolectric with a fake player in place of the SoundPool
 * (see [LocalSfx]), the way [SfxTest] does for the mix.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class SoundMomentsTest {
    @get:Rule
    val compose = createComposeRule()

    private class Played(val sound: Sound, val level: Float)

    private val played = mutableListOf<Played>()
    private var profile = Profile()
    private val sfx = Sfx({ profile }, { s, level, _ -> played += Played(s, level) })

    /** A number the screens never read: changing it recomposes the whole screen with nothing new to say. */
    private var noise by mutableIntStateOf(0)

    @Before fun fresh() {
        resetSoundOnce()
        compose.mainClock.autoAdvance = false
    }

    private fun count(sound: Sound) = played.count { it.sound == sound }

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalSfx provides sfx) { PipPopTheme(darkTheme = false) { Column { Text("$noise"); content() } } }
    }

    private val tester by lazy { StateRestorationTester(compose) }

    private fun show(content: @Composable () -> Unit) {
        tester.setContent { Stage(content) }
        settle()
    }

    private fun settle(ms: Long = 2000) {
        // The clock is stopped (the screens loop and bob for ever), so a change waits for idle to be taken in.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    /** Recomposes everything with nothing new, then waits: nothing may play. */
    private fun recomposeQuietly() {
        compose.runOnIdle { noise += 1 }
        settle()
    }

    /** Shows the screen afresh in the state it ended in, as after a rotation: the moment is not a new one. */
    private fun rebuilt(content: @Composable () -> Unit) {
        show(content)
        tester.emulateSavedInstanceStateRestore()
        settle()
    }

    // ---- sudoku_unit_done -----------------------------------------------------------------

    private val solvedRow = listOf(5, 3, 4, 6, 7, 8, 9, 1, 2)

    private fun sudoku(filled: Int) = BoardState(
        cells = List(81) { if (it < filled) solvedRow[it] else 0 },
        givens = List(81) { false },
    )

    @Test fun finishingARowPlaysTheUnitSoundOnce() {
        var board by mutableStateOf(sudoku(8))
        show { SudokuGrid(board, emptySet(), null, false, null, {}, celebrate = true) }
        assertEquals(0, count(Sound.SUDOKU_UNIT_DONE))
        compose.runOnIdle { board = sudoku(9) }
        settle()
        assertEquals(1, count(Sound.SUDOKU_UNIT_DONE))
        recomposeQuietly()
        assertEquals(1, count(Sound.SUDOKU_UNIT_DONE))
    }

    @Test fun aRowThatIsAlreadyFullWhenTheBoardShowsIsSilent() {
        rebuilt { SudokuGrid(sudoku(9), emptySet(), null, false, null, {}, celebrate = true) }
        assertEquals(0, count(Sound.SUDOKU_UNIT_DONE))
    }

    // ---- c4_four ----------------------------------------------------------------------------

    /** Four discs in the bottom row (squares 35 to 38) of the 42-square board. */
    private fun discs(mark: Char) = ".".repeat(35) + "$mark$mark$mark$mark" + "..."

    @Test fun theLineThroughFourDiscsPlaysOnce() {
        val board = discs('m')
        var line by mutableStateOf(emptyList<Int>())
        show { ConnectFourBoard(board, line, 38, null) }
        assertEquals(0, count(Sound.C4_FOUR))
        compose.runOnIdle { line = listOf(35, 36, 37, 38) }
        settle()
        assertEquals(1, count(Sound.C4_FOUR))
        assertEquals(Sound.C4_FOUR.volume, played.single { it.sound == Sound.C4_FOUR }.level, 1e-6f)
        recomposeQuietly()
        assertEquals(1, count(Sound.C4_FOUR))
    }

    @Test fun aFinishedConnectFourShownAgainIsSilent() {
        rebuilt { ConnectFourBoard(discs('m'), listOf(35, 36, 37, 38), 38, null) }
        assertEquals(0, count(Sound.C4_FOUR))
    }

    @Test fun theirLineIsQuieterAndFollowsTheirMovesSetting() {
        var line by mutableStateOf(emptyList<Int>())
        show { ConnectFourBoard(discs('o'), line, 38, null) }
        compose.runOnIdle { line = listOf(35, 36, 37, 38) }
        settle()
        assertEquals(Sound.C4_FOUR.volume * Sfx.OTHERS, played.single { it.sound == Sound.C4_FOUR }.level, 1e-6f)
        played.clear()
        profile = Profile(othersMoves = false)
        compose.runOnIdle { line = emptyList() }
        settle()
        compose.runOnIdle { line = listOf(35, 36, 37, 38) }
        settle()
        assertEquals(0, count(Sound.C4_FOUR))
    }

    // ---- ttt_board_won ----------------------------------------------------------------------

    @Test fun aSmallBoardWonPlaysOncePerBoardAndADrawIsSilent() {
        var boards by mutableStateOf(".........")
        var board by mutableStateOf(".".repeat(81))
        var last by mutableStateOf<Int?>(null)
        show { com.pippop.ui.screens.TttBoard(board, boards, null, emptyList(), last, true, null) }
        compose.runOnIdle { boards = "d........"; last = 2; board = "..m" + ".".repeat(78) }
        settle()
        assertEquals(0, count(Sound.TTT_BOARD_WON))
        compose.runOnIdle { boards = "dm......."; last = 11; board = "..m......m" + ".".repeat(70) }
        settle()
        assertEquals(1, count(Sound.TTT_BOARD_WON))
        recomposeQuietly()
        assertEquals(1, count(Sound.TTT_BOARD_WON))
        compose.runOnIdle { boards = "dmo......"; last = 20; board = "..m......m.........o" + ".".repeat(61) }
        settle()
        assertEquals(2, count(Sound.TTT_BOARD_WON))
        assertEquals(Sound.TTT_BOARD_WON.volume * Sfx.OTHERS, played.last { it.sound == Sound.TTT_BOARD_WON }.level, 1e-6f)
    }

    // ---- memory_miss ------------------------------------------------------------------------

    private val faces16 = List(16) { null as CardFace? }
    private fun twoUp(a: Int, b: Int) = faces16.mapIndexed { i, _ -> if (i == a || i == b) CardFace(Avatar.FOX, pair = i) else null }

    @Test fun aPairThatTurnsBackPlaysOnce() {
        var faces by mutableStateOf(faces16)
        show { MemoryBoard(faces, ".".repeat(16), null) }
        compose.runOnIdle { faces = twoUp(1, 6) }
        settle(300)
        assertEquals("not before the pair has had its look", 0, count(Sound.MEMORY_MISS))
        settle(2500)
        assertEquals(1, count(Sound.MEMORY_MISS))
        recomposeQuietly()
        assertEquals(1, count(Sound.MEMORY_MISS))
    }

    @Test fun aMissedPairAlreadyShowingWhenTheBoardAppearsIsSilentButTheNextOneSounds() {
        var faces by mutableStateOf(twoUp(1, 6))
        show { MemoryBoard(faces, ".".repeat(16), null) }
        settle(3000)
        assertEquals(0, count(Sound.MEMORY_MISS))
        compose.runOnIdle { faces = faces16 }
        settle()
        compose.runOnIdle { faces = twoUp(2, 9) }
        settle(3000)
        assertEquals(1, count(Sound.MEMORY_MISS))
    }

    // ---- slide_home -------------------------------------------------------------------------

    @Test fun aTileLandingHomePlaysOnceButNotTheLastOne() {
        // From a shuffled 3 x 3 find a slide that puts one more tile in place without solving it.
        val random = Random(4)
        var start = Slide.shuffle(3, random)
        fun home(t: List<Int>) = Slide.solved(3).indices.count { t[it] != 0 && t[it] == Slide.solved(3)[it] }
        var move: Pair<Int, List<Int>>? = null
        while (move == null) {
            start = Slide.shuffle(3, random)
            move = (1..8).filter { Slide.canSlide(start, 3, it) }.map { it to Slide.slide(start, 3, it) }.firstOrNull { (_, next) -> next != start && home(next) > home(start) && next != Slide.solved(3) }
        }
        var tiles by mutableStateOf(start)
        show { SlideBoard(tiles, 3, true, {}) }
        compose.runOnIdle { tiles = move.second }
        settle()
        assertEquals(1, count(Sound.SLIDE_HOME))
        assertEquals(0, count(Sound.SLIDE_SOLVED))
        recomposeQuietly()
        assertEquals(1, count(Sound.SLIDE_HOME))
    }

    @Test fun aNewShuffleNeverClicksEvenWhenMoreTilesAreHome() {
        // A different position with many tiles changed at once, more of them home than before.
        val before = listOf(2, 1, 3, 4, 5, 6, 8, 7, 0)
        var tiles by mutableStateOf(before)
        show { SlideBoard(tiles, 3, true, {}) }
        compose.runOnIdle { tiles = listOf(1, 2, 3, 4, 5, 0, 7, 8, 6) }
        settle()
        assertEquals(0, count(Sound.SLIDE_HOME))
    }

    @Test fun theTileThatSolvesThePuzzleHasTheChimeNotTheClick() {
        val solved = Slide.solved(3)
        var tiles by mutableStateOf(Slide.slide(solved, 3, 8))
        show { SlideBoard(tiles, 3, true, {}) }
        compose.runOnIdle { tiles = solved }
        settle()
        assertEquals(1, count(Sound.SLIDE_SOLVED))
        assertEquals(0, count(Sound.SLIDE_HOME))
    }

    // ---- bingo_one_away ---------------------------------------------------------------------

    @Test fun aCardOneAwayFromALinePlaysOncePerLine() {
        val card = Bingo.dealCard(Random(1))
        var dabs by mutableStateOf(setOf(0, 1, 2))
        show { BingoCard(card, dabs, null, true, {}) }
        assertEquals(0, count(Sound.BINGO_ONE_AWAY))
        compose.runOnIdle { dabs = setOf(0, 1, 2, 3) }
        settle()
        assertEquals("the top row is one away", 1, count(Sound.BINGO_ONE_AWAY))
        recomposeQuietly()
        assertEquals(1, count(Sound.BINGO_ONE_AWAY))
        // Taking a dab off and putting it back is the same line again: silent.
        compose.runOnIdle { dabs = setOf(0, 1, 2) }
        settle()
        compose.runOnIdle { dabs = setOf(0, 1, 2, 3) }
        settle()
        assertEquals(1, count(Sound.BINGO_ONE_AWAY))
        // A different line coming one away sounds again.
        compose.runOnIdle { dabs = setOf(0, 1, 2, 3, 5, 10, 15) }
        settle()
        assertEquals(2, count(Sound.BINGO_ONE_AWAY))
    }

    @Test fun aCardShownWithALineAlreadyOneAwayIsSilent() {
        rebuilt { BingoCard(Bingo.dealCard(Random(1)), setOf(0, 1, 2, 3), null, true, {}) }
        assertEquals(0, count(Sound.BINGO_ONE_AWAY))
    }

    @Test fun aNewCardStartsAfresh() {
        val first = Bingo.dealCard(Random(1))
        val second = Bingo.dealCard(Random(2))
        var card by mutableStateOf(first)
        var dabs by mutableStateOf(setOf(0, 1, 2, 3))
        show { BingoCard(card, dabs, null, true, {}) }
        compose.runOnIdle { dabs = setOf(0, 1, 2) }
        settle()
        compose.runOnIdle { card = second; dabs = emptySet() }
        settle()
        compose.runOnIdle { dabs = setOf(0, 1, 2, 3) }
        settle()
        assertEquals("the same top row on the next card is a new one-away", 1, count(Sound.BINGO_ONE_AWAY))
    }

    // ---- domino_knock -----------------------------------------------------------------------

    private fun dominoes(move: DominoMove?) = DominoView(
        line = listOf(Tile(3, 4)), hand = listOf(Tile(0, 1)), counts = mapOf('m' to 1, 'o' to 5), scores = mapOf('m' to 0, 'o' to 0),
        partners = emptyList(), boneyard = 0, teams = false, draw = false, target = 100, roundNo = 1, lastMove = move, lastRound = null,
    )

    @Test fun aPassIsAKnockOnceAndTheirsIsQuieter() {
        var view by mutableStateOf(dominoes(null))
        show { Column { DominoTable(view, true, true, "", { it.toString() }, { _, _ -> }, {}, {}) } }
        compose.runOnIdle { view = dominoes(DominoMove('o', null, null, drew = false, passed = true)) }
        settle()
        assertEquals(1, count(Sound.DOMINO_KNOCK))
        assertEquals(Sound.DOMINO_KNOCK.volume * Sfx.OTHERS, played.single { it.sound == Sound.DOMINO_KNOCK }.level, 1e-6f)
        recomposeQuietly()
        assertEquals(1, count(Sound.DOMINO_KNOCK))
        compose.runOnIdle { view = dominoes(DominoMove('m', null, null, drew = false, passed = true)) }
        settle()
        assertEquals(2, count(Sound.DOMINO_KNOCK))
        assertEquals(Sound.DOMINO_KNOCK.volume, played.last { it.sound == Sound.DOMINO_KNOCK }.level, 1e-6f)
    }

    // ---- clash_last_card --------------------------------------------------------------------

    private fun clash(mine: Int, theirs: Int, move: ClashMove?) = ClashView(
        top = "r3", colour = 'r', hand = List(mine) { "b$it" }, counts = mapOf('m' to mine, 'o' to theirs), order = listOf('m', 'o'),
        direction = 1, deck = 40, drawn = false, lastMove = move,
    )

    @Test fun beingDownToOneCardPingsOnceForThemOrYou() {
        var view by mutableStateOf(clash(5, 2, null))
        show { Column { ClashTable(view, true, true, "", { it.toString() }, { _, _ -> }, {}, {}) } }
        compose.runOnIdle { view = clash(5, 1, ClashMove('o', "r2", null, 0, false, null)) }
        settle()
        assertEquals(1, count(Sound.CLASH_LAST_CARD))
        assertEquals(Sound.CLASH_LAST_CARD.volume * Sfx.OTHERS, played.single { it.sound == Sound.CLASH_LAST_CARD }.level, 1e-6f)
        recomposeQuietly()
        assertEquals(1, count(Sound.CLASH_LAST_CARD))
        compose.runOnIdle { view = clash(1, 1, ClashMove('m', "r4", null, 0, false, null)) }
        settle()
        assertEquals(2, count(Sound.CLASH_LAST_CARD))
        assertEquals(Sound.CLASH_LAST_CARD.volume, played.last { it.sound == Sound.CLASH_LAST_CARD }.level, 1e-6f)
    }

    @Test fun aTableShownWithSomeoneOnOneCardIsSilent() {
        rebuilt { Column { ClashTable(clash(5, 1, null), true, true, "", { it.toString() }, { _, _ -> }, {}, {}) } }
        assertEquals(0, count(Sound.CLASH_LAST_CARD))
    }

    // ---- score_up and record ----------------------------------------------------------------

    private fun score(mine: Int, theirs: Int) = TonightScore(null, "Maya", null, mine, theirs)

    @Test fun theScoreTicksUpOnceAndNotWhenTheCardIsRebuilt() {
        show { TallyRow(score(2, 1), TallyBump.MINE) }
        assertEquals(1, count(Sound.SCORE_UP))
        assertEquals(Sound.SCORE_UP.volume, played.single { it.sound == Sound.SCORE_UP }.level, 1e-6f)
        recomposeQuietly()
        assertEquals(1, count(Sound.SCORE_UP))
        tester.emulateSavedInstanceStateRestore()
        settle()
        assertEquals("the same point is not scored twice", 1, count(Sound.SCORE_UP))
    }

    @Test fun aPointForThemIsOthersAndATieIsSilent() {
        show { TallyRow(score(1, 3), TallyBump.THEIRS) }
        assertEquals(Sound.SCORE_UP.volume * Sfx.OTHERS, played.single { it.sound == Sound.SCORE_UP }.level, 1e-6f)
    }

    @Test fun theirPointIsTheGamesResultSoTheirMovesOffDoesNotSilenceIt() {
        profile = Profile(othersMoves = false)
        show { TallyRow(score(1, 4), TallyBump.THEIRS) }
        assertEquals(Sound.SCORE_UP.volume * Sfx.OTHERS, played.single { it.sound == Sound.SCORE_UP }.level, 1e-6f)
        played.clear()
        profile = Profile(loudness = Loudness.OFF)
        resetSoundOnce()
        compose.runOnIdle { noise += 1 }
        settle()
        assertTrue("Off silences it", played.isEmpty())
    }

    @Test fun aTieBumpsNothingAndSoPlaysNothing() {
        show { TallyRow(score(3, 4), TallyBump.NONE) }
        assertEquals(0, count(Sound.SCORE_UP))
    }

    @Test fun theSamePointAfterAReconnectIsAnotherGameAndSounds() {
        // The tally starts over on a reconnect, so "1 - 0" comes round again: the game says which point it is.
        var key by mutableStateOf<Any>("score:K7Q2NB:1")
        show { TallyRow(score(1, 0), TallyBump.MINE, key) }
        assertEquals(1, count(Sound.SCORE_UP))
        recomposeQuietly()
        assertEquals(1, count(Sound.SCORE_UP))
        compose.runOnIdle { key = "score:K7Q2NB:2" }
        settle()
        assertEquals("a new game's first point sounds, though the score looks the same", 2, count(Sound.SCORE_UP))
    }

    @Test fun theRecordLinePlaysOnceForARecord() {
        var text by mutableStateOf("Your quickest win at Connect Four: 11 discs")
        show { RecordLine(text) }
        assertEquals(1, count(Sound.RECORD))
        assertEquals(Sound.RECORD.volume, played.single { it.sound == Sound.RECORD }.level, 1e-6f)
        recomposeQuietly()
        assertEquals(1, count(Sound.RECORD))
        tester.emulateSavedInstanceStateRestore()
        settle()
        assertEquals(1, count(Sound.RECORD))
        compose.runOnIdle { text = "Your quickest win at Connect Four: 9 discs" }
        settle()
        assertEquals("a better record is another moment", 2, count(Sound.RECORD))
    }

    @Test fun theRecordSoundFollowsTheRecordNotItsWords() {
        var text by mutableStateOf("Your best at Slide Race (4 \u00d7 4): 1:12")
        show { RecordLine(text, soundKey = "the-record") }
        assertEquals(1, count(Sound.RECORD))
        compose.runOnIdle { text = "Your best at Slide Race (4 \u00d7 4): 1:12 (new wording)" }
        settle()
        assertEquals("the same record in other words is not another record", 1, count(Sound.RECORD))
    }

    // ---- trophy -----------------------------------------------------------------------------

    @Test fun theCupChimesOnceEvenWhenRebuilt() {
        val end = MonthEnd(java.time.YearMonth.of(2026, 10), "Maya", MatchRecord.ME, 15, 10, 25, GameKind.WORDS)
        show { TrophyMoment(end, java.time.YearMonth.of(2026, 11), null, null, {}) }
        assertEquals(1, count(Sound.TROPHY))
        recomposeQuietly()
        assertEquals(1, count(Sound.TROPHY))
        tester.emulateSavedInstanceStateRestore()
        settle()
        assertEquals(1, count(Sound.TROPHY))
    }

    // ---- silence ----------------------------------------------------------------------------

    @Test fun offSilencesEveryNewSoundOnItsScreen() {
        profile = Profile(loudness = Loudness.OFF)
        var line by mutableStateOf(emptyList<Int>())
        show { ConnectFourBoard(discs('m'), line, 38, null); RecordLine("Your best at Slide Race: 1:12") }
        compose.runOnIdle { line = listOf(35, 36, 37, 38) }
        settle()
        assertTrue(played.isEmpty())
    }
}
