package com.pippop.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.PipPopTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shared set-up for a screenshot test class: a subclass only writes its composables and @Test
 * methods, and calls [shot] to save a light or dark picture.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one starts Firebase and loads word lists.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
abstract class ScreenshotBase {
    @get:Rule
    val compose = createComposeRule()

    /** Renders [content] on the app background and saves it as `<name>-light|dark.png`. */
    protected fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        // Animations would never settle (the result loops, bobbing tokens); step the clock instead.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }
}
