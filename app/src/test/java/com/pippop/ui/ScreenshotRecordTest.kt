package com.pippop.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.BrokenRecord
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.SoloRecordStore
import com.pippop.data.TurnsView
import com.pippop.game.slide.Slide
import com.pippop.game.slide.SlideLevel
import com.pippop.ui.screens.LocalMatchSession
import com.pippop.ui.screens.LocalRecord
import com.pippop.ui.screens.LocalRoomControls
import com.pippop.ui.screens.LocalTonight
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MatchSession
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.SoloSlideScreen
import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.TurnGameScreen
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.tonightScore
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import java.io.File

/**
 * The gold line on the result card (step 22): a Connect Four win that is your quickest (target:
 * result-new), a solo record, and the same cards with no line, in light and dark.
 */
class ScreenshotRecordTest : ScreenshotBase() {
    @After fun clear() = SoloRecordStore.installForTest(null)

    private fun player(name: String, avatar: Avatar, game: GameKind) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = true, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = game,
    )

    private val c4Won = listOf(".......", ".......", "...o...", "..mo...", ".moom..", "mmmmooo").joinToString("")

    private val room = RoomState(
        code = "K7Q2NB", role = "host", game = GameKind.CONNECT4, status = RoomStatus.FINISHED, difficulty = null, round = 6, startAt = null,
        puzzle = null, solution = null, winner = "me", endReason = "four", me = player("Sam", Avatar.FOX, GameKind.CONNECT4),
        opponent = player("Maya", Avatar.PANDA, GameKind.CONNECT4),
        ships = null, turns = TurnsView(turn = null, board = c4Won, winLine = listOf(35, 36, 37, 38), lastMoveByMe = true, lastCell = 38),
        serverTime = 0, others = listOf(player("Maya", Avatar.PANDA, GameKind.CONNECT4)), inSession = true,
    )

    @Composable
    private fun Session(record: BrokenRecord?, content: @Composable () -> Unit) {
        val tally = mapOf(TALLY_ME to 3, "Maya" to 2)
        val controls = RoomControls(
            room = room, mode = null, setup = {}, rematch = {}, newGame = {}, switchTo = {},
            games = emptyList(), setupOpen = mutableStateOf(false), busy = false,
        )
        CompositionLocalProvider(
            LocalMatchSession provides MatchSession(tally, listOf(GameKind.LUDO, GameKind.WORDS, GameKind.SHIPS), {}),
            LocalRoomControls provides controls,
            LocalTonight provides tonightScore(room, tally),
            LocalTurnHints provides TurnHints(),
            LocalRecord provides record,
        ) { content() }
    }

    private fun c4(name: String, record: BrokenRecord?, dark: Boolean) {
        val vm = BattleViewModel(ApplicationProvider.getApplicationContext<Application>())
        shot(name, dark) { Session(record) { TurnGameScreen(BattleUiState(room = room, tally = mapOf(TALLY_ME to 3, "Maya" to 2)), vm, onLeave = {}) } }
        val said = hasContentDescription("New record. Your quickest win at Connect Four: 11 discs")
        compose.onAllNodes(said, useUnmergedTree = true).assertCountEquals(if (record == null) 0 else 1)
    }

    private val quickest = BrokenRecord.Quickest(GameKind.CONNECT4, count = 11, ms = null)

    @Test fun c4RecordLight() = c4("record-c4", quickest, false)
    @Test fun c4RecordDark() = c4("record-c4", quickest, true)
    @Test fun c4NoRecordLight() = c4("record-none", null, false)
    @Test fun c4NoRecordDark() = c4("record-none", null, true)

    private fun slide(name: String, beat: Boolean, dark: Boolean) {
        val file = File.createTempFile("solo", ".json").also { it.delete() }
        val store = SoloRecordStore(file)
        store.record(GameKind.SLIDE, "medium", com.pippop.data.SoloMetric.TIME, 80_000)
        store.record(GameKind.SLIDE, "medium", com.pippop.data.SoloMetric.TIME, if (beat) 72_000 else 90_000)
        SoloRecordStore.installForTest(store)
        shot(name, dark) {
            SoloSlideScreen(
                com.pippop.ui.SoloSlideUiState(SlideLevel.MEDIUM, Slide.solved(4), 52, 0, 72_000, mapOf(SlideLevel.MEDIUM to 72_000L)),
                com.pippop.ui.SoloSlideViewModel(), onHome = {},
            )
        }
        compose.onAllNodes(hasContentDescription("New record. Your best at Slide Race (4 × 4): 1:12"), useUnmergedTree = true).assertCountEquals(if (beat) 1 else 0)
        if (beat) {
            // With the record, the card still sits above the dock and the board keeps its height.
            val card = compose.onNodeWithTag("resultCard").getUnclippedBoundsInRoot()
            val dock = compose.onNodeWithTag("dock").getUnclippedBoundsInRoot()
            assertTrue("card ends at ${card.bottom}, the dock starts at ${dock.top}", card.bottom <= dock.top)
            val area = compose.onNodeWithTag("boardArea").getUnclippedBoundsInRoot()
            assertTrue("board ${area.height}", area.height >= 220.dp && area.bottom <= card.top)
        }
    }

    @Test fun soloRecordLight() = slide("record-solo", true, false)
    @Test fun soloRecordDark() = slide("record-solo", true, true)
    @Test fun soloNoRecordLight() = slide("record-solo-none", false, false)
}
