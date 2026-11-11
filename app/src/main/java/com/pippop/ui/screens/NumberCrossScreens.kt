package com.pippop.ui.screens

import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.GameColors
import com.pippop.ui.theme.Pieces
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.Radius
import kotlin.math.PI
import kotlin.math.sin
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
import com.pippop.data.GameKind
import com.pippop.data.NumberBoard
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.numbers.NumberCross
import com.pippop.game.numbers.NumberLevel
import com.pippop.game.numbers.NumberPuzzle
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloNumbersUiState
import com.pippop.ui.SoloNumbersViewModel
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
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

/** Where the bank's tiles and the grid's blanks sit on screen, so a number can fly between them. */
internal class FlightSpots {
    val blanks = HashMap<Int, Rect>()
    val chips = HashMap<Int, Rect>()
    var origin = Offset.Zero
}

/** Set by your Undo tap so the next change to the entries flies back; it lapses if nothing changes. */
internal class FlightHint {
    var at by mutableLongStateOf(0L)

    /** Notes that the change about to come is your Undo. */
    fun mark() {
        at = System.currentTimeMillis()
    }
}

/** One number in the air: [value] to or from blank [k] ([back] means into the bank); [n] tells flights apart. */
private data class Flight(val value: Int, val k: Int, val back: Boolean, val n: Int)

/** A tile standing up from the table: a darker edge under a lighter face, outlined in [edge]. */
private fun Modifier.raised(color: Color, edge: Color, edgeWidth: Dp, lift: Dp, corner: Dp = Radius.S) = drawBehind {
    val liftPx = lift.toPx()
    val face = Size(size.width, size.height - liftPx)
    raisedTile(color, Offset.Zero, face, corner.toPx(), liftPx)
    val half = edgeWidth.toPx() / 2
    drawRoundRect(edge, Offset(half, half), Size(face.width - half * 2, face.height - half * 2), CornerRadius(corner.toPx()), style = Stroke(edgeWidth.toPx()))
}

/** A blank pressed into the board: [color] with a shadow cast down from its top and left edges and a lit lower lip. */
private fun Modifier.sunken(color: Color, deeper: Float) = drawBehind {
    drawRect(color)
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.34f + deeper), 1f to Color.Transparent, endY = size.height * 0.34f))
    drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.2f + deeper), 1f to Color.Transparent, endX = size.width * 0.2f))
    drawRect(Color.White.copy(alpha = 0.35f), Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
}

/**
 * The grid, cropped to the cells in use and drawn whole like a crossword: squares no sum uses
 * are chunky stone blocks, given numbers are raised tiles and blanks are sunken slots showing
 * your entries ([landing] is a blank whose number is still flying in, so its entry waits;
 * [spots] learns where each blank sits); a sum turns green once it is
 * filled in and right, red when filled in and wrong. Tap a blank to pick it.
 */
