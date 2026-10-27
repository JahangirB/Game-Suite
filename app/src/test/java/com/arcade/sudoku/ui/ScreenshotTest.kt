package com.arcade.sudoku.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.Profile
import com.arcade.sudoku.game.ludo.LudoView
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.ui.screens.DieFace
import com.arcade.sudoku.ui.screens.HomeScreen
import com.arcade.sudoku.ui.screens.LudoBoard
import com.arcade.sudoku.ui.screens.PopupAction
import com.arcade.sudoku.ui.screens.ResultPopup
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.ArcadeSudokuTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of screens in light and dark, saved under app/build/outputs/roborazzi and uploaded
 * by CI, so a change to how something looks can be seen without a phone. They are not
 * compared against stored pictures.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one starts Firebase and loads word lists.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** Renders [content] on the app background and saves it as `<name>-light|dark.png`. */
    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        // Animations would never settle (the result loops, bobbing tokens); step the clock instead.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArcadeSudokuTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    @Composable
    private fun home() = HomeScreen(
        solo = SoloUiState(),
        profile = Profile(name = "Sam"),
        onContinue = {},
        onNewGame = {},
        onProfile = {},
        onSolo = {},
        onBattle = {},
        partnerBusy = false,
        onPlayWithPartner = {},
        onInvitePartner = {},
        onPlayMode = {},
        connected = false,
        onConnectNearby = {},
        onStartRoom = {},
        onJoinRoom = {},
    )

    @Test fun homeLight() = shot("home", dark = false) { home() }

    @Test fun homeDark() = shot("home", dark = true) { home() }

    private val ludo = LudoView(
        colours = mapOf('m' to 0, 'o' to 2),
        tokens = mapOf('m' to listOf(-1, 5, 20, 56), 'o' to listOf(-1, -1, 12, 30)),
        roll = 6,
        movable = listOf(0, 1),
        lastMove = null,
    )

    @Composable
    private fun ludoTable() = Box(Modifier.padding(16.dp)) {
        LudoBoard(ludo, onToken = null, modifier = Modifier.size(360.dp))
        DieFace(5, Modifier.padding(top = 372.dp))
    }

    @Test fun ludoLight() = shot("ludo", dark = false) { ludoTable() }

    @Test fun ludoDark() = shot("ludo", dark = true) { ludoTable() }

    @Composable
    private fun result() = Box(Modifier.fillMaxSize()) {
        ResultPopup(
            game = GameKind.CONNECT4,
            outcome = Outcome.WIN,
            title = "You win!",
            detail = "Four in a row",
            finish = PopupAction("Finish", {}),
            again = PopupAction("Rematch", {}),
            scores = listOf("You 3", "Alex 2"),
            status = "Alex wants a rematch",
            statusGood = true,
        )
    }

    @Test fun resultLight() = shot("result", dark = false) { result() }

    @Test fun resultDark() = shot("result", dark = true) { result() }

    // The design-system gallery is long: a tall screen fits it all in one picture.
    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun galleryLight() = shot("gallery", dark = false) { DesignSystemGallery() }

    @Test @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun galleryDark() = shot("gallery", dark = true) { DesignSystemGallery() }

    /** The phone's largest text setting: nothing should be cut off or overlap. */
    @Test @Config(qualifiers = "w411dp-h3200dp-xxhdpi")
    fun galleryLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        shot("gallery-large-text", dark = false) { DesignSystemGallery() }
    }
}
