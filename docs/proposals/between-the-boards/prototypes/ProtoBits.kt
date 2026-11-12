package com.pippop.ui.proto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.Reaction
import com.pippop.ui.art.ReactionImage
import com.pippop.ui.art.color
import com.pippop.ui.art.tagline
import com.pippop.ui.screens.PlayerRow
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.ScreenTopBar
import com.pippop.ui.screens.SecondaryButton
import com.pippop.ui.screens.Tone
import com.pippop.ui.screens.darker
import com.pippop.ui.screens.glossyDisc
import com.pippop.ui.screens.lighter
import com.pippop.ui.screens.pressedEdge
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn

// ---- Reactions ------------------------------------------------------------------------------

@Composable
private fun LineChip(text: String) {
    val p = AppTheme.palette
    Text(
        text, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.pressedEdge(p.ink, 18.dp, 2.dp).clip(Shapes.Full).background(p.card).border(2.dp, p.ink, Shapes.Full).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** The reactions as a tray you take in at once: your own lines on top, then everything else, last used first. */
@Composable
fun ReactTrayNew() {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(p.accent).border(1.5.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Edit, null, tint = p.text, modifier = Modifier.size(16.dp))
            }
            LineChip("Nice one!")
            LineChip("Oops")
            LineChip("One more?")
            LineChip("Hurry up!")
            LineChip("Kylian is watching")
        }
        val all = listOf(
            Reaction.LAUGH, Reaction.FIRE, Reaction.HEART, Reaction.KYLIAN, Reaction.THUMBS_UP, Reaction.CLAP, Reaction.WOW, Reaction.SWEAT,
            Reaction.THINK, Reaction.COOL, Reaction.HURRY, Reaction.SIDE_EYE, Reaction.STONKS, Reaction.GALAXY_BRAIN, Reaction.THIS_IS_FINE, null,
        )
        all.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { r ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        when {
                            r == null -> Box(Modifier.fillMaxSize().clip(Shapes.S).border(1.5.dp, p.ink, Shapes.S).background(p.accent), contentAlignment = Alignment.Center) {
                                Text("+", color = p.text, fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
                            }
                            r.sticker -> Box(Modifier.fillMaxSize().clip(Shapes.S)) { ReactionImage(r, 40.dp) }
                            else -> Box(Modifier.fillMaxSize().clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) { ReactionImage(r, 28.dp) }
                        }
                    }
                }
            }
        }
    }
}

/** Something a player said with one tap, by their chip. */
@Composable
fun Bubble(text: String, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Surface(
        color = p.card, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
        border = BorderStroke(2.dp, p.ink), modifier = modifier.pressedEdge(p.ink, 14.dp, 2.dp),
    ) { Text(text, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }
}

/** Your own six lines, written once and kept on your phone. */
@Composable
fun BoxScope.LinesSheet() {
    val p = AppTheme.palette
    SheetOver {
        Text("Your lines", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Note("Six things to say with one tap, up to 24 letters each. They're yours: Maya writes her own.")
        listOf("Nice one!", "Oops", "One more?", "Hurry up!", "Kylian is watching", "").forEach { line ->
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(Shapes.M).background(p.card).border(2.dp, p.ink, Shapes.M).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line.ifEmpty { "Write a line" }, color = if (line.isEmpty()) p.muted else p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (line.isEmpty()) "" else "${line.length}/24", color = p.muted, fontSize = TextSize.Label)
            }
        }
        PrimaryButton("Done", {})
    }
}

// ---- One toy box ----------------------------------------------------------------------------

/** Leaving the room, asked in the app's own look. */
@Composable
fun BoxScope.LeaveDialogNew() {
    val p = AppTheme.palette
    DialogOver("Leave the room?", "You'll disconnect from Maya. A game you're in ends for both of you.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Stay", {}, Modifier.weight(1f))
            PrimaryButton("Leave", {}, Modifier.weight(1f), container = p.bad, content = readableOn(p.bad))
        }
    }
}

