package com.pippop.game.words

import kotlin.random.Random

/**
 * How good a computer finder is in Word Hunt: it claims one word every [minMs] to [maxMs] milliseconds (a fresh random gap each
 * time), by the same claim rule as a player. Easy also goes for the shorter words first.
 */
enum class HuntSkill(val key: String, val minMs: Long, val maxMs: Long) {
    EASY("easy", 25_000, 35_000),
    MEDIUM("medium", 15_000, 20_000),
    HARD("hard", 8_000, 12_000);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** What a computer finder decides: how long until its next find, and which word. */
object HuntComputer {
    /** The wait before the next find: anywhere in the skill's range. */
    fun gapMs(skill: HuntSkill, random: Random): Long = skill.minMs + random.nextLong(skill.maxMs - skill.minMs + 1)

    /** The word to claim from [unfound]: Easy takes one of the shorter half, the others any; null when nothing is left. */
    fun pick(skill: HuntSkill, unfound: List<HuntWord>, random: Random): HuntWord? {
        if (unfound.isEmpty()) return null
        val pool = if (skill == HuntSkill.EASY) unfound.sortedBy { it.length }.take((unfound.size + 1) / 2) else unfound
        return pool[random.nextInt(pool.size)]
    }
}
