package com.pippop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.words.LadderOptions
import com.pippop.game.words.LadderView
import com.pippop.game.words.WordLadder
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.textFill
import kotlinx.coroutines.delay

/*
 * Word Ladder's screens, against your partner (online or nearby): the setup card, the secret pick,
 * the coin flip, the ladder and the result. The rules live in game/words/LadderMatch.kt; the
 * screens only ask and show, and show the rule's own words when a move or a pick is refused.
 */

/** What the ladder screens ask of the view model, so a test can stand a room of its own behind them. */
class LadderActions(
    val pick: (String) -> Unit,
    val move: (String) -> Unit,
    val rematch: () -> Unit,
    val dismissMessage: () -> Unit,
)

fun ladderActions(vm: BattleViewModel) = LadderActions(vm::ladderPick, vm::ladderMove, vm::readyTurns, vm::dismissMessage)

/** How long the coin flip stays before the first turn is shown by itself. */
private const val FLIP_HOLD_MS = 3500L

// ---- The rules the screens lean on (the same words as LadderMatch) --------------------------------

/** What the pick says as you type: [text] with its [tone], and whether the word can be locked in. */
internal class PickCheck(val text: String, val tone: Tone, val canLock: Boolean)

internal fun ladderPickCheck(word: String, length: Int): PickCheck {
    val w = word.uppercase()
    if (w.length < length) {
        val more = length - w.length
        return PickCheck(if (w.isEmpty()) "Type a $length-letter word" else "$more more ${if (more == 1) "letter" else "letters"}", Tone.Neutral, false)
    }
    // Before the lists are in, the server decides when the word is locked.
    if (!WordLadder.loaded) return PickCheck("We'll check it when you lock it in", Tone.Neutral, true)
    return if (WordLadder.isWord(w) && w !in WordLadder.blocklist) PickCheck("$w is in the word list", Tone.Good, true)
    else PickCheck("Not a word we can use. Pick another.", Tone.Bad, false)
}

/** Why [word] cannot follow the last word of [chain], in the rule's own words, or null when it can. */
internal fun ladderMoveProblem(chain: List<String>, word: String): String? {
    val last = chain.lastOrNull() ?: return null
    val w = word.uppercase()
    if (w.length != last.length || w.indices.count { w[it] != last[it] } != 1) return "Change one letter"
    if (WordLadder.loaded && !WordLadder.isRealWord(w)) return "Not a word"
    if (w in chain) return "Already used"
    return null
}

/** A line to say how to put right what [ladderMoveProblem] said. */
internal fun ladderProblemHelp(reason: String, word: String): String? = when (reason) {
    "Not a word" -> "$word isn't in the word list. Pick another letter."
    "Already used" -> "$word is already on the ladder. Words can't repeat."
    "Change one letter" -> "Pick a different letter: the word has to change in one place."
    else -> null
}

/** One word of the ladder: who made it, and which letter changed from the word before (null for the start). */
internal data class Rung(val word: String, val changed: Int?, val label: String)

/**
 * The ladder as rungs, start first. A step is named after who made it when there is no turn timer (turns then
 * simply alternate, the owner of the target first); with a timer a turn can pass, so steps are only numbered.
 */
internal fun ladderRungs(chain: List<String>, targetIsMine: Boolean?, timer: Int, opponent: String): List<Rung> =
    chain.mapIndexed { i, word ->
        val changed = if (i == 0) null else word.indices.firstOrNull { word[it] != chain[i - 1][it] }
        val label = when {
            i == 0 -> "Start"
            timer == 0 && targetIsMine != null -> if ((i % 2 == 1) == targetIsMine) "You" else opponent
            else -> "Step $i"
        }
        Rung(word, changed, label)
    }

/** The result card's line for why the game ended. */
internal fun ladderResultDetail(winner: String?, reason: String?, name: String, target: String?): String = when (reason) {
    "target" -> if (winner == "me") "Your move spelled ${target.orEmpty()}." else "$name's move spelled ${target.orEmpty()}."
    "stuck" -> if (winner == "me") "$name had no move left." else "You had no move left."
    "forfeit" -> if (winner == "me") "$name left the game." else "You left the game."
    "timeout" -> if (winner == "me") "$name lost connection." else "You were disconnected for too long."
    else -> ""
}

// ---- Setup ----------------------------------------------------------------------------------------

private fun levelName(length: Int) = when (length) { 4 -> "Easy"; 6 -> "Hard"; else -> "Medium" }

/**
 * Word Ladder's setup, in the lobby and in the Change sheet: the word length (the level) and the turn timer.
 * Whoever opened the game changes them; everyone else sees them greyed. Playing the computer comes with solo.
 */
