package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.Reaction
import com.pippop.data.toJson
import com.pippop.game.golf.GolfLevel
import com.pippop.game.Generator
import com.pippop.game.dominoes.DominoMatch
import com.pippop.game.dominoes.DominoOptions
import com.pippop.game.bingo.Bingo
import com.pippop.game.code.CodeBreaker
import com.pippop.game.slide.Slide
import com.pippop.game.slide.SlideLevel
import com.pippop.game.grid.LetterGrid
import com.pippop.game.hockey.Hockey
import org.json.JSONArray
import com.pippop.game.numbers.NumberCross
import com.pippop.game.numbers.NumberLevel
import com.pippop.game.numbers.NumberPuzzle
import com.pippop.game.turns.TurnMatch
import com.pippop.game.words.WordLevel
import com.pippop.game.words.WordList
import com.pippop.game.words.Words
import com.pippop.game.ships.Fleet
import com.pippop.game.ships.Sea
import com.pippop.game.ships.Ship
import com.pippop.game.ships.Shot
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
        // Number Cross: one entry per blank
        var entries: List<String> = emptyList()
        // Letter Grid: the words found this round
        var found: List<String> = emptyList()
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
        // Code Breaker: the code this player set
        var code: String? = null
        // Slide Race: every slide so far
        var slideMoves: List<Int> = emptyList()
        // Bingo: this player's card
        var card: List<Int> = emptyList()
        var solved = false
        /** Left or lost their connection during the match being played, which goes on without them. */
        var out = false
        /** The game this player's screen is on, or null in the menu. */
        var at: GameKind? = null
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

    /** Until when a reaction like Kylian Dictador owns the game: no other reaction meanwhile. */
    private var takenOverUntil = 0L
    private var turn: String? = null
    private var lastShot: Pair<String, Shot>? = null
    // Turn games
    private var firstTurn: String? = null
    private var turnMatch: TurnMatch? = null
    /** The host's Dominoes rules for the next match. */
    var options = DominoOptions()
        private set
    /** Number Cross: the host's level, the puzzle being played, and the one sent with the host's Ready. */
    var numberLevel = NumberLevel.EASY
        private set
    /** Tic-Tac-Toe: the host chose normal 3 × 3 instead of Ultimate. */
    var tttClassic = false
        private set
    /** Word Race: the host's level (the length of the word). */
    var wordLevel = WordLevel.MEDIUM
        private set
    private var numbers: NumberPuzzle? = null
    private var nextPuzzle: NumberPuzzle? = null
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

    /** Players still in the room, wherever they are in it. */
    private fun inRoom() = seated().filter { !players.getValue(it)!!.left }

    /** Players in the room who are at the open game (not in the menu or elsewhere). */
    private fun present() = inRoom().filter { player(it).at == game }

    /** Who sets the open game's options: whoever opened it (the host at first). */
    private var openedBy = HOST

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
        if (status == "closed") throw ApiException("The host closed this game.", fatal = true, reason = "closed")
        if (game.key !in (games ?: OLD_APP_GAMES)) {
            throw ApiException("This game needs the latest Pip & Pop. Update the app to join.", fatal = true, reason = "update_needed")
        }
        players.entries.firstOrNull { it.value?.id == playerId && playerId != null }?.let { return it.key }
        if (playerId != null) throw ApiException("You're not in this game.", fatal = true, reason = "not_member")
        val max = game.maxPlayers
        val seat = ROLES.subList(1, max).firstOrNull { players[it]?.left != false }
            ?: throw ApiException(if (max == 2) "This game already has two players." else "This game is full: $max players.", fatal = true, reason = "full")
        if (status == "started") throw ApiException("A game is being played. Join when it ends.", fatal = true, reason = "busy")
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
            throw ApiException("You can only get ready in the lobby or after a game.")
        }
        if (present().size < 2) {
            if (inRoom().size > 1) throw ApiException("Waiting for the others to come to this game.")
            throw ApiException(if (seated().size > 1) "The other player has left." else "Waiting for someone to join.")
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
        if (game != GameKind.SHIPS || status != "started") throw ApiException("The game isn't running.")
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

    /** Turn games: ready (or not) for the next round; the starting player moves one seat on. */
    fun setTurnReady(role: String, ready: Boolean, now: Long, random: Random = Random.Default) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            val roles = present()
            val first = firstTurn?.let { nextOf(it, roles) } ?: if (roles.size == 2) roles[if (random.nextBoolean()) 0 else 1] else roles[random.nextInt(roles.size)]
            val match = TurnMatch.start(game, random, roles.map { MARKS.getValue(it) }, options, tttClassic, golfLevel)
            // Dominoes picks its own first player (the highest double).
            val starter = match.firstMark?.let { roleOfMark(it) } ?: first
            firstTurn = starter
            turn = starter
            turnMatch = match
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
            match.play(MARKS.getValue(role), move)
        } catch (e: IllegalArgumentException) {
            throw ApiException(e.message ?: "That move isn't allowed.")
        }
        if (result.over) {
            val winnerRole = MARKS.entries.firstOrNull { it.value == result.winner }?.key
            winnerRole?.let { players.getValue(it)!!.finishedAt = now }
            endMatch(winnerRole, if (winnerRole != null) result.reason ?: "won" else "draw")
        } else if (result.turn != null) {
            turn = roleOfMark(result.turn)
        } else if (!result.again) {
            turn = nextOf(role, playing())
        }
        version++
    }

    private fun roleOfMark(mark: Char) = MARKS.entries.first { it.value == mark }.key

    /** The host's choices for the next match (Dominoes: teams, drawing, points target). Everyone gets ready again. */
    fun setOptions(role: String, options: JSONObject) {
        if (role != openedBy) {
            throw ApiException(if (openedBy == HOST) "Only the host can change the rules." else "Only ${player(openedBy).name} can change the rules: they opened this game.")
        }
        if (status != "waiting" && status != "lobby" && status != "finished") throw ApiException("The rules can only change between matches.")
        when (game) {
            GameKind.DOMINOES -> this.options = DominoOptions.from(options)
            GameKind.NUMBERS -> numberLevel = NumberLevel.from(options.optString("level"))
            GameKind.WORDS -> wordLevel = WordLevel.from(options.optString("level"))
            GameKind.SLIDE -> slideLevel = SlideLevel.from(options.optString("level"))
            GameKind.TTT -> tttClassic = options.optString("mode") == "classic"
            GameKind.GOLF -> golfLevel = GolfLevel.from(options.optString("level"))
            else -> throw ApiException("This game has no options.")
        }
        for (r in seated()) player(r).ready = false
        version++
    }

    /** Whether [role] shares the win: they won, or (Dominoes teams) their partner did. */
    private fun wonWith(role: String): Boolean {
        val w = winner ?: return false
        if (w == role) return true
        val match = turnMatch as? DominoMatch ?: return false
        return game == GameKind.DOMINOES && match.teams && match.sameSide(MARKS.getValue(w), MARKS.getValue(role))
    }

    /** The winner's name, or both partners' names for a Dominoes team. */
    private fun winnerNames() = seated().filter { wonWith(it) }.joinToString(" & ") { player(it).name }.ifEmpty { null }

    /** The seat after [role], round the table, that is in [allowed]. */
    private fun nextOf(role: String, allowed: List<String>): String {
        var i = ROLES.indexOf(role)
        repeat(ROLES.size) {
            i = (i + 1) % ROLES.size
            if (ROLES[i] in allowed) return ROLES[i]
        }
        return allowed.first()
    }

    /** Word Race: ready (or not) for the next round; it starts with a countdown when both are. */
    fun setWordsReady(role: String, ready: Boolean, now: Long, random: Random = Random.Default) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            answer = WordList.random(wordLevel, random)
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
        if (word.length != secret.length || !Regex("[a-z]+").matches(word)) throw ApiException("Guess a ${secret.length}-letter word.")
        if (!WordList.isWord(word)) throw ApiException("\"${word.uppercase()}\" isn't in the word list.")
        val colours = Words.score(word, secret)
        me.guesses = me.guesses + (word to colours)
        if (colours == "g".repeat(secret.length)) {
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
            .put("length", answer?.length ?: wordLevel.lengths.first())
            .put("level", wordLevel.key)
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
    // Letter Grid: the letters being played, the next grid from the host, when time is up, and the results.
    private var letters: String? = null
    private var nextGrid: String? = null
    private var gridEndsAt = 0L
    private var gridResults: JSONArray? = null

    /** Letter Grid: ready (or not); the host's Ready carries the grid. The round lasts [LetterGrid.ROUND_MS]. */
    fun setGridReady(role: String, ready: Boolean, grid: String?, now: Long) {
        checkCanReady()
        player(role).ready = ready
        if (role == HOST) {
            nextGrid = if (!ready) null else grid?.lowercase()?.takeIf { Regex("[a-z]{16}").matches(it) } ?: run {
                player(role).ready = false
                throw ApiException("The grid is not valid.")
            }
        }
        val next = nextGrid
        if (allReady() && next != null) {
            nextGrid = null
            letters = next
            gridResults = null
            beginMatch(now + COUNTDOWN_MS) { it.found = emptyList() }
            gridEndsAt = now + COUNTDOWN_MS + LetterGrid.ROUND_MS
        }
        version++
    }

    /** Letter Grid: a word found by [role]. */
    fun findWord(role: String, word: String, now: Long) {
        val start = startAt
        val l = letters
        if (game != GameKind.GRID || status != "started" || start == null || l == null) throw ApiException("The game is not running.")
        if (now < start) throw ApiException("Wait for the countdown.")
        if (now >= gridEndsAt) throw ApiException("Time's up.")
        if (role !in playing()) throw ApiException("You are not in this round.")
        val w = word.lowercase()
        LetterGrid.problem(l, w)?.let { throw ApiException(it) }
        val me = player(role)
        if (w in me.found) throw ApiException("You already found that one.")
        me.found = me.found + w
        version++
    }

    /** Letter Grid: time is up; unique words score double; the top score wins (a tie for it is a draw). */
    private fun finishGrid() {
        val roles = inMatch.filter { players[it] != null }
        val seen = roles.flatMap { player(it).found }.groupingBy { it }.eachCount()
        val scores = roles.associateWith { r -> player(r).found.sumOf { LetterGrid.points(it) * if (seen[it] == 1) 2 else 1 } }
        gridResults = JSONArray(roles.map { r ->
            JSONObject().put("role", r).put("score", scores.getValue(r)).put(
                "words",
                JSONArray(player(r).found.map { w ->
                    val unique = seen[w] == 1
                    JSONObject().put("word", w).put("unique", unique).put("points", LetterGrid.points(w) * if (unique) 2 else 1)
                }),
            )
        })
        val ranked = roles.sortedByDescending { scores.getValue(it) }
        val tie = ranked.size > 1 && scores.getValue(ranked[0]) == scores.getValue(ranked[1])
        endMatch(if (tie) null else ranked.firstOrNull(), if (tie) "draw" else "score")
    }

    private fun gridView(role: String, shown: String): JSONObject {
        val show = shown == "playing" || shown == "finished"
        val results = gridResults
        return JSONObject()
            .put("letters", if (show) letters ?: JSONObject.NULL else JSONObject.NULL)
            .put("endsAt", if (gridEndsAt > 0) gridEndsAt else JSONObject.NULL)
            .put("mine", JSONArray(player(role).found))
            .put("counts", JSONArray(others(role).map { player(it).found.size }))
            .put(
                "results",
                if (results == null) JSONObject.NULL else JSONArray(
                    (listOf(role) + others(role)).mapNotNull { r ->
                        (0 until results.length()).map { results.getJSONObject(it) }.firstOrNull { it.getString("role") == r }?.let {
                            JSONObject().put("who", if (r == role) "me" else "other").put("name", player(r).name)
                                .put("score", it.getInt("score")).put("words", it.getJSONArray("words"))
                        }
                    },
                ),
            )
    }

    // Slide Race: the host's level, the start being played and the next one from the host.
    var slideLevel = SlideLevel.EASY

    /** Mini Golf: how many islands every hole of the next game has. */
    var golfLevel = GolfLevel.EASY
        private set
    private var slideStart: List<Int>? = null
    private var nextSlide: List<Int>? = null

    /** Slide Race: ready (or not); the host's Ready carries the shuffled start. */
    fun setSlideReady(role: String, ready: Boolean, tiles: List<Int>?, now: Long) {
        checkCanReady()
        player(role).ready = ready
        val size = slideLevel.size
        if (role == HOST) {
            nextSlide = if (!ready) null else tiles?.takeIf { t ->
                t.size == size * size && t.sorted() == (0 until size * size).toList() && !Slide.isSolved(t, size)
            } ?: run {
                player(role).ready = false
                throw ApiException("The puzzle is not valid.")
            }
        }
        val next = nextSlide
        if (allReady() && next != null) {
            nextSlide = null
            slideStart = next
            beginMatch(now + COUNTDOWN_MS) {
                it.slideMoves = emptyList()
                it.progress = Slide.progress(next, size)
            }
        }
        version++
    }

    /** Slide Race: [role]'s whole list of moves so far, comma separated; returns true if it changed. */
    fun slideMoves(role: String, text: String, now: Long): Boolean {
        val me = player(role)
        val start = slideStart
        val begin = startAt
        if (game != GameKind.SLIDE || status != "started" || start == null || begin == null || now < begin || me.finishedAt != null) return false
        val size = kotlin.math.sqrt(start.size.toDouble()).toInt()
        val moves = text.split(",").filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: throw ApiException("That tile can't slide.") }
        if (moves.size == me.slideMoves.size) return false
        val tiles = try {
            Slide.replay(start, size, moves)
        } catch (e: IllegalArgumentException) {
            throw ApiException(e.message ?: "That tile can't slide.")
        }
        me.slideMoves = moves
        me.progress = Slide.progress(tiles, size)
        if (Slide.isSolved(tiles, size)) {
            me.finishedAt = now
            endMatch(role, "solved")
        }
        version++
        return true
    }

    private fun slideView(role: String, shown: String): JSONObject {
        val start = slideStart
        val show = shown == "playing" || shown == "finished"
        return JSONObject()
            .put("level", slideLevel.key)
            .put("size", start?.let { kotlin.math.sqrt(it.size.toDouble()).toInt() } ?: slideLevel.size)
            .put("start", if (show && start != null) JSONArray(start) else JSONObject.NULL)
            .put("moves", JSONArray(player(role).slideMoves))
    }

    // Bingo: the call order and, once won, the winning line.
    private var calls: List<Int> = emptyList()
    private var bingoLine: List<Int>? = null

    /** Bingo: ready (or not); the host's phone deals the cards and shuffles the calls. */
    fun setBingoReady(role: String, ready: Boolean, now: Long, random: Random = Random.Default) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            calls = Bingo.shuffledCalls(random)
            bingoLine = null
            beginMatch(now + COUNTDOWN_MS) { it.card = Bingo.dealCard(random) }
        }
        version++
    }

    /** Bingo: [role] claims a full line of numbers called so far. */
    fun claimBingo(role: String, now: Long) {
        val start = startAt
        if (game != GameKind.BINGO || status != "started" || start == null) throw ApiException("The game is not running.")
        if (now < start) throw ApiException("Wait for the countdown.")
        if (role !in playing()) throw ApiException("You are not in this round.")
        val me = player(role)
        val line = Bingo.fullLine(me.card, calls.take(Bingo.calledCount(now - start)).toSet()) ?: throw ApiException("No full line yet.")
        bingoLine = line
        me.finishedAt = now
        endMatch(role, "bingo")
        version++
    }

    private fun bingoView(role: String, shown: String): JSONObject {
        val show = shown == "playing" || shown == "finished"
        val won = winner?.let { players[it] }
        return JSONObject()
            .put("card", if (show) JSONArray(player(role).card) else JSONObject.NULL)
            .put("calls", if (show) JSONArray(calls) else JSONObject.NULL)
            .put("callMs", Bingo.CALL_MS)
            .put("endsAt", startAt?.let { it + Bingo.ROUND_MS } ?: JSONObject.NULL)
            .put("winCard", if (shown == "finished" && won != null) JSONArray(won.card) else JSONObject.NULL)
            .put("line", if (shown == "finished") bingoLine?.let { JSONArray(it) } ?: JSONObject.NULL else JSONObject.NULL)
    }

    // Code Breaker: "set" while the codes are being chosen, then "guess".
    private var codePhase = "set"

    /** Code Breaker: ready (or not); the match starts with both setting their codes. */
    fun setCodeReady(role: String, ready: Boolean, now: Long) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            codePhase = "set"
            beginMatch(now) {
                it.code = null
                it.guesses = emptyList()
                it.solved = false
            }
        }
        version++
    }

    /** Code Breaker: [role] sets their code, or (once both are set) guesses the other's. */
    fun codeMove(role: String, text: String, now: Long) {
        if (game != GameKind.CODE || status != "started") throw ApiException("The game is not running.")
        val me = player(role)
        if (role !in playing()) throw ApiException("You are not in this round.")
        val c = text.lowercase()
        if (!CodeBreaker.isCode(c)) throw ApiException("A code is ${CodeBreaker.PEGS} pegs, each one of the six colours.")
        if (codePhase == "set") {
            if (me.code != null) throw ApiException("Your code is set.")
            me.code = c
            if (playing().all { player(it).code != null }) {
                codePhase = "guess"
                startAt = now + COUNTDOWN_MS
            }
            version++
            return
        }
        if (now < (startAt ?: 0)) throw ApiException("Wait for the countdown.")
        if (me.finishedAt != null) throw ApiException("You are done with this code.")
        val target = others(role).firstOrNull { it in playing() }?.let { player(it).code } ?: throw ApiException("The game is not running.")
        val (black, white) = CodeBreaker.score(c, target)
        me.guesses = me.guesses + (c to "$black$white")
        if (black == CodeBreaker.PEGS) {
            me.solved = true
            me.finishedAt = now
        } else if (me.guesses.size >= CodeBreaker.TRIES) {
            me.finishedAt = now
        }
        decideWords()
        version++
    }

    private fun codeView(role: String, shown: String): JSONObject {
        val me = player(role)
        val other = others(role).firstOrNull()?.let { player(it) }
        val over = shown == "finished"
        fun rows(p: Player?, withCodes: Boolean) = JSONArray(
            p?.guesses.orEmpty().map { (g, s) ->
                JSONObject().put("black", s[0].digitToInt()).put("white", s[1].digitToInt()).apply { if (withCodes) put("word", g) }
            },
        )
        return JSONObject()
            .put("phase", codePhase)
            .put("tries", CodeBreaker.TRIES)
            .put("myCode", me.code ?: JSONObject.NULL)
            .put("theySet", other?.code != null)
            .put("mine", rows(me, true))
            .put("theirs", rows(other, over))
            .put("meDone", me.finishedAt != null)
            .put("theyDone", other?.finishedAt != null)
            .put("theirCode", if (over) other?.code ?: JSONObject.NULL else JSONObject.NULL)
    }

    /** Air Hockey: the score [host, guest], kept by the host's phone. */
    private var hockeyGoals = listOf(0, 0)

    /** Air Hockey: ready (or not); the match starts when both are ready. */
    fun setHockeyReady(role: String, ready: Boolean, now: Long) {
        checkCanReady()
        player(role).ready = ready
        if (allReady()) {
            hockeyGoals = listOf(0, 0)
            beginMatch(now + COUNTDOWN_MS)
        }
        version++
    }

    /** Air Hockey: the host reports the score after a goal; first to [Hockey.GOALS] wins. */
    fun hockeyScore(role: String, score: List<Int>, now: Long) {
        val start = startAt
        if (game != GameKind.HOCKEY || status != "started" || start == null) throw ApiException("The game is not running.")
        if (role != HOST) throw ApiException("Only the host keeps the score.")
        if (now < start) throw ApiException("Wait for the countdown.")
        if (score.size != 2 || score.indices.any { score[it] < hockeyGoals[it] || score[it] > Hockey.GOALS }) throw ApiException("Invalid score.")
        hockeyGoals = score
        val winnerRole = when {
            score[0] >= Hockey.GOALS -> HOST
            score[1] >= Hockey.GOALS -> GUEST
            else -> null
        }
        if (winnerRole != null) {
            player(winnerRole).finishedAt = now
            endMatch(winnerRole, "goals")
        }
        version++
    }

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

    /**
     * Number Cross: ready (or not). The host's Ready carries the next puzzle, built on their phone;
     * the match starts when everyone is ready.
     */
    fun setNumbersReady(role: String, ready: Boolean, puzzle: NumberPuzzle?, now: Long) {
        checkCanReady()
        player(role).ready = ready
        if (role == HOST) {
            nextPuzzle = if (!ready) null else try {
                NumberCross.check(puzzle ?: throw IllegalArgumentException("The puzzle is missing."))
            } catch (e: IllegalArgumentException) {
                player(role).ready = false
                throw ApiException(e.message ?: "The puzzle is not valid.")
            }
        }
        val next = nextPuzzle
        if (allReady() && next != null) {
            nextPuzzle = null
            numbers = next
            beginMatch(now + COUNTDOWN_MS) { it.entries = List(next.blanks.size) { "" }; it.progress = 0 }
        }
        version++
    }

    /** Number Cross: stores a player's entries (one per blank); returns true if anything changed. */
    fun submitNumbers(role: String, entries: List<String>, now: Long): Boolean {
        val player = player(role)
        val p = numbers
        val start = startAt
        if (status != "started" || p == null || start == null || now < start || player.finishedAt != null) return false
        if (entries.size != p.blanks.size || entries.any { it != "" && !NumberCross.isNumber(it) }) throw ApiException("Invalid board.")
        if (entries == player.entries) return false
        player.entries = entries
        player.progress = NumberCross.progressOf(p, entries)
        if (NumberCross.isSolved(p, entries)) {
            player.finishedAt = now
            endMatch(role, "solved")
        }
        version++
        return true
    }

    private fun numbersView(role: String, shown: String): JSONObject {
        val p = numbers
        val show = p != null && (shown == "playing" || shown == "finished")
        val blank = p?.blanks?.toSet().orEmpty()
        return JSONObject()
            .put("level", numberLevel.key)
            .put(
                "puzzle",
                if (p == null || !show) JSONObject.NULL else JSONObject()
                    .put("w", p.w).put("h", p.h)
                    .put("cells", org.json.JSONArray(p.grid.mapIndexed { i, t -> if (i in blank) "?" else t }))
                    .put("blanks", org.json.JSONArray(p.blanks))
                    .put("bank", org.json.JSONArray(p.bank)),
            )
            .put("entries", if (p != null && shown != "lobby" && shown != "waiting") org.json.JSONArray(player(role).entries) else JSONObject.NULL)
            .put("solution", if (shown == "finished" && p != null) org.json.JSONArray(p.grid) else JSONObject.NULL)
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
        if (inRoom().size < 2) throw ApiException("Nobody to send it to yet.")
        val player = players.getValue(role)!!
        if (now < takenOverUntil) throw ApiException("Kylian Dictador has the floor.")
        if (now - player.emojiAt < EMOJI_INTERVAL_MS) throw ApiException("Slow down a little.")
        if (reaction.takesOverMs > 0) takenOverUntil = now + reaction.takesOverMs
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
        // A dominoes hand (or team) can't play on short: the match stops there.
        if (game == GameKind.DOMINOES || game == GameKind.CLASH || game == GameKind.LUDO || game == GameKind.BALLOON || game == GameKind.GOLF || game == GameKind.BLOCKS) return endMatch(null, "left")
        player(role).out = true
        if (game == GameKind.WORDS) decideWords()
        if (game.turnBased && turn == role) turn = nextOf(role, playing())
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
            if (inRoom().size < 2) status = "waiting"
            for (r in seated()) player(r).apply { ready = false; difficulty = null }
        }
        version++
    }

    /**
     * Opens [next] for [role] in the same room, so one connection plays game after game.
     * Opening the game that is open joins it and returns this room. Opening another one,
     * between matches, returns a fresh room for it with the same code, seats and connections,
     * with this player in its lobby; everyone else stays where they were (the menu, or the
     * last game's screen), so their phones can show where this player went. Whoever opens a
     * game sets its options. As on the server (room.js openGame).
     */
    fun openGame(role: String, next: GameKind): LocalRoom {
        val me = player(role)
        if (status == "started") {
            // Back to the match this player is in, from the menu.
            if (next == game && role in playing()) {
                me.at = next
                version++
                return this
            }
            throw ApiException("A game is being played. Join when it ends.", reason = "busy")
        }
        // Two-player games use the first two seats (their rules know only host and guest).
        if (next.maxPlayers == 2 && role != HOST && role != GUEST) {
            throw ApiException("This game is for two: the first two in the room play it.", reason = "full")
        }
        if (next == game) {
            if (role !in present() && present().size >= game.maxPlayers) {
                throw ApiException("This game is full: ${game.maxPlayers} players.", reason = "full")
            }
            me.at = next
            for (r in present()) player(r).apply { ready = false; difficulty = null }
            version++
            return this
        }
        // The round number carries on, so phones never mistake the new game's first round for one they have seen.
        val fresh = LocalRoom(code, host.name, host.avatar, next, generate)
        for (r in ROLES) {
            val p = players[r]
            fresh.players[r] = if (p == null || p.left) null else Player(p.id, p.name, p.avatar).also {
                it.connected = p.connected
                it.awaySince = p.awaySince
                it.emoji = p.emoji
                it.emojiAt = p.emojiAt
                it.at = if (r == role) next else p.at
            }
        }
        fresh.version = version + 1
        fresh.emojiSeq = emojiSeq
        fresh.round = round
        fresh.startPushedRound = startPushedRound
        fresh.openedBy = role
        fresh.status = if (fresh.inRoom().size >= 2) "lobby" else "waiting"
        return fresh
    }

    /** [role] goes back to the menu and stays in the room. Leaving a match being played gives it up. */
    fun toMenu(role: String, now: Long) {
        dropOut(role, "forfeit", now)
        player(role).apply { at = null; ready = false; difficulty = null }
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
        if (roomTimers(now)) return true
        if (needsStartPush() && now >= (startAt ?: return false)) {
            startPushedRound = round
            version++
            return true
        }
        return false
    }

    /**
     * True while a round of a game whose view hides the round (letters, tiles, card, puzzle)
     * until the countdown ends has started but not been pushed at its start time. Without that
     * push the guests see the countdown end with nothing on the board until they next send
     * something.
     */
    private fun needsStartPush() = status == "started" && game in REVEALED_AT_START && startPushedRound != round

    private var startPushedRound = -1

    private fun roomTimers(now: Long): Boolean {
        val start = startAt ?: return false
        if (status != "started" || now < start) return false
        if (game == GameKind.BINGO && now >= start + Bingo.ROUND_MS) {
            endMatch(null, "draw")
            version++
            return true
        }
        if (game == GameKind.GRID && now >= gridEndsAt) {
            finishGrid()
            version++
            return true
        }
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
            .put("winner", winner?.let { if (wonWith(role)) "me" else "opponent" } ?: JSONObject.NULL)
            .put("winnerName", winner?.let { winnerNames() } ?: JSONObject.NULL)
            .put(
                "options",
                when (game) {
                    GameKind.DOMINOES -> options.toJson()
                    GameKind.NUMBERS -> JSONObject().put("level", numberLevel.key)
                    GameKind.WORDS -> JSONObject().put("level", wordLevel.key)
                    GameKind.SLIDE -> JSONObject().put("level", slideLevel.key)
                    GameKind.TTT -> JSONObject().put("mode", if (tttClassic) "classic" else "ultimate")
                    GameKind.GOLF -> JSONObject().put("level", golfLevel.key)
                    else -> JSONObject.NULL
                },
            )
            .put("endReason", endReason ?: JSONObject.NULL)
            .put("maxPlayers", game.maxPlayers)
            // Whether this player sets the open game's options: whoever opened it (the host at first).
            .put("setsOptions", role == openedBy)
            // In Ship Battle both players' progress (share of the enemy fleet hit) is public.
            .put("me", playerView(players.getValue(role)!!, shown == "finished" || game == GameKind.SHIPS, now))
            .put("opponent", opponent?.let { playerView(it, true, now) } ?: JSONObject.NULL)
            // Everyone else in seat order; "opponent" is the first of them, for two-player games.
            .put("others", org.json.JSONArray(otherRoles.map { playerView(player(it), true, now) }))
            .put("ships", if (game == GameKind.SHIPS) shipsView(role, shown) else JSONObject.NULL)
            .put("turns", if (game.turnBased) turnsView(role, shown) else JSONObject.NULL)
            .put("words", if (game == GameKind.WORDS) wordsView(role, shown) else JSONObject.NULL)
            .put("draw", if (game == GameKind.DRAW) drawView(role, shown) else JSONObject.NULL)
            .put("numbers", if (game == GameKind.NUMBERS) numbersView(role, shown) else JSONObject.NULL)
            .put("grid", if (game == GameKind.GRID) gridView(role, shown) else JSONObject.NULL)
            .put("codeGame", if (game == GameKind.CODE) codeView(role, shown) else JSONObject.NULL)
            .put("slide", if (game == GameKind.SLIDE) slideView(role, shown) else JSONObject.NULL)
            .put("bingo", if (game == GameKind.BINGO) bingoView(role, shown) else JSONObject.NULL)
            .put(
                "hockey",
                if (game != GameKind.HOCKEY) JSONObject.NULL else {
                    val mine = if (role == HOST) 0 else 1
                    JSONObject().put("mine", hockeyGoals[mine]).put("theirs", hockeyGoals[1 - mine]).put("target", Hockey.GOALS)
                },
            )
    }

    private fun turnsView(role: String, shown: String): JSONObject {
        // Everyone else's mark shows as 'o', 'p', 'q', in the order of the view's "others".
        val seats = others(role).mapIndexed { i, r -> MARKS.getValue(r) to "opq"[i] }.toMap()
        val json = turnMatch?.view(MARKS.getValue(role), seats) ?: JSONObject()
        val playing = shown == "playing"
        return json.put("turn", if (playing) (if (turn == role) "me" else "opponent") else JSONObject.NULL)
            .put("turnName", if (playing) turn?.let { players[it]?.name } ?: JSONObject.NULL else JSONObject.NULL)
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
        // The game this player's screen is on, or null in the menu.
        .put("at", p.at?.key ?: JSONObject.NULL)

    private val host get() = players.getValue(HOST)!!

    private fun List<Shot>.toShotJson() =
        org.json.JSONArray(map { JSONObject().put("x", it.x).put("y", it.y).put("hit", it.hit) })

    private fun newPlayer(name: String, avatar: Avatar?) =
        Player(UUID.randomUUID().toString(), name.trim().take(16).ifBlank { "Player" }, avatar).also { it.at = game }

    companion object {
        const val HOST = "host"
        const val GUEST = "guest"
        const val P3 = "p3"
        const val P4 = "p4"
        /** Seats in a room, in order. Two-player games use the first two. */
        val ROLES = listOf(HOST, GUEST, P3, P4)
        /** Each seat's one-letter mark on a turn game's board (as on the server). */
        val MARKS = mapOf(HOST to 'h', GUEST to 'g', P3 to '3', P4 to '4')
        const val COUNTDOWN_MS = 5000L
        const val FORFEIT_MS = 45_000L
        const val EMOJI_INTERVAL_MS = 1500L
        const val DRAW_TURNS = 4
        const val DRAW_TURN_MS = 80_000L
        // What apps from before the version check could play; they don't say which games they know.
        val OLD_APP_GAMES = listOf("sudoku", "ships")

        /** Games whose view hides the round until the countdown ends; see needsStartPush. */
        private val REVEALED_AT_START = setOf(GameKind.WORDS, GameKind.GRID, GameKind.SLIDE, GameKind.BINGO, GameKind.NUMBERS)

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
