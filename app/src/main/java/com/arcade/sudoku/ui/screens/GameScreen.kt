package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.Digits
import com.arcade.sudoku.ui.theme.TextSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.BoardState
import com.arcade.sudoku.game.Sudoku
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.OpponentColor
import kotlinx.coroutines.delay

// The server records "last seen" every 10 s and the app polls every 2-4 s, so allow for both.
private const val OPPONENT_LAG_WARNING_MS = 25_000

@Composable
fun GameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val board = state.board ?: return
    val offset by rememberUpdatedState(state.clockOffsetMs)
    val now by produceState(System.currentTimeMillis() + offset) {
        while (true) {
            value = System.currentTimeMillis() + offset
            delay(100)
        }
    }
    val phase = state.phase(now)
    val startAt = room.startAt ?: now
    var showSolution by remember(room.round) { mutableStateOf(false) }
    val opponent = room.opponent
    val playing = phase == RoomStatus.PLAYING
    val conflicts = board.conflicts()
    val localNow = rememberTicker()

    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    val finished = phase == RoomStatus.FINISHED
    val myPercent = room.me.progress?.takeIf { finished } ?: Sudoku.filledPercent(board.cells, board.givens)
    // During a match only the filled share is known locally; the server reveals the solved share once it is over.
    val players = battleChips(room, state, localNow, p.primary, detail = { player, isMe ->
        when {
            isMe -> if (finished) "$myPercent% solved" else "$myPercent% filled"
            playing && (player.online == false || player.idleMs > OPPONENT_LAG_WARNING_MS) -> "connection lost?"
            else -> "${player.progress ?: 0}% solved"
        }
    })
    if (showRules) RulesDialog(GameKind.SUDOKU) { showRules = false }

    GameFrame(
        title = "Sudoku",
        subtitle = "Round ${room.round} · ${room.difficulty?.label.orEmpty()}" + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.SUDOKU.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trailing = {
            Text(style = Digits, text = formatDuration(elapsedMs(room, phase, now, startAt)), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            SudokuPopup(room, state.busy, showSolution, onToggleSolution = { showSolution = !showSolution }, vm = vm, onLeave = onLeave)
        },
    ) {

        BoardArea(1f) { size ->
            Box(size, contentAlignment = Alignment.Center) {
                SudokuGrid(
                    board = board,
                    errors = conflicts,
                    selected = state.selected,
                    hidden = phase == RoomStatus.COUNTDOWN,
                    solution = if (showSolution) room.solution?.let(Sudoku::parse) else null,
                    onSelect = vm::select,
                )
                if (phase == RoomStatus.COUNTDOWN) {
                    val seconds = ((startAt - now + 999) / 1000).coerceAtLeast(1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                        Text(style = Digits, text = seconds.toString(), color = p.primaryText, fontSize = TextSize.Countdown, fontWeight = FontWeight.Bold)
                        opponent?.let { Text("vs ${it.name}", color = OpponentColor, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }

        ToolRow(
            notesMode = state.notesMode,
            canUndo = board.canUndo,
            enabled = playing,
            hintsLeft = null,
            onUndo = vm::undo,
            onErase = vm::erase,
            onNotes = vm::toggleNotes,
            onHint = {},
        )
        NumberPad(
            remaining = remainingDigits(board, conflicts),
            enabled = playing && state.selected != null,
            onDigit = vm::enterDigit,
        )
    }
}

/** How many of each digit are still missing, ignoring cells that are currently wrong. */
fun remainingDigits(board: BoardState, errors: Set<Int>): (Int) -> Int = { digit ->
    9 - board.cells.indices.count { board.cells[it] == digit && it !in errors }
}

private fun elapsedMs(room: RoomState, phase: RoomStatus?, now: Long, startAt: Long): Long = when (phase) {
    RoomStatus.PLAYING -> now - startAt
    RoomStatus.FINISHED -> room.me.finishMs ?: room.opponent?.finishMs ?: 0L
    else -> 0L
}

/** The end of a Sudoku battle: a rematch starts once both choose the same level. */
@Composable
private fun BoxScope.SudokuPopup(
    room: RoomState,
    busy: Boolean,
    showSolution: Boolean,
    onToggleSolution: () -> Unit,
    vm: BattleViewModel,
    onLeave: () -> Unit,
) {
    val p = AppTheme.palette
    val won = room.winner == "me"
    val opponent = room.opponent
    val opponentName = opponent?.name ?: "Your opponent"
    val detail = when (room.endReason) {
        "solved" -> if (won) {
            "You solved it in ${formatDuration(room.me.finishMs ?: 0L)}!"
        } else {
            "$opponentName solved it in ${formatDuration(opponent?.finishMs ?: 0L)}. " +
                "You had ${room.me.progress ?: 0}% correct."
        }
        "forfeit" -> if (won) "$opponentName left the battle." else "You left the battle."
        "timeout" -> if (won) "$opponentName lost connection." else "You were disconnected for too long."
        else -> ""
    }
    MatchPopup(room, GameKind.SUDOKU, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: opponentName), detail, onRematch = null, onFinish = onLeave) {
        if (!won && room.solution != null) {
            TextButton(onClick = onToggleSolution) {
                Text(if (showSolution) "Hide solution" else "Show solution on the board", color = p.primaryText)
            }
        }
        if (opponent != null && !opponent.left) {
            Text("Rematch: both choose the same level", color = p.muted, fontSize = TextSize.Body)
            LevelPicker(room, busy, onPick = vm::chooseLevel)
        }
    }
}
