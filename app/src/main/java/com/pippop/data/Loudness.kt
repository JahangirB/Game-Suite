package com.pippop.data

/**
 * How loud the app's sounds are (the profile's Sound row). Each sound has its own place in the
 * mix ([com.pippop.ui.Sound.volume]); this moves all of them together. [NORMAL] is the level the
 * app always had, so everyone who had sound on keeps it.
 */
enum class Loudness(val label: String) {
    OFF("Off"),
    QUIET("Quiet"),
    NORMAL("Normal"),
    LOUD("Loud");

    /** The level (0 to 1) of a sound whose place in the mix is [base]: Quiet is 0.45 of Normal, Loud lifts the place by 1.4 up to full. */
    fun level(base: Float): Float = when (this) {
        OFF -> 0f
        QUIET -> base * QUIET_SHARE
        NORMAL -> base
        LOUD -> minOf(1f, base * LOUD_LIFT)
    }

    companion object {
        const val QUIET_SHARE = 0.45f
        const val LOUD_LIFT = 1.4f
    }
}
