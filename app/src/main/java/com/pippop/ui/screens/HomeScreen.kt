package com.pippop.ui.screens

import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.art.tagline
import com.pippop.ui.art.color
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.KeyArt
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pippop.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Digits
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
import com.pippop.data.PlayerInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pippop.push.Push
import com.pippop.push.PushApi
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.SoloSetups
import com.pippop.ui.art.facts
import com.pippop.data.SavedPartner
import com.pippop.ui.art.AvatarImage
import com.pippop.ui.theme.OpponentColor
import com.pippop.data.PlayMode
import com.pippop.data.Invite
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.ui.theme.SeatColors
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.seatMark
import android.content.Intent
import com.pippop.data.Profile
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import com.pippop.ui.SoloUiState
import com.pippop.ui.theme.AppTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.pippop.game.Sudoku
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.pippop.ui.art.TrophyCup
import com.pippop.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import androidx.compose.ui.text.rememberTextMeasurer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    solo: SoloUiState,
    profile: Profile,
    onContinue: () -> Unit,
    onProfile: () -> Unit,
    /** Starts a game alone or against the computer, with the setup the sheet states (every game but Draw & Guess). */
    onSolo: (GameKind, Map<String, String>) -> Unit,
    /** The solo setup of a game was changed in its sheet: remember it. */
    onSoloSetup: (GameKind, Map<String, String>) -> Unit = { _, _ -> },
    /** Games alone that were started and not finished (Sudoku's is [SoloUiState.canContinue]). */
    soloUnfinished: Set<GameKind> = emptySet(),
    /** Back to an unfinished game alone: nothing new starts. */
    onContinueSolo: (GameKind) -> Unit = {},
    /** The Arrange games screen (also a long press on a game). */
    onArrange: () -> Unit,
    /** Our scoreboard: this month against your partner, every game, the trophies. */
    onScores: () -> Unit = {},
    partnerBusy: Boolean,
    onPlayWithPartner: (GameKind) -> Unit,
    onInvitePartner: (GameKind) -> Unit,
    /** The Online/Offline switch; it can't change while connected. */
    onPlayMode: (PlayMode) -> Unit,
    connected: Boolean,
    /** Opens the connect sheet for the chosen way (nearby, or online). */
    onConnect: () -> Unit,
    /** The room you're connected to, if any; you stay in it while picking games. */
    room: RoomState? = null,
    nearby: Boolean = false,
    onOpenGame: (GameKind) -> Unit = {},
    onLeaveRoom: () -> Unit = {},
    onNotificationsAnswered: () -> Unit = {},
    onTutorialSeen: (GameKind) -> Unit = {},
    /** A short message in the app's toast. */
    onMessage: (String) -> Unit = {},
    /** The time "Jump back in" counts from; the real time unless a test fixes it. */
    now: Long = remember { System.currentTimeMillis() },
    /** Where each tile is, and which one a game is being opened from (for the grow-out animation). */
    tiles: TileBounds = remember { TileBounds() },
) {
    val p = AppTheme.palette
    var sheetGame by rememberSaveable { mutableStateOf<GameKind?>(null) }
    // The first time a game is opened, its tutorial plays before its sheet.
    var tutorial by rememberSaveable { mutableStateOf<GameKind?>(null) }
    tutorial?.let { kind ->
        TutorialDialog(kind) {
            onTutorialSeen(kind)
            tutorial = null
            sheetGame = kind
        }
    }

    // Surprise us is flicking: taps wait, so its sheet can't open over another.
    var flicking by remember { mutableStateOf(false) }
    fun openGame(kind: GameKind) {
        if (flicking) return
        if (hasTutorial(kind) && kind !in profile.tutorialsSeen) tutorial = kind else sheetGame = kind
    }
    val shelf = profile.gameOrder.filter { it !in profile.putAway }
    val away = profile.gameOrder.filter { it in profile.putAway }
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    // Surprise us: a highlight flicks across the tiles for about half a second, stops on a game and opens its sheet.
    var surprise by remember { mutableIntStateOf(0) }
    var lit by remember { mutableStateOf<GameKind?>(null) }
    val reduceMotion = LocalReduceMotion.current
    val players = (room?.present?.size ?: 0) + 1
    LaunchedEffect(surprise) {
        if (surprise == 0) return@LaunchedEffect
        flicking = true
        val pick = surprisePick(shelf, players)
        if (pick == null) { flicking = false; onMessage("No game fits everyone here"); return@LaunchedEffect }
        surpriseFlick(shelf, pick, reduceMotion) { lit = it }
        flicking = false
        openGame(pick)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (profile.name.isNotBlank()) Text("Hi, ${profile.name}", color = p.muted, fontSize = TextSize.BodyL)
                    Text(stringResource(R.string.app_name), color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape).clickable(onClick = onProfile),
                ) {
                    val avatar = profile.avatar
                    if (avatar != null) AvatarImage(avatar, Modifier.fillMaxSize()) else Icon(Icons.Filled.Person, contentDescription = "Profile", tint = p.text)
                }
            }

            NotificationPrimer(profile, onNotificationsAnswered)

            if (room != null) {
                RoomBar(room, nearby, partnerBusy, Invite(profile.scriptUrl, room.code).toShareText(), onOpenGame, onLeaveRoom)
            } else {
                ConnectBar(profile.playMode, profile.partner, connected, onPlayMode, onConnect)
            }

            val sudokuPercent = solo.game?.takeIf { solo.canContinue }?.let { Sudoku.filledPercent(it.board.cells, it.board.givens) }
            val jump = jumpBackIn(sudokuPercent, profile.recentGames, now)
            if (jump.isNotEmpty()) {
                Eyebrow("Jump back in")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    jump.forEach { t ->
                        val continuing = t.game == GameKind.SUDOKU && sudokuPercent != null
                        RoomTile(
                            t.game, room, Modifier.weight(1f), tag = t.tag, jump = true,
                            onClick = { if (flicking) Unit else if (continuing) { tiles.pending = GameKind.SUDOKU; onContinue() } else openGame(t.game) },
                        )
                    }
                    repeat(JUMP_TILES - jump.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Games", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                    if (!profile.arrangedOnce) Text("Hold a game to move it", color = p.muted, fontSize = TextSize.Label)
                }
                RoundButton("Scores", { if (!flicking) onScores() }) { TrophyCup(Modifier.size(26.dp)) }
                RoundButton("Surprise us", { if (!flicking) surprise++ }) { GameIcon(GameKind.LUDO, Modifier.size(24.dp), tint = p.text) }
                RoundButton("Arrange games", { if (!flicking) onArrange() }) {
                    Canvas(Modifier.size(18.dp)) {
                        for (i in 0..1) for (j in 0..2) drawCircle(p.text, 1.8.dp.toPx(), Offset(size.width * (0.3f + 0.4f * i), size.height * (0.18f + 0.32f * j)))
                    }
                }
            }

            // Three tiles per row, in your order; each row stands on a thin shelf.
            shelf.chunked(3).forEach { row ->
                ShelfRow {
                    row.forEach { kind ->
                        RoomTile(kind, room, Modifier.weight(1f).reportTile(tiles, kind), highlight = lit == kind, onLongClick = { if (!flicking) onArrange() }, onClick = { openGame(kind) })
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (away.isNotEmpty()) {
                MoreGamesBar(away, moreOpen) { moreOpen = !moreOpen }
                if (moreOpen) away.chunked(3).forEach { row ->
                    ShelfRow {
                        row.forEach { kind ->
                            RoomTile(kind, room, Modifier.weight(1f).reportTile(tiles, kind), onLongClick = { if (!flicking) onArrange() }, onClick = { openGame(kind) })
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (solo.generating) {
            Box(
                Modifier.fillMaxSize().background(p.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PipPopLoader(Modifier.size(width = 96.dp, height = 60.dp))
                    Text("Creating your puzzle…", color = p.text)
                }
            }
        }
    }

    // Starting over a game that is not finished asks first.
    var confirmNew by rememberSaveable { mutableStateOf<GameKind?>(null) }
    confirmNew?.let { kind ->
        ToyDialog(
            title = "Start a new game?",
            body = "Your ${kind.label} game in progress will end.",
            onDismiss = { confirmNew = null },
        ) {
            SecondaryButton("Cancel", { confirmNew = null }, Modifier.weight(1f))
            PrimaryButton("New game", {
                confirmNew = null
                tiles.pending = kind
                onSolo(kind, profile.soloSetup(kind))
            }, Modifier.weight(1f))
        }
    }

    sheetGame?.let { kind ->
        ToySheet(onDismiss = { sheetGame = null }) {
            val close = { sheetGame = null }
            // "Change" swaps the sheet's content for the game's setup; Back swaps it back.
            var changing by rememberSaveable(kind) { mutableStateOf(false) }
            val setup = profile.soloSetup(kind)
            val unfinished = if (kind == GameKind.SUDOKU) solo.canContinue else kind in soloUnfinished
            val start = {
                close()
                if (unfinished) confirmNew = kind else { tiles.pending = kind; onSolo(kind, profile.soloSetup(kind)) }
            }
            if (changing) {
                SoloSetupContent(
                    kind, setup,
                    onPick = { key, value -> onSoloSetup(kind, setup + (key to value)) },
                    startLabel = soloLabel(kind, setup), onStart = start, onBack = { changing = false },
                )
            } else {
                GameSheet(
                    kind = kind,
                    solo = solo,
                    room = room,
                    onOpenGame = { close(); onOpenGame(kind) },
                    partner = profile.partner,
                    busy = partnerBusy,
                    setup = setup,
                    unfinished = unfinished,
                    onContinue = { close(); tiles.pending = kind; if (kind == GameKind.SUDOKU) onContinue() else onContinueSolo(kind) },
                    onSolo = start,
                    onSetup = { key, value -> onSoloSetup(kind, setup + (key to value)) },
                    onChange = { changing = true },
                    onPartner = { close(); onPlayWithPartner(kind) },
                    onInvite = { close(); onInvitePartner(kind) },
                )
            }
        }
    }
}

/** One row of tiles standing on a thin shelf. */
@Composable
internal fun ShelfRow(content: @Composable RowScope.() -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(), content = content)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Shapes.Full).background(p.ink.copy(alpha = 0.18f)))
    }
}

/** The art's shape: 280 to 170, as the key art is drawn. */
private const val ART = 280f / 170f

/** How many tiles the "Surprise us" highlight touches before it stops, and how long it stays on each: about half a second. */
private const val SURPRISE_STEPS = 4
private const val SURPRISE_STEP_MS = Motion.STAGGER_MS.toLong()
private const val SURPRISE_HOLD_MS = TileTransition.FADE_MS.toLong()

/** The "Surprise us" flick: a highlight ([setLit]) runs across [pool] for about half a second, stops on [pick], holds, then lets go. */
internal suspend fun surpriseFlick(pool: List<GameKind>, pick: GameKind, reduceMotion: Boolean, setLit: (GameKind?) -> Unit) {
    if (!reduceMotion) {
        repeat(SURPRISE_STEPS) {
            setLit(pool.random())
            delay(SURPRISE_STEP_MS)
        }
    }
    setLit(pick)
    if (!reduceMotion) delay(SURPRISE_HOLD_MS)
    setLit(null)
}

/** A small heading over a group ("JUMP BACK IN"). */
@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), color = AppTheme.palette.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
}

