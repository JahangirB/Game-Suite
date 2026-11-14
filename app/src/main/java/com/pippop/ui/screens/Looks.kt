package com.pippop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.CardBackLook
import com.pippop.data.FrameLook
import com.pippop.data.GameKind
import com.pippop.data.TileLook
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The looks this player chose for card backs, Connect Four's frame and the domino tiles. They
 * are kept on this phone only: nothing about them is sent, so each player sees their own.
 */
data class Looks(
    val cardBack: CardBackLook = CardBackLook.STRIPES,
    val frame: FrameLook = FrameLook.BLUE,
    val tile: TileLook = TileLook.IVORY,
)

val LocalLooks = compositionLocalOf { Looks() }

/** Whether [this] game has a look to choose (its menu then has a Looks row). */
fun GameKind.hasLooks() = this == GameKind.MEMORY || this == GameKind.CLASH || this == GameKind.CONNECT4 || this == GameKind.DOMINOES

/** A card back's colour on Memory Pairs (Colour Clash keeps its dark deck and draws only the pattern). */
val CardBackLook.color: Color
    get() = when (this) {
        CardBackLook.STRIPES -> GameKind.MEMORY.color
        CardBackLook.DOTS -> Pieces.Blue
        CardBackLook.DIAMONDS -> Pieces.Purple
        CardBackLook.WAVES -> Pieces.Green
    }

/** Connect Four's frame colour. */
val FrameLook.color: Color
    get() = when (this) {
        FrameLook.BLUE -> Board.ConnectBlue
        FrameLook.MINT -> Pieces.Teal
        FrameLook.PLUM -> Pieces.Purple
        FrameLook.SLATE -> Pieces.SafeSteel
    }

/** How a domino tile is painted: its [face], the [pip] colour (also the groove), its [edge] line and the [rim] light under a pip. */
class TilePaint(val face: Color, val pip: Color, val edge: Color, val rim: Color, val groove: Color)

val TileLook.paint: TilePaint
    get() = when (this) {
        TileLook.IVORY -> TilePaint(Board.Bone, Board.Ink, Board.Ink, Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.8f))
        TileLook.EBONY -> TilePaint(Board.Ink, Board.Bone, Pieces.SafeDial, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.6f))
        TileLook.MAPLE -> TilePaint(Board.Maple, Board.Ink, Board.Ink, Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f))
    }

// ---- Drawing -------------------------------------------------------------------------------

/**
 * The pattern on the back of a card [w] by [h]: [look]'s stripes, dots, diamonds or waves in
 * [light]. Everything scales with the card's width, so a small swatch and a big card match.
 */
fun DrawScope.backPattern(look: CardBackLook, w: Float, h: Float, light: Color) {
    when (look) {
        CardBackLook.STRIPES -> {
            val step = w * 0.22f
            var x = -h
            while (x < w) {
                drawLine(light, Offset(x, h), Offset(x + h, 0f), step * 0.45f)
                x += step
            }
        }
        CardBackLook.DOTS -> {
            val step = w * 0.17f
            var j = 0
            var y = step / 2
            while (y < h) {
                var x = if (j % 2 == 0) step / 2 else step
                while (x < w) {
                    drawCircle(light, step * 0.22f, Offset(x, y))
                    x += step
                }
                y += step * 0.85f
                j++
            }
        }
        CardBackLook.DIAMONDS -> {
            val step = w * 0.22f
            val r = step * 0.36f
            var y = step / 2
            while (y < h + step) {
                var x = step / 2
                while (x < w + step) {
                    drawPath(
                        Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() },
                        light,
                    )
                    x += step
                }
                y += step
            }
        }
        CardBackLook.WAVES -> {
            val step = w * 0.17f
            val amp = step * 0.3f
            var y = step / 2
            while (y < h + step) {
                val path = Path().apply {
                    moveTo(0f, y)
                    var i = 0
                    var x = 0f
                    while (x < w) {
                        quadraticTo(x + step / 2, y + if (i % 2 == 0) -amp * 2 else amp * 2, x + step, y)
                        x += step
                        i++
                    }
                }
                drawPath(path, light, style = Stroke(w * 0.035f))
                y += step
            }
        }
    }
}

/** The back of a Memory card: [look]'s colour and pattern, a frame, and a star in a disc. Fills the canvas. */
fun DrawScope.drawMemoryBack(look: CardBackLook) {
    val base = look.color
    val w = size.width
    val h = size.height
    drawRect(base)
    backPattern(look, w, h, Color.White.copy(alpha = if (look == CardBackLook.STRIPES) 0.18f else 0.3f))
    val m = w * 0.1f
    drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(m, m), androidx.compose.ui.geometry.Size(w - 2 * m, h - 2 * m), CornerRadius(w * 0.1f), style = Stroke(w * 0.025f))
    val c = Offset(w / 2, h / 2)
    drawCircle(base.darker(0.18f), w * 0.27f, c)
    val star = Path()
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) w * 0.2f else w * 0.09f
        val a = (-PI / 2 + k * PI / 5).toFloat()
        val pt = Offset(c.x + r * cos(a), c.y + r * sin(a))
        if (k == 0) star.moveTo(pt.x, pt.y) else star.lineTo(pt.x, pt.y)
    }
    star.close()
    drawPath(star, Color.White)
}

