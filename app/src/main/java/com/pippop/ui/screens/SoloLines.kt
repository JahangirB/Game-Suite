package com.pippop.ui.screens

import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.SoloKey
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecord
import com.pippop.game.dots.DotsShape
import com.pippop.game.slide.SlideLevel
import com.pippop.game.words.WordLevel

/**
 * One game's line on the Solo tab: its [best] ("6:42", "3 tries"), what it was on ([detail]: the
 * level, size or setting in words) and, when other levels of the same game have a best too,
 * [others] ("Easy 4:05 · Medium 8:12"), so no best is hidden.
 */
data class SoloLine(val game: GameKind, val best: String, val detail: String, val others: String?) {
    /** What a screen reader says: the whole line, in words. */
    val spoken get() = "${game.label}, best $best, $detail" + if (others != null) ". Also $others" else ""
}

/** Turns the solo records into the lines of the Solo tab. */
object SoloLines {
    /** Bests that sit beside a game's main one rather than being it. */
    private val SECOND = setOf(SoloMetric.SOLVED_IN_ROW, SoloMetric.LONGEST_WORD)

    /** One line per game that has a best, the most recently set first. Made-up and real records alike. */
    fun of(records: Map<SoloKey, SoloRecord>): List<SoloLine> {
        val withBest = records.filterValues { it.best != null && it.best > 0 }
        return withBest.keys.groupBy { it.game }.mapNotNull { (game, keys) ->
            val main = keys.filter { it.metric !in SECOND }.ifEmpty { keys }
            // Newest first; bests kept before the store knew when (at 0) come after, the harder level first.
            val ordered = main.sortedWith(compareByDescending<SoloKey> { records.getValue(it).at }.thenByDescending { it.level })
            val head = ordered.firstOrNull() ?: return@mapNotNull null
            val second = keys.filter { it.metric in SECOND && it.level == head.level && it != head }
            val played = records.getValue(head).played
            val first = levelWords(head) + if (game == GameKind.SUDOKU && played > 0) " · best of $played solved" else ""
            val detail = (listOf(first) + second.map { secondWords(it, records.getValue(it).best ?: 0) }).filter { it.isNotEmpty() }.joinToString(" · ")
            val others = ordered.drop(1).filter { it.metric == head.metric }.sortedBy { levelOrder(it) }
                .joinToString(" · ") { "${levelWords(it)}: ${bestText(it.metric, records.getValue(it).best ?: 0)}" }
            game to (records.getValue(head).at to SoloLine(game, bestText(head.metric, records.getValue(head).best ?: 0), detail, others.ifEmpty { null }))
        }.sortedWith(compareByDescending<Pair<GameKind, Pair<Long, SoloLine>>> { it.second.first }.thenBy { it.first.ordinal }).map { it.second.second }
    }

    private fun levelOrder(k: SoloKey) = when (k.game) {
        GameKind.SUDOKU -> Difficulty.entries.indexOfFirst { it.key == k.level }
        else -> 0
    }

    private fun count(n: Long, one: String, many: String) = "$n ${if (n == 1L) one else many}"

    private fun time(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** A best said in words: "6:42", "3 tries", "9 discs". */
    fun bestText(metric: SoloMetric, v: Long): String = when (metric) {
        SoloMetric.TIME -> time(v)
        SoloMetric.SHOTS -> count(v, "shot", "shots")
        SoloMetric.DISCS -> count(v, "disc", "discs")
        SoloMetric.BOXES_AHEAD -> "$v ${if (v == 1L) "box" else "boxes"} ahead"
        SoloMetric.FLIPS -> count(v, "flip", "flips")
        SoloMetric.TRIES -> count(v, "try", "tries")
        SoloMetric.GUESSES -> count(v, "guess", "guesses")
        SoloMetric.STROKES -> count(v, "stroke", "strokes")
        SoloMetric.BALLS -> count(v, "ball", "balls")
        SoloMetric.GOALS_AHEAD -> count(v, "goal", "goals") + " ahead"
        SoloMetric.POINTS_AHEAD -> count(v, "point", "points") + " ahead"
        SoloMetric.POINTS -> count(v, "point", "points")
        SoloMetric.LONGEST_WORD -> count(v, "letter", "letters")
        SoloMetric.SQUARES -> count(v, "square", "squares")
        SoloMetric.WINS_IN_ROW -> if (v == 1L) "1 win" else "$v in a row"
        SoloMetric.SOLVED_IN_ROW -> if (v == 1L) "1 solved" else "$v in a row"
    }

    private fun secondWords(k: SoloKey, v: Long) = when (k.metric) {
        SoloMetric.SOLVED_IN_ROW -> "$v solved in a row"
        SoloMetric.LONGEST_WORD -> "Longest word: ${count(v, "letter", "letters")}"
        else -> ""
    }

    private fun computers(level: String) = when (val n = level.toIntOrNull()) {
        null -> ""
        0 -> "On your own"
        1 -> "1 computer"
        else -> "$n computers"
    }

    private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }

    /** What a record was on, in words people use: "Medium", "16 cards", "4 computers". */
    fun levelWords(k: SoloKey): String = when (k.game) {
        GameKind.SUDOKU -> Difficulty.entries.firstOrNull { it.key == k.level }?.label ?: cap(k.level)
        GameKind.SHIPS -> "Fewest shots to sink the fleet"
        GameKind.CONNECT4 -> if (k.level.isEmpty()) "Quickest win" else "Quickest win against ${cap(k.level)}"
        GameKind.DOTS -> if (k.level.isEmpty()) "" else DotsShape.label(k.level) + " board"
        GameKind.MEMORY -> if (k.level.isEmpty()) "" else "${k.level} cards"
        GameKind.WORDS -> if (k.level == "daily") "Daily word" else WordLevel.entries.firstOrNull { it.key == k.level }
            ?.let { "${it.lengths.first()}${if (it.lengths.size > 1) "–${it.lengths.last()}" else ""} letters" } ?: cap(k.level)
        GameKind.TTT -> cap(k.level)
        GameKind.DOMINOES, GameKind.CLASH, GameKind.LUDO, GameKind.BLOCKS, GameKind.BINGO -> computers(k.level)
        GameKind.NUMBERS, GameKind.HOCKEY, GameKind.GOLF -> cap(k.level)
        GameKind.GRID -> cap(k.level)
        GameKind.BALLOON -> "Words solved in a row"
        GameKind.CODE -> k.level.split("-").let { p ->
            if (p.size == 3) "${p[0]} pegs · ${p[1]} colours" + if (p[2] == "repeats") " · repeats" else "" else k.level
        }
        GameKind.SLIDE -> SlideLevel.entries.firstOrNull { it.key == k.level }?.let { "${it.size} × ${it.size}" } ?: cap(k.level)
        GameKind.DRAW -> ""
    }
}
