package com.pippop.data

import com.pippop.BuildConfig
import com.pippop.ui.art.toSticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * How a sticker from the downloadable packs travels: as a reaction under the key `p:<id>`, where the id
 * is lower-case letters, digits and hyphens, 32 at most. The three servers accept any key of this shape
 * and say so with the cap [CAP]. Today's app reads a key against its own reactions and drops one it does
 * not know, so it ignores a pack sticker.
 */
object PackKey {
    const val PREFIX = "p:"

    /** The server cap that says pack stickers are understood; the app sends a `p:` key only to a server that has it. */
    const val CAP = "packs"

    private val ID = Regex("[a-z0-9-]{1,32}")

    /** Whether [id] is a sticker id. A full match: nothing before, after or in between (not even a line break). */
    fun validId(id: String?): Boolean = id != null && ID.matches(id)

    fun key(id: String): String {
        require(validId(id)) { "bad sticker id" }
        return PREFIX + id
    }

    /** The id inside [key], or null when it is not a pack key of the right shape. */
    fun idOf(key: String?): String? = key?.takeIf { it.startsWith(PREFIX) }?.substring(PREFIX.length)?.takeIf { validId(it) }
}

/** One shape of a downloaded sticker: the form `ui/art/StickerArt.kt` holds (SVG path data, paint, animation). */
data class PackPart(
    val d: String,
    val fill: Long?,
    val stroke: Long?,
    val width: Float,
    val dash: List<Float>?,
    val roundCap: Boolean,
    val roundJoin: Boolean,
    /** An animation named in `ui/art/Stickers.kt` ("glow", "flame b", ...); a name nothing knows stays still. */
    val anim: String?,
    val group: Int,
)

/** What is behind a sticker: [type] is solid (one colour), linear (x1 y1 x2 y2 in [n]) or radial (cx cy r in [n]). */
data class PackBackdrop(val type: String, val colours: List<Long>, val n: List<Float>)

/** A sticker read from its file. It is data only: shapes, numbers and colours, never code. */
data class PackSticker(
    val id: String,
    val name: String,
    val version: Int,
    val backdrop: PackBackdrop?,
    val parts: List<PackPart>,
)

/** A sticker listed in `index.json`: what the packs sheet shows before it is downloaded. */
data class PackListing(val id: String, val name: String, val tag: String, val version: Int, val size: Int)

/**
 * Reads the files of `stickers/` defensively (see stickers/README.md for the limits). Anything that is not
 * a good file reads as null, so a bad file is ignored rather than crashing or drawing garbage.
 */
