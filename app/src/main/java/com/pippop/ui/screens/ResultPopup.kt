package com.pippop.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import com.pippop.ui.art.GameIcon
import com.pippop.ui.theme.Space
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.compositionLocalOf
import com.pippop.ui.Sound
import com.pippop.ui.rememberSfx
import com.pippop.ui.Buzz
import com.pippop.ui.rememberHaptics
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.textFill
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Radius
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.ResultAnimation
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont

/** A button in the result popup. */
class PopupAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

/**
 * The end of a match, as a sheet over the lower part of the game so the board stays in view:
 * [game]'s own animation for [outcome] above it, then [title], [tonight]'s score, [detail],
 * optional [scores], a [status] line (in green when [statusGood]), what the [next] round will be,
 * [extra], and the buttons: [rematch] (or [again]), [onChange] and [finish]. [games] are others
 * to play next, one tap each. Dragging the handle down (or "See the board") folds it away,
 * leaving a tab that brings it back.
 */
@Composable
fun BoxScope.ResultPopup(
    game: GameKind,
    outcome: Outcome,
    title: String,
    detail: String,
    finish: PopupAction,
    again: PopupAction?,
    scores: List<String> = emptyList(),
    status: String? = null,
    statusGood: Boolean = false,
    tonight: String? = null,
    next: String? = null,
    rematch: (@Composable () -> Unit)? = null,
    onChange: (() -> Unit)? = null,
    games: List<GameKind> = emptyList(),
    onOpenGame: (GameKind) -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val p = AppTheme.palette
    var folded by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val sfx = rememberSfx()
    LaunchedEffect(Unit) {
        haptics.play(if (outcome == Outcome.WIN) Buzz.WIN else Buzz.LOSE)
        sfx.play(when (outcome) { Outcome.WIN -> Sound.WIN; Outcome.LOSE -> Sound.LOSE; else -> Sound.TIE })
    }
    if (folded) {
        Surface(
            onClick = { folded = false },
            color = p.primary,
            shape = Shapes.Top,
            border = BorderStroke(2.dp, p.ink),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                "⌃  $title · show result", color = p.onPrimary, fontWeight = FontWeight.Bold, fontSize = TextSize.Body,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
        return
    }
    val enter = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(Unit) { if (reduce) enter.snapTo(1f) else enter.animateTo(1f, tween(320)) }
    // The dim layer also swallows taps, so the game underneath can't be played by accident.
    Box(
        Modifier.matchParentSize().background(p.scrim.copy(alpha = 0.35f * enter.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    )
    BoxWithConstraints(Modifier.matchParentSize()) {
        val room = maxHeight
        // The game's own little scene, over the board above the sheet.
        ResultAnimation(
            game, outcome,
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp).widthIn(max = 220.dp).fillMaxWidth(0.55f)
                .graphicsLayer { alpha = enter.value },
        )
        Surface(
            color = p.card,
            shape = Shapes.Top,
            border = BorderStroke(3.dp, p.ink),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = room * 0.78f)
                .graphicsLayer { translationY = (1 - enter.value) * 300f },
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The handle: drag it down to look at the board.
                var pulled by remember { mutableStateOf(0f) }
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(onDragStart = { pulled = 0f }, onDragEnd = { if (pulled > 60.dp.toPx()) folded = true }) { _, dy -> pulled += dy }
                        }
                        .clickable { folded = true },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(width = 44.dp, height = 5.dp).clip(Shapes.Full).background(p.hairline)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM, modifier = Modifier.weight(1f))
                    if (tonight != null) {
                        Text(
                            tonight, color = p.text, fontWeight = FontWeight.Bold, fontSize = TextSize.Label,
                            modifier = Modifier.clip(Shapes.Full).background(p.accent).border(1.5.dp, p.ink, Shapes.Full).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (detail.isNotEmpty()) Text(detail, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold)
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (statusGood) Box(Modifier.size(10.dp).clip(CircleShape).background(p.good))
                        Text(status, color = if (statusGood) p.good else p.muted, fontWeight = FontWeight.Bold, fontSize = TextSize.Body)
                    }
                }
                if (next != null) Text("NEXT ROUND: " + next.uppercase(), color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
                extra()
                when {
                    rematch != null -> rematch()
                    again != null -> PrimaryButton(again.label, again.onClick, enabled = again.enabled)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onChange != null) SecondaryButton("Change", onChange, Modifier.weight(1f))
                    SecondaryButton(finish.label, finish.onClick, Modifier.weight(1f), enabled = finish.enabled)
                }
                if (games.isNotEmpty()) {
                    Text("OR PLAY", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
                    GameShelf(games.take(4), onPick = onOpenGame)
                }
            }
        }
    }
}

