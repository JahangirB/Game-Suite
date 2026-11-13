package com.pippop.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What a game's sheet remembers about playing alone: its last setup, per game, with today's defaults. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SoloSetupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs() = context.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE)

    /** A store over the file as it is now (the real one is shared by the process). */
    private fun store(): ProfileStore {
        val c = ProfileStore::class.java.getDeclaredConstructor(android.content.SharedPreferences::class.java)
        c.isAccessible = true
        return c.newInstance(prefs())
    }

    private fun fresh(): ProfileStore {
        prefs().edit().clear().commit()
        return store()
    }

    @Test fun nothingSavedPlaysTheDefaults() {
        val p = fresh().profile.value
        assertEquals(mapOf("level" to "medium"), p.soloSetup(GameKind.CONNECT4))
        assertEquals(mapOf("level" to "medium", "shape" to "classic"), p.soloSetup(GameKind.SUDOKU))
        assertEquals("1", p.soloSetup(GameKind.GOLF)["computers"])
        assertEquals("easy", p.soloSetup(GameKind.GOLF)["level"])
        assertEquals("ultimate", p.soloSetup(GameKind.TTT)["mode"])
        assertEquals("4x4", p.soloSetup(GameKind.DOTS)["board"])
        assertEquals("16", p.soloSetup(GameKind.MEMORY)["deck"])
        assertEquals("daily", p.soloSetup(GameKind.WORDS)["mode"])
        assertEquals("timed", p.soloSetup(GameKind.GRID)["timed"])
        assertEquals(mapOf("pegs" to "4", "colours" to "6", "repeats" to "yes"), p.soloSetup(GameKind.CODE))
        assertEquals("normal", p.soloSetup(GameKind.BINGO)["pace"])
    }

    @Test fun everyGameHasADefaultThatIsOneOfItsOptions() {
        for (game in GameKind.entries) {
            val spec = SoloSetups.spec(game) ?: continue
            val values = SoloSetups.resolve(game, null)
            assertEquals(spec.fields.map { it.key }, values.keys.toList())
            spec.fields.forEach { f -> assertTrue("$game ${f.key}", f.options.any { it.value == values[f.key] }) }
            assertTrue("$game button", spec.buttonText(values).isNotBlank())
        }
    }

    @Test fun gamesWithNothingToChooseHaveNoSetup() {
        assertNull(SoloSetups.spec(GameKind.SHIPS))
        assertNull(SoloSetups.spec(GameKind.DRAW))
        assertEquals(emptyMap<String, String>(), fresh().profile.value.soloSetup(GameKind.SHIPS))
    }

    @Test fun aSetupIsRememberedPerGameAndSurvivesARestart() {
        val s = fresh()
        s.setSoloSetup(GameKind.CONNECT4, mapOf("level" to "hard"))
        s.setSoloSetup(GameKind.GOLF, mapOf("level" to "hard", "computers" to "3", "skill" to "easy"))
        assertEquals("hard", s.profile.value.soloSetup(GameKind.CONNECT4)["level"])
        val again = store().profile.value
        assertEquals(mapOf("level" to "hard"), again.soloSetup(GameKind.CONNECT4))
        assertEquals(mapOf("level" to "hard", "computers" to "3", "skill" to "easy"), again.soloSetup(GameKind.GOLF))
        // Another game is untouched.
        assertEquals("medium", again.soloSetup(GameKind.HOCKEY)["level"])
    }

    @Test fun aLaterPickReplacesTheEarlierOne() {
        val s = fresh()
        s.setSoloSetup(GameKind.CLASH, mapOf("opponents" to "2"))
        s.setSoloSetup(GameKind.CLASH, mapOf("opponents" to "3"))
        assertEquals("3", store().profile.value.soloSetup(GameKind.CLASH)["opponents"])
    }

    @Test fun unknownValuesAndKeysFallBackToTheDefault() {
        val s = fresh()
        prefs().edit().putString("solo_setup_connect4", "level=impossible;colour=red").putString("solo_setup_golf", "level=hard;computers=nine;skill").commit()
        val p = store().profile.value
        assertEquals(mapOf("level" to "medium"), p.soloSetup(GameKind.CONNECT4))
        assertEquals("hard", p.soloSetup(GameKind.GOLF)["level"])
        assertEquals("1", p.soloSetup(GameKind.GOLF)["computers"])
        assertEquals("medium", p.soloSetup(GameKind.GOLF)["skill"])
        // A pick that is not an option is not stored either.
        s.setSoloSetup(GameKind.HOCKEY, mapOf("level" to "nightmare"))
        assertEquals("medium", store().profile.value.soloSetup(GameKind.HOCKEY)["level"])
    }

    @Test fun unreadableStoredSetupsAreIgnored() {
        fresh()
        prefs().edit().putString("solo_setup_sudoku", ";;=;==x;level").putString("solo_setup_nonsense", "a=b").putString("solo_setup_ships", "x=y").commit()
        val p = store().profile
        assertEquals(mapOf("level" to "medium", "shape" to "classic"), p.value.soloSetup(GameKind.SUDOKU))
        assertEquals(setOf(GameKind.SUDOKU), p.value.soloSetups.keys)
    }

    @Test fun aSetupIsSavedInOneLineAndReadBack() {
        val values = mapOf("pegs" to "5", "colours" to "7", "repeats" to "no")
        assertEquals(values, SoloSetups.decode(SoloSetups.encode(values)))
        assertEquals(emptyMap<String, String>(), SoloSetups.decode(null))
    }

    @Test fun hiddenChoicesDoNotShowButAreKept() {
        val spec = SoloSetups.spec(GameKind.GOLF)!!
        val alone = SoloSetups.resolve(GameKind.GOLF, mapOf("computers" to "0", "skill" to "hard"))
        assertEquals(listOf("level", "computers"), spec.shown(alone).map { it.key })
        assertEquals("hard", alone["skill"])
        assertEquals("Easy holes · On your own", spec.line(alone))
        assertEquals("Easy", spec.buttonText(alone))
        val daily = SoloSetups.resolve(GameKind.WORDS, null)
        assertEquals("Today's word", SoloSetups.spec(GameKind.WORDS)!!.buttonText(daily))
        assertEquals("Practice · 6 letters", SoloSetups.spec(GameKind.WORDS)!!.buttonText(SoloSetups.resolve(GameKind.WORDS, mapOf("mode" to "practice", "length" to "hard"))))
        assertNotNull(SoloSetups.spec(GameKind.SUDOKU)!!.line(SoloSetups.resolve(GameKind.SUDOKU, null)))
    }
}
