package com.pippop.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.util.lerp
import com.pippop.data.GameKind
import com.pippop.ui.art.color
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.LocalReduceMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * Shared movement for the games, so a change is seen arriving instead of just appearing:
 * pieces slide in from where they came from, cards flip, letters drop and shake.
 */
object Motion {
    /** A piece moving to its place. */
    const val MOVE_MS = 450
    /** A card turning over. */
    const val FLIP_MS = 380
    /** A letter dropping into its cell, and the gap before the next one starts. */
    const val DROP_MS = 300
    const val STAGGER_MS = 110
}

/**
 * Slides this in from ([dx], [dy]) away, turned by [rotation] degrees, whenever [key] changes
 * (and when it first appears, with [onAppear]). A card played from a hand, a tile laid on the
 * table.
 */
fun Modifier.enterFrom(
    key: Any?,
    dx: Dp = 0.dp,
    dy: Dp = 0.dp,
    rotation: Float = 0f,
    scaleFrom: Float = 1f,
    durationMs: Int = Motion.MOVE_MS,
    delayMs: Int = 0,
    onAppear: Boolean = false,
): Modifier = composed {
    if (LocalReduceMotion.current) return@composed Modifier
    val t = remember { Animatable(if (onAppear) 0f else 1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!seen && !onAppear) {
            seen = true
            return@LaunchedEffect
        }
        seen = true
        t.snapTo(0f)
        delay(delayMs.toLong())
        t.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    val density = LocalDensity.current
    graphicsLayer {
        val k = 1f - t.value
        translationX = with(density) { dx.toPx() } * k
        translationY = with(density) { dy.toPx() } * k
        rotationZ = rotation * k
        val s = scaleFrom + (1f - scaleFrom) * t.value
        scaleX = s
        scaleY = s
    }
}

/** Drops this in from above with a little bounce, [delayMs] after [key] changes or it appears. */
fun Modifier.dropIn(key: Any?, delayMs: Int = 0, height: Dp = 36.dp): Modifier = composed {
    if (LocalReduceMotion.current) return@composed Modifier
    val y = remember { Animatable(-1f) }
    LaunchedEffect(key) {
        y.snapTo(-1f)
        delay(delayMs.toLong())
        y.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val density = LocalDensity.current
    graphicsLayer {
        translationY = with(density) { height.toPx() } * y.value
        alpha = (1f + y.value).coerceIn(0f, 1f)
    }
}

/** Gives this a short side-to-side shake [delayMs] after [key] changes to something non-null. */
fun Modifier.shake(key: Any?, delayMs: Int = 0, strength: Dp = 4.dp): Modifier = composed {
    if (LocalReduceMotion.current) return@composed Modifier
    val x = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        delay(delayMs.toLong())
        x.animateTo(0f, keyframes {
            durationMillis = 360
            -1f at 60
            1f at 140
            -0.6f at 220
            0.4f at 290
        })
    }
    val density = LocalDensity.current
    graphicsLayer { translationX = with(density) { strength.toPx() } * x.value }
}

/**
 * A card that turns over: [front] when [faceUp], else [back], turning round the vertical
 * axis. Swapping what [front] shows while face up turns it again (pass a new [key]).
 */
@Composable
fun FlipCard(faceUp: Boolean, modifier: Modifier = Modifier, key: Any? = null, back: @Composable () -> Unit, front: @Composable () -> Unit) {
    val turn = remember { Animatable(if (faceUp) 180f else 0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(faceUp, key) {
        if (reduce) {
            turn.snapTo(if (faceUp) 180f else 0f)
        } else if (faceUp) {
            if (turn.value >= 180f) turn.snapTo(0f)
            turn.animateTo(180f, tween(Motion.FLIP_MS, easing = FastOutSlowInEasing))
        } else {
            turn.animateTo(0f, tween(Motion.FLIP_MS, easing = FastOutSlowInEasing))
        }
    }
    val density = LocalDensity.current.density
    Box(modifier.graphicsLayer { rotationY = turn.value; cameraDistance = 14 * density }) {
        if (turn.value < 90f) back() else Box(Modifier.graphicsLayer { rotationY = 180f }) { front() }
    }
}

/**
 * Where each game's tile is on the Home shelf (in window coordinates), and which game the
 * player just chose to open from it. The Home reports; the app reads it to grow a game out of
 * its tile and to shrink it back.
 */
class TileBounds {
    val rects = HashMap<GameKind, Rect>()
    /** Set by the Home right before it opens a game from a tile; the app takes it. */
    var pending: GameKind? = null
}

/** Reports this tile's on-screen rectangle to [bounds] while it is on the Home. */
fun Modifier.reportTile(bounds: TileBounds, kind: GameKind): Modifier = composed {
    DisposableEffect(kind) { onDispose { bounds.rects.remove(kind) } }
    onGloballyPositioned { bounds.rects[kind] = Rect(it.positionInRoot(), it.size.toSize()) }
}

/**
 * A game opening out of its tile: a block in the game's colour grows from the tile to the whole
 * screen, the game takes its place under it and the colour fades away to show the board.
 * Going back runs it in reverse. Taps are swallowed while it plays (about a third of a second opening, a little longer going back).
 */
class TileTransition(private val bounds: TileBounds) {
    private val grow = Animatable(0f)
    private val fade = Animatable(0f)
    private var kind by mutableStateOf<GameKind?>(null)
    /** True while the animation plays: the app ignores back and taps for that moment. */
    var busy by mutableStateOf(false)
        private set

    /** Grows [game]'s colour from its tile, calls [switch] once the screen is covered, then reveals. */
    suspend fun open(game: GameKind, switch: () -> Unit) {
        if (busy) return
        busy = true
        kind = game
        grow.snapTo(0f); fade.snapTo(1f)
        grow.animateTo(1f, tween(OPEN_MS, easing = FastOutSlowInEasing))
        switch()
        fade.animateTo(0f, tween(FADE_MS))
        kind = null
        busy = false
    }

    /** Covers the game with its colour, calls [switch] to show the Home, then shrinks it into the tile. */
    suspend fun close(game: GameKind, switch: () -> Unit) {
        if (busy) return
        busy = true
        kind = game
        grow.snapTo(1f); fade.snapTo(0f)
        fade.animateTo(1f, tween(FADE_MS))
        switch()
        // One frame, so the Home has been laid out and its tile has reported where it is.
        withFrameNanos { }
        grow.animateTo(0f, tween(OPEN_MS, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(FADE_MS / 2))
        kind = null
        busy = false
    }

    /** The colour block, over everything while it plays; give it the same size as the screen it covers. */
    @Composable
    fun Layer(modifier: Modifier = Modifier) {
        val game = kind ?: return
        val colour = game.color
        val corner = Radius.L
        var origin by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier
                .onGloballyPositioned { origin = it.positionInRoot() }
                // Swallows every touch so nothing underneath can be tapped twice.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } }
                .drawBehind {
                    val t = grow.value
                    val full = Rect(Offset.Zero, size)
                    // A tile that is not on screen any more: grow from the middle of the screen.
                    val tile = bounds.rects[game]?.translate(-origin.x, -origin.y)?.takeIf { it.overlaps(full) }
                        ?: Rect(size.center, 0f)
                    val r = Rect(
                        lerp(tile.left, full.left, t), lerp(tile.top, full.top, t),
                        lerp(tile.right, full.right, t), lerp(tile.bottom, full.bottom, t),
                    )
                    drawRoundRect(
                        colour.copy(alpha = fade.value), r.topLeft, r.size,
                        CornerRadius(lerp(corner.toPx(), 0f, t)),
                    )
                },
        )
    }

    companion object {
        const val OPEN_MS = 200
        const val FADE_MS = 120
    }
}
