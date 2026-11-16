package com.pippop.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.pippop.data.Profile
import com.pippop.data.ProfileStore
import kotlin.math.roundToInt

/** The moments the phone buzzes for, each with its own feel (see the Game moments catalogue). */
enum class Buzz(
    /** Between the Boards: the feel as (milliseconds, strength 0..1) steps, strength 0 a pause (PLAN table 9.5); null for the older ones. */
    val pattern: List<Pair<Long, Float>>? = null,
) {
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
    /** A hit: a shot that hits in Ship Battle, a capture in Ludo. One firm pulse. */
    HIT(listOf(60L to 1f)),
    /** A finished line: a Sudoku unit, four in a row, a Bingo line, a small board won. Two light ticks. */
    LINE_DONE(listOf(14L to 0.5f, 60L to 0f, 14L to 0.5f)),
    /** The dice: a roll in Ludo, four rattles then a thud. */
    DICE(listOf(10L to 0.35f, 26L to 0f, 10L to 0.35f, 26L to 0f, 10L to 0.35f, 26L to 0f, 10L to 0.35f, 34L to 0f, 24L to 0.8f)),
    /** One away: one away from a Bingo line, one card left in Colour Clash. A heartbeat, twice. */
    ONE_AWAY(listOf(28L to 0.6f, 80L to 0f, 40L to 1f, 200L to 0f, 28L to 0.6f, 80L to 0f, 40L to 1f)),
    /** A record: the record line. A tick then a firm pulse. */
    RECORD(listOf(22L to 0.45f, 50L to 0f, 44L to 1f)),
}

/** A buzz as the timings and amplitudes of a waveform; [amplitudes] is null on phones that can't set strength. */
internal class Wave(val timings: LongArray, val amplitudes: IntArray?)

/**
 * The waveform for a buzz with a [Buzz.pattern]: it starts with no delay, then each step in turn, so the steps alternate on and
 * off as a waveform does. Strength 0..1 becomes 0..255 (0 is off). Phones without amplitude control get the same rhythm at
 * full strength, as the older buzzes do (amplitudes are dropped, and the pauses stay pauses).
 */
internal fun Buzz.wave(amplitudeControl: Boolean): Wave? {
    val steps = pattern ?: return null
    val timings = longArrayOf(0L) + steps.map { it.first }
    val amps = intArrayOf(0) + steps.map { (it.second * 255f).roundToInt().coerceIn(0, 255) }
    return Wave(timings, if (amplitudeControl) amps else null)
}

/** Where a buzz goes: the phone's vibrator in the app, a fake in tests. */
internal interface BuzzOut {
    /** Whether the phone can set a buzz's strength. */
    val amplitudeControl: Boolean

    /** Buzzes [buzz]; [wave] is its waveform for those that have a pattern. */
    fun play(buzz: Buzz, wave: Wave?)
}

/**
 * Buzzes for game moments, unless Vibration is off in the profile. Each buzz goes with a
 * sound and something on screen, so turning it off hides nothing.
 */
class Haptics internal constructor(private val settings: () -> Profile, private val out: BuzzOut) {
    private constructor(context: Context, store: ProfileStore) : this({ store.profile.value }, VibratorOut(context))

    /** Buzzes [buzz] unless Vibration is off. */
    fun play(buzz: Buzz) {
        if (!settings().vibration) return
        out.play(buzz, buzz.wave(out.amplitudeControl))
    }

    companion object {
        @Volatile
        private var instance: Haptics? = null

        fun get(context: Context): Haptics = instance ?: synchronized(this) {
            val app = context.applicationContext
            instance ?: Haptics(app, ProfileStore.get(app)).also { instance = it }
        }
    }
}

/** The app's [Haptics], for a screen. */
@Composable
fun rememberHaptics(): Haptics {
    val given = LocalHaptics.current
    val context = LocalContext.current
    return given ?: remember { Haptics.get(context) }
}

private class VibratorOut(context: Context) : BuzzOut {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override val amplitudeControl get() = vibrator?.hasAmplitudeControl() == true

    override fun play(buzz: Buzz, wave: Wave?) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching { v.vibrate(effect(buzz, wave, v.hasAmplitudeControl())) }
    }

    private fun effect(buzz: Buzz, wave: Wave?, amplitudes: Boolean): VibrationEffect {
        if (wave != null) {
            return if (wave.amplitudes != null) VibrationEffect.createWaveform(wave.timings, wave.amplitudes, -1) else VibrationEffect.createWaveform(wave.timings, -1)
        }
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
            else -> error("$buzz has a pattern")
        }
    }

    /** Phones without amplitude control get the same rhythm at full strength. */
    private fun waveform(timings: LongArray, amps: IntArray, amplitudes: Boolean): VibrationEffect =
        if (amplitudes) VibrationEffect.createWaveform(timings, amps, -1) else VibrationEffect.createWaveform(timings, -1)
}

/** Where a test puts its own [Haptics] (with a fake vibrator); null in the app, which uses the real one. */
internal val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }
