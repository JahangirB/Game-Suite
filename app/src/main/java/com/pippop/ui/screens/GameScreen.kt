package com.pippop.ui.screens

import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
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
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.BoardState
import com.pippop.game.Sudoku
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.data.GameKind
import com.pippop.ui.art.color
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.OpponentColor
import kotlinx.coroutines.delay
import com.pippop.game.SudokuShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.pippop.ui.theme.Shapes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pippop.data.nearby.LocalRoom
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.SeatColors

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
    val together = room.together
    val coop = room.coop
    // Together: a wrong digit shows red in its square for a moment, then goes.
    var flash by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val miss = coop?.miss
    LaunchedEffect(miss?.seq) {
        if (miss == null) return@LaunchedEffect
        flash = miss.cell to miss.digit
        delay(MISS_FLASH_MS)
        flash = null
    }
    SoundOnChange(miss?.seq) { play(Sound.ERROR) }
    // During a match only the filled share is known locally; the server reveals the solved share once it is over.
    val players = battleChips(room, state, localNow, if (together) CoopInk[0] else p.primary, detail = { player, isMe ->
        when {
            playing && !isMe && (player.online == false || player.idleMs > OPPONENT_LAG_WARNING_MS) -> "connection lost?"
            together -> {
                val i = if (isMe) 0 else room.others.indexOf(player) + 1
                "${CoopInkNames[i.coerceIn(0, CoopInkNames.lastIndex)]} · ${player.placed} placed"
            }
            isMe -> if (finished) "$myPercent% solved" else "$myPercent% filled"
            else -> "${player.progress ?: 0}% solved"
        }
    }, otherColor = if (together) ({ CoopInk[(it + 1).coerceAtMost(CoopInk.lastIndex)] }) else ({ SeatColors[it % SeatColors.size] }))
    if (showRules) RulesDialog(GameKind.SUDOKU) { showRules = false }

    GameFrame(
        title = "Sudoku",
        subtitle = "Game ${room.round} · ${room.difficulty?.label.orEmpty()}" + (if (board.shape.big) " · ${board.shape.label}" else "") + (if (together) " · Together" else "") + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.SUDOKU.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        onReact = if (opponent != null) ({ reacting = !reacting }) else null,
        // A race: the tools work all the time. Together, digits once placed stay (no Undo) and
        // Erase clears your notes.
        tools = listOfNotNull(
            DockTool(if (state.notesMode) "Notes: on" else "Notes", GameIcons.Pencil, enabled = playing, onClick = vm::toggleNotes),
            if (together) null else DockTool("Undo", GameIcons.Undo, enabled = playing && board.canUndo, onClick = vm::undo),
            DockTool(if (together) "Erase notes" else "Erase", GameIcons.Eraser, enabled = playing, onClick = vm::erase),
            DockTool("Fill all", GameIcons.FillAll, enabled = playing) { vm.fillNotes(null) },
        ),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(style = Digits, text = formatDuration(elapsedMs(room, phase, now, startAt)), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                val fullLives = if (board.shape.big) LocalRoom.COOP_LIVES_BIG else LocalRoom.COOP_LIVES
                if (together) Lives(coop?.lives ?: fullLives, coop?.maxLives ?: fullLives, flash != null)
            }
        },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            SudokuPopup(room, state.busy, showSolution, onToggleSolution = { showSolution = !showSolution }, vm = vm, onLeave = onLeave)
        },
    ) {

        BoardArea(1f) { size ->
            Box(size, contentAlignment = Alignment.Center) {
                val inkOf = coop?.takeIf { it.by.length == board.cells.size }?.let { c -> { i: Int -> coopInk(c.by[i]) } }
                val rings = coop?.cursors.orEmpty().withIndex()
                    .filter { (i, cell) -> cell in board.cells.indices && room.others.getOrNull(i)?.let { !it.left && !it.out } == true }
                    .associate { (i, cell) -> cell to CoopInk[(i + 1).coerceAtMost(CoopInk.lastIndex)] }
                val shown = if (showSolution) room.solution?.let(board.shape::parse) else null
                if (board.shape.big) {
                    // Joined grids: the whole board, to pinch or double-tap into.
                    BigSudokuGrid(
                        board = board, errors = conflicts, selected = state.selected, hidden = phase == RoomStatus.COUNTDOWN,
                        solution = shown, onSelect = vm::select, onLongPress = vm::fillNotes, inkOf = inkOf, rings = rings, flash = flash,
                        celebrate = together,
                    )
                } else {
                    SudokuGrid(
                        board = board,
                        errors = conflicts,
                        selected = state.selected,
                        hidden = phase == RoomStatus.COUNTDOWN,
                        solution = shown,
                        onSelect = vm::select,
                        onLongPress = vm::fillNotes,
                        inkOf = inkOf,
                        rings = rings,
                        flash = flash,
                        // Together only keeps right digits, so a full unit is right; in a race it may not be.
                        celebrate = together,
                    )
                }
                if (phase == RoomStatus.COUNTDOWN) {
                    val seconds = ((startAt - now + 999) / 1000).coerceAtLeast(1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                        CountdownText(seconds, p.primaryText, fontWeight = FontWeight.Bold)
                        opponent?.let {
                            Text(if (together) "One grid, three lives, with ${room.others.joinToString(" & ") { o -> o.name }}" else "vs ${it.name}", color = OpponentColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        NumberPad(
            remaining = remainingDigits(board, conflicts),
            enabled = playing && state.selected != null,
            onDigit = vm::enterDigit,
        )
    }
}

/** How long a wrong digit stays red in its square, Together. */
private const val MISS_FLASH_MS = 900L

/** Together: your digits are blue, the first partner's pink, then green and orange. */
internal val CoopInk = listOf(Pieces.Blue, Pieces.Pink, Pieces.Green, Pieces.Orange)
private val CoopInkNames = listOf("blue", "pink", "green", "orange")

/** The colour of a placed digit from its `by` mark ('m' you, '1' to '3' the others). */
private fun coopInk(mark: Char): Color? = when (mark) {
    'm' -> CoopInk[0]
    in '1'..'3' -> CoopInk[mark - '0']
    else -> null
}

/** The lives everyone shares, as hearts; they shake when one is lost. */
@Composable
private fun Lives(lives: Int, max: Int, losing: Boolean) {
    val p = AppTheme.palette
    val shake = remember { Animatable(0f) }
    LaunchedEffect(losing) {
        if (!losing) return@LaunchedEffect
        for (x in listOf(6f, -6f, 4f, -4f, 0f)) shake.animateTo(x, tween(60))
    }
    Row(
        Modifier
            .graphicsLayer { translationX = shake.value * density }
            .semantics(mergeDescendants = true) { contentDescription = "$lives of $max lives left" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(max) { i -> Text(if (i < lives) "♥" else "♡", color = if (i < lives) p.wrong else p.muted, fontSize = TextSize.Body, fontWeight = FontWeight.Bold) }
    }
}

/** The boards to pick from, as small pictures: one grid, or several joined. */
@Composable
fun ShapePicker(selected: SudokuShape, enabled: Boolean, onPick: (SudokuShape) -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (shape in SudokuShape.offered) {
            val on = shape == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp).clip(Shapes.M)
                    .border(if (on) 2.5.dp else 1.5.dp, if (on) p.primary else p.hairline, Shapes.M)
                    .background(if (on) p.cellSameDigit else p.card)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onPick(shape) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ShapePreview(shape, Modifier.size(48.dp))
                Text(shape.label, color = p.text, fontSize = TextSize.Label, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Sudoku: a race, or one grid for everyone. Whoever opened the game picks. */
@Composable
fun SudokuModeCard(room: RoomState, busy: Boolean, onPick: (Boolean) -> Unit, onShape: (SudokuShape) -> Unit = {}) {
    val p = AppTheme.palette
    if (room.sudokuShapes) AppCard("Board") {
        if (room.setsOptions) ShapePicker(room.sudokuShape, enabled = !busy, onPick = onShape)
        else Text("${room.sudokuShape.label}, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
        Text(
            if (room.sudokuShape.big) "${room.sudokuShape.grids.size} grids joined: the gold boxes are shared, so a digit there counts in each grid. Pinch or double-tap to zoom."
            else "One 9 × 9 grid.",
            color = p.muted, fontSize = TextSize.Label,
        )
    }
    AppCard("Mode") {
        if (room.setsOptions) {
            OptionGroup(listOf(false to "Race", true to "Together"), room.sudokuTogether, enabled = !busy, onPick = onPick)
        } else {
            Text(if (room.sudokuTogether) "Together, picked by whoever opened the game." else "Race, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
        }
        Text(
            if (room.sudokuTogether) "One board for the team: anyone fills any square. Your digits are blue, your partner's pink, and a ring shows where each of you is. A wrong digit costs one of the team's lives (${if (room.sudokuShape.big) LocalRoom.COOP_LIVES_BIG else LocalRoom.COOP_LIVES}): fill the board and you all win."
            else "The same puzzle on every phone: the first to solve it wins.",
            color = p.muted, fontSize = TextSize.Label,
        )
    }
}

/** How many of each digit are still missing, ignoring cells that are currently wrong. */
fun remainingDigits(board: BoardState, errors: Set<Int>): (Int) -> Int = { digit ->
    board.shape.perDigit - board.cells.indices.count { board.cells[it] == digit && it !in errors }
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
    val opponentName = opponent?.name ?: "The other player"
    // Together: who put in how many.
    val counts = (listOf("You" to room.me.placed) + room.others.map { it.name to it.placed }).joinToString(", ") { (n, c) -> "$n $c" }
    val detail = when (room.endReason) {
        "solvedTogether" -> "You filled it together in ${formatDuration(room.me.finishMs ?: 0L)}. Placed: $counts."
        "outOfLives" -> "Out of lives, with ${room.coop?.board?.count { it == '0' } ?: 0} squares to go. Placed: $counts."
        "solved" -> if (won) {
            "You solved it in ${formatDuration(room.me.finishMs ?: 0L)}!"
        } else {
            "$opponentName solved it in ${formatDuration(opponent?.finishMs ?: 0L)}. " +
                "You had ${room.me.progress ?: 0}% correct."
        }
        "forfeit" -> if (won) "$opponentName left the game." else "You left the game."
        "timeout" -> if (won) "$opponentName lost connection." else "You were disconnected for too long."
        else -> ""
    }
    val title = when (room.endReason) {
        "solvedTogether" -> "You did it together!"
        "outOfLives" -> "So close"
        else -> resultTitle(room.winner, room.winnerName ?: opponentName)
    }
    MatchPopup(room, GameKind.SUDOKU, outcomeOf(room.winner), title, detail, onRematch = null, onFinish = onLeave) {
        if ((!won || room.endReason == "outOfLives") && room.solution != null) {
            TextButton(onClick = onToggleSolution) {
                Text(if (showSolution) "Hide solution" else "Show solution on the board", color = p.primaryText)
            }
        }
        // Partner picked another level: theirs is one tap away (Change has them all).
        val theirs = opponent?.takeIf { it.ready && !it.left }?.difficulty
        if (theirs != null && theirs != room.difficulty && !room.me.ready) {
            TextButton(onClick = { vm.chooseLevel(theirs) }) { Text("$opponentName wants ${theirs.label}: play that", color = p.primaryText, fontWeight = FontWeight.Bold) }
        }
    }
}
