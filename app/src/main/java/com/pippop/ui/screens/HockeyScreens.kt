package com.pippop.ui.screens

import com.pippop.ui.startWith
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.lerp
import com.pippop.ui.theme.LocalReduceMotion
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.pippop.ui.Sound
import com.pippop.ui.rememberSfx
import androidx.compose.runtime.SideEffect
import kotlin.math.hypot
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Board
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.game.hockey.Body
import com.pippop.game.hockey.Hockey
import com.pippop.game.hockey.HockeyFrame
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloHockeyUiState
import com.pippop.ui.SoloHockeyViewModel
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme

private val Ice = Board.Ice
private val Line = Board.IceLine
private val Goal = Board.Ink
private val Puck = Board.Ink
private val Rail = Board.IceLine.darker(0.3f)

/** How many recent puck positions the streak remembers. */
private const val TRAIL = 12

/** The puck's last few positions in table units, newest last, in a small fixed ring (no allocation per frame). */
private class PuckTrail {
    val xs = FloatArray(TRAIL)
    val ys = FloatArray(TRAIL)
    var head = 0
    var count = 0

    fun clear() { count = 0 }

    /** Adds the puck's spot; a jump bigger than any real move (a serve after a goal) starts the streak afresh. */
    fun push(x: Float, y: Float) {
        if (count > 0) {
            val last = (head + TRAIL - 1) % TRAIL
            if (hypot(x - xs[last], y - ys[last]) > 0.15f) count = 0
        }
        xs[head] = x
        ys[head] = y
        head = (head + 1) % TRAIL
        if (count < TRAIL) count++
    }

    /** The spot [age] steps back (0 is the newest). */
    fun x(age: Int) = xs[(head + 2 * TRAIL - 1 - age) % TRAIL]
    fun y(age: Int) = ys[(head + 2 * TRAIL - 1 - age) % TRAIL]
}

/**
 * The table, you at the bottom. Put a finger anywhere and your paddle follows it (it stays in
 * your half). [onMove] gets table coordinates.
 */
