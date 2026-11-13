package com.pippop.ui.screens

import com.pippop.ui.startWith
import com.pippop.ui.Sfx
import com.pippop.ui.SoundOnChange
import com.pippop.ui.Sound
import com.pippop.ui.rememberSfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.GameColors
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.Pieces
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.GameKind
import com.pippop.data.RoomStatus
import com.pippop.game.balloon.BalloonView
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloBalloonUiState
import com.pippop.ui.SoloBalloonViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import kotlin.math.cos
import kotlin.math.sin

private val BalloonColors = listOf(
    Pieces.Red, Pieces.Yellow, Pieces.Green, Pieces.Blue,
    Pieces.Purple, Pieces.Orange, Pieces.Pink,
)

/**
 * A row of [total] balloons on strings, swaying a little in front of a drifting-cloud sky; the first [popped] have burst. A
 * balloon that has just popped swells and wobbles, bursts with a flash, and its scraps fly
 * out and fall while the string drops.
 */
@Composable
fun BalloonRow(total: Int, popped: Int, modifier: Modifier = Modifier) {
    val ink = AppTheme.palette.ink
    // Kept as State and read only while drawing, so the loops redraw the picture without recomposing.
    val swayState = loopingFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(3200, easing = LinearEasing)), still = 0f, label = "sway")
    // The clouds drift once across the sky in a minute; with reduced motion they stand still.
    val driftState = loopingFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), still = 0.3f, label = "drift")
    val night = AppTheme.palette.dark
    // The pop being played: which balloon, and how far along (0 to 1).
    var before by remember { mutableStateOf(popped) }
    var bursting by remember { mutableStateOf<Int?>(null) }
    val pop = remember { Animatable(1f) }
    val sfx = rememberSfx()
    LaunchedEffect(popped) {
        if (popped > before) {
            bursting = popped - 1
            pop.snapTo(0f)
            // The rubber squeaks as it swells, then pops as it bursts.
            sfx.play(Sound.BALLOON_STRETCH)
            launch {
                delay((POP_MS * SWELL).toLong())
                sfx.play(Sound.BALLOON_POP)
            }
            pop.animateTo(1f, tween(POP_MS, easing = LinearEasing))
        }
        before = popped
        bursting = null
    }
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val sway = swayState.value
        // Strings are drawn a little stronger on the night sky so they stay visible.
        val stringA = if (night) 1f else 0.6f
        val limpA = if (night) 1f else 0.55f
        sky(driftState.value, night)
        val gap = size.width / total
        for (i in 0 until total) {
            val r = minOf(gap * 0.38f, size.height * 0.24f)
            val bob = sin(sway + i * 0.9f)
            val cx = gap * (i + 0.5f) + bob * r * 0.08f
            val cy = r * 1.3f + 6f + (if (i % 2 == 0) 0f else r * 0.35f) + cos(sway + i) * r * 0.06f
            val color = BalloonColors[i % BalloonColors.size]
            val t = if (i == bursting) pop.value else null
            when {
                t != null && t < SWELL -> {
                    // Swelling and trembling before it goes.
                    val k = t / SWELL
                    val grow = 1f + 0.22f * k
                    val shake = sin(k * 40f) * r * 0.08f * k
                    balloon(cx + shake, cy, r * grow, color, ink, sway + i, stringA)
                }
                t != null -> {
                    val k = (t - SWELL) / (1f - SWELL)
                    // The flash: rays out from the middle, fading.
                    if (k < 0.35f) {
                        val f = k / 0.35f
                        for (ray in 0 until 8) {
                            val a = ray * Math.PI / 4
                            val from = r * (0.4f + f)
                            val to = r * (0.9f + 1.2f * f)
                            drawLine(
                                Board.GlowSoft.copy(alpha = 1f - f), Offset(cx + (from * cos(a)).toFloat(), cy + (from * sin(a)).toFloat()),
                                Offset(cx + (to * cos(a)).toFloat(), cy + (to * sin(a)).toFloat()), 4f,
                            )
                        }
                    }
                    shreds(cx, cy, r, color, i, k, size.height)
                    // The string falls away.
                    // It settles into the limp string it will rest as, so nothing snaps at the end.
                    val rest = limpString(cx, r)
                    val fall = Path().apply {
                        val a = lerp(Offset(cx, cy + r + k * size.height * 0.5f), rest[0], k)
                        val c = lerp(Offset(cx - 1.5f, (cy + r + size.height) / 2f), rest[1], k)
                        val b = lerp(Offset(cx - 3f, size.height), rest[2], k)
                        moveTo(a.x, a.y)
                        quadraticBezierTo(c.x, c.y, b.x, b.y)
                    }
                    drawPath(fall, ink.copy(alpha = stringA + (limpA - stringA) * k), style = Stroke(2f))
                }
                i < popped -> {
                    // Long gone: the scraps lie at the bottom.
                    shreds(cx, cy, r, color, i, 1f, size.height)
                    // Its string stays, limp on the ground.
                    val rest = limpString(cx, r)
                    val limp = Path().apply {
                        moveTo(rest[0].x, rest[0].y)
                        quadraticBezierTo(rest[1].x, rest[1].y, rest[2].x, rest[2].y)
                    }
                    drawPath(limp, ink.copy(alpha = limpA), style = Stroke(2f))
                }
                else -> balloon(cx, cy, r, color, ink, sway + i, stringA)
            }
        }
    }
}

