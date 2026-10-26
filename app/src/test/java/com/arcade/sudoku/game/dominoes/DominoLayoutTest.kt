package com.arcade.sudoku.game.dominoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DominoLayoutTest {
    @Test
    fun tilesNeverOverlapAndEachTouchesTheNext() {
        val r = Random(1)
        repeat(2000) {
            val n = 1 + r.nextInt(28)
            var v = r.nextInt(7)
            val line = List(n) { val a = v; val b = if (r.nextInt(5) == 0) a else r.nextInt(7); v = b; Tile(a, b) }
            val placed = DominoLayout.layout(line, r.nextInt(n))
            assertEquals(n, placed.size)
            for (i in placed.indices) for (j in i + 1 until placed.size) {
                val p = placed[i]
                val q = placed[j]
                val ox = minOf(p.x + p.w, q.x + q.w) - maxOf(p.x, q.x)
                val oy = minOf(p.y + p.h, q.y + q.h) - maxOf(p.y, q.y)
                assertFalse("tiles $i and $j overlap", ox > 0.001f && oy > 0.001f)
            }
            for (i in 0 until n - 1) {
                val p = placed[i]
                val q = placed[i + 1]
                val gx = maxOf(p.x, q.x) - minOf(p.x + p.w, q.x + q.w)
                val gy = maxOf(p.y, q.y) - minOf(p.y + p.h, q.y + q.h)
                assertTrue("tiles $i and ${i + 1} don't touch", gx <= 0.001f && gy <= 0.001f)
            }
        }
    }

    @Test
    fun theFirstTileIsInTheMiddleAndDoublesLieAcross() {
        val line = listOf(Tile(1, 3), Tile(3, 3), Tile(3, 5))
        val placed = DominoLayout.layout(line, start = 1)
        // The double in the middle stands across the line.
        assertEquals(PlacedTile(-0.5f, -1f, 1f, 2f, 3, 3), placed[1])
        assertEquals(PlacedTile(-2.5f, -0.5f, 2f, 1f, 1, 3), placed[0])
        assertEquals(PlacedTile(0.5f, -0.5f, 2f, 1f, 3, 5), placed[2])
    }
}
