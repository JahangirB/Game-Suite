package com.pippop.ui.screens

import androidx.compose.runtime.LaunchedEffect
import com.pippop.ui.theme.Mark
import com.pippop.ui.theme.seatMark
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.SeatColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.ui.BattleUiState
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.ShownReaction
import com.pippop.ui.art.ReactionImage
import com.pippop.ui.art.rules
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont

/** One button in the dock at the bottom of a game. */
class DockAction(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

/** A player in the chips at the top of a game. [active] gets a glow in the game's colour. */
class ChipPlayer(
    val name: String,
    val avatar: Avatar?,
    val color: Color,
    val detail: String? = null,
    val reaction: ShownReaction? = null,
    val active: Boolean = false,
    /** The seat's shape on the avatar (you ●, then ▲, ■, ◆). */
    val mark: Mark? = null,
)

/**
 * The layout every game shares: a title, the two players as chips, the game itself, and a
 * dock of round buttons at the bottom, within reach of your thumb. [tray] shows just above
 * the dock while [trayVisible] (the reactions).
 *
 * The game fits the screen without scrolling: boards take the room left over through
 * [BoardArea]. Only [scrollable] screens (a choice of levels or modes before a game) scroll.
 * [popup] (the result at the end of a match) covers the game but not the dock, which keeps working.
 */
@Composable
fun GameFrame(
    title: String,
    subtitle: String?,
    players: List<ChipPlayer>,
    glow: Color,
    dock: List<DockAction>,
    trailing: @Composable () -> Unit = {},
    trayVisible: Boolean = false,
    tray: @Composable () -> Unit = {},
    scrollable: Boolean = false,
    popup: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * The players can fold into one bar to give the board more room: by a swipe up, the "Hide
     * players" button, or by itself when [focusWhen] turns true (your turn to fire). Tapping
     * the bar or swiping down brings them back.
     */
    swipeToFocus: Boolean = false,
    focusWhen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = AppTheme.palette
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focusWhen) { if (focusWhen && swipeToFocus) focused = true }
    val swipe = if (!swipeToFocus) Modifier else Modifier.pointerInput(Unit) {
        // A real swipe, well past the drift a tap on the board is allowed.
        val far = FOLD_SWIPE_DP.dp.toPx()
        var pulled = 0f
        detectVerticalDragGestures(
            onDragStart = { pulled = 0f },
            onDragEnd = { if (pulled < -far) focused = true else if (pulled > far) focused = false },
        ) { _, dy -> pulled += dy }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .then(swipe)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (swipeToFocus && focused) FoldedPlayers(players, glow) { focused = false }
                if (!(swipeToFocus && focused)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
                        if (subtitle != null) Text(subtitle, color = p.muted, fontSize = TextSize.Body)
                    }
                    trailing()
                }
                if (players.isNotEmpty() && !(swipeToFocus && focused)) {
                    // Three or four players share the row as smaller chips.
                    val compact = players.size > 2
                    // While someone plays, everyone else's chip dims, so whose turn it is reads at a glance.
                    val someoneActive = players.any { it.active }
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp), modifier = Modifier.fillMaxWidth()) {
                        players.forEach { PlayerChip(it, glow, Modifier.weight(1f).alpha(if (someoneActive && !it.active) 0.55f else 1f), compact) }
                    }
                    if (swipeToFocus) {
                        Text(
                            "⌃  Hide players", color = p.primaryText, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().clickable { focused = true },
                        )
                    }
                }
                content()
            }
            popup?.invoke(this)
        }
        if (trayVisible) {
            Surface(color = p.card, border = BorderStroke(2.dp, p.ink), shape = Shapes.Top) {
                Box(Modifier.fillMaxWidth().padding(12.dp)) { tray() }
            }
        }
        GameDock(dock)
    }
}

/** How far a swipe must go to fold or unfold the players. */
private const val FOLD_SWIPE_DP = 48

/**
 * The room left in a game screen, for a board [aspect] wide per unit of height: the board
 * gets the largest size that fits, centred. [weight] shares the room when there are two boards.
 */
@Composable
fun ColumnScope.BoardArea(aspect: Float, weight: Float = 1f, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.weight(weight).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = if (maxHeight == Dp.Infinity) maxWidth else minOf(maxWidth, maxHeight * aspect)
        content(Modifier.width(width).height(width / aspect))
    }
}

