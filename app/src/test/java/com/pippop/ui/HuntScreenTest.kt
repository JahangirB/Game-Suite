package com.pippop.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.center
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.pippop.data.nearby.LocalRoom
import com.pippop.game.words.HuntDir
import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntMatch
import com.pippop.ui.screens.HuntCamera
import com.pippop.ui.screens.HuntCell
import com.pippop.ui.screens.HuntGeometry
import com.pippop.ui.screens.huntSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Word Hunt's screen played against a nearby room's own rules (LocalRoom): drags in each direction, the tap-and-tap way, a refused
 * drag, the hint and the zoom of a big grid, all by touching what a player touches. Sam is this phone; Maya and Priya move from the test.
 */
class HuntScreenTest : HuntTestBase() {
    @Before fun words() = HuntTable.installWords()

    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()

    /** Lets the found-word moment pass, as it does a couple of seconds after a find. */
    private fun later() = compose.mainClock.advanceTimeBy(3000)

    private fun foundBy(h: HuntHarness, word: String) = h.state.value.room!!.turns!!.hunt!!.words.first { it.word == word }.foundBy

    /** Two across words and two down words, to drag in turn. */
    private fun wordsRunning(t: HuntTable, dir: HuntDir, n: Int = 2) = t.words.filter { it.dir == dir }.take(n).also { assertEquals(dir.name, n, it.size) }

    @Test fun dragsAWordInEachDirectionAndClaimsIt() {
        val t = HuntTable()
        val h = HuntHarness(t)
        show(h)
        val across = wordsRunning(t, HuntDir.RIGHT)
        val down = wordsRunning(t, HuntDir.DOWN)

        // Across, first letter to last; across, last letter to first; down, top to bottom; down, bottom to top.
        dragWord(t, across[0].word)
        dragWord(t, across[1].word, reverse = true)
        dragWord(t, down[0].word)
        dragWord(t, down[1].word, reverse = true)

        assertEquals(4, h.claims.size)
        val (a, b) = HuntMatch.ends(across[0])
        assertEquals(listOf(a.first, a.second, b.first, b.second), h.claims[0])
        val (c, d) = HuntMatch.ends(across[1])
        assertEquals("a backwards drag goes from the last letter", listOf(d.first, d.second, c.first, c.second), h.claims[1])
        for (w in across + down) assertEquals(w.word, 'h', foundBy(h, w.word))
        val score = h.state.value.room!!.turns!!.hunt!!.scores['h']
        assertEquals((across + down).sumOf { it.length }, score)
        // The find is announced, the list shows who has it, and nothing was refused.
        shown("You found ${down[1].word}")
        shown("Words: 4 of ${t.words.size} found")
    }

    @Test fun aWobblyDragSnapsToItsMainDirection() {
        val t = HuntTable()
        val h = HuntHarness(t)
        show(h)
        val w = t.words.first { it.dir == HuntDir.RIGHT }
        val (a, b) = HuntMatch.ends(w)
        val start = centre(compose, t, a.first, a.second)
        val end = centre(compose, t, b.first, b.second)
        // The finger ends a cell below the last letter: the line still runs along the row it started in.
        val off = centre(compose, t, b.first, b.second).let { it.copy(y = it.y + (end.x - start.x) / (w.length - 1) * 0.6f) }
        compose.onNodeWithTag("huntGrid").performTouchInput { down(start); moveTo(off); up() }
        compose.waitForIdle()
        assertEquals(listOf(listOf(a.first, a.second, b.first, b.second)), h.claims)
        assertEquals('h', foundBy(h, w.word))
    }

