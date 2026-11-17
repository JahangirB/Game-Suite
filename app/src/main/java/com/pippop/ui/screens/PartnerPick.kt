package com.pippop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/**
 * A partner's choice of a game that is not on your shelf. [who] is the name (or names) as the sheet says it, [avatar] the
 * face of the first, [plural] whether more than one person is waiting there.
 */
data class PartnerPick(val who: String, val avatar: Avatar?, val game: GameKind, val plural: Boolean = false)

const val PICK_BODY = "It's in the Game Store. Add it to join."
const val PICK_ADD = "Add and join"
const val PICK_NOT_NOW = "Not now"

fun partnerPickTitle(pick: PartnerPick) = "${pick.who} ${if (pick.plural) "want" else "wants"} to play ${pick.game.label}"

/**
 * The pick that [room] puts to you: someone else is in the room's open game, waiting for you, and it is not on your [shelf].
 * That is every way a partner's choice reaches a phone in a room (they opened it, switched to it, picked it under "Another game",
 * or changed the game from its lobby): your screen stays where it was and the home offers "Join". Null when you are
 * at the game yourself, paused in it, it is being played (nothing to join) or the game is on the shelf.
 */
fun roomPick(room: RoomState?, shelf: List<GameKind>): PartnerPick? {
    if (room == null || !room.inSession || room.atGame || room.pausedByMe || room.game in shelf) return null
    if (room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING) return null
    val here = room.othersAtGame
    if (here.isEmpty()) return null
    return PartnerPick(joinNames(here.map { it.name }), here.first().avatar, room.game, plural = here.size > 1)
}

/**
 * What the home does with the room's pick ([roomPick]): the sheet shows while there is one and "Not now" has not been said for
 * it. "Not now" changes nothing in the room (you stay where you are; they see what they saw before); the home's "Join" on the
 * game asks again through [askAgain].
 */
class PickGate {
    internal var snoozed by mutableStateOf<Triple<String, GameKind, Int>?>(null)

    /** The key of a pick: the room, its game and round, so a later pick of the same game is asked afresh. */
    internal fun keyOf(room: RoomState) = Triple(room.code, room.game, room.round)
    internal fun isSnoozed(key: Triple<String, GameKind, Int>) = snoozed == key
    internal fun snooze(key: Triple<String, GameKind, Int>) { snoozed = key }
    internal fun forget() { snoozed = null }

    companion object {
        /** Keeps "Not now" through a rotation, a dark-mode or font-size change: the room's code, the game's key and the round. */
        val Saver: androidx.compose.runtime.saveable.Saver<PickGate, Any> = androidx.compose.runtime.saveable.Saver(
            save = { gate -> gate.snoozed?.let { (code, game, round) -> listOf(code, game.key, round) } ?: emptyList<Any>() },
            restore = { saved ->
                val parts = saved as List<*>
                PickGate().also { gate ->
                    val game = GameKind.entries.firstOrNull { it.key == parts.getOrNull(1) }
                    if (parts.size == 3 && game != null) gate.snoozed = Triple(parts[0] as String, game, parts[2] as Int)
                }
            },
        )
    }

    /** The home's "Join" on [game]: true when the sheet has to ask first (the join then waits for "Add and join"). */
    fun askAgain(pick: PartnerPick?, game: GameKind): Boolean {
        if (pick == null || pick.game != game) return false
        snoozed = null
        return true
    }

    /** The sheet, while [room] has a pick and [active] (the home is showing); "Add and join" calls [onAddAndJoin] with the game after it is on the shelf. */
    @Composable
    fun Sheet(room: RoomState?, shelf: List<GameKind>, active: Boolean, onAddToShelf: (GameKind) -> Unit, onJoin: (GameKind) -> Unit) {
        val pick = roomPick(room, shelf)
        val key = if (pick != null && room != null) keyOf(room) else null
        LaunchedEffect(key) { if (key == null) forget() }
        if (pick != null && key != null && active && !isSnoozed(key)) {
            PartnerPickSheet(pick, onAdd = { onAddToShelf(pick.game); onJoin(pick.game) }, onNotNow = { snooze(key) })
        }
    }
}

/** The sheet over the home: their face, what they want to play, and the two ways out. */
@Composable
fun PartnerPickSheet(pick: PartnerPick, onAdd: () -> Unit, onNotNow: () -> Unit) {
    ToySheet(onDismiss = onNotNow, skipPartiallyExpanded = true) { PartnerPickContent(pick, onAdd, onNotNow) }
}

/** What the sheet holds (also drawn alone by the screenshots). */
@Composable
internal fun PartnerPickContent(pick: PartnerPick, onAdd: () -> Unit, onNotNow: () -> Unit) {
    val p = AppTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.L).padding(bottom = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.M), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerAvatar(pick.who, OpponentColor, size = 64, avatar = pick.avatar)
        Text(
            partnerPickTitle(pick), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
        )
        Text(PICK_BODY, color = p.muted, fontSize = TextSize.Body, textAlign = TextAlign.Center)
        PrimaryButton(PICK_ADD, onAdd, Modifier.fillMaxWidth())
        SecondaryButton(PICK_NOT_NOW, onNotNow, Modifier.fillMaxWidth())
    }
}
