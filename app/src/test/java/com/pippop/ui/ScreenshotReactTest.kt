package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.pippop.data.Reaction
import com.pippop.ui.screens.ReactionTray
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Space
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** The whole reactions tray at once: two rows of eight, as it sits above the dock. */
class ScreenshotReactTest : ScreenshotBase() {
    private val usedLast = listOf(Reaction.LAUGH, Reaction.FIRE, Reaction.HEART, Reaction.KYLIAN)

    @androidx.compose.runtime.Composable
    private fun tray() {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().background(AppTheme.palette.card).padding(Space.M)) {
                ReactionTray(Reaction.recentFirst(usedLast.map { it.key }), onReact = {})
            }
        }
    }

    @Test fun light() = shot("react-new", dark = false) { tray() }
    @Test fun dark() = shot("react-new", dark = true) { tray() }

    /** At 360 dp and text 1.3 times larger: eight across, nothing clipped, a 48 dp tall target each. */
    @Test @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    fun narrowAndLarge() {
        shot("react-new-360-large", dark = false) {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) { tray() }
        }
        for (label in listOf("Laugh", "Thinking", "This is fine")) {
            val b = compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertTrue("$label is ${b.height} tall", b.height >= 48.dp)
            assertTrue("$label is ${b.width} wide", b.width >= 48.dp)
            assertTrue("$label sticks out at ${b.right}", b.right <= 360.dp)
        }
    }
}
