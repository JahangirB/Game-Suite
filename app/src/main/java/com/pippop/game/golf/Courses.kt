package com.pippop.game.golf

import kotlin.math.hypot
import kotlin.random.Random

/**
 * Mini Golf's library of [SIZE] holes. Each is built from its number, so every phone builds the
 * same hole from the same number and only the numbers travel. The number also sets how hard it
 * is: the first third are one piece of ground (easy), the next third two islands joined by a
 * bridge (medium), the last third three islands and two bridges (hard). Off the islands is the
 * drop; ponds sink a slow ball. Every hole is checked to be playable: there is a dry way round
 * the walls and over the bridges from the tee to the cup.
 */
object Courses {
    const val SIZE = 1000
    const val MEDIUM = 333
    const val HARD = 666

    private val built = HashMap<Int, Hole>()

    /** Hole number [id] (0 until [SIZE]). */
    fun hole(id: Int): Hole {
        val n = id.mod(SIZE)
        return synchronized(built) { built.getOrPut(n) { build(n) } }
    }

    /** How many islands hole [id] has: 1 easy, 2 medium, 3 hard. */
    fun islands(id: Int) = when (id.mod(SIZE)) {
        in 0 until MEDIUM -> 1
        in MEDIUM until HARD -> 2
        else -> 3
    }

    /** Nine holes for a game, as golf.js picks them: three easy, then three medium, then three hard. */
    fun pick(random: Random = Random.Default): List<Int> =
        listOf(0 until MEDIUM, MEDIUM until HARD, HARD until SIZE).flatMap { band -> band.shuffled(random).take(3) }

    private fun build(id: Int): Hole {
        for (attempt in 0 until 80) {
            val random = Random(id * 1_000_003L + attempt * 7_919L + 41)
            val hole = when (islands(id)) {
                1 -> oneIsland(random)
                else -> floating(random, islands(id))
            } ?: continue
            if (playable(hole)) return hole
        }
        return Hole(2, Pt(0.5, 1.4), Pt(0.5, 0.25))
    }

    // ---- Building -------------------------------------------------------------------------------

    private fun Random.between(a: Double, b: Double) = a + nextDouble() * (b - a)

    /** Room kept clear round the tee and the cup. */
    private const val CLEAR = 0.14

    private fun Box.grow(by: Double) = Box(x0 - by, y0 - by, x1 + by, y1 + by)

    private fun overlaps(a: Box, b: Box) = a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1

    private fun Pond.box() = Box(x - rx, y - ry, x + rx, y + ry)

