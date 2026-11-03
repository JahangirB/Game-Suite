package com.pippop.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.ProfileStore
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.textFill
import kotlinx.coroutines.delay

// ---- The dock ------------------------------------------------------------------------------

/**
 * One of a game's own buttons in its dock, between Menu and React. The dock is the same all
 * game: an [anytime] button works on your partner's turn too, the others grey out then, in place.
 */
class DockTool(
    val label: String,
    val icon: ImageVector,
    val anytime: Boolean = true,
    val enabled: Boolean = true,
    val badge: Int = 0,
    val onClick: () -> Unit,
)

/** Menu, the game's own [tools] and React (when there is someone to react to), in that order. */
fun menuDock(onMenu: () -> Unit, tools: List<DockTool>, myTurn: Boolean, onReact: (() -> Unit)?): List<DockAction> =
    listOf(DockAction("Menu", Icons.Filled.Menu, onClick = onMenu)) +
        tools.map { DockAction(it.label, it.icon, enabled = it.enabled && (it.anytime || myTurn), badge = it.badge, onClick = it.onClick) } +
        listOfNotNull(onReact?.let { DockAction("React", Icons.Filled.Face, onClick = it) })

// ---- The menu ------------------------------------------------------------------------------

/**
 * What a game's Menu offers: Resume, New game, Change mode (showing [mode]), Rules, Sound,
 * Switch game and Leave. A null action leaves its row out. [onOpen] hears the menu open and
 * close: a game on your own pauses meanwhile.
 */
class GameMenu(
    val game: GameKind,
    val onRules: () -> Unit,
    val onLeave: () -> Unit,
    val mode: String? = null,
    val onNewGame: (() -> Unit)? = null,
    val onChangeMode: (() -> Unit)? = null,
    val onSwitchGame: (() -> Unit)? = null,
    val leaveLabel: String = "Leave game",
    val onOpen: (Boolean) -> Unit = {},
)

/** The menu as a sheet over the game: everything in one list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMenuSheet(menu: GameMenu, onDismiss: () -> Unit) {
    val p = AppTheme.palette
    val store = ProfileStore.get(LocalContext.current)
    val profile by store.profile.collectAsState()
    LaunchedEffect(Unit) { menu.onOpen(true) }
    val close = {
        menu.onOpen(false)
        onDismiss()
    }
    // Each row closes the menu first, then does its thing.
    fun then(action: () -> Unit): () -> Unit = {
        close()
        action()
    }
    ModalBottomSheet(onDismissRequest = close, containerColor = p.card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.L).padding(bottom = Space.L).verticalScroll(rememberScrollState())) {
            Text(menu.game.label, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = Space.S))
            MenuRow("Resume", onClick = close)
            menu.onNewGame?.let { MenuRow("New game", onClick = then(it)) }
            menu.onChangeMode?.let { MenuRow("Change mode", detail = menu.mode, onClick = then(it)) }
            MenuRow("Rules", onClick = then(menu.onRules))
            MenuRow("Sound", onClick = { store.setSound(!profile.sound) }) {
                Switch(
                    checked = profile.sound, onCheckedChange = store::setSound,
                    colors = SwitchDefaults.colors(checkedTrackColor = p.primary, checkedThumbColor = p.onPrimary),
                )
            }
            menu.onSwitchGame?.let { MenuRow("Switch game", onClick = then(it)) }
            MenuRow(menu.leaveLabel, color = p.bad, last = true, onClick = then(menu.onLeave))
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    detail: String? = null,
    color: Color = AppTheme.palette.text,
    last: Boolean = false,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = AppTheme.palette
    Column {
        Row(
            Modifier.fillMaxWidth().clip(Shapes.M).clickable(onClick = onClick).padding(horizontal = Space.S, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = color, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, color = p.muted, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = Space.S))
            if (trailing != null) trailing() else if (!last) Text("›", color = p.muted, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
    }
}

// ---- Playing together: what every game against your partner shares ------------------------

/**
 * The room's controls every game screen and its result share, provided by the app around a
 * game against your partner: the current [mode], the options for the next round ([setup], null
 * when the game has none), rematch, asking for a new game, and switching to another game.
 * [setupOpen] holds the Change sheet open (from the result, or after a "Change mode" restart).
 */
