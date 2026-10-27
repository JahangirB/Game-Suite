package com.arcade.sudoku.data

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

data class LevelStats(val wins: Int = 0, val bestMs: Long? = null)

data class Stats(
    val solo: Map<Difficulty, LevelStats> = emptyMap(),
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
) {
    val soloWins get() = solo.values.sumOf { it.wins }
}

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
)

/** One instance per process, shared by every view model, backed by SharedPreferences. */
class ProfileStore private constructor(private val prefs: SharedPreferences) {
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
        prefs.edit { if (order == null) remove(KEY_GAME_ORDER) else putString(KEY_GAME_ORDER, order.joinToString(",") { it.key }) }
        _profile.update { it.copy(gameOrder = orderOf(order?.joinToString(",") { it.key })) }
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

    fun recordSoloWin(difficulty: Difficulty, timeMs: Long) {
        val old = _profile.value.stats.solo[difficulty] ?: LevelStats()
        val best = old.bestMs?.let { minOf(it, timeMs) } ?: timeMs
        prefs.edit {
            putInt("wins_${difficulty.key}", old.wins + 1)
            putLong("best_${difficulty.key}", best)
        }
        _profile.update {
            it.copy(stats = it.stats.copy(solo = it.stats.solo + (difficulty to LevelStats(old.wins + 1, best))))
        }
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
        val solo = Difficulty.entries.associateWith {
            LevelStats(
                wins = prefs.getInt("wins_${it.key}", 0),
                bestMs = if (prefs.contains("best_${it.key}")) prefs.getLong("best_${it.key}", 0) else null,
            )
        }
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
            stats = Stats(solo, prefs.getInt(KEY_BATTLES_WON, 0), prefs.getInt(KEY_BATTLES_LOST, 0)),
            nearbyCode = prefs.getString(KEY_NEARBY_CODE, "").orEmpty(),
            gameOrder = orderOf(prefs.getString(KEY_GAME_ORDER, null)),
            // Someone who set a name before the first-run screens existed has nothing to learn there.
            firstRunDone = prefs.getBoolean(KEY_FIRST_RUN_DONE, prefs.getString(KEY_NAME, "").orEmpty().isNotBlank()),
            vibration = prefs.getBoolean(KEY_VIBRATION, true),
            // Before the switch existed, someone with a server link played online.
            playMode = runCatching { PlayMode.valueOf(prefs.getString(KEY_PLAY_MODE, "").orEmpty()) }.getOrElse {
                if (prefs.getString(KEY_URL, "").orEmpty().startsWith("https://")) PlayMode.ONLINE else PlayMode.OFFLINE
            },
        )
    }

    companion object {
        const val MAX_NAME = 16
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

        /** The saved order, with games added since (or not known any more) put right. */
        fun orderOf(saved: String?): List<GameKind> {
            val known = saved.orEmpty().split(",").mapNotNull { key -> GameKind.entries.firstOrNull { it.key == key } }.distinct()
            return known + (GameKind.entries - known.toSet())
        }
        private const val KEY_BATTLES_WON = "battles_won"
        private const val KEY_BATTLES_LOST = "battles_lost"

        @Volatile
        private var instance: ProfileStore? = null

        fun get(context: Context): ProfileStore = instance ?: synchronized(this) {
            instance ?: ProfileStore(
                context.applicationContext.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
