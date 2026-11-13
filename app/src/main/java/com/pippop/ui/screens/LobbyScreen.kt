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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Face
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import com.pippop.data.Reaction
import com.pippop.ui.art.facts
import com.pippop.ui.art.tagline
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Radius

@Composable
fun LobbyScreen(state: BattleUiState, profile: Profile, vm: BattleViewModel, onLeave: () -> Unit) {
    val room = state.room ?: return
    val opponent = room.opponent?.takeIf { !it.left }
    LobbyFrame(
        state, profile, onLeave,
        // Sudoku's levels are the ready taps, so it has no Ready button, unless the opener picks the level.
        onReady = if (room.game == GameKind.SHIPS) vm::readyShips else if (room.picksByOpener) vm::readySudoku else if (room.game.readyOnly) {
            when (room.game) {
                GameKind.NUMBERS -> vm::readyNumbers
                GameKind.GRID -> vm::readyGrid
                GameKind.SLIDE -> vm::readySlide
                else -> vm::readyTurns
            }
        } else null,
        onReact = vm::react,
    ) {
        if (room.game == GameKind.SHIPS) {
            FleetCard(
                fleet = if (room.me.ready) room.ships?.myFleet.orEmpty() else state.fleetDraft,
                ready = room.me.ready,
                opponent = opponent,
                busy = state.busy,
                onShuffle = vm::shuffleFleet,
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
            if (room.game == GameKind.BLOCKS) BlocksHelpCard()
            // Only servers that know the levels send one.
            if (room.game == GameKind.GOLF && room.golfLevel != null) GolfLevelCard(room, state.busy, vm::setGolfLevel)
        } else {
            if (room.picksByOpener) {
                SudokuOpenerCard(room, state.busy, vm::setSudokuLevel, vm::setSudokuTogether, vm::setSudokuShape)
            } else {
                if (room.game == GameKind.SUDOKU && room.sudokuModes) SudokuModeCard(room, state.busy, vm::setSudokuTogether, vm::setSudokuShape)
                AppCard("Choose the level") {
                    LevelPicker(room, state.busy, onPick = vm::chooseLevel)
                }
            }
        }
    }
}

/**
 * Every lobby's frame: the game's picture and line, the code (big while you are alone, a small
 * label once someone has joined), who is here, then the game's own [options] in the scrolling
 * part, and the Ready bar pinned below it. [onReady] is null where the game has no Ready button.
 */
@Composable
internal fun LobbyFrame(
    state: BattleUiState,
    profile: Profile,
    onLeave: () -> Unit,
    onReady: (() -> Unit)?,
    onReact: (Reaction) -> Unit,
    options: @Composable ColumnScope.() -> Unit,
) {
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
    val alone = room.present.isEmpty()
    val share = {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, Invite(profile.scriptUrl, room.code).toShareText())
        context.startActivity(Intent.createChooser(send, "Send the code"))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(room.game.label, onLeave)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LobbyBanner(room.game)
            if (alone) {
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
                    if (room.isHost) SecondaryButton("Send the code", share, Modifier.fillMaxWidth())
                }
            }

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (room.maxPlayers > 2 && alone) "Players (up to ${room.maxPlayers})" else "Players",
                        color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    // Someone has joined: the code folds into a small label here.
                    if (!alone) RoomChip(room.code, if (room.isHost && state.mode != BattleMode.NEARBY) share else null)
                }
                PlayerRow(room.me, isMe = true, color = p.primary, reaction = visibleReaction(state.myReaction, now), seat = 0, game = room.game, showLevel = !room.picksByOpener)
                room.others.forEachIndexed { i, other ->
                    if (!other.left) key(other.name) {
                        val reaction = state.otherReactions[other.name] ?: state.opponentReaction.takeIf { room.others.size == 1 }
                        PlayerRow(
                            other, isMe = false, color = SeatColors[i % SeatColors.size], reaction = visibleReaction(reaction, now),
                            seat = i + 1, game = room.game, arrived = other.name !in knownNames, showLevel = !room.picksByOpener,
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

            options()
        }
        ReadyBar(room, state.busy, onReady, if (opponent != null) onReact else null)
    }
}

/** What greyed rows say: who is setting up when that is certain (one other player), otherwise nothing is guessed. */
internal fun setupName(room: RoomState): String =
    room.present.singleOrNull()?.let { "${it.name} is setting up" } ?: "Waiting for the setup"

/**
 * One card of options for the next game, as [OptionRow]s. Whoever opened the game ([RoomState.setsOptions])
 * changes them while nothing is being sent; everyone else sees the same rows greyed under
 * "<Name> is setting up". [content] gets whether the rows can be changed now.
 */
@Composable
internal fun OptionsCard(room: RoomState, title: String, busy: Boolean, content: @Composable (enabled: Boolean) -> Unit) {
    AppCard(title) {
        if (!room.setsOptions) Text(setupName(room), color = AppTheme.palette.muted, fontSize = TextSize.Body)
        content(room.setsOptions && !busy)
    }
}

/** A level's label split at its " · " ("Medium · 5 letters"): the short part for the choice, the rest (capitalised) for the note under it. */
internal fun String.levelShort() = substringBefore(" · ")

internal fun String.levelNote() = substringAfter(" · ", "").replaceFirstChar { it.uppercase() }