/**
 * A game on the shelf: its key art with its name under it. A [tag] sits on the picture's corner
 * ("27% done", "Yesterday"); [highlight] rings it while "Surprise us" flicks across. While connected,
 * [seats] says how many it takes ("2–4"), [dim] fades it with the reason ("2 players") and [face] is
 * someone in it, with a green [ring] round the tile. [description] is what a screen reader says
 * instead of the pieces. Holding it moves it (arrange), and the tile dips a little while pressed.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ArtTile(
    kind: GameKind, modifier: Modifier = Modifier, tag: String? = null, highlight: Boolean = false,
    seats: String? = null, dim: String? = null, face: PlayerInfo? = null, ring: Boolean = false, description: String? = null,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val p = AppTheme.palette
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val edge = if (highlight) p.primary else if (ring) p.good else p.ink
    // With a description, the pieces are not read one by one.
    val quiet = if (description != null) Modifier.clearAndSetSemantics { } else Modifier
    Box(modifier) {
        Surface(
            color = p.card, shape = Shapes.M, border = BorderStroke(if (highlight || ring) 3.dp else 2.dp, edge),
            modifier = Modifier.fillMaxWidth().pressedEdge(edge, Radius.M)
                // The dip is drawn only: the tile's place in the layout never moves.
                .graphicsLayer { translationY = if (pressed) 2.dp.toPx() else 0f }
                .clip(Shapes.M)
                .combinedClickable(
                    interactionSource = press, indication = LocalIndication.current, onClickLabel = "Open",
                    onLongClickLabel = if (onLongClick != null) "Arrange games" else null, onLongClick = onLongClick, onClick = onClick,
                )
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        ) {
            Column(Modifier.alpha(if (dim != null) 0.42f else 1f)) {
                Box(Modifier.fillMaxWidth().aspectRatio(ART)) {
                    KeyArt(kind, Modifier.fillMaxSize())
                    if (tag != null) {
                        Text(
                            tag, color = p.onPrimary, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(Shapes.Full).background(p.primary).padding(horizontal = 7.dp, vertical = 1.dp).then(quiet),
                        )
                    }
                    if (face != null) {
                        Box(Modifier.align(Alignment.BottomStart).padding(4.dp).then(quiet)) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(p.good))
                            Box(Modifier.padding(3.dp).size(26.dp).clip(CircleShape).background(p.accent).border(1.5.dp, Board.Ink, CircleShape)) { PlayerAvatar(face.name, SeatColors[0], size = 26, avatar = face.avatar) }
                        }
                    }
                    if (seats != null) {
                        Row(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).clip(Shapes.Full).background(Color.White).border(1.5.dp, Board.Ink, Shapes.Full)
                                .padding(start = 3.dp, end = 6.dp).then(quiet),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Person, null, tint = Board.Ink, modifier = Modifier.size(12.dp))
                            Text(seats, style = Digits, color = Board.Ink, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(edge))
                Text(
                    kind.label, color = p.text, fontSize = TextSize.Body, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                    lineHeight = LineHeight.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp).then(quiet),
                )
            }
        }
        if (dim != null) {
            Text(
                dim, color = p.text, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp).clip(Shapes.Full).background(p.card).border(1.5.dp, p.ink, Shapes.Full)
                    .padding(horizontal = 8.dp, vertical = 2.dp).then(quiet),
            )
        }
    }
}

/**
 * A tile on the home. Connected, it also says how many the game takes, fades when it can't take
 * everyone in the [room], and shows who is in it (the room's open game only) with a green outline. The "Jump back in"
 * row ([jump]) shows the faces but not the seats.
 */
