package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.words.HuntLevel
import com.pippop.game.words.HuntOptions
import com.pippop.game.words.HuntPlace
import com.pippop.game.words.HuntView
import com.pippop.game.words.HuntViewWord
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.textFill

/*
 * Word Hunt's screens, against your partners (online or nearby): the setup card, the covered grid with its 3-2-1, the grid with
 * its word list, the found-word moment and the result. The rules live in game/words/HuntMatch.kt; the screens only ask and show,
 * and show the room's own words when a claim is refused. The grid and the hand are in HuntBoard.kt.
 */

/** What the Word Hunt screen asks of the view model, so a test can stand a room of its own behind it. */
class HuntActions(
    val claim: (Int, Int, Int, Int) -> Unit,
    val hint: () -> Unit,
    val rematch: () -> Unit,
    val dismissMessage: () -> Unit,
)

fun huntActions(vm: BattleViewModel) = HuntActions(vm::huntClaim, vm::huntHint, vm::readyTurns, vm::dismissMessage)

/** How long a found word is announced. */
private const val MOMENT_MS = 2400L
private const val REFUSAL_MS = 2600L

// ---- Setup ----------------------------------------------------------------------------------------

private val HuntThemeNames = listOf("Animals", "Food", "Sport", "Nature", "Home", "Mixed")

private fun huntLevelName(l: HuntLevel) = l.key.replaceFirstChar { it.uppercase() }

/** "Easy 10×10": the choice's label. */
internal fun huntLevelLabel(l: HuntLevel) = "${huntLevelName(l)} ${l.width}×${l.height}"

/**
 * Word Hunt's setup, in the lobby and in the Change sheet: the size, the words' theme, backwards words and the timer. Whoever opened
 * the game changes them; everyone else sees them greyed.
 */
@Composable
fun HuntOptionsCard(room: RoomState, busy: Boolean, onPick: (HuntOptions) -> Unit) {
    val o = room.huntOptions
    val p = AppTheme.palette
    OptionsCard(room, "Word Hunt", busy) { enabled ->
        OptionRow(
            "Size", HuntLevel.entries.map { it to huntLevelLabel(it) }, o.level,
            // A new size brings its usual choice about backwards words, unless this one was changed by hand.
            { l -> onPick(o.copy(level = l, backwards = if (o.backwards == (o.level != HuntLevel.EASY)) l != HuntLevel.EASY else o.backwards)) },
            enabled = enabled, perRow = 2,
            note = "${o.level.words} words in ${o.level.width} columns by ${o.level.height} rows." + if (o.level.width > 10) " Pinch to zoom." else "",
        )
        OptionRow("Words", HuntThemeNames.map { it to it }, o.theme, { onPick(o.copy(theme = it)) }, enabled = enabled, perRow = 3)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = o.backwards, enabled = enabled, role = Role.Switch) { onPick(o.copy(backwards = it)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Backwards words", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                Text(
                    if (o.backwards) "Words may read right to left and bottom to top." else "Words read left to right and top to bottom.",
                    color = p.muted, fontSize = TextSize.Label,
                )
            }
            Spacer(Modifier.width(Space.M))
            ToySwitch(checked = o.backwards, onCheckedChange = null, enabled = enabled)
        }
        OptionRow(
            "Timer", HuntOptions.TIMERS.map { it to if (it == 0) "None" else "$it min" }, o.timer, { onPick(o.copy(timer = it)) }, enabled = enabled,
            note = if (o.timer == 0) "No clock: the round ends when every word is found." else "${o.timer} minutes, or until every word is found.",
        )
    }
}

/** The mode line in the menu and the result ("Easy · Animals · 5 min"). */
internal fun huntModeLabel(o: HuntOptions) = huntLevelName(o.level) + " · " + o.theme + if (o.timer > 0) " · ${o.timer} min" else ""

private fun points(n: Int) = if (n == 1) "1 point" else "$n points"

// ---- The screen -----------------------------------------------------------------------------------

