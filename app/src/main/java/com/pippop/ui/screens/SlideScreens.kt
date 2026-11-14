package com.pippop.ui.screens

import com.pippop.ui.startWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.SeatColors
import com.pippop.ui.theme.contrast

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.slide.Slide
import com.pippop.game.slide.SlideLevel
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloSlideUiState
import com.pippop.ui.SoloSlideViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import kotlin.math.abs
import kotlin.math.sign

/** The puzzle: tiles glide to their new place; tap a tile in line with the gap to slide it. Swipes come from [slideSwipes]. */
@Composable
fun SlideBoard(tiles: List<Int>, size: Int, enabled: Boolean, onTap: (Int) -> Unit, modifier: Modifier = Modifier, photo: androidx.compose.ui.graphics.ImageBitmap? = null) {
    // A tile taps along one square; the last one in place sets off the cascade and chime.
    SoundOnChange(tiles) { play(if (tiles == Slide.solved(size)) Sound.SLIDE_SOLVED else Sound.PIECE_HOP) }
    val p = AppTheme.palette
    // A wooden tray: the frame is Board.Wood, the tiles sit in a darker well.
    Box(modifier.clip(Shapes.L).background(Brush.verticalGradient(listOf(Board.Wood.lighter(0.2f), Board.Wood.darker(0.2f)))).padding(6.dp)) {
    BoxWithConstraints(Modifier.fillMaxSize().clip(Shapes.M).background(Board.Wood.darker(0.45f))) {
        val cell = maxWidth / size
        val goal = Slide.solved(size)
        // A tile in place gets a green edge and a tick in its corner, in a contrasting ink.
        val tickInk = if (contrast(p.good, Color.White) >= contrast(p.good, Board.Ink)) Color.White else Board.Ink
        for (tile in 1 until size * size) key(tile) {
            val at = tiles.indexOf(tile)
            val x by animateDpAsState(cell * (at % size), tween(120), label = "x")
            val y by animateDpAsState(cell * (at / size), tween(120), label = "y")
            val home = goal[at] == tile
            Box(
                Modifier.offset(x, y).size(cell).padding(3.dp).clickable(enabled = enabled) { onTap(tile) }
                    .drawBehind {
                        // A thick tile: a shadow under it and a darker edge below a lighter top.
                        val lift = 4.dp.toPx()
                        val face = Size(this.size.width, this.size.height - lift)
                        val corner = Radius.M.toPx()
                        boxShadow(Offset(0f, lift * 0.5f), face, corner, drop = lift * 0.8f)
                        raisedTile(p.card, Offset.Zero, face, corner, lift)
                    }
                    .padding(bottom = 4.dp).clip(Shapes.M).border(if (home) 3.dp else 2.dp, if (home) p.good else p.ink, Shapes.M),
            ) {
                if (photo != null) {
                    // This tile's piece of the photo, and its number small in the corner.
                    Box {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val piece = photo.width / size
                            val r = (tile - 1) / size
                            val c = (tile - 1) % size
                            // The face is a little shorter than wide: crop the slice to match, not squeeze it.
                            val high = (piece * this.size.height / this.size.width).toInt()
                            drawImage(
                                photo, srcOffset = androidx.compose.ui.unit.IntOffset(c * piece, r * piece + (piece - high) / 2), srcSize = androidx.compose.ui.unit.IntSize(piece, high),
                                dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt()),
                            )
                        }
                        Text(
                            tile.toString(), color = Color.White, fontSize = (cell.value * 0.16f).sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.TopStart).padding(3.dp).background(p.scrim.copy(alpha = 0.45f), Shapes.S).padding(horizontal = 3.dp),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(tile.toString(), color = p.text, fontSize = (cell.value * 0.38f).sp, fontWeight = FontWeight.Black, fontFamily = DisplayFont)
                    }
                }
                if (home) {
                    // The tick sits in the top right corner, clear of the number (top left in photos, centred otherwise).
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().semantics { contentDescription = "in place" }) {
                        val r = this.size.minDimension * 0.11f
                        val c = Offset(this.size.width - r * 1.4f, r * 1.4f)
                        drawCircle(Color.White.copy(alpha = 0.9f), r * 1.18f, c)
                        drawCircle(p.good, r, c)
                        val tick = Path().apply {
                            moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
                            lineTo(c.x - r * 0.12f, c.y + r * 0.35f)
                            lineTo(c.x + r * 0.48f, c.y - r * 0.32f)
                        }
                        drawPath(tick, tickInk, style = Stroke(r * 0.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
    }
}

/** One player's progress for [SlideProgressBars]: [home] of [total] tiles in place. */
class SlideBar(val name: String, val color: Color, val home: Int, val total: Int)

/** How many tiles are in their right place. */
internal fun slideHome(tiles: List<Int>, size: Int): Int {
    val goal = Slide.solved(size)
    return tiles.indices.count { tiles[it] != 0 && tiles[it] == goal[it] }
}

/** Tiles home for a progress percentage from the server (which rounds down). */
internal fun slideHomeFromPercent(percent: Int, size: Int): Int {
    val total = size * size - 1
    return ((percent * total + 99) / 100).coerceIn(0, total)
}

/**
 * A bar for each player under the board: their name, how full the bar is, and the count of
 * tiles home as text, so it never relies on colour alone.
 */
@Composable
fun SlideProgressBars(bars: List<SlideBar>, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val reduce = LocalReduceMotion.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (bar in bars) {
            val target = if (bar.total == 0) 0f else bar.home.toFloat() / bar.total
            val fill = animateFloatAsState(target, if (reduce) snap() else tween(200), label = "bar")
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${bar.name}: ${bar.home} of ${bar.total} tiles home" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(bar.name, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(76.dp).clearAndSetSemantics {})
                Box(Modifier.weight(1f).height(12.dp).clip(Shapes.Full).background(p.accent).border(2.dp, p.ink, Shapes.Full).drawBehind {
                    drawRoundRect(bar.color, size = Size(size.width * fill.value, size.height), cornerRadius = CornerRadius(size.height / 2))
                })
                Text("${bar.home} / ${bar.total}", style = Digits, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 52.dp).clearAndSetSemantics {})
            }
        }
    }
}

/**
 * Swipes for Slide Race, over the whole game area: a swipe slides the tile next to the gap in
 * that direction ([onSwipe] gets -1, 0 or 1 across and down). Once the finger has moved past
 * the touch slop the touch is a swipe, wherever it started or ended: it is taken from the tiles
 * so it can never also count as a tap, and it slides one tile at most.
 */
internal fun Modifier.slideSwipes(enabled: Boolean, onSwipe: (Int, Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = Offset.Zero
            var swiping = false
            var done = false
            while (true) {
                // The Initial pass reaches this before the tiles do, so a swipe is taken first.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                moved += change.positionChange()
                if (!swiping && moved.getDistance() > viewConfiguration.touchSlop) swiping = true
                if (swiping) {
                    change.consume()
                    if (!done && moved.getDistance() > SWIPE_DP.dp.toPx()) {
                        done = true
                        if (abs(moved.x) > abs(moved.y)) onSwipe(moved.x.sign.toInt(), 0) else onSwipe(0, moved.y.sign.toInt())
                    }
                }
                if (!change.pressed) break
            }
        }
    }

/** How far a finger moves before a swipe slides a tile. */
private const val SWIPE_DP = 24

/** The host's level for the next puzzle; everyone else sees it. */
@Composable
fun SlideLevelCard(room: RoomState, busy: Boolean, onPick: (SlideLevel) -> Unit) {
    val level = room.slide?.level ?: SlideLevel.EASY
    OptionsCard(room, "Level", busy) { enabled ->
        OptionRow("Level", SlideLevel.entries.map { it to it.label.levelShort() }, level, onPick, enabled = enabled, perRow = 2, note = "A ${level.label.levelNote()} board.")
        if (room.setsOptions) LocalSlidePhoto.current?.let { (has, set) -> SlidePhotoPicker(has, enabled = !busy, onPhoto = set) }
    }
}

// ---- The race ---------------------------------------------------------------------------------

@Composable
fun SlideGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val slide = room.slide ?: return
    val now = rememberTicker()
    val phase = state.phase(state.serverNow())
    val finished = room.status == RoomStatus.FINISHED
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.SLIDE) { showRules = false }
    // The dock's Peek shows the finished board for a moment.
    var peeking by remember { mutableStateOf(false) }
    LaunchedEffect(peeking) {
        if (peeking) {
            delay(1600)
            peeking = false
        }
    }
    val start = slide.start
    val tiles = start?.let { Slide.replay(it, slide.size, state.slideMoves) }
    val mine = tiles?.let { Slide.progress(it, slide.size) } ?: 0
    val startAt = room.startAt ?: 0L
    val elapsed = when (phase) {
        RoomStatus.PLAYING -> state.serverNow() - startAt
        RoomStatus.FINISHED -> room.me.finishMs ?: room.others.firstNotNullOfOrNull { it.finishMs } ?: 0L
        else -> 0L
    }
    GameFrame(
        title = "Slide Race",
        subtitle = "Game ${room.round} · ${slide.level.label}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, p.primary, detail = { player, isMe ->
            if (isMe) "$mine% in place" else "${player.progress ?: 0}% in place"
        }),
        glow = GameKind.SLIDE.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        tools = if (finished) emptyList() else listOf(DockTool("Peek", GameIcons.Eye, enabled = tiles != null) { peeking = true }),
        onReact = if (room.opponent != null) ({ reacting = !reacting }) else null,
        trailing = { Text(style = Digits, text = formatDuration(elapsed), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        resultUnder = true,
        popup = if (!finished) null else popupContent {
            val name = room.winnerName ?: room.opponent?.name ?: "The other player"
            MatchPopup(
                room, GameKind.SLIDE, outcomeOf(room.winner), resultTitle(room.winner, name),
                when (room.endReason) {
                    "solved" -> if (room.winner == "me") "Solved first, in ${state.slideMoves.size} slides!" else "$name solved it first."
                    "forfeit" -> if (room.winner == "me") "Everyone else left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "The other player lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readySlide, onFinish = onLeave,
            )
        },
    ) {
        if (tiles == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Get ready", color = p.muted, fontSize = TextSize.Title)
                    if (phase == RoomStatus.COUNTDOWN) {
                        CountdownText(((startAt - state.serverNow() + 999) / 1000).coerceAtLeast(1), p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val playing = phase == RoomStatus.PLAYING
            Column(Modifier.weight(1f).fillMaxWidth().slideSwipes(playing, vm::swipeSlide), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!finished) TurnLine("Slides: ${state.slideMoves.size} · tap a tile or swipe anywhere", playing, GameKind.SLIDE.color)
                val photo = remember(state.slidePhoto) { slidePicture(state.slidePhoto) }
                // When someone finishes, the whole photo fades in for everyone.
                val reveal by androidx.compose.animation.core.animateFloatAsState(if (finished && photo != null) 1f else 0f, androidx.compose.animation.core.tween(1200), label = "photo")
                BoardArea(1f) { size ->
                    Box(size) {
                        if (peeking) SlideBoard(Slide.solved(slide.size), slide.size, false, {}, Modifier.fillMaxSize().alpha(0.85f), photo = photo)
                        else SlideBoard(tiles, slide.size, playing, vm::tapSlideTile, Modifier.fillMaxSize(), photo = photo)
                        if (photo != null && reveal > 0f) {
                            androidx.compose.foundation.Image(photo, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(reveal).clip(Shapes.L))
                        }
                    }
                }
                // Your bar from your own tiles; the others' from the progress the server sends.
                val total = slide.size * slide.size - 1
                SlideProgressBars(
                    listOf(SlideBar("You", p.primary, slideHome(tiles, slide.size), total)) +
                        room.others.mapIndexed { i, o -> SlideBar(o.name, SeatColors[i % SeatColors.size], slideHomeFromPercent(o.progress ?: 0, slide.size), total) },
                )
            }
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloSlideScreen(state: SoloSlideUiState, vm: SoloSlideViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.SLIDE) { showRules = false }
    val level = state.level
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    if (level == null) { BackToHome(onHome); return }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.SLIDE, "New game", vm::startWith) { changing = false }
    val now = rememberTicker()
    val done = state.finishedMs
    GameFrame(
        title = "Slide Race",
        subtitle = "On your own · ${level.label}",
        players = emptyList(),
        glow = GameKind.SLIDE.color,
        menu = soloMenu(GameKind.SLIDE, onHome, { showRules = true }, vm::again, { changing = true }),
        trailing = { Text(style = Digits, text = formatDuration(done ?: (now - state.startedAt)), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold) },
        resultUnder = true,
        popup = if (done == null) null else popupContent {
            SoloPopup(
                GameKind.SLIDE, "me",
                "Solved in ${formatDuration(done)} with ${state.moves} slides" + (state.best[level]?.let { " · best ${formatDuration(it)}" } ?: ""),
                onAgain = vm::again, onHome = onHome, finishLabel = "Change size", onFinish = { changing = true },
            )
        },
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().slideSwipes(done == null, vm::swipe), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (done == null) TurnLine("Slides: ${state.moves}", true, GameKind.SLIDE.color)
            BoardArea(1f) { size -> SlideBoard(state.tiles, level.size, done == null, vm::tap, size) }
            // On your own there is only your bar: your tiles are all the screen knows.
            SlideProgressBars(listOf(SlideBar("You", p.primary, slideHome(state.tiles, level.size), level.size * level.size - 1)))
        }
    }
}

/** A Slide Race photo (base64 JPEG) as a picture, or null. */
internal fun slidePicture(photo: String?): androidx.compose.ui.graphics.ImageBitmap? = photo?.let {
    runCatching {
        val bytes = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * A photo from the phone for Slide Race: cropped to its middle square and made small enough to
 * send to every phone (under [PHOTO_LIMIT] as base64, which also fits a Nearby message).
 */
internal fun photoForSlide(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val src = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
    val side = minOf(src.width, src.height)
    val square = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    var px = 240
    var quality = 70
    while (true) {
        val small = android.graphics.Bitmap.createScaledBitmap(square, px, px, true)
        val out = java.io.ByteArrayOutputStream()
        small.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        if (b64.length <= PHOTO_LIMIT || px <= 120) return b64.takeIf { it.length <= PHOTO_LIMIT }
        if (quality > 45) quality -= 10 else px -= 40
    }
    @Suppress("UNREACHABLE_CODE") null
}.getOrNull()

/** Base64 characters a photo may take. */
private const val PHOTO_LIMIT = 22_000

/** Photo or numbers for the race, picked by whoever opened the game. */
@Composable
fun SlidePhotoPicker(hasPhoto: Boolean, enabled: Boolean, onPhoto: (String?) -> Unit) {
    val p = AppTheme.palette
    val context = androidx.compose.ui.platform.LocalContext.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { photoForSlide(context, it) }?.let(onPhoto)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton(if (hasPhoto) "Another photo" else "Use a photo", { pick.launch("image/*") }, Modifier.weight(1f), enabled = enabled)
        if (hasPhoto) SecondaryButton("Numbers", { onPhoto(null) }, Modifier.weight(1f), enabled = enabled)
    }
    Text(if (hasPhoto) "Everyone slides your photo back together; small numbers stay in the corners." else "Slide a photo from your phone instead of numbers.", color = p.muted, fontSize = TextSize.Label)
}

/** The opener's photo switch for the level card: whether there is one, and how to set it. */
val LocalSlidePhoto = androidx.compose.runtime.compositionLocalOf<Pair<Boolean, (String?) -> Unit>?> { null }
