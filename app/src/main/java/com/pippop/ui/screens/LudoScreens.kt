package com.pippop.ui.screens

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.layout.Column
import com.pippop.ui.Sfx
import com.pippop.ui.Sound
import com.pippop.ui.rememberSfx
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.markOf
import com.pippop.ui.theme.drawMark
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Pieces
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
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
import com.pippop.game.ludo.LudoMove
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
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
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.game.ludo.Ludo
import com.pippop.game.ludo.LudoView
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloLudoUiState
import com.pippop.ui.SoloLudoViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import kotlin.math.hypot
import androidx.compose.runtime.SideEffect

/** A Ludo colour (0 red, 1 green, 2 yellow, 3 blue). */
fun ludoColor(c: Int): Color = when (c) {
    0 -> Pieces.Red
    1 -> Pieces.Green
    2 -> Pieces.Yellow
    else -> Pieces.Blue
}

private val Ink = Board.Ink

/** The die's face: white in both themes, like the board, so the dark pips always show. */
private val DieWhite = Board.DieWhite

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
 * A little pawn in [color] standing at [at] (the square's centre), [u] tall-ish: a flared body, a
 * glossy head, a light outline over a darker rim so it shows on the white squares, and a shadow.
 */
private fun DrawScope.drawPawn(at: Offset, u: Float, color: Color) {
    val foot = Offset(at.x, at.y + u * 0.34f)
    val head = Offset(at.x, at.y - u * 0.22f)
    val rim = color.darker(0.5f)
    val light = Color.White.copy(alpha = 0.75f)
    roundShadow(foot, u * 0.3f, drop = u * 0.07f)
    val body = Path().apply {
        moveTo(foot.x - u * 0.3f, foot.y)
        quadraticBezierTo(at.x - u * 0.1f, at.y + u * 0.15f, at.x - u * 0.09f, at.y - u * 0.1f)
        lineTo(at.x + u * 0.09f, at.y - u * 0.1f)
        quadraticBezierTo(at.x + u * 0.1f, at.y + u * 0.15f, foot.x + u * 0.3f, foot.y)
        close()
    }
    val baseTop = Offset(foot.x - u * 0.3f, foot.y - u * 0.08f)
    val baseSize = Size(u * 0.6f, u * 0.16f)
    drawPath(body, rim, style = Stroke(u * 0.1f))
    drawOval(rim, baseTop - Offset(u * 0.03f, u * 0.03f), Size(baseSize.width + u * 0.06f, baseSize.height + u * 0.06f))
    drawPath(body, color.darker(0.08f))
    drawOval(color.darker(0.08f), baseTop, baseSize)
    drawPath(body, light, style = Stroke(u * 0.035f))
    drawCircle(rim, u * 0.26f, head)
    glossyDisc(color, u * 0.22f, head)
    drawCircle(light, u * 0.22f, head, style = Stroke(u * 0.035f))
    markOf(color)?.let { drawMark(it, head, u * 0.22f) }
}

/**
 * The classic cross board. Your [movable] tokens bob; tapping one calls [onToken] with its
 * index. Tokens sharing a square sit side by side. After a roll each token you can move shows
 * where it will land, with a dotted trail leading there; a knocked token flies back to its yard.
 */
@Composable
fun LudoBoard(view: LudoView, onToken: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val bob by loopingFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), still = 1f, label = "bob")
    val tap by rememberUpdatedState(onToken)
    val current by rememberUpdatedState(view)
    // The last token moved hops square by square from where it was; [steps] counts the squares.
    val move = view.lastMove?.takeIf { it.token != null && it.from != null && it.to != null && it.to > it.from }
    val steps = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    val sfx = rememberSfx()
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(move) {
        val m = move
        if (!seen || m == null) {
            seen = true
            steps.snapTo(Float.MAX_VALUE)
            return@LaunchedEffect
        }
        // Coming out of the yard is a single step onto the start square.
        val count = if (m.from!! < 0) 1 else m.to!! - m.from
        launch { ludoSounds(sfx, m, count) }
        steps.snapTo(0f)
        if (reduce) steps.snapTo(count.toFloat()) else steps.animateTo(count.toFloat(), tween(count * HOP_MS, easing = LinearEasing))
    }
    // A token knocked home flies from the square it was hit on to its yard slot, after the hop
    // that hit it. [before] is last frame's board, to tell which token was the one knocked.
    val before = remember { arrayOfNulls<Map<Char, List<Int>>>(1) }
    val knock = view.lastMove?.takeIf { it.captured.isNotEmpty() && it.to != null }
    val flights = remember(knock) {
        val prev = before[0]
        if (knock == null || prev == null) emptyList() else {
            val taken = mutableSetOf<Pair<Char, Int>>()
            knock.captured.mapNotNull { w ->
                val now = view.tokens[w].orEmpty()
                val was = prev[w].orEmpty()
                val sq = view.colours[knock.who]?.let { Ludo.square(it, knock.to!!) }
                val left = now.indices.filter { now[it] == -1 && (w to it) !in taken && was.getOrElse(it) { -1 } >= 0 }
                // The token that stood on the square the mover landed on; any knocked one if none did.
                val i = left.firstOrNull { sq != null && Ludo.square(view.colours[w] ?: 0, was[it]) == sq } ?: left.firstOrNull() ?: return@mapNotNull null
                taken += w to i
                // It is hit on the mover's landing square.
                Triple(w, i, if (sq != null) Ludo.ringCells[sq].let { (x, y) -> x + 0.5f to y + 0.5f } else Ludo.cellOf(view.colours[w] ?: 0, was[i], i))
            }
        }
    }
    val fly = remember(flights) { Animatable(if (flights.isEmpty() || reduce) 1f else 0f) }
    LaunchedEffect(fly) {
        if (fly.value < 1f) fly.animateTo(1f, tween(FLY_MS, delayMillis = ((knock?.to ?: 0) - (knock?.from ?: 0)) * HOP_MS, easing = LinearEasing))
    }
    SideEffect { before[0] = view.tokens }
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
    Canvas(modifier.clip(Shapes.L).then(touch)) {
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
            if (i % 13 == 8 || start) drawStar(Offset((x + 0.5f) * cell, (y + 0.5f) * cell), cell * 0.38f, Ink.copy(alpha = 0.3f))
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
        // After a roll: where each token you can move will land, and a dotted trail to it.
        val roll = view.roll
        val mineNow = view.tokens['m']
        val myColour = view.colours['m']
        if (roll != null && mineNow != null && myColour != null) for (i in view.movable) {
            val from = mineNow[i]
            val to = Ludo.landing(from, roll)
            val mark = ludoColor(myColour)
            fun centre(prog: Int) = Ludo.cellOf(myColour, prog, i).let { (x, y) -> Offset(x * cell, y * cell) }
            val path = (if (from < 0) listOf(-1, 0) else (from..to).toList()).map { centre(it) }
            for (k in 0 until path.size - 1) for (t in listOf(0.35f, 0.7f)) {
                drawCircle(Ink, cell * 0.07f, path[k] + (path[k + 1] - path[k]) * t)
            }
            val end = centre(to)
            val half = if (to == Ludo.HOME) cell * 0.3f else cell * 0.5f
            drawRect(mark.copy(alpha = 0.35f), end - Offset(half, half), Size(half * 2, half * 2))
            drawRect(Ink, end - Offset(half * 0.92f, half * 0.92f), Size(half * 1.84f, half * 1.84f), style = Stroke(cell * 0.07f))
        }
        // Tokens: stacks on one square spread out a little.
        val all = view.tokens.flatMap { (who, list) -> list.mapIndexed { i, prog -> Triple(who, i, prog) } }
        val placed = mutableMapOf<Pair<Float, Float>, Int>()
        for ((who, i, prog) in all) {
            val colour = view.colours[who] ?: continue
            if (fly.value < 1f && flights.any { it.first == who && it.second == i }) continue
            // The token on the move: between two squares, with a little hop.
            val hopping = move != null && who == move.who && i == move.token && steps.value < stepsFor(move)
            val (x, y) = if (hopping) {
                val k = steps.value
                val start = move!!.from!!
                val a = Ludo.cellOf(colour, if (start < 0) -1 else start + k.toInt(), i)
                val b = Ludo.cellOf(colour, if (start < 0) 0 else minOf(move.to!!, start + k.toInt() + 1), i)
                val f = k - k.toInt()
                (a.first + (b.first - a.first) * f) to (a.second + (b.second - a.second) * f - sin(f * PI.toFloat()) * 0.35f)
            } else {
                Ludo.cellOf(colour, prog, i)
            }
            val n = if (hopping) 0 else placed[x to y] ?: 0
            if (!hopping) placed[x to y] = n + 1
            val shift = if (prog in 0 until Ludo.HOME && !hopping) n * cell * 0.18f else 0f
            val lift = if (who == 'm' && i in view.movable) -cell * 0.18f * bob else 0f
            val at = Offset(x * cell + shift, y * cell + shift + lift)
            drawPawn(at, cell, ludoColor(colour))
            if (who == 'm' && i in view.movable) drawCircle(Ink, cell * 0.46f, at, style = Stroke(cell * 0.07f))
        }
        // Knocked tokens: a burst where they were hit, then an arc home, a little bigger at the top.
        if (fly.value < 1f) for ((who, i, hitAt) in flights) {
            val colour = view.colours[who] ?: continue
            val a = Offset(hitAt.first * cell, hitAt.second * cell)
            val b = Ludo.cellOf(colour, -1, i).let { Offset(it.first * cell, it.second * cell) }
            val f = fly.value
            val arc = sin(f * PI.toFloat())
            drawPawn(a + (b - a) * f - Offset(0f, arc * cell * 1.5f), cell * (1f + 0.3f * arc), ludoColor(colour))
            burst(a, f * 2.5f, ludoColor(colour), cell * 1.1f)
        }
    }
}

