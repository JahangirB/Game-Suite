package com.pippop.game.golf

import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A point on the course; the course is [Golf.W] wide and its hole's [Hole.len] long, y down. */
data class Pt(val x: Double, val y: Double)

data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    operator fun contains(p: Pt) = p.x in x0..x1 && p.y in y0..y1

    /** Whether [p] is inside this box with its corners rounded off by [r], as islands are drawn. */
    fun containsRounded(p: Pt, r: Double): Boolean {
        if (p !in this) return false
        val cx = p.x.coerceIn(x0 + r, x1 - r)
        val cy = p.y.coerceIn(y0 + r, y1 - r)
        return hypot(p.x - cx, p.y - cy) <= r
    }
}

/** A pond, an ellipse round ([x], [y]). */
data class Pond(val x: Double, val y: Double, val rx: Double, val ry: Double) {
    operator fun contains(p: Pt): Boolean {
        val dx = (p.x - x) / rx
        val dy = (p.y - y) / ry
        return dx * dx + dy * dy <= 1
    }
}

/** The sticker spots a hole can have. */
enum class ZoneType(val key: String) {
    /** Stop on it: Kylian Dictador takes your next turn. */
    KYLIAN("kylian"),
    /** Land or roll into it: back to the tee. */
    FIRE("fire"),
    /** Roll onto it: thrown up into the air along its arrow. */
    STONKS("stonks"),
    /** Roll into it: out of its other end, still rolling. */
    PORTAL("portal"),
    /** Stop on it: no aim line for the next shot. */
    EYES("eyes");

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** A sticker spot: a [box] on the grass, or for a portal a mouth [at] sending the ball [to]. */
data class Zone(val type: ZoneType, val box: Box? = null, val at: Pt? = null, val to: Pt? = null, val dir: Pt? = null) {
    operator fun contains(p: Pt) = if (type == ZoneType.PORTAL) at != null && hypot(p.x - at.x, p.y - at.y) < Golf.PORTAL else box != null && p in box
}

/** What the ball is on. */
enum class Ground { GRASS, BRIDGE, SAND, POND }

/** Where a lost ball went: the sea (off an island), a pond or the fire. All mean back to the tee. */
enum class Hazard { WATER, DROP, FIRE }

/** A roll along the ground, or a jump that flies over gaps and walls. */
enum class ShotKind(val key: String) {
    ROLL("roll"), JUMP("jump");