@Composable
fun LadderOptionsCard(room: RoomState, busy: Boolean, onPick: (LadderOptions) -> Unit) {
    val o = room.ladderOptions
    OptionsCard(room, "Word Ladder", busy) { enabled ->
        OptionRow(
            "Level", LadderOptions.LENGTHS.map { it to "$it letters" }, o.length, { onPick(o.copy(length = it)) }, enabled = enabled,
            note = "${levelName(o.length)}: both words have ${o.length} letters.",
        )
        OptionRow(
            "Timer", LadderOptions.TIMERS.map { it to if (it == 0) "Off" else "$it s" }, o.timer, { onPick(o.copy(timer = it)) }, enabled = enabled,
            note = if (o.timer == 0) "No clock: take as long as you like." else "${o.timer} seconds a turn. When it runs out, the turn passes.",
        )
    }
}

// ---- Tiles and rungs ------------------------------------------------------------------------------

/**
 * One letter. A [changed] letter glows gold and is underlined, a [selected] one has a thick border and an underline, so
 * neither is told by colour alone. [fill] colours a tile (the target's).
 */
@Composable
internal fun LadderTile(
    letter: Char?, size: Dp, modifier: Modifier = Modifier, changed: Boolean = false, selected: Boolean = false, fill: Color? = null,
) {
    val p = AppTheme.palette
    val background = if (changed) Board.Glow else fill ?: p.card
    val ink = if (changed) Board.Ink else if (fill != null) readableOn(fill) else p.text
    val edge = if (selected) p.primaryText else p.ink
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(Shapes.S).background(background).border(if (selected) 4.dp else 2.dp, edge, Shapes.S),
    ) {
        Text(
            (letter ?: ' ').toString(), color = ink, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
            fontSize = if (size >= 44.dp) TextSize.DisplayM else if (size >= 32.dp) TextSize.Title else TextSize.BodyL,
        )
        if (changed || selected) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(width = size * 0.5f, height = 3.dp).background(if (changed) Board.Ink else p.primaryText))
        }
    }
}

/** A word as a row of tiles; [changed] is the one that moved. */
@Composable
internal fun LadderWord(word: String, tile: Dp, changed: Int? = null, fill: Color? = null, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (tile >= 44.dp) 6.dp else 4.dp)) {
        word.forEachIndexed { i, c -> LadderTile(c, tile, changed = i == changed, fill = fill) }
    }
}

