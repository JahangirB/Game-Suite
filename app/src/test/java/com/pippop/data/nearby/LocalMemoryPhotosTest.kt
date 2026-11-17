package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.parseRoom
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Memory Pairs with the players' own photos (cap and token "memory-photos") in the room a phone hosts: the opener sets one
 * photo per pair by one call each, each player's state carries only their ids, a phone fetches each once, and they go with the
 * room. The same tests are in backend/cloudflare/test/memorypics.test.js.
 */
class LocalMemoryPhotosTest {
    private val t0 = 1_000_000L
    private val newApp = listOf("memory", "memory-photos")
    private val oldApp = listOf("memory")

    private fun lobby(guest: List<String> = newApp) =
        LocalRoom("ABCD", "Alice", Avatar.FOX, GameKind.MEMORY).also { it.join("Bob", Avatar.OWL, null, guest) }

    private fun photosLobby(cards: Int = 16) = lobby().also { it.setOptions(LocalRoom.HOST, JSONObject().put("cards", cards).put("deck", "photos")) }

    /** A JPEG-shaped base64 string of about [chars] characters: FF D8 FF ... FF D9. */
    private fun jpeg(chars: Int = 400, fill: Int = 0x41): String {
        val n = chars / 4 * 3
        val bytes = ByteArray(n) { fill.toByte() }
        bytes[0] = 0xFF.toByte(); bytes[1] = 0xD8.toByte(); bytes[2] = 0xFF.toByte(); bytes[3] = 0xE0.toByte()
        bytes[n - 2] = 0xFF.toByte(); bytes[n - 1] = 0xD9.toByte()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private var clock = t0
    /** Each call is a little after the last, so the pace limit never gets in the way of the other tests. */
    private fun tick() = (clock + 1000).also { clock = it }

    private fun state(room: LocalRoom, role: String = LocalRoom.GUEST) = parseRoom(room.view(role, t0))
    private fun ids(room: LocalRoom) = state(room).memoryPhotoIds

    private fun fill(room: LocalRoom, n: Int, fill: Int = 1) {
        for (i in 0 until n) room.setMemoryPic(LocalRoom.HOST, i, jpeg(300, fill + i), tick())
    }

    private fun failure(block: () -> Unit) = assertThrows(ApiException::class.java) { block() }.message

    @Test fun theCapIsListedAndTheStateSaysWhetherEveryoneHasTheToken() {
        assertTrue(LocalRoom.MEMORY_PHOTOS in LocalRoom.CAPS)
        val v = state(lobby())
        assertTrue("memory-photos" in v.caps)
        assertTrue(v.memoryPhotos)
    }

    @Test fun theSizeCapIs16000CharactersAndTheCountCapTheBiggestDeck() {
        assertEquals(16_000, LocalRoom.MAX_MEMORY_PIC)
        assertEquals(18, com.pippop.data.MemoryPhotos.MAX_PAIRS)
        val room = photosLobby()
        room.setMemoryPic(LocalRoom.HOST, 0, jpeg(LocalRoom.MAX_MEMORY_PIC), tick())
        assertEquals("That photo is too big.", failure { room.setMemoryPic(LocalRoom.HOST, 1, jpeg(LocalRoom.MAX_MEMORY_PIC + 4), tick()) })
        assertEquals("That photo is too big.", failure { room.setMemoryPic(LocalRoom.HOST, 1, jpeg(60_000), tick()) })
        assertEquals("nothing else was kept", 1, ids(room).count { it != null })
        // 16 cards are 8 pairs: places 0 to 7.
        room.setMemoryPic(LocalRoom.HOST, 7, jpeg(300), tick())
        for (slot in listOf(8, 17, 18, -1, null)) assertEquals("That deck has no such place.", failure { room.setMemoryPic(LocalRoom.HOST, slot, jpeg(300), tick()) })
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 36).put("deck", "photos"))
        room.setMemoryPic(LocalRoom.HOST, 17, jpeg(300), tick())
        assertEquals("That deck has no such place.", failure { room.setMemoryPic(LocalRoom.HOST, 18, jpeg(300), tick()) })
    }

    @Test fun badDataIsRefused() {
        val room = photosLobby()
        val png = Base64.getEncoder().encodeToString(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4))
        for (bad in listOf("not base64!", "abc", "abcd====", png, Base64.getEncoder().encodeToString("hello world!".toByteArray()))) {
            assertEquals(bad, "That photo is not usable.", failure { room.setMemoryPic(LocalRoom.HOST, 0, bad, tick()) })
        }
        assertEquals(List(8) { null }, ids(room))
    }

    @Test fun theOpenerSetsThePhotosTheStateCarriesOnlyIdsPerPairAndTheyAreFetchedById() {
        val room = photosLobby()
        assertEquals(List(8) { null }, ids(room))
        val photo = jpeg(300, 9)
        room.setMemoryPic(LocalRoom.HOST, 2, photo, tick())
        room.setMemoryPic(LocalRoom.HOST, 5, jpeg(300, 8), tick())
        val v = state(room)
        assertTrue(v.memoryDeckPhotos)
        assertEquals(8, v.memoryPhotoIds.size)
        val (a, b) = v.memoryPhotoIds[2]!! to v.memoryPhotoIds[5]!!
        assertTrue(a > 0 && b > a)
        assertEquals(2, v.memoryPhotoIds.count { it != null })
        assertFalse("the bytes are not in the state", room.view(LocalRoom.GUEST, t0).toString().contains(photo))
        assertEquals(photo, room.memoryPic(a))
        for (id in listOf(0, -1, 99)) assertNull(id.toString(), room.memoryPic(id))
    }

    @Test fun onlyTheOpenerSetsPhotosAndOnlyBetweenMatches() {
        val room = photosLobby()
        assertEquals("Only whoever opened the game can pick the photos.", failure { room.setMemoryPic(LocalRoom.GUEST, 0, jpeg(), tick()) })
        fill(room, 8)
        room.setTurnReady(LocalRoom.GUEST, true, t0)
        room.setTurnReady(LocalRoom.HOST, true, t0)
        assertEquals("started", room.status)
        assertEquals("The photos can only change between games.", failure { room.setMemoryPic(LocalRoom.HOST, 0, jpeg(), tick()) })
    }

    @Test fun callsArePaced() {
        assertEquals(150L, LocalRoom.MEMORY_PIC_INTERVAL_MS)
        val room = photosLobby()
        val t = tick()
        room.setMemoryPic(LocalRoom.HOST, 0, jpeg(300, 1), t)
        assertEquals("Slow down a little.", failure { room.setMemoryPic(LocalRoom.HOST, 1, jpeg(300, 2), t + LocalRoom.MEMORY_PIC_INTERVAL_MS - 1) })
        assertEquals("the refused one kept nothing", 1, ids(room).count { it != null })
        room.setMemoryPic(LocalRoom.HOST, 1, jpeg(300, 2), t + LocalRoom.MEMORY_PIC_INTERVAL_MS)
        assertEquals(2, ids(room).count { it != null })
    }

    @Test fun aNewPhotoForAPlaceReplacesTheOldUnderANewIdNullEmptiesItAndBothTakeReadyBack() {
        val room = photosLobby()
        fill(room, 8)
        val first = ids(room)[0]!!
        room.setTurnReady(LocalRoom.GUEST, true, t0)
        assertTrue(room.players[LocalRoom.GUEST]!!.ready)
        room.setMemoryPic(LocalRoom.HOST, 0, jpeg(300, 77), tick())
        assertFalse("a changed photo takes Ready back", room.players[LocalRoom.GUEST]!!.ready)
        val second = ids(room)[0]!!
        assertTrue(second > first)
        assertNull("the old one is gone", room.memoryPic(first))
        assertEquals(jpeg(300, 77), room.memoryPic(second))
        room.setMemoryPic(LocalRoom.HOST, 0, null, tick())
        assertNull(ids(room)[0])
        assertNull(room.memoryPic(second))
    }

    @Test fun theGameCannotStartUntilEveryPlaceHasItsPhotoThenTheCardsArePairsOfPN() {
        val room = photosLobby()
        fill(room, 5)
        assertEquals("Choose 3 more photos to start.", failure { room.setTurnReady(LocalRoom.HOST, true, t0) })
        fill(room, 7)
        assertEquals("Choose 1 more photo to start.", failure { room.setTurnReady(LocalRoom.HOST, true, t0) })
        fill(room, 8)
        room.setTurnReady(LocalRoom.GUEST, true, t0)
        room.setTurnReady(LocalRoom.HOST, true, t0)
        assertEquals("started", room.status)
        val mover = if (parseRoom(room.view(LocalRoom.HOST, t0 + 10_000)).turns!!.turn == "me") LocalRoom.HOST else LocalRoom.GUEST
        room.move(mover, JSONObject().put("card", 0), t0 + 10_000)
        val face = room.view(mover, t0 + 10_000).getJSONObject("turns").getJSONArray("faces").getString(0)
        assertTrue(face, Regex("p[0-7]").matches(face))
        assertTrue("the ids stay in the state while playing", state(room).memoryPhotoIds.all { it != null })
    }

    @Test fun anotherDeckOrSizeTakesThePhotosAwayAndTheSameChoiceKeepsThem() {
        val room = photosLobby()
        fill(room, 8)
        val id = ids(room)[0]!!
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 16).put("deck", "photos"))
        assertEquals(8, ids(room).count { it != null })
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 24).put("deck", "photos"))
        assertEquals(List(12) { null }, ids(room))
        assertNull(room.memoryPic(id))
        assertEquals("the stored photos went too", 0, room.memoryPicsKept)
        fill(room, 12)
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 24).put("deck", "animals"))
        assertEquals(JSONObject().put("cards", 24).put("deck", "animals").toString(), room.view(LocalRoom.HOST, t0).getJSONObject("options").toString())
        assertTrue("the animals deck keeps no photos", failure { room.setMemoryPic(LocalRoom.HOST, 0, jpeg(), tick()) }!!.contains("latest"))
        // A call that leaves the deck out keeps it.
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 24).put("deck", "photos"))
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 24))
        assertTrue(state(room).memoryDeckPhotos)
    }

    @Test fun photosGoWithTheRoomAnotherGameAndAClosedRoom() {
        val room = photosLobby()
        fill(room, 8)
        val id = ids(room)[0]!!
        val next = room.openGame(LocalRoom.HOST, GameKind.TTT)
        assertNull("another game", next.memoryPic(id))
        val again = photosLobby()
        fill(again, 8)
        val gone = ids(again)[0]!!
        again.leave(LocalRoom.GUEST)
        again.leave(LocalRoom.HOST)
        assertEquals("closed", again.status)
        assertNull("a closed room keeps none", again.memoryPic(gone))
    }

    @Test fun idsNeverRepeatInARoomEvenAfterAGameWasOpened() {
        val room = photosLobby()
        fill(room, 1)
        val first = ids(room)[0]!!
        val ttt = room.openGame(LocalRoom.HOST, GameKind.TTT)
        val back = ttt.openGame(LocalRoom.HOST, GameKind.MEMORY)
        back.setOptions(LocalRoom.HOST, JSONObject().put("cards", 16).put("deck", "photos"))
        back.setMemoryPic(LocalRoom.HOST, 0, jpeg(300, 5), tick())
        assertTrue(state(back).memoryPhotoIds[0]!! > first)
    }

    @Test fun tokenGatingWithOnePlayerLackingTheTokenThereAreNoPhotosAndTheDeckIsTodays() {
        val room = lobby(oldApp)
        val v = room.view(LocalRoom.HOST, t0)
        assertFalse(v.getBoolean("memoryPhotos"))
        assertEquals("exactly today's options", JSONObject().put("cards", 16).toString(), v.getJSONObject("options").toString())
        assertEquals("Your photos need the latest Pip & Pop on every phone.", failure { room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 16).put("deck", "photos")) })
        assertTrue(failure { room.setMemoryPic(LocalRoom.HOST, 0, jpeg(), tick()) }!!.contains("latest"))
    }

    @Test fun aPlayerWithoutTheTokenWhoJoinsLaterTurnsThePhotosOffAndTheAnimalsAreDealt() {
        val room = photosLobby()
        fill(room, 8)
        room.join("Cy", Avatar.CAT, null, oldApp)
        val v = room.view(LocalRoom.HOST, t0)
        assertFalse(v.getBoolean("memoryPhotos"))
        assertEquals(JSONObject().put("cards", 16).toString(), v.getJSONObject("options").toString())
        assertNull("nobody can fetch one now", room.memoryPic(1))
        for (r in listOf(LocalRoom.HOST, LocalRoom.GUEST, LocalRoom.P3)) room.setTurnReady(r, true, t0)
        assertEquals("started", room.status)
        val mover = listOf(LocalRoom.HOST, LocalRoom.GUEST, LocalRoom.P3).first { parseRoom(room.view(it, t0 + 10_000)).turns!!.turn == "me" }
        room.move(mover, JSONObject().put("card", 0), t0 + 10_000)
        val face = room.view(mover, t0 + 10_000).getJSONObject("turns").getJSONArray("faces").getString(0)
        assertFalse(face, Regex("p\\d+").matches(face))
    }

    @Test fun anOldAppIsUnaffected() {
        val room = lobby(oldApp)
        assertEquals(JSONObject().put("cards", 16).toString(), room.view(LocalRoom.HOST, t0).getJSONObject("options").toString())
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 24))
        assertEquals(24, parseRoom(room.view(LocalRoom.GUEST, t0)).memoryCards)
        room.setTurnReady(LocalRoom.HOST, true, t0)
        room.setTurnReady(LocalRoom.GUEST, true, t0)
        assertEquals("started", room.status)
        assertFalse(parseRoom(room.view(LocalRoom.GUEST, t0)).memoryPhotosOn)
    }

    @Test fun theAnimalsDeckOfAnUpdatedAppIsAsBefore() {
        val room = lobby()
        assertEquals(JSONObject().put("cards", 16).put("deck", "animals").toString(), room.view(LocalRoom.HOST, t0).getJSONObject("options").toString())
        assertFalse(state(room).memoryDeckPhotos)
        room.setTurnReady(LocalRoom.HOST, true, t0)
        room.setTurnReady(LocalRoom.GUEST, true, t0)
        assertEquals("started", room.status)
    }

    @Test fun theCardCountCanStillBeChangedAfterAnOldPhoneJoinsOnlyAskingForPhotosIsRefused() {
        val room = photosLobby()
        fill(room, 8)
        room.join("Cy", Avatar.CAT, null, oldApp)
        room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 24))
        assertEquals(24, parseRoom(room.view(LocalRoom.HOST, t0)).memoryCards)
        assertTrue("the inherited deck is kept silently", room.memoryDeckPhotos)
        assertEquals(0, room.memoryPicsKept)
        assertEquals("Your photos need the latest Pip & Pop on every phone.", failure { room.setOptions(LocalRoom.HOST, JSONObject().put("cards", 24).put("deck", "photos")) })
    }

    @Test fun aPhoneWithoutTheTokenNeverGetsAPhotoCardButThePairsPlainAnimal() {
        val room = photosLobby(36)
        fill(room, 18)
        room.setTurnReady(LocalRoom.GUEST, true, t0)
        room.setTurnReady(LocalRoom.HOST, true, t0)
        val mover = if (parseRoom(room.view(LocalRoom.HOST, t0 + 10_000)).turns!!.turn == "me") LocalRoom.HOST else LocalRoom.GUEST
        val other = if (mover == LocalRoom.HOST) LocalRoom.GUEST else LocalRoom.HOST
        room.move(mover, JSONObject().put("card", 0), t0 + 10_000)
        fun face(viewer: String) = room.view(viewer, t0 + 10_000).getJSONObject("turns").getJSONArray("faces").getString(0)
        val photo = face(other)
        assertTrue(photo, Regex("p\\d+").matches(photo))
        room.players[other]!!.memoryPhotos = false
        val plain = face(other)
        val n = photo.drop(1).toInt()
        assertEquals(Avatar.entries[n % 12].key + if (n >= 12) "*" else "", plain)
    }

    @Test fun whenTheOpenerLeavesTheirPhotosGoAndTheDeckIsTheAnimalsAgain() {
        val room = photosLobby()
        room.join("Cy", Avatar.CAT, null, newApp)
        fill(room, 8)
        room.leave(LocalRoom.HOST)
        assertEquals(0, room.memoryPicsKept)
        assertFalse(room.memoryDeckPhotos)
        assertTrue(ids(room, LocalRoom.GUEST).isEmpty())
    }

    private fun ids(room: LocalRoom, role: String) = parseRoom(room.view(role, t0)).memoryPhotoIds
}
