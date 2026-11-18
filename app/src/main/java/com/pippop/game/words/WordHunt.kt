package com.pippop.game.words

/** The four Word Hunt sizes: [width] columns by [height] rows (portrait: taller than wide), and how many words. */
enum class HuntLevel(val key: String, val width: Int, val height: Int, val words: Int) {
    EASY("easy", 10, 10, 10),
    MEDIUM("medium", 12, 12, 14),
    HARD("hard", 12, 16, 18),
    HUGE("huge", 16, 20, 24),
}

/** Reading direction of a hidden word. LEFT and UP are used only when backwards words are on. */
enum class HuntDir(val key: String, val dr: Int, val dc: Int) {
    RIGHT("right", 0, 1), DOWN("down", 1, 0), LEFT("left", 0, -1), UP("up", -1, 0),
}

/** A hidden word: its first letter is at ([row], [col]), 0-based from the top left, then it reads [dir]. */
data class HuntWord(val word: String, val row: Int, val col: Int, val dir: HuntDir) {
    val length get() = word.length
    fun cells(width: Int): List<Int> = List(word.length) { (row + dir.dr * it) * width + col + dir.dc * it }
}

/** [rows] are [height] strings of [width] letters A-Z; [words] are sorted by word. */
data class HuntGrid(
    val level: HuntLevel, val backwards: Boolean, val seed: Int,
    val rows: List<String>, val words: List<HuntWord>,
) {
    val width get() = level.width
    val height get() = level.height
}

/**
 * Word Hunt grid maker. The same (level, backwards, seed) gives the same grid as the server's
 * backend/cloudflare/src/wordhunt.js; both run src/test/resources/wordhunt-cases.json. Keep the two
 * in step: same random calls in the same order.
 *
 * Every grid draws from one mixed pool ([WordHuntThemes.pool]: all the categories joined, repeats dropped).
 *
 * Random numbers: mulberry32 on 32-bit Ints (Int arithmetic wraps like Math.imul), reduced with `%`.
 */
object WordHunt {
    // Fill letters are weighted towards common ones: A..Z.
    private val WEIGHTS = intArrayOf(8, 2, 3, 4, 12, 2, 2, 6, 7, 1, 1, 4, 2, 7, 8, 2, 1, 6, 6, 9, 3, 1, 2, 1, 2, 1)
    private val WEIGHT_TOTAL = WEIGHTS.sum()
    private const val ATTEMPTS = 200
    private const val FIX_ROUNDS = 1000

    class Mulberry32(seed: Int) {
        private var a = seed
        fun next(): Long {
            a += 0x6D2B79F5
            var t = (a xor (a ushr 15)) * (1 or a)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        }
        fun below(n: Int): Int = (next() % n).toInt()
    }

    fun make(level: HuntLevel, backwards: Boolean, seed: Int): HuntGrid {
        val pool = WordHuntThemes.pool
        check(pool.isNotEmpty()) { "The Word Hunt words are not loaded" }
        val (width, height) = level.width to level.height
        val rng = Mulberry32(seed)
        val dirs = if (backwards) HuntDir.entries else HuntDir.entries.take(2)
        val blocked = WordLadder.blocklist.sorted()

        val shuffled = pool.toMutableList()
        for (i in shuffled.size - 1 downTo 1) {
            val j = rng.below(i + 1)
            val tmp = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = tmp
        }
        fun inside(a: String, b: String) = b.contains(a) || (backwards && b.contains(a.reversed()))
        val chosen = ArrayList<String>()
        for (w in shuffled) {
            if (chosen.size == level.words) break
            if (backwards && w == w.reversed()) continue
            if (blocked.any { inside(it, w) }) continue
            if (chosen.any { inside(it, w) || inside(w, it) }) continue
            // A word that holds another word of the vocabulary (JUMP in JUMPER) would show it, found and refused, inside itself.
            if (pool.any { it != w && it.length >= 4 && inside(it, w) }) continue
            chosen.add(w)
        }
        check(chosen.size >= level.words) { "Too few words for ${level.key}" }
        val order = chosen.sortedWith(compareBy<String>({ -it.length }, { it }))

        fun find(grid: IntArray, word: String): List<HuntWord> {
            val out = ArrayList<HuntWord>()
            val first = word[0].code
            for (r in 0 until height) for (c in 0 until width) {
                if (grid[r * width + c] != first) continue
                for (d in dirs) {
                    var k = 1
                    var rr = r + d.dr
                    var cc = c + d.dc
                    while (k < word.length && rr in 0 until height && cc in 0 until width && grid[rr * width + cc] == word[k].code) {
                        k++; rr += d.dr; cc += d.dc
                    }
                    if (k == word.length) out.add(HuntWord(word, r, c, d))
                }
            }
            return out
        }

        repeat(ATTEMPTS) {
            val grid = IntArray(width * height)
            val locked = BooleanArray(width * height)
            val placed = ArrayList<HuntWord>()
            var ok = true
            for (word in order) {
                val fits = ArrayList<HuntWord>()
                for (d in dirs) for (r in 0 until height) for (c in 0 until width) {
                    val er = r + d.dr * (word.length - 1)
                    val ec = c + d.dc * (word.length - 1)
                    if (er !in 0 until height || ec !in 0 until width) continue
                    val candidate = HuntWord(word, r, c, d)
                    if (candidate.cells(width).withIndex().all { (k, cell) -> grid[cell] == 0 || grid[cell] == word[k].code }) fits.add(candidate)
                }
                if (fits.isEmpty()) { ok = false; break }
                val at = fits[rng.below(fits.size)]
                at.cells(width).forEachIndexed { k, cell -> grid[cell] = word[k].code; locked[cell] = true }
                placed.add(at)
            }
            if (!ok) return@repeat

            fun letter(): Int {
                var n = rng.below(WEIGHT_TOTAL)
                var i = 0
                while (n >= WEIGHTS[i]) { n -= WEIGHTS[i]; i++ }
                return 65 + i
            }
            for (i in grid.indices) if (!locked[i]) grid[i] = letter()

            // Re-roll fill letters until no chosen word shows a second time and no blocklisted or other pool word shows.
            val chosenSet = chosen.toSet()
            fun problems(): List<List<Int>> {
                val out = ArrayList<List<Int>>()
                for (p in placed) for (f in find(grid, p.word)) if (f != p) out.add(f.cells(width))
                for (b in blocked) for (f in find(grid, b)) out.add(f.cells(width))
                // With no list on screen, a word of 4 or more letters that the fill letters spell by chance would be found and
                // refused, so those are re-rolled too. (One wholly inside placed letters, a crossing, cannot be: the attempt fails and starts again.)
                for (w in pool) {
                    if (w.length < 4 || w in chosenSet) continue
                    for (f in find(grid, w)) out.add(f.cells(width))
                }
                return out
            }
            var clean = false
            var round = 0
            while (round < FIX_ROUNDS && ok) {
                val bad = problems()
                if (bad.isEmpty()) { clean = true; break }
                for (cs in bad) {
                    val free = cs.filter { !locked[it] }
                    if (free.isEmpty()) { ok = false; break }
                    grid[free[rng.below(free.size)]] = letter()
                }
                round++
            }
            if (!ok || !clean) return@repeat

            val rows = List(height) { r -> String(CharArray(width) { c -> grid[r * width + c].toChar() }) }
            return HuntGrid(level, backwards, seed, rows, placed.sortedBy { it.word })
        }
        error("No grid found")
    }
}
