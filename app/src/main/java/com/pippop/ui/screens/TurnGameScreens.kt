package com.pippop.ui.screens

import com.pippop.ui.theme.readableOn
import com.pippop.ui.Sfx
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.TextSize
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
fun ConnectFourBoard(board: String, winLine: List<Int>, lastCell: Int?, onDrop: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val fall = remember(lastCell, board) { Animatable(if (lastCell == null) 1f else 0f) }
    // It speeds up as it falls, then bounces once in its hole.
    LaunchedEffect(lastCell, board) {
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
    Canvas(modifier.fillMaxWidth().aspectRatio(ConnectFour.COLS / (ConnectFour.ROWS + 1f)).then(pick)) {
        val cell = size.width / ConnectFour.COLS
        val top = cell
        val radius = cell * 0.38f
        hover?.let { drawCircle(MyDisc.copy(alpha = 0.6f), radius, Offset((it + 0.5f) * cell, top * 0.5f)) }
        val boardSize = Size(size.width - 4f, cell * ConnectFour.ROWS)
        drawRoundRect(p.ink, Offset(2f, top + 6f), boardSize, CornerRadius(cell * 0.28f))
        drawRoundRect(BoardBlue, Offset(2f, top), boardSize, CornerRadius(cell * 0.28f))
        drawRoundRect(p.ink, Offset(2f, top), boardSize, CornerRadius(cell * 0.28f), style = Stroke(4f))
        for (i in board.indices) {
            val center = Offset((i % ConnectFour.COLS + 0.5f) * cell, top + (i / ConnectFour.COLS + 0.5f) * cell)
            drawCircle(p.background, radius, center)
            drawCircle(p.ink.copy(alpha = 0.5f), radius, center, style = Stroke(2.5f))
            val disc = when (board[i]) {
                'm' -> MyDisc
                'o' -> TheirDisc
                else -> continue
            }
            // The newest disc falls from above the board into its hole.
            val at = if (i == lastCell) Offset(center.x, -radius + (center.y + radius) * fall.value) else center
            drawCircle(disc, radius, at)
            drawCircle(p.ink, radius, at, style = Stroke(2.5f))
            drawCircle(Color.White.copy(alpha = 0.35f), radius * 0.3f, at + Offset(-radius * 0.3f, -radius * 0.3f))
        }
        if (fall.value >= 1f) {
            for (i in winLine) {
                val center = Offset((i % ConnectFour.COLS + 0.5f) * cell, top + (i / ConnectFour.COLS + 0.5f) * cell)
                drawCircle(Color.White.copy(alpha = glow), radius * 1.02f, center, style = Stroke(cell * 0.1f))
            }
        }
    }
}

// ---- Ultimate Tic-Tac-Toe -----------------------------------------------------------------

private val TttBlue = Board.TttBlue
private val Target = Board.Glow

/**
 * An Ultimate Tic-Tac-Toe board: [board] holds 81 cells ('m' your X, 'o' their O, '.'), small
 * board by small board; [boards] each small board's winner ('d' drawn). While [playing], the
 * board(s) the next move must go in glow. With [onMark] set, tapping a square marks it.
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
) {
    if (board.length == 9) return ClassicTttBoard(board, winLine, lastCell, onMark, modifier)
    val p = AppTheme.palette
    val pop = remember(lastCell) { Animatable(if (lastCell == null) 1f else 0f) }
    LaunchedEffect(lastCell) { pop.animateTo(1f, tween(260)) }
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
        fun mark(kind: Char, center: Offset, r: Float, width: Float, scale: Float = 1f) {
            val k = r * scale
            when (kind) {
                'm' -> for (color in listOf(p.ink, MyDisc)) {
                    val w = if (color == MyDisc) width else width + 5f
                    drawLine(color, center + Offset(-k, -k), center + Offset(k, k), w, StrokeCap.Round)
                    drawLine(color, center + Offset(k, -k), center + Offset(-k, k), w, StrokeCap.Round)
                }
                'o' -> {
                    drawCircle(p.ink, k, center, style = Stroke(width + 5f))
                    drawCircle(TheirDisc, k, center, style = Stroke(width))
                }
            }
        }
        // Lines between the small boards.
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = 0.85f), Offset(i * big, gap * 0.6f), Offset(i * big, size.height - 6f - gap * 0.6f), 5f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.85f), Offset(gap * 0.6f, i * big), Offset(size.width - gap * 0.6f, i * big), 5f, StrokeCap.Round)
        }
        for (b in 0 until 9) {
            val o = origin(b)
            val target = playing && boards[b] == '.' && (next == null || next == b)
            if (target) {
                drawRoundRect(Target.copy(alpha = 0.3f * pulse), o - Offset(gap * 0.4f, gap * 0.4f), Size(3 * small + gap * 0.8f, 3 * small + gap * 0.8f), CornerRadius(gap))
                drawRoundRect(Target.copy(alpha = pulse), o - Offset(gap * 0.4f, gap * 0.4f), Size(3 * small + gap * 0.8f, 3 * small + gap * 0.8f), CornerRadius(gap), style = Stroke(4f))
            }
            for (i in 1..2) {
                drawLine(Color.White.copy(alpha = 0.45f), o + Offset(i * small, 0f), o + Offset(i * small, 3 * small), 2.5f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.45f), o + Offset(0f, i * small), o + Offset(3 * small, i * small), 2.5f, StrokeCap.Round)
            }
            for (c in 0 until 9) {
                val cell = b * 9 + c
                val center = o + Offset((c % 3 + 0.5f) * small, (c / 3 + 0.5f) * small)
                if (cell == lastCell) drawCircle(Color.White.copy(alpha = 0.22f), small * 0.46f, center)
                mark(board.getOrElse(cell) { '.' }, center, small * 0.26f, small * 0.11f, if (cell == lastCell) pop.value else 1f)
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
private fun ClassicTttBoard(board: String, winLine: List<Int>, lastCell: Int?, onMark: ((Int) -> Unit)?, modifier: Modifier) {
    val p = AppTheme.palette
    val pop = remember(lastCell) { Animatable(if (lastCell == null) 1f else 0f) }
    LaunchedEffect(lastCell) { pop.animateTo(1f, tween(320)) }
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
            val k = step * 0.28f * (if (c == lastCell) pop.value else 1f)
            when (board[c]) {
                'm' -> for (color in listOf(p.ink, MyDisc)) {
                    val w = if (color == MyDisc) step * 0.09f else step * 0.09f + 6f
                    drawLine(color, center + Offset(-k, -k), center + Offset(k, k), w, StrokeCap.Round)
                    drawLine(color, center + Offset(k, -k), center + Offset(-k, k), w, StrokeCap.Round)
                }
                'o' -> {
                    drawCircle(p.ink, k, center, style = Stroke(step * 0.09f + 6f))
                    drawCircle(TheirDisc, k, center, style = Stroke(step * 0.09f))
                }
            }
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
    val p = AppTheme.palette
    AppCard("Which game?") {
        val modes = listOf(false to "Ultimate: nine boards in one", true to "Normal: one 3 × 3 board")
        if (room.setsOptions) {
            OptionGroup(modes, room.tttClassic, enabled = !busy, onPick = onPick)
        } else {
            Text(modes.first { it.first == room.tttClassic }.second + ", picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
        }
    }
}

// ---- Dots & Boxes -------------------------------------------------------------------------

/**
 * A Dots & Boxes board: 5 × 5 dots. [lines] and [boxes] hold 'm' (yours), 'o' (the
 * opponent's) or '.'. A claimed box shows its owner's picture. With [onLine] set, the line
 * under your finger shows faintly and is drawn when you let go.
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
) {
    val p = AppTheme.palette
    SoundOnChange(lastLine) { play(Sound.MARK, if (lines.getOrNull(lastLine ?: -1) == 'm') 1f else Sfx.OTHERS) }
    // A box closed: a stamp and a chime, and the same player goes again.
    SoundOnChange(boxes.count { it != '.' }.takeIf { it > 0 }) { play(Sound.DOTS_BOX) }
    // Under the finger now, and the line chosen by the last tap: a tap lights up the nearest free
    // line, and a second tap on it draws it, so a stray touch never plays a line.
    var preview by remember { mutableStateOf<Int?>(null) }
    var chosen by remember(lines) { mutableStateOf<Int?>(null) }
    val free by rememberUpdatedState(lines)
    val pick = if (onLine == null) Modifier else Modifier.pointerInput(onLine) {
        /** The free line whose middle is nearest [at], within most of a box. */
        fun lineAt(at: Offset): Int? {
            val margin = size.width * 0.08f
            val step = (size.width - 2 * margin) / Dots.BOXES
            fun middle(line: Int) = if (line < 20) {
                Offset(margin + (line % 4 + 0.5f) * step, margin + (line / 4) * step)
            } else {
                val l = line - 20
                Offset(margin + (l % 5) * step, margin + (l / 5 + 0.5f) * step)
            }
            return (0 until Dots.LINES).filter { free.getOrNull(it) == '.' }
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
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).then(pick)) {
        val margin = size.width * 0.08f
        val step = (size.width - 2 * margin) / Dots.BOXES
        fun dot(r: Int, c: Int) = Offset(margin + c * step, margin + r * step)
        fun ends(line: Int) = if (line < 20) {
            dot(line / 4, line % 4) to dot(line / 4, line % 4 + 1)
        } else {
            val l = line - 20
            dot(l / 5, l % 5) to dot(l / 5 + 1, l % 5)
        }
        for (box in 0 until Dots.BOXES * Dots.BOXES) {
            val owner = boxes[box]
            if (owner == '.') continue
            val corner = dot(box / Dots.BOXES, box % Dots.BOXES)
            drawRoundRect(
                markColor(owner).copy(alpha = 0.35f),
                corner + Offset(step * 0.08f, step * 0.08f), Size(step * 0.84f, step * 0.84f), CornerRadius(step * 0.14f),
            )
            val avatar = avatars[owner]
            if (avatar != null) {
                // The owner's picture in the middle of the box.
                val pic = step * 0.56f
                val left = corner.x + (step - pic) / 2
                val top = corner.y + (step - pic) / 2
                inset(left, top, size.width - left - pic, size.height - top - pic) { drawAvatar(avatar) }
            }
        }
        for (line in 0 until Dots.LINES) {
            val (a, b) = ends(line)
            when (lines[line]) {
                '.' -> drawLine(p.lineThin, a, b, step * 0.035f, cap = StrokeCap.Round)
                else -> drawLine(
                    if (lines[line] == 'o') TheirLine else markColor(lines[line]), a, b,
                    step * if (line == lastLine) 0.12f else 0.08f,
                    cap = StrokeCap.Round,
                )
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
        for (r in 0..Dots.BOXES) for (c in 0..Dots.BOXES) {
            drawCircle(p.ink, step * 0.085f, dot(r, c))
            drawCircle(p.card, step * 0.035f, dot(r, c))
        }
    }
}

// ---- Memory Pairs -------------------------------------------------------------------------

/**
 * A Memory Pairs table: 4 × 4 cards. [faces] holds a card's picture when it is face up or
 * matched; [owners] 'm', 'o' or '.'. Cards turn over with a flip. With [onFlip] set, tapping
 * a face-down card flips it.
 */
@Composable
fun MemoryBoard(faces: List<Avatar?>, owners: String, onFlip: ((Int) -> Unit)?, pending: Int? = null, modifier: Modifier = Modifier) {
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    val i = row * 4 + col
                    MemoryCard(shownFaces.getOrNull(i), owners.getOrNull(i) ?: '.', onFlip?.let { { it(i) } }, Modifier.weight(1f), turning = i == pending)
                }
            }
        }
    }
}

