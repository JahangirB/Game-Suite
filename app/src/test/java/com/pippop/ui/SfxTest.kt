package com.pippop.ui

import com.pippop.data.Loudness
import com.pippop.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Sound setting does to every sound, with a fake player in place of the SoundPool. */
class SfxTest {
    private class Played(val sound: Sound, val level: Float)

    private fun sfx(profile: Profile, played: MutableList<Played>) = Sfx({ profile }, { s, level, _ -> played += Played(s, level) })

    @Test fun loudnessMapsTheMix() {
        assertEquals(0f, Loudness.OFF.level(0.65f), 0f)
        assertEquals(0.65f * 0.45f, Loudness.QUIET.level(0.65f), 1e-6f)
        assertEquals(0.65f, Loudness.NORMAL.level(0.65f), 0f)
        assertEquals(0.65f * 1.4f, Loudness.LOUD.level(0.65f), 1e-6f)
        assertEquals(1f, Loudness.LOUD.level(0.9f), 0f)
        for (s in Sound.entries) assertTrue(Loudness.LOUD.level(s.volume) <= 1f)
    }

    @Test fun everySoundFollowsTheLoudness() {
        for ((loudness, factor) in listOf(Loudness.QUIET to 0.45f, Loudness.NORMAL to 1f, Loudness.LOUD to 1.4f)) {
            val played = mutableListOf<Played>()
            val sfx = sfx(Profile(loudness = loudness), played)
            Sound.entries.forEach { sfx.play(it) }
            assertEquals(Sound.entries.size, played.size)
            played.forEach { assertEquals(minOf(1f, it.sound.volume * factor), it.level, 1e-6f) }
        }
    }

    @Test fun offPlaysNothing() {
        val played = mutableListOf<Played>()
        val sfx = sfx(Profile(loudness = Loudness.OFF), played)
        Sound.entries.forEach { sfx.play(it); sfx.play(it, Sfx.OTHERS, others = true) }
        assertTrue(played.isEmpty())
    }

    @Test fun othersKeepTheirShareOfTheLoudness() {
        val played = mutableListOf<Played>()
        sfx(Profile(loudness = Loudness.LOUD), played).play(Sound.WIN, Sfx.OTHERS, others = true)
        assertEquals(Sfx.OTHERS, played.single().level, 1e-6f)
    }

    @Test fun theirMovesOffSilencesOnlyOthers() {
        val played = mutableListOf<Played>()
        val sfx = sfx(Profile(othersMoves = false), played)
        sfx.play(Sound.C4_DROP, Sfx.OTHERS, others = true)
        sfx.play(Sound.TTT_O, Sfx.OTHERS, others = true)
        assertTrue(played.isEmpty())
        sfx.play(Sound.C4_DROP)
        assertEquals(listOf(Sound.C4_DROP), played.map { it.sound })
    }

    @Test fun theirMovesOnPlaysThemQuieter() {
        val played = mutableListOf<Played>()
        sfx(Profile(), played).play(Sound.C4_DROP, Sfx.OTHERS, others = true)
        assertEquals(Sound.C4_DROP.volume * Sfx.OTHERS, played.single().level, 1e-6f)
    }
}
