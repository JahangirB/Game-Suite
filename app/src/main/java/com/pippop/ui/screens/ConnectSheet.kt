package com.pippop.ui.screens

import com.pippop.ui.Problems
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.pippop.data.Profile
import com.pippop.data.SavedPartner
import com.pippop.ui.theme.OpponentColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/** What the home's connect sheet is for: a nearby room, starting an online room, or joining one with its code. */
enum class ConnectKind {
    NEARBY, JOIN_ONLINE,
    /** The home's Connect button in Online mode: start a room or join one, whichever the link and the person need. */
    ONLINE,
}

/** What Android asks for before two phones may find each other directly. */
internal fun nearbyPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()

/**
 * Connecting, from the home: a nearby room (start one or join with the code on the other
 * phone; the code is kept for next time), or an online room joined with its code. Once
 * connected the sheet closes and the home shows who you're with.
 */
@Composable
fun ConnectSheet(kind: ConnectKind, state: BattleUiState, vm: BattleViewModel, profile: Profile = Profile(), onOpenProfile: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Space.XL, end = Space.XL, bottom = Space.XXXL),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        profile.partner?.let { PartnerLine(it) }
        when (kind) {
            ConnectKind.NEARBY -> NearbyConnect(state, vm)
            ConnectKind.ONLINE -> OnlineConnect(state, vm, profile, onOpenProfile)
            ConnectKind.JOIN_ONLINE -> OnlineJoin(state, vm)
        }
    }
}

/** Your saved partner: who the Connect button is for. */
@Composable
private fun PartnerLine(partner: SavedPartner) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(partner.name, OpponentColor, size = 46, avatar = partner.avatar)
        Spacer(Modifier.width(Space.M))
        Column(Modifier.weight(1f)) {
            Text("You & ${partner.name}", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Text("Connect, then pick a game together", color = p.muted, fontSize = TextSize.Body)
        }
    }
}

private enum class OnlineWay { START, JOIN }

/**
 * Online, from the home's one Connect button: with a server link, start a room and send its code or
 * join theirs; without one, join with the message you were sent, or add the link in your profile.
 */
@Composable
private fun OnlineConnect(state: BattleUiState, vm: BattleViewModel, profile: Profile, onOpenProfile: () -> Unit) {
    val p = AppTheme.palette
    if (profile.scriptUrl.startsWith("https://")) {
        var way by rememberSaveable { mutableStateOf(OnlineWay.START) }
        Text("Play online", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text("Start a room and send the code, or join theirs.", color = p.muted, fontSize = TextSize.Body)
        Choices(listOf(OnlineWay.START to "Start a room", OnlineWay.JOIN to "Join with code"), way, { way = it })
        if (way == OnlineWay.START) OnlineHost(state, vm) else OnlineJoin(state, vm)
    } else {
        Text("Play online", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text("To play online, join with the message you were sent: it has the link. Or add the link in your profile.", color = p.muted, fontSize = TextSize.Body)
        OnlineJoin(state, vm)
        SecondaryButton("Open profile", onOpenProfile)
    }
}

@Composable
private fun NearbyConnect(state: BattleUiState, vm: BattleViewModel) {
    val p = AppTheme.palette
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        denied = !granted
        if (granted) pending?.invoke() else vm.showProblem(Problems.noPermission)
        pending = null
    }

    /** Runs [action] once the nearby permissions are granted, asking for them if needed. */
    fun withPermissions(action: () -> Unit) {
        val missing = nearbyPermissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            askPermissions.launch(missing.toTypedArray())
        }
    }

    Text("Connect nearby", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
    Text("Phones in the same room connect over Bluetooth and Wi‑Fi.", color = p.muted, fontSize = TextSize.Body)
    Text(
        "Both phones use the same room code. One starts the room and reads the code out; the other types it and " +
            "joins. Only a phone with the code can join. The code is kept, so next time you just tap.",
        color = p.muted,
        fontSize = TextSize.Body,
    )
    CodeField(state.nearbyCode, vm::setNearbyCode, onNew = vm::newNearbyCode)
    PrimaryButton("Start a nearby room", { withPermissions(vm::hostNearby) }, loading = state.busy)
    SecondaryButton("Join with this code", { withPermissions(vm::joinNearby) }, enabled = !state.busy)
    Text("Keep Bluetooth and Wi‑Fi on and the phones close together. No internet needed.", color = p.muted, fontSize = TextSize.Body)
    if (denied) StatusLine("Nearby play needs the nearby devices permission.", Tone.Bad)
}

@Composable
private fun OnlineHost(state: BattleUiState, vm: BattleViewModel) {
    val p = AppTheme.palette
    Text("Start an online room", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
    Text(
        "Pick the room's code, or keep the one made for you. Send it to the others: they join with it from " +
            "anywhere with internet.",
        color = p.muted,
        fontSize = TextSize.Body,
    )
    CodeField(state.hostCode, vm::setHostCode, onNew = vm::newHostCode, onGo = vm::host)
    PrimaryButton("Start the room", vm::host, loading = state.busy)
}

@Composable
private fun OnlineJoin(state: BattleUiState, vm: BattleViewModel) {
    val p = AppTheme.palette
    val clipboard = LocalClipboardManager.current
    Text("Join a room", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
    Text("Type the code you were sent, or paste the whole message.", color = p.muted, fontSize = TextSize.Body)
    CodeField(state.joinCode, vm::setJoinCode, onGo = vm::join)
    PrimaryButton("Join", vm::join, loading = state.busy)
    SecondaryButton("Paste the message instead", { vm.pasteInvite(clipboard.getText()?.text) })
}
