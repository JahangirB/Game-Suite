package com.pippop.ui.proto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.ui.art.AvatarImage
import com.pippop.ui.art.color
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.ScreenTopBar
import com.pippop.ui.screens.SecondaryButton
import com.pippop.ui.screens.pressedEdge
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LightPalette
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.SudokuPalette
import com.pippop.ui.theme.TextSize

@Composable
private fun Face(avatar: Avatar, size: Dp, dim: Boolean = false) {
    val p = AppTheme.palette
    Box(Modifier.size(size).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape)) {
        AvatarImage(avatar, Modifier.fillMaxSize())
        if (dim) Box(Modifier.fillMaxSize().background(p.card.copy(alpha = 0.55f)))
    }
}

/** Two shares of one bar: yours from the left in coral, theirs from the right in orange. */
@Composable
fun SplitBar(mine: Int, theirs: Int, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val p = AppTheme.palette
    Row(modifier.height(height).clip(Shapes.Full).background(p.accent).border(1.5.dp, p.ink, Shapes.Full)) {
        if (mine > 0) Box(Modifier.weight(mine.toFloat()).fillMaxHeight().background(p.primary))
        if (mine > 0 && theirs > 0) Box(Modifier.width(1.5.dp).fillMaxHeight().background(p.ink))
        if (theirs > 0) Box(Modifier.weight(theirs.toFloat()).fillMaxHeight().background(OpponentColor))
    }
}

@Composable
private fun GameLine(kind: GameKind, mine: Int, theirs: Int, tag: String? = null) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.M, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GameBadge(kind, 30.dp, kind.color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.width(118.dp)) {
                Text(kind.label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tag != null) Text(tag, color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.SemiBold)
            }
            Text("$mine", style = Digits, color = p.primaryText, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(22.dp))
            SplitBar(mine, theirs, Modifier.weight(1f).padding(horizontal = 8.dp))
            Text("$theirs", style = Digits, color = p.opponentText, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        }
    }
}

@Composable
private fun TrophyOnShelf(month: String, score: String, face: Avatar?, tie: Boolean = false, pending: Boolean = false) {
    val p = AppTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (pending) {
            Box(Modifier.size(54.dp, 60.dp).clip(Shapes.M).border(1.5.dp, p.hairline, Shapes.M), contentAlignment = Alignment.Center) {
                Text("?", color = p.muted, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            }
        } else TrophyCup(Modifier.size(54.dp, 60.dp), face = face, tie = tie)
        Text(month, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        Text(score, style = Digits, color = p.muted, fontSize = TextSize.Micro)
    }
}

/** The proposed scoreboard: this month with both faces and the race as one bar; all time and solo one tap away. */
@Composable
fun ScoresNew(tab: Int = 0) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Scoreboard", {})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf("This month", "All time", "Solo"), tab, Modifier.fillMaxWidth(), height = 42.dp)
            if (tab == 2) {
                SoloRecords()
                return@Column
            }
            Surface(color = p.card, shape = Shapes.L, border = BorderStroke(2.dp, p.ink), modifier = Modifier.fillMaxWidth().pressedEdge(p.ink, Radius.L)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("November · 18 days left")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Face(Avatar.FOX, 52.dp)
                            Text("You", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                        }
                        Text("  12", style = Digits, color = p.primaryText, fontSize = TextSize.Score, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                        Text(" – ", color = p.muted, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                        Text("9  ", style = Digits, color = p.opponentText, fontSize = TextSize.Score, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Face(Avatar.PANDA, 52.dp)
                            Text("Maya", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                        }
                    }
                    SplitBar(12, 9, Modifier.fillMaxWidth(), height = 16.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("You lead by 3", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Eyebrow("Last five  ")
                        FormDots(listOf(false, true, true, false, true))
                    }
                }
            }
            Text("Games", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            GameLine(GameKind.WORDS, 3, 0, "Most played")
            GameLine(GameKind.CONNECT4, 2, 1)
            GameLine(GameKind.LUDO, 1, 2, "Maya's on a run of 2")
            GameLine(GameKind.DOMINOES, 0, 2)
            GameLine(GameKind.SUDOKU, 1, 1)
            Text("Trophies", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TrophyOnShelf("Aug", "8 – 6", Avatar.FOX)
                    TrophyOnShelf("Sep", "10 – 15", Avatar.PANDA)
                    TrophyOnShelf("Oct", "4 – 4", null, tie = true)
                    TrophyOnShelf("Nov", "in play", null, pending = true)
                    TrophyOnShelf("Dec", "", null, pending = true)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.Full).background(Board.Wood))
            }
        }
    }
}

