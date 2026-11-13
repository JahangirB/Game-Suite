package com.pippop.ui.screens

import com.pippop.ui.startWith
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.alpha
import com.pippop.game.code.CodeRules
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.rememberSfx
import kotlinx.coroutines.delay
import com.pippop.ui.theme.markOf
import com.pippop.ui.theme.drawMark
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.loopingFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pippop.ui.theme.Board
import androidx.compose.foundation.layout.padding
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Pieces
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.CodeGuess
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.game.code.CodeBreaker
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloCodeUiState
import com.pippop.ui.SoloCodeViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme

/** A peg colour. */
fun pegColor(c: Char): Color = when (c) {
    'r' -> Pieces.Red
    'y' -> Pieces.Yellow
    'g' -> Pieces.Green
    'b' -> Pieces.Blue
    'p' -> Pieces.Purple
    'o' -> Pieces.Orange
    'c' -> Pieces.Teal
    else -> Color.LightGray
}

/** What a peg colour is called, for screen readers. */
private fun pegName(c: Char) = when (c) {
    'r' -> "red"; 'y' -> "yellow"; 'g' -> "green"; 'b' -> "blue"; 'p' -> "purple"; 'o' -> "orange"; else -> "teal"
}

/** Whether the player wants a small mark inside each peg (the setting shared by the letter games). */
@Composable
private fun letterMarksOn(): Boolean {
    val store = com.pippop.data.ProfileStore.get(androidx.compose.ui.platform.LocalContext.current)
    return store.profile.collectAsState().value.letterMarks
}

/**
 * A glossy marble of colour [c] (or an empty hole when null). A [seated] marble sits a little
 * smaller in a dark hole of the wooden tray. With [marks] on, each colour has its own shape
 * inside, in dark or light ink, whichever reads better on that colour.
 */
