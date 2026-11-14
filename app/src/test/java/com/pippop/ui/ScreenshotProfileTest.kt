package com.pippop.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.Loudness
import com.pippop.data.Profile
import com.pippop.data.SavedPartner
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.PlayingOnlineCard
import com.pippop.ui.screens.PreviewAppCard
import com.pippop.ui.screens.ProfileScreen
import com.pippop.ui.screens.SoundAndTouchCard
import org.junit.Test

/** Step 23: the profile in its new order (You, Look, Sound and touch, Playing online), then the preview app's card last. */
class ScreenshotProfileTest : ScreenshotBase() {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val appVm by lazy { AppViewModel(app) }
    private val battle by lazy { BattleViewModel(app) }

    private val profile = Profile(
        name = "Sam", avatar = Avatar.FOX, scriptUrl = "https://pip.example.workers.dev",
        partner = SavedPartner("https://pip.example.workers.dev", "", "4821", "Maya", Avatar.PANDA, "device", "p2"),
    )

    @Composable
    private fun top() = ProfileScreen(profile, busy = false, app = appVm, battle = battle, updates = UpdateUiState(), onCheckUpdates = {}, onUpdate = {}, onBack = {})

    /** What sits below the fold: Playing online (closed, as when the link is set up), the App card and, last, the preview card. */
    @Composable
    private fun bottom() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PlayingOnlineCard(profile, busy = false, app = appVm, battle = battle, startOpen = false)
        AppCard("App") { androidx.compose.material3.Text("Version ${UpdateUiState().currentVersion}") }
        PreviewAppCard()
    }

    /** No link yet: Playing online opens by itself, showing the fields, Paste message and Test link. */
    @Composable
    private fun noLink() = Column(Modifier.fillMaxSize().padding(16.dp)) {
        PlayingOnlineCard(Profile(name = "Sam"), busy = false, app = appVm, battle = battle)
    }

    @Composable
    private fun bigSound() {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) { SoundAndTouchCard(profile.copy(loudness = Loudness.QUIET), appVm) }
        }
    }

    @Test fun profileLight() = shot("profile", dark = false) { top() }

    @Test fun profileDark() = shot("profile", dark = true) { top() }

    @Test fun profileLowerLight() = shot("profile-lower", dark = false) { bottom() }

    @Test fun profileLowerDark() = shot("profile-lower", dark = true) { bottom() }

    @Test fun noLinkLight() = shot("profile-nolink", dark = false) { noLink() }

    @Test fun noLinkDark() = shot("profile-nolink", dark = true) { noLink() }

    @Test fun loudnessBigTextLight() = shot("profile-big", dark = false) { bigSound() }

    @Test fun loudnessBigTextDark() = shot("profile-big", dark = true) { bigSound() }
}
