package com.pippop.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the home remembers: recent games, put-away games and whether the order was ever changed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProfileStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = "arcade_sudoku"

    private fun prefs() = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    /** A store over the file as it is now: a new one reads what the last one wrote (the real one is shared by the process). */
    private fun store(): ProfileStore {
        val c = ProfileStore::class.java.getDeclaredConstructor(android.content.SharedPreferences::class.java)
        c.isAccessible = true
        return c.newInstance(prefs())
    }

    private fun fresh(): ProfileStore {
        prefs().edit().clear().commit()
        return store()
    }

    @Test fun defaultsAreEmpty() {
        val p = fresh().profile.value
        assertEquals(emptyList<RecentGame>(), p.recentGames)
        assertEquals(emptySet<GameKind>(), p.putAway)
        assertFalse(p.arrangedOnce)
    }

    @Test fun recentGamesAreNewestFirstAndRoundTrip() {
        val s = fresh()
        s.noteGamePlayed(GameKind.LUDO, 100)
        s.noteGamePlayed(GameKind.GOLF, 200)
        s.noteGamePlayed(GameKind.SHIPS, 300)
        val expected = listOf(RecentGame(GameKind.SHIPS, 300), RecentGame(GameKind.GOLF, 200), RecentGame(GameKind.LUDO, 100))
        assertEquals(expected, s.profile.value.recentGames)
        assertEquals(expected, store().profile.value.recentGames)
    }

    @Test fun aGamePlayedAgainMovesToTheFrontOnce() {
        val s = fresh()
        s.noteGamePlayed(GameKind.LUDO, 100)
        s.noteGamePlayed(GameKind.GOLF, 200)
        s.noteGamePlayed(GameKind.LUDO, 300)
        assertEquals(listOf(RecentGame(GameKind.LUDO, 300), RecentGame(GameKind.GOLF, 200)), store().profile.value.recentGames)
    }

    @Test fun onlySixAreKept() {
        val s = fresh()
        val games = GameKind.entries.take(8)
        games.forEachIndexed { i, g -> s.noteGamePlayed(g, 1000L + i) }
        val kept = store().profile.value.recentGames
        assertEquals(6, kept.size)
        assertEquals(games.reversed().take(6), kept.map { it.game })
    }

    @Test fun unknownAndCorruptRecentValuesAreIgnored() {
        fresh()
        prefs().edit().putString("recent_games", "ludo:500,nonsense:400,golf:abc,,ships,golf:300:9,ludo:100,golf:200").commit()
        assertEquals(listOf(RecentGame(GameKind.LUDO, 500), RecentGame(GameKind.GOLF, 200)), store().profile.value.recentGames)
        prefs().edit().putString("recent_games", "\u0000:::,,,").commit()
        assertEquals(emptyList<RecentGame>(), store().profile.value.recentGames)
    }

    @Test fun putAwayAndBackRoundTrip() {
        val s = fresh()
        s.setPutAway(setOf(GameKind.GOLF, GameKind.DOTS))
        assertEquals(setOf(GameKind.GOLF, GameKind.DOTS), store().profile.value.putAway)
        s.setPutAway(setOf(GameKind.DOTS))
        assertEquals(setOf(GameKind.DOTS), store().profile.value.putAway)
        s.setPutAway(emptySet())
        assertEquals(emptySet<GameKind>(), store().profile.value.putAway)
    }

    @Test fun putAwayKeepsEveryGameInTheOrder() {
        val s = fresh()
        val order = GameKind.entries.reversed()
        s.setGameOrder(order)
        s.setPutAway(setOf(GameKind.GOLF))
        val p = store().profile.value
        assertEquals(order, p.gameOrder)
        assertTrue(GameKind.GOLF in p.gameOrder)
    }

    @Test fun unknownPutAwayKeysAreIgnored() {
        fresh()
        prefs().edit().putString("put_away", "golf,not-a-game,,dots,golf").commit()
        assertEquals(setOf(GameKind.GOLF, GameKind.DOTS), store().profile.value.putAway)
    }

    @Test fun arrangedOnceIsSetByTheFirstChangeOfOrder() {
        val s = fresh()
        s.setName("Sam")
        s.noteGamePlayed(GameKind.GOLF)
        assertFalse(s.profile.value.arrangedOnce)
        s.setGameOrder(GameKind.entries.reversed())
        assertTrue(s.profile.value.arrangedOnce)
        assertTrue(store().profile.value.arrangedOnce)
        // Putting the order back to the default is still a change that was made.
        s.setGameOrder(null)
        assertTrue(store().profile.value.arrangedOnce)
    }

    @Test fun puttingAGameAwayCountsAsArranging() {
        val s = fresh()
        s.setPutAway(setOf(GameKind.GOLF))
        assertTrue(store().profile.value.arrangedOnce)
    }

    @Test fun anOrderSavedBeforeThisExistedCountsAsArranged() {
        fresh()
        prefs().edit().putString("game_order", GameKind.entries.reversed().joinToString(",") { it.key }).commit()
        assertTrue(store().profile.value.arrangedOnce)
    }

    @Test fun existingValuesAreUntouched() {
        fresh()
        prefs().edit().putString("player_name", "Sam").putString("game_order", "golf,ludo").putString("tutorials_seen", "golf").commit()
        val s = store()
        s.noteGamePlayed(GameKind.GOLF)
        s.setPutAway(setOf(GameKind.LUDO))
        val p = store().profile.value
        assertEquals("Sam", p.name)
        assertEquals(listOf(GameKind.GOLF, GameKind.LUDO), p.gameOrder.take(2))
        assertEquals(setOf(GameKind.GOLF), p.tutorialsSeen)
    }

    @Test fun removingRecentGamesDropsOnlyExactMatches() {
        val s = fresh()
        val sample = listOf(RecentGame(GameKind.GOLF, 100), RecentGame(GameKind.CODE, 200))
        sample.forEach { s.noteGamePlayed(it.game, it.at) }
        s.setSampleRecent(sample)
        assertEquals(sample.toSet(), s.sampleRecent().toSet())
        // A real, later play of Golf replaces the sample's Golf entry; clearing must not touch it.
        s.noteGamePlayed(GameKind.GOLF, 900)
        s.removeRecent(s.sampleRecent())
        s.setSampleRecent(emptyList())
        assertEquals(listOf(RecentGame(GameKind.GOLF, 900)), store().profile.value.recentGames)
        assertEquals(emptyList<RecentGame>(), s.sampleRecent())
    }
}
