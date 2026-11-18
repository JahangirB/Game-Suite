package com.pippop.game.words

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** How each level of the Word Ladder computer chooses, in fixed positions, and that it always has a move when one exists. */
class LadderComputerTest {
    private fun read(name: String) = java.io.File("src/main/assets/words/$name.txt").readLines().filter { it.isNotBlank() }

    @Before fun realWords() {
        WordList.install(read("answers"), read("allowed"))
        WordLadder.install(read("ladder"), read("blocklist"))
    }

    @After fun restore() = realWords()

    private val chain = listOf("COLD", "CORD", "CARD")
    private val target = "WARM"

    private fun picks(level: LadderLevel, chain: List<String>, target: String, seeds: Int = 60) =
        (0 until seeds).mapNotNull { LadderComputer.move(level, chain, target, Random(it)) }.toSet()

    @Test fun theFixedPositionReallyOffersTheTrap() {
        val legal = WordLadder.legalMoves(chain)
        assertTrue("WARD" in legal)
        assertTrue(LadderComputer.nextToTarget("WARD", target))
        assertFalse(LadderComputer.nextToTarget("CART", target))
        assertTrue(legal.size > 3)
    }

    @Test fun easyPlaysAnyLegalMoveIncludingTheTrap() {
        val seen = picks(LadderLevel.EASY, chain, target, 400)
        assertTrue(WordLadder.legalMoves(chain).containsAll(seen))
        assertTrue("WARD" in seen)
        assertTrue(seen.size > 3)
    }

    @Test fun mediumNeverHandsOverTheWin() {
        val seen = picks(LadderLevel.MEDIUM, chain, target, 400)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.none { LadderComputer.nextToTarget(it, target) })
    }

    @Test fun mediumAndHardTakeTheWinWhenTheyHaveOne() {
        val next = listOf("COLD", "CORD", "CARD", "WARD")
        for (level in listOf(LadderLevel.MEDIUM, LadderLevel.HARD)) assertEquals(setOf("WARM"), picks(level, next, target))
    }

    @Test fun hardPlaysTheSafeMoveNearestTheTarget() {
        val seen = picks(LadderLevel.HARD, chain, target)
        val safe = WordLadder.legalMoves(chain).filter { !LadderComputer.nextToTarget(it, target) }
        val distance = WordLadder.distancesFrom(target)
        val nearest = safe.minOf { distance[it] ?: Int.MAX_VALUE }
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it in safe && (distance[it] ?: Int.MAX_VALUE) == nearest })
    }

    @Test fun whenEveryMoveHandsOverTheWinItStillMoves() {
        // Every neighbour of the last word is one step from the target: any legal move loses, and it plays one.
        WordList.install(listOf("take", "tame", "tape", "tale"), emptyList())
        WordLadder.install(listOf("TAKE", "TAME", "TAPE", "TALE"))
        for (level in LadderLevel.entries) {
            val move = LadderComputer.move(level, listOf("TAKE"), "TABE")
            assertNotNull(level.name, move)
        }
    }

    @Test fun hardWinsByLeavingTheOtherPlayerNoMove() {
        // From TAKE: TALE leaves them nothing (TAKE is used), BAKE leaves BARE.
        WordList.install(listOf("take", "tale", "bake", "bare"), emptyList())
        WordLadder.install(listOf("TAKE", "TALE", "BAKE", "BARE"))
        assertEquals(setOf("TALE"), picks(LadderLevel.HARD, listOf("TAKE"), "COLD"))
        // Medium has no such eye: either move is possible.
        assertEquals(setOf("TALE", "BAKE"), picks(LadderLevel.MEDIUM, listOf("TAKE"), "COLD", 100))
    }

    @Test fun noMoveIsNull() {
        WordList.install(listOf("take"), emptyList())
        WordLadder.install(listOf("TAKE"))
        for (level in LadderLevel.entries) assertNull(LadderComputer.move(level, listOf("TAKE"), "COLD"))
    }

    @Test fun easyPicksAnyJoinedWordAndTheOthersPickAFairDistance() {
        for (length in WordLadder.LENGTHS) {
            val mine = WordLadder.all.filter { it.length == length && WordLadder.neighbours(it).isNotEmpty() }.sorted()[40]
            for (level in LadderLevel.entries) for (seed in 0 until 10) {
                val theirs = LadderComputer.pick(level, mine, Random(seed))
                val d = WordLadder.distance(mine, theirs)
                assertTrue("$level $mine -> $theirs", theirs != mine && d >= 1 && theirs.length == length)
                if (level != LadderLevel.EASY && WordLadder.distancesFrom(mine).values.any { it in 3..6 }) assertTrue("$level $mine -> $theirs is $d", d in 3..6)
            }
        }
    }

    @Test fun theComputerIsNeverStuckWhileAMoveExists() {
        // Whole games against a player who moves at random: whenever it is the computer's turn and a move exists, it finds one.
        for (level in LadderLevel.entries) for (seed in 0 until 25) {
            val random = Random(seed)
            val match = LadderMatch(LadderOptions(4, 0), listOf('m', 'o')) { random.nextDouble() }
            val mine = WordLadder.all.filter { it.length == 4 && WordLadder.neighbours(it).isNotEmpty() }.sorted().random(random)
            match.setPick('m', mine)
            match.setPick('o', LadderComputer.pick(level, mine, random))
            var guard = 0
            while (!match.over && guard++ < 5000) {
                val legal = match.legalMoves()
                if (match.turn == 'o') {
                    val move = LadderComputer.move(level, match.words, match.target!!, random)
                    assertEquals("$level seed $seed", legal.isNotEmpty(), move != null)
                    assertTrue(move in legal)
                    match.play('o', move!!)
                } else {
                    match.play('m', legal.random(random))
                }
            }
            assertTrue("$level seed $seed ended", match.over)
        }
    }
}
