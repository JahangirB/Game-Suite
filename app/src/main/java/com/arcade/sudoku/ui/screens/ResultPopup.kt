package com.arcade.sudoku.ui.screens

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
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.art.ResultAnimation
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont

/** A button in the result popup. */
class PopupAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

/**
 * The end of a match, over the game (see GameFrame's popup): [game]'s own animation for
 * [outcome], [title], [detail], optional [scores], a [status] line (your partner's rematch
 * wish, in green when [statusGood]) and the buttons. [extra] goes above the buttons (the
 * Sudoku levels, the next Ship Battle fleet). "See the board" folds it away to look at the
 * final position, leaving a button to bring it back.
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
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val p = AppTheme.palette
    var folded by remember { mutableStateOf(false) }
    if (folded) {
        Surface(
            onClick = { folded = false },
            color = p.primary,
            shape = RoundedCornerShape(50),
            border = BorderStroke(2.dp, p.ink),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).pressedEdge(p.ink, 24.dp),
        ) {
            Text(
                "Show result", color = p.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
        return
    }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320)) }
    // The dim layer also swallows taps, so the game underneath can't be played by accident.
    Box(
        Modifier.matchParentSize().background(p.ink.copy(alpha = 0.55f * enter.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    )
    Surface(
        color = p.card,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(3.dp, p.ink),
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .widthIn(max = 440.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1 - enter.value) * 60f
                scaleX = 0.92f + 0.08f * enter.value
                scaleY = scaleX
            }
            .pressedEdge(p.ink, 26.dp, 5.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                game.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(game.color).padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Text(title, color = p.text, fontSize = 34.sp, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = 36.sp, textAlign = TextAlign.Center)
            ResultAnimation(game, outcome)
            if (detail.isNotEmpty()) Text(detail, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (scores.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scores.forEach {
                        Text(
                            it, color = p.text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(p.background).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (statusGood) Box(Modifier.size(10.dp).clip(CircleShape).background(p.good))
                    Text(status, color = if (statusGood) p.good else p.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            extra()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(finish.label, finish.onClick, Modifier.weight(1f), enabled = finish.enabled)
                if (again != null) PrimaryButton(again.label, again.onClick, Modifier.weight(1.4f), enabled = again.enabled)
            }
            TextButton(onClick = { folded = true }) { Text("See the board", color = p.muted, fontWeight = FontWeight.Bold) }
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

/** The usual result title for a match against your partner. */
fun resultTitle(winner: String?, opponentName: String) = when (winner) {
    "me" -> "You win!"
    "opponent" -> "$opponentName wins"
    else -> "It's a tie"
}

/**
 * The result popup of a match against your partner (or partners): Rematch marks you ready
 * (or takes it back), Finish leaves, and the status line says who wants another round.
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
    // Everyone else still in the room, and who of them wants another round.
    val present = room.present
    val gone = present.isEmpty()
    val keen = present.filter { it.ready }
    val (status, good) = when {
        gone -> (if (room.others.size == 1) "${room.others[0].name} has left the game." else "Everyone else has left.") to false
        room.me.ready && keen.size == present.size -> "Starting…" to true
        room.me.ready -> (if (present.size == 1) "Waiting for ${present[0].name}…" else "Waiting for ${present.size - keen.size} more…") to false
        keen.isNotEmpty() -> (keen.joinToString(" and ") { it.name } + if (keen.size == 1) " wants a rematch" else " want a rematch") to true
        else -> null to false
    }
    ResultPopup(
        game = game,
        outcome = outcome,
        title = title,
        detail = detail,
        finish = PopupAction("Finish", onFinish),
        again = onRematch?.let { PopupAction(if (room.me.ready) "Not ready" else "Rematch", it, enabled = !gone) },
        scores = scores,
        status = status,
        statusGood = good,
        extra = extra,
    )
}
