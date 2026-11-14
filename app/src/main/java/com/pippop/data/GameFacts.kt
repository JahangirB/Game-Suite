package com.pippop.data

/**
 * What a finished game with others can say beyond who won, as far as this phone can tell from the
 * room: how long it took, each side's own score (yours, and the best of the others'), and the
 * winner's count where fewest is quickest. Whatever a game doesn't have is null.
 */
data class GameFacts(val durationMs: Long? = null, val mine: Int? = null, val theirs: Int? = null, val count: Int? = null) {
    companion object {
        /** A game longer than this is not a game that was timed: the clock was wrong, so say nothing. */
        private const val LONGEST_MS = 3 * 60 * 60 * 1000L

        /** The facts of the game that has just finished in [room] (see [GameFacts] for what each game gives). */
        fun of(room: RoomState): GameFacts {
            val meWon = room.winner == "me"
            val theyWon = room.winner == "opponent" && !room.together
            val others = room.others
            val winnerInfo = if (meWon) room.me else others.firstOrNull { it.name == room.winnerName } ?: room.opponent
            val timeOnClock = room.startAt?.let { room.serverTime - it }
            val facts = when (room.game) {
                // A race against the clock: the winner's own time.
                GameKind.SUDOKU, GameKind.NUMBERS, GameKind.SLIDE ->
                    GameFacts(durationMs = if (room.winner != null && !room.together) winnerInfo?.finishMs else null)
                GameKind.SHIPS -> room.ships?.let { GameFacts(count = if (meWon) it.myShots.size else if (theyWon) it.theirShots.size else null) }
                GameKind.CONNECT4 -> room.turns?.board?.let { b ->
                    GameFacts(count = if (meWon) b.count { it == 'm' } else if (theyWon) b.count { it == 'o' } else null)
                }
                GameKind.DOTS -> room.turns?.boxes?.let { b -> GameFacts(mine = b.count { it == 'm' }, theirs = bestOther(b)) }
                GameKind.MEMORY -> room.turns?.owners?.let { o -> GameFacts(mine = o.count { it == 'm' } / 2, theirs = bestOther(o)?.div(2)) }
                GameKind.WORDS -> room.words?.let { w ->
                    val theirs = room.winnerName?.let { n -> others.indexOfFirst { it.name == n }.takeIf { it >= 0 }?.let { w.others.getOrNull(it)?.guesses?.size } } ?: w.theirs.size
                    GameFacts(count = if (meWon) w.mine.size else if (theyWon) theirs else null)
                }
                GameKind.DOMINOES -> room.turns?.dominoes?.let { d ->
                    GameFacts(mine = d.scores['m'], theirs = d.scores.filterKeys { it != 'm' && it !in d.partners }.values.maxOrNull())
                }
                GameKind.HOCKEY -> room.hockey?.let { GameFacts(mine = it.mine, theirs = it.theirs) }
                GameKind.GRID -> room.grid?.results?.let { rs -> GameFacts(mine = rs.firstOrNull { it.mine }?.score, theirs = rs.filter { !it.mine }.maxOfOrNull { it.score }) }
                GameKind.BALLOON -> room.turns?.balloon?.let { b -> GameFacts(mine = b.scores['m'], theirs = b.scores.filterKeys { it != 'm' }.values.maxOrNull()) }
                GameKind.CODE -> room.codeGame?.let { c -> GameFacts(count = if (meWon) c.mine.size else if (theyWon) c.theirs.size else null) }
                GameKind.GOLF -> room.turns?.golf?.let { g ->
                    GameFacts(mine = g.total('m').takeIf { it > 0 }, theirs = g.strokes.filterKeys { it != 'm' }.values.map { it.sum() }.filter { it > 0 }.minOrNull())
                }
                GameKind.BLOCKS -> room.turns?.blocks?.let { b -> GameFacts(mine = b.placed['m'], theirs = b.placed.filterKeys { it != 'm' }.values.maxOrNull()) }
                GameKind.DRAW -> room.draw?.let { d ->
                    if (d.race) GameFacts(mine = d.scores.firstOrNull { it.me }?.points, theirs = d.scores.filter { !it.me }.maxOfOrNull { it.points }) else GameFacts(mine = d.score)
                }
                GameKind.BINGO, GameKind.CLASH, GameKind.LUDO, GameKind.TTT -> null
            } ?: GameFacts()
            val ms = facts.durationMs ?: timeOnClock
            return facts.copy(durationMs = ms?.takeIf { it in 1..LONGEST_MS })
        }

        /** The most boxes or cards anyone but you holds in a string of owners ('.' and ' ' are nobody's). */
        private fun bestOther(owners: String): Int? =
            owners.filter { it != 'm' && it != '.' && it != ' ' }.groupingBy { it }.eachCount().values.maxOrNull()
    }
}
