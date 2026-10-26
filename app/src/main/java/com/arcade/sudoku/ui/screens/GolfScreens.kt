package com.arcade.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.golf.Box
import com.arcade.sudoku.game.golf.Golf
import com.arcade.sudoku.game.golf.GolfView
import com.arcade.sudoku.game.golf.Pt
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloGolfUiState
import com.arcade.sudoku.ui.SoloGolfViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val Grass = Color(0xFF5DBB63)
private val GrassDark = Color(0xFF4CA552)
private val Sand = Color(0xFFF2D68A)
private val Wood = Color(0xFF8B5E34)

/**
 * The hole being played, with everyone's ball ([current] waits on the tee for its first shot).
 * A new last shot rolls along its path before the table settles; on [canShoot], pull back from
 * anywhere like a slingshot and let go to shoot.
 */
@Composable
fun GolfCourse(view: GolfView, current: Char?, canShoot: Boolean, onShoot: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val ink = AppTheme.palette.ink
    // The shot on screen: its path and how far along it is. Shots already played when the
    // screen opened don't roll again.
    var seen by remember { mutableStateOf(view.last?.seq ?: 0) }
    var rolling by remember { mutableStateOf<Pair<Int, List<Pt>>?>(null) }
    val progress = remember { Animatable(1f) }
    val last = view.last
    LaunchedEffect(last?.seq) {
        if (last == null || last.seq <= seen) return@LaunchedEffect
        seen = last.seq
        val hole = Golf.HOLES.getOrNull(last.hole) ?: return@LaunchedEffect
        val path = Golf.simulate(hole, last.from ?: hole.tee, last.angle, last.power).path.toMutableList()
        // The shooter's phone said where it stopped; end there whatever this phone worked out.
        path[path.size - 1] = last.to
        rolling = last.hole to path
        progress.snapTo(0f)
        progress.animateTo(1f, tween((path.size * 1000 / 60).coerceAtLeast(200), easing = LinearEasing))
        rolling = null
    }
    var aim by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val roll = rolling
    val holeIndex = roll?.first ?: view.hole
    val hole = Golf.HOLES[holeIndex.coerceIn(0, Golf.HOLES.size - 1)]
    val myBall = view.balls['m'] ?: hole.tee
    val shooting = canShoot && roll == null && view.done['m'] != true

    Canvas(
        modifier.then(
            if (!shooting) Modifier else Modifier.pointerInput(view.hole, view.last?.seq) {
                var start = Offset.Zero
                detectDragGestures(
                    onDragStart = { start = it },
                    onDragEnd = {
                        aim?.let { (a, power) -> if (power >= 0.04) onShoot(a, power) }
                        aim = null
                    },
                    onDragCancel = { aim = null },
                    onDrag = { change, _ ->
                        val pull = start - change.position
                        val scale = size.width / Golf.W
                        aim = atan2(pull.y.toDouble(), pull.x.toDouble()) to (hypot(pull.x, pull.y) / (scale * 0.6f)).toDouble().coerceAtMost(1.0)
                    },
                )
            },
        ),
    ) {
        val s = size.width / Golf.W.toFloat()
        fun o(p: Pt) = Offset((p.x * s).toFloat(), (p.y * s).toFloat())
        fun box(b: Box, color: Color) {
            drawRoundRect(color, Offset((b.x0 * s).toFloat(), (b.y0 * s).toFloat()), Size(((b.x1 - b.x0) * s).toFloat(), ((b.y1 - b.y0) * s).toFloat()), CornerRadius(s * 0.012f))
        }
        drawRoundRect(Grass, cornerRadius = CornerRadius(s * 0.04f))
        // Mowing stripes.
        for (i in 0 until 8) if (i % 2 == 0) drawRect(GrassDark, Offset(0f, size.height * i / 8), Size(size.width, size.height / 8))
        drawRoundRect(Wood, style = Stroke(s * 0.02f), cornerRadius = CornerRadius(s * 0.04f))
        hole.sand.forEach { box(it, Sand) }
        hole.walls.forEach { b ->
            box(b, Wood)
            drawRoundRect(ink, Offset((b.x0 * s).toFloat(), (b.y0 * s).toFloat()), Size(((b.x1 - b.x0) * s).toFloat(), ((b.y1 - b.y0) * s).toFloat()), CornerRadius(s * 0.012f), style = Stroke(s * 0.005f))
        }
        // Tee, cup and flag.
        drawRoundRect(Color.White.copy(alpha = 0.5f), o(Pt(hole.tee.x - 0.05, hole.tee.y - 0.02)), Size(s * 0.1f, s * 0.04f), CornerRadius(s * 0.01f))
        val cup = o(hole.cup)
        drawCircle(Color(0xFF1B1B1B), (Golf.CUP * s).toFloat(), cup)
        drawLine(ink, cup, cup + Offset(0f, -s * 0.16f), s * 0.008f)
        drawPath(Path().apply { moveTo(cup.x, cup.y - s * 0.16f); lineTo(cup.x + s * 0.09f, cup.y - s * 0.13f); lineTo(cup.x, cup.y - s * 0.1f); close() }, Color(0xFFE8505B))

        fun ball(p: Pt, mark: Char) {
            val c = o(p)
            val r = (Golf.R * s * 1.15).toFloat()
            drawCircle(Color.Black.copy(alpha = 0.18f), r, c + Offset(r * 0.25f, r * 0.3f))
            drawCircle(markColor(mark), r, c)
            drawCircle(Color.White.copy(alpha = 0.55f), r * 0.35f, c + Offset(-r * 0.3f, -r * 0.3f))
            drawCircle(ink, r, c, style = Stroke(s * 0.004f))
        }
        val shooter = view.last?.who
        if (roll != null && shooter != null) {
            val path = roll.second
            if (roll.first == view.hole) {
                view.balls.forEach { (m, p) -> if (m != shooter && p != null && view.done[m] != true) ball(p, m) }
            }
            ball(path[(progress.value * (path.size - 1)).toInt().coerceIn(0, path.size - 1)], shooter)
        } else {
            view.balls.forEach { (m, p) -> if (p != null && view.done[m] != true) ball(p, m) }
            if (current != null && view.balls[current] == null && view.done[current] != true) ball(hole.tee, current)
        }
        aim?.let { (a, power) -> aimLine(o(myBall), a, power, s) }
    }
}