/** Word Hunt against your partners. [clock] is the room's time (a test sets its own). */
@Composable
fun HuntGameScreen(
    state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit,
    actions: HuntActions = huntActions(vm), clock: (BattleUiState) -> Long = { it.serverNow() },
) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val view = room.turns?.hunt ?: return
    val tick = rememberTicker()
    val now = clock(state)
    val phase = state.phase(now)
    val finished = room.status == RoomStatus.FINISHED
    val seats = huntSeats(room, view, p.me)
    val mine = seats.first()
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.HUNT) { showRules = false }

    val covered = !finished && (phase != RoomStatus.PLAYING || view.rows.isEmpty())
    val playing = !finished && !covered && view.phase == "playing"
    val viewport = remember(view.width, view.height, room.code, room.round) { HuntViewport(view.width, view.height) }
    var anchor by remember(room.code, room.round) { mutableStateOf<HuntCell?>(null) }

    // Words found since the screen last looked: announced once ("Maya found TIGER"), never for what was found before it opened.
    var known by remember(room.code, room.round) { mutableStateOf<Set<String>?>(null) }
    var moment by remember(room.code, room.round) { mutableStateOf<HuntMoment?>(null) }
    LaunchedEffect(view.words) {
        val before = known
        if (before != null) huntNewFinds(before, view).lastOrNull()?.let { moment = it }
        known = view.words.filter { it.foundBy != null }.map { it.word }.toSet()
    }
    LaunchedEffect(moment) { if (moment != null) { kotlinx.coroutines.delay(MOMENT_MS); moment = null } }
    val found = view.words.count { it.foundBy != null }
    SoundOnChange(found.takeIf { it > 0 }) { play(Sound.GRID_WORD) }

    // What the room refused (Not a word here, Already found by Maya, ...), said in place with a short shake of the grid.
    var refusal by remember { mutableStateOf<String?>(null) }
    var refusals by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.message) {
        val m = state.message
        if (m != null && state.messageTone == Tone.Bad) {
            refusal = huntRefusalText(m, seats)
            refusals++
            actions.dismissMessage()
        }
    }
    LaunchedEffect(refusals) { if (refusals > 0) { kotlinx.coroutines.delay(REFUSAL_MS); refusal = null } }
    // A hint brings you to the first letter when the grid is zoomed.
    LaunchedEffect(view.hints) { view.hints.lastOrNull()?.let { (r, c) -> viewport.centreOn(HuntCell(r, c)) } }

    val players = battleChips(
        room, state, tick, p.me,
        detail = { player, isMe ->
            val seat = if (isMe) mine else seats.getOrNull(1 + room.others.indexOf(player))
            seat?.let { points(view.scores[it.mark] ?: 0) }
        },
    )
    val clockText = huntClock(view, now)
    val hurry = huntHurry(view, now)

    GameFrame(
        title = "Word Hunt",
        subtitle = "Game ${room.round} · " + huntModeLabel(HuntOptions(view.level, view.theme, view.backwards, view.timer)) + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = GameKind.HUNT.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trailing = {
            if (clockText != null && !finished) {
                Text(
                    clockText, style = Digits, color = if (hurry) p.bad else p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "Time left $clockText" },
                )
            }
        },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }, onLine = vm.lineSender { reacting = false }, onPack = vm.packSender { reacting = false }) },
        popup = if (!finished) null else popupContent {
            val end = huntEnd(view, seats, room.winner, room.endReason, room.winnerName)
            MatchPopup(room, GameKind.HUNT, end.outcome, end.title, end.detail, onRematch = actions.rematch, onFinish = onLeave) { HuntResults(view, seats) }
        },
    ) {
        HuntStatus(finished, covered, moment, refusal, anchor != null, view.hints.isNotEmpty(), seats)
        if (covered) {
            val left = ((room.startAt ?: now) - now + 999) / 1000
            BoardArea(view.width.toFloat() / view.height) { size -> HuntCover(view, left, size) }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HuntBoard(
                    view, seats, viewport, enabled = playing, anchor = anchor, onAnchor = { anchor = it },
                    onClaim = { a, b -> actions.claim(a.row, a.col, b.row, b.col) },
                    refusals = refusals, finds = found, flash = moment, modifier = Modifier.fillMaxSize().shake(refusals.takeIf { it > 0 }),
                )
            }
        }
        HuntWordList(view, seats, moment?.word)
        HuntControls(view, seats, viewport, playing, actions)
    }
}

// ---- The status line, the countdown ----------------------------------------------------------------

