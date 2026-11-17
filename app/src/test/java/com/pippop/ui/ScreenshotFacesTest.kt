package com.pippop.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.Faces
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.TurnsView
import com.pippop.ui.screens.FaceBook
import com.pippop.ui.screens.LobbyFrame
import com.pippop.ui.screens.LocalFaces
import com.pippop.ui.screens.LocalMatchSession
import com.pippop.ui.screens.LocalRoomControls
import com.pippop.ui.screens.LocalTonight
import com.pippop.ui.screens.LocalTurnHints
import com.pippop.ui.screens.MatchSession
import com.pippop.ui.screens.ProfileScreen
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.TurnGameScreen
import com.pippop.ui.screens.TurnHints
import com.pippop.ui.screens.tonightScore
import org.junit.Test

/**
 * Step 29, your photo as your face (target `profile-new`, "Use a photo"): the profile with the button and with a photo set,
 * and a photo in the lobby's chips, the room bar and the result's scoreboard. The pictures are made up here (a sun and a
 * moon on stripes), not people.
 */
class ScreenshotFacesTest : ScreenshotBase() {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val appVm by lazy { AppViewModel(app) }
    private val battle by lazy { BattleViewModel(app) }

    /** A made-up portrait: stripes of [hue] with a disc, as a 96 x 96 JPEG the way the app would keep it. */
    private fun portrait(hue: Float, wide: Boolean = true): String {
        val b = Bitmap.createBitmap(if (wide) 320 else 240, 240, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 8) {
            paint.color = Color.HSVToColor(floatArrayOf((hue + i * 9) % 360, 0.45f, 0.97f))
            c.drawRect(0f, 30f * i, b.width.toFloat(), 30f * (i + 1), paint)
        }
        paint.color = Color.rgb(255, 214, 102)
        c.drawCircle(b.width / 2f, 100f, 62f, paint)
        paint.color = Color.rgb(48, 44, 84)
        c.drawCircle(b.width / 2f, 215f, 80f, paint)
        return Faces.fromBitmap(b)!!
    }

    private val mine by lazy { portrait(200f) }
    private val maya by lazy { portrait(330f, wide = false) }

    private val profile = Profile(name = "Sam", avatar = Avatar.FOX, scriptUrl = "https://pip.example.workers.dev")

    @Composable
    private fun top(p: Profile) = ProfileScreen(p, busy = false, app = appVm, battle = battle, updates = UpdateUiState(), onCheckUpdates = {}, onUpdate = {}, onBack = {})

    @Test fun profileUseAPhotoLight() = shot("faces-profile", dark = false) { top(profile) }
    @Test fun profileUseAPhotoDark() = shot("faces-profile", dark = true) { top(profile) }

    // A photo set, on an Apps Script link: Remove photo, and the line that only you see it.
    private val withPhoto = profile.copy(face = mine, scriptUrl = "https://script.google.com/macros/s/abc/exec")

    // The app decodes your photo off the main thread; the test has it ready the way the app would by then.
    @Test fun profileWithAPhotoLight() { Faces.decode(mine); shot("faces-profile-set", dark = false) { top(withPhoto) } }
    @Test fun profileWithAPhotoDark() { Faces.decode(mine); shot("faces-profile-set", dark = true) { top(withPhoto) } }

    // --- in a room ------------------------------------------------------------------------------

    private fun player(name: String, avatar: Avatar, game: GameKind, ready: Boolean = false, photoId: Int? = null) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null, progress = null,
        finishMs = null, left = false, idleMs = 0, online = true, at = game, photoId = photoId,
    )

    private fun room(game: GameKind, status: RoomStatus, winner: String? = null, turns: TurnsView? = null): RoomState {
        val others = listOf(player("Maya", Avatar.PANDA, game, ready = true, photoId = 7))
        return RoomState(
            code = "K7Q2NB", role = "host", game = game, status = status, difficulty = null, round = if (status == RoomStatus.FINISHED) 6 else 0,
            startAt = null, puzzle = null, solution = null, winner = winner, endReason = if (winner != null) "four" else null,
            me = player("Sam", Avatar.FOX, game), opponent = others.first(), ships = null, turns = turns, serverTime = 0, others = others,
            maxPlayers = game.maxPlayers, inSession = true, caps = listOf(Faces.CAP),
            winnerName = if (winner == "opponent") "Maya" else null,
        )
    }

    private fun faces(room: RoomState) = FaceBook.build("Sam", mine, null, room, mapOf(7 to maya))

    @Composable
    private fun withFaces(room: RoomState, content: @Composable () -> Unit) =
        CompositionLocalProvider(LocalFaces provides faces(room)) { content() }

    @Composable
    private fun lobby(room: RoomState) = withFaces(room) {
        LobbyFrame(BattleUiState(room = room), Profile(name = "Sam", avatar = Avatar.FOX), onLeave = {}, onReady = {}, onReact = {}) {}
    }

    @Test fun lobbyPhotosLight() = shot("faces-lobby", dark = false) { lobby(room(GameKind.DOMINOES, RoomStatus.LOBBY)) }
    @Test fun lobbyPhotosDark() = shot("faces-lobby", dark = true) { lobby(room(GameKind.DOMINOES, RoomStatus.LOBBY)) }

    private val won = listOf(".......", ".......", "...o...", "..mo...", ".moom..", "mmmmooo").joinToString("")

    private fun result(dark: Boolean) {
        val r = room(GameKind.CONNECT4, RoomStatus.FINISHED, "me", TurnsView(turn = null, board = won, winLine = listOf(35, 36, 37, 38), lastMoveByMe = true, lastCell = 38))
        val tally = mapOf(TALLY_ME to 3, "Maya" to 2)
        val controls = RoomControls(
            room = r, mode = null, setup = null, rematch = {}, newGame = {}, switchTo = {},
            games = emptyList(), setupOpen = mutableStateOf(false), busy = false,
        )
        shot("faces-result", dark) {
            CompositionLocalProvider(
                LocalFaces provides faces(r),
                LocalMatchSession provides MatchSession(tally, listOf(GameKind.LUDO, GameKind.WORDS), {}),
                LocalRoomControls provides controls,
                LocalTonight provides tonightScore(r, tally),
                LocalTurnHints provides TurnHints(),
            ) { TurnGameScreen(BattleUiState(room = r, tally = tally), battle, onLeave = {}) }
        }
    }

    @Test fun resultPhotosLight() = result(false)
    @Test fun resultPhotosDark() = result(true)
}
