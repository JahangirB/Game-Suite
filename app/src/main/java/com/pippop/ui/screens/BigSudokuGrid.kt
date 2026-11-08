package com.pippop.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pippop.game.BoardState
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.Shapes
import kotlinx.coroutines.launch

/**
 * A Sudoku board of joined grids ([BoardState.shape]): the whole board fits the square at first;
 * pinch to zoom, drag to move round, double-tap to zoom to the grid under your finger (and again
 * to see the whole board). Shared boxes are tinted gold. Tap picks a square, a long press fills its
 * notes. The rest as [SudokuGrid]: [errors], [solution] shown over wrong squares, and for playing
 * together [inkOf] (whose colour a digit is), [rings] (where the others are) and [flash].
 */
@Composable
fun BigSudokuGrid(
    board: BoardState,
    errors: Set<Int>,
    selected: Int?,
    hidden: Boolean,
    solution: List<Int>?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((Int) -> Unit)? = null,
    inkOf: ((Int) -> Color?)? = null,
    rings: Map<Int, Color> = emptyMap(),
    flash: Pair<Int, Int>? = null,
) {
    val p = AppTheme.palette
    val shape = board.shape
    val n = shape.size
    SoundOnChange(board.cells.count { it != 0 }) { play(Sound.SUDOKU_DIGIT) }
    SoundOnChange(errors.size.takeIf { it > 0 }) { play(Sound.ERROR) }
    val select by rememberUpdatedState(onSelect)
    val longPress by rememberUpdatedState(onLongPress)
    // How far in, and where: a board point b is drawn at b * scale + offset.
    var scale by remember(shape) { mutableFloatStateOf(1f) }
    var offset by remember(shape) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val selectedDigit = selected?.let { board.cells[it] }?.takeIf { it != 0 }
    val shared = lerp(p.boardBackground, Pieces.Yellow, 0.22f)
    val digitPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val empty = board.cells.indices.count { shape.onBoard(it) && board.cells[it] == 0 }

    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = "${shape.label} Sudoku, ${shape.grids.size} grids, $empty empty squares. Pinch or double-tap to zoom." },
    ) {
        val side = with(density) { maxWidth.toPx() }
        val cell0 = side / n
        // Close enough in that six squares fill the width, never less than the whole board.
        val maxScale = maxOf(1f, n / 6f)
        fun clamp(o: Offset, s: Float) = Offset(o.x.coerceIn(side - side * s, 0f), o.y.coerceIn(side - side * s, 0f))
        fun cellAt(pos: Offset): Int? {
            val c = ((pos.x - offset.x) / (cell0 * scale)).toInt()
            val r = ((pos.y - offset.y) / (cell0 * scale)).toInt()
            if (r !in 0 until n || c !in 0 until n) return null
            return (r * n + c).takeIf { shape.onBoard(it) }
        }
        fun zoomTo(s: Float, o: Offset) {
            val s0 = scale
            val o0 = offset
            scope.launch {
                animate(0f, 1f, animationSpec = tween(260)) { k, _ ->
                    scale = s0 + (s - s0) * k
                    offset = clamp(o0 + (o - o0) * k, scale)
                }
            }
        }
        // Double-tap: in to the grid under the finger, or back out to the whole board.
        fun doubleTap(pos: Offset) {
            if (scale > 1.05f) return zoomTo(1f, Offset.Zero)
            val i = cellAt(pos) ?: return
            val (gr, gc) = shape.grids[shape.gridsAt[i].first()]
            val s = minOf(maxScale, n / 9.6f)
            val centre = Offset((gc + 4.5f) * cell0, (gr + 4.5f) * cell0)
            zoomTo(s, clamp(Offset(side / 2, side / 2) - centre * s, s))
        }
        val gestures = Modifier
            .pointerInput(shape, side) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val s = (scale * zoom).coerceIn(1f, maxScale)
                    offset = clamp((offset - centroid) * (s / scale) + centroid + pan, s)
                    scale = s
                }
            }
            .pointerInput(shape, side, hidden) {
                if (hidden) return@pointerInput
                var lastTap = 0L
                var lastPos = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    // true: lifted where it went down; false: moved, pinched or lost. Null: still
                    // held after the long-press time.
                    val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) return@withTimeoutOrNull false
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                            if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
                            if (!change.pressed) return@withTimeoutOrNull true
                        }
                        @Suppress("UNREACHABLE_CODE")
                        false
                    }
                    if (tapped == null) {
                        // Held still: the pencil-mark helper. Wait for the finger to lift.
                        cellAt(down.position)?.let { c -> longPress?.invoke(c) }
                        while (awaitPointerEvent().changes.any { it.pressed }) Unit
                        return@awaitEachGesture
                    }
                    if (!tapped) return@awaitEachGesture
                    val now = down.uptimeMillis
                    if (now - lastTap < viewConfiguration.doubleTapTimeoutMillis && (down.position - lastPos).getDistance() < slop * 3) {
                        // The first tap already picked the square; the second zooms.
                        lastTap = 0L
                        doubleTap(down.position)
                    } else {
                        lastTap = now
                        lastPos = down.position
                        cellAt(down.position)?.let { select(it) }
                    }
                }
            }

        Canvas(Modifier.fillMaxSize().clip(Shapes.M).then(gestures)) {
            val c = cell0 * scale
            val thin = 1.dp.toPx()
            val thick = maxOf(1.6.dp.toPx(), c * 0.07f)
            val r0 = ((-offset.y) / c).toInt().coerceIn(0, n - 1)
            val r1 = ((size.height - offset.y) / c).toInt().coerceIn(0, n - 1)
            val c0 = ((-offset.x) / c).toInt().coerceIn(0, n - 1)
            val c1 = ((size.width - offset.x) / c).toInt().coerceIn(0, n - 1)
            fun at(r: Int, col: Int) = Offset(offset.x + col * c, offset.y + r * c)
            // Squares.
            for (r in r0..r1) for (col in c0..c1) {
                val i = r * n + col
                if (!shape.onBoard(i)) continue
                val digit = board.cells[i]
                val bg = when {
                    hidden -> p.boardBackground
                    i == selected -> p.cellSameDigit
                    !board.givens[i] && i in errors -> p.cellWrong
                    flash?.first == i && digit == 0 -> p.cellWrong
                    selectedDigit != null && digit == selectedDigit -> p.cellSelected
                    selected != null && i in shape.peers[selected] -> p.cellPeer
                    shape.shared(i) -> shared
                    else -> p.boardBackground
                }
                drawRect(bg, at(r, col), Size(c, c))
                drawRect(p.lineThin, at(r, col), Size(c, c), style = Stroke(thin))
            }
            // Box edges: every 3 x 3 box on the board, in ink.
            for (r in (r0 / 3 * 3)..r1 step 3) for (col in (c0 / 3 * 3)..c1 step 3) {
                if (shape.onBoard(r * n + col)) drawRect(p.lineThick, at(r, col), Size(c * 3, c * 3), style = Stroke(thick))
            }
            if (hidden) return@Canvas
            // Digits and notes.
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for (r in r0..r1) for (col in c0..c1) {
                    val i = r * n + col
                    if (!shape.onBoard(i)) continue
                    val given = board.givens[i]
                    val entered = board.cells[i]
                    val revealed = solution != null && !given && entered != solution[i]
                    val flashed = flash?.first == i && entered == 0
                    val digit = if (revealed) solution!![i] else if (flashed) flash!!.second else entered
                    val o = at(r, col)
                    if (digit != 0) {
                        digitPaint.textSize = c * 0.62f
                        digitPaint.typeface = if (given) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        digitPaint.color = when {
                            revealed -> p.good
                            flashed || (!given && i in errors) -> p.wrong
                            given -> p.given
                            else -> inkOf?.invoke(i) ?: p.entry
                        }.toArgb()
                        native.drawText(digit.toString(), o.x + c / 2, o.y + c * 0.72f, digitPaint)
                    } else if (board.notes[i] != 0 && c >= 22.dp.toPx()) {
                        // Notes only once the squares are big enough to read them.
                        digitPaint.textSize = c * 0.26f
                        digitPaint.typeface = Typeface.DEFAULT
                        digitPaint.color = p.note.toArgb()
                        for (d in board.noteDigits(i)) {
                            val k = d - 1
                            native.drawText(d.toString(), o.x + c * (k % 3 + 0.5f) / 3, o.y + c * ((k / 3) + 0.78f) / 3, digitPaint)
                        }
                    }
                }
            }
            // Where the others are, and the square you picked.
            for ((i, color) in rings) {
                val o = at(i / n, i % n)
                drawRect(color, o + Offset(thick / 2, thick / 2), Size(c - thick, c - thick), style = Stroke(maxOf(2.dp.toPx(), c * 0.1f)))
            }
            selected?.let { i -> drawRect(p.primary, at(i / n, i % n), Size(c, c), style = Stroke(2.dp.toPx())) }
        }
    }
}

/** A small picture of [shape]: its grids, with the shared boxes gold. */
@Composable
fun ShapePreview(shape: com.pippop.game.SudokuShape, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val shared = lerp(p.boardBackground, Pieces.Yellow, 0.45f)
    Canvas(modifier.aspectRatio(1f)) {
        val n = shape.size
        val c = size.width / n
        for (r in 0 until n step 3) for (col in 0 until n step 3) {
            val i = r * n + col
            if (!shape.onBoard(i)) continue
            drawRect(if (shape.shared(i)) shared else p.boardBackground, Offset(col * c, r * c), Size(c * 3, c * 3))
            drawRect(p.lineThick, Offset(col * c, r * c), Size(c * 3, c * 3), style = Stroke(1.dp.toPx()))
        }
    }
}
