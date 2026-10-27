package com.arcade.sudoku.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when the phone's "Remove animations" setting is on (animator duration scale 0). Moving
 * pieces then jump straight to their place and looping decorations hold still. Gameplay that
 * is motion itself (the puck, the golf ball, the clock) keeps moving.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Reads the phone's setting once for this screen. */
@Composable
fun reduceMotionSetting(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * A value that loops from [from] to [to] for decoration (bobbing, waving, glowing), or holds
 * at [still] when motion is reduced.
 */
@Composable
fun loopingFloat(from: Float, to: Float, spec: InfiniteRepeatableSpec<Float>, still: Float, label: String): State<Float> {
    if (LocalReduceMotion.current) return remember { mutableFloatStateOf(still) }
    return rememberInfiniteTransition(label = label).animateFloat(from, to, spec, label = label)
}
