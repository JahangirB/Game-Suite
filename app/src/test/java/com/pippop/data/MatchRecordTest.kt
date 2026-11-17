package com.pippop.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** What a saved game remembers, and that lines saved before it remembered more still load. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MatchRecordTest {
    @get:Rule val tmp = TemporaryFolder()

    // Lines exactly as ScoreStore wrote them before the new values existed.
    private val oldLines = listOf(
        """{"at":1731600000000,"game":"ludo","others":["Alex"],"winner":"You"}""",
        """{"at":1731686400000,"game":"golf","others":["Alex","Sam"],"winner":"Alex & Sam"}""",
        """{"at":1731772800000,"game":"ttt","others":["Alex"],"winner":null}""",
    )

    @Test fun oldLinesLoadUnchanged() {
        val file = tmp.newFile("scores.jsonl").apply { writeText(oldLines.joinToString("\n") + "\n") }
        val all = ScoreStore(file).records.value
        assertEquals(3, all.size)
        assertEquals(MatchRecord(1731600000000, GameKind.LUDO, listOf("Alex"), "You"), all[0])
        assertEquals(listOf("Alex", "Sam"), all[1].others)
        assertTrue(all[1].won("Sam"))
        assertNull(all[2].winner)
        all.forEach {
            assertNull(it.durationMs); assertNull(it.myScore); assertNull(it.theirScore); assertNull(it.winnerCount); assertFalse(it.sample)
        }
    }

    @Test fun anOldLineIsWrittenBackWithoutNewKeys() {
        oldLines.forEach { line ->
            val written = MatchRecord.from(JSONObject(line))!!.toJson()
            assertEquals(setOf("at", "game", "others", "winner"), written.keys().asSequence().toSet())
        }
    }

    @Test fun newValuesRoundTrip() {
        val r = MatchRecord(5, GameKind.HOCKEY, listOf("Maya"), "You", durationMs = 312_000, myScore = 7, theirScore = 4, winnerCount = 3, sample = true)
        val back = MatchRecord.from(JSONObject(r.toJson().toString()))
        assertEquals(r, back)
    }

    @Test fun valuesAreOptionalOneByOne() {
        val r = MatchRecord(5, GameKind.SHIPS, listOf("Maya"), null, winnerCount = 41)
        val json = r.toJson()
        assertEquals(41, json.getInt("count"))
        assertFalse(json.has("ms")); assertFalse(json.has("mine")); assertFalse(json.has("theirs")); assertFalse(json.has("sample"))
        assertEquals(r, MatchRecord.from(json))
    }

    @Test fun oldAndNewLinesShareAFileAndSurviveAReload() {
        val file = tmp.newFile("scores.jsonl").apply { writeText(oldLines.joinToString("\n") + "\n") }
        val store = ScoreStore(file)
        val fresh = MatchRecord(1731900000000, GameKind.DOTS, listOf("Maya"), "You", durationMs = 400_000, myScore = 11, theirScore = 5)
        store.add(fresh)
        val reloaded = ScoreStore(file).records.value
        assertEquals(4, reloaded.size)
        assertEquals(fresh, reloaded.last())
        assertEquals(MatchRecord(1731600000000, GameKind.LUDO, listOf("Alex"), "You"), reloaded.first())
    }

    @Test fun aLineThatCannotBeReadIsSkippedNotFatal() {
        val file = tmp.newFile("scores.jsonl").apply { writeText("not json\n" + oldLines[0] + "\n") }
        assertEquals(1, ScoreStore(file).records.value.size)
    }

    @Test fun anAppendAfterACutOffLineStartsOnANewLine() {
        // A write that stopped half-way left a last line with no newline.
        val file = tmp.newFile("scores.jsonl").apply { writeText(oldLines[0] + "\n" + """{"at":1731686400000,"game":"go""") }
        val store = ScoreStore(file)
        val fresh = MatchRecord(1731900000000, GameKind.DOTS, listOf("Maya"), "You")
        store.add(fresh)
        val lines = file.readText().split("\n")
        // The cut-off line stays by itself, and the new record is a whole line of its own.
        assertEquals("""{"at":1731686400000,"game":"go""", lines[1])
        assertEquals(fresh, MatchRecord.from(JSONObject(lines[2])))
        assertEquals(listOf(MatchRecord(1731600000000, GameKind.LUDO, listOf("Alex"), "You"), fresh), ScoreStore(file).records.value)
    }

    @Test fun anAppendToAWholeFileOrAnEmptyOneAddsNoBlankLine() {
        val whole = tmp.newFile("a.jsonl").apply { writeText(oldLines[0] + "\n") }
        ScoreStore(whole).add(MatchRecord(5, GameKind.GOLF, listOf("Maya"), null))
        assertFalse(whole.readText().contains("\n\n"))
        assertEquals(2, whole.readLines().size)
        val empty = File(tmp.root, "b.jsonl")
        ScoreStore(empty).add(MatchRecord(5, GameKind.GOLF, listOf("Maya"), null))
        assertEquals(1, empty.readLines().size)
        val blank = tmp.newFile("c.jsonl")
        ScoreStore(blank).add(MatchRecord(5, GameKind.GOLF, listOf("Maya"), null))
        assertEquals(1, blank.readLines().size)
    }
}