private fun DrawScope.aimLine(from: Offset, angle: Double, power: Double, s: Float) {
    val len = (0.1 + power * 0.4).toFloat() * s
    val to = from + Offset((cos(angle) * len).toFloat(), (sin(angle) * len).toFloat())
    val heat = Color(red = min(1f, 0.3f + power.toFloat()), green = min(1f, 1.2f - power.toFloat()), blue = 0.2f)
    drawLine(Color.White, from, to, s * 0.014f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(s * 0.02f, s * 0.018f)))
    drawCircle(heat, s * 0.018f, to)
}

/** "Hole 3 of 9 · par 3" and everyone's total so far. */
@Composable
private fun GolfHeader(view: GolfView, names: List<Pair<Char, String>>) {
    val p = AppTheme.palette
    val hole = Golf.HOLES[view.hole]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Hole ${view.hole + 1} of ${view.holes} · par ${hole.par}", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(names.joinToString("  ") { (m, n) -> "$n ${view.total(m)}" }, color = p.muted, fontSize = 13.sp)
    }
}

/** Scores per player for the result popup: total and against par. */
private fun golfScores(view: GolfView, names: List<Pair<Char, String>>): List<String> {
    val par = Golf.HOLES.sumOf { it.par }
    return names.map { (m, n) ->
        val t = view.total(m)
        val vs = t - par
        "$n $t (${if (vs > 0) "+$vs" else if (vs == 0) "par" else "$vs"})"
    }
}

// ---- Against others ---------------------------------------------------------------------------