    companion object {
        fun from(key: String?) = if (key == JUMP.key) JUMP else ROLL
    }
}

/**
 * One hole: islands floating on the sea joined by [bridges], the tee and the cup, the low
 * [walls] a roll bounces off (a high jump clears them), sand that slows the ball, ponds, and the
 * sticker [zones].
 */
data class Hole(
    val par: Int,
    val tee: Pt,
    val cup: Pt,
    val islands: List<Box>,
    val bridges: List<Box> = emptyList(),
    val walls: List<Box> = emptyList(),
    val sand: List<Box> = emptyList(),
    val water: List<Pond> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val len: Double = Golf.H,
) {
    /** What's under [p]: null over a gap, where the ball falls into the sea. */
    fun ground(p: Pt): Ground? = when {
        water.any { p in it } -> Ground.POND
        sand.any { it.containsRounded(p, 0.03) } -> Ground.SAND
        bridges.any { p in it } -> Ground.BRIDGE
        islands.any { it.containsRounded(p, Golf.CORNER) } -> Ground.GRASS
        else -> null
    }

    fun onCourse(p: Pt) = islands.any { it.containsRounded(p, Golf.CORNER) } || bridges.any { p in it }

    fun inWater(p: Pt) = water.any { p in it }

    /** The sticker spot at [p], if any. */
    fun zoneAt(p: Pt) = zones.firstOrNull { p in it }

    /** The rocks, palms, plants and posts standing on the hole: the same on every phone (see [Props]). */
    val props: List<Prop> by lazy { Props.of(this) }

    /** What a low ball bounces off like a wall: the walls, the bridges' handrails and the Stonks ramps' sides. */
    val solids: List<Box> by lazy {
        walls + bridges.flatMap { Props.rails(it) } + zones.filter { it.type == ZoneType.STONKS }.mapNotNull { it.box }.flatMap { Props.rampSides(it) }
    }

    internal val propGrid by lazy { PropGrid(props) }
}

/**
 * The things standing on a course. Solid ones are circles the ball bounces off unless it's
 * flying higher than they stand; grass and flowers are soft: a ball pushes through and slows.
 */
enum class PropKind(val key: String, val solid: Boolean) {
    ROCK("rock", true), PALM("palm", true), MARKER("marker", true), STONE("stone", true), EYE("eye", true),
    TUFT("tuft", false), FLOWER("flower", false),
}

/** A thing at ([x], [y]): solid ones are [r] across (course units) and [h] high (world units); [seed] shapes it on screen. */
data class Prop(val kind: PropKind, val x: Double, val y: Double, val r: Double = 0.0, val h: Double = 0.0, val seed: Int = 0)

/**
 * Where a hole's props go, worked out from the hole itself with a seeded random, so every phone
 * puts the same rock in the same place and replays the same bounce. Plants, rocks and a palm
 * line each island's edges, out of the way of the tee, the cup, the walls, bridges and sticker
 * spots; the tee has its markers and a flower bed, portals their ring of stones, the eyes are
 * solid, and so are the Stonks ramp's sides (see [Hole.solids]).
 */
object Props {
    /** How thick a bridge's handrails are. */
    const val RAIL = 0.014
    /** How near a plant the ball must pass to brush it. */
    const val PLANT = 0.028

    /** A bridge's two handrails, along its long sides. */
    fun rails(b: Box): List<Box> =
        if (b.y1 - b.y0 > b.x1 - b.x0) listOf(Box(b.x0, b.y0, b.x0 + RAIL, b.y1), Box(b.x1 - RAIL, b.y0, b.x1, b.y1))
        else listOf(Box(b.x0, b.y0, b.x1, b.y0 + RAIL), Box(b.x0, b.y1 - RAIL, b.x1, b.y1))

    /** The Stonks ramp's solid sides; its front, at the bottom of the box, stays open. */
    fun rampSides(b: Box): List<Box> {
        val cx = (b.x0 + b.x1) / 2
        val half = (b.x1 - b.x0) * 0.4
        return listOf(Box(cx - half - 0.006, b.y0, cx - half + 0.002, b.y1 - 0.03), Box(cx + half - 0.002, b.y0, cx + half + 0.006, b.y1 - 0.03))
    }