@Composable
private fun RoomTile(
    kind: GameKind, room: RoomState?, modifier: Modifier = Modifier,
    tag: String? = null, highlight: Boolean = false, jump: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    if (room == null) return ArtTile(kind, modifier, tag, highlight, onLongClick = onLongClick, onClick = onClick)
    val people = room.present.size + 1
    val here = inGame(room.present, room.game, kind)
    val playing = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
    val fits = fitsRoom(kind, people)
    ArtTile(
        kind, modifier, tag, highlight,
        seats = seatsLabel(kind).takeIf { !jump }, dim = fadedLabel(kind).takeIf { !jump && !fits },
        face = here.firstOrNull(), ring = here.isNotEmpty(),
        description = tileDescription(kind, people, here.map { it.name }, playing) + (tag?.let { ", $it" } ?: ""),
        onLongClick = onLongClick, onClick = onClick,
    )
}

/** A round button with a drawn icon, for the Games row. 48 dp, named for screen readers. */
@Composable
private fun RoundButton(description: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val p = AppTheme.palette
    Box(
        Modifier.size(48.dp).pressedEdge(p.ink, 24.dp, 2.dp).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

/** The end of the shelf: "More games · N", the put-away games as small badges; tapping it opens them out. */
@Composable
private fun MoreGamesBar(games: List<GameKind>, open: Boolean, onToggle: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        color = p.accent, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline),
        modifier = Modifier.fillMaxWidth().clip(Shapes.L)
            .semantics { stateDescription = if (open) "Open" else "Closed" }
            .clickable(role = Role.Button, onClick = onToggle),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("More games · ${games.size}", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!open) games.take(4).forEach { kind ->
                Box(
                    Modifier.size(26.dp).clip(Shapes.S).background(Brush.verticalGradient(listOf(kind.color.lighter(0.18f), kind.color.darker(0.12f)))).border(2.dp, p.ink, Shapes.S),
                    contentAlignment = Alignment.Center,
                ) { GameIcon(kind, Modifier.size(16.dp)) }
            }
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = p.muted)
        }
    }
}