@Composable
private fun LadderRung(rung: Rung, current: Boolean, tile: Dp = 38.dp) {
    val p = AppTheme.palette
    val said = rung.label + ": " + rung.word + (rung.changed?.let { ", letter ${it + 1} changed to ${rung.word[it]}" } ?: "")
    Row(
        Modifier.fillMaxWidth().then(if (current) Modifier.clip(Shapes.M).background(p.accent) else Modifier)
            .padding(horizontal = Space.S, vertical = 3.dp).semantics(mergeDescendants = true) { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Text(
            rung.label, color = if (current) p.text else p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(64.dp),
        )
        LadderWord(rung.word, tile, rung.changed)
    }
}

/** The target, pinned under the ladder: [whose] word, and that whoever's move spells it wins. */
@Composable
private fun LadderTarget(target: String, whose: String) {
    val p = AppTheme.palette
    val fill = textFill(GameKind.LADDER.color)
    Row(
        Modifier.fillMaxWidth().clip(Shapes.L).background(p.card).border(2.dp, p.ink, Shapes.L).padding(horizontal = Space.M, vertical = Space.S)
            .semantics(mergeDescendants = true) { contentDescription = "Target: $target, $whose" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Column(Modifier.width(64.dp)) {
            Text("Target", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
            Text(whose, color = p.muted, fontSize = TextSize.Micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LadderWord(target, 38.dp, fill = fill)
    }
}

/** The ladder on its own, for the result card and the tutorial: every rung, then (when given) the target. */
@Composable
internal fun LadderList(rungs: List<Rung>, modifier: Modifier = Modifier, tile: Dp = 34.dp) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rungs.forEach { LadderRung(it, current = false, tile = tile) }
    }
}

// ---- The screen -----------------------------------------------------------------------------------

/** Word Ladder against your partner. */
@Composable
fun LadderGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit, actions: LadderActions = ladderActions(vm)) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val view = room.turns?.ladder ?: return
    val finished = room.status == RoomStatus.FINISHED
    val opponent = room.opponent
    val name = opponent?.name ?: "The other player"
    val now = rememberTicker()
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.LADDER) { showRules = false }

    // The coin flip shows once after the reveal; a game joined later in its play goes straight to the ladder.
    var flipSeen by rememberSaveable(room.code, room.round) { mutableStateOf(view.phase != "picking" && view.chain.size > 1) }
    val flipping = !flipSeen && !finished && view.phase == "playing" && view.chain.size == 1
    LaunchedEffect(flipping) {
        if (flipping) {
            delay(FLIP_HOLD_MS)
            flipSeen = true
        }
    }
    val picking = view.phase == "picking" && !finished

    val players = battleChips(
        room, state, now, p.me,
        detail = { _, isMe ->
            when {
                picking -> if (if (isMe) view.myPick != null else view.opponentPicked) "Picked" else "Picking"
                view.startOwner == null -> null
                (view.startOwner == "me") == isMe -> "Start word"
                else -> "Target word"
            }
        },
        active = { _, isMe -> !finished && view.phase == "playing" && !flipping && view.turn == if (isMe) "me" else "opponent" },
    )

    GameFrame(
        title = "Word Ladder",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.LADDER.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        myTurn = view.turn == "me" && !finished,
        onReact = if (opponent != null) ({ reacting = !reacting }) else null,
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }, onLine = vm.lineSender { reacting = false }, onPack = vm.packSender { reacting = false }) },
        scrollable = picking || flipping,
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.LADDER, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: name),
                ladderResultDetail(room.winner, room.endReason, name, view.target),
                onRematch = actions.rematch, onFinish = onLeave,
            ) {
                val rungs = ladderRungs(view.chain, view.targetOwner?.let { it == "me" }, view.timer, name)
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    LadderList(rungs)
                    Text("The glowing, underlined letter is the one that changed.", color = p.muted, fontSize = TextSize.Label, modifier = Modifier.padding(top = Space.S, start = Space.S))
                }
            }
        },
    ) {
        when {
            picking -> LadderPick(room, view, name, state, actions)
            flipping -> LadderFlip(room, view, name) { flipSeen = true }
            else -> LadderPlay(view, state, finished, name, now, actions)
        }
    }
}

/** Takes a refusal the server sent for the move or pick just made, and keeps it for the screen to show in place. */
@Composable
private fun RefusalCatcher(state: BattleUiState, waiting: Boolean, actions: LadderActions, onRefused: (String) -> Unit) {
    LaunchedEffect(state.message) {
        val m = state.message
        if (waiting && m != null && state.messageTone == Tone.Bad) {
            onRefused(m)
            actions.dismissMessage()
        }
    }
}

// ---- The secret word ------------------------------------------------------------------------------

@Composable
private fun ColumnScope.LadderPick(room: RoomState, view: LadderView, name: String, state: BattleUiState, actions: LadderActions) {
    val p = AppTheme.palette
    var typed by rememberSaveable(room.code, room.round) { mutableStateOf(view.myPick.orEmpty()) }
    var refused by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(state.firing) { if (!state.firing) waiting = false }
    RefusalCatcher(state, waiting, actions) { refused = it }

    val check = ladderPickCheck(typed, view.length)
    val locked = view.myPick != null && view.myPick == typed
    fun lock() {
        if (!check.canLock || locked || state.firing) return
        refused = null
        waiting = true
        actions.pick(typed)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.XS)) {
        Text("Pick your secret word", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text(
            "A real ${view.length}-letter word. $name won't see it until the coin flip. You can change it until then.",
            color = p.muted, fontSize = TextSize.Body,
        )
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Your word: ${typed.ifEmpty { "empty" }}" }, horizontalArrangement = Arrangement.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            for (i in 0 until view.length) {
                val letter = typed.getOrNull(i)
                LadderTile(letter, 52.dp, selected = letter == null && i == typed.length, fill = if (locked) p.accent else null)
            }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val reason = refused
        if (reason != null) StatusLine(reason, Tone.Bad) else StatusLine(check.text, check.tone)
        StatusLine(
            when {
                locked -> "Locked in. Waiting for $name"
                view.myPick != null -> "Waiting for $name. Your word is still ${view.myPick}."
                view.opponentPicked -> "$name has picked"
                else -> "$name is picking"
            },
            Tone.Neutral,
        )
    }
    PrimaryButton(
        when {
            locked -> "Locked in"
            view.myPick != null && typed.length == view.length -> "Change to $typed"
            typed.length == view.length -> "Lock in $typed"
            else -> "Lock in"
        },
        ::lock, enabled = check.canLock && !locked && !state.firing,
    )
    WordKeyboard(
        emptyList(),
        onLetter = { c -> if (typed.length < view.length) { typed += c.uppercaseChar(); refused = null } },
        onDelete = { typed = typed.dropLast(1); refused = null },
        onEnter = ::lock,
    )
}

