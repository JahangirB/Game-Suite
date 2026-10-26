package com.arcade.sudoku.data.nearby

import android.content.Context
import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.BattleBackend
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.game.numbers.NumberPuzzle
import com.arcade.sudoku.data.Hello
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.game.ships.Ship
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Hosts a nearby battle: this phone runs the room ([LocalRoom]) and advertises it, and the
 * guests' phones (up to three) connect directly. The host's own moves are applied locally; the
 * guests' arrive as messages. All room access happens on the main thread, where Nearby calls back.
 */
class NearbyHost(context: Context, private val hostName: String, private val hostAvatar: Avatar?) : BattleBackend {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pushes = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    override val pushes: Flow<JSONObject> = _pushes
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)

    private var room: LocalRoom? = null
    /** Each joined guest's connection, and the seat it plays in. */
    private val guests = mutableMapOf<String, String>()
    private var advertising = false
    private var timers: Job? = null

    private fun now() = System.currentTimeMillis()

    override suspend fun ping() = JSONObject().put("ok", true)

    override suspend fun create(code: String, me: Hello, game: GameKind): JSONObject = withContext(Dispatchers.Main.immediate) {
        val r = LocalRoom(code, me.name, me.avatar, game)
        room = r
        startAdvertising()
        timers = scope.launch {
            while (true) {
                delay(1000)
                if (r.checkTimers(now())) pushAll()
            }
        }
        r.view(LocalRoom.HOST, now()).put("playerId", r.playerId(LocalRoom.HOST))
    }

    override suspend fun join(code: String, me: Hello, playerId: String?): JSONObject =
        throw UnsupportedOperationException("The host does not join")

    override suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?) =
        local {
            when {
                game.turnBased -> setTurnReady(LocalRoom.HOST, ready, now())
                game == GameKind.WORDS -> setWordsReady(LocalRoom.HOST, ready, now())
                game == GameKind.DRAW -> setDrawReady(LocalRoom.HOST, ready, now())
                game == GameKind.HOCKEY -> setHockeyReady(LocalRoom.HOST, ready, now())
                else -> setReady(LocalRoom.HOST, ready, difficulty, now())
            }
        }

    override suspend fun sync(code: String, playerId: String, board: String?, version: Int?) =
        local {
            if (board != null && game == GameKind.NUMBERS) submitNumbers(LocalRoom.HOST, board.split(","), now())
            else if (board != null) submitBoard(LocalRoom.HOST, board, now())
        }

    override suspend fun emoji(code: String, playerId: String, reaction: Reaction) =
        local { sendEmoji(LocalRoom.HOST, reaction, now()) }

    override suspend fun readyShips(code: String, playerId: String, ready: Boolean, fleet: List<Ship>) =
        local { setShipsReady(LocalRoom.HOST, ready, fleet, now()) }

    override suspend fun fire(code: String, playerId: String, x: Int, y: Int) =
        local { fire(LocalRoom.HOST, x, y, now()) }

    override suspend fun move(code: String, playerId: String, move: JSONObject) =
        local { move(LocalRoom.HOST, move, now()) }

    override suspend fun setOptions(code: String, playerId: String, options: JSONObject) =
        local { setOptions(LocalRoom.HOST, options) }

    override suspend fun readyGrid(code: String, playerId: String, ready: Boolean, letters: String?) =
        local { setGridReady(LocalRoom.HOST, ready, letters, now()) }

    override suspend fun hockeyScore(code: String, playerId: String, score: List<Int>) =
        local { hockeyScore(LocalRoom.HOST, score, now()) }

    override fun sendLive(data: JSONObject) {
        val json = JSONObject().put("type", "live").put("data", data)
        for (endpoint in guests.keys) send(endpoint, json)
    }

    override suspend fun readyNumbers(code: String, playerId: String, ready: Boolean, puzzle: JSONObject?) =
        local { setNumbersReady(LocalRoom.HOST, ready, NumberPuzzle.from(puzzle), now()) }

    override suspend fun guess(code: String, playerId: String, word: String) =
        local {
            when (game) {
                GameKind.DRAW -> drawGuess(LocalRoom.HOST, word, now())
                GameKind.GRID -> findWord(LocalRoom.HOST, word, now())
                else -> guessWord(LocalRoom.HOST, word, now())
            }
        }

    override suspend fun stroke(code: String, playerId: String, stroke: JSONObject): JSONObject = withContext(Dispatchers.Main.immediate) {
        val r = room ?: throw ApiException("The battle is over.", fatal = true)
        val added = r.addStroke(LocalRoom.HOST, stroke, now())
        guests.keys.forEach { send(it, added) }
        JSONObject().put("type", "ack")
    }

    override suspend fun clearDrawing(code: String, playerId: String) =
        local { clearDrawing(LocalRoom.HOST, now()) }

    override suspend fun leave(code: String, playerId: String) {
        withContext(Dispatchers.Main.immediate) {
            room?.leave(LocalRoom.HOST)
            guests.keys.forEach { send(it, errorJson(null, "The host closed this battle.", fatal = true)) }
        }
        // Give the message a moment to leave before the connection drops.
        delay(300)
        close()
    }

    override fun close() {
        timers?.cancel()
        client.stopAdvertising()
        client.stopAllEndpoints()
        scope.cancel()
    }

    /** Applies the host's own change and returns the host's view; the guests get theirs pushed. */
    private suspend fun local(change: LocalRoom.() -> Unit): JSONObject = withContext(Dispatchers.Main.immediate) {
        val r = room ?: throw ApiException("The battle is over.", fatal = true)
        r.change()
        pushToGuests()
        r.view(LocalRoom.HOST, now())
    }

    private fun startAdvertising() {
        if (advertising) return
        val r = room ?: return
        advertising = true
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(advertisedName(r.code, hostName, hostAvatar), SERVICE_ID, lifecycle, options)
            .addOnFailureListener { e ->
                advertising = false
                _pushes.tryEmit(errorJson(null, "Could not start the nearby battle: ${e.message}", fatal = true))
            }
    }

    private fun stopAdvertising() {
        client.stopAdvertising()
        advertising = false
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Accept everyone; a phone that finds no free seat is told so when it sends "open".
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            // The guest still has to send "open"; nothing to do until then.
        }

        override fun onDisconnected(endpointId: String) {
            val role = guests.remove(endpointId) ?: return
            val r = room ?: return
            if (r.status == "closed") return
            r.setConnected(role, false, now())
            pushAll()
            // Let the guest find us again to reconnect (or someone new, in the lobby).
            startAdvertising()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val msg = payload.toJson() ?: return
            handle(endpointId, msg)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun handle(endpointId: String, msg: JSONObject) {
        val r = room ?: return
        val id = if (msg.isNull("id")) null else msg.optString("id")
        val now = now()
        try {
            val role = guests[endpointId]
            if (role == null && msg.optString("type") != "open") throw ApiException("Join the battle first.", fatal = true)
            when (msg.optString("type")) {
                "open" -> {
                    val playerId = if (msg.isNull("playerId")) null else msg.optString("playerId")
                    val games = if (msg.has("games")) msg.optString("games").split(",") else null
                    val seat = r.join(msg.optString("name"), Avatar.from(msg.optString("avatar")), playerId, games)
                    guests[endpointId] = seat
                    r.setConnected(seat, true, now)
                    if (r.seated().count { it != LocalRoom.HOST } >= r.game.maxPlayers - 1) stopAdvertising()
                    send(endpointId, r.view(seat, now).put("re", "open").put("playerId", r.playerId(seat)))
                    pushAll(except = endpointId)
                    return
                }
                "ready" -> if (r.game == GameKind.SHIPS) {
                    r.setShipsReady(role!!, msg.optBoolean("ready", true), parseFleet(msg.optJSONArray("fleet")), now)
                } else if (r.game.turnBased) {
                    r.setTurnReady(role!!, msg.optBoolean("ready", true), now)
                } else if (r.game == GameKind.WORDS) {
                    r.setWordsReady(role!!, msg.optBoolean("ready", true), now)
                } else if (r.game == GameKind.DRAW) {
                    r.setDrawReady(role!!, msg.optBoolean("ready", true), now)
                } else if (r.game == GameKind.GRID) {
                    r.setGridReady(role!!, msg.optBoolean("ready", true), if (msg.isNull("letters")) null else msg.optString("letters"), now)
                } else if (r.game == GameKind.HOCKEY) {
                    r.setHockeyReady(role!!, msg.optBoolean("ready", true), now)
                } else if (r.game == GameKind.NUMBERS) {
                    r.setNumbersReady(role!!, msg.optBoolean("ready", true), NumberPuzzle.from(msg.optJSONObject("puzzle")), now)
                } else {
                    r.setReady(role!!, msg.optBoolean("ready", true), Difficulty.from(msg.optString("difficulty")), now)
                }
                "live" -> {
                    // Real-time play: straight to this phone's game, no reply, no new room state.
                    _pushes.tryEmit(JSONObject().put("type", "live").put("data", msg.optJSONObject("data") ?: JSONObject()))
                    return
                }
                "score" -> r.hockeyScore(role!!, msg.optJSONArray("score")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty(), now)
                "fire" -> r.fire(role!!, msg.optInt("x", -1), msg.optInt("y", -1), now)
                "move" -> r.move(role!!, msg.optJSONObject("move") ?: JSONObject(), now)
                "options" -> r.setOptions(role!!, msg.optJSONObject("options") ?: JSONObject())
                "guess" -> if (r.game == GameKind.DRAW) {
                    r.drawGuess(role!!, msg.optString("word"), now)
                } else if (r.game == GameKind.GRID) {
                    r.findWord(role!!, msg.optString("word"), now)
                } else {
                    r.guessWord(role!!, msg.optString("word"), now)
                }
                "stroke" -> {
                    // Straight to the other screens; the drawer gets a plain acknowledgement.
                    val added = r.addStroke(role!!, msg.optJSONObject("stroke") ?: JSONObject(), now)
                    send(endpointId, JSONObject().put("type", "ack").put("re", id))
                    _pushes.tryEmit(added)
                    guests.keys.filter { it != endpointId }.forEach { send(it, added) }
                    return
                }
                "clear" -> r.clearDrawing(role!!, now)
                "board" -> if (r.game == GameKind.NUMBERS) {
                    r.submitNumbers(role!!, msg.optString("board").split(","), now)
                } else {
                    r.submitBoard(role!!, msg.optString("board"), now)
                }
                "emoji" -> r.sendEmoji(
                    role!!,
                    Reaction.from(msg.optString("emoji")) ?: throw ApiException("Unknown reaction."),
                    now,
                )
                "sync" -> Unit
                "leave" -> {
                    r.leave(role!!)
                    send(endpointId, JSONObject().put("type", "left").put("re", id))
                    guests.remove(endpointId)
                    pushAll()
                    if (r.status != "started") startAdvertising()
                    return
                }
                else -> throw ApiException("Unknown message.")
            }
            send(endpointId, r.view(role!!, now).put("re", id))
            pushAll(except = endpointId)
        } catch (e: ApiException) {
            send(endpointId, errorJson(id ?: if (msg.optString("type") == "open") "open" else null, e.message, e.fatal, e.reason))
        }
    }

    private fun parseFleet(array: org.json.JSONArray?): List<Ship> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
            Ship(it.optInt("x"), it.optInt("y"), it.optInt("len"), it.optBoolean("horizontal"))
        }

    private fun send(endpointId: String, json: JSONObject) {
        client.sendPayload(endpointId, json.toPayload())
    }

    private fun pushHost() {
        room?.let { _pushes.tryEmit(it.view(LocalRoom.HOST, now())) }
    }

    private fun pushToGuests(except: String? = null) {
        val r = room ?: return
        for ((endpoint, role) in guests) if (endpoint != except) send(endpoint, r.view(role, now()))
    }

    /** Sends the room to the host's screen and every guest but [except] (who gets a reply instead). */
    private fun pushAll(except: String? = null) {
        pushHost()
        pushToGuests(except)
    }
}
