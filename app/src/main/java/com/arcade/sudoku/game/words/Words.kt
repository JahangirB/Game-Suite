package com.arcade.sudoku.game.words

import android.content.Context
import java.time.LocalDate
import kotlin.random.Random

/**
 * Word Race rules, matching backend/cloudflare/src/words.js. A guess gets one colour per
 * letter: 'g' right letter in the right place, 'y' in the word but elsewhere, '.' not in it.
 */
object Words {
    const val TRIES = 6

    /** Colours for [guess] against [answer]. A repeated letter only counts as often as the answer has it. */
    fun score(guess: String, answer: String): String {
        val n = answer.length
        val result = CharArray(n) { '.' }
        val left = mutableMapOf<Char, Int>()
        for (i in 0 until n) {
            if (guess[i] == answer[i]) result[i] = 'g' else left[answer[i]] = (left[answer[i]] ?: 0) + 1
        }
        for (i in 0 until n) {
            val k = left[guess[i]] ?: 0
            if (result[i] == '.' && k > 0) {
                result[i] = 'y'
                left[guess[i]] = k - 1
            }
        }
        return String(result)
    }
}

/** Word Race levels, by the length of the secret word: expert is 7 or 8 letters at random. */
enum class WordLevel(val key: String, val label: String, val lengths: List<Int>) {
    EASY("easy", "Easy · 4 letters", listOf(4)),
    MEDIUM("medium", "Medium · 5 letters", listOf(5)),
    HARD("hard", "Hard · 6 letters", listOf(6)),
    EXPERT("expert", "Expert · 7–8 letters", listOf(7, 8));

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/**
 * The word lists (app/src/main/assets/words, from SCOWL): common answers of 4 to 8 letters and
 * every other real word a guess may be (SCOWL size 80). Loaded once when the app starts.
 */
object WordList {
    @Volatile private var answers: Map<Int, List<String>> = emptyMap()
    @Volatile private var allowed: Set<String> = emptySet()

    /** Draw & Guess: things easy to draw (words/draw.txt). */
    @Volatile var drawWords: List<String> = emptyList()
        private set

    fun load(context: Context) {
        if (answers.isNotEmpty()) return
        fun read(name: String) = context.assets.open("words/$name.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        install(read("answers"), read("allowed"))
        drawWords = context.assets.open("words/draw.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }

    /** For tests, and [load]. */
    fun install(answers: List<String>, allowed: List<String>, draw: List<String> = drawWords) {
        drawWords = draw
        this.allowed = (allowed + answers).toSet()
        this.answers = answers.groupBy { it.length }
    }

    fun isWord(word: String) = word in allowed

    /** A secret word for [level]: one of its lengths, then a word of that length. */
    fun random(level: WordLevel = WordLevel.MEDIUM, random: Random = Random.Default): String =
        answers.getValue(level.lengths.random(random)).random(random)

    /** Today's word (five letters): the same for everyone with this app, a new one each day. */
    fun daily(date: LocalDate = LocalDate.now()): String {
        val order = answers.getValue(5).shuffled(Random(DAILY_SEED))
        return order[Math.floorMod(date.toEpochDay(), order.size.toLong()).toInt()]
    }

    private const val DAILY_SEED = 20261022
}
