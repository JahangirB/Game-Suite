package com.arcade.sudoku.data

import com.arcade.sudoku.game.balloon.BalloonView
import com.arcade.sudoku.game.clash.ClashView
import com.arcade.sudoku.game.ludo.LudoView
import com.arcade.sudoku.game.dominoes.DominoOptions
import com.arcade.sudoku.game.dominoes.DominoView
import com.arcade.sudoku.game.numbers.NumberLevel
import com.arcade.sudoku.game.words.WordLevel
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import org.json.JSONArray
import org.json.JSONObject

/** The games a battle room can hold. */
enum class GameKind(val key: String, val label: String) {
    SUDOKU("sudoku", "Sudoku"),
    SHIPS("ships", "Ship Battle"),
    CONNECT4("connect4", "Connect Four"),
    DOTS("dots", "Dots & Boxes"),
    MEMORY("memory", "Memory Pairs"),
    WORDS("words", "Word Race"),
    DRAW("draw", "Draw & Guess"),
    TTT("ttt", "Ultimate Tic-Tac-Toe"),
    DOMINOES("dominoes", "Dominoes"),
    NUMBERS("numbers", "Number Cross"),
    HOCKEY("hockey", "Air Hockey"),
    CLASH("clash", "Colour Clash"),
    LUDO("ludo", "Ludo"),
    GRID("grid", "Letter Grid"),
    BALLOON("balloon", "Balloon Words"),
    CODE("code", "Code Breaker");

    /** Played in turns with moves, sharing the server's turn rules. */
    val turnBased get() = this == CONNECT4 || this == DOTS || this == MEMORY || this == TTT || this == DOMINOES || this == CLASH || this == LUDO || this == BALLOON

    /** How many can play: up to four in the race games, Draw & Guess, Dots & Boxes and Memory Pairs; two in the rest. */
    val maxPlayers get() = if (this == SUDOKU || this == WORDS || this == DRAW || this == DOTS || this == MEMORY || this == DOMINOES || this == NUMBERS || this == CLASH || this == LUDO || this == GRID || this == BALLOON) 4 else 2

    /** Both players just tap Ready to start (no level to agree on, no fleet to place). */
    val readyOnly get() = turnBased || this == WORDS || this == DRAW || this == NUMBERS || this == HOCKEY || this == GRID || this == CODE

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SUDOKU
    }
}

/** Who this phone is when opening a room. [deviceId] stays the same for the app's lifetime. */
data class Hello(val name: String, val avatar: Avatar?, val deviceId: String)

enum class RoomStatus { WAITING, LOBBY, COUNTDOWN, PLAYING, FINISHED;

    companion object {
        fun from(value: String) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WAITING
    }
}

/** [holes] is the number of empty cells to aim for; it must match DIFFICULTY_HOLES in backend/Code.gs. */
enum class Difficulty(val key: String, val label: String, val holes: Int) {
    BEGINNER("beginner", "Beginner", 30),
    EASY("easy", "Easy", 38),
    MEDIUM("medium", "Medium", 46),
    HARD("hard", "Hard", 50),
    EXPERT("expert", "Expert", 54),
    MASTER("master", "Master", 56);

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.key == value } ?: MEDIUM
    }
}

/** Ship Battle state as one player sees it. */
data class ShipsView(
    /** "me", "opponent" or null when nobody is firing. */
    val turn: String?,
    val myFleet: List<Ship>,
    val myShots: List<Shot>,
    val theirShots: List<Shot>,
    val sunkEnemy: List<Ship>,
    /** The whole enemy fleet, only once the battle is over. */
    val enemyFleet: List<Ship>?,
    val lastShotByMe: Boolean?,
)

/**
 * A turn game as one player sees it. Owner strings use 'm' for you, 'o' for the opponent
 * and '.' for nobody.
 */
