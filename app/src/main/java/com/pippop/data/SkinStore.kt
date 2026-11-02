package com.pippop.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pippop.BuildConfig
import com.pippop.ui.art.SkinArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A skin listed online: what the store shows before it's downloaded. */
data class RemoteSkin(val id: String, val name: String, val tag: String, val version: Int, val size: Int)

/** The online list of skins: not set up in this build, on its way, there, or it couldn't be fetched. */
sealed interface SkinCatalog {
    data object NotSetUp : SkinCatalog
    data object Loading : SkinCatalog
    data class Ready(val skins: List<RemoteSkin>) : SkinCatalog
    data class Failed(val why: String) : SkinCatalog
}

/**
 * Ship Battle skins you download in the game. New skins are files published in this project's
 * skins/ folder (scripts/make-skins.js makes them): the store lists skins/index.json, shows each
 * one's picture (<id>.png) and downloads <id>.json. Nothing about a skin is built into the app,
 * so a new one needs no app update. Downloads are kept on the phone and work offline.
 *
 * The address comes from the build (BuildConfig.SKINS_BASE_URL, the repository CI built from),
 * so every copy of this project lists its own skins. Local builds have none.
 */
object SkinStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dir: File? = null
    private var prefs: android.content.SharedPreferences? = null

    private val _installed = MutableStateFlow<List<SkinArt>>(emptyList())
    /** The skins on this phone, in the order they were downloaded. */
    val installed: StateFlow<List<SkinArt>> = _installed.asStateFlow()

    private val _catalog = MutableStateFlow<SkinCatalog>(if (BuildConfig.SKINS_BASE_URL.isEmpty()) SkinCatalog.NotSetUp else SkinCatalog.Loading)
    val catalog: StateFlow<SkinCatalog> = _catalog.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    /** Pictures of the skins listed online, as they arrive. */
    val previews: StateFlow<Map<String, ImageBitmap>> = _previews.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    /** The downloaded skin in use, or null for the built-in style picked in the profile. */
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _seen = MutableStateFlow<Set<String>>(emptySet())
    /** Skins listed online that you've already looked at (the rest are New). */
    val seen: StateFlow<Set<String>> = _seen.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** A download that went wrong, to show once. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun init(context: Context) {
        if (dir != null) return
        val app = context.applicationContext
        dir = File(app.filesDir, "skins").apply { mkdirs() }
        prefs = app.getSharedPreferences("skins", Context.MODE_PRIVATE).also { p ->
            _selected.value = p.getString(KEY_SELECTED, null)
            _seen.value = p.getStringSet(KEY_SEEN, emptySet()).orEmpty().toSet()
        }
        scope.launch {
            val order = prefs?.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }
            val files = dir?.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            val read = files.mapNotNull { f -> runCatching { SkinArt.parse(f.readText()) }.getOrNull() }
            _installed.value = read.sortedBy { order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            refresh()
        }
    }

    /** The skin in use as a look, once the phone has read it. */
    fun art(id: String?) = id?.let { want -> _installed.value.firstOrNull { it.id == want } }

    /** Fetches the online list again (and the pictures not fetched yet). */
    fun refresh() {
        val base = BuildConfig.SKINS_BASE_URL
        if (base.isEmpty()) return
        scope.launch {
            if (_catalog.value !is SkinCatalog.Ready) _catalog.value = SkinCatalog.Loading
            _catalog.value = try {
                val o = JSONObject(fetch("$base/index.json").decodeToString())
                val list = o.getJSONArray("skins")
                SkinCatalog.Ready(
                    (0 until list.length()).map { i ->
                        val k = list.getJSONObject(i)
                        RemoteSkin(k.getString("id"), k.getString("name"), k.optString("tag"), k.optInt("version", 1), k.optInt("size"))
                    }.filter { it.id.matches(ID) },
                )
            } catch (e: Exception) {
                if (_catalog.value is SkinCatalog.Ready) _catalog.value else SkinCatalog.Failed("Couldn't reach the skin list. Check the internet and try again.")
            }
            (_catalog.value as? SkinCatalog.Ready)?.skins?.forEach { skin ->
                if (skin.id in _previews.value) return@forEach
                runCatching {
                    val bytes = fetch("$base/${skin.id}.png")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()?.let { picture -> _previews.update { it + (skin.id to picture) } }
            }
        }
    }

    /** Downloads [skin] and keeps it; it's ready to use when it shows under On your phone. */
    fun download(skin: RemoteSkin, use: Boolean = false) {
        val base = BuildConfig.SKINS_BASE_URL
        if (base.isEmpty() || skin.id in _downloading.value) return
        _downloading.update { it + skin.id }
        scope.launch {
            try {
                val text = fetch("$base/${skin.id}.json").decodeToString()
                val art = SkinArt.parse(text)
                require(art.id == skin.id) { "wrong skin" }
                File(dir, "${skin.id}.json").writeText(text)
                _installed.update { list -> list.filter { it.id != art.id } + art }
                saveOrder()
                if (use) use(art.id)
            } catch (e: Exception) {
                _message.value = "Couldn't download ${skin.name}. Check the internet and try again."
            } finally {
                _downloading.update { it - skin.id }
            }
        }
    }

    /** Uses the downloaded skin [id] in Ship Battle; null goes back to the built-in style. */
    fun use(id: String?) {
        _selected.value = id
        prefs?.edit()?.putString(KEY_SELECTED, id)?.apply()
    }

    /** Takes [id] off the phone (it stays listed online). */
    fun remove(id: String) {
        File(dir, "$id.json").delete()
        _installed.update { list -> list.filter { it.id != id } }
        saveOrder()
        if (_selected.value == id) use(null)
    }

    /** Marks the skins listed online as looked at, so they're no longer New. */
    fun markSeen(ids: Collection<String>) {
        if (_seen.value.containsAll(ids)) return
        _seen.update { it + ids }
        prefs?.edit()?.putStringSet(KEY_SEEN, _seen.value)?.apply()
    }

    fun messageShown() {
        _message.value = null
    }

    private fun saveOrder() {
        prefs?.edit()?.putString(KEY_ORDER, _installed.value.joinToString(",") { it.id })?.apply()
    }

    private fun fetch(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private val ID = Regex("[a-z0-9-]{1,40}")
    private const val KEY_SELECTED = "selected"
    private const val KEY_SEEN = "seen"
    private const val KEY_ORDER = "order"
}
