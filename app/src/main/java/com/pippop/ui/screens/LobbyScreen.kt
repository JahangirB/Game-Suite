package com.pippop.ui.screens

import com.pippop.ui.theme.seatMark
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Tracking
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.SeatColors
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.Invite
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.ui.BattleMode
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.ShownReaction
import com.pippop.ui.art.ReactionImage
import com.pippop.ui.art.color
import com.pippop.ui.art.KeyArt
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.loopingFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.launch

@Composable
fun LobbyScreen(state: BattleUiState, profile: Profile, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val context = LocalContext.current
    val room = state.room ?: return
    val opponent = room.opponent?.takeIf { !it.left }
    val now = rememberTicker()
    // Players already in the room when the lobby opens (or after a rotation) don't pop in.
    var known by rememberSaveable(room.code) { mutableStateOf(room.others.filter { !it.left }.joinToString("\u0001") { it.name }) }
    val knownNames = known.split("\u0001")
    val here = room.others.filter { !it.left }.map { it.name }
    // Remember them once drawn, so each pops only once.
    SideEffect { if (here.any { it !in knownNames }) known = (knownNames + here).distinct().joinToString("\u0001") }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("${room.game.label} lobby", onLeave)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LobbyBanner(room.game)
            if (state.mode == BattleMode.NEARBY) {
                AppCard("Nearby game") {
                    Text(
                        if (room.isHost) {
                            "Room code ${room.code}. The other phone types it in and joins."
                        } else {
                            opponent?.let { "Connected to ${it.name}'s phone." } ?: "Connected to the other phone."
                        },
                        color = p.muted,
                        fontSize = TextSize.Body,
                    )
                }
            } else AppCard("Game code") {
                Text(
                    room.code,
                    color = p.primaryText,
                    fontSize = TextSize.DisplayL,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = Tracking.Code,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (room.isHost) {
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(
                                    Intent.EXTRA_TEXT,
                                    Invite(profile.scriptUrl, room.code).toShareText(),
                                )
                            context.startActivity(Intent.createChooser(send, "Send the code"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send the code")
                    }
                }
            }

            AppCard(if (room.maxPlayers > 2) "Players (up to ${room.maxPlayers})" else "Players") {
                PlayerRow(room.me, isMe = true, color = p.primary, reaction = visibleReaction(state.myReaction, now), seat = 0, game = room.game)
                room.others.forEachIndexed { i, other ->
                    if (!other.left) key(other.name) {
                        val reaction = state.otherReactions[other.name] ?: state.opponentReaction.takeIf { room.others.size == 1 }
                        PlayerRow(
                            other, isMe = false, color = SeatColors[i % SeatColors.size], reaction = visibleReaction(reaction, now),
                            seat = i + 1, game = room.game, arrived = other.name !in knownNames,
                        )
                    }
                }
                val joined = room.present.size
                if (joined == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PipPopLoader(Modifier.size(width = 36.dp, height = 22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(if (room.maxPlayers > 2) "Waiting for others to join…" else "Waiting for someone to join…", color = p.muted)
                    }
                } else if (joined < room.maxPlayers - 1) {
                    val free = room.maxPlayers - 1 - joined
                    Text(
                        "Room for $free more. " + if (state.mode == BattleMode.NEARBY) "They join with code ${room.code}." else "Send them the code.",
                        color = p.muted, fontSize = TextSize.Body,
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
                if (room.game == GameKind.TTT) TttModeCard(room, state.busy, vm::setTttClassic)
                if (room.game == GameKind.MEMORY) MemoryDeckCard(room, state.busy, vm::setMemoryCards)
                if (room.game == GameKind.CODE) CodeRulesCard(room, state.busy, vm::setCodeRules)
                if (room.game == GameKind.DOTS) DotsBoardCard(room, state.busy, vm::setDotsBoard)
                if (room.game == GameKind.DRAW) DrawModeCard(room, state.busy, vm::setDrawRace)
                if (room.game == GameKind.BLOCKS) AppCard("Help") { BlocksHintsSwitch() }
                // Only servers that know the levels send one.
                if (room.game == GameKind.GOLF && room.golfLevel != null) GolfLevelCard(room, state.busy, vm::setGolfLevel)
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
                if (room.game == GameKind.SUDOKU && room.sudokuModes) SudokuModeCard(room, state.busy, vm::setSudokuTogether, vm::setSudokuShape)
                AppCard("Choose the level") {
                    LevelPicker(room, state.busy, onPick = vm::chooseLevel)
                }
            }
            if (opponent != null) ReactionBar(onReact = vm::react)
        }
    }
}

/**
 * The lobby's banner: the game's key art, drifting slowly side to side,
 * with the game's name on a dark chip. Still when motion is reduced.
 */
@Composable
internal fun LobbyBanner(game: GameKind) {
    val drift = loopingFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), still = 0.5f, label = "bannerDrift")
    // Decorative: the top bar already names the game.
    Box(Modifier.fillMaxWidth().height(96.dp).clip(Shapes.M).clearAndSetSemantics {}) {
        KeyArt(
            game,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
                translationX = (drift.value - 0.5f) * 20.dp.toPx()
            },
        )
        Text(
            game.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = TextSize.BodyL,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .clip(Shapes.Full).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** One player's row; an [arrived] player's avatar bounces in with a burst of rays in their colour. */
@Composable
internal fun PlayerRow(player: PlayerInfo, isMe: Boolean, color: Color, reaction: ShownReaction?, seat: Int, game: GameKind, arrived: Boolean = false) {
    val p = AppTheme.palette
    val pops = arrived && !LocalReduceMotion.current
    val pop = remember { Animatable(if (pops) 0f else 1f) }
    val rays = remember { Animatable(if (pops) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (pops) {
            launch { rays.animateTo(1f, tween(500)) }
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.drawWithContent {
                drawContent()
                burst(center, rays.value, color, size.minDimension * 0.9f)
            }.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        ) { PlayerAvatar(player.name, color, avatar = player.avatar, mark = seatMark(seat)) }
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
            // Connected, but not in this game yet: in the menu or still on another game's screen.
            player.at == null -> Pill("In the menu", p.muted)
            player.at != game -> Pill("In ${player.at.label}", p.muted)
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
                            shape = Shapes.M,
                            color = if (mine) p.primary else p.accent,
                            border = if (theirs && !mine) BorderStroke(2.dp, OpponentColor) else null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (if (mine) "✓ " else "") + level.label,
                                    color = if (mine) p.onPrimary else if (canPick) p.primaryText else p.muted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = TextSize.BodyL,
                                )
                            }
                        }
                        Text(
                            if (theirs) pickers.joinToString(", ") { it.name } + if (pickers.size == 1) "'s pick" else "" else " ",
                            color = p.opponentText,
                            fontSize = TextSize.Micro,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        val (message, tone) = if (room.present.size > 1) groupLevelStatus(room) else levelStatus(room, opponent)
        StatusLine(message, tone)
    }
}


/** The level line with three or four players: everyone's choice, and what is still needed. */
@Composable
private fun groupLevelStatus(room: RoomState): Pair<String, Tone> {
    val everyone = listOf(room.me) + room.present
    val chosen = everyone.mapNotNull { it.difficulty?.takeIf { _ -> it.ready } }
    val choices = everyone.joinToString(" · ") { (if (it === room.me) "You" else it.name) + ": " + (it.difficulty?.takeIf { _ -> it.ready }?.label ?: "…") }
    return when {
        chosen.size == everyone.size && chosen.toSet().size == 1 -> "Everyone chose ${chosen[0].label}. Starting…" to Tone.Good
        chosen.toSet().size > 1 -> "$choices. Pick the same level to start." to Tone.Warning
        else -> "$choices. It starts when you all pick the same level." to Tone.Neutral
    }
}

@Composable
private fun levelStatus(room: RoomState, opponent: PlayerInfo?): Pair<String, Tone> {
    val mine = room.me.difficulty?.takeIf { room.me.ready }
    val theirs = opponent?.difficulty?.takeIf { opponent.ready }
    return when {
        opponent == null -> "Waiting for someone to join." to Tone.Neutral
        mine != null && theirs != null && mine != theirs ->
            "${opponent.name} picked ${theirs.label}. Pick ${theirs.label} too to start." to Tone.Warning
        mine != null && theirs != null -> "Both chose ${mine.label}. Starting…" to Tone.Good
        mine != null -> "Waiting for ${opponent.name}… Tap ${mine.label} again to cancel." to Tone.Neutral
        theirs != null -> "${opponent.name} picked ${theirs.label}. Pick it too to start." to Tone.Neutral
        else -> "Pick a level. It starts when you both pick the same one." to Tone.Neutral
    }
}