// ---- The sheet -----------------------------------------------------------------------------

/**
 * What a game's Looks sheet shows: only the looks that apply to [game]. Each choice is a swatch
 * of the real thing with its name under it; the chosen one has a thick edge, a tick and a bold name.
 */
@Composable
internal fun LooksSheetContent(
    game: GameKind,
    looks: Looks,
    onCardBack: (CardBackLook) -> Unit,
    onFrame: (FrameLook) -> Unit,
    onTile: (TileLook) -> Unit,
) {
    val p = AppTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.L).padding(bottom = Space.L).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Text("Looks", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text("Only on your phone. Everyone else picks their own.", color = p.muted, fontSize = TextSize.Body)
        if (game == GameKind.MEMORY || game == GameKind.CLASH) {
            LookGroup("Card backs · Memory Pairs and Colour Clash", CardBackLook.entries, looks.cardBack, { it.label }, onCardBack, perRow = 4) { CardBackSwatch(it) }
        }
        if (game == GameKind.CONNECT4) {
            LookGroup("Frame · Connect Four", FrameLook.entries, looks.frame, { it.label }, onFrame, perRow = 4) { FrameSwatch(it) }
        }
        if (game == GameKind.DOMINOES) {
            LookGroup("Tiles · Dominoes", TileLook.entries, looks.tile, { it.label }, onTile, perRow = 3) { TileSwatch(it) }
        }
    }
}

/** One heading and its swatches in rows (two to a row at larger text, so no name is clipped). */
@Composable
private fun <T> LookGroup(
    title: String,
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onPick: (T) -> Unit,
    perRow: Int,
    swatch: @Composable (T) -> Unit,
) {
    val p = AppTheme.palette
    val across = if (LocalDensity.current.fontScale > 1.2f) 2 else perRow
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(title.uppercase(), color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        options.chunked(across).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                row.forEach { option ->
                    LookPick(name(option), option == selected, { onPick(option) }, Modifier.weight(1f)) { swatch(option) }
                }
                repeat(across - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A swatch a person can pick: a radio button for a screen reader, at least 48 dp tall. */
@Composable
private fun LookPick(name: String, on: Boolean, onPick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val p = AppTheme.palette
    Column(
        modifier.heightIn(min = 48.dp)
            .selectable(selected = on, role = Role.RadioButton, onClick = onPick)
            .semantics { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.XS),
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(Shapes.M)
                .background(if (on) p.accent else p.card)
                .border(if (on) 3.dp else 2.dp, if (on) p.primaryText else p.hairline, Shapes.M)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) { content() }
        Text(
            (if (on) "✓ " else "") + name, color = p.text, fontSize = TextSize.Label,
            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** A small card back, drawn as the real one is. */
@Composable
private fun CardBackSwatch(look: CardBackLook) {
    Canvas(Modifier.size(40.dp, 54.dp).clip(Shapes.S).border(2.dp, AppTheme.palette.ink, Shapes.S)) { drawMemoryBack(look) }
}

/** A small Connect Four frame with an empty hole and a disc of each player in it. */
@Composable
private fun FrameSwatch(look: FrameLook) {
    val ink = AppTheme.palette.ink
    val hole = AppTheme.palette.background
    Canvas(Modifier.size(66.dp, 48.dp)) {
        val color = look.color
        val r = CornerRadius(7.dp.toPx())
        drawRoundRect(Brush.verticalGradient(listOf(color.lighter(0.2f), color.darker(0.16f))), Offset.Zero, size, r)
        drawRoundRect(ink, Offset.Zero, size, r, style = Stroke(2.dp.toPx()))
        val cell = size.width / 3
        for (row in 0..1) for (col in 0..2) {
            val c = Offset(cell * (col + 0.5f), size.height * (0.28f + 0.44f * row))
            val disc = if (row == 1 && col == 0) MyDisc else if (row == 1 && col == 1) TheirDisc else null
            drawCircle(hole, cell * 0.3f, c)
            drawCircle(ink.copy(alpha = 0.5f), cell * 0.3f, c, style = Stroke(1.5.dp.toPx()))
            if (disc != null) {
                glossyDisc(disc, cell * 0.3f, c)
                drawCircle(ink, cell * 0.3f, c, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** A small domino, drawn as a real tile is. */
@Composable
private fun TileSwatch(look: TileLook) {
    Canvas(Modifier.size(64.dp, 34.dp)) { drawDomino(Offset.Zero, size, 3, 4, look = look) }
}
