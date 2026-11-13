package com.pippop.ui.screens

import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RecentGame
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.random.Random

/** One tile of the home's "Jump back in" row: a game and the small tag on its picture. */
data class JumpTile(val game: GameKind, val tag: String)

/** How many tiles "Jump back in" holds. */
const val JUMP_TILES = 3

/**
 * How long ago [at] was, as a tag: "Today", "Yesterday", the weekday for the six days before, then
 * the date ("3 Mar", with the year when it is not this one). Days are counted in [zone].
 */
fun recentTag(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.UK): String {
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(day, today)) {
        in Long.MIN_VALUE..0L -> "Today"
        1L -> "Yesterday"
        in 2L..6L -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        else -> dateTag(day, today, locale)
    }
}

private fun dateTag(day: LocalDate, today: LocalDate, locale: Locale): String {
    val month = day.month.getDisplayName(TextStyle.SHORT, locale)
    return if (day.year == today.year) "${day.dayOfMonth} $month" else "${day.dayOfMonth} $month ${day.year}"
}

/**
 * The first row of the home: the Sudoku in progress first ([sudokuPercent] is how far along it is,
 * null when none is), then the games played last (newest first). No game appears twice.
 */
fun jumpBackIn(
    sudokuPercent: Int?,
    recent: List<RecentGame>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    max: Int = JUMP_TILES,
): List<JumpTile> {
    val first = if (sudokuPercent != null) listOf(JumpTile(GameKind.SUDOKU, "$sudokuPercent% done")) else emptyList()
    val rest = recent.sortedByDescending { it.at }.map { JumpTile(it.game, recentTag(it.at, now, zone)) }
    return (first + rest).distinctBy { it.game }.take(max)
}

/**
 * Whether [kind] suits [players] people at once. Alone (one player: no room, or nobody else in it)
 * a game needs solo play; Draw & Guess has none.
 */
fun fitsPlayers(kind: GameKind, players: Int): Boolean =
    if (players <= 1) kind != GameKind.DRAW else kind.maxPlayers >= players

/** The games "Surprise us" may land on: those on the [shelf] that fit [players]. */
fun surpriseCandidates(shelf: List<GameKind>, players: Int): List<GameKind> = shelf.filter { fitsPlayers(it, players) }

/** One game for "Surprise us", or null when none fits. */
fun surprisePick(shelf: List<GameKind>, players: Int, random: Random = Random): GameKind? =
    surpriseCandidates(shelf, players).let { if (it.isEmpty()) null else it[random.nextInt(it.size)] }

/**
 * Names on one line: "Maya", "Maya & Leo", "Maya, Leo & Kim". When the line would be longer than
 * [maxChars] it is cut and ends in an ellipsis ("Maya, Leo & K…").
 */
fun joinNames(names: List<String>, maxChars: Int = Int.MAX_VALUE): String {
    val line = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " & " + names.last()
    }
    return if (line.length <= maxChars) line else line.take((maxChars - 1).coerceAtLeast(0)).trimEnd() + "…"
}

/** Whether [kind] has a seat for everyone when [people] are in the room (you included). */
fun fitsRoom(kind: GameKind, people: Int): Boolean = kind.maxPlayers >= people

/** How many a game takes, as its tile says it: "2" or "2–4". */
fun seatsLabel(kind: GameKind): String = if (kind.maxPlayers > 2) "2–${kind.maxPlayers}" else "2"

/** What a faded tile says: the most it takes ("2 players"). */
fun fadedLabel(kind: GameKind): String = "${kind.maxPlayers} players"

/**
 * The people in [kind]: only the room's open game ([open]) has anyone, and only those whose screen is on it.
 * A player's old game is not counted once the room has moved on.
 */
fun inGame(others: List<PlayerInfo>, open: GameKind, kind: GameKind): List<PlayerInfo> =
    if (kind != open) emptyList() else others.filter { it.at == open }

/**
 * A tile as a screen reader says it while connected: "Ludo, 2 to 4 players, Maya is playing".
 * [people] is everyone in the room; [who] the names of those in this game, [playing] whether the game is under way (else they wait in it).
 */
fun tileDescription(kind: GameKind, people: Int, who: List<String>, playing: Boolean = true): String {
    val seats = if (kind.maxPlayers > 2) "2 to ${kind.maxPlayers} players" else "2 players"
    val parts = mutableListOf(kind.label, seats)
    if (!fitsRoom(kind, people)) parts += "too few seats for everyone"
    if (who.isNotEmpty()) parts += joinNames(who) + (if (who.size == 1) " is " else " are ") + (if (playing) "playing" else "waiting in it")
    return parts.joinToString(", ")
}