    @Test fun tapsTheFirstThenTheLastLetterToClaim() {
        val t = HuntTable()
        val h = HuntHarness(t)
        show(h)
        val w = t.words.first { it.dir == HuntDir.DOWN }
        val (a, b) = HuntMatch.ends(w)
        tapCell(t, a.first, a.second)
        // One tap sends nothing; it says what is next.
        assertTrue(h.claims.isEmpty())
        shown("Now tap the last letter")
        tapCell(t, b.first, b.second)
        assertEquals(listOf(listOf(a.first, a.second, b.first, b.second)), h.claims)
        assertEquals('h', foundBy(h, w.word))
        shown("You found ${w.word}")
        // Tapping the same letter twice takes the first back.
        val other = t.words.first { it.word != w.word }
        val (c, _) = HuntMatch.ends(other)
        tapCell(t, c.first, c.second)
        tapCell(t, c.first, c.second)
        later()
        shown("Drag across a word")
        assertEquals(1, h.claims.size)
    }

    @Test fun aWrongDragIsRefusedInPlaceAndCostsNothing() {
        val t = HuntTable()
        val h = HuntHarness(t)
        show(h)
        val w = t.words.first { it.dir == HuntDir.RIGHT }
        val (a, _) = HuntMatch.ends(w)
        // Two letters of the word are not the word.
        val from = centre(compose, t, a.first, a.second)
        val to = centre(compose, t, a.first, a.second + 1)
        compose.onNodeWithTag("huntGrid").performTouchInput { down(from); moveTo(to); up() }
        compose.waitForIdle()
        shown("Not a word here")
        assertEquals(0, h.state.value.room!!.turns!!.hunt!!.scores['h'])
        // Nothing is marked, and the same word can still be found.
        dragWord(t, w.word)
        assertEquals('h', foundBy(h, w.word))
    }

    @Test fun aWordSomeoneElseFoundSaysWho() {
        val t = HuntTable()
        val h = HuntHarness(t)
        val w = t.words.first { it.dir == HuntDir.RIGHT }
        t.claim(LocalRoom.GUEST, w.word)
        h.refresh()
        show(h)
        // The list tags Maya's find with her initial and her name for a screen reader.
        compose.onNodeWithContentDescription("${w.word}, found by Maya").assertIsDisplayed()
        dragWord(t, w.word)
        shown("Already found by Maya")
    }

