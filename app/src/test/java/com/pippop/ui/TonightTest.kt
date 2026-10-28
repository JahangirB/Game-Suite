package com.pippop.ui

import com.pippop.ui.screens.TALLY_ME
import com.pippop.ui.screens.tonightLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonightTest {
    @Test
    fun twoPlayersReadAsAScore() {
        assertEquals("Tonight: You 3 – 2 Alex", tonightLine(mapOf(TALLY_ME to 3, "Alex" to 2), listOf("Alex")))
        assertEquals("Tonight: You 0 – 1 Alex", tonightLine(mapOf("Alex" to 1), listOf("Alex")))
    }

    @Test
    fun moreListEveryone() {
        assertEquals("Tonight: You 1 · Alex 0 · Sam 2", tonightLine(mapOf(TALLY_ME to 1, "Sam" to 2), listOf("Alex", "Sam")))
    }

    @Test
    fun nothingBeforeTheFirstWin() {
        assertNull(tonightLine(emptyMap(), listOf("Alex")))
    }
}
