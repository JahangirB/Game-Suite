package com.pippop.ui.screens

import com.pippop.game.dots.DotsShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.State
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import com.pippop.game.memory.MemoryMatch
import com.pippop.game.memory.CardFace
import com.pippop.ui.theme.readableOn
import com.pippop.ui.Sfx
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.textFill
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.GameColors
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Pieces
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import com.pippop.ui.theme.LocalReduceMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.connect4.C4Level
import com.pippop.game.connect4.ConnectFour
import com.pippop.game.dots.Dots
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloC4UiState
import com.pippop.ui.startWith
import com.pippop.ui.SoloConnectFourViewModel
import com.pippop.ui.SoloDotsUiState
import com.pippop.ui.SoloDotsViewModel
import com.pippop.ui.SoloMemoryUiState
import com.pippop.ui.SoloMemoryViewModel
import com.pippop.ui.SoloTttUiState
import com.pippop.ui.SoloTttViewModel
import com.pippop.ui.art.AvatarImage
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.art.drawAvatar
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont

/** Your discs and lines are red and your opponent's yellow, on both phones. */
val MyDisc = Pieces.Red
val TheirDisc = Pieces.Yellow

/** A board mark's colour: yours red, then the others yellow, green and blue in seat order. */
fun markColor(mark: Char): Color = when (mark) {
    'm' -> MyDisc
    'o' -> TheirDisc
    'p' -> Pieces.Green
    'q' -> Pieces.Blue
    else -> Color.Gray
}
private val BoardBlue = Board.ConnectBlue
private val TheirLine = GameColors.TheirLine

// ---- Connect Four -------------------------------------------------------------------------

/**
 * A Connect Four board. [board] holds 'm' (your discs), 'o' (the opponent's) and '.'. The
 * [lastCell] disc falls into place and [winLine] glows. With [onDrop] set, a ghost disc
 * follows your finger along the top and drops into that column when you let go.
 */
@Composable
fun ConnectFourBoard(board: String, winLine: List<Int>, lastCell: Int?, onDrop: ((Int) -> Unit)?, modifier: Modifier = Modifier, replay: Int = 0) {
    val p = AppTheme.palette
    // [replay] going up drops the last disc again: the dock's "Last move".
    val fall = remember(lastCell, board, replay) { Animatable(if (lastCell == null) 1f else 0f) }
    // It speeds up as it falls, then bounces once in its hole.
    LaunchedEffect(lastCell, board, replay) {
        fall.animateTo(1f, keyframes {
            durationMillis = 470
            1f at 320 using FastOutLinearInEasing
            0.93f at 395
            1f at 470
        })
    }
    // The disc rattles down the slot and clacks into its hole as it lands.
    SoundOnChange(lastCell) {
        val v = if (board.getOrNull(lastCell ?: -1) == 'm') 1f else Sfx.OTHERS
        play(Sound.C4_DROP, v)
        delay(320)
        play(Sound.C4_LAND, v)
    }
    val glow by loopingFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), still = 1f, label = "winGlow")
    // Four in a row: once the last disc has landed, a line is drawn through the four (0 to 1)
    // and a burst plays at its end (0 to 1).
    val still = LocalReduceMotion.current
    val line = remember(winLine) { Animatable(0f) }
    val sparks = remember(winLine) { Animatable(0f) }
    LaunchedEffect(winLine) {
        if (winLine.size < 2) return@LaunchedEffect
        if (still) {
            line.snapTo(1f)
            return@LaunchedEffect
        }
        delay(470)
        line.animateTo(1f, tween(380))
        sparks.animateTo(1f, tween(520))
    }
    var hover by remember { mutableStateOf<Int?>(null) }
    val pick = if (onDrop == null) Modifier else Modifier.pointerInput(onDrop) {
        val colAt = { x: Float -> (x / (size.width / ConnectFour.COLS.toFloat())).toInt().coerceIn(0, ConnectFour.COLS - 1) }
        awaitEachGesture {
            hover = colAt(awaitFirstDown().position.x)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                if (!change.pressed) break
                hover = colAt(change.position.x)
            }
            hover?.let(onDrop)
            hover = null
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(ConnectFour.COLS / (ConnectFour.ROWS + 1.12f)).then(pick)) {
        val cell = size.width / ConnectFour.COLS
        val top = cell
        val radius = cell * 0.38f
        val won = winLine.isNotEmpty() && fall.value >= 1f
        fun centre(i: Int) = Offset((i % ConnectFour.COLS + 0.5f) * cell, top + (i / ConnectFour.COLS + 0.5f) * cell)
        // A disc: glossy, with the pressed ring of a real one.
        fun disc(color: Color, at: Offset, r: Float = radius, alpha: Float = 1f) {
            glossyDisc(color, r, at, alpha)
            drawCircle(p.ink.copy(alpha = alpha), r, at, style = Stroke(2.5f))
            drawCircle(Color.Black.copy(alpha = 0.13f * alpha), r * 0.62f, at, style = Stroke(r * 0.09f))
        }
        hover?.let { disc(MyDisc, Offset((it + 0.5f) * cell, top * 0.5f), alpha = 0.6f) }
        // The frame: blue plastic, lighter at the top, standing on two feet with a shadow under it.
        val boardSize = Size(size.width - 4f, cell * ConnectFour.ROWS)
        val corner = cell * 0.28f
        val foot = Size(cell * 0.42f, cell * 0.16f)
        for (x in listOf(cell * 0.3f, size.width - cell * 0.3f - foot.width)) {
            drawRoundRect(BoardBlue.darker(0.35f), Offset(x, top + boardSize.height - 2f), foot, CornerRadius(cell * 0.06f))
            drawRoundRect(p.ink, Offset(x, top + boardSize.height - 2f), foot, CornerRadius(cell * 0.06f), style = Stroke(3f))
        }
        boxShadow(Offset(2f, top), boardSize, corner, drop = cell * 0.12f, strength = 0.28f)
        drawRoundRect(
            Brush.verticalGradient(listOf(BoardBlue.lighter(0.2f), BoardBlue.darker(0.16f)), startY = top, endY = top + boardSize.height),
            Offset(2f, top), boardSize, CornerRadius(corner),
        )
        drawRoundRect(p.ink, Offset(2f, top), boardSize, CornerRadius(corner), style = Stroke(4f))
        for (i in board.indices) {
            val center = centre(i)
            // A hole: the far wall of the frame shows as a darker rim at its top.
            drawCircle(BoardBlue.darker(0.42f), radius * 1.06f, center + Offset(0f, radius * 0.07f))
            drawCircle(p.background, radius, center)
            drawCircle(p.ink.copy(alpha = 0.5f), radius, center, style = Stroke(2.5f))
            val color = when (board[i]) {
                'm' -> MyDisc
                'o' -> TheirDisc
                else -> continue
            }
            // The newest disc falls from above the board into its hole.
            val at = if (i == lastCell) Offset(center.x, -radius + (center.y + radius) * fall.value) else center
            // Once someone has four, those four beat and the rest step back.
            val inLine = won && i in winLine
            disc(color, at, if (inLine) radius * (1f + (glow - 0.35f) * 0.09f) else radius, if (won && !inLine) 0.4f else 1f)
        }
        if (won && winLine.size >= 2) {
            // A line drawn through the four, from the first to the last, then a burst at its end.
            val from = centre(winLine.first())
            val to = centre(winLine.last())
            val end = from + (to - from) * line.value
            drawLine(p.ink, from, end, cell * 0.2f, StrokeCap.Round)
            drawLine(Color.White, from, end, cell * 0.13f, StrokeCap.Round)
            burst(end, sparks.value, Pieces.Yellow, reach = cell * 0.75f)
        }
    }
}

