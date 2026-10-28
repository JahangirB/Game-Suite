package com.pippop.game.golf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.random.Random

/** How hard a game's nine holes are: every hole has this many islands. */
enum class GolfLevel(val key: String, val label: String, val islands: Int) {
    EASY("easy", "Easy · one island", 1),
    MEDIUM("medium", "Medium · two islands", 2),
    HARD("hard", "Hard · three islands", 3);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: EASY
    }
}

/**
 * Mini Golf's library of [SIZE] designed holes, read from assets/golf/courses.jsonl (made by
 * scripts/make-golf-courses.py, one hole per line). Holes 0-332 are one piece of ground (easy),
 * 333-665 two islands joined by a bridge (medium), 666-999 three islands and two bridges (hard).
 * A game plays nine holes of the level picked for it; only their numbers travel between phones.
 */
object Courses {
    const val SIZE = 1000
    const val MEDIUM = 333
    const val HARD = 666
    private const val PATH = "golf/courses.jsonl"

    @Volatile private var holes: List<Hole>? = null
    private var read: (() -> List<String>)? = null

    /** Reads the library in the background; a hole asked for before it's done waits for it. */
    fun load(context: Context) {
        val app = context.applicationContext
        read = { app.assets.open(PATH).bufferedReader().use { it.readLines() } }
        Thread { all() }.start()
    }

    /** The library from [lines] of courses.jsonl (tests read the file directly). */
    fun install(lines: List<String>) {
        holes = lines.filter { it.isNotBlank() }.map(::parse)
    }

    private fun all(): List<Hole> = holes ?: synchronized(this) {
        holes ?: read?.invoke()?.let { install(it); holes } ?: emptyList()
    }

    /** Hole number [id]; the classic holes stand in if the library isn't there (as in some tests). */
    fun hole(id: Int): Hole = all().getOrNull(id.mod(SIZE)) ?: Golf.HOLES[id.mod(Golf.HOLES.size)]

    /** How many islands hole [id] has: 1 easy, 2 medium, 3 hard. */
    fun islands(id: Int) = when (id.mod(SIZE)) {
        in 0 until MEDIUM -> 1
        in MEDIUM until HARD -> 2
        else -> 3
    }

    /** The hole numbers of [level]. */
    fun of(level: GolfLevel) = when (level) {
        GolfLevel.EASY -> 0 until MEDIUM
        GolfLevel.MEDIUM -> MEDIUM until HARD
        GolfLevel.HARD -> HARD until SIZE
    }

    /** Nine different holes of [level] for a game, as golf.js picks them. */
    fun pick(level: GolfLevel, random: Random = Random.Default): List<Int> = of(level).shuffled(random).take(9)

    private fun parse(line: String): Hole {
        val o = JSONObject(line)
        fun pt(a: JSONArray) = Pt(a.getDouble(0), a.getDouble(1))
        fun boxes(key: String) = o.optJSONArray(key)?.let { a ->
            (0 until a.length()).map { i -> a.getJSONArray(i).let { Box(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) } }
        }
        val water = o.optJSONArray("water")?.let { a ->
            (0 until a.length()).map { i -> a.getJSONArray(i).let { Pond(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) } }
        }.orEmpty()
        return Hole(
            par = o.getInt("par"),
            tee = pt(o.getJSONArray("tee")),
            cup = pt(o.getJSONArray("cup")),
            walls = boxes("walls").orEmpty(),
            sand = boxes("sand").orEmpty(),
            cuts = boxes("cuts").orEmpty(),
            water = water,
            islands = if (o.isNull("islands")) null else boxes("islands"),
            bridges = boxes("bridges").orEmpty(),
        )
    }

    private const val G = 0.02

    /**
     * Whether a ball can get from the tee to the cup over dry ground: on the course, out of the
     * water, round the walls, with room for the ball on every side (so bridges are wide enough).
     * The script that designs the holes checks the same before keeping one.
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
