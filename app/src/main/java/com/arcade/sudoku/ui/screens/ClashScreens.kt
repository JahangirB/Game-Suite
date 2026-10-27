package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.Board
import com.arcade.sudoku.ui.theme.Pieces
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.clash.Clash
import com.arcade.sudoku.game.clash.ClashView
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloClashUiState
import com.arcade.sudoku.ui.SoloClashViewModel
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont

private val Wild = Board.Ink

/** A card colour's paint. */
fun clashColor(c: Char): Color = when (c) {
    'r' -> Pieces.Red
    'y' -> Pieces.Yellow
    'g' -> Pieces.Green
    'b' -> Pieces.Blue
    else -> Wild
}

private fun colourName(c: Char) = when (c) {
    'r' -> "red"
    'y' -> "yellow"
    'g' -> "green"
    'b' -> "blue"
    else -> "any"
}

private fun faceLabel(card: String) = when (Clash.face(card)) {
    "S" -> "⦸"
    "R" -> "⇄"
    "D" -> "+2"
    "W" -> "★"
    "F" -> "+4"
    else -> Clash.face(card)
}

/** One card, [width] wide. A wild shows the four colours, or the named colour once played. */
@Composable
fun ClashCard(card: String, width: Dp, modifier: Modifier = Modifier, named: Char? = null) {
    val p = AppTheme.palette
    val wild = Clash.isWild(card)
    Surface(
        color = if (wild) Wild else clashColor(card[0]),
        shape = RoundedCornerShape(width * 0.18f),
        border = BorderStroke(2.dp, p.ink),
        modifier = modifier.size(width, width * 1.45f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (wild) {
                // Four colour quarters (or the colour named) behind the face.
                Canvas(Modifier.size(width * 0.62f)) {
                    val r = size.minDimension / 2
                    val parts = if (named != null) listOf(named, named, named, named) else Clash.COLOURS.toList()
                    parts.forEachIndexed { i, c ->
                        drawArc(clashColor(c), i * 90f, 90f, true, Offset(size.width / 2 - r, size.height / 2 - r), Size(r * 2, r * 2))
                    }
                }
            } else {
                Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(width * 0.66f)) {}
            }
            Text(
                faceLabel(card),
                color = if (wild) Color.White else clashColor(card[0]),
                fontSize = (width.value * 0.34f).sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont,
            )
        }
    }
}

/** The back of a card: the deck. */
@Composable
private fun ClashBack(width: Dp, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Surface(color = p.ink, shape = RoundedCornerShape(width * 0.18f), border = BorderStroke(2.dp, p.ink), modifier = modifier.size(width, width * 1.45f)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width * 0.62f)) {
                val r = size.minDimension / 2
                Clash.COLOURS.forEachIndexed { i, c ->
                    drawArc(clashColor(c), i * 90f + 45f, 90f, true, Offset(size.width / 2 - r, size.height / 2 - r), Size(r * 2, r * 2))
                }
            }
        }
    }
}