// ---- Ultimate Tic-Tac-Toe -----------------------------------------------------------------

private val TttBlue = Board.TttBlue
private val Target = Board.Glow
private const val MARK_INK_MS = 200

/**
 * How far the newest mark has been drawn by hand (0 to 1). [replay] going up draws it again: the
 * dock's "Last move", at twice the time so it is easy to see. A mark already there when the board
 * appeared (or with reduce motion on) is whole.
 */
@Composable
private fun rememberMarkInk(lastCell: Int?, replay: Int): State<Float> {
    val still = LocalReduceMotion.current
    val opening = remember { object { var cell: Int? = lastCell } }
    val ink = remember(lastCell, replay) { Animatable(if (still || lastCell == null || (replay == 0 && lastCell == opening.cell)) 1f else 0f) }
    LaunchedEffect(lastCell, replay) { ink.animateTo(1f, tween(if (replay > 0) MARK_INK_MS * 2 else MARK_INK_MS, easing = LinearEasing)) }
    SideEffect { if (lastCell != opening.cell) opening.cell = null }
    return ink.asState()
}

/**
 * One X ('m') or O ('o') with an outline in [outlineColor] (the palette's ink), drawn as if with a marker: [ink] (0 to 1) is how
 * much is down. An X is two quick strokes one after the other, an O one loop from the top going
 * round. Ends are round.
 */
private fun DrawScope.drawMark(kind: Char, center: Offset, k: Float, width: Float, outline: Float, ink: Float, outlineColor: Color) {
    when (kind) {
        'm' -> for (color in listOf(outlineColor, MyDisc)) {
            val w = if (color == MyDisc) width else width + outline
            val a = center + Offset(-k, -k)
            val b = center + Offset(k, k)
            val c = center + Offset(k, -k)
            val d = center + Offset(-k, k)
            drawLine(color, a, a + (b - a) * (ink * 2f).coerceIn(0f, 1f), w, StrokeCap.Round)
            if (ink > 0.5f) drawLine(color, c, c + (d - c) * ((ink - 0.5f) * 2f).coerceIn(0f, 1f), w, StrokeCap.Round)
        }
        'o' -> for (color in listOf(outlineColor, TheirDisc)) {
            val w = if (color == TheirDisc) width else width + outline
            if (ink >= 1f) drawCircle(color, k, center, style = Stroke(w))
            else drawArc(color, -90f, 360f * ink, false, center - Offset(k, k), Size(2 * k, 2 * k), style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/**
 * An Ultimate Tic-Tac-Toe board: [board] holds 81 cells ('m' your X, 'o' their O, '.'), small
 * board by small board; [boards] each small board's winner ('d' drawn). While [playing], the
 * board(s) the next move must go in glow, and when [next] names one board the other open boards
 * are dimmed. The newest mark is drawn by hand, as if with a marker. With [onMark] set, tapping
 * a square marks it.
 */
@Composable
fun TttBoard(
    board: String,
    boards: String,
    next: Int?,
    winLine: List<Int>,
    lastCell: Int?,
    playing: Boolean,
    onMark: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    replay: Int = 0,
) {
    if (board.length == 9) return ClassicTttBoard(board, winLine, lastCell, onMark, modifier, replay)
    val p = AppTheme.palette
    val ink = rememberMarkInk(lastCell, replay)
    SoundOnChange(lastCell) { tttSound(board.getOrNull(lastCell ?: -1)) }
    val pulse by loopingFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), still = 1f, label = "targetPulse")
    val tap = if (onMark == null) Modifier else Modifier.pointerInput(onMark) {
        detectTapGestures { at ->
            val big = size.width / 3f
            val gap = big * 0.07f
            val small = (big - 2 * gap) / 3f
            val bx = (at.x / big).toInt().coerceIn(0, 2)
            val by = (at.y / big).toInt().coerceIn(0, 2)
            val cx = ((at.x - bx * big - gap) / small).toInt().coerceIn(0, 2)
            val cy = ((at.y - by * big - gap) / small).toInt().coerceIn(0, 2)
            onMark((by * 3 + bx) * 9 + cy * 3 + cx)
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).then(tap)) {
        val big = size.width / 3f
        val gap = big * 0.07f
        val small = (big - 2 * gap) / 3f
        val corner = CornerRadius(big * 0.12f)
        drawRoundRect(p.ink, Offset(0f, 6f), Size(size.width, size.height - 6f), corner)
        drawRoundRect(TttBlue, Offset.Zero, Size(size.width, size.height - 6f), corner)
        drawRoundRect(p.ink, Offset.Zero, Size(size.width, size.height - 6f), corner, style = Stroke(4f))
        fun origin(b: Int) = Offset((b % 3) * big + gap, (b / 3) * big + gap)
        fun mark(kind: Char, center: Offset, r: Float, width: Float, inked: Float = 1f) = drawMark(kind, center, r, width, 5f, inked, p.ink)
        // Lines between the small boards.
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = 0.85f), Offset(i * big, gap * 0.6f), Offset(i * big, size.height - 6f - gap * 0.6f), 5f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.85f), Offset(gap * 0.6f, i * big), Offset(size.width - gap * 0.6f, i * big), 5f, StrokeCap.Round)
        }
        for (b in 0 until 9) {
            val o = origin(b)
            val target = playing && boards[b] == '.' && (next == null || next == b)
            // With one board to play in, the other open boards step back (the newest mark stays bright).
            val dim = playing && next != null && next != b && boards[b] == '.'
            if (target) {
                drawRoundRect(Target.copy(alpha = 0.3f * pulse), o - Offset(gap * 0.4f, gap * 0.4f), Size(3 * small + gap * 0.8f, 3 * small + gap * 0.8f), CornerRadius(gap))
                drawRoundRect(Target.copy(alpha = pulse), o - Offset(gap * 0.4f, gap * 0.4f), Size(3 * small + gap * 0.8f, 3 * small + gap * 0.8f), CornerRadius(gap), style = Stroke(4f))
            }
            for (i in 1..2) {
                drawLine(Color.White.copy(alpha = 0.45f), o + Offset(i * small, 0f), o + Offset(i * small, 3 * small), 2.5f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.45f), o + Offset(0f, i * small), o + Offset(3 * small, i * small), 2.5f, StrokeCap.Round)
            }
            fun cell(c: Int) {
                val center = o + Offset((c % 3 + 0.5f) * small, (c / 3 + 0.5f) * small)
                if (b * 9 + c == lastCell) drawCircle(Color.White.copy(alpha = 0.22f), small * 0.46f, center)
                mark(board.getOrElse(b * 9 + c) { '.' }, center, small * 0.26f, small * 0.11f, if (b * 9 + c == lastCell) ink.value else 1f)
            }
            for (c in 0 until 9) if (!(dim && b * 9 + c == lastCell)) cell(c)
            if (dim) {
                drawRoundRect(TttBlue.copy(alpha = 0.35f), o - Offset(gap * 0.3f, gap * 0.3f), Size(3 * small + gap * 0.6f, 3 * small + gap * 0.6f), CornerRadius(gap))
                if (lastCell != null && lastCell / 9 == b) cell(lastCell % 9)
            }
            // A decided small board: dimmed, with its winner's big mark on top.
            if (boards[b] != '.') {
                drawRoundRect(TttBlue.copy(alpha = 0.72f), o - Offset(gap * 0.3f, gap * 0.3f), Size(3 * small + gap * 0.6f, 3 * small + gap * 0.6f), CornerRadius(gap))
                mark(boards[b], o + Offset(1.5f * small, 1.5f * small), small * 1.05f, small * 0.3f)
            }
        }
        if (winLine.size == 3) {
            val center = { b: Int -> Offset((b % 3 + 0.5f) * big, (b / 3 + 0.5f) * big) }
            drawLine(Color.White.copy(alpha = pulse), center(winLine.first()), center(winLine.last()), big * 0.09f, StrokeCap.Round)
        }
    }
}

