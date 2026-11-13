package com.pippop.ui

import com.pippop.data.Difficulty
import com.pippop.game.SudokuShape
import com.pippop.game.code.CodeRules
import com.pippop.game.connect4.C4Level
import com.pippop.game.dominoes.DominoOptions
import com.pippop.game.golf.GolfLevel
import com.pippop.game.numbers.NumberLevel
import com.pippop.game.slide.SlideLevel
import com.pippop.game.words.WordLevel

/**
 * Starting a solo game from its setup (what [com.pippop.data.SoloSetups] says: the sheet's choices
 * as text). One place says what each choice means to each game; a value that is not understood
 * plays the game's usual values.
 */
private fun Map<String, String>.count(key: String, range: IntRange, fallback: Int) = this[key]?.toIntOrNull()?.takeIf { it in range } ?: fallback

/** 0 for easy, 1 for medium, 2 for hard. */
private fun Map<String, String>.skill(key: String) = listOf("easy", "medium", "hard").indexOf(this[key]).takeIf { it >= 0 } ?: 1

fun SoloViewModel.startWith(values: Map<String, String>) = startNew(
    Difficulty.entries.firstOrNull { it.key == values["level"] } ?: Difficulty.MEDIUM,
    SudokuShape.offered.firstOrNull { it.key == values["shape"] } ?: SudokuShape.CLASSIC,
)

fun SoloConnectFourViewModel.startWith(values: Map<String, String>) =
    start(C4Level.entries.firstOrNull { it.name.equals(values["level"], ignoreCase = true) } ?: C4Level.MEDIUM)

fun SoloDotsViewModel.startWith(values: Map<String, String>) = newGame(values["board"] ?: "4x4")

fun SoloTttViewModel.startWith(values: Map<String, String>) = newGame(values["mode"] == "classic")

fun SoloMemoryViewModel.startWith(values: Map<String, String>) = newGame(values.count("deck", 16..36, 16))

/** Today's word, or practice at the chosen length. */
fun SoloWordsViewModel.startWith(values: Map<String, String>) {
    val level = WordLevel.from(values["length"])
    if (values["mode"] == "practice") playPractice(level) else playDaily(level)
}

fun SoloDominoesViewModel.startWith(values: Map<String, String>) {
    val opponents = values.count("opponents", 1..3, 1)
    setOpponents(opponents)
    setOptions(
        DominoOptions(
            teams = values["teams"] == "teams" && opponents == 3,
            draw = values["draw"] != "block",
            target = values["target"]?.toIntOrNull()?.takeIf { it in DominoOptions.TARGETS } ?: 100,
        ),
    )
    start()
}

fun SoloNumbersViewModel.startWith(values: Map<String, String>) = start(NumberLevel.entries.firstOrNull { it.key == values["level"] } ?: NumberLevel.MEDIUM)

fun SoloHockeyViewModel.startWith(values: Map<String, String>) = start(values.skill("level"))

fun SoloClashViewModel.startWith(values: Map<String, String>) {
    setOpponents(values.count("opponents", 1..3, 1))
    start()
}

fun SoloLudoViewModel.startWith(values: Map<String, String>) {
    setOpponents(values.count("opponents", 1..3, 1))
    start()
}

fun SoloGridViewModel.startWith(values: Map<String, String>) {
    setTimed(values["timed"] != "untimed")
    start()
}

/** "You guess" plays at once; "Computer guesses" waits for the word you pick. */
fun SoloBalloonViewModel.startWith(values: Map<String, String>) = if (values["role"] == "pick") setup() else guessTheirs()

fun SoloCodeViewModel.startWith(values: Map<String, String>) = start(
    CodeRules(pegs = values.count("pegs", 4..5, 4), colours = values.count("colours", 6..7, 6), repeats = values["repeats"] != "no"),
)

fun SoloSlideViewModel.startWith(values: Map<String, String>) =
    start(SlideLevel.entries.firstOrNull { it.key == values["level"] } ?: SlideLevel.MEDIUM)

fun SoloBingoViewModel.startWith(values: Map<String, String>) {
    setPace(BingoPace.entries.firstOrNull { it.name.equals(values["pace"], ignoreCase = true) } ?: BingoPace.NORMAL)
    start(values.count("computers", 1..3, 1))
}

fun SoloGolfViewModel.startWith(values: Map<String, String>) {
    setLevel(GolfLevel.from(values["level"]))
    setComputers(values.count("computers", 0..3, 1))
    setSkill(values.skill("skill"))
    start()
}

fun SoloBlocksViewModel.startWith(values: Map<String, String>) {
    setOpponents(values.count("opponents", 1..3, 1))
    setSkill(values.skill("skill"))
    start()
}
