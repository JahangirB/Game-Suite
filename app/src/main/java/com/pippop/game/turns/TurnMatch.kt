package com.pippop.game.turns

import com.pippop.data.GameKind
import com.pippop.game.balloon.BalloonMatch
import com.pippop.game.blocks.BlocksMatch
import com.pippop.game.clash.ClashMatch
import com.pippop.game.connect4.C4Match
import com.pippop.game.ludo.LudoMatch
import com.pippop.game.dominoes.DominoMatch
import com.pippop.game.dominoes.DominoOptions
import com.pippop.game.dots.DotsMatch
import com.pippop.game.golf.Courses
import com.pippop.game.golf.GolfLevel
import com.pippop.game.golf.GolfMatch
import com.pippop.game.memory.MemoryMatch
import com.pippop.game.ttt.TttMatch
import org.json.JSONObject
import kotlin.random.Random

/**
 * What a move did: [again] the same player moves next; [over] with [winner] null is a draw;
 * [turn], when set, is who moves next (Dominoes: the winner of a round starts the next).
 */
data class TurnResult(val again: Boolean, val over: Boolean = false, val winner: Char? = null, val reason: String? = null, val turn: Char? = null)

/**
 * One round of a turn game, matching the server modules in backend/cloudflare/src
 * (connect4.js, dots.js, memory.js, ttt.js, dominoes.js). A mark is a player's one-letter id.
 */
interface TurnMatch {
    /** Who must move first, when the game decides it (Dominoes); null lets the room choose. */
    val firstMark: Char? get() = null

    /** Plays [move] for [mark]; throws IllegalArgumentException for a move that isn't allowed. */
    fun play(mark: Char, move: JSONObject): TurnResult

    /**
     * The round as [mark] sees it: the same JSON the server sends under "turns". [seats] maps
     * each other player's mark to the letter they show as ('o', 'p', 'q'); empty means 'o'.
     */
    fun view(mark: Char, seats: Map<Char, Char> = emptyMap()): JSONObject

    companion object {
        fun start(
            game: GameKind,
            random: Random = Random.Default,
            marks: List<Char> = listOf('h', 'g'),
            options: DominoOptions = DominoOptions(),
            /** Tic-Tac-Toe: normal 3 × 3 instead of Ultimate. */
            tttClassic: Boolean = false,
            /** Mini Golf: how many islands every hole has. */
            golfLevel: GolfLevel = GolfLevel.EASY,
        ): TurnMatch = when (game) {
            GameKind.DOMINOES -> DominoMatch(marks, options, random)
            GameKind.CLASH -> ClashMatch(marks, random)
            GameKind.LUDO -> LudoMatch(marks, random)
            GameKind.BALLOON -> BalloonMatch(marks)
            GameKind.GOLF -> GolfMatch(marks, Courses.pick(golfLevel, random))
            GameKind.BLOCKS -> BlocksMatch(marks)
            GameKind.DOTS -> DotsMatch()
            GameKind.MEMORY -> MemoryMatch(random)
            GameKind.TTT -> TttMatch(tttClassic)
            else -> C4Match()
        }

        /** [owners] seen from [mark]: 'm' theirs, others as [seats] says ('o' without one). */
        fun side(owners: String, mark: Char, seats: Map<Char, Char> = emptyMap()) =
            owners.map { if (it == '.') '.' else if (it == mark) 'm' else seats[it] ?: 'o' }.joinToString("")

        fun lastMove(by: Char, mark: Char, key: String, value: Int): JSONObject =
            JSONObject().put("by", if (by == mark) "me" else "opponent").put(key, value)

        /** Whoever owns the most cells in [owners], or null when two or more tie for the most. */
        fun winnerOf(owners: String): Char? {
            val ranked = owners.filter { it != '.' }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            if (ranked.isEmpty() || (ranked.size > 1 && ranked[1].value == ranked[0].value)) return null
            return ranked[0].key
        }
    }
}