/** Normal Tic-Tac-Toe: one 3 × 3 board; [board] holds 9 cells ('m' your X, 'o' their O). */
@Composable
private fun ClassicTttBoard(board: String, winLine: List<Int>, lastCell: Int?, onMark: ((Int) -> Unit)?, modifier: Modifier, replay: Int = 0) {
    val p = AppTheme.palette
    val ink = rememberMarkInk(lastCell, replay)
    SoundOnChange(lastCell) { tttSound(board.getOrNull(lastCell ?: -1)) }
    val tap = if (onMark == null) Modifier else Modifier.pointerInput(onMark) {
        detectTapGestures { at ->
            val step = size.width / 3f
            onMark((at.y / step).toInt().coerceIn(0, 2) * 3 + (at.x / step).toInt().coerceIn(0, 2))
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).then(tap)) {
        val step = size.width / 3f
        val corner = CornerRadius(step * 0.2f)
        drawRoundRect(p.ink, Offset(0f, 6f), Size(size.width, size.height - 6f), corner)
        drawRoundRect(TttBlue, Offset.Zero, Size(size.width, size.height - 6f), corner)
        drawRoundRect(p.ink, Offset.Zero, Size(size.width, size.height - 6f), corner, style = Stroke(4f))
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = 0.85f), Offset(i * step, step * 0.15f), Offset(i * step, size.height - step * 0.15f), 7f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.85f), Offset(step * 0.15f, i * step), Offset(size.width - step * 0.15f, i * step), 7f, StrokeCap.Round)
        }
        for (c in 0 until 9) {
            val center = Offset((c % 3 + 0.5f) * step, (c / 3 + 0.5f) * step)
            drawMark(board[c], center, step * 0.28f, step * 0.09f, 6f, if (c == lastCell) ink.value else 1f, p.ink)
        }
        if (winLine.size == 3) {
            val center = { c: Int -> Offset((c % 3 + 0.5f) * step, (c / 3 + 0.5f) * step) }
            drawLine(Color.White, center(winLine.first()), center(winLine.last()), step * 0.1f, StrokeCap.Round)
        }
    }
}

/** Tic-Tac-Toe's mode: the host picks normal 3 × 3 or Ultimate; everyone else sees which. */
@Composable
fun TttModeCard(room: RoomState, busy: Boolean, onPick: (Boolean) -> Unit) {
    OptionsCard(room, "Which game?", busy) { enabled ->
        OptionRow(
            "Game", listOf(false to "Ultimate", true to "Normal"), room.tttClassic, onPick, enabled = enabled,
            note = if (room.tttClassic) "One 3 × 3 board." else "Nine boards in one.",
        )
    }
}

// ---- Dots & Boxes -------------------------------------------------------------------------

/**
 * A Dots & Boxes board: 5 × 5 dots. [lines] and [boxes] hold 'm' (yours), 'o' (the
 * opponent's), 'p', 'q' (the others) or '.'; every line keeps its owner's colour. A new line is
 * inked from one dot to the next, and a claimed box pops in as a tile with its owner's picture
 * and a floating "+1". Lines and boxes already there when the board appears just show. With
 * [onLine] set, the line under your finger shows faintly and is drawn when you let go.
 */
