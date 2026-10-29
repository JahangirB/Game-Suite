package com.pippop.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pippop.R
import com.pippop.data.ProfileStore
import com.pippop.data.Reaction
import kotlin.random.Random

/**
 * Every sound the app plays (docs/SFX.md, priority 1), made by scripts/make-sounds.py. [volume]
 * is this sound's level in the mix: taps and ticks sit low, wins and blasts high. Sounds heard
 * very often are [varied]: each play is a little higher or lower, so they don't feel mechanical.
 */
enum class Sound(val res: Int, val volume: Float, val varied: Boolean = false) {
    TAP(R.raw.sfx_tap, 0.45f, varied = true),
    ERROR(R.raw.sfx_error, 0.65f),
    YOUR_TURN(R.raw.sfx_your_turn, 0.65f),
    COUNTDOWN_TICK(R.raw.sfx_countdown_tick, 0.45f),
    COUNTDOWN_GO(R.raw.sfx_countdown_go, 0.65f),
    WIN(R.raw.sfx_win, 0.9f),
    LOSE(R.raw.sfx_lose, 0.9f),
    TIE(R.raw.sfx_tie, 0.9f),
    CARD_DEAL(R.raw.sfx_card_deal, 0.45f, varied = true),
    CARD_SLIDE(R.raw.sfx_card_slide, 0.65f, varied = true),
    CARD_FLIP(R.raw.sfx_card_flip, 0.65f, varied = true),
    DICE_ROLL(R.raw.sfx_dice_roll, 0.65f),
    DICE_LAND(R.raw.sfx_dice_land, 0.65f, varied = true),
    PIECE_HOP(R.raw.sfx_piece_hop, 0.45f, varied = true),
    PIECE_PLACE(R.raw.sfx_piece_place, 0.65f, varied = true),
    LETTER_TYPE(R.raw.sfx_letter_type, 0.45f, varied = true),
    LETTER_DROP(R.raw.sfx_letter_drop, 0.65f, varied = true),
    LETTER_RIGHT(R.raw.sfx_letter_right, 0.65f),
    MARK(R.raw.sfx_mark, 0.45f, varied = true),
    SUDOKU_DIGIT(R.raw.sfx_sudoku_digit, 0.45f, varied = true),
    SHIPS_FIRE(R.raw.sfx_ships_fire, 0.9f),
    SHIPS_HIT(R.raw.sfx_ships_hit, 0.9f),
    SHIPS_MISS(R.raw.sfx_ships_miss, 0.65f),
    SHIPS_SUNK(R.raw.sfx_ships_sunk, 0.9f),
    C4_DROP(R.raw.sfx_c4_drop, 0.65f),
    C4_LAND(R.raw.sfx_c4_land, 0.65f, varied = true),
    DOTS_BOX(R.raw.sfx_dots_box, 0.65f),
    MEMORY_MATCH(R.raw.sfx_memory_match, 0.65f),
    WORDS_SOLVED(R.raw.sfx_words_solved, 0.9f),
    DRAW_CORRECT(R.raw.sfx_draw_correct, 0.9f),
    TTT_X(R.raw.sfx_ttt_x, 0.45f),
    TTT_O(R.raw.sfx_ttt_o, 0.45f),
    NUMBERS_SUM_RIGHT(R.raw.sfx_numbers_sum_right, 0.65f),
    HOCKEY_HIT(R.raw.sfx_hockey_hit, 0.65f, varied = true),
    HOCKEY_WALL(R.raw.sfx_hockey_wall, 0.45f),
    HOCKEY_GOAL(R.raw.sfx_hockey_goal, 0.9f),
    LUDO_OUT(R.raw.sfx_ludo_out, 0.65f),
    LUDO_CAPTURE(R.raw.sfx_ludo_capture, 0.9f),
    LUDO_HOME(R.raw.sfx_ludo_home, 0.65f),
    GRID_TRACE(R.raw.sfx_grid_trace, 0.45f),
    GRID_WORD(R.raw.sfx_grid_word, 0.65f),
    BALLOON_STRETCH(R.raw.sfx_balloon_stretch, 0.65f),
    BALLOON_POP(R.raw.sfx_balloon_pop, 0.65f),
    CODE_PEG(R.raw.sfx_code_peg, 0.45f, varied = true),
    CODE_PIN_RED(R.raw.sfx_code_pin_red, 0.45f),
    CODE_PIN_WHITE(R.raw.sfx_code_pin_white, 0.45f),
    CODE_CRACKED(R.raw.sfx_code_cracked, 0.9f),
    SLIDE_SOLVED(R.raw.sfx_slide_solved, 0.9f),
    BINGO_BALL(R.raw.sfx_bingo_ball, 0.65f),
    BINGO_DAB(R.raw.sfx_bingo_dab, 0.45f, varied = true),
    BINGO_LINE_READY(R.raw.sfx_bingo_line_ready, 0.65f),
    BINGO_SHOUT(R.raw.sfx_bingo_shout, 0.9f),
    GOLF_PUTT(R.raw.sfx_golf_putt, 0.65f, varied = true),
    GOLF_WALL(R.raw.sfx_golf_wall, 0.45f),
    GOLF_CUP(R.raw.sfx_golf_cup, 0.65f),
    GOLF_SPLASH(R.raw.sfx_golf_splash, 0.6f),
    GOLF_DROP(R.raw.sfx_golf_drop, 0.5f),
    /** A sticker's song, 11 s; kept at 32 kHz mono so it loads with the rest and starts at once. */
    KYLIAN_DICTADOR(R.raw.sfx_kylian_dictador, 0.9f),
}

