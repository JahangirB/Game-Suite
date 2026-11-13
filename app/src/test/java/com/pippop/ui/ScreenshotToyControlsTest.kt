package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.pippop.ui.screens.OptionGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pippop.data.GameKind
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.Choices
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.GameMenuRows
import com.pippop.ui.screens.OptionRow
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.screens.ToySwitch
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import org.junit.Test

/** The app's own choices and switch: the game menu with its switch, a game's rules, and the profile's Appearance and sound. */
class ScreenshotToyControlsTest : ScreenshotBase() {
    @Composable
    private fun menu() {
        Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
            ToySheetCard {
                GameMenuRows(
                    GameMenu(GameKind.CONNECT4, onRules = {}, onLeave = {}, mode = "Classic", onNewGame = {}, onChangeMode = {}, onSwitchGame = {}),
                    close = {}, then = { it },
                )
            }
        }
    }

    @Composable
    private fun rules() = Column(Modifier.padding(Space.L)) {
        AppCard("Rules") {
            OptionRow("When stuck", listOf(true to "Draw", false to "Pass"), true, {}, note = "Take tiles from the boneyard until one fits.")
            OptionRow("Play to", listOf(50 to "50", 100 to "100", 150 to "150"), 100, {})
            OptionRow("Sides", listOf(false to "Each alone", true to "2 v 2"), false, {}, off = setOf(true), note = "2 v 2 needs four players.")
            Choices((1..6).map { it to "$it cards" }, 3, {})
        }
    }

    @Composable
    private fun profile() = Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.L)) {
        val p = AppTheme.palette
        AppCard("Appearance") {
            OptionRow("Theme", listOf(0 to "System", 1 to "Light", 2 to "Dark"), 0, {})
            Choices(listOf(0 to "Toy Harbor", 1 to "Pirate Wood", 2 to "Midnight Lagoon Deluxe"), 0, {}, perRow = 2)
        }
        AppCard("Sound and touch") {
            listOf("Sound" to true, "Vibration" to false).forEach { (name, on) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    ToySwitch(on, null)
                }
            }
        }
    }

    @Composable
    private fun big() {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.3f)) {
            Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.L)) {
                AppCard("Golf") {
                    OptionGroup(listOf("easy" to "Easy", "hard" to "Hard · three islands, more sticker spots"), "hard", enabled = true, onPick = {})
                    OptionGroup(listOf("day" to "Day", "sunset" to "Sunset", "night" to "Night", "mixed" to "Mixed"), "sunset", enabled = true, onPick = {})
                    OptionGroup(listOf(1 to "Classic", 2 to "Ultimate: nine boards in one"), 1, enabled = true, onPick = {})
                    OptionGroup(listOf(1 to "Easy", 2 to "Medium", 3 to "Hard"), 2, enabled = true, onPick = {})
                }
            }
        }
    }

    @Test fun bigLight() = shot("toy-controls-big", dark = false) { big() }
    @Test fun bigDark() = shot("toy-controls-big", dark = true) { big() }
    @Test fun menuLight() = shot("toy-controls-menu", dark = false) { menu() }
    @Test fun menuDark() = shot("toy-controls-menu", dark = true) { menu() }
    @Test fun rulesLight() = shot("toy-controls-rules", dark = false) { rules() }
    @Test fun rulesDark() = shot("toy-controls-rules", dark = true) { rules() }
    @Test fun profileLight() = shot("toy-controls-profile", dark = false) { profile() }
    @Test fun profileDark() = shot("toy-controls-profile", dark = true) { profile() }
}
