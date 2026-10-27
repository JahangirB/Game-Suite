package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.TextSize
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.theme.AppTheme

/** What Android asks for before two phones may find each other directly. */
private fun nearbyPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()

/** Open or join a room with a phone close by, without internet or a server. */
@Composable
fun NearbySection(state: BattleUiState, vm: BattleViewModel) {
    val p = AppTheme.palette
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        denied = !granted
        if (granted) pending?.invoke()
        pending = null
    }

    /** Runs [action] once the nearby permissions are granted, asking for them if needed. */
    fun withPermissions(action: () -> Unit) {
        val missing = nearbyPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            askPermissions.launch(missing.toTypedArray())
        }
    }

    AppCard("Play in the same room") {
        Text(
            "Two phones connect directly over Bluetooth and Wi‑Fi. No internet and no server needed. " +
                "Keep both phones close together with Bluetooth on.",
            color = p.muted,
            fontSize = TextSize.Body,
        )
        if (denied) {
            Text(
                "Nearby battles need the Bluetooth, location, and nearby devices permissions (precise location). " +
                    "You can allow them in the app's settings.",
                color = p.bad,
                fontSize = TextSize.Body,
            )
        }
    }

    AppCard("Room code") {
        Text(
            "Both phones use the same code. One opens the room and reads the code out; the other types it " +
                "and joins. Only a phone with the code can join. The code is kept, so next time you just tap.",
            color = p.muted,
            fontSize = TextSize.Body,
        )
        OutlinedTextField(
            value = state.nearbyCode,
            onValueChange = vm::setNearbyCode,
            label = { Text("Room code") },
            singleLine = true,
            textStyle = codeStyle(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = vm::newNearbyCode) { Icon(Icons.Filled.Refresh, "New code") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AppCard("Open the room") {
        GameChooser(state.hostGame, vm::setHostGame)
        PrimaryButton("Open nearby room", { withPermissions(vm::hostNearby) }, loading = state.busy)
    }

    AppCard("Join the room") {
        Text("The other phone opens the room first.", color = p.muted, fontSize = TextSize.Body)
        SecondaryButton("Join nearby room", { withPermissions(vm::joinNearby) }, enabled = !state.busy)
    }
}
