package com.pippop.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** How this phone meets others: nearby over Bluetooth and Wi‑Fi, or online through a server. */
enum class PlayMode { OFFLINE, ONLINE }

/** How the ships look in Ship Battle; each player picks their own. */
enum class ShipStyle(val label: String) { TOY("Toy Harbor"), PIRATE("Pirate Wood") }

/** A game that was played, and when (epoch milliseconds), for the home's "jump back in" row. */
data class RecentGame(val game: GameKind, val at: Long)

/** Sudoku's wins and best time at one level, as this file kept them before [SoloRecordStore]; read once to bring them across. */
data class LevelStats(val wins: Int = 0, val bestMs: Long? = null)

/** Games won and lost with others. (Sudoku's solo bests moved to [SoloRecordStore].) */
data class Stats(
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
)

/**
 * The last person you played online with, so one tap reconnects: same server, same
 * secret code, and your player id in that room.
 */
data class SavedPartner(
    val serverUrl: String,
    val password: String,
    val code: String,
    val name: String,
    val avatar: Avatar?,
    val deviceId: String?,
    val myPlayerId: String?,
)

/** Everything the player sets up in the profile screen, plus their win statistics. */
data class Profile(
    val name: String = "",
    val avatar: Avatar? = null,
    /** The battle server: a Google Apps Script link or a Cloudflare Worker link. */
    val scriptUrl: String = "",
    /** Only needed for a Cloudflare server whose host set a password. */
    val serverPassword: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val shipStyle: ShipStyle = ShipStyle.TOY,
    val stats: Stats = Stats(),
    val partner: SavedPartner? = null,
    /** This install's permanent id; lets a partner invite you again. */
    val deviceId: String = "",
    /** The last nearby room's code, so the same code reconnects the same two phones later. */
    val nearbyCode: String = "",
    /** The home's Online/Offline switch, as last set. */
    val playMode: PlayMode = PlayMode.OFFLINE,
    /** The games in the order this player arranged them on the home. */
    val gameOrder: List<GameKind> = GameKind.entries,
    /** Whether the first-run screens have been seen (or skipped, by someone set up before them). */
    val firstRunDone: Boolean = false,
    /** Buzz for game moments (your turn, a move, a win). */
    val vibration: Boolean = true,
    /** How loud the sound effects are, or off; in the app only (the phone's silent mode doesn't change them). */
    val loudness: Loudness = Loudness.NORMAL,
    /** Whether other players' moves make their sounds too (a little quieter than your own). */
    val othersMoves: Boolean = true,
    /** Corner Blocks: show where your pieces can go ("Where can I go?"). */
    val blocksHints: Boolean = true,
    /** Word Race: hard mode (letters found must be used again). */
    val wordsHard: Boolean = false,
    /** Letter games (Word Race, later Code Breaker): a small mark on each result tile besides its colour. */
    val letterMarks: Boolean = true,
    /** Mini Golf's look: "day", "sunset", "night" or "mixed" (each hole picks one). */
    val golfTime: String = "day",
    /**
     * The notification question: due once an invitation has been sent or received, and
     * answered (allowed or "Not now") for good, so it is never asked again on every launch.
     */
    val notificationsDue: Boolean = false,
    val notificationsAsked: Boolean = false,
    /** Games whose tutorial has been seen (it plays the first time a game is opened). */
    val tutorialsSeen: Set<GameKind> = emptySet(),
    /** The last six different games played, newest first. */
    val recentGames: List<RecentGame> = emptyList(),
    /** Games taken off the shelf on the arrange screen; they stay in [gameOrder]. */
    val putAway: Set<GameKind> = emptySet(),
    /** True once the order has been changed, so the home can stop saying how. */
    val arrangedOnce: Boolean = false,
    /** The last solo setup of each game that has one, as picked (see [soloSetup] for what it plays with). */
    val soloSetups: Map<GameKind, Map<String, String>> = emptyMap(),
    /** The latest month whose cup has been shown ("a month ends with a moment"), or null before the first. */
    val cupShown: java.time.YearMonth? = null,
) {
    /** What [game] starts with alone: the last setup, with the default for anything not picked yet or no longer known. */
    fun soloSetup(game: GameKind): Map<String, String> = SoloSetups.resolve(game, soloSetups[game])

    /** Whether any sound plays at all. */
    val sound: Boolean get() = loudness != Loudness.OFF
}

