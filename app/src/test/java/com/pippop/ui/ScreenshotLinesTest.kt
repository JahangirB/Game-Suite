package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.pippop.data.Avatar
import com.pippop.data.ChatLines
import com.pippop.data.GameKind
import com.pippop.data.Reaction
import com.pippop.ui.art.color
import com.pippop.ui.screens.ChipPlayer
import com.pippop.ui.screens.GameFrame
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.LinesSheetBody
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MyDisc
import com.pippop.ui.screens.ReactionTray
import com.pippop.ui.screens.TheirDisc
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.TurnLine
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.seatMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lines of your own: the tray with its row of lines, the sheet that edits them, and a line said by a chip. Target: react-lines. */
class ScreenshotLinesTest : ScreenshotBase() {
    @Composable
    private fun tray(lines: List<String>?, onLine: (String) -> Unit = {}, onEdit: () -> Unit = {}) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().background(AppTheme.palette.card).padding(Space.M)) {
                ReactionTray(
                    Reaction.recentFirst(listOf("laugh", "fire", "heart", "kylian")), onReact = {},
                    lines = lines, onLine = onLine.takeIf { lines != null }, onEditLines = onEdit,
                )
            }
        }
    }

    @Test fun trayLight() = shot("react-lines-tray", dark = false) { tray(ChatLines.DEFAULTS) }
    @Test fun trayDark() = shot("react-lines-tray", dark = true) { tray(ChatLines.DEFAULTS) }

    @Composable
    private fun sheet() {
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard {
                LinesSheetBody(listOf("Nice one", "Hurry up", "Lucky", "Good game", "One more?", ""), onChange = {}, onDone = {})
            }
        }
    }

    @Test fun sheetLight() = shot("react-lines", dark = false) { sheet() }
    @Test fun sheetDark() = shot("react-lines", dark = true) { sheet() }

    @Composable
    private fun bubble() {
        CompositionLocalProvider(LocalTurnHints provides TurnHints(turns = 5)) {
            GameFrame(
                title = "Connect Four",
                subtitle = "Game 6",
                players = listOf(
                    ChipPlayer("You", Avatar.FOX, MyDisc, "Red", active = true, mark = seatMark(0)),
                    ChipPlayer("Maya", Avatar.PANDA, TheirDisc, "Yellow", ShownReaction(null, 0, "Good game"), mark = seatMark(1)),
                ),
                glow = GameKind.CONNECT4.color,
                menu = GameMenu(GameKind.CONNECT4, onRules = {}, onLeave = {}),
                onReact = {},
            ) {
                TurnLine("Your turn: slide to a column and let go", true, GameKind.CONNECT4.color)
            }
        }
    }

    @Test fun bubbleLight() = shot("react-lines-bubble", dark = false) { bubble() }
    @Test fun bubbleDark() = shot("react-lines-bubble", dark = true) { bubble() }

    private val longLine = "WWWW iiii WWWW iiii WWWW"

    /** A 24-character line on the first chip, in a game of four and in a game of two: it stays on the screen. */
    @Composable
    private fun wide(four: Boolean, mine: Boolean = true) {
        val said = ShownReaction(null, 0, longLine)
        val players = if (four) listOf(
            ChipPlayer("You", Avatar.FOX, MyDisc, "Red", if (mine) said else null, active = true, mark = seatMark(0), mine = mine),
            ChipPlayer("Maya", Avatar.PANDA, TheirDisc, "Yellow", if (mine) null else said, mark = seatMark(1)),
            ChipPlayer("Sam", Avatar.OWL, TheirDisc, "Blue", mark = seatMark(2)),
            ChipPlayer("Lee", Avatar.FOX, TheirDisc, "Green", mark = seatMark(3)),
        ) else listOf(
            ChipPlayer("You", Avatar.FOX, MyDisc, "Red", said, active = true, mark = seatMark(0), mine = true),
            ChipPlayer("Maya", Avatar.PANDA, TheirDisc, "Yellow", mark = seatMark(1)),
        )
        CompositionLocalProvider(LocalTurnHints provides TurnHints(turns = 5)) {
            GameFrame(
                title = "Connect Four", subtitle = "Game 6", players = players, glow = GameKind.CONNECT4.color,
                menu = GameMenu(GameKind.CONNECT4, onRules = {}, onLeave = {}), onReact = {},
            ) { TurnLine("Your turn: slide to a column and let go", true, GameKind.CONNECT4.color) }
        }
    }

    @Test fun wideFourLight() = shot("react-lines-wide4", dark = false) { wide(four = true) }
    @Test fun wideFourDark() = shot("react-lines-wide4", dark = true) { wide(four = true) }
    @Test fun wideTwoLight() = shot("react-lines-wide2", dark = false) { wide(four = false) }
    @Test fun wideTwoDark() = shot("react-lines-wide2", dark = true) { wide(four = false) }

    @Test fun aWideBubbleStaysOnTheScreen() {
        compose.setContent { com.pippop.ui.theme.PipPopTheme(darkTheme = false) { wide(four = true) } }
        compose.waitForIdle()
        val b = compose.onNodeWithContentDescription("You said: $longLine").getUnclippedBoundsInRoot()
        assertTrue("bubble starts at ${b.left}", b.left >= 0.dp)
        assertTrue("bubble ends at ${b.right}", b.right <= 411.dp)
    }

    @Test fun aBubbleIsAnnouncedWithTheSpeakersName() {
        shot("react-lines-bubble-name", dark = false) { bubble() }
        compose.onNodeWithContentDescription("Maya says: Good game").assertIsDisplayed()
    }

    @Test fun aTapOnALineSendsItAndThePencilEdits() {
        var sent: String? = null
        var edited = false
        shot("react-lines-tap", dark = false) { tray(ChatLines.DEFAULTS, { sent = it }, { edited = true }) }
        compose.onNodeWithText("Lucky").performClick()
        assertEquals("Lucky", sent)
        compose.onNodeWithContentDescription("Edit your lines").performClick()
        assertTrue(edited)
    }

    @Test fun withoutTheCapThereIsNoRow() {
        shot("react-lines-none", dark = false) { tray(null) }
        compose.onAllNodesWithContentDescription("Edit your lines").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Laugh").assertCountEquals(1)
    }

    @Test fun theChipsAndThePencilAreAtLeast48dpTall() {
        shot("react-lines-size", dark = false) { tray(ChatLines.DEFAULTS) }
        val pencil = compose.onNodeWithContentDescription("Edit your lines").getUnclippedBoundsInRoot()
        assertTrue("pencil ${pencil.width} x ${pencil.height}", pencil.height >= 48.dp && pencil.width >= 48.dp)
        val chip = compose.onNodeWithText("Lucky").getUnclippedBoundsInRoot()
        assertTrue("a chip is ${chip.height} tall", chip.height >= 48.dp)
    }
}
