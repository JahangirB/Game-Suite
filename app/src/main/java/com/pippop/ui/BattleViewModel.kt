package com.pippop.ui

import com.pippop.ui.screens.TALLY_ME
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.data.ApiException
import com.pippop.data.BattleBackend
import com.pippop.data.Difficulty
import com.pippop.data.DrawStroke
import com.pippop.data.DrawView
import com.pippop.data.GameKind
import com.pippop.game.connect4.ConnectFour
import com.pippop.game.dominoes.DominoOptions
import com.pippop.game.dominoes.Dominoes
import com.pippop.game.bingo.Bingo
import com.pippop.game.blocks.Blocks
import com.pippop.game.blocks.Placement
import com.pippop.game.code.CodeBreaker
import com.pippop.game.golf.Golf
import com.pippop.game.golf.GolfLastShot
import com.pippop.game.slide.Slide
import com.pippop.game.slide.SlideLevel
import com.pippop.game.grid.LetterGrid
import com.pippop.game.grid.LetterGridGenerator
import com.pippop.game.hockey.Body
import com.pippop.game.hockey.Hockey
import com.pippop.game.hockey.HockeyFrame
import com.pippop.game.hockey.HockeyTable
import com.pippop.game.hockey.Side
import com.pippop.game.hockey.ahead
import com.pippop.game.hockey.flip
import com.pippop.game.numbers.NumberCrossGenerator
import com.pippop.game.numbers.NumberLevel
import com.pippop.game.ttt.Ttt
import com.pippop.game.ttt.TttPosition
import com.pippop.game.words.WordLevel
import com.pippop.game.words.WordList
import com.pippop.game.words.Words
import com.pippop.data.Hello
import com.pippop.data.SavedPartner
import com.pippop.data.Invite
import com.pippop.data.ProfileStore
import com.pippop.data.Reaction
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.TurnsView
import com.pippop.data.nearby.NearbyGuest
import com.pippop.data.nearby.NearbyHost
import com.pippop.data.parseRoom
import com.pippop.game.BoardState
import com.pippop.game.ships.Fleet
import com.pippop.game.ships.Ship
import com.pippop.push.Push
import com.pippop.push.PushApi
import com.pippop.game.Sudoku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Online goes through the host's server; nearby connects two phones directly, no internet. */
enum class BattleMode { ONLINE, NEARBY }

/** A reaction on screen and when it appeared. */
data class ShownReaction(val reaction: Reaction, val at: Long)

data class BattleUiState(
    val hostCode: String = Invite.randomCode(),
    val joinCode: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val mode: BattleMode = BattleMode.ONLINE,
    /** The game to open when hosting. */
    val hostGame: GameKind = GameKind.SUDOKU,
    val room: RoomState? = null,
    val board: BoardState? = null,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    /** Server clock minus local clock, so both phones count down to the same instant. */
    val clockOffsetMs: Long = 0,
    val reconnecting: Boolean = false,
    /** The newest reaction from anyone else (it plays large over the game). */
    val opponentReaction: ShownReaction? = null,
    /** Each other player's latest reaction, by name, for their chip. */
    val otherReactions: Map<String, ShownReaction> = emptyMap(),
    val myReaction: ShownReaction? = null,
    /** Ship Battle: the fleet you will be ready with (shuffle until you like it). */
    val fleetDraft: List<Ship> = Fleet.random(),
    /** A move or shot is on its way; it already shows where the rules allow (see [playMove]). */
    val firing: Boolean = false,
    /** Ship Battle: the cell of the shot on its way, marked until the server says hit or miss. */
    val pendingShot: Pair<Int, Int>? = null,
    /** Memory Pairs: the card being turned while the server reveals its picture. */
    val pendingCard: Int? = null,
    /** Word Race: the letters typed for the next guess, and the round they belong to. */
    val wordInput: String = "",
    val wordRound: Int = -1,
    /** Draw & Guess: the strokes on the canvas this turn (yours appear before the server confirms them). */
    val drawStrokes: List<DrawStroke> = emptyList(),
    /** Number Cross: your entries (one per blank), the blank picked to fill next, and the round they belong to. */
    val numberEntries: List<String>? = null,
    val numberSelected: Int? = null,
    val numbersRound: Int = -1,
    /** Slide Race: your slides so far this round, and the round they belong to. */
    val slideMoves: List<Int> = emptyList(),
    val slideRound: Int = -1,
    /** Bingo: the squares you dabbed, for round [bingoRound]. */
    val bingoDabs: Set<Int> = emptySet(),
    val bingoRound: Int = -1,
    /**
     * The nearby room's code: the host reads it out, the other phone types it. The last one is
     * kept, so the same two phones meet again without typing.
     */
    val nearbyCode: String = "",
    /** Something that went wrong, with its fix; shown until dismissed or fixed. */
    val problem: Problem? = null,
    /** Wins since connecting, by name ("You" for this phone): the result's "Tonight" line. */
    val tally: Map<String, Int> = emptyMap(),
) {
    /** Like the server status, but switches to PLAYING the moment the shared start time passes. */
    fun phase(serverNow: Long): RoomStatus? {
        val room = room ?: return null
        val startAt = room.startAt
        return if (room.status == RoomStatus.COUNTDOWN && startAt != null && serverNow >= startAt) {
            RoomStatus.PLAYING
        } else {
            room.status
        }
    }

    fun serverNow() = System.currentTimeMillis() + clockOffsetMs

    fun canEdit() = phase(serverNow()) == RoomStatus.PLAYING && board != null
}

class BattleViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore.get(app)
    private val haptics = Haptics.get(app)
    private val sfx = Sfx.get(app)

    private val _state = MutableStateFlow(
        // The last partner's code stays filled in, so reconnecting needs no typing.
        store.profile.value.let { p ->
            BattleUiState(
                hostCode = p.partner?.code ?: Invite.randomCode(),
                joinCode = p.partner?.code.orEmpty(),
                nearbyCode = p.nearbyCode.ifEmpty { Invite.randomCode() },
            )
        },
    )
    val state: StateFlow<BattleUiState> = _state.asStateFlow()

    private var backend: BattleBackend? = null
    private var playerId: String? = null
    private var sessionJob: Job? = null
    private val syncNow = Channel<Unit>(Channel.CONFLATED)
    private var loadedRound = -1
    /** The Ship Battle round after which the draft fleet was last reshuffled. */
    private var reshuffledRound = -1
    // Draw & Guess: the round and turn, and the clear count, the shown strokes belong to.
    private var strokesTurn = -1 to -1
    private var strokesClears = 0
    private var recordedRound = -1
    private var lastSentBoard: String? = null
    private var bestRoundTripMs = Long.MAX_VALUE
    /** Room version from the last full reply; -1 forces the server to send everything. */
    private var lastVersion = -1
    /** When the opponent's progress last changed, to poll faster while they are active. */
    private var opponentActiveAt = 0L
    private var lastOpponentEmojiSeq = -1
    private val foreground = MutableStateFlow(true)
    /** The server of the current online session, remembered with the partner. */
    private var sessionServer: Pair<String, String>? = null

    init {
        // A declined invitation shows on screen too when the app is open.
        viewModelScope.launch {
            Push.events.collect { event ->
                if (event.type == "invite_reply" && !event.accepted) showMessage("${event.fromName} can't play right now.")
            }
        }
    }

    // --- Start screen -------------------------------------------------------------------------

    fun setHostCode(value: String) = _state.update { it.copy(hostCode = cleanCode(value)) }
    fun newHostCode() = _state.update { it.copy(hostCode = Invite.randomCode()) }
    fun setJoinCode(value: String) = _state.update { it.copy(joinCode = cleanCode(value)) }
    fun setNearbyCode(value: String) = _state.update { it.copy(nearbyCode = cleanCode(value)) }
    fun newNearbyCode() = _state.update { it.copy(nearbyCode = Invite.randomCode()) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun dismissProblem() = _state.update { it.copy(problem = null) }
    fun showProblem(problem: Problem) = _state.update { it.copy(problem = problem) }
    fun setHostGame(game: GameKind) = _state.update { it.copy(hostGame = game) }

    /** Fills in the server link and password (saved to the profile) and the secret code from an invite. */
    fun pasteInvite(text: String?) {
        val invite = Invite.parse(text.orEmpty())
        if (invite.scriptUrl == null && invite.code == null) {
            showMessage("No room code in the copied message.")
            return
        }
        invite.scriptUrl?.let { url ->
            // Messages no longer carry a password: keep the one you have for the same link.
            if (url != store.profile.value.scriptUrl || invite.password != null) store.setServerPassword(invite.password.orEmpty())
            store.setScriptUrl(url)
        }
        invite.code?.let { code -> _state.update { it.copy(joinCode = code) } }
    }

    fun testConnection() = runBusy {
        val url = checkedUrl() ?: return@runBusy
        val server = BattleBackend.forUrl(url, store.profile.value.serverPassword)
        try {
            val kind = if (server.ping().optString("kind") == "cloudflare") "Cloudflare" else "Google Script"
            // Timed on a second call, once the connection is set up: about what each move waits.
            val start = System.currentTimeMillis()
            server.ping()
            showMessage("Connected to the $kind server. Round trip: ${System.currentTimeMillis() - start} ms.")
        } finally {
            server.close()
        }
    }

    /**
     * "Start a room": opens an online room with [BattleUiState.hostCode] and waits in the menu
     * for the others; you pick a game together once they're in. (The room is made with Sudoku,
     * the one game a server that can't switch games plays.)
     */
    fun host() {
        val code = _state.value.hostCode
        if (code.length < 4) return showMessage("The room code needs at least 4 letters or digits.")
        val url = checkedUrl() ?: return
        startOnline(url) { b, me -> b.create(code, me, GameKind.SUDOKU) }
    }

    /** A server from before [game] existed opens a Sudoku room instead; say so rather than play the wrong game. */
    private fun checkGame(room: JSONObject, game: GameKind) {
        if (GameKind.from(room.optString("game")) != game) {
            throw ApiException("The online server needs an update to play ${game.label}. See \"Updating the server\" in the README.", fatal = true)
        }
    }

    fun join() {
        val code = _state.value.joinCode
        if (code.length < 4) return showMessage("Type the code you were sent.")
        val url = checkedUrl() ?: return
        startOnline(url, code = code) { b, me -> b.join(code, me) }
    }

    /**
     * One tap to play with the last partner: same server, same secret code. Whoever taps
     * first opens the room and the other joins it; an old room is simply rejoined.
     */
    fun playWithPartner(game: GameKind = _state.value.hostGame) {
        val partner = store.profile.value.partner ?: return
        if (game != GameKind.SUDOKU && BattleBackend.isAppsScript(partner.serverUrl)) {
            return showMessage("${game.label} can't be played on this online link. Play it nearby, or see \"Choosing a server\" in the README.")
        }
        store.setScriptUrl(partner.serverUrl)
        store.setServerPassword(partner.password)
        _state.update { it.copy(joinCode = partner.code, hostCode = partner.code, hostGame = game) }
        startOnline(partner.serverUrl, partner.password, then = game, code = partner.code) { b, me ->
            openPartnerRoom(b, partner.code, me, partner.myPlayerId, game)
        }
    }

    private suspend fun openPartnerRoom(b: BattleBackend, code: String, me: Hello, playerId: String?, game: GameKind): JSONObject {
        var id = playerId
        repeat(3) {
            try {
                return b.join(code, me, id)
            } catch (e: ApiException) {
                when (e.reason) {
                    // A new room under the same code: join it as a new player.
                    "not_member" -> {
                        id = null
                        return@repeat
                    }
                    // Nobody is there: open it below.
                    "not_found", "closed" -> Unit
                    else -> throw e
                }
            }
            try {
                return b.create(code, me, game).also { checkGame(it, game) }
            } catch (e: ApiException) {
                // The partner opened it a moment ago: join it on the next round.
                if (e.reason != "code_taken") throw e
            }
        }
        throw ApiException("Couldn't open the game. Try again in a moment.")
    }

    fun forgetPartner() = store.setPartner(null)

    /** Opens the partner's room and sends them a notification inviting them to it. */
    fun invitePartner(game: GameKind) {
        val partner = store.profile.value.partner ?: return
        val to = partner.deviceId
            ?: return showMessage("Play one online game together first, then you can invite.")
        if (!PushApi.supports(partner.serverUrl)) return showMessage("This online link can't send invitations. See \"Choosing a server\" in the README.")
        store.setScriptUrl(partner.serverUrl)
        store.setServerPassword(partner.password)
        _state.update { it.copy(hostGame = game) }
        startOnline(partner.serverUrl, partner.password, then = game, code = partner.code) { b, me ->
            val json = openPartnerRoom(b, partner.code, me, partner.myPlayerId, game)
            try {
                PushApi(partner.serverUrl, partner.password).invite(me.deviceId, to, partner.code, game)
                showMessage("Invitation sent to ${partner.name}.")
                store.notificationsNeeded()
            } catch (e: Exception) {
                // The room is open either way; the partner can still tap Play.
                showMessage(e.message ?: "Could not send the invitation.")
            }
            json
        }
    }

    /** "Play" on an invitation: join the partner's room with the code from the notification. */
    fun acceptInvite(code: String, game: GameKind) {
        // Invitations work both ways: offer notifications so the next one reaches this phone too.
        store.notificationsNeeded()
        // Tapping "your turn" while already in that room needs nothing more.
        if (_state.value.room?.code == code) return
        val partner = store.profile.value.partner
        if (partner == null) {
            _state.update { it.copy(joinCode = code) }
            return join()
        }
        if (partner.code != code) store.setPartner(partner.copy(code = code, myPlayerId = null))
        playWithPartner(game)
    }

    private fun startOnline(
        url: String,
        password: String = store.profile.value.serverPassword,
        then: GameKind? = null,
        code: String = "",
        open: suspend (BattleBackend, Hello) -> JSONObject,
    ) {
        startSession(BattleBackend.forUrl(url, password), BattleMode.ONLINE, server = url to password, then = then, code = code, open = open)
    }

    // --- Nearby (offline) battles -------------------------------------------------------------

    /**
     * This phone opens a nearby room with [BattleUiState.nearbyCode] and waits. Only a phone
     * given the code can join; the code is kept for next time.
     */
    fun hostNearby() {
        val code = _state.value.nearbyCode
        if (code.length < 4) return showMessage("The room code needs at least 4 letters or digits.")
        if (Problems.bluetoothOff(getApplication())) return showProblem(Problems.bluetooth)
        store.setNearbyCode(code)
        val p = store.profile.value
        val host = NearbyHost(getApplication(), displayName(p.name), p.avatar)
        // The room waits in the menu; you pick a game together once the others are in.
        startSession(host, BattleMode.NEARBY) { b, me -> b.create(code, me, GameKind.SUDOKU) }
    }

    /** Finds the nearby room with [BattleUiState.nearbyCode] and joins it. */
    fun joinNearby() {
        val code = _state.value.nearbyCode
        if (code.length < 4) return showMessage("Enter the room code from the other phone.")
        if (Problems.bluetoothOff(getApplication())) return showProblem(Problems.bluetooth)
        store.setNearbyCode(code)
        startSession(NearbyGuest(getApplication()), BattleMode.NEARBY, code = code) { b, me -> b.join(code, me) }
    }

    // --- Sessions -----------------------------------------------------------------------------

    /**
     * Connects with [open], then (in a room that plays game after game) goes to [then]'s lobby,
     * or to the menu when null, where everyone sees where the others are. A match this player
     * is back in stays on screen.
     */
    private fun startSession(
        newBackend: BattleBackend,
        mode: BattleMode,
        server: Pair<String, String>? = null,
        then: GameKind? = null,
        /** The code being joined, for a message that says which room wasn't found. */
        code: String = "",
        open: suspend (BattleBackend, Hello) -> JSONObject,
    ) = runBusy {
        val p = store.profile.value
        val sentAt = System.currentTimeMillis()
        val json = try {
            open(newBackend, Hello(displayName(p.name), p.avatar, p.deviceId))
        } catch (e: Exception) {
            newBackend.close()
            if (e is CancellationException) throw e
            // A failure with a known fix shows that fix instead of a bare message.
            val problem = Problems.connecting(e, getApplication(), mode == BattleMode.NEARBY, code, p.partner?.name)
            if (problem != null) {
                _state.update { it.copy(problem = problem) }
                return@runBusy
            }
            throw e
        }
        backend = newBackend
        sessionServer = server
        playerId = json.getString("playerId")
        loadedRound = -1
        recordedRound = -1
        lastSentBoard = null
        bestRoundTripMs = Long.MAX_VALUE
        lastVersion = -1
        lastOpponentEmojiSeq = -1
        talliedRound = -1
        _state.update { it.copy(mode = mode, opponentReaction = null, myReaction = null, tally = emptyMap()) }
        val room = applyResponse(json, sentAt, record = false)
        // Joining a finished room must not count its old result again.
        if (room.status == RoomStatus.FINISHED) recordedRound = room.round
        if (newBackend.pushes != null) startListening(newBackend) else startPolling()
        val id = playerId
        val inMatch = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
        if (room.inSession && id != null && !(inMatch && room.atGame)) {
            val sent = System.currentTimeMillis()
            applyResponse(if (then != null) newBackend.openGame(room.code, id, then) else newBackend.toMenu(room.code, id), sent, record = false)
        }
    }

    // --- One connection, many games -----------------------------------------------------------

    /** Opens [game] in the room you're connected to, or joins it if it's the open one. The others see where you went. */
    fun openGame(game: GameKind) = runBusy {
        val room = _state.value.room ?: return@runBusy
        val id = playerId ?: return@runBusy
        _state.update { it.copy(hostGame = game) }
        val sentAt = System.currentTimeMillis()
        applyResponse(requireBackend().openGame(room.code, id, game), sentAt)
    }

    /**
     * Back to the games, still connected. Giving up a match being played is the caller's
     * question to ask. A room that plays only one game is left instead.
     */
    fun backToGames() {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (!room.inSession) return leave()
        // The menu shows at once; the room's answer follows.
        _state.update { it.copy(room = room.copy(me = room.me.copy(at = null))) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.toMenu(room.code, id), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem: ${e.message ?: "no connection"}")
            }
        }
    }

    private fun checkedUrl(): String? {
        val url = store.profile.value.scriptUrl
        if (!url.startsWith("https://")) {
            showMessage("To play online, paste the message you were sent: it has the link.")
            return null
        }
        return url
    }

    // --- Lobby and match ----------------------------------------------------------------------

    /**
     * Tapping a level marks you ready for it; tapping your chosen level again cancels.
     * The round starts once both players are ready with the same level.
     */
    fun chooseLevel(level: Difficulty) {
        val room = _state.value.room ?: return
        val cancel = room.me.ready && room.me.difficulty == level
        val me = if (cancel) room.me.copy(ready = false) else room.me.copy(ready = true, difficulty = level)
        sendReady(room.copy(me = me)) { b, id -> b.ready(room.code, id, !cancel, level) }
    }

    /** Sends a quick reaction; it shows next to your name on both phones. */
    fun react(reaction: Reaction) {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        _state.update { it.copy(myReaction = ShownReaction(reaction, System.currentTimeMillis())) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.emoji(room.code, id, reaction), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A lost reaction is not worth an error message.
            }
        }
    }

    fun shuffleFleet() = _state.update { it.copy(fleetDraft = Fleet.random()) }

    /** Ship Battle: the fleet as arranged by hand. */
    fun setFleet(fleet: List<Ship>) = _state.update { if (it.room?.me?.ready == true) it else it.copy(fleetDraft = fleet) }

    /** Ship Battle: ready with the drafted fleet, or not ready any more. */
    fun readyShips() {
        val room = _state.value.room ?: return
        val fleet = _state.value.fleetDraft
        sendReady(room.copy(me = room.me.copy(ready = !room.me.ready))) { b, id -> b.readyShips(room.code, id, !room.me.ready, fleet) }
    }

    /** Turn games: ready for the next round, or not ready any more. */
    fun readyTurns() {
        val room = _state.value.room ?: return
        sendReady(room.copy(me = room.me.copy(ready = !room.me.ready))) { b, id -> b.ready(room.code, id, !room.me.ready, null) }
    }

    private var readying = false

    /**
     * Ready buttons: [shown] (the room with your new ready state) appears at once, and the
     * server's answer replaces it. If the server refuses or can't be reached, it is undone.
     */
    private fun sendReady(shown: RoomState, request: suspend (BattleBackend, String) -> JSONObject) {
        val before = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (readying) return
        readying = true
        _state.update { it.copy(room = shown) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(request(b, id), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                undo(shown, before)
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                undo(shown, before)
                showMessage("Network problem, try again.")
            } finally {
                readying = false
            }
        }
    }

    /** Puts [before] back if the screen still shows the guess [shown] (no newer state arrived). */
    private fun undo(shown: RoomState?, before: RoomState) {
        if (shown != null) _state.update { if (it.room === shown) it.copy(room = before) else it }
    }

    /** Connect Four: drop a disc into [col]. */
    fun dropDisc(col: Int) {
        val turns = _state.value.room?.turns ?: return
        val board = turns.board ?: return
        val cell = ConnectFour.landing(board, col) ?: return
        val shown = turns.copy(board = board.replaceAt(cell, 'm'), lastCell = cell, lastMoveByMe = true, turn = "opponent")
        playMove(JSONObject().put("col", col), shown)
    }

    /** Dots & Boxes: draw [line]. */
    fun drawLine(line: Int) {
        val turns = _state.value.room?.turns ?: return
        val lines = turns.lines ?: return
        // Whether it closes a box (and keeps the turn) is the server's call; the line shows now.
        if (lines.getOrNull(line) == '.') playMove(JSONObject().put("line", line), turns.copy(lines = lines.replaceAt(line, 'm'), lastLine = line, lastMoveByMe = true))
    }

    /** Ultimate Tic-Tac-Toe: mark [cell]. */
    fun markCell(cell: Int) {
        val turns = _state.value.room?.turns ?: return
        val pos = TttPosition(turns.board ?: return, turns.smallBoards ?: return, turns.nextBoard ?: -1)
        if (!Ttt.isLegal(pos, cell)) return
        val after = Ttt.play(pos, 'm', cell)
        val shown = turns.copy(
            board = after.cells, smallBoards = after.boards, nextBoard = after.next.takeIf { it >= 0 },
            lastCell = cell, lastMoveByMe = true, turn = "opponent",
        )
        playMove(JSONObject().put("cell", cell), shown)
    }

    /** Memory Pairs: flip [card]. */
    fun flipCard(card: Int) {
        val turns = _state.value.room?.turns ?: return
        // The picture is the server's secret: the card starts turning and finishes when it arrives.
        if (turns.owners?.getOrNull(card) == '.' && turns.faces.getOrNull(card) == null) playMove(JSONObject().put("card", card), pendingCard = card)
    }

    /** Dominoes: put hand tile [index] on [end] ("left" or "right"). */
    fun playTile(index: Int, end: String) {
        val turns = _state.value.room?.turns ?: return
        val view = turns.dominoes ?: return
        val tile = view.hand.getOrNull(index) ?: return
        if (end !in Dominoes.fits(view.line, tile)) return
        val shown = view.copy(
            line = Dominoes.place(view.line, tile, end),
            hand = view.hand.filterIndexed { i, _ -> i != index },
            counts = view.counts + ('m' to view.hand.size - 1),
        )
        playMove(JSONObject().put("tile", index).put("end", end), turns.copy(dominoes = shown, lastMoveByMe = true, turn = "opponent"))
    }

    /** Colour Clash: play hand card [index] ([colour] names the colour for a wild). */
    fun playCard(index: Int, colour: Char? = null) {
        val turns = _state.value.room?.turns ?: return
        val view = turns.clash ?: return
        if (!view.canPlay(index)) return
        val card = view.hand[index]
        val move = JSONObject().put("card", index)
        if (colour != null) move.put("colour", colour.toString())
        // The card goes on the pile at once; who plays next is the server's call.
        val shown = view.copy(
            top = card, colour = colour ?: card[0], hand = view.hand.filterIndexed { i, _ -> i != index },
            counts = view.counts + ('m' to view.hand.size - 1), drawn = false,
        )
        playMove(move, turns.copy(clash = shown, lastMoveByMe = true))
    }

    /** Balloon Words: as this round's picker, choose the secret word. */
    fun pickBalloonWord(word: String) = playMove(JSONObject().put("word", word))

    /** Balloon Words: guess [letter]. */
    fun guessBalloonLetter(letter: Char) {
        val turns = _state.value.room?.turns ?: return
        val view = turns.balloon ?: return
        if (letter in view.guessed) return
        playMove(JSONObject().put("letter", letter.toString()), turns.copy(balloon = view.copy(guessed = view.guessed + letter)))
    }

    /** Mini Golf: shoot at [angle] with [power]; this phone works out where the ball stops. */
    fun shootGolf(angle: Double, power: Double) {
        val turns = _state.value.room?.turns ?: return
        val golf = turns.golf ?: return
        val hole = Golf.HOLES.getOrNull(golf.hole) ?: return
        val from = golf.balls['m']
        val shot = Golf.simulate(hole, from ?: hole.tee, angle, power)
        val shown = golf.copy(
            balls = golf.balls + ('m' to shot.end),
            last = GolfLastShot('m', golf.hole, from, angle, power, shot.end, shot.holed, (golf.last?.seq ?: 0) + 1),
        )
        playMove(
            JSONObject().put("angle", angle).put("power", power).put("x", shot.end.x).put("y", shot.end.y).put("holed", shot.holed),
            turns.copy(turn = "opponent", golf = shown),
        )
    }

    /** Corner Blocks: lay [p]; it shows at once and the server (or nearby host) checks it. */
    fun placeBlock(p: Placement) {
        val turns = _state.value.room?.turns ?: return
        val view = turns.blocks ?: return
        if (!view.allows(p)) return
        val board = view.board.toCharArray()
        for ((cx, cy) in Blocks.shape(p.piece, p.orient)) board[(p.y + cy) * view.size + p.x + cx] = 'm'
        val shown = view.copy(board = String(board), left = view.left + ('m' to view.left['m'].orEmpty() - p.piece))
        playMove(JSONObject().put("piece", p.piece).put("orient", p.orient).put("x", p.x).put("y", p.y), turns.copy(turn = "opponent", blocks = shown))
    }

    /** Ludo: roll the die (the server rolls it). */
    fun rollDie() = playMove(JSONObject().put("roll", true))

    /** Ludo: move token [index] by the roll. */
    fun moveToken(index: Int) {
        val turns = _state.value.room?.turns ?: return
        val view = turns.ludo ?: return
        val roll = view.roll ?: return
        if (index !in view.movable) return
        // The token moves at once; captures and who goes next are the server's call.
        val mine = view.tokens['m'].orEmpty().toMutableList()
        mine[index] = if (mine[index] == -1) 0 else mine[index] + roll
        playMove(JSONObject().put("token", index), turns.copy(ludo = view.copy(tokens = view.tokens + ('m' to mine), roll = null, movable = emptyList()), lastMoveByMe = true))
    }

    /** Colour Clash: take a card from the deck. */
    fun drawCard() = playMove(JSONObject().put("draw", true))

    /** Colour Clash: keep the card you drew and end your turn. */
    fun passCard() {
        val turns = _state.value.room?.turns ?: return
        playMove(JSONObject().put("pass", true), turns.copy(lastMoveByMe = true, turn = "opponent"))
    }

    /** Dominoes: take a tile from the boneyard (when nothing in your hand fits). */
    fun drawTile() = playMove(JSONObject().put("draw", true))

    /** Dominoes: pass (when nothing fits and there is nothing to draw). */
    fun passTurn() {
        val turns = _state.value.room?.turns ?: return
        playMove(JSONObject().put("pass", true), turns.copy(lastMoveByMe = true, turn = "opponent"))
    }

    /** Dominoes: whoever opened the game changes the rules of the next match; everyone gets ready again. */
    fun setDominoOptions(options: DominoOptions) {
        val room = _state.value.room ?: return
        if (!room.setsOptions || room.options == options) return
        sendReady(room.copy(options = options, me = room.me.copy(ready = false))) { b, id -> b.setOptions(room.code, id, options.toJson()) }
    }

    /** Tic-Tac-Toe: whoever opened the game picks normal (3 × 3) or Ultimate; everyone gets ready again. */
    fun setTttClassic(classic: Boolean) {
        val room = _state.value.room ?: return
        if (!room.setsOptions || room.tttClassic == classic) return
        sendReady(room.copy(tttClassic = classic, me = room.me.copy(ready = false))) { b, id ->
            b.setOptions(room.code, id, JSONObject().put("mode", if (classic) "classic" else "ultimate"))
        }
    }

    /**
     * Turn games: send one move when it is this player's turn. [shown] is the board with the
     * move already made, so it appears at once instead of after a trip to the server; the
     * server's answer replaces it, or it is undone if the move fails.
     */
    private fun playMove(move: JSONObject, shown: TurnsView? = null, pendingCard: Int? = null) {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (s.firing || room.turns?.turn != "me") return
        val guess = shown?.let { room.copy(turns = it) }
        _state.update { it.copy(firing = true, room = guess ?: it.room, pendingCard = pendingCard) }
        haptics.play(Buzz.MOVE)
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.move(room.code, id, move), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                undo(guess, room)
                if (!e.fatal) {
                    haptics.play(Buzz.NOT_ALLOWED)
                    sfx.play(Sound.ERROR)
                }
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                undo(guess, room)
                showMessage("Network problem, try again.")
            } finally {
                _state.update { it.copy(firing = false, pendingCard = null) }
            }
        }
    }

    private fun String.replaceAt(i: Int, c: Char) = substring(0, i) + c + substring(i + 1)

    /** Word Race: type a letter of the next guess. */
    fun typeLetter(letter: Char) = _state.update {
        if (it.wordInput.length < (it.room?.words?.length ?: 5)) it.copy(wordInput = it.wordInput + letter.lowercaseChar()) else it
    }

    fun deleteLetter() = _state.update { it.copy(wordInput = it.wordInput.dropLast(1)) }

    /** Word Race: send the typed word as a guess; it stays typed if the server refuses it. */
    fun submitWord() {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        val word = s.wordInput
        if (s.firing || word.length < (room.words?.length ?: 5)) return
        // Only real words count: say so at once instead of after a trip to the server.
        if (!WordList.isWord(word)) return showMessage("\"${word.uppercase()}\" isn't in the word list.")
        _state.update { it.copy(firing = true) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.guess(room.code, id, word), sentAt)
                _state.update { it.copy(wordInput = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // Not a word, or the wrong length: it stays typed, and the phone says no.
                if (!e.fatal) {
                    haptics.play(Buzz.NOT_ALLOWED)
                    sfx.play(Sound.ERROR)
                }
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            } finally {
                _state.update { it.copy(firing = false) }
            }
        }
    }

    /**
     * Draw & Guess: the strokes to show when a room state arrives. A new turn or a newer clear
     * takes the server's list; a state from before your own clear is ignored. Otherwise the longer
     * list wins: strokes still on their way (yours, or live ones from your partner) are kept.
     */
    private fun mergeStrokes(round: Int, draw: DrawView, shown: List<DrawStroke>): List<DrawStroke> = when {
        (round to draw.turn) != strokesTurn || draw.clears > strokesClears -> {
            strokesTurn = round to draw.turn
            strokesClears = draw.clears
            draw.strokes
        }
        draw.clears < strokesClears -> shown
        draw.strokes.size >= shown.size -> draw.strokes
        else -> shown
    }

    /** Draw & Guess: a stroke your partner just drew, sent on its own. */
    private fun addLiveStroke(json: JSONObject) {
        val turn = _state.value.room?.draw?.turn ?: return
        if (json.optInt("turn") != turn) return
        val stroke = json.optJSONObject("stroke")?.let(DrawStroke::from) ?: return
        // A stroke a room state already brought must not show twice.
        _state.update { if (json.optInt("index") < it.drawStrokes.size) it else it.copy(drawStrokes = it.drawStrokes + stroke) }
    }

    /** Draw & Guess: you drew [stroke]; it shows at once and goes to your partner in the background. */
    fun sendStroke(stroke: DrawStroke) {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (room.draw?.drawing != true) return
        _state.update { it.copy(drawStrokes = it.drawStrokes + stroke) }
        val turn = room.draw?.turn ?: return
        viewModelScope.launch {
            // While the connection is coming back the send fails; try again for a few seconds.
            for (wait in STROKE_RETRY_MS) {
                try {
                    b.stroke(room.code, id, stroke.toJson())
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    return@launch // The turn is over or the stroke was refused: nothing to retry.
                } catch (e: IOException) {
                    delay(wait)
                    if (_state.value.room?.draw?.turn != turn || backend !== b) return@launch
                }
            }
        }
    }

    fun clearDrawing() {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        // Older states still hold the old drawing; they are ignored until the server counts this clear.
        strokesClears = (room.draw?.clears ?: 0) + 1
        _state.update { it.copy(drawStrokes = emptyList()) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.clearDrawing(room.code, id), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                strokesClears = room.draw?.clears ?: 0
                showMessage("Could not clear the drawing, try again.")
            }
        }
    }

    /** Draw & Guess: guess what your partner is drawing. */
    fun guessDrawing(text: String) {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.guess(room.code, id, text), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            }
        }
    }

    /** Ship Battle: fire at the opponent's sea. */
    fun fire(x: Int, y: Int) {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        val ships = room.ships ?: return
        if (s.firing || ships.turn != "me" || ships.myShots.any { it.x == x && it.y == y }) return
        _state.update { it.copy(firing = true, pendingShot = x to y) }
        haptics.play(Buzz.MOVE)
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.fire(room.code, id, x, y), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (!e.fatal) {
                    haptics.play(Buzz.NOT_ALLOWED)
                    sfx.play(Sound.ERROR)
                }
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            } finally {
                _state.update { it.copy(firing = false, pendingShot = null) }
            }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selected = if (it.selected == index) null else index) }
    fun toggleNotes() = _state.update { it.copy(notesMode = !it.notesMode) }

    fun enterDigit(digit: Int) {
        val s = _state.value
        val index = s.selected ?: return
        val board = s.board ?: return
        if (!s.canEdit() || board.givens[index]) return
        val updated = if (s.notesMode && digit != 0) board.toggleNote(index, digit) else board.place(index, digit)
        if (updated === board) return
        _state.update { it.copy(board = updated) }
        syncNow.trySend(Unit)
    }

    fun erase() = enterDigit(0)

    // ---- Air Hockey: real time ----------------------------------------------------------------
    //
    // The host's phone runs the table (it is the bottom player) and sends where everything is
    // 20 times a second; the guest sends where its paddle is. Each phone sees itself at the
    // bottom: the guest's view is the table turned round. Goals go to the server as the score.

    private val _hockey = MutableStateFlow<HockeyFrame?>(null)
    /** The table as this phone sees it (you at the bottom), many times a second. */
    val hockey: StateFlow<HockeyFrame?> = _hockey.asStateFlow()
    private var hockeyJob: Job? = null
    private var hockeyRound = -1
    private var hockeyTable: HockeyTable? = null
    // The guest: the last table from the host (host's view) and when it came; its own paddle.
    private var hockeySnapshot: HockeyFrame? = null
    private var hockeySnapshotAt = 0L
    private var myPaddle = HockeyFrame.start().bottom

    /** Air Hockey: your finger at (x, y), in your own view of the table. */
    fun moveHockeyPaddle(x: Float, y: Float) {
        val table = hockeyTable
        if (table != null) table.moveBottom(x, y) else myPaddle = HockeyTable.clampPaddle(Side.BOTTOM, x, y)
    }

    private fun stopHockey() {
        hockeyJob?.cancel()
        hockeyJob = null
        hockeyTable = null
    }

    /** Starts the table for a new round, and stops it when the round is over. */
    private fun syncHockey(room: RoomState) {
        val live = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
        if (!live) {
            stopHockey()
            return
        }
        if (room.round == hockeyRound && hockeyJob != null) return
        stopHockey()
        hockeyRound = room.round
        hockeySnapshot = null
        myPaddle = HockeyFrame.start().bottom
        _hockey.value = HockeyFrame.start()
        hockeyJob = viewModelScope.launch { if (room.isHost) hostHockey(room.code) else guestHockey() }
    }

    private fun playingNow(): Boolean {
        val s = _state.value
        return s.phase(s.serverNow()) == RoomStatus.PLAYING
    }

    private suspend fun hostHockey(code: String) {
        val table = HockeyTable().also { hockeyTable = it }
        var last = System.nanoTime()
        var lastSent = 0L
        var reported = 0 to 0
        while (true) {
            delay(HOCKEY_FRAME_MS)
            val now = System.nanoTime()
            val dt = (now - last) / 1e9f
            last = now
            if (!playingNow()) continue
            val scorer = table.step(dt)
            val f = table.frame
            _hockey.value = f
            if (scorer != null && (f.bottomScore to f.topScore) != reported) {
                reported = f.bottomScore to f.topScore
                val score = listOf(f.bottomScore, f.topScore)
                viewModelScope.launch {
                    val id = playerId ?: return@launch
                    try {
                        applyResponse(backend?.hockeyScore(code, id, score) ?: return@launch, null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showMessage("Couldn't send the score; it goes with the next goal.")
                        reported = -1 to -1
                    }
                }
                if (f.bottomScore >= Hockey.GOALS || f.topScore >= Hockey.GOALS) break
            }
            val ms = System.currentTimeMillis()
            if (ms - lastSent >= HOCKEY_SEND_MS) {
                lastSent = ms
                backend?.sendLive(f.toLive())
            }
        }
    }

    private suspend fun guestHockey() {
        var lastSent = 0L
        var sentPaddle: Body? = null
        while (true) {
            delay(HOCKEY_FRAME_MS)
            val snap = hockeySnapshot
            if (snap != null) {
                // Your view is the host's table turned round; the puck moves on from where it was last seen.
                val age = (System.currentTimeMillis() - hockeySnapshotAt) / 1000f
                val mine = snap.flipped()
                _hockey.value = mine.copy(puck = mine.puck.ahead(age), bottom = myPaddle)
            } else {
                _hockey.value = _hockey.value?.copy(bottom = myPaddle)
            }
            val ms = System.currentTimeMillis()
            if (playingNow() && ms - lastSent >= HOCKEY_SEND_MS && myPaddle != sentPaddle) {
                lastSent = ms
                sentPaddle = myPaddle
                val onTable = myPaddle.flip()
                backend?.sendLive(JSONObject().put("g", JSONArray().put(onTable.x.toDouble()).put(onTable.y.toDouble())))
            }
        }
    }

    /** A live message from the other phone: the host's table, or the guest's paddle. */
    private fun onLive(data: JSONObject) {
        val table = hockeyTable
        if (table != null) {
            data.optJSONArray("g")?.let { table.moveTop(it.optDouble(0).toFloat(), it.optDouble(1).toFloat()) }
        } else {
            hockeyFrameFrom(data)?.let {
                hockeySnapshot = it
                hockeySnapshotAt = System.currentTimeMillis()
            }
        }
    }

    private fun HockeyFrame.toLive(): JSONObject {
        fun body(b: Body) = JSONArray().put(b.x.toDouble()).put(b.y.toDouble()).put(b.vx.toDouble()).put(b.vy.toDouble())
        return JSONObject().put("p", body(puck)).put("b", body(bottom)).put("t", body(top))
            .put("s", JSONArray().put(bottomScore).put(topScore))
    }

    private fun hockeyFrameFrom(data: JSONObject): HockeyFrame? {
        fun body(key: String): Body? = data.optJSONArray(key)?.let {
            Body(it.optDouble(0).toFloat(), it.optDouble(1).toFloat(), it.optDouble(2).toFloat(), it.optDouble(3).toFloat())
        }
        val score = data.optJSONArray("s")
        return HockeyFrame(body("p") ?: return null, body("b") ?: return null, body("t") ?: return null, score?.optInt(0) ?: 0, score?.optInt(1) ?: 0)
    }

    /** Letter Grid: ready (or not). The host's phone rolls the next grid and sends it with its Ready. */
    fun readyGrid() {
        val room = _state.value.room ?: return
        val ready = !room.me.ready
        val letters = if (ready && room.isHost) LetterGridGenerator().generate() else null
        sendReady(room.copy(me = room.me.copy(ready = ready))) { b, id -> b.readyGrid(room.code, id, ready, letters) }
    }

    /**
     * Letter Grid: a traced word. It is checked here first (on the grid, in the list, not
     * found already) so a miss is told at once; a good word shows in your list straight away.
     */
    fun findGridWord(word: String) {
        val room = _state.value.room ?: return
        val grid = room.grid ?: return
        val letters = grid.letters ?: return
        val id = playerId ?: return
        val b = backend ?: return
        val w = word.lowercase()
        if (w.length < 3) return
        if (w in grid.mine) return showMessage("You already found ${w.uppercase()}.")
        LetterGrid.problem(letters, w)?.let { return showMessage(it) }
        val shown = room.copy(grid = grid.copy(mine = grid.mine + w))
        _state.update { it.copy(room = shown) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.guess(room.code, id, w), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                undo(shown, room)
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                undo(shown, room)
                showMessage("Network problem, try again.")
            }
        }
    }

    /** Code Breaker: set your code, or (once both are set) guess theirs. Bingo claims go the same way. */
    fun sendCode(code: String) {
        val room = _state.value.room ?: return
        val id = playerId ?: return
        val b = backend ?: return
        if (_state.value.firing || (room.game == GameKind.CODE && !CodeBreaker.isCode(code))) return
        _state.update { it.copy(firing = true) }
        viewModelScope.launch {
            try {
                val sentAt = System.currentTimeMillis()
                applyResponse(b.guess(room.code, id, code), sentAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem, try again.")
            } finally {
                _state.update { it.copy(firing = false) }
            }
        }
    }

    /** Slide Race: ready (or not). The host's phone shuffles the next puzzle and sends it with its Ready. */
    fun readySlide() {
        val room = _state.value.room ?: return
        val ready = !room.me.ready
        val tiles = if (ready && room.isHost) Slide.shuffle(room.slide?.level?.size ?: 3) else null
        sendReady(room.copy(me = room.me.copy(ready = ready))) { b, id -> b.readySlide(room.code, id, ready, tiles) }
    }

    /** Slide Race: the host picks the size of the next puzzle; everyone gets ready again. */
    fun setSlideLevel(level: SlideLevel) {
        val room = _state.value.room ?: return
        val slide = room.slide ?: return
        if (!room.setsOptions || slide.level == level) return
        sendReady(room.copy(slide = slide.copy(level = level, size = level.size), me = room.me.copy(ready = false))) { b, id ->
            b.setOptions(room.code, id, JSONObject().put("level", level.key))
        }
    }

    /** Bingo: dab (or un-dab) square [index] of your card; only called numbers take a dab. */
    fun dabBingo(index: Int) {
        val s = _state.value
        val room = s.room ?: return
        val bingo = room.bingo ?: return
        val card = bingo.card ?: return
        val calls = bingo.calls ?: return
        val start = room.startAt ?: return
        if (s.phase(s.serverNow()) != RoomStatus.PLAYING || index == Bingo.FREE) return
        val n = card.getOrNull(index) ?: return
        if (n !in calls.take(Bingo.calledCount(s.serverNow() - start))) return
        _state.update {
            val dabs = if (it.bingoRound == room.round) it.bingoDabs else emptySet()
            it.copy(bingoDabs = if (index in dabs) dabs - index else dabs + index, bingoRound = room.round)
        }
    }

    /** Bingo: claim a full line; the server checks it against the calls. */
    fun claimBingo() = sendCode(BINGO_CLAIM)

    /** Slide Race: a swipe anywhere on the screen slides the tile next to the gap that way. */
    fun swipeSlide(dx: Int, dy: Int) {
        val s = _state.value
        val slide = s.room?.slide ?: return
        val start = slide.start ?: return
        val tiles = Slide.replay(start, slide.size, s.slideMoves)
        Slide.tileForSwipe(tiles, slide.size, dx, dy)?.let(::tapSlideTile)
    }

    /** Slide Race: tap a tile in line with the gap; it (and any tiles between) slide over at once. */
    fun tapSlideTile(tile: Int) {
        val s = _state.value
        val slide = s.room?.slide ?: return
        val start = slide.start ?: return
        if (s.phase(s.serverNow()) != RoomStatus.PLAYING || s.slideRound != s.room?.round) return
        val tiles = Slide.replay(start, slide.size, s.slideMoves)
        val slides = Slide.slidesFor(tiles, slide.size, tile)
        if (slides.isEmpty()) return
        _state.update { it.copy(slideMoves = it.slideMoves + slides) }
        syncNow.trySend(Unit)
    }

    /** Number Cross: ready (or not). The host's phone builds the next puzzle and sends it with its Ready. */
    fun readyNumbers() {
        val room = _state.value.room ?: return
        val ready = !room.me.ready
        val puzzle = if (ready && room.isHost) NumberCrossGenerator().generate(room.numbers?.level ?: NumberLevel.EASY).toJson() else null
        sendReady(room.copy(me = room.me.copy(ready = ready))) { b, id -> b.readyNumbers(room.code, id, ready, puzzle) }
    }

    /** Word Race: the host picks the level (the length of the word); everyone gets ready again. */
    fun setWordLevel(level: WordLevel) {
        val room = _state.value.room ?: return
        val words = room.words ?: return
        if (!room.setsOptions || words.level == level) return
        sendReady(room.copy(words = words.copy(level = level, length = level.lengths.first()), me = room.me.copy(ready = false))) { b, id ->
            b.setOptions(room.code, id, JSONObject().put("level", level.key))
        }
    }

    /** Number Cross: the host picks the level of the next puzzle; everyone gets ready again. */
    fun setNumberLevel(level: NumberLevel) {
        val room = _state.value.room ?: return
        val numbers = room.numbers ?: return
        if (!room.setsOptions || numbers.level == level) return
        sendReady(room.copy(numbers = numbers.copy(level = level), me = room.me.copy(ready = false))) { b, id ->
            b.setOptions(room.code, id, JSONObject().put("level", level.key))
        }
    }

    /** Number Cross: pick blank [k] to fill (again to put it back). */
    fun selectBlank(k: Int) = _state.update { it.copy(numberSelected = if (it.numberSelected == k) null else k) }

    /** Number Cross: put [value] in the picked blank (or "" to empty it), then pick the next empty one. */
    fun placeNumber(value: String) {
        val s = _state.value
        val k = s.numberSelected ?: return
        val entries = s.numberEntries ?: return
        if (s.phase(s.serverNow()) != RoomStatus.PLAYING || k !in entries.indices) return
        val updated = entries.toMutableList().also { it[k] = value }
        val next = if (value == "") k else (1..updated.size).map { (k + it) % updated.size }.firstOrNull { updated[it] == "" }
        _state.update { it.copy(numberEntries = updated, numberSelected = next) }
        syncNow.trySend(Unit)
    }

    fun undo() {
        val s = _state.value
        val board = s.board ?: return
        if (!s.canEdit() || !board.canUndo) return
        _state.update { it.copy(board = board.undo()) }
        syncNow.trySend(Unit)
    }

    fun leave() {
        val room = _state.value.room
        val id = playerId
        val old = backend
        endSession(null, closeBackend = false)
        if (old != null) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    if (room != null && id != null) runCatching { old.leave(room.code, id) }
                    old.close()
                }
            }
        }
    }

    /** Called by the UI when the app goes to or comes back from the background. */
    fun setForeground(value: Boolean) {
        foreground.value = value
        // In a turn game, closing the app disconnects so the server can send "your turn" pushes.
        if (_state.value.room?.game?.turnBased == true) {
            if (value) backend?.resume() else backend?.pause()
        }
    }

    /** Polling servers (Apps Script): ask for news every few seconds. */
    private fun startPolling() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            while (isActive) {
                val moved = withTimeoutOrNull(pollInterval()) { syncNow.receive() } != null
                // Quick successive moves go out together in one call.
                if (moved) delay(MOVE_BATCH_MS)
                // Outside a match there is nothing to miss, so don't poll from the background.
                if (!inMatch()) foreground.first { it }
                syncOnce(polling = true)
            }
        }
    }

    /** Live servers (Cloudflare, nearby phone): updates arrive by themselves; only send moves. */
    private fun startListening(b: BattleBackend) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            launch {
                b.pushes?.collect { json ->
                    if (json.optString("type") == "error") {
                        if (json.optBoolean("fatal")) endSession(json.optString("error")) else showMessage(json.optString("error"))
                    } else if (json.optString("type") == "live") {
                        onLive(json.optJSONObject("data") ?: return@collect)
                    } else if (json.optString("type") == "stroke") {
                        addLiveStroke(json)
                    } else if (json.has("me")) {
                        applyResponse(json, sentAt = null)
                    }
                }
            }
            launch { b.connected.collect { up -> _state.update { it.copy(reconnecting = !up) } } }
            while (isActive) {
                syncNow.receive()
                delay(MOVE_BATCH_MS)
                syncOnce(polling = false)
            }
        }
    }

    private fun inMatch(): Boolean {
        val s = _state.value
        val phase = s.phase(s.serverNow())
        return phase == RoomStatus.COUNTDOWN || phase == RoomStatus.PLAYING
    }

    /** Fast while the opponent is busy, slower when nothing is happening. */
    private fun pollInterval(): Long {
        val s = _state.value
        return when (s.phase(s.serverNow())) {
            RoomStatus.COUNTDOWN -> 2000L
            RoomStatus.PLAYING ->
                if (System.currentTimeMillis() - opponentActiveAt < ACTIVE_WINDOW_MS) 2000L else 4000L
            else -> 4000L
        }
    }

    private suspend fun syncOnce(polling: Boolean) {
        val s = _state.value
        val room = s.room ?: return
        val id = playerId ?: return
        val board = when {
            room.game == GameKind.SLIDE ->
                s.slideMoves.takeIf { it.isNotEmpty() && s.phase(s.serverNow()) == RoomStatus.PLAYING && s.slideRound == room.round }?.joinToString(",")
            room.game == GameKind.NUMBERS ->
                s.numberEntries?.takeIf { s.phase(s.serverNow()) == RoomStatus.PLAYING && s.numbersRound == room.round }?.joinToString(",")
            s.canEdit() -> Sudoku.encode(s.board!!.cells)
            else -> null
        }?.takeIf { it != lastSentBoard }
        // A live server pushes news by itself, so there is only something to do with a new board.
        if (!polling && board == null) return
        val sentAt = System.currentTimeMillis()
        try {
            val json = requireBackend().sync(room.code, id, board, lastVersion.takeIf { it >= 0 && polling })
            if (json.optBoolean("unchanged")) {
                // A stored board always changes the version, so "unchanged" means it was not stored.
                applyUnchanged(json, sentAt)
            } else {
                val updated = applyResponse(json, sentAt)
                // The server ignores boards that arrive before its own clock reaches the start.
                if (board != null && updated.status != RoomStatus.COUNTDOWN) {
                    lastSentBoard = board
                } else if (board != null && !polling) {
                    delay(EARLY_BOARD_RETRY_MS)
                    syncNow.trySend(Unit)
                }
            }
            if (polling) _state.update { it.copy(reconnecting = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.fatal) endSession(e.message) else showMessage(e.message)
        } catch (e: IOException) {
            _state.update { it.copy(reconnecting = true) }
            delay(1000)
            // Try the move again once the connection is back.
            if (!polling) syncNow.trySend(Unit)
        }
    }

    /** Server clock minus local clock; the fastest round trip gives the most accurate value. */
    private fun clockOffset(serverTime: Long, sentAt: Long): Long {
        val receivedAt = System.currentTimeMillis()
        val roundTrip = receivedAt - sentAt
        if (roundTrip > bestRoundTripMs) return _state.value.clockOffsetMs
        bestRoundTripMs = roundTrip
        return serverTime - (sentAt + receivedAt) / 2
    }

    private fun applyUnchanged(json: JSONObject, sentAt: Long) {
        val offset = clockOffset(json.getLong("serverTime"), sentAt)
        val idle = if (json.isNull("opponentIdleMs")) null else json.optLong("opponentIdleMs")
        _state.update { s ->
            val room = s.room ?: return@update s
            s.copy(
                clockOffsetMs = offset,
                room = if (idle == null) room else room.copy(opponent = room.opponent?.copy(idleMs = idle)),
            )
        }
    }

    /** [sentAt] is when the request went out; null for pushed updates, which can't time the clock. */
    private fun applyResponse(json: JSONObject, sentAt: Long?, record: Boolean = true): RoomState {
        val room = parseRoom(json)
        // Your turn just came round in a turn game or Ship Battle.
        val before = _state.value.room
        fun myTurn(r: RoomState?) = r != null && r.atGame && r.status == RoomStatus.PLAYING && (r.turns?.turn == "me" || r.ships?.turn == "me")
        if (myTurn(room) && !myTurn(before)) {
            haptics.play(Buzz.YOUR_TURN)
            sfx.play(Sound.YOUR_TURN)
        }
        val offset = sentAt?.let { clockOffset(room.serverTime, it) } ?: _state.value.clockOffsetMs
        lastVersion = json.optInt("version", -1)
        if (room.others.map { it.progress } != _state.value.room?.others?.map { it.progress }) {
            opponentActiveAt = System.currentTimeMillis()
        }
        // A new Ship Battle round needs a fresh fleet.
        if (room.game == GameKind.SHIPS && room.status == RoomStatus.FINISHED && room.round != reshuffledRound) {
            reshuffledRound = room.round
            _state.update { it.copy(fleetDraft = Fleet.random()) }
        }
        // Show other players' reactions when new ones arrive (not old ones on first load).
        val now = System.currentTimeMillis()
        val fresh = room.others.mapNotNull { other ->
            other.emoji?.takeIf { lastOpponentEmojiSeq >= 0 && it.seq > lastOpponentEmojiSeq }?.let { other.name to it }
        }
        lastOpponentEmojiSeq = maxOf(lastOpponentEmojiSeq, room.others.maxOfOrNull { it.emoji?.seq ?: 0 } ?: 0)
        _state.update { s ->
            var next = s.copy(room = room, clockOffsetMs = offset)
            fresh.maxByOrNull { it.second.seq }?.let { (_, e) -> next = next.copy(opponentReaction = ShownReaction(e.reaction, now)) }
            if (fresh.isNotEmpty()) next = next.copy(otherReactions = next.otherReactions + fresh.map { (name, e) -> name to ShownReaction(e.reaction, now) })
            if (room.game == GameKind.WORDS && room.round != next.wordRound) next = next.copy(wordInput = "", wordRound = room.round)
            room.draw?.let { next = next.copy(drawStrokes = mergeStrokes(room.round, it, s.drawStrokes)) }
            room.slide?.takeIf { it.start != null && room.round != next.slideRound }?.let { sl ->
                lastSentBoard = null
                next = next.copy(slideMoves = sl.moves, slideRound = room.round)
            }
            room.numbers?.board?.let { board ->
                if (room.round != next.numbersRound) {
                    lastSentBoard = null
                    next = next.copy(
                        numberEntries = room.numbers?.entries?.takeIf { it.size == board.blanks.size } ?: List(board.blanks.size) { "" },
                        numberSelected = null,
                        numbersRound = room.round,
                    )
                }
            }
            val puzzle = room.puzzle
            if (puzzle != null && room.round != loadedRound) {
                loadedRound = room.round
                lastSentBoard = null
                next = next.copy(board = BoardState.fromPuzzle(puzzle), selected = null, notesMode = false)
            }
            next
        }
        rememberPartner(room)
        if (room.game == GameKind.HOCKEY && room.atGame) syncHockey(room)
        countForTonight(room)
        // A match counts for the players who were in it, not for someone waiting in the menu.
        if (record && room.atGame && room.status == RoomStatus.FINISHED && room.winner != null && room.round != recordedRound) {
            recordedRound = room.round
            store.recordBattle(won = room.winner == "me")
        }
        return room
    }

    /** The round last counted in [BattleUiState.tally], so each result counts once. */
    private var talliedRound = -1

    /** Adds a finished match's winner (or winners, for a team) to the score since connecting. */
    private fun countForTonight(room: RoomState) {
        if (!room.atGame || room.status != RoomStatus.FINISHED || room.round == talliedRound) return
        talliedRound = room.round
        val winners = when (room.winner) {
            "me" -> listOf(TALLY_ME)
            "opponent" -> room.winnerName?.split(" & ").orEmpty()
            else -> emptyList()
        }
        if (winners.isEmpty()) return
        _state.update { s -> s.copy(tally = s.tally + winners.associateWith { (s.tally[it] ?: 0) + 1 }) }
    }

    /** Saves an online opponent so the next game is one tap away. */
    private fun rememberPartner(room: RoomState) {
        val (url, password) = sessionServer ?: return
        val opponent = room.opponent?.takeIf { !it.left } ?: return
        val old = store.profile.value.partner
        val partner = SavedPartner(
            serverUrl = url,
            password = password,
            code = room.code,
            name = opponent.name,
            avatar = opponent.avatar,
            deviceId = opponent.deviceId ?: old?.takeIf { it.name == opponent.name }?.deviceId,
            myPlayerId = playerId,
        )
        if (partner != old) {
            store.setPartner(partner)
            // A new partner or server: make sure this phone can be invited there.
            if (old == null || old.serverUrl != partner.serverUrl || old.deviceId != partner.deviceId) {
                viewModelScope.launch { Push.registerEverywhere(getApplication()) }
            }
        }
    }

    private fun endSession(message: String?, closeBackend: Boolean = true) {
        stopHockey()
        _hockey.value = null
        sessionJob?.cancel()
        sessionJob = null
        if (closeBackend) backend?.close()
        backend = null
        playerId = null
        sessionServer = null
        loadedRound = -1
        lastVersion = -1
        _state.update {
            it.copy(
                room = null, board = null, selected = null, notesMode = false, reconnecting = false,
                message = message, opponentReaction = null, otherReactions = emptyMap(), myReaction = null,
                hostCode = store.profile.value.partner?.code ?: Invite.randomCode(),
            )
        }
    }

    private fun requireBackend() = backend ?: throw ApiException("Not connected to a game.", fatal = true)

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.fatal && _state.value.room != null) endSession(e.message) else showMessage(e.message)
            } catch (e: IOException) {
                showMessage("Network problem: ${e.message ?: "no connection"}")
            } catch (e: Exception) {
                showMessage(e.message ?: "That didn't work. Try again in a moment.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun showMessage(message: String?) = _state.update { it.copy(message = message) }

    override fun onCleared() {
        // The app is closing: tell the server so the opponent is not left waiting.
        val room = _state.value.room
        val id = playerId
        val old = backend
        if (old != null) {
            CoroutineScope(Dispatchers.IO).launch {
                if (room != null && id != null) runCatching { old.leave(room.code, id) }
                old.close()
            }
        }
    }

    private companion object {
        const val MOVE_BATCH_MS = 700L
        // Bingo: a claim goes as a "guess" with this word; the server only checks the card.
        const val BINGO_CLAIM = "bingo"
        // Air Hockey: the table steps about 60 times a second and goes to the other phone 20 times a second.
        const val HOCKEY_FRAME_MS = 16L
        const val HOCKEY_SEND_MS = 50L
        val STROKE_RETRY_MS = listOf(500L, 1000L, 2000L, 4000L, 4000L)
        const val ACTIVE_WINDOW_MS = 15_000L
        const val EARLY_BOARD_RETRY_MS = 500L

        fun cleanCode(value: String) = value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(12)

        fun displayName(name: String) = name.trim().ifBlank { "Player" }
    }
}