/** The sound a reaction plays on the phones it's sent to (stickers have one), or null. */
val Reaction.sound: Sound?
    get() = when (this) {
        Reaction.KYLIAN -> Sound.KYLIAN_DICTADOR
        else -> null
    }

/**
 * Plays [Sound]s through one small SoundPool, loaded once. Only the app's own Sound switch (in
 * the profile) turns them off: the phone's silent or vibrate mode doesn't, and the media volume
 * sets how loud they are. Every sound goes with something on screen (and most with a buzz), so
 * with Sound off nothing is missed.
 */
class Sfx private constructor(context: Context) {
    private val store = ProfileStore.get(context)
    private val attributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
    private val loaded = Sound.entries.associateWith { pool.load(context, it.res, 1) }

    /** Plays [sound]; [volume] below 1 for other players' events, which sound a little quieter. */
    fun play(sound: Sound, volume: Float = 1f, pitch: Float = 1f) {
        if (!store.profile.value.sound) return
        val id = loaded[sound] ?: return
        val level = (sound.volume * volume).coerceIn(0f, 1f)
        val rate = (pitch * if (sound.varied) 0.95f + Random.nextFloat() * 0.1f else 1f).coerceIn(0.5f, 2f)
        pool.play(id, level, level, 1, 0, rate)
    }

    companion object {
        /** Other players' moves play at this share of the volume (about −4 dB). */
        const val OTHERS = 0.6f

        @Volatile
        private var instance: Sfx? = null

        fun get(context: Context): Sfx = instance ?: synchronized(this) {
            instance ?: Sfx(context.applicationContext).also { instance = it }
        }
    }
}

/** The app's [Sfx], for a screen. */
@Composable
fun rememberSfx(): Sfx {
    val context = LocalContext.current
    return remember { Sfx.get(context) }
}

private object Unseen

/**
 * Plays sounds with [block] each time [key] changes to something new, such as a move
 * arriving, but not when the screen first shows the current state (or [key] is null). Put it
 * where that change's animation starts, so the sound and the movement line up.
 */
@Composable
fun SoundOnChange(key: Any?, block: suspend Sfx.() -> Unit) {
    val sfx = rememberSfx()
    val seen = remember { arrayOf<Any?>(Unseen) }
    LaunchedEffect(key) {
        val first = seen[0] === Unseen
        seen[0] = key
        if (!first && key != null) sfx.block()
    }
}
