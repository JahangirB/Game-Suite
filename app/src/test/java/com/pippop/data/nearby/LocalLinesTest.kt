package com.pippop.data.nearby

import com.pippop.data.ApiException
import com.pippop.data.Avatar
import com.pippop.data.ChatLines
import com.pippop.data.Reaction
import com.pippop.data.parseRoom
import com.pippop.game.Generator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lines of the player's own (cap "lines") in the room a phone hosts. A line travels as a reaction under
 * the key "line" with its words in "text"; today's app drops a reaction key it does not know. The same
 * rule and tests are in backend/test/code.test.js and backend/cloudflare/test/lines.test.js.
 */
class LocalLinesTest {
    private val t0 = 1_000_000L
    private val emoji = "😀"
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    private fun lobby() = LocalRoom("ABCD", "Alice", Avatar.FOX, generate = { Generator.Result("000" + solution.substring(3), solution) })
        .also { it.join("Bob", Avatar.OWL, null) }

    private fun LocalRoom.said(now: Long = t0) = view(LocalRoom.GUEST, now).getJSONObject("opponent").getJSONObject("emoji")

    @Test fun theCapIsListed() {
        assertTrue(LocalRoom.LINES in LocalRoom.CAPS)
        assertTrue("lines" in parseRoom(lobby().view(LocalRoom.HOST, t0)).caps)
    }

    @Test fun aLineReachesTheOtherPlayerUnderTheKeyLine() {
        val room = lobby()
        room.sendLine(LocalRoom.HOST, "Nice one", t0)
        val e = room.said()
        assertEquals("line", e.getString("key"))
        assertEquals("Nice one", e.getString("text"))
        assertEquals(1, e.getInt("seq"))
        // Today's app reads the key against its reactions and drops one it does not know.
        assertNull(Reaction.from(e.getString("key")))
        assertEquals("Nice one", parseRoom(room.view(LocalRoom.GUEST, t0)).opponent!!.emoji!!.line)
    }

    @Test fun controlCharactersAreStrippedTheEndsTrimmedAndNothingLeftIsRefused() {
        val room = lobby()
        room.sendLine(LocalRoom.HOST, "  Hi\u0007 th\u0000ere\n\t ", t0)
        assertEquals("Hi there", room.said().getString("text"))
        for (empty in listOf("", "   ", "\u0007\u0000\n")) {
            assertThrows(ApiException::class.java) { room.sendLine(LocalRoom.HOST, empty, t0 + 10_000) }
        }
        assertEquals(1, room.said().getInt("seq"))
    }

    @Test fun atMost24CharactersCountedAsCodePointsSoAnEmojiIsNotSplit() {
        val room = lobby()
        var now = t0
        fun next(text: String): String {
            now += LocalRoom.EMOJI_INTERVAL_MS
            room.sendLine(LocalRoom.HOST, text, now)
            return room.said(now).getString("text")
        }
        assertEquals("abcdefghijabcdefghijabcd", next("abcdefghij".repeat(3)))
        assertEquals("a".repeat(23) + emoji, next("a".repeat(23) + emoji + "zzz"))
        assertEquals("a".repeat(24), next("a".repeat(24) + emoji))
        assertEquals("a".repeat(23), next("a".repeat(23) + " tail"))
        assertEquals(ChatLines.MAX, 24)
    }

    @Test fun theSamePaceLimitAsReactionsInEitherOrder() {
        val room = lobby()
        room.sendLine(LocalRoom.HOST, "One", t0)
        assertThrows(ApiException::class.java) { room.sendLine(LocalRoom.HOST, "Two", t0 + 100) }
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.HOST, Reaction.FIRE, t0 + 100) }
        room.sendLine(LocalRoom.HOST, "Two", t0 + LocalRoom.EMOJI_INTERVAL_MS)
        assertEquals(2, room.said().getInt("seq"))
        assertThrows(ApiException::class.java) { room.sendEmoji(LocalRoom.HOST, Reaction.FIRE, t0 + LocalRoom.EMOJI_INTERVAL_MS + 100) }
    }

    @Test fun aLineIsRefusedWhileKylianDictadorHasTheFloorAndTakesNothingOver() {
        val room = lobby()
        room.sendEmoji(LocalRoom.HOST, Reaction.KYLIAN, t0)
        assertThrows(ApiException::class.java) { room.sendLine(LocalRoom.GUEST, "Nice one", t0 + 5_000) }
        assertThrows(ApiException::class.java) { room.sendLine(LocalRoom.HOST, "Nice one", t0 + Reaction.KYLIAN.takesOverMs - 1) }
        room.sendLine(LocalRoom.GUEST, "Nice one", t0 + Reaction.KYLIAN.takesOverMs)
        assertEquals("Nice one", parseRoom(room.view(LocalRoom.HOST, t0)).opponent!!.emoji!!.line)
        // The line took nothing over: after the pace limit the sender is answered at once.
        room.sendEmoji(LocalRoom.HOST, Reaction.FIRE, t0 + Reaction.KYLIAN.takesOverMs + LocalRoom.EMOJI_INTERVAL_MS)
    }

    @Test fun invisibleFormatCharactersAreStrippedBeforeItTravels() {
        val room = lobby()
        room.sendLine(LocalRoom.HOST, "a\u202Eb\u200Bc", t0)
        assertEquals("abc", room.said().getString("text"))
    }

    @Test fun aLoneHighSurrogateCountsAsOne() {
        val room = lobby()
        room.sendLine(LocalRoom.HOST, "a".repeat(23) + "\uD83Dbbbb", t0)
        assertEquals("a".repeat(23) + "\uD83D", room.said().getString("text"))
    }

    @Test fun aLineNeedsSomeoneToSendItTo() {
        val room = LocalRoom("ABCD", "Alice", Avatar.FOX)
        assertThrows(ApiException::class.java) { room.sendLine(LocalRoom.HOST, "Anyone?", t0) }
    }

    @Test fun aReactionWithoutTextIsUnchanged() {
        val room = lobby()
        room.sendEmoji(LocalRoom.HOST, Reaction.FIRE, t0)
        val e = room.said()
        assertEquals("fire", e.getString("key"))
        assertEquals(1, e.getInt("seq"))
        assertTrue("no text field", !e.has("text"))
        assertEquals(Reaction.FIRE, parseRoom(room.view(LocalRoom.GUEST, t0)).opponent!!.emoji!!.reaction)
    }
}
