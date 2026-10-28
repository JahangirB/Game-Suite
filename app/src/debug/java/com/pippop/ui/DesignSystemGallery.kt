package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.ui.screens.AppCard
import com.pippop.ui.screens.OptionGroup
import com.pippop.ui.screens.Pill
import com.pippop.ui.screens.PlayerAvatar
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.ProgressRow
import com.pippop.ui.screens.SecondaryButton
import com.pippop.ui.screens.StatusLine
import com.pippop.ui.screens.Tone
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.SeatColors
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/**
 * Every shared component in its states, and the tokens they are built from, on one page. It
 * lives in debug code and is drawn by the screenshot tests (light, dark and large text), so a
 * change to a component can be seen in one picture. The app itself never shows it.
 */
@Composable
fun DesignSystemGallery() {
    val p = AppTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.L),
    ) {
        Text("Design system", color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)

        Section("Buttons") {
            PrimaryButton("Rematch", {})
            SecondaryButton("Back to games", {})
            PrimaryButton("Not available", {}, enabled = false)
            PrimaryButton("Loading", {}, loading = true)
        }

        Section("Cards") {
            AppCard("Flat: information") { Text("Players, codes, settings", color = p.muted, fontSize = TextSize.Body) }
            AppCard("Raised: a card you press", raised = true) { Text("Game tiles and choices", color = p.muted, fontSize = TextSize.Body) }
        }

        Section("Status lines") {
            StatusLine("Waiting for Alex…", Tone.Neutral)
            StatusLine("Both chose Hard. Starting…", Tone.Good)
            StatusLine("Alex picked Easy. Switch to Easy?", Tone.Warning)
            StatusLine("Alex has left the game.", Tone.Bad)
        }

        Section("Pills") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                Pill("Choosing…", p.muted)
                Pill("Ready · Hard", p.good)
                Pill("Offline", p.bad)
            }
        }

        Section("Option group") {
            OptionGroup(listOf(1 to "Easy", 2 to "Medium", 3 to "Hard"), selected = 2, enabled = true) {}
        }

        Section("Players") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar("Sam", p.primary)
                PlayerAvatar("Alex", OpponentColor)
                SeatColors.drop(1).forEachIndexed { i, c -> PlayerAvatar(listOf("Jo", "Kim")[i], c) }
                PlayerAvatar("Fox", p.primary, avatar = Avatar.FOX)
            }
            ProgressRow("You", 42, p.primary, showAvatar = true)
            ProgressRow("Alex", 67, OpponentColor, showAvatar = true)
        }

        Section("Type") {
            Text("Score 3 – 2", color = p.text, fontSize = TextSize.Score, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, style = Digits)
            Text("Display large", color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Text("Display medium", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Text("Title", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Text("Body large: Alex picked Easy.", color = p.text, fontSize = TextSize.BodyL)
            Text("Body: four in a row wins.", color = p.text, fontSize = TextSize.Body)
            Text("Label: Leave · React · Rules", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
            Text("Micro: Alex's pick", color = p.muted, fontSize = TextSize.Micro)
        }

        Section("Colours") {
            Swatches(listOf(p.primary, p.primaryText, p.good, p.bad, p.warning, p.muted, p.ink, p.hairline))
            Swatches(listOf(Pieces.Red, Pieces.Yellow, Pieces.Green, Pieces.Blue, Pieces.Purple, Pieces.Orange, Pieces.Pink))
            Swatches(listOf(Board.Ink, Board.Felt, Board.Bone, Board.Glow, Board.ConnectBlue, Board.TttBlue, Board.Ice))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(title.uppercase(), color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Swatches(colors: List<Color>) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
        colors.forEach { c -> Box(Modifier.size(32.dp).background(c, Shapes.S).border(1.dp, p.hairline, Shapes.S)) }
    }
}