@Composable
private fun StatLine(label: String, mine: String, theirs: String) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(mine, style = Digits, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
        Text(label, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(theirs, style = Digits, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
    }
}

/** One game's story between you: the month, each one's records side by side, and the last ten as a row of winners. */
@Composable
fun BoxScope.GameStorySheet() {
    val p = AppTheme.palette
    SheetOver {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameBadge(GameKind.LUDO, 38.dp, GameKind.LUDO.color)
            Column(Modifier.weight(1f)) {
                Text("Ludo", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text("This month: you 1, Maya 2", color = p.muted, fontSize = TextSize.Body)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Face(Avatar.FOX, 34.dp)
            Text("  You", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Maya  ", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
            Face(Avatar.PANDA, 34.dp)
        }
        SplitBar(14, 17, Modifier.fillMaxWidth(), height = 14.dp)
        StatLine("Wins, all time", "14", "17")
        StatLine("Longest run", "3 in a row", "4 in a row")
        StatLine("Quickest win", "9 min", "7 min")
        StatLine("Biggest win", "4 home to 1", "4 home to 0")
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
        Eyebrow("Last ten, newest first")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Avatar.PANDA, Avatar.PANDA, Avatar.FOX, Avatar.PANDA, Avatar.FOX, Avatar.FOX, Avatar.PANDA, Avatar.FOX, Avatar.PANDA, Avatar.PANDA).forEach { Face(it, 29.dp) }
        }
        Note("Times and margins are kept from this update on; wins go back to your first game together.")
    }
}

@Composable
private fun SoloLine(kind: GameKind, best: String, detail: String) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.M, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GameBadge(kind, 30.dp, kind.color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                Text(detail, color = p.muted, fontSize = TextSize.Label)
            }
            Text(best, style = Digits, color = p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        }
    }
}

/** Your own bests, one line a game, kept for good. */
@Composable
fun SoloRecords() {
    SoloLine(GameKind.SUDOKU, "6:42", "Hard · best of 14 solved")
    SoloLine(GameKind.WORDS, "3 tries", "5 letters · 9 solved in a row")
    SoloLine(GameKind.CONNECT4, "9 discs", "Quickest win against Hard")
    SoloLine(GameKind.SLIDE, "1:24", "4 × 4 · 96 slides")
    SoloLine(GameKind.NUMBERS, "2:18", "Medium")
    SoloLine(GameKind.MEMORY, "52 flips", "36 cards")
    SoloLine(GameKind.CODE, "4 guesses", "4 pegs · 6 colours")
    SoloLine(GameKind.GRID, "31 points", "Longest word: STRANGE")
    SoloLine(GameKind.GOLF, "29 strokes", "Hard · nine holes")
    SoloLine(GameKind.SHIPS, "31 shots", "Fewest to sink the fleet")
}

/** The first time you open the app after a month ends: who took it, the cup, and onto the shelf it goes. */
@Composable
fun TrophyMoment() {
    val p = AppTheme.palette
    Box(Modifier.fillMaxSize()) {
        Confetti(Modifier.fillMaxWidth().height(420.dp), count = 60)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Gap(60.dp)
            Eyebrow("October is over")
            TrophyCup(Modifier.size(170.dp, 190.dp), face = Avatar.PANDA)
            Text("Maya takes October", color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Face(Avatar.FOX, 40.dp, dim = true)
                Text("10 – 15", style = Digits, color = p.text, fontSize = TextSize.Score, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Face(Avatar.PANDA, 40.dp)
            }
            Text("25 games together. Your best was Word Race, 5 – 1.", color = p.muted, fontSize = TextSize.BodyL, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Text("November starts level.", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            PrimaryButton("Onto the shelf", {})
        }
    }
}

// ---- Profile --------------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SwitchLine(label: String, line: String, on: Boolean) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            Text(line, color = p.muted, fontSize = TextSize.Label)
        }
        ToySwitch(on)
    }
}

