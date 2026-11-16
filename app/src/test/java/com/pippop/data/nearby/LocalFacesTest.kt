package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.parseRoom
import com.pippop.game.Generator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Players' photos (cap "faces") in the room a phone hosts: one call sets your photo, each player in the state carries its
 * id, a phone fetches it once by id, and it goes with the room. The same tests are in backend/cloudflare/test/faces.test.js.
 */
class LocalFacesTest {
    private val t0 = 1_000_000L
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    private fun lobby(game: GameKind = GameKind.SUDOKU) =
        LocalRoom("ABCD", "Alice", Avatar.FOX, game, generate = { Generator.Result("000" + solution.substring(3), solution) })
            .also { it.join("Bob", Avatar.OWL, null, listOf(game.key)) }

    /** A JPEG-shaped base64 string of about [chars] characters: FF D8 FF ... FF D9. */
    private fun jpeg(chars: Int = 400, fill: Int = 0x41): String {
        val n = chars / 4 * 3
        val bytes = ByteArray(n) { fill.toByte() }
        bytes[0] = 0xFF.toByte(); bytes[1] = 0xD8.toByte(); bytes[2] = 0xFF.toByte(); bytes[3] = 0xE0.toByte()
        bytes[n - 2] = 0xFF.toByte(); bytes[n - 1] = 0xD9.toByte()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private var clock = t0
    /** Each call is a few seconds after the last, so the pace limit never gets in the way of the other tests. */
    private fun tick() = (clock + 5000).also { clock = it }

    private fun idOf(room: LocalRoom, seenBy: String) = parseRoom(room.view(seenBy, t0)).opponent!!.photoId

    @Test fun theCapIsListed() {
        assertTrue(LocalRoom.FACES in LocalRoom.CAPS)
        assertTrue("faces" in parseRoom(lobby().view(LocalRoom.HOST, t0)).caps)
    }

    @Test fun aPhotoIsSetAndEachPlayerInTheStateCarriesOnlyItsId() {
        val room = lobby()
        assertNull(idOf(room, LocalRoom.GUEST))
        val photo = jpeg()
        room.setFace(LocalRoom.HOST, photo, tick())
        val seen = parseRoom(room.view(LocalRoom.GUEST, t0))
        assertTrue((seen.opponent!!.photoId ?: 0) > 0)
        assertNull("Bob has none", seen.me.photoId)
        assertEquals(seen.opponent!!.photoId, seen.others.first().photoId)
        assertEquals(seen.opponent!!.photoId, parseRoom(room.view(LocalRoom.HOST, t0)).me.photoId)
        assertFalse("the bytes are not in the state", room.view(LocalRoom.GUEST, t0).toString().contains(photo))
        assertEquals(photo, room.face(seen.opponent!!.photoId!!))
    }

    @Test fun theSizeCapIsAbout12000Characters() {
        assertEquals(12_000, LocalRoom.MAX_FACE)
        val room = lobby()
        room.setFace(LocalRoom.HOST, jpeg(LocalRoom.MAX_FACE), tick())
        assertEquals("That photo is too big.", assertThrows(ApiException::class.java) { room.setFace(LocalRoom.GUEST, jpeg(LocalRoom.MAX_FACE + 4), tick()) }.message)
        assertThrows(ApiException::class.java) { room.setFace(LocalRoom.GUEST, jpeg(50_000), tick()) }
        assertNull("nothing was kept", idOf(room, LocalRoom.HOST))
    }

    @Test fun badDataIsRefused() {
        val room = lobby()
        val png = Base64.getEncoder().encodeToString(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4))
        val noEnd = Base64.getEncoder().encodeToString(byteArrayOf(-1, -40, -1, -32, 1, 2, 3, 4, 5, 6))
        for (bad in listOf("not base64!", "abc", "abcd====", "/9j/ 4AAQ", png, noEnd, Base64.getEncoder().encodeToString("hello world!".toByteArray()))) {
            val e = assertThrows(bad, ApiException::class.java) { room.setFace(LocalRoom.HOST, bad, tick()) }
            assertEquals("That photo is not usable.", e.message)
        }
        assertNull(idOf(room, LocalRoom.GUEST))
    }

    @Test fun aPhotoIsFetchedByItsIdAndOnlyThePhotoOfSomeoneInTheRoom() {
        val room = lobby()
        room.setFace(LocalRoom.HOST, jpeg(300, 1), tick())
        room.setFace(LocalRoom.GUEST, jpeg(300, 2), tick())
        val a = idOf(room, LocalRoom.GUEST)!!
        val b = idOf(room, LocalRoom.HOST)!!
        assertTrue(a != b)
        assertEquals(jpeg(300, 1), room.face(a))
        assertEquals(jpeg(300, 2), room.face(b))
        for (id in listOf(0, -1, 99)) assertNull(id.toString(), room.face(id))
    }

