package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.Digits
import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.GameColors
import com.arcade.sudoku.ui.theme.Pieces
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.random.Random
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.NumberBoard
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.numbers.NumberCross
import com.arcade.sudoku.game.numbers.NumberLevel
import com.arcade.sudoku.game.numbers.NumberPuzzle
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloNumbersUiState
import com.arcade.sudoku.ui.SoloNumbersViewModel
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont
import kotlinx.coroutines.delay

private val Right = Pieces.Green
private val Wrong = Pieces.Red

/** The puzzle a player sees, from the generator's solved puzzle (on your own). */
private fun NumberPuzzle.toBoard(): NumberBoard {
    val blank = blanks.toSet()
    return NumberBoard(w, h, grid.mapIndexed { i, t -> if (i in blank) "?" else t }, blanks, bank)
}

/** The board's sums, as cell indexes (blanks stand in as numbers to find the shape). */
private fun sumsOf(board: NumberBoard) = runCatching {
    NumberCross.sums(board.cells.map { if (it == "?") "0" else it }, board.w, board.h)
}.getOrDefault(emptyList())

/** The board with [entries] in the blanks. */
private fun filledCells(board: NumberBoard, entries: List<String>) =
    board.cells.toMutableList().also { c -> board.blanks.forEachIndexed { k, i -> c[i] = entries.getOrElse(k) { "" } } }

/** Your share of the sums with a blank that hold, as the server counts it. */
private fun progressOf(board: NumberBoard, entries: List<String>): Int {
    val cells = filledCells(board, entries)
    val blank = board.blanks.toSet()
    val open = sumsOf(board).filter { s -> s.any { it in blank } }
    if (open.isEmpty()) return 100
    return open.count { s -> NumberCross.holds(s.map { cells[it] }) } * 100 / open.size
}

private fun shown(token: String) = when (token) {
    "*" -> "×"
    "/" -> "÷"
    "-" -> "−"
    else -> token
}

/**
 * The grid, cropped to the cells in use and drawn whole like a crossword: squares no sum uses
 * are stone blocks. Blanks show your entries; a sum turns green once it is
 * filled in and right, red when filled in and wrong. Tap a blank to pick it.
 */
