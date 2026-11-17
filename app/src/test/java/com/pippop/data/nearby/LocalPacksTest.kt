package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.Reaction
import com.pippop.data.parseRoom
import com.pippop.game.Generator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stickers from the downloadable packs (cap "packs") in the room a phone hosts. A pack sticker travels as a
 * reaction under the key "p:<id>" (id: ^[a-z0-9-]{1,32}$); today's app drops a reaction key it does not know.
 * The same rule and tests are in backend/test/code.test.js and backend/cloudflare/test/packs.test.js.
 */
class LocalPacksTest {
    private val t0 = 1_000_000L
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    private fun lobby() = LocalRoom("ABCD", "Alice", Avatar.FOX, generate = { Generator.Result("000" + solution.substring(3), solution) })
        .also { it.join("Bob", Avatar.OWL, null) }

    private fun LocalRoom.said(now: Long = t0) = view(LocalRoom.GUEST, now).getJSONObject("opponent").getJSONObject("emoji")

    @Test fun theCapIsListed() {
        assertTrue(LocalRoom.PACKS in LocalRoom.CAPS)
        assertTrue("packs" in parseRoom(lobby().view(LocalRoom.HOST, t0)).caps)
    }

    @Test fun aValidKeyReachesTheOtherPlayerAsItWasSent() {
        for (key in listOf("p:sunny-day", "p:a", "p:0", "p:" + "a".repeat(32), "p:a-b-9")) {
            val room = lobby()
            room.sendPack(LocalRoom.HOST, key, t0)
            val e = room.said()
            assertEquals(key, e.getString("key"))
            assertEquals(1, e.getInt("seq"))
            assertTrue("no text field", !e.has("text"))
            // Today's app reads the key against its reactions and drops one it does not know.
            assertNull(Reaction.from(e.getString("key")))
            val parsed = parseRoom(room.view(LocalRoom.GUEST, t0)).opponent!!.emoji!!
            assertEquals(key.removePrefix("p:"), parsed.pack)
            assertNull(parsed.reaction)
        }
    }

    @Test fun aKeyOfTheWrongShapeIsRefusedLikeAnUnknownReaction() {
        val room = lobby()
        for (key in listOf("p:", "p:Sunny", "p:" + "a".repeat(33), "p:a/b", "p:a.b", "p:../x", "p:a b", "p:a\n", "P:abc", "p", "pack:abc", "p:é", "")) {
            val e = assertThrows(key, ApiException::class.java) { room.sendPack(LocalRoom.HOST, key, t0) }
            assertEquals("Unknown reaction.", e.message)
        }
        assertEquals("nothing was shown", null, parseRoom(room.view(LocalRoom.GUEST, t0)).opponent!!.emoji)
    }

    @Test fun theSamePaceLimitAsReactionsInEitherOrder() {
        val room = lobby()
        room.sendPack(LocalRoom.HOST, "p:one", t0)
        assertThrows(ApiException::class.java) { room.sendPack(LocalRoom.HOST, "p:two", t0 + 100) }
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.HOST, Reaction.FIRE, t0 + 100) }
        room.sendPack(LocalRoom.HOST, "p:two", t0 + LocalRoom.EMOJI_INTERVAL_MS)
        assertEquals(2, room.said().getInt("seq"))
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.HOST, Reaction.FIRE, t0 + LocalRoom.EMOJI_INTERVAL_MS + 100) }
    }

    @Test fun aPackStickerIsRefusedWhileKylianDictadorHasTheFloorAndTakesNothingOver() {
        val room = lobby()
        room.sendEmoji(LocalRoom.HOST, Reaction.KYLIAN, t0)
        assertThrows(ApiException::class.java) { room.sendPack(LocalRoom.GUEST, "p:one", t0 + 5_000) }
        room.sendPack(LocalRoom.GUEST, "p:one", t0 + Reaction.KYLIAN.takesOverMs)
        // It took nothing over: after the pace limit the sender is answered at once.
        room.sendEmoji(LocalRoom.GUEST, Reaction.FIRE, t0 + Reaction.KYLIAN.takesOverMs + LocalRoom.EMOJI_INTERVAL_MS)
    }

    @Test fun aPackStickerNeedsSomeoneToSendItTo() {
        val room = LocalRoom("ABCD", "Alice", Avatar.FOX)
        assertThrows(ApiException::class.java) { room.sendPack(LocalRoom.HOST, "p:one", t0) }
    }

    @Test fun normalReactionsAreUnchanged() {
        val room = lobby()
        room.sendEmoji(LocalRoom.HOST, Reaction.FIRE, t0)
        val e = room.said()
        assertEquals("fire", e.getString("key"))
        assertEquals(1, e.getInt("seq"))
        assertEquals(Reaction.FIRE, parseRoom(room.view(LocalRoom.GUEST, t0)).opponent!!.emoji!!.reaction)
        assertNull(parseRoom(room.view(LocalRoom.GUEST, t0)).opponent!!.emoji!!.pack)
    }
}
