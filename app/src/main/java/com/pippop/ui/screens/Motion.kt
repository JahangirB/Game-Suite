package com.pippop.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.pippop.ui.theme.LocalReduceMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