/** Gives an inline popup its type for GameFrame's `popup`, e.g. `if (over) popupContent { … } else null`. */
fun popupContent(content: @Composable BoxScope.() -> Unit) = content

/** The outcome for this phone from a room's winner ("me", "opponent", or none for a tie). */
fun outcomeOf(winner: String?) = when (winner) {
    "me" -> Outcome.WIN
    "opponent" -> Outcome.LOSE
    else -> Outcome.TIE
}

/** This phone's name in the score since connecting. */
const val TALLY_ME = "You"

/**
 * What a match's result needs from the connection it was played in: the score since
 * connecting ([tally], by name), a couple of other games the players could pick next, and
 * how to open one. Null outside a connection that plays game after game.
 */
class MatchSession(val tally: Map<String, Int>, val nextGames: List<GameKind>, val onOpenGame: (GameKind) -> Unit)

val LocalMatchSession = compositionLocalOf<MatchSession?> { null }

/** "Tonight: You 3 – 2 Alex", or each name and score with three or more; null before anyone has won. */
fun tonightLine(tally: Map<String, Int>, names: List<String>): String? {
    if (tally.values.sum() == 0) return null
    val everyone = listOf(TALLY_ME) + names
    val scores = everyone.map { it to (tally[it] ?: 0) }
    return "Tonight: " + if (scores.size == 2) "${scores[0].first} ${scores[0].second} – ${scores[1].second} ${scores[1].first}"
    else scores.joinToString(" · ") { (n, w) -> "$n $w" }
}

/** The usual result title for a match against your partner. */
fun resultTitle(winner: String?, opponentName: String) = when (winner) {
    "me" -> "You win!"
    "opponent" -> "$opponentName wins"
    else -> "It's a tie"
}

/**
 * The result of a match against your partner (or partners): Rematch with everyone's ready
 * light (and a countdown once they're all ready), Change for the next round's options,
 * back to the games, and other games to play together next.
 */
@Composable
fun BoxScope.MatchPopup(
    room: RoomState,
    game: GameKind,
    outcome: Outcome,
    title: String,
    detail: String,
    onRematch: (() -> Unit)?,
    onFinish: () -> Unit,
    scores: List<String> = emptyList(),
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val present = room.present
    val gone = present.isEmpty()
    val status = when {
        gone -> if (room.others.size == 1) "${room.others[0].name} has left the game." else "Everyone else has left."
        room.endReason == "restart" -> "You both asked for a new game."
        else -> null
    }
    val session = LocalMatchSession.current.takeIf { room.inSession }
    val controls = LocalRoomControls.current
    if (controls != null && controls.setupOpen.value && !gone) SetupSheet(game, controls) { controls.setupOpen.value = false }
    val rematch = onRematch ?: controls?.rematch
    ResultPopup(
        game = game,
        outcome = if (room.endReason == "restart") Outcome.TIE else outcome,
        title = if (room.endReason == "restart") "New game" else title,
        detail = if (room.endReason == "restart") "" else detail,
        finish = PopupAction(if (room.inSession) "Games" else "Done", onFinish),
        again = null,
        scores = scores,
        status = status,
        tonight = session?.let { tonightLine(it.tally, present.map { o -> o.name }) }?.removePrefix("Tonight: ")?.let { "Tonight $it" },
        next = controls?.mode,
        rematch = if (rematch == null || gone) null else ({ RematchButton(room, rematch, enabled = !gone) }),
        onChange = if (controls?.setup == null || gone) null else ({ controls.setupOpen.value = true }),
        games = if (session == null || gone) emptyList() else session.nextGames.filter { it != game },
        onOpenGame = { g -> controls?.switchTo?.invoke(g) ?: session?.onOpenGame?.invoke(g) },
        extra = extra,
    )
}