    fun of(hole: Hole): List<Prop> {
        val out = mutableListOf<Prop>()
        val random = Random(((hole.tee.x * 1000).toInt() * 7919) xor ((hole.cup.y * 1000).toInt() * 104729) xor (hole.islands.size * 31) xor (hole.walls.size * 977))
        fun free(x: Double, y: Double, pad: Double = 0.0): Boolean {
            val p = Pt(x, y)
            if (hole.islands.none { it.containsRounded(p, Golf.CORNER + 0.012) && x > it.x0 + 0.008 && x < it.x1 - 0.008 && y > it.y0 + 0.008 && y < it.y1 - 0.008 }) return false
            if (hypot(x - hole.tee.x, y - hole.tee.y) < 0.12 + pad || hypot(x - hole.cup.x, y - hole.cup.y) < 0.12 + pad) return false
            if (hole.zones.any { z -> z.box?.let { x > it.x0 - 0.04 && x < it.x1 + 0.04 && y > it.y0 - 0.04 && y < it.y1 + 0.04 } ?: z.at?.let { hypot(it.x - x, it.y - y) < 0.13 } ?: false }) return false
            if (hole.bridges.any { x > it.x0 - 0.05 && x < it.x1 + 0.05 && y > it.y0 - 0.08 && y < it.y1 + 0.08 }) return false
            if (hole.walls.any { x > it.x0 - 0.03 && x < it.x1 + 0.03 && y > it.y0 - 0.03 && y < it.y1 + 0.03 }) return false
            if (hole.sand.any { x > it.x0 - 0.02 && x < it.x1 + 0.02 && y > it.y0 - 0.02 && y < it.y1 + 0.02 }) return false
            if (hole.water.any { Pond(it.x, it.y, it.rx + 0.03, it.ry + 0.03).contains(p) }) return false
            return out.none { hypot(it.x - x, it.y - y) < 0.022 }
        }
        var seed = 1
        for (b in hole.islands) {
            // Along the edges: grass tufts, flowers and the odd rock.
            val count = ((b.x1 - b.x0 + b.y1 - b.y0) * 2 * 8).toInt()
            repeat(count) {
                val side = random.nextInt(4)
                val t = random.nextDouble()
                val inset = 0.014 + random.nextDouble() * 0.028
                val x = when (side) { 0 -> b.x0 + inset; 1 -> b.x1 - inset; else -> b.x0 + 0.05 + t * (b.x1 - b.x0 - 0.1) }
                val y = when (side) { 2 -> b.y0 + inset; 3 -> b.y1 - inset; else -> b.y0 + 0.05 + t * (b.y1 - b.y0 - 0.1) }
                val kind = random.nextDouble()
                val size = 0.08 + random.nextDouble() * 0.1
                if (!free(x, y)) return@repeat
                out += when {
                    kind < 0.55 -> Prop(PropKind.TUFT, x, y, seed = seed++)
                    kind < 0.85 -> Prop(PropKind.FLOWER, x, y, seed = seed++)
                    else -> Prop(PropKind.ROCK, x, y, r = size * 0.95 / 10, h = 0.3, seed = seed++)
                }
            }
            // A palm in one corner, a pile of rocks in the opposite one.
            val px = b.x1 - 0.06
            val py = b.y0 + 0.08
            if (free(px, py, 0.05)) out += Prop(PropKind.PALM, px, py, r = 0.017, h = 9.0, seed = seed++)
            val rx = b.x0 + 0.07
            val ry = b.y1 - 0.06
            if (free(rx, ry, 0.05)) repeat(4) {
                val size = 0.1 + random.nextDouble() * 0.14
                val ox = (random.nextDouble() - 0.5) * 0.045
                val oy = (random.nextDouble() - 0.5) * 0.045
                out += Prop(PropKind.ROCK, rx + ox, ry + oy, r = size * 0.95 / 10, h = 0.4, seed = seed++)
            }
        }
        // A flower bed beside the tee, on whichever side has room.
        for (side in listOf(1, -1)) {
            val fx = hole.tee.x + side * 0.2
            if ((0 until 9).all { i -> free(fx + (i % 3) * 0.035 - 0.035, hole.tee.y + (i / 3) * 0.022 - 0.022, -0.1) }) {
                for (i in 0 until 9) out += Prop(PropKind.FLOWER, fx + (i % 3) * 0.035 - 0.035, hole.tee.y + (i / 3) * 0.022 - 0.022, seed = 300 + i)
                break
            }
        }
        // The tee's markers.
        for (s in listOf(-1, 1)) out += Prop(PropKind.MARKER, hole.tee.x + s * 0.06, hole.tee.y, r = 0.008, h = 0.26, seed = s)
        for (z in hole.zones) when (z.type) {
            ZoneType.PORTAL -> z.at?.let { at ->
                for (i in 0 until 6) {
                    val a = i * PI / 3
                    out += Prop(PropKind.STONE, at.x + cos(a) * 0.095, at.y + sin(a) * 0.095, r = 0.009, h = 0.25, seed = seed++)
                }
            }
            ZoneType.EYES -> z.box?.let { b ->
                for (f in listOf(0.3, 0.7)) out += Prop(PropKind.EYE, b.x0 + (b.x1 - b.x0) * f, b.y0 + (b.y1 - b.y0) * 0.4, r = 0.04, h = 0.7, seed = seed++)
            }
            else -> Unit
        }
        // Never block the way: a hole the rocks or a palm would close is played without them.
        if (!Courses.playable(hole, out)) out.removeAll { it.kind == PropKind.ROCK }
        if (!Courses.playable(hole, out)) out.removeAll { it.kind == PropKind.PALM }
        return out
    }
}

/** The props by where they are, so the ball only looks at the ones near it. */
internal class PropGrid(props: List<Prop>) {
    private val cells = HashMap<Int, MutableList<Prop>>()

