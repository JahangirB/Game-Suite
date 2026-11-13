package com.pippop.ui.proto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.ui.art.AvatarImage
import com.pippop.ui.art.GameIcon
import com.pippop.ui.screens.pressedEdge
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize

/** Which home is shown: nobody connected, you and Maya, or three of you. */
enum class HomeMode { SOLO, TWO, THREE }

/** The order this player arranged, with the four games they put away at the end. */
val HomeOrder = listOf(
    GameKind.SUDOKU, GameKind.SHIPS, GameKind.CONNECT4, GameKind.LUDO, GameKind.WORDS, GameKind.DOMINOES,
    GameKind.CLASH, GameKind.GOLF, GameKind.MEMORY, GameKind.DOTS, GameKind.DRAW, GameKind.TTT,
    GameKind.BINGO, GameKind.HOCKEY, GameKind.CODE, GameKind.BALLOON,
)
val PutAway = listOf(GameKind.NUMBERS, GameKind.GRID, GameKind.SLIDE, GameKind.BLOCKS)

@Composable
fun HomeHeader() {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hi, Sam", color = p.muted, fontSize = TextSize.BodyL)
            Text("Pip & Pop", color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape)) { AvatarImage(Avatar.FOX, Modifier.fillMaxSize()) }
    }
}

/** Not connected: the switch and the one button, on one line. What each way needs is said in the sheet the button opens. */
@Composable
fun ConnectBar() {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Segmented(listOf("Nearby", "Online"), 1, Modifier.width(148.dp), height = 46.dp)
            Row(
                Modifier.weight(1f).height(46.dp).pressedEdge(p.ink, 23.dp).clip(Shapes.Full).background(p.primary).border(2.dp, p.ink, Shapes.Full),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(p.card).border(1.5.dp, p.ink, CircleShape)) { AvatarImage(Avatar.PANDA, Modifier.fillMaxSize()) }
                Spacer(Modifier.width(8.dp))
                Text("Connect with Maya", color = p.onPrimary, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** Connected: who you're with on one line, then what is happening (someone waiting, or the hint). */
@Composable
fun RoomBar(mode: HomeMode) {
    val p = AppTheme.palette
    val three = mode == HomeMode.THREE
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Faces(if (three) listOf(Avatar.FOX, Avatar.PANDA, Avatar.OWL) else listOf(Avatar.FOX, Avatar.PANDA), size = 36.dp, step = 25.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (three) "Maya & Leo" else "Maya", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("● Online · room K7Q2NB", color = p.good, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text("Leave", color = p.bad, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            }
            when (mode) {
                HomeMode.TWO -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Maya is waiting in Ludo", color = p.good, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.height(40.dp).pressedEdge(p.ink, 20.dp).clip(Shapes.Full).background(p.primary).border(2.dp, p.ink, Shapes.Full).padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Join", color = p.onPrimary, fontSize = TextSize.Body, fontWeight = FontWeight.Bold) }
                }
                else -> Note("Pick a game: whoever picks it sets it up.")
            }
        }
    }
}

/** How many can play a game, as the tile says it. */
fun seatsOf(kind: GameKind) = if (kind.maxPlayers > 2) "2–4" else "2"

/** The proposed home: one line to connect, the last games played, then every game as its picture. */
@Composable
fun HomeNew(mode: HomeMode) {
    val p = AppTheme.palette
    val connected = mode != HomeMode.SOLO
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeHeader()
        if (connected) RoomBar(mode) else ConnectBar()

        Eyebrow("Jump back in")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArtTile(GameKind.SUDOKU, Modifier.weight(1f), tag = "27% done", label = "Sudoku")
            ArtTile(
                GameKind.LUDO, Modifier.weight(1f), tag = "Yesterday",
                face = Avatar.PANDA.takeIf { mode == HomeMode.TWO }, ring = p.good.takeIf { mode == HomeMode.TWO },
            )
            ArtTile(GameKind.WORDS, Modifier.weight(1f), tag = "Sunday")
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Games", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // The scoreboard: a cup. Surprise us: a die. Arrange: a grip of six dots.
            RoundButton { TrophyCup(Modifier.size(24.dp)) }
            RoundButton { GameIcon(GameKind.LUDO, Modifier.size(22.dp), tint = p.text) }
            RoundButton {
                Canvas(Modifier.size(18.dp)) {
                    for (i in 0..1) for (j in 0..2) drawCircle(p.text, 1.8.dp.toPx(), Offset(size.width * (0.3f + 0.4f * i), size.height * (0.18f + 0.32f * j)))
                }
            }
        }
        HomeOrder.chunked(3).forEach { row ->
            Shelf {
                row.forEach { kind ->
                    val tooFew = mode == HomeMode.THREE && kind.maxPlayers == 2
                    ArtTile(
                        kind, Modifier.weight(1f),
                        seats = seatsOf(kind).takeIf { connected },
                        dim = "2 players".takeIf { tooFew },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Surface(color = p.accent, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("More games · ${PutAway.size}", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                PutAway.forEach { GameBadge(it, 26.dp, p.muted) }
                Text("⌄", color = p.muted, fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
            }
        }
    }
}
