package com.pippop.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The reactions tray's order: the ones sent last come first, and a brand-new install keeps the old order. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReactionOrderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE)

    private fun store(): ProfileStore {
        val c = ProfileStore::class.java.getDeclaredConstructor(android.content.SharedPreferences::class.java)
        c.isAccessible = true
        return c.newInstance(prefs)
    }

    private fun fresh(): ProfileStore {
        prefs.edit().clear().commit()
        return store()
    }

    private fun trayOf(s: ProfileStore) = Reaction.recentFirst(s.profile.value.recentReactions)

    @Test fun newInstallKeepsTodaysOrder() {
        val s = fresh()
        assertEquals(emptyList<String>(), s.profile.value.recentReactions)
        assertEquals(Reaction.entries.toList(), trayOf(s))
    }

    @Test fun sentOnesComeFirstLastSentFirst() {
        val s = fresh()
        s.noteReactionSent(Reaction.HEART)
        s.noteReactionSent(Reaction.FIRE)
        s.noteReactionSent(Reaction.HEART)
        val tray = trayOf(s)
        assertEquals(listOf(Reaction.HEART, Reaction.FIRE), tray.take(2))
        assertEquals(Reaction.entries.size, tray.size)
        assertEquals(Reaction.entries.toSet(), tray.toSet())
        // The rest keep today's order.
        assertEquals(Reaction.entries.filter { it != Reaction.HEART && it != Reaction.FIRE }, tray.drop(2))
    }

    @Test fun theOrderSurvivesARestart() {
        val s = fresh()
        s.noteReactionSent(Reaction.WOW)
        s.noteReactionSent(Reaction.KYLIAN)
        assertEquals(listOf(Reaction.KYLIAN, Reaction.WOW), trayOf(store()).take(2))
    }

    @Test fun staleStoredKeysAreIgnored() {
        fresh()
        // Keys of reactions that no longer exist, blanks and repeats around real ones.
        prefs.edit().putString("recent_reactions", "gone,clap,,clap,alsogone,fire").commit()
        val tray = trayOf(store())
        assertEquals(listOf(Reaction.CLAP, Reaction.FIRE), tray.take(2))
        assertEquals(Reaction.entries.size, tray.size)
        assertEquals(Reaction.entries.toSet(), tray.toSet())
    }

    @Test fun onlyUnknownKeysMeansTodaysOrder() {
        assertEquals(Reaction.entries.toList(), Reaction.recentFirst(listOf("nope", "")))
    }
}