@Composable
fun DotsBoard(
    lines: String,
    boxes: String,
    lastLine: Int?,
    /** Each owner mark's picture, shown in the boxes they claim. */
    avatars: Map<Char, Avatar?>,
    onLine: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    replay: Int = 0,
    /** The board: 4 × 4, 5 × 5, 6 × 6 or the heart. */
    shape: DotsShape = DotsShape.from("4x4"),
) {
    val p = AppTheme.palette
    val still = LocalReduceMotion.current
    // The newest line is inked from one dot to the next (0 to 1); [replay] going up inks it
    // again: the dock's "Last line". A line already there when the board appeared is whole.
    val opening = remember { object { var line: Int? = lastLine } }
    val ink = remember(lastLine, replay) { Animatable(if (still || lastLine == null || (replay == 0 && lastLine == opening.line)) 1f else 0f) }
    LaunchedEffect(lastLine, replay) { ink.animateTo(1f, tween(DOT_INK_MS, easing = LinearEasing)) }
    SideEffect { if (lastLine != opening.line) opening.line = null }
    // Boxes popping in: how far each one's pop (0 to 1) and its floating label (0 to 1) have got,
    // and which number its label reads. A box with no entry yet is not shown; ones owned when
    // the board appeared start finished.
    fun owned(box: Int) = boxes.getOrNull(box).let { it != null && it != '.' && it != ' ' }
    val pop = remember { mutableStateMapOf<Int, Float>().apply { for (b in 0 until shape.boxes) if (owned(b)) put(b, 1f) } }
    val rise = remember { mutableStateMapOf<Int, Float>() }
    val tally = remember { mutableStateMapOf<Int, Int>() }
    val popScope = rememberCoroutineScope()
    // The running pop of each box, so a box cleared by a new game stops animating at once.
    val popJobs = remember { mutableMapOf<Int, Job>() }
    LaunchedEffect(boxes) {
        for (b in pop.keys.toList()) if (!owned(b)) { popJobs.remove(b)?.cancel(); pop.remove(b); rise.remove(b); tally.remove(b) }
        val fresh = (0 until shape.boxes).filter { owned(it) && it !in pop }
        for ((k, box) in fresh.withIndex()) {
            tally[box] = k + 1
            if (still) { pop[box] = 1f; continue }
            // Hidden until its turn; the line lands first, then the boxes pop one after another.
            pop[box] = 0f
            popJobs[box] = popScope.launch {
                delay(DOT_LINE_MS + k * DOT_POP_GAP_MS)
                launch { animate(0f, 1f, animationSpec = tween(DOT_FLOAT_MS, easing = LinearEasing)) { v, _ -> rise[box] = v } }
                animate(0f, 1f, animationSpec = tween(DOT_POP_MS, easing = LinearEasing)) { v, _ -> pop[box] = v }
            }
        }
    }
    val labels = rememberTextMeasurer()
    SoundOnChange(lastLine) { play(Sound.MARK, if (lines.getOrNull(lastLine ?: -1) == 'm') 1f else Sfx.OTHERS) }
    // A box closed: a stamp and a chime, and the same player goes again.
    SoundOnChange(boxes.count { it != '.' }.takeIf { it > 0 }) { play(Sound.DOTS_BOX) }
    // Under the finger now, and the line chosen by the last tap: a tap lights up the nearest free
    // line, and a second tap on it draws it, so a stray touch never plays a line.
    var preview by remember { mutableStateOf<Int?>(null) }
    var chosen by remember(lines) { mutableStateOf<Int?>(null) }
    val free by rememberUpdatedState(lines)
    val g = shape
    val pick = if (onLine == null) Modifier else Modifier.pointerInput(onLine, g) {
        /** The free line whose middle is nearest [at], within most of a box. */
        fun lineAt(at: Offset): Int? {
            val margin = size.width * 0.08f
            val step = (size.width - 2 * margin) / g.w
            fun middle(line: Int) = if (line < g.horizontal) {
                Offset(margin + (line % g.w + 0.5f) * step, margin + (line / g.w) * step)
            } else {
                val l = line - g.horizontal
                Offset(margin + (l % (g.w + 1)) * step, margin + (l / (g.w + 1) + 0.5f) * step)
            }
            return (0 until g.lines).filter { free.getOrNull(it) == '.' }
                .minByOrNull { (middle(it) - at).getDistance() }
                ?.takeIf { (middle(it) - at).getDistance() < step * 0.75f }
        }
        awaitEachGesture {
            preview = lineAt(awaitFirstDown().position)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                if (!change.pressed) break
                preview = lineAt(change.position)
            }
            val line = preview
            preview = null
            when {
                line == null -> chosen = null
                line == chosen -> {
                    chosen = null
                    onLine(line)
                }
                else -> chosen = line
            }
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(dotsAspect(g)).then(pick)) {
        val margin = size.width * 0.08f
        val step = (size.width - 2 * margin) / g.w
        fun dot(r: Int, c: Int) = Offset(margin + c * step, margin + r * step)
        fun ends(line: Int) = if (line < g.horizontal) {
            dot(line / g.w, line % g.w) to dot(line / g.w, line % g.w + 1)
        } else {
            val l = line - g.horizontal
            dot(l / (g.w + 1), l % (g.w + 1)) to dot(l / (g.w + 1) + 1, l % (g.w + 1))
        }
        for (box in 0 until g.boxes) {
            val owner = boxes.getOrNull(box) ?: continue
            if (owner == '.' || owner == ' ') continue
            val popped = pop[box] ?: continue
            val corner = dot(box / g.w, box % g.w)
            val middle = corner + Offset(step / 2, step / 2)
            // The tile lands with a small overshoot: it swells past full size, then settles.
            val grow = popped - 1f
            val land = 1f + 2.70158f * grow * grow * grow + 1.70158f * grow * grow
            scale(land, land, pivot = middle) {
                raisedTile(
                    markColor(owner),
                    corner + Offset(step * 0.08f, step * 0.08f), Size(step * 0.84f, step * 0.84f), step * 0.14f, lift = step * 0.05f,
                )
                val avatar = avatars[owner]
                if (avatar != null) {
                    // The owner's picture in the middle of the box.
                    val pic = step * 0.56f
                    val left = corner.x + (step - pic) / 2
                    val top = corner.y + (step - pic) / 2 - step * 0.02f
                    inset(left, top, size.width - left - pic, size.height - top - pic) { drawAvatar(avatar) }
                }
            }
            burst(middle, popped, markColor(owner), reach = step * 0.7f)
        }
        for (line in 0 until g.lines) {
            val (a, b) = ends(line)
            when (lines.getOrNull(line) ?: ' ') {
                ' ' -> Unit
                '.' -> drawLine(p.lineThin, a, b, step * 0.035f, cap = StrokeCap.Round)
                else -> {
                    // Every line keeps its owner's colour (the opponent's a deeper amber, so it
                    // reads on the board); the newest is inked from dot a towards dot b.
                    val inking = line == lastLine && ink.value < 1f
                    if (inking) drawLine(p.lineThin, a, b, step * 0.035f, cap = StrokeCap.Round)
                    drawLine(
                        if (lines[line] == 'o') TheirLine else markColor(lines[line]), a, if (inking) a + (b - a) * ink.value else b,
                        // The newest line stays a little thicker, so the last move shows at a glance.
                        step * if (line == lastLine) 0.12f else 0.08f, cap = StrokeCap.Round,
                    )
                }
            }
        }
        // The chosen line glows, ready for the second tap; one under the finger shows faintly.
        chosen?.takeIf { lines.getOrNull(it) == '.' }?.let { line ->
            val (a, b) = ends(line)
            drawLine(MyDisc, a, b, step * 0.1f, cap = StrokeCap.Round)
        }
        preview?.takeIf { it != chosen && lines.getOrNull(it) == '.' }?.let { line ->
            val (a, b) = ends(line)
            drawLine(MyDisc.copy(alpha = 0.45f), a, b, step * 0.08f, cap = StrokeCap.Round)
        }
        for (r in 0..g.h) for (c in 0..g.w) {
            // A dot only where a box of the board meets it.
            if (listOf(r - 1 to c - 1, r - 1 to c, r to c - 1, r to c).none { (br, bc) -> br in 0 until g.h && bc in 0 until g.w && g.exists(br * g.w + bc) }) continue
            // A small glossy peg standing on the board.
            roundShadow(dot(r, c), step * 0.085f, strength = 0.28f)
            glossyDisc(p.ink, step * 0.085f, dot(r, c))
        }
        // "+1", "+2"... float up from the boxes just taken and fade.
        for ((box, rising) in rise) {
            if (rising <= 0f || rising >= 1f) continue
            if (!owned(box)) continue
            val owner = boxes[box]
            val text = labels.measure("+${tally[box] ?: 1}", TextStyle(color = (if (owner == 'o') TheirLine else markColor(owner)).copy(alpha = 1f - rising * rising), fontSize = (step * 0.3f).toSp(), fontWeight = FontWeight.Bold))
            val middle = dot(box / g.w, box % g.w) + Offset(step / 2, step / 2)
            drawText(text, topLeft = middle - Offset(text.size.width / 2f, text.size.height / 2f + step * 0.6f * rising))
        }
    }
}

/** How long a new line takes to ink from one dot to the next. */
private const val DOT_INK_MS = 200
/** How long after a move the first captured box starts to pop (the line has mostly landed). */
private const val DOT_LINE_MS = 180L
/** The gap between boxes popping one after another in the same move. */
private const val DOT_POP_GAP_MS = 90L
/** How long one box takes to scale in. */
private const val DOT_POP_MS = 380
/** How long a "+n" label takes to float up and fade. */
private const val DOT_FLOAT_MS = 750

