package com.pippop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/** How many tiles "Another game" shows before "All games". */
const val ANOTHER_TILES = 6

/**
 * Whose pick the next game is, as the sheet says it. [line] is the suggestion (either player can still pick);
 * [picker] is the one it names, with [mine] true when that is you; null when it names nobody.
 */
class WhoPicks(val line: String, val picker: PlayerInfo?, val mine: Boolean = false)

/**
 * Who picks the next game: whoever lost. You won ("me"), so they lost; they won ("opponent"), so you lost,
 * named or not. A tie, Sudoku together, Draw & Guess's team game and a
 * restart have no loser, so either picks. With three or four people there is no single loser to name,
 * so anyone can pick.
 */
fun whoPicks(room: RoomState): WhoPicks {
    if (room.present.size >= 2) return WhoPicks("Anyone can pick.", null)
    val them = room.present.firstOrNull() ?: room.opponent
    val noLoser = room.together || room.endReason == "restart"
    return when {
        !noLoser && room.winner == "me" && them != null ->
            WhoPicks("${them.name} lost that one, so ${them.name} picks. You can still pick if they'd rather.", them)
        // Apps Script names no winner, so a loss is "opponent" whether or not winnerName is set.
        !noLoser && room.winner == "opponent" -> WhoPicks("You lost that one, so you pick.", room.me, mine = true)
        else -> WhoPicks("A draw, so either of you picks.", null)
    }
}

/** The games the sheet offers: the [order] without the one just played ([current]), only those with a seat for all [people], at most [max]. */
fun anotherGameTiles(order: List<GameKind>, current: GameKind, people: Int, max: Int = ANOTHER_TILES): List<GameKind> =
    order.filter { it != current && fitsRoom(it, people) }.take(max)

/**
 * What "Another game" needs: the [games] to choose from (already without the one just played), those [away] (put away, left out of
 * the six tiles but in "All games"), how many [players] are in the room, [who] picks and the call that opens one.
 */
class AnotherGame(
    val current: GameKind, val games: List<GameKind>, val away: Set<GameKind>, val players: Int, val who: WhoPicks, val onPick: (GameKind) -> Unit,
) {
    /** The six tiles: games that fit and are not put away. */
    fun tiles() = anotherGameTiles(games.filter { it !in away }, current, players)

    /** Everything that fits, put-away games too. */
    fun everything() = anotherGameTiles(games, current, players, Int.MAX_VALUE)
}

/** "Another game" as a sheet over the result. Picking a game calls [AnotherGame.onPick] and closes it; [onBack] adds "Back to games" at the bottom. */
@Composable
fun AnotherGameSheet(another: AnotherGame, onBack: (() -> Unit)? = null, onDismiss: () -> Unit) {
    ToySheet(onDismiss = onDismiss, skipPartiallyExpanded = true) {
        AnotherGameContent(another, onBack = onBack?.let { b -> { onDismiss(); b() } }) { onDismiss(); another.onPick(it) }
    }
}

/** What the sheet holds; [onPick] is called with the chosen game. [allOpen] starts with the whole shelf out. */
@Composable
internal fun AnotherGameContent(another: AnotherGame, allOpen: Boolean = false, onBack: (() -> Unit)? = null, onPick: (GameKind) -> Unit) {
    val p = AppTheme.palette
    var all by remember { mutableStateOf(allOpen) }
    var surprise by remember { mutableIntStateOf(0) }
    var flicking by remember { mutableStateOf(false) }
    var lit by remember { mutableStateOf<GameKind?>(null) }
    val tiles = another.tiles()
    val everything = another.everything()
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(surprise) {
        if (surprise == 0) return@LaunchedEffect
        flicking = true
        // What can be seen: the six, or everything once "All games" is open.
        val pool = if (all) everything else tiles
        val pick = surprisePick(pool, another.players)
        if (pick != null) surpriseFlick(pool, pick, reduceMotion) { lit = it }
        flicking = false
        if (pick != null) onPick(pick)
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.L).padding(bottom = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Text(
            "Another game", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            Modifier.fillMaxWidth().clip(Shapes.M).background(p.accent).padding(Space.M),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            another.who.picker?.let { PlayerAvatar(it.name, if (another.who.mine) p.primary else OpponentColor, size = 30, avatar = it.avatar) }
            Text(another.who.line, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        (if (all) everything else tiles).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                row.forEach { g ->
                    ArtTile(
                        g, Modifier.weight(1f), highlight = lit == g, seats = seatsLabel(g),
                        description = tileDescription(g, another.players, emptyList()),
                    ) { if (!flicking) onPick(g) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            if (!all && everything.size > tiles.size) SecondaryButton("All games", { if (!flicking) all = true }, Modifier.weight(1f))
            SecondaryButton("Surprise us", { if (!flicking) surprise++ }, Modifier.weight(1f))
        }
        if (onBack != null) SecondaryButton("Back to games", onBack, Modifier.fillMaxWidth())
    }
}
