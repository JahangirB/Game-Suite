package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.ui.art.color
import com.pippop.ui.screens.ChipPlayer
import com.pippop.ui.screens.GameFrame
import com.pippop.ui.theme.AppTheme
import org.junit.Test

/**
 * The backdrop behind a game: a soft tint and a faint pattern (tiles, clouds, discs), shown
 * in GameFrame around a plain placeholder board, for Dominoes, Balloon Words and Connect Four.
 */
class ScreenshotBackdropTest : ScreenshotBase() {
    @Composable
    private fun frame(kind: GameKind) {
        val p = AppTheme.palette
        GameFrame(
            title = kind.label,
            subtitle = "Game 1",
            players = listOf(ChipPlayer("You", null, kind.color, "0", active = true), ChipPlayer("Sam", null, p.muted, "0")),
            glow = kind.color,
        ) {
            Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)).background(p.card), contentAlignment = Alignment.Center) {
                Text("Board", color = p.text)
            }
            Text("Your turn", color = p.muted)
        }
    }

    @Test fun backdropDominoesLight() = shot("backdrop-dominoes", dark = false) { frame(GameKind.DOMINOES) }
    @Test fun backdropDominoesDark() = shot("backdrop-dominoes", dark = true) { frame(GameKind.DOMINOES) }
    @Test fun backdropBalloonLight() = shot("backdrop-balloon", dark = false) { frame(GameKind.BALLOON) }
    @Test fun backdropBalloonDark() = shot("backdrop-balloon", dark = true) { frame(GameKind.BALLOON) }
    @Test fun backdropConnect4Light() = shot("backdrop-connect4", dark = false) { frame(GameKind.CONNECT4) }
    @Test fun backdropConnect4Dark() = shot("backdrop-connect4", dark = true) { frame(GameKind.CONNECT4) }
}
