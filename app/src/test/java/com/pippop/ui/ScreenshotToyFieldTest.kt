package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.pippop.ui.screens.CodeField
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.SecondaryButton
import com.pippop.ui.screens.ToyField
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import org.junit.Test

/** The join sheet's code box and the profile's fields in the app's own look. */
class ScreenshotToyFieldTest : ScreenshotBase() {
    @Composable
    private fun join() = Column(Modifier.fillMaxWidth()) {
        val p = AppTheme.palette
        ToySheetCard {
            Column(Modifier.padding(horizontal = Space.XL).padding(bottom = Space.XXXL), verticalArrangement = Arrangement.spacedBy(Space.M)) {
                Text("Join a room", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text("Type the code you were sent, or paste the whole message.", color = p.muted, fontSize = TextSize.Body)
                CodeField("K7Q2NB", {}, onGo = {})
                PrimaryButton("Join", {})
                SecondaryButton("Paste the message instead", {})
            }
        }
    }

    @Composable
    private fun profile() = Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.L)) {
        ToyField("Maya", {}, label = "Your name", singleLine = true)
        ToyField("", {}, label = "Online link", placeholder = "https://…workers.dev or https://script.google.com/…", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        ToyField("hunter2", {}, label = "Password", help = "Only if the link has one. Ask whoever set it up.", singleLine = true)
        ToyField("", {}, placeholder = "What is it?", singleLine = true)
        CodeField("K7Q2", {}, onNew = {})
    }

    @Test fun joinLight() = shot("toy-field-join", dark = false) { join() }

    @Test fun joinDark() = shot("toy-field-join", dark = true) { join() }

    @Test fun profileLight() = shot("toy-field-profile", dark = false) { profile() }

    @Test fun profileDark() = shot("toy-field-profile", dark = true) { profile() }
}
