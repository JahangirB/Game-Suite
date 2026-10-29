package com.pippop.game.clash

import com.pippop.game.turns.TurnMatch
import com.pippop.game.turns.TurnResult
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Colour Clash rules, matching the server (backend/cloudflare/src/clash.js). A card is a
 * string: a colour (r, y, g, b) and a face (0-9, S skip, R reverse, D draw two, K Kylian
 * Dictador: draw eight), or a wild: W (wild) or F (wild draw four). There's one Kylian per colour.
 */
object Clash {
    const val COLOURS = "rygb"

    fun isWild(card: String) = card == "W" || card == "F"

    fun face(card: String) = if (isWild(card)) card else card.substring(1)

    fun fits(card: String, top: String, colour: Char) = isWild(card) || card[0] == colour || face(card) == face(top)

    /** Kylian Dictador: the next player draws eight, and he takes the game over. */
    fun isKylian(card: String) = face(card) == "K"

    /** How many cards a draw card makes the next player take (0 for any other card). */
    fun draws(card: String) = when (face(card)) { "D" -> 2; "F" -> 4; "K" -> 8; else -> 0 }

    fun fullDeck(): List<String> = buildList {
        for (c in COLOURS) {
            add("${c}0")
            for (f in "123456789SRD") repeat(2) { add("$c$f") }
            add("${c}K")
        }
        repeat(4) { add("W"); add("F") }
    }
}

/** One Colour Clash game: first to empty their hand wins. Every result says who plays next. */
class ClashMatch(val marks: List<Char>, private val random: Random = Random.Default) : TurnMatch {
    val hands: Map<Char, MutableList<String>>
    var deck: MutableList<String>
        private set
    private var pile: MutableList<String>
    var colour: Char
        private set
    private var direction = 1
    private var drawn: Char? = null
    private var last: JSONObject? = null

    val top get() = pile.last()

    init {
        val cards = Clash.fullDeck().shuffled(random).toMutableList()
        hands = marks.associateWith { MutableList(7) { cards.removeAt(0) } }
        val at = cards.indexOfFirst { !Clash.isWild(it) && it[1].isDigit() }
        pile = mutableListOf(cards.removeAt(at))
        deck = cards
        colour = pile.last()[0]
    }

    /** Whether [mark] has just drawn a card that may be played. */
    fun hasDrawn(mark: Char) = drawn == mark

    private fun after(mark: Char, steps: Int = 1): Char {
        val n = marks.size
        val i = marks.indexOf(mark)
        return marks[(((i + direction * steps) % n) + n) % n]
    }

    private fun take(n: Int): List<String> = buildList {
        repeat(n) {
            if (deck.isEmpty()) {
                val keep = pile.removeAt(pile.lastIndex)
                deck = pile.shuffled(random).toMutableList()
                pile = mutableListOf(keep)
            }
            if (deck.isEmpty()) return@buildList
            add(deck.removeAt(deck.lastIndex))
        }
    }

    override fun play(mark: Char, move: JSONObject): TurnResult {
        val hand = hands.getValue(mark)
        val top = pile.last()
        if (move.optBoolean("draw")) {
            require(drawn != mark) { "You already drew: play it or pass." }
            val card = take(1).firstOrNull() ?: throw IllegalArgumentException("There are no cards left to draw: pass.")
            hand += card
            last = JSONObject().put("mark", mark.toString()).put("drew", 1)
            if (Clash.fits(card, top, colour)) {
                drawn = mark
                return TurnResult(again = true, turn = mark)
            }
            drawn = null
            return TurnResult(again = false, turn = after(mark))
        }
        if (move.optBoolean("pass")) {
            require(drawn == mark || (deck.isEmpty() && pile.size <= 1)) { "Draw a card first." }
            drawn = null
            last = JSONObject().put("mark", mark.toString()).put("passed", true)
            return TurnResult(again = false, turn = after(mark))
        }
        val index = move.optInt("card", -1)
        require(index in hand.indices) { "Pick one of your cards." }
        require(drawn != mark || index == hand.lastIndex) { "Play the card you drew, or pass." }
        val card = hand[index]
        require(Clash.fits(card, top, colour)) { "That card doesn't match." }
        var chosen = card[0]
        if (Clash.isWild(card)) {
            val c = move.optString("colour")
            require(c.length == 1 && c[0] in Clash.COLOURS) { "Choose a colour." }
            chosen = c[0]
        }
        hand.removeAt(index)
        pile += card
        colour = chosen
        drawn = null
        val lastMove = JSONObject().put("mark", mark.toString()).put("card", card).put("colour", chosen.toString())
        last = lastMove
        if (hand.isEmpty()) return TurnResult(again = false, over = true, winner = mark, reason = "out")
        var next = after(mark)
        when (Clash.face(card)) {
            "R" -> {
                direction = -direction
                next = if (marks.size == 2) mark else after(mark)
            }
            "S" -> next = after(mark, 2)
            "D", "F", "K" -> {
                val victim = after(mark)
                val n = Clash.draws(card)
                hands.getValue(victim) += take(n)
                lastMove.put("made", JSONObject().put("mark", victim.toString()).put("drew", n))
                next = after(mark, 2)
            }
        }
        return TurnResult(again = next == mark, turn = next)
    }

