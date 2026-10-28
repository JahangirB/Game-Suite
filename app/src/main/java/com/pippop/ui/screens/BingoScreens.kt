package com.pippop.ui.screens

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

/**
 * A card under the BINGO letters. Tap a called number to dab it; [line] (at the end) is
 * outlined. The free centre is always dabbed.
 */
@Composable
fun BingoCard(card: List<Int>, dabs: Set<Int>, line: List<Int>?, enabled: Boolean, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    SoundOnChange(dabs.size.takeIf { enabled }) { play(Sound.BINGO_DAB) }
    // A full line dabbed: the Bingo button lights up with a shimmer.
    SoundOnChange((enabled && Bingo.fullLine(card, dabs.map { card[it] }.toSet()) != null).takeIf { it }) { play(Sound.BINGO_LINE_READY) }
    BoxWithConstraints(modifier) {
        val cell = minOf(maxWidth, maxHeight * 5f / 6f) / Bingo.SIZE
        Column(Modifier.align(Alignment.Center)) {
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
                    Surface(
                        color = if (inLine) Board.GlowSoft else p.card,
                        shape = Shapes.S,
                        border = BorderStroke(if (inLine) 3.dp else 2.dp, p.ink),
                        modifier = Modifier.size(cell).padding(3.dp).clickable(enabled = enabled && !free) { onTap(i) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // A dab pops onto the square (the free star is always dabbed, without the pop).
                            if (dabbed) Box(
                                Modifier.fillMaxSize().padding(cell * 0.12f)
                                    .enterFrom(Unit, scaleFrom = 0.2f, durationMs = 220, onAppear = !free)
                                    .clip(CircleShape).background(ColumnColours[c].copy(alpha = 0.75f)),
                            )
                            Text(
                                if (free) "★" else n.toString(),
                                color = if (dabbed) Color.White else p.text,
                                fontSize = (cell.value * if (free) 0.42f else 0.36f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont,
                            )
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
                Text(Bingo.letter(n).toString(), color = AppTheme.palette.ink, fontSize = (size.value * 0.16f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.17f).sp)
                Text(n.toString(), color = AppTheme.palette.ink, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont, lineHeight = (size.value * 0.27f).sp)
            }
        }
    }
}

/** The latest call, big, and the few before it. */
@Composable
private fun CallStrip(calls: List<Int>, called: Int) {
    val p = AppTheme.palette
    SoundOnChange(called.takeIf { it > 0 }) { play(Sound.BINGO_BALL) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (called == 0) {
            Text("Get ready…", color = p.muted, fontSize = TextSize.BodyL)
        } else {
            // Each new call rolls in from the side, turning as it rolls.
            Box(Modifier.enterFrom(called, dx = 260.dp, rotation = 360f, durationMs = 600)) { Ball(calls[called - 1], 76.dp) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Call $called of ${Bingo.NUMBERS}", color = p.muted, fontSize = TextSize.Body)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in (called - 2) downTo maxOf(0, called - 6)) Ball(calls[k], 36.dp)
                }
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
    val canClaim = card != null && Bingo.fullLine(card, dabs.map { card[it] }.toSet()) != null
    GameFrame(
        title = "Bingo",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, p.primary, detail = { _, isMe -> if (isMe && card != null) "${dabs.size} dabbed" else null }),
        glow = GameKind.BINGO.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
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
    GameFrame(
        title = "Bingo",
        subtitle = if (playing) "Against ${state.computers} computer card${if (state.computers > 1) "s" else ""} · ${state.wins} won" else "On your own",
        players = emptyList(),
        glow = GameKind.BINGO.color,
        dock = soloDock(onHome, { showRules = true }, if (playing) vm::again else null),
        scrollable = !playing,
        popup = if (!playing || state.winner == null) null else popupContent {
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
