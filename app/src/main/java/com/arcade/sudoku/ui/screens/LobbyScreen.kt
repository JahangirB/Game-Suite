package com.arcade.sudoku.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.Invite
import com.arcade.sudoku.data.PlayerInfo
import com.arcade.sudoku.data.Profile
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.ui.BattleMode
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.ShownReaction
import com.arcade.sudoku.ui.art.ReactionImage
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.OpponentColor

@Composable
fun LobbyScreen(state: BattleUiState, profile: Profile, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val context = LocalContext.current
    val room = state.room ?: return
    val opponent = room.opponent?.takeIf { !it.left }
    val now = rememberTicker()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("${room.game.label} lobby", onLeave)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.mode == BattleMode.NEARBY) {
                AppCard("Nearby battle") {
                    Text(
                        if (room.isHost) {
                            "Your friend opens Battle a friend → Nearby, taps Find and picks your name. " +
                                "Keep Bluetooth and Wi‑Fi on; no internet is needed."
                        } else {
                            "Connected directly to ${opponent?.name ?: "the host"}'s phone."
                        },
                        color = p.muted,
                        fontSize = 14.sp,
                    )
                }
            } else AppCard("Secret code") {
                Text(
                    room.code,
                    color = p.primary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(profile.scriptUrl, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (room.isHost) {
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(
                                    Intent.EXTRA_TEXT,
                                    Invite(profile.scriptUrl, room.code, profile.serverPassword).toShareText(),
                                )
                            context.startActivity(Intent.createChooser(send, "Invite your opponent"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share link + code")
                    }
                }
            }

            AppCard(if (room.maxPlayers > 2) "Players (up to ${room.maxPlayers})" else "Players") {
                PlayerRow(room.me, isMe = true, color = p.primary, reaction = visibleReaction(state.myReaction, now))
                room.others.forEachIndexed { i, other ->
                    if (!other.left) {
                        val reaction = state.otherReactions[other.name] ?: state.opponentReaction.takeIf { room.others.size == 1 }
                        PlayerRow(other, isMe = false, color = SeatColors[i % SeatColors.size], reaction = visibleReaction(reaction, now))
                    }
                }
                val joined = room.present.size
                if (joined == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(if (room.maxPlayers > 2) "Waiting for others to join…" else "Waiting for your opponent to join…", color = p.muted)
                    }
                } else if (joined < room.maxPlayers - 1) {
                    val free = room.maxPlayers - 1 - joined
                    Text(
                        "Room for $free more. " + if (state.mode == BattleMode.NEARBY) "They pick your name under Nearby." else "Share the code to invite them.",
                        color = p.muted, fontSize = 13.sp,
                    )
                }
            }

            if (room.game == GameKind.SHIPS) {
                FleetCard(
                    fleet = if (room.me.ready) room.ships?.myFleet.orEmpty() else state.fleetDraft,
                    ready = room.me.ready,
                    opponent = opponent,
                    busy = state.busy,
                    onShuffle = vm::shuffleFleet,
                    onReady = vm::readyShips,
                    onMove = vm::setFleet,
                )
            } else if (room.game.readyOnly) {
                if (room.game == GameKind.DOMINOES) DominoOptionsCard(room, state.busy, vm::setDominoOptions)
                if (room.game == GameKind.NUMBERS) NumbersLevelCard(room, state.busy, vm::setNumberLevel)
                if (room.game == GameKind.WORDS) WordLevelCard(room, state.busy, vm::setWordLevel)
                if (room.game == GameKind.SLIDE) SlideLevelCard(room, state.busy, vm::setSlideLevel)
                TurnReadyCard(
                    room, state.busy,
                    when (room.game) {
                        GameKind.NUMBERS -> vm::readyNumbers
                        GameKind.GRID -> vm::readyGrid
                        GameKind.SLIDE -> vm::readySlide
                        else -> vm::readyTurns
                    },
                )
            } else {
                AppCard("Choose the level") {
                    LevelPicker(room, state.busy, onPick = vm::chooseLevel)
                }
            }
            if (opponent != null) ReactionBar(onReact = vm::react)
        }
    }
}