data class TurnsView(
    /** "me", "opponent" or null when nobody is to move. */
    val turn: String?,
    /** Connect Four: 42 cells from the top row. Ultimate Tic-Tac-Toe: 81 cells, board by board. */
    val board: String?,
    /** Connect Four: the winning cells once someone has four in a row. Ultimate Tic-Tac-Toe: the winning small boards. */
    val winLine: List<Int>,
    val lastMoveByMe: Boolean?,
    /** Connect Four and Ultimate Tic-Tac-Toe: the cell of the last move. */
    val lastCell: Int?,
    /** Dots & Boxes: who drew each of the 40 lines, and who owns each of the 16 boxes. */
    val lines: String? = null,
    val boxes: String? = null,
    val lastLine: Int? = null,
    /** Memory Pairs: each card's picture when face up or matched, and who found it. */
    val faces: List<Avatar?> = emptyList(),
    val owners: String? = null,
    /** Whose turn it is, by name (with more than two players "opponent" doesn't say who). */
    val turnName: String? = null,
    /** Ultimate Tic-Tac-Toe: who won each small board ('d' drawn), and the board to play in next (null: any). */
    val smallBoards: String? = null,
    val nextBoard: Int? = null,
    /** Dominoes: the line, your hand and everyone's counts and scores. */
    val dominoes: DominoView? = null,
    /** Colour Clash: the pile, your hand and everyone's card counts. */
    val clash: ClashView? = null,
    /** Ludo: every player's colour and tokens, and your roll. */
    val ludo: LudoView? = null,
    /** Balloon Words: the word so far, the balloons and the scores. */
    val balloon: BalloonView? = null,
)

/**
 * A Number Cross puzzle as a player sees it: [cells] row by row ('' empty, a number, an operator,
 * or '?' for a blank), the [blanks] in order, and the [bank] of numbers that fill them.
 */
data class NumberBoard(val w: Int, val h: Int, val cells: List<String>, val blanks: List<Int>, val bank: List<Int>)

/** A Number Cross round: the host's level, the board (once playing), your entries and, at the end, the solved grid. */
data class NumbersView(val level: NumberLevel, val board: NumberBoard?, val entries: List<String>?, val solution: List<String>?)

/** One Code Breaker guess: its pegs (hidden for the other player until the end) and the black and white score. */
data class CodeGuess(val code: String?, val black: Int, val white: Int)

/**
 * A Code Breaker round: "set" while codes are being chosen, then "guess". Your own code, your
 * guesses at theirs, their scores (their guesses once it's over) and their code at the end.
 */
data class CodeView(
    val phase: String,
    val tries: Int,
    val myCode: String?,
    val theySet: Boolean,
    val mine: List<CodeGuess>,
    val theirs: List<CodeGuess>,
    val meDone: Boolean,
    val theyDone: Boolean,
    val theirCode: String?,
)

/** One word in a Letter Grid result: unique words (found by nobody else) score double. */
data class GridWord(val word: String, val unique: Boolean, val points: Int)

/** A player's Letter Grid result. */
data class GridResult(val mine: Boolean, val name: String, val score: Int, val words: List<GridWord>)

/**
 * A Letter Grid round: the 16 letters (once playing), when time is up, your words, how many
 * each other player has found (in the order of "others"), and everyone's results at the end.
 */
data class GridView(val letters: String?, val endsAt: Long?, val mine: List<String>, val counts: List<Int>, val results: List<GridResult>?)

/** Air Hockey goals: yours, theirs, and how many win. */
data class HockeyScore(val mine: Int, val theirs: Int, val target: Int)

/** One Word Race guess: the word (hidden for the opponent until the round is over) and its colours. */
data class WordGuess(val word: String?, val score: String)

/** A Word Race round as one player sees it. */
data class WordsView(
    val tries: Int,
    /** Letters in the secret word (the host's level sets it). */
    val length: Int = 5,
    val level: WordLevel = WordLevel.MEDIUM,
    val mine: List<WordGuess>,
    val theirs: List<WordGuess>,
    val meDone: Boolean,
    val theyDone: Boolean,
    /** The secret word, once the round is over. */
    val answer: String?,
    /** Everyone else's guesses (colours only until the end) and whether they are done, in the order of [RoomState.others]. */
    val others: List<OtherWords> = emptyList(),
)

/** Another player's Word Race round. */
data class OtherWords(val guesses: List<WordGuess>, val done: Boolean)

/** A Draw & Guess stroke: a polyline in 0-1 coordinates (x, y pairs), one of six colours, thin (1) or thick (2). */
class DrawStroke(val points: FloatArray, val color: Int, val width: Int) {
    fun toJson(): JSONObject = JSONObject()
        .put("points", JSONArray((0 until points.size / 2).map { JSONArray().put(points[it * 2].toDouble()).put(points[it * 2 + 1].toDouble()) }))
        .put("color", color)
        .put("width", width)