/** How long a pop takes, and how much of it the swelling is. */
private const val POP_MS = 1100
private const val SWELL = 0.3f

/** The three points (start, bend, end) of the limp string a popped balloon leaves lying at the bottom. */
private fun DrawScope.limpString(cx: Float, r: Float) =
    listOf(Offset(cx - r * 0.5f, size.height - 2f), Offset(cx, size.height - 16f), Offset(cx + r * 0.5f, size.height - 3f))

/** The sky behind the balloons: a soft gradient (deep blue in dark mode) with three clouds drifting by at [drift] (0 to 1). */
private fun DrawScope.sky(drift: Float, night: Boolean) {
    val top = if (night) GameColors.BalloonSkyNightTop else GameColors.BalloonSkyTop
    val bottom = if (night) GameColors.BalloonSkyNightBottom else GameColors.BalloonSkyBottom
    drawRoundRect(Brush.verticalGradient(listOf(top, bottom)), size = size, cornerRadius = CornerRadius(Radius.M.toPx()))
    val cloud = if (night) GameColors.BalloonCloudNight.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.8f)
    val span = size.width * 1.4f
    // The clouds stay inside the panel's rounded corners.
    clipPath(Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(Radius.M.toPx()))) }) {
        for (n in 0 until 3) {
            val w = size.height * (0.55f + 0.15f * n)
            val x = ((drift + n / 3f) % 1f) * span - w
            val y = size.height * (0.22f + 0.28f * ((n * 2) % 3) / 2f)
            drawCircle(cloud, w * 0.22f, Offset(x + w * 0.25f, y))
            drawCircle(cloud, w * 0.3f, Offset(x + w * 0.5f, y - w * 0.08f))
            drawCircle(cloud, w * 0.22f, Offset(x + w * 0.75f, y))
            drawRoundRect(cloud, Offset(x + w * 0.1f, y), Size(w * 0.8f, w * 0.18f), CornerRadius(w * 0.09f))
        }
    }
}

/** A glossy balloon with a knot, hanging from a curved string that sways with [phase]. */
private fun DrawScope.balloon(cx: Float, cy: Float, r: Float, color: Color, ink: Color, phase: Float, stringAlpha: Float) {
    val string = Path().apply {
        moveTo(cx, cy + r + 6f)
        quadraticBezierTo(cx + sin(phase) * r * 0.3f, cy + r + (size.height - cy - r) * 0.5f, cx - 3f, size.height)
    }
    drawPath(string, ink.copy(alpha = stringAlpha), style = Stroke(2f))
    val light = Offset(cx - r * 0.35f, cy - r * 0.4f)
    drawOval(
        Brush.radialGradient(0f to color.lighter(0.5f), 0.5f to color, 1f to color.darker(0.25f), center = light, radius = r * 1.6f),
        Offset(cx - r * 0.85f, cy - r), Size(r * 1.7f, r * 2f),
    )
    drawOval(ink, Offset(cx - r * 0.85f, cy - r), Size(r * 1.7f, r * 2f), style = Stroke(2.5f))
    drawCircle(Color.White.copy(alpha = 0.45f), r * 0.22f, Offset(cx - r * 0.35f, cy - r * 0.4f))
    val knot = Path().apply { moveTo(cx - 5f, cy + r + 6f); lineTo(cx + 5f, cy + r + 6f); lineTo(cx, cy + r - 2f); close() }
    drawPath(knot, color)
}

