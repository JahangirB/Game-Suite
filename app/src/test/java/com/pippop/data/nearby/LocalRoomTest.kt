package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.Reaction
import com.pippop.data.RoomStatus
import com.pippop.data.parseRoom
import com.pippop.game.Generator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRoomTest {
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
    private val puzzle = "000" + solution.substring(3)
    private val t0 = 1_000_000L

    private fun lobby() = LocalRoom("ABCD", "Alice", Avatar.FOX, generate = { Generator.Result(puzzle, solution) })
        .also { it.join("Bob", Avatar.OWL, null) }

    private fun started() = lobby().apply {
        setReady(LocalRoom.HOST, true, Difficulty.EASY, t0)
        setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
    }

    @Test
    fun viewsParseLikeServerRooms() {
        val room = lobby()
        val guest = parseRoom(room.view(LocalRoom.GUEST, t0))
        assertEquals(RoomStatus.LOBBY, guest.status)
        assertEquals("Alice", guest.opponent!!.name)
        assertEquals(Avatar.FOX, guest.opponent!!.avatar)
        assertEquals(Avatar.OWL, guest.me.avatar)
    }

    @Test
    fun startsOnlyWhenBothPickTheSameLevel() {
        val room = lobby()
        room.setReady(LocalRoom.HOST, true, Difficulty.HARD, t0)
        room.setReady(LocalRoom.GUEST, true, Difficulty.EASY, t0)
        assertEquals("lobby", room.status)
        room.setReady(LocalRoom.GUEST, true, Difficulty.HARD, t0)
        val view = parseRoom(room.view(LocalRoom.HOST, t0))
        assertEquals(RoomStatus.COUNTDOWN, view.status)
        assertEquals(Difficulty.HARD, view.difficulty)
        assertEquals(puzzle, view.puzzle)
        assertEquals(RoomStatus.PLAYING, parseRoom(room.view(LocalRoom.HOST, t0 + 5000)).status)
    }

    @Test
    fun firstCorrectBoardWins() {
        val room = started()
        val start = room.startAt!!
        assertFalse("too early", room.submitBoard(LocalRoom.HOST, solution, t0))
        assertThrows(ApiException::class.java) { room.submitBoard(LocalRoom.HOST, "9".repeat(81), start) }
        assertTrue(room.submitBoard(LocalRoom.GUEST, solution, start + 20_000))
        val guest = parseRoom(room.view(LocalRoom.GUEST, start + 20_000))
        assertEquals(RoomStatus.FINISHED, guest.status)
        assertEquals("me", guest.winner)
        assertEquals(20_000L, guest.me.finishMs)
        assertEquals("opponent", parseRoom(room.view(LocalRoom.HOST, start + 20_000)).winner)
    }

    @Test
    fun ownProgressHiddenOpponentProgressShown() {
        val room = started()
        val start = room.startAt!!
        room.submitBoard(LocalRoom.HOST, "5" + puzzle.substring(1), start)
        assertNull(parseRoom(room.view(LocalRoom.HOST, start)).me.progress)
        assertEquals(33, parseRoom(room.view(LocalRoom.GUEST, start)).opponent!!.progress)
    }

    @Test
    fun disconnectedTooLongLoses() {
        val room = started()
        val start = room.startAt!!
        room.setConnected(LocalRoom.GUEST, false, start + 1000)
        assertFalse(room.checkTimers(start + 30_000))
        assertTrue(room.checkTimers(start + 1000 + LocalRoom.FORFEIT_MS))
        val host = parseRoom(room.view(LocalRoom.HOST, start + 60_000))
        assertEquals("me", host.winner)
        assertEquals("timeout", host.endReason)
        assertEquals(false, host.opponent!!.online)
    }

    @Test
    fun reactionsAndReconnect() {
        val room = lobby()
        val guestId = room.playerId(LocalRoom.GUEST)
        room.sendEmoji(LocalRoom.GUEST, Reaction.FIRE, t0)
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.GUEST, Reaction.CLAP, t0 + 10) }
        val host = parseRoom(room.view(LocalRoom.HOST, t0))
        assertEquals(Reaction.FIRE, host.opponent!!.emoji!!.reaction)
        assertEquals(LocalRoom.GUEST, room.join("Bob", null, guestId))
        // Sudoku takes four.
        assertEquals(LocalRoom.P3, room.join("Eve", null, null))
        assertEquals(LocalRoom.P4, room.join("Fay", null, null))
        assertThrows(ApiException::class.java) { room.join("Gus", null, null) }
    }

    @Test
    fun kylianDictadorHasTheFloorUntilHisSongEnds() {
        val room = lobby()
        room.sendEmoji(LocalRoom.HOST, Reaction.KYLIAN, t0)
        // Not the other player, not the sender, not even another Kylian.
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.GUEST, Reaction.LAUGH, t0 + 5_000) }
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.HOST, Reaction.KYLIAN, t0 + Reaction.KYLIAN.takesOverMs - 1) }
        room.sendEmoji(LocalRoom.GUEST, Reaction.LAUGH, t0 + Reaction.KYLIAN.takesOverMs)
        assertEquals(Reaction.LAUGH, parseRoom(room.view(LocalRoom.HOST, t0)).opponent!!.emoji!!.reaction)
    }

    @Test
    fun fourPlayersShareARoundAndDropOutOneByOne() {
        val room = lobby()
        room.join("Cat", null, null)
        room.join("Dan", null, null)
        for (role in LocalRoom.ROLES.take(3)) room.setReady(role, true, Difficulty.EASY, t0)
        assertEquals("lobby", room.status)
        room.setReady(LocalRoom.P4, true, Difficulty.EASY, t0)
        assertEquals("started", room.status)
        val cat = parseRoom(room.view(LocalRoom.P3, t0))
        assertEquals(listOf("Dan", "Alice", "Bob"), cat.others.map { it.name })
        assertEquals(4, cat.maxPlayers)
        room.leave(LocalRoom.P4, t0)
        assertEquals("started", room.status)
        room.leave(LocalRoom.GUEST, t0)
        room.leave(LocalRoom.P3, t0)
        assertEquals("finished", room.status)
        assertEquals("Alice", parseRoom(room.view(LocalRoom.P3, t0)).winnerName)
    }

    @Test
    fun hostLeavingClosesTheRoom() {
        val room = started()
        room.leave(LocalRoom.HOST)
        assertEquals("closed", room.status)
        val e = assertThrows(ApiException::class.java) { room.join("Bob", null, null) }
        assertTrue(e.fatal)
    }
}

