package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.PipPopLoader
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.Tone
import com.pippop.ui.screens.Toast
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import org.junit.Test

/** Pip and Pop while you wait, in a card and a button, and the four toasts. */
class ScreenshotToyBitsTest : ScreenshotBase() {
    @Composable
    private fun bits() = Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.L), horizontalAlignment = Alignment.CenterHorizontally) {
        val p = AppTheme.palette
        AppCard("Making your puzzle") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.M)) {
                PipPopLoader(Modifier.padding(Space.S), t = 0.3f)
                Text("Making your puzzle…", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                PipPopLoader(still = true)
            }
        }
        PrimaryButton("Join", {}, Modifier.fillMaxWidth(), loading = true)
        Toast("Saved. Maya can see it now.", Tone.Good)
        Toast("Nobody has joined yet.", Tone.Warning)
        Toast("Update failed: network problem", Tone.Bad)
        Toast("Maya can't play right now.", Tone.Neutral)
    }

    @Test fun bitsLight() = shot("toy-bits", dark = false) { bits() }
    @Test fun bitsDark() = shot("toy-bits", dark = true) { bits() }
}
