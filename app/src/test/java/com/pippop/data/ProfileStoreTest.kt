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

/** What the home remembers: recent games, the shelf and the Store, and whether the order was ever changed. */
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

    private val nine = listOf(
        GameKind.SUDOKU, GameKind.SHIPS, GameKind.MEMORY, GameKind.WORDS, GameKind.TTT,
        GameKind.DOMINOES, GameKind.CLASH, GameKind.LUDO, GameKind.GOLF,
    )

    @Test fun aNewPhoneStartsWithTheNine() {
        val p = fresh().profile.value
        assertEquals(nine, p.shelf)
        assertFalse(p.shelfNoteShown)
        assertEquals(nine, store().profile.value.shelf)
    }

    @Test fun anExistingPhoneAlsoStartsWithTheNineWhateverItHad() {
        // An install from before the shelf: the old keys, and no shelf key.
        prefs().edit().clear()
            .putString("player_name", "Sam")
            .putString("game_order", GameKind.entries.reversed().joinToString(",") { it.key })
            .putString("put_away", "golf,dots")
            .commit()
        val before = prefs().all.toMap()
        val s = store()
        assertEquals(before, prefs().all.filterKeys { it in before })
        assertEquals(nine, s.profile.value.shelf)
        // The old keys stay, untouched.
        assertEquals("golf,dots", prefs().getString("put_away", null))
        assertTrue(prefs().contains("game_order"))
        assertEquals("Sam", s.profile.value.name)
    }

    @Test fun theOldKeysAreNotReadOnceTheShelfExists() {
        val s = fresh()
        s.addToShelf(GameKind.CONNECT4)
        prefs().edit().putString("game_order", "ludo").putString("put_away", "sudoku").commit()
        assertEquals(nine + GameKind.CONNECT4, store().profile.value.shelf)
    }

    @Test fun runningTheMigrationTwiceChangesNothing() {
        prefs().edit().clear().putString("game_order", "golf,ludo").commit()
        val s = store()
        s.addToShelf(GameKind.DOTS)
        val before = prefs().all.toMap()
        s.migrateShelf()
        store().migrateShelf()
        assertEquals(before, prefs().all.toMap())
        assertEquals(nine + GameKind.DOTS, store().profile.value.shelf)
    }

    @Test fun addingPutsAGameAtTheEndOnce() {
        val s = fresh()
        s.addToShelf(GameKind.CONNECT4)
        s.addToShelf(GameKind.DOTS)
        s.addToShelf(GameKind.CONNECT4)
        s.addToShelf(GameKind.SUDOKU)
        assertEquals(nine + GameKind.CONNECT4 + GameKind.DOTS, s.profile.value.shelf)
        assertEquals(nine + GameKind.CONNECT4 + GameKind.DOTS, store().profile.value.shelf)
    }

    @Test fun removingKeepsEverythingTheGameHad() {
        val s = fresh()
        s.noteGamePlayed(GameKind.GOLF, 500)
        s.tutorialSeen(GameKind.GOLF)
        s.removeFromShelf(GameKind.GOLF)
        s.removeFromShelf(GameKind.GOLF)
        val p = store().profile.value
        assertEquals(nine - GameKind.GOLF, p.shelf)
        assertEquals(listOf(RecentGame(GameKind.GOLF, 500)), p.recentGames)
        assertEquals(setOf(GameKind.GOLF), p.tutorialsSeen)
        assertTrue(GameKind.GOLF in ProfileStore.storeGames(p.shelf))
    }

    @Test fun movingReordersAndIgnoresPlacesOffTheShelf() {
        val s = fresh()
        s.moveOnShelf(8, 0)
        assertEquals(listOf(GameKind.GOLF) + nine.dropLast(1), store().profile.value.shelf)
        s.moveOnShelf(0, 2)
        assertEquals(listOf(GameKind.SUDOKU, GameKind.SHIPS, GameKind.GOLF), s.profile.value.shelf.take(3))
        val now = s.profile.value.shelf
        s.moveOnShelf(0, 9)
        s.moveOnShelf(-1, 2)
        s.moveOnShelf(3, 3)
        assertEquals(now, s.profile.value.shelf)
    }

    @Test fun theStoreHoldsEveryGameNotOnTheShelfInCatalogueOrder() {
        assertEquals(
            listOf(
                GameKind.CONNECT4, GameKind.DOTS, GameKind.DRAW, GameKind.NUMBERS, GameKind.HOCKEY, GameKind.GRID,
                GameKind.BALLOON, GameKind.CODE, GameKind.SLIDE, GameKind.BINGO, GameKind.BLOCKS, GameKind.LADDER,
            ),
            ProfileStore.storeGames(nine),
        )
        // Order on the shelf does not matter; a game added to it leaves the Store, one removed joins it in its place.
        assertEquals(ProfileStore.storeGames(nine), ProfileStore.storeGames(nine.reversed()))
        assertFalse(GameKind.DOTS in ProfileStore.storeGames(nine + GameKind.DOTS))
        assertEquals(GameKind.entries.size - nine.size + 1, ProfileStore.storeGames(nine - GameKind.GOLF).size)
        assertEquals(emptyList<GameKind>(), ProfileStore.storeGames(GameKind.entries))
    }

    @Test fun anEmptyShelfStaysEmpty() {
        val s = fresh()
        nine.forEach { s.removeFromShelf(it) }
        assertEquals(emptyList<GameKind>(), store().profile.value.shelf)
    }

    @Test fun theStoreNoteIsShownOnce() {
        val s = fresh()
        assertFalse(s.profile.value.shelfNoteShown)
        s.markShelfNoteShown()
        assertTrue(store().profile.value.shelfNoteShown)
    }

    @Test fun theStoreNoteIsDueOnlyOnAPhoneThatHadTheApp() {
        // A brand-new phone: nothing saved before the migration, so no note, now or after a restart.
        val fresh = fresh().profile.value
        assertFalse(fresh.shelfNoteDue)
        assertFalse(store().profile.value.shelfNoteDue)
        // A phone from before the shelf: the old keys are there, so the note is due, and stays due after a restart.
        prefs().edit().clear().putString("player_name", "Sam").commit()
        assertTrue(store().profile.value.shelfNoteDue)
        assertTrue(store().profile.value.shelfNoteDue)
        // Not because it was run twice: the flag is not set again, nor cleared, by running the migration again.
        store().migrateShelf()
        assertTrue(store().profile.value.shelfNoteDue)
    }

    @Test fun theStoreNoteStaysSeenOnceDismissed() {
        prefs().edit().clear().putString("player_name", "Sam").commit()
        val s = store()
        assertTrue(s.profile.value.shelfNoteDue && !s.profile.value.shelfNoteShown)
        s.markShelfNoteShown()
        val p = store().profile.value
        assertTrue(p.shelfNoteShown)
        assertTrue(p.shelfNoteDue)
    }

    @Test fun unknownAndRepeatedShelfKeysAreIgnored() {
        fresh()
        prefs().edit().putString("shelf", "golf,not-a-game,,dots,golf").commit()
        assertEquals(listOf(GameKind.GOLF, GameKind.DOTS), store().profile.value.shelf)
    }

    @Test fun setShelfSavesTheOrderAndLeavesArrangedOnceAlone() {
        val s = fresh()
        prefs().edit().putBoolean("arranged_once", true).commit()
        s.setShelf(nine.reversed())
        assertEquals(nine.reversed(), store().profile.value.shelf)
        s.setShelf(null)
        assertEquals(nine, store().profile.value.shelf)
        // The old key stays where it was; nothing reads it.
        assertTrue(prefs().getBoolean("arranged_once", false))
    }

    @Test fun existingValuesAreUntouched() {
        prefs().edit().clear().putString("player_name", "Sam").putString("game_order", "golf,ludo").putString("tutorials_seen", "golf").commit()
        val s = store()
        s.noteGamePlayed(GameKind.GOLF)
        s.removeFromShelf(GameKind.LUDO)
        val p = store().profile.value
        assertEquals("Sam", p.name)
        assertEquals(nine - GameKind.LUDO, p.shelf)
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

    // Loudness: the old on/off `sound` setting becomes Off or Normal under a new key; the old key stays.
    @Test fun loudnessDefaultsToNormalAndTheirMovesOn() {
        val p = fresh().profile.value
        assertEquals(Loudness.NORMAL, p.loudness)
        assertTrue(p.othersMoves)
    }

    @Test fun oldSoundOnBecomesNormal() {
        prefs().edit().clear().putBoolean("sound", true).commit()
        assertEquals(Loudness.NORMAL, store().profile.value.loudness)
    }

    @Test fun oldSoundOffBecomesOff() {
        prefs().edit().clear().putBoolean("sound", false).commit()
        val s = store()
        assertEquals(Loudness.OFF, s.profile.value.loudness)
        assertFalse(s.profile.value.sound)
        // The old key is read, never removed or changed.
        assertTrue(prefs().contains("sound"))
        assertFalse(prefs().getBoolean("sound", true))
    }

    @Test fun migrationTwiceChangesNothing() {
        prefs().edit().clear().putBoolean("sound", false).commit()
        store()
        val once = prefs().all.toMap()
        assertEquals(Loudness.OFF, store().profile.value.loudness)
        assertEquals(once, prefs().all.toMap().filterKeys { it in once })
    }

    @Test fun anAlreadyMigratedChoiceWinsOverTheOldKey() {
        prefs().edit().clear().putBoolean("sound", false).commit()
        val s = store()
        s.setLoudness(Loudness.LOUD)
        assertEquals(Loudness.LOUD, store().profile.value.loudness)
        prefs().edit().putBoolean("sound", false).commit()
        assertEquals(Loudness.LOUD, store().profile.value.loudness)
    }

    @Test fun theMenuSwitchGoesBackToTheLastLevel() {
        val s = fresh()
        s.setLoudness(Loudness.QUIET)
        s.setSound(false)
        assertEquals(Loudness.OFF, s.profile.value.loudness)
        s.setSound(true)
        assertEquals(Loudness.QUIET, s.profile.value.loudness)
    }

    @Test fun theirMovesIsKept() {
        val s = fresh()
        s.setOthersMoves(false)
        assertFalse(store().profile.value.othersMoves)
    }

    @Test fun looksStartAsToday() {
        val p = fresh().profile.value
        assertEquals(CardBackLook.STRIPES, p.cardBack)
        assertEquals(FrameLook.BLUE, p.frame)
        assertEquals(TileLook.IVORY, p.tile)
    }

    @Test fun looksAreKeptAndReadBack() {
        val s = fresh()
        s.setCardBack(CardBackLook.WAVES)
        s.setFrame(FrameLook.PLUM)
        s.setTile(TileLook.MAPLE)
        assertEquals(CardBackLook.WAVES, s.profile.value.cardBack)
        val back = store().profile.value
        assertEquals(CardBackLook.WAVES, back.cardBack)
        assertEquals(FrameLook.PLUM, back.frame)
        assertEquals(TileLook.MAPLE, back.tile)
    }

    @Test fun anUnknownLookFallsBackToTheFirstOne() {
        fresh()
        prefs().edit().putString("look_card_back", "HOLOGRAM").putString("look_frame", "").putString("look_tile", "stone").commit()
        val p = store().profile.value
        assertEquals(CardBackLook.STRIPES, p.cardBack)
        assertEquals(FrameLook.BLUE, p.frame)
        assertEquals(TileLook.IVORY, p.tile)
    }
}
