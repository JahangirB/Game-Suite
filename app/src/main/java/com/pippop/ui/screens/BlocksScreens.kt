package com.pippop.ui.screens

import com.pippop.ui.startWith
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column

import com.pippop.ui.theme.loopingFloat

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.alpha
import com.pippop.data.ProfileStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import com.pippop.ui.Sound
import com.pippop.ui.rememberSfx
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Pieces
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.game.blocks.Blocks
import com.pippop.game.blocks.BlocksView
import com.pippop.game.blocks.Placement
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloBlocksUiState
import com.pippop.ui.SoloBlocksViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme

/** The board: everyone's squares, empty start squares ringed, the last piece outlined and [preview]. */
@Composable
internal fun BlocksBoard(
    view: BlocksView,
    preview: List<Pair<Int, Int>>?,
    previewOk: Boolean,
    onTap: ((Int, Int) -> Unit)?,
    modifier: Modifier = Modifier,
    /** Called when a drag across the board ends (the finger lifts). */
    onDrop: (() -> Unit)? = null,
    /** Told true when a drag starts and false when it ends, so the screen can tell tap from drag. */
    onDragging: ((Boolean) -> Unit)? = null,
    /** "Where can I go?": the corners a piece of yours can build from, each with a pulsing dot. */
    spots: Set<Pair<Int, Int>> = emptySet(),
) {
    val p = AppTheme.palette
    val pulse = loopingFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), still = 0.8f, label = "spots")
    // The piece just placed drops onto the board from above and settles; earlier ones stay put.
    val land = remember { Animatable(1f) }
    val sfx = rememberSfx()
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(view.lastCells) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        if (view.lastCells.isEmpty()) return@LaunchedEffect
        land.snapTo(0f)
        land.animateTo(1f, tween(Motion.MOVE_MS, easing = FastOutSlowInEasing))
        sfx.play(Sound.PIECE_PLACE)
    }
    val dropped = rememberUpdatedState(onDrop)
    val dragging = rememberUpdatedState(onDragging)
    val dark = p.dark
    val landing = land.value < 1f
    val falling = if (landing) view.lastCells.toSet() else emptySet()
    Box(
        modifier.then(
            if (onTap == null) Modifier else Modifier.pointerInput(view.size) {
                detectTapGestures { o ->
                    val cell = size.width.toFloat() / view.size
                    onTap((o.x / cell).toInt().coerceIn(0, view.size - 1), (o.y / cell).toInt().coerceIn(0, view.size - 1))
                }
            }.then(
                // Dragging carries the piece: the preview follows the finger, and lifting it is "drop".
                if (onTap == null) Modifier else Modifier.pointerInput(view.size) {
                    val cell = size.width.toFloat() / view.size
                    // The piece is aimed two squares above the finger so the finger doesn't hide it.
                    fun at(o: Offset) = onTap((o.x / cell).toInt().coerceIn(0, view.size - 1), ((o.y - 2 * cell) / cell).toInt().coerceIn(0, view.size - 1))
                    detectDragGestures(
                        onDragStart = { dragging.value?.invoke(true); at(it) },
                        onDrag = { change, _ -> at(change.position) },
                        onDragEnd = { dragging.value?.invoke(false); dropped.value?.invoke() },
                        onDragCancel = { dragging.value?.invoke(false) },
                    )
                },
            ),
        ),
    ) {
      Canvas(Modifier.fillMaxSize()) {
        val n = view.size
        val c = size.width / n
        // The board stands on the table: a shadow under it, and a dot in every square.
        boxShadow(Offset.Zero, size, c * 0.4f, drop = c * 0.18f, strength = 0.22f)
        drawRoundRect(p.card, cornerRadius = CornerRadius(c * 0.4f))
        for (y in 0 until n) for (x in 0 until n) {
            val mark = view.board[y * n + x]
            val tl = Offset(x * c + c * 0.06f, y * c + c * 0.06f)
            val sz = Size(c * 0.88f, c * 0.88f)
            if (mark == '.' || (x to y) in falling) {
                drawRoundRect(p.accent, tl, sz, CornerRadius(c * 0.18f))
                drawCircle(p.ink.copy(alpha = 0.22f), c * 0.07f, Offset((x + 0.5f) * c, (y + 0.5f) * c))
            } else plasticSquare(markColor(mark), tl, c)
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
                plasticSquare(color, at - Offset(half, half), half * 2 / 0.88f)
            }
        }
        // Start squares nobody has covered yet.
        view.starts.forEach { (m, s) ->
            if (view.at(s.first, s.second) == '.') drawCircle(markColor(m), c * 0.3f, Offset((s.first + 0.5f) * c, (s.second + 0.5f) * c), style = Stroke(c * 0.12f))
        }
        if (!landing) view.lastCells.forEach { (x, y) ->
            drawRoundRect(p.ink, Offset(x * c + c * 0.06f, y * c + c * 0.06f), Size(c * 0.88f, c * 0.88f), CornerRadius(c * 0.18f), style = Stroke(c * 0.1f))
        }
        if (preview != null) {
            val ink = if (previewOk) Pieces.Green.darker(0.4f) else if (dark) Color.White else Pieces.Red.darker(0.4f)
            if (previewOk) {
                // The corners of yours it would touch, lit.
                val lit = preview.flatMap { (x, y) -> listOf(x - 1 to y - 1, x + 1 to y - 1, x - 1 to y + 1, x + 1 to y + 1) }.filter { view.at(it.first, it.second) == 'm' }
                lit.forEach { (x, y) ->
                    val tl = Offset(x * c + c * 0.06f, y * c + c * 0.06f)
                    drawRoundRect(p.ink, tl, Size(c * 0.88f, c * 0.88f), CornerRadius(c * 0.18f), style = Stroke(c * 0.2f))
                    drawRoundRect(Pieces.Yellow, tl, Size(c * 0.88f, c * 0.88f), CornerRadius(c * 0.18f), style = Stroke(c * 0.12f))
                }
            }
            preview.forEach { (x, y) ->
                if (x in 0 until n && y in 0 until n) {
                    val tl = Offset(x * c + c * 0.06f, y * c + c * 0.06f)
                    val sz = Size(c * 0.88f, c * 0.88f)
                    drawRoundRect((if (previewOk) Pieces.Green else Pieces.Red).copy(alpha = 0.75f), tl, sz, CornerRadius(c * 0.18f))
                    if (previewOk) {
                        // Fits: a solid outline.
                        drawRoundRect(ink, tl, sz, CornerRadius(c * 0.18f), style = Stroke(c * 0.1f))
                    } else {
                        // Doesn't fit: a dashed outline and a cross, so colour is not the only clue.
                        drawRoundRect(ink, tl, sz, CornerRadius(c * 0.18f), style = Stroke(c * 0.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(c * 0.16f, c * 0.1f))))
                        val m = c * 0.3f
                        drawLine(ink, tl + Offset(m, m), tl + Offset(sz.width - m, sz.height - m), c * 0.08f)
                        drawLine(ink, tl + Offset(sz.width - m, m), tl + Offset(m, sz.height - m), c * 0.08f)
                    }
                }
            }
        }
      }
      // The pulsing "where can I go" dots live on their own layer, so only they redraw each frame.
      Canvas(Modifier.fillMaxSize()) {
        val c = size.width / view.size
        val t = pulse.value
        spots.forEach { (x, y) ->
            drawCircle(markColor('m').copy(alpha = 0.35f + 0.5f * t), c * (0.16f + 0.06f * t), Offset((x + 0.5f) * c, (y + 0.5f) * c))
        }
      }
    }
}