    init {
        for (p in props) {
            val reach = maxOf(p.r, Props.PLANT) + Golf.R + 0.01
            for (c in cell(p.x - reach)..cell(p.x + reach)) for (r in cell(p.y - reach)..cell(p.y + reach)) cells.getOrPut(key(c, r)) { mutableListOf() } += p
        }
    }

    fun near(x: Double, y: Double): List<Prop> = cells[key(cell(x), cell(y))] ?: emptyList()

    private fun cell(v: Double) = floor(v / SIZE).toInt()
    private fun key(c: Int, r: Int) = (r + 100) * 1000 + (c + 100)

    private companion object {
        const val SIZE = 0.1
    }
}

/** One frame of a shot, a sixtieth of a second: where the ball is and how high ([h], in world units). */
data class Frame(val x: Double, val y: Double, val h: Double)

/** Things that happen during a shot, for sounds and pictures: at [frame], [what] (see [Golf.simulate]). */
data class ShotEvent(val frame: Int, val what: String)

/**
 * A shot's [frames], whether it dropped in, any [hazard] it ended in, the [events] along the
 * way and the sticker spot it stopped on ([restZone]: Kylian or the eyes).
 */
data class GolfShot(
    val frames: List<Frame>,
    val holed: Boolean,
    val hazard: Hazard? = null,
    val events: List<ShotEvent> = emptyList(),
    val restZone: ZoneType? = null,
) {
    val path get() = frames.map { Pt(it.x, it.y) }
    val end get() = frames.last().let { Pt(it.x, it.y) }

    /** Where the ball is played from next: back on the tee after the sea, a pond or the fire. */
    fun rest(hole: Hole) = if (hazard != null) hole.tee else end

    /** Whether Kylian takes the shooter's next turn. */
    val skip get() = restZone == ZoneType.KYLIAN && hazard == null && !holed
}

/**
 * Mini Golf's courses and physics, all on the phone: the player shooting works the shot out
 * and sends where it stopped; the other phones replay it with [simulate] (the same numbers
 * give the same path). The server keeps only strokes and turns (golf.js). The same numbers
 * drive the 3D view (assets/golf3d), which only shows the frames worked out here.
 */
object Golf {
    const val W = 1.0
    /** How long the holes are. */
    const val H = 3.2
    /** The ball's radius, for walls. */
    const val R = 0.022
    const val CUP = 0.04
    /** How near a portal's middle the ball must roll to go through. */
    const val PORTAL = 0.06
    /** How round an island's corners are, on screen and to the ball. */
    const val CORNER = 0.04
    /** A safety net only: a hole ends when someone is in and the others have had their extra shots. */
    const val MAX_STROKES = 20
    /** Once someone holes out, the shots everyone still out gets to finish. */
    const val AFTER_FIRST_IN = 3
    /** How long phones show a lost ball sinking before it's back on the tee. */
    const val LOST_MS = 1100

    // The physics, as in the approved preview: the ball's height is in world units (10 a course unit).
    private const val DT = 1.0 / 120
    const val ROLL_SPEED = 2.4
    const val JUMP_SPEED = 0.6
    const val JUMP_UP = 7.0
    const val GRAVITY = 14.0
    /** Where the sea is, below the islands' tops. */
    const val SEA = -1.7
    /** Lower than this (world units), the ball hits a wall; higher, it flies over. */
    private const val WALL_TOP = 0.45
    /** Lower than this, the ball brushes grass and flowers. */
    private const val PLANT_TOP = 0.35
    /** A ball rolling slower than this over the cup drops in. */
    private const val DROP_SPEED = 1.3
    /** Slower than this on a pond, the ball sinks. */
    private const val SINK_SPEED = 0.45
    private fun decel(g: Ground) = when (g) {
        Ground.GRASS, Ground.POND -> 2.2
        Ground.BRIDGE -> 1.8
        Ground.SAND -> 6.5
    }

    /**
     * Hole [index] of a game playing [course] (ids into [Courses]); a game without one (from
     * a server that predates the library) plays [HOLES].
     */
    fun hole(course: List<Int>?, index: Int): Hole =
        course?.getOrNull(index)?.let { Courses.hole(it) } ?: HOLES[index.mod(HOLES.size)]

    /** Three holes for when the library isn't there: one, two and three islands. */
    val HOLES = listOf(
        Hole(
            4, Pt(0.5, 3.0), Pt(0.5, 0.25),
            islands = listOf(Box(0.05, 0.05, 0.95, 3.15)),
            walls = listOf(Box(0.2, 1.6, 0.7, 1.64), Box(0.4, 0.8, 0.9, 0.84)),
            sand = listOf(Box(0.55, 2.2, 0.85, 2.4)),
        ),
        Hole(
            5, Pt(0.5, 3.0), Pt(0.3, 0.3),
            islands = listOf(Box(0.05, 0.05, 0.95, 1.45), Box(0.05, 1.75, 0.95, 3.15)),
            bridges = listOf(Box(0.75, 1.43, 0.88, 1.77)),
            walls = listOf(Box(0.3, 2.4, 0.8, 2.44)),
            water = listOf(Pond(0.65, 0.8, 0.12, 0.08)),
            zones = listOf(Zone(ZoneType.EYES, Box(0.1, 2.0, 0.3, 2.24))),
        ),
        // The sticker hole from the preview.
        Hole(
            6, Pt(0.5, 3.0), Pt(0.2, 0.2),
            islands = listOf(Box(0.04, 0.05, 0.96, 1.05), Box(0.08, 1.35, 0.92, 2.1), Box(0.04, 2.45, 0.96, 3.15)),
            bridges = listOf(Box(0.08, 2.05, 0.22, 2.5), Box(0.78, 1.0, 0.92, 1.4)),
            walls = listOf(Box(0.3, 0.42, 0.96, 0.46), Box(0.48, 1.62, 0.52, 2.1)),
            sand = listOf(Box(0.6, 0.1, 0.9, 0.3)),
            water = listOf(Pond(0.8, 2.75, 0.09, 0.07)),
            zones = listOf(
                Zone(ZoneType.FIRE, Box(0.56, 1.78, 0.88, 2.07)),
                Zone(ZoneType.STONKS, Box(0.12, 1.37, 0.3, 1.52), dir = Pt(0.0, -1.0)),
                Zone(ZoneType.KYLIAN, Box(0.06, 0.64, 0.36, 0.9)),
                Zone(ZoneType.PORTAL, at = Pt(0.15, 3.04), to = Pt(0.86, 0.68)),
                Zone(ZoneType.PORTAL, at = Pt(0.86, 0.68), to = Pt(0.15, 3.04)),
                Zone(ZoneType.EYES, Box(0.3, 0.12, 0.5, 0.36)),
            ),
        ),
    )

    private enum class Phase { AIR, ROLL, FALLING, LOST, HOLED, REST }

    private class Ball(var x: Double, var y: Double) {
        var h = 0.0
        var vx = 0.0
        var vy = 0.0
        var vh = 0.0
        var phase = Phase.ROLL
        var launched = false
        var portalCool = 0.0
    }

