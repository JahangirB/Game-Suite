package com.pippop.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.MemoryPhotos
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.TurnsView
import com.pippop.game.memory.CardFace
import com.pippop.ui.screens.LobbyFrame
import com.pippop.ui.screens.LocalMatchSession
import com.pippop.ui.screens.LocalRoomControls
import com.pippop.ui.screens.LocalTonight
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MatchSession
import com.pippop.ui.screens.MemoryDeckCard
import com.pippop.ui.screens.MemoryPhotosPanel
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.TurnGameScreen
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.tonightScore
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import org.junit.Test

/**
 * Step 30, Memory Pairs with your photos (target `memory-photos`): the lobby with "Your photos" chosen, once the eight
 * photos are sent and while they are being sent ("3 of 8 sent"), and the board with some photos arrived and some pairs
 * still on their plain faces. The pictures are made up here (a sun, hills and a pair of dots on a sky), not people.
 */
class ScreenshotMemoryPhotosTest : ScreenshotBase() {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val battle by lazy { BattleViewModel(app) }

    /** A made-up photo: a sky of [kind]'s colours with a sun and hills, or two dots on a body, as a 160 x 160 JPEG the way the app makes it. */
    private fun scene(kind: Int): String {
        val b = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val skies = listOf(Color.rgb(255, 178, 107) to Color.rgb(255, 217, 142), Color.rgb(110, 198, 255) to Color.rgb(200, 236, 255), Color.rgb(161, 140, 255) to Color.rgb(255, 196, 225), Color.rgb(79, 209, 165) to Color.rgb(215, 255, 240))
        val (top, bottom) = skies[kind % 4]
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, 180f, top, bottom, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 240f, 180f, paint)
        paint.shader = null
        if (kind % 2 == 0) {
            paint.color = Color.rgb(255, 246, 194)
            c.drawCircle(170f, 50f, 24f, paint)
            paint.color = Color.rgb(63, 143, 90)
            c.drawPath(Path().apply { moveTo(0f, 180f); lineTo(84f, 80f); lineTo(144f, 135f); lineTo(192f, 100f); lineTo(240f, 180f); close() }, paint)
        } else {
            paint.color = Color.rgb(106, 76, 255); c.drawCircle(86f, 190f, 62f, paint)
            paint.color = Color.rgb(232, 80, 91); c.drawCircle(158f, 192f, 62f, paint)
            paint.color = Color.rgb(243, 201, 160); c.drawCircle(86f, 95f, 33f, paint)
            paint.color = Color.rgb(201, 142, 98); c.drawCircle(158f, 98f, 33f, paint)
        }
        return MemoryPhotos.fromBitmap(b)!!
    }

    private val photos by lazy { List(8) { scene(it) } }

    /** The photos as the room would hold them: ids 11 to 18, and (once fetched and decoded) their pictures. */
    private fun arrived(n: Int): Map<Int, String> = photos.take(n).mapIndexed { i, p -> MemoryPhotos.decode(p); 11 + i to p }.toMap()

    private fun lobby(room: RoomState, pics: Map<Int, String>, picking: MemoryPicking? = null) = @Composable {
        LobbyFrame(
            BattleUiState(room = room, memoryPics = pics, memoryPicking = picking), Profile(name = "Sam", avatar = Avatar.FOX), onLeave = {},
            onReady = {}, onReact = {},
        ) { MemoryDeckCard(room, false, {}, MemoryPhotosPanel(pics, picking, {}, { _, _ -> })) }
    }

    private val full = roomForTest(memoryPhotoIds = (11..18).toList())

    // Eight photos sent and here: the target.
    @Test fun lobbyPhotosLight() = shot("memory-photos-lobby", dark = false, content = lobby(full, arrived(8)))
    @Test fun lobbyPhotosDark() = shot("memory-photos-lobby", dark = true, content = lobby(full, arrived(8)))

    // While sending: three of eight made and sent (their thumbnails are the photos just made), the rest empty. The game cannot start yet.
    private val none = roomForTest(memoryPhotoIds = List(8) { null })
    private val sending = MemoryPicking(total = 8, sent = 3, thumbs = photos.take(3).mapIndexed { i, p -> MemoryPhotos.decode(p); i to p }.toMap())
    @Test fun lobbySendingLight() = shot("memory-photos-sending", dark = false, content = lobby(none, emptyMap(), sending))
    @Test fun lobbySendingDark() = shot("memory-photos-sending", dark = true, content = lobby(none, emptyMap(), sending))

    // Too few: five photos chosen, three places still empty; Ready says what to do. The other player sees the same row greyed.
    private val five = roomForTest(memoryPhotoIds = listOf(11, 12, 13, 14, 15, null, null, null))
    @Test fun lobbyTooFewLight() = shot("memory-photos-few", dark = false, content = lobby(five, arrived(5)))
    @Test fun lobbyTooFewDark() = shot("memory-photos-few", dark = true, content = lobby(five, arrived(5)))

    // The other player's view: the pictures arrive one by one; the third has not reached this phone yet.
    private val guestView = roomForTest(memoryPhotoIds = (11..18).toList(), opener = false)
    @Test fun lobbyGuestLight() = shot("memory-photos-guest", dark = false, content = lobby(guestView, arrived(8).filterKeys { it != 13 && it != 16 }))
    @Test fun lobbyGuestDark() = shot("memory-photos-guest", dark = true, content = lobby(guestView, arrived(8).filterKeys { it != 13 && it != 16 }))

    // The other player while the opener has not finished: the Ready bar says "Waiting for the photos."
    private val guestUnfinished = roomForTest(memoryPhotoIds = listOf(11, 12, 13, null, null, null, null, null), opener = false)
    @Test fun lobbyGuestWaitingLight() = shot("memory-photos-waiting", dark = false, content = lobby(guestUnfinished, arrived(3)))
    @Test fun lobbyGuestWaitingDark() = shot("memory-photos-waiting", dark = true, content = lobby(guestUnfinished, arrived(3)))

    // --- the board ------------------------------------------------------------------------------

    /** Pairs 0, 1, 3 and 4 have their photos here; pair 2 and the rest are still plain faces. */
    private fun board(dark: Boolean) {
        val pairOf = listOf(0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7)
        val up = setOf(0, 8, 2, 12)        // pair 0 face up (a first flip and its match found by Sam), pair 2 and pair 4 face up now
        val owners = "mooooooommoooooo".let { ".".repeat(16).toCharArray().also { a -> listOf(0, 8).forEach { i -> a[i] = 'm' }; listOf(3, 11).forEach { i -> a[i] = 'o' } }.concatToString() }
        val faces = List(16) { i -> if (owners[i] != '.' || i in setOf(2, 12)) CardFace.photo(pairOf[i]) else null }
        // Photos 11 (pair 0), 12 (pair 1), 14 (pair 3) and 15 (pair 4) have arrived; 13 (pair 2) has not.
        val pics = arrived(8).filterKeys { it in setOf(11, 12, 14, 15) }
        val r = roomForTest(
            status = RoomStatus.PLAYING, memoryPhotoIds = (11..18).toList(),
            turns = TurnsView(turn = "me", board = null, winLine = emptyList(), lastMoveByMe = null, lastCell = null, faces = faces, owners = owners),
        )
        val tally = mapOf(TALLY_ME to 0, "Maya" to 0)
        val controls = RoomControls(room = r, mode = null, setup = null, rematch = {}, newGame = {}, switchTo = {}, games = emptyList(), setupOpen = mutableStateOf(false), busy = false)
        shot("memory-photos-board", dark) {
            CompositionLocalProvider(
                LocalMatchSession provides MatchSession(tally, emptyList(), {}),
                LocalRoomControls provides controls,
                LocalTonight provides tonightScore(r, tally),
                LocalTurnHints provides TurnHints(),
            ) { TurnGameScreen(BattleUiState(room = r, tally = tally, memoryPics = pics), battle, onLeave = {}) }
        }
    }

    @Test fun boardPhotosLight() = board(false)
    @Test fun boardPhotosDark() = board(true)
}
