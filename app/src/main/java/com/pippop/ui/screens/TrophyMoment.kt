package com.pippop.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pippop.data.Avatar
import com.pippop.data.MatchRecord
import com.pippop.data.MonthEnd
import com.pippop.ui.art.TrophyCup
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import java.time.YearMonth
import java.time.format.TextStyle as MonthStyle
import java.util.Locale

/** "October" in the phone's language. */
private fun monthName(month: YearMonth): String = month.month.getDisplayName(MonthStyle.FULL, Locale.getDefault())

/** "Maya takes October", "You take October" or "October is a tie". */
internal fun trophyTitle(end: MonthEnd): String {
    val month = monthName(end.month)
    return when (end.winner) {
        null -> "$month is a tie"
        MatchRecord.ME -> "You take $month"
        else -> "${end.winner} takes $month"
    }
}

/**
 * One honest line about the month, from its games with the partner: how many, and the game played
 * most (the one with most results; on a tie of counts, the one played last).
 */
internal fun trophyLine(end: MonthEnd): String = when {
    end.games == 1 -> "One game together."
    end.mostPlayed == null -> "${end.games} games together."
    else -> "${end.games} games together. You played ${end.mostPlayed.label} the most."
}

/** "November starts level." is said only when the month after the cup's is the month now (else it may already have games). */
internal fun startsLevel(end: MonthEnd, current: YearMonth) = end.month.plusMonths(1) == current

/** Confetti shows only while motion is not reduced. */
internal fun showsConfetti(reduceMotion: Boolean) = !reduceMotion

/**
 * The first time the app opens after a month with games has ended: a full-screen moment over
 * whatever is behind it. [onDone] runs from "Onto the shelf" and from Back.
 */
@Composable
fun TrophyMomentDialog(end: MonthEnd, current: YearMonth, myAvatar: Avatar?, partnerAvatar: Avatar?, onDone: () -> Unit) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = onDone)
        Surface(color = AppTheme.palette.background, modifier = Modifier.fillMaxSize()) {
            TrophyMoment(end, current, myAvatar, partnerAvatar, onDone, Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
        }
    }
}

/** The moment without its window, so a picture can show it. */
@Composable
internal fun TrophyMoment(end: MonthEnd, current: YearMonth, myAvatar: Avatar?, partnerAvatar: Avatar?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val mineWon = end.winner == MatchRecord.ME
    val tie = end.winner == null
    val partnerName = end.partner
    val cupFace = if (mineWon) myAvatar else partnerAvatar.takeIf { !tie }
    Box(modifier.fillMaxSize()) {
        if (showsConfetti(LocalReduceMotion.current)) Confetti(Modifier.fillMaxWidth().height(420.dp), count = 60)
        Column(Modifier.fillMaxSize().padding(horizontal = Space.XXL, vertical = Space.XL), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.M),
            ) {
                Spacer(Modifier.height(60.dp))
                Text("${monthName(end.month)} is over".uppercase(), color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
                TrophyCup(
                    Modifier.size(170.dp, 190.dp).clearAndSetSemantics { }, face = cupFace, tie = tie,
                    initial = if (tie) null else if (mineWon) "Y" else partnerName.trim().firstOrNull()?.uppercase() ?: "?",
                    initialFill = if (mineWon) p.primary else OpponentColor,
                )
                Text(
                    trophyTitle(end), modifier = Modifier.semantics { heading() }, color = p.text, fontSize = TextSize.DisplayL,
                    fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Row(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = "You ${end.mine}, $partnerName ${end.theirs}" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Face("You", myAvatar, p.primary, 40.dp, dim = !tie && !mineWon)
                    Text(
                        "${end.mine} – ${end.theirs}", style = Digits, color = p.text, fontSize = TextSize.Score,
                        fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                    )
                    Face(partnerName, partnerAvatar, OpponentColor, 40.dp, dim = !tie && mineWon)
                }
                Text(trophyLine(end), color = p.muted, fontSize = TextSize.BodyL, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Space.M))
            if (startsLevel(end, current)) {
                Text("${monthName(current)} starts level.", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Space.M))
            }
            PrimaryButton("Onto the shelf", onDone, Modifier.fillMaxWidth())
        }
    }
}

/** Scraps of coloured paper that drift down into place once, and stay. Not drawn when motion is reduced (see [showsConfetti]). */
@Composable
internal fun Confetti(modifier: Modifier = Modifier, count: Int = 46) {
    val colors = listOf(Pieces.Red, Pieces.Yellow, Pieces.Green, Pieces.Blue, Pieces.Purple, Pieces.Pink)
    val fall = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fall.animateTo(1f, tween(Motion.MOVE_MS * 2, easing = FastOutSlowInEasing)) }
    Canvas(modifier.graphicsLayer { alpha = fall.value; translationY = -size.height * 0.12f * (1f - fall.value) }) {
        var seed = 7L
        fun next(): Float {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return (seed % 10000L) / 10000f
        }
        repeat(count) { i ->
            val x = next() * size.width
            val y = next() * size.height
            val w = 5.dp.toPx() + next() * 5.dp.toPx()
            rotate(next() * 180f, Offset(x, y)) { drawRect(colors[i % colors.size], Offset(x - w / 2, y - w / 4), Size(w, w / 2)) }
        }
    }
}
