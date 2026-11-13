package com.pippop.data

import com.pippop.game.SudokuShape
import com.pippop.ui.screens.tagline
import com.pippop.game.dots.DotsShape
import com.pippop.game.memory.MemoryMatch

/** One thing a player can pick: the [value] that is stored, its [short] label in a row of choices, and its [long] one in a sentence. */
data class SoloOption(val value: String, val short: String, val long: String = short)

/**
 * One setup choice of a game played alone (a level, a size, a number of computers). Shown only
 * [shownWhen] the other choices allow it; [note] explains the chosen option where it needs it.
 */
data class SoloField(
    val key: String,
    val label: String,
    val options: List<SoloOption>,
    val default: String,
    val shownWhen: (Map<String, String>) -> Boolean = { true },
    val note: (Map<String, String>) -> String? = { null },
) {
    init { require(options.any { it.value == default }) { "$key: default $default is not an option" } }

    fun optionOf(values: Map<String, String>) = options.first { it.value == values[key] }
}

/**
 * How a game is set up alone: its [fields] (one means a single row of choices on the game's sheet,
 * more mean one line saying the last setup and "Change"), [rowLabel] for that single row, and the
 * [button] text after "Play solo ·" (the main choice).
 */
class SoloSpec(
    val fields: List<SoloField>,
    val rowLabel: String = "Solo",
    private val button: ((Map<String, String>) -> String)? = null,
) {
    val single get() = fields.size == 1

    /** The choices that matter now. */
    fun shown(values: Map<String, String>) = fields.filter { it.shownWhen(values) }

    /** The main choice, said short: "Medium", "2 computers". */
    fun buttonText(values: Map<String, String>): String = button?.invoke(values) ?: fields.first().optionOf(values).long

    /** The whole setup in one line: "Medium · Classic". */
    fun line(values: Map<String, String>): String = shown(values).joinToString(" · ") { it.optionOf(values).long }
}

/** What each game's solo setup offers, and how it is remembered. */
object SoloSetups {
    private fun levels(vararg labels: String) = labels.map { SoloOption(it.lowercase(), it) }
    private val EASY_MEDIUM_HARD = levels("Easy", "Medium", "Hard")

    private fun computers(count: IntRange, none: Boolean = false) = count.map {
        SoloOption(it.toString(), if (it == 0 && none) "None" else "$it", if (it == 0) "On your own" else if (it == 1) "1 computer" else "$it computers")
    }

    private val SKILLS = listOf(SoloOption("easy", "Easy", "Easy skill"), SoloOption("medium", "Medium", "Medium skill"), SoloOption("hard", "Hard", "Hard skill"))

    private val SHAPES = SudokuShape.offered.map { SoloOption(it.key, it.label) }
    private val DIFFICULTIES = Difficulty.entries.map { SoloOption(it.key, it.label) }

