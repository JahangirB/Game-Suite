package com.pippop.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.GameKind
import com.pippop.data.Reaction
import com.pippop.data.RoomStatus
import com.pippop.game.clash.Clash
import com.pippop.game.clash.ClashView
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.Sfx
import com.pippop.ui.SoloClashUiState
import com.pippop.ui.SoloClashViewModel
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.Takeover
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.art.drawSticker
import com.pippop.ui.art.stickerOf
import com.pippop.ui.rememberSfx
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.MarkIcon
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.markOf
import kotlinx.coroutines.delay

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
    "K" -> "+8"
    else -> Clash.face(card)
}

/** The ink outline of a card with corner radius [r], just inside its bounds. */
private fun DrawScope.cardEdge(ink: Color, r: Float) {
    val edge = 1.dp.toPx()
    drawRoundRect(ink, Offset(edge, edge), Size(size.width - 2 * edge, size.height - 2 * edge), CornerRadius(r - edge), style = Stroke(2.dp.toPx()))
}

/**
 * A card's body: a soft shadow under it, its colour shaded a little lighter at the top and
 * darker at the bottom, and the ink outline. A [soft] card (one you can play) also gets a thin
 * soft gold line just inside the outline.
 */
private fun Modifier.cardBody(base: Color, width: Dp, ink: Color, soft: Boolean = false) = drawBehind {
    val r = width.toPx() * 0.18f
    boxShadow(Offset.Zero, size, r, drop = 2.dp.toPx(), strength = 0.28f)
    drawRoundRect(Brush.verticalGradient(listOf(base.lighter(0.18f), base.darker(0.14f))), cornerRadius = CornerRadius(r))
    cardEdge(ink, r)
    if (soft) {
        val inset = 3.dp.toPx()
        drawRoundRect(Board.GlowSoft, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius((r - inset).coerceAtLeast(0f)), style = Stroke(2.dp.toPx()))
    }
}

/**
 * One card, [width] wide. A wild shows the four colours, or the named colour once played. Its
 * value is small in two opposite corners as well as large in the middle; [soft] marks a card
 * you can play with a gold line inside its outline.
 */
@Composable
fun ClashCard(card: String, width: Dp, modifier: Modifier = Modifier, named: Char? = null, soft: Boolean = false) {
    val p = AppTheme.palette
    val wild = Clash.isWild(card)
    val shape = RoundedCornerShape(width * 0.18f)
    Box(
        modifier.size(width, width * 1.45f).cardBody(if (wild) Wild else clashColor(card[0]), width, p.ink, soft).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (wild) {
            // Four colour quarters (or the colour named) behind the face.
            Canvas(Modifier.size(width * 0.62f)) {
                val r = size.minDimension / 2
                val parts = if (named != null) listOf(named, named, named, named) else Clash.COLOURS.toList()
                parts.forEachIndexed { i, c ->
                    drawArc(clashColor(c), i * 90f, 90f, true, Offset(size.width / 2 - r, size.height / 2 - r), Size(r * 2, r * 2))
                }
            }
        } else if (Clash.isKylian(card)) {
            // Kylian Dictador himself, standing on his colour, with what he costs.
            Canvas(Modifier.align(Alignment.BottomCenter).size(width * 0.96f)) { drawSticker(stickerOf(Reaction.KYLIAN)) }
            markOf(clashColor(card[0]))?.let { mark ->
                MarkIcon(mark, Modifier.align(Alignment.TopStart).padding(width * 0.08f).size(width * 0.22f))
            }
            Text(
                "+8",
                color = if (card[0] == 'y') Board.Ink else Color.White,
                fontSize = (width.value * 0.2f).sp,
                fontWeight = FontWeight.Black,
                fontFamily = DisplayFont,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = width * 0.08f, top = width * 0.02f),
            )
            // The outline again on top, so the sticker doesn't hide it.
            Canvas(Modifier.fillMaxSize()) { cardEdge(p.ink, width.toPx() * 0.18f) }
            return@Box
        } else {
            Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(width * 0.66f)) {}
            // The colour's mark in the corner, so cards can be told apart without seeing colour.
            markOf(clashColor(card[0]))?.let { mark ->
                MarkIcon(mark, Modifier.align(Alignment.TopStart).padding(width * 0.08f).size(width * 0.22f))
            }
        }
        val corner = Modifier.padding(horizontal = width * 0.08f, vertical = width * 0.04f)
        val small = (width.value * 0.17f).sp
        // The small corner values repeat the big one, so a screen reader skips them.
        val cornerInk = if (card[0] == 'y') Board.Ink else Color.White
        Text(faceLabel(card), color = cornerInk, fontSize = small, fontWeight = FontWeight.Black, fontFamily = DisplayFont, modifier = Modifier.align(Alignment.TopEnd).then(corner).clearAndSetSemantics {})
        Text(faceLabel(card), color = cornerInk, fontSize = small, fontWeight = FontWeight.Black, fontFamily = DisplayFont, modifier = Modifier.align(Alignment.BottomStart).then(corner).graphicsLayer { rotationZ = 180f }.clearAndSetSemantics {})
        Text(
            faceLabel(card),
            color = if (wild) Color.White else clashColor(card[0]),
            fontSize = (width.value * 0.34f).sp,
            fontWeight = FontWeight.Black,
            fontFamily = DisplayFont,
        )
    }
}