object StickerFiles {
    const val MAX_BYTES = 100_000
    const val MAX_PARTS = 400
    const val MAX_PATH = 8_000
    const val MAX_NAME = 24
    const val MAX_TAG = 60
    const val MAX_LISTED = 200
    private const val MAX_INDEX_BYTES = 50_000
    private const val MAX_COORD = 1_000.0
    private const val MAX_DEPTH = 8
    private const val MAX_WIDTH = 50.0
    private val COLOUR = Regex("#[0-9A-Fa-f]{8}")
    private val ANIM = Regex("[a-z-]{1,16}( [a-d])?")
    private val PATH_PART = Regex("([MmLlHhVvCcSsQqTtAaZz])|([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)|[\\s,]+")
    private val ARITY = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7, 'Z' to 0)

    /** A sticker's file, or null if it is too big, malformed, or holds anything outside the format. [expectId] is the file's own name. */
    fun parse(text: String, expectId: String? = null): PackSticker? = try {
        if (text.length > MAX_BYTES || !shallow(text)) null else read(JSONObject(text), expectId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: StackOverflowError) {
        null
    }

    /**
     * Whether [text] nests brackets at most [MAX_DEPTH] deep (brackets inside strings do not count). The JSON reader
     * recurses once per level, so a file of a few thousand `[` would overflow its stack: it is refused before it is read.
     */
    fun shallow(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '[', '{' -> if (++depth > MAX_DEPTH) return false
                ']', '}' -> depth--
            }
        }
        return true
    }

    /** The listings of `index.json` that are good ones (a bad entry is skipped), or null if the file is not an index at all. */
    fun parseIndex(text: String): List<PackListing>? = try {
        if (text.length > MAX_INDEX_BYTES || !shallow(text)) {
            null
        } else {
            val list = JSONObject(text).getJSONArray("stickers")
            (0 until minOf(list.length(), MAX_LISTED)).mapNotNull { i -> listing(list.optJSONObject(i)) }.distinctBy { it.id }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: StackOverflowError) {
        null
    }

    private fun listing(o: JSONObject?): PackListing? {
        o ?: return null
        val id = o.opt("id") as? String ?: return null
        if (!PackKey.validId(id)) return null
        val name = cleanName(o.opt("name") as? String) ?: return null
        return PackListing(
            id, name, cleanText(o.opt("tag") as? String, MAX_TAG),
            (o.opt("version") as? Int)?.coerceAtLeast(1) ?: 1,
            (o.opt("size") as? Int)?.coerceAtLeast(0) ?: 0,
        )
    }

    private fun read(o: JSONObject, expectId: String?): PackSticker? {
        val id = o.opt("id") as? String ?: return null
        if (!PackKey.validId(id) || (expectId != null && id != expectId)) return null
        val name = cleanName(o.opt("name") as? String) ?: return null
        val list = o.optJSONArray("parts") ?: return null
        if (list.length() == 0 || list.length() > MAX_PARTS) return null
        val parts = (0 until list.length()).map { i -> part(list.optJSONObject(i)) ?: return null }
        val backdrop = if (o.isNull("backdrop")) null else backdrop(o.optJSONObject("backdrop")) ?: return null
        return PackSticker(id, name, (o.opt("version") as? Int)?.coerceAtLeast(1) ?: 1, backdrop, parts)
    }

    private fun part(o: JSONObject?): PackPart? {
        o ?: return null
        val d = o.opt("d") as? String ?: return null
        if (!validPath(d)) return null
        val fill = colour(o, "fill") ?: return null
        val stroke = colour(o, "stroke") ?: return null
        val width = number(o.opt("width"), 0.0, MAX_WIDTH) ?: return null
        val dash = if (o.isNull("dash")) null else {
            val a = o.optJSONArray("dash") ?: return null
            // An even count (dash, gap, ...) of 2 to 8, none too fine to draw.
            if (a.length() !in 2..8 || a.length() % 2 != 0) return null
            (0 until a.length()).map { i -> number(a.opt(i), 0.5, 200.0) ?: return null }
        }
        val anim = if (o.isNull("anim")) null else (o.opt("anim") as? String)?.takeIf { ANIM.matches(it) } ?: return null
        val group = if (anim == null) -1 else ((o.opt("group") as? Int)?.takeIf { it in 0..63 } ?: return null)
        return PackPart(
            d, fill.value, stroke.value, width.toFloat(), dash?.map { it.toFloat() },
            o.opt("roundCap") == true, o.opt("roundJoin") == true, anim, group,
        )
    }

    private fun backdrop(o: JSONObject?): PackBackdrop? {
        o ?: return null
        val type = o.opt("type") as? String ?: return null
        val keys = when (type) {
            "solid" -> emptyList()
            "linear" -> listOf("x1", "y1", "x2", "y2")
            "radial" -> listOf("cx", "cy", "r")
            else -> return null
        }
        val a = o.optJSONArray("colours") ?: return null
        if (a.length() !in (if (type == "solid") 1..1 else 2..8)) return null
        val colours = (0 until a.length()).map { i -> parseColour(a.opt(i) as? String) ?: return null }
        // A radial gradient needs a radius above nothing.
        val n = keys.map { k -> (if (k == "r") number(o.opt(k), 0.01, MAX_COORD) else number(o.opt(k), -MAX_COORD, MAX_COORD))?.toFloat() ?: return null }
        return PackBackdrop(type, colours, n)
    }

    /** A holder, so a colour of none (null) can be told from a colour that is bad. */
    private class Colour(val value: Long?)

    private fun colour(o: JSONObject, key: String): Colour? =
        if (!o.has(key) || o.isNull(key)) Colour(null) else parseColour(o.opt(key) as? String)?.let { Colour(it) }

    private fun parseColour(s: String?): Long? = s?.takeIf { COLOUR.matches(it) }?.substring(1)?.toLong(16)

    /** [v] as a finite number in [lo]..[hi]; a number only (not a string that looks like one). */
    private fun number(v: Any?, lo: Double, hi: Double): Double? =
        (v as? Number)?.toDouble()?.takeIf { it.isFinite() && it in lo..hi }

    /**
     * Whether [d] is SVG path data and nothing else: commands, numbers and separators, starting with a move,
     * each command given a whole number of its arguments. That is what the drawing code can read without failing.
     */
    fun validPath(d: String): Boolean {
        if (d.isEmpty() || d.length > MAX_PATH) return false
        var at = 0
        var command: Char? = null
        var numbers = 0
        fun closeRun(): Boolean {
            val c = command ?: return true
            val arity = ARITY.getValue(c.uppercaseChar())
            return if (arity == 0) numbers == 0 else numbers > 0 && numbers % arity == 0
        }
        while (at < d.length) {
            val m = PATH_PART.matchAt(d, at) ?: return false
            if (m.value.isEmpty()) return false
            m.groups[1]?.let { g ->
                if (!closeRun()) return false
                if (command == null && g.value[0] != 'M' && g.value[0] != 'm') return false
                command = g.value[0]
                numbers = 0
            }
            m.groups[2]?.let { g ->
                if (command == null) return false
                val v = g.value.toDoubleOrNull() ?: return false
                if (!v.isFinite() || kotlin.math.abs(v) > MAX_COORD) return false
                numbers++
            }
            at += m.value.length
        }
        return command != null && closeRun()
    }

    /** [raw] without control characters, trimmed, at most [MAX_NAME] characters; null when nothing is left. */
    private fun cleanName(raw: String?): String? = cleanText(raw, MAX_NAME).ifEmpty { null }

    private fun cleanText(raw: String?, max: Int): String {
        val s = (raw ?: "").take(256).filter { !it.isISOControl() }.trim()
        return if (s.codePointCount(0, s.length) > max) s.substring(0, s.offsetByCodePoints(0, max)).trim() else s
    }
}

