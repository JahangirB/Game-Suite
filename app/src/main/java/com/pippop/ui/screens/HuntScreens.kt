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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import com.pippop.game.words.HuntFound
import com.pippop.data.PlayerInfo
import androidx.compose.ui.platform.testTag
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.HuntSoloMode
import com.pippop.ui.SoloHuntUiState
import com.pippop.ui.SoloHuntViewModel
import com.pippop.ui.startWith
import com.pippop.data.ProfileStore
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.pippop.ui.art.Outcome
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
 * its "9 of 24 found" line, the found-word moment, the chips that count each player's finds (a tap lists them) and the result. The
 * words themselves stay hidden: there is no list. The rules live in game/words/HuntMatch.kt; the screens only ask and show,
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

private fun huntLevelName(l: HuntLevel) = l.key.replaceFirstChar { it.uppercase() }

/** "Easy 10×10": the choice's label. */
internal fun huntLevelLabel(l: HuntLevel) = "${huntLevelName(l)} ${l.width}×${l.height}"

/**
 * Word Hunt's setup, in the lobby and in the Change sheet: the size, backwards words and the timer. Whoever opened
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

/** The mode line in the menu and the result ("Easy · 5 min"). */
internal fun huntModeLabel(o: HuntOptions) = huntLevelName(o.level) + if (o.timer > 0) " · ${o.timer} min" else ""

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
    val feed = rememberHuntFeed(room.code to room.round, view, seats, state.message, state.messageTone == Tone.Bad, actions.dismissMessage)

    var opened by remember { mutableStateOf<Char?>(null) }
    fun seatOf(player: PlayerInfo, isMe: Boolean) = if (isMe) mine else seats.getOrNull(1 + room.others.indexOf(player))
    val players = battleChips(
        room, state, tick, p.me,
        detail = { player, isMe -> seatOf(player, isMe)?.let { huntChipDetail(huntFoundBy(view, it.mark), view.scores[it.mark] ?: 0, seats.size > 2) } },
        badge = { player, isMe -> seatOf(player, isMe)?.let { huntFoundBy(view, it.mark) } },
        onClick = { player, isMe -> seatOf(player, isMe)?.let { seat -> Triple(huntChipLabel(seat), huntChipSpeech(seat, view), { opened = seat.mark }) } },
    )
    opened?.let { mark -> seats.of(mark)?.let { HuntWordsSheet(view, it) { opened = null } } }
    val clockText = huntClock(view, now)
    val hurry = huntHurry(view, now)

    GameFrame(
        title = "Word Hunt",
        subtitle = "Game ${room.round} · " + huntModeLabel(HuntOptions(view.level, view.backwards, view.timer)) + if (state.reconnecting) " · reconnecting…" else "",
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
        HuntArena(feed, view, seats, covered, ((room.startAt ?: now) - now + 999) / 1000, playing, finished, actions)
    }
}

/**
 * What the Word Hunt screens keep between frames, for one round: the camera, the letter chosen first when tapping, the word just
 * found (announced once, never for what was found before the screen opened) and what the room refused (shown with a shake).
 */
@Stable
internal class HuntFeed(val viewport: HuntViewport) {
    var anchor by mutableStateOf<HuntCell?>(null)
    var known by mutableStateOf<Set<String>?>(null)
    var moment by mutableStateOf<HuntMoment?>(null)
    var refusal by mutableStateOf<String?>(null)
    var refusals by mutableIntStateOf(0)
}

/** The feed of the round [key], taking [message] (shown once it is [bad]; [dismiss] clears it) as the room's answer to the last claim. */
@Composable
internal fun rememberHuntFeed(key: Any, view: HuntView, seats: List<HuntSeat>, message: String?, bad: Boolean, dismiss: () -> Unit): HuntFeed {
    val feed = remember(key, view.width, view.height) { HuntFeed(HuntViewport(view.width, view.height)) }
    LaunchedEffect(view.found) {
        val before = feed.known
        if (before != null) huntNewFinds(before, view).lastOrNull()?.let { feed.moment = it }
        feed.known = view.found.map { it.word }.toSet()
    }
    LaunchedEffect(feed.moment) { if (feed.moment != null) { kotlinx.coroutines.delay(MOMENT_MS); feed.moment = null } }
    SoundOnChange(view.found.size.takeIf { it > 0 }) { play(Sound.GRID_WORD) }
    LaunchedEffect(message) {
        if (message != null && bad) {
            feed.refusal = huntRefusalText(message, seats)
            feed.refusals++
            dismiss()
        }
    }
    LaunchedEffect(feed.refusals) { if (feed.refusals > 0) { kotlinx.coroutines.delay(REFUSAL_MS); feed.refusal = null } }
    // A hint brings you to the first letter when the grid is zoomed.
    LaunchedEffect(view.hints) { view.hints.lastOrNull()?.let { (r, c) -> feed.viewport.centreOn(HuntCell(r, c)) } }
    return feed
}

