package com.pippop.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pippop.ui.screens.Face
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.platform.testTag
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pippop.data.Avatar
import com.pippop.data.Profile
import com.pippop.ui.screens.FaceBook
import com.pippop.ui.screens.Face
import com.pippop.ui.screens.HomeTopBar
import com.pippop.ui.screens.LocalFaces
import com.pippop.ui.screens.PlayerAvatar
import com.pippop.ui.screens.ProfileScreen
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.PipPopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Game Store step 7: the photo you chose is your face wherever your own face is drawn (top bar, a game chip, the
 * scoreboard), and the profile's Look section no longer holds Ship Battle styles. The photo is a made-up bitmap.
 */
class ScreenshotYourFaceTest : ScreenshotBase() {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val appVm by lazy { AppViewModel(app) }
    private val battle by lazy { BattleViewModel(app) }

    private fun portrait(fill: Int = Color.rgb(0, 200, 60)): ImageBitmap {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = fill
        c.drawRect(0f, 0f, 96f, 96f, paint)
        paint.color = Color.rgb(255, 214, 102)
        c.drawCircle(48f, 40f, 24f, paint)
        paint.color = Color.rgb(48, 44, 84)
        c.drawCircle(48f, 96f, 34f, paint)
        return b.asImageBitmap()
    }

    @Composable
    private fun withPhoto(content: @Composable () -> Unit) = CompositionLocalProvider(LocalFaces provides FaceBook(mine = portrait()), content = content)

    @Composable
    private fun topBar() = withPhoto { HomeTopBar(Avatar.FOX, scrolled = false, onProfile = {}) }