/** The back of a card: the deck. */
@Composable
internal fun ClashBack(width: Dp, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Box(modifier.size(width, width * 1.45f).cardBody(Board.Ink, width, p.ink).clip(RoundedCornerShape(width * 0.18f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width * 0.62f)) {
            val r = size.minDimension / 2
            Clash.COLOURS.forEachIndexed { i, c ->
                drawArc(clashColor(c), i * 90f + 45f, 90f, true, Offset(size.width / 2 - r, size.height / 2 - r), Size(r * 2, r * 2))
            }
        }
    }
}

/** The deck as a stack: up to [edges] card edges show, offset, under the top card. */
@Composable
private fun ClashStack(width: Dp, edges: Int) {
    Box(Modifier.size(width + 3.dp * edges, width * 1.45f + 3.dp * edges)) {
        for (n in edges downTo 1) ClashBack(width, Modifier.offset(3.dp * n, 3.dp * n))
        ClashBack(width)
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
        m.card != null && Clash.isKylian(m.card) -> "$who played ${colourName(m.card[0])} Kylian Dictador.$made"
        m.card != null -> "$who played ${colourName(m.card[0])} ${faceLabel(m.card)}.$made"
        m.drew > 0 -> "$who drew a card."
        m.passed -> "$who passed."
        else -> null
    }
}