/** A raised, bevelled square of [color] for a cell of width [c], its top-left corner at [tl]. */
private fun DrawScope.plasticSquare(color: Color, tl: Offset, c: Float) {
    val side = c * 0.88f
    val lift = c * 0.06f
    raisedTile(color, tl, Size(side, side - lift), c * 0.18f, lift)
    // The light catching the top and left edges.
    drawRoundRect(Color.White.copy(alpha = 0.5f), tl + Offset(c * 0.1f, c * 0.07f), Size(side * 0.62f, c * 0.07f), CornerRadius(c * 0.04f))
    drawRoundRect(Color.White.copy(alpha = 0.35f), tl + Offset(c * 0.07f, c * 0.1f), Size(c * 0.07f, side * 0.4f), CornerRadius(c * 0.04f))
}

/** A piece drawn small, for the tray. */
@Composable
private fun PieceIcon(piece: Int, orient: Int, color: Color, selected: Boolean, noRoom: Boolean = false, onClick: () -> Unit) {
    val p = AppTheme.palette
    val cells = Blocks.shape(piece, orient)
    Surface(
        color = if (selected) p.accent else p.card,
        shape = Shapes.S,
        border = BorderStroke(if (selected) 3.dp else 2.dp, if (selected) p.primary else p.ink),
        modifier = Modifier.size(52.dp).alpha(if (noRoom) 0.4f else 1f).clickable(onClick = onClick),
    ) {
        // A piece that fits nowhere is grey, with a small "no room" under it.
        if (noRoom) Text("no room", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 36.dp))
        Canvas(Modifier.padding(6.dp)) {
            val c = size.width / 5
            val w = cells.maxOf { it.first } + 1
            val h = cells.maxOf { it.second } + 1
            val ox = (5 - w) * c / 2
            val oy = (5 - h) * c / 2
            for ((x, y) in cells) drawRoundRect(if (noRoom) p.hairline else color, Offset(ox + x * c + 1, oy + y * c + 1), Size(c - 2, c - 2), CornerRadius(c * 0.2f))
        }
    }
}

