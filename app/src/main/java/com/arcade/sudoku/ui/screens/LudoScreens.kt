package com.arcade.sudoku.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.ludo.Ludo
import com.arcade.sudoku.game.ludo.LudoView
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloLudoUiState
import com.arcade.sudoku.ui.SoloLudoViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import kotlin.math.hypot

/** A Ludo colour (0 red, 1 green, 2 yellow, 3 blue). */
fun ludoColor(c: Int): Color = when (c) {
    0 -> Color(0xFFE8505B)
    1 -> Color(0xFF20A47B)
    2 -> Color(0xFFFFC93C)
    else -> Color(0xFF3D7BF7)
}

private val Ink = Color(0xFF2B2440)

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = -Math.PI / 2 + i * Math.PI / 5
        val x = c.x + rr * kotlin.math.cos(a).toFloat()
        val y = c.y + rr * kotlin.math.sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

/**
 * The classic cross board. Your [movable] tokens bob; tapping one calls [onToken] with its
 * index. Tokens sharing a square sit side by side.
 */
@Composable
fun LudoBoard(view: LudoView, onToken: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val bob by rememberInfiniteTransition(label = "ludo").animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "bob")
    val tap by rememberUpdatedState(onToken)
    val current by rememberUpdatedState(view)
    val touch = if (onToken == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures { pos ->
            val v = current
            val cell = size.width / 15f
            val colour = v.colours['m'] ?: return@detectTapGestures
            val mine = v.tokens['m'].orEmpty()
            // The movable token nearest the tap, if close enough.
            val hit = v.movable.minByOrNull { i ->
                val (x, y) = Ludo.cellOf(colour, mine[i], i)
                hypot(x * cell - pos.x, y * cell - pos.y)
            }
            if (hit != null) {
                val (x, y) = Ludo.cellOf(colour, mine[hit], hit)
                if (hypot(x * cell - pos.x, y * cell - pos.y) < cell * 1.6f) tap?.invoke(hit)
            }
        }
    }
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).then(touch)) {
        val cell = size.width / 15f
        fun sq(x: Int, y: Int, color: Color) {
            drawRect(color, Offset(x * cell, y * cell), Size(cell, cell))
            drawRect(Ink.copy(alpha = 0.25f), Offset(x * cell, y * cell), Size(cell, cell), style = Stroke(1f))
        }
        drawRect(Color.White)
        // Yards in the corners.
        for (c in 0..3) {
            val (x, y) = Ludo.yardCorner(c)
            drawRect(ludoColor(c), Offset(x * cell, y * cell), Size(cell * 6, cell * 6))
            drawRect(Color.White, Offset((x + 0.8f) * cell, (y + 0.8f) * cell), Size(cell * 4.4f, cell * 4.4f))
            for (k in 0 until 4) {
                val (cx, cy) = Ludo.cellOf(c, -1, k)
                drawCircle(ludoColor(c).copy(alpha = 0.3f), cell * 0.55f, Offset(cx * cell, cy * cell))
            }
        }
        // The ring: starts in their colour, stars on the safe squares.
        Ludo.ringCells.forEachIndexed { i, (x, y) ->
            val start = i % 13 == 0
            sq(x, y, if (start) ludoColor(i / 13).copy(alpha = 0.75f) else Color.White)
            if (i % 13 == 8) drawStar(Offset((x + 0.5f) * cell, (y + 0.5f) * cell), cell * 0.38f, Ink.copy(alpha = 0.3f))
        }
        for (c in 0..3) for (k in 0 until 5) {
            val (x, y) = Ludo.homeCell(c, k)
            sq(x, y, ludoColor(c).copy(alpha = 0.6f))
        }
        // The centre: four triangles, one per colour.
        val mid = Offset(7.5f * cell, 7.5f * cell)
        val corners = listOf(Offset(6 * cell, 9 * cell), Offset(6 * cell, 6 * cell), Offset(9 * cell, 6 * cell), Offset(9 * cell, 9 * cell))
        for (c in 0..3) {
            val path = Path().apply {
                moveTo(corners[c].x, corners[c].y)
                lineTo(corners[(c + 1) % 4].x, corners[(c + 1) % 4].y)
                lineTo(mid.x, mid.y)
                close()
            }
            drawPath(path, ludoColor(c))
        }
        // Tokens: stacks on one square spread out a little.
        val all = view.tokens.flatMap { (who, list) -> list.mapIndexed { i, prog -> Triple(who, i, prog) } }
        val placed = mutableMapOf<Pair<Float, Float>, Int>()
        for ((who, i, prog) in all) {
            val colour = view.colours[who] ?: continue
            val (x, y) = Ludo.cellOf(colour, prog, i)
            val n = placed[x to y] ?: 0
            placed[x to y] = n + 1
            val shift = if (prog in 0 until Ludo.HOME) n * cell * 0.18f else 0f
            val lift = if (who == 'm' && i in view.movable) -cell * 0.18f * bob else 0f
            val at = Offset(x * cell + shift, y * cell + shift + lift)
            drawCircle(Ink, cell * 0.38f, at)
            drawCircle(ludoColor(colour), cell * 0.31f, at)
            drawCircle(Color.White.copy(alpha = 0.45f), cell * 0.12f, Offset(at.x - cell * 0.08f, at.y - cell * 0.08f))
            if (who == 'm' && i in view.movable) drawCircle(p.ink, cell * 0.46f, at, style = Stroke(cell * 0.07f))
        }
    }
}

