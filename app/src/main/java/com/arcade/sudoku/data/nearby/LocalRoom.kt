package com.arcade.sudoku.data.nearby

import com.arcade.sudoku.data.ApiException
import com.arcade.sudoku.data.Avatar
import com.arcade.sudoku.data.Difficulty
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.data.Reaction
import com.arcade.sudoku.data.toJson
import com.arcade.sudoku.game.Generator
import com.arcade.sudoku.game.turns.TurnMatch
import com.arcade.sudoku.game.words.WordList
import com.arcade.sudoku.game.words.Words
import com.arcade.sudoku.game.ships.Fleet
import com.arcade.sudoku.game.ships.Sea
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import kotlin.random.Random
import org.json.JSONObject
import java.util.UUID

/**
 * The battle rules for a nearby game, run on the host's phone. They match the servers
 * (backend/cloudflare/src/room.js) and produce the same JSON views, so the rest of the
 * app treats a nearby room like an online one.
 */
class LocalRoom(
    val code: String,
    hostName: String,
    hostAvatar: Avatar?,
    val game: GameKind = GameKind.SUDOKU,
    private val generate: (Int) -> Generator.Result = { Generator.generate(it) },
) {
    class Player(val id: String, val name: String, var avatar: Avatar?) {
        var ready = false
        var difficulty: Difficulty? = null
        var board: String? = null
        var progress = 0
        var finishedAt: Long? = null
        var left = false
        var connected = true
        var awaySince: Long? = null
        var emoji: Pair<Reaction, Int>? = null
        var emojiAt = 0L
        // Ship Battle
        var pendingFleet: List<Ship>? = null
        var fleet: List<Ship> = emptyList()
        var shots: List<Shot> = emptyList()
        // Word Race: (word, colours) per guess
        var guesses: List<Pair<String, String>> = emptyList()
        var solved = false
        /** Left or lost their connection during the match being played, which goes on without them. */
        var out = false
    }

    var status = "waiting"
        private set
    var startAt: Long? = null
        private set
    private var version = 0
    private var difficulty: Difficulty? = null
    private var round = 0
    private var puzzle: String? = null
    private var solution: String? = null
    private var winner: String? = null
    private var endReason: String? = null
    private var emojiSeq = 0
    private var turn: String? = null
    private var lastShot: Pair<String, Shot>? = null
    // Turn games
    private var firstTurn: String? = null
    private var turnMatch: TurnMatch? = null
    // Word Race
    private var answer: String? = null
    // Draw & Guess
    private var drawTurn = 0
    private var drawer = HOST
    private var drawWord = ""
    private val usedWords = mutableListOf<String>()
    private var strokes = mutableListOf<JSONObject>()
    private var drawClears = 0
    private var drawGuesses = mutableListOf<Pair<String, String>>()
    private var drawScore = 0
    private var lastDrawTurn: JSONObject? = null
    private var turnEndsAt = 0L
    private var drawTurns = DRAW_TURNS
    /** The players a match started with, in seat order. */
    private var inMatch: List<String> = listOf(HOST, GUEST)
    val players = mutableMapOf<String, Player?>(HOST to newPlayer(hostName, hostAvatar), GUEST to null, P3 to null, P4 to null)

    fun playerId(role: String) = players.getValue(role)!!.id

    /** Roles with a player in them (gone ones included until the lobby clears them). */
    fun seated() = ROLES.filter { players[it] != null }

    /** Players still in the room. */
    private fun present() = seated().filter { !players.getValue(it)!!.left }

    /** Players still playing this match. */
    private fun playing() = inMatch.filter { r -> players[r]?.let { !it.left && !it.out } == true }

    /** The other players as [role] sees them: seat order starting after their own seat. */
    fun others(role: String): List<String> {
        val roles = seated()
        val i = roles.indexOf(role)
        return roles.drop(i + 1) + roles.take(maxOf(i, 0))
    }

    private fun player(role: String) = players.getValue(role)!!

    /**
     * Joins as guest, or reconnects when [playerId] belongs to the room. Returns the role.
     * [games] lists the games the joining app knows (null for apps that predate the list).
     */
    fun join(name: String, avatar: Avatar?, playerId: String?, games: List<String>? = null): String {
        if (status == "closed") throw ApiException("The host closed this battle.", fatal = true, reason = "closed")
        if (game.key !in (games ?: OLD_APP_GAMES)) {
            throw ApiException("This game needs the latest Game Suite. Update the app to join.", fatal = true, reason = "update_needed")
        }
        players.entries.firstOrNull { it.value?.id == playerId && playerId != null }?.let { return it.key }
        if (playerId != null) throw ApiException("You are not part of this battle.", fatal = true, reason = "not_member")
        val max = game.maxPlayers
        val seat = ROLES.subList(1, max).firstOrNull { players[it]?.left != false }
            ?: throw ApiException(if (max == 2) "This battle already has two players." else "This game is full: $max players.", fatal = true, reason = "full")
        if (status == "started") throw ApiException("A match is being played. Join when it ends.", fatal = true, reason = "busy")
        players[seat] = newPlayer(name, avatar)
        // Everyone gets ready again, so the newcomer is part of the next round.
        for (r in seated()) player(r).apply { this.ready = false; difficulty = null }
        if (status == "waiting") status = "lobby"
        version++
        return seat
    }

    /** Throws unless there is someone to play with; used by every "ready". */
    private fun checkCanReady() {
        if (status != "lobby" && status != "finished") {
            throw ApiException("You can only get ready in the lobby or after a match.")
        }
        if (present().size < 2) {
            throw ApiException(if (seated().size > 1) "Your opponent has left the battle." else "Waiting for an opponent to join.")
        }
    }

    /** Whether every player in the room is ready (and there are at least two). */
    private fun allReady() = present().let { roles -> roles.size >= 2 && roles.all { player(it).ready } }

    /** Starts a match with everyone in the room; [reset] runs on each of them. */
    private fun beginMatch(startTime: Long, reset: (Player) -> Unit = {}) {
        inMatch = present()
        round++
        status = "started"
        startAt = startTime
        winner = null
        endReason = null
        for (r in inMatch) player(r).apply {
            ready = false
            difficulty = null
            finishedAt = null
            out = false
            reset(this)
        }
    }

    fun setReady(role: String, ready: Boolean, level: Difficulty?, now: Long) {
        checkCanReady()
        val me = player(role)
        if (!ready) {
            me.ready = false
            me.difficulty = null
        } else {
            me.ready = true
            me.difficulty = level ?: throw ApiException("Choose a difficulty level.")
        }
        if (allReady() && present().map { player(it).difficulty }.toSet().size == 1) startRound(now)
        version++
    }

    private fun startRound(now: Long) {
        val level = host.difficulty!!
        val generated = generate(level.holes)
        difficulty = level
        puzzle = generated.puzzle
        solution = generated.solution
        beginMatch(now + COUNTDOWN_MS) {
            it.board = generated.puzzle
            it.progress = 0
        }
    }

    /** Ship Battle: ready with a fleet. The battle starts when both fleets are in. */
    fun setShipsReady(role: String, ready: Boolean, fleet: List<Ship>, now: Long, random: Random = Random.Default) {
        checkCanReady()
        val me = player(role)
        if (!ready) {
            me.ready = false
            me.pendingFleet = null
        } else {
            try {
                Fleet.validate(fleet)
            } catch (e: IllegalArgumentException) {
                throw ApiException(e.message ?: "Invalid fleet.")
            }
            me.pendingFleet = fleet
            me.ready = true
        }
        if (allReady()) {
            turn = if (random.nextBoolean()) HOST else GUEST
            lastShot = null
            beginMatch(now) { p ->
                p.fleet = p.pendingFleet!!
                p.pendingFleet = null
                p.shots = emptyList()
                p.progress = 0
            }
        }
        version++
    }

    /** Ship Battle: fire at the other player's sea; a hit fires again. */
    fun fire(role: String, x: Int, y: Int, now: Long) {
        if (game != GameKind.SHIPS || status != "started") throw ApiException("The battle is not running.")
        if (turn != role) throw ApiException("It's not your turn.")
        if (x !in 0 until Sea.SIZE || y !in 0 until Sea.SIZE) throw ApiException("Fire inside the sea.")
        val me = players.getValue(role)!!
        val enemy = players.getValue(other(role))!!
        if (me.shots.any { it.x == x && it.y == y }) throw ApiException("You already fired there.")
        val hit = enemy.fleet.any { Sea.cell(x, y) in it.cells }
        val shot = Shot(x, y, hit)
        me.shots = me.shots + shot
        lastShot = role to shot
        val hits = me.shots.count { it.hit }
        me.progress = hits * 100 / Sea.FLEET_CELLS
        if (hits == Sea.FLEET_CELLS) {
            me.finishedAt = now
            endMatch(role, "sunk")
        } else if (!hit) {
            turn = other(role)
        }
        version++
    }

    /** Turn games: ready (or not) for the next round; the starting player alternates. */
    fun setTurnReady(role: String, ready: Boolean, now: Long, random: Random = Random.Default) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            val first = firstTurn?.let { other(it) } ?: if (random.nextBoolean()) HOST else GUEST
            firstTurn = first
            turn = first
            turnMatch = TurnMatch.start(game, random)
            beginMatch(now)
        }
        version++
    }

    /** Turn games: plays [move] ({"col"}, {"line"}, {"card"} or {"cell"}) for [role]. */
    fun move(role: String, move: JSONObject, now: Long) {
        val match = turnMatch
        if (!game.turnBased || status != "started" || match == null) throw ApiException("The game is not running.")
        if (turn != role) throw ApiException("It's not your turn.")
        val result = try {
            match.play(role[0], move)
        } catch (e: IllegalArgumentException) {
            throw ApiException(e.message ?: "That move isn't allowed.")
        }
        if (result.over) {
            val winnerRole = when (result.winner) {
                'h' -> HOST
                'g' -> GUEST
                else -> null
            }
            winnerRole?.let { players.getValue(it)!!.finishedAt = now }
            endMatch(winnerRole, if (winnerRole != null) result.reason ?: "won" else "draw")
        } else if (!result.again) {
            turn = other(role)
        }
        version++
    }

    /** Word Race: ready (or not) for the next round; it starts with a countdown when both are. */
    fun setWordsReady(role: String, ready: Boolean, now: Long, random: Random = Random.Default) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            answer = WordList.random(random)
            beginMatch(now + COUNTDOWN_MS) {
                it.guesses = emptyList()
                it.solved = false
            }
        }
        version++
    }

    /** Word Race: one guess at the secret word. */
    fun guessWord(role: String, guess: String, now: Long) {
        val secret = answer
        val start = startAt
        if (game != GameKind.WORDS || status != "started" || secret == null || start == null) throw ApiException("The game is not running.")
        if (now < start) throw ApiException("Wait for the countdown.")
        val me = player(role)
        if (role !in playing()) throw ApiException("You are not in this round.")
        if (me.finishedAt != null) throw ApiException("You are done with this word.")
        val word = guess.lowercase()
        if (!Regex("[a-z]{5}").matches(word)) throw ApiException("Guess a five-letter word.")
        val colours = Words.score(word, secret)
        me.guesses = me.guesses + (word to colours)
        if (colours == "ggggg") {
            me.solved = true
            me.finishedAt = now
        } else if (me.guesses.size >= Words.TRIES) {
            me.finishedAt = now
        }
        decideWords()
        version++
    }

    /** Ends the round once nobody can change the result any more (as on the server). */
    private fun decideWords() {
        val roles = playing()
        // The best solver so far: fewest tries, then the fastest.
        val best = roles.filter { player(it).solved }
            .minWithOrNull(compareBy<String>({ player(it).guesses.size }, { player(it).finishedAt ?: Long.MAX_VALUE }))
        val open = roles.filter { player(it).finishedAt == null }
        if (best == null) {
            if (open.isEmpty()) endMatch(null, "draw")
            return
        }
        // Someone still guessing could only win by solving in fewer tries than the best.
        if (open.all { player(it).guesses.size + 1 >= player(best).guesses.size }) endMatch(best, "solved")
    }

    private fun wordsView(role: String, shown: String): JSONObject {
        val me = player(role)
        val opponent = others(role).firstOrNull()?.let { player(it) }
        val over = shown == "finished"
        val showWords = shown == "playing" || over
        fun rows(p: Player?, withWords: Boolean) = org.json.JSONArray(
            if (!showWords || p == null) emptyList()
            else p.guesses.map { (w, sc) -> JSONObject().put("score", sc).apply { if (withWords) put("word", w) } },
        )
        return JSONObject()
            .put("tries", Words.TRIES)
            .put("mine", rows(me, true))
            .put("theirs", rows(opponent, over))
            .put("meDone", me.finishedAt != null)
            .put("theyDone", opponent?.finishedAt != null)
            .put("answer", if (over) answer ?: JSONObject.NULL else JSONObject.NULL)
            .put(
                "others",
                org.json.JSONArray(others(role).map { r -> JSONObject().put("guesses", rows(player(r), over)).put("done", player(r).finishedAt != null) }),
            )
    }

    /** Draw & Guess: ready (or not) for a match of [DRAW_TURNS] turns; the drawer swaps every turn. */
    fun setDrawReady(role: String, ready: Boolean, now: Long, random: Random = Random.Default) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            val roles = present()
            drawTurn = 0
            drawTurns = turnsFor(roles.size)
            drawer = roles[random.nextInt(roles.size)]
            drawScore = 0
            usedWords.clear()
            lastDrawTurn = null
            beginMatch(now + COUNTDOWN_MS)
            nextDrawTurn(now + COUNTDOWN_MS, random, first = true)
        }
        version++
    }

    /** The next turn: the next player round the table draws. Ends the match after the last one. */
    private fun nextDrawTurn(now: Long, random: Random, first: Boolean = false) {
        val roles = playing()
        if (drawTurn >= drawTurns || roles.size < 2) return endMatch(null, "done")
        drawTurn++
        if (!first || drawer !in roles) {
            var i = inMatch.indexOf(drawer)
            do {
                i = (i + 1) % inMatch.size
            } while (inMatch[i] !in roles)
            drawer = inMatch[i]
        }
        val fresh = WordList.drawWords.filter { it !in usedWords }.ifEmpty { WordList.drawWords }
        drawWord = fresh.random(random)
        usedWords += drawWord
        strokes = mutableListOf()
        drawClears = 0
        drawGuesses = mutableListOf()
        turnEndsAt = now + DRAW_TURN_MS
    }

    private fun drawResult(guessed: Boolean, points: Int, guesser: String? = null) = JSONObject()
        .put("word", drawWord).put("guessed", guessed).put("points", points).put("drawer", drawer).put("guesser", guesser ?: JSONObject.NULL)

    private fun checkDrawing(role: String, now: Long) {
        val start = startAt
        if (game != GameKind.DRAW || status != "started" || start == null || now < start) throw ApiException("The game is not running.")
        if (drawer != role) throw ApiException("It's not your turn to draw.")
    }

    /** Draw & Guess: the drawer adds a stroke. Returns {index, stroke}, sent to the guesser on its own. */
    fun addStroke(role: String, stroke: JSONObject, now: Long): JSONObject {
        checkDrawing(role, now)
        if (strokes.size >= 2000) throw ApiException("The drawing is full. Clear it to keep drawing.")
        val points = stroke.optJSONArray("points")
        if (points == null || points.length() !in 1..120) throw ApiException("Bad stroke.")
        for (i in 0 until points.length()) {
            val pt = points.optJSONArray(i) ?: throw ApiException("Bad stroke.")
            if (pt.optDouble(0, -1.0) !in 0.0..1.0 || pt.optDouble(1, -1.0) !in 0.0..1.0) throw ApiException("Bad stroke.")
        }
        val clean = JSONObject().put("points", points).put("color", stroke.optInt("color").coerceIn(0, 5)).put("width", if (stroke.optInt("width") == 2) 2 else 1)
        strokes += clean
        return JSONObject().put("type", "stroke").put("turn", drawTurn).put("index", strokes.size - 1).put("stroke", clean)
    }

    fun clearDrawing(role: String, now: Long) {
        checkDrawing(role, now)
        strokes = mutableListOf()
        drawClears++
        version++
    }

    /** Draw & Guess: the guesser tries a word; a right one scores the seconds left. */
    fun drawGuess(role: String, guess: String, now: Long, random: Random = Random.Default) {
        val start = startAt
        if (game != GameKind.DRAW || status != "started" || start == null || now < start) throw ApiException("The game is not running.")
        if (drawer == role) throw ApiException("You are drawing this turn.")
        if (role !in playing()) throw ApiException("You are not in this round.")
        val text = guess.trim().take(30)
        if (text.isEmpty()) throw ApiException("Type a guess.")
        if (text.lowercase().filter { it in 'a'..'z' } == drawWord) {
            val points = maxOf(1, ((turnEndsAt - now + 999) / 1000).toInt())
            drawScore += points
            lastDrawTurn = drawResult(guessed = true, points = points, guesser = role)
            nextDrawTurn(now, random)
        } else {
            drawGuesses += role to text.lowercase()
        }
        version++
    }

    private fun drawView(role: String, shown: String): JSONObject {
        val playing = shown == "playing" || shown == "countdown"
        val mine = drawer == role
        fun nameOf(r: String?) = r?.let { players[it]?.name } ?: JSONObject.NULL
        return JSONObject()
            .put("turn", drawTurn)
            .put("turns", drawTurns)
            .put("drawer", if (playing) (if (mine) "me" else "opponent") else JSONObject.NULL)
            .put("drawerName", if (playing) nameOf(drawer) else JSONObject.NULL)
            .put("word", if (playing && mine) drawWord else JSONObject.NULL)
            .put("hint", if (playing && drawWord.isNotEmpty()) drawWord.map { if (it in 'a'..'z') '_' else it }.joinToString("") else JSONObject.NULL)
            .put("endsAt", if (playing) turnEndsAt else JSONObject.NULL)
            .put("strokes", org.json.JSONArray(if (playing) strokes else emptyList<JSONObject>()))
            .put("clears", drawClears)
            .put("guesses", org.json.JSONArray(if (playing) drawGuesses.map { (by, w) -> JSONObject().put("by", if (by == role) "me" else "opponent").put("name", nameOf(by)).put("word", w) } else emptyList()))
            .put("score", drawScore)
            .put(
                "lastTurn",
                lastDrawTurn?.let {
                    val guesser = it.optString("guesser").takeIf { g -> !it.isNull("guesser") && g.isNotEmpty() }
                    JSONObject().put("word", it.getString("word")).put("guessed", it.getBoolean("guessed"))
                        .put("points", it.getInt("points")).put("drewIt", it.getString("drawer") == role)
                        .put("drawerName", nameOf(it.getString("drawer"))).put("guesserName", nameOf(guesser)).put("guessedIt", guesser == role)
                } ?: JSONObject.NULL,
            )
    }

    /** Stores a board during a match; returns true if anything changed. */
    fun submitBoard(role: String, board: String, now: Long): Boolean {
        val player = players.getValue(role)!!
        val start = startAt
        if (status != "started" || start == null || now < start || player.finishedAt != null) return false
        if (board == player.board) return false
        val given = puzzle!!
        if (!Regex("[0-9]{81}").matches(board)) throw ApiException("Invalid board.")
        for (i in 0 until 81) {
            if (given[i] != '0' && board[i] != given[i]) throw ApiException("Invalid board: a given number was changed.")
        }
        player.board = board
        player.progress = progressOf(board, given, solution!!)
        if (board == solution) {
            player.finishedAt = now
            endMatch(role, "solved")
        }
        version++
        return true
    }

    fun sendEmoji(role: String, reaction: Reaction, now: Long) {
        if (present().size < 2) throw ApiException("Nobody to send it to yet.")
        val player = players.getValue(role)!!
        if (now - player.emojiAt < EMOJI_INTERVAL_MS) throw ApiException("Slow down a little.")
        emojiSeq++
        player.emoji = reaction to emojiSeq
        player.emojiAt = now
        version++
    }

    /**
     * Takes [role] out of the match being played (they left or lost their connection). With one
     * player left, that player wins; with more, the match goes on without them.
     */
    private fun dropOut(role: String, reason: String, now: Long) {
        if (status != "started" || role !in playing()) return
        val rest = playing() - role
        if (rest.size < 2) return endMatch(rest.firstOrNull(), reason)
        player(role).out = true
        if (game == GameKind.WORDS) decideWords()
        if (game == GameKind.DRAW && drawer == role) {
            lastDrawTurn = drawResult(guessed = false, points = 0)
            nextDrawTurn(now, Random.Default)
        }
    }

    fun leave(role: String, now: Long = System.currentTimeMillis()) {
        dropOut(role, "forfeit", now)
        if (role == HOST) {
            status = "closed"
            host.left = true
        } else if (status == "finished" || status == "started") {
            players[role]?.left = true
        } else {
            players[role] = null
            if (present().size < 2) status = "waiting"
            for (r in seated()) player(r).apply { ready = false; difficulty = null }
        }
        version++
    }

    fun setConnected(role: String, connected: Boolean, now: Long) {
        val player = players[role] ?: return
        player.connected = connected
        player.awaySince = if (connected) null else now
        version++
    }

    /** A player away for 45 s during a match loses; returns true if that happened. */
    fun checkTimers(now: Long): Boolean {
        val start = startAt ?: return false
        if (status != "started" || now < start) return false
        if (game == GameKind.DRAW && now >= turnEndsAt) {
            lastDrawTurn = drawResult(guessed = false, points = 0)
            nextDrawTurn(now, Random.Default)
            version++
            return true
        }
        val gone = playing()
            .filter { r -> player(r).let { !it.connected && now - maxOf(it.awaySince ?: now, start) >= FORFEIT_MS } }
            .minByOrNull { player(it).awaySince ?: now }
            ?: return false
        dropOut(gone, "timeout", now)
        version++
        return true
    }

    private fun endMatch(winnerRole: String?, reason: String) {
        winner = winnerRole
        endReason = reason
        status = "finished"
        for (p in players.values.filterNotNull()) {
            p.ready = false
            p.difficulty = null
        }
    }

    /** The room as [role] sees it: the same JSON the servers send. */
    fun view(role: String, now: Long): JSONObject {
        val start = startAt
        val shown = if (status == "started") (if (start != null && now < start) "countdown" else "playing") else status
        val showPuzzle = shown == "countdown" || shown == "playing" || (shown == "finished" && round > 0)
        val otherRoles = others(role)
        val opponent = otherRoles.firstOrNull()?.let { player(it) }
        return JSONObject()
            .put("ok", true)
            .put("type", "state")
            .put("serverTime", now)
            .put("code", code)
            .put("version", version)
            .put("role", role)
            .put("game", game.key)
            .put("status", shown)
            .put("difficulty", difficulty?.key ?: JSONObject.NULL)
            .put("round", round)
            .put("startAt", start ?: JSONObject.NULL)
            .put("puzzle", if (showPuzzle) puzzle else JSONObject.NULL)
            .put("solution", if (shown == "finished") solution else JSONObject.NULL)
            .put("winner", winner?.let { if (it == role) "me" else "opponent" } ?: JSONObject.NULL)
            .put("winnerName", winner?.let { players[it]?.name } ?: JSONObject.NULL)
            .put("endReason", endReason ?: JSONObject.NULL)
            .put("maxPlayers", game.maxPlayers)
            // In Ship Battle both players' progress (share of the enemy fleet hit) is public.
            .put("me", playerView(players.getValue(role)!!, shown == "finished" || game == GameKind.SHIPS, now))
            .put("opponent", opponent?.let { playerView(it, true, now) } ?: JSONObject.NULL)
            // Everyone else in seat order; "opponent" is the first of them, for two-player games.
            .put("others", org.json.JSONArray(otherRoles.map { playerView(player(it), true, now) }))
            .put("ships", if (game == GameKind.SHIPS) shipsView(role, shown) else JSONObject.NULL)
            .put("turns", if (game.turnBased) turnsView(role, shown) else JSONObject.NULL)
            .put("words", if (game == GameKind.WORDS) wordsView(role, shown) else JSONObject.NULL)
            .put("draw", if (game == GameKind.DRAW) drawView(role, shown) else JSONObject.NULL)
    }

    private fun turnsView(role: String, shown: String): JSONObject {
        val json = turnMatch?.view(role[0]) ?: JSONObject()
        return json.put("turn", if (shown == "playing") (if (turn == role) "me" else "opponent") else JSONObject.NULL)
    }

    private fun shipsView(role: String, shown: String): JSONObject {
        val me = players.getValue(role)!!
        val opponent = players[other(role)]
        return JSONObject()
            .put("turn", if (shown == "playing") (if (turn == role) "me" else "opponent") else JSONObject.NULL)
            .put("myFleet", (me.fleet.ifEmpty { me.pendingFleet.orEmpty() }).toJson())
            .put("myShots", me.shots.toShotJson())
            .put("theirShots", opponent?.shots.orEmpty().toShotJson())
            .put("sunkEnemy", opponent?.fleet.orEmpty().filter { Fleet.isSunk(it, me.shots) }.toJson())
            .put("enemyFleet", if (shown == "finished" && opponent != null) opponent.fleet.toJson() else JSONObject.NULL)
            .put(
                "lastShot",
                lastShot?.let { (by, s) ->
                    JSONObject().put("by", if (by == role) "me" else "opponent").put("x", s.x).put("y", s.y).put("hit", s.hit)
                } ?: JSONObject.NULL,
            )
    }

    private fun playerView(p: Player, includeProgress: Boolean, now: Long): JSONObject = JSONObject()
        .put("name", p.name)
        .put("avatar", p.avatar?.key ?: JSONObject.NULL)
        .put("emoji", p.emoji?.let { JSONObject().put("key", it.first.key).put("seq", it.second) } ?: JSONObject.NULL)
        .put("ready", p.ready)
        .put("difficulty", p.difficulty?.key ?: JSONObject.NULL)
        .put("progress", if (includeProgress) p.progress else JSONObject.NULL)
        .put("finishMs", p.finishedAt?.let { it - (startAt ?: it) } ?: JSONObject.NULL)
        .put("left", p.left)
        .put("out", p.out)
        .put("idleMs", p.awaySince?.let { maxOf(0, now - it) } ?: 0)
        .put("online", p.connected)

    private val host get() = players.getValue(HOST)!!

    private fun List<Shot>.toShotJson() =
        org.json.JSONArray(map { JSONObject().put("x", it.x).put("y", it.y).put("hit", it.hit) })

    private fun newPlayer(name: String, avatar: Avatar?) =
        Player(UUID.randomUUID().toString(), name.trim().take(16).ifBlank { "Player" }, avatar)

    companion object {
        const val HOST = "host"
        const val GUEST = "guest"
        const val P3 = "p3"
        const val P4 = "p4"
        /** Seats in a room, in order. Two-player games use the first two. */
        val ROLES = listOf(HOST, GUEST, P3, P4)
        const val COUNTDOWN_MS = 5000L
        const val FORFEIT_MS = 45_000L
        const val EMOJI_INTERVAL_MS = 1500L
        const val DRAW_TURNS = 4
        const val DRAW_TURN_MS = 80_000L
        // What apps from before the version check could play; they don't say which games they know.
        val OLD_APP_GAMES = listOf("sudoku", "ships")

        /** The other player of a two-player game. */
        fun other(role: String) = if (role == HOST) GUEST else HOST

        /** Draw & Guess turns in a match: everyone draws twice (at least four turns). */
        fun turnsFor(players: Int) = maxOf(DRAW_TURNS, players * 2)

        fun progressOf(board: String, puzzle: String, solution: String): Int {
            var empty = 0
            var correct = 0
            for (i in 0 until 81) {
                if (puzzle[i] != '0') continue
                empty++
                if (board[i] == solution[i]) correct++
            }
            return if (empty == 0) 100 else correct * 100 / empty
        }
    }
}
