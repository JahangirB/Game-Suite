package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pippop.data.GameKind
import com.pippop.ui.screens.RulesSheetBody
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/** The rules sheet (its card on a dim layer, as the sheet's window is not in the test's root). */
class ScreenshotRulesTest : ScreenshotBase() {
    @Composable
    private fun rules(kind: GameKind) {
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard { RulesSheetBody(kind, null, onShowHow = {}, onDismiss = {}) }
        }
    }

    @Test fun golfLight() = shot("rules-golf", dark = false) { rules(GameKind.GOLF) }

    @Test fun golfDark() = shot("rules-golf", dark = true) { rules(GameKind.GOLF) }

    @Test fun ludoLight() = shot("rules-ludo", dark = false) { rules(GameKind.LUDO) }

    @Test fun ludoDark() = shot("rules-ludo", dark = true) { rules(GameKind.LUDO) }
}
