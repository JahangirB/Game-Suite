package com.pippop.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.ResultAnimation
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize

/**
 * True while [GameFrame] draws a result under the game (the card) instead of over it (the
 * sheet). [MatchPopup] and [ResultPopup] read it, so a screen only says `resultUnder = true`.
 */
val LocalResultUnder = androidx.compose.runtime.compositionLocalOf { false }

/** Whose digit in tonight's score went up for this game: the one the app counted ([BattleViewModel]'s tally), else nobody. */
enum class TallyBump { NONE, MINE, THEIRS }

/**
 * The side to bump, from what the tally counts: you when [RoomState.winner] is "me"; them when it is
 * "opponent" with a named winner and not Sudoku together (where a loss is everyone's); else none
 * (a tie, Draw & Guess's team game, running out of lives).
 */
fun tallyBump(room: com.pippop.data.RoomState) = when {
    room.winner == "me" -> TallyBump.MINE
    room.winner == "opponent" && !room.together && room.winnerName != null -> TallyBump.THEIRS
    else -> TallyBump.NONE
}

/** Whether the card offers Change: whoever sets the options, or a Sudoku room of an older server (each picks their own level there). */
fun cardOffersChange(room: com.pippop.data.RoomState) =
    room.setsOptions || (room.game == GameKind.SUDOKU && !room.picksByOpener)

/** Who won, for the card's face: [avatar] (or an initial when null) in [color]. */
class ResultWinner(val name: String, val avatar: Avatar?, val color: Color)

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
        drawPath(shape, Board.Ink, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        for (x in listOf(0.02f, 0.5f, 0.98f)) drawCircle(Board.Ink, 2.2.dp.toPx(), Offset(w * x, h * (if (x == 0.5f) 0.08f else 0.3f)))
    }
}

/** A picture with rays behind it in [color] and, for the winner, a crown on top. */
@Composable
fun WinnerFace(winner: ResultWinner, size: Dp, crowned: Boolean = true) {
    val p = AppTheme.palette
    Box(Modifier.size(size + 20.dp, size + 22.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.size(size + 20.dp).align(Alignment.BottomCenter)) {
            burst(center, 0.42f, winner.color, size.toPx() * 0.72f, rays = 14, width = 3.dp.toPx())
        }
        Box(Modifier.padding(bottom = 8.dp).size(size).clip(CircleShape).background(p.accent).border(3.dp, p.ink, CircleShape)) {
            PlayerAvatar(winner.name, winner.color, size = size.value.toInt(), avatar = winner.avatar)
        }
        if (crowned) Crown(Modifier.align(Alignment.TopCenter).size(size * 0.55f, size * 0.36f))
    }
}

/** The game's own icon on its colour: the card's picture when nobody won. */
@Composable
private fun GameFace(game: GameKind, size: Dp) {
    val p = AppTheme.palette
    Box(Modifier.size(size + 20.dp, size + 22.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.padding(bottom = 8.dp).size(size).clip(Shapes.M).background(game.color).border(3.dp, p.ink, Shapes.M),
            contentAlignment = Alignment.Center,
        ) { GameIcon(game, Modifier.size(size * 0.62f)) }
    }
}

/** A digit that rolls up from [from] to [to] (just shows [to] when they match or motion is reduced). */
@Composable
private fun RollingDigit(from: Int, to: Int, color: Color) {
    val reduce = LocalReduceMotion.current
    val still = reduce || from == to
    val t = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(from, to, still) {
        if (still) t.snapTo(1f) else {
            t.snapTo(0f)
            t.animateTo(1f, tween(Motion.MOVE_MS, delayMillis = Motion.STAGGER_MS))
        }
    }
    @Composable
    fun digit(n: Int, modifier: Modifier) = Text(
        "$n", style = Digits, color = color, fontSize = TextSize.DisplayM, fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM, modifier = modifier,
    )
    Box(Modifier.clipToBounds()) {
        digit(to, Modifier.graphicsLayer { translationY = size.height * (1f - t.value) })
        if (t.value < 1f) digit(from, Modifier.graphicsLayer { translationY = -size.height * t.value })
    }
}

/**
 * Tonight's score under the result, both faces and big digits. The winner's digit rolls up by
 * one with a "+1" ([score] already counts this game). A tie bumps nothing.
 */