@Composable
fun HockeyRink(frame: HockeyFrame, myColor: Color, theirColor: Color, onMove: ((Float, Float) -> Unit)?, modifier: Modifier = Modifier) {
    val move by rememberUpdatedState(onMove)
    // Sounds from the frames themselves: a goal when the score goes up, and a tock whenever the
    // puck turns back, off a paddle (louder the faster it leaves) or, thinner, off a wall.
    val sfx = rememberSfx()
    val previous = remember { arrayOf<HockeyFrame?>(null) }
    val reduceMotion = LocalReduceMotion.current
    val trail = remember { PuckTrail() }
    // The goal flash: 1 at the goal, fading to 0 in under a second; who scored (1 you, 2 them).
    // Only the drawing reads them, so a flash redraws the table and nothing else.
    val flash = remember { Animatable(0f) }
    val scorer = remember { mutableIntStateOf(0) }
    val seenGoals = remember { intArrayOf(-1, -1) }
    LaunchedEffect(frame.bottomScore, frame.topScore) {
        val oldMine = seenGoals[0]
        val oldTheirs = seenGoals[1]
        val one = oldMine >= 0 && frame.bottomScore + frame.topScore == oldMine + oldTheirs + 1
        val mineUp = one && frame.bottomScore == oldMine + 1
        val theirsUp = one && frame.topScore == oldTheirs + 1
        seenGoals[0] = frame.bottomScore
        seenGoals[1] = frame.topScore
        if (!mineUp && !theirsUp) return@LaunchedEffect
        scorer.intValue = if (mineUp) 1 else 2
        flash.snapTo(1f)
        // Reduce motion: the same highlight, held still, then gone.
        if (reduceMotion) delay(GOAL_FLASH_MS.toLong()) else flash.animateTo(0f, tween(GOAL_FLASH_MS, easing = LinearEasing))
        flash.snapTo(0f)
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(1) }
    // Each side's score, big, measured once per score or size (never per frame).
    val numberStyle = remember(widthPx, density) { TextStyle(fontSize = with(density) { (widthPx * 0.4f).toSp() }, fontWeight = FontWeight.Bold).merge(Digits) }
    val mineText = remember(frame.bottomScore, widthPx) { measurer.measure("${frame.bottomScore}", numberStyle) }
    val theirText = remember(frame.topScore, widthPx) { measurer.measure("${frame.topScore}", numberStyle) }
    SideEffect {
        val before = previous[0]
        previous[0] = frame
        if (!reduceMotion) {
            if (before != null && frame.bottomScore + frame.topScore != before.bottomScore + before.topScore) trail.clear()
            trail.push(frame.puck.x, frame.puck.y)
        }
        if (before == null) return@SideEffect
        val a = before.puck
        val b = frame.puck
        if (frame.bottomScore + frame.topScore > before.bottomScore + before.topScore) {
            sfx.play(Sound.HOCKEY_GOAL)
        } else if (a.vx * b.vx < 0 || a.vy * b.vy < 0) {
            val reach = Hockey.PUCK_R + Hockey.PADDLE_R + 0.02f
            val byPaddle = listOf(frame.bottom, frame.top).any { hypot(it.x - b.x, it.y - b.y) < reach }
            val speed = (hypot(b.vx, b.vy) / Hockey.MAX_SPEED).coerceIn(0.3f, 1f)
            if (byPaddle) sfx.play(Sound.HOCKEY_HIT, speed) else sfx.play(Sound.HOCKEY_WALL, speed)
        }
    }
    val touch = if (onMove == null) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val scale = size.width / Hockey.W
            move?.invoke(down.position.x / scale, down.position.y / scale)
            while (true) {
                val event = awaitPointerEvent()
                val p = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!p.pressed) break
                p.consume()
                move?.invoke(p.position.x / scale, p.position.y / scale)
            }
        }
    }
    Canvas(modifier.clip(Shapes.L).onSizeChanged { widthPx = it.width.coerceAtLeast(1) }.then(touch)) {
        val k = size.width / Hockey.W
        fun at(b: Body) = Offset(b.x * k, b.y * k)
        val lit = flash.value
        val scoredBy = scorer.intValue
        drawRect(Ice)
        // Each side's score, big and faint in its half (yours at the bottom, always). The scorer's
        // number blinks while the goal flashes.
        fun number(text: TextLayoutResult, centreY: Float, mine: Boolean) {
            val blink = lit > 0f && scoredBy == (if (mine) 1 else 2) && (lit * 6).toInt() % 2 == 0
            drawText(text, Puck.copy(alpha = if (blink) 0.5f else 0.16f), Offset(size.width / 2 - text.size.width / 2f, centreY - text.size.height / 2f))
        }
        number(theirText, size.height * 0.25f, false)
        number(mineText, size.height * 0.75f, true)
        // Centre line and circle, and the two goal mouths.
        drawLine(Line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx())
        drawCircle(Line, 0.18f * k, Offset(size.width / 2, size.height / 2), style = Stroke(3.dp.toPx()))
        val goal = Hockey.GOAL_W * k
        val depth = 8.dp.toPx()
        // The rails round the ice, on the table's edge and open at the goal slots. After a goal
        // they light in the scorer's colour.
        val railColor = if (lit > 0f) lerp(Rail, if (scoredBy == 1) myColor else theirColor, lit) else Rail
        val rail = 4.dp.toPx()
        val gx = (size.width - goal) / 2
        drawRoundRect(railColor, cornerRadius = CornerRadius(Radius.L.toPx()), style = Stroke(rail))
        // The goal slots: each glows in its defender's colour; the one just scored in turns gold.
        fun slot(top: Boolean, c: Color) {
            val y = if (top) -depth else size.height - depth
            drawRoundRect(Goal, Offset(gx, y), Size(goal, depth * 2), CornerRadius(depth))
            drawRoundRect(c.copy(alpha = 0.7f), Offset(gx, y), Size(goal, depth * 2), CornerRadius(depth), style = Stroke(3.dp.toPx()))
            if (lit > 0f && scoredBy == (if (top) 1 else 2)) {
                drawRoundRect(Board.Glow.copy(alpha = lit), Offset(gx, y), Size(goal, depth * 2), CornerRadius(depth))
                drawRoundRect(Board.Glow.copy(alpha = lit * 0.5f), Offset(gx - 6.dp.toPx(), y - 6.dp.toPx()), Size(goal + 12.dp.toPx(), depth * 2 + 12.dp.toPx()), CornerRadius(depth * 1.5f), style = Stroke(5.dp.toPx()))
            }
        }
        slot(true, theirColor)
        slot(false, myColor)
        // The puck's streak: a few fading dots behind it, more the faster it goes.
        val speed = (hypot(frame.puck.vx, frame.puck.vy) / Hockey.MAX_SPEED).coerceIn(0f, 1f)
        val tail = minOf((speed * TRAIL * 1.6f).toInt(), trail.count - 1)
        for (age in 1..tail) {
            val fade = 1f - age / (TRAIL + 1f)
            drawCircle(Puck.copy(alpha = 0.3f * fade), Hockey.PUCK_R * k * fade, Offset(trail.x(age) * k, trail.y(age) * k))
        }
        // Shadows under the puck and the mallets.
        roundShadow(at(frame.puck), Hockey.PUCK_R * k)
        roundShadow(at(frame.top), Hockey.PADDLE_R * k, strength = 0.18f)
        roundShadow(at(frame.bottom), Hockey.PADDLE_R * k, strength = 0.18f)
        // Paddles: theirs at the top, yours at the bottom.
        for ((b, c) in listOf(frame.top to theirColor, frame.bottom to myColor)) {
            drawCircle(c, Hockey.PADDLE_R * k, at(b))
            drawCircle(Color.White.copy(alpha = 0.35f), Hockey.PADDLE_R * 0.55f * k, at(b))
            drawCircle(Goal, Hockey.PADDLE_R * k, at(b), style = Stroke(2.dp.toPx()))
        }
        drawCircle(Puck, Hockey.PUCK_R * k, at(frame.puck))
        drawCircle(Color.White.copy(alpha = 0.25f), Hockey.PUCK_R * 0.5f * k, at(frame.puck))
    }
}