/** A Dots board's width per unit of height. */
internal fun dotsAspect(g: DotsShape) = (g.w * 0.84f + 0.16f * g.w) / (g.h * 0.84f + 0.16f * g.w)

// ---- Memory Pairs -------------------------------------------------------------------------

/**
 * A Memory Pairs table: 4 × 4, 4 × 6 or 6 × 6 cards, from the deck's size. [faces] holds a
 * card's picture when it is face up or matched; [owners] 'm', 'o' or '.'. Cards turn over with
 * a flip. With [onFlip] set, tapping a face-down card flips it.
 */
@Composable
fun MemoryBoard(faces: List<CardFace?>, owners: String, onFlip: ((Int) -> Unit)?, pending: Int? = null, modifier: Modifier = Modifier) {
    // Two face-up cards nobody owns are a missed pair: the rules leave them up until the next
    // flip, but after a look they turn back here, so nobody waits on the next player.
    val missed = faces.indices.filter { faces[it] != null && owners.getOrNull(it) == '.' }.takeIf { it.size == 2 }
    var hidden by remember { mutableStateOf<List<Int>?>(null) }
    SoundOnChange(faces.count { it != null }.takeIf { it > 0 }) { play(Sound.CARD_FLIP) }
    SoundOnChange(owners.count { it != '.' }.takeIf { it > 0 }) { play(Sound.MEMORY_MATCH) }
    LaunchedEffect(missed) {
        if (missed == null) return@LaunchedEffect
        delay(MISSED_LOOK_MS)
        hidden = missed
    }
    val shownFaces = if (missed != null && missed == hidden) faces.mapIndexed { i, f -> if (i in missed) null else f } else faces
    val cols = MemoryMatch.columns(faces.size)
    val rows = (faces.size + cols - 1) / cols
    val gap = if (faces.size > 16) 5.dp else 8.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until cols) {
                    val i = row * cols + col
                    MemoryCard(shownFaces.getOrNull(i), owners.getOrNull(i) ?: '.', onFlip?.let { { it(i) } }, Modifier.weight(1f), turning = i == pending, wobble = missed?.takeIf { i in it })
                }
            }
        }
    }
}

/** How long a missed pair stays face up for everyone to see. */
private const val MISSED_LOOK_MS = 1400L

/**
 * One Memory card. Face down it shows a striped back with a star and a soft shadow, and lifts
 * a little while it turns. A pair that was just matched gets a burst in its finder's colour,
 * then shrinks and flies up and away (leaving a faded, edged place); a pair that was already
 * matched when the board appeared is simply in its place. [wobble] non-null shakes a missed card.
 */
@Composable
private fun MemoryCard(face: CardFace?, owner: Char, onFlip: (() -> Unit)?, modifier: Modifier, turning: Boolean = false, wobble: Any? = null) {
    val p = AppTheme.palette
    // Remember the last picture so the card still shows it while turning back.
    var shown by remember { mutableStateOf(face) }
    if (face != null) shown = face
    // [turning]: tapped, picture on its way from the server; the card starts turning at once.
    val turn by animateFloatAsState(if (face != null) 180f else if (turning) 70f else 0f, tween(320), label = "flip")
    val density = LocalDensity.current.density
    val edge = if (owner == '.') p.ink else markColor(owner)
    // Matched: 0 the card is in place, 1 it has flown off. Burst: 0 just matched, 1 gone.
    val matched = owner != '.'
    val flown = remember { Animatable(if (matched) 1f else 0f) }
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(matched) {
        val appeared = first
        first = false
        if (!matched) {
            flown.snapTo(0f)
            pop.snapTo(1f)
        } else if (!appeared) {
            // Let the second card finish turning, show the match, then send the pair off.
            delay(Motion.FLIP_MS.toLong())
            if (!reduce) launch { pop.snapTo(0f); pop.animateTo(1f, tween(450)) }
            delay(MATCH_LOOK_MS)
            if (reduce) flown.snapTo(1f) else flown.animateTo(1f, tween(MATCH_FLY_MS, easing = FastOutLinearInEasing))
        }
    }
    @Composable
    fun face(m: Modifier, fade: Float = 1f) = Box(
        contentAlignment = Alignment.Center,
        modifier = m.border(if (owner == '.') 2.dp else 4.dp, edge, Shapes.M),
    ) {
        // Only the picture and its card fade, never the edge that says whose pair it is.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade }.background(if (shown?.gold == true) Pieces.Gold else p.card), contentAlignment = Alignment.Center) {
            shown?.let { AvatarImage(it.avatar, Modifier.fillMaxWidth(0.78f).aspectRatio(1f)) }
        }
    }
    Box(
        modifier
            .aspectRatio(0.78f)
            .shake(wobble, delayMs = Motion.FLIP_MS)
            .drawWithContent { drawContent(); burst(center, pop.value, edge, size.width * 0.75f) },
    ) {
        // The taken place: a faded picture inside the finder's edge, once the pair has flown.
        if (matched) Box(Modifier.fillMaxSize().clip(Shapes.M)) { face(Modifier.fillMaxSize(), fade = 0.3f * flown.value) }
        val lift = sin(Math.toRadians(turn.toDouble())).toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -flown.value * size.height * 1.1f
                    scaleX = 1f - 0.5f * flown.value
                    scaleY = scaleX
                    alpha = 1f - flown.value
                }
                .drawBehind {
                    // Narrow with the turn, so no bare slab shows when the card is edge on.
                    val across = size.width * abs(cos(Math.toRadians(turn.toDouble()))).toFloat()
                    boxShadow(Offset((size.width - across) / 2, 0f), Size(across, size.height), size.width * 0.14f, drop = size.width * (0.04f + 0.06f * lift), strength = 0.2f + 0.08f * lift)
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = turn; cameraDistance = 14 * density; translationY = -lift * size.height * 0.04f }
                    .clip(Shapes.M),
            ) {
                if (turn < 90f) {
                    // The back: stripes, a frame and a star on Memory amber.
                    Canvas(Modifier.fillMaxSize().background(GameKind.MEMORY.color).border(2.dp, p.ink, Shapes.M)) {
                        val w = size.width
                        val h = size.height
                        val step = w * 0.22f
                        var x = -h
                        while (x < w) {
                            drawLine(Color.White.copy(alpha = 0.18f), Offset(x, h), Offset(x + h, 0f), step * 0.45f)
                            x += step
                        }
                        val m = w * 0.1f
                        drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(m, m), Size(w - 2 * m, h - 2 * m), CornerRadius(w * 0.1f), style = Stroke(w * 0.025f))
                        val c = Offset(w / 2, h / 2)
                        drawCircle(GameKind.MEMORY.color.darker(0.18f), w * 0.27f, c)
                        val star = Path()
                        for (k in 0 until 10) {
                            val r = if (k % 2 == 0) w * 0.2f else w * 0.09f
                            val a = (-PI / 2 + k * PI / 5).toFloat()
                            val pt = Offset(c.x + r * cos(a), c.y + r * sin(a))
                            if (k == 0) star.moveTo(pt.x, pt.y) else star.lineTo(pt.x, pt.y)
                        }
                        star.close()
                        drawPath(star, Color.White)
                    }
                } else {
                    face(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
                }
            }
        }
        // Taps are taken here, on the card's own place, so a pair that has flown away blocks nothing.
        Box(Modifier.fillMaxSize().clip(Shapes.M).clickable(enabled = onFlip != null && face == null) { onFlip?.invoke() })
    }
}

