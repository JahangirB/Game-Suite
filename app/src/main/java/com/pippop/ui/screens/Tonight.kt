package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.RoomState
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.SeatColors
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize

/**
 * Tonight's score in a game against exactly one other player: both faces and the wins since
 * connecting. [GameFrame] reads it from [LocalTonight], so no game screen passes it.
 */
class TonightScore(
    val myAvatar: Avatar?,
    val otherName: String,
    val otherAvatar: Avatar?,
    val mine: Int,
    val theirs: Int,
) {
    /** What a screen reader says: nothing here is told by colour alone. */
    val description get() = "Tonight: you $mine, $otherName $theirs"
}

/** Null (no pill) unless exactly one other player is in the room. */
fun tonightScore(room: RoomState, tally: Map<String, Int>): TonightScore? {
    val other = room.others.singleOrNull()?.takeIf { !it.left } ?: return null
    return TonightScore(room.me.avatar, other.name, other.avatar, tally[TALLY_ME] ?: 0, tally[other.name] ?: 0)
}

val LocalTonight = compositionLocalOf<TonightScore?> { null }

/** Tonight's score, small, in a game's title row: "Tonight" over both faces and "2 – 2". */
@Composable
fun TonightPill(score: TonightScore) {
    val p = AppTheme.palette
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = score.description },
    ) {
        Text("TONIGHT", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
        Surface(color = p.card, shape = Shapes.Full, border = BorderStroke(2.dp, p.ink)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerAvatar("You", p.primary, size = 22, avatar = score.myAvatar)
                Text(
                    "${score.mine} – ${score.theirs}", style = Digits, color = p.text, fontSize = TextSize.BodyL,
                    fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                )
                PlayerAvatar(score.otherName, SeatColors[0], size = 22, avatar = score.otherAvatar)
            }
        }
    }
}

/**
 * Counts a game's turns so "Your turn" can say what to do for the first two and then leave it out.
 * A turn is each change from someone else's turn to yours; a second go in a row (a match in
 * Memory Pairs) is the same turn. It lives with the game screen ([LocalTurnHints], new for each
 * game and round) and is never saved. [turns] and [mine] start a count part-way, for tests.
 */
class TurnHints(private var turns: Int = 0, private var mine: Boolean = false) {
    /** Tells it whose turn it is now; returns which of your turns this is (0 while it isn't yours). */
    fun note(myTurn: Boolean): Int {
        if (myTurn && !mine) turns++
        mine = myTurn
        return if (myTurn) turns else 0
    }

    companion object {
        /** The what-to-do line shows on your first this many turns. */
        const val HINT_TURNS = 2

        fun showsHint(turn: Int) = turn in 1..HINT_TURNS
    }
}

/** Null where nothing scopes a count (solo games count for as long as the banner is on screen). */
val LocalTurnHints = compositionLocalOf<TurnHints?> { null }