/**
 * Why [cells] can't go down, in a few plain words, or null when the game's own check
 * ([BlocksView.allows]) accepts it. The game only answers yes or no; this gives the same yes/no
 * answer from the same squares, and names the first rule that is broken.
 */
internal fun blocksWhy(view: BlocksView, cells: List<Pair<Int, Int>>, first: Boolean): String? {
    if (cells.any { (x, y) -> view.at(x, y) == null }) return "Off the board"
    if (cells.any { (x, y) -> view.at(x, y) != '.' }) return "Covers a piece"
    if (cells.any { (x, y) -> listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dy) -> view.at(x + dx, y + dy) == 'm' } }) return "Touches your colour along a side"
    if (first) return if (view.starts['m'] in cells) null else "Must cover your start square"
    return if (cells.any { (x, y) -> listOf(1 to 1, -1 to 1, 1 to -1, -1 to -1).any { (dx, dy) -> view.at(x + dx, y + dy) == 'm' } }) null else "Must touch your colour at a corner"
}

/** The pieces you have left: one chip per size with a count; the open size shows its pieces. */
@Composable
private fun BlocksTray(
    mine: List<Int>,
    chosen: Int?,
    orient: Int,
    openSize: Int?,
    noRoom: (Int) -> Boolean,
    onGroup: (Int) -> Unit,
    onPiece: (Int) -> Unit,
) {
    val p = AppTheme.palette
    val groups = mine.groupBy { Blocks.PIECES[it].size }.toSortedMap(compareByDescending { it })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.forEach { (size, pieces) ->
            val open = size == openSize
            Surface(
                color = if (open) p.accent else p.card,
                shape = Shapes.S,
                border = BorderStroke(if (open) 3.dp else 2.dp, if (open) p.primary else p.ink),
                modifier = Modifier.weight(1f).clickable { onGroup(size) }
                    .semantics { contentDescription = "$size squares, ${pieces.size} left" },
            ) {
                Column(Modifier.padding(vertical = 4.dp).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$size", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                    Text("×${pieces.size}", color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    val shown = groups[openSize].orEmpty()
    if (shown.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { i -> PieceIcon(i, if (i == chosen) orient else 0, markColor('m'), i == chosen, noRoom = noRoom(i)) { onPiece(i) } }
        }
    }
}

/**
 * The board with your pieces underneath. Pick a piece, turn or flip it, tap or drag on the
 * board to put it there (green fits, red doesn't, with the reason under the board), and Place.
 * Lifting your finger from a drag on red sends the piece back to the tray.
 */
@Composable
fun ColumnScope.BlocksPlay(
    view: BlocksView,
    canPlay: Boolean,
    onPlace: (Placement) -> Unit,
    /** Each step up turns (or flips) the chosen piece: the dock's Turn and Flip. Zero keeps the buttons here. */
    turns: Int = 0,
    flips: Int = 0,
    inlineTools: Boolean = true,
) {
    val hintsOn = ProfileStore.get(LocalContext.current).profile.collectAsState().value.blocksHints && canPlay
    BlocksPlayBody(view, canPlay, hintsOn, onPlace, turns, flips, inlineTools)
}

/** [BlocksPlay] with "Where can I go?" ([hintsOn]) given, and a piece and spot to start from (for pictures). */
@Composable
internal fun ColumnScope.BlocksPlayBody(
    view: BlocksView,
    canPlay: Boolean,
    hintsOn: Boolean,
    onPlace: (Placement) -> Unit,
    turns: Int = 0,
    flips: Int = 0,
    inlineTools: Boolean = true,
    startPiece: Int? = null,
    startTarget: Pair<Int, Int>? = null,
) {
    val p = AppTheme.palette
    val mine = view.left['m'].orEmpty()
    var piece by remember { mutableStateOf(startPiece) }
    var orient by remember { mutableStateOf(0) }
    var turnsSeen by remember { mutableIntStateOf(turns) }
    var flipsSeen by remember { mutableIntStateOf(flips) }
    if (turns != turnsSeen) {
        repeat((turns - turnsSeen).coerceAtLeast(0)) { orient = (orient / 4) * 4 + (orient + 1) % 4 }
        turnsSeen = turns
    }
    if (flips != flipsSeen) {
        if ((flips - flipsSeen) % 2 != 0) orient = (orient + 4) % 8
        flipsSeen = flips
    }
    var target by remember { mutableStateOf(startTarget) }
    var openSize by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val chosen = piece?.takeIf { it in mine }
    // A turn that ends mid-drag removes the board's touch handling without a cancel; reset so announcements return.
    LaunchedEffect(canPlay, chosen) { if (!canPlay || chosen == null) dragging = false }
    val shape = chosen?.let { Blocks.shape(it, orient) }
    val placement = if (shape != null && target != null) {
        val (tx, ty) = target!!
        Placement(chosen, orient, tx - shape.maxOf { it.first } / 2, ty - shape.maxOf { it.second } / 2)
    } else {
        null
    }
    val cells = placement?.let { pl -> shape!!.map { (x, y) -> (pl.x + x) to (pl.y + y) } }
    val ok = placement != null && view.allows(placement)
    // Why not, in plain words: the same squares the game's check looks at.
    val why = if (cells != null && !ok) blocksWhy(view, cells, mine.size == Blocks.PIECES.size) else null
    // "Where can I go?" (on unless switched off): the corners to build from, only those the
    // chosen piece fits once one is picked, and pieces that fit nowhere greyed.
    val targets = remember(view.board, view.left, hintsOn) { if (hintsOn) view.targets() else emptyList() }
    val fits = remember(view.board, view.left, hintsOn) { if (hintsOn) mine.associateWith { view.fitsAt(it, targets) } else emptyMap() }
    val spots = when {
        !hintsOn -> emptySet()
        chosen != null -> fits[chosen].orEmpty()
        else -> fits.values.flatten().toSet()
    }
    fun putBack() {
        piece = null
        target = null
        orient = 0
    }
    /** The legality of whatever is in hand right now, read fresh so a fast lift can't use an old verdict. */
    fun okNow(): Boolean {
        val c = piece?.takeIf { it in mine } ?: return false
        val t = target ?: return false
        val sh = Blocks.shape(c, orient)
        return view.allows(Placement(c, orient, t.first - sh.maxOf { it.first } / 2, t.second - sh.maxOf { it.second } / 2))
    }
    BoardArea(1f) { size ->
        BlocksBoard(
            view, cells, ok, if (canPlay && chosen != null) { x, y -> target = x to y } else null, size,
            onDrop = { if (!okNow()) putBack() }, onDragging = { dragging = it }, spots = if (cells == null) spots else emptySet(),
        )
    }
    // The verdict on the piece in hand, always two lines tall so the board doesn't jump.
    val verdict = when {
        cells == null -> if (chosen == null) "Pick a piece" else "Drag the piece onto the board"
        ok -> if (mine.size == Blocks.PIECES.size) "Fits" else "Fits at your corner"
        else -> why ?: "Doesn't fit"
    }
    Text(
        verdict, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
        // Read out only on taps and drops, not at every square of a drag.
        modifier = Modifier.fillMaxWidth().semantics { if (!dragging) liveRegion = LiveRegionMode.Polite },
    )
    // The open group: the chosen piece's, else the last one opened, else the largest.
    val sizes = mine.map { Blocks.PIECES[it].size }.toSet()
    val openNow = chosen?.let { Blocks.PIECES[it].size } ?: openSize?.takeIf { it in sizes } ?: sizes.maxOrNull()
    BlocksTray(
        mine, chosen, orient,
        openSize = openNow,
        noRoom = { hintsOn && fits[it].isNullOrEmpty() },
        onGroup = { size ->
            if (size != openNow) {
                if (chosen != null) putBack()
                openSize = size
            }
        },
        onPiece = { i ->
            if (piece != i) orient = 0
            piece = i
            openSize = Blocks.PIECES[i].size
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (inlineTools) {
            SecondaryButton("Turn", { orient = (orient / 4) * 4 + (orient + 1) % 4 }, Modifier.weight(1f), enabled = chosen != null)
            SecondaryButton("Flip", { orient = (orient + 4) % 8 }, Modifier.weight(1f), enabled = chosen != null)
        }
        PrimaryButton(
            "Place",
            {
                placement?.let(onPlace)
                putBack()
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
    var turnTaps by remember { mutableIntStateOf(0) }
    var flipTaps by remember { mutableIntStateOf(0) }
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.BLOCKS) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    val turnName = turns.turnName ?: room.opponent?.name
    val names = listOf('m' to "You") + room.others.mapIndexed { i, o -> letterOf(i) to o.name }
    GameFrame(
        title = "Corner Blocks",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
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
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        myTurn = myTurn,
        tools = listOf(
            DockTool("Turn", GameIcons.Rotate, anytime = false) { turnTaps++ },
            DockTool("Flip", GameIcons.Flip, anytime = false) { flipTaps++ },
        ),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.BLOCKS,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "The other player"),
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
        BlocksPlay(view, myTurn && !state.firing, vm::placeBlock, turns = turnTaps, flips = flipTaps, inlineTools = false)
    }
}

// ---- Against the computer -----------------------------------------------------------------------

@Composable
fun SoloBlocksScreen(state: SoloBlocksUiState, vm: SoloBlocksViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.BLOCKS) { showRules = false }
    val view = state.view
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    if (view == null) { BackToHome(onHome); return }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.BLOCKS, "New game", vm::startWith) { changing = false }
    val names = listOf('m' to "You") + (1..state.opponents).map { "opq"[it - 1] to "CPU $it" }
    GameFrame(
        title = "Corner Blocks",
        subtitle = "Against ${state.opponents} computer${if (state.opponents > 1) "s" else ""}",
        players = emptyList(),
        glow = GameKind.BLOCKS.color,
        menu = soloMenu(GameKind.BLOCKS, onHome, { showRules = true }, vm::start, { changing = true }),
        popup = if (state.result == null) null else popupContent {
            SoloPopup(
                GameKind.BLOCKS, state.result, blocksScores(view, names).joinToString(" · "),
                onAgain = vm::start, onHome = onHome, finishLabel = "Change players", onFinish = { changing = true },
            )
        },
    ) {
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

/** "Where can I go?" on or off, on this phone (for players who want it hard). */
@Composable
fun BlocksHintsSwitch() {
    val p = AppTheme.palette
    val store = ProfileStore.get(LocalContext.current)
    val on = store.profile.collectAsState().value.blocksHints
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = store::setBlocksHints)) {
        Column(Modifier.weight(1f)) {
            Text("Where can I go?", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
            Text("Light the corners your pieces can build from; grey the pieces that fit nowhere.", color = p.muted, fontSize = TextSize.Label)
        }
        ToySwitch(checked = on, onCheckedChange = null)
    }
}
