package com.pippop.ui.screens

import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.art.tagline
import com.pippop.ui.art.rules
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
import androidx.compose.material3.AlertDialog
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
    onNewGame: (Difficulty, com.pippop.game.SudokuShape) -> Unit,
    onProfile: () -> Unit,
    /** Against the computer or alone, for every game but Sudoku. */
    onSolo: (GameKind) -> Unit,
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
    var pickingLevel by rememberSaveable { mutableStateOf(false) }
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
        if (!reduceMotion) {
            repeat(SURPRISE_STEPS) {
                lit = shelf.random()
                delay(SURPRISE_STEP_MS)
            }
        }
        lit = pick
        if (!reduceMotion) delay(SURPRISE_HOLD_MS)
        lit = null
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
                ConnectedCard(room, nearby, partnerBusy, Invite(profile.scriptUrl, room.code).toShareText(), onOpenGame, onLeaveRoom)
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
                        ArtTile(
                            t.game, Modifier.weight(1f), tag = t.tag,
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
                        ArtTile(kind, Modifier.weight(1f).reportTile(tiles, kind), highlight = lit == kind, onLongClick = { if (!flicking) onArrange() }, onClick = { openGame(kind) })
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (away.isNotEmpty()) {
                MoreGamesBar(away, moreOpen) { moreOpen = !moreOpen }
                if (moreOpen) away.chunked(3).forEach { row ->
                    ShelfRow {
                        row.forEach { kind ->
                            ArtTile(kind, Modifier.weight(1f).reportTile(tiles, kind), onLongClick = { if (!flicking) onArrange() }, onClick = { openGame(kind) })
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

    sheetGame?.let { kind ->
        ToySheet(onDismiss = { sheetGame = null }) {
            val close = { sheetGame = null }
            GameSheet(
                kind = kind,
                solo = solo,
                room = room,
                onOpenGame = { close(); onOpenGame(kind) },
                partner = profile.partner,
                busy = partnerBusy,
                onContinue = { close(); tiles.pending = kind; onContinue() },
                onNewSudoku = { close(); pickingLevel = true },
                onSolo = { close(); tiles.pending = kind; onSolo(kind) },
                onPartner = { close(); onPlayWithPartner(kind) },
                onInvite = { close(); onInvitePartner(kind) },
            )
        }
    }

    if (pickingLevel) {
        LevelDialog(
            title = "New game",
            onDismiss = { pickingLevel = false },
            onPick = { level, shape ->
                pickingLevel = false
                tiles.pending = GameKind.SUDOKU
                onNewGame(level, shape)
            },
        )
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

/** A small heading over a group ("JUMP BACK IN"). */
@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), color = AppTheme.palette.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
}

/**
 * A game on the shelf: its key art with its name under it. A [tag] sits on the picture's corner
 * ("27% done", "Yesterday"); [highlight] rings it while "Surprise us" flicks across. Holding it
 * moves it (arrange), and the tile dips a little while pressed.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ArtTile(
    kind: GameKind, modifier: Modifier = Modifier, tag: String? = null, highlight: Boolean = false,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val p = AppTheme.palette
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val edge = if (highlight) p.primary else p.ink
    Surface(
        color = p.card, shape = Shapes.M, border = BorderStroke(if (highlight) 3.dp else 2.dp, edge),
        modifier = modifier.pressedEdge(edge, Radius.M)
            // The dip is drawn only: the tile's place in the layout never moves.
            .graphicsLayer { translationY = if (pressed) 2.dp.toPx() else 0f }
            .clip(Shapes.M)
            .combinedClickable(
                interactionSource = press, indication = LocalIndication.current, onClickLabel = "Open",
                onLongClickLabel = if (onLongClick != null) "Arrange games" else null, onLongClick = onLongClick, onClick = onClick,
            ),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(ART)) {
                KeyArt(kind, Modifier.fillMaxSize())
                if (tag != null) {
                    Text(
                        tag, color = p.onPrimary, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(Shapes.Full).background(p.primary).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(edge))
            Text(
                kind.label, color = p.text, fontSize = TextSize.Body, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                lineHeight = LineHeight.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }
    }
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
 * While connected: who you're with, the room, and where everyone is. Someone at a game shows
 * with a Join button; picking any game opens it for everyone. Leave is the only way out.
 */
@Composable
private fun ConnectedCard(room: RoomState, nearby: Boolean, busy: Boolean, shareText: String, onOpenGame: (GameKind) -> Unit, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val context = LocalContext.current
    val others = room.present
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(room.me.name, p.primary, avatar = room.me.avatar, mark = seatMark(0))
            others.forEachIndexed { i, o ->
                Spacer(Modifier.width(Space.XS))
                PlayerAvatar(o.name, SeatColors[i % SeatColors.size], avatar = o.avatar, mark = seatMark(i + 1))
            }
            Spacer(Modifier.width(Space.M))
            Column(Modifier.weight(1f)) {
                Text(
                    if (others.isEmpty()) "Waiting for someone to join" else "Connected with ${others.joinToString(" & ") { it.name }}",
                    color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                )
                Text(
                    "● ${if (nearby) "Nearby" else "Online"} · room ${room.code}",
                    color = if (others.any { it.online == false }) p.warning else p.good,
                    fontSize = TextSize.Body,
                )
            }
            TextButton(onClick = onLeave) { Text("Leave", color = p.bad, fontWeight = FontWeight.Bold) }
        }
        val here = room.othersAtGame
        val playing = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
        when {
            others.isEmpty() && !nearby -> {
                Text("Send the code. The game list stays here; you pick one together once they're in.", color = p.muted, fontSize = TextSize.Body)
                SecondaryButton("Send the code", {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, "Send the code"))
                })
            }
            others.isEmpty() -> Text("Read the code to the other phone: they type it and tap Join with this code.", color = p.muted, fontSize = TextSize.Body)
            // You paused a match: it waits for you a while.
            room.pausedByMe && playing -> {
                val left = ((room.me.pausedMs ?: 0) / 60_000).coerceAtLeast(0) + 1
                StatusLine("Your ${room.game.label} game is paused. It waits about $left more min.", Tone.Good)
                PrimaryButton("Resume ${room.game.label}", { onOpenGame(room.game) }, loading = busy)
            }
            here.isNotEmpty() && playing -> {
                StatusLine("${here.joinToString(" & ") { it.name }} ${if (here.size == 1) "is" else "are"} playing ${room.game.label}.", Tone.Neutral)
            }
            here.isNotEmpty() -> {
                val names = here.joinToString(" & ") { it.name }
                StatusLine("$names ${if (here.size == 1) "is" else "are"} waiting in ${room.game.label}.", Tone.Good)
                PrimaryButton("Join $names in ${room.game.label}", { onOpenGame(room.game) }, loading = busy)
            }
            else -> Text("Pick a game together: whoever picks it sets its options.", color = p.muted, fontSize = TextSize.Body)
        }
    }
}

