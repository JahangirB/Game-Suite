package com.pippop.game.words

import kotlin.random.Random

/** How well the computer plays Word Ladder. */
enum class LadderLevel(val key: String) {
    EASY("easy"), MEDIUM("medium"), HARD("hard");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/**
 * The computer's side of Word Ladder against one player. It plays by the same rules (the moves come from
 * [WordLadder.legalMoves]); a level only changes which legal move or secret word it picks. Needs the word lists loaded.
 */
object LadderComputer {
    /** A word one letter from [target] hands the next player the win, unless it is the target itself. */
    fun nextToTarget(word: String, target: String) = word.length == target.length && word.indices.count { word[it] != target[it] } == 1

    /**
     * The computer's secret word, a ladder word of the player's word's length that a chain of real words joins to [rival].
     * Easy takes any such word; Medium and Hard take one 3 to 6 steps away (the nearest to that, when the lists have none).
     */
    fun pick(level: LadderLevel, rival: String, random: Random = Random.Default): String {
        val away = WordLadder.distancesFrom(rival).filter { (w, d) ->
            d >= 1 && w.length == rival.length && w !in WordLadder.blocklist
        }
        if (away.isEmpty()) return rival
        if (level == LadderLevel.EASY) return away.keys.sorted().random(random)
        val fair = away.filterValues { it in 3..6 }
        val pool = fair.ifEmpty {
            val nearest = away.values.minOf { kotlin.math.abs(it - 4) }
            away.filterValues { kotlin.math.abs(it - 4) == nearest }
        }
        return pool.keys.sorted().random(random)
    }

    /**
     * The computer's move after [chain] (the last word is the one in play), or null when it has none (it then loses).
     * Easy: any legal move. Medium: the target if it can spell it, else a move that does not land next to the target when it can.
     * Hard: the same, and a move that leaves the other player no move at all wins too; otherwise, of the safe moves, the one nearest
     * the target by the fewest-changes distance (ties broken at random).
     */
    fun move(level: LadderLevel, chain: List<String>, target: String, random: Random = Random.Default): String? {
        val legal = WordLadder.legalMoves(chain).sorted()
        if (legal.isEmpty()) return null
        if (level == LadderLevel.EASY) return legal.random(random)
        if (target in legal) return target
        if (level == LadderLevel.HARD) {
            legal.filter { WordLadder.legalMoves(chain + it).isEmpty() }.takeIf { it.isNotEmpty() }?.let { return it.random(random) }
        }
        val safe = legal.filter { !nextToTarget(it, target) }
        // Every move hands over the win: nothing to choose between.
        if (safe.isEmpty()) return legal.random(random)
        if (level == LadderLevel.MEDIUM) return safe.random(random)
        val distance = WordLadder.distancesFrom(target)
        val nearest = safe.minOf { distance[it] ?: Int.MAX_VALUE }
        return safe.filter { (distance[it] ?: Int.MAX_VALUE) == nearest }.random(random)
    }
}