/** One instance per process, shared by every view model, backed by SharedPreferences. */
class ProfileStore private constructor(private val prefs: SharedPreferences) {
    private var lastAudible = Loudness.NORMAL
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    fun setName(value: String) {
        val name = value.take(MAX_NAME)
        prefs.edit { putString(KEY_NAME, name) }
        _profile.update { it.copy(name = name) }
    }

    fun setAvatar(value: Avatar?) {
        prefs.edit { putString(KEY_AVATAR, value?.key) }
        _profile.update { it.copy(avatar = value) }
    }

    fun setServerPassword(value: String) {
        val password = value.trim()
        prefs.edit { putString(KEY_PASSWORD, password) }
        _profile.update { it.copy(serverPassword = password) }
    }

    fun setScriptUrl(value: String) {
        val url = value.trim()
        prefs.edit { putString(KEY_URL, url) }
        _profile.update { it.copy(scriptUrl = url) }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _profile.update { it.copy(theme = mode) }
    }

    fun setShipStyle(style: ShipStyle) {
        prefs.edit { putString(KEY_SHIP_STYLE, style.name) }
        _profile.update { it.copy(shipStyle = style) }
    }

    fun setPartner(partner: SavedPartner?) {
        prefs.edit {
            if (partner == null) {
                PARTNER_KEYS.forEach { remove(it) }
            } else {
                putString(KEY_P_URL, partner.serverUrl)
                putString(KEY_P_PASSWORD, partner.password)
                putString(KEY_P_CODE, partner.code)
                putString(KEY_P_NAME, partner.name)
                putString(KEY_P_AVATAR, partner.avatar?.key)
                putString(KEY_P_DEVICE, partner.deviceId)
                putString(KEY_P_PLAYER, partner.myPlayerId)
            }
        }
        _profile.update { it.copy(partner = partner) }
    }

    /** Saves the home's game order; null puts the games back in the default order. */
    fun setGameOrder(order: List<GameKind>?) {
        prefs.edit {
            if (order == null) remove(KEY_GAME_ORDER) else putString(KEY_GAME_ORDER, order.joinToString(",") { it.key })
            putBoolean(KEY_ARRANGED_ONCE, true)
        }
        _profile.update { it.copy(gameOrder = orderOf(order?.joinToString(",") { it.key }), arrangedOnce = true) }
    }

    /** Saves the games taken off the shelf (none: all back). Nothing is deleted; [Profile.gameOrder] still holds them. */
    fun setPutAway(games: Set<GameKind>) {
        prefs.edit {
            putString(KEY_PUT_AWAY, games.joinToString(",") { it.key })
            putBoolean(KEY_ARRANGED_ONCE, true)
        }
        _profile.update { it.copy(putAway = games, arrangedOnce = true) }
    }

    /** Remembers that [month]'s cup has been shown, so it is not shown again. */
    fun setCupShown(month: java.time.YearMonth) {
        prefs.edit { putString(KEY_CUP_SHOWN, month.toString()) }
        _profile.update { it.copy(cupShown = month) }
    }

    /** A game has started: it goes to the front of [Profile.recentGames], once, and only six are kept. */
    fun noteGamePlayed(game: GameKind, at: Long = System.currentTimeMillis()) {
        val recent = (listOf(RecentGame(game, at)) + _profile.value.recentGames.filter { it.game != game }).take(MAX_RECENT)
        prefs.edit { putString(KEY_RECENT, recent.joinToString(",") { "${it.game.key}:${it.at}" }) }
        _profile.update { it.copy(recentGames = recent) }
    }

    /** Drops only the recent games that match [entries] exactly (game and time); a later real play of the same game stays. */
    fun removeRecent(entries: Collection<RecentGame>) {
        if (entries.isEmpty()) return
        val recent = _profile.value.recentGames.filter { it !in entries }
        prefs.edit { putString(KEY_RECENT, recent.joinToString(",") { "${it.game.key}:${it.at}" }) }
        _profile.update { it.copy(recentGames = recent) }
    }

    /** The preview's sample recent games, as [setSampleRecent] saved them. */
    fun sampleRecent(): List<RecentGame> = recentOf(prefs.getString(KEY_SAMPLE_RECENT, null))

    /** Remembers which recent games the preview's Fill added (none: forgets). Preview only. */
    fun setSampleRecent(entries: List<RecentGame>) {
        prefs.edit { if (entries.isEmpty()) remove(KEY_SAMPLE_RECENT) else putString(KEY_SAMPLE_RECENT, entries.joinToString(",") { "${it.game.key}:${it.at}" }) }
    }

