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
    const val TRIES = 10

    fun isCode(c: String) = c.length == PEGS && c.all { it in COLOURS }

    /** Black and white pegs for [guess] against [code]. */
    fun score(guess: String, code: String): Pair<Int, Int> {
        var black = 0
        val left = mutableMapOf<Char, Int>()
        val rest = mutableListOf<Char>()
        for (i in 0 until PEGS) {
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