// ---- Against your partner ---------------------------------------------------------------------

/** Air Hockey against your partner, in real time. */
@Composable
fun HockeyScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val frame by vm.hockey.collectAsStateWithLifecycle()
    val shown = frame ?: HockeyFrame.start()
    val now = rememberTicker()
    val phase = state.phase(state.serverNow())
    val finished = room.status == RoomStatus.FINISHED
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.HOCKEY) { showRules = false }
    val opponent = room.opponent
    // While playing, the live table has the latest score; at the end, the server's.
    val mine = if (finished) room.hockey?.mine ?: shown.bottomScore else shown.bottomScore
    val theirs = if (finished) room.hockey?.theirs ?: shown.topScore else shown.topScore
    // The last few seconds of play, kept so the dock's Replay can show the last goal again.
    val recent = remember { ArrayDeque<HockeyFrame>() }
    var lastGoal by remember { mutableStateOf<List<HockeyFrame>>(emptyList()) }
    var goalsSeen by remember { mutableIntStateOf(-1) }
    LaunchedEffect(shown) {
        recent.addLast(shown)
        while (recent.size > REPLAY_FRAMES) recent.removeFirst()
        val goals = shown.bottomScore + shown.topScore
        if (goalsSeen in 0 until goals) lastGoal = recent.toList()
        goalsSeen = goals
    }
    var replaying by remember { mutableStateOf(false) }
    if (replaying && lastGoal.isNotEmpty()) InfoSheet("The last goal", onDismiss = { replaying = false }) {
        // Plays the frames back at their pace, then holds the last one.
        var run by remember { mutableIntStateOf(0) }
        var i by remember(run) { mutableIntStateOf(0) }
        LaunchedEffect(run) {
            while (i < lastGoal.lastIndex) {
                delay(REPLAY_STEP_MS)
                i++
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(Hockey.W / Hockey.H)) {
            HockeyRink(lastGoal[i], MyDisc, TheirDisc, onMove = null, modifier = Modifier.fillMaxSize())
        }
        TextButton(onClick = { run++ }) { Text("Watch it again", color = p.primaryText, fontWeight = FontWeight.Bold) }
    }

    GameFrame(
        title = "Air Hockey",
        subtitle = "First to ${Hockey.GOALS}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, MyDisc, detail = { _, isMe -> if (isMe) "$mine goals" else "$theirs goals" }),
        glow = GameKind.HOCKEY.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        tools = listOf(DockTool("Replay", GameIcons.History, enabled = lastGoal.isNotEmpty()) { replaying = true }),
        onReact = if (opponent != null) ({ reacting = !reacting }) else null,
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = opponent?.name ?: "The other player"
            MatchPopup(
                room, GameKind.HOCKEY, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: name),
                when (room.endReason) {
                    "goals" -> "$mine – $theirs"
                    "forfeit" -> if (room.winner == "me") "$name left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "$name lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
            )
        },
    ) {
        BoardArea(Hockey.W / Hockey.H) { size ->
            Box(size, contentAlignment = Alignment.Center) {
                HockeyRink(shown, MyDisc, TheirDisc, onMove = if (finished) null else vm::moveHockeyPaddle, modifier = Modifier.fillMaxSize())
                if (phase == RoomStatus.COUNTDOWN) {
                    val seconds = (((room.startAt ?: 0L) - state.serverNow() + 999) / 1000).coerceAtLeast(1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                        CountdownText(seconds, p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

/** Air Hockey against the computer. */
@Composable
fun SoloHockeyScreen(state: SoloHockeyUiState, vm: SoloHockeyViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.HOCKEY) { showRules = false }
    val level = state.level
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    if (level == null) { BackToHome(onHome); return }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.HOCKEY, "New game", vm::startWith) { changing = false }
    val levels = listOf("Easy", "Medium", "Hard")
    val f = state.frame
    val result = state.result
    GameFrame(
        title = "Air Hockey",
        subtitle = "Against the computer · ${levels[level]}",
        players = listOf(
            ChipPlayer("You", null, MyDisc, "${f.bottomScore} goals"),
            ChipPlayer("Computer", null, TheirDisc, "${f.topScore} goals"),
        ),
        glow = GameKind.HOCKEY.color,
        menu = soloMenu(GameKind.HOCKEY, onHome, { showRules = true }, vm::again, { changing = true }),
        popup = if (result == null) null else popupContent {
            SoloPopup(
                GameKind.HOCKEY, result, "${f.bottomScore} – ${f.topScore}",
                onAgain = vm::again, onHome = onHome, finishLabel = "Change level", onFinish = { changing = true },
            )
        },
    ) {
        BoardArea(Hockey.W / Hockey.H) { size ->
            HockeyRink(f, MyDisc, TheirDisc, onMove = if (result == null) vm::move else null, modifier = size)
        }
    }
}

/** How long the goal flash lasts; play carries on underneath (nothing pauses after a goal). */
private const val GOAL_FLASH_MS = 600

/** Frames kept for the goal replay (about three seconds), and the replay's pace. */
private const val REPLAY_FRAMES = 180
private const val REPLAY_STEP_MS = 16L
