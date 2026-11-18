package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.KeyArt
import com.pippop.ui.art.color
import com.pippop.ui.art.minutes
import com.pippop.ui.art.playersFact
import com.pippop.ui.art.rules
import com.pippop.ui.art.tagline
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

// ---- What the Store holds, and how it is sorted -----------------------------------------------------

/** The Store's Choices row: everything, or one kind of game. */
enum class StoreFilter(val label: String) {
    ALL("All"), PUZZLES("Puzzles"), WORDS("Words"), PARTY("Party"), QUICK("Quick"),
}

/**
 * Which games are in which kind. One explicit list, so it is easy to read and to change. A game can be in more than one.
 * Puzzles: think it through. Words: made of letters or guessing words. Party: social, or for three or four. Quick: about 5 minutes or less.
 */
val STORE_CATEGORIES: Map<StoreFilter, Set<GameKind>> = mapOf(
    StoreFilter.PUZZLES to setOf(
        GameKind.SUDOKU, GameKind.NUMBERS, GameKind.CODE, GameKind.SLIDE, GameKind.MEMORY,
        GameKind.BLOCKS, GameKind.CONNECT4, GameKind.DOTS, GameKind.SHIPS,
    ),
    StoreFilter.WORDS to setOf(GameKind.WORDS, GameKind.GRID, GameKind.BALLOON, GameKind.DRAW, GameKind.LADDER),
    StoreFilter.PARTY to setOf(
        GameKind.DRAW, GameKind.BINGO, GameKind.LUDO, GameKind.CLASH, GameKind.DOMINOES, GameKind.GOLF, GameKind.DOTS, GameKind.MEMORY,
    ),
    StoreFilter.QUICK to setOf(
        GameKind.CONNECT4, GameKind.MEMORY, GameKind.WORDS, GameKind.TTT, GameKind.NUMBERS, GameKind.HOCKEY,
        GameKind.SLIDE, GameKind.BALLOON, GameKind.CODE, GameKind.GRID, GameKind.BINGO, GameKind.LADDER,
    ),
)

/** Whether [game] is shown under [filter] ("All" shows every game). */
fun StoreFilter.holds(game: GameKind): Boolean = this == StoreFilter.ALL || game in STORE_CATEGORIES.getValue(this)

/** The Store's list for [filter]: the games not on the [shelf] that the filter holds, in the catalogue's order. */
fun storeList(shelf: List<GameKind>, filter: StoreFilter): List<GameKind> = ProfileStore.storeGames(shelf).filter { filter.holds(it) }

/** What the Store card on the home says: "Game Store · 11 more games". */
fun storeCardText(storeGames: Int): String = when (storeGames) {
    0 -> "Game Store · every game is on your shelf"
    1 -> "Game Store · 1 more game"
    else -> "Game Store · $storeGames more games"
}

/** What the Store says when there is nothing left to get. */
const val STORE_EMPTY = "Every game is on your shelf."

/** What the Store says when a Choice holds nothing that is left to get. */
const val STORE_FILTER_EMPTY = "No games here are left to get."

/** "2–4 players · 5 min", as a Store row says it. */
fun storeFacts(kind: GameKind): String = "${kind.playersFact} · ${kind.minutes} min"

/**
 * Why a game can't be opened from the Store in a room, or null when it can: the same rule as the switch-game sheet
 * (not the game open now, and a seat for everyone). [roomSeats] is null outside a room.
 */
fun roomReason(kind: GameKind, roomSeats: Int?, roomGame: GameKind?): String? = when {
    roomSeats == null -> null
    kind == roomGame -> "Playing now"
    kind.maxPlayers < roomSeats -> "Too few seats"
    else -> null
}

/** The toast after a game is put on the shelf. */
fun onShelfToast(kind: GameKind) = "${kind.label} is on your shelf"

/** The toast after a game goes back to the Store. */
fun backInStoreToast(kind: GameKind) = "${kind.label} is back in the Store."

/**
 * Opens the Game Store from a sheet in a room. [open] takes what "Play" should do there with a game from the Store (switch to it, or pick it).
 * Null where the Store can't be opened (a screenshot, a test).
 */
