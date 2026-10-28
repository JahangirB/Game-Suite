package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.LineHeight
import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Radius
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.art.tagline
import com.arcade.sudoku.ui.art.rules
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.art.GameIcon
import com.arcade.sudoku.ui.art.KeyArt
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.arcade.sudoku.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.arcade.sudoku.push.Push
import com.arcade.sudoku.push.PushApi
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.SavedPartner
import com.arcade.sudoku.ui.art.AvatarImage
import com.arcade.sudoku.ui.theme.OpponentColor
import com.arcade.sudoku.data.PlayMode
import com.arcade.sudoku.data.Invite
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.ui.theme.SeatColors
import com.arcade.sudoku.ui.theme.Space
import com.arcade.sudoku.ui.theme.seatMark
import android.content.Intent
import com.arcade.sudoku.data.Profile
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import com.arcade.sudoku.ui.SoloUiState
import com.arcade.sudoku.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    solo: SoloUiState,
    profile: Profile,
    onContinue: () -> Unit,
    onNewGame: (Difficulty) -> Unit,
    onProfile: () -> Unit,
    /** Against the computer or alone, for every game but Sudoku. */
    onSolo: (GameKind) -> Unit,
    /** The Arrange games screen (also a long press on a game). */
    onArrange: () -> Unit,
    partnerBusy: Boolean,
    onPlayWithPartner: (GameKind) -> Unit,
    onInvitePartner: (GameKind) -> Unit,
    /** The Online/Offline switch; it can't change while connected. */
    onPlayMode: (PlayMode) -> Unit,
    connected: Boolean,
    onConnectNearby: () -> Unit,
    onStartRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    /** The room you're connected to, if any; you stay in it while picking games. */
    room: RoomState? = null,
    nearby: Boolean = false,
    onOpenGame: (GameKind) -> Unit = {},
    onLeaveRoom: () -> Unit = {},
    onNotificationsAnswered: () -> Unit = {},
    onTutorialSeen: (GameKind) -> Unit = {},
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

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                ConnectionCard(profile, connected, onPlayMode, onConnectNearby, onStartRoom, onJoinRoom, onProfile)
                profile.partner?.let { PartnerCard(it) }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Games", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                    Text("Hold a game to move it", color = p.muted, fontSize = TextSize.Label)
                }
                TextButton(onClick = onArrange) { Text("Arrange", color = p.primaryText, fontWeight = FontWeight.Bold) }
            }

            // Two tiles per row, in your order.
            profile.gameOrder.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { kind ->
                        GameTile(
                            kind, Modifier.weight(1f), badge = if (kind == GameKind.SUDOKU && solo.canContinue) "Continue" else null,
                            onLongClick = onArrange,
                        ) { if (hasTutorial(kind) && kind !in profile.tutorialsSeen) tutorial = kind else sheetGame = kind }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (solo.generating) {
            Box(
                Modifier.fillMaxSize().background(p.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = p.primary)
                    Text("Creating your puzzle…", color = p.text)
                }
            }
        }
    }

    sheetGame?.let { kind ->
        ModalBottomSheet(onDismissRequest = { sheetGame = null }, containerColor = p.card) {
            val close = { sheetGame = null }
            GameSheet(
                kind = kind,
                solo = solo,
                room = room,
                onOpenGame = { close(); onOpenGame(kind) },
                partner = profile.partner,
                busy = partnerBusy,
                onContinue = { close(); onContinue() },
                onNewSudoku = { close(); pickingLevel = true },
                onSolo = { close(); onSolo(kind) },
                onPartner = { close(); onPlayWithPartner(kind) },
                onInvite = { close(); onInvitePartner(kind) },
            )
        }
    }

    if (pickingLevel) {
        LevelDialog(
            title = "New game",
            onDismiss = { pickingLevel = false },
            onPick = {
                pickingLevel = false
                onNewGame(it)
            },
        )
    }
}