/** How long a missed pair stays face up for everyone to see. */
private const val MISSED_LOOK_MS = 1400L

@Composable
private fun MemoryCard(face: Avatar?, owner: Char, onFlip: (() -> Unit)?, modifier: Modifier, turning: Boolean = false) {
    val p = AppTheme.palette
    // Remember the last picture so the card still shows it while turning back.
    var shown by remember { mutableStateOf(face) }
    if (face != null) shown = face
    // [turning]: tapped, picture on its way from the server; the card starts turning at once.
    val turn by animateFloatAsState(if (face != null) 180f else if (turning) 70f else 0f, tween(320), label = "flip")
    val density = LocalDensity.current.density
    val edge = if (owner == '.') p.ink else markColor(owner)
    Box(
        modifier
            .aspectRatio(0.78f)
            .graphicsLayer { rotationY = turn; cameraDistance = 14 * density }
            .clip(Shapes.M)
            .clickable(enabled = onFlip != null && face == null) { onFlip?.invoke() },
    ) {
        if (turn < 90f) {
            // The back: the app's grid pattern on Memory amber.
            Canvas(Modifier.fillMaxSize().background(GameKind.MEMORY.color).border(2.dp, p.ink, Shapes.M)) {
                val s = size.width / 4
                for (r in 0 until 6) for (c in 0 until 4) {
                    if ((r + c) % 2 == 0) drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(c * s + s * 0.2f, r * s + s * 0.2f), Size(s * 0.6f, s * 0.6f), CornerRadius(s * 0.15f))
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .background(p.card)
                    .border(if (owner == '.') 2.dp else 4.dp, edge, Shapes.M),
            ) { shown?.let { AvatarImage(it, Modifier.fillMaxWidth(0.78f).aspectRatio(1f)) } }
        }
    }
}

