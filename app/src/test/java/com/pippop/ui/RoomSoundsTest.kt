package com.pippop.ui

import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.Loudness
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `player_join`, `player_leave` and `ready`: who they play for, and what they ignore (step 31). */
class RoomSoundsTest {
    private fun player(name: String, ready: Boolean = false, left: Boolean = false, online: Boolean? = true, restart: Boolean = false, id: String? = null) = PlayerInfo(
        name = name, avatar = Avatar.FOX, deviceId = id, emoji = null, ready = ready, difficulty = null, progress = null, finishMs = null,
        left = left, idleMs = 0, online = online, wantsRestart = restart,
    )

    private fun room(vararg others: PlayerInfo, code: String = "K7Q2NB") = RoomState(
        code = code, role = "host", game = GameKind.CONNECT4, status = RoomStatus.LOBBY, difficulty = null, round = 0, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = player("Me", ready = true), opponent = others.firstOrNull(),
        ships = null, turns = null, serverTime = 0, others = others.toList(), maxPlayers = 4,
    )

    private val maya = player("Maya")
    private val sam = player("Sam")

    @Test fun nothingPlaysForTheFirstStateOfARoom() {
        assertTrue(roomSounds(null, room(maya)).isEmpty())
        assertTrue(roomSounds(room(maya, code = "AAAAAA"), room(maya, sam, code = "BBBBBB")).isEmpty())
    }

    @Test fun someoneElseJoining() {
        assertEquals(listOf(Sound.PLAYER_JOIN), roomSounds(room(maya), room(maya, sam)))
        assertEquals(listOf(Sound.PLAYER_JOIN), roomSounds(room(), room(maya)))
    }

    @Test fun someoneElseLeavingByLeftOrByGoingFromTheList() {
        assertEquals(listOf(Sound.PLAYER_LEAVE), roomSounds(room(maya, sam), room(maya, sam.copy(left = true))))
        assertEquals(listOf(Sound.PLAYER_LEAVE), roomSounds(room(maya, sam), room(maya)))
    }

    @Test fun aReconnectBlipIsSilent() {
        // They drop (online false, paused) and come back: still in the room throughout.
        val dropped = maya.copy(online = false, out = true, pausedMs = 20_000)
        assertTrue(roomSounds(room(maya), room(dropped)).isEmpty())
        assertTrue(roomSounds(room(dropped), room(maya)).isEmpty())
    }

    @Test fun readyAndRematchFromSomeoneElse() {
        assertEquals(listOf(Sound.READY), roomSounds(room(maya), room(maya.copy(ready = true))))
        assertEquals(listOf(Sound.READY), roomSounds(room(maya), room(maya.copy(wantsRestart = true))))
        // Already ready, ready no more, a different player's change: nothing new.
        assertTrue(roomSounds(room(maya.copy(ready = true)), room(maya.copy(ready = true))).isEmpty())
        assertTrue(roomSounds(room(maya.copy(ready = true)), room(maya)).isEmpty())
    }

    @Test fun aPlayerWhoJoinsAlreadyReadyMakesOnlyTheJoinSound() {
        assertEquals(listOf(Sound.PLAYER_JOIN), roomSounds(room(maya), room(maya, sam.copy(ready = true))))
    }

    @Test fun yourOwnReadyIsNeverHeard() {
        val before = room(maya)
        val after = before.copy(me = before.me.copy(ready = false, wantsRestart = true))
        assertTrue(roomSounds(before, after).isEmpty())
    }

    @Test fun playersAreToldApartByTheirPhoneWhereThereIsOne() {
        val a = player("Kim", id = "d1")
        val b = player("Kim", id = "d2")
        assertEquals(listOf(Sound.PLAYER_JOIN), roomSounds(room(a), room(a, b)))
    }

    @Test fun eachSoundIsOneOfOthersAndFollowsTheirMovesAndOff() {
        for (sound in listOf(Sound.PLAYER_JOIN, Sound.PLAYER_LEAVE, Sound.READY, Sound.LINE_SENT)) {
            val played = mutableListOf<Pair<Sound, Float>>()
            fun sfx(p: Profile) = Sfx({ p }, { s, level, _ -> played += s to level })
            sfx(Profile(othersMoves = false)).play(sound, Sfx.OTHERS, others = true)
            sfx(Profile(loudness = Loudness.OFF)).play(sound, Sfx.OTHERS, others = true)
            assertTrue("$sound", played.isEmpty())
            sfx(Profile()).play(sound, Sfx.OTHERS, others = true)
            assertEquals(sound.volume * Sfx.OTHERS, played.single().second, 1e-6f)
        }
    }
}
