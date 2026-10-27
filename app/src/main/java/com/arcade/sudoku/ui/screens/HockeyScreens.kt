package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Board
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.game.hockey.Body
import com.arcade.sudoku.game.hockey.Hockey
import com.arcade.sudoku.game.hockey.HockeyFrame
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloHockeyUiState
import com.arcade.sudoku.ui.SoloHockeyViewModel
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.theme.AppTheme

private val Ice = Board.Ice
private val Line = Board.IceLine
private val Goal = Board.Ink
private val Puck = Board.Ink

/**
 * The table, you at the bottom. Put a finger anywhere and your paddle follows it (it stays in
 * your half). [onMove] gets table coordinates.
 */
@Composable
fun HockeyRink(frame: HockeyFrame, myColor: Color, theirColor: Color, onMove: ((Float, Float) -> Unit)?, modifier: Modifier = Modifier) {
    val move by rememberUpdatedState(onMove)
    val touch = if (onMove == null) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val scale = size.width / Hockey.W
            move?.invoke(down.position.x / scale, down.position.y / scale)
            while (true) {
                val event = awaitPointerEvent()
                val p = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!p.pressed) break
                p.consume()
                move?.invoke(p.position.x / scale, p.position.y / scale)
            }
        }
    }
    Canvas(modifier.clip(Shapes.L).then(touch)) {
        val k = size.width / Hockey.W
        fun at(b: Body) = Offset(b.x * k, b.y * k)
        drawRect(Ice)
        // Centre line and circle, and the two goal mouths.
        drawLine(Line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx())
        drawCircle(Line, 0.18f * k, Offset(size.width / 2, size.height / 2), style = Stroke(3.dp.toPx()))
        val goal = Hockey.GOAL_W * k
        val depth = 8.dp.toPx()
        drawRoundRect(Goal, Offset((size.width - goal) / 2, -depth), Size(goal, depth * 2), CornerRadius(depth))
        drawRoundRect(Goal, Offset((size.width - goal) / 2, size.height - depth), Size(goal, depth * 2), CornerRadius(depth))
        // Paddles: theirs at the top, yours at the bottom.
        for ((b, c) in listOf(frame.top to theirColor, frame.bottom to myColor)) {
            drawCircle(c, Hockey.PADDLE_R * k, at(b))
            drawCircle(Color.White.copy(alpha = 0.35f), Hockey.PADDLE_R * 0.55f * k, at(b))
            drawCircle(Goal, Hockey.PADDLE_R * k, at(b), style = Stroke(2.dp.toPx()))
        }
        drawCircle(Puck, Hockey.PUCK_R * k, at(frame.puck))
        drawCircle(Color.White.copy(alpha = 0.25f), Hockey.PUCK_R * 0.5f * k, at(frame.puck))
    }
}

// ---- Against your partner ---------------------------------------------------------------------

/** Air Hockey against your partner, in real time. */
@Composable
fun HockeyScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val frame by vm.hockey.collectAsStateWithLifecycle()
    val shown = frame ?: HockeyFrame.start()
    val now = rememberTicker()
    val phase = state.phase(state.serverNow())
    val finished = room.status == RoomStatus.FINISHED
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.HOCKEY) { showRules = false }
    val opponent = room.opponent
    // While playing, the live table has the latest score; at the end, the server's.
    val mine = if (finished) room.hockey?.mine ?: shown.bottomScore else shown.bottomScore
    val theirs = if (finished) room.hockey?.theirs ?: shown.topScore else shown.topScore

    GameFrame(
        title = "Air Hockey",
        subtitle = "First to ${Hockey.GOALS}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(room, state, now, MyDisc, detail = { _, isMe -> if (isMe) "$mine goals" else "$theirs goals" }),
        glow = GameKind.HOCKEY.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = opponent?.name ?: "Your opponent"
            MatchPopup(
                room, GameKind.HOCKEY, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: name),
                when (room.endReason) {
                    "goals" -> "$mine – $theirs"
                    "forfeit" -> if (room.winner == "me") "$name left the game." else "You left the game."
                    "timeout" -> if (room.winner == "me") "$name lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
            )
        },
    ) {
        BoardArea(Hockey.W / Hockey.H) { size ->
            Box(size, contentAlignment = Alignment.Center) {
                HockeyRink(shown, MyDisc, TheirDisc, onMove = if (finished) null else vm::moveHockeyPaddle, modifier = Modifier.fillMaxSize())
                if (phase == RoomStatus.COUNTDOWN) {
                    val seconds = (((room.startAt ?: 0L) - state.serverNow() + 999) / 1000).coerceAtLeast(1)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Get ready", color = p.muted, fontSize = 18.sp)
                        Text(seconds.toString(), color = p.primary, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---- On your own ------------------------------------------------------------------------------

/** Air Hockey against the computer. */
@Composable
fun SoloHockeyScreen(state: SoloHockeyUiState, vm: SoloHockeyViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.HOCKEY) { showRules = false }
    val level = state.level
    val levels = listOf("Easy", "Medium", "Hard")
    val f = state.frame
    val result = state.result
    GameFrame(
        title = "Air Hockey",
        subtitle = level?.let { "Against the computer · ${levels[it]}" } ?: "Against the computer",
        players = if (level == null) emptyList() else listOf(
            ChipPlayer("You", null, MyDisc, "${f.bottomScore} goals"),
            ChipPlayer("Computer", null, TheirDisc, "${f.topScore} goals"),
        ),
        glow = GameKind.HOCKEY.color,
        dock = soloDock(onHome, { showRules = true }, if (level != null) vm::changeLevel else null),
        scrollable = level == null,
        popup = if (level == null || result == null) null else popupContent {
            SoloPopup(
                GameKind.HOCKEY, result, "${f.bottomScore} – ${f.topScore}",
                onAgain = vm::again, onHome = onHome, finishLabel = "Change level", onFinish = vm::changeLevel,
            )
        },
    ) {
        if (level == null) {
            AppCard("Choose the computer's level") {
                Text("Drag your paddle to knock the puck into the computer's goal. First to ${Hockey.GOALS} wins.", color = p.muted, fontSize = 14.sp)
                levels.forEachIndexed { i, label -> PrimaryButton(label, { vm.start(i) }, Modifier.fillMaxWidth()) }
            }
        } else {
            BoardArea(Hockey.W / Hockey.H) { size ->
                HockeyRink(f, MyDisc, TheirDisc, onMove = if (result == null) vm::move else null, modifier = size)
            }
        }
    }
}
