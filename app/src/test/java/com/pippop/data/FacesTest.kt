package com.pippop.data

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pippop.ui.screens.FaceBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Step 29, your photo as your face: the 96 x 96 JPEG made from a picture, the files that keep it (yours and the saved
 * partner's), the fallback to the animal, and the one gate that decides whether a photo is sent.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class FacesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("arcade_sudoku", Context.MODE_PRIVATE)
    private val dir get() = File(context.filesDir, "faces-test")

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

    private fun photo(w: Int = 400, h: Int = 300, hue: Float = 200f): String = Faces.fromBitmap(picture(w, h, hue))!!

    private fun store(): ProfileStore {
        val c = ProfileStore::class.java.getDeclaredConstructor(android.content.SharedPreferences::class.java, File::class.java)
        c.isAccessible = true
        return c.newInstance(prefs, dir)
    }

    private fun fresh(): ProfileStore {
        prefs.edit().clear().commit()
        dir.deleteRecursively()
        return store()
    }

    private fun partner(face: String? = null, name: String = "Maya") =
        SavedPartner("https://x.example", "", "4821", name, Avatar.PANDA, "device", "p2", face)

    // --- the picture ----------------------------------------------------------------------------

    @Test fun theCropIsTheMiddleSquare() {
        assertEquals(Faces.Crop(50, 0, 300), Faces.centreSquare(400, 300))
        assertEquals(Faces.Crop(0, 100, 300), Faces.centreSquare(300, 500))
        assertEquals(Faces.Crop(0, 0, 200), Faces.centreSquare(200, 200))
    }

    @Test fun anyShapeBecomesA96By96JpegUnderTheCap() {
        for ((w, h) in listOf(400 to 300, 300 to 500, 96 to 96, 2000 to 1500, 40 to 30)) {
            val text = Faces.fromBitmap(picture(w, h))
            assertNotNull("$w x $h", text)
            assertTrue("$w x $h is ${text!!.length} characters", text.length <= Faces.MAX_BASE64)
            val bytes = Base64.decode(text, Base64.DEFAULT)
            assertTrue("a JPEG", Faces.isJpeg(bytes))
            val out = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(96, out.width)
            assertEquals(96, out.height)
        }
        assertEquals(96, Faces.SIZE)
    }

    @Test fun aNoisyPictureStillFitsByLoweringTheQuality() {
        val noisy = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val r = java.util.Random(7)
        for (x in 0 until 300) for (y in 0 until 300) noisy.setPixel(x, y, Color.rgb(r.nextInt(256), r.nextInt(256), r.nextInt(256)))
        val text = Faces.fromBitmap(noisy)
        assertTrue(text == null || text.length <= Faces.MAX_BASE64)
    }

    @Test fun onlyAWholeJpegIsOne() {
        assertTrue(Faces.isJpeg(byteArrayOf(-1, -40, -1, -32, 1, 2, -1, -39)))
        assertFalse(Faces.isJpeg(byteArrayOf(-1, -40, -1, -32, 1, 2, 3, 4)))
        assertFalse(Faces.isJpeg(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3, 4)))
        assertFalse(Faces.isJpeg(ByteArray(0)))
    }

    // --- the store ----------------------------------------------------------------------------

    @Test fun aPhotoIsSetRemovedAndSurvivesARestart() {
        val s = fresh()
        assertNull(s.profile.value.face)
        val a = photo()
        s.setFace(a)
        assertEquals(a, s.profile.value.face)
        assertEquals("kept as a small file", true, File(dir, "me.jpg").length() in 1..9_000)
        assertEquals("after a restart", a, store().profile.value.face)
        val b = photo(hue = 20f)
        s.setFace(b)
        assertEquals(b, store().profile.value.face)
        s.setFace(null)
        assertNull(s.profile.value.face)
        assertFalse(File(dir, "me.jpg").exists())
        assertNull(store().profile.value.face)
    }

    @Test fun theAnimalIsNotTouchedByThePhoto() {
        val s = fresh()
        s.setAvatar(Avatar.FOX)
        s.setFace(photo())
        assertEquals(Avatar.FOX, s.profile.value.avatar)
        s.setFace(null)
        assertEquals(Avatar.FOX, store().profile.value.avatar)
    }

    @Test fun aDamagedFileIsIgnored() {
        val s = fresh()
        s.setFace(photo())
        File(dir, "me.jpg").writeBytes(ByteArray(300) { 7 })
        assertNull(store().profile.value.face)
        File(dir, "me.jpg").writeBytes(ByteArray(0))
        assertNull(store().profile.value.face)
    }

    @Test fun thePartnersLastPhotoIsKeptWithThePartner() {
        val s = fresh()
        s.setPartner(partner())
        assertNull(s.profile.value.partner?.face)
        val a = photo(hue = 90f)
        s.setPartnerFace(a)
        assertEquals(a, s.profile.value.partner?.face)
        assertEquals("survives a restart", a, store().profile.value.partner?.face)
        assertEquals("their other details are untouched", "Maya", store().profile.value.partner?.name)
        // The same partner saved again with the photo keeps it; a different partner has none.
        s.setPartner(partner(face = a))
        assertEquals(a, store().profile.value.partner?.face)
        s.setPartner(partner(name = "Lee"))
        assertNull(store().profile.value.partner?.face)
        s.setPartner(partner(face = a))
        s.setPartner(null)
        assertFalse(File(dir, "partner.jpg").exists())
        assertNull(store().profile.value.partner)
    }

    @Test fun noPartnerMeansNowhereToKeepAPhoto() {
        val s = fresh()
        s.setPartnerFace(photo())
        assertNull(s.profile.value.partner)
        assertFalse(File(dir, "partner.jpg").exists())
    }

    @Test fun aStoreWithoutAFolderStillWorksLikeToday() {
        prefs.edit().clear().commit()
        val c = ProfileStore::class.java.getDeclaredConstructor(android.content.SharedPreferences::class.java)
        c.isAccessible = true
        val s = c.newInstance(prefs)
        s.setAvatar(Avatar.OWL)
        assertEquals(Avatar.OWL, s.profile.value.avatar)
        assertNull(s.profile.value.face)
    }

    // --- the fallback ----------------------------------------------------------------------------

    @Test fun aMissingOrBrokenPhotoFallsBackToTheAnimal() {
        assertNull(Faces.decode(null))
        assertNull(Faces.decode(""))
        assertNull(Faces.decode("not base64 at all!!"))
        assertNull(Faces.decode(Base64.encodeToString(ByteArray(100) { 3 }, Base64.NO_WRAP)))
        assertNull("too long", Faces.decode("A".repeat(Faces.MAX_BASE64 + 4)))
        // A JPEG shaped file that is not a picture.
        val fake = Base64.encodeToString(byteArrayOf(-1, -40, -1, -32, 1, 2, 3, 4, 5, 6, -1, -39), Base64.NO_WRAP)
        assertNull(Faces.decode(fake))
        assertNotNull(Faces.decode(photo()))

        val book = FaceBook.build("Sam", "broken", partner(face = "also broken"), null, emptyMap())
        assertNull(book.of("Sam"))
        assertNull(book.of("You"))
        assertNull(book.of("Maya"))
    }

    @Test fun faceBookShowsYoursAlwaysAndTheOthersOnlyWhereTheyHaveOne() {
        val mine = photo(hue = 10f)
        val book = FaceBook.build("Sam", mine, partner(face = photo(hue = 120f)), null, emptyMap())
        assertNotNull(book.of("You"))
        assertNotNull(book.of("Sam"))
        assertNotNull("the saved partner outside a room", book.of("Maya"))
        assertNull(book.of("Nobody"))
    }

    // --- the gate ----------------------------------------------------------------------------

    @Test fun aPhotoIsSentOnlyWhereTheRoomListsTheCap() {
        val mine = "abcd"
        // An Apps Script room, or an older server, lists no cap: nothing is ever sent.
        assertEquals(Faces.Share.Nothing, Faces.share(emptyList(), "ROOM", mine, null, null))
        assertEquals(Faces.Share.Nothing, Faces.share(listOf("lines", "packs", "sudoku-opener"), "ROOM", mine, null, null))
        assertEquals(Faces.Share.Nothing, Faces.share(emptyList(), "ROOM", null, 5, null))
        // With the cap: sent once per room and photo.
        val caps = listOf("lines", Faces.CAP)
        assertEquals(Faces.Share.Send(mine), Faces.share(caps, "ROOM", mine, null, null))
        assertEquals(Faces.Share.Nothing, Faces.share(caps, "ROOM", mine, 3, Faces.Sent("ROOM", mine)))
        assertEquals(Faces.Share.Send(mine), Faces.share(caps, "OTHER", mine, 3, Faces.Sent("ROOM", mine)))
        assertEquals("a new photo replaces the old", Faces.Share.Send("wxyz"), Faces.share(caps, "ROOM", "wxyz", 3, Faces.Sent("ROOM", mine)))
        // Removed: only tell a server that holds one.
        assertEquals(Faces.Share.Remove, Faces.share(caps, "ROOM", null, 3, Faces.Sent("ROOM", mine)))
        assertEquals(Faces.Share.Nothing, Faces.share(caps, "ROOM", null, null, null))
        assertEquals(Faces.Share.Nothing, Faces.share(caps, "ROOM", null, null, Faces.Sent("ROOM", mine)))
        assertEquals("faces", Faces.CAP)
    }

    // --- hostile photos, orientation ---------------------------------------------------------------

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** A JPEG header declaring [w] x [h] (a baseline frame header) and nothing else, 12 bytes of file. */
    private fun declaring(w: Int, h: Int) = byteArrayOf(
        -1, -40, -1, -64, 0, 11, 8, (h shr 8).toByte(), h.toByte(), (w shr 8).toByte(), w.toByte(), 1, 1, 0x11, 0, -1, -39,
    )

    @Test fun aSmallFileDeclaringAHugePictureIsNotDecoded() {
        assertNull(Faces.decode(b64(declaring(8000, 8000))))
        assertNull(Faces.decode(b64(declaring(257, 96))))
        assertNull(Faces.decode(b64(declaring(0, 0))))
    }

    @Test fun aWellFormedPictureOverTheSideLimitIsNotUsedEither() {
        fun jpegOf(side: Int): String {
            val out = java.io.ByteArrayOutputStream()
            picture(side, side).compress(Bitmap.CompressFormat.JPEG, 30, out)
            return b64(out.toByteArray())
        }
        assertNotNull(Faces.decode(jpegOf(256)))
        assertNull(Faces.decode(jpegOf(300)))
        assertEquals(256, Faces.MAX_SIDE)
    }

    @Test fun decodedPicturesAreKeptSoTheyAreMadeOnce() {
        val p = photo(hue = 77f)
        val first = Faces.decode(p)
        assertNotNull(first)
        assertTrue(Faces.peek(p) === first)
        assertNull(Faces.peek("never decoded"))
    }

    @Test fun aPictureShotSidewaysIsStoodUpright() {
        val wide = picture(40, 20)
        for (o in listOf(android.media.ExifInterface.ORIENTATION_ROTATE_90, android.media.ExifInterface.ORIENTATION_ROTATE_270, android.media.ExifInterface.ORIENTATION_TRANSPOSE)) {
            val r = Faces.upright(wide, o)
            assertEquals("orientation $o", 20, r.width)
            assertEquals(40, r.height)
        }
        for (o in listOf(android.media.ExifInterface.ORIENTATION_ROTATE_180, android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            assertEquals(40, Faces.upright(wide, o).width)
        }
        // The top of a picture shot with the phone turned ends up on the right: red top, rotated 90 degrees, red on the right.
        val top = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.BLUE); for (x in 0..3) it.setPixel(x, 0, Color.RED) }
        val r = Faces.upright(top, android.media.ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(Color.RED, r.getPixel(1, 0))
        assertEquals(Color.BLUE, r.getPixel(0, 0))
    }

    // --- the same name ----------------------------------------------------------------------------

    private fun info(name: String, photoId: Int?, device: String? = null, left: Boolean = false) = PlayerInfo(
        name = name, avatar = Avatar.PANDA, deviceId = device, emoji = null, ready = false, difficulty = null, progress = null,
        finishMs = null, left = left, idleMs = 0, online = true, photoId = photoId,
    )

    private fun roomWith(meName: String, vararg others: PlayerInfo) = RoomState(
        code = "K7Q2NB", role = "host", game = GameKind.DOMINOES, status = RoomStatus.LOBBY, difficulty = null, round = 0, startAt = null,
        puzzle = null, solution = null, winner = null, endReason = null, me = info(meName, null), opponent = others.firstOrNull(),
        ships = null, turns = null, serverTime = 0, others = others.toList(), caps = listOf(Faces.CAP),
    )

    @Test fun anotherPlayerWithYourNameDoesNotShowYourPhoto() {
        val mine = photo(hue = 10f)
        val theirs = photo(hue = 150f)
        val alone = FaceBook.build("Sam", mine, null, roomWith("Sam", info("Maya", 3)), mapOf(3 to theirs))
        assertNotNull(alone.of("Sam"))
        val twin = FaceBook.build("Sam", mine, null, roomWith("Sam", info("Sam", 3)), mapOf(3 to theirs))
        assertNull("two Sams: no photo by name", twin.of("Sam"))
        assertNotNull("but you are still You", twin.of("You"))
        // Outside a room your name is yours alone.
        assertNotNull(FaceBook.build("Sam", mine, null, null, emptyMap()).of("Sam"))
    }

    // --- the partner's kept photo -----------------------------------------------------------------

    private val kept = photo(hue = 90f)
    private val fresh = photo(hue = 300f)
    private val saved = SavedPartner("https://x.example", "", "4821", "Maya", Avatar.PANDA, "dev-maya", "p2", kept)
    private val caps = listOf(Faces.CAP)

    @Test fun thePartnerPhotoIsTakenOnlyFromExactlyOnePresentPlayerWithThatName() {
        val one = listOf(info("Maya", 5, "dev-maya"), info("Lee", 6))
        assertEquals(Faces.PartnerFace.Set(fresh), Faces.partnerFace(saved, one, caps, mapOf(5 to fresh)))
        assertEquals("not fetched yet", Faces.PartnerFace.Keep, Faces.partnerFace(saved, one, caps, emptyMap()))
        val two = listOf(info("Maya", 5), info("Maya", 6))
        assertEquals("two Mayas", Faces.PartnerFace.Keep, Faces.partnerFace(saved, two, caps, mapOf(5 to fresh, 6 to fresh)))
        val gone = listOf(info("Maya", 5, left = true))
        assertEquals("one who has left", Faces.PartnerFace.Keep, Faces.partnerFace(saved, gone, caps, mapOf(5 to fresh)))
        assertEquals("somebody else", Faces.PartnerFace.Keep, Faces.partnerFace(saved, listOf(info("Lee", 5)), caps, mapOf(5 to fresh)))
    }

    @Test fun aDifferentPhoneWithTheSameNameIsNotThePartner() {
        val other = listOf(info("Maya", 5, "dev-someone-else"))
        assertEquals(Faces.PartnerFace.Keep, Faces.partnerFace(saved, other, caps, mapOf(5 to fresh)))
        // An unknown id on either side does not stop it.
        assertEquals(Faces.PartnerFace.Set(fresh), Faces.partnerFace(saved, listOf(info("Maya", 5, null)), caps, mapOf(5 to fresh)))
        assertEquals(Faces.PartnerFace.Set(fresh), Faces.partnerFace(saved.copy(deviceId = null), other, caps, mapOf(5 to fresh)))
    }

    @Test fun thePartnerRemovingTheirPhotoClearsTheKeptOneOnlyWhereTheServerHasTheCap() {
        val none = listOf(info("Maya", null, "dev-maya"))
        assertEquals(Faces.PartnerFace.Clear, Faces.partnerFace(saved, none, caps, emptyMap()))
        assertEquals(Faces.PartnerFace.Keep, Faces.partnerFace(saved, none, emptyList(), emptyMap()))
    }

    @Test fun aResavedPartnerKeepsThePhotoOnlyForTheSameNameAndPhone() {
        assertEquals(kept, Faces.carryOver(saved, "Maya", "dev-maya"))
        assertNull("another phone", Faces.carryOver(saved, "Maya", "dev-other"))
        assertNull("another name", Faces.carryOver(saved, "Lee", "dev-maya"))
        assertNull("no id now", Faces.carryOver(saved, "Maya", null))
        assertNull("no id before", Faces.carryOver(saved.copy(deviceId = null), "Maya", "dev-maya"))
        assertNull(Faces.carryOver(null, "Maya", "dev-maya"))
    }
}