@Composable
internal fun NumberGrid(board: NumberBoard, entries: List<String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit, modifier: Modifier, spots: FlightSpots? = null, landing: Int? = null) {
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
    // A number set down clacks; a sum coming out right rings a soft bell.
    SoundOnChange(entries.count { it.isNotEmpty() }.takeIf { enabled }) { play(Sound.PIECE_PLACE) }
    SoundOnChange(verdict.count { it.value }.takeIf { it > 0 }) { play(Sound.NUMBERS_SUM_RIGHT) }
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
                                Box(
                                    Modifier.fillMaxWidth().height(cell).clip(Shapes.S).sunken(tint, if (p.dark) 0.2f else 0f)
                                        .border(if (pick) 3.dp else 1.5.dp, if (pick) p.ink else p.ink.copy(alpha = 0.6f), Shapes.S)
                                        .onGloballyPositioned { spots?.blanks?.put(k, it.boundsInRoot()) }
                                        .clickable(enabled = enabled) { onSelect(k) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val ink = if (k == landing) Color.Transparent else if (verdict[i] != null || pick) Color.White else p.text
                                    Text(token, color = ink, fontSize = fontSize, fontWeight = FontWeight.Bold)
                                }
                            }
                            NumberCross.isNumber(token) -> Box(
                                Modifier.fillMaxWidth().height(cell).raised(p.card, (verdict[i]?.let { if (it) Right else Wrong }) ?: p.ink, 1.5.dp, cell / 12),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(token, color = p.text, fontSize = fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = cell / 12))
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

/** An unused square: a chunky stone block standing up from the board, with a wide bevel and a crack or two, different square to square. */
@Composable
private fun StoneBlock(seed: Int, modifier: Modifier) {
    Canvas(modifier.clip(Shapes.S)) {
        val w = size.width
        val lift = w * 0.12f
        val h = w - lift
        raisedTile(Stone, Offset.Zero, Size(w, h), Radius.S.toPx(), lift)
        // Light top-left edge, dark bottom-right edge.
        val b = 0.2f
        drawPath(Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w * (1 - b), h * b); lineTo(w * b, h * b); lineTo(w * b, h * (1 - b)); lineTo(0f, h); close() }, StoneLight)
        drawPath(Path().apply { moveTo(w, h); lineTo(0f, h); lineTo(w * b, h * (1 - b)); lineTo(w * (1 - b), h * (1 - b)); lineTo(w * (1 - b), h * b); lineTo(w, 0f); close() }, StoneDark)
        val r = Random(seed)
        repeat(1 + r.nextInt(2)) {
            val x0 = w * (0.25f + 0.5f * r.nextFloat())
            val y0 = h * (0.2f + 0.2f * r.nextFloat())
            drawPath(
                Path().apply {
                    moveTo(x0, y0)
                    lineTo(x0 + w * (r.nextFloat() - 0.5f) * 0.3f, y0 + h * 0.22f)
                    lineTo(x0 + w * (r.nextFloat() - 0.5f) * 0.4f, y0 + h * 0.42f)
                },
                StoneDark, style = Stroke(w * 0.035f),
            )
        }
        // A few specks.
        repeat(4) { drawCircle(StoneDark.copy(alpha = 0.5f), w * 0.03f, Offset(w * (0.2f + 0.6f * r.nextFloat()), h * (0.2f + 0.6f * r.nextFloat()))) }
    }
}

