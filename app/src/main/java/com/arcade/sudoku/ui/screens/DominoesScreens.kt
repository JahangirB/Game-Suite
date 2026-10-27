package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.Sound
import com.arcade.sudoku.ui.rememberSfx
import com.arcade.sudoku.ui.Sfx
import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Board
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.dominoes.DominoLayout
import com.arcade.sudoku.game.dominoes.DominoOptions
import com.arcade.sudoku.game.dominoes.DominoView
import com.arcade.sudoku.game.dominoes.Dominoes
import com.arcade.sudoku.game.dominoes.Tile
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloDominoesUiState
import com.arcade.sudoku.ui.SoloDominoesViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme

private val Felt = Board.Felt
private val Bone = Board.Bone
private val Ink = Board.Ink
private val Glow = Board.Glow

/** The line board's width per unit of height. */
private const val LINE_ASPECT = 1.25f

// ---- Drawing -------------------------------------------------------------------------------

/** One tile in [topLeft]/[size]: [a] on the left (or top, when upright) and [b] on the other half. */
private fun DrawScope.drawDomino(topLeft: Offset, size: Size, a: Int, b: Int, highlight: Boolean = false) {
    val r = minOf(size.width, size.height) * 0.18f
    drawRoundRect(Bone, topLeft, size, CornerRadius(r))
    drawRoundRect(if (highlight) Glow else Ink, topLeft, size, CornerRadius(r), style = Stroke(if (highlight) 3.dp.toPx() else 1.5.dp.toPx()))
    val across = size.width > size.height
    val half = if (across) size.width / 2 else size.height / 2
    val inset = minOf(size.width, size.height) * 0.2f
    if (across) {
        drawLine(Ink, Offset(topLeft.x + half, topLeft.y + inset), Offset(topLeft.x + half, topLeft.y + size.height - inset), 1.2.dp.toPx())
        pips(Offset(topLeft.x + half / 2, topLeft.y + size.height / 2), size.height, a)
        pips(Offset(topLeft.x + half * 1.5f, topLeft.y + size.height / 2), size.height, b)
    } else {
        drawLine(Ink, Offset(topLeft.x + inset, topLeft.y + half), Offset(topLeft.x + size.width - inset, topLeft.y + half), 1.2.dp.toPx())
        pips(Offset(topLeft.x + size.width / 2, topLeft.y + half / 2), size.width, a)
        pips(Offset(topLeft.x + size.width / 2, topLeft.y + half * 1.5f), size.width, b)
    }
}

/** [n] pips on a square half [side] wide, centred on [c]. */
private fun DrawScope.pips(c: Offset, side: Float, n: Int) {
    val d = side * 0.26f
    val spots = when (n) {
        0 -> emptyList<Pair<Float, Float>>()
        1 -> listOf(0f to 0f)
        2 -> listOf(-1f to -1f, 1f to 1f)
        3 -> listOf(-1f to -1f, 0f to 0f, 1f to 1f)
        4 -> listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
        5 -> listOf(-1f to -1f, 1f to -1f, 0f to 0f, -1f to 1f, 1f to 1f)
        else -> listOf(-1f to -1f, 1f to -1f, -1f to 0f, 1f to 0f, -1f to 1f, 1f to 1f)
    }
    spots.forEach { (x, y) -> drawCircle(Ink, side * 0.085f, Offset(c.x + x * d, c.y + y * d)) }
}

/**
 * The line of play on the table, laid out like a real one: the round's first tile ([start]
 * in the line) in the middle, doubles across, turning near the edges, all scaled to fit. The
 * tile at [highlight] (the last one played) has a gold edge and slides in when [incoming]
 * changes: up from your hand ([fromBelow]) or down from the other side of the table.
 */
