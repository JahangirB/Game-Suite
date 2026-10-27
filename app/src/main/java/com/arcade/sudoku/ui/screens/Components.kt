package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.Sound
import com.arcade.sudoku.ui.rememberSfx
import androidx.compose.runtime.DisposableEffect
import com.arcade.sudoku.ui.Buzz
import com.arcade.sudoku.ui.rememberHaptics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontFamily
import com.arcade.sudoku.ui.theme.Digits
import com.arcade.sudoku.ui.theme.Mark
import com.arcade.sudoku.ui.theme.MarkIcon
import com.arcade.sudoku.ui.theme.Board
import com.arcade.sudoku.ui.theme.contrast
import com.arcade.sudoku.ui.theme.Space
import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.readableOn
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Radius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.ui.ShownReaction
import com.arcade.sudoku.ui.art.AvatarImage
import com.arcade.sudoku.ui.art.ReactionImage
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.AppTheme

/**
 * The Toy Box "pressed edge": a strip of [color] peeking out under a card or button, like the
 * thick base of a toy block. Draw it before the element's own background.
 */
fun Modifier.pressedEdge(color: Color, radius: Dp, depth: Dp = 3.dp) = drawBehind {
    drawRoundRect(color, topLeft = Offset(0f, depth.toPx()), size = size, cornerRadius = CornerRadius(radius.toPx()))
}

/** A button that sinks onto its edge while pressed. */
@Composable
private fun ToyButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    content: Color,
    body: @Composable () -> Unit,
) {
    val p = AppTheme.palette
    val sfx = rememberSfx()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val shape = Shapes.L
    Box(modifier.fillMaxWidth().height(54.dp).pressedEdge(p.ink.copy(alpha = if (enabled) 1f else 0.3f), Radius.L)) {
        Button(
            onClick = {
                sfx.play(Sound.TAP)
                onClick()
            },
            enabled = enabled,
            shape = shape,
            interactionSource = press,
            border = BorderStroke(2.dp, p.ink.copy(alpha = if (enabled) 1f else 0.3f)),
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container.copy(alpha = 0.45f),
                disabledContentColor = content.copy(alpha = 0.8f),
            ),
            elevation = null,
            modifier = Modifier.fillMaxSize().offset(y = if (pressed) 3.dp else 0.dp),
        ) { body() }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = AppTheme.palette.primary,
    content: Color = AppTheme.palette.onPrimary,
) {
    ToyButton(onClick, modifier, enabled && !loading, container, content) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = content)
        } else {
            Text(text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = AppTheme.palette
    ToyButton(onClick, modifier, enabled, p.card, p.text) {
        Text(text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
    }
}

/**
 * A card holding information; [title] in the rounded heading font. It is flat, with a thin
 * border, so the chunky outline and pressed edge stay for things you press. [raised] gives
 * it the chunky look anyway, for a card that is itself a button.
 */
@Composable
fun AppCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    content: @Composable () -> Unit,
) {
    val p = AppTheme.palette
    Surface(
        color = p.card,
        shape = Shapes.L,
        border = if (raised) BorderStroke(2.dp, p.ink) else BorderStroke(1.dp, p.hairline),
        modifier = modifier.fillMaxWidth().then(if (raised) Modifier.pressedEdge(p.ink, Radius.L) else Modifier),
    ) {
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            if (title != null) Text(title, color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** How a [StatusLine] reads: each tone has its own colour and its own mark. */
enum class Tone { Neutral, Good, Warning, Bad }

/** One line of status ("Starting…", "Levels don't match"), marked so it doesn't rely on colour. */
@Composable
fun StatusLine(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val (color, mark) = when (tone) {
        Tone.Neutral -> p.muted to null
        Tone.Good -> p.good to "✓"
        Tone.Warning -> p.warning to "!"
        Tone.Bad -> p.bad to "✕"
    }
    Text(
        if (mark == null) text else "$mark  $text",
        color = color,
        fontSize = TextSize.Body,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A choice among a few [options] (value to label): the chosen one is the main button, the
 * rest secondary. Without [enabled] nothing can be picked.
 */
@Composable
fun <T> OptionGroup(options: List<Pair<T, String>>, selected: T, enabled: Boolean, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
        options.forEach { (value, label) ->
            if (value == selected) PrimaryButton(label, {}, Modifier.fillMaxWidth(), enabled = enabled)
            else SecondaryButton(label, { onPick(value) }, Modifier.fillMaxWidth(), enabled = enabled)
        }
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val p = AppTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = p.text)
        }
        Text(title, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * A round badge with the player's chosen picture, or their first letter if they have none.
 * [mark] adds their seat's shape in a small badge, so players aren't told apart by colour alone.
 */
@Composable
fun PlayerAvatar(name: String, color: Color, size: Int = 40, avatar: Avatar? = null, mark: Mark? = null) {
    Box(Modifier.size(size.dp)) {
        if (avatar != null) {
            AvatarImage(avatar, Modifier.size(size.dp))
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(size.dp).clip(CircleShape).background(color),
            ) {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = readableOn(color),
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * 0.45f).sp,
                )
            }
        }
        if (mark != null) {
            val badge = (size * 0.42f).dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(badge)
                    .clip(CircleShape).background(Color.White).border(1.5.dp, Board.Ink, CircleShape),
            ) { MarkIcon(mark, Modifier.size(badge * 0.55f), color = color.takeIf { contrast(it, Color.White) >= 3f } ?: Board.Ink) }
        }
    }
}

/**
 * A player's progress bar. With [showAvatar] it starts with their picture; a [reaction]
 * they just sent pops up at the end.
 */
@Composable
fun ProgressRow(
    label: String,
    percent: Int,
    color: Color,
    trailing: String = "$percent%",
    showAvatar: Boolean = false,
    avatar: Avatar? = null,
    reaction: ShownReaction? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (showAvatar) {
            PlayerAvatar(label, color, size = 34, avatar = avatar)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) { ProgressBar(label, percent, color, trailing) }
        if (reaction != null) {
            ReactionImage(reaction.reaction, 34.dp, Modifier.padding(start = 8.dp), startedAt = reaction.at)
        }
    }
}

@Composable
private fun ProgressBar(label: String, percent: Int, color: Color, trailing: String) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = TextSize.Body, modifier = Modifier.weight(1f))
            Text(trailing, color = color, fontWeight = FontWeight.Bold, fontSize = TextSize.Body)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            color = color,
            trackColor = p.accent,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(Shapes.XS),
        )
    }
}

