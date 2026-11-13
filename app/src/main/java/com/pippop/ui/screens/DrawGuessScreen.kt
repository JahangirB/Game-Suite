package com.pippop.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.data.nearby.LocalRoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.GameColors
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Pieces
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.DrawStroke
import com.pippop.data.DrawView
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.OpponentColor
import kotlinx.coroutines.delay


/** Points per stroke message: small enough that the drawing appears on the other phone as it is drawn. */
private const val CHUNK_POINTS = 12

/** A Draw & Guess team score from here up counts as a good game (about two words guessed). */
private const val GOOD_TEAM_SCORE = 80

private fun DrawScope.drawStroke(points: FloatArray, color: Int, width: Int) {
    if (points.size < 2) return
    val c = PenColors.getOrElse(color) { PenColors[0] }
    val w = size.width * penWidth(width)
    if (points.size == 2) {
        drawCircle(c, w / 2, Offset(points[0] * size.width, points[1] * size.height))
        return
    }
    val path = Path()
    path.moveTo(points[0] * size.width, points[1] * size.height)
    for (i in 1 until points.size / 2) path.lineTo(points[i * 2] * size.width, points[i * 2 + 1] * size.height)
    drawPath(path, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The square canvas. Shows [strokes]; with [onStroke] set you draw on it, and the line you
 * are drawing is sent in short pieces so your partner sees it appear.
 */
@Composable
internal fun DrawingPad(strokes: List<DrawStroke>, color: Int, width: Int, onStroke: ((DrawStroke) -> Unit)?, modifier: Modifier = Modifier, fillMode: Boolean = false) {
    val p = AppTheme.palette
    val current = remember { mutableStateListOf<Float>() }
    val pen = rememberUpdatedState(Triple(color, width, fillMode))
    val input = if (onStroke == null) Modifier else Modifier.pointerInput(onStroke) {
        fun at(o: Offset) = listOf((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
        // The first piece of each line is marked, so undo takes the whole line back.
        var first = true
        fun flush(keepLast: Boolean) {
            if (current.size < 2) return
            onStroke(DrawStroke(current.toFloatArray(), pen.value.first, pen.value.second, start = first))
            first = false
            val last = current.takeLast(2)
            current.clear()
            if (keepLast) current.addAll(last)
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            // The bucket: a tap pours the colour into the shape under the finger.
            if (pen.value.third) {
                val (x, y) = at(down.position)
                onStroke(DrawStroke(floatArrayOf(x, y), pen.value.first, 1, fill = true, start = true))
                return@awaitEachGesture
            }
            first = true
            current.clear()
            current.addAll(at(down.position))
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                if (!change.pressed) break
                change.consume()
                current.addAll(at(change.position))
                if (current.size / 2 >= CHUNK_POINTS) flush(keepLast = true)
            }
            // A tap with no movement still leaves a dot.
            if (current.size == 2 || current.size > 2) flush(keepLast = false)
            current.clear()
        }
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).pressedEdge(p.ink, Radius.L).clip(Shapes.L)
            .background(Color.White).border(2.dp, p.ink, Shapes.L),
    ) {
        // With a fill in it the drawing is painted as a picture (fills need its pixels).
        val picture = remember(strokes) { if (strokes.any { it.fill }) renderDrawing(strokes) else null }
        if (picture != null) androidx.compose.foundation.Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().then(input)) {
            if (picture == null) strokes.forEach { drawStroke(it.points, it.color, it.width) }
            if (current.isNotEmpty()) drawStroke(current.toFloatArray(), pen.value.first, pen.value.second)
        }
    }
}

/** Draw & Guess with your partner. */
@Composable
fun DrawGuessScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val draw = room.draw ?: return
    val offset by rememberUpdatedState(state.clockOffsetMs)
    val now by produceState(System.currentTimeMillis() + offset) {
        while (true) {
            value = System.currentTimeMillis() + offset
            delay(250)
        }
    }
    val phase = state.phase(now)
    val finished = phase == RoomStatus.FINISHED
    val opponent = room.opponent
    val localNow = rememberTicker()
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.DRAW) { showRules = false }
    val drawing = draw.drawing == true
    val playing = phase == RoomStatus.PLAYING
    // Who draws: you, or the player the server names (the only other one with two players).
    fun draws(player: com.pippop.data.PlayerInfo, isMe: Boolean) =
        if (isMe) drawing else !drawing && (draw.drawerName == null || draw.drawerName == player.name)
    val players = battleChips(
        room, state, localNow, p.primary,
        detail = { player, isMe ->
            val pts = draw.scores.firstOrNull { if (isMe) it.me else it.name == player.name }?.points
            listOfNotNull(pts?.let { "$it pts" }, if (!playing) null else if (draws(player, isMe)) "Drawing" else "Guessing").joinToString(" · ").ifEmpty { null }
        },
        active = { player, isMe -> playing && draws(player, isMe) },
    )
    val secondsLeft = draw.endsAt?.let { ((it - now + 999) / 1000).coerceAtLeast(0) }
    // The clock round the word: the share of the turn left, only while playing with a time limit.
    val clock = draw.endsAt?.takeIf { playing }?.let { ((it - now) / LocalRoom.DRAW_TURN_MS.toFloat()).coerceIn(0f, 1f) }
    val context = LocalContext.current
    val sketchbook = remember { Sketchbook.get(context) }
    var showSketchbook by remember { mutableStateOf(false) }
    val keepLast: (com.pippop.data.DrawTurnResult) -> Unit = { last ->
        sketchbook.keep(Sketch(System.currentTimeMillis(), last.word, if (last.drewIt) "You" else last.drawerName ?: "", last.strokes))
    }
    if (showSketchbook) SketchbookSheet(sketchbook, draw.lastTurn?.takeIf { it.strokes.isNotEmpty() }, keepLast) { showSketchbook = false }
    // When a turn ends its drawing replays, quickly, with the word; Keep saves it. Whoever draws
    // next isn't held up: the last drawing waits for them in the Sketchbook.
    fun keyOf(last: com.pippop.data.DrawTurnResult?) = last?.let { "${room.round}-${draw.turn}-${it.word}" }
    var replayed by remember { mutableStateOf(keyOf(draw.lastTurn)) }
    val replay = draw.lastTurn?.takeIf { keyOf(it) != replayed && it.strokes.isNotEmpty() }
    if (replay != null && !(drawing && playing)) {
        ReplaySheet(replay, room.me.name, onKeep = { keepLast(replay) }) { replayed = keyOf(replay) }
    }

    GameFrame(
        title = "Draw & Guess",
        subtitle = (if (finished) "Game over" else "Turn ${draw.turn} of ${draw.turns}") + (if (draw.race) " · Guess race" else " · Team score ${draw.score}") +
            if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.DRAW.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        onReact = if (opponent != null) ({ reacting = !reacting }) else null,
        // Undo only while you draw; the sketchbook any time.
        myTurn = drawing && playing,
        tools = listOf(
            DockTool("Undo", GameIcons.Undo, anytime = false, enabled = state.drawStrokes.isNotEmpty(), onClick = vm::undoDrawing),
            DockTool("Sketchbook", GameIcons.Book, badge = 0) { showSketchbook = true },
        ),
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = opponent?.name ?: "The other player"
            // A team game: the score decides the picture, not a winner.
            if (draw.race) {
                MatchPopup(
                    room, GameKind.DRAW, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: name),
                    if (room.endReason == "draw") "Top scores are level." else "The fastest right guesses scored the most.",
                    onRematch = vm::readyTurns, onFinish = onLeave,
                    scores = draw.scores.sortedByDescending { it.points }.map { "${if (it.me) "You" else it.name} ${it.points}" },
                )
                return@popupContent
            }
            val good = draw.score >= GOOD_TEAM_SCORE
            MatchPopup(
                room, GameKind.DRAW, if (good) Outcome.WIN else Outcome.LOSE,
                if (good) "Great teamwork!" else "Better luck next time",
                when (room.endReason) {
                    "forfeit" -> if (room.winner == "me") "$name left the game." else "You left the game."
                    "timeout" -> "The connection was lost."
                    else -> "Every right guess scored the seconds left. Play again to beat it."
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = listOf("Team score ${draw.score}"),
            )
        },
    ) {
        // The word guessed: a bell for the team.
        SoundOnChange(draw.lastTurn?.takeIf { it.guessed }) { play(Sound.DRAW_CORRECT) }
        draw.lastTurn?.let { last ->
            val who = when {
                last.guessedIt -> "You"
                last.guesserName != null -> last.guesserName
                last.drewIt -> opponent?.name ?: "The other player"
                else -> "You"
            }
            Text(
                if (last.guessed) "$who guessed ${last.word.uppercase()}! +${last.points}" else "Time's up: it was ${last.word.uppercase()}",
                color = if (last.guessed) p.good else p.muted, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            phase == RoomStatus.COUNTDOWN -> {
                val seconds = (((room.startAt ?: now) - now + 999) / 1000).coerceAtLeast(1)
                AppCard {
                    Text(if (drawing) "You draw first" else "${draw.drawerName ?: opponent?.name ?: "The other player"} draws first", color = p.muted, fontSize = TextSize.Title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    CountdownText(seconds, GameKind.DRAW.color, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            finished -> BoardArea(1f) { size -> DrawingPad(state.drawStrokes, 0, 1, null, size) }
            drawing -> DrawerView(draw, state.drawStrokes, vm, clock, secondsLeft)
            else -> GuesserView(draw, state.drawStrokes, vm, clock, secondsLeft)
        }
    }
}

/**
 * The clock round the word: an outline round [content] that shrinks as the turn runs out, from
 * the theme colour to amber to red for the last seconds. [fraction] is the share of the turn
 * left (null: no time limit, no ring). The ring is the only thing redrawn as it moves, and it
 * moves in steps when the phone has animations off. [seconds] stays on screen beneath it.
 */
@Composable
internal fun WordClock(fraction: Float?, seconds: Long?, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    val reduce = LocalReduceMotion.current
    val smooth = remember { Animatable(fraction ?: 0f) }
    // It runs down smoothly between ticks, but a refill (a new turn) snaps full at once.
    LaunchedEffect(fraction) {
        val f = fraction ?: 0f
        if (f > smooth.value) smooth.snapTo(f) else smooth.animateTo(f, tween(250, easing = LinearEasing))
    }
    val left: () -> Float = if (reduce) { { fraction ?: 0f } } else { { smooth.value } }
    val measure = remember { PathMeasure() }
    val track = remember { Path() }
    val run = remember { Path() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.drawBehind {
                if (fraction == null) return@drawBehind
                val w = 4.dp.toPx()
                track.rewind()
                track.addRoundRect(RoundRect(Rect(Offset(w / 2, w / 2), Size(size.width - w, size.height - w)), CornerRadius(size.height / 2)))
                drawPath(track, p.ink.copy(alpha = 0.15f), style = Stroke(w))
                val f = left()
                val color = if (f > 0.25f) p.primary else if (f > 0.125f) p.warning else p.bad
                measure.setPath(track, false)
                run.rewind()
                measure.getSegment(0f, measure.length * f, run, true)
                drawPath(run, color, style = Stroke(w, cap = StrokeCap.Round))
            }.padding(horizontal = 10.dp, vertical = 6.dp),
        ) { content() }
        if (seconds != null && fraction != null) {
            Text(style = Digits, text = "${seconds}s", color = if (seconds <= 10) p.bad else p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        }
    }
}

/** The drawing pad with a right guess's stamp on it (the race); the stamp presses in only for a guess that arrives while you watch. */
@Composable
internal fun PadWithStamp(draw: DrawView, strokes: List<DrawStroke>, color: Int, width: Int, onStroke: ((DrawStroke) -> Unit)?, modifier: Modifier, fillMode: Boolean = false) {
    val rightAt = draw.rightGuesses.lastIndexOf(true)
    // What was already guessed when the screen opened shows the stamp already down; later right guesses press it in.
    val seen = remember(draw.turn) { rightAt }
    Box(modifier) {
        DrawingPad(strokes, color, width, onStroke, fillMode = fillMode)
        if (rightAt >= 0) {
            val who = if (draw.guesses.getOrNull(rightAt)?.first == true) "You" else draw.guessers.getOrNull(rightAt)
            androidx.compose.runtime.key(rightAt) { GuessStamp(who, rightAt > seen, Modifier.align(Alignment.BottomEnd).padding(12.dp)) }
        }
    }
}

/**
 * A rubber stamp on the page: a tilted rounded outline saying "Got it!" and who. With [press] it
 * lands from slightly large in under a fifth of a second (not at all when motion is reduced).
 * It takes no touches, so drawing and the controls work through it.
 */
@Composable
internal fun GuessStamp(who: String?, press: Boolean, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val lands = press && !LocalReduceMotion.current
    val scale = remember { Animatable(if (lands) 1.5f else 1f) }
    LaunchedEffect(Unit) { if (lands) scale.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { rotationZ = -12f; scaleX = scale.value; scaleY = scale.value }
            .clip(Shapes.M).background(Color.White.copy(alpha = 0.7f)).border(3.dp, Board.Stamp, Shapes.M)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text("Got it!", color = Board.Stamp, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        if (who != null) Text(who.uppercase(), color = Board.Stamp, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.DrawerView(draw: DrawView, strokes: List<DrawStroke>, vm: BattleViewModel, clock: Float?, secondsLeft: Long?) {
    val p = AppTheme.palette
    var color by rememberSaveable { mutableStateOf(0) }
    var width by rememberSaveable { mutableStateOf(1) }
    var bucket by rememberSaveable { mutableStateOf(false) }
    WordClock(clock, secondsLeft) {
        Text("Draw: ${draw.word.orEmpty().uppercase()}", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    BoardArea(1f) { size -> PadWithStamp(draw, strokes, color, width, vm::sendStroke, size, fillMode = bucket) }
    // The tray: eight colours and the eraser, three brush sizes, the bucket, and Clear.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        (TrayColors + ERASER).forEach { i ->
            val on = color == i
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (on) 30.dp else 25.dp).clip(CircleShape).background(PenColors[i])
                    .border(if (on) 3.dp else 2.dp, p.ink, CircleShape).clickable { color = i },
            ) { if (i == ERASER) Text("⌫", color = p.ink, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        // The dot shows the brush: its size, in the colour picked.
        for (w in 1..3) {
            val on = width == w && !bucket
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(38.dp).clip(Shapes.S).background(if (on) p.accent else p.card).border(2.dp, p.ink, Shapes.S)
                    .clickable { width = w; bucket = false },
            ) { Box(Modifier.size((4 + w * w * 3).dp).clip(CircleShape).background(PenColors[color]).border(1.dp, p.ink, CircleShape)) }
        }
        Text(
            "🪣 Fill", color = p.text, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(Shapes.S).background(if (bucket) p.accent else p.card).border(2.dp, p.ink, Shapes.S).clickable { bucket = !bucket }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f))
        Text(
            "Clear", color = p.bad, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(Shapes.S).border(2.dp, p.ink, Shapes.S).clickable { vm.clearDrawing() }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
    if (draw.guesses.isNotEmpty()) GuessFeed(draw)
}

@Composable
private fun ColumnScope.GuesserView(draw: DrawView, strokes: List<DrawStroke>, vm: BattleViewModel, clock: Float?, secondsLeft: Long?) {
    val p = AppTheme.palette
    var guess by rememberSaveable(draw.turn) { mutableStateOf("") }
    val send = {
        vm.guessDrawing(guess)
        guess = ""
    }
    // The race: once you've got it you see the word, and wait for the others.
    WordClock(clock, secondsLeft) {
        Text(
            if (draw.gotIt && draw.word != null) "You got it: ${draw.word.uppercase()}!" else draw.hint.orEmpty().map { it }.joinToString(" "),
            color = if (draw.gotIt) p.good else p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
    }
    BoardArea(1f) { size -> PadWithStamp(draw, strokes, 0, 1, null, size) }
    if (!draw.gotIt) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToyField(
            value = guess,
            onValueChange = { guess = it.take(30) },
            placeholder = "What is it?",
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
        )
        PrimaryButton("Guess", send, Modifier.weight(0.45f), enabled = guess.isNotBlank())
    }
    if (draw.guesses.isNotEmpty()) GuessFeed(draw)
}

/**
 * The guesses this turn, newest last: wrong ones in grey, and in the race right ones in green
 * ("Alex got it! +80") with the word still hidden from those guessing.
 */
@Composable
private fun GuessFeed(draw: DrawView) {
    val p = AppTheme.palette
    if (!draw.race) {
        Text("Not it: " + guessList(draw), color = p.muted, fontSize = TextSize.Body)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        draw.guesses.forEachIndexed { i, (byMe, word) ->
            val right = draw.rightGuesses.getOrNull(i) == true
            val who = if (byMe) "You" else draw.guessers.getOrNull(i) ?: "?"
            Text(
                if (right) "$who got it!" else "$who: $word",
                color = if (right) p.onPrimary else p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(Shapes.Full).background(if (right) p.good else p.card).border(1.dp, p.ink, Shapes.Full).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** A finished turn's drawing playing back in about four seconds, with the word; Keep saves it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReplaySheet(last: com.pippop.data.DrawTurnResult, myName: String, onKeep: () -> Unit, onDone: () -> Unit) {
    val p = AppTheme.palette
    var shown by remember(last) { mutableStateOf(0) }
    var kept by remember(last) { mutableStateOf(false) }
    LaunchedEffect(last) {
        val step = (REPLAY_MS / last.strokes.size.coerceAtLeast(1)).coerceIn(8L, 200L)
        while (shown < last.strokes.size) {
            delay(step)
            shown++
        }
        delay(1800)
        if (!kept) onDone()
    }
    InfoSheet(last.word.uppercase(), onDismiss = onDone) {
        Text(
            when {
                last.rights.isNotEmpty() -> last.rights.joinToString(", ") + " got it"
                last.guessed -> "${last.guesserName ?: "Someone"} guessed it"
                else -> "Nobody got it this time"
            } + (last.drawerName?.let { " · drawn by ${if (last.drewIt) "you" else it}" } ?: ""),
            color = p.muted, fontSize = TextSize.Body,
        )
        Box(Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally)) {
            DrawingPad(last.strokes.take(shown), 0, 1, null)
            // A right guess stamps the finished picture once the replay has drawn it all.
            if (last.guessed && shown >= last.strokes.size) {
                GuessStamp(if (last.guessedIt) "You" else (last.guesserName ?: last.rights.firstOrNull()).let { if (it == myName) "You" else it }, true, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (kept) Text("In the sketchbook", color = p.good, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(top = 14.dp))
            else PrimaryButton("Keep", { kept = true; onKeep() }, Modifier.weight(1f))
            SecondaryButton("Next", onDone, Modifier.weight(1f))
        }
    }
}

/** The replay's length, whatever the number of strokes. */
private const val REPLAY_MS = 4000L

/** The sketchbook: every drawing you kept, newest first, with its word, who drew it and when. */
@Composable
private fun SketchbookSheet(book: Sketchbook, last: com.pippop.data.DrawTurnResult?, onKeep: (com.pippop.data.DrawTurnResult) -> Unit, onDismiss: () -> Unit) {
    val p = AppTheme.palette
    val sketches by book.sketches.collectAsState()
    InfoSheet("Sketchbook", onDismiss = onDismiss) {
        // The turn just played, to keep if you like it.
        if (last != null && sketches.none { it.word == last.word }) {
            Text("LAST TURN: ${last.word.uppercase()}", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth(0.6f)) { DrawingPad(last.strokes, 0, 1, null) }
            PrimaryButton("Keep it", { onKeep(last) })
        }
        if (sketches.isEmpty()) Text("Nothing kept yet: tap Keep when a turn's drawing replays.", color = p.muted, fontSize = TextSize.Body)
        sketches.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { sk ->
                    Column(Modifier.weight(1f)) {
                        DrawingPad(sk.strokes, 0, 1, null)
                        Text(sk.word.uppercase(), color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                        val d = java.time.Instant.ofEpochMilli(sk.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        Text("${sk.drawer} · ${d.dayOfMonth} ${d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}", color = p.muted, fontSize = TextSize.Label)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Draw & Guess: the team game or the guess race, picked by whoever opened the game. */
@Composable
fun DrawModeCard(room: com.pippop.data.RoomState, busy: Boolean, onPick: (Boolean) -> Unit) {
    val p = AppTheme.palette
    AppCard("Mode") {
        if (room.setsOptions) {
            OptionGroup(listOf(false to "Team", true to "Race for 4"), room.drawRace, enabled = !busy, onPick = onPick)
        } else {
            Text(if (room.drawRace) "Guess race, picked by whoever opened the game." else "Team game, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
        }
        Text(
            if (room.drawRace) "One draws, everyone else guesses at once: 80, 60, 40 for the first right guesses, and the drawer 20 for each."
            else "Take turns drawing; every right guess adds the seconds left to your team score.",
            color = p.muted, fontSize = TextSize.Label,
        )
    }
}

/** The wrong guesses this turn; with several guessers each says who made it. */
private fun guessList(draw: DrawView): String {
    val named = draw.guessers.toSet().size > 1
    return draw.guesses.mapIndexed { i, (byMe, word) ->
        if (!named) word else (if (byMe) "you" else draw.guessers.getOrNull(i) ?: "?") + ": " + word
    }.joinToString(", ")
}