@Composable
private fun PlayerRow(player: PlayerInfo, isMe: Boolean, color: Color, reaction: ShownReaction?) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        PlayerAvatar(player.name, color, avatar = player.avatar)
        Spacer(Modifier.width(12.dp))
        Text(
            player.name + if (isMe) " (you)" else "",
            color = p.text,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (reaction != null) ReactionImage(reaction.reaction, 30.dp, Modifier.padding(end = 8.dp), startedAt = reaction.at)
        when {
            player.online == false -> Pill("Offline", p.bad)
            player.ready && player.difficulty != null -> Pill("Ready · ${player.difficulty.label}", p.good)
            player.ready -> Pill("Ready", p.good)
            else -> Pill("Choosing…", p.muted)
        }
    }
}

/**
 * The level buttons plus the status line. Tapping a level marks you ready for it; the battle
 * starts only when both players are ready with the same level.
 */
@Composable
fun LevelPicker(room: RoomState, busy: Boolean, onPick: (Difficulty) -> Unit) {
    val p = AppTheme.palette
    val opponent = room.opponent?.takeIf { !it.left }
    val canPick = room.present.isNotEmpty() && !busy

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.entries.chunked(3).forEach { rowLevels ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowLevels.forEach { level ->
                    val mine = room.me.ready && room.me.difficulty == level
                    // Who else has picked this level.
                    val pickers = room.present.filter { it.ready && it.difficulty == level }
                    val theirs = pickers.isNotEmpty()
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onPick(level) },
                            enabled = canPick,
                            shape = RoundedCornerShape(12.dp),
                            color = if (mine) p.primary else p.accent,
                            border = if (theirs && !mine) BorderStroke(2.dp, OpponentColor) else null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (if (mine) "✓ " else "") + level.label,
                                    color = if (mine) p.onPrimary else if (canPick) p.primary else p.muted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                        Text(
                            if (theirs) pickers.joinToString(", ") { it.name } + if (pickers.size == 1) "'s pick" else "" else " ",
                            color = OpponentColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        val (message, color) = if (room.present.size > 1) groupLevelStatus(room) else levelStatus(room, opponent)
        Text(message, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private val Warning = Color(0xFFE08A00)

/** The level line with three or four players: everyone's choice, and what is still needed. */
@Composable
private fun groupLevelStatus(room: RoomState): Pair<String, Color> {
    val p = AppTheme.palette
    val everyone = listOf(room.me) + room.present
    val chosen = everyone.mapNotNull { it.difficulty?.takeIf { _ -> it.ready } }
    val choices = everyone.joinToString(" · ") { (if (it === room.me) "You" else it.name) + ": " + (it.difficulty?.takeIf { _ -> it.ready }?.label ?: "…") }
    return when {
        chosen.size == everyone.size && chosen.toSet().size == 1 -> "Everyone chose ${chosen[0].label}. Starting…" to p.good
        chosen.toSet().size > 1 -> "$choices. Everyone must choose the same level." to Warning
        else -> "$choices. The game starts when everyone picks the same level." to p.muted
    }
}

@Composable
private fun levelStatus(room: RoomState, opponent: PlayerInfo?): Pair<String, Color> {
    val p = AppTheme.palette
    val mine = room.me.difficulty?.takeIf { room.me.ready }
    val theirs = opponent?.difficulty?.takeIf { opponent.ready }
    return when {
        opponent == null -> "Waiting for an opponent to join before you can choose a level." to p.muted
        mine != null && theirs != null && mine != theirs ->
            "Levels don't match: you chose ${mine.label}, ${opponent.name} chose ${theirs.label}. " +
                "Both players must choose the same level before the game can start." to Warning
        mine != null && theirs != null -> "Both chose ${mine.label}. Starting…" to p.good
        mine != null -> "You chose ${mine.label}. Waiting for ${opponent.name} to choose a level… " +
            "(tap it again to cancel)" to p.muted
        theirs != null -> "${opponent.name} chose ${theirs.label}. Choose ${theirs.label} too to start, " +
            "or pick another level." to p.muted
        else -> "Both players choose a level. The game starts when you both pick the same one." to p.muted
    }
}
