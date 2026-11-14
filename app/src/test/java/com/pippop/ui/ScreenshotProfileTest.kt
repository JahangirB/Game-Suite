package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.ui.screens.PreviewAppCard
import org.junit.Test

/** The preview app's card with its test tools. */
class ScreenshotProfileTest : ScreenshotBase() {
    @Composable
    private fun profile() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PreviewAppCard()
    }

    @Test fun profileLight() = shot("profile", dark = false) { profile() }

    @Test fun profileDark() = shot("profile", dark = true) { profile() }
}
