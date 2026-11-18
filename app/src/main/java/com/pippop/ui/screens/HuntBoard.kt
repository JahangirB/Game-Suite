package com.pippop.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pippop.game.words.HuntDir
import com.pippop.game.words.HuntView
import com.pippop.game.words.HuntViewWord
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.textFill

/*
 * Word Hunt's grid: letters on a light board, found words as bars in their finder's colour with the finder's initial, and the
 * hand: one finger drags across a word (the highlight snaps to the row or column of the main direction), two fingers pan and
 * pinch on grids wider than 10 (with a small map of where you are), and tapping a first and then a last letter does the same
 * as a drag. Cells on a 10 wide grid are about 38 dp on a phone, under the 48 dp of a comfortable touch, which is why the
 * tap-and-tap way exists; bigger grids zoom to cells of 48 dp and more.
 */

/** Where the camera is on a grid, and the controls (+, -, whole grid) that move it. The board fills in [geometry] as it is measured. */
@Stable
internal class HuntViewport(val width: Int, val height: Int) {
    var camera by mutableStateOf(HuntCamera())
    var geometry by mutableStateOf<HuntGeometry?>(null)

    val canZoom get() = width > 10
    val zoom get() = geometry?.clamp(camera)?.zoom ?: 1f
    val zoomed get() = zoom > 1.02f

    fun zoomIn() { geometry?.let { camera = it.zoomTo(camera, it.zoomInStep(zoom)) } }
    fun zoomOut() { geometry?.let { camera = it.zoomTo(camera, it.zoomOutStep(zoom)) } }
    fun fit() { camera = HuntCamera() }
    fun centreOn(cell: HuntCell) { geometry?.let { camera = it.centreOn(camera, cell) } }
}

/** A drag in progress: from [start], with the finger at [finger] in window pixels. */
private class HuntDrag(val start: HuntCell, val finger: Offset)

/** The cells of a word, first to last. */
private fun HuntViewWord.ends(): Pair<HuntCell, HuntCell>? {
    val r = row ?: return null
    val c = col ?: return null
    val d = HuntDir.entries.firstOrNull { it.key == dir } ?: return null
    return HuntCell(r, c) to HuntCell(r + d.dr * (length - 1), c + d.dc * (length - 1))
}

