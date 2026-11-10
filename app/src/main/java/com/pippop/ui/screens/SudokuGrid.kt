package com.pippop.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.pippop.ui.Sound
import com.pippop.ui.SoundOnChange
import com.pippop.ui.rememberSfx
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pippop.game.BoardState
import com.pippop.game.Sudoku
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.Pieces
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The 9x9 board. [hidden] blanks it (countdown or pause) so nobody gets a head start.
 * [errors] are drawn in red; [solution], when given, is shown for cells that were not solved.
 * With [celebrate], a row, column or box filled with no error sweeps gold.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SudokuGrid(
    board: BoardState,
    errors: Set<Int>,
    selected: Int?,
    hidden: Boolean,
    solution: List<Int>?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** A long press on a square: the pencil-mark helper fills in what it could be. */
    onLongPress: ((Int) -> Unit)? = null,
    /** Sudoku together: the colour of whoever put each placed digit there (null: the usual ink). */
    inkOf: ((Int) -> Color?)? = null,
    /** Sudoku together: the squares the others are on, each ringed in their colour. */
    rings: Map<Int, Color> = emptyMap(),
    /** Sudoku together: a wrong digit (square to digit) that just cost a life, shown for a moment. */
    flash: Pair<Int, Int>? = null,
    /** Sweep gold along each unit the player completes (only where a full unit is known to be right). */
    celebrate: Boolean = false,
) {
    val p = AppTheme.palette
    // Notepad paper: every other 3x3 box faintly tinted, calmer than the highlights.
    val tinted = lerp(p.boardBackground, p.cellPeer, 0.6f)
    val tintedPeer = lerp(p.cellPeer, p.cellSameDigit, 0.4f)
    val sweeps = rememberUnitSweeps(board, errors, celebrate)
    // A pencil tap for each digit placed, and the gentle bonk when one clashes.
    SoundOnChange(board.cells.count { it != 0 }) { play(Sound.SUDOKU_DIGIT) }
    SoundOnChange(errors.size.takeIf { it > 0 }) { play(Sound.ERROR) }
    val selectedDigit = selected?.let { board.cells[it] }?.takeIf { it != 0 }
    // The highlight spreads out from the selected cell like a ripple: [wave] is how many cells
    // out it has reached.
    val wave = remember { Animatable(9f) }
    LaunchedEffect(selected, selectedDigit) {
        if (selected == null) return@LaunchedEffect
        wave.snapTo(0f)
        wave.animateTo(9f, tween(WAVE_MS, easing = LinearEasing))
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // The Toy Box board: rounded, outlined in ink, standing on its edge with a soft
            // shadow under it, like a notepad page on the table.
            .drawBehind { boxShadow(Offset.Zero, size, Radius.M.toPx(), drop = 8.dp.toPx(), strength = 0.12f) }
            .pressedEdge(p.lineThick, Radius.M, 4.dp)
            .clip(Shapes.M)
            .background(p.boardBackground)
            .border(3.dp, p.lineThick, Shapes.M)
    ) {
        val cellSize = maxWidth / 9
        val density = LocalDensity.current
        val digitSize = with(density) { (cellSize * 0.6f).toSp() }
        val noteSize = with(density) { (cellSize * 0.24f).toSp() }

        Column {
            for (row in 0 until 9) {
                Row {
                    for (col in 0 until 9) {
                        val index = row * 9 + col
                        val given = board.givens[index]
                        val entered = board.cells[index]
                        val revealed = solution != null && !given && entered != solution[index]
                        val flashed = !hidden && flash?.first == index && entered == 0
                        val digit = if (revealed) solution!![index] else if (flashed) flash!!.second else entered
                        val isError = !hidden && !given && (index in errors || flashed)
                        val ink = if (given || revealed || hidden) null else inkOf?.invoke(index)
                        val ring = rings[index]?.takeIf { !hidden }
                        val paper = if ((row / 3 + col / 3) % 2 == 1) tinted else p.boardBackground
                        val glow = sweeps.glowAt(index)

                        val lit = when {
                            hidden || selected == null || index == selected || isError -> null
                            // The same number elsewhere stands out in the strong colour (it's
                            // what you're hunting), the square you picked in the calmer one.
                            selectedDigit != null && digit == selectedDigit -> p.cellSelected
                            // On tinted paper the row, column and box highlight is a step
                            // stronger, so it stands out as clearly as on the plain boxes.
                            Sudoku.arePeers(index, selected) -> if (paper == tinted) tintedPeer else p.cellPeer
                            else -> null
                        }
                        val background = when {
                            hidden -> paper
                            index == selected -> p.cellSameDigit
                            isError -> p.cellWrong
                            lit != null -> {
                                // Cells further away light up as the wave reaches them.
                                val away = maxOf(abs(row - selected!! / 9), abs(col - selected % 9))
                                lerp(paper, lit, ((wave.value - away + 1f) / 1.5f).coerceIn(0f, 1f))
                            }
                            else -> paper
                        }.let { if (glow > 0f) lerp(it, Pieces.Yellow, glow * 0.6f) else it }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(cellSize)
                                .background(background)
                                // The selected square also gets a coral ring, so it never hangs on its fill alone.
                                .then(if (!hidden && index == selected) Modifier.border(2.dp, p.primary) else Modifier)
                                // Where a partner is: a ring in their colour, inside your own.
                                .then(if (ring != null) Modifier.padding(if (index == selected) 2.dp else 0.dp).border(2.5.dp, ring, Shapes.S) else Modifier)
                                .combinedClickable(
                                    enabled = !hidden,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClick = onLongPress?.let { { it(index) } },
                                ) { onSelect(index) },
                        ) {
                            when {
                                hidden -> Unit
                                digit != 0 -> Text(
                                    text = digit.toString(),
                                    fontSize = digitSize,
                                    // Printed digits in bold, yours a step lighter (and in blue).
                                    fontWeight = if (given) FontWeight.Bold else FontWeight.SemiBold,
                                    // A clash is underlined as well as red, so it shows without colour.
                                    textDecoration = if (isError) TextDecoration.Underline else null,
                                    color = when {
                                        revealed -> p.good
                                        isError -> p.wrong
                                        given -> p.given
                                        ink != null -> ink
                                        else -> p.entry
                                    },
                                )
                                board.notes[index] != 0 -> Notes(board.noteDigits(index), noteSize)
                            }
                        }
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 9
            // The outside edge is the board's border.
            for (i in 1..8) {
                val thick = i % 3 == 0
                val stroke = if (thick) 2.5.dp.toPx() else 1.dp.toPx()
                val color = if (thick) p.lineThick else p.lineThin
                val position = i * step
                drawLine(color, Offset(position, 0f), Offset(position, size.height), stroke)
                drawLine(color, Offset(0f, position), Offset(size.width, position), stroke)
            }
            // A finished unit's tick, in the corner of its last square.
            for (sweep in sweeps) {
                val last = sweep.unit.last()
                doneTick(Offset((last % 9 + 0.78f) * step, (last / 9 + 0.22f) * step), step * 0.17f, p.good, sweep.tick)
            }
        }
    }
}

/** How long the highlight takes to spread across the board. */
private const val WAVE_MS = 420

/** How long a finished unit's celebration lasts: the gold runs in the first [SWEEP_RUN] of it, then the tick fades. */
private const val SWEEP_MS = 900

/** The share of [SWEEP_MS] the gold takes to run along the unit (about 560 ms). */
private const val SWEEP_RUN = 0.62f

/** A finished row, column or box: [unit] is its cells in order, [progress] goes 0 to 1 once. */
internal class UnitSweep(val unit: IntArray) {
    val progress = Animatable(0f)

    /** How strong the tick at the end is (0 to 1): it comes in as the gold arrives, then fades. */
    val tick get() = minOf((progress.value - 0.5f) / 0.12f, (1f - progress.value) / 0.25f).coerceIn(0f, 1f)
}

/**
 * The gold sweeps playing on [board]: one for each row, column or box ([com.pippop.game.SudokuShape.units])
 * that has just been filled with no square in [errors], and only when [celebrate]. Units already
 * full when the board first shows, or when a new puzzle starts, don't sweep; with motion
 * reduced nothing does.
 */
@Composable
internal fun rememberUnitSweeps(board: BoardState, errors: Set<Int>, celebrate: Boolean): List<UnitSweep> {
    val still = LocalReduceMotion.current
    val sfx = rememberSfx()
    val scope = rememberCoroutineScope()
    val units = board.shape.units
    // A new puzzle (new givens) starts afresh: what is full then is where it began.
    val sweeps = remember(board.givens, celebrate) { mutableStateListOf<UnitSweep>() }
    val seen = remember(board.givens, celebrate) { arrayOf<Set<Int>?>(null) }
    val full = remember(board.cells, errors, celebrate) {
        if (!celebrate) emptySet() else units.indices.filterTo(HashSet()) { u -> units[u].all { board.cells[it] != 0 && it !in errors } }
    }
    LaunchedEffect(seen, full) {
        val before = seen[0]
        seen[0] = full
        if (before == null || still) return@LaunchedEffect
        val fresh = full - before
        if (fresh.isEmpty()) return@LaunchedEffect
        sfx.play(Sound.NUMBERS_SUM_RIGHT)
        for (u in fresh) {
            val sweep = UnitSweep(units[u])
            sweeps += sweep
            // Its own coroutine: the next digit placed must not cut a sweep short.
            scope.launch {
                try {
                    sweep.progress.animateTo(1f, tween(SWEEP_MS, easing = LinearEasing))
                } finally {
                    sweeps -= sweep
                }
            }
        }
    }
    return sweeps
}

/** How gold [cell] is (0 to 1): each sweep's bright spot passes along its unit in order. */
internal fun List<UnitSweep>.glowAt(cell: Int): Float {
    var glow = 0f
    for (sweep in this) {
        val k = sweep.unit.indexOf(cell)
        if (k < 0) continue
        // From two squares before the first to two past the last.
        val head = -2f + 12f * (sweep.progress.value / SWEEP_RUN).coerceAtMost(1f)
        glow = maxOf(glow, 1f - abs(head - k) / 2f)
    }
    return glow.coerceIn(0f, 1f)
}

/** A small round badge with a tick in it at [center], in [color]; [alpha] fades it (0 draws nothing). */
internal fun DrawScope.doneTick(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    drawCircle(color.copy(alpha = alpha), radius, center)
    val tick = Path().apply {
        moveTo(center.x - radius * 0.45f, center.y + radius * 0.02f)
        lineTo(center.x - radius * 0.12f, center.y + radius * 0.35f)
        lineTo(center.x + radius * 0.48f, center.y - radius * 0.32f)
    }
    drawPath(tick, Color.White.copy(alpha = alpha), style = Stroke(radius * 0.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Pencil marks laid out like a phone keypad inside one cell. */
@Composable
private fun Notes(digits: List<Int>, fontSize: TextUnit) {
    val color = AppTheme.palette.note
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 3) {
            Row(Modifier.weight(1f)) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (digit in digits) Text(digit.toString(), fontSize = fontSize, color = color)
                    }
                }
            }
        }
    }
}