    override fun view(mark: Char, seats: Map<Char, Char>): JSONObject {
        fun who(m: Char) = if (m == mark) 'm' else seats[m] ?: 'o'
        return JSONObject()
            .put("top", pile.last())
            .put("colour", colour.toString())
            .put("hand", JSONArray(hands[mark].orEmpty()))
            .put("counts", JSONObject().apply { for (m in marks) put(who(m).toString(), hands.getValue(m).size) })
            .put("order", JSONArray(marks.map { who(it).toString() }))
            .put("direction", direction)
            .put("deck", deck.size)
            .put("drawn", drawn == mark)
            .put(
                "lastMove",
                last?.let {
                    val by = it.getString("mark")[0]
                    JSONObject().put("by", if (by == mark) "me" else "opponent").put("who", who(by).toString())
                        .put("card", it.opt("card") ?: JSONObject.NULL).put("colour", it.opt("colour") ?: JSONObject.NULL)
                        .put("drew", it.optInt("drew")).put("passed", it.optBoolean("passed"))
                        .put("made", it.optJSONObject("made")?.let { m -> JSONObject().put("who", who(m.getString("mark")[0]).toString()).put("drew", m.optInt("drew")) } ?: JSONObject.NULL)
                } ?: JSONObject.NULL,
            )
    }
}

/**
 * The computer: plays a matching card if it has one (Kylian first, then action cards, wilds
 * last, naming the colour it holds most of), else draws, and plays the drawn card if it can.
 */
class ClashComputer {
    fun move(match: ClashMatch, mark: Char): JSONObject {
        val hand = match.hands.getValue(mark)
        val playable = hand.indices.filter { Clash.fits(hand[it], match.top, match.colour) }
            .filter { !match.hasDrawn(mark) || it == hand.lastIndex }
        if (playable.isEmpty()) return JSONObject().put(if (match.hasDrawn(mark)) "pass" else "draw", true)
        val pick = playable.maxBy { i ->
            val c = hand[i]
            when {
                Clash.isWild(c) -> 0
                Clash.isKylian(c) -> 4
                Clash.face(c) in listOf("S", "R", "D") -> 3
                c[0] == match.colour -> 2
                else -> 1
            }
        }
        val move = JSONObject().put("card", pick)
        if (Clash.isWild(hand[pick])) {
            val best = hand.filter { !Clash.isWild(it) }.groupingBy { it[0] }.eachCount().maxByOrNull { it.value }?.key ?: 'r'
            move.put("colour", best.toString())
        }
        return move
    }
}

/** The last move: who ('m' you), the card played and colour named, or a draw or pass. */
data class ClashMove(val who: Char, val card: String?, val colour: Char?, val drew: Int, val passed: Boolean, val madeDraw: Pair<Char, Int>?)

/** A Colour Clash game as one player sees it: the "turns" JSON from [ClashMatch.view] or the server. */
data class ClashView(
    val top: String,
    val colour: Char,
    val hand: List<String>,
    val counts: Map<Char, Int>,
    val order: List<Char>,
    val direction: Int,
    val deck: Int,
    /** You just drew a card that fits: play it (the last in your hand) or pass. */
    val drawn: Boolean,
    val lastMove: ClashMove?,
) {
    fun canPlay(i: Int) = i in hand.indices && Clash.fits(hand[i], top, colour) && (!drawn || i == hand.lastIndex)

    companion object {
        fun from(json: JSONObject): ClashView {
            val h = json.optJSONArray("hand")
            val o = json.optJSONArray("order")
            val counts = json.optJSONObject("counts")
            val last = json.optJSONObject("lastMove")
            return ClashView(
                top = json.optString("top"),
                colour = json.optString("colour", "r").ifEmpty { "r" }[0],
                hand = h?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
                counts = counts?.keys()?.asSequence()?.associate { it[0] to counts.optInt(it) }.orEmpty(),
                order = o?.let { a -> (0 until a.length()).map { a.optString(it)[0] } }.orEmpty(),
                direction = json.optInt("direction", 1),
                deck = json.optInt("deck"),
                drawn = json.optBoolean("drawn"),
                lastMove = last?.let {
                    ClashMove(
                        it.optString("who", "o").ifEmpty { "o" }[0],
                        if (it.isNull("card")) null else it.optString("card"),
                        if (it.isNull("colour")) null else it.optString("colour").firstOrNull(),
                        it.optInt("drew"), it.optBoolean("passed"),
                        it.optJSONObject("made")?.let { m -> m.optString("who", "o")[0] to m.optInt("drew") },
                    )
                },
            )
        }
    }
}
