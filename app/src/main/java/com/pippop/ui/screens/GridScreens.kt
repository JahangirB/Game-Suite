package com.pippop.ui.screens

import com.pippop.ui.startWith
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.rememberSfx
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Radius
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.withFrameMillis
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.readableOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.pippop.ui.theme.Space
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.GameKind
import com.pippop.data.GridResult
import com.pippop.data.RoomStatus
import com.pippop.game.grid.LetterGrid
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloGridUiState
import com.pippop.ui.SoloGridViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont

/** How a finished drag was judged: a new word, one already found, or not a word. */
private enum class Verdict { GOOD, REPEAT, BAD }

/** The ribbon left behind by a finished drag, shown briefly in the colour of its [verdict]. */
private data class Settled(val path: List<Int>, val verdict: Verdict, val word: String)

/**
 * The 4 × 4 letters on chunky cream tiles. Drag through touching squares to spell a word (going
 * back one square takes the last letter off); letting go sends it to [onWord]. The path is one
 * rounded ribbon through the letters; on letting go it turns green (new word, which flies up
 * with its points), yellow (already found) or red (not a word). While [fuseLeftMs] is given, the
 * frame round the grid runs down like a fuse over [LetterGrid.ROUND_MS] and turns red at the end.
 * [startPath] is the squares already dragged over (only for pictures).
 */