    /**
     * Moves [b] on by its speed, turning back off any wall, handrail or ramp side it would go
     * into and bouncing off solid things (unless it's flying over them); grass and flowers slow it.
     */
    private fun moveWithWalls(hole: Hole, b: Ball, dt: Double, events: MutableList<String>) {
        val nx = b.x + b.vx * dt
        val ny = b.y + b.vy * dt
        val w = if (b.h < WALL_TOP) hole.solids.firstOrNull { nx > it.x0 - R && nx < it.x1 + R && ny > it.y0 - R && ny < it.y1 + R } else null
        if (w != null) {
            if (b.x <= w.x0 - R || b.x >= w.x1 + R) b.vx = -b.vx * 0.75 else b.vy = -b.vy * 0.75
            events += "wall"
            return
        }
        val near = hole.propGrid.near(nx, ny)
        for (p in near) {
            if (!p.kind.solid || b.h >= p.h) continue
            val dx = nx - p.x
            val dy = ny - p.y
            val d = hypot(dx, dy)
            val min = p.r + R
            if (d >= min) continue
            val ux = if (d > 1e-6) dx / d else 1.0
            val uy = if (d > 1e-6) dy / d else 0.0
            val vn = b.vx * ux + b.vy * uy
            if (vn < 0) {
                b.vx -= 1.7 * vn * ux
                b.vy -= 1.7 * vn * uy
            }
            b.x = p.x + ux * min
            b.y = p.y + uy * min
            events += "bump"
            return
        }
        if (b.h < PLANT_TOP) for (p in near) {
            if (!p.kind.solid && hypot(nx - p.x, ny - p.y) < Props.PLANT) {
                val k = exp(-dt * 2.2)
                b.vx *= k
                b.vy *= k
            }
        }
        b.x = nx
        b.y = ny
    }

    /** One step of [dt] seconds for [b]: what happens goes into [events]. */
    private fun advance(hole: Hole, b: Ball, dt: Double, events: MutableList<String>) {
        if (b.portalCool > 0) b.portalCool -= dt
        when (b.phase) {
            Phase.AIR -> {
                moveWithWalls(hole, b, dt, events)
                b.vh -= GRAVITY * dt
                b.h += b.vh * dt
                if (b.h <= 0 && b.vh < 0) {
                    val at = Pt(b.x, b.y)
                    val ground = hole.ground(at)
                    if (ground == null) {
                        b.phase = Phase.FALLING
                        events += "drop"
                        return
                    }
                    val zone = hole.zoneAt(at)
                    if (ground == Ground.POND || zone?.type == ZoneType.FIRE) {
                        b.h = 0.0
                        b.phase = Phase.LOST
                        events += if (ground == Ground.POND) "water" else "fire"
                        return
                    }
                    // Land, hop a little, and roll on.
                    b.h = 0.0
                    val keep = if (ground == Ground.SAND) 0.12 else 0.45
                    b.vx *= keep
                    b.vy *= keep
                    b.phase = Phase.ROLL
                    b.vh = 1.6
                    events += "land"
                }
            }
            Phase.ROLL -> {
                // A little hop after landing, with no grip until it's down.
                if (b.vh > 0 || b.h > 0) {
                    b.vh -= GRAVITY * dt
                    b.h = maxOf(0.0, b.h + b.vh * dt)
                    if (b.h == 0.0) b.vh = 0.0
                }
                val at = Pt(b.x, b.y)
                val ground = hole.ground(at)
                if (ground == null) {
                    b.phase = Phase.FALLING
                    events += "drop"
                    return
                }
                val speed = hypot(b.vx, b.vy)
                if (ground == Ground.POND && speed < SINK_SPEED) {
                    b.phase = Phase.LOST
                    events += "water"
                    return
                }
                val zone = hole.zoneAt(at)
                if (zone?.type == ZoneType.FIRE) {
                    b.phase = Phase.LOST
                    events += "fire"
                    return
                }
                if (zone?.type == ZoneType.STONKS && !b.launched && b.h == 0.0) {
                    // The ramp turns a roll into a jump, along its arrow.
                    val dir = zone.dir ?: Pt(0.0, -1.0)
                    b.launched = true
                    b.phase = Phase.AIR
                    b.vx = dir.x * 0.85
                    b.vy = dir.y * 0.85
                    b.vh = 5.0
                    events += "stonks"
                    return
                }
                if (zone?.type == ZoneType.PORTAL && b.portalCool <= 0 && zone.to != null) {
                    b.portalCool = 0.9
                    b.x = zone.to.x
                    b.y = zone.to.y
                    events += "portal"
                    return
                }
                if (hypot(b.x - hole.cup.x, b.y - hole.cup.y) < CUP && speed < DROP_SPEED && b.h == 0.0) {
                    b.phase = Phase.HOLED
                    b.x = hole.cup.x
                    b.y = hole.cup.y
                    events += "holed"
                    return
                }
                val d = decel(ground) * dt
                if (speed <= d) {
                    b.vx = 0.0
                    b.vy = 0.0
                    if (b.h == 0.0) b.phase = Phase.REST
                    return
                }
                b.vx -= b.vx / speed * d
                b.vy -= b.vy / speed * d
                moveWithWalls(hole, b, dt, events)
            }
            Phase.FALLING -> {
                b.x += b.vx * dt * 0.6
                b.y += b.vy * dt * 0.6
                b.vh -= GRAVITY * dt
                b.h += b.vh * dt
                if (b.h <= SEA) {
                    b.phase = Phase.LOST
                    events += "sea"
                }
            }
            else -> Unit
        }
    }