/**
 * Not connected: the Nearby/Online choice and one button, on one line. The button opens the connect
 * sheet, which says what each way needs. The choice is saved, and locked while connected.
 */
@Composable
private fun ConnectBar(mode: PlayMode, partner: SavedPartner?, connected: Boolean, onPlayMode: (PlayMode) -> Unit, onConnect: () -> Unit) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choices(listOf(PlayMode.OFFLINE to "Nearby", PlayMode.ONLINE to "Online"), mode, onPlayMode, Modifier.width(150.dp), enabled = !connected)
            PrimaryButton(
                if (partner != null) "Connect with ${partner.name}" else "Connect", onConnect, Modifier.weight(1f),
                leading = partner?.let { { PlayerAvatar(it.name, OpponentColor, size = 26, avatar = it.avatar) } },
            )
        }
    }
}

/**
 * While connected, one bar: everyone's faces, the names on one line, the room under them and "Leave".
 * Tapping the room line sends the invite (online). Under it, where everyone is: someone waiting in a
 * game with a Join button, or the hint to pick one. Picking any game opens it for everyone.
 */
@Composable
private fun RoomBar(room: RoomState, nearby: Boolean, busy: Boolean, shareText: String, onOpenGame: (GameKind) -> Unit, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val context = LocalContext.current
    val others = room.present
    fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
        context.startActivity(Intent.createChooser(send, "Send the code"))
    }
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, top = 6.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Faces(listOf(room.me) + others)
                Spacer(Modifier.width(10.dp))
                // Online, tapping the names and the room line sends the invite (one 48 dp target).
                Column(
                    Modifier.weight(1f).heightIn(min = 48.dp).then(if (nearby) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Send the invite") { share() }),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (others.isEmpty()) "Waiting for someone to join" else joinNames(others.map { it.name }, NAMES_MAX),
                        color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val roomLine = "${if (nearby) "Nearby" else "Online"} · room ${room.code}"
                    Text(
                        "● $roomLine",
                        color = if (others.any { it.online == false }) p.warning else p.good,
                        fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
                TextButton(onClick = onLeave, modifier = Modifier.heightIn(min = 48.dp)) { Text("Leave", color = p.bad, fontSize = TextSize.Body, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomStatusLines(room, nearby, busy, onOpenGame, onShare = ::share)
            }
        }
    }
}

/** Where everyone is: what the room bar says under the names. */
@Composable
private fun RoomStatusLines(room: RoomState, nearby: Boolean, busy: Boolean, onOpenGame: (GameKind) -> Unit, onShare: () -> Unit) {
    val p = AppTheme.palette
    val others = room.present
    val here = room.othersAtGame
    val playing = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
    when {
        others.isEmpty() && !nearby -> {
            Text("Send the code. The game list stays here; you pick one together once they're in.", color = p.muted, fontSize = TextSize.Body)
            SecondaryButton("Send the code", onShare)
        }
        others.isEmpty() -> Text("Read the code to the other phone: they type it and tap Join with this code.", color = p.muted, fontSize = TextSize.Body)
        // You paused a match: it waits for you a while.
        room.pausedByMe && playing -> {
            val left = ((room.me.pausedMs ?: 0) / 60_000).coerceAtLeast(0) + 1
            StatusLine("Your ${room.game.label} game is paused. It waits about $left more min.", Tone.Good)
            PrimaryButton("Resume ${room.game.label}", { onOpenGame(room.game) }, loading = busy)
        }
        here.isNotEmpty() && playing -> {
            StatusLine("${joinNames(here.map { it.name })} ${if (here.size == 1) "is" else "are"} playing ${room.game.label}.", Tone.Neutral)
        }
        here.isNotEmpty() -> {
            val names = joinNames(here.map { it.name })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "$names ${if (here.size == 1) "is" else "are"} waiting in ${room.game.label}", color = p.good, fontSize = TextSize.Body,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    "Join", { onOpenGame(room.game) }, Modifier.width(96.dp).semantics { contentDescription = "Join $names in ${room.game.label}" },
                    loading = busy,
                )
            }
        }
        else -> Text("Pick a game: whoever picks it sets it up.", color = p.muted, fontSize = TextSize.Body)
    }
}

