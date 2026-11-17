package com.pippop.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import com.pippop.data.GameKind
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.LocalReduceMotion
import com.pippop.ui.theme.loopingFloat
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.readableOn
import com.pippop.ui.theme.textFill

/** How far a card tilts either way while the shelf is being edited, in degrees (gentle, and slow: a full swing takes four times DROP_MS). */
internal const val WIGGLE_DEGREES = 1.5f

/** The lifted card grows a little, so it reads as picked up. */
private const val LIFT_SCALE = 1.06f

/** Three cards to a row, 10 dp apart; each row stands on a 6 dp shelf. */
private const val COLUMNS = 3
private val GAP_X = 10.dp
private val BAR_HEIGHT = 6.dp
private val BAR_SPACING = 6.dp
private val ROW_SPACING = 12.dp

/** Where the cards sit in the grid, as the last layout put them (the grid's own coordinates, by place, not by game). */
private class ShelfGeometry {
    var cells: List<Rect> = emptyList()
    var rowGap: Float = 0f
    var columnGap: Float = 0f
}

/**
 * The edit bar: it takes the fixed top bar's place (the home draws it over the top bar, which stays in the layout
 * so the height never changes) with what to do and "Done". It is a live region, so a screen reader says it when
 * editing starts. A tap on it outside Done ends editing too (the home's tap handler).
 */
@Composable
internal fun ShelfEditBar(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val full = "Drag to move. Tap × to remove."
    // With large text the line would wrap and make the bar taller: it says less, and a screen reader still hears all of it.
    val short = LocalDensity.current.fontScale > 1.15f
    Row(
        modifier.background(p.background)
            .padding(horizontal = 16.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (short) "Drag to move" else full, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.weight(1f).padding(end = Space.M).semantics { contentDescription = full },
        )
        PrimaryButton("Done", onDone, Modifier.width(96.dp))
    }
}

/**
 * The shelf while it is being edited: the same grid of three, but each card wiggles (a still dashed
 * outline under reduced motion) with a round × at its corner, and a card held a moment can be
 * dragged: it lifts, follows the finger, and the others make room as it goes. The page scrolls
 * itself when the finger is near the top or bottom edge of [viewport]. Dropping saves the move
 * ([onMove], one call from the old place to the new one). Cards can also be moved and removed with a
 * screen reader's actions.
 */
