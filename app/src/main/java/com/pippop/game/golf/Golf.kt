package com.pippop.game.golf

import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A point on the course; the course is [Golf.W] wide and [Golf.H] long, y down. */
data class Pt(val x: Double, val y: Double)

data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    operator fun contains(p: Pt) = p.x in x0..x1 && p.y in y0..y1
}

/**
 * One hole: where you tee off, the cup, the walls you bounce off and the sand that slows you.
 * [cuts] are bites out of the course's edge (so it isn't a plain rectangle): the ball bounces
 * off them just like walls, but they are drawn as the sky beyond the course.
 */
data class Hole(
    val par: Int,
    val tee: Pt,
    val cup: Pt,
    val walls: List<Box> = emptyList(),
    val sand: List<Box> = emptyList(),
    val cuts: List<Box> = emptyList(),
) {
    /** Everything the ball bounces off. */
    val solid get() = walls + cuts
}

/** A shot's path (about 60 points a second) and whether it dropped in. */
data class GolfShot(val path: List<Pt>, val holed: Boolean) {
    val end get() = path.last()
}

/**
 * Mini Golf's courses and physics, all on the phone: the player shooting works the shot out
 * and sends where it stopped; the other phones replay it with [simulate] (the same numbers
 * give the same path). The server keeps only strokes and turns (golf.js).
 */
object Golf {
    const val W = 1.0
    const val H = 1.6
    const val R = 0.022
    const val CUP = 0.04
    const val MAX_SPEED = 2.6
    private const val DT = 1.0 / 120
    /** Faster than this, the ball rolls over the cup. */
    private const val DROP_SPEED = 0.9
    const val MAX_STROKES = 6

    val HOLES = listOf(
        Hole(2, Pt(0.5, 1.4), Pt(0.5, 0.25)),
        Hole(2, Pt(0.5, 1.4), Pt(0.5, 0.25), walls = listOf(Box(0.3, 0.72, 0.7, 0.84))),
        Hole(3, Pt(0.2, 1.4), Pt(0.2, 0.25), cuts = listOf(Box(0.0, 0.78, 0.66, 0.88))),
        Hole(3, Pt(0.8, 1.42), Pt(0.8, 0.22), cuts = listOf(Box(0.38, 1.0, 1.0, 1.08), Box(0.0, 0.52, 0.62, 0.6))),
        Hole(2, Pt(0.5, 1.4), Pt(0.5, 0.28), sand = listOf(Box(0.22, 0.42, 0.78, 0.66))),
        Hole(2, Pt(0.5, 1.4), Pt(0.5, 0.25), cuts = listOf(Box(0.0, 0.76, 0.41, 0.84), Box(0.59, 0.76, 1.0, 0.84))),
        Hole(
            3, Pt(0.5, 1.42), Pt(0.5, 0.2),
            walls = listOf(
                Box(0.15, 0.45, 0.27, 0.57), Box(0.44, 0.65, 0.56, 0.77), Box(0.73, 0.45, 0.85, 0.57),
                Box(0.29, 0.95, 0.41, 1.07), Box(0.59, 0.95, 0.71, 1.07),
            ),
        ),
        Hole(3, Pt(0.22, 1.4), Pt(0.78, 1.4), cuts = listOf(Box(0.45, 0.5, 0.55, 1.6))),
        Hole(
            4, Pt(0.15, 1.45), Pt(0.2, 0.25),
            cuts = listOf(Box(0.0, 1.05, 0.7, 1.12), Box(0.3, 0.6, 1.0, 0.67)),
            sand = listOf(Box(0.45, 0.12, 0.95, 0.45)),
        ),
    )

