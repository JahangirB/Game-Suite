package com.pippop.ui.proto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.game.SudokuShape
import com.pippop.ui.art.AvatarImage
import com.pippop.ui.art.KeyArt
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.ResultAnimation
import com.pippop.ui.art.color
import com.pippop.ui.art.tagline
import com.pippop.ui.screens.BoardArea
import com.pippop.ui.screens.ChipPlayer
import com.pippop.ui.screens.ConnectFourBoard
import com.pippop.ui.screens.DockTool
import com.pippop.ui.screens.GameFrame
import com.pippop.ui.screens.GameIcons
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.MyDisc
import com.pippop.ui.screens.PlayerRow
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.ScreenTopBar
import com.pippop.ui.screens.SecondaryButton
import com.pippop.ui.screens.ShapePicker
import com.pippop.ui.screens.TheirDisc
import com.pippop.ui.screens.pressedEdge
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Mark
import com.pippop.ui.theme.MarkIcon
import com.pippop.ui.theme.OpponentColor
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.seatMark

/** One fact about a game, as a small pill ("2 players", "About 5 min"). */
@Composable
fun Fact(text: String) {
    val p = AppTheme.palette
    Text(
        text, color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(Shapes.Full).background(p.accent).border(1.dp, p.hairline, Shapes.Full).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The last few games between you, newest last: yours in coral, theirs in orange, a tie hollow. */
@Composable
fun FormDots(results: List<Boolean?>, size: Dp = 12.dp) {
    val p = AppTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        results.forEach { r ->
            Box(Modifier.size(size).clip(CircleShape).background(when (r) { true -> p.primary; false -> OpponentColor; null -> p.card }).border(1.5.dp, p.ink, CircleShape))
        }
    }
}

@Composable
private fun SmallFace(avatar: Avatar, size: Dp = 26.dp, dim: Boolean = false) {
    val p = AppTheme.palette
    Box(Modifier.size(size).alpha(if (dim) 0.55f else 1f).clip(CircleShape).background(p.accent).border(1.5.dp, p.ink, CircleShape)) { AvatarImage(avatar, Modifier.fillMaxSize()) }
}

// ---- The sheet a game opens with ------------------------------------------------------------

/** Tapping a game: what it is at a glance, and solo with its level right here. */
@Composable
fun BoxScope.GameSheetNew(kind: GameKind = GameKind.CONNECT4) {
    val p = AppTheme.palette
    SheetOver {
        KeyArt(kind, Modifier.fillMaxWidth().height(128.dp).clip(Shapes.L).border(2.dp, p.ink, Shapes.L))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
                Text(kind.tagline, color = p.muted, fontSize = TextSize.Body)
            }
            Text(
                "How to play", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(Shapes.Full).background(p.card).border(2.dp, p.ink, Shapes.Full).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Fact("2 players")
            Fact("About 5 min")
            Fact("Take turns")
        }
        PrimaryButton("Play with Maya", {})
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Solo", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
            Segmented(listOf("Easy", "Medium", "Hard"), 1, Modifier.weight(1f))
        }
        SecondaryButton("Play solo · Medium", {})
    }
}

@Composable
private fun RulePoint(title: String, body: String, last: Boolean = false) {
    val p = AppTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(title, color = p.primaryText, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.width(108.dp))
            Text(body, color = p.text, fontSize = TextSize.Body, modifier = Modifier.weight(1f))
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
    }
}

/** The rules as a handful of short points you can find your place in, with the way to the walk-through. */
@Composable
fun BoxScope.RulesSheetNew() {
    val p = AppTheme.palette
    SheetOver {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameBadge(GameKind.GOLF, 36.dp, GameKind.GOLF.color)
            Text("How to play Mini Golf", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
        }
        RulePoint("Goal", "Nine holes. The fewest strokes wins.")
        RulePoint("Aim", "Swipe to turn round your ball. Pinch to see the whole hole; double-tap faces the flag.")
        RulePoint("Shoot", "Slide up the bar on the right and let go. Low is a roll; high is a jump, over gaps and walls.")
        RulePoint("Trouble", "Off an island, or stopped on a pond: back to the tee. Sand slows a lot, grass a little.")
        RulePoint("Sticker spots", "Kylian takes your next turn. Fire sends you to the tee. Stonks throws you up the course. Portals swap you over. Hmm. Sure. hides your aim line.")
        RulePoint("Ending a hole", "Once someone is in, the rest get three more shots. A ball still out scores one more.", last = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Show me how", {}, Modifier.weight(1f))
            PrimaryButton("Got it", {}, Modifier.weight(1f))
        }
    }
}

// ---- The lobby ------------------------------------------------------------------------------

fun mate(name: String, avatar: Avatar?, ready: Boolean, at: GameKind?) = PlayerInfo(
    name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null,
    progress = null, finishMs = null, left = false, idleMs = 0, online = true, at = at,
)

/** Ready (or Rematch) with everyone's picture and a light each; green once the others are ready. */
@Composable
fun ReadyButton(label: String, sub: String?, othersReady: Boolean, mineReady: Boolean = false, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val fill = if (othersReady) p.good else p.primary
    val ink = if (othersReady) readableOn(p.good) else p.onPrimary
    Row(
        modifier.height(58.dp).pressedEdge(p.ink, Radius.L).clip(Shapes.L).background(fill).border(2.dp, p.ink, Shapes.L).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Faces(listOf(Avatar.FOX, Avatar.PANDA), size = 30.dp, step = 20.dp, lights = listOf(mineReady, othersReady))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = ink, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = ink.copy(alpha = 0.85f), fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The bar that stays at the bottom of a lobby: Ready within reach whatever is on screen, and React beside it. */
@Composable
fun ReadyBar() {
    val p = AppTheme.palette
    Column {
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.ink))
        Row(Modifier.fillMaxWidth().background(p.card).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadyButton("Ready", "Maya is ready: tap to start", othersReady = true, modifier = Modifier.weight(1f))
            RoundButton(50.dp) { Icon(Icons.Filled.Face, null, tint = p.text, modifier = Modifier.size(24.dp)) }
        }
    }
}

/** The proposed lobby: the game's whole picture, who is here (the code tucked into a corner), the setup as rows of choices, Ready pinned below. */
@Composable
fun LobbyNew(sudoku: Boolean = false) {
    val p = AppTheme.palette
    val kind = if (sudoku) GameKind.SUDOKU else GameKind.DOMINOES
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(kind.label, {})
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyArt(kind, Modifier.width(150.dp).aspectRatio(ART).clip(Shapes.M).border(2.dp, p.ink, Shapes.M))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(kind.tagline, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, lineHeight = LineHeight.Body)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Fact("2–4 players")
                        Fact(if (sudoku) "10 min" else "15 min")
                    }
                }
            }
            Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Players", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Row(
                            Modifier.clip(Shapes.Full).background(p.accent).border(1.5.dp, p.ink, Shapes.Full).padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Room K7Q2NB", color = p.text, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Icon(Icons.Filled.Share, null, tint = p.text, modifier = Modifier.size(14.dp))
                        }
                    }
                    PlayerRow(mate("Sam", Avatar.FOX, false, kind), isMe = true, color = p.primary, reaction = null, seat = 0, game = kind)
                    PlayerRow(mate("Maya", Avatar.PANDA, true, kind), isMe = false, color = OpponentColor, reaction = null, seat = 1, game = kind)
                }
            }
            Surface(color = p.card, shape = Shapes.L, border = BorderStroke(1.dp, p.hairline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (sudoku) {
                        Text("Setup", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                        OptionRow("Play", listOf("Race", "Together"), 0)
                        Row {
                            Text("Level", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp).padding(top = 10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Segmented(listOf("Beginner", "Easy", "Medium"), -1)
                                Segmented(listOf("Hard", "Expert", "Master"), 0)
                            }
                        }
                        ShapePicker(SudokuShape.CLASSIC, enabled = true) {}
                        Note("You opened Sudoku, so you set it up. Maya sees your choices and taps Ready.")
                    } else {
                        Text("Rules", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                        OptionRow("When stuck", listOf("Draw", "Pass"), 0, note = "Take tiles from the boneyard until one fits.")
                        OptionRow("Play to", listOf("50", "100", "150"), 1)
                        OptionRow("Sides", listOf("Each alone", "2 v 2"), 0, off = setOf(1), note = "2 v 2 needs four players.")
                    }
                }
            }
        }
        ReadyBar()
    }
}

// ---- In the game, and its end ---------------------------------------------------------------

private val C4 = listOf(".......", ".......", "...o...", "..mo...", ".mooo..", "mmomo.o").joinToString("")
private val C4Won = listOf(".......", ".......", "...o...", "..mo...", ".moom..", "mmmmoo.").joinToString("")
private val C4Lost = listOf(".......", ".......", "...m...", "..om...", ".ommo..", "oooomm.").joinToString("")

/** Tonight's score, small, in a game's title row. */
@Composable
fun TonightPill(mine: Int, theirs: Int) {
    val p = AppTheme.palette
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow("Tonight")
        Row(
            Modifier.clip(Shapes.Full).background(p.card).border(1.5.dp, p.ink, Shapes.Full).padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallFace(Avatar.FOX, 22.dp)
            Text("$mine – $theirs", style = Digits, color = p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            SmallFace(Avatar.PANDA, 22.dp)
        }
    }
}

/** "Your turn", big; what to do under it for your first turns only, then it steps back. */
@Composable
fun TurnBannerNew(hint: String?) {
    val p = AppTheme.palette
    val c = GameKind.CONNECT4.color
    Surface(color = c, shape = Shapes.L, border = BorderStroke(3.dp, p.ink), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = if (hint != null) 9.dp else 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("● Your turn", color = readableOn(c), fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
            if (hint != null) Text(hint, color = readableOn(c).copy(alpha = 0.9f), fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Tonight's score on the result: the winner's number goes up by one. */
@Composable
fun TallyRow(mine: Int, theirs: Int, bumpMine: Boolean) {
    val p = AppTheme.palette
    Surface(color = p.accent, shape = Shapes.M, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Eyebrow("Tonight") }
            SmallFace(Avatar.FOX, 28.dp)
            Text(
                "  $mine", style = Digits, color = if (bumpMine) p.primaryText else p.text, fontSize = TextSize.DisplayM,
                fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
            )
            Text("  –  ", color = p.muted, fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
            Text(
                "$theirs  ", style = Digits, color = if (!bumpMine) p.opponentText else p.text, fontSize = TextSize.DisplayM,
                fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
            )
            SmallFace(Avatar.PANDA, 28.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "+1", color = if (bumpMine) p.onPrimary else Board.Ink, fontSize = TextSize.Label, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(Shapes.Full).background(if (bumpMine) p.primary else OpponentColor).border(1.5.dp, p.ink, Shapes.Full).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** A record that just fell, said in one gold line. */
@Composable
fun RecordBadge(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.Full).background(Board.GlowSoft).border(1.5.dp, Board.Ink, Shapes.Full).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MarkIcon(Mark.Star, Modifier.size(14.dp), color = Board.Ink)
        Text(text, color = Board.Ink, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The result as a card under the board: who won (with their face), tonight's score, a record if one fell, Rematch. */
@Composable
fun ResultCard(win: Boolean) {
    val p = AppTheme.palette
    Surface(color = p.card, shape = Shapes.L, border = BorderStroke(3.dp, p.ink), modifier = Modifier.fillMaxWidth().pressedEdge(p.ink, Radius.L, 4.dp)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WinnerFace(if (win) Avatar.FOX else Avatar.PANDA, 50.dp, if (win) p.primary else OpponentColor)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (win) "You win!" else "Maya wins", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
                    Text(if (win) "Four in a row, in 11 discs" else "Maya got four in a row", color = p.muted, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold)
                }
                ResultAnimation(GameKind.CONNECT4, if (win) Outcome.WIN else Outcome.LOSE, Modifier.width(84.dp))
            }
            TallyRow(if (win) 3 else 2, if (win) 2 else 3, bumpMine = win)
            if (win) RecordBadge("Your quickest win at Connect Four: 11 discs")
            ReadyButton("Rematch", if (win) "Maya is ready: tap to start" else null, othersReady = win, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Change", {}, Modifier.weight(1f))
                SecondaryButton("Another game", {}, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Connect Four against Maya, proposed: tonight's score in the title row, a turn banner that fits
 * one line, and at the end the result under the board, which steps up to make room.
 */
@Composable
fun GameNew(hint: String? = "Slide to a column and let go", finished: String? = null, tray: Boolean = false, bubble: String? = null) {
    val over = finished != null
    val win = finished == "me"
    GameFrame(
        title = "Connect Four",
        subtitle = "Game 6",
        players = listOf(
            ChipPlayer("You", Avatar.FOX, MyDisc, "Red", active = !over, mark = seatMark(0)),
            ChipPlayer("Maya", Avatar.PANDA, TheirDisc, "Yellow", mark = seatMark(1)),
        ),
        glow = GameKind.CONNECT4.color,
        menu = GameMenu(GameKind.CONNECT4, onRules = {}, onLeave = {}),
        tools = listOf(DockTool("Last move", GameIcons.Eye, enabled = over) {}),
        onReact = {},
        trailing = { TonightPill(if (over && win) 3 else 2, if (over && !win) 3 else 2) },
        trayVisible = tray,
        tray = { ReactTrayNew() },
    ) {
        if (bubble != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { Bubble(bubble) }
        if (!over) TurnBannerNew(hint)
        BoardArea(1f) { size ->
            ConnectFourBoard(
                if (!over) C4 else if (win) C4Won else C4Lost,
                winLine = if (over) listOf(35, 36, 37, 38) else emptyList(), lastCell = null, onDrop = null, modifier = size,
            )
        }
        if (over) ResultCard(win)
    }
}

/** "Another game": the shelf as a sheet, with whose pick it is (whoever lost) said first. */
@Composable
fun BoxScope.AnotherGameSheet() {
    val p = AppTheme.palette
    SheetOver {
        Text("Another game", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().clip(Shapes.M).background(p.accent).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallFace(Avatar.PANDA, 30.dp)
            Text("Maya lost that one, so Maya picks. You can still pick if she'd rather.", color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        listOf(listOf(GameKind.LUDO, GameKind.WORDS, GameKind.SHIPS), listOf(GameKind.DOMINOES, GameKind.CLASH, GameKind.GOLF)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { ArtTile(it, Modifier.weight(1f), seats = seatsOf(it)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("All games", {}, Modifier.weight(1f))
            SecondaryButton("Surprise us", {}, Modifier.weight(1f))
        }
    }
}

// ---- Confetti, for the month's cup ------------------------------------------------------------

/** Scraps of coloured paper falling, for a finale. */
@Composable
fun Confetti(modifier: Modifier = Modifier, count: Int = 46) {
    val colors = listOf(Pieces.Red, Pieces.Yellow, Pieces.Green, Pieces.Blue, Pieces.Purple, Pieces.Pink)
    Canvas(modifier) {
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