/** How long a token takes per square. */
private const val HOP_MS = 150

/** How long a knocked token takes to fly home. */
private const val FLY_MS = 450

private const val TUMBLE_MS = 560

private fun stepsFor(m: LudoMove) = if (m.from!! < 0) 1f else (m.to!! - m.from).toFloat()

/**
 * A die face showing [n]. When [rolled] changes the die tumbles through a few faces first, so
 * every roll is seen happening.
 */
@Composable
internal fun DieFace(n: Int?, modifier: Modifier = Modifier, rolled: Any? = null) {
    var face by remember { mutableStateOf(n) }
    val sfx = rememberSfx()
    val spin = remember { Animatable(0f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(rolled, n) {
        if (!seen || rolled == null || n == null) {
            seen = true
            face = n
            return@LaunchedEffect
        }
        launch { spin.snapTo(0f); spin.animateTo(1f, tween(TUMBLE_MS, easing = FastOutSlowInEasing)) }
        sfx.play(Sound.DICE_ROLL)
        repeat(7) {
            face = (1..6).random()
            delay(TUMBLE_MS / 8L)
        }
        face = n
        sfx.play(Sound.DICE_LAND)
    }
    Surface(
        color = DieWhite, shape = Shapes.M, border = BorderStroke(2.dp, Ink),
        modifier = modifier.size(52.dp).graphicsLayer { rotationZ = spin.value * 360f; scaleX = 1f + 0.2f * sin(spin.value * PI.toFloat()); scaleY = scaleX },
    ) {
        Canvas(Modifier.size(52.dp)) {
            val n = face
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
    val myColour = view.colours['m'] ?: 0
    val canMove = myTurn && enabled
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
    ludoNews(view, name)?.let { Text(it, color = p.muted, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    BoardArea(1f) { size -> LudoBoard(view, onToken = if (canMove && view.roll != null) onToken else null, modifier = size) }
    // The die and the Roll button: the roll shows at once, nothing to wait for.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DieFace(view.roll ?: view.lastMove?.roll, rolled = view.lastMove ?: view.roll)
        PrimaryButton("Roll", onRoll, Modifier.weight(1f), enabled = canMove && view.roll == null)
    }
}

/**
 * Dice history: how often each number has come up this game, for everyone and for each player,
 * and the last rolls in order. The die is a fair secure random number, so over a game each face
 * comes up about a sixth of the time.
 */
@Composable
fun DiceHistory(view: LudoView, name: (Char) -> String) {
    val p = AppTheme.palette
    val rolls = view.rolls
    if (rolls.isEmpty()) {
        Text("No rolls yet.", color = p.muted, fontSize = TextSize.Body)
        return
    }
    @Composable
    fun counts(label: String, of: List<Int>) {
        Text("$label · ${of.size} rolls", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            (1..6).forEach { n ->
                val c = of.count { it == n }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    DieFace(n, Modifier.size(30.dp))
                    Text("$c", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                    Text(if (of.isEmpty()) "" else "${(100 * c + of.size / 2) / of.size}%", color = p.muted, fontSize = TextSize.Micro)
                }
            }
        }
    }
    counts("Everyone", rolls.map { it.second })
    rolls.map { it.first }.distinct().sortedBy { "mopq".indexOf(it) }.forEach { who ->
        counts(name(who), rolls.filter { it.first == who }.map { it.second })
    }
    Text("A fair die: each number has the same chance on every roll, about 17%. Over a few rolls the counts wander; over a whole game they even out.", color = p.muted, fontSize = TextSize.Label)
    Text("LAST ROLLS, NEWEST FIRST", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
    Text(rolls.takeLast(30).reversed().joinToString("  ") { (w, n) -> "${name(w).take(3)} $n" }, color = p.text, fontSize = TextSize.Body)
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
    var showDice by remember { mutableStateOf(false) }
    if (showDice) InfoSheet("Dice history", onDismiss = { showDice = false }) { DiceHistory(view, ::name) }

    GameFrame(
        title = "Ludo",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, ludoColor(view.colours['m'] ?: 0),
            detail = { player, isMe -> "${homeCount(view, if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0)))}/4 home" },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> ludoColor(view.colours[letterOf(i)] ?: 1) },
        ),
        glow = GameKind.LUDO.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        tools = listOf(DockTool("Dice history", GameIcons.History, badge = view.rollCount) { showDice = true }),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.LUDO,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "The other player"),
                when (room.endReason) {
                    "home" -> if (room.winner == "me") "All four of your tokens are home!" else "${room.winnerName} got all four tokens home."
                    "left" -> "A player left, so the game stopped."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
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
            waitingText = if (waitingOn?.online == false) "$turnName is away. They'll get a notification." else "${turnName ?: "The other player"} is playing…",
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
    var showDice by remember { mutableStateOf(false) }
    if (showDice && view != null) InfoSheet("Dice history", onDismiss = { showDice = false }) { DiceHistory(view, ::name) }
    GameFrame(
        title = "Ludo",
        subtitle = "Against the computer",
        players = if (view == null) emptyList() else view.colours.keys.sortedBy { "mopq".indexOf(it) }.map { mark ->
            ChipPlayer(name(mark), null, ludoColor(view.colours[mark] ?: 0), "${homeCount(view, mark)}/4 home", active = result == null && state.turn == mark)
        },
        glow = GameKind.LUDO.color,
        menu = soloMenu(GameKind.LUDO, onHome, { showRules = true }, if (view != null) vm::changeSetup else null),
        tools = if (view == null) emptyList() else listOf(DockTool("Dice history", GameIcons.History) { showDice = true }),
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
                Text("Roll, bring your tokens out with a 6, race them round and home. Land on others to send them back.", color = p.muted, fontSize = TextSize.Body)
                Text("Computer players", color = p.text, fontSize = TextSize.BodyL)
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

/**
 * A token's move, in sound: a pop out of the yard, a tap on each square (rising as it goes),
 * then a bonk and a slide whistle for a token knocked back, or a little fanfare at home.
 */
private suspend fun ludoSounds(sfx: Sfx, m: LudoMove, count: Int) {
    val v = if (m.who == 'm') 1f else Sfx.OTHERS
    if (m.from!! < 0) {
        sfx.play(Sound.LUDO_OUT, v)
        return
    }
    repeat(count) { i ->
        sfx.play(Sound.PIECE_HOP, v, pitch = 1f + 0.04f * i)
        delay(HOP_MS.toLong())
    }
    when {
        m.captured.isNotEmpty() -> sfx.play(Sound.LUDO_CAPTURE)
        m.to == Ludo.HOME -> sfx.play(Sound.LUDO_HOME, v)
    }
}