    @Test fun theHintCostsTwoPointsAndIsUsedOnce() {
        val t = HuntTable()
        val h = HuntHarness(t)
        show(h)
        val first = t.words.first { it.dir == HuntDir.RIGHT }
        dragWord(t, first.word)
        compose.onNodeWithText("Hint (minus 2)").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, h.hints)
        val v = h.state.value.room!!.turns!!.hunt!!
        assertEquals("a hint costs 2 points", maxOf(0, first.length - 2), v.scores['h'])
        assertEquals(1, v.hints.size)
        assertEquals(0, v.hintsLeft['h'])
        // The hint cell is the first letter of a word nobody has found, and the screen says to look for it.
        assertTrue(t.words.any { it.row == v.hints[0].first && it.col == v.hints[0].second && it.word != first.word })
        later()
        shown("The ring shows where a word starts")
        compose.onNodeWithText("Hint used").assertIsNotEnabled()
        // Only Sam's phone knows it.
        assertTrue(parseOther(t).hints.isEmpty())
    }

    private fun parseOther(t: HuntTable) = com.pippop.data.parseRoom(t.room.view(LocalRoom.GUEST, t.now)).turns!!.hunt!!

    @Test fun theCountdownCoversTheGridUntilItEnds() {
        val t = HuntTable()
        t.now = t.t0 + 2500
        val h = HuntHarness(t)
        show(h)
        shown("Get ready")
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithTag("huntGrid").assertDoesNotExist()
        compose.onNodeWithText("Hint (minus 2)").assertIsNotEnabled()
        // When it ends the grid is there.
        compose.runOnIdle { t.now = t.go + 500; h.refresh() }
        compose.onNodeWithTag("huntGrid").assertIsDisplayed()
    }

    @Test fun theRoundEndsWithRanksAndARematch() {
        val t = HuntTable()
        val h = HuntHarness(t)
        for (w in t.words.take(t.words.size - 1)) t.claim(LocalRoom.HOST, w.word)
        t.claim(LocalRoom.GUEST, t.words.last().word)
        h.refresh()
        show(h)
        shown("You win!")
        shown("All ${t.words.size} words found.")
        shown("1st")
        shown("Maya")
        compose.onNodeWithText("Rematch").assertIsDisplayed()
    }

    @Test fun aScreenReaderClaimsAWordWithItsDoubleTapsAlone() {
        val t = HuntTable()
        val h = HuntHarness(t)
        show(h)
        val w = t.words.first { it.dir == HuntDir.DOWN }
        val (a, b) = HuntMatch.ends(w)
        fun cell(r: Int, c: Int) = compose.onNode(hasContentDescription("Row ${r + 1}, column ${c + 1},", substring = true))
        // Every letter is a node of its own that says where it is.
        cell(a.first, a.second).assertExists()
        cell(a.first, a.second).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        // The first double-tap sets the start and says so; nothing is sent yet.
        assertTrue(h.claims.isEmpty())
        compose.onNode(hasContentDescription("Start: row ${a.first + 1}, column ${a.second + 1}. Now pick the last letter.")).assertExists()
        cell(b.first, b.second).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf(listOf(a.first, a.second, b.first, b.second)), h.claims)
        assertEquals('h', foundBy(h, w.word))
        // The found word is told by letter too, with who found it.
        assertTrue(cell(a.first, a.second).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString().contains("found by You"))
    }

    @Test fun letGoingWellOutsideTheGridCancelsTheClaim() {
        val t = HuntTable(level = HuntLevel.HARD)
        val h = HuntHarness(t)
        show(h)
        val w = t.words.first { it.dir == HuntDir.RIGHT && HuntMatch.ends(it).first.second >= 2 }
        val (a, _) = HuntMatch.ends(w)
        val start = centre(compose, t, a.first, a.second)
        // The window is wider than the grid on a tall grid: its left edge is over a cell's width from the grid's.
        compose.onNodeWithTag("huntGrid").performTouchInput { down(start); moveTo(Offset(start.x / 2, start.y)); moveTo(Offset(1f, start.y)); up() }
        compose.waitForIdle()
        assertTrue("no claim from outside: ${h.claims}", h.claims.isEmpty())
    }

    @Test fun theClockHoldsTheFullTimeThroughTheCountdown() {
        val t = HuntTable(timer = 5)
        t.now = t.t0 + 100
        val v = t.sam().turns!!.hunt!!
        assertEquals("5:00", com.pippop.ui.screens.huntClock(v, t.now))
        assertEquals("4:58", com.pippop.ui.screens.huntClock(v, t.go + 2000))
        assertEquals(null, com.pippop.ui.screens.huntClock(HuntTable(timer = 0).sam().turns!!.hunt!!, t.now))
    }

    @Test fun nearTheGridMeansWithinOneCellOfIt() {
        assertTrue(com.pippop.ui.screens.huntNear(-0.9f, 5f, 10, 10))
        assertTrue(com.pippop.ui.screens.huntNear(10.9f, 10.9f, 10, 10))
        assertTrue(!com.pippop.ui.screens.huntNear(-1.2f, 5f, 10, 10))
        assertTrue(!com.pippop.ui.screens.huntNear(5f, 11.2f, 10, 10))
    }

    // ---- The pure parts ------------------------------------------------------------------------------

    @Test fun snappingFollowsTheMainAxisFromEitherEnd() {
        val start = HuntCell(4, 4)
        // More across than down: stays in the row, to the finger's column.
        assertEquals(HuntCell(4, 9), huntSnap(start, 9.4f, 5.3f, 10, 10))
        // More down than across: stays in the column.
        assertEquals(HuntCell(0, 4), huntSnap(start, 5.2f, 0.6f, 10, 10))
        // Backwards is the same rule from the other side; past the edge it stops at the edge.
        assertEquals(HuntCell(4, 0), huntSnap(start, -3.0f, 3.9f, 10, 10))
        assertEquals(HuntCell(9, 4), huntSnap(start, 4.5f, 14f, 10, 10))
        // A tie goes across.
        assertEquals(HuntCell(4, 6), huntSnap(start, 6.5f, 6.5f, 10, 10))
    }

    @Test fun bigGridsZoomToCellsOf48dpAndStayInsideTheWindow() {
        val touch = 48f * 2.625f
        // 16 by 20 in a 383 by 440 dp window: cells are 22 dp whole, so the 48 dp zoom is a little over 2.
        val g = HuntGeometry(383f * 2.625f, 440f * 2.625f, 16, 20, touch)
        assertTrue(g.canZoom)
        assertEquals(48f, g.fit * g.comfort / 2.625f, 0.5f)
        assertTrue(g.comfort > 2f)
        // Pinching about a point keeps the point where it was; panning never leaves the grid.
        val focus = androidx.compose.ui.geometry.Offset(300f, 500f)
        val whole = g.clamp(HuntCamera())
        val before = g.toGrid(whole, focus)
        val zoomed = g.transform(whole, focus, androidx.compose.ui.geometry.Offset.Zero, 2.5f)
        val after = g.toGrid(zoomed, focus)
        assertEquals(before.x, after.x, 0.01f)
        assertEquals(before.y, after.y, 0.01f)
        val far = g.transform(zoomed, focus, androidx.compose.ui.geometry.Offset(5000f, 5000f), 1f)
        assertEquals(0f, far.x, 0.01f)
        assertEquals(0f, far.y, 0.01f)
        assertEquals(g.maxZoom, g.transform(zoomed, focus, androidx.compose.ui.geometry.Offset.Zero, 100f).zoom, 0.001f)
        // A 10 wide grid does not zoom.
        val easy = HuntGeometry(383f * 2.625f, 440f * 2.625f, 10, 10, touch)
        assertEquals(1f, easy.maxZoom, 0f)
        assertEquals(1f, easy.clamp(HuntCamera(3f, 9f, 9f)).zoom, 0f)
    }

    @Test fun theMapShowsTheWindowAsAPartOfTheGrid() {
        val g = HuntGeometry(400f, 400f, 12, 12, 120f)
        val cam = g.zoomTo(HuntCamera(), 2f)
        val (l, t, r, b) = g.seen(cam)
        assertEquals(6f, r - l, 0.01f)
        assertEquals(6f, b - t, 0.01f)
        val corner = g.centreOn(cam, HuntCell(11, 11))
        assertEquals(12f, g.seen(corner)[2], 0.01f)
        assertNotNull(g.cellAt(corner, androidx.compose.ui.geometry.Offset(399f, 399f)))
    }

    @Test fun aBigGridZoomsWithTheButtonsAndShowsTheMap() {
        val t = HuntTable(level = HuntLevel.HARD, backwards = true)
        val h = HuntHarness(t)
        show(h)
        compose.onNodeWithContentDescription("Zoom in").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Zoom out").assertIsEnabled()
        compose.onNodeWithContentDescription("Show the whole grid").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
        // Two fingers pinch the grid in; one finger still drags a line across it (and the claim is about the cells under it).
        compose.onNodeWithTag("huntGrid").performTouchInput {
            val c = center
            pinch(c + Offset(-40f, 0f), c + Offset(40f, 0f), c + Offset(-240f, 0f), c + Offset(240f, 0f))
        }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Zoomed in", substring = true)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Zoom out").assertIsEnabled()
        h.claims.clear()
        compose.onNodeWithTag("huntGrid").performTouchInput { down(center); moveTo(center + Offset(130f, 20f)); moveTo(center + Offset(390f, 20f)); up() }
        compose.waitForIdle()
        assertEquals(1, h.claims.size)
        val (r1, c1, r2, c2) = h.claims[0]
        assertEquals("a line along one row", r1, r2)
        assertTrue("two cells apart at least: $c1 to $c2", Math.abs(c2 - c1) >= 2)
    }
}
