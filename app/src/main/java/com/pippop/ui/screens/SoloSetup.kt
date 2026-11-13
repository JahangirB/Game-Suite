package com.pippop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import com.pippop.data.SoloSetups
import com.pippop.game.SudokuShape
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/**
 * A game's solo setup, set in the game's sheet and set again from inside the game: its [kind]'s
 * choices as [OptionRow]s (what the game offers, nothing more), the settings that live on this
 * phone, and one main button that starts the game with them. Every pick is remembered at once,
 * so the next game starts with the same setup in one tap.
 */
@Composable
fun SoloSetupContent(
    kind: GameKind,
    values: Map<String, String>,
    onPick: (key: String, value: String) -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    /** A way back to where the setup was opened from (the game's sheet), shown as a second button. */
    onBack: (() -> Unit)? = null,
) {
    val p = AppTheme.palette
    val spec = SoloSetups.spec(kind) ?: return
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Space.XL, end = Space.XL, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Text("${kind.label} on your own", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
        spec.shown(values).forEach { field ->
            if (kind == GameKind.SUDOKU && field.key == "shape") {
                // The boards are drawn, so they are easier to tell apart than named.
                Text(field.label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                ShapePicker(SudokuShape.offered.firstOrNull { it.key == values[field.key] } ?: SudokuShape.CLASSIC, enabled = true) { onPick(field.key, it.key) }
                field.note(values)?.let { Text(it, color = p.muted, fontSize = TextSize.Label, lineHeight = LineHeight.Label) }
            } else {
                OptionRow(
                    label = field.label,
                    options = field.options.map { it.value to it.short },
                    selected = values[field.key] ?: field.default,
                    onPick = { onPick(field.key, it) },
                    note = field.note(values),
                )
            }
        }
        // What belongs to this phone rather than to a game: it is saved on its own, as before.
        when (kind) {
            GameKind.WORDS -> WordsHardSwitch()
            GameKind.GOLF -> GolfTimePicker()
            GameKind.BLOCKS -> BlocksHintsSwitch()
            else -> Unit
        }
        PrimaryButton(startLabel, onStart)
        if (onBack != null) SecondaryButton("Back", onBack)
    }
}

/** [SoloSetupContent] in a sheet of its own, for the setup that is opened from inside a game. It starts with [onStart] and closes itself first. */
@Composable
fun SoloSetupSheet(kind: GameKind, startLabel: String, onStart: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val store = ProfileStore.get(LocalContext.current)
    val profile by store.profile.collectAsState()
    val values = profile.soloSetup(kind)
    ToySheet(onDismiss = onDismiss) {
        SoloSetupContent(
            kind, values,
            onPick = { key, value -> store.setSoloSetup(kind, values + (key to value)) },
            startLabel = SoloSetups.spec(kind)?.let { "$startLabel · ${it.buttonText(values)}" } ?: startLabel,
            onStart = {
                onDismiss()
                onStart(values)
            },
        )
    }
}

/**
 * What a game's sheet says about solo: one row of [Choices] for a game with a single choice, or
 * a line stating the last setup with "Change" for a game with more; then the button that
 * starts with it ("Play solo · Medium"). A game with no setup is just the button.
 */
@Composable
fun SoloSetupRow(kind: GameKind, values: Map<String, String>, onPick: (key: String, value: String) -> Unit, onChange: () -> Unit) {
    val p = AppTheme.palette
    val spec = SoloSetups.spec(kind) ?: return
    if (spec.single) {
        val field = spec.fields.first()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.rowLabel, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = Space.M))
            Choices(field.options.map { it.value to it.short }, values[field.key] ?: field.default, { onPick(field.key, it) }, Modifier.weight(1f))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.line(values), color = p.muted, fontSize = TextSize.Body, modifier = Modifier.weight(1f))
            Text(
                "Change", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(Shapes.Full)
                    .clickable(role = Role.Button, onClickLabel = "Change how ${kind.label} is set up", onClick = onChange)
                    .padding(vertical = Space.M, horizontal = Space.XS)
                    .background(p.card, Shapes.Full)
                    .border(2.dp, p.ink, Shapes.Full)
                    .padding(horizontal = Space.M, vertical = Space.S),
            )
        }
    }
}

/** One fact about a game, as a small pill ("2 players", "About 5 min"). */
@Composable
fun Fact(text: String) {
    val p = AppTheme.palette
    Text(
        text, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(Shapes.Full).background(p.accent).border(1.dp, p.hairline, Shapes.Full).padding(horizontal = Space.M, vertical = Space.XS),
    )
}

/** The three facts under a game's name, side by side (wrapping when the text is big). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Facts(facts: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { facts.forEach { Fact(it) } }
}

/**
 * Shown by a solo screen opened without a game under way (the app was restored on it): there is
 * nothing to show, and the setup lives in the game's sheet, so it goes back to the home.
 */
@Composable
fun BackToHome(onHome: () -> Unit) {
    LaunchedEffect(Unit) { onHome() }
    Box(Modifier.fillMaxSize())
}