/** A die face showing [n]. */
@Composable
private fun DieFace(n: Int?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, p.ink), modifier = modifier.size(52.dp)) {
        Canvas(Modifier.size(52.dp)) {
            if (n == null) return@Canvas
            val d = size.width * 0.25f
            val c = Offset(size.width / 2, size.height / 2)
            val spots = when (n) {
                1 -> listOf(0 to 0)
                2 -> listOf(-1 to -1, 1 to 1)
                3 -> listOf(-1 to -1, 0 to 0, 1 to 1)
                4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
                5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
                else -> listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1)
            }
            spots.forEach { (x, y) -> drawCircle(Ink, size.width * 0.08f, Offset(c.x + x * d, c.y + y * d)) }
        }
    }
}

private fun ludoNews(view: LudoView, name: (Char) -> String): String? {
    val m = view.lastMove ?: return null
    val who = name(m.who)
    return when {
        m.thirdSix -> "$who rolled a third 6: turn over."
        m.stuck -> "$who rolled ${m.roll}: no move."
        m.to != null && m.captured.isNotEmpty() -> "$who rolled ${m.roll} and knocked ${m.captured.joinToString(" and ") { name(it) }} back!"
        m.to == Ludo.HOME -> "$who rolled ${m.roll}: a token is home!"
        m.from == -1 -> "$who rolled a 6 and brought a token out."
        m.to != null -> "$who rolled ${m.roll}."
        else -> "$who rolled ${m.roll}."
    }
}