/** A unit is the size of a cell at 48 dp; the grid is drawn in units and scaled to the zoom. */
@Composable
internal fun HuntBoard(
    view: HuntView,
    seats: List<HuntSeat>,
    viewport: HuntViewport,
    enabled: Boolean,
    anchor: HuntCell?,
    onAnchor: (HuntCell?) -> Unit,
    onClaim: (HuntCell, HuntCell) -> Unit,
    refusals: Int,
    finds: Int,
    flash: HuntMoment?,
    modifier: Modifier = Modifier,
) {
    val p = AppTheme.palette
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val claim by rememberUpdatedState(onClaim)
    val setAnchor by rememberUpdatedState(onAnchor)
    val anchorNow by rememberUpdatedState(anchor)
    val width = view.width
    val height = view.height

    var drag by remember { mutableStateOf<HuntDrag?>(null) }
    // The last claim sent: it stays lit until the room answers (a find clears it, a refusal turns it red and shakes).
    var sent by remember { mutableStateOf<Pair<HuntCell, HuntCell>?>(null) }
    var refused by remember { mutableStateOf(false) }
    LaunchedEffect(finds) { sent = null; refused = false }
    LaunchedEffect(refusals) { if (refusals > 0 && sent != null) { refused = true; kotlinx.coroutines.delay(if (reduce) 900 else 650); sent = null; refused = false } }
    LaunchedEffect(sent) { if (sent != null && !refused) { kotlinx.coroutines.delay(2500); sent = null } }

    val glow = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash == null) glow.snapTo(0f)
        else if (reduce) glow.snapTo(1f)
        else { glow.snapTo(0f); glow.animateTo(1f, tween(220)); glow.animateTo(0.55f, tween(700)) }
    }
    val pulse = if (view.hints.isNotEmpty()) loopingFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), 1f, "hunt hint").value else 1f

    val measurer = rememberTextMeasurer()
    val letterStyle = remember { TextStyle(color = Board.Ink, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Black) }
    val initialStyle = remember { TextStyle(fontSize = TextSize.Micro, fontFamily = DisplayFont, fontWeight = FontWeight.Black) }
    val fixed = Density(density.density, 1f)
    val letters = remember(measurer, density.density) { ('A'..'Z').associateWith { measurer.measure(it.toString(), letterStyle, density = fixed) } }
    val initials = remember(measurer, seats, density.density) {
        seats.associate { s -> s.mark to measurer.measure(s.initial, initialStyle.copy(color = readableOn(textFill(s.color))), density = fixed) }
    }

    BoxWithConstraints(modifier.clip(Shapes.M).clipToBounds()) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val touchPx = with(density) { 48.dp.toPx() }
        val geometry = remember(vw, vh, width, height, touchPx) { HuntGeometry(vw, vh, width, height, touchPx) }
        SideEffect {
            if (viewport.geometry !== geometry) viewport.geometry = geometry
            val clamped = geometry.clamp(viewport.camera)
            if (clamped != viewport.camera) viewport.camera = clamped
        }
        val cam = geometry.clamp(viewport.camera)
        val camNow by rememberUpdatedState(cam)

        // Dragging into the window's edge scrolls a zoomed grid, so a long word can be dragged right across.
        val edgePx = with(density) { 40.dp.toPx() }
        val speedPx = with(density) { 520.dp.toPx() }
        LaunchedEffect(drag != null, geometry) {
            if (drag == null || !geometry.canZoom) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                val dt = (t - last) / 1_000_000_000f
                last = t
                val d = drag ?: break
                fun push(pos: Float, size: Float): Float {
                    val near = if (pos < edgePx) -(edgePx - pos) / edgePx else if (pos > size - edgePx) (pos - (size - edgePx)) / edgePx else 0f
                    return near.coerceIn(-1f, 1f)
                }
                val dx = push(d.finger.x, vw)
                val dy = push(d.finger.y, vh)
                if (dx != 0f || dy != 0f) {
                    val c = geometry.clamp(viewport.camera)
                    viewport.camera = geometry.clamp(c.copy(x = c.x - dx * speedPx * dt, y = c.y - dy * speedPx * dt))
                }
            }
        }

        fun endOf(start: HuntCell, finger: Offset, c: HuntCamera): HuntCell = geometry.toGrid(c, finger).let { huntSnap(start, it.x, it.y, width, height) }

        // A tap: the first letter, then the last (the same for a finger and for a screen reader's double-tap).
        fun tap(cell: HuntCell) {
            val a = anchorNow
            when {
                a == null -> setAnchor(cell)
                a == cell -> setAnchor(null)
                else -> {
                    val end = huntSnap(a, cell.col + 0.5f, cell.row + 0.5f, width, height)
                    setAnchor(null)
                    if (end != a) { sent = a to end; refused = false; claim(a, end) }
                }
            }
        }
        // A finger well outside the grid (more than a cell past its edge) is not on any word: letting go there cancels.
        fun outside(finger: Offset, c: HuntCamera) = geometry.toGrid(c, finger).let { !huntNear(it.x, it.y, width, height) }

        val touch = if (!enabled) Modifier else Modifier.pointerInput(geometry) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val first = geometry.cellAt(camNow, down.position)
                var transforming = false
                var dragging = false
                var lastPos = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    if (geometry.canZoom && event.changes.count { it.pressed } >= 2) {
                        // Two fingers: pan and pinch, and what the first finger started is dropped.
                        if (!transforming) { transforming = true; dragging = false; drag = null }
                        val factor = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centre = event.calculateCentroid(useCurrent = false)
                        viewport.camera = geometry.transform(camNow, centre, pan, factor)
                        event.changes.forEach { it.consume() }
                    } else if (!transforming) {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastPos = change.position
                        if (!dragging && first != null && (change.position - down.position).getDistance() > slop) dragging = true
                        if (dragging && first != null) { drag = HuntDrag(first, change.position); change.consume() }
                    }
                }
                drag = null
                if (transforming || first == null) return@awaitEachGesture
                if (dragging) {
                    if (outside(lastPos, camNow)) return@awaitEachGesture
                    val end = endOf(first, lastPos, camNow)
                    if (end != first) { setAnchor(null); sent = first to end; refused = false; claim(first, end) }
                } else tap(first)
            }
        }

        val live = drag
        val shownLine = when {
            live != null -> if (outside(live.finger, cam)) null else live.start to endOf(live.start, live.finger, cam)
            else -> sent
        }
        val said = buildString {
            append("Word grid, $width by $height. Drag across a word, or tap its first and last letters.")
            if (cam.zoom > 1.02f) append(" Zoomed in.")
            anchor?.let { append(" First letter chosen: ${view.rows.getOrNull(it.row)?.getOrNull(it.col) ?: ""}, row ${it.row + 1}, column ${it.col + 1}.") }
        }
        val colors = p
        Canvas(Modifier.fillMaxSize().testTag("huntGrid").semantics { contentDescription = said }.then(touch)) {
            val unit = 48.dp.toPx()
            val cell = geometry.cell(cam)
            val s = cell / unit
            fun px(dp: Float) = dp.dp.toPx() / s
            // The board, then everything on it in units.
            drawRoundRect(Board.Bone, Offset(cam.x, cam.y), Size(cell * width, cell * height), CornerRadius(8.dp.toPx()))
            withTransform({ translate(cam.x, cam.y); scale(s, s, Offset.Zero) }) {
                // Faint lines between the cells, so the rows and columns are easy to follow.
                val line = Board.Ink.copy(alpha = 0.07f)
                for (c in 1 until width) drawLine(line, Offset(c * unit, 0f), Offset(c * unit, height * unit), px(1f))
                for (r in 1 until height) drawLine(line, Offset(0f, r * unit), Offset(width * unit, r * unit), px(1f))
                fun centre(c: HuntCell) = Offset((c.col + 0.5f) * unit, (c.row + 0.5f) * unit)
                fun bar(a: HuntCell, b: HuntCell, w: Float, color: Color, outline: Color? = null, edge: Float = px(2.5f)) {
                    if (outline != null) drawLine(outline, centre(a), centre(b), w * unit + edge * 2, StrokeCap.Round)
                    drawLine(color, centre(a), centre(b), w * unit, StrokeCap.Round)
                }
                // Found words: a bar in the finder's colour, edged with it.
                for (w in view.words) {
                    val (a, b) = w.ends() ?: continue
                    val seat = seats.of(w.foundBy)?.color ?: continue
                    if (flash?.word == w.word) bar(a, b, 1.18f, seat.copy(alpha = 0.5f * glow.value))
                    bar(a, b, 0.84f, lerp(Board.Bone, seat, 0.4f), outline = seat, edge = px(2f))
                }
                // The hand: the line being dragged, or the last one sent.
                shownLine?.let { (a, b) ->
                    val fill = if (refused) colors.bad.copy(alpha = 0.7f) else Board.GlowSoft.copy(alpha = 0.92f)
                    bar(a, b, 0.84f, fill, outline = Board.Ink, edge = px(2.5f))
                }
                anchor?.takeIf { shownLine == null }?.let { a ->
                    drawCircle(Board.GlowSoft, 0.44f * unit, centre(a))
                    drawCircle(Board.Ink, 0.44f * unit, centre(a), style = Stroke(px(3f)))
                }
                // Letters.
                for (r in 0 until height) {
                    val row = view.rows.getOrNull(r) ?: continue
                    for (c in 0 until width) {
                        val layout = letters[row.getOrNull(c)] ?: continue
                        drawText(layout, topLeft = Offset((c + 0.5f) * unit - layout.size.width / 2f, (r + 0.5f) * unit - layout.size.height / 2f))
                    }
                }
                // The finder's initial on the first letter of each found word.
                for (w in view.words) {
                    val (a, _) = w.ends() ?: continue
                    val seat = seats.of(w.foundBy) ?: continue
                    val at = Offset((a.col + 0.27f) * unit, (a.row + 0.27f) * unit)
                    drawCircle(textFill(seat.color), 0.18f * unit, at)
                    drawCircle(Board.Bone, 0.18f * unit, at, style = Stroke(px(1.2f)))
                    if (cell >= 26.dp.toPx()) initials[seat.mark]?.let { drawText(it, topLeft = at - Offset(it.size.width / 2f, it.size.height / 2f)) }
                }
                // Your hint: a double ring round the first letter, pulsing (still but just as plain when motion is reduced).
                for ((r, c) in view.hints) {
                    val at = centre(HuntCell(r, c))
                    val radius = (0.46f + 0.08f * pulse) * unit
                    drawCircle(Board.Glow, radius, at, style = Stroke(0.13f * unit))
                    drawCircle(Board.Ink, radius + 0.065f * unit, at, style = Stroke(px(2f)))
                    drawCircle(Board.Ink, radius - 0.065f * unit, at, style = Stroke(px(2f)))
                }
            }
            drawRoundRect(Board.Ink, Offset(cam.x, cam.y), Size(cell * width, cell * height), CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
        if (enabled) HuntCellNodes(view, seats, geometry, cam, anchor, ::tap)
    }
}

