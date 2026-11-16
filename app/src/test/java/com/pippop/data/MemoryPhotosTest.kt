package com.pippop.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import com.pippop.game.memory.CardFace
import com.pippop.game.memory.MemoryMatch
import com.pippop.ui.BattleUiState
import com.pippop.ui.screens.memoryPhotoOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Step 30, Memory Pairs with your photos: the gating (cap and every player's token), the 160 x 160 crop and its size cap, the
 * decode cache and the fetch-once rule, and the plain face a card shows until its photo has arrived.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class MemoryPhotosTest {
    /** A made-up picture (stripes and a disc, no real person) of any shape. */
    private fun picture(w: Int, h: Int, hue: Float = 200f): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 10) {
            paint.color = Color.HSVToColor(floatArrayOf((hue + i * 12) % 360, 0.5f, 0.95f))
            c.drawRect(0f, h * i / 10f, w.toFloat(), h * (i + 1) / 10f, paint)
        }
        paint.color = Color.rgb(40, 40, 70)
        c.drawCircle(w / 2f, h / 2f, minOf(w, h) / 3f, paint)
        return b
    }

    private fun photo(hue: Float = 200f) = MemoryPhotos.fromBitmap(picture(400, 300, hue))!!

    private fun room(
        caps: List<String> = listOf(MemoryPhotos.CAP), everyone: Boolean = true, photos: Boolean = true, ids: List<Int?> = List(8) { null },
        game: GameKind = GameKind.MEMORY,
    ) = com.pippop.ui.roomForTest(game = game, caps = caps, memoryPhotos = everyone, memoryDeckPhotos = photos, memoryPhotoIds = ids)

    // --- gating ---------------------------------------------------------------------------------

    @Test fun theDeckOffersYourPhotosOnlyWithTheCapAndEveryPlayersToken() {
        assertTrue(room().memoryPhotosOffered)
        assertFalse("no cap (Apps Script, or an older server)", room(caps = emptyList()).memoryPhotosOffered)
        assertFalse("a server with other caps", room(caps = listOf("faces", "lines")).memoryPhotosOffered)
        assertFalse("one player without the token", room(everyone = false).memoryPhotosOffered)
        assertFalse("another game", room(game = GameKind.TTT).memoryPhotosOffered)
        assertTrue(MemoryPhotos.offered(listOf("memory-photos"), true))
        assertFalse(MemoryPhotos.offered(listOf("memory-photos"), false))
        assertFalse(MemoryPhotos.offered(emptyList(), true))
    }

    @Test fun theDeckIsThePhotosOnlyWhenOfferedAndChosen() {
        assertTrue(room().memoryPhotosOn)
        assertFalse(room(photos = false).memoryPhotosOn)
        assertFalse("chosen, but one player lacks the token", room(everyone = false).memoryPhotosOn)
        assertFalse("chosen, but the server has no cap", room(caps = emptyList()).memoryPhotosOn)
    }

    @Test fun theTokenIsSentWithTheGamesThisAppKnows() {
        assertTrue("memory-photos" in BattleBackend.SUPPORTED_GAMES.split(","))
        assertTrue("sudoku-opener" in BattleBackend.SUPPORTED_GAMES.split(","))
    }

    @Test fun anOldServersStateHasNoPhotosAndReadsAsToday() {
        val json = org.json.JSONObject(
            """{"ok":true,"type":"state","code":"K7Q2NB","role":"host","game":"memory","status":"lobby","round":0,"serverTime":0,"version":1,
               "options":{"cards":24},"me":{"name":"Sam","avatar":"fox","ready":false,"left":false,"online":true},"opponent":null,"others":[]}""",
        )
        val r = parseRoom(json)
        assertEquals(24, r.memoryCards)
        assertFalse(r.memoryPhotos)
        assertFalse(r.memoryDeckPhotos)
        assertTrue(r.memoryPhotoIds.isEmpty())
        assertFalse(r.memoryPhotosOffered)
    }

    @Test fun aServersPhotosStateIsRead() {
        val json = org.json.JSONObject(
            """{"ok":true,"type":"state","code":"K7Q2NB","role":"host","game":"memory","status":"lobby","round":0,"serverTime":0,"version":1,
               "caps":["memory-photos"],"memoryPhotos":true,"options":{"cards":16,"deck":"photos","photos":[3,null,5,6,null,8,9,10]},
               "me":{"name":"Sam","avatar":"fox","ready":false,"left":false,"online":true},"opponent":null,"others":[]}""",
        )
        val r = parseRoom(json)
        assertTrue(r.memoryPhotosOn)
        assertEquals(listOf(3, null, 5, 6, null, 8, 9, 10), r.memoryPhotoIds)
        assertEquals(6, r.memoryPhotosSent)
        assertEquals(8, r.memoryPairs)
    }

    // --- the picture ----------------------------------------------------------------------------

    @Test fun anyShapeBecomesA160By160JpegUnderTheCap() {
        assertEquals(160, MemoryPhotos.SIZE)
        assertEquals(16_000, MemoryPhotos.MAX_BASE64)
        for ((w, h) in listOf(400 to 300, 300 to 500, 160 to 160, 2000 to 1500, 90 to 70)) {
            val text = MemoryPhotos.fromBitmap(picture(w, h))
            assertNotNull("$w x $h", text)
            assertTrue("$w x $h is ${text!!.length} characters", text.length <= MemoryPhotos.MAX_BASE64)
            val bytes = Base64.decode(text, Base64.DEFAULT)
            assertTrue("a JPEG", Faces.isJpeg(bytes))
            val out = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(160, out.width)
            assertEquals(160, out.height)
        }
    }

    @Test fun theCropIsTheMiddleSquare() {
        assertEquals(Faces.Crop(50, 0, 300), Faces.centreSquare(400, 300))
        // A wide picture's two edges are cut away: a bar at the far left of a 400 x 300 picture is not in the result.
        val b = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(Color.rgb(0, 200, 0))
            drawRect(0f, 0f, 40f, 300f, Paint().apply { color = Color.rgb(255, 0, 0) })
        }
        val bytes = Base64.decode(MemoryPhotos.fromBitmap(b)!!, Base64.DEFAULT)
        val out = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val px = out.getPixel(2, 80)
        assertTrue("the red edge was cropped away", Color.red(px) < 100 && Color.green(px) > 120)
    }

    @Test fun aNoisyPictureStillFitsByLoweringTheQuality() {
        val noisy = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val r = java.util.Random(7)
        for (x in 0 until 300) for (y in 0 until 300) noisy.setPixel(x, y, Color.rgb(r.nextInt(256), r.nextInt(256), r.nextInt(256)))
        val text = MemoryPhotos.fromBitmap(noisy)
        assertNotNull("even random noise fits 16,000 at the lowest quality", text)
        assertTrue(text!!.length <= MemoryPhotos.MAX_BASE64)
    }

    @Test fun aReceivedPhotoIsCheckedBeforeItIsDecoded() {
        assertNull(MemoryPhotos.decode(null))
        assertNull(MemoryPhotos.decode(""))
        assertNull(MemoryPhotos.decode("not base64 at all!!"))
        assertNull("too long", MemoryPhotos.decode("A".repeat(MemoryPhotos.MAX_BASE64 + 4)))
        assertNull("not a JPEG", MemoryPhotos.decode(Base64.encodeToString(ByteArray(100) { 3 }, Base64.NO_WRAP)))
        // A small file may declare a huge picture: refused by its declared size, before anything is decoded.
        val huge = MemoryPhotos.fromBitmap(picture(300, 300))!!
        assertNotNull(MemoryPhotos.decode(huge))
        val big = Bitmap.createBitmap(MemoryPhotos.MAX_SIDE + 100, MemoryPhotos.MAX_SIDE + 100, Bitmap.Config.ARGB_8888)
        val out = java.io.ByteArrayOutputStream()
        big.compress(Bitmap.CompressFormat.JPEG, 5, out)
        val tooWide = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        assertTrue("small enough to be sent", tooWide.length <= MemoryPhotos.MAX_BASE64)
        assertNull("declares more than 256 pixels a side", MemoryPhotos.decode(tooWide))
    }

    // --- the cache and the fetch-once rule ------------------------------------------------------

    @Test fun aDecodedPhotoIsKeptSoItIsDecodedOnce() {
        val a = photo(10f)
        assertNull(MemoryPhotos.peek(a))
        val first = MemoryPhotos.decode(a)
        assertNotNull(first)
        assertTrue("the second ask is the same picture", first === MemoryPhotos.decode(a))
        assertTrue("and peek finds it without decoding", first === MemoryPhotos.peek(a))
    }

    @Test fun theCacheHoldsAWholeDeck() {
        val photos = (0 until MemoryPhotos.MAX_PAIRS).map { photo(it * 15f) }
        photos.forEach { MemoryPhotos.decode(it) }
        assertTrue("all 18 are still there", photos.all { MemoryPhotos.peek(it) != null })
    }

    @Test fun eachPhotoIsFetchedOncePerId() {
        val asked = mutableSetOf<Int>()
        assertEquals(listOf(3, 5, 6), MemoryPhotos.toFetch(listOf(3, null, 5, 6), asked))
        assertEquals("the same state again asks for nothing", emptyList<Int>(), MemoryPhotos.toFetch(listOf(3, null, 5, 6), asked))
        assertEquals("a replaced photo has a new id: fetched once more", listOf(9), MemoryPhotos.toFetch(listOf(3, null, 9, 6), asked))
        assertEquals("one that arrived while this phone sent it is not fetched back", emptyList<Int>(), MemoryPhotos.toFetch(listOf(3, 9), asked.also { it.add(11) }))
        assertEquals(emptyList<Int>(), MemoryPhotos.toFetch(emptyList(), asked))
    }

    // --- what is sent where ---------------------------------------------------------------------

    @Test fun picksFillTheDecksPlacesInOrder() {
        // Eight places, all empty: eight photos fill them all; more photos than places are not used.
        assertEquals((0 until 8).map { it to it }, MemoryPhotos.plan(8, List(8) { null }, 8, false))
        assertEquals((0 until 8).map { it to it }, MemoryPhotos.plan(8, emptyList(), 12, false))
        // Three of eight chosen: they take the first three places.
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), MemoryPhotos.plan(8, List(8) { null }, 3, false))
        // Choosing again with fewer than a full set empties the places after them, so the deck is never part old, part new.
        assertEquals(listOf(0 to 0, 1 to 1, 2 to null, 3 to null), MemoryPhotos.plan(4, listOf(1, 2, 3, 4), 2, false))
        // "Choose three more" fills only the empty places.
        assertEquals(listOf(1 to 0, 4 to 1, 5 to 2), MemoryPhotos.plan(8, listOf(7, null, 8, 9, null, null, 10, 11), 3, true))
        assertEquals("one more than are missing is cut", listOf(2 to 0), MemoryPhotos.plan(4, listOf(1, 2, null, 4), 2, true))
        assertEquals(emptyList<Pair<Int, Int?>>(), MemoryPhotos.plan(4, listOf(1, 2, 3, 4), 2, true))
    }

    @Test fun thePickerAsksFromTheStateNowNotFromTheLastTap() {
        // None or all places filled: choose the whole set, asking for every pair (never capped at two).
        assertEquals(false to 8, MemoryPhotos.pickRule(8, 0))
        assertEquals(false to 8, MemoryPhotos.pickRule(8, 8))
        assertEquals(false to 18, MemoryPhotos.pickRule(18, 18))
        // Some filled: fill the empty ones; one missing is still asked as two (the picker needs two) and cut to one.
        assertEquals(true to 5, MemoryPhotos.pickRule(8, 3))
        assertEquals(true to 2, MemoryPhotos.pickRule(8, 7))
        assertEquals("Choose the photos to start.", MemoryPhotos.readyLine(8, 0))
        assertEquals("Choose five more to start.", MemoryPhotos.readyLine(8, 3))
    }

    @Test fun tooFewPhotosLeaveTheGameUnstartedAndSayHowManyMoreToChoose() {
        assertEquals("3 of 8 sent", MemoryPhotos.progress(3, 8))
        assertEquals("Eight photos make sixteen cards.", MemoryPhotos.note(8, 8))
        assertEquals("Eight photos make sixteen cards. Choose them to start.", MemoryPhotos.note(8, 0))
        assertEquals("Eight photos make sixteen cards. Choose five more to start.", MemoryPhotos.note(8, 3))
        assertEquals("Eighteen photos make 36 cards. Choose one more to start.", MemoryPhotos.note(18, 17))
        assertEquals("Choose photos", MemoryPhotos.buttonLabel(8, 0))
        assertEquals("Choose five more", MemoryPhotos.buttonLabel(8, 3))
        assertEquals("Choose other photos", MemoryPhotos.buttonLabel(8, 8))
        assertEquals(8, MemoryPhotos.pairsFor(16))
        assertEquals(18, MemoryPhotos.pairsFor(36))
    }

    // --- the board ------------------------------------------------------------------------------

    @Test fun aPhotosDeckIsPairsOfPN() {
        for (cards in MemoryMatch.DECKS) {
            val m = MemoryMatch(Random(cards), cards, photos = true)
            assertEquals(cards, m.deck.size)
            assertTrue(m.deck.groupingBy { it }.eachCount().values.all { it == 2 })
            assertEquals((0 until cards / 2).map { it }.toSet(), m.deck.map { it.pair }.toSet())
        }
        // The animals deck is as it was.
        assertTrue(MemoryMatch(Random(1), 16).deck.all { it.pair == null })
    }

    @Test fun aCardOfAPairKeepsItsKeyAndItsPlainAnimalSoThePairAlwaysLooksAlike() {
        val a = CardFace.from("p3")!!
        assertEquals("p3", a.key)
        assertEquals(3, a.pair)
        assertEquals(a, CardFace.from("p3"))
        assertEquals(CardFace.photo(3), a)
        assertTrue("different pairs differ", CardFace.from("p4") != a)
        // Eighteen pairs: the animals run out after twelve, then gold ones.
        val faces = (0 until 18).map { CardFace.photo(it) }
        assertEquals(18, faces.distinct().size)
        assertTrue(faces.drop(12).all { it.gold } && faces.take(12).none { it.gold })
        // Animals and gold animals still read as they did, and "panda" is not a photo.
        assertEquals(Avatar.PANDA, CardFace.from("panda")!!.avatar)
        assertNull(CardFace.from("panda")!!.pair)
        assertTrue(CardFace.from("fox*")!!.gold)
        assertNull(CardFace.from("p"))
        assertNull(CardFace.from("pxyz"))
    }

    @Test fun aCardShowsAPlainFaceUntilItsPhotoHasArrived() {
        val ids = listOf(11, 12, null, 14, 15, 16, 17, 18)
        val r = room(ids = ids)
        val one = photo(40f)
        MemoryPhotos.decode(one)
        val face0 = CardFace.photo(0)
        val face2 = CardFace.photo(2)
        // Nothing has arrived: every card is plain.
        assertNull(memoryPhotoOf(r, BattleUiState(room = r))(face0))
        // Photo 11 (pair 0) has arrived and is decoded: pair 0 shows it, the others stay plain.
        val have = BattleUiState(room = r, memoryPics = mapOf(11 to one))
        assertNotNull(memoryPhotoOf(r, have)(face0))
        assertNull("pair 1's has not", memoryPhotoOf(r, have)(CardFace.photo(1)))
        assertNull("pair 2 has none sent at all", memoryPhotoOf(r, have)(face2))
        // A photo that arrived but could not be decoded is left: the card stays plain.
        val broken = BattleUiState(room = r, memoryPics = mapOf(12 to "AAAA"))
        assertNull(memoryPhotoOf(r, broken)(CardFace.photo(1)))
        // An animal card is never a photo; and the animals deck (or no token) shows no photos at all.
        assertNull(memoryPhotoOf(r, have)(CardFace(Avatar.FOX)))
        val animals = room(photos = false, ids = ids)
        assertNull(memoryPhotoOf(animals, have)(face0))
        val noToken = room(everyone = false, ids = ids)
        assertNull(memoryPhotoOf(noToken, have)(face0))
    }
}
