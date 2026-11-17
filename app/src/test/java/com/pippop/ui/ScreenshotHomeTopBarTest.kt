package com.pippop.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pippop.data.Avatar
import com.pippop.data.Faces
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.Profile
import com.pippop.ui.screens.FaceBook
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.LocalFaces
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.PipPopTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixed top bar of the home: one thin row, the app's name and the player's photo (or animal) in an ink ring on the
 * right; no greeting. Pictures: with a photo, without, scrolled (the bar stays, shelf cards under it), and at font scale 1.3.
 */
class ScreenshotHomeTopBarTest : ScreenshotBase() {
    private fun portrait(): androidx.compose.ui.graphics.ImageBitmap {
        val b = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 8) {
            paint.color = Color.HSVToColor(floatArrayOf((200f + i * 9) % 360, 0.45f, 0.97f))
            c.drawRect(0f, 30f * i, 240f, 30f * (i + 1), paint)
        }
        paint.color = Color.rgb(255, 214, 102)
        c.drawCircle(120f, 100f, 62f, paint)
        paint.color = Color.rgb(48, 44, 84)
        c.drawCircle(120f, 215f, 80f, paint)
        return Faces.decode(Faces.fromBitmap(b))!!
    }

    private val shelf = listOf(
        GameKind.SUDOKU, GameKind.SHIPS, GameKind.MEMORY, GameKind.WORDS, GameKind.TTT, GameKind.DOMINOES,
        GameKind.CLASH, GameKind.LUDO, GameKind.GOLF,
    )

    @Composable
    private fun home(name: String = "Sam", photo: Boolean, scrollable: Boolean = false, onProfile: () -> Unit = {}) {
        val faces = if (photo) FaceBook(mine = portrait()) else FaceBook.None
        CompositionLocalProvider(LocalFaces provides faces) {
            HomeScreen(
                solo = SoloUiState(), profile = Profile(name = name, avatar = Avatar.FOX, playMode = PlayMode.ONLINE, shelf = if (scrollable) GameKind.entries else shelf),
                onContinue = {}, onProfile = onProfile, onSolo = { _, _ -> }, onScores = {},
                partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {}, connected = false, onConnect = {},
                now = 0L,
            )
        }
    }

    private fun snap(name: String, dark: Boolean, scale: Float? = null, scroll: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) {
                    if (scale == null) content()
                    else CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        if (scroll) {
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f, durationMillis = 200) }
            compose.mainClock.advanceTimeBy(2500)
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    @Test fun photoLight() = snap("topbar-photo", false) { home(photo = true) }
    @Test fun photoDark() = snap("topbar-photo", true) { home(photo = true) }
    @Test fun animalLight() = snap("topbar-animal", false) { home(photo = false) }
    @Test fun animalDark() = snap("topbar-animal", true) { home(photo = false) }
    @Test fun scrolledLight() = snap("topbar-scrolled", false, scroll = true) { home(photo = true, scrollable = true) }
    @Test fun scrolledDark() = snap("topbar-scrolled", true, scroll = true) { home(photo = true, scrollable = true) }
    @Test fun bigTextLight() = snap("topbar-big", false, scale = 1.3f) { home(photo = true) }
    @Test fun bigTextDark() = snap("topbar-big", true, scale = 1.3f) { home(photo = true) }

    @Test fun theBarIsAtMost64dpTallAndHasNoGreeting() {
        compose.setContent { PipPopTheme(darkTheme = false) { home(photo = false) } }
        val bar = compose.onNodeWithTag("homeTopBar").getUnclippedBoundsInRoot()
        assertTrue("bar is ${bar.height}", bar.height <= 64.dp)
        assertTrue("bar is ${bar.height}", bar.height >= 48.dp)
        compose.onNodeWithText("Hi, Sam").assertDoesNotExist()
    }

    @Test fun theBarStaysTheSameHeightAtLargeTextAndWhenScrolled() {
        compose.setContent {
            PipPopTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) { home(photo = false) }
            }
        }
        assertTrue(compose.onNodeWithTag("homeTopBar").getUnclippedBoundsInRoot().height <= 64.dp)
    }

    @Test fun tappingTheAvatarOpensTheProfile() {
        var opened = 0
        compose.setContent { PipPopTheme(darkTheme = false) { home(photo = false, onProfile = { opened++ }) } }
        compose.onNodeWithContentDescription("Profile and settings").performClick()
        assertEquals(1, opened)
    }
}
