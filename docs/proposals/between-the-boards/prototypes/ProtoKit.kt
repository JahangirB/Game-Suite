package com.pippop.ui.proto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.ui.art.AvatarImage
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.KeyArt
import com.pippop.ui.screens.Tone
import com.pippop.ui.screens.burst
import com.pippop.ui.screens.darker
import com.pippop.ui.screens.glossyDisc
import com.pippop.ui.screens.lighter
import com.pippop.ui.screens.pressedEdge
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

// Scratch prototypes for the catalogue: the proposed looks, built from the app's own tokens and
// parts so the pictures are what the app would really draw. Not product code.

/** The key art's own shape (280 × 170), so a tile shows the whole picture. */
const val ART = 280f / 170f

/** A small heading over a group, as on the result sheet ("OR PLAY"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = AppTheme.palette.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, modifier = modifier)
}

/** A row of choices with one filled in: the home's Offline/Online switch, made a part every screen can use. */
@Composable
fun Segmented(
    options: List<String>, selected: Int, modifier: Modifier = Modifier, height: Dp = 40.dp,
    enabled: Boolean = true, fill: Color = AppTheme.palette.primary, onFill: Color = AppTheme.palette.onPrimary,
    off: Set<Int> = emptySet(),
) {
    val p = AppTheme.palette
    Row(modifier.height(height).clip(Shapes.Full).background(p.card).border(2.dp, p.ink, Shapes.Full)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(Modifier.weight(1f).fillMaxHeight().background(if (on) fill else Color.Transparent), contentAlignment = Alignment.Center) {
                Text(
                    label, color = if (on) onFill else p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.alpha(if ((enabled && i !in off) || on) 1f else 0.4f),
                )
            }
        }
    }
}

/** One setting of a game: its name, the choices beside it, and a line saying what the chosen one means. */
@Composable
fun OptionRow(label: String, options: List<String>, selected: Int, note: String? = null, off: Set<Int> = emptySet(), fill: Color = AppTheme.palette.primary, onFill: Color = AppTheme.palette.onPrimary) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
            Segmented(options, selected, Modifier.weight(1f), off = off, fill = fill, onFill = onFill)
        }
        if (note != null) Text(note, color = p.muted, fontSize = TextSize.Label, modifier = Modifier.padding(start = 92.dp))
    }
}

/** A chunky switch: the knob is a little block that sits left (off) or right (on). */
@Composable
fun ToySwitch(on: Boolean, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Box(
        modifier.size(54.dp, 32.dp).clip(Shapes.Full).background(if (on) p.primary else p.accent).border(2.dp, p.ink, Shapes.Full),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 4.dp).size(22.dp).pressedEdge(p.ink, 11.dp, 2.dp).clip(CircleShape).background(p.card).border(2.dp, p.ink, CircleShape))
    }
}