    /**
     * Plays a [kind] shot from [from] at [angle] (radians, 0 = right, y down) with [power]
     * (0 to 1). Events: "wall" (a wall or handrail), "bump" (a rock, palm, post...), "land", "drop" (over the edge), "sea", "water", "fire",
     * "stonks", "portal", "holed", and at the end "kylian" or "eyes" when it stops on one.
     */
    fun simulate(hole: Hole, from: Pt, kind: ShotKind, angle: Double, power: Double): GolfShot {
        val b = Ball(from.x, from.y)
        val dx = cos(angle)
        val dy = sin(angle)
        if (kind == ShotKind.JUMP) {
            b.vx = dx * JUMP_SPEED * power
            b.vy = dy * JUMP_SPEED * power
            b.vh = JUMP_UP * power
            b.phase = Phase.AIR
        } else {
            b.vx = dx * ROLL_SPEED * power
            b.vy = dy * ROLL_SPEED * power
        }
        val frames = mutableListOf(Frame(b.x, b.y, 0.0))
        val events = mutableListOf<ShotEvent>()
        val now = mutableListOf<String>()
        var step = 0
        // Eight seconds at most; a ball still creeping then just stops.
        while (step < 8 * 120 && b.phase != Phase.LOST && b.phase != Phase.HOLED && b.phase != Phase.REST) {
            advance(hole, b, DT, now)
            step++
            if (step % 2 == 0 || b.phase == Phase.LOST || b.phase == Phase.HOLED || b.phase == Phase.REST) frames += Frame(b.x, b.y, b.h)
            for (e in now) if (events.lastOrNull()?.let { it.what == e && it.frame >= frames.size - 3 } != true) events += ShotEvent(frames.size - 1, e)
            now.clear()
        }
        val hazard = when (events.lastOrNull { it.what in LOST }?.what) {
            "fire" -> Hazard.FIRE
            "water" -> Hazard.WATER
            "sea" -> Hazard.DROP
            else -> null
        }
        val holed = b.phase == Phase.HOLED
        var restZone: ZoneType? = null
        if (hazard == null && !holed) {
            restZone = hole.zoneAt(Pt(b.x, b.y))?.type?.takeIf { it == ZoneType.KYLIAN || it == ZoneType.EYES }
            restZone?.let { events += ShotEvent(frames.size - 1, it.key) }
        }
        return GolfShot(frames, holed, hazard, events, restZone)
    }