/** How long a matched pair stays in place, to be seen, before it flies off. */
private const val MATCH_LOOK_MS = 500L
/** How long a matched pair takes to fly up and fade. */
private const val MATCH_FLY_MS = 400

/** The pairs each player has found, as small pictures under the chips. */
@Composable
private fun FoundPairs(faces: List<CardFace?>, owners: String, players: List<Pair<String, Char>>) {
    val p = AppTheme.palette
    @Composable
    fun line(label: String, mark: Char, color: Color) {
        val found = faces.indices.filter { owners.getOrNull(it) == mark }.mapNotNull { faces[it] }.distinct()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(64.dp))
            if (found.isEmpty()) Text("none yet", color = p.muted, fontSize = TextSize.Label)
            found.forEach { CardPicture(it, 24.dp) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        players.forEach { (name, mark) -> line(name, mark, if (mark == 'o') TheirLine else markColor(mark)) }
    }
}

// ---- Shared pieces -----------------------------------------------------------------------

/**
 * What a turn banner says: "Your turn" and, on your first two turns of the game only, what to do
 * (the text after "Your turn: ", capitalised). Any other line comes back whole. Turns are counted
 * by whose turn it is ([myTurn]), never by the wording, so "A pair! Go again" or a roll after a
 * six is the same turn. [LocalTurnHints] shares the count between a game's banners.
 */
@Composable
internal fun turnWords(text: String, myTurn: Boolean): Pair<String, String?> =
    turnWords(text, myTurn, LocalTurnHints.current ?: remember { TurnHints() })

internal fun turnWords(text: String, myTurn: Boolean, hints: TurnHints): Pair<String, String?> {
    val turn = hints.note(myTurn)
    if (!myTurn || !text.startsWith("$YOUR_TURN:")) return text to null
    val hint = text.removePrefix("$YOUR_TURN:").trim().takeIf { it.isNotEmpty() && TurnHints.showsHint(turn) }
    return YOUR_TURN to hint?.replaceFirstChar { it.uppercase() }
}

private const val YOUR_TURN = "Your turn"

@Composable
internal fun TurnLine(text: String, highlight: Boolean, color: Color = AppTheme.palette.primary) {
    val p = AppTheme.palette
    val (title, hint) = turnWords(text, highlight)
    // When the turn is yours the line becomes a banner in the game's colour and pops in, so it
    // can't be missed; otherwise it sits quietly in grey. "Your turn" stays big on one line and
    // what to do sits small under it for your first turns. The fill is the same with or without
    // the hint, and dark enough for the small text.
    val fill = if (highlight) textFill(color) else p.accent
    Surface(
        color = fill,
        shape = Shapes.L,
        border = BorderStroke(if (highlight) 3.dp else 2.dp, p.ink),
        modifier = Modifier.fillMaxWidth().enterFrom(highlight.takeIf { it }, scaleFrom = 0.9f, durationMs = 220),
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) {}
                .padding(horizontal = 12.dp, vertical = if (hint != null) 9.dp else if (highlight) 14.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                (if (highlight) "● " else "") + title,
                color = if (highlight) readableOn(fill) else p.text,
                fontWeight = FontWeight.Bold,
                fontSize = if (highlight) TextSize.Title else TextSize.BodyL,
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Text(hint, color = readableOn(fill), fontSize = TextSize.Label, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The menu of a game on your own: a fresh game, the rules, sound, and back home. */
internal fun soloMenu(game: GameKind, onHome: () -> Unit, onRules: () -> Unit, onNew: (() -> Unit)?, onChange: (() -> Unit)? = null) =
    GameMenu(game, onRules = onRules, onLeave = onHome, onNewGame = onNew, onChangeMode = onChange, leaveLabel = "Home", changeLabel = "Change setup")

// ---- Against your partner -----------------------------------------------------------------

/** A turn game (Connect Four, Dots & Boxes, Memory Pairs, Ultimate Tic-Tac-Toe) against your partner. */
@Composable
fun TurnGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val game = room.game
    val opponent = room.opponent
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me"
    val canMove = myTurn && !finished && !state.firing
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(game) { showRules = false }
    // The dock's Last move / Last line plays the last move again; Pairs lists who found what.
    var replay by remember { mutableIntStateOf(0) }
    var showPairs by remember { mutableStateOf(false) }
    if (showPairs) InfoSheet("Pairs found", onDismiss = { showPairs = false }) {
        FoundPairs(turns.faces, turns.owners ?: "", listOf("You" to 'm') + room.others.mapIndexed { i, o -> o.name to "opq"[i] })
    }

    // Boxes or pairs each player has, for the games that keep score.
    val owned = turns.boxes ?: turns.owners
    fun score(mark: Char): String? = owned?.let { o -> o.count { it == mark }.let { if (game == GameKind.MEMORY) it / 2 else it } }
        ?.let { if (game == GameKind.MEMORY) "$it pairs" else "$it boxes" }
    val (myLabel, theirLabel) = if (game == GameKind.TTT) "X" to "O" else "Red" to "Yellow"
    // Each other player's mark on the board: 'o', 'p', 'q' in seat order.
    fun markOf(player: PlayerInfo, isMe: Boolean) = if (isMe) 'm' else "opq"[room.others.indexOf(player).coerceAtLeast(0)]
    // Whose turn: with two players "opponent" is enough; with more the server names them.
    val turnName = turns.turnName ?: opponent?.name
    val players = battleChips(
        room, state, now, MyDisc,
        detail = { player, isMe -> score(markOf(player, isMe)) ?: if (isMe) myLabel else theirLabel },
        active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
        otherColor = { i -> markColor("opq"[i]) },
    )

    GameFrame(
        title = game.label,
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = players,
        glow = game.color,
        menu = battleMenu(room.game, onRules = { showRules = true }, onLeave = onLeave),
        onReact = if (opponent != null) ({ reacting = !reacting }) else null,
        myTurn = myTurn,
        tools = listOf(
            when (game) {
                GameKind.MEMORY -> DockTool("Pairs", GameIcons.Cards) { showPairs = true }
                GameKind.DOTS -> DockTool("Last line", GameIcons.Eye, enabled = turns.lastLine != null) { replay++ }
                else -> DockTool("Last move", GameIcons.Eye, enabled = turns.lastCell != null) { replay++ }
            },
        ),
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = opponent?.name ?: "The other player"
            MatchPopup(
                room, game, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: name),
                turnResultDetail(game, room.winner, room.endReason, name),
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = if (owned == null) emptyList() else listOf("You ${score('m')}") + room.others.mapIndexed { i, o -> "${o.name} ${score("opq"[i])}" },
            )
        },
    ) {
        if (!finished) {
            val name = turnName ?: "The other player"
            val waitingOn = room.others.firstOrNull { it.name == turnName } ?: opponent
            // In Dots & Boxes and Memory, a move that scores keeps the turn: say so.
            val again = myTurn && turns.lastMoveByMe == true && (game == GameKind.DOTS || game == GameKind.MEMORY)
            TurnLine(
                when {
                    !myTurn && waitingOn?.online == false -> "$name is away. They'll get a notification that it's their turn."
                    !myTurn -> "$name is thinking…"
                    again && game == GameKind.DOTS -> "Box! Go again"
                    again && game == GameKind.MEMORY && turns.faces.count { it != null } % 2 == 0 -> "A pair! Go again"
                    game == GameKind.DOTS -> "Your turn: tap a line, then tap it again to draw it"
                    game == GameKind.MEMORY -> "Your turn: flip two cards"
                    game == GameKind.TTT && turns.board?.length == 9 -> "Your turn: three in a row wins"
                    game == GameKind.TTT -> if (turns.nextBoard == null) "Your turn: play in any open board" else "Your turn: play in the glowing board"
                    else -> "Your turn: slide to a column and let go"
                },
                myTurn,
                if (again) p.good else game.color,
            )
        }

        BoardArea(when (game) { GameKind.MEMORY -> memoryAspect(turns.faces.size.takeIf { it > 0 } ?: 16); GameKind.DOTS -> dotsAspect(DotsShape.from(turns.dotsBoard)); else -> 1f }) { size ->
            when (game) {
                GameKind.DOTS -> DotsBoard(
                    turns.lines ?: DotsShape.from(turns.dotsBoard).emptyLines(), turns.boxes ?: DotsShape.from(turns.dotsBoard).emptyBoxes(), turns.lastLine,
                    mapOf('m' to room.me.avatar) + room.others.mapIndexed { i, o -> "opq"[i] to o.avatar },
                    onLine = if (canMove) vm::drawLine else null,
                    modifier = size,
                    replay = replay,
                    shape = DotsShape.from(turns.dotsBoard),
                )
                GameKind.MEMORY -> MemoryBoard(turns.faces, turns.owners ?: ".".repeat(turns.faces.size), onFlip = if (canMove) vm::flipCard else null, pending = state.pendingCard, modifier = size)
                GameKind.TTT -> TttBoard(
                    board = turns.board ?: ".".repeat(81),
                    boards = turns.smallBoards ?: ".".repeat(9),
                    next = turns.nextBoard,
                    winLine = turns.winLine,
                    lastCell = turns.lastCell,
                    playing = !finished,
                    onMark = if (canMove) vm::markCell else null,
                    modifier = size,
                    replay = replay,
                )
                else -> ConnectFourBoard(
                    board = turns.board ?: ConnectFour.EMPTY,
                    winLine = turns.winLine,
                    lastCell = turns.lastCell,
                    onDrop = if (canMove) vm::dropDisc else null,
                    modifier = size,
                    replay = replay,
                )
            }
        }
    }
}