class RoomControls(
    val room: RoomState,
    val mode: String?,
    val setup: (@Composable ColumnScope.() -> Unit)?,
    val rematch: () -> Unit,
    val newGame: () -> Unit,
    val switchTo: (GameKind) -> Unit,
    val games: List<GameKind>,
    val setupOpen: MutableState<Boolean>,
    val busy: Boolean,
)

val LocalRoomControls = compositionLocalOf<RoomControls?> { null }

/** Whether a match is being played (counting down or on). */
val RoomState.inMatch get() = status == RoomStatus.COUNTDOWN || status == RoomStatus.PLAYING

/**
 * The menu of a game against your partner: New game and Change mode ask your partner during a
 * match (and restart it), Switch game invites them to another one.
 */
@Composable
fun battleMenu(game: GameKind, onRules: () -> Unit, onLeave: () -> Unit): GameMenu {
    val controls = LocalRoomControls.current
    var switching by remember { mutableStateOf(false) }
    if (switching && controls != null) SwitchGameSheet(controls, onDismiss = { switching = false })
    if (controls == null) return GameMenu(game, onRules, onLeave)
    val room = controls.room
    val partner = room.present.isNotEmpty()
    return GameMenu(
        game = game,
        onRules = onRules,
        onLeave = onLeave,
        mode = controls.mode,
        onNewGame = when {
            !partner -> null
            room.inMatch -> controls.newGame
            room.status == RoomStatus.FINISHED -> controls.rematch
            else -> null
        },
        onChangeMode = if (controls.setup == null || !partner) null else ({
            controls.setupOpen.value = true
            if (room.inMatch) controls.newGame()
        }),
        onSwitchGame = if (room.inSession && partner) ({ switching = true }) else null,
        leaveLabel = if (room.inSession) "Leave game" else "Leave",
    )
}

/**
 * Switch game: the shelf as a sheet. Picking one opens it and invites your partner; during a
 * match it says first that this one ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchGameSheet(controls: RoomControls, onDismiss: () -> Unit) {
    val p = AppTheme.palette
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.L).padding(bottom = Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            Text("Switch game", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            val partner = controls.room.present.singleOrNull()?.name ?: "Everyone else"
            Text(
                (if (controls.room.inMatch) "This game ends and counts as a loss. " else "") + "$partner gets an invite to join you.",
                color = p.muted, fontSize = TextSize.Body,
            )
            GameShelf(controls.games) {
                onDismiss()
                controls.switchTo(it)
            }
        }
    }
}

/** Games as small tiles, four to a row. */
@Composable
fun GameShelf(games: List<GameKind>, perRow: Int = 4, onPick: (GameKind) -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
        games.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S), modifier = Modifier.fillMaxWidth()) {
                row.forEach { g ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clip(Shapes.M).clickable { onPick(g) }.padding(vertical = Space.XS),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp).pressedEdge(p.ink, 14.dp).clip(Shapes.M).background(g.color).border(2.dp, p.ink, Shapes.M),
                        ) { GameIcon(g, Modifier.size(28.dp)) }
                        Spacer(Modifier.height(Space.XS))
                        Text(g.label, color = p.text, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The Change sheet: the options for the next round, as in the lobby, and a button that asks
 * everyone to play with them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(game: GameKind, controls: RoomControls, onDismiss: () -> Unit) {
    val p = AppTheme.palette
    val setup = controls.setup ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.L).padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            Text("${game.label} setup", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            setup()
            val room = controls.room
            val partner = room.present.singleOrNull()?.name ?: "everyone"
            if (room.status == RoomStatus.FINISHED && !room.me.ready && room.present.isNotEmpty()) {
                PrimaryButton("Ask $partner to play this", {
                    onDismiss()
                    controls.rematch()
                }, loading = controls.busy)
            } else {
                SecondaryButton("Done", onDismiss)
            }
        }
    }
}

// ---- Rematch: ready lights and a countdown --------------------------------------------------

/**
 * Rematch with everyone's face on it and a light each: yours turns green when you tap, theirs
 * when they do. If they're all ready when you tap, it counts 3, 2, 1 first (tap again to stop)
 * and then starts. Once you're ready, tapping takes it back.
 */