/** One line above the grid: what to do, or the word just found, or why a drag was not counted. */
@Composable
private fun HuntStatus(finished: Boolean, covered: Boolean, moment: HuntMoment?, refusal: String?, tapping: Boolean, hinted: Boolean, seats: List<HuntSeat>) {
    val p = AppTheme.palette
    val seat = moment?.let { seats.of(it.mark) }
    val fill = if (seat != null) textFill(seat.color) else p.accent
    val ink = if (seat != null) readableOn(fill) else p.text
    val text = when {
        moment != null -> huntMomentText(moment, seats)
        refusal != null -> "✕  $refusal"
        finished -> "Round over"
        covered -> "The grid shows when the countdown ends"
        tapping -> "Now tap the last letter of the word"
        hinted -> "The ring shows where a word starts. Drag, or tap its first and last letters"
        else -> "Drag across a word, or tap its first and last letters"
    }
    Surface(
        color = if (refusal != null && moment == null) p.card else fill, shape = Shapes.L,
        border = BorderStroke(if (seat != null) 3.dp else 2.dp, p.ink),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = Space.M, vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
            if (moment != null) {
                // The finder's initial in a disc, so the find is not told by colour alone.
                Box(Modifier.size(28.dp).clip(CircleShape).background(ink).padding(2.dp), contentAlignment = Alignment.Center) {
                    Text(seat?.initial.orEmpty(), color = fill, fontSize = TextSize.Label, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(Space.S))
            }
            Text(
                text, color = if (refusal != null && moment == null) p.bad else ink, fontSize = TextSize.Body,
                fontWeight = if (moment != null || refusal != null) FontWeight.Bold else FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f),
            )
            if (moment != null) Text("+${moment.word.length}", color = ink, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Black, style = Digits)
        }
    }
}

