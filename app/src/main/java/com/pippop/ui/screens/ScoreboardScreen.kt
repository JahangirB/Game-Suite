package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pippop.data.GameScore
import com.pippop.data.MatchRecord
import com.pippop.data.ScoreStore
import com.pippop.data.Scoreboard
import com.pippop.data.Trophy
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import java.time.format.TextStyle
import java.util.Locale

/**
 * Our scoreboard: who's ahead this month against your partner, each game's wins this month
 * (tap one for its last ten results and best streak), your streak, and a shelf with a trophy
 * for each month's winner. Every match you finish together counts; it lives on this phone.
 */
@Composable
fun ScoreboardScreen(onBack: () -> Unit) {
    val p = AppTheme.palette
    val store = ScoreStore.get(LocalContext.current)
    val records by store.records.collectAsState()
    val board = remember(records) { Scoreboard.of(records, System.currentTimeMillis()) }
    var open by remember { mutableStateOf<GameScore?>(null) }
    open?.let { g -> GameHistorySheet(g, board.partner ?: "Partner") { open = null } }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Scoreboard", onBack) {}
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            val partner = board.partner
            if (partner == null) {
                AppCard("Nothing yet") {
                    Text("Play a game with your partner: every match you finish together counts here, month by month.", color = p.muted, fontSize = TextSize.Body)
                }
                return@Column
            }
            MonthCard(board, partner)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This month", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(streakLine(board.streak, partner), color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
            }
            if (board.games.isEmpty()) Text("No games together yet this month.", color = p.muted, fontSize = TextSize.Body)
            val most = board.games.maxOfOrNull { maxOf(it.mine, it.theirs) }?.coerceAtLeast(1) ?: 1
            board.games.forEach { g -> GameBar(g, most) { open = g } }
            Text("Trophies", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            TrophyShelf(board.trophies, partner)
            Text(
                "${board.total} games together so far · your best run: ${board.bestStreak} wins in a row. Kept on this phone; your partner's keeps its own.",
                color = p.muted, fontSize = TextSize.Label,
            )
            Spacer(Modifier.height(Space.L))
        }
    }
}

private fun streakLine(streak: Int, partner: String) = when {
    streak > 1 -> "Streak: you · $streak wins"
    streak < -1 -> "Streak: $partner · ${-streak} wins"
    else -> ""
}