class LocalShipsTest {
    private val fleet = listOf(
        com.pippop.game.ships.Ship(0, 0, 5, true), com.pippop.game.ships.Ship(0, 2, 4, true),
        com.pippop.game.ships.Ship(0, 4, 3, true), com.pippop.game.ships.Ship(0, 6, 3, true),
        com.pippop.game.ships.Ship(0, 8, 2, true),
    )

    @Test
    fun shipBattleOverANearbyRoom() {
        val room = LocalRoom("SEA1", "Ann", null, com.pippop.data.GameKind.SHIPS)
        room.join("Ben", null, null)
        room.setShipsReady(LocalRoom.HOST, true, fleet, 0, kotlin.random.Random(1))
        assertEquals("lobby", room.status)
        room.setShipsReady(LocalRoom.GUEST, true, fleet, 0, kotlin.random.Random(1))
        val host = parseRoom(room.view(LocalRoom.HOST, 0))
        assertEquals(com.pippop.data.GameKind.SHIPS, host.game)
        assertEquals(RoomStatus.PLAYING, host.status)
        val shooter = if (host.ships!!.turn == "me") LocalRoom.HOST else LocalRoom.GUEST
        room.fire(shooter, 0, 8, 0)
        room.fire(shooter, 1, 8, 0)
        val view = parseRoom(room.view(shooter, 0)).ships!!
        assertEquals(1, view.sunkEnemy.size)
        assertEquals("me", view.turn)
        assertThrows(ApiException::class.java) { room.fire(LocalRoom.other(shooter), 0, 0, 0) }
        assertEquals(2, parseRoom(room.view(LocalRoom.other(shooter), 0)).ships!!.theirShots.size)
    }

    private val T0 = 1_000_000L

