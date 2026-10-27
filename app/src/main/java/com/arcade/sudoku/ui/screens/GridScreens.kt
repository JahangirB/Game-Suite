package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.Sound
import com.arcade.sudoku.ui.SoundOnChange
import com.arcade.sudoku.ui.rememberSfx
import com.arcade.sudoku.ui.theme.Digits
import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Radius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.GridResult
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.grid.LetterGrid
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloGridUiState
import com.arcade.sudoku.ui.SoloGridViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont

/**
 * The 4 × 4 letters. Drag through touching squares to spell a word (going back one square
 * takes the last letter off); letting go sends it to [onWord].
 */
@Composable
fun LetterGridBoard(letters: String, enabled: Boolean, onWord: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    var path by remember(letters) { mutableStateOf(listOf<Int>()) }
    val send by rememberUpdatedState(onWord)
    val sfx = rememberSfx()
    val n = LetterGrid.SIZE
    BoxWithConstraints(modifier) {
        val cellPx = with(LocalDensity.current) { maxWidth.toPx() } / n
        val touch = if (!enabled) Modifier else Modifier.pointerInput(letters) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // A square counts only when the finger is near its middle, so diagonals are easy.
                fun cellAt(x: Float, y: Float): Int? {
                    val c = (x / cellPx).toInt()
                    val r = (y / cellPx).toInt()
                    if (c !in 0 until n || r !in 0 until n) return null
                    val dx = x - (c + 0.5f) * cellPx
                    val dy = y - (r + 0.5f) * cellPx
                    return if (dx * dx + dy * dy <= (cellPx * 0.42f) * (cellPx * 0.42f)) r * n + c else null
                }
                var trail = listOfNotNull(cellAt(down.position.x, down.position.y))
                if (trail.isNotEmpty()) sfx.play(Sound.GRID_TRACE)
                path = trail
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) break
                    pointer.consume()
                    val cell = cellAt(pointer.position.x, pointer.position.y) ?: continue
                    trail = when {
                        trail.isEmpty() -> listOf(cell)
                        trail.size >= 2 && cell == trail[trail.size - 2] -> trail.dropLast(1)
                        cell !in trail && LetterGrid.touching(trail.last(), cell) -> trail + cell
                        else -> trail
                    }
                    // Each letter added ticks a little higher along the word.
                    if (trail.size > path.size) sfx.play(Sound.GRID_TRACE, pitch = 1f + 0.06f * (trail.size - 1))
                    path = trail
                }
                if (trail.size >= 3) send(trail.joinToString("") { letters[it].toString() })
                path = emptyList()
            }
        }
        val side = maxWidth / n - 6.dp
        Column(Modifier.then(touch), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until n) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until n) {
                    val i = r * n + c
                    val on = i in path
                    Surface(
                        color = if (on) p.primary else p.card,
                        shape = Shapes.M,
                        border = BorderStroke(2.dp, p.ink),
                        modifier = Modifier.size(side).pressedEdge(p.ink, Radius.M),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(letters[i].uppercase(), color = if (on) Color.White else p.text, fontSize = (side.value * 0.45f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont)
                        }
                    }
                }
            }
        }
    }
}