    /** An invitation went out or came in: from now on the home offers to turn on notifications. */
    fun notificationsNeeded() {
        if (_profile.value.notificationsDue) return
        prefs.edit { putBoolean(KEY_NOTIFY_DUE, true) }
        _profile.update { it.copy(notificationsDue = true) }
    }

    fun notificationsAnswered() {
        prefs.edit { putBoolean(KEY_NOTIFY_ASKED, true) }
        _profile.update { it.copy(notificationsAsked = true) }
    }

    fun tutorialSeen(kind: GameKind) {
        val seen = _profile.value.tutorialsSeen + kind
        prefs.edit { putString(KEY_TUTORIALS, seen.joinToString(",") { it.key }) }
        _profile.update { it.copy(tutorialsSeen = seen) }
    }

    /** Remembers how [game] was last set up to play alone. */
    fun setSoloSetup(game: GameKind, values: Map<String, String>) {
        if (SoloSetups.spec(game) == null) return
        val setup = SoloSetups.resolve(game, values)
        prefs.edit { putString(KEY_SOLO_PREFIX + game.key, SoloSetups.encode(setup)) }
        _profile.update { it.copy(soloSetups = it.soloSetups + (game to setup)) }
    }

    fun setGolfTime(time: String) {
        prefs.edit { putString(KEY_GOLF_TIME, time) }
        _profile.update { it.copy(golfTime = time) }
    }

    fun setWordsHard(on: Boolean) {
        prefs.edit { putBoolean(KEY_WORDS_HARD, on) }
        _profile.update { it.copy(wordsHard = on) }
    }

    fun setLetterMarks(on: Boolean) {
        prefs.edit { putBoolean(KEY_LETTER_MARKS, on) }
        _profile.update { it.copy(letterMarks = on) }
    }

    fun setBlocksHints(on: Boolean) {
        prefs.edit { putBoolean(KEY_BLOCKS_HINTS, on) }
        _profile.update { it.copy(blocksHints = on) }
    }

    fun setLoudness(value: Loudness) {
        if (value != Loudness.OFF) lastAudible = value
        prefs.edit { putString(KEY_LOUDNESS, value.name) }
        _profile.update { it.copy(loudness = value) }
    }

    /** The game menu's on/off: off, or back to the last level that was on (Normal if none). */
    fun setSound(on: Boolean) = setLoudness(if (on) lastAudible else Loudness.OFF)

    fun setOthersMoves(on: Boolean) {
        prefs.edit { putBoolean(KEY_OTHERS_MOVES, on) }
        _profile.update { it.copy(othersMoves = on) }
    }

    fun setVibration(on: Boolean) {
        prefs.edit { putBoolean(KEY_VIBRATION, on) }
        _profile.update { it.copy(vibration = on) }
    }

    fun finishFirstRun() {
        prefs.edit { putBoolean(KEY_FIRST_RUN_DONE, true) }
        _profile.update { it.copy(firstRunDone = true) }
    }

    fun setPlayMode(mode: PlayMode) {
        prefs.edit { putString(KEY_PLAY_MODE, mode.name) }
        _profile.update { it.copy(playMode = mode) }
    }

    fun setNearbyCode(code: String) {
        prefs.edit { putString(KEY_NEARBY_CODE, code) }
        _profile.update { it.copy(nearbyCode = code) }
    }

    fun recordBattle(won: Boolean) {
        val stats = _profile.value.stats
        val key = if (won) KEY_BATTLES_WON else KEY_BATTLES_LOST
        prefs.edit { putInt(key, (if (won) stats.battlesWon else stats.battlesLost) + 1) }
        _profile.update {
            it.copy(
                stats = if (won) stats.copy(battlesWon = stats.battlesWon + 1)
                else stats.copy(battlesLost = stats.battlesLost + 1),
            )
        }
    }

