package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.Profile
import com.pippop.data.ProfileStore
import com.pippop.ui.screens.AnotherGame
import com.pippop.ui.screens.AnotherGameContent
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.StoreFilter
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.screens.WhoPicks
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/**
 * Game Store step 5, light and dark: the home's Store card and the one-time note, the Store (Party chosen; empty), a game's page before
 * and after it is added (with the toast), and "Another game" with its Game Store entry.
 */
class ScreenshotStoreTest : ScreenshotBase() {
    private val nine = ProfileStore.DEFAULT_SHELF
    private val seenAll = GameKind.entries.toSet()

    @Composable
    private fun Home(profile: Profile) {
        HomeScreen(
            solo = SoloUiState(), profile = profile, onContinue = {}, onProfile = {}, onSolo = { _, _ -> }, onScores = {},
            partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {}, connected = false, onConnect = {}, now = 0L,
        )
    }

    private fun home(dark: Boolean, name: String, profile: Profile) = shot(name, dark) { Home(profile) }
    private fun store(dark: Boolean, name: String, host: StoreHost) = shot(name, dark) { host.Content() }

    private val plain = Profile(name = "Sam", avatar = Avatar.FOX, tutorialsSeen = seenAll)

    @Test fun homeCardLight() = home(false, "store-home-card", plain)
    @Test fun homeCardDark() = home(true, "store-home-card", plain)

    @Test fun homeCardEmptyLight() = home(false, "store-home-card-empty", plain.copy(shelf = GameKind.entries))
    @Test fun homeCardEmptyDark() = home(true, "store-home-card-empty", plain.copy(shelf = GameKind.entries))

    @Test fun homeEmptyShelfLight() = home(false, "store-home-empty-shelf", plain.copy(shelf = emptyList()))
    @Test fun homeEmptyShelfDark() = home(true, "store-home-empty-shelf", plain.copy(shelf = emptyList()))

    @Test fun noteLight() = home(false, "store-note", plain.copy(shelfNoteDue = true))
    @Test fun noteDark() = home(true, "store-note", plain.copy(shelfNoteDue = true))

    @Test fun storeLight() = store(false, "store-list", StoreHost(nine, StoreFilter.PARTY))
    @Test fun storeDark() = store(true, "store-list", StoreHost(nine, StoreFilter.PARTY))

    @Test fun storeAllLight() = store(false, "store-list-all", StoreHost(nine))
    @Test fun storeAllDark() = store(true, "store-list-all", StoreHost(nine))

    @Test fun storeEmptyLight() = store(false, "store-empty", StoreHost(GameKind.entries))
    @Test fun storeEmptyDark() = store(true, "store-empty", StoreHost(GameKind.entries))

    @Test fun pageLight() = store(false, "store-page", StoreHost(nine, page = GameKind.BINGO))
    @Test fun pageDark() = store(true, "store-page", StoreHost(nine, page = GameKind.BINGO))

    @Test fun pageAddedLight() = store(false, "store-page-added", StoreHost(nine + GameKind.BINGO, page = GameKind.BINGO, toast = "Bingo is on your shelf"))
    @Test fun pageAddedDark() = store(true, "store-page-added", StoreHost(nine + GameKind.BINGO, page = GameKind.BINGO, toast = "Bingo is on your shelf"))

    @Composable
    private fun Another() {
        val shelf = listOf(GameKind.LUDO, GameKind.WORDS, GameKind.SHIPS, GameKind.DOMINOES, GameKind.CLASH, GameKind.GOLF)
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard {
                AnotherGameContent(AnotherGame(GameKind.CONNECT4, shelf, 2, WhoPicks("Anyone can pick.", null), {}).also { it.onStore = {} }) {}
            }
        }
    }

    @Test fun anotherGameLight() = shot("store-another-game", false) { Another() }
    @Test fun anotherGameDark() = shot("store-another-game", true) { Another() }
}
