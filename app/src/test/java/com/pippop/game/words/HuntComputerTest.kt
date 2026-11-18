package com.pippop.game.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** What a computer finder decides: its gap between finds (by skill) and which word it goes for. */
class HuntComputerTest {
    @Test fun eachSkillWaitsWithinItsRangeWithJitter() {
        val r = Random(1)
        val ranges = mapOf(HuntSkill.EASY to 25_000L..35_000L, HuntSkill.MEDIUM to 15_000L..20_000L, HuntSkill.HARD to 8_000L..12_000L)
        for ((skill, range) in ranges) {
            val gaps = List(300) { HuntComputer.gapMs(skill, r) }
            assertTrue("$skill: $gaps", gaps.all { it in range })
            assertTrue("$skill has jitter", gaps.toSet().size > 50)
            assertTrue("$skill uses the whole range", gaps.min() < range.first + 1500 && gaps.max() > range.last - 1500)
        }
        assertEquals(HuntSkill.MEDIUM, HuntSkill.from(null))
    }

    private fun words(vararg w: String) = w.mapIndexed { i, text -> HuntWord(text, i, 0, HuntDir.RIGHT) }

    @Test fun easyTakesTheShorterWordsHardAndMediumAnyOfThem() {
        val unfound = words("ANT", "CAT", "TIGER", "CAMEL", "ELEPHANT", "GIRAFFE")
        val r = Random(2)
        val easy = List(200) { HuntComputer.pick(HuntSkill.EASY, unfound, r)!!.word }.toSet()
        assertEquals(setOf("ANT", "CAT", "TIGER"), easy)
        for (skill in listOf(HuntSkill.MEDIUM, HuntSkill.HARD)) {
            assertEquals(unfound.map { it.word }.toSet(), List(300) { HuntComputer.pick(skill, unfound, r)!!.word }.toSet())
        }
        assertEquals("ANT", HuntComputer.pick(HuntSkill.EASY, words("ANT"), r)!!.word)
        assertNull(HuntComputer.pick(HuntSkill.EASY, emptyList(), r))
    }
}
