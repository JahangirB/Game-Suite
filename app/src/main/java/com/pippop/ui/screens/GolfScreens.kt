package com.pippop.ui.screens

import com.pippop.ui.Sound
import com.pippop.ui.rememberSfx
import com.pippop.ui.Sfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.GameColors
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
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
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.data.RoomState
import com.pippop.game.golf.Box
import com.pippop.game.golf.Golf
import com.pippop.game.golf.GolfLevel
import com.pippop.game.golf.Hazard
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.Pt
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloGolfUiState
import com.pippop.ui.SoloGolfViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val Grass = GameColors.Grass
private val GrassDark = GameColors.GrassDark
private val Rim = GameColors.Rim
private val RimTop = GameColors.RimTop
private val CourseSide = GameColors.CourseSide
private val Sand = GameColors.Sand
private val SandEdge = GameColors.SandEdge
private val SkyTop = GameColors.SkyTop
private val SkyBottom = GameColors.SkyBottom
private val FlagRed = GameColors.Flag
private val Water = GameColors.Water
private val WaterEdge = GameColors.WaterEdge
private val Wood = GameColors.Wood
private val WoodDark = GameColors.WoodDark

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
    val sfx = rememberSfx()
    val progress = remember { Animatable(1f) }
    // A ball lost in the water or over the edge: what happened and where, while it sinks or falls.
    var lost by remember { mutableStateOf<Pair<Hazard, Pt>?>(null) }
    val sink = remember { Animatable(0f) }
    val last = view.last
    LaunchedEffect(last?.seq) {
        if (last == null || last.seq <= seen) return@LaunchedEffect
        seen = last.seq
        // A shot that comes in while the last lost ball is still sinking takes over from it.
        lost = null
        val hole = view.holeAt(last.hole)
        val shot = Golf.simulate(hole, last.from ?: hole.tee, last.angle, last.power)
        val path = shot.path.toMutableList()
        // The shooter's phone said where it stopped; end there whatever this phone worked out.
        // (A lost ball ends where it went in; it's back on the tee afterwards.)
        if (shot.hazard == null) path[path.size - 1] = last.to
        rolling = last.hole to path
        progress.snapTo(0f)
        val v = if (last.who == 'm') 1f else Sfx.OTHERS
        sfx.play(Sound.GOLF_PUTT, v * (0.6f + 0.4f * last.power.toFloat()))
        val sounds = launch {
            // A bonk each time the ball turns back off a wall (one path point a frame), and the cup at the end.
            var at = 0
            for (i in bounces(path)) {
                delay((i - at) * 1000L / 60)
                at = i
                sfx.play(Sound.GOLF_WALL, v)
            }
        }
        progress.animateTo(1f, tween((path.size * 1000 / 60).coerceAtLeast(200), easing = LinearEasing))
        sounds.cancel()
        if (last.holed) sfx.play(Sound.GOLF_CUP)
        shot.hazard?.let { hazard ->
            // A splash, or a whistle down into the drop, then the ball is back on the tee.
            lost = hazard to path.last()
            sfx.play(if (hazard == Hazard.WATER) Sound.GOLF_SPLASH else Sound.GOLF_DROP, v)
            sink.snapTo(0f)
            try {
                sink.animateTo(1f, tween(Golf.LOST_MS, easing = LinearEasing))
            } finally {
                // Even if the next shot cuts it short, so the ball never stays hidden.
                lost = null
            }
        }
        rolling = null
    }
    var aim by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val flagWave by loopingFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), still = 0f, label = "flag")
    val roll = rolling
    val holeIndex = roll?.first ?: view.hole
    val hole = view.holeAt(holeIndex.coerceIn(0, view.holes - 1))
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
        // The course sits raised on the sky, scaled to fit with room for its shaded side.
        val margin = 0.05
        val depth = 0.035
        val s = minOf(size.width / (Golf.W + 2 * margin), size.height / (Golf.H + 2 * margin + depth)).toFloat()
        val ox = (size.width - Golf.W.toFloat() * s) / 2
        val oy = (size.height - (Golf.H + depth).toFloat() * s) / 2
        fun o(p: Pt) = Offset(ox + (p.x * s).toFloat(), oy + (p.y * s).toFloat())
        fun rect(b: Box, grow: Double = 0.0) = Rect(o(Pt(b.x0 - grow, b.y0 - grow)), o(Pt(b.x1 + grow, b.y1 + grow)))
        fun rounded(r: Rect, radius: Double) = Path().apply { addRoundRect(RoundRect(r, CornerRadius((radius * s).toFloat()))) }
        /** The course's shape, [inset] in from its edge (and round its cuts): one piece, or its islands. */
        fun course(inset: Double): Path {
            val pieces = hole.islands ?: listOf(Box(0.0, 0.0, Golf.W, Golf.H))
            // Islands' corners are as round as the ball finds them (Golf.CORNER).
            val corner = if (hole.islands != null) Golf.CORNER else 0.07
            val outer = pieces.map { rounded(rect(it, -inset), corner) }
                .reduce { a, b -> Path().apply { op(a, b, PathOperation.Union) } }
            return hole.cuts.fold(outer) { shape, cut -> Path().apply { op(shape, rounded(rect(cut, inset), 0.025), PathOperation.Difference) } }
        }
        val rim = 0.028

        // Sky and a few clouds.
        drawRect(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
        for ((cx, cy, r) in listOf(Triple(0.15f, 0.12f, 0.09f), Triple(0.85f, 0.35f, 0.07f), Triple(0.2f, 0.8f, 0.06f))) {
            val c = Offset(size.width * cx, size.height * cy)
            val w = size.width * r
            for ((dx, dy, k) in listOf(Triple(-1f, 0.2f, 0.8f), Triple(0f, 0f, 1f), Triple(1.1f, 0.25f, 0.75f))) {
                drawCircle(Color.White.copy(alpha = 0.55f), w * k, c + Offset(dx * w, dy * w))
            }
        }
        // The side of the course, then its light rim and the grass inside.
        translate(0f, (depth * s).toFloat()) { drawPath(course(0.0), CourseSide) }
        // Bridges over the drop: wooden boards, their ends under the islands' rims.
        hole.bridges.forEach { b ->
            translate(0f, (depth * 0.5 * s).toFloat()) { drawRect(WoodDark, rect(b).topLeft, rect(b).size) }
            drawRect(Wood, rect(b).topLeft, rect(b).size)
            var y = b.y0 + 0.03
            while (y < b.y1) {
                drawLine(WoodDark, o(Pt(b.x0, y)), o(Pt(b.x1, y)), 0.005f * s)
                y += 0.035
            }
            drawRect(WoodDark, rect(b).topLeft, rect(b).size, style = Stroke(0.006f * s))
        }
        drawPath(course(0.0), Rim)
        val grass = course(rim)
        drawPath(grass, Grass)
        clipPath(grass) {
            // Mowing stripes, and a few grassy mounds (just for looks).
            for (i in 0 until 10) if (i % 2 == 0) drawRect(GrassDark.copy(alpha = 0.35f), o(Pt(0.0, Golf.H * i / 10)), Size(Golf.W.toFloat() * s, (Golf.H / 10 * s).toFloat()))
            for ((mx, my) in listOf(0.12 to 0.62, 0.86 to 0.95, 0.15 to 1.2)) {
                val c = o(Pt(mx, my))
                drawOval(GrassDark.copy(alpha = 0.6f), c + Offset(-0.05f * s, 0f), Size(0.1f * s, 0.035f * s))
                drawPath(Path().apply { moveTo(c.x - 0.045f * s, c.y + 0.015f * s); quadraticBezierTo(c.x, c.y - 0.05f * s, c.x + 0.045f * s, c.y + 0.015f * s); close() }, Rim)
            }
        }
        hole.sand.forEach {
            drawPath(rounded(rect(it), 0.05), Sand)
            drawPath(rounded(rect(it), 0.05), SandEdge, style = Stroke(0.006f * s))
        }
        // Ponds, with a glint and a ripple.
        hole.water.forEach { w ->
            val box = rect(Box(w.x - w.rx, w.y - w.ry, w.x + w.rx, w.y + w.ry))
            drawOval(Water, box.topLeft, box.size)
            drawOval(WaterEdge, box.topLeft, box.size, style = Stroke(0.007f * s))
            val c = o(Pt(w.x, w.y))
            val rx = (w.rx * s).toFloat()
            val ry = (w.ry * s).toFloat()
            drawArc(Color.White.copy(alpha = 0.6f), 200f, 60f, false, c - Offset(rx * 0.6f, ry * 0.55f), Size(rx * 0.7f, ry * 0.6f), style = Stroke(0.006f * s, cap = StrokeCap.Round))
            drawArc(Color.White.copy(alpha = 0.35f), 20f, 70f, false, c - Offset(rx * 0.1f, ry * 0.1f), Size(rx * 0.8f, ry * 0.7f), style = Stroke(0.005f * s, cap = StrokeCap.Round))
        }
        // Blocks standing on the course: a shaded side under a light top.
        hole.walls.forEach { b ->
            translate(0f, (depth * 0.6 * s).toFloat()) { drawPath(rounded(rect(b), 0.02), CourseSide) }
            drawPath(rounded(rect(b), 0.02), Rim)
            drawPath(rounded(rect(b, -0.012), 0.015), RimTop)
        }
        // The tee: white arcs behind where you start.
        val tee = o(hole.tee)
        for (k in 1..2) {
            val r = (0.045 + 0.03 * k).toFloat() * s
            drawArc(Color.White.copy(alpha = 0.8f), 180f, 180f, false, tee - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(0.007f * s))
        }
        drawLine(Color.White.copy(alpha = 0.8f), tee + Offset(-0.12f * s, 0f), tee + Offset(0.12f * s, 0f), 0.007f * s)
        // The cup with a ring round it, and the flag with the hole's number.
        val cup = o(hole.cup)
        drawCircle(Color.White.copy(alpha = 0.45f), (Golf.CUP * 2.4 * s).toFloat(), cup, style = Stroke(0.008f * s))
        drawOval(GameColors.Cup, cup - Offset((Golf.CUP * s).toFloat(), (Golf.CUP * 0.75 * s).toFloat()), Size((Golf.CUP * 2 * s).toFloat(), (Golf.CUP * 1.5 * s).toFloat()))
        val top = cup + Offset(0f, -0.26f * s)
        drawLine(ink, cup, top, 0.016f * s, StrokeCap.Round)
        drawLine(Color.White, cup, top, 0.009f * s, StrokeCap.Round)
        val wave = sin(flagWave) * 0.012f * s
        val flag = Path().apply {
            moveTo(top.x, top.y)
            quadraticBezierTo(top.x + 0.07f * s, top.y - 0.02f * s + wave, top.x + 0.15f * s, top.y + 0.01f * s)
            quadraticBezierTo(top.x + 0.1f * s, top.y + 0.045f * s - wave, top.x + 0.15f * s, top.y + 0.085f * s)
            quadraticBezierTo(top.x + 0.07f * s, top.y + 0.06f * s + wave, top.x, top.y + 0.09f * s)
            close()
        }
        drawPath(flag, FlagRed)
        drawPath(flag, ink, style = Stroke(0.005f * s))
        drawIntoCanvas {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 0.055f * s
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            it.nativeCanvas.drawText("${holeIndex + 1}", top.x + 0.06f * s, top.y + 0.065f * s, paint)
        }

        fun ball(p: Pt, mark: Char, size: Float = 1f) {
            val c = o(p)
            val r = (Golf.R * s * 1.15).toFloat() * size
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
            val gone = lost
            if (gone == null) {
                ball(path[(progress.value * (path.size - 1)).toInt().coerceIn(0, path.size - 1)], shooter)
            } else {
                // Sinking with rings spreading on the water, or shrinking away down the drop.
                val (hazard, at) = gone
                val k = sink.value
                if (hazard == Hazard.WATER) {
                    for (ring in 0..1) {
                        val t = (k * 1.4f - ring * 0.35f).coerceIn(0f, 1f)
                        if (t > 0f) drawCircle(Color.White.copy(alpha = 0.8f * (1 - t)), (0.02f + t * 0.07f) * s, o(at), style = Stroke(0.006f * s))
                    }
                }
                if (k < 0.7f) ball(at, shooter, 1f - k / 0.7f)
                drawIntoCanvas {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textSize = 0.06f * s
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textAlign = android.graphics.Paint.Align.CENTER
                        setShadowLayer(0.01f * s, 0f, 0.004f * s, android.graphics.Color.BLACK)
                    }
                    val text = if (hazard == Hazard.WATER) "Splash! Back to the tee" else "Over the edge! Back to the tee"
                    it.nativeCanvas.drawText(text, size.width / 2, o(at).y - 0.08f * s, paint)
                }
            }
        } else {
            view.balls.forEach { (m, p) -> if (p != null && view.done[m] != true) ball(p, m) }
            if (current != null && view.balls[current] == null && view.done[current] != true) ball(hole.tee, current)
        }
        aim?.let { (a, power) -> aimLine(o(myBall), a, power, s) }
    }
}