@Composable
fun GolfGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val view = turns.golf ?: return
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me" && !finished
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GOLF) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    val turnName = turns.turnName ?: room.opponent?.name
    val current = if (myTurn) 'm' else room.others.indexOfFirst { it.name == turnName }.takeIf { it >= 0 }?.let(::letterOf)
    val names = listOf('m' to "You") + room.others.mapIndexed { i, o -> letterOf(i) to o.name }

    GameFrame(
        title = "Mini Golf",
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, MyDisc,
            detail = { player, isMe ->
                val mark = if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0))
                "${view.strokes[mark]?.getOrNull(view.hole) ?: 0} this hole"
            },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> markColor(letterOf(i)) },
        ),
        glow = GameKind.GOLF.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.GOLF,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "Your opponent"),
                when (room.endReason) {
                    "strokes" -> "Nine holes played: the fewest strokes wins."
                    "draw" -> "Level after nine holes."
                    "left" -> "A player left, so the game stopped."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = golfScores(view, names),
            )
        },
    ) {
        GolfHeader(view, names)
        TurnLine(
            when {
                finished -> "Round over"
                view.done['m'] == true -> "You're in! Waiting for the others…"
                myTurn -> "Your shot: pull back and let go"
                else -> "${turnName ?: "Someone"} is shooting…"
            },
            myTurn, GameKind.GOLF.color,
        )
        BoardArea((Golf.W / Golf.H).toFloat()) { size -> GolfCourse(view, current, myTurn && !state.firing, vm::shootGolf, size) }
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloGolfScreen(state: SoloGolfUiState, vm: SoloGolfViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GOLF) { showRules = false }
    val view = state.view
    val names = listOf('m' to "You") + (1..state.computers).map { "opq"[it - 1] to "CPU $it" }
    GameFrame(
        title = "Mini Golf",
        subtitle = if (state.computers == 0) "On your own" + (state.best?.let { " · best $it" } ?: "") else "Against ${state.computers} computer${if (state.computers > 1) "s" else ""}",
        players = emptyList(),
        glow = GameKind.GOLF.color,
        dock = soloDock(onHome, { showRules = true }, if (view != null) vm::start else null),
        scrollable = view == null,
        popup = if (view == null || state.result == null) null else popupContent {
            SoloPopup(
                GameKind.GOLF, state.result,
                golfScores(view, names).joinToString(" · "),
                onAgain = vm::start, onHome = onHome, finishLabel = "Change players", onFinish = vm::changeSetup,
            )
        },
    ) {
        if (view == null) {
            AppCard("Nine holes") {
                Text("Pull back from the ball like a slingshot and let go: the further you pull, the harder the shot. Fewest strokes over nine holes wins.", color = p.muted, fontSize = 14.sp)
                Text("Computers", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 0..3) {
                        val label = if (n == 0) "None" else "$n"
                        if (state.computers == n) PrimaryButton(label, {}, Modifier.weight(1f)) else SecondaryButton(label, { vm.setComputers(n) }, Modifier.weight(1f))
                    }
                }
                if (state.computers > 0) {
                    Text("How good", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Easy", "Medium", "Hard").forEachIndexed { i, label ->
                            if (state.skill == i) PrimaryButton(label, {}, Modifier.weight(1f)) else SecondaryButton(label, { vm.setSkill(i) }, Modifier.weight(1f))
                        }
                    }
                }
                PrimaryButton("Tee off", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            GolfHeader(view, names)
            val myTurn = state.turn == 'm' && state.result == null
            TurnLine(
                when {
                    state.result != null -> "Round over"
                    myTurn -> "Your shot: pull back and let go"
                    else -> "${names.firstOrNull { it.first == state.turn }?.second ?: "The computer"} is shooting…"
                },
                myTurn, GameKind.GOLF.color,
            )
            BoardArea((Golf.W / Golf.H).toFloat()) { size -> GolfCourse(view, state.turn, myTurn, vm::shoot, size) }
        }
    }
}