/** The pile, your hand and what you can do: shared by battle and solo. */
@Composable
internal fun ColumnScope.ClashTable(
    view: ClashView,
    myTurn: Boolean,
    enabled: Boolean,
    waitingText: String,
    name: (Char) -> String,
    onPlay: (Int, Char?) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
    /** Draw and Keep as buttons under the hand; against your partners they're in the dock. */
    buttons: Boolean = true,
    /** Your hand in order of colour and number (the dock's Sort), not as dealt. */
    sorted: Boolean = false,
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
    clashNews(view, name)?.let { Text(it, color = p.muted, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

    // The pile with the colour to match, and the deck.
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClashStack(64.dp, edges = (view.deck - 1).coerceIn(0, 2))
                Text("${view.deck} left", color = p.muted, fontSize = TextSize.Label)
            }
            Spacer(Modifier.width(28.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // A played card flies in from whoever played it: yours from your hand below,
                // anyone else's from the top, turning face up on the way.
                val played = view.lastMove?.takeIf { it.card != null }
                val mine = played?.who == 'm'
                SoundOnChange(played) {
                    play(Sound.CARD_SLIDE, if (mine) 1f else Sfx.OTHERS)
                    // Kylian Dictador takes over every phone, the player's own too: his sticker and song.
                    if (played?.card?.let(Clash::isKylian) == true) {
                        Takeover.show(Reaction.KYLIAN)
                        play(Sound.KYLIAN_DICTADOR)
                    }
                }
                // The view holds only the top card, so the last few tops are remembered here: the
                // one or two before it show, a little turned, under the top card.
                var tops by remember { mutableStateOf(listOf(view.top)) }
                var lastPlay by remember { mutableStateOf(played) }
                LaunchedEffect(view.lastMove) {
                    if (view.lastMove == null) tops = listOf(view.top)
                    else if (played != null && played != lastPlay) { tops = (tops + view.top).takeLast(3); lastPlay = played }
                }
                // A reshuffle (the deck grows) turns the old pile into the deck: start the pile again.
                var deckBefore by remember { mutableIntStateOf(view.deck) }
                LaunchedEffect(view.deck) {
                    if (view.deck > deckBefore) tops = listOf(view.top)
                    deckBefore = view.deck
                }
                // The cards under the top one, worked out now so a new top never shows twice.
                val under = (if (tops.lastOrNull() == view.top) tops.dropLast(1) else tops).takeLast(2)
                // A soft ring in the colour in play (the colour named, after a wild) glows round
                // the pile, pulsing on your turn and steady otherwise; its name is written below.
                val pulse = loopingFloat(0.55f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), still = 0.85f, label = "clashRing")
                // Darker on the light theme, where yellow and green would barely show on the table.
                val ringBase = clashColor(view.colour).let { if (p.dark) it else it.darker(0.4f) }
                Box(
                    Modifier.size(144.dp, 190.dp).drawBehind {
                        val ring = ringBase.copy(alpha = if (myTurn) pulse.value else 0.85f)
                        val corner = CornerRadius(Radius.L.toPx())
                        val at = Offset(3.dp.toPx(), 3.dp.toPx())
                        val inner = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx())
                        drawRoundRect(ring.copy(alpha = ring.alpha * 0.14f), at, inner, corner, style = Stroke(14.dp.toPx()))
                        drawRoundRect(ring.copy(alpha = ring.alpha * 0.28f), at, inner, corner, style = Stroke(7.dp.toPx()))
                        drawRoundRect(ring, at, inner, corner, style = Stroke(2.dp.toPx()))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    under.forEach { card ->
                        // Each card keeps its own tilt for as long as it is in the pile.
                        val turn = listOf(-11f, 8f, -6f, 12f, -9f, 6f)[Math.floorMod(card.hashCode(), 6)]
                        ClashCard(card, 108.dp, Modifier.graphicsLayer { rotationZ = turn })
                    }
                    FlipCard(
                        faceUp = true,
                        key = played,
                        modifier = Modifier.enterFrom(played, dy = if (mine) 260.dp else (-260).dp, rotation = if (mine) -20f else 20f, scaleFrom = 0.6f),
                        back = { ClashBack(108.dp) },
                    ) { ClashCard(view.top, 108.dp, named = view.colour) }
                }
                Text("${colourName(view.colour).replaceFirstChar { it.uppercase() }} in play", color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Body)
            }
            Spacer(Modifier.width(28.dp))
            Text(if (view.direction > 0) "↻" else "↺", color = p.muted, fontSize = TextSize.DisplayL)
        }
    }

    // Your hand: on your turn the cards you can play rise with a gold line inside; the rest are dimmed.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Keyed by card (and which copy), so a newly dealt or drawn card arrives from the
            // deck face down and turns over, and the others keep their place.
            val seen = mutableMapOf<String, Int>()
            val dealing = view.hand.size >= 7 && view.lastMove == null
            // Cards dealt or drawn into your hand flick in, one sound each.
            val sfx = rememberSfx()
            var handBefore by remember { mutableIntStateOf(view.hand.size) }
            LaunchedEffect(view.hand.size) {
                val added = view.hand.size - handBefore
                handBefore = view.hand.size
                repeat(added.coerceIn(0, 7)) {
                    sfx.play(Sound.CARD_DEAL)
                    delay(Motion.STAGGER_MS.toLong())
                }
            }
            val order = if (sorted) view.hand.indices.sortedBy { view.hand[it] } else view.hand.indices.toList()
            order.forEach { i ->
                val card = view.hand[i]
                val copy = seen.getOrDefault(card, 0).also { seen[card] = it + 1 }
                key(card, copy) {
                    val ok = canMove && view.canPlay(i)
                    FlipCard(
                        faceUp = true,
                        key = Unit,
                        modifier = Modifier
                            .enterFrom(Unit, dx = (-120).dp, dy = (-240).dp, rotation = -30f, scaleFrom = 0.7f, delayMs = if (dealing) i * Motion.STAGGER_MS else 0, onAppear = true)
                            .graphicsLayer {
                                translationY = if (ok) -8.dp.toPx() else 0f
                                alpha = if (!canMove || ok) 1f else 0.5f
                            }
                            .clickable(enabled = ok) { if (Clash.isWild(card)) wildAt = i else onPlay(i, null) },
                        back = { ClashBack(50.dp) },
                    ) { ClashCard(card, 50.dp, soft = ok) }
                }
            }
        }
    }
    if (buttons && canMove) {
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
    var sortHand by remember { mutableStateOf(false) }
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.CLASH) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    fun name(mark: Char) = if (mark == 'm') "You" else room.others.getOrNull("opq".indexOf(mark))?.name ?: "Someone"
    val turnName = turns.turnName ?: room.opponent?.name

    GameFrame(
        title = "Colour Clash",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, MyDisc,
            detail = { player, isMe -> "${view.counts[if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0))] ?: 0} cards" },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> markColor(letterOf(i)) },
        ),
        glow = GameKind.CLASH.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        myTurn = myTurn,
        tools = listOf(
            // Draw a card, or once you've drawn one that fits, keep it and pass.
            if (view.drawn) DockTool("Keep it", GameIcons.Cards, anytime = false, enabled = myTurn && !state.firing, onClick = vm::passCard)
            else DockTool("Draw", GameIcons.Cards, anytime = false, enabled = myTurn && !state.firing, onClick = vm::drawCard),
            DockTool(if (sortHand) "As dealt" else "Sort", GameIcons.Sort) { sortHand = !sortHand },
        ),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.CLASH,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "The other player"),
                when (room.endReason) {
                    "out" -> if (room.winner == "me") "You played your last card!" else "${room.winnerName} played their last card."
                    "left" -> "A player left, so the hands can't be played out."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
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
            waitingText = if (waitingOn?.online == false) "$turnName is away. They'll get a notification." else "${turnName ?: "The other player"} is thinking…",
            name = ::name,
            onPlay = vm::playCard, onDraw = vm::drawCard, onPass = vm::passCard,
            buttons = false, sorted = sortHand,
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
        menu = soloMenu(GameKind.CLASH, onHome, { showRules = true }, if (view != null) vm::changeSetup else null),
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
                Text("Match the colour or the number; first to empty their hand wins.", color = p.muted, fontSize = TextSize.Body)
                Text("Computer players", color = p.text, fontSize = TextSize.BodyL)
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