/** A trail of shrinking dots from the ball the way it will go; longer for a harder shot. */
private fun DrawScope.aimLine(from: Offset, angle: Double, power: Double, s: Float) {
    val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
    val reach = (0.12 + power * 0.45).toFloat() * s
    for (k in 1..6) {
        val t = k / 6f
        drawCircle(Color.White.copy(alpha = 1f - t * 0.5f), s * (0.022f - t * 0.012f), from + dir * (reach * t))
    }
}

/** "Hole 3 of 9 · par 3" and everyone's total so far. */
@Composable
private fun GolfHeader(view: GolfView, names: List<Pair<Char, String>>) {
    val p = AppTheme.palette
    val hole = view.holeAt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Hole ${view.hole + 1} of ${view.holes} · par ${hole.par}", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
        Text(names.joinToString("  ") { (m, n) -> "$n ${view.total(m)}" }, color = p.muted, fontSize = TextSize.Body)
    }
}

/** Scores per player for the result popup: total and against par. */
private fun golfScores(view: GolfView, names: List<Pair<Char, String>>): List<String> {
    val par = (0 until view.holes).sumOf { view.holeAt(it).par }
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
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
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
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "The other player"),
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

/** Mini Golf's level: whoever opened the game picks how many islands every hole has; everyone else sees it. */
@Composable
fun GolfLevelCard(room: RoomState, busy: Boolean, onPick: (GolfLevel) -> Unit) {
    val p = AppTheme.palette
    val level = room.golfLevel ?: return
    AppCard("Which holes?") {
        if (room.setsOptions) {
            OptionGroup(GolfLevel.entries.map { it to it.label }, level, enabled = !busy, onPick = onPick)
        } else {
            Text("${level.label}, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
        }
        Text("All nine holes are this kind, new from the 1,000 each game.", color = p.muted, fontSize = TextSize.Body)
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
                Text("Pull back from the ball like a slingshot and let go: the further you pull, the harder the shot. Fewest strokes over nine holes wins.", color = p.muted, fontSize = TextSize.Body)
                Text("Holes", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                OptionGroup(GolfLevel.entries.map { it to it.label }, state.level, enabled = true, onPick = vm::setLevel)
                Text("Computers", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 0..3) {
                        val label = if (n == 0) "None" else "$n"
                        if (state.computers == n) PrimaryButton(label, {}, Modifier.weight(1f)) else SecondaryButton(label, { vm.setComputers(n) }, Modifier.weight(1f))
                    }
                }
                if (state.computers > 0) {
                    Text("How good", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
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

/** Where along [path] the ball turned back: off a wall or a block. */
private fun bounces(path: List<Pt>): List<Int> = (2 until path.size).filter { i ->
    val (a, b, c) = Triple(path[i - 2], path[i - 1], path[i])
    (b.x - a.x) * (c.x - b.x) < 0 || (b.y - a.y) * (c.y - b.y) < 0
}
