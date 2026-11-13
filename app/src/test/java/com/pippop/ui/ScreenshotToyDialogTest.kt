package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.SecondaryButton
import com.pippop.ui.screens.ToyDialogCard
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.readableOn
import org.junit.Test

/** The app's own question card: leaving the room (the update offer uses the same card). */
class ScreenshotToyDialogTest : ScreenshotBase() {
    @Composable
    private fun over(content: @Composable () -> Unit) {
        // The dialog's window is not part of the test's root, so the card is shown on a dim layer.
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { content() }
    }

    @Composable
    private fun leave() = over {
        val p = AppTheme.palette
        ToyDialogCard("Leave the room?", body = "You'll disconnect from Maya. A game you're in ends for both of you.") {
            SecondaryButton("Stay", {}, Modifier.weight(1f))
            PrimaryButton("Leave", {}, Modifier.weight(1f), container = p.bad, content = readableOn(p.bad))
        }
    }

    @Test fun leaveLight() = shot("toy-dialog-leave", dark = false) { leave() }

    @Test fun leaveDark() = shot("toy-dialog-leave", dark = true) { leave() }
}
