package com.pippop.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.Profile
import com.pippop.ui.AppViewModel
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/**
 * The first time the app opens: what it is, your name and picture, how you'll play most
 * (the home's switch), and how the home works. Each choice is saved as it is made.
 */
@Composable
fun FirstRunScreen(profile: Profile, app: AppViewModel, onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val askNearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { page = 3 }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.XL, vertical = Space.XXL),
        verticalArrangement = Arrangement.spacedBy(Space.L),
    ) {
        Dots(page)
        when (page) {
            0 -> Welcome { page = 1 }
            1 -> AboutYou(profile, app) { page = 2 }
            2 -> HowYouPlay(profile.playMode, app::setPlayMode) {
                // Nearby play needs Android's permission to find phones; ask now, with the reason on screen.
                if (profile.playMode == PlayMode.OFFLINE) askNearby.launch(nearbyPermissions()) else page = 3
            }
            else -> Ready(onDone)
        }
    }
}

/** Where you are in the four pages. */
@Composable
private fun Dots(page: Int) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        repeat(4) { i ->
            Box(Modifier.size(if (i == page) 24.dp else 8.dp, 8.dp).clip(Shapes.Full).background(if (i <= page) p.primary else p.hairline))
        }
    }
}

@Composable
private fun Title(eyebrow: String, title: String, line: String) {
    val p = AppTheme.palette
    Text(eyebrow.uppercase(), color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
    Text(title, color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayL)
    Text(line, color = p.muted, fontSize = TextSize.BodyL)
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    val p = AppTheme.palette
    Title("Welcome", "${GameKind.entries.size} games to play together", "On two or more phones: in the same room with no internet, or far apart online.")
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        listOf(GameKind.CONNECT4, GameKind.SUDOKU, GameKind.LUDO, GameKind.DOMINOES, GameKind.SHIPS, GameKind.MEMORY).forEach { kind ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clip(Shapes.M).background(kind.color).border(2.dp, p.ink, Shapes.M),
            ) { GameIcon(kind, Modifier.size(26.dp)) }
        }
    }
    PrimaryButton("Get started", onNext)
}

@Composable
private fun AboutYou(profile: Profile, app: AppViewModel, onNext: () -> Unit) {
    Title("About you", "What should others see?", "Your name and picture show on the other phones during games.")
    OutlinedTextField(
        value = profile.name,
        onValueChange = app::setName,
        label = { Text("Your name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    AvatarPicker(selected = profile.avatar, onPick = app::setAvatar)
    PrimaryButton("Next", onNext, enabled = profile.name.isNotBlank())
}

@Composable
private fun HowYouPlay(mode: PlayMode, onPick: (PlayMode) -> Unit, onNext: () -> Unit) {
    val p = AppTheme.palette
    Title("How you play", "How will you play most?", "You can switch any time at the top of the home screen.")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.M)) {
        ModeChoice(
            "Offline · nearby", "Phones in the same room, on a plane or a train. No internet needed.",
            selected = mode == PlayMode.OFFLINE,
        ) { onPick(PlayMode.OFFLINE) }
        ModeChoice(
            "Online", "Far apart. Works once someone sends you an invite, or you set up your own link.",
            selected = mode == PlayMode.ONLINE,
        ) { onPick(PlayMode.ONLINE) }
    }
    if (mode == PlayMode.OFFLINE) {
        Text(
            "Next, Android asks to let phones find each other over Bluetooth and Wi‑Fi. It's only used to connect for games.",
            color = p.muted, fontSize = TextSize.Body,
        )
    }
    PrimaryButton("Next", onNext)
}

@Composable
private fun ModeChoice(title: String, line: String, selected: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        color = p.card,
        shape = Shapes.L,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) p.primary else p.hairline),
        modifier = Modifier.fillMaxWidth().clip(Shapes.L).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.XS)) {
            Text((if (selected) "● " else "○ ") + title, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Text(line, color = p.muted, fontSize = TextSize.Body)
        }
    }
}

@Composable
private fun Ready(onDone: () -> Unit) {
    Title("Ready", "How the home works", "Three things, then you're playing.")
    Tip(1, "Offline or online: pick it with the switch at the top. It's remembered.")
    Tip(2, "Connect once. You stay connected while you play game after game.")
    Tip(3, "Then pick games together. Hold a game to move it.")
    PrimaryButton("Got it, let's play", onDone)
}

@Composable
private fun Tip(n: Int, text: String) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp).clip(Shapes.Full).background(p.primary)) {
            Text("$n", color = p.onPrimary, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(Space.M))
        Text(text, color = p.text, fontSize = TextSize.BodyL, modifier = Modifier.weight(1f))
    }
}