@Composable
fun LetterGridBoard(
    letters: String, enabled: Boolean, onWord: (String) -> Unit, modifier: Modifier = Modifier,
    found: List<String> = emptyList(), fuseLeftMs: (() -> Long)? = null, startPath: List<Int> = emptyList(),
) {
    val p = AppTheme.palette
    var path by remember(letters) { mutableStateOf(startPath) }
    var finger by remember(letters) { mutableStateOf<Offset?>(null) }
    var shown by remember(letters) { mutableStateOf<Settled?>(null) }
    val settle = remember(letters) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val send by rememberUpdatedState(onWord)
    val foundNow by rememberUpdatedState(found)
    val sfx = rememberSfx()
    val n = LetterGrid.SIZE
    // The fuse reads the clock once a frame, but only the frame's drawing looks at it.
    val frame = remember { mutableLongStateOf(0L) }
    if (fuseLeftMs != null && enabled) LaunchedEffect(Unit) { while (true) withFrameMillis { frame.longValue = it } }
    val framePad = if (fuseLeftMs != null) 10.dp else 0.dp
    BoxWithConstraints(
        modifier.drawBehind {
            if (fuseLeftMs != null) {
                frame.longValue
                drawFuse((fuseLeftMs() / LetterGrid.ROUND_MS.toFloat()).coerceIn(0f, 1f), fuseLeftMs(), framePad.toPx(), p.ink, p.bad, p.ink)
            }
        }.padding(framePad),
    ) {
        val cellPx = with(LocalDensity.current) { maxWidth.toPx() } / n
        val touch = if (!enabled) Modifier else Modifier.pointerInput(letters) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // A square counts only when the finger is near its middle, so diagonals are easy.
                fun cellAt(x: Float, y: Float): Int? {
                    val c = (x / cellPx).toInt()
                    val r = (y / cellPx).toInt()
                    if (c !in 0 until n || r !in 0 until n) return null
                    val dx = x - (c + 0.5f) * cellPx
                    val dy = y - (r + 0.5f) * cellPx
                    return if (dx * dx + dy * dy <= (cellPx * 0.42f) * (cellPx * 0.42f)) r * n + c else null
                }
                var trail = listOfNotNull(cellAt(down.position.x, down.position.y))
                if (trail.isNotEmpty()) sfx.play(Sound.GRID_TRACE)
                path = trail
                shown = null
                finger = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) break
                    pointer.consume()
                    finger = pointer.position
                    val cell = cellAt(pointer.position.x, pointer.position.y) ?: continue
                    trail = when {
                        trail.isEmpty() -> listOf(cell)
                        trail.size >= 2 && cell == trail[trail.size - 2] -> trail.dropLast(1)
                        cell !in trail && LetterGrid.touching(trail.last(), cell) -> trail + cell
                        else -> trail
                    }
                    // Each letter added ticks a little higher along the word.
                    if (trail.size > path.size) sfx.play(Sound.GRID_TRACE, pitch = 1f + 0.06f * (trail.size - 1))
                    path = trail
                }
                if (trail.size >= 3) {
                    val word = trail.joinToString("") { letters[it].toString() }
                    val verdict = when {
                        word.lowercase() in foundNow -> Verdict.REPEAT
                        LetterGrid.problem(letters, word.lowercase()) != null -> Verdict.BAD
                        else -> Verdict.GOOD
                    }
                    shown = Settled(trail, verdict, word)
                    // The ribbon holds its verdict colour a moment (a new word also flies up), then goes.
                    scope.launch {
                        settle.snapTo(0f)
                        if (reduce) { delay(450); settle.snapTo(1f) } else settle.animateTo(1f, tween(650, easing = LinearEasing))
                    }
                    send(word)
                }
                path = emptyList()
                finger = null
            }
        }
        val side = maxWidth / n - 6.dp
        val sidePx = with(LocalDensity.current) { side.toPx() }
        val pitchPx = with(LocalDensity.current) { (side + 6.dp).toPx() }
        // Tiles lift a little while the finger's path is over them.
        val lifts = List(n * n) { i ->
            animateFloatAsState(if (i in path) 1f else 0f, if (reduce) snap() else tween(90), label = "tileLift")
        }
        Box(Modifier.then(touch)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until n) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until n) {
                        val lift = lifts[r * n + c]
                        Box(
                            Modifier.size(side).graphicsLayer { translationY = -3.dp.toPx() * lift.value }.drawBehind {
                                val edge = 4.dp.toPx()
                                val face = Size(size.width, size.height - edge)
                                raisedTile(Board.Bone, Offset.Zero, face, Radius.M.toPx(), edge)
                                drawRoundRect(Board.Ink, Offset.Zero, face, CornerRadius(Radius.M.toPx()), style = Stroke(2.dp.toPx()))
                            },
                        )
                    }
                }
            }
            // The ribbon: the path (and the finger's last stretch) as one rounded stroke, inked at the edge.
            Canvas(Modifier.matchParentSize().semantics { contentDescription = if (path.isEmpty()) "" else "Path: " + path.joinToString("") { letters[it].toString() } }) {
                val live = path
                val now = shown
                val cells = if (live.isNotEmpty()) live else now?.path.orEmpty()
                if (cells.isEmpty()) return@Canvas
                val color = if (live.isNotEmpty()) p.primary else when (now?.verdict) {
                    Verdict.GOOD -> p.good
                    Verdict.REPEAT -> Pieces.Yellow
                    else -> p.bad
                }
                val alpha = if (live.isNotEmpty()) 1f else 1f - ((settle.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
                fun centre(i: Int) = Offset((i % n) * pitchPx + sidePx / 2, (i / n) * pitchPx + sidePx / 2 - 3.dp.toPx() * lifts[i].value)
                val ribbon = Path().apply {
                    moveTo(centre(cells[0]).x, centre(cells[0]).y)
                    for (k in 1 until cells.size) centre(cells[k]).let { lineTo(it.x, it.y) }
                    if (live.isNotEmpty()) finger?.let { lineTo(it.x, it.y) }
                }
                val w = sidePx * 0.3f
                if (cells.size == 1 && live.size == 1 && finger == null) drawCircle(color, w / 2, centre(cells[0]), alpha)
                drawPath(ribbon, Board.Ink, alpha, Stroke(w + 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(ribbon, color, alpha, Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until n) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        val lift = lifts[i]
                        Box(Modifier.size(side).graphicsLayer { translationY = -3.dp.toPx() * lift.value }, contentAlignment = Alignment.Center) {
                            Text(letters[i].uppercase(), color = Board.Ink, fontSize = (side.value * 0.45f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont)
                        }
                    }
                }
            }
        }
        // A new word flies up from the middle of the grid towards the score, with its points.
        val flying = shown?.takeIf { it.verdict == Verdict.GOOD && !reduce }
        if (flying != null) {
            Box(
                Modifier.align(Alignment.Center).clearAndSetSemantics {}.graphicsLayer {
                    val t = settle.value
                    translationY = -t * cellPx * n * 0.9f
                    alpha = 1f - ((t - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    scaleX = 1f + 0.25f * (1f - t); scaleY = scaleX
                }.background(p.good, Shapes.Full).padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("${flying.word.uppercase()} +${LetterGrid.points(flying.word.lowercase())}", color = readableOn(p.good), fontSize = TextSize.Body, fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * The frame round the grid as a fuse: a faint track all the way round, and the part of the
 * time still left in [color], red in the last ten seconds, with a spark at its burning end.
 */
private fun DrawScope.drawFuse(fraction: Float, leftMs: Long, pad: Float, color: Color, urgent: Color, track: Color) {
    val stroke = pad * 0.5f
    val inset = stroke / 2f
    val outline = Path().apply {
        addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(pad * 1.6f)))
    }
    drawPath(outline, track.copy(alpha = 0.15f), style = Stroke(stroke))
    val measure = PathMeasure().apply { setPath(outline, false) }
    val len = measure.length
    val left = Path()
    measure.getSegment(len * (1f - fraction), len, left, true)
    val live = if (leftMs <= 10_000L) urgent else color
    drawPath(left, live, style = Stroke(stroke, cap = StrokeCap.Round))
    if (fraction in 0.01f..0.99f) drawCircle(Pieces.Yellow, stroke * 0.9f, measure.getPosition(len * (1f - fraction)))
}

/** Your found words as a few short lines, newest first: the newest in green, dropping in. */
@Composable
internal fun FoundWords(words: List<String>) {
    val p = AppTheme.palette
    val reduce = LocalReduceMotion.current
    SoundOnChange(words.size.takeIf { it > 0 }) { play(Sound.GRID_WORD) }
    val drop = remember { Animatable(0f) }
    val seen = remember { intArrayOf(words.size) }
    LaunchedEffect(words.size) {
        val grew = words.size > seen[0]
        seen[0] = words.size
        if (grew && !reduce) { drop.snapTo(1f); drop.animateTo(0f, tween(260)) }
    }
    val text = if (words.isEmpty()) AnnotatedString("Drag through touching letters to spell words of three letters or more.") else buildAnnotatedString {
        val newest = words.reversed()
        withStyle(SpanStyle(color = p.good, fontWeight = FontWeight.Black)) { append(newest.first().uppercase()) }
        for (w in newest.drop(1)) append("  ·  " + w.uppercase())
    }
    Text(
        text,
        color = if (words.isEmpty()) p.muted else p.text,
        fontSize = TextSize.Body, fontWeight = if (words.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
        maxLines = 3, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = -drop.value * 14.dp.toPx(); alpha = 1f - 0.6f * drop.value },
    )
}

private fun timeLeft(endsAt: Long, now: Long): String {
    val s = ((endsAt - now + 999) / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Each player's words at the end: unique words (double points) in bold. */
@Composable
private fun ColumnScope.GridResults(results: List<GridResult>) {
    val p = AppTheme.palette
    for (r in results) {
        Text("${if (r.mine) "You" else r.name}: ${r.score} points", color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Body)
        Text(
            r.words.joinToString("  ") { (if (it.unique) "★" else "") + it.word.uppercase() }.ifEmpty { "No words" },
            color = p.muted, fontSize = TextSize.Label, maxLines = 3,
        )
    }
}

// ---- Against your partners -----------------------------------------------------------------

@Composable
fun GridGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val grid = room.grid ?: return
    rememberTicker()
    val now = state.serverNow()
    val phase = state.phase(now)
    val finished = room.status == RoomStatus.FINISHED
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GRID) { showRules = false }
    var showWords by remember { mutableStateOf(false) }
    if (showWords) InfoSheet("Your words: ${grid.mine.size}", onDismiss = { showWords = false }) {
        Text(
            grid.mine.sortedWith(compareBy({ -it.length }, { it })).joinToString("  ·  ") { it.uppercase() }.ifEmpty { "None yet." },
            color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold,
        )
    }
    val letters = grid.letters
    val endsAt = grid.endsAt ?: 0L
    val playing = phase == RoomStatus.PLAYING && now < endsAt

    GameFrame(
        title = "Letter Grid",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, rememberTicker(), p.primary, detail = { player, isMe ->
            if (isMe) "${grid.mine.size} words" else "${grid.counts.getOrNull(room.others.indexOf(player)) ?: 0} words"
        }),
        glow = GameKind.GRID.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        tools = listOf(DockTool("My words", GameIcons.Book, badge = grid.mine.size) { showWords = true }),
        trailing = { Text(if (playing) timeLeft(endsAt, now) else "", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.GRID, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: "The other player"),
                when (room.endReason) {
                    "score" -> "★ words only one of you found score double."
                    "draw" -> "Top scores are level."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyGrid, onFinish = onLeave,
            ) { grid.results?.let { GridResults(it) } }
        },
    ) {
        if (letters == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                    if (phase == RoomStatus.COUNTDOWN) {
                        CountdownText((((room.startAt ?: 0L) - now + 999) / 1000).coerceAtLeast(1), p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            TurnLine(if (playing) "Find words: drag through touching letters" else "Time's up!", playing, GameKind.GRID.color)
            BoardArea(1f) { size -> LetterGridBoard(letters, playing, vm::findGridWord, size, found = grid.mine, fuseLeftMs = { endsAt - state.serverNow() }) }
            FoundWords(grid.mine)
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloGridScreen(state: SoloGridUiState, vm: SoloGridViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GRID) { showRules = false }
    val now = rememberTicker()
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    if (!state.started) { BackToHome(onHome); return }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.GRID, "New game", vm::startWith) { changing = false }
    val letters = state.letters
    GameFrame(
        title = "Letter Grid",
        subtitle = state.best?.let { "On your own · best $it" } ?: "On your own",
        players = emptyList(),
        glow = GameKind.GRID.color,
        menu = soloMenu(GameKind.GRID, onHome, { showRules = true }, vm::start, { changing = true }),
        trailing = {
            if (letters != null && !state.over && state.timed) Text(timeLeft(state.endsAt, now), style = Digits, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        },
        popup = if (!state.over) null else popupContent {
            ResultPopup(
                game = GameKind.GRID,
                outcome = if (state.score > 0 && state.score >= (state.best ?: 0)) Outcome.WIN else Outcome.TIE,
                title = "${state.score} points",
                detail = "${state.found.size} of ${state.total} words found." +
                    if (state.missed.isEmpty()) "" else " You missed: " + state.missed.joinToString(", ") { it.uppercase() },
                finish = PopupAction("Home", onHome),
                again = PopupAction("New grid", vm::start),
            )
        },
    ) {
        if (letters == null) {
            // The grid is made a moment after the game is asked for.
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PipPopLoader(Modifier.size(width = 96.dp, height = 60.dp))
                Text("Making your grid…", color = p.text, modifier = Modifier.padding(top = Space.L))
            }
        } else {
            TurnLine(state.message ?: "Score ${state.score}: drag through touching letters", state.message == null && !state.over, if (state.message != null) p.bad else GameKind.GRID.color)
            BoardArea(1f) { size -> LetterGridBoard(letters, !state.over, vm::find, size, found = state.found, fuseLeftMs = if (state.timed) ({ state.endsAt - System.currentTimeMillis() }) else null) }
            FoundWords(state.found)
            if (!state.over) SecondaryButton(if (state.timed) "Finish now" else "I'm done", vm::finish, Modifier.fillMaxWidth())
        }
    }
}
