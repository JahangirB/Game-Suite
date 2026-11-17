package com.pippop.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The player's six lines: the defaults, editing, the 24 cap, empty ones falling back, and an incoming line. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ChatLinesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE)

    private fun fresh(): ProfileStore {
        prefs.edit().clear().commit()
        return again()
    }

    private fun again(): ProfileStore {
        val c = ProfileStore::class.java.getDeclaredConstructor(android.content.SharedPreferences::class.java)
        c.isAccessible = true
        return c.newInstance(prefs)
    }

    private val six = listOf("Nice one", "Hurry up", "Lucky", "Good game", "One more?", "Your turn")
    private val emoji = "😀"

    @Test fun aNewInstallStartsWithTheSixDefaults() {
        assertEquals(six, ChatLines.DEFAULTS)
        assertEquals(six, fresh().profile.value.lines)
    }

    @Test fun editedLinesAreKeptAndSurviveARestart() {
        val s = fresh()
        s.setLines(listOf("Well played", "Hurry up", "Lucky", "Good game", "Rematch?", "Your turn"))
        val want = listOf("Well played", "Hurry up", "Lucky", "Good game", "Rematch?", "Your turn")
        assertEquals(want, s.profile.value.lines)
        assertEquals(want, again().profile.value.lines)
    }

    @Test fun aLineIsCappedAt24CharactersAndAnEmojiIsNotSplit() {
        val s = fresh()
        s.setLines(listOf("x".repeat(30), "a".repeat(23) + emoji + "zzz", "a".repeat(24) + emoji, "Lucky", "Good game", "One more?"))
        val lines = s.profile.value.lines
        assertEquals("x".repeat(24), lines[0])
        assertEquals("a".repeat(23) + emoji, lines[1])
        assertEquals("a".repeat(24), lines[2])
        assertEquals(lines, again().profile.value.lines)
    }

    @Test fun anEmptyLineFallsBackToItsOwnDefault() {
        val s = fresh()
        s.setLines(listOf("", "   ", "Mine", "\u0007\n", "One more?", "Your turn"))
        assertEquals(listOf("Nice one", "Hurry up", "Mine", "Good game", "One more?", "Your turn"), s.profile.value.lines)
        // A short list is filled up too.
        s.setLines(listOf("Only one"))
        assertEquals(listOf("Only one") + six.drop(1), s.profile.value.lines)
    }

    @Test fun controlCharactersAreStrippedAndTheEndsTrimmed() {
        assertEquals("Hi there", ChatLines.clean("  Hi\u0007 th\u0000ere\n\t "))
        assertEquals("", ChatLines.clean("   "))
        assertEquals("a".repeat(23), ChatLines.clean("a".repeat(23) + " tail"))
        assertEquals("one two", ChatLines.limit("one two"))
    }

    @Test fun invisibleFormatCharactersGoAndTheJoinerStays() {
        assertEquals("abc", ChatLines.clean("a\u202Eb\u200Bc"))
        assertEquals("abcdefghijkl", ChatLines.clean("\u00ADa\u061Cb\u200Ec\u200Fd\u2028e\u2029f\u202Ag\u2060h\u2064i\u2066j\u2069k\uFEFFl"))
        assertEquals("abc", ChatLines.limit("a\u202Eb\u200Bc"))
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69"
        assertEquals(family, ChatLines.clean(family))
    }

    @Test fun aHugeTextIsOnlyReadToTheFirst256Units() {
        assertEquals("a".repeat(24), ChatLines.clean("a".repeat(100_000)))
        assertEquals("", ChatLines.clean(" ".repeat(300) + "hi"))
    }

    @Test fun aLoneHighSurrogateCountsAsOneCharacter() {
        assertEquals("a".repeat(23) + "\uD83D", ChatLines.clean("a".repeat(23) + "\uD83Dbbbb"))
    }

    @Test fun aTextThatIsNotAStringIsNotALine() {
        assertNull(roomWith(JSONObject().put("key", "line").put("seq", 1).put("text", 12345)).emoji)
        assertNull(roomWith(JSONObject().put("key", "line").put("seq", 1).put("text", JSONObject().put("a", 1))).emoji)
    }

    @Test fun resettingGoesBackToTheDefaults() {
        val s = fresh()
        s.setLines(List(6) { "Mine $it" })
        s.setLines(ChatLines.DEFAULTS)
        assertEquals(six, s.profile.value.lines)
    }

    private fun roomWith(emojiJson: JSONObject?): PlayerInfo {
        val room = com.pippop.data.nearby.LocalRoom("ABCD", "Alice", Avatar.FOX)
        room.join("Bob", Avatar.OWL, null)
        // The host's view of the guest, with the guest's latest reaction as the server sends it.
        val view = room.view(com.pippop.data.nearby.LocalRoom.HOST, 0)
        view.getJSONObject("opponent").put("emoji", emojiJson ?: JSONObject.NULL)
        return parseRoom(view).opponent!!
    }

    @Test fun anIncomingLineIsParsed() {
        val e = roomWith(JSONObject().put("key", "line").put("seq", 4).put("text", "  Nice\u0007 one ")).emoji!!
        assertNull(e.reaction)
        assertEquals("Nice one", e.line)
        assertEquals(4, e.seq)
    }

    @Test fun aLineWithNoWordsIsDropped() {
        assertNull(roomWith(JSONObject().put("key", "line").put("seq", 1)).emoji)
        assertNull(roomWith(JSONObject().put("key", "line").put("seq", 1).put("text", "  ")).emoji)
    }

    @Test fun aReactionIsParsedAsBefore() {
        val e = roomWith(JSONObject().put("key", "fire").put("seq", 2).put("text", "ignored")).emoji!!
        assertEquals(Reaction.FIRE, e.reaction)
        assertNull(e.line)
    }

    /** Today's app parses `Reaction.from(key)` and drops what is null: the key a line travels under is not a reaction. */
    @Test fun todaysAppDropsTheKeyALineTravelsUnder() {
        assertNull(Reaction.from(ChatLines.KEY))
    }
}
