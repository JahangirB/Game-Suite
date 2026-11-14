package com.pippop.ui.screens

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.pippop.BuildConfig
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.pippop.data.Avatar
import com.pippop.data.Loudness
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pippop.data.MonthEnd
import com.pippop.data.ProfileStore
import com.pippop.data.SampleGames
import com.pippop.data.ScoreStore
import com.pippop.push.Push
import com.pippop.push.PushApi
import com.pippop.ui.theme.OpponentColor
import com.pippop.data.Profile
import com.pippop.data.ShipStyle
import androidx.compose.runtime.getValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.collectAsState
import com.pippop.data.SkinStore
import com.pippop.ui.art.AvatarImage
import com.pippop.data.ThemeMode
import com.pippop.ui.AppViewModel
import com.pippop.ui.BattleViewModel
import com.pippop.ui.UpdateUiState
import com.pippop.ui.theme.AppTheme

/** The pictures players can choose; tapping the chosen one again clears it. */
@Composable
internal fun AvatarPicker(selected: Avatar?, onPick: (Avatar?) -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar.entries.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { avatar ->
                    val chosen = avatar == selected
                    Surface(
                        onClick = { onPick(if (chosen) null else avatar) },
                        shape = CircleShape,
                        color = if (chosen) p.primary.copy(alpha = 0.2f) else p.accent,
                        border = if (chosen) BorderStroke(2.dp, p.primary) else null,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) { AvatarImage(avatar, Modifier.fillMaxSize().padding(3.dp)) }
                    }
                }
            }
        }
    }
}

/** All setup lives here: name, battle server link, appearance. */
@Composable
fun ProfileScreen(
    profile: Profile,
    busy: Boolean,
    app: AppViewModel,
    battle: BattleViewModel,
    updates: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    val p = AppTheme.palette

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Profile", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppCard("You") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayerAvatar(profile.name.ifBlank { "?" }, p.primary, size = 52, avatar = profile.avatar)
                    ToyField(
                        value = profile.name,
                        onValueChange = app::setName,
                        label = "Your name",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Avatar", color = p.muted, fontSize = TextSize.Body)
                AvatarPicker(selected = profile.avatar, onPick = app::setAvatar)
            }

            AppCard("Look") {
                OptionRow("Theme", ThemeMode.entries.map { it to it.label }, profile.theme, app::setTheme)
                // Step 24 (colour themes): the four swatches go here, under Theme.
                Text("Ship Battle style", color = p.muted, fontSize = TextSize.Body)
                // The two built in, then any skins downloaded in Ship Battle (its Skins button).
                val skinId by SkinStore.selected.collectAsState()
                val skins by SkinStore.installed.collectAsState()
                val using = skins.firstOrNull { it.id == skinId }
                val styles = ShipStyle.entries.map { "style:${it.name}" to it.label } + skins.map { "skin:${it.id}" to it.name }
                val chosen = if (using == null) "style:${profile.shipStyle.name}" else "skin:${using.id}"
                // Downloaded skins have longer names: two to a row so they keep their room.
                Choices(styles, chosen, perRow = 2, onPick = { key ->
                    if (key.startsWith("skin:")) SkinStore.use(key.removePrefix("skin:"))
                    else {
                        app.setShipStyle(ShipStyle.valueOf(key.removePrefix("style:")))
                        SkinStore.use(null)
                    }
                })
                Text("More skins: tap Skins at the bottom of Ship Battle.", color = p.muted, fontSize = TextSize.Label)
            }

            SoundAndTouchCard(profile, app)

            PlayingOnlineCard(profile, busy, app, battle)

            AppCard("App") {
                Text("Version ${updates.currentVersion}", color = p.text)
                val available = updates.available
                if (available != null) {
                    PrimaryButton(
                        "Update to ${available.versionName}",
                        onUpdate,
                        loading = updates.downloading,
                    )
                } else {
                    SecondaryButton(
                        if (updates.checking) "Checking…" else "Check for updates",
                        onCheckUpdates,
                        enabled = !updates.checking,
                    )
                }
            }

            // Only in the preview app, so the owner always knows which of the two apps is open.
            if (BuildConfig.PREVIEW) {
                PreviewAppCard()
            }
        }
    }
}

/** The Sound row (Off, Quiet, Normal, Loud), whether other players' moves sound, and the buzz. */
@Composable
internal fun SoundAndTouchCard(profile: Profile, app: AppViewModel) {
    val p = AppTheme.palette
    AppCard("Sound and touch") {
        OptionRow(
            "Sound", Loudness.entries.map { it to it.label }, profile.loudness, app::setLoudness,
            note = "The phone's silent mode doesn't change this. The media volume sets how loud.",
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().toggleable(value = profile.othersMoves, role = Role.Switch, onValueChange = app::setOthersMoves)) {
            Column(Modifier.weight(1f)) {
                Text("Their moves too", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold)
                Text("Hear the other players' moves, a little quieter.", color = p.muted, fontSize = TextSize.Body)
            }
            Spacer(Modifier.width(Space.M))
            ToySwitch(checked = profile.othersMoves, onCheckedChange = null)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().toggleable(value = profile.vibration, role = Role.Switch, onValueChange = app::setVibration)) {
            Column(Modifier.weight(1f)) {
                Text("Vibration", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold)
                Text("A buzz for your turn, your moves and the result.", color = p.muted, fontSize = TextSize.Body)
            }
            Spacer(Modifier.width(Space.M))
            ToySwitch(checked = profile.vibration, onCheckedChange = null)
        }
    }
}