/** A game on the shelf: its coloured icon, name and one line about it. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GameTile(kind: GameKind, modifier: Modifier, badge: String?, onLongClick: () -> Unit, onClick: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        color = p.card,
        shape = Shapes.L,
        border = BorderStroke(2.dp, p.ink),
        modifier = modifier.pressedEdge(p.ink, Radius.L).clip(Shapes.L)
            .combinedClickable(onClickLabel = "Open", onLongClickLabel = "Arrange games", onLongClick = onLongClick, onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp).clip(Shapes.M).background(kind.color).border(2.dp, p.ink, Shapes.M),
                ) { GameIcon(kind, Modifier.size(28.dp)) }
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    Text(
                        badge, color = p.onPrimary, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(Shapes.Full).background(p.primary).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(kind.label, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.Body)
            Text(kind.tagline, color = p.muted, fontSize = TextSize.Body, lineHeight = LineHeight.Label, minLines = 2)
        }
    }
}

/**
 * The Online/Offline switch and how to connect in that mode: nearby over Bluetooth and Wi‑Fi,
 * or online by starting a room or joining one with its code. The switch is saved, and locked
 * while connected.
 */
@Composable
private fun ConnectionCard(
    profile: Profile,
    connected: Boolean,
    onPlayMode: (PlayMode) -> Unit,
    onConnectNearby: () -> Unit,
    onStartRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onProfile: () -> Unit,
) {
    val p = AppTheme.palette
    AppCard {
        ModeSwitch(profile.playMode, enabled = !connected, onPick = onPlayMode)
        Text("Not connected", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        when {
            profile.playMode == PlayMode.OFFLINE -> {
                Text("Phones in the same room connect over Bluetooth and Wi‑Fi. No internet needed.", color = p.muted, fontSize = TextSize.Body)
                PrimaryButton("Connect nearby", onConnectNearby)
            }
            profile.scriptUrl.startsWith("https://") -> {
                Text("Start a room and send the code, or join theirs.", color = p.muted, fontSize = TextSize.Body)
                PrimaryButton("Start a room", onStartRoom)
                SecondaryButton("Join with code", onJoinRoom)
            }
            else -> {
                Text("To play online, join with the message you were sent: it has the link. Or add the link in your profile.", color = p.muted, fontSize = TextSize.Body)
                PrimaryButton("Join with code", onJoinRoom)
                SecondaryButton("Open profile", onProfile)
            }
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

/** Two segments, Offline and Online; the chosen one is filled. */
@Composable
private fun ModeSwitch(mode: PlayMode, enabled: Boolean, onPick: (PlayMode) -> Unit) {
    val p = AppTheme.palette
    Row(
        Modifier.fillMaxWidth().clip(Shapes.Full).border(2.dp, p.ink, Shapes.Full).selectableGroup(),
    ) {
        listOf(PlayMode.OFFLINE to "Offline · nearby", PlayMode.ONLINE to "Online").forEach { (value, label) ->
            val on = value == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (on) p.primary else Color.Transparent)
                    .selectable(selected = on, enabled = enabled, role = Role.Tab) { onPick(value) },
            ) {
                Text(
                    label,
                    color = if (on) p.onPrimary else p.text,
                    fontSize = TextSize.Body,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(if (enabled || on) 1f else 0.5f),
                )
            }
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
private fun LevelDialog(title: String, onDismiss: () -> Unit, onPick: (Difficulty) -> Unit) {
    val p = AppTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, color = p.text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Difficulty.entries.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Shapes.M)
                            .clickable { onPick(level) }
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

/** Your saved partner, at the top of the shelf. */
@Composable
private fun PartnerCard(partner: SavedPartner) {
    val p = AppTheme.palette
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(partner.name, OpponentColor, size = 46, avatar = partner.avatar)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("You & ${partner.name}", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text("Tap a game to play together", color = p.muted, fontSize = TextSize.Body)
            }
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
