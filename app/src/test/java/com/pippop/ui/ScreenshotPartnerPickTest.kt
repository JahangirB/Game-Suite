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
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.ProfileStore
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.SavedPartner
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.PartnerPick
import com.pippop.ui.screens.PartnerPickContent
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/** Game Store step 6, light and dark: the sheet over the home when Maya wants to play Corner Blocks and it is not on the shelf. */
class ScreenshotPartnerPickTest : ScreenshotBase() {
    private fun player(name: String, avatar: Avatar, at: GameKind?) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = at,
    )

    private val room = RoomState(
        code = "K7Q2NB", role = "host", game = GameKind.BLOCKS, status = RoomStatus.LOBBY, difficulty = null, round = 0, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam", Avatar.FOX, null),
        opponent = player("Maya", Avatar.PANDA, GameKind.BLOCKS), ships = null, turns = null, serverTime = 0,
        others = listOf(player("Maya", Avatar.PANDA, GameKind.BLOCKS)), inSession = true,
    )

    @Composable
    private fun Sheet() {
        val profile = Profile(
            name = "Sam", avatar = Avatar.FOX, playMode = PlayMode.ONLINE, shelf = ProfileStore.DEFAULT_SHELF, tutorialsSeen = GameKind.entries.toSet(),
            partner = SavedPartner("https://example.com/x", "", "K7Q2NB", "Maya", Avatar.PANDA, "device", "me"),
        )
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                solo = SoloUiState(), profile = profile, onContinue = {}, onProfile = {}, onSolo = { _, _ -> }, onScores = {},
                partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {}, connected = true, onConnect = {},
                room = room, now = 0L,
            )
            Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
                ToySheetCard { PartnerPickContent(PartnerPick("Maya", Avatar.PANDA, GameKind.BLOCKS), onAdd = {}, onNotNow = {}) }
            }
        }
    }

    @Test fun sheetLight() = shot("partner-pick", false) { Sheet() }

    @Test fun sheetDark() = shot("partner-pick", true) { Sheet() }
}
