package com.pippop.game.dots

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DotsShapeTest {
    @Test
    fun theUsualBoardKeepsItsNumbers() {
        val g = DotsShape.from("4x4")
        assertEquals(40, g.lines)
        assertEquals(listOf(0, 4, 20, 21), g.boxLines(0))
        assertEquals(listOf(15, 19, 38, 39), g.boxLines(15))
    }

    @Test
    fun everyBoardPlaysToTheEndAgainstTheComputer() {
        for (shape in DotsShape.ALL) {
            for (l in 0 until shape.lines) for (b in shape.lineBoxes(l)) assertTrue(l in shape.boxLines(b))
            val m = DotsMatch(shape)
            val cpu = DotsComputer(Random(3), shape)
            var mark = 'h'
            var over = false
            repeat(shape.lines) {
                if (over) return@repeat
                val r = m.play(mark, JSONObject().put("line", cpu.move(m.lines)))
                over = r.over
                if (!r.again) mark = if (mark == 'h') 'g' else 'h'
            }
            assertTrue(shape.key, over)
            assertFalse('.' in m.boxes)
        }
    }

    @Test
    fun theHeartHasGapsWithoutLines() {
        val m = DotsMatch(DotsShape.from("heart"))
        assertEquals(' ', m.boxes[0])
        assertEquals(' ', m.lines[0])
        assertThrows(IllegalArgumentException::class.java) { m.play('h', JSONObject().put("line", 0)) }
    }
}