/** The burst balloon's scraps, [k] (0 to 1) of the way from flying out to lying on the ground. */
private fun DrawScope.shreds(cx: Float, cy: Float, r: Float, color: Color, seed: Int, k: Float, floor: Float) {
    for (n in 0 until 7) {
        val a = Math.toRadians((n * 51 + seed * 23).toDouble())
        val out = r * (0.4f + 1.1f * minOf(1f, k * 2.5f))
        val x = cx + (out * cos(a)).toFloat()
        // Out first, then falling to the ground.
        val fall = maxOf(0f, k - 0.25f) / 0.75f
        val y0 = cy + (out * sin(a)).toFloat()
        val y = y0 + (floor - 6f - y0) * fall * fall
        val w = r * (0.28f + 0.08f * (n % 3))
        rotate(n * 40f + k * 220f, Offset(x, y)) {
            drawRoundRect(color, Offset(x - w / 2, y - w / 4), Size(w, w / 2), CornerRadius(w / 4))
        }
    }
}

/** The word as tiles: found letters shown, blanks empty. */
@Composable
internal fun WordTiles(pattern: String, reveal: String? = null) {
    val p = AppTheme.palette
    // A letter found drops into its tile with a tok and a ting.
    SoundOnChange(pattern.count { it != '_' }.takeIf { it > 0 }) {
        delay(150)
        play(Sound.LETTER_DROP)
        play(Sound.LETTER_RIGHT)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        pattern.forEachIndexed { i, c ->
            val shown = if (c != '_') c else reveal?.getOrNull(i)
            Surface(
                color = if (c != '_') p.card else if (shown != null) p.accent else p.card,
                shape = Shapes.S, border = BorderStroke(2.dp, p.ink),
                // A letter found drops into its tile.
                modifier = Modifier.weight(1f, fill = false).height(44.dp).then(if (c != '_') Modifier.dropIn(c, delayMs = 150) else Modifier),
            ) {
                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                    Text((shown ?: ' ').toString(), color = if (c == '_') p.muted else p.text, fontSize = TextSize.Title, fontWeight = FontWeight.Black, fontFamily = DisplayFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** A–Z keys: letters in the word turn green, wrong ones are greyed and struck through. */
@Composable
internal fun LetterKeys(guessed: List<Char>, word: String, enabled: Boolean, onLetter: (Char) -> Unit) {
    val p = AppTheme.palette
    val sfx = rememberSfx()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    val tried = c in guessed
                    val hit = tried && c in word
                    Surface(
                        color = when {
                            hit -> p.good
                            tried -> p.accent
                            else -> p.card
                        },
                        shape = Shapes.S, border = BorderStroke(2.dp, if (tried) p.muted else p.ink),
                        modifier = Modifier.weight(1f).height(48.dp)
                            .semantics { if (tried) stateDescription = if (hit) "in the word" else "not in the word" }
                            .clickable(enabled = enabled && !tried) {
                            sfx.play(Sound.LETTER_TYPE)
                            onLetter(c)
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // A wrong letter is struck through as well as greyed, so it doesn't rely on colour alone.
                            Text(
                                c.toString(), color = if (hit) readableOn(p.good) else if (tried) p.muted else p.text,
                                fontWeight = FontWeight.Bold, fontSize = TextSize.BodyL,
                                textDecoration = if (tried && !hit) TextDecoration.LineThrough else null,
                            )
                        }
                    }
                }
                repeat(7 - row.length) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Type a secret word; the letters stay hidden as you type. */
@Composable
private fun PickWord(prompt: String, onPick: (String) -> Unit) {
    val p = AppTheme.palette
    var word by rememberSaveable { mutableStateOf("") }
    AppCard(prompt) {
        ToyField(
            value = word,
            onValueChange = { v -> word = v.filter { it.isLetter() }.take(12) },
            singleLine = true,
            label = "Secret word (3 to 12 letters)",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Any word works: names and in-jokes too. Nobody else sees it.", color = p.muted, fontSize = TextSize.Body)
        PrimaryButton("Hide it in the balloons", { onPick(word); word = "" }, Modifier.fillMaxWidth(), enabled = word.length >= 3)
    }
}

// ---- Against your partners -----------------------------------------------------------------

@Composable
fun BalloonGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val view = turns.balloon ?: return
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me" && !finished
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.BALLOON) { showRules = false }
    var showUsed by remember { mutableStateOf(false) }
    if (showUsed) InfoSheet("Used letters", onDismiss = { showUsed = false }) {
        val inWord = view.guessed.filter { it in view.pattern }.sorted()
        LetterTiles("IN THE WORD", inWord, GameKind.BALLOON.color)
        LetterTiles("NOT IN IT (A BALLOON EACH)", view.guessed.filter { it !in view.pattern }.sorted(), AppTheme.palette.hairline)
        Text("${view.balloons - view.misses} of ${view.balloons} balloons left.", color = AppTheme.palette.muted, fontSize = TextSize.Body)
    }
    fun letterOf(index: Int) = "opq"[index]
    fun name(mark: Char?) = when (mark) {
        null -> "Nobody"
        'm' -> "You"
        else -> room.others.getOrNull("opq".indexOf(mark))?.name ?: "Someone"
    }
    val turnName = turns.turnName ?: room.opponent?.name

    GameFrame(
        title = "Balloon Words",
        subtitle = "Round ${view.roundNo} of ${view.rounds}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, MyDisc,
            detail = { player, isMe ->
                val mark = if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0))
                (if (view.picker == mark) "Picker · " else "") + "${view.scores[mark] ?: 0} pts"
            },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> markColor(letterOf(i)) },
        ),
        glow = GameKind.BALLOON.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        tools = listOf(DockTool("Used letters", GameIcons.Sort, badge = view.guessed.size) { showUsed = true }),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.BALLOON,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "The other player"),
                when (room.endReason) {
                    "points" -> "Everyone has picked a word: the most points wins."
                    "draw" -> "Top scores are level."
                    "left" -> "A player left, so the game stopped."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = listOf("You ${view.scores['m'] ?: 0}") + room.others.mapIndexed { i, o -> "${o.name} ${view.scores[letterOf(i)] ?: 0}" },
            )
        },
    ) {
        view.lastRound?.takeIf { view.phase == "pick" }?.let { (word, picker, by) ->
            Text(
                if (by != null) "${name(by)} finished $word!" else "The balloons popped: it was $word. ${name(picker)} scores 5.",
                color = p.muted, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        when {
            view.phase == "pick" && view.picker == 'm' && !finished -> PickWord("Your turn to pick a word", vm::pickBalloonWord)
            view.phase == "pick" -> TurnLine("${name(view.picker)} is picking a secret word…", false)
            else -> BalloonRound(view, myTurn && !state.firing, if (view.picker == 'm') "Your word: they're guessing" else if (myTurn) "Your turn: pick a letter" else "$turnName is guessing…", vm::guessBalloonLetter)
        }
    }
}

@Composable
private fun ColumnScope.BalloonRound(view: BalloonView, canGuess: Boolean, line: String, onLetter: (Char) -> Unit) {
    TurnLine(line, canGuess, GameKind.BALLOON.color)
    BalloonRow(view.balloons, view.misses)
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { WordTiles(view.pattern, view.word) }
    LetterKeys(view.guessed, view.word ?: view.pattern, canGuess, onLetter)
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloBalloonScreen(state: SoloBalloonUiState, vm: SoloBalloonViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.BALLOON) { showRules = false }
    val result = state.result
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.BALLOON, "New game", vm::startWith) { changing = false }
    GameFrame(
        title = "Balloon Words",
        subtitle = "On your own" + if (state.streak > 0) " · streak ${state.streak}" else "",
        players = emptyList(),
        glow = GameKind.BALLOON.color,
        menu = soloMenu(GameKind.BALLOON, onHome, { showRules = true }, if (state.started) (if (state.youGuess) vm::guessTheirs else vm::setup) else null, if (state.started) ({ changing = true }) else null),
        scrollable = !state.started,
        popup = if (result == null) null else popupContent {
            val won = (result == "found") == state.youGuess
            ResultPopup(
                game = GameKind.BALLOON,
                outcome = if (won) Outcome.WIN else Outcome.LOSE,
                title = when {
                    state.youGuess && won -> "You found it!"
                    state.youGuess -> "Pop! Out of balloons"
                    won -> "It couldn't find your word!"
                    else -> "The computer found it"
                },
                detail = "The word was ${state.word}.",
                finish = PopupAction("Change", { changing = true }),
                again = PopupAction("Next word", if (state.youGuess) vm::guessTheirs else vm::setup),
            )
        },
    ) {
        if (!state.started) {
            // "Computer guesses" was chosen in the sheet: the word is the one thing left to give.
            PickWord("Give the computer a word to guess", vm::giveYours)
            state.message?.let { Text(it, color = p.bad, fontSize = TextSize.Body) }
        } else {
            TurnLine(if (state.youGuess) "Pick a letter" else "The computer is guessing your word…", state.youGuess && result == null, GameKind.BALLOON.color)
            BalloonRow(7, state.misses)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                WordTiles(state.pattern, if (state.youGuess && result == null) null else state.word)
            }
            LetterKeys(state.guessed, state.word, state.youGuess && result == null, vm::guess)
        }
    }
}
