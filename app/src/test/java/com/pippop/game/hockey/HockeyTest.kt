package com.pippop.game.hockey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HockeyTest {
    @Test
    fun paddlesStayInTheirHalf() {
        val bottom = HockeyTable.clampPaddle(Side.BOTTOM, 2f, 0.1f)
        assertEquals(Hockey.W - Hockey.PADDLE_R, bottom.x, 1e-6f)
        assertTrue(bottom.y > Hockey.H / 2)
        val top = HockeyTable.clampPaddle(Side.TOP, -1f, 1.5f)
        assertTrue(top.x >= Hockey.PADDLE_R && top.y < Hockey.H / 2)
    }

    @Test
    fun aStruckPuckFliesAndStaysOnTheTable() {
        val table = HockeyTable()
        // Swing the bottom paddle up through the puck waiting at the centre.
        table.moveBottom(Hockey.W / 2, Hockey.H / 2 + 0.05f)
        repeat(12) { table.step(1 / 60f) }
        val puck = table.frame.puck
        assertTrue("the puck moves up after the hit", puck.vy < 0)
        repeat(600) {
            table.step(1 / 60f)
            val p = table.frame.puck
            assertTrue(p.x in 0f..Hockey.W)
        }
    }

    @Test
    fun computersScoreGoalsAndScoresAddUp() {
        val table = HockeyTable()
        val fast = HockeyComputer(HockeyComputer.SPEEDS[2])
        val slow = HockeyComputer(HockeyComputer.SPEEDS[0])
        var goals = 0
        repeat(60 * 600) {
            if (goals >= 3) return@repeat
            val (tx, ty) = fast.move(table.frame, 1 / 60f)
            table.moveTop(tx, ty)
            // The slow computer plays the bottom end through the flipped view.
            val (bx, by) = slow.move(table.frame.flipped(), 1 / 60f)
            table.moveBottom(Hockey.W - bx, Hockey.H - by)
            if (table.step(1 / 60f) != null) goals++
        }
        assertTrue("goals are scored ($goals)", goals >= 1)
        assertEquals(goals, table.frame.bottomScore + table.frame.topScore)
    }

    @Test
    fun flippingTwiceGivesTheSameTable() {
        val f = HockeyFrame(Body(0.2f, 0.3f, 1f, -1f), Body(0.5f, 1.4f), Body(0.4f, 0.2f), 2, 5)
        val back = f.flipped().flipped()
        assertEquals(f.puck.x, back.puck.x, 1e-5f)
        assertEquals(f.puck.y, back.puck.y, 1e-5f)
        assertEquals(f.bottom.y, back.bottom.y, 1e-5f)
        assertEquals(5, f.flipped().bottomScore)
    }
}