    private val LOST = setOf("fire", "water", "sea")

    /** How long [shot] plays on screen, with a lost ball sinking, so the next shot doesn't start before it's done. */
    fun playMs(shot: GolfShot) = shot.frames.size * 1000L / 60 + if (shot.hazard != null) LOST_MS else 0

    private const val G = 0.02

    /** How far each spot is from the cup on foot, going round the walls and over the bridges (for the computer). */
    fun distanceField(hole: Hole): (Pt) -> Double {
        val cols = (W / G).roundToInt()
        val rows = (hole.len / G).roundToInt()
        val d = DoubleArray(cols * rows) { Double.POSITIVE_INFINITY }
        fun blocked(c: Int, r: Int): Boolean {
            val x = (c + 0.5) * G
            val y = (r + 0.5) * G
            val p = Pt(x, y)
            return hole.solids.any { x > it.x0 - R && x < it.x1 + R && y > it.y0 - R && y < it.y1 + R } ||
                hole.props.any { it.kind.solid && it.r > 0.012 && hypot(x - it.x, y - it.y) < it.r + R } ||
                !hole.onCourse(p) || hole.inWater(p) || hole.zoneAt(p)?.type == ZoneType.FIRE
        }
        val start = floor(hole.cup.y / G).toInt().coerceIn(0, rows - 1) * cols + floor(hole.cup.x / G).toInt().coerceIn(0, cols - 1)
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
        // Over a gap there's no way on foot: as far as the nearest ground's, and a bit.
        return { p ->
            val c = floor(p.x / G).toInt().coerceIn(0, cols - 1)
            val r = floor(p.y / G).toInt().coerceIn(0, rows - 1)
            d[r * cols + c].takeIf { it.isFinite() } ?: (hypot(p.x - hole.cup.x, p.y - hole.cup.y) * 2 + 1)
        }
    }

    /** The computer's shot: what kind, which way and how hard. */
    data class Plan(val kind: ShotKind, val angle: Double, val power: Double)

    /**
     * The computer's shot from [from]: the best of a spread of rolls and jumps tried with the
     * real physics, then a wobble on aim and strength that grows as [skill] (0 to 1) drops.
     * It keeps off Kylian (a lost turn) and, with less weight, the eyes.
     */
    fun computerShot(hole: Hole, from: Pt, skill: Double, field: (Pt) -> Double = distanceField(hole), random: Random = Random.Default): Plan {
        var best = Plan(ShotKind.ROLL, 0.0, 0.5)
        var bestScore = Double.POSITIVE_INFINITY
        val lost = field(hole.tee) + 0.6
        fun score(kind: ShotKind, angle: Double, power: Double): Double {
            val shot = simulate(hole, from, kind, angle, power)
            return when {
                shot.holed -> -1 + power * 0.01
                shot.hazard != null -> lost
                else -> field(shot.end) + when (shot.restZone) {
                    ZoneType.KYLIAN -> 0.5
                    ZoneType.EYES -> 0.05
                    else -> 0.0
                }
            }
        }
        for (kind in ShotKind.entries) for (a in 0 until 72) for (p in 1..12) {
            val angle = a / 72.0 * 2 * PI
            val power = p / 12.0
            var s = score(kind, angle, power)
            // Near water, the fire or an edge a shot is only as good as its near misses.
            if (s < bestScore) s = maxOf(s, score(kind, angle - 0.03, power), score(kind, angle + 0.03, power), score(kind, angle, (power * 1.06).coerceAtMost(1.0)))
            if (s < bestScore) {
                bestScore = s
                best = Plan(kind, angle, power)
            }
        }
        val aim = 0.02 + (1 - skill) * 0.1
        val strength = 0.04 + (1 - skill) * 0.2
        return best.copy(
            angle = best.angle + (random.nextDouble() * 2 - 1) * aim,
            power = (best.power * (1 + (random.nextDouble() * 2 - 1) * strength)).coerceIn(0.05, 1.0),
        )
    }
}
