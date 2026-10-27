package com.arcade.sudoku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.nearby.LocalRoom
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.Digits
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Space
import com.arcade.sudoku.ui.theme.TextSize

/**
 * Over a game: "Reconnecting…" while this phone's link is down, and a calm banner when
 * someone else's phone goes quiet during a match. A race counts down to the moment the rules
 * give them up; a turn game waits for them. [onBack] goes back to the games.
 */
@Composable
fun ConnectionBanners(state: BattleUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val room = state.room ?: return
    val now = rememberTicker()
    if (state.reconnecting) {
        Banner(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppTheme.palette.primary)
                Spacer(Modifier.width(Space.M))
                Column {
                    BannerTitle("Reconnecting…")
                    BannerLine("Your game is kept. Hold on a moment.")
                }
            }
        }
        return
    }
    val inMatch = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
    val quiet = room.othersAtGame.firstOrNull { it.online == false && !it.out } ?: return
    if (!inMatch) return
    // "Keep waiting" hides it until they're back; if their phone goes quiet again, it shows again.
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    val goneFor = quiet.idleMs + (now + state.clockOffsetMs - room.serverTime)
    Banner(modifier) {
        BannerTitle("${quiet.name}'s phone went quiet")
        if (room.game.turnBased) {
            BannerLine("Turn games wait for ${quiet.name} to come back.")
        } else {
            val left = ((LocalRoom.FORFEIT_MS - goneFor) / 1000).coerceAtLeast(0)
            BannerLine("Waiting for ${quiet.name} to come back… ${left / 60}:${"%02d".format(left % 60)}", digits = true)
            BannerLine("If they're not back in time, they lose this game.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            TextButton(onClick = { dismissed = true }) { Text("Keep waiting", color = AppTheme.palette.primaryText, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onBack) { Text("Back to games", color = AppTheme.palette.primaryText, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Banner(modifier: Modifier, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Surface(
        color = p.card,
        shape = Shapes.L,
        border = BorderStroke(2.dp, p.warning),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.L, vertical = Space.S),
    ) {
        Column(Modifier.padding(horizontal = Space.L, vertical = Space.M), verticalArrangement = Arrangement.spacedBy(Space.XS)) { content() }
    }
}

@Composable
private fun BannerTitle(text: String) {
    Text(text, color = AppTheme.palette.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
}

@Composable
private fun BannerLine(text: String, digits: Boolean = false) {
    if (digits) {
        Text(text, style = Digits, color = AppTheme.palette.muted, fontSize = TextSize.Body)
    } else {
        Text(text, color = AppTheme.palette.muted, fontSize = TextSize.Body)
    }
}
