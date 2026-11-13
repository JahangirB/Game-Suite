package com.pippop.ui.art

import com.pippop.data.GameKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every game's rules are four to six short points, the first of them the goal. */
class GameRulesTest {
    private fun words(s: String) = s.trim().split(Regex("\\s+")).size

    @Test fun everyGamesRulesKeepToTheLimits() {
        assertEquals(20, GameKind.values().size)
        for (kind in GameKind.values()) {
            val r = kind.rules
            assertTrue("$kind: ${r.size} points", r.size in 4..6)
            assertEquals("$kind: first point", "Goal", r.first().title)
            r.forEach {
                assertTrue("$kind: title '${it.title}'", words(it.title) <= 3)
                assertTrue("$kind: body of '${it.title}' is ${words(it.body)} words", words(it.body) <= 30)
            }
            val total = r.sumOf { words(it.title) + words(it.body) }
            assertTrue("$kind: $total words in all", total <= 120)
        }
    }

    @Test fun miniGolfIsThePrototypesSixPoints() {
        assertEquals(
            listOf(
                RulePoint("Goal", "Nine holes. The fewest strokes wins."),
                RulePoint("Aim", "Swipe to turn round your ball. Pinch to see the whole hole; double-tap faces the flag."),
                RulePoint("Shoot", "Slide up the bar on the right and let go. Low is a roll; high is a jump, over gaps and walls."),
                RulePoint("Trouble", "Off an island, or stopped on a pond: back to the tee. Sand slows a lot, grass a little."),
                RulePoint("Sticker spots", "Kylian takes your next turn. Fire sends you to the tee. Stonks throws you up the course. Portals swap you over. Hmm. Sure. hides your aim line."),
                RulePoint("Ending a hole", "Once someone is in, the rest get three more shots. A ball still out scores one more."),
            ),
            GameKind.GOLF.rules,
        )
    }
}
