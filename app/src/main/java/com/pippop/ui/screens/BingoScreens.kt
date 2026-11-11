package com.pippop.ui.screens

import androidx.compose.foundation.layout.width

import com.pippop.ui.BingoPace
import com.pippop.ui.theme.Space
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.rememberSfx
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Pieces
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.loopingFloat
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.game.bingo.Bingo
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloBingoUiState
import com.pippop.ui.SoloBingoViewModel
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont

private val ColumnColours = listOf(Pieces.Red, Pieces.Orange, Pieces.Green, Pieces.Blue, Pieces.Purple)

/** A number's column colour. */
private fun ballColour(n: Int) = ColumnColours[((n - 1) / 15).coerceIn(0, 4)]

/** Wins whose ribbon has played (card and line), so folding the result sheet or rotating never replays it. */
private val playedWins = mutableSetOf<String>()

/** The squares that finish a line when dabbed: lines with exactly one undabbed square left (the free centre counts as dabbed). */
private fun oneAwaySquares(dabs: Set<Int>): Set<Int> = buildSet {
    for (l in Bingo.LINES) {
        val open = l.filter { it != Bingo.FREE && it !in dabs }
        if (open.size == 1) add(open[0])
    }
}

/**
 * The win: the five squares hop one after another, a gold ribbon runs through them and a little
 * confetti falls. [p] runs 0 to 1; at 1 only the ribbon is left. Squares sit in a grid of [cell]
 * with the BINGO letters above, so square i has its centre at column i % 5, row i / 5 + 1.
 */
private fun DrawScope.winRibbon(line: List<Int>, p: Float, cell: Float) {
    fun centre(i: Int) = Offset((i % Bingo.SIZE + 0.5f) * cell, (i / Bingo.SIZE + 1.5f) * cell)
    val from = centre(line.first())
    val to = centre(line.last())
    val reach = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
    val end = from + (to - from) * reach
    // Once the run is over the ribbon thins to a light trace, so the numbers under it read.
    val settle = ((p - 0.8f) / 0.2f).coerceIn(0f, 1f)
    val strength = 1f - 0.65f * settle
    val thin = 1f - 0.5f * settle
    drawLine(Board.Ink.copy(alpha = 0.35f * strength), from, end, cell * 0.26f * thin, StrokeCap.Round)
    drawLine(Board.Glow.copy(alpha = 0.7f * strength), from, end, cell * 0.18f * thin, StrokeCap.Round)
    val t = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
    if (t <= 0f || t >= 1f) return
    val mid = (from + to) / 2f
    for (k in 0 until 28) {
        val a = k * 2.4f
        val speed = 0.5f + (k * 37 % 10) / 10f
        val at = mid + Offset(cos(a) * speed * cell * 3f * t, (sin(a) * 0.6f - 0.6f) * speed * cell * 3f * t + cell * 4f * t * t)
        drawRect(ColumnColours[k % 5].copy(alpha = 1f - t * t), at - Offset(cell * 0.05f, cell * 0.05f), Size(cell * 0.1f, cell * 0.1f))
    }
}

/**
 * A card printed on one sheet under the BINGO letters. Tap a called number to dab it (it looks
 * like dauber ink, a little uneven, the same on every draw); [line] (at the end) is outlined,
 * and its squares hop with a ribbon and confetti. A square that would finish a line glows gold
 * and beats. The free centre is always dabbed.
 */