/** The grid before the round: covered, with a plain "Get ready" and then 3, 2, 1. */
@Composable
private fun HuntCover(view: HuntView, left: Long, size: Modifier) {
    val p = AppTheme.palette
    Box(size.clip(Shapes.M).background(p.card).border(2.dp, p.ink, Shapes.M), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cw = this.size.width / view.width
            val ch = this.size.height / view.height
            for (c in 1 until view.width) drawLine(p.hairline, Offset(c * cw, 0f), Offset(c * cw, this.size.height), 1.dp.toPx())
            for (r in 1 until view.height) drawLine(p.hairline, Offset(0f, r * ch), Offset(this.size.width, r * ch), 1.dp.toPx())
        }
        Column(
            Modifier.clip(Shapes.L).background(p.card).padding(horizontal = Space.XL, vertical = Space.L).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                left in 1..3 -> {
                    Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                    CountdownText(left, p.primaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                left > 3 -> {
                    Text("Get ready", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                    Text("${view.words.size} ${view.theme.lowercase()} words are hiding", color = p.muted, fontSize = TextSize.Body)
                }
                else -> Text("Go!", color = p.primaryText, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---- The word list and the controls ---------------------------------------------------------------

/** The words to find: found ones struck through and tagged with the finder's initial and colour, the rest plain. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HuntWordList(view: HuntView, seats: List<HuntSeat>, flashed: String?) {
    val p = AppTheme.palette
    val found = view.words.count { it.foundBy != null }
    Column(
        Modifier.fillMaxWidth().clip(Shapes.L).background(p.card.copy(alpha = 0.6f)).border(1.dp, p.hairline, Shapes.L).padding(Space.S),
        verticalArrangement = Arrangement.spacedBy(Space.XS),
    ) {
        Text("Words: $found of ${view.words.size} found", color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        FlowRow(
            Modifier.heightIn(max = 104.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            view.words.sortedWith(compareBy({ it.foundBy != null }, { -it.length }, { it.word })).forEach { WordChip(it, seats.of(it.foundBy), it.word == flashed) }
        }
    }
}

@Composable
private fun WordChip(word: HuntViewWord, seat: HuntSeat?, flashed: Boolean) {
    val p = AppTheme.palette
    val said = if (seat == null) "${word.word}, not found yet" else "${word.word}, found by ${seat.name}"
    Row(
        Modifier.clip(Shapes.Full)
            .background(if (seat == null) p.card else lerp(p.card, seat.color, if (flashed) 0.4f else 0.2f))
            .border(if (seat == null) 1.dp else 2.dp, seat?.color ?: p.hairline, Shapes.Full)
            .padding(start = if (seat == null) 10.dp else 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (seat != null) {
            val fill = textFill(seat.color)
            Box(Modifier.size(22.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
                Text(seat.initial, color = readableOn(fill), fontSize = TextSize.Micro, fontWeight = FontWeight.Black)
            }
        }
        Text(
            word.word, color = if (seat == null) p.text else p.muted, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
            textDecoration = if (seat == null) null else TextDecoration.LineThrough, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}

/** The hint button (and how many are left), and on grids wider than 10 the zoom buttons: a way to zoom without pinching. */
@Composable
private fun HuntControls(view: HuntView, seats: List<HuntSeat>, viewport: HuntViewport, playing: Boolean, actions: HuntActions) {
    val left = view.hintsLeft[view.me] ?: 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("Hint" + if (left > 0) " (minus 2)" else " used", actions.hint, Modifier.weight(1f), enabled = playing && left > 0)
        if (viewport.canZoom) {
            val max = viewport.geometry?.maxZoom ?: 1f
            HuntMap(view, seats, viewport)
            ZoomButton("-", "Zoom out", playing && viewport.zoom > 1.02f, Modifier.width(48.dp), viewport::zoomOut)
            ZoomButton("+", "Zoom in", playing && viewport.zoom < max - 0.02f, Modifier.width(48.dp), viewport::zoomIn)
            ZoomButton("All", "Show the whole grid", playing && viewport.zoomed, Modifier.width(56.dp), viewport::fit)
        }
    }
}

/** The small map beside the zoom buttons: the whole grid with its found words, and a frame round what the window shows. */
@Composable
private fun HuntMap(view: HuntView, seats: List<HuntSeat>, viewport: HuntViewport) {
    val p = AppTheme.palette
    val g = viewport.geometry
    val cam = viewport.camera
    Canvas(
        Modifier.size(width = 52.dp, height = 54.dp)
            .semantics { contentDescription = if (viewport.zoomed) "Map of the grid, zoomed in" else "Map of the grid, all of it showing" },
    ) {
        val pad = 4.dp.toPx()
        val k = minOf((size.width - 2 * pad) / view.width, (size.height - 2 * pad) / view.height)
        val w = k * view.width
        val h = k * view.height
        val at = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        drawRoundRect(p.card, androidx.compose.ui.geometry.Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        drawRect(com.pippop.ui.theme.Board.Bone, at, androidx.compose.ui.geometry.Size(w, h))
        for (word in view.words) {
            val seat = seats.of(word.foundBy) ?: continue
            val r = word.row ?: continue
            val c = word.col ?: continue
            val d = com.pippop.game.words.HuntDir.entries.firstOrNull { it.key == word.dir } ?: continue
            val from = at + Offset((c + 0.5f) * k, (r + 0.5f) * k)
            val to = at + Offset((c + d.dc * (word.length - 1) + 0.5f) * k, (r + d.dr * (word.length - 1) + 0.5f) * k)
            drawLine(seat.color, from, to, maxOf(k * 0.9f, 2.dp.toPx()), androidx.compose.ui.graphics.StrokeCap.Round)
        }
        if (g != null) {
            val (l, t, rr, b) = g.seen(cam)
            val tl = at + Offset(l * k, t * k)
            val sz = androidx.compose.ui.geometry.Size((rr - l) * k, (b - t) * k)
            drawRect(com.pippop.ui.theme.Board.Glow.copy(alpha = 0.3f), tl, sz)
            drawRect(com.pippop.ui.theme.Board.Ink, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        drawRect(com.pippop.ui.theme.Board.Ink, at, androidx.compose.ui.geometry.Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawRoundRect(p.ink, androidx.compose.ui.geometry.Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

/** A square button of the app's look for the zoom: [label] on it, [description] for a screen reader, 54 dp tall. */
@Composable
private fun ZoomButton(label: String, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = AppTheme.palette
    val edge = p.ink.copy(alpha = if (enabled) 1f else 0.3f)
    Box(modifier.height(54.dp).pressedEdge(edge, Radius.L)) {
        Box(
            Modifier.fillMaxSize().clip(Shapes.L).background(p.card).border(2.dp, edge, Shapes.L)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            val ink = p.text.copy(alpha = if (enabled) 1f else 0.5f)
            if (label == "All") Text(label, color = ink, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
            else Canvas(Modifier.size(18.dp)) {
                // A minus and a plus drawn as strokes: the text hyphen looked like a dash.
                val w = 3.dp.toPx()
                drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, androidx.compose.ui.graphics.StrokeCap.Round)
                if (label == "+") drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

// ---- The result -----------------------------------------------------------------------------------

/** Everyone's place with their score and words, and the round's facts. */
@Composable
internal fun HuntResults(view: HuntView, seats: List<HuntSeat>) {
    val p = AppTheme.palette
    val places = view.results.ifEmpty {
        seats.map { HuntPlace(it.mark, view.scores[it.mark] ?: 0, huntFinds(view, it.mark).size, 1) }.sortedByDescending { it.score }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        places.forEach { place ->
            val seat = seats.of(place.mark) ?: return@forEach
            val words = huntFinds(view, place.mark)
            Row(
                Modifier.fillMaxWidth().clip(Shapes.M).background(p.background).padding(Space.S)
                    .semantics(mergeDescendants = true) { contentDescription = "${huntPlace(place.rank)}: ${seat.name}, ${points(place.score)}, ${words.joinToString { it.word }.ifEmpty { "no words" }}" },
                horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.Top,
            ) {
                Text(huntPlace(place.rank), color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
                PlayerAvatar(seat.name, seat.color, size = 32, avatar = seat.avatar)
                Column(Modifier.weight(1f)) {
                    Text("${seat.name} · ${points(place.score)}", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                    Text(words.joinToString(", ") { it.word }.ifEmpty { "No words found" }, color = p.muted, fontSize = TextSize.Label)
                }
            }
        }
        Text("THE ROUND", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.XS))
        Fact("Grid", "${view.width} × ${view.height}, ${view.theme}")
        huntLongest(view, seats)?.let { Fact("Longest word", it) }
        Fact("Hints used", seats.joinToString(", ") { "${it.name} ${1 - (view.hintsLeft[it.mark] ?: 1)}" })
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(label, color = p.muted, fontSize = TextSize.Label, modifier = Modifier.width(96.dp))
        Text(value, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

// ---- Rules art ------------------------------------------------------------------------------------

private val PictureRows = listOf("TIGERQXK", "OAXKPLMD", "MZOWLQRE", "BEARNSTH", "KQDHXPCV", "LUOWLZAN", "PRSTNMEG", "ZXCVBNMQ")

/** A small grid for the tutorial: [found] words (TIGER across the top, OWL down from the second row) in the colours of you ('h') and Maya ('g'). */
@Composable
internal fun HuntPicture(found: List<Pair<String, Char>>, hint: Boolean, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val rows = PictureRows
    val places = mapOf("TIGER" to Triple(0, 0, "right"), "OWL" to Triple(5, 2, "right"))
    val words = listOf("TIGER", "OWL", "BEAR").map { w ->
        val at = places[w]
        val who = found.firstOrNull { it.first == w }?.second
        HuntViewWord(w, w.length, who, if (who != null) at?.first ?: 3 else null, if (who != null) at?.second ?: 0 else null, if (who != null) "right" else null)
    }
    val view = HuntView(
        'h', HuntLevel.EASY, "Animals", false, 0, 8, 8, rows, "playing", words, listOf('h', 'g'), mapOf('h' to 5, 'g' to 3), mapOf('h' to 1, 'g' to 1),
        if (hint) listOf(3 to 0) else emptyList(), 0, null, emptyList(), null, emptyList(),
    )
    val seats = listOf(
        HuntSeat('h', "You", "Y", p.me, true, null),
        HuntSeat('g', "Maya", "M", com.pippop.ui.theme.SeatColors[0], false, null),
    )
    val viewport = remember { HuntViewport(8, 8) }
    HuntBoard(view, seats, viewport, enabled = false, anchor = null, onAnchor = {}, onClaim = { _, _ -> }, refusals = 0, finds = found.size, flash = null, modifier = modifier)
}