/**
 * The online link's state on one line with "Change" (which opens the link's fields: the link, its
 * password, Paste message and Test link), then the saved partner. Closed when the link is set up.
 */
@Composable
internal fun PlayingOnlineCard(profile: Profile, busy: Boolean, app: AppViewModel, battle: BattleViewModel, startOpen: Boolean = !profile.scriptUrl.startsWith("https://")) {
    val p = AppTheme.palette
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableStateOf(startOpen) }
    val setUp = profile.scriptUrl.startsWith("https://")
    AppCard("Playing online") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Online link", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold)
                Text(
                    if (setUp) "\u2713 Set up" else "Not set up yet",
                    color = if (setUp) p.good else p.muted, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (open) "Done" else "Change \u203A", color = p.muted, fontWeight = FontWeight.Bold)
            }
        }
        if (open) {
            Text(
                "Online play goes through a link one of you set up (see the README). Joining with a " +
                    "message you were sent fills it in. Nearby play needs no link.",
                color = p.muted,
                fontSize = TextSize.Body,
            )
            ToyField(
                value = profile.scriptUrl,
                onValueChange = app::setScriptUrl,
                label = "Online link",
                placeholder = "https://…workers.dev or https://script.google.com/…",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            ToyField(
                value = profile.serverPassword,
                onValueChange = app::setServerPassword,
                label = "Password",
                help = "Only if the link has one. Ask whoever set it up.",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    "Paste message",
                    { battle.pasteInvite(clipboard.getText()?.text) },
                    Modifier.weight(1f),
                    enabled = !busy,
                )
                SecondaryButton("Test link", battle::testConnection, Modifier.weight(1f), enabled = !busy)
            }
        }
        profile.partner?.let { partner ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(partner.name, OpponentColor, avatar = partner.avatar)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(partner.name, color = p.text, fontWeight = FontWeight.SemiBold)
                    Text("Your saved partner \u00b7 code ${partner.code}", color = p.muted, fontSize = TextSize.Body)
                }
                TextButton(onClick = battle::forgetPartner) { Text("Forget", color = p.bad) }
            }
            Text(
                when {
                    !Push.available -> "Invitations are off in this build (see the README to switch them on)."
                    !PushApi.supports(partner.serverUrl) -> "This online link can't send invitations."
                    partner.deviceId == null -> "Play one online game together to switch on invitations."
                    else -> "You can invite ${partner.name} from the home screen."
                },
                color = p.muted,
                fontSize = TextSize.Body,
            )
        }
    }
}

/** The last card of the preview app's profile: which app this is, and the test tools. */
@Composable
internal fun PreviewAppCard() {
    val p = AppTheme.palette
    AppCard("Preview app") {
        Text("This is the preview app. It keeps its own games and scores.", color = p.text)
        Text("Version ${BuildConfig.VERSION_NAME}", color = p.muted, fontSize = TextSize.Body)
        PreviewTestTools()
    }
}

/**
 * The preview app's test tools: made-up games to look at the scoreboard with, and a way to take
 * them out again. Real games are never touched. Only called when [BuildConfig.PREVIEW] is true.
 */
@Composable
internal fun PreviewTestTools() {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    SecondaryButton("Fill with sample games", {
        val recent = SampleGames.fill(context)
        said = "Added games with ${SampleGames.PARTNER}, solo records" + if (recent) " and recent games." else "."
    }, Modifier.fillMaxWidth())
    SecondaryButton("Clear sample games", { asking = true }, Modifier.fillMaxWidth())
    // Last month's cup, as the home shows it once; this does not change which month is remembered.
    var cup by remember { mutableStateOf<MonthEnd?>(null) }
    SecondaryButton("Show last month's cup", {
        val end = MonthEnd.latest(ScoreStore.get(context).records.value, System.currentTimeMillis())
        if (end == null) said = "No finished month yet." else cup = end
    }, Modifier.fillMaxWidth())
    cup?.let { end ->
        val profile = ProfileStore.get(context).profile.value
        TrophyMomentDialog(end, java.time.YearMonth.now(), profile.avatar, profile.partner?.takeIf { it.name == end.partner }?.avatar) { cup = null }
    }
    said?.let { Text(it, color = AppTheme.palette.muted, fontSize = TextSize.Body) }
    if (asking) {
        ToyDialog(
            title = "Clear sample games?",
            body = "Only the sample games and records go. Your own games stay.",
            onDismiss = { asking = false },
        ) {
            SecondaryButton("Cancel", { asking = false }, Modifier.weight(1f))
            PrimaryButton("Clear", {
                asking = false
                SampleGames.clear(context)
                said = "Sample games cleared."
            }, Modifier.weight(1f))
        }
    }
}