/** What fills a Word Hunt screen under its chips: the status line, the covered grid or the grid, "9 of 24 found" and the controls. */
@Composable
internal fun ColumnScope.HuntArena(feed: HuntFeed, view: HuntView, seats: List<HuntSeat>, covered: Boolean, left: Long, playing: Boolean, finished: Boolean, actions: HuntActions) {
    HuntStatus(finished, covered, feed.moment, feed.refusal, feed.anchor != null, view.hints.isNotEmpty(), seats)
    if (covered) {
        BoardArea(view.width.toFloat() / view.height) { size -> HuntCover(view, left, size) }
    } else {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HuntBoard(
                view, seats, feed.viewport, enabled = playing, anchor = feed.anchor, onAnchor = { feed.anchor = it },
                onClaim = { a, b -> actions.claim(a.row, a.col, b.row, b.col) },
                refusals = feed.refusals, finds = view.found.size, flash = feed.moment,
                modifier = Modifier.fillMaxSize().shake(feed.refusals.takeIf { it > 0 }),
            )
        }
    }
    HuntProgress(view)
    HuntControls(view, seats, feed.viewport, playing, actions)
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
                    Text("${view.total} words are hiding", color = p.muted, fontSize = TextSize.Body)
                }
                else -> Text("Go!", color = p.primaryText, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---- How many are found, the sheet of a player's words, and the controls ------------------------------

/** "9 of 24 found" under the grid. The words themselves are hidden: this and the chips are all the round says about them. */
@Composable
private fun HuntProgress(view: HuntView) {
    val p = AppTheme.palette
    Text(
        huntProgress(view), color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, style = Digits, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag("huntProgress"),
    )
}

/** What a screen reader says for a chip: "Maya, 7 found, 12 points". */
internal fun huntChipSpeech(seat: HuntSeat, view: HuntView) = "${seat.name}, ${huntFoundBy(view, seat.mark)} found, ${points(view.scores[seat.mark] ?: 0)}"

/** What a chip does when it is tapped, for a screen reader: "Show your words", "Show Maya's words". */
internal fun huntChipLabel(seat: HuntSeat) = if (seat.mine) "Show your words" else "Show ${seat.name}'s words"

/** The sheet a chip opens: [seat]'s found words, the newest first. */
@Composable
internal fun HuntWordsSheet(view: HuntView, seat: HuntSeat, onDismiss: () -> Unit) {
    ToySheet(onDismiss = onDismiss) { HuntWordsBody(view, seat) }
}

/** What the sheet holds (also drawn by screenshots, where the sheet's own window is not in the root). */
@Composable
internal fun HuntWordsBody(view: HuntView, seat: HuntSeat) {
    val p = AppTheme.palette
    val words = huntWordsOf(view, seat.mark)
    val fill = textFill(seat.color)
    Column(
        Modifier.fillMaxWidth().testTag("huntWordsSheet").padding(horizontal = Space.L).padding(bottom = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            PlayerAvatar(seat.name, seat.color, size = 36, avatar = seat.avatar)
            Column(Modifier.weight(1f)) {
                Text(huntSheetTitle(seat), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text(
                    "${words.size} of ${view.total} found · ${points(view.scores[seat.mark] ?: 0)}", color = p.muted, fontSize = TextSize.Label, style = Digits,
                    modifier = Modifier.testTag("huntWordsCount"),
                )
            }
        }
        if (words.isEmpty()) {
            Text(if (seat.mine) "No words yet. Drag across one in the grid." else "No words yet.", color = p.muted, fontSize = TextSize.Body)
        } else {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                words.forEach { w ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Shapes.M).background(lerp(p.card, seat.color, 0.18f)).border(2.dp, seat.color, Shapes.M)
                            .padding(horizontal = Space.S).semantics(mergeDescendants = true) { contentDescription = "${w.word}, ${w.length} points" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S),
                    ) {
                        // The finder's initial in their colour, so the list is not told by colour alone.
                        Box(Modifier.size(28.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
                            Text(seat.initial, color = readableOn(fill), fontSize = TextSize.Label, fontWeight = FontWeight.Black)
                        }
                        Text(w.word, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("+${w.length}", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Black, style = Digits)
                    }
                }
            }
        }
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
        for (word in view.found) {
            val seat = seats.of(word.mark) ?: continue
            val r = word.row
            val c = word.col
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
        Fact("Grid", "${view.width} × ${view.height}")
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
    val places = mapOf("TIGER" to (0 to 0), "OWL" to (5 to 2))
    val finds = found.mapIndexed { i, (w, who) ->
        val at = places.getValue(w)
        HuntFound(w, who, at.first, at.second, "right", 1000L * (i + 1))
    }
    val view = HuntView(
        'h', HuntLevel.EASY, false, 0, 8, 8, rows, "playing", 3, finds, listOf('h', 'g'), mapOf('h' to 5, 'g' to 3), mapOf('h' to 1, 'g' to 1),
        if (hint) listOf(3 to 0) else emptyList(), 0, null, emptyList(), null, emptyList(),
    )
    val seats = listOf(
        HuntSeat('h', "You", "Y", p.me, true, null),
        HuntSeat('g', "Maya", "M", com.pippop.ui.theme.SeatColors[0], false, null),
    )
    val viewport = remember { HuntViewport(8, 8) }
    HuntBoard(view, seats, viewport, enabled = false, anchor = null, onAnchor = {}, onClaim = { _, _ -> }, refusals = 0, finds = found.size, flash = null, modifier = modifier)
}


// ---- On your own ------------------------------------------------------------------------------------

/** Word Hunt alone: against the clock, or against computer finders. */
@Composable
fun SoloHuntScreen(state: SoloHuntUiState, vm: SoloHuntViewModel, onHome: () -> Unit, clock: () -> Long = vm::gameTime) {
    val p = AppTheme.palette
    val view = state.view
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    if (view == null) { BackToHome(onHome); return }
    rememberTicker()
    val now = clock()
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.HUNT) { showRules = false }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.HUNT, "New game", vm::startWith) { changing = false }
    val avatar = ProfileStore.get(LocalContext.current).profile.collectAsState().value.avatar
    val seats = huntSoloSeats(view, state.computers, p.me, avatar)
    var opened by remember { mutableStateOf<Char?>(null) }
    opened?.let { mark -> seats.of(mark)?.let { HuntWordsSheet(view, it) { opened = null } } }
    val finished = state.over
    val left = (SoloHuntViewModel.COUNTDOWN_MS - now + 999) / 1000
    val covered = !finished && left > 0
    val playing = !finished && !covered
    val feed = rememberHuntFeed(state.gameId, view, seats, state.message, true, vm::dismissMessage)
    val actions = remember(vm) { HuntActions(vm::claim, vm::hint, vm::rematch, vm::dismissMessage) }
    val clockText = huntClock(view, now)
    val hurry = huntHurry(view, now)
    val what = if (state.mode == HuntSoloMode.CLOCK) "Against the clock" else "Against the computer" + if (state.computers > 1) "s" else ""

    GameFrame(
        title = "Word Hunt",
        subtitle = what + " · " + huntModeLabel(HuntOptions(view.level, view.backwards, view.timer)),
        players = seats.map { seat ->
            ChipPlayer(
                seat.name, seat.avatar, seat.color, huntChipDetail(huntFoundBy(view, seat.mark), view.scores[seat.mark] ?: 0, seats.size > 2), mine = seat.mine,
                badge = huntFoundBy(view, seat.mark), onClick = { opened = seat.mark }, onClickLabel = huntChipLabel(seat), description = huntChipSpeech(seat, view),
            )
        },
        glow = GameKind.HUNT.color,
        menu = soloMenu(GameKind.HUNT, onHome, { showRules = true }, vm::rematch, { changing = true }),
        trailing = {
            if (clockText != null && !finished) {
                Text(
                    clockText, style = Digits, color = if (hurry) p.bad else p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "Time left $clockText" },
                )
            }
        },
        popup = if (!finished) null else popupContent {
            val found = view.found.size
            val end = huntEnd(view, seats, null, null, null)
            if (state.mode == HuntSoloMode.COMPUTERS) {
                ResultPopup(
                    GameKind.HUNT, end.outcome, end.title, end.detail,
                    finish = PopupAction("Home", onHome), again = PopupAction("Play again", vm::rematch),
                    extra = { HuntResults(view, seats) },
                )
            } else {
                val timed = view.timer > 0
                val all = found == view.total
                ResultPopup(
                    GameKind.HUNT, if (all) Outcome.WIN else Outcome.TIE,
                    if (timed) points(view.scores[view.me] ?: 0) else state.finishedMs?.let { "All found in ${huntElapsed(it)}" } ?: "Round over",
                    "$found of ${view.total} words found." + if (!timed && (view.hintsLeft[view.me] ?: 1) == 0) " The hint added 10 seconds." else "",
                    finish = PopupAction("Home", onHome), again = PopupAction("Play again", vm::rematch),
                    extra = { HuntResults(view, seats) },
                )
            }
        },
    ) {
        HuntArena(feed, view, seats, covered, left, playing, finished, actions)
    }
}

/** A time as m:ss for the result line. */
internal fun huntElapsed(ms: Long): String = (ms / 1000).let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