    companion object {
        fun from(json: JSONObject): DrawStroke {
            val pts = json.optJSONArray("points") ?: JSONArray()
            val flat = FloatArray(pts.length() * 2)
            for (i in 0 until pts.length()) {
                val pt = pts.optJSONArray(i) ?: continue
                flat[i * 2] = pt.optDouble(0).toFloat()
                flat[i * 2 + 1] = pt.optDouble(1).toFloat()
            }
            return DrawStroke(flat, json.optInt("color"), json.optInt("width", 1))
        }
    }
}

/** How a Draw & Guess turn ended. */
data class DrawTurnResult(
    val word: String,
    val guessed: Boolean,
    val points: Int,
    val drewIt: Boolean,
    val drawerName: String? = null,
    val guesserName: String? = null,
    val guessedIt: Boolean = false,
)

/** A Draw & Guess match as one player sees it. */
data class DrawView(
    val turn: Int,
    val turns: Int,
    /** True when you draw this turn, false when you guess, null between matches. */
    val drawing: Boolean?,
    /** The secret word, for the drawer only. */
    val word: String?,
    /** The word as underscores, for the guesser. */
    val hint: String?,
    /** When this turn's time runs out (server clock). */
    val endsAt: Long?,
    val strokes: List<DrawStroke>,
    /** How many times the drawer cleared this turn's drawing. */
    val clears: Int = 0,
    /** Wrong guesses this turn: (by me, word), and who made each. */
    val guesses: List<Pair<Boolean, String>>,
    val score: Int,
    val lastTurn: DrawTurnResult?,
    val drawerName: String? = null,
    val guessers: List<String?> = emptyList(),
)

/** A player's latest reaction; [seq] goes up with every new one. */
data class EmojiEvent(val reaction: Reaction, val seq: Int)

data class PlayerInfo(
    val name: String,
    val avatar: Avatar?,
    /** The other phone's permanent id, used to invite them again later (Cloudflare only). */
    val deviceId: String?,
    val emoji: EmojiEvent?,
    val ready: Boolean,
    /** The level this player is ready for; null until they choose one. */
    val difficulty: Difficulty?,
    /** Percentage of correctly solved cells; only sent for the opponent (and for you after a match). */
    val progress: Int?,
    val finishMs: Long?,
    val left: Boolean,
    val idleMs: Long,
    /** Whether a live connection is up; null for the polling (Apps Script) server. */
    val online: Boolean?,
    /** Left or lost their connection during the match being played, which goes on without them. */
    val out: Boolean = false,
)

data class RoomState(
    val code: String,
    val role: String,
    val game: GameKind,
    val status: RoomStatus,
    /** The level of the current or last round; null before the first round starts. */
    val difficulty: Difficulty?,
    val round: Int,
    val startAt: Long?,
    val puzzle: String?,
    val solution: String?,
    /** "me", "opponent" or null while nobody has won. */
    val winner: String?,
    /** "solved", "sunk", "four", "draw", "forfeit" or "timeout". */
    val endReason: String?,
    val me: PlayerInfo,
    /** The first of [others]: the opponent in a two-player game. */
    val opponent: PlayerInfo?,
    val ships: ShipsView?,
    val turns: TurnsView?,
    val words: WordsView? = null,
    val draw: DrawView? = null,
    val numbers: NumbersView? = null,
    /** Air Hockey: the score as the server last heard it from the host. */
    val hockey: HockeyScore? = null,
    val grid: GridView? = null,
    /** Code Breaker ("code" in the JSON; [code] is the room's code). */
    val codeGame: CodeView? = null,
    val serverTime: Long,
    /** Everyone else in the room, in seat order (up to three). */
    val others: List<PlayerInfo> = listOfNotNull(opponent),
    /** The winner's name, whoever it is. */
    val winnerName: String? = null,
    val maxPlayers: Int = 2,
    /** Dominoes: the host's rules for the match. */
    val options: DominoOptions? = null,
) {
    val isHost get() = role == "host"

    /** Others still in the room. */
    val present get() = others.filter { !it.left }
}

