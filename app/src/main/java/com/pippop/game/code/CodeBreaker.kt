package com.pippop.game.code

import kotlin.random.Random

/**
 * Code Breaker rules, matching the server (backend/cloudflare/src/code.js): a code is [PEGS]
 * pegs from [COLOURS]; a guess gets black pegs (right colour, right place) and white pegs
 * (right colour, wrong place). [TRIES] guesses each.
 */
object CodeBreaker {
    const val PEGS = 4
    const val COLOURS = "rygbpo"
    /** Every colour there is; the harder setting adds the seventh (teal). */
    const val ALL_COLOURS = "rygbpoc"
    const val TRIES = 10

    fun isCode(c: String) = c.length == PEGS && c.all { it in COLOURS }

    /** Black and white pegs for [guess] against [code]. */
    fun score(guess: String, code: String): Pair<Int, Int> {
        var black = 0
        val left = mutableMapOf<Char, Int>()
        val rest = mutableListOf<Char>()
        for (i in code.indices) {
            if (guess[i] == code[i]) black++ else {
                left[code[i]] = (left[code[i]] ?: 0) + 1
                rest += guess[i]
            }
        }
        var white = 0
        for (g in rest) {
            val n = left[g] ?: 0
            if (n > 0) {
                white++
                left[g] = n - 1
            }
        }
        return black to white
    }

    fun randomCode(random: Random = Random.Default) = (1..PEGS).map { COLOURS.random(random) }.joinToString("")
}

/**
 * A match's settings, as on the server (code.js cleanOptions): [pegs] 4 or 5, [colours] 6 or 7,
 * and whether a colour may [repeats] in a code. Both players get the same.
 */
data class CodeRules(val pegs: Int = 4, val colours: Int = 6, val repeats: Boolean = true) {
    val palette get() = CodeBreaker.ALL_COLOURS.take(colours)
    val harder get() = this != CodeRules()

    /** Why [c] isn't a code under these rules, or null when it is. */
    fun problem(c: String): String? = when {
        c.length != pegs || c.any { it !in palette } -> "A code is $pegs pegs, each one of the ${if (colours == 7) "seven" else "six"} colours."
        !repeats && c.toSet().size != c.length -> "No repeats: every colour in the code is different."
        else -> null
    }

    fun isCode(c: String) = problem(c) == null

    fun random(random: Random = Random.Default): String =
        if (repeats) (1..pegs).map { palette.random(random) }.joinToString("") else palette.toList().shuffled(random).take(pegs).joinToString("")

    /** A few words for badges and the menu ("5 pegs · 7 colours · no repeats"). */
    val label get() = "$pegs pegs · $colours colours" + if (repeats) "" else " · no repeats"

    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("pegs", pegs).put("colours", colours).put("repeats", repeats)

    companion object {
        fun from(o: org.json.JSONObject?) = CodeRules(
            pegs = if (o?.optInt("pegs") == 5) 5 else 4,
            colours = if (o?.optInt("colours") == 7) 7 else 6,
            repeats = o?.optBoolean("repeats", true) ?: true,
        )
    }
}