@Composable
internal fun EditableShelf(
    shelf: List<GameKind>,
    editing: Boolean,
    scroll: ScrollState,
    viewport: () -> Rect,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (GameKind) -> Unit,
    /** Holding a card starts editing (and carries straight on into dragging it); false while something else (Surprise us) has the shelf. */
    canEdit: () -> Boolean,
    onEnterEdit: () -> Unit,
    /** A card as it is when the shelf is not being edited: the key art that opens its game. */
    normalCard: @Composable (GameKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val geometry = remember { ShelfGeometry() }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // While a card is dragged the order shown is the draft; the shelf itself is saved once, on drop.
    var dragged by remember { mutableStateOf<GameKind?>(null) }
    var draft by remember { mutableStateOf<List<GameKind>?>(null) }
    var dragFrom by remember { mutableIntStateOf(0) }
    // Where the finger is and where it took hold of the card, in the grid's own coordinates.
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    // -1: the page scrolls up, 1: down, 0: still.
    var edge by remember { mutableIntStateOf(0) }

    val shown = draft ?: shelf
    val currentShown by rememberUpdatedState(shown)
    val currentShelf by rememberUpdatedState(shelf)

    // After a drop the draft stays until the saved shelf has caught up with it, so nothing flickers back.
    LaunchedEffect(shelf, draft, dragged) {
        if (dragged == null && draft != null) {
            if (shelf != draft) delay(Motion.MOVE_MS.toLong())
            draft = null
        }
    }

    fun retarget() {
        val kind = dragged ?: return
        val list = draft ?: return
        var to = geometry.cells.indexOfFirst { cell ->
            Rect(cell.left - geometry.columnGap / 2, cell.top - geometry.rowGap / 2, cell.right + geometry.columnGap / 2, cell.bottom + geometry.rowGap / 2).contains(finger)
        }
        if (to < 0) {
            // Past the last card (the empty places in the last row, or under it): the end of the shelf.
            val last = geometry.cells.lastOrNull()
            if (last != null && finger.y >= last.top - geometry.rowGap / 2 && (finger.x > last.right || finger.y > last.bottom)) to = list.lastIndex
        }
        if (to < 0 || to > list.lastIndex || list[to] == kind) return
        draft = list.toMutableList().apply { remove(kind); add(to, kind) }
    }

    // Near the top or bottom edge of the page the scroll carries on by itself, and the card goes with it.
    LaunchedEffect(edge) {
        if (edge == 0) return@LaunchedEffect
        val step = with(density) { AUTO_SCROLL.toPx() }
        while (edge != 0) {
            val used = scroll.scrollBy(step * edge)
            finger += Offset(0f, used)
            retarget()
            withFrameNanos { }
        }
    }

    fun updateEdge() {
        val coords = gridCoords
        if (coords == null || !coords.isAttached) { edge = 0; return }
        val y = coords.localToRoot(finger).y
        val view = viewport()
        val zone = with(density) { EDGE_ZONE.toPx() }
        edge = when {
            y < view.top + zone && scroll.value > 0 -> -1
            y > view.bottom - zone && scroll.value < scroll.maxValue -> 1
            else -> 0
        }
    }

    val currentEditing by rememberUpdatedState(editing)
    val currentCanEdit by rememberUpdatedState(canEdit)
    val currentEnter by rememberUpdatedState(onEnterEdit)
    val currentMove by rememberUpdatedState(onMove)
    Layout(
        modifier = modifier.onGloballyPositioned { gridCoords = it }
            // One detector for the whole grid: a card held a moment lifts (and editing starts if it had not), and the same
            // gesture carries on as the drag. A quick swipe is not taken, so the page still scrolls.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val from = geometry.cells.indexOfFirst { it.contains(down.position) }
                    val list = currentShelf
                    if (from < 0 || from > list.lastIndex || !currentCanEdit()) return@awaitEachGesture
                    awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val kind = list[from]
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!currentEditing) currentEnter()
                    dragFrom = from
                    draft = list
                    dragged = kind
                    grab = down.position - geometry.cells[from].topLeft
                    finger = down.position
                    var finished = false
                    try {
                        // True when the finger was lifted; a cancelled touch is not a drop.
                        finished = drag(down.id) { change ->
                            change.consume()
                            finger = change.position
                            retarget()
                            updateEdge()
                        }
                        val to = draft?.indexOf(kind) ?: from
                        if (finished && to != from && to >= 0) currentMove(from, to)
                        // The lift-off belongs to the drag: it is not also a tap that ends editing.
                        currentEvent.changes.fastForEach { it.consume() }
                    } finally {
                        edge = 0
                        dragged = null
                        // A cancelled touch puts the card back where it was.
                        if (!finished) draft = null
                    }
                }
            },
        content = {
            shown.forEachIndexed { index, kind ->
                key(kind) {
                    if (!editing) {
                        normalCard(kind)
                    } else {
                        val actions = buildList {
                            if (index > 0) add(CustomAccessibilityAction("Move earlier") { onMove(index, index - 1); true })
                            if (index < shown.lastIndex) add(CustomAccessibilityAction("Move later") { onMove(index, index + 1); true })
                            add(CustomAccessibilityAction("Remove") { onRemove(kind); true })
                        }
                        EditCard(
                            kind = kind, index = index, count = shown.size, lifted = dragged == kind, reduceMotion = reduceMotion,
                            geometry = geometry,
                            // Where the held card's corner is, in the grid: the card follows the finger.
                            liftedAt = { finger - grab },
                            actions = actions,
                            onRemove = { onRemove(kind) },
                        )
                    }
                }
            }
            repeat((shown.size + COLUMNS - 1) / COLUMNS) {
                Box(Modifier.background(AppTheme.palette.ink.copy(alpha = 0.18f), Shapes.Full))
            }
        },
    ) { measurables, constraints ->
        val gap = GAP_X.roundToPx()
        val bar = BAR_HEIGHT.roundToPx()
        val under = BAR_SPACING.roundToPx()
        val between = ROW_SPACING.roundToPx()
        val width = constraints.maxWidth
        val cardWidth = (width - gap * (COLUMNS - 1)) / COLUMNS
        val count = shown.size
        val cards = measurables.take(count).map { it.measure(androidx.compose.ui.unit.Constraints.fixedWidth(cardWidth)) }
        val bars = measurables.drop(count).map { it.measure(androidx.compose.ui.unit.Constraints.fixed(width, bar)) }
        val rows = cards.chunked(COLUMNS).map { row -> row.maxOf { it.height } }
        val tops = ArrayList<Int>()
        var y = 0
        rows.forEach { h -> tops += y; y += h + under + bar + between }
        val height = if (rows.isEmpty()) 0 else y - between
        geometry.columnGap = gap.toFloat()
        geometry.rowGap = (under + bar + between).toFloat()
        geometry.cells = cards.indices.map { i ->
            val row = i / COLUMNS
            Rect(Offset(((i % COLUMNS) * (cardWidth + gap)).toFloat(), tops[row].toFloat()), Size(cardWidth.toFloat(), rows[row].toFloat()))
        }
        layout(width, height) {
            cards.forEachIndexed { i, c -> c.placeRelative((i % COLUMNS) * (cardWidth + gap), tops[i / COLUMNS]) }
            bars.forEachIndexed { r, b -> b.placeRelative(0, tops[r] + rows[r] + under) }
        }
    }
}

