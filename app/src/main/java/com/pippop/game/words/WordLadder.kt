package com.pippop.game.words

import android.content.Context

/**
 * The Word Ladder dictionary (words/ladder.txt): common 4, 5 and 6 letter words, UPPERCASE, the
 * same as the server's (backend/cloudflare/src/ladder.js). Neighbours (words one letter away) are
 * worked out when the list is installed, from "C_LD" style keys, not stored. The app loads it on a
 * background thread at start (PipPopApplication); a nearby ladder game waits until [loaded].
 */
object WordLadder {
    val LENGTHS = listOf(4, 5, 6)

    @Volatile private var words: Set<String> = emptySet()
    @Volatile var blocklist: Set<String> = emptySet()
        private set
    @Volatile private var buckets: Map<String, List<String>> = emptyMap()

    val loaded get() = words.isNotEmpty()
    val all: Set<String> get() = words

    fun load(context: Context) {
        if (loaded) return
        fun read(name: String) = context.assets.open("words/$name.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        install(read("ladder"), read("blocklist"))
    }

    /** For tests, and [load]. */
    fun install(list: List<String>, blocked: List<String> = emptyList()) {
        blocklist = blocked.toSet()
        val map = HashMap<String, MutableList<String>>()
        for (w in list) for (i in w.indices) map.getOrPut(w.substring(0, i) + "_" + w.substring(i + 1)) { mutableListOf() }.add(w)
        buckets = map
        words = list.toSet()
    }

    fun isWord(word: String) = word in words

    /**
     * A real word a move may use: any word of 4 to 6 letters in the Word Race lists (loaded at
     * start: [WordList]), not blocklisted. The Letter Grid list adds no words.
     */
    fun isRealWord(word: String): Boolean {
        if (word.length !in 4..6 || word in blocklist) return false
        val lower = word.lowercase()
        return WordList.isWord(lower)
    }

    /** Ladder words that differ from [word] in exactly one letter (none for a word not in the list). */
    fun neighbours(word: String): List<String> = word.indices.flatMap { i ->
        buckets[word.substring(0, i) + "_" + word.substring(i + 1)].orEmpty().filter { it != word }
    }

    /** The fewest one-letter changes from [from] to [to] (0 if the same), or -1 if there is no way. */
    fun distance(from: String, to: String): Int {
        if (from !in words || to !in words || from.length != to.length) return -1
        if (from == to) return 0
        val seen = hashMapOf(from to 0)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val w = queue.removeFirst()
            for (n in neighbours(w)) {
                if (n in seen) continue
                seen[n] = seen.getValue(w) + 1
                if (n == to) return seen.getValue(n)
                queue.addLast(n)
            }
        }
        return -1
    }

    fun pathExists(from: String, to: String) = distance(from, to) >= 0
}

/**
 * Word Hunt themes (words/hunt.txt): a name and 40 or more UPPERCASE words of 3 to 8 letters each,
 * the same as the server's (backend/cloudflare/src/ladder.js huntThemes).
 */
object WordHuntThemes {
    @Volatile var themes: Map<String, List<String>> = emptyMap()
        private set

    fun load(context: Context) {
        if (themes.isNotEmpty()) return
        install(context.assets.open("words/hunt.txt").bufferedReader().readLines().filter { it.isNotBlank() })
    }

    /** Lines look like "Animals|ANT BAT BEAR". For tests, and [load]. */
    fun install(lines: List<String>) {
        themes = lines.associate { line -> line.substringBefore('|') to line.substringAfter('|').split(' ').filter { it.isNotEmpty() } }
    }
}