/** Ask which colour a wild names. */
@Composable
private fun ColourPicker(onPick: (Char) -> Unit, onCancel: () -> Unit) {
    val p = AppTheme.palette
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = p.card,
        title = { Text("Choose the colour", color = p.text, fontWeight = FontWeight.Bold) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Clash.COLOURS.forEach { c ->
                    Surface(
                        color = clashColor(c), shape = CircleShape, border = BorderStroke(2.dp, p.ink),
                        modifier = Modifier.size(52.dp).clickable { onPick(c) },
                    ) {}
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** What just happened, in a line. */
private fun clashNews(view: ClashView, name: (Char) -> String): String? {
    val m = view.lastMove ?: return null
    val who = name(m.who)
    val made = m.madeDraw?.let { (w, n) -> " ${name(w)} draws $n." } ?: ""
    return when {
        m.card != null && Clash.isWild(m.card) -> "$who played a wild: ${colourName(m.colour ?: 'r')}.$made"
        m.card != null -> "$who played ${colourName(m.card[0])} ${faceLabel(m.card)}.$made"
        m.drew > 0 -> "$who drew a card."
        m.passed -> "$who passed."
        else -> null
    }
}

/** The pile, your hand and what you can do: shared by battle and solo. */
@Composable
private fun ColumnScope.ClashTable(
    view: ClashView,
    myTurn: Boolean,
    enabled: Boolean,
    waitingText: String,
    name: (Char) -> String,
    onPlay: (Int, Char?) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
) {
    val p = AppTheme.palette
    var wildAt by remember { mutableStateOf<Int?>(null) }
    val canMove = myTurn && enabled
    wildAt?.let { i -> ColourPicker(onPick = { c -> wildAt = null; onPlay(i, c) }, onCancel = { wildAt = null }) }
    TurnLine(
        when {
            !myTurn -> waitingText
            view.drawn -> "You drew a card that fits: play it or keep it"
            view.hand.indices.any { view.canPlay(it) } -> "Your turn: match ${colourName(view.colour)} or ${faceLabel(view.top)}"
            else -> "Nothing matches: draw a card"
        },
        myTurn,
        clashColor(view.colour),
    )
    clashNews(view, name)?.let { Text(it, color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

    // The pile with the colour to match, and the deck.
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClashBack(64.dp)
                Text("${view.deck} left", color = p.muted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // A played card flies in from whoever played it: yours from your hand below,
                // anyone else's from the top, turning face up on the way.
                val played = view.lastMove?.takeIf { it.card != null }
                val mine = played?.who == 'm'
                FlipCard(
                    faceUp = true,
                    key = played,
                    modifier = Modifier.enterFrom(played, dy = if (mine) 260.dp else (-260).dp, rotation = if (mine) -20f else 20f, scaleFrom = 0.6f),
                    back = { ClashBack(108.dp) },
                ) { ClashCard(view.top, 108.dp, named = view.colour) }
                Text("Match ${colourName(view.colour)}", color = clashColor(view.colour), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(28.dp))
            Text(if (view.direction > 0) "↻" else "↺", color = p.muted, fontSize = 34.sp)
        }
    }

    // Your hand: cards you can play stand up; the rest are dimmed on your turn.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Keyed by card (and which copy), so a newly dealt or drawn card arrives from the
            // deck face down and turns over, and the others keep their place.
            val seen = mutableMapOf<String, Int>()
            val dealing = view.hand.size >= 7 && view.lastMove == null
            view.hand.forEachIndexed { i, card ->
                val copy = seen.getOrDefault(card, 0).also { seen[card] = it + 1 }
                key(card, copy) {
                    val ok = canMove && view.canPlay(i)
                    FlipCard(
                        faceUp = true,
                        key = Unit,
                        modifier = Modifier
                            .enterFrom(Unit, dx = (-120).dp, dy = (-240).dp, rotation = -30f, scaleFrom = 0.7f, delayMs = if (dealing) i * Motion.STAGGER_MS else 0, onAppear = true)
                            .graphicsLayer {
                                translationY = if (ok) -10.dp.toPx() else 0f
                                alpha = if (!canMove || ok) 1f else 0.5f
                            }
                            .clickable(enabled = ok) { if (Clash.isWild(card)) wildAt = i else onPlay(i, null) },
                        back = { ClashBack(50.dp) },
                    ) { ClashCard(card, 50.dp) }
                }
            }
        }
    }
    if (canMove) {
        when {
            view.drawn -> SecondaryButton("Keep it and pass", onPass, Modifier.fillMaxWidth())
            view.hand.indices.none { view.canPlay(it) } -> PrimaryButton("Draw a card", onDraw, Modifier.fillMaxWidth())
            else -> SecondaryButton("Draw instead", onDraw, Modifier.fillMaxWidth())
        }
    }
}

// ---- Against your partners -----------------------------------------------------------------

/** Colour Clash against your partners, two to four of you. */
@Composable
fun ClashGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val room = state.room ?: return
    val turns = room.turns ?: return
    val view = turns.clash ?: return
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me" && !finished
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.CLASH) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    fun name(mark: Char) = if (mark == 'm') "You" else room.others.getOrNull("opq".indexOf(mark))?.name ?: "Someone"
    val turnName = turns.turnName ?: room.opponent?.name

    GameFrame(
        title = "Colour Clash",
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, MyDisc,
            detail = { player, isMe -> "${view.counts[if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0))] ?: 0} cards" },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> markColor(letterOf(i)) },
        ),
        glow = GameKind.CLASH.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.CLASH,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "Your opponent"),
                when (room.endReason) {
                    "out" -> if (room.winner == "me") "You played your last card!" else "${room.winnerName} played their last card."
                    "left" -> "A player left, so the hands can't be played out."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "Your opponent lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = listOf("You ${view.counts['m'] ?: 0} cards") + room.others.mapIndexed { i, o -> "${o.name} ${view.counts[letterOf(i)] ?: 0} cards" },
            )
        },
    ) {
        val waitingOn = room.others.firstOrNull { it.name == turnName }
        ClashTable(
            view, myTurn, enabled = !state.firing,
            waitingText = if (waitingOn?.online == false) "$turnName is away. They'll get a notification." else "${turnName ?: "Your opponent"} is thinking…",
            name = ::name,
            onPlay = vm::playCard, onDraw = vm::drawCard, onPass = vm::passCard,
        )
    }
}

// ---- On your own ------------------------------------------------------------------------------

/** Colour Clash against one to three computer players. */
@Composable
fun SoloClashScreen(state: SoloClashUiState, vm: SoloClashViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.CLASH) { showRules = false }
    val view = state.view
    val result = state.result
    fun name(mark: Char) = if (mark == 'm') "You" else "Computer " + ("opq".indexOf(mark) + 1)
    GameFrame(
        title = "Colour Clash",
        subtitle = "Against the computer",
        players = if (view == null) emptyList() else view.order.map { mark ->
            ChipPlayer(name(mark), null, markColor(mark), "${view.counts[mark] ?: 0} cards", active = result == null && state.turn == mark)
        },
        glow = GameKind.CLASH.color,
        dock = soloDock(onHome, { showRules = true }, if (view != null) vm::changeSetup else null),
        scrollable = view == null,
        popup = if (view == null || result == null) null else popupContent {
            SoloPopup(
                GameKind.CLASH, result,
                if (result == "me") "You played your last card!" else "A computer played its last card first.",
                onAgain = vm::start, onHome = onHome, finishLabel = "Change players", onFinish = vm::changeSetup,
            )
        },
    ) {
        if (view == null) {
            AppCard("Set up the table") {
                Text("Match the colour or the number; first to empty their hand wins.", color = p.muted, fontSize = 14.sp)
                Text("Computer players", color = p.text, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { n -> FilterChip(selected = state.opponents == n, onClick = { vm.setOpponents(n) }, label = { Text("$n") }) }
                }
                PrimaryButton("Deal", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            ClashTable(
                view, myTurn = state.turn == 'm' && result == null, enabled = true,
                waitingText = "${name(state.turn)} is thinking…",
                name = ::name,
                onPlay = vm::playCard, onDraw = vm::drawCard, onPass = vm::passCard,
            )
        }
    }
}
