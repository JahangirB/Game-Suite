package com.pippop.game.memory

import com.pippop.data.Avatar
import com.pippop.game.turns.TurnMatch
import com.pippop.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** A Memory card's picture: an animal, on a plain card or (in the 36-card deck) a gold one. */
data class CardFace(val avatar: Avatar, val gold: Boolean = false) {
    val key get() = avatar.key + if (gold) "*" else ""

    companion object {
        fun from(key: String?): CardFace? = key?.let { k -> Avatar.from(k.removeSuffix("*"))?.let { CardFace(it, k.endsWith("*")) } }
    }
}

/**
 * One Memory Pairs round, matching backend/cloudflare/src/memory.js: [cards] cards (16, 24 or
 * 36), pairs of avatar pictures (gold-backed ones once the animals run out). Flip two; a pair is
 * yours and you go again, otherwise they stay up until the next flip and the turn passes.
 * Moves are {"card": n}.
 */
class MemoryMatch(random: Random = Random.Default, cards: Int = CARDS) : TurnMatch {
    val cards = cards.takeIf { it in DECKS } ?: CARDS
    val deck: List<CardFace> = (Avatar.entries.shuffled(random).map { CardFace(it) } + Avatar.entries.shuffled(random).map { CardFace(it, gold = true) })
        .take(this.cards / 2).let { (it + it).shuffled(random) }
    var owners = ".".repeat(this.cards)
        private set
    /** Face-up cards that aren't matched: a first flip, or a missed pair until the next flip. */
    var up: List<Int> = emptyList()
        private set
    private var last: Pair<Char, Int>? = null

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val card = move.optInt("card", -1)
        require(card in 0 until cards) { "Pick a card." }
        require(owners[card] == '.') { "That pair is already found." }
        if (up.size == 2) up = emptyList()
        require(card !in up) { "That card is already face up." }
        last = mark to card
        if (up.isEmpty()) {
            up = listOf(card)
            return TurnResult(again = true)
        }
        val first = up[0]
        if (deck[first] != deck[card]) {
            up = listOf(first, card)
            return TurnResult(again = false)
        }
        up = emptyList()
        owners = owners.substring(0, first) + mark + owners.substring(first + 1)
        owners = owners.substring(0, card) + mark + owners.substring(card + 1)
        if ('.' in owners) return TurnResult(again = true)
        return TurnResult(again = false, over = true, winner = TurnMatch.winnerOf(owners), reason = "most")
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject = JSONObject()
        .put("faces", JSONArray(deck.mapIndexed { i, a -> if (owners[i] != '.' || i in up) a.key else JSONObject.NULL }))
        .put("owners", TurnMatch.side(owners, mark, seats))
        .put("lastMove", last?.let { (by, card) -> TurnMatch.lastMove(by, mark, "card", card) } ?: JSONObject.NULL)

    companion object {
        const val CARDS = 16

        /** The deck sizes to pick from: 4 × 4, 4 × 6 or 6 × 6 (as on the server). */
        val DECKS = listOf(16, 24, 36)

        /** Cards across a row for a deck of [cards]. */
        fun columns(cards: Int) = if (cards == 36) 6 else 4
    }
}
