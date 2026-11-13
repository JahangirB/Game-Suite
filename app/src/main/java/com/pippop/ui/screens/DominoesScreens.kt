package com.pippop.ui.screens

import com.pippop.ui.startWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.pippop.ui.Sound
import com.pippop.ui.rememberSfx
import com.pippop.ui.Sfx
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.loopingFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.dominoes.DominoLayout
import com.pippop.game.dominoes.DominoOptions
import com.pippop.game.dominoes.DominoView
import com.pippop.game.dominoes.Dominoes
import com.pippop.game.dominoes.Tile
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloDominoesUiState
import com.pippop.ui.SoloDominoesViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme

private val Felt = Board.Felt
private val Bone = Board.Bone
private val Ink = Board.Ink
private val Glow = Board.Glow
private val Brass = Board.Brass
private val Wood = Board.Wood

/** The line board's width per unit of height. */
private const val LINE_ASPECT = 1.25f

// ---- Drawing -------------------------------------------------------------------------------

/**
 * One ivory tile in [topLeft]/[size]: [a] on the left (or top, when upright) and [b] on the
 * other half. It shades softly from top to bottom, casts a shadow, and has a groove with a
 * brass pin between the halves.
 */
private fun DrawScope.drawDomino(topLeft: Offset, size: Size, a: Int, b: Int, highlight: Boolean = false, soft: Boolean = false) {
    val r = minOf(size.width, size.height) * 0.18f
    boxShadow(topLeft, size, r, drop = 1.5.dp.toPx(), strength = 0.3f)
    drawRoundRect(
        Brush.verticalGradient(listOf(Bone, Bone.darker(0.1f)), startY = topLeft.y, endY = topLeft.y + size.height),
        topLeft, size, CornerRadius(r),
    )
    drawRoundRect(if (highlight) Glow else Ink, topLeft, size, CornerRadius(r), style = Stroke(if (highlight) 3.dp.toPx() else 1.5.dp.toPx()))
    // A tile that can be played also has a thin soft gold edge just inside its outline.
    if (soft && !highlight) {
        val inset = 1.5.dp.toPx() + 1.dp.toPx()
        drawRoundRect(Board.GlowSoft, topLeft + Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius((r - inset).coerceAtLeast(0f)), style = Stroke(2.dp.toPx()))
    }
    val across = size.width > size.height
    val half = if (across) size.width / 2 else size.height / 2
    val inset = minOf(size.width, size.height) * 0.2f
    val mid = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)
    val rim = 1.dp.toPx()
    // The groove: a dark line with a light edge below it, as if cut into the ivory.
    if (across) {
        val from = Offset(topLeft.x + half, topLeft.y + inset)
        val to = Offset(topLeft.x + half, topLeft.y + size.height - inset)
        drawLine(Color.White.copy(alpha = 0.8f), from + Offset(rim, 0f), to + Offset(rim, 0f), 1.2.dp.toPx())
        drawLine(Ink, from, to, 1.2.dp.toPx())
        pips(Offset(topLeft.x + half / 2, topLeft.y + size.height / 2), size.height, a)
        pips(Offset(topLeft.x + half * 1.5f, topLeft.y + size.height / 2), size.height, b)
    } else {
        val from = Offset(topLeft.x + inset, topLeft.y + half)
        val to = Offset(topLeft.x + size.width - inset, topLeft.y + half)
        drawLine(Color.White.copy(alpha = 0.8f), from + Offset(0f, rim), to + Offset(0f, rim), 1.2.dp.toPx())
        drawLine(Ink, from, to, 1.2.dp.toPx())
        pips(Offset(topLeft.x + size.width / 2, topLeft.y + half / 2), size.width, a)
        pips(Offset(topLeft.x + size.width / 2, topLeft.y + half * 1.5f), size.width, b)
    }
    // The brass pin, with a bright dot on its top left.
    val pin = minOf(size.width, size.height) * 0.07f
    drawCircle(Brass, pin, mid)
    drawCircle(Brass.lighter(0.6f), pin * 0.4f, mid + Offset(-pin * 0.3f, -pin * 0.3f))
}

/** [n] drilled pips on a square half [side] wide, centred on [c]. */
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
    // Each pip looks drilled: dark, with a faint light rim just below it.
    spots.forEach { (x, y) ->
        val at = Offset(c.x + x * d, c.y + y * d)
        drawCircle(Color.White.copy(alpha = 0.85f), side * 0.085f, at + Offset(0f, side * 0.03f))
        drawCircle(Ink, side * 0.085f, at)
    }
}

