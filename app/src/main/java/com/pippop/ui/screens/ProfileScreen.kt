package com.pippop.ui.screens

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.pippop.BuildConfig
import com.pippop.ui.theme.TextSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.pippop.data.Avatar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import com.pippop.data.Difficulty
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

/** All setup lives here: name, battle server link, appearance, plus your statistics. */
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
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Profile", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppCard("Player") {
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

            AppCard("Online link") {
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
                AppCard("Saved partner") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(partner.name, OpponentColor, avatar = partner.avatar)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(partner.name, color = p.text, fontWeight = FontWeight.SemiBold)
                            Text("Code ${partner.code}", color = p.muted, fontSize = TextSize.Body)
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

            AppCard("Appearance") {
                OptionRow("Theme", ThemeMode.entries.map { it to it.label }, profile.theme, app::setTheme)
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

            AppCard("Sound and touch") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().toggleable(value = profile.sound, role = Role.Switch, onValueChange = app::setSound)) {
                    Column(Modifier.weight(1f)) {
                        Text("Sound", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold)
                        Text("Game sounds, on or off here: the phone's silent mode doesn't change them. The media volume sets how loud.", color = p.muted, fontSize = TextSize.Body)
                    }
                    ToySwitch(checked = profile.sound, onCheckedChange = null)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().toggleable(value = profile.vibration, role = Role.Switch, onValueChange = app::setVibration)) {
                    Column(Modifier.weight(1f)) {
                        Text("Vibration", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold)
                        Text("A buzz for your turn, your moves and the result.", color = p.muted, fontSize = TextSize.Body)
                    }
                    ToySwitch(checked = profile.vibration, onCheckedChange = null)
                }
            }

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

            AppCard("Statistics") {
                Difficulty.entries.forEach { level ->
                    val stats = profile.stats.solo[level]
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(level.label, color = p.text, modifier = Modifier.weight(1f))
                        Text(
                            if (stats == null || stats.wins == 0) "—"
                            else "${stats.wins} won · best ${formatDuration(stats.bestMs ?: 0L)}",
                            color = p.muted,
                            fontSize = TextSize.Body,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Battles", color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${profile.stats.battlesWon} won · ${profile.stats.battlesLost} lost",
                        color = p.muted,
                        fontSize = TextSize.Body,
                    )
                }
            }

            // Only in the preview app, so the owner always knows which of the two apps is open.
            if (BuildConfig.PREVIEW) {
                AppCard("Preview app") {
                    Text("This is the preview app. It keeps its own games and scores.", color = p.text)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = p.muted, fontSize = TextSize.Body)
                }
            }
        }
    }
}