/** The bank: each missing number with how many are left to place; Clear empties the picked blank. */
@Composable
private fun NumberBank(bank: List<Int>, entries: List<String>, enabled: Boolean, onPlace: (String) -> Unit, spots: FlightSpots) {
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
                    Box(
                        Modifier.weight(1f).height(46.dp)
                            .raised(if (clear) p.card else if (left > 0) p.card else p.accent, if (on) p.ink else p.muted, 2.dp, 4.dp, Radius.M)
                            .onGloballyPositioned { if (!clear) spots.chips[value] = it.boundsInRoot() }
                            .clickable(enabled = on) { onPlace(if (clear) "" else value.toString()) },
                    ) {
                        Column(Modifier.fillMaxSize().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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

/**
 * The board and the bank, shared by the race and the game on your own. A number you place
 * while watching lifts out of its bank tile and arcs into the blank (Undo sends it back); a
 * change that wasn't your tap (the other side, the server, a new game) just appears.
 */
@Composable
internal fun ColumnScope.NumberCrossPlay(board: NumberBoard, entries: List<String>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit, onPlace: (String) -> Unit, hint: FlightHint) {
    val used = board.cells.indices.filter { board.cells[it] != "" }
    val rows = used.map { it / board.w }.let { if (it.isEmpty()) 1 else it.max() - it.min() + 1 }
    val cols = used.map { it % board.w }.let { if (it.isEmpty()) 1 else it.max() - it.min() + 1 }
    val spots = remember(board) { FlightSpots() }
    var flight by remember(board) { mutableStateOf<Flight?>(null) }
    var count by remember(board) { mutableIntStateOf(0) }
    val before = remember(board) { arrayOf(entries) }
    val calm = LocalReduceMotion.current
    val progress = remember(board) { Animatable(0f) }
    // Undo: a change right after your tap, to exactly one blank, sends that number back.
    LaunchedEffect(entries) {
        val old = before[0]
        before[0] = entries
        if (calm || hint.at == 0L || old.size != entries.size) return@LaunchedEffect
        val changed = entries.indices.filter { old[it] != entries[it] }
        hint.at = 0L
        val k = changed.singleOrNull() ?: return@LaunchedEffect
        val value = old[k].toIntOrNull() ?: return@LaunchedEffect
        flight = Flight(value, k, true, ++count)
    }
    // An undo that changed nothing must not leave the hint armed for a later change.
    LaunchedEffect(hint.at) {
        if (hint.at != 0L) {
            delay(300)
            hint.at = 0L
        }
    }
    LaunchedEffect(flight) {
        val f = flight ?: return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        progress.snapTo(0f)
        if (flight == f) flight = null
    }
    Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { spots.origin = it.boundsInRoot().topLeft }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardArea(cols.toFloat() / rows) { size -> NumberGrid(board, entries, selected, enabled, onSelect, size, spots, flight?.takeIf { !it.back }?.k) }
            NumberBank(board.bank, entries, enabled && selected != null, { v ->
                // Your own pick starts flying now, so the number never shows in the blank first.
                val k = selected
                if (!calm && k != null && v != "" && entries.getOrNull(k) != v) flight = Flight(v.toInt(), k, false, ++count)
                onPlace(v)
            }, spots)
        }
        flight?.let { f ->
            val blank = spots.blanks[f.k]
            val chip = spots.chips[f.value]
            if (blank != null && chip != null) {
                val p = AppTheme.palette
                val side = blank.width * 0.92f
                val lift = side / 12
                Box(
                    Modifier.size(with(LocalDensity.current) { side.toDp() })
                        .graphicsLayer {
                            val t = progress.value
                            val from = if (f.back) blank.center else chip.center
                            val to = if (f.back) chip.center else blank.center
                            val at = from + (to - from) * t - spots.origin
                            translationX = at.x - side / 2
                            translationY = at.y - side / 2 - sin(PI.toFloat() * t) * side * 1.1f
                            val grow = 1f + 0.25f * sin(PI.toFloat() * t)
                            scaleX = grow
                            scaleY = grow
                        }
                        .raised(p.card, p.ink, 1.5.dp, with(LocalDensity.current) { lift.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        f.value.toString(), color = p.text, fontWeight = FontWeight.Bold,
                        fontSize = with(LocalDensity.current) { (side * 0.42f).toSp() },
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
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
    val hint = remember { FlightHint() }
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
        subtitle = "Game ${room.round} · ${numbers.level.label}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, localNow, p.primary, detail = { player, isMe ->
            if (isMe) "$mine% right" else "${player.progress ?: 0}% right"
        }),
        glow = GameKind.NUMBERS.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        tools = listOf(DockTool("Undo", GameIcons.Undo, enabled = playing && state.numberHistory.isNotEmpty(), onClick = { hint.mark(); vm.undoNumber() })),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trailing = { Text(style = Digits, text = formatDuration(elapsed), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = room.winnerName ?: room.opponent?.name ?: "The other player"
            MatchPopup(
                room, GameKind.NUMBERS, outcomeOf(room.winner), resultTitle(room.winner, name),
                when (room.endReason) {
                    "solved" -> if (room.winner == "me") "You solved it first!" else "$name solved it first."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
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
                        CountdownText(((startAt - now + 999) / 1000).coerceAtLeast(1), p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            NumberCrossPlay(board, entries, state.numberSelected, playing, vm::selectBlank, vm::placeNumber, hint)
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

/** Number Cross on your own, against the clock. */
@Composable
fun SoloNumbersScreen(state: SoloNumbersUiState, vm: SoloNumbersViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    val hint = remember { FlightHint() }
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
        menu = soloMenu(GameKind.NUMBERS, onHome, { showRules = true }, if (puzzle != null) vm::again else null),
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
            NumberCrossPlay(board, state.entries, state.selected, done == null, vm::select, vm::place, hint)
        }
    }
}