@Composable
fun RematchButton(room: RoomState, onRematch: () -> Unit, enabled: Boolean = true) {
    val p = AppTheme.palette
    val present = room.present
    val othersReady = present.isNotEmpty() && present.all { it.ready }
    var counting by remember(room.round) { mutableStateOf(false) }
    val count = remember(room.round) { Animatable(3f) }
    LaunchedEffect(counting) {
        if (!counting) {
            count.snapTo(3f)
            return@LaunchedEffect
        }
        count.animateTo(0f, tween(COUNTDOWN_MS, easing = LinearEasing))
        delay(80)
        counting = false
        onRematch()
    }
    // Their light going off mid-countdown stops it.
    LaunchedEffect(othersReady) { if (!othersReady) counting = false }
    val label = when {
        counting -> "Starting in ${count.value.toInt() + 1}…  tap to stop"
        room.me.ready && othersReady -> "Starting…"
        room.me.ready -> "Waiting for " + present.filter { !it.ready }.let { if (it.size == 1) it[0].name else "${it.size} more" } + " · Cancel"
        othersReady -> "Rematch: " + (if (present.size == 1) "${present[0].name} is ready" else "they're ready")
        else -> "Rematch"
    }
    Surface(
        onClick = {
            when {
                counting -> counting = false
                !room.me.ready && othersReady -> counting = true
                else -> onRematch()
            }
        },
        enabled = enabled,
        color = if (counting || othersReady) p.good else p.primary,
        shape = Shapes.L,
        border = BorderStroke(2.dp, p.ink),
        modifier = Modifier.fillMaxWidth().pressedEdge(p.ink, 16.dp),
    ) {
        Row(Modifier.padding(horizontal = Space.M, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ReadyFaces(room)
            Spacer(Modifier.width(Space.M))
            Text(label, color = p.onPrimary, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

/** Your face and everyone else's, overlapping, each with a light: green when ready. */
@Composable
private fun ReadyFaces(room: RoomState) {
    val faces: List<Pair<PlayerInfo, Boolean>> = listOf(room.me to true) + room.present.map { it to false }
    Box(Modifier.width((26 + 18 * (faces.size - 1)).dp).height(30.dp)) {
        faces.forEachIndexed { i, (player, mine) ->
            Box(Modifier.padding(start = (18 * i).dp)) {
                PlayerAvatar(if (mine) "You" else player.name, if (mine) AppTheme.palette.primary else AppTheme.palette.accent, size = 26, avatar = player.avatar)
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(top = 18.dp, start = 18.dp).size(10.dp).clip(CircleShape)
                        .background(if (player.ready) AppTheme.palette.good else AppTheme.palette.hairline).border(1.5.dp, AppTheme.palette.ink, CircleShape),
                )
            }
        }
    }
}

/** How long the rematch countdown takes. */
private const val COUNTDOWN_MS = 2400

/** A coloured pill with [game]'s name, for sheets and results. */
@Composable
fun GamePill(game: GameKind) {
    Text(
        game.label, color = readableOn(textFill(game.color)), fontWeight = FontWeight.Bold, fontSize = TextSize.Label,
        modifier = Modifier.clip(Shapes.Full).background(textFill(game.color)).padding(horizontal = 12.dp, vertical = 3.dp),
    )
}

/** Keeps the bottom of a sheet clear of the navigation bar. */
fun Modifier.sheetBottom() = this.navigationBarsPadding()

// ---- The options for the next round, in the lobby and the Change sheet ----------------------

/** Whether [room]'s game has options to choose between rounds. */
fun hasOptions(room: RoomState) = when (room.game) {
    GameKind.DOMINOES, GameKind.NUMBERS, GameKind.WORDS, GameKind.SLIDE, GameKind.TTT, GameKind.SUDOKU -> true
    GameKind.GOLF -> room.golfLevel != null
    GameKind.BLOCKS, GameKind.MEMORY -> true
    else -> false
}

/** The options for [room]'s next round, as the lobby shows them. */
@Composable
fun RoomOptions(room: RoomState, busy: Boolean, vm: com.pippop.ui.BattleViewModel) {
    when (room.game) {
        GameKind.DOMINOES -> DominoOptionsCard(room, busy, vm::setDominoOptions)
        GameKind.NUMBERS -> NumbersLevelCard(room, busy, vm::setNumberLevel)
        GameKind.WORDS -> WordLevelCard(room, busy, vm::setWordLevel)
        GameKind.SLIDE -> SlideLevelCard(room, busy, vm::setSlideLevel)
        GameKind.TTT -> TttModeCard(room, busy, vm::setTttClassic)
        GameKind.GOLF -> if (room.golfLevel != null) GolfLevelCard(room, busy, vm::setGolfLevel)
        GameKind.BLOCKS -> AppCard("Help") { BlocksHintsSwitch() }
        GameKind.MEMORY -> MemoryDeckCard(room, busy, vm::setMemoryCards)
        GameKind.SUDOKU -> AppCard("Level") {
            Text("Tap a level: the next round starts when you both pick the same one.", color = AppTheme.palette.muted, fontSize = TextSize.Body)
            LevelPicker(room, busy, onPick = vm::chooseLevel)
        }
        else -> Unit
    }
}

/** What [room] is playing, in a few words ("Hard", "Teams · to 100"), or null. */
fun modeLabel(room: RoomState): String? = when (room.game) {
    GameKind.SUDOKU -> room.difficulty?.label
    GameKind.WORDS -> room.words?.level?.label
    GameKind.NUMBERS -> room.numbers?.level?.label
    GameKind.SLIDE -> room.slide?.level?.label
    GameKind.TTT -> if (room.tttClassic) "Classic 3 × 3" else "Ultimate"
    GameKind.GOLF -> room.golfLevel?.label
    GameKind.MEMORY -> "${room.memoryCards} cards"
    GameKind.DOMINOES -> room.options?.let { (if (it.teams) "Teams" else "Each for their own") + " · " + (if (it.draw) "Draw" else "Block") + " · to ${it.target}" }
    else -> null
}

// ---- Leaving -------------------------------------------------------------------------------

/**
 * Back or Leave during a match: Keep playing, Pause and come back (the match waits up to ten
 * minutes and your partner sees you're away), or Leave game (your partner wins). Nothing ends
 * by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveSheet(room: RoomState, onStay: () -> Unit, onPause: () -> Unit, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val partner = room.present.singleOrNull()?.name ?: "Everyone else"
    val canPause = room.inSession && room.inMatch && room.present.isNotEmpty()
    ModalBottomSheet(onDismissRequest = onStay, containerColor = p.card, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.L).padding(bottom = Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            Text(if (room.inMatch) "Leave the game?" else "Leave?", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            PrimaryButton("Keep playing", onStay)
            if (canPause) {
                SecondaryButton("Pause and come back", onPause)
                Text("The game waits up to 10 minutes, even if the app closes. $partner sees you're away.", color = p.muted, fontSize = TextSize.Body)
            }
            SecondaryButton(if (room.inSession) "Leave game" else "Leave", onLeave)
            Text(
                when {
                    room.inMatch && room.present.size == 1 -> "$partner wins this one. " + if (room.inSession) "You stay connected." else ""
                    room.inMatch -> "It counts as a loss." + if (room.inSession) " You stay connected." else ""
                    room.isHost -> "Everyone else is disconnected."
                    else -> "You can join again with the same code."
                },
                color = p.muted, fontSize = TextSize.Body,
            )
        }
    }
}

/** A sheet for a dock button that shows something (the pairs found, the calls, the dice). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = AppTheme.palette
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.L).padding(bottom = Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            Text(title, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** A row of letter tiles under a [title], in [fill]; "none yet" when there are none. */
@Composable
fun LetterTiles(title: String, letters: List<Char>, fill: Color, ink: Color = AppTheme.palette.ink) {
    val p = AppTheme.palette
    Text(title, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
    if (letters.isEmpty()) {
        Text("none yet", color = p.muted, fontSize = TextSize.Body)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        letters.chunked(9).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(32.dp).clip(Shapes.S).background(fill).border(1.5.dp, ink, Shapes.S),
                    ) { Text(c.uppercase(), color = readableOn(fill), fontSize = TextSize.Body, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