/** One line on how a turn game ended, for the result popup. */
private fun turnResultDetail(game: GameKind, winner: String?, endReason: String?, name: String) = when (endReason) {
    "four" -> if (winner == "me") "Four in a row!" else "$name got four in a row."
    "three" -> if (winner == "me") "Three in a row!" else "$name got three in a row."
    "most" -> {
        val what = if (game == GameKind.MEMORY) "pairs" else "boxes"
        if (winner == "me") "You found more $what." else "$name found more $what."
    }
    "draw" -> when (game) {
        GameKind.CONNECT4 -> "The board is full and nobody got four."
        GameKind.TTT -> "Nobody got three in a row."
        else -> "You both have the same number."
    }
    "forfeit" -> if (winner == "me") "$name left the game." else "You left the game."
    "timeout" -> if (winner == "me") "$name lost connection." else "You were disconnected for too long."
    else -> ""
}

/** Memory Pairs' 4 × 4 cards with their gaps, width per unit of height. */
internal const val MEMORY_ASPECT = 0.75f

/** The table's width per unit of height for a deck of [cards]: 4 × 4, 4 × 6 or 6 × 6. */
internal fun memoryAspect(cards: Int): Float {
    val cols = MemoryMatch.columns(cards)
    val rows = (cards + cols - 1) / cols
    return (cols * 0.78f) / rows * 0.96f
}

/** A small card picture: the animal, on gold for the gold cards. */
@Composable
private fun CardPicture(face: CardFace, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(Shapes.S).background(if (face.gold) Pieces.Gold else Color.Transparent)) { AvatarImage(face.avatar, Modifier.size(size)) }
}

// ---- On your own --------------------------------------------------------------------------

/** The result popup of a game against the computer: "me", "computer" or "draw". */
@Composable
internal fun BoxScope.SoloPopup(game: GameKind, result: String, detail: String, onAgain: () -> Unit, onHome: () -> Unit, finishLabel: String = "Home", onFinish: () -> Unit = onHome) {
    ResultPopup(
        game = game,
        outcome = when (result) {
            "me" -> Outcome.WIN
            "computer" -> Outcome.LOSE
            else -> Outcome.TIE
        },
        title = when (result) {
            "me" -> "You win!"
            "computer" -> "The computer wins"
            else -> "It's a tie"
        },
        detail = detail,
        finish = PopupAction(finishLabel, onFinish),
        again = PopupAction("Play again", onAgain),
    )
}

/** Connect Four against the computer. */
@Composable
fun SoloConnectFourScreen(state: SoloC4UiState, vm: SoloConnectFourViewModel, onHome: () -> Unit) {
    // Started from the game's sheet; with nothing under way (the app was restored here) there is nothing to show.
    val level = state.level
    if (level == null) { BackToHome(onHome); return }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.CONNECT4) { showRules = false }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.CONNECT4, "New game", vm::startWith) { changing = false }
    val result = state.result
    GameFrame(
        title = "Connect Four",
        subtitle = "Against the computer · ${level.label}",
        players = listOf(
            ChipPlayer("You", null, MyDisc, "Red", active = state.myTurn && result == null),
            ChipPlayer("Computer", null, TheirDisc, level.label, active = !state.myTurn && result == null),
        ),
        glow = GameKind.CONNECT4.color,
        menu = soloMenu(GameKind.CONNECT4, onHome, { showRules = true }, vm::rematch, { changing = true }),
        popup = if (result == null) null else popupContent {
            SoloPopup(
                GameKind.CONNECT4, result,
                when (result) {
                    "me" -> "Four in a row against ${level.label}!"
                    "computer" -> "The computer got four in a row."
                    else -> "The board is full and nobody got four."
                },
                onAgain = vm::rematch, onHome = onHome, finishLabel = "Change level", onFinish = { changing = true },
            )
        },
    ) {
        TurnLine(
            when {
                result != null -> "Game over"
                state.myTurn -> "Your turn: slide to a column and let go"
                else -> "The computer is thinking…"
            },
            state.myTurn && result == null, GameKind.CONNECT4.color,
        )
        BoardArea(1f) { size ->
            ConnectFourBoard(state.board, state.winLine, state.lastCell, onDrop = if (state.myTurn && result == null) vm::drop else null, modifier = size)
        }
    }
}