/** "Room K7Q2NB" with a share mark: tapping it sends the code, where the player may. */
@Composable
private fun RoomChip(code: String, onShare: (() -> Unit)?) {
    val p = AppTheme.palette
    val shareName = "Room $code" + if (onShare != null) ", send the code" else ""
    Box(
        Modifier.heightIn(min = 48.dp)
            .then(if (onShare != null) Modifier.clip(Shapes.Full).clickable(role = Role.Button, onClick = onShare) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = shareName },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(Shapes.Full).background(p.accent).border(1.5.dp, p.ink, Shapes.Full).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Room $code", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, maxLines = 1)
            if (onShare != null) Icon(Icons.Filled.Share, contentDescription = null, tint = p.text, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * What the Ready button says: [title] and [sub] words, whether it is [green] (the others are
 * ready and waiting for you) and whether it can be tapped.
 */
internal data class ReadyLook(val title: String, val sub: String?, val green: Boolean, val enabled: Boolean)

/** "Maya", "Maya & Leo", or "3 more". */
private fun nameList(players: List<PlayerInfo>) = if (players.size <= 2) players.joinToString(" & ") { it.name } else "${players.size} more"

internal fun readyLook(room: RoomState): ReadyLook {
    val others = room.present
    val waiting = others.filter { !it.ready }
    val ready = others.filter { it.ready }
    return when {
        others.isEmpty() && room.others.isNotEmpty() -> ReadyLook("Ready", "${room.others[0].name} has left. Leave to start a new game.", false, false)
        others.isEmpty() -> ReadyLook("Ready", "Waiting for someone to join.", false, false)
        room.me.ready && waiting.isEmpty() -> ReadyLook("Starting…", null, true, true)
        room.me.ready -> ReadyLook("Waiting for ${nameList(waiting)}…", "Cancel", false, true)
        waiting.isEmpty() -> ReadyLook("Ready", nameList(ready) + (if (ready.size == 1) " is" else " are") + " ready: tap to start", true, true)
        ready.isNotEmpty() -> ReadyLook("Ready", nameList(ready) + (if (ready.size == 1) " is" else " are") + " ready", false, true)
        else -> ReadyLook("Ready", null, false, true)
    }
}

/**
 * The bar pinned under every lobby's scrolling part: the Ready button with everyone's face and
 * light, and a round React button that opens the reactions above it. [onReady] is null where the
 * game has no Ready button (the level taps are the ready); [onReact] is null while you are alone.
 */
@Composable
fun ReadyBar(room: RoomState, busy: Boolean, onReady: (() -> Unit)?, onReact: ((Reaction) -> Unit)?) {
    val p = AppTheme.palette
    var reacting by remember { mutableStateOf(false) }
    if (onReady == null && onReact == null) return
    Column(Modifier.fillMaxWidth().background(p.card).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.ink))
        if (reacting && onReact != null) ReactionBar(
            onReact = { onReact(it); reacting = false },
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onReady != null) ReadyButton(room, readyLook(room), busy = busy, onClick = onReady, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            if (onReact != null) {
                Box(
                    Modifier.size(50.dp).pressedEdge(p.ink, 25.dp, 2.dp).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape)
                        .semantics { contentDescription = "React"; stateDescription = if (reacting) "Open" else "Closed" }
                        .clickable(role = Role.Button) { reacting = !reacting },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Face, contentDescription = null, tint = p.text, modifier = Modifier.size(24.dp)) }
            }
        }
    }
}

/** Ready in coral; green with white words once the others are ready (4.5:1 or better). */
@Composable
private fun ReadyButton(room: RoomState, look: ReadyLook, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val on = look.enabled && !busy
    val fill = (if (look.green) p.good else p.primary).let { if (on) it else it.copy(alpha = 0.45f) }
    val edge = p.ink.copy(alpha = if (on) 1f else 0.3f)
    val ink = rematchInk(p, look.green).let { if (on) it else it.copy(alpha = 0.8f) }
    Surface(
        onClick = onClick,
        enabled = on,
        color = fill,
        shape = Shapes.L,
        border = BorderStroke(2.dp, edge),
        modifier = modifier.pressedEdge(edge, Radius.L).semantics { liveRegion = LiveRegionMode.Polite; if (!on) disabled() },
    ) {
        Row(Modifier.heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ReadyFaces(room)
            Spacer(Modifier.width(12.dp))
            if (busy) {
                PipPopLoader(Modifier.size(width = 40.dp, height = 24.dp), color = ink, color2 = ink.copy(alpha = 0.7f))
            } else Column(Modifier.weight(1f)) {
                Text(look.title, color = ink, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (look.sub != null) Text(look.sub, color = ink.copy(alpha = 0.9f), fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val LOBBY_ART = 280f / 170f

/** The lobby's banner: the game's whole key art at the left, its line and its three facts beside it. */
@Composable
internal fun LobbyBanner(game: GameKind) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Decorative: the top bar already names the game.
        KeyArt(game, Modifier.width(150.dp).aspectRatio(LOBBY_ART).clip(Shapes.M).border(2.dp, p.ink, Shapes.M).clearAndSetSemantics {})
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(game.tagline, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, lineHeight = LineHeight.Body)
            Facts(game.facts)
        }
    }
}

/** One player's row; an [arrived] player's avatar bounces in with a burst of rays in their colour. */
@Composable
internal fun PlayerRow(player: PlayerInfo, isMe: Boolean, color: Color, reaction: ShownReaction?, seat: Int, game: GameKind, arrived: Boolean = false, showLevel: Boolean = true) {
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
            player.ready && player.difficulty != null && showLevel -> Pill("Ready · ${player.difficulty.label}", p.good)
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