@Composable
fun DominoLine(line: List<Tile>, start: Int, highlight: Int?, modifier: Modifier = Modifier, incoming: Any? = null, fromBelow: Boolean = true) {
    val shape = Shapes.L
    val slide = remember { Animatable(1f) }
    val sfx = rememberSfx()
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(incoming) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        if (incoming == null) return@LaunchedEffect
        slide.snapTo(0f)
        slide.animateTo(1f, tween(Motion.MOVE_MS + 150, easing = FastOutSlowInEasing))
        // The tile clacks onto the table as it arrives.
        sfx.play(Sound.PIECE_PLACE, if (fromBelow) 1f else Sfx.OTHERS)
    }
    val placed = remember(line, start) { DominoLayout.layout(line, start) }
    Canvas(modifier.clip(shape).background(Felt).border(2.dp, AppTheme.palette.ink, shape)) {
        if (placed.isEmpty()) return@Canvas
        val pad = 12.dp.toPx()
        val minX = placed.minOf { it.x }
        val minY = placed.minOf { it.y }
        val w = placed.maxOf { it.x + it.w } - minX
        val h = placed.maxOf { it.y + it.h } - minY
        // Half a tile is at most 32 dp, so the first few tiles don't fill the table.
        val unit = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h, 32.dp.toPx())
        val left = (size.width - w * unit) / 2 - minX * unit
        val top = (size.height - h * unit) / 2 - minY * unit
        placed.forEachIndexed { i, t ->
            var offset = Offset(left + t.x * unit, top + t.y * unit)
            if (i == highlight && slide.value < 1f) {
                // From the bottom edge (your hand) or the top (everyone else), turning as it comes.
                val k = 1f - slide.value
                offset += Offset(0f, (if (fromBelow) size.height - offset.y else -offset.y - t.h * unit) * k)
            }
            drawDomino(offset + Offset(1f, 1f), Size(t.w * unit - 2f, t.h * unit - 2f), t.first, t.second, highlight = i == highlight)
        }
    }
}

/** Your hand as upright tiles. Tiles that fit are bright (when [enabled]); [selected] rises. */
@Composable
private fun DominoHand(hand: List<Tile>, line: List<Tile>, enabled: Boolean, selected: Int?, onTap: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hand.forEachIndexed { i, t ->
                // Tiles are all different, so each keeps its place; one just drawn slides in.
                key(t) {
                    val fits = enabled && Dominoes.fits(line, t).isNotEmpty()
                    Canvas(
                        Modifier.size(30.dp, 60.dp)
                            .enterFrom(Unit, dy = (-120).dp, scaleFrom = 0.6f, delayMs = if (line.isEmpty()) i * Motion.STAGGER_MS else 0, onAppear = true)
                            .graphicsLayer {
                                alpha = if (!enabled || fits) 1f else 0.4f
                                translationY = if (i == selected) -8.dp.toPx() else 0f
                            }
                            .clickable(enabled = fits) { onTap(i) },
                    ) { drawDomino(Offset.Zero, size, t.a, t.b, highlight = i == selected) }
                }
            }
        }
    }
}

// ---- The table, shared by battle and solo ---------------------------------------------------

/** What just happened: the last move, or how the last round ended when a new one has begun. */
private fun dominoNews(view: DominoView, name: (Char) -> String): String? {
    val move = view.lastMove
    val round = view.lastRound
    return when {
        move != null -> when {
            move.drew -> "${name(move.who)} drew a tile"
            move.passed -> "${name(move.who)} passed"
            move.tile != null -> "${name(move.who)} played ${move.tile.a}–${move.tile.b}"
            else -> null
        }
        round != null && round.winner == null -> "Blocked, and a tie: nobody scores. New round!"
        round != null && round.winner != null ->
            "${name(round.winner)} won the round" + (if (round.blocked) " (blocked)" else "") + ": ${round.points} points. New round!"
        else -> null
    }
}

/**
 * The line, your hand and your choices. Tapping a tile that fits only one end plays it; one
 * that fits both ends asks which. With nothing to play: Draw (the draw game) or Pass.
 */
