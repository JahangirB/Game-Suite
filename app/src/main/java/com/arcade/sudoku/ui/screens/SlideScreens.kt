package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.Sound
import com.arcade.sudoku.ui.SoundOnChange
import com.arcade.sudoku.ui.theme.Digits
import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Shapes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import kotlin.math.abs
import kotlin.math.sign

/** The puzzle: tiles glide to their new place; tap a tile in line with the gap to slide it. Swipes come from [slideSwipes]. */
@Composable
fun SlideBoard(tiles: List<Int>, size: Int, enabled: Boolean, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    // A tile taps along one square; the last one in place sets off the cascade and chime.
    SoundOnChange(tiles) { play(if (tiles == Slide.solved(size)) Sound.SLIDE_SOLVED else Sound.PIECE_HOP) }
    val p = AppTheme.palette
    BoxWithConstraints(
        modifier.clip(Shapes.L).background(p.accent),
    ) {
        val cell = maxWidth / size
        val goal = Slide.solved(size)
        for (tile in 1 until size * size) key(tile) {
            val at = tiles.indexOf(tile)
            val x by animateDpAsState(cell * (at % size), tween(120), label = "x")
            val y by animateDpAsState(cell * (at / size), tween(120), label = "y")
            val home = goal[at] == tile
            Surface(
                color = if (home) GameKind.SLIDE.color else p.card,
                shape = Shapes.M,
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

/**
 * Swipes for Slide Race, over the whole game area: a swipe slides the tile next to the gap in
 * that direction ([onSwipe] gets -1, 0 or 1 across and down). Once the finger has moved past
 * the touch slop the touch is a swipe, wherever it started or ended: it is taken from the tiles
 * so it can never also count as a tap, and it slides one tile at most.
 */
internal fun Modifier.slideSwipes(enabled: Boolean, onSwipe: (Int, Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = Offset.Zero
            var swiping = false
            var done = false
            while (true) {
                // The Initial pass reaches this before the tiles do, so a swipe is taken first.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                moved += change.positionChange()
                if (!swiping && moved.getDistance() > viewConfiguration.touchSlop) swiping = true
                if (swiping) {
                    change.consume()
                    if (!done && moved.getDistance() > SWIPE_DP.dp.toPx()) {
                        done = true
                        if (abs(moved.x) > abs(moved.y)) onSwipe(moved.x.sign.toInt(), 0) else onSwipe(0, moved.y.sign.toInt())
                    }
                }
                if (!change.pressed) break
            }
        }
    }

/** How far a finger moves before a swipe slides a tile. */
private const val SWIPE_DP = 24

/** The host's level for the next puzzle; everyone else sees it. */
@Composable
fun SlideLevelCard(room: RoomState, busy: Boolean, onPick: (SlideLevel) -> Unit) {
    val p = AppTheme.palette
    val level = room.slide?.level ?: SlideLevel.EASY
    AppCard("Level") {
        if (room.setsOptions) {
            OptionGroup(SlideLevel.entries.map { it to it.label }, level, enabled = !busy, onPick = onPick)
        } else {
            Text("${level.label}, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
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
        subtitle = "Game ${room.round} · ${slide.level.label}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, p.primary, detail = { player, isMe ->
            if (isMe) "$mine% in place" else "${player.progress ?: 0}% in place"
        }),
        glow = GameKind.SLIDE.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trailing = { Text(style = Digits, text = formatDuration(elapsed), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = room.winnerName ?: room.opponent?.name ?: "The other player"
            MatchPopup(
                room, GameKind.SLIDE, outcomeOf(room.winner), resultTitle(room.winner, name),
                when (room.endReason) {
                    "solved" -> if (room.winner == "me") "Solved first, in ${state.slideMoves.size} slides!" else "$name solved it first."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readySlide, onFinish = onLeave,
            )
        },
    ) {
        if (tiles == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                    if (phase == RoomStatus.COUNTDOWN) {
                        CountdownText(((startAt - state.serverNow() + 999) / 1000).coerceAtLeast(1), p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val playing = phase == RoomStatus.PLAYING
            Column(Modifier.weight(1f).fillMaxWidth().slideSwipes(playing, vm::swipeSlide), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TurnLine("Slides: ${state.slideMoves.size} · tap a tile or swipe anywhere", playing, GameKind.SLIDE.color)
                BoardArea(1f) { size -> SlideBoard(tiles, slide.size, playing, vm::tapSlideTile, size) }
            }
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
        trailing = { if (level != null) Text(style = Digits, text = formatDuration(done ?: (now - state.startedAt)), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
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
                Text("Slide the tiles back into order, 1 to the last, with the gap at the end. Tap a tile in line with the gap, or swipe anywhere.", color = p.muted, fontSize = TextSize.Body)
                SlideLevel.entries.forEach { l ->
                    PrimaryButton(l.label + (state.best[l]?.let { " · best ${formatDuration(it)}" } ?: ""), { vm.start(l) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().slideSwipes(done == null, vm::swipe), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TurnLine("Slides: ${state.moves}", done == null, GameKind.SLIDE.color)
                BoardArea(1f) { size -> SlideBoard(state.tiles, level.size, done == null, vm::tap, size) }
            }
        }
    }
}