/** The month: both of you and the score, the days left, and who leads. */
@Composable
private fun MonthCard(board: Scoreboard, partner: String) {
    val p = AppTheme.palette
    val name = board.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()
    Surface(color = p.primary, shape = Shapes.L, border = BorderStroke(2.dp, p.ink), modifier = Modifier.fillMaxWidth().pressedEdge(p.ink, 16.dp)) {
        Column(Modifier.padding(Space.L), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.XS)) {
            Text("$name · ${board.daysLeft} DAYS LEFT", color = p.onPrimary.copy(alpha = 0.8f), fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.L)) {
                Text("You", color = p.onPrimary, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                Text("${board.mine}", style = Digits, color = p.onPrimary, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text("–", color = p.onPrimary.copy(alpha = 0.7f), fontSize = TextSize.DisplayM)
                Text("${board.theirs}", style = Digits, color = p.onPrimary, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text(partner, color = p.onPrimary, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            }
            Text(
                when {
                    board.mine > board.theirs -> "You lead by ${board.mine - board.theirs}"
                    board.theirs > board.mine -> "$partner leads by ${board.theirs - board.mine}"
                    board.mine == 0 -> "A fresh month"
                    else -> "Level"
                },
                color = p.onPrimary, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** One game: its icon and name, your wins as a bar to the left and your partner's to the right. */
@Composable
private fun GameBar(g: GameScore, most: Int, onClick: () -> Unit) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.M, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = Space.M, vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp).clip(Shapes.S).background(g.game.color).border(1.5.dp, p.ink, Shapes.S)) {
                GameIcon(g.game, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Space.S))
            Text(g.game.label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${g.mine}", style = Digits, color = p.primaryText, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(Space.XS))
            Canvas(Modifier.width(110.dp).height(12.dp)) {
                val mid = size.width / 2
                val a = mid * g.mine / most
                val b = mid * g.theirs / most
                drawRoundRect(p.primary, Offset(mid - a - 2, 0f), androidx.compose.ui.geometry.Size(a, size.height), androidx.compose.ui.geometry.CornerRadius(6f))
                drawRoundRect(OpponentColor, Offset(mid + 2, 0f), androidx.compose.ui.geometry.Size(b, size.height), androidx.compose.ui.geometry.CornerRadius(6f))
            }
            Spacer(Modifier.width(Space.XS))
            Text("${g.theirs}", style = Digits, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
        }
    }
}

/** A trophy for each month that's ended, in the winner's colour; a tie gets a grey one. */
@Composable
private fun TrophyShelf(trophies: List<Trophy>, partner: String) {
    val p = AppTheme.palette
    Surface(color = p.accent, shape = Shapes.M, border = BorderStroke(1.5.dp, p.ink), modifier = Modifier.fillMaxWidth()) {
        if (trophies.isEmpty()) {
            Text("The first trophy goes to this month's winner on the 1st.", color = p.muted, fontSize = TextSize.Body, modifier = Modifier.padding(Space.M))
            return@Surface
        }
        Row(Modifier.padding(Space.M), horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            trophies.takeLast(5).forEach { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    val holder = when (t.winner) {
                        MatchRecord.ME -> p.primary
                        null -> p.hairline
                        else -> OpponentColor
                    }
                    Cup(holder, Modifier.size(36.dp))
                    Text(t.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                    Text(
                        when (t.winner) {
                            MatchRecord.ME -> "You"
                            null -> "Tie"
                            else -> partner
                        } + " ${t.mine}–${t.theirs}",
                        color = p.muted, fontSize = TextSize.Micro, maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
            }
            repeat(5 - trophies.takeLast(5).size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** A gold cup with the winner's colour on its badge. */
@Composable
private fun Cup(badge: Color, modifier: Modifier) {
    val gold = AppTheme.palette.warning
    val ink = AppTheme.palette.ink
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cup = Path().apply {
            moveTo(w * 0.18f, h * 0.08f)
            lineTo(w * 0.82f, h * 0.08f)
            lineTo(w * 0.74f, h * 0.48f)
            quadraticTo(w * 0.5f, h * 0.7f, w * 0.26f, h * 0.48f)
            close()
        }
        drawPath(cup, gold)
        drawRect(gold, Offset(w * 0.44f, h * 0.6f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.18f))
        drawRoundRect(ink, Offset(w * 0.28f, h * 0.78f), androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.12f), androidx.compose.ui.geometry.CornerRadius(4f))
        drawCircle(badge, w * 0.11f, Offset(w * 0.5f, h * 0.3f))
    }
}

/** A game's last ten results with your partner, newest first, and your best streak in it. */
@Composable
private fun GameHistorySheet(g: GameScore, partner: String, onDismiss: () -> Unit) {
    val p = AppTheme.palette
    InfoSheet(g.game.label, onDismiss = onDismiss) {
        Text("This month: you ${g.mine}, $partner ${g.theirs}" + if (g.ties > 0) ", ${g.ties} tied" else "", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
        Text("Your best run: ${g.bestStreak} wins in a row", color = p.muted, fontSize = TextSize.Body)
        Text("LAST TEN, NEWEST FIRST", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
        g.last.forEach { r ->
            val date = java.time.Instant.ofEpochMilli(r.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
                    color = p.muted, fontSize = TextSize.Body, modifier = Modifier.width(70.dp),
                )
                Text(
                    when {
                        r.winner == null -> "Tie"
                        r.won(MatchRecord.ME) -> "You won"
                        else -> "${r.winner} won"
                    },
                    color = if (r.won(MatchRecord.ME)) p.good else p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
