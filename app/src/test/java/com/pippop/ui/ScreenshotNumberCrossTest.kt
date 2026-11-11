package com.pippop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.NumberBoard
import com.pippop.game.numbers.NumberCross
import com.pippop.game.numbers.NumberCrossGenerator
import com.pippop.game.numbers.NumberLevel
import com.pippop.ui.screens.FlightHint
import com.pippop.ui.screens.NumberCrossPlay
import org.junit.Test
import kotlin.random.Random

/**
 * Number Cross part-way through a medium puzzle from the game's own generator: the first
 * blanks are filled with their right numbers, the rest are open slots, and the bank is below.
 * Shows raised number tiles, sunken blanks and chunky stone blocks.
 */
class ScreenshotNumberCrossTest : ScreenshotBase() {
    // A puzzle the generator made (a fixed seed, so the picture is the same every run), checked by the game's own rules.
    private val puzzle = NumberCross.check(NumberCrossGenerator(Random(11)).generate(NumberLevel.MEDIUM))
    private val blank = puzzle.blanks.toSet()
    private val board = NumberBoard(puzzle.w, puzzle.h, puzzle.grid.mapIndexed { i, t -> if (i in blank) "?" else t }, puzzle.blanks, puzzle.bank)

    // The first half of the blanks are right; the picked one is the next open blank.
    private val entries = puzzle.blanks.mapIndexed { k, i -> if (k < puzzle.blanks.size / 2) puzzle.grid[i] else "" }

    @Composable
    private fun numberCross() = Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberCrossPlay(board, entries, puzzle.blanks.size / 2, true, {}, {}, remember { FlightHint() })
    }

    @Test fun numbersLight() = shot("numbers", dark = false) { numberCross() }

    @Test fun numbersDark() = shot("numbers", dark = true) { numberCross() }
}
