package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Pieces
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.blocks.Blocks
import com.arcade.sudoku.game.blocks.BlocksView
import com.arcade.sudoku.game.blocks.Placement
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloBlocksUiState
import com.arcade.sudoku.ui.SoloBlocksViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme

/** The board: everyone's squares, empty start squares ringed, the last piece outlined and [preview]. */
@Composable
private fun BlocksBoard(view: BlocksView, preview: List<Pair<Int, Int>>?, previewOk: Boolean, onTap: ((Int, Int) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    // The piece just placed drops onto the board from above and settles; earlier ones stay put.
    val land = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(view.lastCells) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        if (view.lastCells.isEmpty()) return@LaunchedEffect
        land.snapTo(0f)
        land.animateTo(1f, tween(Motion.MOVE_MS, easing = FastOutSlowInEasing))
    }
    val landing = land.value < 1f
    val falling = if (landing) view.lastCells.toSet() else emptySet()
    Canvas(
        modifier.then(
            if (onTap == null) Modifier else Modifier.pointerInput(view.size) {
                detectTapGestures { o ->
                    val cell = size.width.toFloat() / view.size
                    onTap((o.x / cell).toInt().coerceIn(0, view.size - 1), (o.y / cell).toInt().coerceIn(0, view.size - 1))
                }
            },
        ),
    ) {
        val n = view.size
        val c = size.width / n
        drawRoundRect(p.card, cornerRadius = CornerRadius(c * 0.4f))
        for (y in 0 until n) for (x in 0 until n) {
            val mark = view.board[y * n + x]
            val tl = Offset(x * c + c * 0.06f, y * c + c * 0.06f)
            val sz = Size(c * 0.88f, c * 0.88f)
            if (mark == '.' || (x to y) in falling) drawRoundRect(p.accent, tl, sz, CornerRadius(c * 0.18f))
            else drawRoundRect(markColor(mark), tl, sz, CornerRadius(c * 0.18f))
        }
        if (landing) {
            // Falling from above, a little bigger, its shadow growing as it nears the board.
            val k = 1f - land.value
            val grow = 1f + 0.25f * k
            val color = view.lastWho?.let { markColor(it) } ?: p.primary
            val cx = view.lastCells.map { it.first + 0.5f }.average().toFloat() * c
            val cy = view.lastCells.map { it.second + 0.5f }.average().toFloat() * c
            for ((x, y) in view.lastCells) {
                val tl = Offset(x * c + c * 0.06f, y * c + c * 0.06f)
                drawRoundRect(Color.Black.copy(alpha = 0.18f * (1f - k)), tl, Size(c * 0.88f, c * 0.88f), CornerRadius(c * 0.18f))
            }
            for ((x, y) in view.lastCells) {
                val base = Offset((x + 0.5f) * c, (y + 0.5f) * c)
                val at = Offset(cx + (base.x - cx) * grow, cy + (base.y - cy) * grow - k * c * 2.5f)
                val half = c * 0.44f * grow
                drawRoundRect(color, at - Offset(half, half), Size(half * 2, half * 2), CornerRadius(c * 0.18f))
            }
        }
        // Start squares nobody has covered yet.
        view.starts.forEach { (m, s) ->
            if (view.at(s.first, s.second) == '.') drawCircle(markColor(m), c * 0.3f, Offset((s.first + 0.5f) * c, (s.second + 0.5f) * c), style = Stroke(c * 0.12f))
        }
        if (!landing) view.lastCells.forEach { (x, y) ->
            drawRoundRect(p.ink, Offset(x * c + c * 0.06f, y * c + c * 0.06f), Size(c * 0.88f, c * 0.88f), CornerRadius(c * 0.18f), style = Stroke(c * 0.1f))
        }
        preview?.forEach { (x, y) ->
            if (x in 0 until n && y in 0 until n) {
                drawRoundRect(
                    (if (previewOk) Pieces.Green else Pieces.Red).copy(alpha = 0.75f),
                    Offset(x * c + c * 0.06f, y * c + c * 0.06f), Size(c * 0.88f, c * 0.88f), CornerRadius(c * 0.18f),
                )
            }
        }
    }
}

/** A piece drawn small, for the tray. */
@Composable
private fun PieceIcon(piece: Int, orient: Int, color: Color, selected: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    val cells = Blocks.shape(piece, orient)
    Surface(
        color = if (selected) p.accent else p.card,
        shape = Shapes.S,
        border = BorderStroke(if (selected) 3.dp else 2.dp, if (selected) p.primary else p.ink),
        modifier = Modifier.size(52.dp).clickable(onClick = onClick),
    ) {
        Canvas(Modifier.padding(6.dp)) {
            val c = size.width / 5
            val w = cells.maxOf { it.first } + 1
            val h = cells.maxOf { it.second } + 1
            val ox = (5 - w) * c / 2
            val oy = (5 - h) * c / 2
            for ((x, y) in cells) drawRoundRect(color, Offset(ox + x * c + 1, oy + y * c + 1), Size(c - 2, c - 2), CornerRadius(c * 0.2f))
        }
    }
}

/**
 * The board with your pieces underneath. Pick a piece, turn or flip it, tap the board to put
 * it there (green fits, red doesn't), and Place.
 */