/** Joining with a code: one big box with the letters spaced out, and pasting as a real button. */
@Composable
fun BoxScope.JoinSheetNew() {
    val p = AppTheme.palette
    SheetOver {
        Text("Join a room", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Note("Type the code you were sent.")
        Row(
            Modifier.fillMaxWidth().height(72.dp).pressedEdge(p.primary, Radius.L).clip(Shapes.L).background(p.card).border(3.dp, p.primary, Shapes.L),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Text("K7Q2NB", color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, letterSpacing = 10.sp)
            Box(Modifier.size(3.dp, 34.dp).background(p.primary))
        }
        PrimaryButton("Join", {})
        SecondaryButton("Paste the message I was sent", {})
    }
}

@Composable
private fun MenuLine(label: String, detail: String? = null, color: Color = AppTheme.palette.text, last: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    val p = AppTheme.palette
    Column {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = color, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, color = p.muted, fontSize = TextSize.Body, modifier = Modifier.padding(end = 8.dp))
            if (trailing != null) trailing() else if (!last) Text("›", color = p.muted, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
    }
}

/** The game's menu in the same sheet as the result: ink edge, the app's handle, a chunky switch. */
@Composable
fun BoxScope.MenuSheetNew() {
    val p = AppTheme.palette
    SheetOver {
        Text("Connect Four", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Column {
            MenuLine("Resume")
            MenuLine("New game")
            MenuLine("Change mode", "Classic")
            MenuLine("Rules")
            MenuLine("Sound") { ToySwitch(true) }
            MenuLine("Switch game")
            MenuLine("Leave game", color = p.bad, last = true)
        }
    }
}

/** A wait and a message, both in the app's look: Pip and Pop hopping, and a toast with its mark. */
@Composable
fun BoxScope.BitsNew() {
    val p = AppTheme.palette
    Box(Modifier.matchParentSize().background(p.background.copy(alpha = 0.88f)))
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PipPopLoader(Modifier.size(96.dp, 60.dp), t = 0.3f)
        Text("Making your puzzle…", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
    }
    Toast("Maya can't play right now.", Tone.Neutral, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
}

// ---- Looks you choose -----------------------------------------------------------------------

@Composable
private fun Pick(on: Boolean, modifier: Modifier = Modifier, name: String, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().height(74.dp).clip(Shapes.M).background(if (on) p.accent else p.card).border(if (on) 3.dp else 1.5.dp, if (on) p.primary else p.hairline, Shapes.M),
            contentAlignment = Alignment.Center,
        ) { content() }
        Text(name, color = p.text, fontSize = TextSize.Micro, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** A card's back: a colour and a pattern (0 stripes, 1 dots, 2 diamonds, 3 waves) with a star in the middle. */
@Composable
private fun CardBack(color: Color, pattern: Int) {
    Canvas(Modifier.size(40.dp, 54.dp)) {
        val r = CornerRadius(6.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.2f), Offset(0f, 2.dp.toPx()), size, r)
        drawRoundRect(color, Offset.Zero, size, r)
        val light = Color.White.copy(alpha = 0.38f)
        clipRect(3.dp.toPx(), 3.dp.toPx(), size.width - 3.dp.toPx(), size.height - 3.dp.toPx()) {
            when (pattern) {
                0 -> for (i in -6..10) drawLine(light, Offset(i * 7.dp.toPx(), 0f), Offset(i * 7.dp.toPx() + size.height, size.height), 3.dp.toPx())
                1 -> for (i in 0..5) for (j in 0..7) drawCircle(light, 2.dp.toPx(), Offset((i + if (j % 2 == 0) 0.5f else 0f) * 8.dp.toPx(), j * 8.dp.toPx() + 3.dp.toPx()))
                2 -> for (i in 0..4) for (j in 0..5) {
                    val c = Offset(i * 10.dp.toPx() + 5.dp.toPx(), j * 10.dp.toPx() + 5.dp.toPx())
                    drawPath(Path().apply { moveTo(c.x, c.y - 4.dp.toPx()); lineTo(c.x + 4.dp.toPx(), c.y); lineTo(c.x, c.y + 4.dp.toPx()); lineTo(c.x - 4.dp.toPx(), c.y); close() }, light)
                }
                else -> for (j in 0..7) {
                    val path = Path().apply {
                        moveTo(0f, j * 8.dp.toPx())
                        for (i in 0..5) quadraticTo(i * 8.dp.toPx() + 4.dp.toPx(), j * 8.dp.toPx() + (if (i % 2 == 0) -4 else 4).dp.toPx(), (i + 1) * 8.dp.toPx(), j * 8.dp.toPx())
                    }
                    drawPath(path, light, style = Stroke(2.dp.toPx()))
                }
            }
        }
        drawCircle(color.darker(0.25f), 8.dp.toPx(), center)
        drawCircle(Color.White, 3.dp.toPx(), center)
        drawRoundRect(Board.Ink, Offset.Zero, size, r, style = Stroke(1.6.dp.toPx()))
    }
}

/** A small Connect Four frame in a colour, with a disc of each player in it. */
@Composable
private fun FrameSwatch(color: Color) {
    Canvas(Modifier.size(66.dp, 48.dp)) {
        val r = CornerRadius(7.dp.toPx())
        drawRoundRect(Brush.verticalGradient(listOf(color.lighter(0.15f), color.darker(0.15f))), Offset.Zero, size, r)
        drawRoundRect(Board.Ink, Offset.Zero, size, r, style = Stroke(1.6.dp.toPx()))
        val cell = size.width / 3
        for (row in 0..1) for (col in 0..2) {
            val c = Offset(cell * (col + 0.5f), size.height * (0.28f + 0.44f * row))
            val disc = if (row == 1 && col == 0) Pieces.Red else if (row == 1 && col == 1) Pieces.Yellow else null
            if (disc == null) drawCircle(Color.White.copy(alpha = 0.9f), cell * 0.3f, c) else glossyDisc(disc, cell * 0.3f, c)
        }
    }
}

/** A domino in a material: its face, its pips, its line. */
@Composable
private fun TileSwatch(face: Color, pips: Color) {
    Canvas(Modifier.size(64.dp, 34.dp)) {
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.2f), Offset(0f, 2.dp.toPx()), size, r)
        drawRoundRect(Brush.verticalGradient(listOf(face.lighter(0.12f), face.darker(0.08f))), Offset.Zero, size, r)
        drawRoundRect(Board.Ink, Offset.Zero, size, r, style = Stroke(1.6.dp.toPx()))
        drawLine(pips.copy(alpha = 0.6f), Offset(size.width / 2, 5.dp.toPx()), Offset(size.width / 2, size.height - 5.dp.toPx()), 1.5.dp.toPx())
        for ((x, y) in listOf(0.14f to 0.28f, 0.36f to 0.72f, 0.25f to 0.5f, 0.64f to 0.28f, 0.86f to 0.28f, 0.64f to 0.72f, 0.86f to 0.72f)) drawCircle(pips, 2.6.dp.toPx(), Offset(size.width * x, size.height * y))
    }
}

/** "Looks", from a game's menu: each of you picks the card backs, the frame and the tiles you like. */
@Composable
fun BoxScope.LooksSheet() {
    val p = AppTheme.palette
    SheetOver {
        Text("Looks", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Note("Only on your phone: Maya picks her own.")
        Eyebrow("Card backs · Memory Pairs and Colour Clash")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pick(true, Modifier.weight(1f), "Stripes") { CardBack(GameKind.MEMORY.color, 0) }
            Pick(false, Modifier.weight(1f), "Dots") { CardBack(Pieces.Blue, 1) }
            Pick(false, Modifier.weight(1f), "Diamonds") { CardBack(Pieces.Purple, 2) }
            Pick(false, Modifier.weight(1f), "Waves") { CardBack(Pieces.Green, 3) }
        }
        Eyebrow("Frame · Connect Four")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pick(true, Modifier.weight(1f), "Blue") { FrameSwatch(Board.ConnectBlue) }
            Pick(false, Modifier.weight(1f), "Mint") { FrameSwatch(Pieces.Teal) }
            Pick(false, Modifier.weight(1f), "Plum") { FrameSwatch(Pieces.Purple) }
            Pick(false, Modifier.weight(1f), "Slate") { FrameSwatch(Pieces.SafeSteel) }
        }
        Eyebrow("Tiles · Dominoes")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pick(true, Modifier.weight(1f), "Ivory") { TileSwatch(Board.Bone, Board.Ink) }
            Pick(false, Modifier.weight(1f), "Ebony") { TileSwatch(Board.Ink, Board.Bone) }
            Pick(false, Modifier.weight(1f), "Maple") { TileSwatch(Color(0xFFE2B77E), Board.Ink) }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** A stand-in for one of your photos: a little scene, so it reads as a photo. */
@Composable
private fun PhotoThumb(kind: Int, modifier: Modifier = Modifier) {
    val skies = listOf(Color(0xFFFFB26B) to Color(0xFFFFD98E), Color(0xFF6EC6FF) to Color(0xFFC8ECFF), Color(0xFFA18CFF) to Color(0xFFFFC4E1), Color(0xFF4FD1A5) to Color(0xFFD7FFF0))
    val (a, b) = skies[kind % 4]
    Canvas(modifier.clip(Shapes.S).border(1.5.dp, Board.Ink, Shapes.S)) {
        drawRect(Brush.verticalGradient(listOf(a, b)))
        if (kind % 2 == 0) {
            drawCircle(Color(0xFFFFF6C2), size.height * 0.13f, Offset(size.width * 0.72f, size.height * 0.28f))
            drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width * 0.35f, size.height * 0.45f); lineTo(size.width * 0.6f, size.height * 0.75f); lineTo(size.width * 0.8f, size.height * 0.55f); lineTo(size.width, size.height); close() }, Color(0xFF3F8F5A))
        } else {
            drawCircle(Color(0xFF6A4CFF), size.width * 0.26f, Offset(size.width * 0.36f, size.height * 1.0f))
            drawCircle(Color(0xFFE8505B), size.width * 0.26f, Offset(size.width * 0.66f, size.height * 1.02f))
            drawCircle(Color(0xFFF3C9A0), size.width * 0.14f, Offset(size.width * 0.36f, size.height * 0.52f))
            drawCircle(Color(0xFFC98E62), size.width * 0.14f, Offset(size.width * 0.66f, size.height * 0.54f))
        }
    }
}

/** Memory Pairs' lobby with the cards showing your own photos: picked once by whoever opened it, sent to every phone. */
@Composable
fun LobbyMemoryPhotos() {
    val p = AppTheme.palette
    val kind = GameKind.MEMORY
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(kind.label, {})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Players", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                    PlayerRow(mate("Sam", Avatar.FOX, false, kind), isMe = true, color = p.primary, reaction = null, seat = 0, game = kind)
                    PlayerRow(mate("Maya", Avatar.PANDA, true, kind), isMe = false, color = OpponentColor, reaction = null, seat = 1, game = kind)
                }
            }
            Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Setup", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                    OptionRow("Deck", listOf("16", "24", "36"), 0)
                    OptionRow("Pictures", listOf("Animals", "Your photos"), 1, note = "Eight photos make sixteen cards.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(8) { PhotoThumb(it, Modifier.weight(1f).aspectRatio(1f)) }
                    }
                    SecondaryButton("Choose other photos", {})
                    Note("Each photo is cropped square, made small and sent once to the other phones, the way Slide Race sends its picture.")
                }
            }
        }
        ReadyBar()
    }
}

@Suppress("unused")
private val keepBits = listOf<Any>(LineHeight.Body, Dp.Unspecified)