@Composable
fun TallyRow(score: TonightScore, bump: TallyBump) {
    val p = AppTheme.palette
    val bumpMine = bump == TallyBump.MINE && score.mine >= 1
    val bumpTheirs = bump == TallyBump.THEIRS && score.theirs >= 1
    Surface(
        color = p.accent, shape = Shapes.M, modifier = Modifier.fillMaxWidth()
            .clearAndSetSemantics { contentDescription = score.description },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Text("TONIGHT", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold) }
            PlayerAvatar("You", p.primary, size = 28, avatar = score.myAvatar)
            Spacer(Modifier.width(8.dp))
            RollingDigit(if (bumpMine) score.mine - 1 else score.mine, score.mine, if (bumpMine) p.primaryText else p.text)
            Text("  –  ", color = p.muted, fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
            RollingDigit(if (bumpTheirs) score.theirs - 1 else score.theirs, score.theirs, if (bumpTheirs) p.opponentText else p.text)
            Spacer(Modifier.width(8.dp))
            PlayerAvatar(score.otherName, OpponentColor, size = 28, avatar = score.otherAvatar)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (bumpMine || bumpTheirs) {
                    val fill = if (bumpMine) p.primary else OpponentColor
                    Text(
                        "+1", color = com.pippop.ui.theme.readableOn(fill), fontSize = TextSize.Label, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(Shapes.Full).background(fill).border(1.5.dp, p.ink, Shapes.Full).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * The result as a card under the board: the winner's face (crowned, rays in their colour), the
 * [title] and its [detail], the game's scene small at the right; tonight's [tally] (two players);
 * what the sheet also held ([scores], [status], [next], [extra]); then [rematch] (or [again]), Change and the way on.
 * [onAnother] opens the "Another game" sheet;; "Back to games" ([finish]) is then at the bottom of that sheet.
 */
@Composable
internal fun ResultCard(
    game: GameKind,
    outcome: Outcome,
    title: String,
    detail: String,
    winner: ResultWinner?,
    tally: TonightScore?,
    bump: TallyBump,
    finish: PopupAction,
    again: PopupAction?,
    scores: List<String>,
    status: String?,
    statusGood: Boolean,
    next: String?,
    rematch: (@Composable () -> Unit)?,
    onChange: (() -> Unit)?,
    onAnother: (() -> Unit)?,
    extra: @Composable ColumnScope.() -> Unit,
    /** What the gold line says when this result is a record ([RecordLines]); null for no line. */
    record: String? = null,
) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(3.dp, p.ink), modifier = Modifier.fillMaxWidth().testTag("resultCard").pressedEdge(p.ink, Radius.L, 4.dp)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // What is said: read out once when the card arrives.
            Row(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (winner != null) WinnerFace(winner, 50.dp) else GameFace(game, 50.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                        lineHeight = LineHeight.DisplayM, modifier = Modifier.semantics { heading() },
                    )
                    // A record takes the detail line's own place (it says more, and adds no height): a compact gold pill.
                    if (record != null) RecordLine(record)
                    else if (detail.isNotEmpty()) Text(detail, color = p.muted, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold, lineHeight = LineHeight.Body)
                }
                Spacer(Modifier.width(8.dp))
                ResultAnimation(game, outcome, Modifier.width(84.dp))
            }
            if (tally != null) TallyRow(tally, bump)
            if (scores.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scores.forEach {
                        Text(
                            it, color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Label,
                            modifier = Modifier.clip(Shapes.Full).background(p.background).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            if (status != null) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (statusGood) Box(Modifier.size(10.dp).clip(CircleShape).background(p.good))
                    Text(status, color = if (statusGood) p.good else p.muted, fontWeight = FontWeight.Bold, fontSize = TextSize.Body)
                }
            }
            if (next != null) Text("NEXT ROUND: " + next.uppercase(), color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
            extra()
            when {
                rematch != null -> rematch()
                again != null -> PrimaryButton(again.label, again.onClick, Modifier.fillMaxWidth(), enabled = again.enabled)
            }
            val label = cardFinishLabel(finish.label)
            // With the sheet to open, "Back to games" lives at the bottom of the sheet.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val sheet = label == ANOTHER_GAME && onAnother != null
                // "Another game" is the longer label: with Change beside it, it gets the wider share so it stays on one line.
                if (onChange != null) SecondaryButton("Change", onChange, Modifier.weight(1f))
                if (sheet) SecondaryButton(label, onAnother!!, Modifier.weight(if (onChange != null) 1.6f else 1f))
                else SecondaryButton(label, finish.onClick, Modifier.weight(1f), enabled = finish.enabled)
            }
        }
    }
}

private const val ANOTHER_GAME = "Another game"

/** The sheet's closing button's words, in the card's: "Games" is "Another game", "Home" is "Back to games". */
internal fun cardFinishLabel(label: String) = when (label) {
    "Games" -> ANOTHER_GAME
    "Home" -> "Back to games"
    else -> label
}
