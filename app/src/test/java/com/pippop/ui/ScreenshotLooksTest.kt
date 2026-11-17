package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.CardBackLook
import com.pippop.data.FrameLook
import com.pippop.data.GameKind
import com.pippop.data.TileLook
import com.pippop.game.dominoes.Tile
import com.pippop.game.memory.CardFace
import com.pippop.ui.screens.ClashBack
import com.pippop.ui.screens.ClashCard
import com.pippop.ui.screens.ConnectFourBoard
import com.pippop.ui.screens.DominoHand
import com.pippop.ui.screens.DominoLine
import com.pippop.ui.screens.LocalLooks
import com.pippop.ui.screens.Looks
import com.pippop.ui.screens.LooksSheetContent
import com.pippop.ui.screens.MemoryBoard
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/**
 * Step 25: the Looks sheet of each of the four games (only what applies to it), and a board for
 * each look that is not the first one, light and dark. Compare the sheets with `looks-new`.
 */
class ScreenshotLooksTest : ScreenshotBase() {
    @Composable
    private fun sheet(game: GameKind, looks: Looks = Looks()) {
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard {
                LooksSheetContent(game, looks, onCardBack = {}, onFrame = {}, onTile = {})
            }
        }
    }

    @Test fun sheetMemoryLight() = shot("looks-sheet-memory", false) { sheet(GameKind.MEMORY) }
    @Test fun sheetMemoryDark() = shot("looks-sheet-memory", true) { sheet(GameKind.MEMORY) }
    @Test fun sheetClashLight() = shot("looks-sheet-clash", false) { sheet(GameKind.CLASH, Looks(cardBack = CardBackLook.DOTS)) }
    @Test fun sheetClashDark() = shot("looks-sheet-clash", true) { sheet(GameKind.CLASH, Looks(cardBack = CardBackLook.DOTS)) }
    @Test fun sheetConnectLight() = shot("looks-sheet-connect4", false) { sheet(GameKind.CONNECT4) }
    @Test fun sheetConnectDark() = shot("looks-sheet-connect4", true) { sheet(GameKind.CONNECT4) }
    @Test fun sheetDominoesLight() = shot("looks-sheet-dominoes", false) { sheet(GameKind.DOMINOES, Looks(tile = TileLook.EBONY)) }
    @Test fun sheetDominoesDark() = shot("looks-sheet-dominoes", true) { sheet(GameKind.DOMINOES, Looks(tile = TileLook.EBONY)) }

    // ---- Boards ----

    @Composable
    private fun cards(look: CardBackLook) = CompositionLocalProvider(LocalLooks provides Looks(cardBack = look)) {
        Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            // Memory: two cards face up, one pair found, the rest face down.
            val faces = List(16) { i -> if (i in listOf(1, 6, 10)) CardFace(Avatar.entries[i % Avatar.entries.size]) else null }
            val owners = ".".repeat(10) + "m" + ".".repeat(5)
            MemoryBoard(faces, owners, onFlip = null, modifier = Modifier.size(300.dp, 360.dp))
            // Colour Clash: the deck and a card that is played, side by side.
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                ClashBack(72.dp)
                ClashCard("g4", 72.dp)
                ClashCard("r7", 72.dp)
                ClashCard("b2", 72.dp)
            }
        }
    }

    @Composable
    private fun frame(look: FrameLook, won: Boolean = false) = CompositionLocalProvider(LocalLooks provides Looks(frame = look)) {
        Column(Modifier.padding(16.dp)) {
            val rows = if (won) listOf(".......", ".......", "...m...", "..mo...", ".mmo...", "mmoooo.")
            else listOf(".......", ".......", "...o...", "..mo...", ".mooo..", "mmomo.o")
            ConnectFourBoard(rows.joinToString(""), winLine = if (won) listOf(37, 38, 39, 40) else emptyList(), lastCell = if (won) 40 else null, onDrop = null, modifier = Modifier.size(300.dp, 348.dp))
        }
    }

    @Composable
    private fun tiles(look: TileLook) = CompositionLocalProvider(LocalLooks provides Looks(tile = look)) {
        Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            val line = listOf(Tile(2, 5), Tile(5, 5), Tile(5, 1), Tile(1, 6), Tile(6, 0), Tile(0, 3))
            DominoLine(line, start = 1, highlight = 5, modifier = Modifier.size(360.dp, 288.dp))
            DominoHand(listOf(Tile(3, 4), Tile(0, 6), Tile(2, 2), Tile(1, 1), Tile(6, 6)), line, enabled = true, selected = 1, onTap = {})
        }
    }

    @Test fun cardsDotsLight() = shot("looks-cards-dots", false) { cards(CardBackLook.DOTS) }
    @Test fun cardsDotsDark() = shot("looks-cards-dots", true) { cards(CardBackLook.DOTS) }
    @Test fun cardsDiamondsLight() = shot("looks-cards-diamonds", false) { cards(CardBackLook.DIAMONDS) }
    @Test fun cardsDiamondsDark() = shot("looks-cards-diamonds", true) { cards(CardBackLook.DIAMONDS) }
    @Test fun cardsWavesLight() = shot("looks-cards-waves", false) { cards(CardBackLook.WAVES) }
    @Test fun cardsWavesDark() = shot("looks-cards-waves", true) { cards(CardBackLook.WAVES) }

    @Test fun frameMintLight() = shot("looks-frame-mint", false) { frame(FrameLook.MINT) }
    @Test fun frameMintDark() = shot("looks-frame-mint", true) { frame(FrameLook.MINT) }
    @Test fun framePlumLight() = shot("looks-frame-plum", false) { frame(FrameLook.PLUM) }
    @Test fun framePlumDark() = shot("looks-frame-plum", true) { frame(FrameLook.PLUM) }
    @Test fun frameSlateLight() = shot("looks-frame-slate", false) { frame(FrameLook.SLATE, won = true) }
    @Test fun frameSlateDark() = shot("looks-frame-slate", true) { frame(FrameLook.SLATE, won = true) }

    @Test fun tilesEbonyLight() = shot("looks-tiles-ebony", false) { tiles(TileLook.EBONY) }
    @Test fun tilesEbonyDark() = shot("looks-tiles-ebony", true) { tiles(TileLook.EBONY) }
    @Test fun tilesMapleLight() = shot("looks-tiles-maple", false) { tiles(TileLook.MAPLE) }
    @Test fun tilesMapleDark() = shot("looks-tiles-maple", true) { tiles(TileLook.MAPLE) }
}