fun parseRoom(json: JSONObject) = RoomState(
    code = json.getString("code"),
    role = json.getString("role"),
    game = GameKind.from(json.stringOrNull("game")),
    status = RoomStatus.from(json.getString("status")),
    difficulty = json.stringOrNull("difficulty")?.let { Difficulty.from(it) },
    round = json.optInt("round"),
    startAt = json.longOrNull("startAt"),
    puzzle = json.stringOrNull("puzzle"),
    solution = json.stringOrNull("solution"),
    winner = json.stringOrNull("winner"),
    endReason = json.stringOrNull("endReason"),
    me = parsePlayer(json.getJSONObject("me")),
    opponent = json.optJSONObject("opponent")?.let(::parsePlayer),
    ships = json.optJSONObject("ships")?.let(::parseShips),
    turns = json.optJSONObject("turns")?.let(::parseTurns),
    words = json.optJSONObject("words")?.let(::parseWords),
    draw = json.optJSONObject("draw")?.let(::parseDraw),
    numbers = json.optJSONObject("numbers")?.let(::parseNumbers),
    grid = json.optJSONObject("grid")?.let(::parseGrid),
    codeGame = json.optJSONObject("code")?.let(::parseCode),
    hockey = json.optJSONObject("hockey")?.let { HockeyScore(it.optInt("mine"), it.optInt("theirs"), it.optInt("target", 7)) },
    serverTime = json.getLong("serverTime"),
    // Servers from before rooms of four send only "opponent".
    others = json.optJSONArray("others")?.objects()?.map(::parsePlayer) ?: listOfNotNull(json.optJSONObject("opponent")?.let(::parsePlayer)),
    winnerName = json.stringOrNull("winnerName"),
    maxPlayers = json.optInt("maxPlayers", 2),
    options = json.optJSONObject("options")?.let { DominoOptions.from(it) },
)

private fun parsePlayer(json: JSONObject) = PlayerInfo(
    name = json.optString("name", "Player"),
    avatar = Avatar.from(json.stringOrNull("avatar")),
    deviceId = json.stringOrNull("deviceId"),
    emoji = json.optJSONObject("emoji")?.let { e ->
        Reaction.from(e.optString("key"))?.let { EmojiEvent(it, e.optInt("seq")) }
    },
    ready = json.optBoolean("ready"),
    difficulty = json.stringOrNull("difficulty")?.let { Difficulty.from(it) },
    progress = if (json.isNull("progress")) null else json.optInt("progress"),
    finishMs = json.longOrNull("finishMs"),
    left = json.optBoolean("left"),
    idleMs = json.optLong("idleMs"),
    online = if (json.has("online")) json.optBoolean("online") else null,
    out = json.optBoolean("out"),
)

private fun parseShips(json: JSONObject) = ShipsView(
    turn = json.stringOrNull("turn"),
    myFleet = json.optJSONArray("myFleet").ships(),
    myShots = json.optJSONArray("myShots").shots(),
    theirShots = json.optJSONArray("theirShots").shots(),
    sunkEnemy = json.optJSONArray("sunkEnemy").ships(),
    enemyFleet = if (json.isNull("enemyFleet")) null else json.optJSONArray("enemyFleet").ships(),
    lastShotByMe = json.optJSONObject("lastShot")?.optString("by")?.let { it == "me" },
)

private fun parseDraw(json: JSONObject) = DrawView(
    turn = json.optInt("turn"),
    turns = json.optInt("turns", 4),
    drawing = json.stringOrNull("drawer")?.let { it == "me" },
    word = json.stringOrNull("word"),
    hint = json.stringOrNull("hint"),
    endsAt = json.longOrNull("endsAt"),
    strokes = json.optJSONArray("strokes").objects().map(DrawStroke::from),
    clears = json.optInt("clears"),
    guesses = json.optJSONArray("guesses").objects().map { (it.optString("by") == "me") to it.optString("word") },
    score = json.optInt("score"),
    lastTurn = json.optJSONObject("lastTurn")?.let {
        DrawTurnResult(
            it.optString("word"), it.optBoolean("guessed"), it.optInt("points"), it.optBoolean("drewIt"),
            it.stringOrNull("drawerName"), it.stringOrNull("guesserName"), it.optBoolean("guessedIt"),
        )
    },
    drawerName = json.stringOrNull("drawerName"),
    guessers = json.optJSONArray("guesses").objects().map { it.stringOrNull("name") },
)

