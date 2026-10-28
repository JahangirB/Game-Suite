package com.pippop.data

import com.pippop.game.ships.Ship
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * A way to reach a battle room. Every call returns the room as this player sees it (the
 * JSON both servers send). Polling servers return null [pushes]; live ones push changes.
 */
interface BattleBackend {
    /** Room updates and errors the server sends on its own, or null if the app must poll [sync]. */
    val pushes: Flow<JSONObject>?

    /** False while a live connection is down and being re-established. */
    val connected: StateFlow<Boolean>

    suspend fun ping(): JSONObject
    suspend fun create(code: String, me: Hello, game: GameKind): JSONObject

    /** Joins as a new player, or rejoins as [playerId] from an earlier session. */
    suspend fun join(code: String, me: Hello, playerId: String? = null): JSONObject
    suspend fun ready(code: String, playerId: String, ready: Boolean, difficulty: Difficulty?): JSONObject

    /** Uploads [board] if given; [version] lets a polling server answer just "unchanged". */
    suspend fun sync(code: String, playerId: String, board: String?, version: Int?): JSONObject
    suspend fun emoji(code: String, playerId: String, reaction: Reaction): JSONObject

    /** Ship Battle: ready with [fleet] (or not ready). */
    suspend fun readyShips(code: String, playerId: String, ready: Boolean, fleet: List<Ship>): JSONObject

    /** Ship Battle: fire at the opponent's sea. */
    suspend fun fire(code: String, playerId: String, x: Int, y: Int): JSONObject

    /** Turn games: play [move] ({"col": n} in Connect Four). Readiness goes through [ready]. */
    suspend fun move(code: String, playerId: String, move: JSONObject): JSONObject

    /** Number Cross: ready (or not); the host's Ready carries the next [puzzle], built on their phone. */
    suspend fun readyNumbers(code: String, playerId: String, ready: Boolean, puzzle: JSONObject?): JSONObject

    /** Slide Race: ready (or not); the host's Ready carries the shuffled start [tiles]. */
    suspend fun readySlide(code: String, playerId: String, ready: Boolean, tiles: List<Int>?): JSONObject

    /** Letter Grid: ready (or not); the host's Ready carries the next grid's 16 [letters]. */
    suspend fun readyGrid(code: String, playerId: String, ready: Boolean, letters: String?): JSONObject

    /** Air Hockey: the host reports the score [host, guest] after a goal. */
    suspend fun hockeyScore(code: String, playerId: String, score: List<Int>): JSONObject

    /**
     * Real-time play (Air Hockey): sends [data] straight to the other phones, unanswered and
     * unstored. Anything sent to this phone arrives in [pushes] as {"type": "live", "data": …}.
     */
    fun sendLive(data: JSONObject) {}

    /** Dominoes: the host sets the next match's rules (teams, drawing, target). */
    suspend fun setOptions(code: String, playerId: String, options: JSONObject): JSONObject

    /** Word Race and Draw & Guess: guess [word]. Readiness goes through [ready]. */
    suspend fun guess(code: String, playerId: String, word: String): JSONObject

    /** Draw & Guess: add a stroke. The reply is only an acknowledgement, not a room state. */
    suspend fun stroke(code: String, playerId: String, stroke: JSONObject): JSONObject

    /** Draw & Guess: wipe the drawing. */
    suspend fun clearDrawing(code: String, playerId: String): JSONObject

    /**
     * Opens [game] in the same room: the open game is joined, another one (between matches)
     * replaces it, with the others left where they are until they follow. Rooms that can't
     * switch games say so.
     */
    suspend fun openGame(code: String, playerId: String, game: GameKind): JSONObject =
        throw ApiException("This connection plays one game. Connect again to play another.")

    /** Back to the menu, staying in the room. Leaving a match being played gives it up. */
    suspend fun toMenu(code: String, playerId: String): JSONObject =
        throw ApiException("This connection plays one game. Connect again to play another.")
    suspend fun leave(code: String, playerId: String)

    /** Drops any live connection. */
    fun close() {}

    /** Disconnects for now (the app went to the background) without leaving; [resume] reconnects. */
    fun pause() {}
    fun resume() {}

    companion object {
        /** The games this app can play, sent when joining so a server can ask older apps to update. */
        val SUPPORTED_GAMES: String = GameKind.entries.joinToString(",") { it.key }

        /** Apps Script links poll; anything else is a Cloudflare server with a live connection. */
        fun forUrl(url: String, password: String): BattleBackend =
            if (isAppsScript(url)) BattleApi(url) else CloudflareBattle(url, password)

        fun isAppsScript(url: String) =
            url.contains("script.google.com") || url.contains("script.googleusercontent.com")
    }
}
