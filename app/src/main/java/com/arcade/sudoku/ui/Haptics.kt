package com.arcade.sudoku.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.arcade.sudoku.data.ProfileStore

/** The moments the phone buzzes for, each with its own feel (see the Game moments catalogue). */
enum class Buzz {
    /** You made a move: a light tick. */
    MOVE,
    /** It's your turn now: a short double buzz. */
    YOUR_TURN,
    /** The move isn't allowed: one firm buzz. */
    NOT_ALLOWED,
    /** Each number of a countdown. */
    COUNTDOWN,
    /** You won: a rising pattern. */
    WIN,
    /** You lost or tied: one soft buzz, never harsh. */
    LOSE,
}

/**
 * Buzzes for game moments, unless Vibration is off in the profile. Each buzz goes with a
 * sound and something on screen, so turning it off hides nothing.
 */
class Haptics private constructor(context: Context) {
    private val store = ProfileStore.get(context)
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun play(buzz: Buzz) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (!store.profile.value.vibration) return
        runCatching { v.vibrate(effect(buzz, v.hasAmplitudeControl())) }
    }

    private fun effect(buzz: Buzz, amplitudes: Boolean): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (buzz) {
                Buzz.MOVE, Buzz.COUNTDOWN -> return VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                Buzz.YOUR_TURN -> return VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                Buzz.NOT_ALLOWED -> return VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else -> Unit
            }
        }
        return when (buzz) {
            Buzz.MOVE, Buzz.COUNTDOWN -> VibrationEffect.createOneShot(12, 80)
            Buzz.YOUR_TURN -> waveform(longArrayOf(0, 30, 70, 30), intArrayOf(0, 140, 0, 140), amplitudes)
            Buzz.NOT_ALLOWED -> VibrationEffect.createOneShot(60, 255)
            // Three pulses, each a little stronger.
            Buzz.WIN -> waveform(longArrayOf(0, 40, 60, 50, 60, 80), intArrayOf(0, 90, 0, 170, 0, 255), amplitudes)
            Buzz.LOSE -> VibrationEffect.createOneShot(90, 70)
        }
    }

    /** Phones without amplitude control get the same rhythm at full strength. */
    private fun waveform(timings: LongArray, amps: IntArray, amplitudes: Boolean): VibrationEffect =
        if (amplitudes) VibrationEffect.createWaveform(timings, amps, -1) else VibrationEffect.createWaveform(timings, -1)

    companion object {
        @Volatile
        private var instance: Haptics? = null

        fun get(context: Context): Haptics = instance ?: synchronized(this) {
            instance ?: Haptics(context.applicationContext).also { instance = it }
        }
    }
}

/** The app's [Haptics], for a screen. */
@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember { Haptics.get(context) }
}
