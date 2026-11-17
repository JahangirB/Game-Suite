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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PackCatalog
import com.pippop.data.PackListing
import com.pippop.data.PackSticker
import com.pippop.data.Reaction
import com.pippop.data.StickerFiles
import com.pippop.ui.art.color
import com.pippop.ui.art.toSticker
import com.pippop.ui.screens.ChipPlayer
import com.pippop.ui.screens.GameFrame
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MyDisc
import com.pippop.ui.screens.PacksPlus
import com.pippop.ui.screens.PacksSheetBody
import com.pippop.ui.screens.ReactionTray
import com.pippop.ui.screens.TheirDisc
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.TurnLine
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.seatMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Stickers without an app update: the tray with its "+" and a downloaded sticker, the packs sheet, a downloaded
 * sticker on a player's chip, and the name bubble that stands in when a sticker is slow to arrive.
 * The sticker is the real first one of the first pack, stickers/sunny-day.json.
 */
class ScreenshotPacksTest : ScreenshotBase() {
    private val sunny: PackSticker = run {
        val dir = File("../stickers").takeIf { it.isDirectory } ?: File("stickers")
        StickerFiles.parse(File(dir, "sunny-day.json").readText(), expectId = "sunny-day")!!
    }

    @Composable
    private fun tray(packs: List<PackSticker>, withPlus: Boolean, onPack: (String) -> Unit = {}, onPlus: () -> Unit = {}) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().background(AppTheme.palette.card).padding(Space.M)) {
                ReactionTray(
                    Reaction.recentFirst(listOf("laugh", "fire", "heart", "kylian")), onReact = {},
                    trailing = if (withPlus) ({ size -> PacksPlus(size, onPlus) }) else null,
                    packs = packs, onPack = if (withPlus) onPack else null,
                )
            }
        }
    }

    @Test fun trayLight() = shot("react-packs-tray", dark = false) { tray(listOf(sunny), withPlus = true) }
    @Test fun trayDark() = shot("react-packs-tray", dark = true) { tray(listOf(sunny), withPlus = true) }

    private fun many(n: Int) = List(n) { sunny.copy(id = "sunny-$it", name = "Sunny $it") }

    /** Eight downloads: more cells than the two rows hold. The "+" is the last cell in view; the rest scroll after it. */
    @Test fun trayWithManyDownloadsLight() = shot("react-packs-tray-many", dark = false) { tray(many(8), withPlus = true) }
    @Test fun trayWithManyDownloadsDark() = shot("react-packs-tray-many", dark = true) { tray(many(8), withPlus = true) }

    @Test fun thePlusIsFullyInViewWithManyDownloads() {
        shot("react-packs-tray-many-check", dark = false) { tray(many(8), withPlus = true) }
        compose.onNodeWithContentDescription("More stickers").assertIsDisplayed()
        val plus = compose.onNodeWithContentDescription("More stickers").getUnclippedBoundsInRoot()
        val first = compose.onNodeWithContentDescription("Laugh").getUnclippedBoundsInRoot()
        assertTrue("plus is in the second row", plus.top > first.top && plus.bottom - first.top <= 97.dp)
    }

    @Test fun aStickerThatCannotBeDrawnIsNotMadeIntoADrawing() {
        assertNotNull(sunny.toSticker())
        val hopeless = sunny.copy(parts = listOf(sunny.parts[0].copy(d = "M 0,0 " + "L 1000,0 L 0,0 ".repeat(50), dash = listOf(0.5f, 0.5f))))
        assertEquals(null, hopeless.toSticker())
        val back = sunny.backdrop!!.copy(type = "radial", n = listOf(1f, 1f, 0f))
        assertEquals(null, sunny.copy(backdrop = back).toSticker())
    }

    @Test fun trayWithNothingDownloadedYetLight() = shot("react-packs-tray-empty", dark = false) { tray(emptyList(), withPlus = true) }
    @Test fun trayWithNothingDownloadedYetDark() = shot("react-packs-tray-empty", dark = true) { tray(emptyList(), withPlus = true) }

    private val listed = PackCatalog.Ready(
        listOf(
            PackListing("sunny-day", "Sunny day", "A smiling sun, for a good moment", 1, 2270),
            PackListing("party-hat", "Party hat", "For a win, or just because", 1, 3100),
            PackListing("rain-cloud", "Rain cloud", "Better luck next time", 1, 2800),
        ),
    )

    @Composable
    private fun sheet(catalog: PackCatalog = listed, installed: List<PackSticker> = listOf(sunny), downloading: Set<String> = setOf("rain-cloud"), message: String? = null) {
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard {
                PacksSheetBody(catalog, installed, downloading, message, onAdd = {}, onRetry = {}, onMessageSeen = {}, onDone = {})
            }
        }
    }

    @Test fun sheetLight() = shot("react-packs", dark = false) { sheet() }
    @Test fun sheetDark() = shot("react-packs", dark = true) { sheet() }

    @Test fun sheetCouldNotReachTheListLight() = shot("react-packs-failed", dark = false) {
        sheet(PackCatalog.Failed("Couldn't reach the sticker list. Check the internet and try again."), emptyList(), emptySet())
    }
    @Test fun sheetCouldNotReachTheListDark() = shot("react-packs-failed", dark = true) {
        sheet(PackCatalog.Failed("Couldn't reach the sticker list. Check the internet and try again."), emptyList(), emptySet())
    }

    @Composable
    private fun chips(said: ShownReaction) {
        CompositionLocalProvider(LocalTurnHints provides TurnHints(turns = 5)) {
            GameFrame(
                title = "Connect Four",
                subtitle = "Game 6",
                players = listOf(
                    ChipPlayer("You", Avatar.FOX, MyDisc, "Red", active = true, mark = seatMark(0)),
                    ChipPlayer("Maya", Avatar.PANDA, TheirDisc, "Yellow", said, mark = seatMark(1)),
                ),
                glow = GameKind.CONNECT4.color,
                menu = GameMenu(GameKind.CONNECT4, onRules = {}, onLeave = {}),
                onReact = {},
            ) {
                TurnLine("Your turn: slide to a column and let go", true, GameKind.CONNECT4.color)
            }
        }
    }

    @Test fun downloadedStickerOnAChipLight() = shot("react-packs-chip", dark = false) { chips(ShownReaction(null, 0, pack = sunny)) }
    @Test fun downloadedStickerOnAChipDark() = shot("react-packs-chip", dark = true) { chips(ShownReaction(null, 0, pack = sunny)) }

    /** A sticker that took over two seconds (or failed) shows its name in step 27's bubble. */
    @Test fun nameBubbleLight() = shot("react-packs-name", dark = false) { chips(ShownReaction(null, 0, line = "Sunny day")) }
    @Test fun nameBubbleDark() = shot("react-packs-name", dark = true) { chips(ShownReaction(null, 0, line = "Sunny day")) }

    @Test fun theNameBubbleIsAnnouncedWithTheSpeakersName() {
        shot("react-packs-name-said", dark = false) { chips(ShownReaction(null, 0, line = "Sunny day")) }
        compose.onNodeWithContentDescription("Maya says: Sunny day").assertIsDisplayed()
    }

    @Test fun thePlusAndTheStickerAreTappableAndAtLeast48dp() {
        var sent: String? = null
        var opened = false
        shot("react-packs-tap", dark = false) { tray(listOf(sunny), withPlus = true, onPack = { sent = it }, onPlus = { opened = true }) }
        val plus = compose.onNodeWithContentDescription("More stickers").getUnclippedBoundsInRoot()
        assertTrue("plus ${plus.width} x ${plus.height}", plus.height >= 48.dp && plus.width >= 48.dp)
        val cell = compose.onNodeWithContentDescription("Sunny day").getUnclippedBoundsInRoot()
        assertTrue("sticker ${cell.width} x ${cell.height}", cell.height >= 48.dp && cell.width >= 48.dp)
        compose.onNodeWithContentDescription("More stickers").performClick()
        assertTrue(opened)
        compose.onNodeWithContentDescription("Sunny day").performClick()
        assertEquals("sunny-day", sent)
    }

    @Test fun withoutTheCapThereIsNoPlusAndNoPackStickers() {
        shot("react-packs-none", dark = false) { tray(listOf(sunny), withPlus = false) }
        compose.onAllNodesWithContentDescription("More stickers").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Sunny day").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Laugh").assertCountEquals(1)
    }

    @Test fun theSheetOffersWhatIsNotYetOnThePhone() {
        shot("react-packs-offer", dark = false) { sheet() }
        compose.onNodeWithText("Add · 3 KB").assertIsDisplayed()
        compose.onNodeWithText("Added").assertIsDisplayed()
        compose.onNodeWithText("Adding…").assertIsDisplayed()
        assertNotNull(sunny)
    }
}