val LocalOpenStore = compositionLocalOf<((pick: ((GameKind) -> Unit)?) -> Unit)?> { null }

// ---- The Store ----------------------------------------------------------------------------------------

/**
 * The Game Store: the games not on the shelf, each with "Get" (it goes on the shelf at once), and below them "On your shelf" with "Open".
 * Tapping a row opens the game's page ([onOpenPage]); "Open" starts it as the shelf would ([onOpen]).
 */
@Composable
fun StoreScreen(
    shelf: List<GameKind>,
    filter: StoreFilter,
    onFilter: (StoreFilter) -> Unit,
    onBack: () -> Unit,
    onGet: (GameKind) -> Unit,
    onOpenPage: (GameKind) -> Unit,
    onOpen: (GameKind) -> Unit,
    /** When opened from a room: how many are in it, and the game it is on. A game with too few seats, or the one open now, can't be opened. */
    roomSeats: Int? = null,
    roomGame: GameKind? = null,
) {
    val p = AppTheme.palette
    val inStore = storeList(shelf, filter)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Game Store", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            val big = LocalDensity.current.fontScale > 1.2f
            Choices(StoreFilter.entries.map { it to it.label }, filter, onFilter, Modifier.fillMaxWidth(), perRow = if (big) null else StoreFilter.entries.size)
            if (inStore.isEmpty()) {
                Text(
                    if (ProfileStore.storeGames(shelf).isEmpty()) STORE_EMPTY else STORE_FILTER_EMPTY,
                    color = p.muted, fontSize = TextSize.BodyL, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Space.XL),
                )
            }
            inStore.forEach { kind ->
                StoreRow(kind, "Get", "Get ${kind.label}", { onOpenPage(kind) }) { onGet(kind) }
            }
            if (shelf.isNotEmpty()) {
                Text(
                    "On your shelf", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Space.S).semantics { heading() },
                )
                shelf.forEach { kind ->
                    StoreRow(kind, "Open", "Open ${kind.label}", { onOpenPage(kind) }, roomReason(kind, roomSeats, roomGame)) { onOpen(kind) }
                }
            }
        }
    }
}

