package com.pippop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.ProfileStore
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.ui.screens.PartnerPick
import com.pippop.ui.screens.PickGate
import com.pippop.ui.screens.roomPick
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Game Store step 6: a partner's pick that is not on your shelf. Every way into a room's game reaches the phone the same way
 * (their screen is on the game, yours is not), so each is a change of the room: they opened it, switched to it, picked it under
 * "Another game", or changed the game from the lobby. Off the shelf: the sheet, then "Add and join" puts the game on the shelf and
 * joins; "Not now" leaves everything as it is. On the shelf: no sheet.
 */
class PartnerPickTest : ScreenshotBase() {
    private val nine = ProfileStore.DEFAULT_SHELF
    private val x = GameKind.BLOCKS

    private fun player(name: String, at: GameKind?, left: Boolean = false) = PlayerInfo(
        name = name, avatar = Avatar.PANDA, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = left, idleMs = 0, online = true, at = at,
    )

    private fun room(
        game: GameKind, mineAt: GameKind?, theirs: GameKind? = game, status: RoomStatus = RoomStatus.LOBBY, round: Int = 3, inSession: Boolean = true,
        vararg more: PlayerInfo,
    ) = RoomState(
        code = "K7Q2NB", role = "host", game = game, status = status, difficulty = null, round = round, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Sam", mineAt),
        opponent = player("Maya", theirs), ships = null, turns = null, serverTime = 0,
        others = listOf(player("Maya", theirs)) + more, inSession = inSession,
    )

    // ---- Which rooms ask ----

    @Test fun anOffShelfGameThePartnerIsWaitingInAsks() {
        val pick = roomPick(room(x, mineAt = null), nine)
        assertEquals(PartnerPick("Maya", Avatar.PANDA, x), pick)
    }

    @Test fun aGameOnTheShelfNeverAsks() = assertNull(roomPick(room(GameKind.LUDO, mineAt = null), nine))

    @Test fun nothingAsksWhenYouAreAlreadyThere() = assertNull(roomPick(room(x, mineAt = x), nine))

    @Test fun nothingAsksWhenNobodyIsThereYet() = assertNull(roomPick(room(x, mineAt = null, theirs = null), nine))

    @Test fun nothingAsksWhileTheMatchIsBeingPlayed() = assertNull(roomPick(room(x, mineAt = null, status = RoomStatus.PLAYING), nine))

    @Test fun nothingAsksOnAServerThatPlaysOneGame() = assertNull(roomPick(room(x, mineAt = x, inSession = false), nine))

    @Test fun aMatchYouPausedIsYoursAndNeverAsks() {
        val r = room(x, mineAt = null, status = RoomStatus.PLAYING)
        assertNull(roomPick(r.copy(status = RoomStatus.LOBBY, me = r.me.copy(pausedMs = 60_000)), nine))
    }

    @Test fun twoWaitingAreNamedTogether() {
        val r = room(x, mineAt = null, more = arrayOf(player("Leo", x)))
        assertEquals("Maya & Leo want to play Corner Blocks", com.pippop.ui.screens.partnerPickTitle(roomPick(r, nine)!!))
    }

    // ---- The paths, as the home wires them ----

    /** The home's piece of the wiring: the room, the shelf, "Join" and the gate's sheet. */
    private class Host(room: RoomState, shelf: List<GameKind>) {
        var room by mutableStateOf(room)
        var shelf by mutableStateOf(shelf)
        var home by mutableStateOf(true)
        val opened = mutableListOf<GameKind>()
        val gate = PickGate()

        @Composable
        fun Content() {
            Column {
                Text("Join", Modifier.clickable {
                    if (!gate.askAgain(roomPick(room, shelf), room.game)) opened += room.game
                })
                gate.Sheet(room, shelf, active = home, onAddToShelf = { shelf = shelf + it }, onJoin = { opened += it })
            }
        }
    }

    private fun host(start: RoomState, shelf: List<GameKind> = nine): Host {
        val h = Host(start, shelf)
        compose.setContent { PipPopTheme(darkTheme = false) { h.Content() } }
        compose.waitForIdle()
        return h
    }

    private fun sheetShows() = compose.onAllNodesWithText("Maya wants to play Corner Blocks").fetchSemanticsNodes().isNotEmpty()

    /** The sheet's path, from the room [before] to the room [after] (the partner's choice arriving). */
    private fun path(before: RoomState, after: RoomState) {
        // Add and join.
        host(before).let { h ->
            assertEquals("no sheet before the pick arrives", false, sheetShows())
            h.room = after
            compose.waitForIdle()
            compose.onNodeWithText("Maya wants to play Corner Blocks").assertIsDisplayed()
            compose.onNodeWithText("It's in the Game Store. Add it to join.").assertIsDisplayed()
            compose.onNodeWithText("Add and join").assertHeightIsAtLeast(48.dp).performClick()
            compose.waitForIdle()
            assertEquals(nine + x, h.shelf)
            assertEquals("the join carries on", listOf(x), h.opened)
            assertEquals(false, sheetShows())
        }
    }