/** Everyone's pictures, overlapping, each with an outline so they read apart. You come first. */
@Composable
private fun Faces(people: List<PlayerInfo>, size: Dp = 36.dp, step: Dp = 25.dp) {
    val p = AppTheme.palette
    Box(Modifier.width(size + step * (people.size - 1)).height(size)) {
        people.forEachIndexed { i, who ->
            Box(Modifier.offset(x = step * i).size(size).clip(CircleShape).border(2.dp, p.ink, CircleShape)) {
                PlayerAvatar(who.name, if (i == 0) p.primary else SeatColors[(i - 1) % SeatColors.size], size = size.value.toInt(), avatar = who.avatar, mark = seatMark(i))
            }
        }
    }
}

/** How much of the names line fits before it is cut with an ellipsis. */
private const val NAMES_MAX = 26

/** "Play solo · Medium": the button that starts a game alone, saying the main choice of its setup. */
private fun soloLabel(kind: GameKind, setup: Map<String, String>) =
    SoloSetups.spec(kind)?.let { "Play solo · ${it.buttonText(setup)}" } ?: "Play solo"

/**
 * What tapping a game offers: what it is at a glance (its name, what to expect), play with your
 * partner or the room, and play alone with the setup right here. Only one button is the main one.
 */
@Composable
internal fun GameSheet(
    kind: GameKind,
    solo: SoloUiState,
    room: RoomState?,
    onOpenGame: () -> Unit,
    partner: SavedPartner?,
    busy: Boolean,
    setup: Map<String, String>,
    unfinished: Boolean,
    onContinue: () -> Unit,
    onSolo: () -> Unit,
    onSetup: (String, String) -> Unit,
    onChange: () -> Unit,
    onPartner: () -> Unit,
    onInvite: () -> Unit,
) {
    val p = AppTheme.palette
    var showRules by rememberSaveable(kind) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeyArt(kind, Modifier.fillMaxWidth().height(128.dp).clip(Shapes.L).border(2.dp, p.ink, Shapes.L))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
                Text(kind.tagline, color = p.muted, fontSize = TextSize.Body)
            }
            Text(
                "How to play", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(Shapes.Full)
                    .clickable(role = Role.Button) { showRules = !showRules }
                    .padding(vertical = Space.S)
                    .background(p.card, Shapes.Full)
                    .border(2.dp, p.ink, Shapes.Full)
                    .padding(horizontal = Space.M, vertical = Space.S),
            )
        }
        if (showRules) RulesSheet(kind) { showRules = false }
        Facts(kind.facts)

        // The one main button: the room or the partner when there is one, otherwise continuing a Sudoku.
        var main = false
        val names = room?.present?.joinToString(" & ") { it.name }
        if (room != null) {
            // Connected: open it for everyone in the room; they see where you went.
            when {
                names.isNullOrEmpty() -> { PrimaryButton("Open ${kind.label}", onOpenGame, loading = busy); main = true }
                kind.maxPlayers < room.present.size + 1 -> Text("${kind.label} is for ${kind.maxPlayers}.", color = p.muted, fontSize = TextSize.Body)
                else -> { PrimaryButton("Play with $names", onOpenGame, loading = busy); main = true }
            }
        } else if (partner != null) {
            val canInvite = Push.available && partner.deviceId != null && PushApi.supports(partner.serverUrl)
            main = true
            if (canInvite) {
                PrimaryButton("Invite ${partner.name}", onInvite, loading = busy)
                SecondaryButton("Join ${partner.name}'s game", onPartner, enabled = !busy)
            } else {
                PrimaryButton("Play with ${partner.name}", onPartner, loading = busy)
            }
        }
        val game = solo.game
        if (unfinished && kind != GameKind.DRAW) {
            val label = if (kind == GameKind.SUDOKU && game != null) "Continue  ·  ${game.difficulty.label}  ·  ${formatDuration(game.elapsedMs)}" else "Continue"
            if (main) SecondaryButton(label, onContinue) else PrimaryButton(label, onContinue)
        }
        if (kind == GameKind.DRAW) {
            SecondaryButton("Play solo", {}, enabled = false)
            Text("Needs 2 or more players.", color = p.muted, fontSize = TextSize.Body)
        } else {
            SoloSetupRow(kind, setup, onSetup, onChange)
            SecondaryButton(if (unfinished) "New solo game · ${SoloSetups.spec(kind)?.buttonText(setup).orEmpty()}" else soloLabel(kind, setup), onSolo)
        }
        if (room == null && kind != GameKind.DRAW) {
            Text("To play together, connect first, at the top of the home.", color = p.muted, fontSize = TextSize.Body)
        }
    }
}

/**
 * Invitations and "your turn" only show with the notification permission (Android 13 and
 * later). Asked once, after the first invitation was sent or received, with the reason first;
 * either answer is saved, so the question never comes back on every launch.
 */
@Composable
private fun NotificationPrimer(profile: Profile, onAnswered: () -> Unit) {
    if (!profile.notificationsDue || profile.notificationsAsked || !Push.available || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAnswered() }
    if (granted) return
    val p = AppTheme.palette
    val who = profile.partner?.name ?: "your partner"
    AppCard("Get a buzz when it's your turn") {
        Text("Allow notifications to hear when $who invites you or plays their turn while the app is closed.", color = p.muted, fontSize = TextSize.Body)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            PrimaryButton("Allow", { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.weight(1f))
            SecondaryButton("Not now", onAnswered, Modifier.weight(1f))
        }
    }
}