/** One game in the Store's lists: art, name, line, facts and its button. The text and picture open the game's page; the button is its own target. */
@Composable
private fun StoreRow(kind: GameKind, button: String, description: String, onPage: () -> Unit, reason: String? = null, onButton: () -> Unit) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(Shapes.M).clickable(role = Role.Button, onClickLabel = "See ${kind.label}", onClick = onPage),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.M),
            ) {
                KeyArt(kind, Modifier.width(88.dp).height(54.dp).clip(Shapes.S).border(2.dp, p.ink, Shapes.S))
                Column(Modifier.weight(1f)) {
                    Text(kind.label, color = p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(kind.tagline, color = p.muted, fontSize = TextSize.Label, lineHeight = LineHeight.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(storeFacts(kind), color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            if (reason != null) {
                Text(reason, color = p.muted, fontSize = TextSize.Label, textAlign = TextAlign.Center, modifier = Modifier.width(84.dp))
            } else {
                StoreButton(button, description, primary = button == "Get", onClick = onButton)
            }
        }
    }
}

/** A pill button of at least 48 dp whose spoken name is [description] ("Get Bingo"), not just its label. */
@Composable
private fun StoreButton(label: String, description: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Box(
        modifier.heightIn(min = 48.dp).clip(Shapes.Full).background(if (primary) p.primary else p.card).border(2.dp, p.ink, Shapes.Full)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button }
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.L),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) p.onPrimary else p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ---- A game's page -----------------------------------------------------------------------------------

/**
 * One game's page: a big picture, its name and line, the facts, the first rule under "How to play" with a link to all of them,
 * and "Add to my games" (which becomes "Play" and "Remove from my games" once it is on the shelf).
 * [roomSeats] is how many are in the room when the Store was opened from one: a game with too few seats can't be played there.
 * Nothing on the page says what is on a partner's shelf: the app is not told.
 */
@Composable
fun GamePageScreen(
    kind: GameKind,
    onShelf: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit,
    roomSeats: Int? = null,
    roomGame: GameKind? = null,
) {
    val p = AppTheme.palette
    var rules by rememberSaveable(kind) { mutableStateOf(false) }
    if (rules) RulesSheet(kind) { rules = false }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Game Store", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            KeyArt(kind, Modifier.fillMaxWidth().aspectRatio(280f / 170f).clip(Shapes.L).border(2.dp, p.ink, Shapes.L).testTag("store-banner"))
            Column(verticalArrangement = Arrangement.spacedBy(Space.XS)) {
                Text(
                    kind.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                    lineHeight = LineHeight.DisplayM, modifier = Modifier.semantics { heading() },
                )
                Text(kind.tagline, color = p.muted, fontSize = TextSize.BodyL)
            }
            Facts(listOf(kind.playersFact, "About ${kind.minutes} min", "Plays nearby and online"))
            Text("How to play", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            kind.rules.firstOrNull()?.let { first ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                    Text(first.title, color = p.primaryText, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.width(88.dp))
                    Text(first.body, color = p.text, fontSize = TextSize.Body, modifier = Modifier.weight(1f))
                }
            }
            Text(
                "See all rules", color = p.primaryText, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
                modifier = Modifier.heightIn(min = 48.dp).clip(Shapes.S).clickable(role = Role.Button) { rules = true }.padding(vertical = Space.M),
            )
            if (!onShelf) {
                PrimaryButton("Add to my games", onAdd, Modifier.fillMaxWidth())
            } else {
                val reason = roomReason(kind, roomSeats, roomGame)
                if (reason != null) {
                    Text(if (kind == roomGame) "${kind.label} is open now." else "${kind.label} is for ${kind.maxPlayers}.", color = p.muted, fontSize = TextSize.Body)
                } else {
                    PrimaryButton("Play", onPlay, Modifier.fillMaxWidth())
                }
                SecondaryButton("Remove from my games", onRemove, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Space.S))
        }
    }
}

// ---- On the home -------------------------------------------------------------------------------------

/** The end of the shelf: "Game Store · N more games" (or that every game is on the shelf). It opens the Store; while the shelf is edited it rests. */
@Composable
internal fun StoreCard(storeGames: List<GameKind>, enabled: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        color = p.accent, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline),
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f).clip(Shapes.L).testTag(STORE_CARD)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = Space.S),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(storeCardText(storeGames.size), color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            storeGames.take(2).forEach { kind ->
                Box(
                    Modifier.size(26.dp).clip(Shapes.S).background(kind.color).border(2.dp, p.ink, Shapes.S).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) { GameIcon(kind, Modifier.size(16.dp)) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = p.muted)
        }
    }
}

internal const val STORE_CARD = "store-card"

/** What the one-time note says. */
const val SHELF_NOTE = "The other games are in the Game Store"

/** The one-time note on the home for a phone that had the app before the shelf: where the other games went. Dismissible. */
@Composable
internal fun ShelfNote(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val p = AppTheme.palette
    Surface(color = p.accent, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth().testTag(SHELF_NOTE_TAG)) {
        Column(Modifier.padding(start = Space.L, top = Space.S, bottom = Space.L, end = Space.S), verticalArrangement = Arrangement.spacedBy(Space.S)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SHELF_NOTE, color = p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(top = Space.S).semantics { heading() },
                )
                Box(
                    Modifier.size(48.dp).clip(Shapes.Full).clickable(role = Role.Button, onClick = onDismiss)
                        .semantics { contentDescription = "Dismiss" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = null, tint = p.text) }
            }
            Text("Add any of them back to your shelf, whenever you like.", color = p.text, fontSize = TextSize.Body, modifier = Modifier.padding(end = Space.L))
            PrimaryButton("Open the Game Store", onOpen, Modifier.fillMaxWidth().padding(end = Space.L))
        }
    }
}

internal const val SHELF_NOTE_TAG = "shelf-note"