    /**
     * Walls, sand and ponds scattered over [area], clear of the tee, the cup, each other and
     * [keepClear] (where bridges land).
     */
    private fun obstacles(random: Random, area: Box, tee: Pt, cup: Pt, keepClear: List<Box>, walls: Int, sand: Int, ponds: Int): Triple<List<Box>, List<Box>, List<Pond>> {
        val taken = keepClear.toMutableList()
        fun free(b: Box) = taken.none { overlaps(it, b) } &&
            listOf(tee, cup).none { p -> overlaps(b, Box(p.x - CLEAR, p.y - CLEAR, p.x + CLEAR, p.y + CLEAR)) }
        fun place(w: Double, h: Double, inset: Double): Box? {
            repeat(30) {
                val x0 = random.between(area.x0 + inset, area.x1 - inset - w)
                val y0 = random.between(area.y0 + inset, area.y1 - inset - h)
                val b = Box(x0, y0, x0 + w, y0 + h)
                if (x0 >= area.x0 + inset && y0 >= area.y0 + inset && b.x1 <= area.x1 - inset && b.y1 <= area.y1 - inset && free(b)) {
                    taken += b.grow(0.07)
                    return b
                }
            }
            return null
        }
        val wallList = (0 until walls).mapNotNull {
            if (random.nextBoolean()) place(random.between(0.14, 0.34), random.between(0.05, 0.09), 0.06)
            else place(random.between(0.05, 0.09), random.between(0.12, 0.26), 0.06)
        }
        val sandList = (0 until sand).mapNotNull { place(random.between(0.18, 0.4), random.between(0.1, 0.2), 0.05) }
        val pondList = (0 until ponds).mapNotNull {
            val rx = random.between(0.07, 0.15)
            val ry = random.between(0.05, 0.11)
            place(2 * rx, 2 * ry, 0.05)?.let { b -> Pond((b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2, rx, ry) }
        }
        return Triple(wallList, sandList, pondList)
    }

    /** Easy: one piece of ground, with a few walls, maybe sand, maybe a pond, maybe a bite out of a side. */
    private fun oneIsland(random: Random): Hole? {
        val tee = Pt(random.between(0.2, 0.8), random.between(1.36, 1.44))
        val cup = Pt(random.between(0.18, 0.82), random.between(0.2, 0.32))
        val cuts = if (random.nextDouble() < 0.35) {
            val y = random.between(0.6, 1.0)
            val reach = random.between(0.35, 0.6)
            listOf(if (random.nextBoolean()) Box(0.0, y, reach, y + 0.08) else Box(1.0 - reach, y, 1.0, y + 0.08))
        } else {
            emptyList()
        }
        val area = Box(0.0, 0.0, 1.0, 1.6)
        val (walls, sand, ponds) = obstacles(
            random, area, tee, cup, cuts.map { it.grow(0.06) },
            walls = random.nextInt(1, 4), sand = random.nextInt(0, 2), ponds = if (random.nextDouble() < 0.45) 1 else 0,
        )
        return Hole(2, tee, cup, walls = walls, sand = sand, cuts = cuts, water = ponds)
    }

    /**
     * Medium and hard: [count] islands stacked up the course with a gap between each, and a
     * narrow bridge over every gap. Islands don't always reach the sides, and bridges sit
     * wherever two islands overlap, so the way across changes from hole to hole.
     */
    private fun floating(random: Random, count: Int): Hole? {
        val gaps = List(count - 1) { random.between(0.13, if (count == 3) 0.22 else 0.26) }
        val room = 1.5 - gaps.sum()
        val shares = List(count) { random.between(0.85, 1.15) }
        val heights = shares.map { it / shares.sum() * room }
        if (heights.any { it < 0.3 }) return null
        // Top (the cup's island) to bottom (the tee's), y down.
        var y = 0.05
        val islands = heights.mapIndexed { i, h ->
            val narrow = count == 3 && i == 1
            val width = if (narrow) random.between(0.5, 0.75) else random.between(0.72, 1.0)
            val x0 = random.between(0.0, 1.0 - width)
            val island = Box(x0, y, x0 + width, y + h)
            y += h + gaps.getOrElse(i) { 0.0 }
            island
        }
        val bridges = (0 until count - 1).map { i ->
            val upper = islands[i]
            val lower = islands[i + 1]
            val from = maxOf(upper.x0, lower.x0) + 0.04
            val to = minOf(upper.x1, lower.x1) - 0.04
            val width = random.between(if (count == 3) 0.1 else 0.12, if (count == 3) 0.15 else 0.18)
            if (to - from < width) return null
            val x0 = random.between(from, to - width)
            // A little onto each island, so there's no seam.
            Box(x0, upper.y1 - 0.02, x0 + width, lower.y0 + 0.02)
        }
        val top = islands.first()
        val bottom = islands.last()
        val cup = Pt(random.between(top.x0 + 0.14, top.x1 - 0.14), random.between(top.y0 + 0.12, top.y0 + 0.2))
        val tee = Pt(random.between(bottom.x0 + 0.14, bottom.x1 - 0.14), random.between(bottom.y1 - 0.18, bottom.y1 - 0.12))
        if (top.x1 - top.x0 < 0.3 || bottom.x1 - bottom.x0 < 0.3) return null
        // Where the bridges land stays clear, so there's always a run-up.
        val landings = bridges.map { Box(it.x0 - 0.05, it.y0 - 0.1, it.x1 + 0.05, it.y1 + 0.1) }
        val walls = mutableListOf<Box>()
        val sand = mutableListOf<Box>()
        val ponds = mutableListOf<Pond>()
        for (island in islands) {
            val (w, s, p) = obstacles(
                random, island, tee, cup, landings + walls + sand + ponds.map { it.box() },
                walls = random.nextInt(0, if (count == 3) 2 else 3),
                sand = if (random.nextDouble() < 0.3) 1 else 0,
                ponds = if (random.nextDouble() < (if (count == 3) 0.4 else 0.5)) 1 else 0,
            )
            walls += w
            sand += s
            ponds += p
        }
        return Hole(count + 1, tee, cup, walls = walls, sand = sand, water = ponds, islands = islands, bridges = bridges)
    }

    // ---- Checking -------------------------------------------------------------------------------

    private const val G = 0.02

    /**
     * Whether a ball can get from the tee to the cup over dry ground: on the course, out of the
     * water, round the walls, with room for the ball on every side (so bridges are wide enough).
     */
    fun playable(hole: Hole): Boolean {
        val cols = (Golf.W / G).toInt()
        val rows = (Golf.H / G).toInt()
        val r = Golf.R
        fun open(c: Int, row: Int): Boolean {
            val p = Pt((c + 0.5) * G, (row + 0.5) * G)
            if (p.x < r || p.x > Golf.W - r || p.y < r || p.y > Golf.H - r) return false
            val around = listOf(p, Pt(p.x - r, p.y), Pt(p.x + r, p.y), Pt(p.x, p.y - r), Pt(p.x, p.y + r))
            if (around.any { !hole.onCourse(it) }) return false
            if (hole.inWater(p)) return false
            return hole.solid.none { p.x > it.x0 - r && p.x < it.x1 + r && p.y > it.y0 - r && p.y < it.y1 + r }
        }
        fun cell(p: Pt) = (p.x / G).toInt().coerceIn(0, cols - 1) to (p.y / G).toInt().coerceIn(0, rows - 1)
        val (tc, tr) = cell(hole.tee)
        val (cc, cr) = cell(hole.cup)
        if (!open(tc, tr) || !open(cc, cr)) return false
        if (hypot(hole.tee.x - hole.cup.x, hole.tee.y - hole.cup.y) < 0.5) return false
        val seen = BooleanArray(cols * rows)
        val queue = ArrayDeque<Int>()
        seen[tr * cols + tc] = true
        queue += tr * cols + tc
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val c = i % cols
            val row = i / cols
            if (c == cc && row == cr) return true
            for ((dc, dr) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nc = c + dc
                val nr = row + dr
                if (nc !in 0 until cols || nr !in 0 until rows) continue
                val j = nr * cols + nc
                if (!seen[j] && open(nc, nr)) {
                    seen[j] = true
                    queue += j
                }
            }
        }
        return false
    }
}