    private fun notNow(before: RoomState, after: RoomState) {
        val h = host(before)
        h.room = after
        compose.waitForIdle()
        compose.onNodeWithText("Not now").assertHeightIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        assertEquals(false, sheetShows())
        assertEquals("nothing joined", emptyList<GameKind>(), h.opened)
        assertEquals("the shelf is as it was", nine, h.shelf)
        assertEquals("the room is as the partner set it", after, h.room)
        // "Join" on the game asks again, and still joins nothing until you say so.
        compose.onNodeWithText("Join").performClick()
        compose.waitForIdle()
        assertEquals(true, sheetShows())
        assertEquals(emptyList<GameKind>(), h.opened)
    }

    // They opened it while you were in the menu (joining the room whose open game is X).
    private val opened = room(GameKind.LUDO, mineAt = null, theirs = null)
    private val openedX = room(x, mineAt = null)

    @Test fun joiningARoomWhoseOpenGameIsOffTheShelf_addAndJoin() = path(opened, openedX)
    @Test fun joiningARoomWhoseOpenGameIsOffTheShelf_notNow() = notNow(opened, openedX)

    // They switched the room to it while you were in the game (a switch-game invite).
    private val inGame = room(GameKind.LUDO, mineAt = GameKind.LUDO, status = RoomStatus.PLAYING)
    private val switched = room(x, mineAt = GameKind.LUDO, theirs = x, status = RoomStatus.LOBBY, round = 4)

    @Test fun aSwitchGameInvite_addAndJoin() = path(inGame, switched)
    @Test fun aSwitchGameInvite_notNow() = notNow(inGame, switched)

    // They picked it under "Another game" while you were on the result.
    private val result = room(GameKind.CLASH, mineAt = GameKind.CLASH, status = RoomStatus.FINISHED)
    private val anotherGame = room(x, mineAt = GameKind.CLASH, theirs = x)

    @Test fun anotherGamePickedByTheOtherPlayer_addAndJoin() = path(result, anotherGame)
    @Test fun anotherGamePickedByTheOtherPlayer_notNow() = notNow(result, anotherGame)

    // The opener changed the game while you were in its lobby.
    private val lobby = room(GameKind.DOMINOES, mineAt = GameKind.DOMINOES, theirs = GameKind.DOMINOES)
    private val changed = room(x, mineAt = GameKind.DOMINOES, theirs = x)

    @Test fun theOpenerChangesTheGameInTheLobby_addAndJoin() = path(lobby, changed)
    @Test fun theOpenerChangesTheGameInTheLobby_notNow() = notNow(lobby, changed)

    @Test fun aGameOnTheShelfShowsNoSheetAndJoinsAtOnce() {
        val h = host(room(GameKind.LUDO, mineAt = null, theirs = null))
        h.room = room(GameKind.LUDO, mineAt = null, theirs = GameKind.LUDO, round = 4)
        compose.waitForIdle()
        compose.onAllNodesWithText("Not now").assertCountEquals(0)
        compose.onNodeWithText("Join").performClick()
        assertEquals(listOf(GameKind.LUDO), h.opened)
    }

    @Test fun aGameAddedLaterWithNoSheetNeedsNoSecondQuestion() {
        val h = host(openedX, shelf = nine + x)
        compose.onAllNodesWithText("Add and join").assertCountEquals(0)
        compose.onNodeWithText("Join").performClick()
        assertEquals(listOf(x), h.opened)
    }

    @Test fun theSheetWaitsWhileYouAreNotOnTheHome() {
        val h = host(openedX)
        compose.onNodeWithText("Add and join").assertExists()
        h.home = false
        compose.waitForIdle()
        compose.onAllNodesWithText("Add and join").assertCountEquals(0)
        h.home = true
        compose.waitForIdle()
        compose.onNodeWithText("Add and join").assertExists()
    }

    @Test fun aLaterPickOfTheSameGameAsksAgainAfterNotNow() {
        val h = host(openedX)
        compose.onNodeWithText("Not now").performClick()
        compose.waitForIdle()
        assertEquals(false, sheetShows())
        // They went away to something on the shelf, then picked it again.
        h.room = room(GameKind.LUDO, mineAt = null, theirs = GameKind.LUDO, round = 5)
        compose.waitForIdle()
        h.room = room(x, mineAt = null, round = 6)
        compose.waitForIdle()
        assertEquals(true, sheetShows())
        assertNotNull(roomPick(h.room, nine))
    }
}