/** A colour theme to choose: its page colour with its main colour on it. */
@Composable
private fun Swatch(theme: SudokuPalette, name: String, on: Boolean, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.fillMaxWidth().height(40.dp).clip(Shapes.M).background(theme.background).border(if (on) 3.dp else 2.dp, if (on) p.primary else p.ink, Shapes.M),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(34.dp, 18.dp).clip(Shapes.Full).background(theme.primary).border(1.5.dp, theme.ink, Shapes.Full)) }
        Text(name, color = p.text, fontSize = TextSize.Micro, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** The proposed profile: you, the look and the sound first; the one-time online setup folded away at the end. */
@Composable
fun ProfileNew() {
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Profile", {})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("You") {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Face(Avatar.FOX, 58.dp)
                    ToyField("Your name", "Sam", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Avatar.FOX, Avatar.CAT, Avatar.DOG, Avatar.PANDA).forEach { a ->
                        Box(Modifier.size(42.dp).clip(CircleShape).background(p.accent).border(if (a == Avatar.FOX) 3.dp else 0.dp, if (a == Avatar.FOX) p.primary else Color.Transparent, CircleShape)) { AvatarImage(a, Modifier.fillMaxSize().padding(2.dp)) }
                    }
                    Text("+8", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.clip(Shapes.Full).background(p.accent).padding(horizontal = 10.dp, vertical = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Text("Use a photo", color = p.primaryText, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                }
            }
            Section("Look") {
                OptionRow("Theme", listOf("System", "Light", "Dark"), 0)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatch(LightPalette, "Lavender", true, Modifier.weight(1f))
                    Swatch(Themes.Mint, "Mint", false, Modifier.weight(1f))
                    Swatch(Themes.Peach, "Peach", false, Modifier.weight(1f))
                    Swatch(Themes.Sky, "Sky", false, Modifier.weight(1f))
                }
            }
            Section("Sound and touch") {
                OptionRow("Sound", listOf("Off", "Quiet", "Normal", "Loud"), 2)
                SwitchLine("Their moves too", "Hear the other players' moves, a little quieter.", true)
                SwitchLine("Vibration", "A buzz for your turn, your moves and the result.", true)
            }
            Section("Playing online") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Online link", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                        Text("Set up · tested 2 days ago", color = p.good, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Change ›", color = p.muted, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Face(Avatar.PANDA, 34.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Maya", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                        Text("Your saved partner · invitations on", color = p.muted, fontSize = TextSize.Label)
                    }
                    Text("Forget", color = p.bad, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                }
            }
            Section("App") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Version 1.0.163", color = p.text, fontSize = TextSize.Body, modifier = Modifier.weight(1f))
                    Text("Check for updates", color = p.primaryText, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Other colourways of the same look: the page, the main colour, the soft fill and the thin line change; ink and cards stay. */
object Themes {
    val Mint = LightPalette.copy(background = Color(0xFFDDF5EA), primary = Color(0xFF2FBF8F), primaryText = Color(0xFF0B6E4F), accent = Color(0xFFEAFBF3), hairline = Color(0xFFBFE5D3), cellPeer = Color(0xFFEAFBF3), cellSameDigit = Color(0xFFC9EEDD))
    val Peach = LightPalette.copy(background = Color(0xFFFFE9DC), primary = Color(0xFFFF8A5B), primaryText = Color(0xFFB8431A), accent = Color(0xFFFFF3EB), hairline = Color(0xFFF3CDB8), cellPeer = Color(0xFFFFF3EB), cellSameDigit = Color(0xFFFFDCC8))
    val Sky = LightPalette.copy(background = Color(0xFFDDEEFF), primary = Color(0xFF5AA9FF), primaryText = Color(0xFF1B5FB3), accent = Color(0xFFEDF6FF), hairline = Color(0xFFBCD8F5), cellPeer = Color(0xFFEDF6FF), cellSameDigit = Color(0xFFCBE2FB))
}

@Suppress("unused")
private val keepButtons: @Composable () -> Unit = { SecondaryButton("", {}) }