    private val SPECS: Map<GameKind, SoloSpec> = mapOf(
        GameKind.SUDOKU to SoloSpec(listOf(
            SoloField("level", "Level", DIFFICULTIES, "medium", note = { v -> Difficulty.entries.firstOrNull { it.key == v["level"] }?.tagline() }),
            SoloField("shape", "Board", SHAPES, "classic", note = { v ->
                SudokuShape.offered.firstOrNull { it.key == v["shape"] }?.takeIf { it.big }?.let { "${it.grids.size} grids joined: saves as you go, ${com.pippop.game.SoloGame.BIG_MISTAKES} mistakes allowed." }
            }),
        )),
        GameKind.CONNECT4 to SoloSpec(listOf(SoloField("level", "Level", EASY_MEDIUM_HARD, "medium"))),
        GameKind.DOTS to SoloSpec(listOf(SoloField("board", "Board", DotsShape.ALL.map { SoloOption(it.key, DotsShape.label(it.key)) }, "4x4")), rowLabel = "Board"),
        GameKind.MEMORY to SoloSpec(listOf(SoloField("deck", "Deck", MemoryMatch.DECKS.map { SoloOption(it.toString(), "$it cards") }, "16")), rowLabel = "Deck"),
        GameKind.WORDS to SoloSpec(
            listOf(
                SoloField("mode", "Word", listOf(SoloOption("daily", "Today's word"), SoloOption("practice", "Practice")), "daily"),
                SoloField(
                    "length", "Length",
                    listOf(SoloOption("easy", "4", "4 letters"), SoloOption("medium", "5", "5 letters"), SoloOption("hard", "6", "6 letters"), SoloOption("expert", "7–8", "7–8 letters")),
                    "medium", shownWhen = { it["mode"] == "practice" },
                ),
            ),
            button = { v -> if (v["mode"] == "practice") "Practice · " + wordsLength(v) else "Today's word" },
        ),
        GameKind.TTT to SoloSpec(listOf(SoloField("mode", "Game", listOf(SoloOption("classic", "Normal", "Normal 3 × 3"), SoloOption("ultimate", "Ultimate")), "ultimate")), rowLabel = "Game"),
        GameKind.DOMINOES to SoloSpec(
            listOf(
                SoloField("opponents", "Against", computers(1..3), "1"),
                SoloField("draw", "When stuck", listOf(SoloOption("draw", "Draw", "Draw when stuck"), SoloOption("block", "Block", "Pass when stuck")), "draw", note = { v ->
                    if (v["draw"] == "block") "Stuck? Pass. Nobody can play: the lightest hand wins the round." else "Stuck? Take tiles from the boneyard until one fits."
                }),
                SoloField("target", "Play to", listOf(50, 100, 150).map { SoloOption(it.toString(), "$it", "first to $it") }, "100"),
                SoloField(
                    "teams", "Sides", listOf(SoloOption("alone", "Each alone"), SoloOption("teams", "2 v 2", "two against two")), "alone",
                    shownWhen = { it["opponents"] == "3" },
                    note = { v -> if (v["teams"] == "teams" && v["opponents"] == "3") "Your partner is Computer 2, sitting opposite." else null },
                ),
            ),
        ),
        GameKind.NUMBERS to SoloSpec(listOf(SoloField("level", "Level", EASY_MEDIUM_HARD, "medium"))),
        GameKind.HOCKEY to SoloSpec(listOf(SoloField("level", "Level", EASY_MEDIUM_HARD, "medium"))),
        GameKind.CLASH to SoloSpec(listOf(SoloField("opponents", "Against", computers(1..3), "1")), rowLabel = "Against"),
        GameKind.LUDO to SoloSpec(listOf(SoloField("opponents", "Against", computers(1..3), "1")), rowLabel = "Against"),
        GameKind.GRID to SoloSpec(listOf(SoloField("timed", "Time", listOf(SoloOption("timed", "2 minutes"), SoloOption("untimed", "No limit", "No time limit")), "timed")), rowLabel = "Time"),
        GameKind.BALLOON to SoloSpec(listOf(SoloField("role", "Who guesses", listOf(SoloOption("guess", "You guess"), SoloOption("pick", "Computer guesses")), "guess")), rowLabel = "Play"),
        GameKind.CODE to SoloSpec(
            listOf(
                SoloField("pegs", "Pegs", listOf(SoloOption("4", "4", "4 pegs"), SoloOption("5", "5", "5 pegs")), "4"),
                SoloField("colours", "Colours", listOf(SoloOption("6", "6", "6 colours"), SoloOption("7", "7", "7 colours")), "6"),
                SoloField("repeats", "Repeats", listOf(SoloOption("yes", "Allowed", "repeats allowed"), SoloOption("no", "None", "no repeats")), "yes"),
            ),
            button = { v -> "${v["pegs"]} pegs · ${v["colours"]} colours" },
        ),
        GameKind.SLIDE to SoloSpec(listOf(SoloField("level", "Size", listOf(SoloOption("easy", "3 × 3"), SoloOption("medium", "4 × 4"), SoloOption("hard", "5 × 5"), SoloOption("expert", "6 × 6")), "medium")), rowLabel = "Size"),
        GameKind.BINGO to SoloSpec(
            listOf(
                SoloField("computers", "Against", computers(1..3), "1"),
                SoloField(
                    "pace", "Calls", listOf(SoloOption("relaxed", "Relaxed", "relaxed calls"), SoloOption("normal", "Normal", "normal calls"), SoloOption("fast", "Fast", "fast calls")), "normal",
                    note = { v -> mapOf("relaxed" to "The next number comes when you tap Next.", "normal" to "Every 3.5 seconds.", "fast" to "Every 2.5 seconds.")[v["pace"]] },
                ),
            ),
        ),
        GameKind.GOLF to SoloSpec(
            listOf(
                SoloField(
                    "level", "Holes", listOf(SoloOption("easy", "Easy", "Easy holes"), SoloOption("medium", "Medium", "Medium holes"), SoloOption("hard", "Hard", "Hard holes")), "easy",
                    note = { v -> mapOf("easy" to "One island.", "medium" to "Two islands, sticker spots.", "hard" to "Three islands, more sticker spots.")[v["level"]] },
                ),
                SoloField("computers", "Computers", computers(0..3, none = true), "1"),
                SoloField("skill", "How good", SKILLS, "medium", shownWhen = { it["computers"] != "0" }),
            ),
            button = { v -> v["level"].orEmpty().replaceFirstChar { it.uppercase() } },
        ),
        GameKind.BLOCKS to SoloSpec(
            listOf(
                SoloField("opponents", "Computers", computers(1..3), "1"),
                SoloField("skill", "How good", SKILLS, "medium"),
            ),
        ),
    )

    private fun wordsLength(v: Map<String, String>) = when (v["length"]) {
        "easy" -> "4 letters"
        "hard" -> "6 letters"
        "expert" -> "7–8 letters"
        else -> "5 letters"
    }

    /** What [game] offers alone; null for a game with nothing to set up (Ship Battle places its fleet in the game; Draw & Guess has no solo game). */
    fun spec(game: GameKind): SoloSpec? = SPECS[game]

    /** The setup to use for [game]: each choice as [saved] says, or the default where it says nothing or something unknown. */
    fun resolve(game: GameKind, saved: Map<String, String>?): Map<String, String> {
        val spec = SPECS[game] ?: return emptyMap()
        return spec.fields.associate { f ->
            f.key to (saved?.get(f.key)?.takeIf { v -> f.options.any { it.value == v } } ?: f.default)
        }
    }

    /** A setup as stored: "key=value" pairs joined by ";". */
    fun encode(values: Map<String, String>): String = values.entries.joinToString(";") { "${it.key}=${it.value}" }

    /** The pairs of a stored setup; anything that is not a "key=value" pair is left out. */
    fun decode(saved: String?): Map<String, String> =
        saved.orEmpty().split(";").mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else pair.substring(0, i) to pair.substring(i + 1)
        }.toMap()
}