/**
 * The line of play on the table, laid out like a real one: the round's first tile ([start]
 * in the line) in the middle, doubles across, turning near the edges, all scaled to fit. The
 * tile at [highlight] (the last one played) has a gold edge and slides in when [incoming]
 * changes: up from your hand ([fromBelow]) or down from the other side of the table. Each open
 * end has a gold ring with the number that fits there, pulsing gently on [myTurn].
 */
@Composable
fun DominoLine(line: List<Tile>, start: Int, highlight: Int?, modifier: Modifier = Modifier, incoming: Any? = null, fromBelow: Boolean = true, myTurn: Boolean = false) {
    val shape = Shapes.L
    val beat by loopingFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), still = 1f, label = "ends")
    val digits = rememberTextMeasurer()
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
    Canvas(modifier.clip(shape).border(6.dp, Brush.verticalGradient(listOf(Wood.lighter(0.2f), Wood.darker(0.2f))), shape)) {
        // Green felt, darker towards the edge.
        drawRect(Brush.radialGradient(0f to Felt, 0.55f to Felt, 1f to Felt.darker(0.4f), center = center, radius = maxOf(size.width, size.height) * 0.75f))
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
        // A ring just beyond each open end of the line, showing the number that fits there.
        val ringR = maxOf(unit * 0.6f, 11.dp.toPx())
        val grow = if (myTurn) 1f + 0.1f * beat else 1f
        val glow = if (myTurn) 0.6f + 0.4f * beat else 1f
        fun ring(i: Int, neighbour: Int?, outward: Float, number: Int) {
            val t = placed[i]
            val c = Offset(left + (t.x + t.w / 2) * unit, top + (t.y + t.h / 2) * unit)
            val dir = if (neighbour == null) Offset(outward, 0f) else {
                val n = placed[neighbour]
                val dx = c.x - (left + (n.x + n.w / 2) * unit)
                val dy = c.y - (top + (n.y + n.h / 2) * unit)
                if (abs(dx) >= abs(dy)) Offset(if (dx < 0) -1f else 1f, 0f) else Offset(0f, if (dy < 0) -1f else 1f)
            }
            val along = (if (dir.x != 0f) t.w else t.h) * unit
            val edge = 8.dp.toPx()
            val ideal = c + dir * (along / 2 + ringR + 2.dp.toPx())
            // The full ring only where it fits on the felt, clear of the wooden rim and the tile;
            // otherwise a small badge on the tile's outer half.
            val most = ringR * 1.1f + 1.25.dp.toPx()
            val fits = ideal.x in (most + edge)..(size.width - most - edge) && ideal.y in (most + edge)..(size.height - most - edge)
            val at = if (fits) ideal else c + dir * (along / 4)
            val r = (if (fits) ringR else unit * 0.45f) * grow
            drawCircle(Felt.darker(0.45f).copy(alpha = 0.7f), r, at)
            drawCircle(Glow.copy(alpha = glow), r, at, style = Stroke(2.5.dp.toPx()))
            val text = digits.measure("$number", TextStyle(fontSize = TextSize.Label, fontWeight = FontWeight.Bold))
            drawText(text, Color.White, at - Offset(text.size.width / 2f, text.size.height / 2f))
        }
        ring(0, if (placed.size > 1) 1 else null, -1f, line.first().a)
        ring(placed.lastIndex, if (placed.size > 1) placed.lastIndex - 1 else null, 1f, line.last().b)
    }
}