@Composable
private fun ColumnScope.DominoTable(
    view: DominoView,
    myTurn: Boolean,
    enabled: Boolean,
    waitingText: String,
    name: (Char) -> String,
    onPlay: (Int, String) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
) {
    val p = AppTheme.palette
    var selected by remember(view.hand, view.line) { mutableStateOf<Int?>(null) }
    val canMove = myTurn && enabled
    TurnLine(
        when {
            !myTurn -> waitingText
            view.canPlay -> "Your turn: tap a tile to play it"
            view.draw && view.boneyard > 0 -> "Nothing fits: draw a tile"
            else -> "Nothing fits: pass"
        },
        myTurn,
        GameKind.DOMINOES.color,
    )
    dominoNews(view, name)?.let { Text(it, color = p.muted, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

    val move = view.lastMove
    val lastIndex = move?.tile?.let { if (move.end == "left") 0 else view.line.lastIndex }
    BoardArea(LINE_ASPECT) { size -> DominoLine(view.line, view.start, lastIndex, size, incoming = move?.takeIf { it.tile != null }, fromBelow = move?.who == 'm') }

    val chosen = selected
    if (canMove && chosen != null && view.line.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("◀ On the ${view.line.first().a}", { onPlay(chosen, "left") }, Modifier.weight(1f))
            SecondaryButton("On the ${view.line.last().b} ▶", { onPlay(chosen, "right") }, Modifier.weight(1f))
        }
    }
    DominoHand(view.hand, view.line, canMove, chosen) { i ->
        val ends = Dominoes.fits(view.line, view.hand[i])
        // Both ends showing the same number is no real choice.
        val same = view.line.isNotEmpty() && view.line.first().a == view.line.last().b
        if (ends.size == 1 || same) onPlay(i, ends.last()) else selected = if (selected == i) null else i
    }
    if (canMove && !view.canPlay) {
        if (view.draw && view.boneyard > 0) {
            PrimaryButton("Draw a tile (${view.boneyard} left)", onDraw, Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Pass", onPass, Modifier.fillMaxWidth())
        }
    }
}

/** The line under each chip: partner, tiles in hand and points. */
private fun dominoDetail(view: DominoView, mark: Char): String {
    val partner = if (mark in view.partners) "Partner · " else ""
    return partner + "${view.counts[mark] ?: 0} tiles · ${view.scores[mark] ?: 0} pts"
}

private fun dominoSubtitle(view: DominoView) =
    "Round ${view.roundNo} · first to ${view.target}" + (if (view.draw) " · ${view.boneyard} to draw" else " · block") +
        (if (view.teams) " · teams" else "")

// ---- Against your partners ------------------------------------------------------------------

/** Dominoes against your partners, two to four of you. */
@Composable
fun DominoesGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val room = state.room ?: return
    val turns = room.turns ?: return
    val view = turns.dominoes ?: return
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me" && !finished
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.DOMINOES) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    fun name(mark: Char) = if (mark == 'm') "You" else room.others.getOrNull("opq".indexOf(mark))?.name ?: "Someone"
    val turnName = turns.turnName ?: room.opponent?.name

    GameFrame(
        title = "Dominoes",
        subtitle = dominoSubtitle(view) + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, MyDisc,
            detail = { player, isMe -> dominoDetail(view, if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0))) },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> markColor(letterOf(i)) },
        ),
        glow = GameKind.DOMINOES.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.DOMINOES,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "The other player"),
                when (room.endReason) {
                    "points" -> if (room.winner == "me") "You reached ${view.target} points!" else "${room.winnerName} reached ${view.target} points."
                    "left" -> "A player left, so the hands can't be played out."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = listOf("You ${view.scores['m'] ?: 0}") + room.others.mapIndexed { i, o -> "${o.name} ${view.scores[letterOf(i)] ?: 0}" },
            )
        },
    ) {
        val waitingOn = room.others.firstOrNull { it.name == turnName }
        DominoTable(
            view, myTurn, enabled = !state.firing,
            waitingText = if (waitingOn?.online == false) "${turnName} is away. They'll get a notification." else "${turnName ?: "The other player"} is thinking…",
            name = ::name,
            onPlay = vm::playTile, onDraw = vm::drawTile, onPass = vm::passTurn,
        )
    }
}

