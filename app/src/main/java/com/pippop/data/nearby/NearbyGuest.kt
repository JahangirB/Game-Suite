package com.pippop.data.nearby

import android.content.Context
import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.BattleBackend
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.Hello
import com.pippop.data.Reaction
import com.pippop.data.toJson
import com.pippop.game.ships.Ship
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Joins a nearby battle hosted on another phone. Speaks the same request/reply messages as
 * the Cloudflare server, carried over a direct Nearby Connections link. If the link drops,
 * it finds the host again and reconnects with the player's id.
 */
class NearbyGuest(context: Context) : BattleBackend {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pushes = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    override val pushes: Flow<JSONObject> = _pushes
    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected

    private val pending = mutableMapOf<String, CompletableDeferred<JSONObject>>()
    private val nextId = AtomicInteger()
    private var endpointId: String? = null
    private var linked: CompletableDeferred<Unit>? = null
    private var session: Session? = null
    private var closed = false
    private var reconnecting: Job? = null

    private data class Session(val code: String, val tag: String, val me: Hello, val playerId: String)

    /** Hosts currently advertising a battle nearby. Stops looking when collection stops. */
    private fun discover(): Flow<List<FoundHost>> = callbackFlow {
        val found = linkedMapOf<String, FoundHost>()
        val callback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                if (info.serviceId != SERVICE_ID) return
                parseAdvertisedName(endpointId, info.endpointName)?.let {
                    found[endpointId] = it
                    trySend(found.values.toList())
                }
            }

