package com.pippop.ui.screens

import com.pippop.data.GameKind
import com.pippop.data.ProfileStore
import com.pippop.ui.art.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Game Store step 5: the Choices map and what each one shows, the Store's list as games are got and removed, and the words it uses. */
class StoreLogicTest {
    private val nine = ProfileStore.DEFAULT_SHELF

    @Test fun everyGameIsInAtLeastOneKind() {
        GameKind.entries.forEach { g ->
            assertTrue("${g.label} is in no kind", StoreFilter.entries.any { it != StoreFilter.ALL && it.holds(g) })
        }
    }

    @Test fun allHoldsEveryGame() {
        assertEquals(GameKind.entries, GameKind.entries.filter { StoreFilter.ALL.holds(it) })
    }

    @Test fun quickIsExactlyTheGamesOfFiveMinutesOrLess() {
        assertEquals(GameKind.entries.filter { it.minutes <= 5 }.toSet(), STORE_CATEGORIES.getValue(StoreFilter.QUICK))
    }

    @Test fun aGameCanBeInMoreThanOneKind() {
        assertTrue(StoreFilter.PUZZLES.holds(GameKind.MEMORY) && StoreFilter.PARTY.holds(GameKind.MEMORY) && StoreFilter.QUICK.holds(GameKind.MEMORY))
        assertTrue(StoreFilter.WORDS.holds(GameKind.DRAW) && StoreFilter.PARTY.holds(GameKind.DRAW))
    }

    @Test fun theFilterShowsOnlyThatKindOfStoreGame() {
        assertEquals(listOf(GameKind.DRAW, GameKind.GRID, GameKind.BALLOON, GameKind.LADDER).sortedBy { it.ordinal }, storeList(nine, StoreFilter.WORDS))
        assertEquals(listOf(GameKind.DOTS, GameKind.DRAW, GameKind.BINGO), storeList(nine, StoreFilter.PARTY))
        assertTrue(storeList(nine, StoreFilter.QUICK).all { it.minutes <= 5 && it !in nine })
        // The filters together leave nothing out of "All".
        val all = storeList(nine, StoreFilter.ALL)
        assertEquals(12, all.size)
        assertEquals(all.toSet(), StoreFilter.entries.filter { it != StoreFilter.ALL }.flatMap { storeList(nine, it) }.toSet())
    }

    @Test fun gettingAGameMovesItOutOfTheStoreAndRemovingPutsItBack() {
        val got = nine + GameKind.BINGO
        assertFalse(GameKind.BINGO in storeList(got, StoreFilter.ALL))
        assertFalse(GameKind.BINGO in storeList(got, StoreFilter.PARTY))
        assertTrue(GameKind.BINGO in storeList(got - GameKind.BINGO, StoreFilter.PARTY))
        assertTrue(GameKind.GOLF in storeList(nine - GameKind.GOLF, StoreFilter.ALL))
    }

    @Test fun theStoreIsEmptyWhenEveryGameIsOnTheShelf() {
        assertEquals(emptyList<GameKind>(), storeList(GameKind.entries, StoreFilter.ALL))
        assertEquals(emptyList<GameKind>(), storeList(GameKind.entries, StoreFilter.QUICK))
        assertEquals("Game Store · every game is on your shelf", storeCardText(0))
        assertEquals("Every game is on your shelf.", STORE_EMPTY)
    }

    @Test fun theCardCountsTheStoreGames() {
        assertEquals("Game Store · 12 more games", storeCardText(storeList(nine, StoreFilter.ALL).size))
        assertEquals("Game Store · 1 more game", storeCardText(1))
    }

    @Test fun factsAndToasts() {
        assertEquals("2–4 players · 3 min", storeFacts(GameKind.BINGO))
        assertEquals("2 players · 5 min", storeFacts(GameKind.CONNECT4))
        assertEquals("Bingo is on your shelf", onShelfToast(GameKind.BINGO))
        assertEquals("Bingo is back in the Store.", backInStoreToast(GameKind.BINGO))
    }
}
