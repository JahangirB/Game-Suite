package com.pippop.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What the profile shows: the setting and the log. */
data class QuietView(val enabled: Boolean = true, val entries: List<QuietEntry> = emptyList())

/** [QuietState] kept in its own preferences file; one per process, shared by the job and the screens. */
class QuietStore private constructor(private val prefs: SharedPreferences) : QuietState {
    private val _view = MutableStateFlow(QuietView(readEnabled(), readEntries()))
    val view: StateFlow<QuietView> = _view.asStateFlow()

    override var enabled: Boolean
        get() = readEnabled()
        set(value) {
            prefs.edit { putBoolean(KEY_ENABLED, value) }
            _view.value = _view.value.copy(enabled = value)
        }

    override val entries: List<QuietEntry> get() = readEntries()

    @Synchronized
    override fun add(entry: QuietEntry) {
        val next = readEntries().withEntry(entry)
        prefs.edit { putString(KEY_ENTRIES, write(next)) }
        _view.value = _view.value.copy(entries = next)
    }

    override var pending: QuietPending?
        get() = prefs.getString(KEY_PENDING, null)?.let {
            runCatching { JSONObject(it).let { j -> QuietPending(j.getInt("code"), j.optString("name"), j.optLong("at")) } }.getOrNull()
        }
        // Written at once: the app may be replaced a moment later.
        set(value) = prefs.edit(commit = true) {
            if (value == null) remove(KEY_PENDING)
            else putString(KEY_PENDING, JSONObject().put("code", value.versionCode).put("name", value.versionName).put("at", value.at).toString())
        }

    override var lastSeenVersionCode: Int
        get() = prefs.getInt(KEY_SEEN, 0)
        set(value) = prefs.edit { putInt(KEY_SEEN, value) }

    override var gameGoing: Boolean
        get() = prefs.getBoolean(KEY_GAME, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_GAME, value) }

    override var hintedKey: String?
        get() = prefs.getString(KEY_HINTED, null)
        set(value) = prefs.edit { putString(KEY_HINTED, value) }

    private fun readEnabled() = prefs.getBoolean(KEY_ENABLED, true)

    private fun readEntries(): List<QuietEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ENTRIES, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val j = array.getJSONObject(i)
            val outcome = QuietOutcome.entries.firstOrNull { it.name == j.optString("o") } ?: return@mapNotNull null
            QuietEntry(j.optLong("at"), outcome, j.optInt("code"), j.optString("name"), j.optString("why"))
        }
    }.getOrDefault(emptyList())

    private fun write(list: List<QuietEntry>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("at", it.at).put("o", it.outcome.name).put("code", it.versionCode).put("name", it.versionName).put("why", it.detail))
        }
    }.toString()

    companion object {
        private const val FILE = "quiet_update"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_PENDING = "pending"
        private const val KEY_SEEN = "last_seen_version_code"
        private const val KEY_HINTED = "hinted_key"
        private const val KEY_GAME = "game_going"

        @Volatile private var instance: QuietStore? = null

        fun get(context: Context): QuietStore = instance ?: synchronized(this) {
            instance ?: QuietStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)).also { instance = it }
        }

        /** A store over [prefs], for tests. */
        fun over(prefs: SharedPreferences) = QuietStore(prefs)
    }
}

/** One log line in words, with the time: "14 Nov, 09:12 · Installed by itself: 1.0.7-preview". */
fun quietLine(entry: QuietEntry, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val time = java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.UK)
        .format(java.time.Instant.ofEpochMilli(entry.at).atZone(zone))
    val build = entry.versionName.ifBlank { null }
    val what = when (entry.outcome) {
        QuietOutcome.UP_TO_DATE -> "Up to date"
        QuietOutcome.DOWNLOADED -> "Downloaded ${build.orEmpty()}".trim()
        QuietOutcome.INSTALLED -> "Installed by itself" + (build?.let { ": $it" } ?: "")
        QuietOutcome.NEEDED_TAP -> "Needed a tap" + (entry.detail.takeIf { it.isNotBlank() && it != "needs a tap" }?.let { ": $it" } ?: "")
        QuietOutcome.FAILED -> "Failed" + (entry.detail.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        QuietOutcome.SKIPPED -> "Skipped" + (entry.detail.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
    }
    return "$time · $what"
}