/** Your hand as upright tiles. Tiles that fit stand up with a soft gold edge (when [enabled]), the rest fade; [selected] rises higher with the full gold edge. */
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
                                translationY = if (i == selected) -8.dp.toPx() else if (fits) -5.dp.toPx() else 0f
                            }
                            .clickable(enabled = fits) { onTap(i) },
                    ) { drawDomino(Offset.Zero, size, t.a, t.b, highlight = i == selected, soft = fits) }
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
internal fun ColumnScope.DominoTable(
    view: DominoView,
    myTurn: Boolean,
    enabled: Boolean,
    waitingText: String,
    name: (Char) -> String,
    onPlay: (Int, String) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
    /** Draw and Pass as buttons under the hand; against your partner they're in the dock. */
    buttons: Boolean = true,
) {
    val p = AppTheme.palette
    var selected by remember(view.hand, view.line) { mutableStateOf<Int?>(null) }
    val canMove = myTurn && enabled
    TurnLine(
        when {
            !myTurn -> waitingText
            view.canPlay -> {
                val n = view.hand.count { Dominoes.fits(view.line, it).isNotEmpty() }
                if (n == 1) "Your turn: 1 tile fits, tap it" else "Your turn: $n tiles fit, tap one"
            }
            view.draw && view.boneyard > 0 -> "Nothing fits: draw a tile"
            else -> "Nothing fits: pass"
        },
        myTurn,
        GameKind.DOMINOES.color,
    )
    dominoNews(view, name)?.let { Text(it, color = p.muted, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

    val move = view.lastMove
    val lastIndex = move?.tile?.let { if (move.end == "left") 0 else view.line.lastIndex }
    BoardArea(LINE_ASPECT) { size -> DominoLine(view.line, view.start, lastIndex, size, incoming = move?.takeIf { it.tile != null }, fromBelow = move?.who == 'm', myTurn = canMove) }

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
    if (buttons && canMove && !view.canPlay) {
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
    if (showRules) RulesSheet(GameKind.DOMINOES) { showRules = false }
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
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        myTurn = myTurn,
        tools = run {
            // Only when nothing fits: Draw while the boneyard lasts (in the draw game), else Pass.
            val stuck = myTurn && !state.firing && !view.canPlay
            val canDraw = view.draw && view.boneyard > 0
            listOf(
                DockTool(if (view.draw) "Draw (${view.boneyard})" else "Draw", GameIcons.Cards, anytime = false, enabled = stuck && canDraw, onClick = vm::drawTile),
                DockTool("Pass", Icons.AutoMirrored.Filled.ArrowForward, anytime = false, enabled = stuck && !canDraw, onClick = vm::passTurn),
            )
        },
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
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
            buttons = false,
        )
    }
}

/** The host's rules for the next match; everyone else sees what they are. */
@Composable
fun DominoOptionsCard(room: RoomState, busy: Boolean, onChange: (DominoOptions) -> Unit) {
    val o = room.options ?: DominoOptions()
    OptionsCard(room, "Rules", busy) { enabled ->
        DominoOptionChips(o, fourPlayers = room.present.size == 3, enabled = enabled, onChange = onChange)
    }
}

/** [OptionRow]s for drawing or blocking, the points target and (four players) teams. */
@Composable
private fun DominoOptionChips(o: DominoOptions, fourPlayers: Boolean, enabled: Boolean, onChange: (DominoOptions) -> Unit) {
    val p = AppTheme.palette
    OptionRow(
        "When stuck", listOf(true to "Draw", false to "Block"), o.draw, { onChange(o.copy(draw = it)) }, enabled = enabled,
        note = if (o.draw) "Take tiles from the boneyard until one fits." else "Pass. If nobody can play, the lightest hand wins the round.",
    )
    OptionRow("Play to", DominoOptions.TARGETS.map { it to "$it" }, o.target, { onChange(o.copy(target = it)) }, enabled = enabled)
    OptionRow("Sides", listOf(false to "Each alone", true to "2 v 2"), o.teams, { onChange(o.copy(teams = it)) }, enabled = enabled)
    if (o.teams && !fourPlayers) Text("Teams need four players; with fewer everyone plays for themselves.", color = p.muted, fontSize = TextSize.Body)
}

// ---- On your own ----------------------------------------------------------------------------

/** Dominoes against one to three computer players. */
@Composable
fun SoloDominoesScreen(state: SoloDominoesUiState, vm: SoloDominoesViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.DOMINOES) { showRules = false }
    val view = state.view
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    if (view == null) { BackToHome(onHome); return }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.DOMINOES, "New game", vm::startWith) { changing = false }
    val result = state.result
    fun name(mark: Char) = if (mark == 'm') "You" else "Computer " + ("opq".indexOf(mark) + 1)
    GameFrame(
        title = "Dominoes",
        subtitle = dominoSubtitle(view),
        players = view.counts.keys.sortedBy { "mopq".indexOf(it) }.map { mark ->
            ChipPlayer(name(mark), null, markColor(mark), dominoDetail(view, mark), active = result == null && state.turn == mark)
        },
        glow = GameKind.DOMINOES.color,
        menu = soloMenu(GameKind.DOMINOES, onHome, { showRules = true }, vm::start, { changing = true }),
        popup = if (result == null) null else popupContent {
            SoloPopup(
                GameKind.DOMINOES, result,
                if (result == "me") "You reached ${view.target} points!" else "The computer reached ${view.target} points.",
                onAgain = vm::start, onHome = onHome, finishLabel = "Change rules", onFinish = { changing = true },
            )
        },
    ) {
        DominoTable(
            view, myTurn = state.turn == 'm' && result == null, enabled = true,
            waitingText = "${name(state.turn)} is thinking…",
            name = ::name,
            onPlay = vm::playTile, onDraw = vm::drawTile, onPass = vm::passTurn,
        )
    }
}
