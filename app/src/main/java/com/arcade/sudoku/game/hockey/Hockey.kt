package com.arcade.sudoku.game.hockey

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Air Hockey on a table [W] wide and [H] long. The bottom player defends the goal at y = H,
 * the top player the goal at y = 0; each paddle stays in its own half. First to [GOALS] wins
 * (the server checks the same number in backend/cloudflare/src/room.js).
 */
object Hockey {
    const val W = 1f
    const val H = 1.6f
    const val PUCK_R = 0.04f
    const val PADDLE_R = 0.075f
    const val GOAL_W = 0.38f
    const val GOALS = 7
    const val MAX_SPEED = 2.8f
    private const val FRICTION = 0.25f
    private const val BOUNCE = 0.92f

    /** Who scored with the puck at y: past the top end BOTTOM did, past the bottom end TOP did. (Only the goal mouth lets it out.) */
    internal fun goalAt(y: Float): Side? = when {
        y < -PUCK_R -> Side.BOTTOM
        y > H + PUCK_R -> Side.TOP
        else -> null
    }

    internal fun friction(dt: Float) = 1f - FRICTION * dt
    internal const val E = BOUNCE
}

enum class Side { BOTTOM, TOP }

/** A paddle or the puck: where it is and how fast it moves (table units per second). */
data class Body(val x: Float, val y: Float, val vx: Float = 0f, val vy: Float = 0f)

/** One moment of a table, as drawn on screen and sent to the other phone. */
data class HockeyFrame(
    val puck: Body,
    val bottom: Body,
    val top: Body,
    val bottomScore: Int = 0,
    val topScore: Int = 0,
) {
    /** The same table seen from the other end: the top player's own view. */
    fun flipped() = HockeyFrame(puck.flip(), top.flip(), bottom.flip(), topScore, bottomScore)

    companion object {
        fun start() = HockeyFrame(
            puck = Body(Hockey.W / 2, Hockey.H / 2),
            bottom = Body(Hockey.W / 2, Hockey.H * 0.85f),
            top = Body(Hockey.W / 2, Hockey.H * 0.15f),
        )
    }
}

fun Body.flip() = Body(Hockey.W - x, Hockey.H - y, -vx, -vy)

/** The table's physics. The host's phone (or the solo game) runs one of these. */
class HockeyTable {
    var frame = HockeyFrame.start()
        private set

    /** Where each paddle's player wants it; it moves there on the next step. */
    private var bottomTarget = frame.bottom
    private var topTarget = frame.top

    fun moveBottom(x: Float, y: Float) { bottomTarget = clampPaddle(Side.BOTTOM, x, y) }

    fun moveTop(x: Float, y: Float) { topTarget = clampPaddle(Side.TOP, x, y) }

    /** Puts the puck back for a new point, on the side of whoever just let in a goal. */
    private fun serve(conceded: Side) {
        val y = if (conceded == Side.BOTTOM) Hockey.H * 0.62f else Hockey.H * 0.38f
        frame = frame.copy(puck = Body(Hockey.W / 2, y))
    }

    /** Advances the table [dt] seconds; returns the side that scored, if any. */
    fun step(dt: Float): Side? {
        val t = dt.coerceIn(0f, 0.05f)
        if (t == 0f) return null
        // Paddles go to where their players put them; their speed is what hits the puck.
        var bottom = follow(frame.bottom, bottomTarget, t)
        var top = follow(frame.top, topTarget, t)
        var puck = frame.puck
        val steps = 4
        val h = t / steps
        repeat(steps) {
            puck = Body(puck.x + puck.vx * h, puck.y + puck.vy * h, puck.vx, puck.vy)
            puck = walls(puck)
            puck = hit(puck, bottom)
            puck = hit(puck, top)
            // A hit can't push the puck through a wall; a paddle squeezing it gives way instead.
            puck = walls(puck)
            bottom = yieldTo(bottom, puck)
            top = yieldTo(top, puck)
        }
        val f = Hockey.friction(t)
        puck = puck.copy(vx = puck.vx * f, vy = puck.vy * f)
        // A puck that hasn't left a small circle for a while (stuck in a corner, or nobody
        // hitting it) goes back to the middle of its half.
        if (hypot(puck.x - anchorX, puck.y - anchorY) > STILL_RADIUS) {
            anchorX = puck.x
            anchorY = puck.y
            stillFor = 0f
        } else {
            stillFor += t
        }
        if (stillFor > STILL_SECONDS) {
            stillFor = 0f
            puck = Body(Hockey.W / 2, if (puck.y < Hockey.H / 2) Hockey.H * 0.3f else Hockey.H * 0.7f)
        }
        frame = frame.copy(puck = puck, bottom = bottom, top = top)
        val scorer = Hockey.goalAt(puck.y) ?: return null
        frame = if (scorer == Side.BOTTOM) frame.copy(bottomScore = frame.bottomScore + 1) else frame.copy(topScore = frame.topScore + 1)
        serve(conceded = if (scorer == Side.BOTTOM) Side.TOP else Side.BOTTOM)
        return scorer
    }

    private var anchorX = frame.puck.x
    private var anchorY = frame.puck.y
    private var stillFor = 0f

    /** [paddle] moved back just out of the puck, if it overlaps it. */
    private fun yieldTo(paddle: Body, puck: Body): Body {
        val dx = paddle.x - puck.x
        val dy = paddle.y - puck.y
        val d = hypot(dx, dy)
        val reach = Hockey.PUCK_R + Hockey.PADDLE_R
        if (d >= reach) return paddle
        val nx = if (d == 0f) 0f else dx / d
        val ny = if (d == 0f) 1f else dy / d
        return paddle.copy(x = puck.x + nx * reach, y = puck.y + ny * reach)
    }