@Composable
private fun Peg(c: Char?, size: Dp, marks: Boolean, modifier: Modifier = Modifier, seated: Boolean = false) {
    val p = AppTheme.palette
    val color = c?.let { pegColor(it) }
    val describe = if (c == null) modifier else modifier.semantics { contentDescription = pegName(c) + " peg" }
    Canvas(describe.size(size)) {
        val r = this.size.minDimension / 2
        when {
            seated -> drawCircle(Board.Wood.darker(0.5f), r, center)
            color == null -> {
                drawCircle(p.accent, r, center)
                drawCircle(p.muted, r - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
        }
        if (color != null) {
            val rm = if (seated) r * 0.86f else r * 0.96f
            if (!seated) roundShadow(center, rm)
            glossyDisc(color, rm, center)
            if (marks) {
                val ink = readableOn(color)
                val mark = markOf(color)
                if (mark != null) drawMark(mark, center, r * 0.96f, ink)
                // The seventh colour (teal) has a ring, which no other piece colour uses.
                else drawCircle(ink, r * 0.4f, center, style = Stroke(r * 0.2f))
            }
        }
    }
}

/** A right colour in the right place. */
private val RightPlace = Pieces.Red

/**
 * The small score pins: red for right colour and place, white for right colour, wrong place.
 * With [drop] on (a guess that just arrived) each pin falls into its hole in turn, a tenth of a
 * second each, red ones first; otherwise (and with reduced motion) they are simply there.
 */
@Composable
private fun ScorePegs(black: Int, white: Int, drop: Boolean = false) {
    val p = AppTheme.palette
    val scored = black + white
    val animate = drop && !LocalReduceMotion.current
    // How many pins have landed so far; pin i lands when this reaches i + 1.
    val landed = remember { Animatable(if (animate) 0f else scored.toFloat()) }
    LaunchedEffect(animate, scored) {
        if (animate) landed.animateTo(scored.toFloat(), tween(scored * 100, easing = LinearEasing))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (col in 0 until 2) {
                val i = row * 2 + col
                // Right colour and place is filled; right colour only is a white pin with a dark
                // ring; so the two read apart by shape as well as colour.
                val isWhite = i in black until scored
                Box(Modifier.size(9.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(p.accent).border(1.dp, p.ink.copy(alpha = 0.5f), CircleShape))
                    if (i < scored) Box(
                        Modifier.size(9.dp)
                            // Falls in from above while it lands; read in the draw step only.
                            .graphicsLayer {
                                val t = (landed.value - i).coerceIn(0f, 1f)
                                alpha = t
                                translationY = -(1f - t) * 12.dp.toPx()
                            }
                            .clip(CircleShape).background(if (isWhite) Color.White else RightPlace)
                            .border(if (isWhite) 2.dp else 1.dp, if (isWhite) Board.Ink else p.ink.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }
        }
    }
}

/** Guesses so far, newest at the bottom; [hidden] shows only the score pegs (the other player's guesses). */
@Composable
internal fun GuessRows(guesses: List<CodeGuess>, modifier: Modifier = Modifier, pegs: Int = CodeBreaker.PEGS) {
    // A scored guess: a click for each red pin, then each white one, and a lock springing open when cracked.
    // Each tick sounds as its pin lands, a tenth of a second apart (the pins fall in ScorePegs).
    SoundOnChange(guesses.size.takeIf { it > 0 }) {
        val g = guesses.last()
        repeat(g.black) {
            delay(100)
            play(Sound.CODE_PIN_RED)
        }
        repeat(g.white) {
            delay(100)
            play(Sound.CODE_PIN_WHITE)
        }
        if (g.black == pegs) play(Sound.CODE_CRACKED)
    }
    val marks = letterMarksOn()
    // Only guesses that arrive while the screen is showing drop their pins in: not the ones
    // already there on opening, and a new game starts counting again from none.
    val watchFrom = remember { intArrayOf(guesses.size) }
    if (guesses.size < watchFrom[0]) watchFrom[0] = 0
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        guesses.forEachIndexed { i, g ->
            key(i, g) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${i + 1}", color = AppTheme.palette.muted, fontSize = TextSize.Label, modifier = Modifier.width(18.dp))
                    // Each guess sits in its own wooden tray, one marble to a hole.
                    Row(
                        Modifier.background(Board.Wood, Shapes.S).padding(horizontal = 5.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        for (k in 0 until pegs) Peg(g.code?.getOrNull(k), 26.dp, marks, seated = true)
                    }
                    ScorePegs(g.black, g.white, drop = i >= watchFrom[0])
                }
            }
        }
    }
}

/** How to read the pins: a secret code and a few guesses at it, scored by the game itself. */
@Composable
fun CodeExamples() {
    val p = AppTheme.palette
    val secret = "rygb"
    val marks = letterMarksOn()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Secret:", color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Body, modifier = Modifier.width(64.dp))
            secret.forEach { Peg(it, 22.dp, marks) }
        }
        listOf("rybg", "gbry", "rrrr", "oppo", "rygb").forEach { guess ->
            val (red, white) = CodeBreaker.score(guess, secret)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.width(64.dp))
                guess.forEach { Peg(it, 22.dp, marks) }
                ScorePegs(red, white)
            }
            Text(
                pinsMeaning(red, white),
                color = p.muted, fontSize = TextSize.Body,
            )
        }
        Text("Pins never say which peg they mean: work it out from guess to guess.", color = p.muted, fontSize = TextSize.Body)
    }
}

/** What [red] and [white] pins say about a guess, in words. */
private fun pinsMeaning(red: Int, white: Int): String {
    if (red == CodeBreaker.PEGS) return "4 red: cracked!"
    if (red + white == 0) return "No pins: none of these colours is in the code."
    val counts = listOfNotNull("$red red".takeIf { red > 0 }, "$white white".takeIf { white > 0 }).joinToString(" and ")
    val reds = if (red == 1) "one peg is the right colour in the right place" else "$red pegs are the right colour in the right place"
    val whites = if (white == 1) "one more colour is in the code but somewhere else" else "$white more colours are in the code but somewhere else"
    return "$counts: " + listOfNotNull(reds.takeIf { red > 0 }, whites.takeIf { white > 0 }).joinToString("; ") + "."
}