/** Fetches one file over the network; throws when it can't or when it is bigger than [maxBytes]. */
fun interface PackFetcher {
    suspend fun fetch(url: String, maxBytes: Int): ByteArray
}

/** The real fetcher: plain HTTPS, a size cap, and no more than a few seconds of waiting. */
class UrlPackFetcher : PackFetcher {
    override suspend fun fetch(url: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            if (conn.contentLengthLong > maxBytes) throw IOException("too big")
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw IOException("too big")
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}

/** The online list of stickers: not set up in this build, on its way, there, or it couldn't be fetched. */
sealed interface PackCatalog {
    data object NotSetUp : PackCatalog
    data object Loading : PackCatalog
    data class Ready(val stickers: List<PackListing>) : PackCatalog
    data class Failed(val why: String) : PackCatalog
}

/**
 * Stickers you download in the game, the way Ship Battle's skins are (SkinStore): new ones are files in this
 * project's stickers/ folder, listed by stickers/index.json, so a new one needs no app update. A download is
 * kept on the phone (in [dir], one `<id>.json` each) and works offline. A sticker that someone sends you and
 * you do not have is fetched by [PackReceiver], and kept the same way.
 *
 * [baseUrl] comes from the build (BuildConfig.STICKERS_BASE_URL, the repository CI built from); empty for
 * a local build, which then has nothing to download.
 */
class StickerStore(
    private val dir: File?,
    private val baseUrl: String,
    private val fetcher: PackFetcher,
    private val scope: CoroutineScope,
    /** Whether a sticker can be drawn; one that cannot is never kept and is dropped on load. */
    private val drawable: (PackSticker) -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _installed = MutableStateFlow<List<PackSticker>>(emptyList())

    /** The stickers on this phone, in the order they were downloaded. */
    val installed: StateFlow<List<PackSticker>> = _installed.asStateFlow()

    private val _catalog = MutableStateFlow<PackCatalog>(if (baseUrl.isEmpty()) PackCatalog.NotSetUp else PackCatalog.Loading)
    val catalog: StateFlow<PackCatalog> = _catalog.asStateFlow()

    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** A download that went wrong, to show once. */
    val message: StateFlow<String?> = _message.asStateFlow()

    private val inFlight = HashMap<String, Deferred<PackSticker?>>()

    /** When each id last failed to download, so a sticker that cannot be had is not asked for again at once. */
    private val failedAt = HashMap<String, Long>()

    /** Reads the stickers kept on the phone. A file that is not a good sticker is ignored (and left alone). */
    fun load() {
        val d = dir ?: return
        val order = File(d, ORDER).takeIf { it.isFile }?.readText().orEmpty().split(',').filter { it.isNotBlank() }
        val read = d.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { StickerFiles.parse(f.readText(), expectId = f.name.removeSuffix(".json")) }.getOrNull()
        }.filter { runCatching { drawable(it) }.getOrDefault(false) }
        _installed.value = read.sortedWith(compareBy({ order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.id }))
    }

    fun get(id: String): PackSticker? = _installed.value.firstOrNull { it.id == id }

    /** What to call a sticker: its name in the index, else its own name once downloaded, else its id. */
    fun nameOf(id: String): String =
        (_catalog.value as? PackCatalog.Ready)?.stickers?.firstOrNull { it.id == id }?.name ?: get(id)?.name ?: id

    /** Fetches the online list again. */
    fun refresh() {
        scope.launch { loadCatalog(force = true) }
    }