@Composable
fun CenteredMessage(text: String, color: Color = AppTheme.palette.muted) {
    Text(text, color = color, textAlign = TextAlign.Center, fontSize = TextSize.Body, modifier = Modifier.fillMaxWidth())
}

/** One-line description of how demanding each level is. */
fun Difficulty.tagline() = when (this) {
    Difficulty.BEGINNER -> "A gentle start"
    Difficulty.EASY -> "Relaxed and fun"
    Difficulty.MEDIUM -> "Needs some thinking"
    Difficulty.HARD -> "A real challenge"
    Difficulty.EXPERT -> "For experienced players"
    Difficulty.MASTER -> "Fewest clues, no mercy"
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

/** A countdown's big number; the phone ticks as each one shows. */
@Composable
fun CountdownText(
    seconds: Number,
    color: Color,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = FontWeight.Bold,
    textAlign: TextAlign? = null,
) {
    val haptics = rememberHaptics()
    val sfx = rememberSfx()
    val n = seconds.toInt()
    LaunchedEffect(n) {
        haptics.play(Buzz.COUNTDOWN)
        sfx.play(Sound.COUNTDOWN_TICK)
    }
    // The number goes when the race starts.
    DisposableEffect(Unit) { onDispose { sfx.play(Sound.COUNTDOWN_GO) } }
    Text(style = Digits, text = n.toString(), color = color, fontSize = TextSize.Countdown, fontFamily = fontFamily, fontWeight = fontWeight, textAlign = textAlign, modifier = modifier)
}