    @Test
    fun numberCrossHostSendsThePuzzle() {
        val room = LocalRoom("NUM1", "Ann", null, com.pippop.data.GameKind.NUMBERS)
        val guest = room.join("Bob", null, null, listOf("numbers"))
        room.setOptions(LocalRoom.HOST, org.json.JSONObject().put("level", "hard"))
        val puzzle = com.pippop.game.numbers.NumberCrossGenerator(kotlin.random.Random(1))
            .generate(com.pippop.game.numbers.NumberLevel.HARD)
        room.setNumbersReady(guest, true, null, T0)
        room.setNumbersReady(LocalRoom.HOST, true, puzzle, T0)
        val now = T0 + 10_000
        val view = parseRoom(room.view(guest, now))
        assertEquals(com.pippop.game.numbers.NumberLevel.HARD, view.numbers!!.level)
        val board = view.numbers!!.board!!
        assertEquals(puzzle.bank, board.bank)
        assertTrue(board.blanks.all { board.cells[it] == "?" })
        room.submitNumbers(guest, puzzle.blanks.map { puzzle.grid[it] }, now)
        val end = parseRoom(room.view(LocalRoom.HOST, now))
        assertEquals(RoomStatus.FINISHED, end.status)
        assertEquals("opponent", end.winner)
        assertEquals("solved", end.endReason)
    }

    @Test
    fun numberCrossPuzzleIsPushedWhenTheCountdownEnds() {
        val room = LocalRoom("NUM2", "Ann", null, com.pippop.data.GameKind.NUMBERS)
        val guest = room.join("Bob", null, null, listOf("numbers"))
        val puzzle = com.pippop.game.numbers.NumberCrossGenerator(kotlin.random.Random(1))
            .generate(com.pippop.game.numbers.NumberLevel.EASY)
        room.setNumbersReady(guest, true, null, T0)
        room.setNumbersReady(LocalRoom.HOST, true, puzzle, T0)
        val start = room.startAt!!
        // Hidden during the countdown, so the guest needs a fresh view when it ends.
        assertNull(parseRoom(room.view(guest, start - 1)).numbers!!.board)
        assertFalse(room.checkTimers(start - 1))
        assertTrue(room.checkTimers(start))
        // Only once per round.
        assertFalse(room.checkTimers(start + 1))
    }

    @Test
    fun dominoesTeamsWinTogether() {
        val room = LocalRoom("DOM1", "Ann", null, com.pippop.data.GameKind.DOMINOES)
        val guests = listOf("Bob", "Cy", "Di").map { room.join(it, null, null, listOf("dominoes")) }
        val roles = listOf(LocalRoom.HOST) + guests
        assertThrows(ApiException::class.java) {
            room.setOptions(guests[0], com.pippop.game.dominoes.DominoOptions(teams = true).toJson())
        }
        room.setOptions(LocalRoom.HOST, com.pippop.game.dominoes.DominoOptions(teams = true, target = 50).toJson())
        assertEquals(true, parseRoom(room.view(guests[1], T0)).options?.teams)
        for (r in roles) room.setTurnReady(r, true, T0)
        var now = T0 + 10_000
        repeat(5000) {
            val view = roles.associateWith { parseRoom(room.view(it, now)) }
            if (view.values.first().status == RoomStatus.FINISHED) return@repeat
            val (role, state) = view.entries.first { it.value.turns?.turn == "me" }
            val d = state.turns!!.dominoes!!
            val tile = d.hand.indexOfFirst { com.pippop.game.dominoes.Dominoes.fits(d.line, it).isNotEmpty() }
            val move = when {
                tile >= 0 -> org.json.JSONObject().put("tile", tile)
                d.draw && d.boneyard > 0 -> org.json.JSONObject().put("draw", true)
                else -> org.json.JSONObject().put("pass", true)
            }
            room.move(role, move, now)
            now += 10
        }
        val host = parseRoom(room.view(LocalRoom.HOST, now))
        val partner = parseRoom(room.view(guests[1], now))
        assertEquals(RoomStatus.FINISHED, host.status)
        assertEquals("points", host.endReason)
        // Ann and Cy sit opposite: they win or lose together.
        assertEquals(host.winner, partner.winner)
        assertTrue(host.winnerName!!.contains(" & "))
    }
}
