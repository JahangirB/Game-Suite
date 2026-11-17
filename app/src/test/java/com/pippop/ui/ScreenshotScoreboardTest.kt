package com.pippop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pippop.data.Avatar
import com.pippop.data.SampleGames
import com.pippop.data.Scoreboard
import com.pippop.ui.screens.GameStoryContent
import com.pippop.ui.screens.ScoreTab
import com.pippop.ui.screens.ScoreboardContent
import com.pippop.ui.screens.SoloLines
import com.pippop.ui.screens.ToySheetCard
import com.pippop.ui.theme.AppTheme
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The scoreboard with the preview app's sample games: this month, all time, a game's story, solo, and nothing yet. */
class ScreenshotScoreboardTest : ScreenshotBase() {
    private val utc = ZoneOffset.UTC
    // 18 days left in November.
    private val now = LocalDateTime.of(2026, 11, 12, 21, 0).toInstant(utc).toEpochMilli()
    private val records = SampleGames.matches(now, utc)
    private val board = Scoreboard.of(records, now, utc)
    private val lines = SoloLines.of(SampleGames.soloRecords(now))
    private val fox = Avatar.FOX
    private val panda = Avatar.PANDA

    @Composable
    private fun screen(tab: ScoreTab, board: Scoreboard = this.board, solo: List<com.pippop.ui.screens.SoloLine> = lines) =
        ScoreboardContent(board, solo, tab, {}, fox, panda, {}) {}

    @Composable
    private fun story() {
        val g = board.thisMonth.games.first()
        Box(Modifier.fillMaxSize()) {
            screen(ScoreTab.MONTH)
            Box(Modifier.fillMaxSize().background(AppTheme.palette.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.BottomCenter) {
                ToySheetCard { GameStoryContent(Scoreboard.story(records, g.game, "Maya"), g, "This month", "Maya", fox, panda) }
            }
        }
    }

    @Test fun monthLight() = shot("scores-new", dark = false) { screen(ScoreTab.MONTH) }
    @Test fun monthDark() = shot("scores-new", dark = true) { screen(ScoreTab.MONTH) }

    @Test fun allTimeLight() = shot("scores-alltime", dark = false) { screen(ScoreTab.ALL) }
    @Test fun allTimeDark() = shot("scores-alltime", dark = true) { screen(ScoreTab.ALL) }

    @Test fun storyLight() = shot("scores-game", dark = false) { story() }
    @Test fun storyDark() = shot("scores-game", dark = true) { story() }

    @Test fun soloLight() = shot("scores-solo", dark = false) { screen(ScoreTab.SOLO) }
    @Test fun soloDark() = shot("scores-solo", dark = true) { screen(ScoreTab.SOLO) }

    private val empty = Scoreboard.of(emptyList(), now, utc)

    @Test fun emptyLight() = shot("scores-empty", dark = false) { screen(ScoreTab.MONTH, empty, emptyList()) }
    @Test fun emptyDark() = shot("scores-empty", dark = true) { screen(ScoreTab.MONTH, empty, emptyList()) }

    @Test fun emptySoloLight() = shot("scores-empty-solo", dark = false) { screen(ScoreTab.SOLO, empty, emptyList()) }
    @Test fun emptySoloDark() = shot("scores-empty-solo", dark = true) { screen(ScoreTab.SOLO, empty, emptyList()) }

    // A short month, so the shelf of cups shows in one picture (a tie among them).
    private val shortBoard = run {
        val keep = board.thisMonth.games.take(2).map { it.game }
        val tied = records.map { r -> if (r.at < now - 40L * 86_400_000L && r.at > now - 70L * 86_400_000L) r.copy(winner = null) else r }
        Scoreboard.of(tied.filter { r -> r.at < now - 12L * 86_400_000L || r.game in keep }, now, utc)
    }

    @Test fun cupsLight() = shot("scores-cups", dark = false) { screen(ScoreTab.MONTH, shortBoard) }
    @Test fun cupsDark() = shot("scores-cups", dark = true) { screen(ScoreTab.MONTH, shortBoard) }

    // Two-digit counts at a big font, and winners with no picture (their first letter on the cup).
    private val busy = run {
        val more = records.flatMap { r -> (1..6).map { i -> r.copy(at = r.at - i * 60_000L) } }
        Scoreboard.of(records + more, now, utc)
    }

    @Composable
    private fun big(avatars: Boolean) {
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1.3f)) {
            ScoreboardContent(busy, lines, ScoreTab.MONTH, {}, fox.takeIf { avatars }, panda.takeIf { avatars }, {}) {}
        }
    }

    @Test fun bigCountsLight() = shot("scores-big", dark = false) { big(true) }
    @Test fun bigCountsDark() = shot("scores-big", dark = true) { big(true) }

    @Test fun noPicturesLight() = shot("scores-nopics", dark = false) { ScoreboardContent(shortBoard, lines, ScoreTab.MONTH, {}, null, null, {}) {} }
    @Test fun noPicturesDark() = shot("scores-nopics", dark = true) { ScoreboardContent(shortBoard, lines, ScoreTab.MONTH, {}, null, null, {}) {} }
}
