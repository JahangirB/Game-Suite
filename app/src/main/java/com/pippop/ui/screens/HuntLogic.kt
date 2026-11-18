package com.pippop.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pippop.data.Avatar
import com.pippop.data.RoomState
import com.pippop.game.words.HuntView
import com.pippop.game.words.HuntViewWord
import com.pippop.ui.art.Outcome
import com.pippop.ui.theme.SeatColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Word Hunt's screens ask these small questions: who is whose mark, which cells a drag means, where the
 * grid sits in its window when zoomed, and how a round ended. None of it draws anything, so tests can
 * check it without a screen. The rules themselves are in game/words/HuntMatch.kt.
 */

/** One square of the grid, counted from the top left. */
internal data class HuntCell(val row: Int, val col: Int)

/**
 * Someone in the round: their [mark] in the room's view, their [name] ("You" for this phone), the one-character [initial]
 * that tags their words (never by colour alone) and their [color].
 */
internal class HuntSeat(val mark: Char, val name: String, val initial: String, val color: Color, val mine: Boolean, val avatar: Avatar?)

/**
 * Everyone in the round in the order of the view's marks. The room lists the others after you, in seat order, wrapping round, and
 * the marks are in seat order too, so the others follow your own mark. Two names that start alike get the seat's number instead.
 */
internal fun huntSeats(room: RoomState, view: HuntView, myColor: Color): List<HuntSeat> {
    val marks = view.marks.ifEmpty { listOf(view.me) }
    val at = marks.indexOf(view.me).coerceAtLeast(0)
    val after = marks.drop(at + 1) + marks.take(at)
    val used = HashSet<String>()
    fun initialOf(name: String, number: Int): String {
        val first = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: number.toString()
        return if (used.add(first)) first else number.toString().also { used.add(it) }
    }
    val me = HuntSeat(view.me, "You", initialOf("You", 1), myColor, true, room.me.avatar)
    val others = after.mapIndexed { i, mark ->
        val player = room.others.getOrNull(i)
        val name = player?.name ?: "Player ${i + 2}"
        HuntSeat(mark, name, initialOf(name, i + 2), SeatColors[i % SeatColors.size], false, player?.avatar)
    }
    return listOf(me) + others
}

internal fun List<HuntSeat>.of(mark: Char?) = firstOrNull { it.mark == mark }

// ---- Selecting ------------------------------------------------------------------------------------------

/**
 * Where a drag from [start] ends when the finger is at ([fx], [fy]) in cell units (column and row as fractions): the highlight
 * snaps to the row or column of the drag's main axis, so a shaky diagonal still means a line. A tie goes across.
 */
internal fun huntSnap(start: HuntCell, fx: Float, fy: Float, width: Int, height: Int): HuntCell {
    val dx = fx - (start.col + 0.5f)
    val dy = fy - (start.row + 0.5f)
    return if (abs(dx) >= abs(dy)) HuntCell(start.row, floor(fx).toInt().coerceIn(0, width - 1))
    else HuntCell(floor(fy).toInt().coerceIn(0, height - 1), start.col)
}

/** Whether a finger at ([fx], [fy]) in cell units is on the grid or within one cell of it (so edge-scrolling still works). */
internal fun huntNear(fx: Float, fy: Float, width: Int, height: Int) = fx >= -1f && fx <= width + 1f && fy >= -1f && fy <= height + 1f

/** The cells from [a] to [b] along their row or column, ends included. */
internal fun huntLine(a: HuntCell, b: HuntCell): List<HuntCell> {
    if (a.row != b.row && a.col != b.col) return listOf(a)
    val steps = max(abs(b.row - a.row), abs(b.col - a.col))
    val dr = Integer.signum(b.row - a.row)
    val dc = Integer.signum(b.col - a.col)
    return List(steps + 1) { HuntCell(a.row + dr * it, a.col + dc * it) }
}

// ---- The grid in its window ------------------------------------------------------------------------------

