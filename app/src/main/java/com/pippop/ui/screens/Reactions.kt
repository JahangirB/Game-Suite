package com.pippop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pippop.data.ProfileStore
import com.pippop.data.Reaction
import com.pippop.ui.ShownReaction
import com.pippop.ui.Takeover
import com.pippop.ui.art.ReactionImage
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Shapes
import kotlinx.coroutines.delay

/** How long a reaction stays next to a player's name. */
private const val REACTION_SHOWN_MS = 3500L

/** What a tray item draws; [size] is the width of its cell, so it can scale down on a narrow screen. */
typealias TraySlot = @Composable (size: Dp) -> Unit

/** Eight items fit a row when each has its 48 dp; a narrower screen gets fewer columns instead. */
private const val TRAY_COLUMNS = 8

private val TrayTarget = 48.dp

/** The space between neighbours, drawn inside each cell so the cells themselves touch. */
private val TrayGap = 6.dp

/**
 * The reactions as a tray taken in at once: two rows of eight, the ones used last first
 * ([recentKeys], from the profile). On a phone too narrow for eight 48 dp targets a row holds
 * fewer and three rows show. If there are more reactions than rows shown, the rest sit below a
 * row that peeks out, and the tray scrolls up to them.
 * Tap one to show it to your opponent. While a reaction that takes the game over is playing
 * (Kylian Dictador), the tray is dimmed and nothing can be sent.
 *
 * [trailing] is an optional item after the last reaction (the "+" for sticker packs). It takes a
 * place in the grid like any reaction; pass null, the default, and no place is made.
 * [onLine] says one of the player's own lines; it is null where the server has no "lines" cap
 * (`BattleViewModel.lineSender`), and then the row of lines above the grid is not drawn.
 */
@Composable
fun ReactionBar(
    onReact: (Reaction) -> Unit,
    modifier: Modifier = Modifier,
    trailing: TraySlot? = null,
    onLine: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember(context) { ProfileStore.get(context) }
    val profile by store.profile.collectAsState()
    var editing by remember { mutableStateOf(false) }
    if (editing && onLine != null) LinesSheet(profile.lines, onSave = store::setLines) { editing = false }
    ReactionTray(
        Reaction.recentFirst(profile.recentReactions), onReact, modifier, trailing,
        lines = profile.lines.takeIf { onLine != null }, onLine = onLine, onEditLines = { editing = true },
    )
}

/** The tray for an already ordered list of reactions (the screenshot tests draw this one). * With [lines] (the player's own, when the server can carry them) a row of chips sits above the grid,
 * the pencil first ([onEditLines]); a tap on a chip says it ([onLine]). It is not part of the grid.
 */
@Composable
internal fun ReactionTray(
    reactions: List<Reaction>,
    onReact: (Reaction) -> Unit,
    modifier: Modifier = Modifier,
    trailing: TraySlot? = null,
    lines: List<String>? = null,
    onLine: ((String) -> Unit)? = null,
    onEditLines: () -> Unit = {},
) {
    val lockedUntil by Takeover.until.collectAsState()
    val locked = rememberTicker() < lockedUntil
    val cells = buildList<@Composable (Dp) -> Unit> {
        reactions.forEach { r -> add { size -> ReactionCell(r, size, locked, onReact) } }
        trailing?.let { add(it) }
    }
    Column(modifier.fillMaxWidth().alpha(if (locked) 0.35f else 1f)) {
        if (lines != null && onLine != null) LinesRow(lines, enabled = !locked, onLine = onLine, onEdit = onEditLines)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = minOf(TRAY_COLUMNS, (maxWidth / TrayTarget).toInt()).coerceAtLeast(1)
            val cell = maxWidth / columns
            val rows = cells.chunked(columns)
            val rowsShown = if (columns < TRAY_COLUMNS) 3 else 2
            Column(
                modifier = if (rows.size > rowsShown) {
                    Modifier.heightIn(max = TrayTarget * rowsShown + TrayTarget / 2).verticalScroll(rememberScrollState())
                } else Modifier,
            ) {
                rows.forEach { row ->
                    Row {
                        row.forEach { slot -> Box(Modifier.size(cell, TrayTarget), contentAlignment = Alignment.Center) { slot(cell) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionCell(reaction: Reaction, width: Dp, locked: Boolean, onReact: (Reaction) -> Unit) {
    val p = AppTheme.palette
    // The whole cell is the touch target; the picture inside leaves half a gap on every side.
    val art = minOf(width - TrayGap, 42.dp)
    Surface(
        onClick = { onReact(reaction) }, enabled = !locked, shape = Shapes.S, color = Color.Transparent,
        modifier = Modifier.size(width, TrayTarget).semantics { contentDescription = reaction.label() },
    ) {
        Box(Modifier.padding(TrayGap / 2), contentAlignment = Alignment.Center) {
            if (reaction.sticker) {
                ReactionImage(reaction, art)
            } else {
                Box(Modifier.size(art).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                    ReactionImage(reaction, art * 0.72f)
                }
            }
        }
    }
}

/** What a screen reader says for a reaction. */
private fun Reaction.label(): String = when (this) {
    Reaction.KYLIAN -> "Kylian Dictador"
    Reaction.SIDE_EYE -> "Hmm. Sure."
    Reaction.SWEAT -> "Phew"
    Reaction.THINK -> "Thinking"
    Reaction.HURRY -> "Hurry up"
    Reaction.THIS_IS_FINE -> "This is fine"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** [shown] while it should stay on screen, or null once it has been there long enough. */
fun visibleReaction(shown: ShownReaction?, now: Long): ShownReaction? =
    shown?.takeIf { now - it.at < REACTION_SHOWN_MS }

/** The local clock, ticking a few times a second so reactions disappear on time. */
@Composable
fun rememberTicker(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(250)
            value = System.currentTimeMillis()
        }
    }
    return now
}