/** The host's rules for the next match; everyone else sees what they are. */
@Composable
fun DominoOptionsCard(room: RoomState, busy: Boolean, onChange: (DominoOptions) -> Unit) {
    val p = AppTheme.palette
    val o = room.options ?: DominoOptions()
    AppCard("Rules") {
        if (room.setsOptions) {
            DominoOptionChips(o, fourPlayers = room.present.size == 3, enabled = !busy, onChange = onChange)
        } else {
            Text(dominoRulesLine(o), color = p.muted, fontSize = TextSize.Body)
        }
    }
}

private fun dominoRulesLine(o: DominoOptions) =
    (if (o.draw) "Draw from the boneyard" else "Block game: pass when stuck") + " · first to ${o.target}" +
        (if (o.teams) " · two against two" else "")

/** Chips for drawing or blocking, the points target and (four players) teams. */
@Composable
private fun DominoOptionChips(o: DominoOptions, fourPlayers: Boolean, enabled: Boolean, onChange: (DominoOptions) -> Unit) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = o.draw, onClick = { onChange(o.copy(draw = true)) }, enabled = enabled, label = { Text("Draw") })
        FilterChip(selected = !o.draw, onClick = { onChange(o.copy(draw = false)) }, enabled = enabled, label = { Text("Block") })
    }
    Text(
        if (o.draw) "Stuck? Take tiles from the boneyard until one fits." else "Stuck? Pass. Nobody can play: the lightest hand wins the round.",
        color = p.muted, fontSize = TextSize.Body,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DominoOptions.TARGETS.forEach { t ->
            FilterChip(selected = o.target == t, onClick = { onChange(o.copy(target = t)) }, enabled = enabled, label = { Text("To $t") })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !o.teams, onClick = { onChange(o.copy(teams = false)) }, enabled = enabled, label = { Text("Each for themselves") })
        FilterChip(selected = o.teams, onClick = { onChange(o.copy(teams = true)) }, enabled = enabled, label = { Text("2 v 2") })
    }
    if (o.teams && !fourPlayers) Text("Teams need four players; with fewer everyone plays for themselves.", color = p.muted, fontSize = TextSize.Body)
}

// ---- On your own ----------------------------------------------------------------------------

/** Dominoes against one to three computer players. */
@Composable
fun SoloDominoesScreen(state: SoloDominoesUiState, vm: SoloDominoesViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.DOMINOES) { showRules = false }
    val view = state.view
    val result = state.result
    fun name(mark: Char) = if (mark == 'm') "You" else "Computer " + ("opq".indexOf(mark) + 1)
    GameFrame(
        title = "Dominoes",
        subtitle = view?.let(::dominoSubtitle) ?: "Against the computer",
        players = if (view == null) emptyList() else view.counts.keys.sortedBy { "mopq".indexOf(it) }.map { mark ->
            ChipPlayer(name(mark), null, markColor(mark), dominoDetail(view, mark), active = result == null && state.turn == mark)
        },
        glow = GameKind.DOMINOES.color,
        dock = soloDock(onHome, { showRules = true }, if (view != null) vm::changeRules else null),
        scrollable = view == null,
        popup = if (view == null || result == null) null else popupContent {
            SoloPopup(
                GameKind.DOMINOES, result,
                if (result == "me") "You reached ${view.target} points!" else "The computer reached ${view.target} points.",
                onAgain = vm::start, onHome = onHome, finishLabel = "Change rules", onFinish = vm::changeRules,
            )
        },
    ) {
        if (view == null) {
            AppCard("Set up the table") {
                Text("Computer players", color = p.text, fontSize = TextSize.BodyL)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { n -> FilterChip(selected = state.opponents == n, onClick = { vm.setOpponents(n) }, label = { Text("$n") }) }
                }
                DominoOptionChips(state.options, fourPlayers = state.opponents == 3, enabled = true, onChange = vm::setOptions)
                if (state.options.teams && state.opponents == 3) Text("Your partner is Computer 2, sitting opposite.", color = p.muted, fontSize = TextSize.Body)
                PrimaryButton("Deal", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            DominoTable(
                view, myTurn = state.turn == 'm' && result == null, enabled = true,
                waitingText = "${name(state.turn)} is thinking…",
                name = ::name,
                onPlay = vm::playTile, onDraw = vm::drawTile, onPass = vm::passTurn,
            )
        }
    }
}
