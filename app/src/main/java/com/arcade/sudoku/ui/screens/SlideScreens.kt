package com.arcade.sudoku.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.slide.Slide
import com.arcade.sudoku.game.slide.SlideLevel
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloSlideUiState
import com.arcade.sudoku.ui.SoloSlideViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont

/** The puzzle: tiles glide to their new place; tap a tile in line with the gap to slide. */
@Composable
fun SlideBoard(tiles: List<Int>, size: Int, enabled: Boolean, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    BoxWithConstraints(modifier.clip(RoundedCornerShape(18.dp)).background(p.accent)) {
        val cell = maxWidth / size
        val goal = Slide.solved(size)
        for (tile in 1 until size * size) key(tile) {
            val at = tiles.indexOf(tile)
            val x by animateDpAsState(cell * (at % size), tween(120), label = "x")
            val y by animateDpAsState(cell * (at / size), tween(120), label = "y")
            val home = goal[at] == tile
            Surface(
                color = if (home) GameKind.SLIDE.color else p.card,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, p.ink),
                modifier = Modifier.offset(x, y).size(cell).padding(3.dp).clickable(enabled = enabled) { onTap(tile) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(tile.toString(), color = if (home) Color.White else p.text, fontSize = (cell.value * 0.38f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont)
                }
            }
        }
    }
}

/** The host's level for the next puzzle; everyone else sees it. */
@Composable
fun SlideLevelCard(room: RoomState, busy: Boolean, onPick: (SlideLevel) -> Unit) {
    val p = AppTheme.palette
    val level = room.slide?.level ?: SlideLevel.EASY
    AppCard("Level") {
        if (room.isHost) {
            SlideLevel.entries.forEach { l ->
                if (l == level) PrimaryButton(l.label, {}, Modifier.fillMaxWidth(), enabled = !busy)
                else SecondaryButton(l.label, { onPick(l) }, Modifier.fillMaxWidth(), enabled = !busy)
            }
        } else {
            Text("${level.label}: the host's phone shuffles the puzzle.", color = p.muted, fontSize = 14.sp)
        }
    }
}

// ---- The race ---------------------------------------------------------------------------------

@Composable
fun SlideGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val slide = room.slide ?: return
    val now = rememberTicker()
    val phase = state.phase(state.serverNow())
    val finished = room.status == RoomStatus.FINISHED
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.SLIDE) { showRules = false }
    val start = slide.start
    val tiles = start?.let { Slide.replay(it, slide.size, state.slideMoves) }
    val mine = tiles?.let { Slide.progress(it, slide.size) } ?: 0
    val startAt = room.startAt ?: 0L
    val elapsed = when (phase) {
        RoomStatus.PLAYING -> state.serverNow() - startAt
        RoomStatus.FINISHED -> room.me.finishMs ?: room.others.firstNotNullOfOrNull { it.finishMs } ?: 0L
        else -> 0L
    }
    GameFrame(
        title = "Slide Race",
        subtitle = "Round ${room.round} · ${slide.level.label}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, p.primary, detail = { player, isMe ->
            if (isMe) "$mine% in place" else "${player.progress ?: 0}% in place"
        }),
        glow = GameKind.SLIDE.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trailing = { Text(formatDuration(elapsed), color = p.text, fontSize = 22.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = room.winnerName ?: room.opponent?.name ?: "Your opponent"
            MatchPopup(
                room, GameKind.SLIDE, outcomeOf(room.winner), resultTitle(room.winner, name),
                when (room.endReason) {
                    "solved" -> if (room.winner == "me") "Solved first, in ${state.slideMoves.size} slides!" else "$name solved it first."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "Your opponent lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readySlide, onFinish = onLeave,
            )
        },
    ) {
        if (tiles == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = 18.sp)
                    if (phase == RoomStatus.COUNTDOWN) {
                        Text(((startAt - state.serverNow() + 999) / 1000).coerceAtLeast(1).toString(), color = p.primary, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            TurnLine("Slides: ${state.slideMoves.size} · tap a tile in line with the gap", phase == RoomStatus.PLAYING, GameKind.SLIDE.color)
            BoardArea(1f) { size -> SlideBoard(tiles, slide.size, phase == RoomStatus.PLAYING, vm::tapSlideTile, size) }
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloSlideScreen(state: SoloSlideUiState, vm: SoloSlideViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.SLIDE) { showRules = false }
    val level = state.level
    val now = rememberTicker()
    val done = state.finishedMs
    GameFrame(
        title = "Slide Race",
        subtitle = level?.let { "On your own · ${it.label}" } ?: "On your own",
        players = emptyList(),
        glow = GameKind.SLIDE.color,
        dock = soloDock(onHome, { showRules = true }, if (level != null) vm::again else null),
        trailing = { if (level != null) Text(formatDuration(done ?: (now - state.startedAt)), color = p.text, fontSize = 22.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        scrollable = level == null,
        popup = if (level == null || done == null) null else popupContent {
            SoloPopup(
                GameKind.SLIDE, "me",
                "Solved in ${formatDuration(done)} with ${state.moves} slides" + (state.best[level]?.let { " · best ${formatDuration(it)}" } ?: ""),
                onAgain = vm::again, onHome = onHome, finishLabel = "Change level", onFinish = vm::changeLevel,
            )
        },
    ) {
        if (level == null) {
            AppCard("Choose the size") {
                Text("Slide the tiles back into order, 1 to the last, with the gap at the end. Tap a tile in line with the gap.", color = p.muted, fontSize = 14.sp)
                SlideLevel.entries.forEach { l ->
                    PrimaryButton(l.label + (state.best[l]?.let { " · best ${formatDuration(it)}" } ?: ""), { vm.start(l) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            TurnLine("Slides: ${state.moves}", done == null, GameKind.SLIDE.color)
            BoardArea(1f) { size -> SlideBoard(state.tiles, level.size, done == null, vm::tap, size) }
        }
    }
}