/** Four holes to fill from the colour buttons, with Clear and the send button. */
@Composable
internal fun CodeEntry(enabled: Boolean, sendLabel: String, onSend: (String) -> Unit, clearKey: Int = 0, clearButton: Boolean = true, rules: CodeRules = CodeRules()) {
    var code by remember { mutableStateOf("") }
    // The dock's Clear row empties it from outside.
    LaunchedEffect(clearKey) { if (clearKey > 0) code = "" }
    val sfx = rememberSfx()
    val marks = letterMarksOn()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // 48 dp pegs: big enough to hit with a thumb (42 with seven colours, so they fit across).
        val pegSize = if (rules.colours > 6) 42.dp else 48.dp
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in 0 until rules.pegs) Peg(code.getOrNull(k), 48.dp, marks, Modifier.clickable(enabled = enabled && k < code.length) { code = code.removeRange(k, k + 1) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rules.palette.forEach { c ->
                // No repeats: a colour already in the code can't be used again.
                val free = rules.repeats || c !in code
                Peg(c, pegSize, marks, Modifier.alpha(if (free) 1f else 0.3f).clickable(enabled = enabled && free && code.length < rules.pegs) {
                    sfx.play(Sound.CODE_PEG)
                    code += c
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (clearButton) SecondaryButton("Clear", { code = "" }, Modifier.weight(1f), enabled = enabled && code.isNotEmpty())
            PrimaryButton(sendLabel, { onSend(code); code = "" }, Modifier.weight(1f), enabled = enabled && code.length == rules.pegs)
        }
    }
}

// ---- Against your partner ------------------------------------------------------------------

@Composable
fun CodeGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val code = room.codeGame ?: return
    val now = rememberTicker()
    val phase = state.phase(state.serverNow())
    val finished = room.status == RoomStatus.FINISHED
    val opponent = room.opponent
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.CODE, extra = { CodeExamples() }) { showRules = false }
    val name = opponent?.name ?: "The other player"
    var clearKey by remember { mutableIntStateOf(0) }
    val marks = letterMarksOn()
    GameFrame(
        title = "Code Breaker",
        // Harder settings show as a badge for both players.
        subtitle = "Game ${room.round}" + (if (code.rules.harder) " · ${code.rules.label}" else "") + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, p.primary, detail = { _, isMe ->
            val n = if (isMe) code.mine.size else code.theirs.size
            if (code.phase == "set") (if (isMe) (if (code.myCode != null) "Code set" else "Setting…") else if (code.theySet) "Code set" else "Setting…")
            else "$n of ${code.tries} guesses"
        }),
        glow = GameKind.CODE.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave, showLetterMarks = true),
        onReact = if (opponent != null) ({ reacting = !reacting }) else null,
        tools = listOf(DockTool("Clear row", Icons.Filled.Clear, enabled = !finished) { clearKey++ }),
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.CODE, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: name),
                when (room.endReason) {
                    "solved" -> if (room.winner == "me") "Cracked in ${code.mine.size}!" else "$name cracked it in ${code.theirs.size}."
                    "draw" -> "Nobody cracked the code."
                    "forfeit" -> if (room.winner == "me") "$name left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "$name lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
            ) {
                code.theirCode?.let { c ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Their code:", color = p.muted, fontSize = TextSize.Body)
                        c.forEach { Peg(it, 22.dp, marks) }
                    }
                }
            }
        },
    ) {
        when {
            code.phase == "set" && code.myCode == null -> {
                TurnLine("Set a secret code for $name to crack", true, GameKind.CODE.color)
                Box(Modifier.weight(1f))
                CodeEntry(!state.firing, "Set my code", vm::sendCode, clearKey = clearKey, clearButton = false, rules = code.rules)
            }
            code.phase == "set" -> {
                TurnLine("Waiting for $name to set their code…", false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    Text("Your code:", color = p.muted, fontSize = TextSize.Body)
                    code.myCode.orEmpty().forEach { Peg(it, 24.dp, marks) }
                }
            }
            else -> {
                val countdown = phase == RoomStatus.COUNTDOWN
                TurnLine(
                    when {
                        countdown -> "Get ready… " + (((room.startAt ?: 0) - state.serverNow() + 999) / 1000).coerceAtLeast(1)
                        code.meDone -> if (code.mine.lastOrNull()?.black == CodeBreaker.PEGS) "Cracked! Waiting for $name…" else "Out of guesses. Waiting for $name…"
                        else -> "Crack $name's code"
                    },
                    !countdown && !code.meDone, GameKind.CODE.color,
                )
                // The safe: yours big, your partner's small in the corner, so you see theirs open too.
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    CodeSafe(code.mine, code.rules.pegs, Modifier.weight(1f).height(108.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CodeSafe(code.theirs, code.rules.pegs, Modifier.size(64.dp))
                        Text(name, color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GuessRows(code.mine, Modifier.weight(1f), pegs = code.rules.pegs)
                    // Their progress: only the scores, never their guesses.
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                        code.theirs.forEach { ScorePegs(it.black, it.white) }
                    }
                }
                CodeEntry(!countdown && !code.meDone && !state.firing && !finished, "Guess", vm::sendCode, clearKey = clearKey, clearButton = false, rules = code.rules)
            }
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloCodeScreen(state: SoloCodeUiState, vm: SoloCodeViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.CODE, extra = { CodeExamples() }) { showRules = false }
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    if (!state.started) { BackToHome(onHome); return }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.CODE, "New game", vm::startWith) { changing = false }
    GameFrame(
        title = "Code Breaker",
        subtitle = state.best?.let { "On your own · best $it" } ?: "On your own",
        players = emptyList(),
        glow = GameKind.CODE.color,
        menu = GameMenu(
            GameKind.CODE, onRules = { showRules = true }, onLeave = onHome, onNewGame = vm::start, onChangeMode = { changing = true }, changeLabel = "Change setup",
            leaveLabel = "Home", showLetterMarks = true,
        ),
        popup = if (!state.done) null else popupContent {
            ResultPopup(
                game = GameKind.CODE,
                outcome = if (state.solved) Outcome.WIN else Outcome.LOSE,
                title = if (state.solved) "Cracked in ${state.guesses.size}!" else "Out of guesses",
                detail = "The code was " + state.secret.map { mapOf('r' to "red", 'y' to "yellow", 'g' to "green", 'b' to "blue", 'p' to "purple", 'o' to "orange", 'c' to "teal")[it] }.joinToString(", ") + ".",
                finish = PopupAction("Home", onHome),
                again = PopupAction("New code", vm::start),
            )
        },
    ) {
        TurnLine("Guess ${state.guesses.size + 1} of ${CodeBreaker.TRIES}" + if (state.rules.harder) " · ${state.rules.label}" else "", !state.done, GameKind.CODE.color)
        CodeSafe(state.guesses, state.rules.pegs, Modifier.fillMaxWidth().height(108.dp))
        GuessRows(state.guesses, Modifier.weight(1f).fillMaxWidth(), pegs = state.rules.pegs)
        CodeEntry(!state.done, "Guess", vm::guess, rules = state.rules)
    }
}

/** Code Breaker's settings: code length, colours, and whether colours can repeat. */
@Composable
fun CodeRulesPicker(rules: CodeRules, enabled: Boolean, onChange: (CodeRules) -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pegs", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
        OptionGroup(listOf(4 to "4 pegs", 5 to "5 pegs"), rules.pegs, enabled = enabled) { onChange(rules.copy(pegs = it)) }
        Text("Colours", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
        OptionGroup(listOf(6 to "6 colours", 7 to "7 colours"), rules.colours, enabled = enabled) { onChange(rules.copy(colours = it)) }
        Text("Repeats", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
        OptionGroup(listOf(true to "Allowed", false to "None"), rules.repeats, enabled = enabled) { onChange(rules.copy(repeats = it)) }
    }
}

/** The settings card in the lobby and the Change sheet: picked by whoever opened the game. */
@Composable
fun CodeRulesCard(room: com.pippop.data.RoomState, busy: Boolean, onPick: (CodeRules) -> Unit) {
    val p = AppTheme.palette
    AppCard("Code") {
        if (room.setsOptions) CodeRulesPicker(room.codeRules, enabled = !busy, onChange = onPick)
        else Text("${room.codeRules.label}, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
    }
}

/**
 * The safe your guesses go into: its dial turns a click for each red pin of every guess, its
 * row of lights shows the best guess so far (one lit per red pin), and when the code is cracked
 * the bolts slide back and the door swings open on the gold inside.
 */
@Composable
fun CodeSafe(guesses: List<CodeGuess>, pegs: Int, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val reduce = com.pippop.ui.theme.LocalReduceMotion.current
    val clicks = guesses.sumOf { it.black + 1 }
    val dial by androidx.compose.animation.core.animateFloatAsState(clicks * 36f, androidx.compose.animation.core.tween(if (reduce) 0 else 700), label = "dial")
    // A slow idle drift on top of the clicks; still with reduced motion. Read only while drawing.
    val drift = loopingFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), 0f, "dialDrift")
    val cracked = guesses.any { it.black == pegs }
    val open by androidx.compose.animation.core.animateFloatAsState(if (cracked) 1f else 0f, androidx.compose.animation.core.tween(if (reduce) 0 else 900, delayMillis = 500), label = "door")
    val best = guesses.maxOfOrNull { it.black } ?: 0
    val steel = Pieces.SafeSteel
    val dark = Pieces.SafeDark
    val gold = Pieces.Gold
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2
        val box = androidx.compose.ui.geometry.Size(side, side)
        val corner = androidx.compose.ui.geometry.CornerRadius(side * 0.08f)
        // The body and the gold inside, seen once the door swings away.
        drawRoundRect(dark, Offset(left, 0f), box, corner)
        drawRoundRect(gold, Offset(left + side * 0.1f, side * 0.1f), androidx.compose.ui.geometry.Size(side * 0.8f, side * 0.8f), androidx.compose.ui.geometry.CornerRadius(side * 0.05f))
        for (k in 0 until 3) drawRoundRect(Pieces.GoldBar, Offset(left + side * (0.18f + k * 0.22f), side * 0.55f), androidx.compose.ui.geometry.Size(side * 0.18f, side * 0.12f), androidx.compose.ui.geometry.CornerRadius(side * 0.02f))
        // The door narrows towards its hinge as it opens.
        val w = side * (1f - 0.88f * open)
        drawRoundRect(steel, Offset(left, 0f), androidx.compose.ui.geometry.Size(w, side), corner)
        drawRoundRect(p.ink, Offset(left, 0f), androidx.compose.ui.geometry.Size(w, side), corner, style = Stroke(side * 0.025f))
        if (open < 0.6f) {
            val c = Offset(left + w * 0.5f, side * 0.44f)
            val r = side * 0.24f
            // The dial, with ticks that turn.
            drawCircle(dark, r * 1.08f, c)
            drawCircle(Pieces.SafeDial, r, c)
            for (t in 0 until 12) {
                val a = Math.toRadians((dial + drift.value + t * 30).toDouble())
                val inner = if (t == 0) 0.55f else 0.75f
                drawLine(p.ink, Offset(c.x + (r * inner * kotlin.math.cos(a)).toFloat(), c.y + (r * inner * kotlin.math.sin(a)).toFloat()), Offset(c.x + (r * 0.95f * kotlin.math.cos(a)).toFloat(), c.y + (r * 0.95f * kotlin.math.sin(a)).toFloat()), side * if (t == 0) 0.03f else 0.012f)
            }
            drawCircle(dark, r * 0.2f, c)
            // The lights: one lit for each red pin of the best guess.
            val lw = side * 0.08f
            val gap = (w * 0.8f - pegs * lw) / (pegs - 1).coerceAtLeast(1)
            for (k in 0 until pegs) {
                val x = left + w * 0.1f + k * (lw + gap) + lw / 2
                drawCircle(if (k < best) Pieces.Red else Pieces.SafeLightOff, lw / 2, Offset(x, side * 0.84f))
            }
            // The bolts slide back as it opens.
            val slide = open * side * 0.2f
            drawRoundRect(dark, Offset(left + w - side * 0.05f - slide, side * 0.2f), androidx.compose.ui.geometry.Size(side * 0.08f, side * 0.06f), androidx.compose.ui.geometry.CornerRadius(4f))
            drawRoundRect(dark, Offset(left + w - side * 0.05f - slide, side * 0.66f), androidx.compose.ui.geometry.Size(side * 0.08f, side * 0.06f), androidx.compose.ui.geometry.CornerRadius(4f))
        }
    }
}