    @Composable
    private fun chips() = withPhoto {
        val p = AppTheme.palette
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerAvatar(FaceBook.YOU, p.me, size = 40, avatar = Avatar.FOX)
                PlayerAvatar("Maya", p.accent, size = 40, avatar = Avatar.PANDA)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Face("You", Avatar.FOX, p.me, 52.dp)
                Face("Maya", Avatar.PANDA, p.accent, 52.dp)
            }
        }
    }

    @Composable
    private fun look() = withPhoto {
        ProfileScreen(Profile(name = "Sam", avatar = Avatar.FOX), busy = false, app = appVm, battle = battle, updates = UpdateUiState(), onCheckUpdates = {}, onUpdate = {}, onBack = {})
    }

    @Test fun topBarLight() = shot("yourface-topbar", false) { topBar() }
    @Test fun topBarDark() = shot("yourface-topbar", true) { topBar() }
    @Test fun chipLight() = shot("yourface-chip", false) { chips() }
    @Test fun chipDark() = shot("yourface-chip", true) { chips() }
    @Test fun lookLight() = shot("yourface-look", false) { look() }
    @Test fun lookDark() = shot("yourface-look", true) { look() }

    // --- which avatar shows what: read the pixel in the middle of a 48dp avatar -------------------

    private var pixelShots = 0

    private fun middle(faces: FaceBook, name: String): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFaces provides faces) {
                    androidx.compose.foundation.layout.Box(Modifier.testTag("a")) { PlayerAvatar(name, AppTheme.palette.me, size = 48, avatar = Avatar.FOX) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val path = "build/outputs/roborazzi/yourface-pixel-${pixelShots++}.png"
        compose.onNodeWithTag("a").captureRoboImage(path)
        val img = android.graphics.BitmapFactory.decodeFile(path)
        // Below the face's gold disc, above the dark shoulders: the plain background of the photo.
        return img.getPixel(img.width / 2, img.height / 8)
    }

    private val green = Color.rgb(0, 200, 60)

    @Test fun yourAvatarWithAPhotoShowsThePhoto() = assertEquals(green, middle(FaceBook(mine = portrait()), FaceBook.YOU))

    @Test fun yourAvatarWithoutAPhotoShowsTheAnimal() = assertNotEquals(green, middle(FaceBook.None, FaceBook.YOU))

    @Test fun aBrokenPhotoFallsBackToTheAnimal() {
        val book = FaceBook.build("Sam", "not a photo", null, null, emptyMap())
        assertNotEquals(green, middle(book, FaceBook.YOU))
    }

    @Test fun anotherPlayerNeverShowsYourPhoto() = assertNotEquals(green, middle(FaceBook(mine = portrait()), "Maya"))

    // --- the scoreboard knows you by a flag, not by the word "You" ------------------------------

    private fun scoreboardFace(name: String, mine: Boolean): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFaces provides FaceBook(mine = portrait())) {
                    androidx.compose.foundation.layout.Box(Modifier.testTag("a")) { Face(name, Avatar.FOX, AppTheme.palette.me, 52.dp, mine = mine) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val path = "build/outputs/roborazzi/yourface-pixel-${pixelShots++}.png"
        compose.onNodeWithTag("a").captureRoboImage(path)
        val img = android.graphics.BitmapFactory.decodeFile(path)
        return img.getPixel(img.width / 2, img.height / 8)
    }

    @Test fun theScoreboardShowsYourPhotoForTheRowMarkedAsYou() = assertEquals(green, scoreboardFace("Me", mine = true))

    @Test fun aPlayerCalledYouIsNotYouUnlessMarked() = assertNotEquals(green, scoreboardFace("You", mine = false))

    // --- the profile's own avatar: the photo, and the animal again once it is removed ------------

    @Test fun theProfileAvatarShowsThePhotoAndTheAnimalAfterItIsRemoved() {
        val photo = com.pippop.data.Faces.fromBitmap(portrait().asAndroidBitmap())!!
        com.pippop.data.Faces.decode(photo)
        val profile = androidx.compose.runtime.mutableStateOf(Profile(name = "Sam", avatar = Avatar.FOX, face = photo))
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Box(Modifier.testTag("p")) {
                    ProfileScreen(profile.value, busy = false, app = appVm, battle = battle, updates = UpdateUiState(), onCheckUpdates = {}, onUpdate = {}, onBack = {})
                }
            }
        }
        fun avatarPixel(): Int {
            compose.waitForIdle()
            val path = "build/outputs/roborazzi/yourface-profile-pixel-${pixelShots++}.png"
            compose.onNodeWithTag("p").captureRoboImage(path)
            val img = android.graphics.BitmapFactory.decodeFile(path)
            // Left of the 52dp avatar middle, in the "You" card (xxhdpi: 3 px per dp).
            return img.getPixel(129, 470)
        }
        val withPhoto = avatarPixel()
        assertTrue("green channel leads: $withPhoto", Color.green(withPhoto) > Color.red(withPhoto) + 60 && Color.green(withPhoto) > Color.blue(withPhoto) + 60)
        profile.value = profile.value.copy(face = null)
        val without = avatarPixel()
        assertFalse("the animal shows again: $without", Color.green(without) > Color.red(without) + 60 && Color.green(without) > Color.blue(without) + 60)
    }

    // --- the month-end cup window (a Dialog, so it is composed under the same LocalFaces as the rest) ------

    private fun greenPixelsInCupDialog(faces: FaceBook): Int {
        val end = com.pippop.data.MonthEnd(java.time.YearMonth.of(2026, 10), "Maya", com.pippop.data.MatchRecord.ME, 15, 10, 25, com.pippop.data.GameKind.WORDS)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFaces provides faces) {
                    com.pippop.ui.screens.TrophyMomentDialog(end, java.time.YearMonth.of(2026, 11), Avatar.FOX, Avatar.PANDA) {}
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val path = "build/outputs/roborazzi/yourface-cup-pixel-${pixelShots++}.png"
        compose.onNode(androidx.compose.ui.test.isDialog()).captureRoboImage(path)
        val img = android.graphics.BitmapFactory.decodeFile(path)
        var n = 0
        for (y in 0 until img.height) for (x in 0 until img.width) if (img.getPixel(x, y) == green) n++
        return n
    }

    @Test fun theMonthEndCupWindowShowsYourPhoto() {
        assertTrue(greenPixelsInCupDialog(FaceBook(mine = portrait())) > 200)
    }

    @Test fun theMonthEndCupWindowShowsTheAnimalWithoutAPhoto() = assertEquals(0, greenPixelsInCupDialog(FaceBook.None))

    // --- the lobby: your row keeps your photo even when another player has your name ----------------

    private fun lobbyRowPixel(isMe: Boolean): Int {
        val info = com.pippop.data.PlayerInfo(
            name = "Sam", avatar = Avatar.FOX, deviceId = null, emoji = null, ready = false, difficulty = null, progress = null,
            finishMs = null, left = false, idleMs = 0, online = true, at = null,
        )
        // A room where two players are called Sam: the book has no photo by that name, only yours under YOU.
        val book = FaceBook(emptyMap(), portrait())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                CompositionLocalProvider(LocalFaces provides book) {
                    androidx.compose.foundation.layout.Box(Modifier.testTag("a")) {
                        com.pippop.ui.screens.PlayerRow(info, isMe = isMe, color = AppTheme.palette.me, reaction = null, seat = 0, game = com.pippop.data.GameKind.WORDS)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val path = "build/outputs/roborazzi/yourface-lobby-pixel-${pixelShots++}.png"
        compose.onNodeWithTag("a").captureRoboImage(path)
        val img = android.graphics.BitmapFactory.decodeFile(path)
        // The 40dp avatar at the row's start (xxhdpi): the photo's plain top-left area.
        return img.getPixel(24, 40)
    }

    @Test fun yourLobbyRowShowsYourPhotoWhateverTheNames() = assertEquals(green, lobbyRowPixel(isMe = true))

    @Test fun anotherPlayersLobbyRowIsNotYourPhoto() = assertNotEquals(green, lobbyRowPixel(isMe = false))
}