            override fun onEndpointLost(endpointId: String) {
                found.remove(endpointId)
                trySend(found.values.toList())
            }
        }
        trySend(emptyList())
        client.startDiscovery(SERVICE_ID, callback, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { close(IOException("Could not look for nearby players: ${it.message}")) }
        awaitClose { client.stopDiscovery() }
    }

    /** Opens the direct link to [host]. */
    private suspend fun connectTo(host: FoundHost, myName: String) {
        val done = CompletableDeferred<Unit>()
        linked = done
        endpointId = host.endpointId
        client.requestConnection(myName, host.endpointId, lifecycle)
            .addOnFailureListener { done.completeExceptionally(IOException("Could not connect: ${it.message}")) }
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { done.await() }
            ?: throw IOException("The other phone did not answer.")
        client.stopDiscovery()
    }

    override suspend fun ping() = JSONObject().put("ok", true)

    override suspend fun create(code: String, me: Hello, game: GameKind): JSONObject =
        throw UnsupportedOperationException("A nearby guest does not host")

    /**
     * Finds the nearby room with [code] and joins it. The code is never broadcast, so this
     * tries each phone hosting nearby in turn; one hosting another room turns the code away.
     */
    override suspend fun join(code: String, me: Hello, playerId: String?): JSONObject {
        val tried = mutableSetOf<String>()
        val deadline = System.currentTimeMillis() + JOIN_SEARCH_MS
        while (true) {
            val left = deadline - System.currentTimeMillis()
            val host = if (left <= 0) null else withTimeoutOrNull(left) {
                discover().first { hosts -> hosts.any { it.endpointId !in tried } }.first { it.endpointId !in tried }
            }
            host ?: throw ApiException(
                "No room with that code nearby. Check the code, and that the other phone has the room open.",
                reason = "not_found",
            )
            tried += host.endpointId
            try {
                connectTo(host, me.name)
                val json = request(open(me.name, me.avatar, playerId, code), OPEN)
                session = Session(code, host.tag, me, json.getString("playerId"))
                return json
            } catch (e: ApiException) {
                if (e.reason != WRONG_CODE) throw e
            } catch (e: IOException) {
                // That phone didn't answer; try the next one.
            }
            endpointId?.let { client.disconnectFromEndpoint(it) }
            endpointId = null
        }
    }

    override suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?) =
        request(JSONObject().put("type", "ready").put("ready", ready).put("difficulty", difficulty?.key))

    override suspend fun sync(code: String, playerId: String, board: String?, version: Int?) =
        request(
            if (board != null) JSONObject().put("type", "board").put("board", board)
            else JSONObject().put("type", "sync"),
        )

    override suspend fun emoji(code: String, playerId: String, reaction: Reaction) =
        request(JSONObject().put("type", "emoji").put("emoji", reaction.key))

    override suspend fun readyShips(code: String, playerId: String, ready: Boolean, fleet: List<Ship>) =
        request(JSONObject().put("type", "ready").put("ready", ready).put("fleet", fleet.toJson()))

    override suspend fun fire(code: String, playerId: String, x: Int, y: Int) =
        request(JSONObject().put("type", "fire").put("x", x).put("y", y))

    override suspend fun move(code: String, playerId: String, move: JSONObject) =
        request(JSONObject().put("type", "move").put("move", move))

    override suspend fun readyNumbers(code: String, playerId: String, ready: Boolean, puzzle: JSONObject?) =
        request(JSONObject().put("type", "ready").put("ready", ready).put("puzzle", puzzle ?: JSONObject.NULL))

    override suspend fun readySlide(code: String, playerId: String, ready: Boolean, tiles: List<Int>?) =
        request(JSONObject().put("type", "ready").put("ready", ready).put("tiles", tiles?.let { org.json.JSONArray(it) } ?: JSONObject.NULL))

    override suspend fun readyGrid(code: String, playerId: String, ready: Boolean, letters: String?) =
        request(JSONObject().put("type", "ready").put("ready", ready).put("letters", letters ?: JSONObject.NULL))

    override suspend fun hockeyScore(code: String, playerId: String, score: List<Int>) =
        request(JSONObject().put("type", "score").put("score", org.json.JSONArray(score)))

    override fun sendLive(data: JSONObject) {
        val target = endpointId ?: return
        client.sendPayload(target, JSONObject().put("type", "live").put("data", data).toPayload())
    }

    override suspend fun setOptions(code: String, playerId: String, options: JSONObject) =
        request(JSONObject().put("type", "options").put("options", options))

    override suspend fun guess(code: String, playerId: String, word: String) =
        request(JSONObject().put("type", "guess").put("word", word))

    override suspend fun stroke(code: String, playerId: String, stroke: JSONObject) =
        request(JSONObject().put("type", "stroke").put("stroke", stroke))

    override suspend fun clearDrawing(code: String, playerId: String) =
        request(JSONObject().put("type", "clear"))

    override suspend fun openGame(code: String, playerId: String, game: GameKind) =
        request(JSONObject().put("type", "game").put("game", game.key))

    override suspend fun toMenu(code: String, playerId: String) =
        request(JSONObject().put("type", "menu"))

    override suspend fun leave(code: String, playerId: String) {
        try {
            withTimeoutOrNull(LEAVE_TIMEOUT_MS) { request(JSONObject().put("type", "leave")) }
        } catch (e: Exception) {
            // The host notices the dropped link and handles it like leaving.
        } finally {
            close()
        }
    }

    override fun close() {
        closed = true
        reconnecting?.cancel()
        client.stopDiscovery()
        client.stopAllEndpoints()
        pending.values.forEach { it.completeExceptionally(IOException("Connection closed.")) }
        pending.clear()
        scope.cancel()
    }

    private fun open(name: String, avatar: Avatar?, playerId: String?, code: String) = JSONObject()
        .put("type", "open").put("code", code).put("name", name)
        .put("avatar", avatar?.key ?: JSONObject.NULL)
        .put("playerId", playerId ?: JSONObject.NULL)
        .put("games", BattleBackend.SUPPORTED_GAMES)

    private suspend fun request(message: JSONObject, id: String = "m${nextId.incrementAndGet()}"): JSONObject {
        val target = endpointId ?: throw IOException("Not connected.")
        val reply = CompletableDeferred<JSONObject>()
        pending[id] = reply
        client.sendPayload(target, message.put("id", id).toPayload())
            .addOnFailureListener { pending.remove(id)?.completeExceptionally(IOException("Could not send.")) }
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { reply.await() }
            ?: throw IOException("The other phone did not answer.").also { pending.remove(id) }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                linked?.complete(Unit)
            } else {
                linked?.completeExceptionally(IOException("The connection was refused."))
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (endpointId != this@NearbyGuest.endpointId || closed) return
            this@NearbyGuest.endpointId = null
            pending.values.forEach { it.completeExceptionally(IOException("Connection lost.")) }
            pending.clear()
            if (session != null) reconnect()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val json = payload.toJson() ?: return
            val re = if (json.isNull("re")) null else json.optString("re")
            if (re == OPEN) _connected.value = true
            val waiter = re?.let { pending.remove(it) }
            when {
                waiter == null -> _pushes.tryEmit(json)
                json.optString("type") == "error" ->
                    waiter.completeExceptionally(ApiException.from(json))
                else -> waiter.complete(json)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    /** Finds the same host again and rejoins; the host's reply arrives as a push. */
    private fun reconnect() {
        val s = session ?: return
        _connected.value = false
        reconnecting?.cancel()
        reconnecting = scope.launch {
            while (!closed && endpointId == null) {
                try {
                    val host = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                        discover().first { hosts -> hosts.any { it.tag == s.tag } }.first { it.tag == s.tag }
                    }
                    if (host != null) {
                        connectTo(host, s.me.name)
                        client.sendPayload(host.endpointId, open(s.me.name, s.me.avatar, s.playerId, s.code).put("id", OPEN).toPayload())
                        return@launch
                    }
                } catch (e: Exception) {
                    if (closed) return@launch
                }
                delay(RETRY_MS)
            }
        }
    }

    private companion object {
        const val OPEN = "open"
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val LEAVE_TIMEOUT_MS = 2_000L
        const val SEARCH_TIMEOUT_MS = 15_000L
        // Looking for the room with a code: long enough to try a few phones.
        const val JOIN_SEARCH_MS = 30_000L
        const val RETRY_MS = 2_000L
    }
}
