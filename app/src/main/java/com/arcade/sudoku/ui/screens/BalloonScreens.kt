package com.arcade.sudoku.ui.screens

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.balloon.BalloonView
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloBalloonUiState
import com.arcade.sudoku.ui.SoloBalloonViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont

private val BalloonColors = listOf(
    Color(0xFFE8505B), Color(0xFFFFC93C), Color(0xFF20A47B), Color(0xFF3D7BF7),
    Color(0xFF7B6CF0), Color(0xFFFF8A3D), Color(0xFFEC6FA9),
)

/** A row of [total] balloons on strings; the first [popped] have burst. */
@Composable
fun BalloonRow(total: Int, popped: Int, modifier: Modifier = Modifier) {
    val ink = AppTheme.palette.ink
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val gap = size.width / total
        for (i in 0 until total) {
            val cx = gap * (i + 0.5f)
            val r = minOf(gap * 0.38f, size.height * 0.24f)
            val cy = r + 6f + if (i % 2 == 0) 0f else r * 0.35f
            val color = BalloonColors[i % BalloonColors.size]
            if (i < popped) {
                // Burst: a few scraps where the balloon was, and a loose string.
                for (k in 0 until 5) {
                    val a = Math.toRadians((k * 72 + i * 20).toDouble())
                    drawCircle(color, r * 0.18f, Offset(cx + (r * 0.7f * Math.cos(a)).toFloat(), cy + (r * 0.7f * Math.sin(a)).toFloat()))
                }
                drawLine(ink.copy(alpha = 0.35f), Offset(cx, cy + r * 0.6f), Offset(cx + 4f, size.height), 2f)
            } else {
                drawLine(ink.copy(alpha = 0.6f), Offset(cx, cy + r), Offset(cx - 3f, size.height), 2f)
                drawOval(color, Offset(cx - r * 0.85f, cy - r), androidx.compose.ui.geometry.Size(r * 1.7f, r * 2f))
                drawOval(ink, Offset(cx - r * 0.85f, cy - r), androidx.compose.ui.geometry.Size(r * 1.7f, r * 2f), style = Stroke(2.5f))
                drawCircle(Color.White.copy(alpha = 0.45f), r * 0.22f, Offset(cx - r * 0.35f, cy - r * 0.4f))
                val knot = Path().apply { moveTo(cx - 5f, cy + r + 6f); lineTo(cx + 5f, cy + r + 6f); lineTo(cx, cy + r - 2f); close() }
                drawPath(knot, color)
            }
        }
    }
}

/** The word as tiles: found letters shown, blanks empty. */
@Composable
private fun WordTiles(pattern: String, reveal: String? = null) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        pattern.forEachIndexed { i, c ->
            val shown = if (c != '_') c else reveal?.getOrNull(i)
            Surface(
                color = if (c != '_') p.card else if (shown != null) p.accent else p.card,
                shape = RoundedCornerShape(8.dp), border = BorderStroke(2.dp, p.ink),
                modifier = Modifier.weight(1f, fill = false).height(44.dp),
            ) {
                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                    Text((shown ?: ' ').toString(), color = if (c == '_') p.muted else p.text, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** A–Z keys: letters already tried are greyed, hits in green. */
@Composable
private fun LetterKeys(guessed: List<Char>, word: String, enabled: Boolean, onLetter: (Char) -> Unit) {
    val p = AppTheme.palette
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
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(2.dp, if (tried) p.muted else p.ink),
                        modifier = Modifier.weight(1f).height(42.dp).clickable(enabled = enabled && !tried) { onLetter(c) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(c.toString(), color = if (hit) Color.White else if (tried) p.muted else p.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
        OutlinedTextField(
            value = word,
            onValueChange = { v -> word = v.filter { it.isLetter() }.take(12) },
            singleLine = true,
            label = { Text("Secret word (3 to 12 letters)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Any word works: names and in-jokes too. Nobody else sees it.", color = p.muted, fontSize = 13.sp)
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
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.BALLOON,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "Your opponent"),
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
                color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
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
    GameFrame(
        title = "Balloon Words",
        subtitle = "On your own" + if (state.streak > 0) " · streak ${state.streak}" else "",
        players = emptyList(),
        glow = GameKind.BALLOON.color,
        dock = soloDock(onHome, { showRules = true }, if (state.started) vm::setup else null),
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
                finish = PopupAction("Change", vm::setup),
                again = PopupAction("Next word", vm::guessTheirs),
            )
        },
    ) {
        if (!state.started) {
            AppCard("Guess the computer's word") {
                Text("Pick letters before the seven balloons pop.", color = p.muted, fontSize = 14.sp)
                PrimaryButton("Play", vm::guessTheirs, Modifier.fillMaxWidth())
            }
            PickWord("Or give the computer one of yours", vm::giveYours)
            state.message?.let { Text(it, color = p.bad, fontSize = 13.sp) }
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