private fun parseWords(json: JSONObject): WordsView {
    fun guesses(key: String) = json.optJSONArray(key).objects().map { WordGuess(it.stringOrNull("word"), it.optString("score")) }
    return WordsView(
        tries = json.optInt("tries", 6),
        length = json.optInt("length", 5),
        level = WordLevel.from(json.stringOrNull("level")),
        mine = guesses("mine"),
        theirs = guesses("theirs"),
        meDone = json.optBoolean("meDone"),
        theyDone = json.optBoolean("theyDone"),
        answer = json.stringOrNull("answer"),
        others = json.optJSONArray("others").objects().map { o ->
            OtherWords(o.optJSONArray("guesses").objects().map { WordGuess(it.stringOrNull("word"), it.optString("score")) }, o.optBoolean("done"))
        },
    )
}

private fun parseCode(json: JSONObject): CodeView {
    fun rows(key: String) = json.optJSONArray(key).objects().map { CodeGuess(it.stringOrNull("word"), it.optInt("black"), it.optInt("white")) }
    return CodeView(
        phase = json.optString("phase", "set"),
        tries = json.optInt("tries", 10),
        myCode = json.stringOrNull("myCode"),
        theySet = json.optBoolean("theySet"),
        mine = rows("mine"),
        theirs = rows("theirs"),
        meDone = json.optBoolean("meDone"),
        theyDone = json.optBoolean("theyDone"),
        theirCode = json.stringOrNull("theirCode"),
    )
}

private fun parseGrid(json: JSONObject) = GridView(
    letters = json.stringOrNull("letters"),
    endsAt = json.longOrNull("endsAt"),
    mine = json.optJSONArray("mine")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
    counts = json.optJSONArray("counts")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty(),
    results = json.optJSONArray("results")?.objects()?.map { r ->
        GridResult(
            r.optString("who") == "me", r.optString("name"), r.optInt("score"),
            r.optJSONArray("words").objects().map { GridWord(it.optString("word"), it.optBoolean("unique"), it.optInt("points")) },
        )
    },
)

private fun parseNumbers(json: JSONObject): NumbersView {
    fun strings(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.optString(i) } }
    val p = json.optJSONObject("puzzle")
    return NumbersView(
        level = NumberLevel.from(json.stringOrNull("level")),
        board = p?.let {
            NumberBoard(
                it.optInt("w"), it.optInt("h"), strings(it.optJSONArray("cells")).orEmpty(),
                it.optJSONArray("blanks")?.let { a -> (0 until a.length()).map { i -> a.optInt(i) } }.orEmpty(),
                it.optJSONArray("bank")?.let { a -> (0 until a.length()).map { i -> a.optInt(i) } }.orEmpty(),
            )
        },
        entries = strings(json.optJSONArray("entries")),
        solution = strings(json.optJSONArray("solution")),
    )
}

private fun parseTurns(json: JSONObject): TurnsView {
    val last = json.optJSONObject("lastMove")
    return TurnsView(
        turn = json.stringOrNull("turn"),
        board = json.stringOrNull("board"),
        winLine = json.optJSONArray("winLine")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty(),
        lastMoveByMe = last?.optString("by")?.let { it == "me" },
        lastCell = last?.takeIf { it.has("cell") }?.optInt("cell"),
        lines = json.stringOrNull("lines"),
        boxes = json.stringOrNull("boxes"),
        lastLine = last?.takeIf { it.has("line") }?.optInt("line"),
        faces = json.optJSONArray("faces")?.let { a -> (0 until a.length()).map { if (a.isNull(it)) null else Avatar.from(a.optString(it)) } }.orEmpty(),
        owners = json.stringOrNull("owners"),
        turnName = json.stringOrNull("turnName"),
        smallBoards = json.stringOrNull("boards"),
        nextBoard = json.optInt("next", -1).takeIf { it >= 0 },
        dominoes = if (json.has("line")) DominoView.from(json) else null,
        clash = if (json.has("top")) ClashView.from(json) else null,
        ludo = if (json.has("tokens")) LudoView.from(json) else null,
        balloon = if (json.has("pattern")) BalloonView.from(json) else null,
    )
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.ships() = objects().map {
    Ship(it.optInt("x"), it.optInt("y"), it.optInt("len"), it.optBoolean("horizontal"))
}

private fun JSONArray?.shots() = objects().map { Shot(it.optInt("x"), it.optInt("y"), it.optBoolean("hit")) }

/** A fleet as the servers expect it. */
fun List<Ship>.toJson(): JSONArray = JSONArray(
    map { JSONObject().put("x", it.x).put("y", it.y).put("len", it.len).put("horizontal", it.horizontal) },
)

private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)