    private fun load(): Profile {
        val deviceId = prefs.getString(KEY_DEVICE, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE, it) }
        }
        val partner = prefs.getString(KEY_P_CODE, null)?.let { code ->
            SavedPartner(
                serverUrl = prefs.getString(KEY_P_URL, "").orEmpty(),
                password = prefs.getString(KEY_P_PASSWORD, "").orEmpty(),
                code = code,
                name = prefs.getString(KEY_P_NAME, "").orEmpty(),
                avatar = Avatar.from(prefs.getString(KEY_P_AVATAR, null)),
                deviceId = prefs.getString(KEY_P_DEVICE, null),
                myPlayerId = prefs.getString(KEY_P_PLAYER, null),
            )
        }
        return Profile(
            partner = partner,
            deviceId = deviceId,
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            avatar = Avatar.from(prefs.getString(KEY_AVATAR, null)),
            scriptUrl = prefs.getString(KEY_URL, "").orEmpty(),
            serverPassword = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, "").orEmpty()) }
                .getOrDefault(ThemeMode.SYSTEM),
            shipStyle = runCatching { ShipStyle.valueOf(prefs.getString(KEY_SHIP_STYLE, "").orEmpty()) }
                .getOrDefault(ShipStyle.TOY),
            stats = Stats(prefs.getInt(KEY_BATTLES_WON, 0), prefs.getInt(KEY_BATTLES_LOST, 0)),
            nearbyCode = prefs.getString(KEY_NEARBY_CODE, "").orEmpty(),
            gameOrder = orderOf(prefs.getString(KEY_GAME_ORDER, null)),
            // Someone who set a name before the first-run screens existed has nothing to learn there.
            firstRunDone = prefs.getBoolean(KEY_FIRST_RUN_DONE, prefs.getString(KEY_NAME, "").orEmpty().isNotBlank()),
            vibration = prefs.getBoolean(KEY_VIBRATION, true),
            loudness = loudnessOf(prefs).also { if (it != Loudness.OFF) lastAudible = it },
            othersMoves = prefs.getBoolean(KEY_OTHERS_MOVES, true),
            blocksHints = prefs.getBoolean(KEY_BLOCKS_HINTS, true),
            wordsHard = prefs.getBoolean(KEY_WORDS_HARD, false),
            letterMarks = prefs.getBoolean(KEY_LETTER_MARKS, true),
            golfTime = prefs.getString(KEY_GOLF_TIME, "day") ?: "day",
            notificationsDue = prefs.getBoolean(KEY_NOTIFY_DUE, false),
            notificationsAsked = prefs.getBoolean(KEY_NOTIFY_ASKED, false),
            tutorialsSeen = prefs.getString(KEY_TUTORIALS, "").orEmpty().split(",").mapNotNull { k -> GameKind.entries.firstOrNull { it.key == k } }.toSet(),
            recentGames = recentOf(prefs.getString(KEY_RECENT, null)),
            putAway = gamesOf(prefs.getString(KEY_PUT_AWAY, null)),
            // Someone who arranged the games before this was kept has already learned how.
            arrangedOnce = prefs.getBoolean(KEY_ARRANGED_ONCE, prefs.contains(KEY_GAME_ORDER)),
            soloSetups = GameKind.entries.mapNotNull { game ->
                prefs.getString(KEY_SOLO_PREFIX + game.key, null)?.let { game to SoloSetups.resolve(game, SoloSetups.decode(it)) }
            }.filter { it.second.isNotEmpty() }.toMap(),
            cupShown = runCatching { java.time.YearMonth.parse(prefs.getString(KEY_CUP_SHOWN, null)) }.getOrNull(),
            // Before the switch existed, someone with a server link played online.
            playMode = runCatching { PlayMode.valueOf(prefs.getString(KEY_PLAY_MODE, "").orEmpty()) }.getOrElse {
                if (prefs.getString(KEY_URL, "").orEmpty().startsWith("https://")) PlayMode.ONLINE else PlayMode.OFFLINE
            },
        )
    }

    companion object {
        const val MAX_NAME = 16
        const val MAX_RECENT = 6
        // These two keys predate the profile screen; keep them so saved settings carry over.
        private const val KEY_URL = "script_url"
        private const val KEY_NAME = "player_name"
        private const val KEY_THEME = "theme"
        private const val KEY_SHIP_STYLE = "ship_style"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_P_URL = "partner_url"
        private const val KEY_P_PASSWORD = "partner_password"
        private const val KEY_P_CODE = "partner_code"
        private const val KEY_P_NAME = "partner_name"
        private const val KEY_P_AVATAR = "partner_avatar"
        private const val KEY_P_DEVICE = "partner_device"
        private const val KEY_P_PLAYER = "partner_player_id"
        private val PARTNER_KEYS = listOf(KEY_P_URL, KEY_P_PASSWORD, KEY_P_CODE, KEY_P_NAME, KEY_P_AVATAR, KEY_P_DEVICE, KEY_P_PLAYER)
        private const val KEY_PASSWORD = "server_password"
        private const val KEY_NEARBY_CODE = "nearby_code"
        private const val KEY_PLAY_MODE = "play_mode"
        private const val KEY_GAME_ORDER = "game_order"
        private const val KEY_FIRST_RUN_DONE = "first_run_done"
        private const val KEY_VIBRATION = "vibration"
        /** The old on/off sound setting: read once to make [KEY_LOUDNESS], never written or removed. */
        private const val KEY_SOUND = "sound"
        private const val KEY_LOUDNESS = "sound_loudness"
        private const val KEY_OTHERS_MOVES = "sound_others_moves"
        private const val KEY_BLOCKS_HINTS = "blocks_hints"
        private const val KEY_WORDS_HARD = "words_hard"
        private const val KEY_LETTER_MARKS = "letter_marks"
        private const val KEY_GOLF_TIME = "golf_time"
        private const val KEY_NOTIFY_DUE = "notifications_due"
        private const val KEY_NOTIFY_ASKED = "notifications_asked"
        private const val KEY_TUTORIALS = "tutorials_seen"
        private const val KEY_RECENT = "recent_games"
        /** Preview app only: the recent games its Fill added, so Clear takes out just those. */
        private const val KEY_SAMPLE_RECENT = "preview_sample_recent"
        private const val KEY_PUT_AWAY = "put_away"
        /** The last month whose trophy moment was shown, as "2026-10". */
        private const val KEY_CUP_SHOWN = "cup_shown_month"
        private const val KEY_ARRANGED_ONCE = "arranged_once"
        /** A game's last solo setup is kept under this and the game's key. */
        private const val KEY_SOLO_PREFIX = "solo_setup_"

        /**
         * The saved loudness. Before it existed, sound was on or off (`sound`): on becomes Normal,
         * off becomes Off, written to the new key the first time. The old key stays as it was, and
         * once the new one is there it alone decides, so running this again changes nothing.
         */
        internal fun loudnessOf(prefs: SharedPreferences): Loudness {
            val saved = prefs.getString(KEY_LOUDNESS, null)?.let { s -> Loudness.entries.firstOrNull { it.name == s } }
            if (saved != null) return saved
            val migrated = if (prefs.getBoolean(KEY_SOUND, true)) Loudness.NORMAL else Loudness.OFF
            prefs.edit { putString(KEY_LOUDNESS, migrated.name) }
            return migrated
        }

        /** Games from a comma list of keys; unknown keys and repeats are left out. */
        fun gamesOf(saved: String?): Set<GameKind> =
            saved.orEmpty().split(",").mapNotNull { key -> GameKind.entries.firstOrNull { it.key == key } }.toSet()

        /** Recent games from "key:millis" pairs, newest first; anything unreadable is left out. */
        fun recentOf(saved: String?): List<RecentGame> =
            saved.orEmpty().split(",").mapNotNull { pair ->
                val (key, at) = pair.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
                val game = GameKind.entries.firstOrNull { it.key == key } ?: return@mapNotNull null
                RecentGame(game, at.toLongOrNull() ?: return@mapNotNull null)
            }.sortedByDescending { it.at }.distinctBy { it.game }.take(MAX_RECENT)

        /** The saved order, with games added since (or not known any more) put right. */
        fun orderOf(saved: String?): List<GameKind> {
            val known = saved.orEmpty().split(",").mapNotNull { key -> GameKind.entries.firstOrNull { it.key == key } }.distinct()
            return known + (GameKind.entries - known.toSet())
        }
        private const val KEY_BATTLES_WON = "battles_won"
        private const val KEY_BATTLES_LOST = "battles_lost"

        /**
         * Sudoku's bests as older versions wrote them here (`wins_<level>`, `best_<level>`). Nothing
         * writes these keys any more and nothing removes them: [SoloRecordStore] copies them across.
         */
        fun oldSudokuBests(context: Context): Map<Difficulty, LevelStats> = oldSudokuBests(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
        )

        internal fun oldSudokuBests(prefs: SharedPreferences): Map<Difficulty, LevelStats> = Difficulty.entries.associateWith {
            LevelStats(
                wins = prefs.getInt("wins_${it.key}", 0),
                bestMs = if (prefs.contains("best_${it.key}")) prefs.getLong("best_${it.key}", 0) else null,
            )
        }

        // The file keeps the app's first name: renaming it would lose everyone's profile.
        private const val FILE = "arcade_sudoku"

        @Volatile
        private var instance: ProfileStore? = null

        fun get(context: Context): ProfileStore = instance ?: synchronized(this) {
            instance ?: ProfileStore(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
