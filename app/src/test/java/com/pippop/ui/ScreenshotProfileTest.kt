package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.SoloKey
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecord
import com.pippop.data.Stats
import com.pippop.ui.screens.PreviewAppCard
import com.pippop.ui.screens.StatisticsCard
import org.junit.Test

/** The profile's Statistics card (Sudoku bests from the solo records) and the preview app's card with its test tools. */
class ScreenshotProfileTest : ScreenshotBase() {
    private val solo = mapOf(
        SoloKey(GameKind.SUDOKU, "easy", SoloMetric.TIME) to SoloRecord(244_000, 5, 9),
        SoloKey(GameKind.SUDOKU, "medium", SoloMetric.TIME) to SoloRecord(512_000, 0, 14),
        SoloKey(GameKind.SUDOKU, "hard", SoloMetric.TIME) to SoloRecord(930_000, 9, 3),
    )

    @Composable
    private fun profile() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatisticsCard(solo, Stats(battlesWon = 12, battlesLost = 7))
        PreviewAppCard()
    }

    @Test fun profileLight() = shot("profile", dark = false) { profile() }

    @Test fun profileDark() = shot("profile", dark = true) { profile() }
}