/** What tapping a game offers: play with your partner, alone or against the computer, or online with a code. */
@Composable
private fun GameSheet(
    kind: GameKind,
    solo: SoloUiState,
    room: RoomState?,
    onOpenGame: () -> Unit,
    partner: SavedPartner?,
    busy: Boolean,
    onContinue: () -> Unit,
    onNewSudoku: () -> Unit,
    onSolo: () -> Unit,
    onPartner: () -> Unit,
    onInvite: () -> Unit,
) {
    val p = AppTheme.palette
    var showRules by rememberSaveable(kind) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeyArt(kind, Modifier.fillMaxWidth().height(150.dp).clip(Shapes.L).border(2.dp, p.ink, Shapes.L))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
                Text(kind.tagline, color = p.muted, fontSize = TextSize.Body)
            }
            TextButton(onClick = { showRules = !showRules }) { Text(if (showRules) "Hide rules" else "How to play", color = p.primaryText, fontWeight = FontWeight.Bold) }
        }
        if (showRules) Text(kind.rules, color = p.text, fontSize = TextSize.BodyL)

        val names = room?.present?.joinToString(" & ") { it.name }
        if (room != null) {
            // Connected: open it for everyone in the room; they see where you went.
            when {
                names.isNullOrEmpty() -> PrimaryButton("Open ${kind.label}", onOpenGame, loading = busy)
                kind.maxPlayers < room.present.size + 1 -> Text("${kind.label} is for ${kind.maxPlayers}.", color = p.muted, fontSize = TextSize.Body)
                else -> PrimaryButton("Play with $names", onOpenGame, loading = busy)
            }
        } else if (partner != null) {
            val canInvite = Push.available && partner.deviceId != null && PushApi.supports(partner.serverUrl)
            if (canInvite) {
                PrimaryButton("Invite ${partner.name}", onInvite, loading = busy)
                SecondaryButton("Join ${partner.name}'s game", onPartner, enabled = !busy)
            } else {
                PrimaryButton("Play with ${partner.name}", onPartner, loading = busy)
            }
        }
        val game = solo.game
        when {
            kind == GameKind.SUDOKU && solo.canContinue && game != null -> {
                PrimaryButton("Continue  ·  ${game.difficulty.label}  ·  ${formatDuration(game.elapsedMs)}", onContinue)
                SecondaryButton("New solo game", onNewSudoku)
            }
            kind == GameKind.SUDOKU -> SecondaryButton("Play solo", onNewSudoku)
            kind == GameKind.DRAW -> {
                SecondaryButton("Play solo", {}, enabled = false)
                Text("Needs 2 or more players.", color = p.muted, fontSize = TextSize.Body)
            }
            else -> SecondaryButton("Play solo", onSolo)
        }
        if (room == null && kind != GameKind.DRAW) {
            Text("To play together, connect first, at the top of the home.", color = p.muted, fontSize = TextSize.Body)
        }
    }
}

@Composable
private fun LevelDialog(title: String, onDismiss: () -> Unit, onPick: (Difficulty, com.pippop.game.SudokuShape) -> Unit) {
    val p = AppTheme.palette
    // One grid, or several joined into one long puzzle.
    var shape by remember { mutableStateOf(com.pippop.game.SudokuShape.CLASSIC) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ShapePicker(shape, enabled = true) { shape = it }
                if (shape.big) Text("${shape.grids.size} grids joined: saves as you go, ${com.pippop.game.SoloGame.BIG_MISTAKES} mistakes allowed.", color = p.muted, fontSize = TextSize.Label, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                Difficulty.entries.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Shapes.M)
                            .clickable { onPick(level, shape) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(level.label, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold)
                            Text(level.tagline(), color = p.muted, fontSize = TextSize.Body)
                        }
                        Text("${level.ordinal + 1}", color = p.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.primaryText) } },
    )
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