private val AUTO_SCROLL: Dp = 12.dp
private val EDGE_ZONE: Dp = 72.dp

/** What one card remembers between layouts, so it can slide from where it was shown to where it now belongs. */
private class CardMotion(var index: Int) {
    /** 0 when a slide starts, 1 when it has arrived. */
    var progress by mutableFloatStateOf(1f)
    /** The fade-in under reduced motion. */
    var fade by mutableFloatStateOf(1f)
    /** Where in the grid the card was drawn when the slide started. */
    var from = Offset.Zero
    var lastLifted = Offset.Zero
    var wasLifted = false
    var settling by mutableStateOf(false)
    var run = 0

    /** Where the card is drawn now (the grid position of its corner), given the cells of the last layout. */
    fun visual(cells: List<Rect>, at: Int): Offset {
        val cell = cells.getOrNull(at)?.topLeft ?: return from
        return if (progress < 1f) from + (cell - from) * progress else cell
    }
}

/**
 * One card in edit mode: its key art and name (not opening anything), tilting gently back and forth, with a
 * round × at the top-right corner. While it is [lifted] it follows the finger ([liftedAt] is where its corner is) and
 * its place shows a dashed gap. When its place changes it glides there (a short fade under reduced motion); a dropped
 * card settles into its place the same way. The drag itself is the grid's.
 */
