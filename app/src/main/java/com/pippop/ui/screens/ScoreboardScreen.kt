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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.GameScore
import com.pippop.data.GameStory
import com.pippop.data.MatchRecord
import com.pippop.data.ProfileStore
import com.pippop.data.ScoreStore
import com.pippop.data.Scoreboard
import com.pippop.data.ShelfSlot
import com.pippop.data.SideStory
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecordStore
import com.pippop.data.Standing
import com.pippop.ui.art.AvatarImage
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.TrophyCup
import com.pippop.ui.art.color
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import java.time.format.TextStyle
import java.util.Locale

/** The three tabs at the top of the scoreboard. */
enum class ScoreTab(val label: String) { MONTH("This month"), ALL("All time"), SOLO("Solo") }

/**
 * Our scoreboard: who's ahead this month against your partner (or over every game kept), each
 * game's wins as one bar split where you stand (tap one for its story), a shelf with a cup for
 * each month's winner, and, on its own tab, your best at each game played alone. Every game you
 * finish together counts; it lives on this phone.
 */
@Composable
fun ScoreboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val records by ScoreStore.get(context).records.collectAsState()
    val solo by SoloRecordStore.get(context).records.collectAsState()
    val profile by ProfileStore.get(context).profile.collectAsState()
    val board = remember(records) { Scoreboard.of(records, System.currentTimeMillis()) }
    val lines = remember(solo) { SoloLines.of(solo) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<GameScore?>(null) }
    val partner = board.partner
    val partnerAvatar = profile.partner?.takeIf { it.name == partner }?.avatar
    open?.let { g ->
        val story = remember(records, g.game, partner) { Scoreboard.story(records, g.game, partner) }
        GameStorySheet(story, g, ScoreTab.entries[tab], partner ?: "Partner", profile.avatar, partnerAvatar) { open = null }
    }
    ScoreboardContent(board, lines, ScoreTab.entries[tab], { tab = it.ordinal }, profile.avatar, partnerAvatar, onBack) { open = it }
}

/** The screen without its stores: what [ScoreboardScreen] draws, so a picture can show any state. */
@Composable
internal fun ScoreboardContent(
    board: Scoreboard,
    solo: List<SoloLine>,
    tab: ScoreTab,
    onTab: (ScoreTab) -> Unit,
    myAvatar: Avatar?,
    partnerAvatar: Avatar?,
    onBack: () -> Unit,
    onOpen: (GameScore) -> Unit,
) {
    val p = AppTheme.palette
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Scoreboard", onBack) {}
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            Choices(ScoreTab.entries.map { it to it.label }, tab, onTab, Modifier.fillMaxWidth())
            val partner = board.partner
            when {
                tab == ScoreTab.SOLO -> SoloList(solo)
                partner == null -> EmptyScores("Nothing yet", "No games together yet. Play one with your partner and it lands here, month by month.")
                else -> {
                    val standing = if (tab == ScoreTab.MONTH) board.thisMonth else board.allTime
                    val eyebrow = if (tab == ScoreTab.MONTH) {
                        val month = board.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                        "$month · ${board.daysLeft} ${if (board.daysLeft == 1) "day" else "days"} left"
                    } else "All time · ${board.total} ${if (board.total == 1) "game" else "games"}"
                    val name = if (tab == ScoreTab.MONTH) board.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) else "All time"
                    StandingCard(eyebrow, name, standing, partner, myAvatar, partnerAvatar)
                    Text("Games", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                    if (standing.games.isEmpty()) {
                        Text("No games together yet this month. A fresh start.", color = p.muted, fontSize = TextSize.Body)
                    }
                    standing.games.forEachIndexed { i, g ->
                        GameLine(g, partner, tag = gameTag(g, partner, first = i == 0 && standing.games.size > 1)) { onOpen(g) }
                    }
                    Text("Trophies", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                    TrophyShelf(board.shelf, partner, board, myAvatar, partnerAvatar)
                    Text(
                        "${board.total} games together so far · your best run: ${board.bestStreak} ${if (board.bestStreak == 1) "win" else "wins"} in a row. Kept on this phone; your partner's keeps its own.",
                        color = p.muted, fontSize = TextSize.Label,
                    )
                }
            }
            Spacer(Modifier.height(Space.L))
        }
    }
}

/** The app's empty look: a card with a title and a warm line. */
@Composable
internal fun EmptyScores(title: String, line: String) {
    AppCard(title) { Text(line, color = AppTheme.palette.muted, fontSize = TextSize.Body) }
}

/** The short line under a game's name: a run somebody is on, ties, or that it is the most played. */
private fun gameTag(g: GameScore, partner: String, first: Boolean): String? {
    // g.last is newest first: count the wins in a row at its front.
    val lead = g.last.firstOrNull()?.takeIf { it.winner != null }
    val who = lead?.let { r -> if (r.won(MatchRecord.ME)) MatchRecord.ME else if (r.won(partner)) partner else null }
    val run = if (who == null) 0 else g.last.takeWhile { it.won(who) }.size
    return when {
        who != null && run >= 2 -> if (who == MatchRecord.ME) "You're on a run of $run" else "$partner's on a run of $run"
        g.ties > 0 -> "${g.ties} tied"
        first -> "Most played"
        else -> null
    }
}

/** A face: the player's picture in a ringed circle, or their first letter. */
@Composable
internal fun Face(name: String, avatar: Avatar?, color: Color, size: Dp, dim: Boolean = false) {
    val p = AppTheme.palette
    Box(Modifier.size(size).clip(CircleShape).background(p.accent).border(2.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) {
        if (avatar != null) AvatarImage(avatar, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = readableOn(color), fontWeight = FontWeight.Bold, fontSize = TextSize.Body)
        }
        if (dim) Box(Modifier.fillMaxSize().background(p.card.copy(alpha = 0.55f)))
    }
}

/** Two shares of one bar: yours from the left in coral, theirs from the right in orange; [mine] and [theirs] are counts. */
@Composable
internal fun SplitBar(mine: Int, theirs: Int, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val p = AppTheme.palette
    Row(modifier.height(height).clip(Shapes.Full).background(p.accent).border(1.5.dp, p.ink, Shapes.Full)) {
        if (mine > 0) Box(Modifier.weight(mine.toFloat()).fillMaxHeight().background(p.primary))
        if (mine > 0 && theirs > 0) Box(Modifier.width(1.5.dp).fillMaxHeight().background(p.ink))
        if (theirs > 0) Box(Modifier.weight(theirs.toFloat()).fillMaxHeight().background(OpponentColor))
    }
}

/**
 * The last results, oldest first: yours coral with a Y, your partner's orange with their letter, a
 * tie hollow with a dash, so no dot depends on its colour. [initial] is the partner's.
 */