/**
 * One invisible node per cell in view, so a screen reader can move over the letters ("Row 3, column 5, T", and who found the word it is
 * part of) and double-tap each: the first sets the start, the second the end, as tapping does. Nothing is drawn.
 */
@Composable
private fun HuntCellNodes(view: HuntView, seats: List<HuntSeat>, g: HuntGeometry, cam: HuntCamera, anchor: HuntCell?, onTap: (HuntCell) -> Unit) {
    val density = LocalDensity.current
    val cell = g.cell(cam)
    val dp = with(density) { cell.toDp() }
    val owners = HashMap<HuntCell, HuntSeat>()
    for (w in view.words) {
        val seat = seats.of(w.foundBy) ?: continue
        val (a, b) = w.ends() ?: continue
        huntLine(a, b).forEach { owners[it] = seat }
    }
    val hints = view.hints.map { HuntCell(it.first, it.second) }.toSet()
    val (l, t, r, b) = g.seen(cam)
    val rows = floor(t).toInt()..minOf(view.height - 1, ceil(b).toInt() - 1)
    val cols = floor(l).toInt()..minOf(view.width - 1, ceil(r).toInt() - 1)
    // Said when the start is chosen, so the next double-tap is known to end the word.
    Box(
        Modifier.size(1.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = anchor?.let { "Start: row ${it.row + 1}, column ${it.col + 1}. Now pick the last letter." }.orEmpty()
        },
    )
    for (row in rows) for (col in cols) {
        val c = HuntCell(row, col)
        val letter = view.rows.getOrNull(row)?.getOrNull(col) ?: continue
        val said = "Row ${row + 1}, column ${col + 1}, $letter" +
            (owners[c]?.let { ", found by ${it.name}" } ?: "") + (if (c == anchor) ", start" else "") + (if (c in hints) ", your hint: a word starts here" else "")
        Box(
            Modifier.offset { IntOffset((cam.x + col * cell).roundToInt(), (cam.y + row * cell).roundToInt()) }.size(dp)
                .semantics { contentDescription = said; role = Role.Button; onClick(label = if (anchor == null) "Start here" else "End here") { onTap(c); true } },
        )
    }
}
