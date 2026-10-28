package com.pippop.ui.screens

import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.rememberSfx
import kotlinx.coroutines.delay
import com.pippop.ui.theme.markOf
import com.pippop.ui.theme.MarkIcon
import com.pippop.ui.theme.Board
import androidx.compose.foundation.layout.fillMaxSize
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
    else -> Color.LightGray
}

@Composable
private fun Peg(c: Char?, size: Dp, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Box(
        modifier.size(size).clip(CircleShape).background(if (c == null) p.accent else pegColor(c))
            .border(2.dp, if (c == null) p.muted else p.ink, CircleShape),
    ) {
        // Each colour's mark, so pegs can be told apart without seeing colour.
        val mark = c?.let { markOf(pegColor(it)) }
        if (mark != null) MarkIcon(mark, Modifier.fillMaxSize().padding(size * 0.26f))
    }
}

/** A right colour in the right place. */
private val RightPlace = Pieces.Red

/** The small score pins: red for right colour and place, white for right colour, wrong place. */
@Composable
private fun ScorePegs(black: Int, white: Int) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (col in 0 until 2) {
                val i = row * 2 + col
                val fill = when {
                    i < black -> RightPlace
                    i < black + white -> Color.White
                    else -> p.accent
                }
                // Right colour and place is filled; right colour only is a white pin with a dark
                // ring; so the two read apart by shape as well as colour.
                val ring = if (i in black until black + white) 2.dp else 1.dp
                val ringColor = if (i in black until black + white) Board.Ink else p.ink.copy(alpha = 0.5f)
                Box(Modifier.size(9.dp).clip(CircleShape).background(fill).border(ring, ringColor, CircleShape))
            }
        }
    }
}

/** Guesses so far, newest at the bottom; [hidden] shows only the score pegs (the other player's guesses). */
@Composable
internal fun GuessRows(guesses: List<CodeGuess>, modifier: Modifier = Modifier) {
    // A scored guess: a click for each red pin, then each white one, and a lock springing open when cracked.
    SoundOnChange(guesses.size.takeIf { it > 0 }) {
        val g = guesses.last()
        repeat(g.black) {
            play(Sound.CODE_PIN_RED)
            delay(120)
        }
        repeat(g.white) {
            play(Sound.CODE_PIN_WHITE)
            delay(120)
        }
        if (g.black == CodeBreaker.PEGS) play(Sound.CODE_CRACKED)
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        guesses.forEachIndexed { i, g ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${i + 1}", color = AppTheme.palette.muted, fontSize = TextSize.Label, modifier = Modifier.width(18.dp))
                for (k in 0 until CodeBreaker.PEGS) Peg(g.code?.getOrNull(k), 26.dp)
                ScorePegs(g.black, g.white)
            }
        }
    }
}

/** How to read the pins: a secret code and a few guesses at it, scored by the game itself. */
@Composable
fun CodeExamples() {
    val p = AppTheme.palette
    val secret = "rygb"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Secret:", color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Body, modifier = Modifier.width(64.dp))
            secret.forEach { Peg(it, 22.dp) }
        }
        listOf("rybg", "gbry", "rrrr", "oppo", "rygb").forEach { guess ->
            val (red, white) = CodeBreaker.score(guess, secret)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.width(64.dp))
                guess.forEach { Peg(it, 22.dp) }
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
private fun CodeEntry(enabled: Boolean, sendLabel: String, onSend: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val sfx = rememberSfx()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // 48 dp pegs: big enough to hit with a thumb.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in 0 until CodeBreaker.PEGS) Peg(code.getOrNull(k), 48.dp, Modifier.clickable(enabled = enabled && k < code.length) { code = code.removeRange(k, k + 1) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CodeBreaker.COLOURS.forEach { c ->
                Peg(c, 48.dp, Modifier.clickable(enabled = enabled && code.length < CodeBreaker.PEGS) {
                    sfx.play(Sound.CODE_PEG)
                    code += c
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Clear", { code = "" }, Modifier.weight(1f), enabled = enabled && code.isNotEmpty())
            PrimaryButton(sendLabel, { onSend(code); code = "" }, Modifier.weight(1f), enabled = enabled && code.length == CodeBreaker.PEGS)
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
    GameFrame(
        title = "Code Breaker",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, p.primary, detail = { _, isMe ->
            val n = if (isMe) code.mine.size else code.theirs.size
            if (code.phase == "set") (if (isMe) (if (code.myCode != null) "Code set" else "Setting…") else if (code.theySet) "Code set" else "Setting…")
            else "$n of ${code.tries} guesses"
        }),
        glow = GameKind.CODE.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
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
                        c.forEach { Peg(it, 22.dp) }
                    }
                }
            }
        },
    ) {
        when {
            code.phase == "set" && code.myCode == null -> {
                TurnLine("Set a secret code for $name to crack", true, GameKind.CODE.color)
                Box(Modifier.weight(1f))
                CodeEntry(!state.firing, "Set my code", vm::sendCode)
            }
            code.phase == "set" -> {
                TurnLine("Waiting for $name to set their code…", false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    Text("Your code:", color = p.muted, fontSize = TextSize.Body)
                    code.myCode.orEmpty().forEach { Peg(it, 24.dp) }
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
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GuessRows(code.mine, Modifier.weight(1f))
                    // Their progress: only the scores, never their guesses.
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                        code.theirs.forEach { ScorePegs(it.black, it.white) }
                    }
                }
                CodeEntry(!countdown && !code.meDone && !state.firing && !finished, "Guess", vm::sendCode)
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
    GameFrame(
        title = "Code Breaker",
        subtitle = state.best?.let { "On your own · best $it" } ?: "On your own",
        players = emptyList(),
        glow = GameKind.CODE.color,
        dock = soloDock(onHome, { showRules = true }, if (state.started) vm::start else null),
        scrollable = !state.started,
        popup = if (!state.started || !state.done) null else popupContent {
            ResultPopup(
                game = GameKind.CODE,
                outcome = if (state.solved) Outcome.WIN else Outcome.LOSE,
                title = if (state.solved) "Cracked in ${state.guesses.size}!" else "Out of guesses",
                detail = "The code was " + state.secret.map { mapOf('r' to "red", 'y' to "yellow", 'g' to "green", 'b' to "blue", 'p' to "purple", 'o' to "orange")[it] }.joinToString(", ") + ".",
                finish = PopupAction("Home", onHome),
                again = PopupAction("New code", vm::start),
            )
        },
    ) {
        if (!state.started) {
            AppCard("Crack the code") {
                Text(
                    "The computer hides four coloured pegs (colours can repeat). After each guess: a red pin for each right colour in the right place, a white pin for a right colour in the wrong place. Ten guesses.",
                    color = p.muted, fontSize = TextSize.Body,
                )
                CodeExamples()
                PrimaryButton("Start", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            TurnLine("Guess ${state.guesses.size + 1} of ${CodeBreaker.TRIES}", !state.done, GameKind.CODE.color)
            GuessRows(state.guesses, Modifier.weight(1f).fillMaxWidth())
            CodeEntry(!state.done, "Guess", vm::guess)
        }
    }
}