@Composable
private fun ColumnScope.LudoTable(
    view: LudoView,
    myTurn: Boolean,
    enabled: Boolean,
    waitingText: String,
    name: (Char) -> String,
    onRoll: () -> Unit,
    onToken: (Int) -> Unit,
) {
    val p = AppTheme.palette
    val canMove = myTurn && enabled
    val myColour = view.colours['m'] ?: 0
    TurnLine(
        when {
            !myTurn -> waitingText
            view.roll == null -> "Your turn: roll the die"
            view.movable.size == 1 -> "You rolled ${view.roll}: tap your token"
            else -> "You rolled ${view.roll}: tap a token to move"
        },
        myTurn,
        ludoColor(myColour),
    )
    ludoNews(view, name)?.let { Text(it, color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    BoardArea(1f) { size -> LudoBoard(view, onToken = if (canMove && view.roll != null) onToken else null, modifier = size) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DieFace(view.roll ?: view.lastMove?.roll)
        PrimaryButton("Roll", onRoll, Modifier.weight(1f), enabled = canMove && view.roll == null)
    }
}

/** Tokens home out of four, for the chips. */
private fun homeCount(view: LudoView, who: Char) = view.tokens[who].orEmpty().count { it == Ludo.HOME }

// ---- Against your partners -----------------------------------------------------------------

@Composable
fun LudoGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val room = state.room ?: return
    val turns = room.turns ?: return
    val view = turns.ludo ?: return
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me" && !finished
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.LUDO) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    fun name(mark: Char) = if (mark == 'm') "You" else room.others.getOrNull("opq".indexOf(mark))?.name ?: "Someone"
    val turnName = turns.turnName ?: room.opponent?.name

    GameFrame(
        title = "Ludo",
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, ludoColor(view.colours['m'] ?: 0),
            detail = { player, isMe -> "${homeCount(view, if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0)))}/4 home" },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> ludoColor(view.colours[letterOf(i)] ?: 1) },
        ),
        glow = GameKind.LUDO.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.LUDO,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "Your opponent"),
                when (room.endReason) {
                    "home" -> if (room.winner == "me") "All four of your tokens are home!" else "${room.winnerName} got all four tokens home."
                    "left" -> "A player left, so the game stopped."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "Your opponent lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = listOf("You ${homeCount(view, 'm')}/4") + room.others.mapIndexed { i, o -> "${o.name} ${homeCount(view, letterOf(i))}/4" },
            )
        },
    ) {
        val waitingOn = room.others.firstOrNull { it.name == turnName }
        LudoTable(
            view, myTurn, enabled = !state.firing,
            waitingText = if (waitingOn?.online == false) "$turnName is away. They'll get a notification." else "${turnName ?: "Your opponent"} is playing…",
            name = ::name,
            onRoll = vm::rollDie, onToken = vm::moveToken,
        )
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloLudoScreen(state: SoloLudoUiState, vm: SoloLudoViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.LUDO) { showRules = false }
    val view = state.view
    val result = state.result
    fun name(mark: Char) = if (mark == 'm') "You" else "Computer " + ("opq".indexOf(mark) + 1)
    GameFrame(
        title = "Ludo",
        subtitle = "Against the computer",
        players = if (view == null) emptyList() else view.colours.keys.sortedBy { "mopq".indexOf(it) }.map { mark ->
            ChipPlayer(name(mark), null, ludoColor(view.colours[mark] ?: 0), "${homeCount(view, mark)}/4 home", active = result == null && state.turn == mark)
        },
        glow = GameKind.LUDO.color,
        dock = soloDock(onHome, { showRules = true }, if (view != null) vm::changeSetup else null),
        scrollable = view == null,
        popup = if (view == null || result == null) null else popupContent {
            SoloPopup(
                GameKind.LUDO, result,
                if (result == "me") "All four of your tokens are home!" else "A computer got all four tokens home first.",
                onAgain = vm::start, onHome = onHome, finishLabel = "Change players", onFinish = vm::changeSetup,
            )
        },
    ) {
        if (view == null) {
            AppCard("Set up the board") {
                Text("Roll, bring your tokens out with a 6, race them round and home. Land on others to send them back.", color = p.muted, fontSize = 14.sp)
                Text("Computer players", color = p.text, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { n -> FilterChip(selected = state.opponents == n, onClick = { vm.setOpponents(n) }, label = { Text("$n") }) }
                }
                PrimaryButton("Start", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            LudoTable(
                view, myTurn = state.turn == 'm' && result == null, enabled = true,
                waitingText = "${name(state.turn)} is playing…",
                name = ::name,
                onRoll = vm::roll, onToken = vm::moveToken,
            )
        }
    }
}