    /** Rolls a ball from [from] at [angle] (radians, 0 = right, y down) with [power] (0 to 1). */
    fun simulate(hole: Hole, from: Pt, angle: Double, power: Double): GolfShot {
        var x = from.x
        var y = from.y
        var vx = cos(angle) * power * MAX_SPEED
        var vy = sin(angle) * power * MAX_SPEED
        val path = mutableListOf(from)
        for (step in 0 until 12 * 120) {
            val inSand = hole.sand.any { Pt(x, y) in it }
            val sp = hypot(vx, vy)
            val dec = (if (inSand) 3.2 else 0.55) * DT + sp * (if (inSand) 2.5 else 0.45) * DT
            if (sp <= dec || sp < 0.03) break
            val k = (sp - dec) / sp
            vx *= k
            vy *= k
            x += vx * DT
            y += vy * DT
            if (x < R) { x = R; vx = abs(vx) * 0.75 }
            if (x > W - R) { x = W - R; vx = -abs(vx) * 0.75 }
            if (y < R) { y = R; vy = abs(vy) * 0.75 }
            if (y > H - R) { y = H - R; vy = -abs(vy) * 0.75 }
            for (w in hole.solid) {
                val cx = min(max(x, w.x0), w.x1)
                val cy = min(max(y, w.y0), w.y1)
                val d = hypot(x - cx, y - cy)
                if (d >= R) continue
                val nx: Double
                val ny: Double
                if (d == 0.0) {
                    // The centre got inside: out by the nearest side.
                    val sides = listOf(Triple(x - w.x0, -1.0, 0.0), Triple(w.x1 - x, 1.0, 0.0), Triple(y - w.y0, 0.0, -1.0), Triple(w.y1 - y, 0.0, 1.0))
                    val side = sides.minBy { it.first }
                    nx = side.second
                    ny = side.third
                    if (nx < 0) x = w.x0 - R else if (nx > 0) x = w.x1 + R
                    if (ny < 0) y = w.y0 - R else if (ny > 0) y = w.y1 + R
                } else {
                    nx = (x - cx) / d
                    ny = (y - cy) / d
                    x = cx + nx * R
                    y = cy + ny * R
                }
                val vn = vx * nx + vy * ny
                if (vn < 0) {
                    vx -= 1.75 * vn * nx
                    vy -= 1.75 * vn * ny
                }
            }
            if (hypot(x - hole.cup.x, y - hole.cup.y) < CUP && hypot(vx, vy) < DROP_SPEED) {
                path += hole.cup
                return GolfShot(path, holed = true)
            }
            if (step % 2 == 1) path += Pt(x, y)
        }
        path += Pt(x, y)
        return GolfShot(path, holed = false)
    }

    private const val G = 0.02

    /** How far each spot is from the cup on foot, going round the walls (for the computer). */
    fun distanceField(hole: Hole): (Pt) -> Double {
        val cols = (W / G).roundToInt()
        val rows = (H / G).roundToInt()
        val d = DoubleArray(cols * rows) { Double.POSITIVE_INFINITY }
        fun blocked(c: Int, r: Int): Boolean {
            val x = (c + 0.5) * G
            val y = (r + 0.5) * G
            return hole.solid.any { x > it.x0 - R && x < it.x1 + R && y > it.y0 - R && y < it.y1 + R }
        }
        val start = floor(hole.cup.y / G).toInt() * cols + floor(hole.cup.x / G).toInt()
        d[start] = 0.0
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        queue += 0.0 to start
        while (queue.isNotEmpty()) {
            val (di, i) = queue.poll()!!
            if (di > d[i]) continue
            val c = i % cols
            val r = i / cols
            for (dr in -1..1) for (dc in -1..1) {
                val nc = c + dc
                val nr = r + dr
                if ((dr == 0 && dc == 0) || nc < 0 || nr < 0 || nc >= cols || nr >= rows || blocked(nc, nr)) continue
                val j = nr * cols + nc
                val nd = di + G * hypot(dr.toDouble(), dc.toDouble())
                if (nd < d[j]) {
                    d[j] = nd
                    queue += nd to j
                }
            }
        }
        return { p ->
            val c = floor(p.x / G).toInt().coerceIn(0, cols - 1)
            val r = floor(p.y / G).toInt().coerceIn(0, rows - 1)
            d[r * cols + c]
        }
    }

    /**
     * The computer's shot from [from]: the best of a spread of angles and strengths, then a
     * wobble on aim and strength that grows as [skill] (0 to 1) drops.
     */
    fun computerShot(hole: Hole, from: Pt, skill: Double, field: (Pt) -> Double = distanceField(hole), random: Random = Random.Default): Pair<Double, Double> {
        var bestAngle = 0.0
        var bestPower = 0.5
        var bestScore = Double.POSITIVE_INFINITY
        for (a in 0 until 96) for (p in 1..12) {
            val angle = a / 96.0 * 2 * PI
            val power = p / 12.0
            val shot = simulate(hole, from, angle, power)
            val score = if (shot.holed) -1 + power * 0.01 else field(shot.end)
            if (score < bestScore) {
                bestScore = score
                bestAngle = angle
                bestPower = power
            }
        }
        val aim = 0.03 + (1 - skill) * 0.12
        val strength = 0.06 + (1 - skill) * 0.25
        return (bestAngle + (random.nextDouble() * 2 - 1) * aim) to (bestPower * (1 + (random.nextDouble() * 2 - 1) * strength)).coerceIn(0.05, 1.0)
    }
}