/** Your found words as a few short lines, newest first. */
@Composable
private fun FoundWords(words: List<String>) {
    val p = AppTheme.palette
    SoundOnChange(words.size.takeIf { it > 0 }) { play(Sound.GRID_WORD) }
    Text(
        if (words.isEmpty()) "Drag through touching letters to spell words of three letters or more."
        else words.reversed().joinToString("  ·  ") { it.uppercase() },
        color = if (words.isEmpty()) p.muted else p.text,
        fontSize = TextSize.Body, fontWeight = if (words.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
        maxLines = 3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

private fun timeLeft(endsAt: Long, now: Long): String {
    val s = ((endsAt - now + 999) / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Each player's words at the end: unique words (double points) in bold. */
@Composable
private fun ColumnScope.GridResults(results: List<GridResult>) {
    val p = AppTheme.palette
    for (r in results) {
        Text("${if (r.mine) "You" else r.name}: ${r.score} points", color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Body)
        Text(
            r.words.joinToString("  ") { (if (it.unique) "★" else "") + it.word.uppercase() }.ifEmpty { "No words" },
            color = p.muted, fontSize = TextSize.Label, maxLines = 3,
        )
    }
}

// ---- Against your partners -----------------------------------------------------------------

@Composable
fun GridGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val grid = room.grid ?: return
    rememberTicker()
    val now = state.serverNow()
    val phase = state.phase(now)
    val finished = room.status == RoomStatus.FINISHED
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GRID) { showRules = false }
    val letters = grid.letters
    val endsAt = grid.endsAt ?: 0L
    val playing = phase == RoomStatus.PLAYING && now < endsAt

    GameFrame(
        title = "Letter Grid",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, rememberTicker(), p.primary, detail = { player, isMe ->
            if (isMe) "${grid.mine.size} words" else "${grid.counts.getOrNull(room.others.indexOf(player)) ?: 0} words"
        }),
        glow = GameKind.GRID.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trailing = { Text(if (playing) timeLeft(endsAt, now) else "", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.GRID, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: "The other player"),
                when (room.endReason) {
                    "score" -> "★ words only one of you found score double."
                    "draw" -> "Top scores are level."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyGrid, onFinish = onLeave,
            ) { grid.results?.let { GridResults(it) } }
        },
    ) {
        if (letters == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                    if (phase == RoomStatus.COUNTDOWN) {
                        CountdownText((((room.startAt ?: 0L) - now + 999) / 1000).coerceAtLeast(1), p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            TurnLine(if (playing) "Find words: drag through touching letters" else "Time's up!", playing, GameKind.GRID.color)
            BoardArea(1f) { size -> LetterGridBoard(letters, playing, vm::findGridWord, size) }
            FoundWords(grid.mine)
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloGridScreen(state: SoloGridUiState, vm: SoloGridViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GRID) { showRules = false }
    val now = rememberTicker()
    val letters = state.letters
    GameFrame(
        title = "Letter Grid",
        subtitle = state.best?.let { "On your own · best $it" } ?: "On your own",
        players = emptyList(),
        glow = GameKind.GRID.color,
        dock = soloDock(onHome, { showRules = true }, if (letters != null) vm::start else null),
        trailing = {
            if (letters != null && !state.over && state.timed) Text(timeLeft(state.endsAt, now), style = Digits, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        },
        scrollable = letters == null,
        popup = if (!state.over) null else popupContent {
            ResultPopup(
                game = GameKind.GRID,
                outcome = if (state.score > 0 && state.score >= (state.best ?: 0)) Outcome.WIN else Outcome.TIE,
                title = "${state.score} points",
                detail = "${state.found.size} of ${state.total} words found." +
                    if (state.missed.isEmpty()) "" else " You missed: " + state.missed.joinToString(", ") { it.uppercase() },
                finish = PopupAction("Home", onHome),
                again = PopupAction("New grid", vm::start),
            )
        },
    ) {
        if (letters == null) {
            AppCard("One grid, as many words as you can") {
                Text("Spell words by dragging through touching letters, sideways or corner to corner. Longer words score more.", color = p.muted, fontSize = TextSize.Body)
                OptionGroup(listOf(false to "No time limit", true to "2 minutes"), state.timed, enabled = true, onPick = vm::setTimed)
                PrimaryButton("Start", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            TurnLine(state.message ?: "Score ${state.score}: drag through touching letters", state.message == null && !state.over, if (state.message != null) p.bad else GameKind.GRID.color)
            BoardArea(1f) { size -> LetterGridBoard(letters, !state.over, vm::find, size) }
            FoundWords(state.found)
            if (!state.over) SecondaryButton(if (state.timed) "Finish now" else "I'm done", vm::finish, Modifier.fillMaxWidth())
        }
    }
}
