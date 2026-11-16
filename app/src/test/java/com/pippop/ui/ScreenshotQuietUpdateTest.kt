package com.pippop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.Profile
import com.pippop.data.QuietEntry
import com.pippop.data.QuietOutcome
import com.pippop.ui.screens.AppUpdateCard
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.PreviewAppCard
import com.pippop.ui.screens.Tone
import com.pippop.ui.screens.Toast
import org.junit.Test

/** "Update by itself": the hint on the home after a quiet update, and after one that needed a tap, and the preview card's log. */
class ScreenshotQuietUpdateTest : ScreenshotBase() {
    private val noon = java.time.LocalDateTime.of(2026, 11, 13, 12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Composable
    private fun homeWith(text: String, tone: Tone) = Box(Modifier.fillMaxSize()) {
        HomeScreen(
            solo = SoloUiState(), profile = Profile(name = "Sam"), onContinue = {}, onProfile = {}, onSolo = { _, _ -> },
            onArrange = {}, onScores = {}, partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {},
            connected = false, onConnect = {}, now = noon,
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Toast(text, tone) }
    }

    private val log = listOf(
        QuietEntry(noon, QuietOutcome.INSTALLED, 7, "1.0.7-preview"),
        QuietEntry(noon - 60_000, QuietOutcome.DOWNLOADED, 7, "1.0.7-preview"),
        QuietEntry(noon - 3_600_000, QuietOutcome.NEEDED_TAP, 6, "1.0.6-preview", "needs a tap"),
        QuietEntry(noon - 7_200_000, QuietOutcome.FAILED, 6, "1.0.6-preview", "the download failed"),
        QuietEntry(noon - 86_400_000, QuietOutcome.UP_TO_DATE),
    )

    @Composable
    private fun card(state: UpdateUiState) = Column(Modifier.fillMaxSize().padding(16.dp)) { PreviewAppCard(state) }

    @Test fun hintDoneLight() = shot("quiet-hint-done", dark = false) { homeWith("Updated by itself to 1.0.7-preview", Tone.Good) }
    @Test fun hintDoneDark() = shot("quiet-hint-done", dark = true) { homeWith("Updated by itself to 1.0.7-preview", Tone.Good) }
    @Test fun hintTapLight() = shot("quiet-hint-tap", dark = false) { homeWith("Couldn't update by itself: needs a tap", Tone.Warning) }
    @Test fun hintTapDark() = shot("quiet-hint-tap", dark = true) { homeWith("Couldn't update by itself: needs a tap", Tone.Warning) }
    @Test fun cardLogLight() = shot("quiet-card", dark = false) { card(UpdateUiState(currentVersion = "1.0.7-preview", quietLog = log)) }
    @Test fun cardLogDark() = shot("quiet-card", dark = true) { card(UpdateUiState(currentVersion = "1.0.7-preview", quietLog = log)) }
    @Composable
    private fun appCard(state: UpdateUiState) = Column(Modifier.fillMaxSize().padding(16.dp)) { AppUpdateCard(state, {}, {}, {}) }

    @Test fun appCardLight() = shot("quiet-app-card", dark = false) { appCard(UpdateUiState(currentVersion = "1.0.7-preview")) }
    @Test fun appCardDark() = shot("quiet-app-card", dark = true) { appCard(UpdateUiState(currentVersion = "1.0.7-preview", quietOn = false)) }
    @Test fun cardEmptyOffDark() = shot("quiet-card-empty", dark = true) { card(UpdateUiState(currentVersion = "1.0.7-preview", quietOn = false)) }
    @Test fun cardEmptyOffLight() = shot("quiet-card-empty", dark = false) { card(UpdateUiState(currentVersion = "1.0.7-preview", quietOn = false)) }
}