/** How the grid sits in the window: zoomed by [zoom] (1 shows it all), with its top left at ([x], [y]) in window pixels. */
internal data class HuntCamera(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/** A cell is never drawn bigger than this many times the size that gives 48 dp. */
private const val MAX_COMFORT = 1.5f

/**
 * The window ([vw] by [vh] px) and the grid ([width] by [height] cells) with the camera on it. [fit] is the cell size that shows the
 * whole grid; [comfort] is the zoom that makes a cell [touchPx] (48 dp) wide, or 1 when the grid already shows that big.
 * Grids no wider than 10 do not zoom ([maxZoom] is 1).
 */
internal class HuntGeometry(val vw: Float, val vh: Float, val width: Int, val height: Int, touchPx: Float) {
    val fit = if (width > 0 && height > 0) min(vw / width, vh / height) else 0f
    val canZoom = width > 10
    val comfort = if (canZoom && fit > 0f) max(1f, touchPx / fit) else 1f
    val maxZoom = if (canZoom) max(2f, comfort * MAX_COMFORT) else 1f

    /** The camera kept inside what is allowed: the zoom within its limits, the grid centred when smaller than the window, else not past an edge. */
    fun clamp(cam: HuntCamera): HuntCamera {
        val zoom = cam.zoom.coerceIn(1f, maxZoom)
        val cell = fit * zoom
        val cw = cell * width
        val ch = cell * height
        val x = if (cw <= vw) (vw - cw) / 2f else cam.x.coerceIn(vw - cw, 0f)
        val y = if (ch <= vh) (vh - ch) / 2f else cam.y.coerceIn(vh - ch, 0f)
        return HuntCamera(zoom, x, y)
    }

    fun cell(cam: HuntCamera) = fit * cam.zoom

    /** A window point as a place in the grid, in cell units. */
    fun toGrid(cam: HuntCamera, p: Offset): Offset = cell(cam).let { Offset((p.x - cam.x) / it, (p.y - cam.y) / it) }

    /** The cell under a window point, or null outside the grid. */
    fun cellAt(cam: HuntCamera, p: Offset): HuntCell? {
        val g = toGrid(cam, p)
        val c = floor(g.x).toInt()
        val r = floor(g.y).toInt()
        return if (c in 0 until width && r in 0 until height) HuntCell(r, c) else null
    }

    /** The middle of a cell in window pixels. */
    fun centreOf(cam: HuntCamera, cell: HuntCell): Offset = cell(cam).let { Offset(cam.x + (cell.col + 0.5f) * it, cam.y + (cell.row + 0.5f) * it) }

    /** [cam] after a pinch and pan: [factor] times the zoom about the fingers' middle [focus], which also moved by [pan]. */
    fun transform(cam: HuntCamera, focus: Offset, pan: Offset, factor: Float): HuntCamera {
        val now = clamp(cam)
        val zoom = (now.zoom * factor).coerceIn(1f, maxZoom)
        val k = zoom / now.zoom
        val moved = focus + pan
        return clamp(HuntCamera(zoom, moved.x - (focus.x - now.x) * k, moved.y - (focus.y - now.y) * k))
    }

    /** [cam] zoomed to [zoom] about the window's middle (the + and - buttons). */
    fun zoomTo(cam: HuntCamera, zoom: Float) = transform(cam, Offset(vw / 2f, vh / 2f), Offset.Zero, zoom / clamp(cam).zoom)

    /** [cam] moved so that [cell] is in the middle of the window, where the zoom allows it. */
    fun centreOn(cam: HuntCamera, cell: HuntCell): HuntCamera {
        val now = clamp(cam)
        val size = cell(now)
        return clamp(now.copy(x = vw / 2f - (cell.col + 0.5f) * size, y = vh / 2f - (cell.row + 0.5f) * size))
    }

    /** The part of the grid in the window, in cell units: left, top, right, bottom (for the small map). */
    fun seen(cam: HuntCamera): List<Float> {
        val now = clamp(cam)
        val size = cell(now)
        return listOf(
            (-now.x / size).coerceIn(0f, width.toFloat()), (-now.y / size).coerceIn(0f, height.toFloat()),
            ((vw - now.x) / size).coerceIn(0f, width.toFloat()), ((vh - now.y) / size).coerceIn(0f, height.toFloat()),
        )
    }

    /** One step of the + button: to the zoom that makes cells 48 dp first, then bigger. */
    fun zoomInStep(zoom: Float) = if (zoom < comfort - 0.01f) comfort else min(maxZoom, zoom * 1.35f)

    /** One step of the - button; close to the whole grid it shows all of it. */
    fun zoomOutStep(zoom: Float) = (zoom / 1.35f).let { if (it < 1.1f) 1f else it }
}

// ---- What happened -------------------------------------------------------------------------------------

/** A word that was found since the screen last looked: [word] by [mark]. */
internal class HuntMoment(val word: String, val mark: Char)

/** The words in [view] that are found and were not in [known] (the words the screen has already shown). */
internal fun huntNewFinds(known: Set<String>, view: HuntView): List<HuntMoment> =
    view.words.filter { it.foundBy != null && it.word !in known }.map { HuntMoment(it.word, it.foundBy!!) }

/** What the screen says for the moment: "Maya found TIGER", "You found TIGER". */
internal fun huntMomentText(moment: HuntMoment, seats: List<HuntSeat>) = "${seats.of(moment.mark)?.name ?: "Someone"} found ${moment.word}"

/** The room's refusal in the screen's words: it names a mark ("Already found by g"); the screen names the player. */
internal fun huntRefusalText(message: String, seats: List<HuntSeat>): String = when {
    message.startsWith("Already found by ") -> "Already found by " + message.removePrefix("Already found by ").trim().firstOrNull().let { m -> seats.of(m)?.name?.let { if (it == "You") "you" else it } ?: "someone" }
    message == "Too late" -> "Time's up"
    else -> message
}

/** The timer as m:ss, or null when the round has none. */
internal fun huntClock(view: HuntView, serverNow: Long): String? {
    if (view.timer == 0 || view.endsAt == 0L) return null
    // Before play starts the clock holds the full time (the countdown is not part of it).
    val s = (minOf(view.endsAt - serverNow, view.timer * 60_000L) + 999).div(1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Whether the last ten seconds are running. */
internal fun huntHurry(view: HuntView, serverNow: Long) = view.timer > 0 && view.endsAt > 0 && view.endsAt - serverNow in 1..10_000

/** The words each player found, longest first. */
internal fun huntFinds(view: HuntView, mark: Char): List<HuntViewWord> =
    view.words.filter { it.foundBy == mark }.sortedWith(compareBy({ -it.length }, { it.word }))

/** "1st", "2nd"... for a place. */
internal fun huntPlace(rank: Int) = when (rank) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${rank}th" }

/** How a finished round is told: this phone's [outcome], the card's [title] and its [detail] line. */
internal class HuntEnd(val outcome: Outcome, val title: String, val detail: String)

/** "You", "You and Maya", "You, Maya and Priya". */
private fun names(list: List<String>) = if (list.size <= 1) list.joinToString() else list.dropLast(1).joinToString(", ") + " and " + list.last()

/**
 * The end of the round for this phone. A tie for the top score is a draw. When players left, [roomWinner] ("me", "opponent" or null)
 * and [endReason] from the room say who is left, and the scores that were reached stay in the results.
 */
internal fun huntEnd(view: HuntView, seats: List<HuntSeat>, roomWinner: String?, endReason: String?, winnerName: String?): HuntEnd {
    val total = view.words.size
    val found = view.words.count { it.foundBy != null }
    val tied = view.winners.size > 1
    val me = seats.firstOrNull { it.mine }
    val iWon = if (endReason == "forfeit" || endReason == "timeout") roomWinner == "me" else me != null && me.mark in view.winners
    val line = when {
        endReason == "forfeit" -> if (roomWinner == "me") "Everyone else left the game." else "You left the game."
        endReason == "timeout" -> if (roomWinner == "me") "The other player lost connection." else "You were disconnected for too long."
        view.reason == "all" -> "All $total words found."
        else -> "Time's up. $found of $total words found."
    }
    if (endReason != "forfeit" && endReason != "timeout" && tied) {
        val tops = view.winners.mapNotNull { seats.of(it)?.name }
        return HuntEnd(
            if (iWon) Outcome.TIE else Outcome.LOSE, "It's a draw",
            "${names(tops)} ${if (tops.size == 2) "both" else "all"} have ${view.scores[view.winners.first()] ?: 0}. $line",
        )
    }
    val title = when {
        iWon -> "You win!"
        else -> "${winnerName ?: view.winner?.let { seats.of(it)?.name } ?: "Someone"} wins"
    }
    return HuntEnd(if (iWon) Outcome.WIN else Outcome.LOSE, title, line)
}

/** The longest word found, with who found it, or null when nothing was found. */
internal fun huntLongest(view: HuntView, seats: List<HuntSeat>): String? =
    view.words.filter { it.foundBy != null }.sortedWith(compareBy({ -it.length }, { it.word })).firstOrNull()?.let { "${it.word} (${seats.of(it.foundBy)?.name ?: "someone"})" }