    /** A paddle moving towards [to], no faster than a hand can (so it never jumps through the puck). */
    private fun follow(from: Body, to: Body, t: Float): Body {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val d = hypot(dx, dy)
        val k = if (d == 0f) 0f else min(1f, PADDLE_SPEED * t / d)
        return Body(from.x + dx * k, from.y + dy * k, dx * k / t, dy * k / t)
    }

    private fun walls(p: Body): Body {
        var (x, y, vx, vy) = p
        val r = Hockey.PUCK_R
        if (x < r) { x = r; vx = abs(vx) * Hockey.E }
        if (x > Hockey.W - r) { x = Hockey.W - r; vx = -abs(vx) * Hockey.E }
        // The ends bounce, except across the mouth of the goal.
        val inMouth = abs(x - Hockey.W / 2) < Hockey.GOAL_W / 2
        if (!inMouth && y < r) { y = r; vy = abs(vy) * Hockey.E }
        if (!inMouth && y > Hockey.H - r) { y = Hockey.H - r; vy = -abs(vy) * Hockey.E }
        return Body(x, y, vx, vy)
    }

    private fun hit(p: Body, paddle: Body): Body {
        val dx = p.x - paddle.x
        val dy = p.y - paddle.y
        val d = hypot(dx, dy)
        val reach = Hockey.PUCK_R + Hockey.PADDLE_R
        if (d >= reach || d == 0f) return p
        val nx = dx / d
        val ny = dy / d
        // Out of the paddle, then bounce off it, taking on the paddle's speed.
        val x = paddle.x + nx * reach
        val y = paddle.y + ny * reach
        val rvx = p.vx - paddle.vx
        val rvy = p.vy - paddle.vy
        val along = rvx * nx + rvy * ny
        var vx = p.vx
        var vy = p.vy
        if (along < 0) {
            vx -= (1 + Hockey.E) * along * nx
            vy -= (1 + Hockey.E) * along * ny
        }
        val speed = hypot(vx, vy)
        if (speed > Hockey.MAX_SPEED) {
            vx *= Hockey.MAX_SPEED / speed
            vy *= Hockey.MAX_SPEED / speed
        }
        return Body(x, y, vx, vy)
    }

    companion object {
        private const val PADDLE_SPEED = 4f
        private const val STILL_RADIUS = 0.08f
        private const val STILL_SECONDS = 4f

        /** A paddle kept inside the table and its own half. */
        fun clampPaddle(side: Side, x: Float, y: Float): Body {
            val r = Hockey.PADDLE_R
            val cx = x.coerceIn(r, Hockey.W - r)
            val cy = if (side == Side.BOTTOM) y.coerceIn(Hockey.H / 2 + r, Hockey.H - r) else y.coerceIn(r, Hockey.H / 2 - r)
            return Body(cx, cy)
        }
    }
}

/**
 * The computer at the top end: [speed] is how fast its paddle can move (table units per
 * second). It aims at a spot across the far goal mouth that changes now and then.
 */
class HockeyComputer(private val speed: Float, private val random: kotlin.random.Random = kotlin.random.Random.Default) {
    private var aim = Hockey.W / 2

    /** Where the computer puts its paddle after [dt] seconds, given the table now. */
    fun move(frame: HockeyFrame, dt: Float): Pair<Float, Float> {
        val me = frame.top
        val puck = frame.puck
        val r = Hockey.PADDLE_R
        val target = if (puck.y < Hockey.H / 2 + 0.05f && puck.vy < 0.8f) {
            if (random.nextFloat() < dt * 1.5f) aim = Hockey.W / 2 + (random.nextFloat() * 2 - 1) * Hockey.GOAL_W * 0.45f
            // The puck is on our side: get behind it, on the line to the aim, and strike.
            val ax = aim - puck.x
            val ay = Hockey.H - puck.y
            val al = hypot(ax, ay)
            val tx = puck.x - ax / al * r * 0.6f
            val ty = puck.y - ay / al * r * 0.6f
            if (ty < r || tx < r || tx > Hockey.W - r) {
                // No room behind it (in a corner or against our end): strike from the middle side.
                val cx = Hockey.W / 2 - puck.x
                val cy = Hockey.H * 0.3f - puck.y
                val cl = hypot(cx, cy).takeIf { it > 0f } ?: 1f
                (puck.x + cx / cl * r * 0.6f) to (puck.y + cy / cl * r * 0.6f)
            } else {
                tx to ty
            }
        } else {
            // Defend: stay in front of the goal, following the puck across.
            (Hockey.W / 2 + (puck.x - Hockey.W / 2) * 0.6f) to Hockey.H * 0.12f
        }
        val dx = target.first - me.x
        val dy = target.second - me.y
        val d = hypot(dx, dy)
        val step = min(d, speed * dt)
        if (d == 0f) return me.x to me.y
        return (me.x + dx / d * step) to (me.y + dy / d * step)
    }

    companion object {
        val SPEEDS = listOf(0.7f, 1.1f, 1.6f)
    }
}

/** How far a puck seen [ageS] seconds ago has gone since, bouncing off the side walls. */
fun Body.ahead(ageS: Float): Body {
    val t = ageS.coerceIn(0f, 0.25f)
    var x = x + vx * t
    var vxNow = vx
    val r = Hockey.PUCK_R
    if (x < r) { x = 2 * r - x; vxNow = -vxNow }
    if (x > Hockey.W - r) { x = 2 * (Hockey.W - r) - x; vxNow = -vxNow }
    return Body(x.coerceIn(r, Hockey.W - r), max(-r, min(Hockey.H + r, y + vy * t)), vxNow, vy)
}