@Composable
internal fun FormDots(results: List<Boolean?>, initial: String) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Space.XS)) {
        results.forEach { r ->
            val fill = when (r) { true -> p.primary; false -> OpponentColor; null -> p.card }
            Box(Modifier.size(18.dp).clip(CircleShape).background(fill).border(1.5.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    when (r) { true -> "Y"; false -> initial; null -> "=" },
                    style = Digits.copy(lineHeight = TextSize.Micro, platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)),
                    color = if (r == null) p.text else readableOn(fill), fontSize = TextSize.Micro, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun who(r: Boolean?, partner: String) = when (r) { true -> "you"; false -> partner; null -> "a tie" }

/** What a screen reader says of a window: the score, who leads and the last five. */
internal fun standingSpoken(name: String, s: Standing, partner: String): String {
    val lead = when {
        s.mine > s.theirs -> "You lead by ${s.mine - s.theirs}."
        s.theirs > s.mine -> "$partner leads by ${s.theirs - s.mine}."
        s.mine == 0 -> "No games yet."
        else -> "Level."
    }
    val form = if (s.form.isEmpty()) "" else " Last five, oldest first: ${s.form.joinToString(", ") { who(it, partner) }}."
    return "$name: you ${s.mine}, $partner ${s.theirs}. $lead$form"
}

/** The window's card: both faces, the score in the biggest digits, one split bar, who leads and the last five. */
@Composable
private fun StandingCard(eyebrow: String, name: String, s: Standing, partner: String, myAvatar: Avatar?, partnerAvatar: Avatar?) {
    val p = AppTheme.palette
    Surface(
        color = p.card, shape = Shapes.L, border = BorderStroke(2.dp, p.ink),
        modifier = Modifier.fillMaxWidth().pressedEdge(p.ink, Radius.L).semantics(mergeDescendants = true) { contentDescription = standingSpoken(name, s, partner) },
    ) {
        Column(Modifier.padding(Space.L), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S)) {
            Text(eyebrow.uppercase(), color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                    Face("You", myAvatar, p.primary, 52.dp)
                    Text("You", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${s.mine}", style = Digits, color = p.primaryText, fontSize = TextSize.Score, fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                )
                Text(" – ", color = p.muted, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text(
                    "${s.theirs}", style = Digits, color = p.opponentText, fontSize = TextSize.Score, fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                    Face(partner, partnerAvatar, OpponentColor, 52.dp)
                    Text(partner, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SplitBar(s.mine, s.theirs, Modifier.fillMaxWidth(), height = 16.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        s.mine > s.theirs -> "You lead by ${s.mine - s.theirs}"
                        s.theirs > s.mine -> "$partner leads by ${s.theirs - s.mine}"
                        s.mine == 0 -> "A fresh start"
                        else -> "Level"
                    },
                    color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                if (s.form.isNotEmpty()) {
                    Text("LAST FIVE  ", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
                    FormDots(s.form, partner.trim().firstOrNull()?.uppercase() ?: "?")
                }
            }
        }
    }
}

/** A game's icon on its colour, small: for lists. */
@Composable
internal fun GameBadge(kind: GameKind, size: Dp = 30.dp) {
    val p = AppTheme.palette
    val color = kind.color
    Box(
        Modifier.size(size).clip(Shapes.S).background(Brush.verticalGradient(listOf(color.lighter(0.18f), color.darker(0.12f)))).border(2.dp, p.ink, Shapes.S),
        contentAlignment = Alignment.Center,
    ) { GameIcon(kind, Modifier.size(size * 0.62f)) }
}

private fun gameSpoken(g: GameScore, partner: String) =
    "${g.game.label}: you ${g.mine}, $partner ${g.theirs}" + (if (g.ties > 0) ", ${g.ties} tied" else "")

/** One game: its icon and name, your wins, one bar split where you stand, your partner's wins. Tap for its story. */
@Composable
private fun GameLine(g: GameScore, partner: String, tag: String?, onClick: () -> Unit) {
    val p = AppTheme.palette
    Surface(
        color = p.card, shape = Shapes.M, border = BorderStroke(1.dp, p.hairline),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open the story", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = gameSpoken(g, partner) + (if (tag != null) ". $tag" else "") },
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = Space.M, vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
            GameBadge(g.game)
            Spacer(Modifier.width(Space.M))
            Column(Modifier.weight(0.9f)) {
                Text(g.game.label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tag != null) Text(tag, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
            Text("${g.mine}", style = Digits, color = p.primaryText, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 24.dp))
            SplitBar(g.mine, g.theirs, Modifier.weight(1f).padding(horizontal = Space.S))
            Text("${g.theirs}", style = Digits, color = p.opponentText, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 20.dp))
        }
    }
}

/** The shelf's spoken line for one place. */
internal fun slotSpoken(slot: ShelfSlot, partner: String, board: Scoreboard): String {
    val month = slot.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val t = slot.trophy
    return when {
        t != null && t.winner == null -> "$month: a tie, ${t.mine} each"
        t != null && t.winner == MatchRecord.ME -> "$month: you took it, you ${t.mine}, $partner ${t.theirs}"
        t != null -> "$month: $partner took it, you ${t.mine}, $partner ${t.theirs}"
        slot.current -> "$month: in play, you ${board.mine}, $partner ${board.theirs}"
        else -> "$month: still to come"
    }
}

/** What is written under a cup besides the month: its score, "in play", or nothing for a month to come. */
internal fun slotScore(slot: ShelfSlot): String = when {
    slot.trophy != null -> "${slot.trophy.mine}–${slot.trophy.theirs}"
    slot.current -> "in play"
    else -> ""
}

/** A cup for each month that has ended (the winner's face on gold, a tie silver), the month in play, and empty places for the months to come. */
@Composable
private fun TrophyShelf(slots: List<ShelfSlot>, partner: String, board: Scoreboard, myAvatar: Avatar?, partnerAvatar: Avatar?) {
    val p = AppTheme.palette
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.XS), horizontalArrangement = Arrangement.SpaceBetween) {
            slots.forEach { slot ->
                Column(
                    Modifier.width(60.dp).heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = slotSpoken(slot, partner, board) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val t = slot.trophy
                    if (t != null) {
                        val mineWon = t.winner == MatchRecord.ME
                        TrophyCup(
                            Modifier.size(54.dp, 60.dp), face = if (mineWon) myAvatar else partnerAvatar.takeIf { t.winner != null }, tie = t.winner == null,
                            initial = if (t.winner == null) null else if (mineWon) "Y" else partner.trim().firstOrNull()?.uppercase() ?: "?",
                            initialFill = if (mineWon) p.primary else OpponentColor,
                        )
                    } else {
                        Box(Modifier.size(54.dp, 60.dp).clip(Shapes.M).border(1.5.dp, p.hairline, Shapes.M), contentAlignment = Alignment.Center) {
                            Text("?", color = p.muted, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(slot.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold)
                    // Muted text on the background is 4.5:1 or more in both themes (ScoreboardTest checks it).
                    Text(slotScore(slot), style = Digits, color = p.muted, fontSize = TextSize.Label, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.Full).background(Board.Wood))
    }
}

// ---- A game's story ------------------------------------------------------------------------------

/** What the games' own counts are called in a margin ("9 to 4 boxes"). */
internal fun scoreUnit(game: GameKind) = when (game) {
    GameKind.DOTS -> "boxes"
    GameKind.MEMORY -> "pairs"
    GameKind.HOCKEY -> "goals"
    GameKind.GOLF -> "strokes"
    GameKind.BLOCKS -> "squares"
    else -> "points"
}

internal fun countMetric(game: GameKind) = when (game) {
    GameKind.SHIPS -> SoloMetric.SHOTS
    GameKind.CONNECT4 -> SoloMetric.DISCS
    GameKind.WORDS -> SoloMetric.TRIES
    else -> SoloMetric.GUESSES
}

private fun runText(n: Int) = when (n) { 0 -> "–"; 1 -> "1 win"; else -> "$n in a row" }

private fun quickestText(game: GameKind, s: SideStory) = when {
    s.quickestCount != null -> SoloLines.bestText(countMetric(game), s.quickestCount.toLong())
    s.quickestMs != null -> SoloLines.bestText(SoloMetric.TIME, s.quickestMs)
    else -> "–"
}

private fun biggestText(game: GameKind, s: SideStory) = s.biggestWin?.let { "${it.winnerScore} to ${it.otherScore} ${scoreUnit(game)}" } ?: "–"

/** One row of the story: your value, what it is, their value. */
@Composable
private fun StatLine(label: String, mine: String, theirs: String, partner: String) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label: you $mine, $partner $theirs" }, verticalAlignment = Alignment.CenterVertically) {
        Text(mine, style = Digits, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(label, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Space.XS))
        Text(theirs, style = Digits, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** A game's story in a sheet over the scoreboard. */
@Composable
private fun GameStorySheet(story: GameStory, g: GameScore, tab: ScoreTab, partner: String, myAvatar: Avatar?, partnerAvatar: Avatar?, onDismiss: () -> Unit) {
    ToySheet(onDismiss = onDismiss) {
        GameStoryContent(story, g, if (tab == ScoreTab.MONTH) "This month" else "All time", partner, myAvatar, partnerAvatar)
    }
}

/**
 * Both players' records side by side: wins of all time, longest run, quickest win and biggest win
 * (a line is left out where neither has anything), then the last ten as a row of winners' faces.
 * [windowName] says which counts the line under the title gives.
 */
@Composable
internal fun GameStoryContent(story: GameStory, g: GameScore, windowName: String, partner: String, myAvatar: Avatar?, partnerAvatar: Avatar?) {
    val p = AppTheme.palette
    val m = story.mine
    val t = story.theirs
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.L).padding(bottom = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            GameBadge(story.game, 38.dp)
            Column(Modifier.weight(1f)) {
                Text(story.game.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                Text(
                    "$windowName: you ${g.mine}, $partner ${g.theirs}" + if (g.ties > 0) ", ${g.ties} tied" else "",
                    color = p.muted, fontSize = TextSize.Body,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
            Face("You", myAvatar, p.primary, 34.dp)
            Text("  You", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$partner  ", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Face(partner, partnerAvatar, OpponentColor, 34.dp)
        }
        SplitBar(m.wins, t.wins, Modifier.fillMaxWidth(), height = 14.dp)
        StatLine("Wins, all time", "${m.wins}", "${t.wins}", partner)
        if (m.longestRun > 0 || t.longestRun > 0) StatLine("Longest run", runText(m.longestRun), runText(t.longestRun), partner)
        if (m.quickestMs != null || m.quickestCount != null || t.quickestMs != null || t.quickestCount != null) {
            StatLine("Quickest win", quickestText(story.game, m), quickestText(story.game, t), partner)
        }
        if (m.biggestWin != null || t.biggestWin != null) StatLine("Biggest win", biggestText(story.game, m), biggestText(story.game, t), partner)
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
        Text("LAST TEN, NEWEST FIRST", color = p.muted, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold)
        val winners = story.last.map { r -> if (r.won(MatchRecord.ME)) true else if (r.won(partner)) false else null }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Last ten, newest first: " + winners.joinToString(", ") { who(it, partner) } },
        ) {
            winners.forEach { w ->
                when (w) {
                    true -> Face("You", myAvatar, p.primary, 29.dp)
                    false -> Face(partner, partnerAvatar, OpponentColor, 29.dp)
                    null -> Box(Modifier.size(29.dp).clip(CircleShape).background(p.card).border(2.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) {
                        Text("=", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (winners.any { it == null }) Text("A circle with = is a tie.", color = p.muted, fontSize = TextSize.Label)
        if (story.missingFacts) {
            Text("Times and margins are kept from this update on; wins go back to your first game together.", color = p.muted, fontSize = TextSize.Label)
        }
    }
}

// ---- Solo ----------------------------------------------------------------------------------------

/** Your own bests, one line a game, kept for good. */
@Composable
private fun SoloList(lines: List<SoloLine>) {
    val p = AppTheme.palette
    if (lines.isEmpty()) {
        EmptyScores("Nothing yet", "Play a game on your own and your best lands here.")
        return
    }
    lines.forEach { l ->
        Surface(
            color = p.card, shape = Shapes.M, border = BorderStroke(1.dp, p.hairline),
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = l.spoken },
        ) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = Space.M, vertical = Space.S), verticalAlignment = Alignment.CenterVertically) {
                GameBadge(l.game)
                Spacer(Modifier.width(Space.M))
                Column(Modifier.weight(1f)) {
                    Text(l.game.label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                    if (l.detail.isNotEmpty()) Text(l.detail, color = p.muted, fontSize = TextSize.Label)
                    if (l.others != null) Text("Also ${l.others}", color = p.muted, fontSize = TextSize.Label)
                }
                Spacer(Modifier.width(Space.S))
                Text(l.best, style = Digits, color = p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
        }
    }
}