/** Dots & Boxes against the computer. */
@Composable
fun SoloDotsScreen(state: SoloDotsUiState, vm: SoloDotsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.DOTS) { showRules = false }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.DOTS, "New game", vm::startWith) { changing = false }
    val result = state.result
    val playing = result == null
    val mine = state.boxes.count { it == 'm' }
    val theirs = state.boxes.count { it == 'o' }
    GameFrame(
        title = "Dots & Boxes",
        subtitle = "Against the computer",
        players = listOf(
            ChipPlayer("You", null, MyDisc, "$mine boxes", active = state.myTurn && playing),
            ChipPlayer("Computer", null, TheirDisc, "$theirs boxes", active = !state.myTurn && playing),
        ),
        glow = GameKind.DOTS.color,
        menu = soloMenu(GameKind.DOTS, onHome, { showRules = true }, { vm.newGame() }, { changing = true }),
        popup = if (result == null) null else popupContent {
            SoloPopup(GameKind.DOTS, result, "You $mine boxes, the computer $theirs.", onAgain = { vm.newGame() }, onHome = onHome)
        },
    ) {
        val again = state.myTurn && state.lastLine != null && state.lines[state.lastLine] == 'm'
        TurnLine(
            when {
                !playing -> "Game over"
                !state.myTurn -> "The computer is drawing…"
                again -> "Box! Go again"
                else -> "Your turn: tap a line, then tap it again to draw it"
            },
            state.myTurn && playing,
            if (again && playing) p.good else GameKind.DOTS.color,
        )
        BoardArea(dotsAspect(DotsShape.from(state.board))) { size ->
            DotsBoard(state.lines, state.boxes, state.lastLine, emptyMap(), onLine = if (state.myTurn && playing) vm::drawLine else null, modifier = size, shape = DotsShape.from(state.board))
        }
    }
}

/** Ultimate Tic-Tac-Toe against the computer. */
@Composable
fun SoloTttScreen(state: SoloTttUiState, vm: SoloTttViewModel, onHome: () -> Unit) {
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.TTT) { showRules = false }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.TTT, "New game", vm::startWith) { changing = false }
    val result = state.result
    val playing = result == null
    val pos = state.position
    GameFrame(
        title = "Tic-Tac-Toe",
        subtitle = (if (pos.classic) "Normal" else "Ultimate") + " · against the computer",
        players = listOf(
            ChipPlayer("You", null, MyDisc, if (pos.classic) "X" else "X · ${pos.boards.count { it == 'm' }} boards", active = state.myTurn && playing),
            ChipPlayer("Computer", null, TheirDisc, if (pos.classic) "O" else "O · ${pos.boards.count { it == 'o' }} boards", active = !state.myTurn && playing),
        ),
        glow = GameKind.TTT.color,
        menu = soloMenu(GameKind.TTT, onHome, { showRules = true }, { vm.newGame() }, { changing = true }),
        popup = if (result == null) null else popupContent {
            SoloPopup(
                GameKind.TTT, result,
                when (result) {
                    "me" -> "Three in a row!"
                    "computer" -> "The computer got three in a row."
                    else -> "Nobody got three in a row."
                },
                onAgain = { vm.newGame() }, onHome = onHome,
            )
        },
    ) {
        TurnLine(
            when {
                !playing -> "Game over"
                !state.myTurn -> "The computer is thinking…"
                pos.classic -> "Your turn: three in a row wins"
                pos.next < 0 -> "Your turn: play in any open board"
                else -> "Your turn: play in the glowing board"
            },
            state.myTurn && playing,
            GameKind.TTT.color,
        )
        BoardArea(1f) { size ->
            TttBoard(
                board = pos.cells,
                boards = pos.boards,
                next = pos.next.takeIf { it >= 0 },
                winLine = state.winLine,
                lastCell = state.lastCell,
                playing = playing,
                onMark = if (state.myTurn && playing) vm::mark else null,
                modifier = size,
            )
        }
    }
}

/** Memory Pairs on your own: find every pair in as few flips as you can. */
@Composable
fun SoloMemoryScreen(state: SoloMemoryUiState, vm: SoloMemoryViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.MEMORY) { showRules = false }
    var changing by remember { mutableStateOf(false) }
    if (changing) SoloSetupSheet(GameKind.MEMORY, "New game", vm::startWith) { changing = false }
    val finished = state.finishedMs
    GameFrame(
        title = "Memory Pairs",
        subtitle = "On your own",
        players = emptyList(),
        glow = GameKind.MEMORY.color,
        menu = soloMenu(GameKind.MEMORY, onHome, { showRules = true }, { vm.newGame() }, { changing = true }),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text("${state.flips} flips", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                state.bestFlips?.let { Text("Best: $it", color = p.muted, fontSize = TextSize.Body) }
            }
        },
        popup = if (finished == null) null else popupContent {
            ResultPopup(
                game = GameKind.MEMORY,
                outcome = Outcome.WIN,
                title = "All pairs found!",
                detail = "${state.flips} flips in ${formatDuration(finished)}",
                scores = listOfNotNull(state.bestFlips?.let { "Best: $it flips" }),
                finish = PopupAction("Home", onHome),
                again = PopupAction("Play again", { vm.newGame() }),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Found", color = p.muted, fontSize = TextSize.Body, modifier = Modifier.width(52.dp))
            val found = state.faces.indices.filter { state.found[it] != '.' }.mapNotNull { state.faces[it] }.distinct()
            if (found.isEmpty() && state.flips > 0) Text("Find all ${state.cards / 2} pairs in as few flips as you can.", color = p.muted, fontSize = TextSize.Body)
            found.forEach { CardPicture(it, 24.dp) }
            Spacer(Modifier.width(4.dp))
        }
        BoardArea(memoryAspect(state.cards)) { size ->
            MemoryBoard(state.faces, state.found, onFlip = if (finished == null) vm::flip else null, modifier = size)
        }
    }
}

/** An X (yours) or an O (theirs) chalked on a Tic-Tac-Toe board. */
private fun Sfx.tttSound(mark: Char?) {
    if (mark == 'm') play(Sound.TTT_X) else play(Sound.TTT_O, Sfx.OTHERS)
}

/** Memory Pairs' deck: 16, 24 or 36 cards, picked by whoever opened the game. */
@Composable
fun MemoryDeckCard(room: RoomState, busy: Boolean, onPick: (Int) -> Unit) {
    OptionsCard(room, "Deck", busy) { enabled ->
        OptionRow("Cards", MemoryMatch.DECKS.map { it to "$it" }, room.memoryCards, onPick, enabled = enabled, note = "Bigger decks bring in more pictures and a longer game.")
    }
}

/** Dots & Boxes' board, picked by whoever opened the game. */
@Composable
fun DotsBoardCard(room: RoomState, busy: Boolean, onPick: (String) -> Unit) {
    OptionsCard(room, "Board", busy) { enabled ->
        OptionRow(
            "Board", DotsShape.ALL.map { it.key to DotsShape.label(it.key) }, room.dotsBoard, onPick, enabled = enabled, perRow = 2,
            note = "Bigger boards make longer chains. The heart has gaps where there are no dots.",
        )
    }
}