@Composable
private fun EditCard(
    kind: GameKind, index: Int, count: Int, lifted: Boolean, reduceMotion: Boolean,
    geometry: ShelfGeometry, liftedAt: () -> Offset, actions: List<CustomAccessibilityAction>, onRemove: () -> Unit,
) {
    val p = AppTheme.palette
    // Each card swings a little out of step with the others (its phase comes from the game, so it keeps it when it moves).
    val tilt by loopingFloat(
        -WIGGLE_DEGREES, WIGGLE_DEGREES,
        infiniteRepeatable(
            tween(Motion.DROP_MS * 2), RepeatMode.Reverse,
            initialStartOffset = StartOffset((kind.ordinal * 230) % (Motion.DROP_MS * 4), StartOffsetType.FastForward),
        ),
        still = 0f, label = "wiggle",
    )
    val m = remember { CardMotion(index) }
    // The place changed (a neighbour was dragged past, or a screen reader moved it): the layout has not run yet, so the
    // cells are still where the card was drawn.
    if (m.index != index) {
        if (!lifted) {
            m.run++
            if (reduceMotion) m.fade = 0f else { m.from = m.visual(geometry.cells, m.index); m.progress = 0f }
        }
        m.index = index
    }
    // Let go: the card settles from where the finger left it.
    if (m.wasLifted && !lifted) {
        m.run++
        if (reduceMotion) m.fade = 0f else { m.from = m.lastLifted; m.progress = 0f; m.settling = true }
    }
    m.wasLifted = lifted
    LaunchedEffect(m.run) {
        if (m.run == 0) return@LaunchedEffect
        if (reduceMotion) animate(0f, 1f, animationSpec = tween(Motion.SHELF_FADE_MS)) { v, _ -> m.fade = v }
        else {
            animate(0f, 1f, animationSpec = tween(Motion.GLIDE_MS, easing = FastOutSlowInEasing)) { v, _ -> m.progress = v }
            m.settling = false
        }
    }
    val outline = p.ink
    Box(
        Modifier
            // A dropped card stays on top until it has reached its place.
            .zIndex(if (lifted || m.settling) 1f else 0f)
            .drawBehind {
                // Under reduced motion nothing wiggles, so a still dashed outline says the card can move; the lifted card leaves its gap dashed.
                if (reduceMotion || lifted) {
                    // Round the card, a little outside it, so the card itself does not move; the lifted card's gap is drawn on its own edge.
                    val inset = if (lifted) 1.5.dp.toPx() else -4.dp.toPx()
                    drawRoundRect(
                        outline, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(Radius.M.toPx()),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                    )
                }
            },
    ) {
        Box(
            Modifier.graphicsLayer {
                val cell = geometry.cells.getOrNull(index)?.topLeft ?: Offset.Zero
                if (lifted) {
                    val at = liftedAt()
                    m.lastLifted = at
                    translationX = at.x - cell.x
                    translationY = at.y - cell.y
                    scaleX = LIFT_SCALE
                    scaleY = LIFT_SCALE
                    shadowElevation = 12.dp.toPx()
                    shape = Shapes.M
                    clip = false
                } else {
                    val left = 1f - m.progress
                    translationX = (m.from.x - cell.x) * left
                    translationY = (m.from.y - cell.y) * left
                    if (m.settling) {
                        // The lift eases away over the same glide: its size and its shadow.
                        scaleX = 1f + (LIFT_SCALE - 1f) * left
                        scaleY = scaleX
                        shadowElevation = 12.dp.toPx() * left
                        shape = Shapes.M
                        clip = false
                    }
                    rotationZ = tilt
                    alpha = m.fade
                }
            },
        ) {
            ArtTile(
                kind, Modifier, interactive = false, description = "${kind.label}, ${index + 1} of $count", actions = actions, onClick = {},
                overlay = { RemoveButton(kind, onRemove, Modifier.align(Alignment.TopEnd)) },
            )
        }
    }
}

/** The round × on a card's corner: a 48 dp target around a smaller circle that sits on the corner. */
@Composable
private fun RemoveButton(kind: GameKind, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val fill = textFill(p.ink)
    Box(
        modifier.size(48.dp)
            .semantics { contentDescription = "Remove ${kind.label}"; role = Role.Button }
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            Modifier.padding(top = Space.XS, end = Space.XS).size(28.dp).clip(CircleShape).background(fill).border(2.dp, p.card, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = readableOn(fill), fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
        }
    }
}
