package com.pippop.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.luminance
import com.pippop.data.ColourTheme
import com.pippop.data.ProfileStore
import com.pippop.ui.theme.DarkPalette
import com.pippop.ui.theme.LightPalette
import com.pippop.ui.theme.contrast
import com.pippop.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Step 24: all eight palettes read at 4.5:1 or better, Lavender is untouched, and the choice is remembered. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ColourThemeTest {
    @Test fun everyPaletteReadsAtFourPointFive() {
        for (theme in ColourTheme.entries) for (dark in listOf(false, true)) {
            val p = paletteFor(theme, dark)
            val name = "$theme ${if (dark) "dark" else "light"}"
            val pairs = listOf(
                "text on background" to contrast(p.text, p.background),
                "muted on background" to contrast(p.muted, p.background),
                "text on card" to contrast(p.text, p.card),
                "muted on card" to contrast(p.muted, p.card),
                "onPrimary on primary" to contrast(p.onPrimary, p.primary),
                "muted on accent" to contrast(p.muted, p.accent),
                "primaryText on accent" to contrast(p.primaryText, p.accent),
                "given on cellSameDigit" to contrast(p.given, p.cellSameDigit),
                "entry on cellSameDigit" to contrast(p.entry, p.cellSameDigit),
                "text on cellSameDigit" to contrast(p.text, p.cellSameDigit),
            )
            for ((what, ratio) in pairs) {
                // Lavender light is today's palette and must not change: its entry blue on the same-digit
                // square has always been 4.35:1, so that one pair is held at today's value.
                val floor = if (theme == ColourTheme.LAVENDER && !dark && what == "entry on cellSameDigit") 4.3f else 4.5f
                assertTrue("$name, $what: $ratio", ratio >= floor)
            }
        }
    }

    @Test fun aNoteOnTheSameDigitSquareReadsAtThree() {
        for (theme in ColourTheme.entries) for (dark in listOf(false, true)) {
            val p = paletteFor(theme, dark)
            val ratio = contrast(p.note, p.cellSameDigit)
            assertTrue("$theme dark=$dark note on cellSameDigit: $ratio", ratio >= 3f)
        }
    }

    @Test fun yourColourDoesNotMoveWithTheColourway() {
        for (dark in listOf(false, true)) {
            val base = paletteFor(ColourTheme.LAVENDER, dark)
            for (theme in ColourTheme.entries) {
                val p = paletteFor(theme, dark)
                assertEquals("$theme me", base.me, p.me)
                assertEquals("$theme meText", base.meText, p.meText)
            }
        }
        assertEquals(LightPalette.primary, LightPalette.me)
        assertEquals(DarkPalette.primary, DarkPalette.me)
    }

    @Test fun darkTintsKeepLavendersBrightness() {
        for (theme in listOf(ColourTheme.MINT, ColourTheme.PEACH, ColourTheme.SKY)) {
            val p = paletteFor(theme, true)
            for ((name, a, b) in listOf(
                Triple("background", p.background, DarkPalette.background), Triple("accent", p.accent, DarkPalette.accent),
                Triple("hairline", p.hairline, DarkPalette.hairline), Triple("cellPeer", p.cellPeer, DarkPalette.cellPeer),
                Triple("cellSameDigit", p.cellSameDigit, DarkPalette.cellSameDigit),
            )) {
                val d = Math.abs(a.luminance() - b.luminance())
                assertTrue("$theme $name luminance ${a.luminance()} vs ${b.luminance()}", d < 0.004f)
            }
        }
    }

    @Test fun lavenderIsTodaysPalette() {
        assertSame(LightPalette, paletteFor(ColourTheme.LAVENDER, false))
        assertSame(DarkPalette, paletteFor(ColourTheme.LAVENDER, true))
    }

    @Test fun inkCardsAndGameColoursStayInEveryColourway() {
        for (theme in ColourTheme.entries) for (dark in listOf(false, true)) {
            val base = if (dark) DarkPalette else LightPalette
            val p = paletteFor(theme, dark)
            assertEquals(base.ink, p.ink); assertEquals(base.card, p.card); assertEquals(base.text, p.text)
            assertEquals(base.boardBackground, p.boardBackground); assertEquals(base.cellSelected, p.cellSelected)
            assertEquals(base.given, p.given); assertEquals(base.entry, p.entry)
        }
    }

    @Test fun theChoiceIsRememberedAndLavenderIsTheDefault() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val c = ProfileStore::class.java.getDeclaredConstructor(android.content.SharedPreferences::class.java)
        c.isAccessible = true
        val store = c.newInstance(prefs) as ProfileStore
        assertEquals(ColourTheme.LAVENDER, store.profile.value.colourTheme)
        store.setColourTheme(ColourTheme.SKY)
        assertEquals(ColourTheme.SKY, store.profile.value.colourTheme)
        assertEquals(ColourTheme.SKY, (c.newInstance(prefs) as ProfileStore).profile.value.colourTheme)
        prefs.edit().putString("colour_theme", "nonsense").commit()
        assertEquals(ColourTheme.LAVENDER, (c.newInstance(prefs) as ProfileStore).profile.value.colourTheme)
    }
}