@Composable
fun ColumnScope.BlocksPlay(view: BlocksView, canPlay: Boolean, onPlace: (Placement) -> Unit) {
    val mine = view.left['m'].orEmpty()
    var piece by remember { mutableStateOf<Int?>(null) }
    var orient by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val chosen = piece?.takeIf { it in mine }
    val shape = chosen?.let { Blocks.shape(it, orient) }
    val placement = if (shape != null && target != null) {
        val (tx, ty) = target!!
        Placement(chosen, orient, tx - shape.maxOf { it.first } / 2, ty - shape.maxOf { it.second } / 2)
    } else {
        null
    }
    val cells = placement?.let { pl -> shape!!.map { (x, y) -> (pl.x + x) to (pl.y + y) } }
    val ok = placement != null && view.allows(placement)
    BoardArea(1f) { size -> BlocksBoard(view, cells, ok, if (canPlay && chosen != null) { x, y -> target = x to y } else null, size) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mine.sortedByDescending { Blocks.PIECES[it].size }.forEach { i ->
            PieceIcon(i, if (i == chosen) orient else 0, markColor('m'), i == chosen) {
                if (piece != i) orient = 0
                piece = i
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Turn", { orient = (orient / 4) * 4 + (orient + 1) % 4 }, Modifier.weight(1f), enabled = chosen != null)
        SecondaryButton("Flip", { orient = (orient + 4) % 8 }, Modifier.weight(1f), enabled = chosen != null)
        PrimaryButton(
            "Place",
            {
                placement?.let(onPlace)
                piece = null
                target = null
                orient = 0
            },
            Modifier.weight(1f), enabled = canPlay && ok,
        )
    }
}

private fun blocksScores(view: BlocksView, names: List<Pair<Char, String>>) = names.map { (m, n) -> "$n ${view.placed[m] ?: 0}" }

// ---- Against others ---------------------------------------------------------------------------

@Composable
fun BlocksGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val view = turns.blocks ?: return
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me" && !finished
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.BLOCKS) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    val turnName = turns.turnName ?: room.opponent?.name
    val names = listOf('m' to "You") + room.others.mapIndexed { i, o -> letterOf(i) to o.name }
    GameFrame(
        title = "Corner Blocks",
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, MyDisc,
            detail = { player, isMe ->
                val mark = if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0))
                "${view.placed[mark] ?: 0} squares"
            },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> markColor(letterOf(i)) },
        ),
        glow = GameKind.BLOCKS.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.BLOCKS,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "Your opponent"),
                when (room.endReason) {
                    "squares" -> "Nobody can place another piece: the most squares wins."
                    "draw" -> "Level on squares."
                    "left" -> "A player left, so the game stopped."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = blocksScores(view, names),
            )
        },
    ) {
        TurnLine(
            when {
                finished -> "Game over"
                myTurn -> if (view.left['m'].orEmpty().size == Blocks.PIECES.size) "Cover your ringed start square" else "Your turn: touch your colour only at a corner"
                else -> "${turnName ?: "Someone"} is placing…"
            },
            myTurn, GameKind.BLOCKS.color,
        )
        BlocksPlay(view, myTurn && !state.firing, vm::placeBlock)
    }
}

// ---- Against the computer -----------------------------------------------------------------------

@Composable
fun SoloBlocksScreen(state: SoloBlocksUiState, vm: SoloBlocksViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.BLOCKS) { showRules = false }
    val view = state.view
    val names = listOf('m' to "You") + (1..state.opponents).map { "opq"[it - 1] to "CPU $it" }
    GameFrame(
        title = "Corner Blocks",
        subtitle = "Against ${state.opponents} computer${if (state.opponents > 1) "s" else ""}",
        players = emptyList(),
        glow = GameKind.BLOCKS.color,
        dock = soloDock(onHome, { showRules = true }, if (view != null) vm::start else null),
        scrollable = view == null,
        popup = if (view == null || state.result == null) null else popupContent {
            SoloPopup(
                GameKind.BLOCKS, state.result, blocksScores(view, names).joinToString(" · "),
                onAgain = vm::start, onHome = onHome, finishLabel = "Change players", onFinish = vm::changeSetup,
            )
        },
    ) {
        if (view == null) {
            AppCard("Corner Blocks") {
                Text("Lay your 21 pieces. Your first covers your start square; after that each piece must touch yours only corner to corner. Most squares placed wins.", color = p.muted, fontSize = TextSize.Body)
                Text("Computers", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 1..3) {
                        if (state.opponents == n) PrimaryButton("$n", {}, Modifier.weight(1f)) else SecondaryButton("$n", { vm.setOpponents(n) }, Modifier.weight(1f))
                    }
                }
                Text("How good", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Easy", "Medium", "Hard").forEachIndexed { i, label ->
                        if (state.skill == i) PrimaryButton(label, {}, Modifier.weight(1f)) else SecondaryButton(label, { vm.setSkill(i) }, Modifier.weight(1f))
                    }
                }
                PrimaryButton("Start", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            val myTurn = state.turn == 'm' && state.result == null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TurnLine(
                    when {
                        state.result != null -> "Game over"
                        myTurn -> "Your turn"
                        else -> "${names.firstOrNull { it.first == state.turn }?.second ?: "The computer"} is placing…"
                    },
                    myTurn, GameKind.BLOCKS.color,
                )
            }
            Text(blocksScores(view, names).joinToString("  "), color = p.muted, fontSize = TextSize.Body)
            BlocksPlay(view, myTurn, vm::place)
        }
    }
}
