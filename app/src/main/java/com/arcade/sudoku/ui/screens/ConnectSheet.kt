package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.Problems
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.Space
import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Tracking

/** What the home's connect sheet is for: a nearby room, or joining an online room with its code. */
enum class ConnectKind { NEARBY, JOIN_ONLINE }

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
fun ConnectSheet(kind: ConnectKind, state: BattleUiState, vm: BattleViewModel) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Space.XL, end = Space.XL, bottom = Space.XXXL),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        when (kind) {
            ConnectKind.NEARBY -> NearbyConnect(state, vm)
            ConnectKind.JOIN_ONLINE -> OnlineJoin(state, vm)
        }
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
    Text(
        "Both phones use the same room code. One starts the room and reads the code out; the other types it and " +
            "joins. Only a phone with the code can join. The code is kept, so next time you just tap.",
        color = p.muted,
        fontSize = TextSize.Body,
    )
    CodeField(state.nearbyCode, vm::setNearbyCode, label = "Room code", onNew = vm::newNearbyCode)
    PrimaryButton("Start a nearby room", { withPermissions(vm::hostNearby) }, loading = state.busy)
    SecondaryButton("Join with this code", { withPermissions(vm::joinNearby) }, enabled = !state.busy)
    Text("Keep Bluetooth and Wi‑Fi on and the phones close together. No internet needed.", color = p.muted, fontSize = TextSize.Body)
    if (denied) StatusLine("Nearby play needs the nearby devices permission.", Tone.Bad)
}

@Composable
private fun OnlineJoin(state: BattleUiState, vm: BattleViewModel) {
    val p = AppTheme.palette
    val clipboard = LocalClipboardManager.current
    Text("Join a room", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
    Text("Type the code you were sent, or paste the whole message.", color = p.muted, fontSize = TextSize.Body)
    CodeField(state.joinCode, vm::setJoinCode, label = "Room code", onGo = vm::join)
    PrimaryButton("Join", vm::join, loading = state.busy)
    TextButton(onClick = { vm.pasteInvite(clipboard.getText()?.text) }) {
        Text("Paste the message instead", color = p.primaryText)
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, label: String, onNew: (() -> Unit)? = null, onGo: (() -> Unit)? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = codeStyle(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = if (onGo != null) ImeAction.Go else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onGo = { onGo?.invoke() }),
        trailingIcon = onNew?.let { new -> { IconButton(onClick = new) { Icon(Icons.Filled.Refresh, "New code") } } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun codeStyle() = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = TextSize.Title,
    letterSpacing = Tracking.CodeSmall,
    color = AppTheme.palette.text,
)