@Composable
fun BingoCard(card: List<Int>, dabs: Set<Int>, line: List<Int>?, enabled: Boolean, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    SoundOnChange(dabs.size.takeIf { enabled }) { play(Sound.BINGO_DAB) }
    // A full line dabbed: the Bingo button lights up with a shimmer.
    SoundOnChange((enabled && Bingo.fullLine(card, dabs.map { card[it] }.toSet()) != null).takeIf { it }) { play(Sound.BINGO_LINE_READY) }
    val oneAway = remember(dabs, enabled, line == null) { if (enabled && line == null) oneAwaySquares(dabs) else emptySet() }
    // A heartbeat for the squares one dab from a line; a still glow when motion is reduced.
    val beat = if (oneAway.isNotEmpty()) {
        loopingFloat(0f, 0f, infiniteRepeatable(keyframes { durationMillis = 1200; 1f at 140; 0.25f at 300; 0.75f at 440; 0f at 700 }, RepeatMode.Restart), 0.6f, "oneAway")
    } else null
    // The win plays once when the line arrives (not again after a rotation); with reduced motion only the ribbon shows.
    val reduce = LocalReduceMotion.current
    val winLine = line?.sorted()
    val winKey = "$card$winLine"
    val win = remember { Animatable(0f) }
    LaunchedEffect(winLine) {
        if (winLine == null) win.snapTo(0f)
        else {
            if (reduce || winKey in playedWins) win.snapTo(1f) else win.animateTo(1f, tween(1300, easing = LinearEasing))
            playedWins.add(winKey)
        }
    }
    BoxWithConstraints(modifier) {
        val cell = minOf(maxWidth, maxHeight * 5f / 6f) / Bingo.SIZE
        Column(
            Modifier.align(Alignment.Center)
                // The sheet: paper with a soft shadow under it.
                .drawBehind {
                    val corner = CornerRadius(cell.toPx() * 0.2f)
                    boxShadow(Offset.Zero, size, corner.x, cell.toPx() * 0.06f, 0.25f)
                    drawRoundRect(p.card, Offset.Zero, size, corner)
                    drawRoundRect(p.ink.copy(alpha = 0.25f), Offset.Zero, size, corner, style = Stroke(1.dp.toPx()))
                }
                .drawWithContent {
                    drawContent()
                    if (winLine != null && win.value > 0f) winRibbon(winLine, win.value, cell.toPx())
                },
        ) {
            Row {
                "BINGO".forEachIndexed { c, ch ->
                    Box(Modifier.size(cell).padding(3.dp).clip(Shapes.S).background(ColumnColours[c]), contentAlignment = Alignment.Center) {
                        Text(ch.toString(), color = readableOn(ColumnColours[c]), fontSize = (cell.value * 0.45f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont)
                    }
                }
            }
            for (r in 0 until Bingo.SIZE) Row {
                for (c in 0 until Bingo.SIZE) {
                    val i = r * Bingo.SIZE + c
                    val n = card.getOrElse(i) { 0 }
                    val free = i == Bingo.FREE
                    val dabbed = free || i in dabs
                    val inLine = line?.contains(i) == true
                    val away = i in oneAway
                    val hopAt = winLine?.indexOf(i) ?: -1
                    Box(
                        Modifier.size(cell)
                            .graphicsLayer {
                                if (away) {
                                    val s = 1f + 0.07f * (beat?.value ?: 0f)
                                    scaleX = s
                                    scaleY = s
                                }
                                // Squares of the winning line hop one after another.
                                if (hopAt >= 0) translationY = -sin(PI.toFloat() * ((win.value - hopAt * 0.12f) / 0.3f).coerceIn(0f, 1f)) * size.height * 0.22f
                            }
                            .drawBehind {
                                if (away) drawRoundRect(Board.Glow, alpha = 0.3f + 0.7f * (beat?.value ?: 0f), cornerRadius = CornerRadius(size.minDimension * 0.2f))
                            },
                    ) {
                        Surface(
                            color = if (inLine || away) Board.GlowSoft else p.card,
                            shape = Shapes.S,
                            // A one-away square also gets a thicker outline, so gold is not the only cue.
                            border = BorderStroke(if (away) 4.dp else if (inLine) 3.dp else 2.dp, p.ink),
                            modifier = Modifier.fillMaxSize().padding(3.dp)
                                .semantics { if (away) stateDescription = "One dab from a line" }
                                .clickable(enabled = enabled && !free) { onTap(i) },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                // A dab pops onto the square like dauber ink, a little different on each square (the free star has no pop).
                                if (dabbed) Box(
                                    Modifier.fillMaxSize().padding(cell * 0.12f)
                                        .enterFrom(Unit, scaleFrom = 0.2f, durationMs = 220, onAppear = !free)
                                        .graphicsLayer {
                                            scaleX = 0.94f + (i * 7 % 5) * 0.03f
                                            scaleY = 0.94f + (i * 11 % 5) * 0.03f
                                            rotationZ = ((i * 13 % 9) - 4) * 4f
                                            translationX = ((i * 5 % 7) - 3) * 0.012f * size.width
                                            translationY = ((i * 3 % 7) - 3) * 0.012f * size.height
                                        }
                                        .clip(CircleShape).background(ColumnColours[c].copy(alpha = 0.88f))
                                        .border(1.5.dp, ColumnColours[c].darker(0.3f), CircleShape),
                                )
                                Text(
                                    if (free) "★" else n.toString(),
                                    color = if (dabbed) readableOn(lerp(p.card, ColumnColours[c], 0.88f)) else if (away) Board.Ink else p.text,
                                    fontSize = (cell.value * if (free) 0.42f else 0.36f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Ball(n: Int, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(ballColour(n)).border(2.dp, AppTheme.palette.ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * 0.66f).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Bingo.letter(n).toString(), color = Board.Ink, fontSize = (size.value * 0.16f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.17f).sp)
                Text(n.toString(), color = Board.Ink, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont, lineHeight = (size.value * 0.27f).sp)
            }
        }
    }
}

private val BigBall = 76.dp
/** The older balls' size as a share of the newest one's (36dp of 76dp). */
private const val SmallShare = 36f / 76f
private const val RailSlots = 6

/** Where a ball [slot] places along a rail [width] wide (dp): 0 is the newest, big, at the left. */
private fun railX(slot: Int, width: Dp): Float = when {
    slot == 0 -> 0f
    slot < RailSlots -> BigBall.value + 8f + (slot - 1) * 42f
    else -> width.value
}

/**
 * One ball on the rail. A call that arrives while watching ([live]) rolls in from the left,
 * turning once; every ball slides along and shrinks as newer ones arrive. Without [live]
 * (opening the screen) it sits at rest; with reduced motion it jumps.
 */
@Composable
private fun RailBall(n: Int, slot: Int, live: Boolean, width: Dp) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current.density
    val targetX = railX(slot, width)
    val targetScale = if (slot == 0) 1f else SmallShare
    val rollIn = live && !reduce
    val x = remember { Animatable(if (rollIn) -BigBall.value else targetX) }
    val turn = remember { Animatable(if (rollIn) -360f else 0f) }
    val scale = remember { Animatable(targetScale) }
    LaunchedEffect(slot, width) {
        if (reduce) {
            x.snapTo(targetX); turn.snapTo(0f); scale.snapTo(targetScale)
        } else {
            launch { turn.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(targetScale, tween(450, easing = FastOutSlowInEasing)) }
            x.animateTo(targetX, tween(450, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.size(BigBall).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = x.value * density
            scaleX = scale.value
            scaleY = scale.value
            // The smaller balls sit on the bottom of the rail.
            translationY = BigBall.toPx() * (1f - scale.value)
        },
    ) {
        Box(Modifier.graphicsLayer { rotationZ = turn.value }) { Ball(n, BigBall) }
    }
}

/** The ball rail: the latest call big at the left, the few before it shuffling along to the right. */
@Composable
internal fun CallStrip(calls: List<Int>, called: Int) {
    val p = AppTheme.palette
    SoundOnChange(called.takeIf { it > 0 }) { play(Sound.BINGO_BALL) }
    val base = remember(calls) { called }
    if (called == 0) {
        Text("Get ready…", color = p.muted, fontSize = TextSize.BodyL)
        return
    }
    // Calls already made when this game came on screen sit at rest; later ones roll in.
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(BigBall + 8.dp).clip(Shapes.M)
            .background(p.ink.copy(alpha = 0.08f))
            .clearAndSetSemantics { contentDescription = "Call $called of ${Bingo.NUMBERS}: ${Bingo.letter(calls[called - 1])} ${calls[called - 1]}" },
    ) {
        val railWidth = maxWidth
        Box(Modifier.fillMaxSize().padding(4.dp)) {
            Text("Call $called of ${Bingo.NUMBERS}", color = p.muted, fontSize = TextSize.Body, modifier = Modifier.padding(start = BigBall + 8.dp))
            key(calls) {
                for (k in (called - 1) downTo maxOf(0, called - 1 - RailSlots)) key(k) { RailBall(calls[k], called - 1 - k, k >= base, railWidth) }
            }
        }
    }
}

// ---- Against others ---------------------------------------------------------------------------

@Composable
fun BingoGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val bingo = room.bingo ?: return
    val now = rememberTicker()
    val phase = state.phase(state.serverNow())
    val finished = room.status == RoomStatus.FINISHED
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.BINGO) { showRules = false }
    val startAt = room.startAt ?: 0L
    val card = bingo.card
    val calls = bingo.calls
    // Once over, the calls stop where the winner shouted.
    val endMs = room.me.finishMs ?: room.others.firstNotNullOfOrNull { it.finishMs }
    val called = when {
        calls == null -> 0
        finished && endMs != null -> Bingo.calledCount(endMs)
        else -> Bingo.calledCount(state.serverNow() - startAt)
    }
    val dabs = if (state.bingoRound == room.round) state.bingoDabs else emptySet()
    var showCalls by remember { mutableStateOf(false) }
    if (showCalls) InfoSheet("Called so far: $called", onDismiss = { showCalls = false }) {
        val list = calls?.take(called).orEmpty()
        if (list.isEmpty()) Text("No numbers yet.", color = p.muted, fontSize = TextSize.Body)
        // Newest first, in rows of eight; the numbers on your card in bold colour.
        list.reversed().chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { n ->
                    val mine = card?.contains(n) == true
                    Text(
                        "$n", color = if (mine) GameKind.BINGO.color else p.text, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        fontSize = TextSize.BodyL, modifier = Modifier.width(34.dp),
                    )
                }
            }
        }
    }
    // Your own win seen live: the result sheet waits for the ribbon to finish (not on reopening, not with reduced motion).
    val watched = remember(room.round) { !finished }
    var ribbonDone by remember(room.round) { mutableStateOf(false) }
    val myWin = finished && room.winner == "me" && bingo.line != null
    LaunchedEffect(myWin) { if (myWin) { delay(1300); ribbonDone = true } }
    val holdPopup = myWin && watched && !ribbonDone && !LocalReduceMotion.current
    val canClaim = card != null && Bingo.fullLine(card, dabs.map { card[it] }.toSet()) != null
    GameFrame(
        title = "Bingo",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, p.primary, detail = { _, isMe -> if (isMe && card != null) "${dabs.size} dabbed" else null }),
        glow = GameKind.BINGO.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        tools = listOf(DockTool("Calls", GameIcons.History, badge = called) { showCalls = true }),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished || holdPopup) null else popupContent {
            val name = room.winnerName ?: room.opponent?.name ?: "The other player"
            MatchPopup(
                room, GameKind.BINGO, outcomeOf(room.winner), resultTitle(room.winner, name),
                when (room.endReason) {
                    "bingo" -> if (room.winner == "me") "Bingo after $called calls!" else "$name called Bingo first."
                    "draw" -> "The calls ran out with nobody calling Bingo."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
            ) {
                val win = bingo.winCard
                if (room.winner == "opponent" && win != null) {
                    BingoCard(win, emptySet(), bingo.line, false, {}, Modifier.size(180.dp))
                }
            }
        },
    ) {
        if (card == null || calls == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                    if (phase == RoomStatus.COUNTDOWN) {
                        CountdownText(((startAt - state.serverNow() + 999) / 1000).coerceAtLeast(1), p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            CallStrip(calls, called)
            BingoCard(card, dabs, if (finished && room.winner == "me") bingo.line else null, phase == RoomStatus.PLAYING, vm::dabBingo, Modifier.fillMaxWidth().weight(1f))
            val sfx = rememberSfx()
            PrimaryButton("BINGO!", { sfx.play(Sound.BINGO_SHOUT); vm.claimBingo() }, Modifier.fillMaxWidth(), enabled = canClaim && !state.firing && !finished)
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloBingoScreen(state: SoloBingoUiState, vm: SoloBingoViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.BINGO) { showRules = false }
    val playing = state.computers > 0
    // As in battle: on your own live win the result sheet waits for the ribbon to finish.
    val watched = remember(state.calls) { state.winner == null }
    var ribbonDone by remember(state.calls) { mutableStateOf(false) }
    val myWin = state.winner == "me"
    LaunchedEffect(myWin) { if (myWin) { delay(1300); ribbonDone = true } }
    val hold = myWin && watched && !ribbonDone && !LocalReduceMotion.current
    GameFrame(
        title = "Bingo",
        subtitle = if (playing) "Against ${state.computers} computer card${if (state.computers > 1) "s" else ""} · ${state.wins} won" else "On your own",
        players = emptyList(),
        glow = GameKind.BINGO.color,
        menu = soloMenu(GameKind.BINGO, onHome, { showRules = true }, if (playing) vm::again else null),
        scrollable = !playing,
        popup = if (!playing || state.winner == null || hold) null else popupContent {
            SoloPopup(
                GameKind.BINGO, when (state.winner) { "me" -> "me"; "none" -> "none"; else -> "computer" },
                when (state.winner) {
                    "me" -> "Bingo after ${state.called} calls!"
                    "none" -> "The calls ran out."
                    else -> "Computer ${state.winner} called Bingo after ${state.called} calls."
                },
                onAgain = vm::again, onHome = onHome, finishLabel = "Change players", onFinish = vm::changePlayers,
            )
        },
    ) {
        if (!playing) {
            AppCard("How fast are numbers called?") {
                OptionGroup(BingoPace.entries.map { it to it.label }, state.pace, enabled = true, onPick = vm::setPace)
                Text(state.pace.line + ".", color = p.muted, fontSize = TextSize.Body)
            }
            AppCard("How many computer cards?") {
                Text("Dab the called numbers on your card and shout Bingo when you fill a row, a column or a diagonal. The computers never miss a number, but they're slow to shout.", color = p.muted, fontSize = TextSize.Body)
                for (k in 1..3) PrimaryButton("Against $k", { vm.start(k) }, Modifier.fillMaxWidth())
            }
        } else {
            CallStrip(state.calls, state.called)
            BingoCard(state.card, state.dabs, if (state.winner == "me") state.line else null, state.winner == null && state.called > 0, vm::dab, Modifier.fillMaxWidth().weight(1f))
            val sfx = rememberSfx()
            if (state.pace == BingoPace.RELAXED) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("Next number", vm::next, Modifier.weight(1f), enabled = state.winner == null && state.called > 0 && state.called < Bingo.NUMBERS)
                    PrimaryButton("BINGO!", { sfx.play(Sound.BINGO_SHOUT); vm.claim() }, Modifier.weight(1f), enabled = state.winner == null && state.myLine != null)
                }
            } else {
                PrimaryButton("BINGO!", { sfx.play(Sound.BINGO_SHOUT); vm.claim() }, Modifier.fillMaxWidth(), enabled = state.winner == null && state.myLine != null)
            }
        }
    }
}