// ---- The coin flip --------------------------------------------------------------------------------

@Composable
private fun ColumnScope.LadderFlip(room: RoomState, view: LadderView, name: String, onStart: () -> Unit) {
    val p = AppTheme.palette
    val start = view.start.orEmpty()
    val target = view.target.orEmpty()
    val startMine = view.startOwner == "me"
    val targetMine = view.targetOwner == "me"
    val starterName = if (startMine) "You" else name
    // The coin turns over and lands on whoever's word starts.
    var up by remember { mutableStateOf(false) }
    val still = LocalReduceMotion.current
    LaunchedEffect(Unit) {
        if (!still) delay(250)
        up = true
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.M)) {
        FlipCard(
            faceUp = up, key = view.start,
            back = { Coin { Text("?", color = p.text, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = TextSize.DisplayL) } },
            front = {
                Coin {
                    val who = if (startMine) room.me else room.opponent
                    PlayerAvatar(if (startMine) "You" else name, if (startMine) p.me else com.pippop.ui.theme.OpponentColor, size = 56, avatar = who?.avatar)
                }
            },
        )
        Text(
            if (startMine) "Your word starts" else "$name's word starts", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            "$start → ${if (targetMine) "your word" else "$name's word"}: $target",
            color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
    }
    AppCard {
        Text("START · ${if (startMine) "YOUR WORD" else "${name.uppercase()}'S WORD"}", color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        LadderWord(start, 48.dp)
        Canvas(Modifier.align(Alignment.CenterHorizontally).size(28.dp)) {
            val line = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(
                Path().apply {
                    moveTo(size.width / 2, size.height * 0.1f); lineTo(size.width / 2, size.height * 0.9f)
                    moveTo(size.width * 0.2f, size.height * 0.65f); lineTo(size.width / 2, size.height * 0.9f); lineTo(size.width * 0.8f, size.height * 0.65f)
                },
                p.ink, style = line,
            )
        }
        Text("TARGET · ${if (targetMine) "YOUR WORD" else "${name.uppercase()}'S WORD"}", color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        LadderWord(target, 48.dp, fill = textFill(GameKind.LADDER.color))
    }
    Text(
        "Whoever makes the move that spells $target wins. ${if (targetMine) "You move" else "$name moves"} first.",
        color = p.text, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    PrimaryButton("Start climbing", onStart)
}

/** A coin: a round, outlined disc. */
@Composable
private fun Coin(content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Box(Modifier.size(88.dp).clip(CircleShape).background(p.card).border(3.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) { content() }
}

// ---- The ladder -----------------------------------------------------------------------------------

@Composable
private fun ColumnScope.LadderPlay(view: LadderView, state: BattleUiState, finished: Boolean, name: String, now: Long, actions: LadderActions) {
    val p = AppTheme.palette
    val chain = view.chain
    val current = chain.lastOrNull().orEmpty()
    val myTurn = view.turn == "me" && !finished
    val canMove = myTurn && !state.firing
    val rungs = ladderRungs(chain, view.targetOwner?.let { it == "me" }, view.timer, name)

    // The turn banner, with the timer running round it when there is one.
    if (!finished) {
        val text = if (myTurn) "Your turn: change one letter of $current" else "$name is thinking…"
        val left = if (view.timer > 0 && view.turnEndsAt > 0) view.turnEndsAt - (now + state.clockOffsetMs) else null
        if (left == null) TurnLine(text, myTurn, GameKind.LADDER.color)
        else WordClock((left / (view.timer * 1000f)).coerceIn(0f, 1f), ((left + 999) / 1000).coerceAtLeast(0)) { TurnLine(text, myTurn, GameKind.LADDER.color) }
    }

    // The ladder: start at the top, the word in play last; it scrolls to the newest rung.
    val listState = rememberLazyListState()
    val still = LocalReduceMotion.current
    LaunchedEffect(rungs.size) {
        if (rungs.isNotEmpty()) if (still) listState.scrollToItem(rungs.lastIndex) else listState.animateScrollToItem(rungs.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().clip(Shapes.L).background(p.card.copy(alpha = 0.6f)).border(1.dp, p.hairline, Shapes.L),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.S),
    ) {
        itemsIndexed(rungs) { i, rung -> LadderRung(rung, current = !finished && i == rungs.lastIndex) }
    }
    view.target?.let { LadderTarget(it, if (view.targetOwner == "me") "Your word" else "$name's word") }

    if (myTurn) LadderMove(view, state, canMove, actions)
}

/** Your turn: tap a letter of the word, pick its new letter, Play. */
@Composable
private fun ColumnScope.LadderMove(view: LadderView, state: BattleUiState, canMove: Boolean, actions: LadderActions) {
    val p = AppTheme.palette
    val chain = view.chain
    val current = chain.last()
    // Each new word, or a turn passing, starts the choice over.
    var spot by remember(chain.size, view.turn) { mutableIntStateOf(-1) }
    var letter by remember(chain.size, view.turn) { mutableStateOf<Char?>(null) }
    var problem by remember(chain.size, view.turn) { mutableStateOf<String?>(null) }
    var shakes by remember(chain.size, view.turn) { mutableIntStateOf(0) }
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(state.firing) { if (!state.firing) waiting = false }
    val draft = if (spot >= 0 && letter != null) current.substring(0, spot) + letter + current.substring(spot + 1) else current

    fun refuse(why: String) { problem = why; shakes++ }
    RefusalCatcher(state, waiting, actions) { refuse(it) }

    fun play() {
        if (letter == null || !canMove) return
        val why = ladderMoveProblem(chain, draft)
        if (why != null) { refuse(why); return }
        problem = null
        waiting = true
        actions.move(draft)
    }
    fun type(c: Char) {
        if (spot < 0) { problem = null; refuse("Tap a letter of $current first"); return }
        val up = c.uppercaseChar()
        if (up == current[spot]) { refuse("Change one letter"); letter = null; return }
        problem = null
        letter = up
    }

    // The prompt, or why the word was refused, and Play beside it.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.M)) {
        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
            val why = problem
            if (why != null) {
                Text("✕  $why", color = p.bad, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                ladderProblemHelp(why, draft)?.let { Text(it, color = p.muted, fontSize = TextSize.Label, maxLines = 2) }
            } else {
                Text(
                    when {
                        spot < 0 -> "Tap a letter of $current to change it"
                        letter == null -> "Now pick the new letter for spot ${spot + 1}"
                        else -> "$current → $draft"
                    },
                    color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold, maxLines = 2,
                )
            }
        }
        PrimaryButton("Play", ::play, Modifier.width(112.dp), enabled = canMove && letter != null)
    }
    // The word in play, a tile to touch for each letter; the new letter shows in the one chosen.
    Row(Modifier.fillMaxWidth().shake(shakes.takeIf { it > 0 }), horizontalArrangement = Arrangement.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            draft.forEachIndexed { i, c ->
                LadderTile(
                    c, 48.dp, changed = i == spot && letter != null, selected = i == spot && letter == null,
                    modifier = Modifier
                        .semantics { selected = i == spot; contentDescription = "Letter ${i + 1}, ${current[i]}" + if (i == spot && letter != null) ", changing to $c" else "" }
                        .clickable(enabled = canMove, role = Role.Button, onClickLabel = "Change this letter") { spot = i; letter = null; problem = null },
                )
            }
        }
    }
    WordKeyboard(emptyList(), onLetter = ::type, onDelete = { if (letter != null) letter = null else spot = -1; problem = null }, onEnter = ::play)
}

// ---- Rules art: the ladder as the tutorial and the shelf show it ------------------------------------------

/** A picture of a ladder for tutorials: [chain] from the top, an optional [target] under it, tiles sized to fit. */
@Composable
internal fun LadderPicture(chain: List<String>, target: String?, modifier: Modifier = Modifier, selected: Int? = null) {
    val p = AppTheme.palette
    val rungs = ladderRungs(chain, null, 0, "")
    BoxWithConstraints(modifier.clip(Shapes.L).background(p.card).border(1.dp, p.hairline, Shapes.L), contentAlignment = Alignment.Center) {
        val length = chain.first().length
        val tile = minOf(maxWidth / (length + 1), maxHeight / (rungs.size + (if (target != null) 2 else 1)), 48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(tile * 0.12f), horizontalAlignment = Alignment.CenterHorizontally) {
            rungs.forEachIndexed { i, rung ->
                Row(horizontalArrangement = Arrangement.spacedBy(tile * 0.1f)) {
                    rung.word.forEachIndexed { j, c -> LadderTile(c, tile, changed = j == rung.changed, selected = i == rungs.lastIndex && j == selected) }
                }
            }
            if (target != null) LadderWord(target, tile, fill = textFill(GameKind.LADDER.color))
        }
    }
}