    @Test fun aNewPhotoReplacesTheOldUnderANewIdAndNullRemovesIt() {
        val room = lobby()
        room.setFace(LocalRoom.HOST, jpeg(300, 1), tick())
        val first = idOf(room, LocalRoom.GUEST)!!
        room.setFace(LocalRoom.HOST, jpeg(300, 2), tick())
        val second = idOf(room, LocalRoom.GUEST)!!
        assertTrue(second > first)
        assertNull("the old one is gone", room.face(first))
        assertEquals(jpeg(300, 2), room.face(second))
        room.setFace(LocalRoom.HOST, null, tick())
        assertNull(idOf(room, LocalRoom.GUEST))
        assertNull(room.face(second))
        room.setFace(LocalRoom.HOST, "", tick())
    }

    @Test fun aPhotoGoesWithItsPlayerAndTheRoomAndSurvivesANewGame() {
        val room = lobby()
        room.setFace(LocalRoom.HOST, jpeg(300, 1), tick())
        room.setFace(LocalRoom.GUEST, jpeg(300, 2), tick())
        val hostId = idOf(room, LocalRoom.GUEST)!!
        val guestId = idOf(room, LocalRoom.HOST)!!

        // A new game is a fresh room with the same players: their photos come along, and the ids carry on.
        val next = room.openGame(LocalRoom.HOST, GameKind.TTT)
        assertEquals(hostId, idOf(next, LocalRoom.GUEST))
        assertEquals(jpeg(300, 1), next.face(hostId))
        next.setFace(LocalRoom.HOST, jpeg(300, 3), tick())
        assertTrue(idOf(next, LocalRoom.GUEST)!! > guestId)

        next.leave(LocalRoom.GUEST)
        assertNull("Bob's photo left with him", next.face(guestId))
        val alice = parseRoom(next.view(LocalRoom.HOST, t0)).me.photoId!!
        assertEquals("Alice's stays", jpeg(300, 3), next.face(alice))
        next.leave(LocalRoom.HOST)
        assertNull("a closed room keeps none", next.face(alice))
    }

    @Test fun aSeatTakenByANewPlayerStartsWithoutThePhotoOfWhoLeft() {
        val room = lobby()
        room.setFace(LocalRoom.GUEST, jpeg(300, 2), tick())
        val id = idOf(room, LocalRoom.HOST)!!
        room.leave(LocalRoom.GUEST)
        assertNull(room.face(id))
        room.join("Cy", Avatar.CAT, null, listOf(GameKind.SUDOKU.key))
        assertNull(idOf(room, LocalRoom.HOST))
    }

    @Test fun anOldAppIsUnaffected() {
        val room = lobby(GameKind.SLIDE)
        val player = room.view(LocalRoom.HOST, t0).getJSONObject("opponent")
        // The same fields as before, plus one that is null until a photo is set (today's app ignores it).
        assertTrue(player.has("photoId") && player.isNull("photoId"))
        for (key in listOf("name", "avatar", "emoji", "ready", "difficulty", "progress", "finishMs", "left", "out", "idleMs", "online", "at", "pausedMs", "restart", "hard", "placed")) {
            assertTrue(key, player.has(key))
        }
        assertEquals("Bob", player.getString("name"))
        // Slide Race's photo has its own id and calls, and is nobody's face.
        room.setSlidePhoto(LocalRoom.HOST, "abcd")
        assertTrue((parseRoom(room.view(LocalRoom.GUEST, t0)).slide?.photoId ?: 0) > 0)
        assertNull(idOf(room, LocalRoom.GUEST))
    }

    @Test fun aPlayerMayChangeTheirPhotoOnceEveryTwoSeconds() {
        assertEquals(2000L, LocalRoom.FACE_INTERVAL_MS)
        val room = lobby()
        room.setFace(LocalRoom.HOST, jpeg(300, 1), t0)
        assertEquals("Slow down a little.", assertThrows(ApiException::class.java) { room.setFace(LocalRoom.HOST, jpeg(300, 2), t0 + 1999) }.message)
        assertThrows(ApiException::class.java) { room.setFace(LocalRoom.HOST, null, t0 + 1000) }
        room.setFace(LocalRoom.GUEST, jpeg(300, 3), t0 + 10)
        assertEquals("the refused change kept the photo", jpeg(300, 1), room.face(idOf(room, LocalRoom.GUEST)!!))
        room.setFace(LocalRoom.HOST, jpeg(300, 2), t0 + 2000)
        assertEquals(jpeg(300, 2), room.face(idOf(room, LocalRoom.GUEST)!!))
    }
}