    /** Fetches the online list unless it is already here; returns when it is done, whether it worked or not. */
    suspend fun loadCatalog(force: Boolean = false) {
        if (baseUrl.isEmpty() || (!force && _catalog.value is PackCatalog.Ready)) return
        if (_catalog.value !is PackCatalog.Ready) _catalog.value = PackCatalog.Loading
        val listed = try {
            StickerFiles.parseIndex(fetcher.fetch("$baseUrl/index.json", 50_000).decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        _catalog.value = when {
            listed != null -> PackCatalog.Ready(listed)
            _catalog.value is PackCatalog.Ready -> _catalog.value
            else -> PackCatalog.Failed("Couldn't reach the sticker list. Check the internet and try again.")
        }
    }

    /**
     * The sticker [id]: the one on the phone, else downloaded now and kept. Null when it can't be had (no
     * internet, not published, not a good file). Many askers for one id share one download.
     */
    suspend fun fetch(id: String): PackSticker? {
        get(id)?.let { return it }
        return download(id, retryFailed = false)
    }

    /**
     * Downloads [id] from the repository and keeps it, replacing an older one. Null when it can't be had. With
     * [retryFailed] false, an id that failed less than [RETRY_AFTER_MS] ago is not asked for again.
     */
    suspend fun download(id: String, retryFailed: Boolean = true): PackSticker? {
        if (baseUrl.isEmpty() || !PackKey.validId(id)) return null
        val job = synchronized(inFlight) {
            inFlight[id] ?: run {
                val failed = failedAt[id]
                if (!retryFailed && failed != null && clock() - failed < RETRY_AFTER_MS) return null
                scope.async { fetchAndKeep(id) }.also { d ->
                    inFlight[id] = d
                    // A finished download leaves the table, whoever was waiting for it.
                    d.invokeOnCompletion { synchronized(inFlight) { inFlight.remove(id, d) } }
                }
            }
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    private suspend fun fetchAndKeep(id: String): PackSticker? {
        _downloading.update { it + id }
        try {
            val bytes = fetcher.fetch("$baseUrl/$id.json", StickerFiles.MAX_BYTES)
            val text = bytes.decodeToString()
            val sticker = StickerFiles.parse(text, expectId = id)?.takeIf { drawable(it) }
            if (sticker == null) {
                failedAt[id] = clock()
                return null
            }
            dir?.let { d ->
                d.mkdirs()
                val tmp = File(d, "$id.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(File(d, "$id.json"))) {
                    File(d, "$id.json").writeText(text)
                    tmp.delete()
                }
            }
            _installed.update { list -> list.filter { it.id != id } + sticker }
            saveOrder()
            failedAt.remove(id)
            return sticker
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failedAt[id] = clock()
            return null
        } finally {
            _downloading.update { it - id }
        }
    }

    /** The packs sheet's button: downloads [listing] and keeps it, or says why it could not. */
    fun add(listing: PackListing) {
        scope.launch {
            if (download(listing.id, retryFailed = true) == null) _message.value = "Couldn't download ${listing.name}. Check the internet and try again."
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun saveOrder() {
        val d = dir ?: return
        runCatching { File(d, ORDER).writeText(_installed.value.joinToString(",") { it.id }) }
    }

    companion object {
        private const val ORDER = "order.txt"

        /** A sticker that failed to download is not asked for again for this long (unless the player taps Add). */
        const val RETRY_AFTER_MS = 60_000L
        private var shared: StickerStore? = null

        /** The phone's store: its downloads live in the app's files, and it reads the online list of this build. */
        @Synchronized
        fun get(context: android.content.Context): StickerStore = shared ?: StickerStore(
            File(context.applicationContext.filesDir, "stickers"), BuildConfig.STICKERS_BASE_URL, UrlPackFetcher(),
            // The last backstop: nothing a download does may take the app down.
            CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }),
            drawable = { it.toSticker() != null },
        ).also { store ->
            shared = store
            store.scope.launch { store.load() }
        }
    }
}

/** What a phone shows for a sticker someone sent: the sticker itself, or its name in a bubble. */
sealed interface PackShown {
    data class Sticker(val sticker: PackSticker) : PackShown
    data class Name(val name: String) : PackShown
}

/**
 * What to do with a pack sticker that arrives. One the phone has is shown at once. One it has not is fetched
 * and kept, and shown if that is done within [slowMs]; if it takes longer, or fails, its name (from the
 * index, else its id) is shown in a bubble instead. A fetch that is still going when the bubble shows carries
 * on, so the sticker is there the next time.
 */
class PackReceiver(private val store: StickerStore, private val slowMs: Long = SLOW_MS) {
    suspend fun receive(id: String): PackShown {
        store.get(id)?.let { return PackShown.Sticker(it) }
        return withTimeoutOrNull(slowMs) {
            coroutineScope {
                // The names come with the list: ask for it while the sticker comes, so a failure can name it.
                val names = async { store.loadCatalog() }
                val sticker = store.fetch(id)
                if (sticker != null) {
                    names.cancel()
                    PackShown.Sticker(sticker)
                } else {
                    names.await()
                    PackShown.Name(store.nameOf(id))
                }
            }
        } ?: PackShown.Name(store.nameOf(id))
    }

    companion object {
        /** After this long the name is shown instead of waiting for the picture. */
        const val SLOW_MS = 2_000L
    }
}