/** The pairs each player has found, as small pictures under the chips. */
@Composable
private fun FoundPairs(faces: List<Avatar?>, owners: String, players: List<Pair<String, Char>>) {
    val p = AppTheme.palette
    @Composable
    fun line(label: String, mark: Char, color: Color) {
        val found = faces.indices.filter { owners.getOrNull(it) == mark }.mapNotNull { faces[it] }.distinct()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(64.dp))
            if (found.isEmpty()) Text("none yet", color = p.muted, fontSize = TextSize.Label)
            found.forEach { AvatarImage(it, Modifier.size(24.dp)) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        players.forEach { (name, mark) -> line(name, mark, if (mark == 'o') TheirLine else markColor(mark)) }
    }
}

// ---- Shared pieces -----------------------------------------------------------------------

@Composable
internal fun TurnLine(text: String, highlight: Boolean, color: Color = AppTheme.palette.primary) {
    val p = AppTheme.palette
    // When the turn is yours the line becomes a banner in the game's colour and pops in, so it
    // can't be missed; otherwise it sits quietly in grey.
    Surface(
        color = if (highlight) color else p.accent,
        shape = Shapes.L,
        border = BorderStroke(if (highlight) 3.dp else 2.dp, p.ink),
        modifier = Modifier.fillMaxWidth().enterFrom(highlight.takeIf { it }, scaleFrom = 0.9f, durationMs = 220),
    ) {
        Text(
            (if (highlight) "● " else "") + text,
            color = if (highlight) readableOn(color) else p.text,
            fontWeight = FontWeight.Bold,
            fontSize = if (highlight) TextSize.Title else TextSize.BodyL,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (highlight) 14.dp else 12.dp),
        )
    }
}

