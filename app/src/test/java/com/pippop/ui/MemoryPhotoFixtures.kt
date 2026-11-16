package com.pippop.ui

import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayerInfo
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.TurnsView

/** A Memory Pairs room (Sam, the opener, and Maya) for the step 30 tests; the photos' ids and the token are what each test sets. */
fun roomForTest(
    game: GameKind = GameKind.MEMORY,
    caps: List<String> = listOf("memory-photos"),
    memoryPhotos: Boolean = true,
    memoryDeckPhotos: Boolean = true,
    memoryPhotoIds: List<Int?> = List(8) { null },
    memoryCards: Int = 16,
    status: RoomStatus = RoomStatus.LOBBY,
    opener: Boolean = true,
    turns: TurnsView? = null,
    mayaReady: Boolean = true,
): RoomState {
    fun player(name: String, avatar: Avatar, ready: Boolean) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = null,
        progress = null, finishMs = null, left = false, idleMs = 0, online = true, at = game,
    )
    val sam = player("Sam", Avatar.FOX, false)
    val maya = player("Maya", Avatar.PANDA, mayaReady)
    return RoomState(
        code = "K7Q2NB", role = if (opener) "host" else "guest", game = game, status = status, difficulty = null, round = 0,
        startAt = null, puzzle = null, solution = null, winner = null, endReason = null,
        me = if (opener) sam else maya, opponent = if (opener) maya else sam, ships = null, turns = turns, serverTime = 0,
        others = listOf(if (opener) maya else sam),
        maxPlayers = 4, setsOptions = opener, inSession = true, memoryCards = memoryCards,
        caps = caps, memoryPhotos = memoryPhotos, memoryDeckPhotos = memoryDeckPhotos, memoryPhotoIds = memoryPhotoIds,
    )
}
