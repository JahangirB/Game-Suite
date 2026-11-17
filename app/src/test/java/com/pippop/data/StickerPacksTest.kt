package com.pippop.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Stickers from the downloadable packs: the id rule, reading their files defensively, the store that keeps
 * them on the phone, and what the phone does with one that someone sends it (have it: show it; don't: fetch it,
 * and if that takes over two seconds or fails, show its name). A fake fetcher stands in for the network and
 * the test scheduler's virtual time for the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StickerPacksTest {
    @get:Rule val tmp = TemporaryFolder()

    private val base = "https://example.test/stickers"

    // ---- Files ----

    private fun part(d: String = "M 10,10 L 50,10 L 30,50 Z") = JSONObject()
        .put("d", d).put("fill", "#FFFFC93C").put("stroke", "#FF14121C").put("width", 3.0)
        .put("dash", JSONObject.NULL).put("roundCap", false).put("roundJoin", true)

    private fun sticker(id: String = "sunny-day", name: String = "Sunny day", parts: List<JSONObject> = listOf(part())) = JSONObject()
        .put("id", id).put("name", name).put("version", 1).put("parts", JSONArray(parts))

    private fun text(id: String = "sunny-day", name: String = "Sunny day") = sticker(id, name).toString()

    private fun index(vararg entries: Triple<String, String, Int>) = JSONObject().put(
        "stickers",
        JSONArray(entries.map { (id, name, size) -> JSONObject().put("id", id).put("name", name).put("tag", "A tag").put("version", 1).put("size", size) }),
    ).toString()

    @Test fun anIdIsLowerCaseLettersDigitsAndHyphensUpTo32() {
        for (ok in listOf("a", "0", "sunny-day", "a-b-9", "a".repeat(32))) assertTrue(ok, PackKey.validId(ok))
        for (bad in listOf("", "A", "Sunny", "a".repeat(33), "a/b", "a.b", "../x", "a b", "a\n", "\na", " a", "a_b", "é", "a:b", "p:a")) {
            assertFalse(bad, PackKey.validId(bad))
        }
        assertFalse(PackKey.validId(null))
    }

    @Test fun aKeyIsPColonThenAnId() {
        assertEquals("p:sunny-day", PackKey.key("sunny-day"))
        assertEquals("sunny-day", PackKey.idOf("p:sunny-day"))
        for (bad in listOf("p:", "P:abc", "abc", "p", "pack:abc", "p:A", "p:a/b", "p:a.b", "p:" + "a".repeat(33), "p:a\n", null)) assertNull(bad, PackKey.idOf(bad))
        assertThrows(IllegalArgumentException::class.java) { PackKey.key("Bad") }
    }

    @Test fun todaysAppReadsTheKeyAgainstItsReactionsAndDropsIt() {
        // Today's parser: Reaction.from(key)?.let { EmojiEvent(it, seq) }. A pack key is none of its reactions.
        assertNull(Reaction.from("p:sunny-day"))
    }

    @Test fun aGoodFileIsRead() {
        val s = StickerFiles.parse(text(), expectId = "sunny-day")!!
        assertEquals("sunny-day", s.id)
        assertEquals("Sunny day", s.name)
        assertEquals(1, s.parts.size)
        val p = s.parts[0]
        assertEquals(0xFFFFC93CL, p.fill)
        assertEquals(0xFF14121CL, p.stroke)
        assertEquals(3f, p.width)
        assertNull(p.dash)
        assertTrue(p.roundJoin)
        assertFalse(p.roundCap)
        assertNull(s.backdrop)
        assertEquals(-1, p.group)
    }

    @Test fun theBackdropAnimationAndDashAreRead() {
        val parts = listOf(part().put("dash", JSONArray(listOf(1.6, 1))).put("anim", "flame b").put("group", 3))
        val o = sticker(parts = parts).put("backdrop", JSONObject().put("type", "linear").put("colours", JSONArray(listOf("#FF7CC8F5", "#FFD9F1FF"))).put("x1", 60).put("y1", 0).put("x2", 60).put("y2", 120))
        val s = StickerFiles.parse(o.toString())!!
        assertEquals(PackBackdrop("linear", listOf(0xFF7CC8F5L, 0xFFD9F1FFL), listOf(60f, 0f, 60f, 120f)), s.backdrop)
        assertEquals(listOf(1.6f, 1f), s.parts[0].dash)
        assertEquals("flame b", s.parts[0].anim)
        assertEquals(3, s.parts[0].group)
    }

    @Test fun theCommittedStickersAllRead() {
        val dir = File("../stickers").takeIf { it.isDirectory } ?: File("stickers")
        val listed = StickerFiles.parseIndex(File(dir, "index.json").readText())!!
        assertTrue(listed.isNotEmpty())
        for (l in listed) {
            val file = File(dir, "${l.id}.json")
            assertTrue("${l.id} is a file", file.isFile)
            assertNotNull("${l.id} reads", StickerFiles.parse(file.readText(), expectId = l.id))
            if (l.size > 0) assertEquals(file.length().toInt(), l.size)
        }
    }

    @Test fun aFileForAnotherIdIsRefused() {
        assertNull(StickerFiles.parse(text("other"), expectId = "sunny-day"))
        assertNotNull(StickerFiles.parse(text("other")))
    }

    @Test fun anOversizedFileIsIgnored() {
        assertNotNull(StickerFiles.parse(sticker(parts = List(StickerFiles.MAX_PARTS) { part() }).toString()))
        assertNull("too many shapes", StickerFiles.parse(sticker(parts = List(StickerFiles.MAX_PARTS + 1) { part() }).toString()))
        assertNull("too many bytes", StickerFiles.parse(text() + " ".repeat(StickerFiles.MAX_BYTES)))
        assertNull("a path too long", StickerFiles.parse(sticker(parts = listOf(part("M 0,0 " + "L 1,1 ".repeat(2000)))).toString()))
        assertNull("no shapes", StickerFiles.parse(sticker(parts = emptyList()).toString()))
    }

    @Test fun aMalformedFileIsIgnoredNotACrash() {
        for (bad in listOf("", "nope", "{", "[]", "null", "{}", text().dropLast(5), "{\"id\":\"sunny-day\",\"name\":\"x\"}", "{\"id\":\"sunny-day\",\"name\":\"x\",\"parts\":5}", "{\"id\":\"sunny-day\",\"name\":\"x\",\"parts\":[1]}")) {
            assertNull(bad, StickerFiles.parse(bad))
        }
        assertNull(StickerFiles.parse(sticker(id = "Bad Id").toString()))
        assertNull(StickerFiles.parse(sticker(name = "").toString()))
        assertNull(StickerFiles.parse(sticker().put("id", 7).toString()))
        assertNull(StickerFiles.parse(sticker().put("backdrop", "red").toString()))
    }

    @Test fun onlyValidNumbersAndColoursAreAccepted() {
        fun bad(p: JSONObject) = assertNull(p.toString(), StickerFiles.parse(sticker(parts = listOf(p)).toString()))
        bad(part().put("width", 51))
        bad(part().put("width", -1))
        bad(part().put("width", "3"))
        bad(part().put("width", Double.MAX_VALUE))
        bad(part().put("fill", "red"))
        bad(part().put("fill", "#FFF"))
        bad(part().put("fill", "#GGGGGGGG"))
        bad(part().put("fill", 12))
        bad(part().put("dash", JSONArray(List(9) { 1 })))
        bad(part().put("dash", JSONArray(listOf(0))))
        bad(part().put("dash", JSONArray(listOf("a"))))
        bad(part().put("anim", "Bad Name!").put("group", 1))
        bad(part().put("anim", "glow").put("group", 64))
        bad(part().put("anim", "glow"))
        // Fill and stroke may be none.
        assertNotNull(StickerFiles.parse(sticker(parts = listOf(part().put("fill", JSONObject.NULL))).toString()))
        val back = { b: JSONObject -> StickerFiles.parse(sticker().put("backdrop", b).toString()) }
        assertNull(back(JSONObject().put("type", "wavy").put("colours", JSONArray(listOf("#FF000000")))))
        assertNull(back(JSONObject().put("type", "radial").put("colours", JSONArray(listOf("#FF000000", "#FFFFFFFF"))).put("cx", 1).put("cy", 1)))
        assertNull(back(JSONObject().put("type", "solid").put("colours", JSONArray(listOf("#FF000000", "#FFFFFFFF")))))
        assertNotNull(back(JSONObject().put("type", "solid").put("colours", JSONArray(listOf("#FF000000")))))
    }

    @Test fun aPathIsDataAndNeverCode() {
        for (ok in listOf("M 0,0 L 10,10 Z", "m0 0l10 10z", "M 1.5e1,-2 C 1,2 3,4 5,6 S 1 2 3 4 Q 1 2 3 4 T 5 6 H 4 V 5 A 1,1 0 0,1 5,5 Z", "M 0,0 L 1,1 L 2,2 3,3")) {
            assertTrue(ok, StickerFiles.validPath(ok))
        }
        for (bad in listOf(
            "", "L 1,1", "M 0,0 L 1", "M 0,0 L 1,1,1", "M 0,0 Z 5", "M 0,0 alert(1)", "M 0,0; drop table", "<svg onload=x>", "M 0,0 L 1,1 \u0000",
            "M 0,0 L NaN,1", "M 0,0 L Infinity,1", "M 0,0 L 1e400,1", "M 0,0 L 999999,1", "M 0,0 L 1,1 x", "M 0,0 E 1,1", "M 0,0 L ../1,1", "M",
        )) {
            assertFalse(bad, StickerFiles.validPath(bad))
        }
    }

    @Test fun theIndexKeepsGoodEntriesAndSkipsBadOnes() {
        val o = JSONObject().put(
            "stickers",
            JSONArray()
                .put(JSONObject().put("id", "sunny-day").put("name", "Sunny day").put("tag", "A sun").put("version", 2).put("size", 2270))
                .put(JSONObject().put("id", "Bad Id").put("name", "x"))
                .put(JSONObject().put("id", "no-name"))
                .put("junk")
                .put(JSONObject().put("id", "sunny-day").put("name", "Again"))
                .put(JSONObject().put("id", "long").put("name", "n".repeat(40))),
        )
        val list = StickerFiles.parseIndex(o.toString())!!
        assertEquals(listOf("sunny-day", "long"), list.map { it.id })
        assertEquals(PackListing("sunny-day", "Sunny day", "A sun", 2, 2270), list[0])
        assertEquals(24, list[1].name.length)
        assertEquals(1, list[1].version)
        assertNull(StickerFiles.parseIndex("nope"))
        assertNull(StickerFiles.parseIndex("{}"))
        assertNull(StickerFiles.parseIndex("{\"stickers\":3}"))
        assertNull(StickerFiles.parseIndex(" ".repeat(60_000) + "{}"))
    }

    @Test fun deepNestingIsRefusedBeforeItCanOverflowTheStack() {
        val deep = "{\"id\":" + "[".repeat(49_000)
        assertNull(StickerFiles.parse(deep))
        assertNull(StickerFiles.parse("{\"id\":" + "[".repeat(49_000) + "]".repeat(49_000) + "}"))
        assertNull(StickerFiles.parseIndex("{\"stickers\":" + "[".repeat(40_000)))
        assertFalse(StickerFiles.shallow("[".repeat(9)))
        assertTrue(StickerFiles.shallow("[".repeat(8) + "]".repeat(8)))
        // Brackets inside a string are only text.
        assertTrue(StickerFiles.shallow("{\"a\":\"" + "[".repeat(50) + "\\\"[[[\"}"))
        assertNotNull(StickerFiles.parse(sticker(name = "[[[[[[[[[[[[ ]]]").toString()))
    }

    @Test fun aRadialBackdropNeedsARadiusAboveNothing() {
        fun radial(r: Number) = StickerFiles.parse(
            sticker().put("backdrop", JSONObject().put("type", "radial").put("colours", JSONArray(listOf("#FF000000", "#FFFFFFFF"))).put("cx", 1).put("cy", 1).put("r", r)).toString(),
        )
        assertNull(radial(0))
        assertNull(radial(-5))
        assertNull(radial(0.001))
        assertNotNull(radial(0.01))
        assertNotNull(radial(60))
    }

    @Test fun dashesAreAnEvenCountOfSensibleSizesAndCoordinatesAreSmall() {
        fun dashed(vararg v: Number) = StickerFiles.parse(sticker(parts = listOf(part().put("dash", JSONArray(v.toList())))).toString())
        assertNotNull(dashed(2, 1))
        assertNotNull(dashed(0.5, 0.5, 1, 1, 2, 2, 3, 3))
        assertNull("odd", dashed(1, 1, 1))
        assertNull("one", dashed(2))
        assertNull("none", dashed())
        assertNull("too fine", dashed(0.4, 1))
        assertNull("too many", dashed(1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
        assertFalse(StickerFiles.validPath("M 0,0 L 1001,1"))
        assertTrue(StickerFiles.validPath("M 0,0 L 1000,-1000"))
    }

    // ---- The store ----

    /** Serves files from [files]; a url not in it fails. Counts every fetch. */
    private class FakeNet(val files: MutableMap<String, ByteArray> = mutableMapOf(), var latencyMs: Long = 0) : PackFetcher {
        val asked = mutableListOf<String>()
        var offline = false

        override suspend fun fetch(url: String, maxBytes: Int): ByteArray {
            asked += url
            if (latencyMs > 0) delay(latencyMs)
            if (offline) throw IOException("offline")
            val bytes = files[url] ?: throw IOException("HTTP 404")
            if (bytes.size > maxBytes) throw IOException("too big")
            return bytes
        }
    }

    private fun FakeNet.serve(id: String = "sunny-day", name: String = "Sunny day") = apply { files["$base/$id.json"] = text(id, name).toByteArray() }
    private fun FakeNet.serveIndex(vararg e: Triple<String, String, Int>) = apply { files["$base/index.json"] = index(*e).toByteArray() }

    @Test fun aDownloadIsKeptOnThePhoneAndSurvivesARestart() = runTest {
        val dir = tmp.newFolder("a")
        val net = FakeNet().serve()
        val store = StickerStore(dir, base, net, backgroundScope)
        assertNull(store.get("sunny-day"))
        val got = store.fetch("sunny-day")!!
        assertEquals("Sunny day", got.name)
        assertEquals(listOf(got), store.installed.value)
        assertTrue(File(dir, "sunny-day.json").isFile)
        // A new run of the app reads it back, and works with no internet at all.
        val again = StickerStore(dir, base, FakeNet().also { it.offline = true }, backgroundScope)
        again.load()
        assertEquals(got, again.get("sunny-day"))
        assertEquals(got, again.fetch("sunny-day"))
    }

    @Test fun aSecondAskForTheSameStickerDoesNotDownloadItAgain() = runTest {
        val net = FakeNet().serve().also { it.latencyMs = 100 }
        val store = StickerStore(tmp.newFolder("b"), base, net, backgroundScope)
        val a = async { store.fetch("sunny-day") }
        val b = async { store.fetch("sunny-day") }
        advanceUntilIdle()
        assertEquals(a.await(), b.await())
        assertEquals(1, net.asked.count { it.endsWith("sunny-day.json") })
        store.fetch("sunny-day")
        assertEquals(1, net.asked.count { it.endsWith("sunny-day.json") })
    }

    @Test fun aBadDownloadIsNotKept() = runTest {
        val dir = tmp.newFolder("c")
        val net = FakeNet()
        val store = StickerStore(dir, base, net, backgroundScope)
        net.files["$base/junk.json"] = "{ not json".toByteArray()
        net.files["$base/wrong.json"] = text("other").toByteArray()
        net.files["$base/big.json"] = ByteArray(StickerFiles.MAX_BYTES + 1) { ' '.code.toByte() }
        net.files["$base/code.json"] = sticker("code", parts = listOf(part("M 0,0 alert(1)"))).toString().toByteArray()
        for (id in listOf("junk", "wrong", "big", "code", "missing")) assertNull(id, store.fetch(id))
        assertTrue(store.installed.value.isEmpty())
        assertEquals(emptyList<String>(), dir.listFiles().orEmpty().map { it.name })
        assertTrue("nothing left downloading", store.downloading.value.isEmpty())
    }

    @Test fun anIdThatIsNotOneIsNeverFetched() = runTest {
        val net = FakeNet()
        val store = StickerStore(tmp.newFolder("d"), base, net, backgroundScope)
        for (id in listOf("../x", "A", "a/b", "", "a".repeat(33))) assertNull(store.fetch(id))
        assertTrue(net.asked.isEmpty())
    }

    @Test fun aBuildWithNoAddressHasNothingToDownload() = runTest {
        val net = FakeNet().serve()
        val store = StickerStore(tmp.newFolder("e"), "", net, backgroundScope)
        assertEquals(PackCatalog.NotSetUp, store.catalog.value)
        assertNull(store.fetch("sunny-day"))
        assertTrue(net.asked.isEmpty())
    }

    @Test fun aBrokenFileOnThePhoneIsIgnoredAndTheOthersLoad() = runTest {
        val dir = tmp.newFolder("f")
        File(dir, "sunny-day.json").writeText(text())
        File(dir, "broken.json").writeText("{ nope")
        File(dir, "swapped.json").writeText(text("other"))
        val store = StickerStore(dir, base, FakeNet(), backgroundScope)
        store.load()
        assertEquals(listOf("sunny-day"), store.installed.value.map { it.id })
    }

    @Test fun theyStayInTheOrderTheyWereDownloaded() = runTest {
        val dir = tmp.newFolder("g")
        val net = FakeNet().serve("zebra", "Zebra").serve("apple", "Apple").serve("mango", "Mango")
        val store = StickerStore(dir, base, net, backgroundScope)
        for (id in listOf("zebra", "apple", "mango")) store.fetch(id)
        assertEquals(listOf("zebra", "apple", "mango"), store.installed.value.map { it.id })
        val again = StickerStore(dir, base, net, backgroundScope)
        again.load()
        assertEquals(listOf("zebra", "apple", "mango"), again.installed.value.map { it.id })
    }

    @Test fun aNewerVersionReplacesTheOneOnThePhone() = runTest {
        val net = FakeNet().serve(name = "Old name")
        val store = StickerStore(tmp.newFolder("h"), base, net, backgroundScope)
        store.fetch("sunny-day")
        net.serve(name = "New name")
        assertEquals("Old name", store.fetch("sunny-day")!!.name)
        assertEquals("New name", store.download("sunny-day")!!.name)
        assertEquals(listOf("New name"), store.installed.value.map { it.name })
    }

    @Test fun theListComesFromTheIndexAndNamesTheStickers() = runTest {
        val net = FakeNet().serveIndex(Triple("sunny-day", "Sunny day", 2270), Triple("party", "Party", 900))
        val store = StickerStore(tmp.newFolder("i"), base, net, backgroundScope)
        assertEquals(PackCatalog.Loading, store.catalog.value)
        assertEquals("party", store.nameOf("party"))
        store.loadCatalog()
        assertEquals(listOf("sunny-day", "party"), (store.catalog.value as PackCatalog.Ready).stickers.map { it.id })
        assertEquals("Party", store.nameOf("party"))
        assertEquals("unlisted", store.nameOf("unlisted"))
        // Once it is here it is not fetched again, and a later failure does not lose it.
        store.loadCatalog()
        assertEquals(1, net.asked.size)
        net.offline = true
        store.loadCatalog(force = true)
        assertTrue(store.catalog.value is PackCatalog.Ready)
    }

    @Test fun aListThatCannotBeReachedSaysSo() = runTest {
        val net = FakeNet().also { it.offline = true }
        val store = StickerStore(tmp.newFolder("j"), base, net, backgroundScope)
        store.loadCatalog()
        assertTrue(store.catalog.value is PackCatalog.Failed)
        net.offline = false
        net.files["$base/index.json"] = "garbage".toByteArray()
        store.loadCatalog(force = true)
        assertTrue("an index that is not one is a failure too", store.catalog.value is PackCatalog.Failed)
    }

    @Test fun addingFromTheSheetSaysWhyWhenItFails() = runTest {
        val net = FakeNet().also { it.offline = true }
        val store = StickerStore(tmp.newFolder("k"), base, net, backgroundScope)
        store.add(PackListing("sunny-day", "Sunny day", "", 1, 0))
        runCurrent()
        assertEquals("Couldn't download Sunny day. Check the internet and try again.", store.message.value)
        store.messageShown()
        assertNull(store.message.value)
        net.offline = false
        net.serve()
        store.add(PackListing("sunny-day", "Sunny day", "", 1, 0))
        runCurrent()
        assertNull(store.message.value)
        assertNotNull(store.get("sunny-day"))
    }

    // ---- Receiving ----

    private fun receiver(store: StickerStore) = PackReceiver(store)

    @Test fun aStickerThePhoneHasIsShownAtOnce() = runTest {
        val net = FakeNet().serve()
        val store = StickerStore(tmp.newFolder("l"), base, net, backgroundScope)
        store.fetch("sunny-day")
        net.asked.clear()
        val shown = receiver(store).receive("sunny-day")
        assertEquals("sunny-day", (shown as PackShown.Sticker).sticker.id)
        assertTrue("no fetch", net.asked.isEmpty())
    }

    @Test fun aStickerThePhoneLacksIsFetchedKeptAndShown() = runTest {
        val net = FakeNet(latencyMs = 500).serve().serveIndex(Triple("sunny-day", "Sunny day", 2270))
        val store = StickerStore(tmp.newFolder("m"), base, net, backgroundScope)
        val shown = receiver(store).receive("sunny-day")
        assertEquals("sunny-day", (shown as PackShown.Sticker).sticker.id)
        assertEquals("shown as soon as it arrived", 500, currentTime)
        assertNotNull("kept", store.get("sunny-day"))
        assertTrue(File(tmp.root, "m/sunny-day.json").isFile)
    }

    @Test fun aStickerThatTakesOverTwoSecondsShowsItsNameAndIsStillKept() = runTest {
        val net = FakeNet().serve().serveIndex(Triple("sunny-day", "Sunny day", 2270))
        // The picture takes 5 s; the list (its name) arrives at once.
        val slow = PackFetcher { url, max ->
            if (url.endsWith("sunny-day.json")) delay(5_000)
            net.fetch(url, max)
        }
        val store = StickerStore(tmp.newFolder("n"), base, slow, backgroundScope)
        val shown = receiver(store).receive("sunny-day")
        assertEquals(PackShown.Name("Sunny day"), shown)
        assertEquals(PackReceiver.SLOW_MS, currentTime)
        assertNull("not here yet", store.get("sunny-day"))
        // The download carries on, so the sticker is there next time.
        advanceTimeBy(3_100)
        advanceUntilIdle()
        assertNotNull(store.get("sunny-day"))
        assertTrue(receiver(store).receive("sunny-day") is PackShown.Sticker)
    }

    @Test fun aFailedFetchShowsTheNameFromTheIndex() = runTest {
        val net = FakeNet().serveIndex(Triple("party", "Party hat", 900))
        val store = StickerStore(tmp.newFolder("o"), base, net, backgroundScope)
        assertEquals(PackShown.Name("Party hat"), receiver(store).receive("party"))
        assertTrue("it did not wait the two seconds", currentTime < PackReceiver.SLOW_MS)
        assertNull(store.get("party"))
    }

    @Test fun aFailedFetchWithNoIndexShowsTheId() = runTest {
        val net = FakeNet().also { it.offline = true }
        val store = StickerStore(tmp.newFolder("p"), base, net, backgroundScope)
        assertEquals(PackShown.Name("party"), receiver(store).receive("party"))
    }

    @Test fun aBuildWithNoAddressShowsTheId() = runTest {
        val store = StickerStore(tmp.newFolder("q"), "", FakeNet(), backgroundScope)
        assertEquals(PackShown.Name("party"), receiver(store).receive("party"))
    }

    @Test fun aSlowIndexDoesNotHoldBackASticker() = runTest {
        // The sticker arrives in 300 ms; the list would take 10 s. The sticker is shown, not held up for the names.
        val net = FakeNet().serve().serveIndex(Triple("sunny-day", "Sunny day", 2270))
        val slowIndex = PackFetcher { url, max ->
            if (url.endsWith("index.json")) delay(10_000) else delay(300)
            net.fetch(url, max)
        }
        val store = StickerStore(tmp.newFolder("r"), base, slowIndex, backgroundScope)
        assertTrue(receiver(store).receive("sunny-day") is PackShown.Sticker)
        assertEquals(300, currentTime)
    }

    @Test fun twoPeopleSendingTheSameStickerFetchItOnce() = runTest {
        val net = FakeNet(latencyMs = 200).serve().serveIndex(Triple("sunny-day", "Sunny day", 2270))
        val store = StickerStore(tmp.newFolder("s"), base, net, backgroundScope)
        val a = async { receiver(store).receive("sunny-day") }
        val b = async { receiver(store).receive("sunny-day") }
        assertTrue(a.await() is PackShown.Sticker)
        assertTrue(b.await() is PackShown.Sticker)
        assertEquals(1, net.asked.count { it.endsWith("sunny-day.json") })
    }

    // ---- Robustness of the store and receiver ----

    private fun failing(error: () -> Throwable) = PackFetcher { _, _ -> throw error() }

    @Test fun nothingASickerDoesThrowsOutOfTheStoreOrTheReceiver() = runTest {
        val errors = listOf<() -> Throwable>({ RuntimeException("x") }, { IllegalStateException() }, { StackOverflowError() }, { OutOfMemoryError() })
        for ((i, e) in errors.withIndex()) {
            val store = StickerStore(tmp.newFolder("t$i"), base, failing(e), backgroundScope)
            store.refresh()
            store.add(PackListing("sunny-day", "Sunny day", "", 1, 0))
            runCurrent()
            assertNull(store.download("sunny-day"))
            assertNull(store.fetch("other"))
            assertEquals(PackShown.Name("sunny-day"), PackReceiver(store).receive("sunny-day"))
            assertTrue(store.catalog.value is PackCatalog.Failed)
        }
        // A file nested deep enough to overflow a reader is just a bad file.
        val net = FakeNet()
        net.files["$base/sunny-day.json"] = ("{\"id\":" + "[".repeat(49_000)).toByteArray()
        net.files["$base/index.json"] = ("{\"stickers\":" + "[".repeat(49_000)).toByteArray()
        val store = StickerStore(tmp.newFolder("deep"), base, net, backgroundScope)
        store.refresh()
        store.add(PackListing("sunny-day", "Sunny day", "", 1, 0))
        runCurrent()
        assertTrue(store.catalog.value is PackCatalog.Failed)
        assertNull(store.get("sunny-day"))
        assertEquals(PackShown.Name("sunny-day"), PackReceiver(store).receive("sunny-day"))
    }

    @Test fun aFinishedDownloadDoesNotStayInTheTable() = runTest {
        // The picture takes 5 s and the receiver gives up at 2 s; when it is done, asking again fetches again.
        val net = FakeNet().serve()
        val slow = PackFetcher { url, max ->
            if (url.endsWith("sunny-day.json")) delay(5_000)
            net.fetch(url, max)
        }
        val store = StickerStore(tmp.newFolder("u"), base, slow, backgroundScope)
        assertTrue(PackReceiver(store).receive("sunny-day") is PackShown.Name)
        advanceTimeBy(3_100)
        runCurrent()
        assertNotNull(store.get("sunny-day"))
        assertEquals(1, net.asked.count { it.endsWith("sunny-day.json") })
        net.serve(name = "Newer")
        assertEquals("Newer", store.download("sunny-day")!!.name)
        assertEquals(2, net.asked.count { it.endsWith("sunny-day.json") })
    }

    @Test fun aFailedIdIsNotAskedForAgainFor60Seconds() = runTest {
        val net = FakeNet().also { it.offline = true }
        val store = StickerStore(tmp.newFolder("v"), base, net, backgroundScope, clock = { currentTime })
        assertNull(store.fetch("sunny-day"))
        assertNull(store.fetch("sunny-day"))
        assertEquals(1, net.asked.size)
        advanceTimeBy(59_000)
        assertNull(store.fetch("sunny-day"))
        assertEquals(1, net.asked.size)
        advanceTimeBy(2_000)
        net.offline = false
        net.serve()
        assertNotNull(store.fetch("sunny-day"))
        assertEquals(2, net.asked.size)
    }

    @Test fun tappingAddAsksAgainAtOnceEvenAfterAFailure() = runTest {
        val net = FakeNet().also { it.offline = true }
        val store = StickerStore(tmp.newFolder("w"), base, net, backgroundScope, clock = { currentTime })
        assertNull(store.fetch("sunny-day"))
        net.offline = false
        net.serve()
        store.add(PackListing("sunny-day", "Sunny day", "", 1, 0))
        runCurrent()
        assertNotNull(store.get("sunny-day"))
    }

    @Test fun aStickerThatCannotBeDrawnIsNeverKeptAndIsDroppedOnLoad() = runTest {
        val dir = tmp.newFolder("x")
        val net = FakeNet().serve()
        val store = StickerStore(dir, base, net, backgroundScope, drawable = { false })
        assertNull(store.fetch("sunny-day"))
        assertEquals(emptyList<String>(), dir.listFiles().orEmpty().map { it.name })
        File(dir, "sunny-day.json").writeText(text())
        val again = StickerStore(dir, base, net, backgroundScope, drawable = { false })
        again.load()
        assertTrue(again.installed.value.isEmpty())
        StickerStore(dir, base, net, backgroundScope).also { it.load() }.let { assertEquals(1, it.installed.value.size) }
    }

    @Test fun aStoreWithNoFolderKeepsStickersInMemoryOnly() = runTest {
        val store = StickerStore(null, base, FakeNet().serve(), backgroundScope)
        store.load()
        assertNotNull(store.fetch("sunny-day"))
        assertFalse(File("order.txt").exists())
    }
}
