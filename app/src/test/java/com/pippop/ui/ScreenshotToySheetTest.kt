package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pippop.data.GameKind
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.GameMenuRows
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/** The game menu in the app's sheet (the sheet's window is not in the test's root, so its card is shown on a dim layer). */
class ScreenshotToySheetTest : ScreenshotBase() {
    @Composable
    private fun menu() {
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard {
                val m = GameMenu(
                    GameKind.CONNECT4, onRules = {}, onLeave = {}, mode = "Best of 3",
                    onNewGame = {}, onChangeMode = {}, onSwitchGame = {},
                )
                GameMenuRows(m, close = {}, then = { it })
            }
        }
    }

    @Test fun menuLight() = shot("toy-sheet-menu", dark = false) { menu() }

    @Test fun menuDark() = shot("toy-sheet-menu", dark = true) { menu() }
}