/** Get ready for a turn game: in the lobby and for a rematch. */
@Composable
fun TurnReadyCard(room: RoomState, busy: Boolean, onReady: () -> Unit) {
    val p = AppTheme.palette
    val others = room.present
    val waiting = others.filter { !it.ready }
    AppCard(if (room.round == 0) "Ready to play?" else "Rematch") {
        PrimaryButton(if (room.me.ready) "Cancel" else "Ready", onReady, enabled = others.isNotEmpty(), loading = busy)
        Text(
            when {
                others.isEmpty() && room.others.isNotEmpty() -> "${room.others[0].name} has left. Leave to start a new game."
                others.isEmpty() -> "Waiting for someone to join."
                room.me.ready && waiting.isEmpty() -> "Starting…"
                room.me.ready -> "Waiting for " + waiting.joinToString(" and ") { it.name } + "…"
                waiting.size < others.size -> others.filter { it.ready }.joinToString(" and ") { it.name } +
                    (if (others.size - waiting.size == 1) " is" else " are") + " ready. Tap Ready to start."
                room.game == GameKind.WORDS -> "The race starts when everyone is ready: same word, same moment."
                room.game == GameKind.DRAW -> "It starts when everyone is ready. You take turns drawing."
                room.game == GameKind.NUMBERS -> "Everyone gets the same new puzzle. The first to solve it wins."
                room.game == GameKind.SLIDE -> "Same shuffle for everyone; the first to put it in order wins."
                room.game == GameKind.BINGO -> "A card each; the first full line of called numbers wins."
                room.game == GameKind.GOLF -> "Nine holes, taking turns; the fewest strokes wins."
                room.game == GameKind.BLOCKS -> "Lay your pieces corner to corner; the most squares wins."
                room.game == GameKind.CODE -> "Each of you hides a code for the other; fewest guesses wins."
                room.game == GameKind.GRID -> "Same letters for everyone, two minutes on the clock."
                room.game == GameKind.HOCKEY -> "Live air hockey, first to 7. Best nearby, where the phones talk directly."
                else -> "It starts when you are both ready. Who goes first swaps every game."
            },
            color = if (waiting.size < others.size && !room.me.ready) p.good else p.muted,
            fontSize = TextSize.Body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The dock for a game on your own: back home, the rules, and a fresh game. */
internal fun soloDock(onHome: () -> Unit, onRules: () -> Unit, onNew: (() -> Unit)?) = listOfNotNull(
    DockAction("Home", Icons.Filled.Home, onClick = onHome),
    DockAction("Rules", Icons.Filled.Info, onClick = onRules),
    onNew?.let { DockAction("New game", Icons.Filled.Refresh, onClick = it) },
)

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
    if (showRules) RulesDialog(game) { showRules = false }

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
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
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
        if (game == GameKind.MEMORY) FoundPairs(turns.faces, turns.owners ?: "", listOf("You" to 'm') + room.others.mapIndexed { i, o -> o.name to "opq"[i] })
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

        BoardArea(if (game == GameKind.MEMORY) MEMORY_ASPECT else 1f) { size ->
            when (game) {
                GameKind.DOTS -> DotsBoard(
                    turns.lines ?: ".".repeat(Dots.LINES), turns.boxes ?: ".".repeat(16), turns.lastLine,
                    mapOf('m' to room.me.avatar) + room.others.mapIndexed { i, o -> "opq"[i] to o.avatar },
                    onLine = if (canMove) vm::drawLine else null,
                    modifier = size,
                )
                GameKind.MEMORY -> MemoryBoard(turns.faces, turns.owners ?: ".".repeat(16), onFlip = if (canMove) vm::flipCard else null, pending = state.pendingCard, modifier = size)
                GameKind.TTT -> TttBoard(
                    board = turns.board ?: ".".repeat(81),
                    boards = turns.smallBoards ?: ".".repeat(9),
                    next = turns.nextBoard,
                    winLine = turns.winLine,
                    lastCell = turns.lastCell,
                    playing = !finished,
                    onMark = if (canMove) vm::markCell else null,
                    modifier = size,
                )
                else -> ConnectFourBoard(
                    board = turns.board ?: ConnectFour.EMPTY,
                    winLine = turns.winLine,
                    lastCell = turns.lastCell,
                    onDrop = if (canMove) vm::dropDisc else null,
                    modifier = size,
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
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.CONNECT4) { showRules = false }
    val level = state.level
    val result = state.result
    GameFrame(
        title = "Connect Four",
        subtitle = level?.let { "Against the computer · ${it.label}" } ?: "Against the computer",
        players = if (level == null) emptyList() else listOf(
            ChipPlayer("You", null, MyDisc, "Red", active = state.myTurn && result == null),
            ChipPlayer("Computer", null, TheirDisc, level.label, active = !state.myTurn && result == null),
        ),
        glow = GameKind.CONNECT4.color,
        dock = soloDock(onHome, { showRules = true }, if (level != null) ({ vm.changeLevel() }) else null),
        scrollable = level == null,
        popup = if (level == null || result == null) null else popupContent {
            SoloPopup(
                GameKind.CONNECT4, result,
                when (result) {
                    "me" -> "Four in a row against ${level.label}!"
                    "computer" -> "The computer got four in a row."
                    else -> "The board is full and nobody got four."
                },
                onAgain = vm::rematch, onHome = onHome, finishLabel = "Change level", onFinish = vm::changeLevel,
            )
        },
    ) {
        if (level == null) {
            AppCard("Choose the computer's level") {
                Text("Line up four of your discs across, down or diagonally before the computer does.", color = p.muted, fontSize = TextSize.Body)
                C4Level.entries.forEach { PrimaryButton(it.label, { vm.start(it) }) }
            }
        } else {
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
}

/** Dots & Boxes against the computer. */
@Composable
fun SoloDotsScreen(state: SoloDotsUiState, vm: SoloDotsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.DOTS) { showRules = false }
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
        dock = soloDock(onHome, { showRules = true }, vm::newGame),
        popup = if (result == null) null else popupContent {
            SoloPopup(GameKind.DOTS, result, "You $mine boxes, the computer $theirs.", onAgain = vm::newGame, onHome = onHome)
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
        BoardArea(1f) { size ->
            DotsBoard(state.lines, state.boxes, state.lastLine, emptyMap(), onLine = if (state.myTurn && playing) vm::drawLine else null, modifier = size)
        }
    }
}

/** Ultimate Tic-Tac-Toe against the computer. */
@Composable
fun SoloTttScreen(state: SoloTttUiState, vm: SoloTttViewModel, onHome: () -> Unit) {
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.TTT) { showRules = false }
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
        dock = soloDock(onHome, { showRules = true }, { vm.newGame() }),
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
        // Normal or Ultimate: switching starts a new game.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "Normal 3 × 3", false to "Ultimate").forEach { (classic, label) ->
                if (pos.classic == classic) PrimaryButton(label, {}, Modifier.weight(1f))
                else SecondaryButton(label, { vm.newGame(classic) }, Modifier.weight(1f))
            }
        }
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
    if (showRules) RulesDialog(GameKind.MEMORY) { showRules = false }
    val finished = state.finishedMs
    GameFrame(
        title = "Memory Pairs",
        subtitle = "On your own",
        players = emptyList(),
        glow = GameKind.MEMORY.color,
        dock = soloDock(onHome, { showRules = true }, vm::newGame),
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
                again = PopupAction("Play again", vm::newGame),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Found", color = p.muted, fontSize = TextSize.Body, modifier = Modifier.width(52.dp))
            val found = state.faces.indices.filter { state.found[it] != '.' }.mapNotNull { state.faces[it] }.distinct()
            if (found.isEmpty()) Text("Find all 8 pairs in as few flips as you can.", color = p.muted, fontSize = TextSize.Body)
            found.forEach { AvatarImage(it, Modifier.size(24.dp)) }
            Spacer(Modifier.width(4.dp))
        }
        BoardArea(MEMORY_ASPECT) { size ->
            MemoryBoard(state.faces, state.found, onFlip = if (finished == null) vm::flip else null, modifier = size)
        }
    }
}

/** An X (yours) or an O (theirs) chalked on a Tic-Tac-Toe board. */
private fun Sfx.tttSound(mark: Char?) {
    if (mark == 'm') play(Sound.TTT_X) else play(Sound.TTT_O, Sfx.OTHERS)
}