/** A text field in the app's look: its label above it, an ink outline, room for a thumb. */
@Composable
fun ToyField(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val p = AppTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(Shapes.M).background(p.card).border(2.dp, p.ink, Shapes.M).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifEmpty { hint.orEmpty() }, color = if (value.isEmpty()) p.muted else p.text, fontSize = TextSize.BodyL,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

/** A room code as one box per character, the next one to type ringed. */
@Composable
fun CodeBoxes(code: String, length: Int = 4, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(length) { i ->
            val next = i == code.length
            Box(
                Modifier.weight(1f).height(68.dp).pressedEdge(if (next) p.primary else p.ink, Radius.M).clip(Shapes.M)
                    .background(p.card).border(if (next) 3.dp else 2.dp, if (next) p.primary else p.ink, Shapes.M),
                contentAlignment = Alignment.Center,
            ) {
                Text(code.getOrNull(i)?.toString() ?: "", color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Players' pictures, overlapping, each with an outline so they read apart. */
@Composable
fun Faces(avatars: List<Avatar?>, size: Dp = 32.dp, step: Dp = 22.dp, lights: List<Boolean>? = null) {
    val p = AppTheme.palette
    Box(Modifier.width(size + step * (avatars.size - 1)).height(size + if (lights != null) 2.dp else 0.dp)) {
        avatars.forEachIndexed { i, a ->
            Box(Modifier.offset(x = step * i)) {
                Box(Modifier.size(size).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) {
                    if (a != null) AvatarImage(a, Modifier.fillMaxSize()) else Text("?", color = p.text, fontWeight = FontWeight.Bold)
                }
                if (lights != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(11.dp).clip(CircleShape)
                            .background(if (lights[i]) p.good else p.hairline).border(1.5.dp, p.ink, CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * A game on the shelf as its picture: the key art with the name under it. [tag] sits on the
 * picture's corner ("Yesterday"), [seats] says how many can play, [dim] greys it out with a
 * reason, and [face] is someone waiting there.
 */
@Composable
fun ArtTile(
    kind: GameKind, modifier: Modifier = Modifier, tag: String? = null, seats: String? = null,
    dim: String? = null, face: Avatar? = null, label: String = kind.label, ring: Color? = null,
) {
    val p = AppTheme.palette
    val edge = ring ?: p.ink
    Box(modifier) {
        Surface(
            color = p.card, shape = Shapes.M, border = BorderStroke(if (ring != null) 3.dp else 2.dp, edge),
            modifier = Modifier.fillMaxWidth().pressedEdge(edge, Radius.M),
        ) {
            Column(Modifier.alpha(if (dim != null) 0.42f else 1f)) {
                Box(Modifier.fillMaxWidth().aspectRatio(ART)) {
                    KeyArt(kind, Modifier.fillMaxSize())
                    if (tag != null) {
                        Text(
                            tag, color = p.onPrimary, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(Shapes.Full).background(p.primary).padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                    if (face != null) {
                        Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(p.good))
                            Box(Modifier.padding(3.dp).size(26.dp).clip(CircleShape).background(p.accent).border(1.5.dp, Board.Ink, CircleShape)) { AvatarImage(face, Modifier.fillMaxSize()) }
                        }
                    }
                    if (seats != null) {
                        Row(
                            Modifier.align(Alignment.TopEnd).padding(5.dp).clip(Shapes.Full).background(Color.White).border(1.5.dp, Board.Ink, Shapes.Full).padding(start = 3.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Person, null, tint = Board.Ink, modifier = Modifier.size(12.dp))
                            Text(seats, style = Digits, color = Board.Ink, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(edge))
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        label, color = p.text, fontSize = TextSize.Body, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                        lineHeight = LineHeight.Label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (dim != null) {
            Text(
                dim, color = p.text, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp).clip(Shapes.Full).background(p.card).border(1.5.dp, p.ink, Shapes.Full).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** A round button with a drawn icon, like the ones in a game's dock, for a header. */
@Composable
fun RoundButton(size: Dp = 40.dp, content: @Composable BoxScope.() -> Unit) {
    val p = AppTheme.palette
    Box(
        Modifier.size(size).pressedEdge(p.ink, size / 2, 2.dp).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape),
        contentAlignment = Alignment.Center, content = content,
    )
}

/** A sheet over the screen, as the result has it: a dim layer, an ink edge on top, a handle. */
@Composable
fun BoxScope.SheetOver(scrim: Float = 0.35f, content: @Composable ColumnScope.() -> Unit) {
    val p = AppTheme.palette
    Box(Modifier.matchParentSize().background(p.scrim.copy(alpha = scrim)))
    Surface(
        color = p.card, shape = Shapes.Top, border = BorderStroke(3.dp, p.ink),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 44.dp, height = 5.dp).clip(Shapes.Full).background(p.hairline))
            }
            content()
        }
    }
}

/** A question in the middle of the screen, in the app's look: a card with an ink edge and real buttons. */
@Composable
fun BoxScope.DialogOver(title: String, body: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = AppTheme.palette
    Box(Modifier.matchParentSize().background(p.scrim.copy(alpha = 0.45f)))
    Surface(
        color = p.card, shape = Shapes.L, border = BorderStroke(2.dp, p.ink),
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp).pressedEdge(p.ink, Radius.L, 4.dp),
    ) {
        Column(Modifier.padding(Space.XL), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            Text(title, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
            if (body != null) Text(body, color = p.muted, fontSize = TextSize.BodyL)
            content()
        }
    }
}

/** A short message that slides in and leaves by itself: its tone's mark in a coloured disc, then the words. */
@Composable
fun Toast(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val (color, mark) = when (tone) {
        Tone.Good -> p.good to "✓"
        Tone.Warning -> p.warning to "!"
        Tone.Bad -> p.bad to "✕"
        Tone.Neutral -> p.muted to "i"
    }
    Surface(color = p.card, shape = Shapes.Full, border = BorderStroke(2.dp, p.ink), modifier = modifier.pressedEdge(p.ink, 28.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(mark, color = Color.White, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            }
            Text(text, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Two beads taking turns to hop, Pip and Pop: the app's own "one moment". [t] 0 to 1 is where the loop is. */
@Composable
fun PipPopLoader(modifier: Modifier = Modifier, t: Float = 0.3f) {
    val p = AppTheme.palette
    Canvas(modifier.size(64.dp, 40.dp)) {
        val r = size.height * 0.26f
        val ground = size.height - r
        val hop = size.height * 0.5f
        val a = kotlin.math.sin(t * Math.PI).toFloat().coerceAtLeast(0f)
        val b = kotlin.math.sin(((t + 0.5f) % 1f) * Math.PI).toFloat().coerceAtLeast(0f)
        for ((x, k, c) in listOf(Triple(size.width * 0.3f, a, p.primary), Triple(size.width * 0.7f, b, Board.Glow))) {
            drawOval(Color.Black.copy(alpha = 0.14f * (1f - k * 0.6f)), Offset(x - r * (1f - k * 0.3f), size.height - r * 0.28f), Size(2 * r * (1f - k * 0.3f), r * 0.4f))
            val c0 = Offset(x, ground - hop * k)
            glossyDisc(c, r, c0)
            drawCircle(Board.Ink, r, c0, style = Stroke(1.6.dp.toPx()))
        }
    }
}

/** A little gold crown for whoever won. */
@Composable
fun Crown(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val shape = Path().apply {
            moveTo(w * 0.06f, h * 0.9f)
            lineTo(w * 0.02f, h * 0.3f)
            lineTo(w * 0.28f, h * 0.56f)
            lineTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.72f, h * 0.56f)
            lineTo(w * 0.98f, h * 0.3f)
            lineTo(w * 0.94f, h * 0.9f)
            close()
        }
        drawPath(shape, Board.Glow)
        drawPath(shape, Board.Ink, style = Stroke(2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
        for (x in listOf(0.02f, 0.5f, 0.98f)) drawCircle(Board.Ink, 2.2.dp.toPx(), Offset(w * x, h * (if (x == 0.5f) 0.08f else 0.3f)))
    }
}

/** A picture with rays behind it and, for the winner, a crown on top. */
@Composable
fun WinnerFace(avatar: Avatar?, size: Dp, color: Color, crowned: Boolean = true, rays: Boolean = true) {
    val p = AppTheme.palette
    Box(Modifier.size(size + 20.dp, size + 22.dp), contentAlignment = Alignment.BottomCenter) {
        if (rays) Canvas(Modifier.size(size + 20.dp).align(Alignment.BottomCenter)) { burst(center, 0.42f, color, size.toPx() * 0.72f, rays = 14, width = 3.dp.toPx()) }
        Box(Modifier.padding(bottom = 8.dp).size(size).clip(CircleShape).background(p.accent).border(3.dp, p.ink, CircleShape)) {
            if (avatar != null) AvatarImage(avatar, Modifier.fillMaxSize())
        }
        if (crowned) Crown(Modifier.align(Alignment.TopCenter).size(size * 0.55f, size * 0.36f))
    }
}

/** A cup for a month: gold, with handles, on a wooden foot, the winner's picture on its front. */
@Composable
fun TrophyCup(modifier: Modifier = Modifier, face: Avatar? = null, tie: Boolean = false) {
    val p = AppTheme.palette
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gold = if (tie) Color(0xFFC9CED6) else Board.Glow
            val line = Stroke(maxOf(1.5.dp.toPx(), w * 0.022f), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            val grip = w * 0.075f
            // Handles: a thick gold loop each side, inked round.
            for ((x, start) in listOf(w * 0.03f to 95f, w * 0.63f to -85f)) {
                drawArc(Board.Ink, start, 170f, false, Offset(x, h * 0.1f), Size(w * 0.34f, h * 0.32f), style = Stroke(grip + line.width * 2, cap = StrokeCap.Round))
                drawArc(gold, start, 170f, false, Offset(x, h * 0.1f), Size(w * 0.34f, h * 0.32f), style = Stroke(grip, cap = StrokeCap.Round))
            }
            val bowl = Path().apply {
                moveTo(w * 0.19f, h * 0.05f)
                lineTo(w * 0.81f, h * 0.05f)
                lineTo(w * 0.77f, h * 0.38f)
                quadraticTo(w * 0.72f, h * 0.6f, w * 0.5f, h * 0.62f)
                quadraticTo(w * 0.28f, h * 0.6f, w * 0.23f, h * 0.38f)
                close()
            }
            drawPath(bowl, gold)
            // A band of light down the left of the bowl.
            drawPath(Path().apply { moveTo(w * 0.25f, h * 0.09f); lineTo(w * 0.31f, h * 0.09f); lineTo(w * 0.34f, h * 0.44f); lineTo(w * 0.29f, h * 0.4f); close() }, Color.White.copy(alpha = 0.45f))
            drawPath(bowl, Board.Ink, style = line)
            // Stem, a gold collar, then the wooden foot.
            drawRect(gold.darker(0.14f), Offset(w * 0.455f, h * 0.62f), Size(w * 0.09f, h * 0.12f))
            drawRect(Board.Ink, Offset(w * 0.455f, h * 0.62f), Size(w * 0.09f, h * 0.12f), style = line)
            drawRoundRect(gold, Offset(w * 0.36f, h * 0.72f), Size(w * 0.28f, h * 0.06f), CornerRadius(h * 0.03f))
            drawRoundRect(Board.Ink, Offset(w * 0.36f, h * 0.72f), Size(w * 0.28f, h * 0.06f), CornerRadius(h * 0.03f), style = line)
            drawRoundRect(Board.Wood, Offset(w * 0.27f, h * 0.78f), Size(w * 0.46f, h * 0.18f), CornerRadius(h * 0.03f))
            drawRoundRect(Board.Wood.lighter(0.25f), Offset(w * 0.33f, h * 0.83f), Size(w * 0.34f, h * 0.08f), CornerRadius(h * 0.02f))
            drawRoundRect(Board.Ink, Offset(w * 0.27f, h * 0.78f), Size(w * 0.46f, h * 0.18f), CornerRadius(h * 0.03f), style = line)
        }
        if (face != null) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.6f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(0.4f).aspectRatio(1f).clip(CircleShape).background(p.card).border(1.5.dp, Board.Ink, CircleShape)) { AvatarImage(face, Modifier.fillMaxSize()) }
            }
        }
    }
}

/** A game's icon on its colour, small: for lists. */
@Composable
fun GameBadge(kind: GameKind, size: Dp = 32.dp, color: Color) {
    val p = AppTheme.palette
    Box(
        Modifier.size(size).clip(Shapes.S).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.lighter(0.18f), color.darker(0.12f)))).border(2.dp, p.ink, Shapes.S),
        contentAlignment = Alignment.Center,
    ) { GameIcon(kind, Modifier.size(size * 0.62f)) }
}

/** A thin shelf under a row, as on the home. */
@Composable
fun Shelf(content: @Composable RowScope.() -> Unit) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(), content = content)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Shapes.Full).background(p.ink.copy(alpha = 0.18f)))
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** Centred small print under something. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, color: Color = AppTheme.palette.muted) {
    Text(text, color = color, fontSize = TextSize.Label, textAlign = align, modifier = modifier)
}

@Suppress("unused")
private val keep = RoundedCornerShape(0.dp)
