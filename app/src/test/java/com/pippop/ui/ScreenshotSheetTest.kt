package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.Profile
import com.pippop.data.SavedPartner
import com.pippop.ui.screens.GameSheet
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.SoloSetupContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/**
 * A game's sheet over the home, as it opens: the facts, the way to play together, and solo with its
 * setup. Connect Four has one choice (a row of Choices); Mini Golf has several (a line and "Change").
 * Mini Golf's setup sheet shows what Change opens. Nothing says "your best" or "you two".
 */
class ScreenshotSheetTest : ScreenshotBase() {
    private val profile = Profile(
        name = "Sam", avatar = Avatar.FOX, playMode = PlayMode.ONLINE,
        partner = SavedPartner("https://example.com/x", "", "K7Q2NB", "Maya", Avatar.PANDA, "device", "me"),
    )

    @Composable
    private fun over(sheet: @Composable () -> Unit) {
        HomeScreen(
            solo = SoloUiState(), profile = profile, onContinue = {}, onProfile = {}, onSolo = { _, _ -> },
            onArrange = {}, onScores = {}, partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {},
            connected = false, onConnect = {},
        )
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard { sheet() }
        }
    }

    @Composable
    private fun sheet(kind: GameKind, setup: Map<String, String> = emptyMap()) = over {
        GameSheet(
            kind = kind, solo = SoloUiState(), room = null, onOpenGame = {}, partner = profile.partner, busy = false,
            setup = com.pippop.data.SoloSetups.resolve(kind, setup), unfinished = false, onContinue = {}, onSolo = {}, onSetup = { _, _ -> },
            onChange = {}, onPartner = {}, onInvite = {},
        )
    }

    @Composable
    private fun setup() = over {
        SoloSetupContent(
            GameKind.GOLF, com.pippop.data.SoloSetups.resolve(GameKind.GOLF, mapOf("level" to "medium", "computers" to "2")),
            onPick = { _, _ -> }, startLabel = "Play solo · Medium", onStart = {}, onBack = {},
        )
    }

    @Test fun singleLight() = shot("sheet-new", dark = false) { sheet(GameKind.CONNECT4) }

    @Test fun singleDark() = shot("sheet-new", dark = true) { sheet(GameKind.CONNECT4) }

    @Test fun manyLight() = shot("sheet-many", dark = false) { sheet(GameKind.GOLF, mapOf("level" to "medium", "computers" to "2")) }

    @Test fun manyDark() = shot("sheet-many", dark = true) { sheet(GameKind.GOLF, mapOf("level" to "medium", "computers" to "2")) }

    @Test fun setupLight() = shot("sheet-setup", dark = false) { setup() }

    @Test fun setupDark() = shot("sheet-setup", dark = true) { setup() }
}
