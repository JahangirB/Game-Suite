package com.pippop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.BattleBackend
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.ProfileStore
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.ui.screens.PartnerPick
import com.pippop.ui.screens.PickGate
import com.pippop.ui.screens.anotherGameTiles
import com.pippop.ui.screens.partnerPickTitle
import com.pippop.ui.screens.roomPick
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Words step 11: the room flows know Word Ladder and Word Hunt: a partner's pick of either (Add and join), the games "Another game" and the
 * switch sheet offer by seats, the games the app tells a server it knows, and what a server that does not know them answers.
 */
class WordGamesRoomFlowTest : ScreenshotBase() {
    private val nine = ProfileStore.DEFAULT_SHELF
    private val both = listOf(GameKind.LADDER, GameKind.HUNT)

    private fun player(name: String, at: GameKind?) = PlayerInfo(
        name = name, avatar = Avatar.PANDA, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = at,
    )

    private fun room(game: GameKind, mineAt: GameKind?, theirs: GameKind?) = RoomState(
        code = "K7Q2NB", role = "host", game = game, status = RoomStatus.LOBBY, difficulty = null, round = 3, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam", mineAt),
        opponent = player("Maya", theirs), ships = null, turns = null, serverTime = 0,
        others = listOf(player("Maya", theirs)), inSession = true,
    )

    @Test fun aPartnersPickOfEitherGameAsksBecauseItIsNotOnTheShelf() {
        for (g in both) {
            val pick = roomPick(room(g, mineAt = null, theirs = g), nine)
            assertEquals(PartnerPick("Maya", Avatar.PANDA, g), pick)
            assertEquals("Maya wants to play ${g.label}", partnerPickTitle(pick!!))
            // Once got, nothing asks.
            assertNull(roomPick(room(g, mineAt = null, theirs = g), nine + g))
        }
    }

    private class Host(room: RoomState, shelf: List<GameKind>) {
        var room by mutableStateOf(room)
        var shelf by mutableStateOf(shelf)
        val opened = mutableListOf<GameKind>()
        val gate = PickGate()

        @Composable
        fun Content() {
            Column {
                Text("Join", Modifier.clickable { if (!gate.askAgain(roomPick(room, shelf), room.game)) opened += room.game })
                gate.Sheet(room, shelf, active = true, onAddToShelf = { shelf = shelf + it }, onJoin = { opened += it })
            }
        }
    }

    private fun addAndJoin(g: GameKind) {
        val h = Host(room(GameKind.LUDO, mineAt = null, theirs = null), nine)
        compose.setContent { PipPopTheme(darkTheme = false) { h.Content() } }
        compose.waitForIdle()
        h.room = room(g, mineAt = null, theirs = g)
        compose.waitForIdle()
        compose.onNodeWithText("Maya wants to play ${g.label}").assertIsDisplayed()
        compose.onNodeWithText("It's in the Game Store. Add it to join.").assertIsDisplayed()
        compose.onNodeWithText("Add and join").performClick()
        compose.waitForIdle()
        assertEquals(nine + g, h.shelf)
        assertEquals(listOf(g), h.opened)
    }

    @Test fun wordLadderPickedByAPartnerCanBeAddedAndJoined() = addAndJoin(GameKind.LADDER)

    @Test fun wordHuntPickedByAPartnerCanBeAddedAndJoined() = addAndJoin(GameKind.HUNT)

    @Test fun anotherGameAndTheSwitchSheetOfferEachByItsSeats() {
        val order = GameKind.entries
        // Two people: both fit. Three or four: only Word Hunt has seats.
        assertTrue(both.all { it in anotherGameTiles(order, GameKind.SUDOKU, 2, Int.MAX_VALUE) })
        val three = anotherGameTiles(order, GameKind.SUDOKU, 3, Int.MAX_VALUE)
        assertTrue(GameKind.HUNT in three && GameKind.LADDER !in three)
        assertEquals(4, GameKind.HUNT.maxPlayers)
        assertEquals(2, GameKind.LADDER.maxPlayers)
        assertTrue(com.pippop.ui.screens.roomReason(GameKind.LADDER, 3, GameKind.SUDOKU) == "Too few seats")
        assertNull(com.pippop.ui.screens.roomReason(GameKind.HUNT, 4, GameKind.SUDOKU))
    }

    @Test fun theAppTellsEveryServerItKnowsBothGames() {
        val known = BattleBackend.SUPPORTED_GAMES.split(",")
        assertTrue("ladder" in known && "hunt" in known)
        assertEquals(GameKind.entries.map { it.key }, known.take(GameKind.entries.size))
    }

    @Test fun aServerThatDoesNotKnowAGameIsNamedNotTheApp() {
        // An older Cloudflare server answers an unknown game with "update_needed", worded as if the app were old.
        val old = ApiException("This game needs the latest Pip & Pop.", reason = "update_needed")
        for (g in both) {
            val said = serverTooOld(old, g, online = true)
            assertEquals("${g.label} needs the updated Cloudflare server. Play it nearby, or see \"Choosing a server\" in the README.", said.message)
            assertEquals("server_old", said.reason)
        }
        // Nearby, the same answer is about the other phone's app and is left as it is; other errors are never changed.
        assertSame(old, serverTooOld(old, GameKind.HUNT, online = false))
        val other = ApiException("Room not found.", reason = "not_found")
        assertSame(other, serverTooOld(other, GameKind.HUNT, online = true))
    }
}