@Composable
private fun NumberGrid(board: NumberBoard, entries: List<String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit, modifier: Modifier) {
    val p = AppTheme.palette
    val used = board.cells.indices.filter { board.cells[it] != "" }
    if (used.isEmpty()) return
    val rows = used.map { it / board.w }.let { it.min()..it.max() }
    val cols = used.map { it % board.w }.let { it.min()..it.max() }
    val cells = filledCells(board, entries)
    val blankIndex = board.blanks.withIndex().associate { (k, i) -> i to k }
    // Each cell's state from the sums through it: true all right, false any wrong, null not finished.
    val verdict = mutableMapOf<Int, Boolean>()
    for (s in sumsOf(board)) {
        if (s.none { it in blankIndex } || s.any { cells[it] == "" }) continue
        val ok = NumberCross.holds(s.map { cells[it] })
        for (i in s) verdict[i] = (verdict[i] ?: true) && ok
    }
    BoxWithConstraints(modifier) {
        val cell = minOf(maxWidth / cols.count(), maxHeight / rows.count())
        val fontSize = with(LocalDensity.current) { (cell * 0.42f).toSp() }
        Column(
            Modifier.align(Alignment.Center).clip(Shapes.S).background(GridFrame)
                .border(2.dp, p.ink, Shapes.S).padding(3.dp),
        ) {
            for (r in rows) Row {
                for (c in cols) {
                    val i = r * board.w + c
                    val token = cells[i]
                    val k = blankIndex[i]
                    Box(Modifier.size(cell - 6.dp / maxOf(cols.count(), rows.count())).padding(1.5.dp), contentAlignment = Alignment.Center) {
                        when {
                            board.cells[i] == "" -> StoneBlock(i, Modifier.fillMaxSize())
                            k != null -> {
                                val pick = k == selected
                                val tint = when (verdict[i]) {
                                    true -> Right
                                    false -> Wrong
                                    null -> if (pick) p.primary else p.accent
                                }
                                Surface(
                                    color = tint,
                                    shape = Shapes.S,
                                    border = BorderStroke(if (pick) 3.dp else 1.5.dp, if (pick) p.ink else p.ink.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth().height(cell).clickable(enabled = enabled) { onSelect(k) },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(token, color = if (verdict[i] != null || pick) Color.White else p.text, fontSize = fontSize, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            NumberCross.isNumber(token) -> Surface(
                                color = p.card,
                                shape = Shapes.S,
                                border = BorderStroke(1.5.dp, (verdict[i]?.let { if (it) Right else Wrong }) ?: p.ink),
                                modifier = Modifier.fillMaxWidth().height(cell),
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(token, color = p.text, fontSize = fontSize, fontWeight = FontWeight.Bold) }
                            }
                            else -> Box(Modifier.fillMaxSize().clip(Shapes.S).background(OperatorTile), contentAlignment = Alignment.Center) {
                                Text(shown(token), color = p.muted, fontSize = fontSize, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val GridFrame = GameColors.GridFrame
private val OperatorTile = GameColors.OperatorTile
private val Stone = GameColors.Stone
private val StoneLight = GameColors.StoneLight
private val StoneDark = GameColors.StoneDark

/** An unused square: a stone block with a bevel and a crack or two, different square to square. */
@Composable
private fun StoneBlock(seed: Int, modifier: Modifier) {
    Canvas(modifier.clip(Shapes.S)) {
        val w = size.width
        drawRect(Stone)
        // Light top-left edge, dark bottom-right edge.
        drawPath(Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w * 0.86f, w * 0.14f); lineTo(w * 0.14f, w * 0.14f); lineTo(w * 0.14f, w * 0.86f); lineTo(0f, w); close() }, StoneLight)
        drawPath(Path().apply { moveTo(w, w); lineTo(0f, w); lineTo(w * 0.14f, w * 0.86f); lineTo(w * 0.86f, w * 0.86f); lineTo(w * 0.86f, w * 0.14f); lineTo(w, 0f); close() }, StoneDark)
        val r = Random(seed)
        repeat(1 + r.nextInt(2)) {
            val x0 = w * (0.25f + 0.5f * r.nextFloat())
            val y0 = w * (0.2f + 0.2f * r.nextFloat())
            drawPath(
                Path().apply {
                    moveTo(x0, y0)
                    lineTo(x0 + w * (r.nextFloat() - 0.5f) * 0.3f, y0 + w * 0.22f)
                    lineTo(x0 + w * (r.nextFloat() - 0.5f) * 0.4f, y0 + w * 0.42f)
                },
                StoneDark, style = Stroke(w * 0.035f),
            )
        }
        // A few specks.
        repeat(4) { drawCircle(StoneDark.copy(alpha = 0.5f), w * 0.03f, Offset(w * (0.2f + 0.6f * r.nextFloat()), w * (0.2f + 0.6f * r.nextFloat()))) }
    }
}

/** The bank: each missing number with how many are left to place; Clear empties the picked blank. */
@Composable
private fun NumberBank(bank: List<Int>, entries: List<String>, enabled: Boolean, onPlace: (String) -> Unit) {
    val p = AppTheme.palette
    val placed = entries.filter { it != "" }.groupingBy { it }.eachCount()
    val counts = bank.groupingBy { it }.eachCount()
    val chips = counts.keys.sorted().map { it to (counts.getValue(it) - (placed[it.toString()] ?: 0)) } + (-1 to 0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (value, left) ->
                    val clear = value < 0
                    val on = enabled && (clear || left > 0)
                    Surface(
                        color = if (clear) p.card else if (left > 0) p.card else p.accent,
                        shape = Shapes.M,
                        border = BorderStroke(2.dp, if (on) p.ink else p.muted),
                        modifier = Modifier.weight(1f).height(46.dp).clickable(enabled = on) { onPlace(if (clear) "" else value.toString()) },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (clear) "Clear" else value.toString(), color = if (on) p.text else p.muted, fontWeight = FontWeight.Bold, fontSize = if (clear) TextSize.Body else TextSize.BodyL)
                            if (!clear && counts.getValue(value) > 1) Text("×$left", color = p.muted, fontSize = TextSize.Micro)
                        }
                    }
                }
                repeat(7 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** The board and the bank, shared by the race and the game on your own. */
@Composable
private fun ColumnScope.NumberCrossPlay(board: NumberBoard, entries: List<String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit, onPlace: (String) -> Unit) {
    val used = board.cells.indices.filter { board.cells[it] != "" }
    val rows = used.map { it / board.w }.let { if (it.isEmpty()) 1 else it.max() - it.min() + 1 }
    val cols = used.map { it % board.w }.let { if (it.isEmpty()) 1 else it.max() - it.min() + 1 }
    BoardArea(cols.toFloat() / rows) { size -> NumberGrid(board, entries, selected, enabled, onSelect, size) }
    NumberBank(board.bank, entries, enabled && selected != null, onPlace)
}

/** The host's level for the next puzzle; everyone else sees it. */
@Composable
fun NumbersLevelCard(room: RoomState, busy: Boolean, onPick: (NumberLevel) -> Unit) {
    val p = AppTheme.palette
    val level = room.numbers?.level ?: NumberLevel.EASY
    AppCard("Level") {
        if (room.setsOptions) {
            OptionGroup(NumberLevel.entries.map { it to it.label }, level, enabled = !busy, onPick = onPick)
            Text("A new puzzle each round, the same for everyone.", color = p.muted, fontSize = TextSize.Body)
        } else {
            Text("${level.label}, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
        }
    }
}

// ---- The race ---------------------------------------------------------------------------------

/** Number Cross against your partners: the same puzzle for everyone; the first to solve it wins. */
@Composable
fun NumberCrossScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val numbers = room.numbers ?: return
    val offset by rememberUpdatedState(state.clockOffsetMs)
    val now by produceState(System.currentTimeMillis() + offset) {
        while (true) {
            value = System.currentTimeMillis() + offset
            delay(200)
        }
    }
    val localNow = rememberTicker()
    val phase = state.phase(now)
    val playing = phase == RoomStatus.PLAYING
    val finished = phase == RoomStatus.FINISHED
    val board = numbers.board
    val entries = state.numberEntries.orEmpty()
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.NUMBERS) { showRules = false }
    val mine = if (board != null) progressOf(board, entries) else 0
    val startAt = room.startAt ?: now
    val elapsed = when (phase) {
        RoomStatus.PLAYING -> now - startAt
        RoomStatus.FINISHED -> room.me.finishMs ?: room.others.firstNotNullOfOrNull { it.finishMs } ?: 0L
        else -> 0L
    }

    GameFrame(
        title = "Number Cross",
        subtitle = "Round ${room.round} · ${numbers.level.label}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, localNow, p.primary, detail = { player, isMe ->
            if (isMe) "$mine% right" else "${player.progress ?: 0}% right"
        }),
        glow = GameKind.NUMBERS.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trailing = { Text(style = Digits, text = formatDuration(elapsed), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = room.winnerName ?: room.opponent?.name ?: "Your opponent"
            MatchPopup(
                room, GameKind.NUMBERS, outcomeOf(room.winner), resultTitle(room.winner, name),
                when (room.endReason) {
                    "solved" -> if (room.winner == "me") "You solved it first!" else "$name solved it first."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "Your opponent lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyNumbers, onFinish = onLeave,
                scores = listOf("You $mine%") + room.others.map { "${it.name} ${it.progress ?: 0}%" },
            )
        },
    ) {
        if (board == null) {
            // The puzzle arrives when the countdown ends.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                    if (phase == RoomStatus.COUNTDOWN) {
                        Text(style = Digits, text = ((startAt - now + 999) / 1000).coerceAtLeast(1).toString(), color = p.primaryText, fontSize = TextSize.Countdown, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            NumberCrossPlay(board, entries, state.numberSelected, playing, vm::selectBlank, vm::placeNumber)
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

/** Number Cross on your own, against the clock. */
@Composable
fun SoloNumbersScreen(state: SoloNumbersUiState, vm: SoloNumbersViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.NUMBERS) { showRules = false }
    val puzzle = state.puzzle
    val board = remember(puzzle) { puzzle?.toBoard() }
    val now = rememberTicker()
    val done = state.finishedMs
    GameFrame(
        title = "Number Cross",
        subtitle = if (puzzle == null) "On your own" else "On your own · ${state.level.label}",
        players = emptyList(),
        glow = GameKind.NUMBERS.color,
        dock = soloDock(onHome, { showRules = true }, if (puzzle != null) vm::again else null),
        trailing = {
            if (puzzle != null) Text(style = Digits, text = formatDuration(done ?: (now - state.startedAt)), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        },
        scrollable = puzzle == null,
        popup = if (puzzle == null || done == null) null else popupContent {
            SoloPopup(
                GameKind.NUMBERS, "me",
                "Solved in ${formatDuration(done)}" + (state.best[state.level]?.let { " · best ${formatDuration(it)}" } ?: ""),
                onAgain = vm::again, onHome = onHome, finishLabel = "Change level", onFinish = vm::changeLevel,
            )
        },
    ) {
        if (board == null) {
            AppCard("Choose the level") {
                Text("Fill the blanks from the bank so every sum works, across and down. Every puzzle is new.", color = p.muted, fontSize = TextSize.Body)
                NumberLevel.entries.forEach { level ->
                    PrimaryButton(level.label + (state.best[level]?.let { " · best ${formatDuration(it)}" } ?: ""), { vm.start(level) })
                }
            }
        } else {
            NumberCrossPlay(board, state.entries, state.selected, done == null, vm::select, vm::place)
        }
    }
}