/** The players folded into one bar: each with their score, and a way back to the full row. */
@Composable
private fun FoldedPlayers(players: List<ChipPlayer>, glow: Color, onOpen: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        onClick = onOpen,
        color = p.card,
        shape = Shapes.Full,
        border = BorderStroke(2.dp, p.ink),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                players.joinToString("  ·  ") { listOfNotNull(it.name, it.detail).joinToString(" ") },
                style = Digits, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("Players ⌄", color = if (players.any { it.active }) glow else p.primaryText, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlayerChip(player: ChipPlayer, glow: Color, modifier: Modifier, compact: Boolean = false) {
    val p = AppTheme.palette
    val edge = if (player.active) glow else p.ink
    Surface(
        color = p.card,
        shape = Shapes.L,
        border = BorderStroke(if (player.active) 3.dp else 2.dp, edge),
        modifier = modifier.pressedEdge(edge, Radius.L),
    ) {
        if (compact) {
            // Avatar over name and detail, so four fit across a phone.
            Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.TopEnd) {
                    PlayerAvatar(player.name, player.color, size = 30, avatar = player.avatar, mark = player.mark)
                    if (player.reaction != null) ReactionImage(player.reaction.reaction, 20.dp, startedAt = player.reaction.at)
                }
                Text(player.name, color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (player.detail != null) {
                    Text(player.detail, style = Digits, color = if (player.active) glow else p.muted, fontWeight = FontWeight.SemiBold, fontSize = TextSize.Micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            return@Surface
        }
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(player.name, player.color, size = 34, avatar = player.avatar, mark = player.mark)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(player.name, color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.BodyL, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (player.detail != null) {
                    Text(player.detail, style = Digits, color = if (player.active) glow else p.muted, fontWeight = FontWeight.SemiBold, fontSize = TextSize.Label, maxLines = 1)
                }
            }
            if (player.reaction != null) ReactionImage(player.reaction.reaction, 28.dp, startedAt = player.reaction.at)
        }
    }
}


/**
 * Chips for you and everyone else in a battle. [detail] gives the line under each name;
 * [active] marks whoever is playing now.
 */
fun battleChips(
    room: RoomState,
    state: BattleUiState,
    now: Long,
    myColor: Color,
    detail: (PlayerInfo, Boolean) -> String?,
    active: (PlayerInfo, Boolean) -> Boolean = { _, _ -> false },
    otherColor: (Int) -> Color = { SeatColors[it % SeatColors.size] },
): List<ChipPlayer> = listOf(
    ChipPlayer("You", room.me.avatar, myColor, detail(room.me, true), visibleReaction(state.myReaction, now), active(room.me, true), seatMark(0)),
) + room.others.mapIndexed { i, other ->
    // Presence shows in the chip's detail line, not glued onto the name where it truncates.
    val presence = when {
        other.left -> "Left"
        other.online == false -> "Away"
        else -> null
    }
    ChipPlayer(
        other.name, other.avatar, otherColor(i), presence ?: detail(other, false),
        visibleReaction(state.otherReactions[other.name] ?: state.opponentReaction.takeIf { room.others.size == 1 }, now),
        active(other, false),
        seatMark(i + 1),
    )
}

@Composable
private fun GameDock(actions: List<DockAction>) {
    val p = AppTheme.palette
    Column {
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.ink))
        Row(
            Modifier.fillMaxWidth().background(p.card).padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            actions.forEach { action ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(Shapes.M)
                        .clickable(enabled = action.enabled, onClick = action.onClick)
                        .alpha(if (action.enabled) 1f else 0.4f)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(46.dp).pressedEdge(p.ink, 23.dp, 2.dp).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape),
                    ) { Icon(action.icon, contentDescription = null, tint = p.text, modifier = Modifier.size(22.dp)) }
                    Text(action.label, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The dock for a game against your partner: back to the games, send a reaction, read the rules. */
fun battleDock(finished: Boolean, onLeave: () -> Unit, onReact: (() -> Unit)?, onRules: () -> Unit) = listOfNotNull(
    DockAction("Games", Icons.AutoMirrored.Filled.ArrowBack, onClick = onLeave),
    onReact?.let { DockAction("React", Icons.Filled.Face, onClick = it) },
    DockAction("Rules", Icons.Filled.Info, onClick = onRules),
)

/** How to play [kind], from the dock's Rules button. */
@Composable
fun RulesDialog(kind: GameKind, extra: (@Composable () -> Unit)? = null, onDismiss: () -> Unit) {
    val p = AppTheme.palette
    // "Show me how" plays the game's tutorial again, then comes back to the rules.
    var showing by remember { mutableStateOf(false) }
    if (showing) return TutorialDialog(kind) { showing = false }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(kind.label, color = p.text, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(kind.rules, color = p.text, fontSize = TextSize.BodyL)
                extra?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = p.primaryText, fontWeight = FontWeight.Bold) } },
        dismissButton = if (hasTutorial(kind)) ({ TextButton(onClick = { showing = true }) { Text("Show me how", color = p.primaryText) } }) else null,
    )
}
