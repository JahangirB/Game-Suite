package com.pippop.ui

import com.pippop.data.GameKind
import com.pippop.data.SoloMetric
import com.pippop.data.SoloRecordStore
import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.pippop.data.WordGuess
import com.pippop.game.words.WordLevel
import com.pippop.game.words.WordList
import com.pippop.game.words.Words
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Word Race on your own: today's word (the same for everyone, saved for the day) or practice. */
data class SoloWordsUiState(
    /** Null until a mode is picked. */
    val daily: Boolean? = null,
    val answer: String = "",
    val guesses: List<WordGuess> = emptyList(),
    val input: String = "",
    val message: String? = null,
    /** Practice: the level picked (today's word is always five letters). */
    val level: WordLevel = WordLevel.MEDIUM,
) {
    val length get() = answer.length.takeIf { it > 0 } ?: 5
    val solved get() = guesses.lastOrNull()?.score == "g".repeat(length)
    val done get() = solved || guesses.size >= Words.TRIES
}

class SoloWordsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("word_race", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(SoloWordsUiState())
    val state: StateFlow<SoloWordsUiState> = _state.asStateFlow()

    /** Today's word, with any guesses already made today. */
    fun playDaily(level: WordLevel = _state.value.level) {
        val today = LocalDate.now().toString()
        val answer = WordList.daily()
        val saved = if (prefs.getString(KEY_DATE, null) == today) prefs.getString(KEY_GUESSES, "").orEmpty() else ""
        val guesses = saved.split(',').filter { it.length == answer.length }.map { WordGuess(it, Words.score(it, answer)) }
        _state.value = SoloWordsUiState(daily = true, answer = answer, guesses = guesses, level = level)
    }

    fun playPractice(level: WordLevel = _state.value.level) {
        _state.value = SoloWordsUiState(daily = false, answer = WordList.random(level), level = level)
    }

    fun type(letter: Char) = _state.update {
        if (it.done || it.input.length >= it.length) it else it.copy(input = it.input + letter.lowercaseChar(), message = null)
    }

    fun delete() = _state.update { it.copy(input = it.input.dropLast(1), message = null) }

    fun submit() {
        val s = _state.value
        if (s.done || s.input.length < s.length) return
        if (!WordList.isWord(s.input)) {
            _state.update { it.copy(message = "\"${s.input.uppercase()}\" isn't in the word list.") }
            return
        }
        // Hard mode: every letter found must be used again.
        if (com.pippop.data.ProfileStore.get(getApplication()).profile.value.wordsHard) {
            Words.hardMiss(s.guesses.mapNotNull { g -> g.word?.let { it to g.score } }, s.input)?.let { miss ->
                _state.update { it.copy(message = "$miss (hard mode).") }
                return
            }
        }
        val guesses = s.guesses + WordGuess(s.input, Words.score(s.input, s.answer))
        _state.update { it.copy(guesses = guesses, input = "", message = null) }
        // Only a guess made now can end the round: opening today's finished word again is not a result.
        val after = _state.value
        if (after.done) {
            val store = SoloRecordStore.current
            val level = if (after.daily == true) "daily" else after.level.key
            if (after.solved) store?.record(GameKind.WORDS, level, SoloMetric.TRIES, after.guesses.size.toLong())
            else store?.record(GameKind.WORDS, level, SoloMetric.TRIES, null)
            store?.recordRun(GameKind.WORDS, level, SoloMetric.SOLVED_IN_ROW, after.solved)
        }
        if (s.daily == true) {
            prefs.edit {
                putString(KEY_DATE, LocalDate.now().toString())
                putString(KEY_GUESSES, guesses.joinToString(",") { it.word.orEmpty() })
            }
        }
    }

    private companion object {
        const val KEY_DATE = "daily_date"
        const val KEY_GUESSES = "daily_guesses"
    }
}
