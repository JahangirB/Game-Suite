package com.pippop.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A player's photo as their face ("Use a photo" in the profile). It is a [SIZE] x [SIZE] JPEG, kept on the phone as a
 * file and carried as base64 text (at most [MAX_BASE64] characters). It leaves the phone only to a server whose room
 * state lists the cap [CAP], by one call (`setFace`), and phones fetch it once by the id the state carries
 * (`PlayerInfo.photoId`). The animal stays the avatar every server and every older app gets.
 */
object Faces {
    /** The server cap that says photos are understood; the app sends a photo only to a server that has it. */
    const val CAP = "faces"

    /** The sides of the picture kept and sent, in pixels. */
    const val SIZE = 96

    /** The most base64 characters a photo may take; the three servers refuse more. */
    const val MAX_BASE64 = 12_000

    /** A centred square cut from a picture [w] x [h]: where it starts and how long its side is. */
    data class Crop(val left: Int, val top: Int, val side: Int)

    /** The simplest crop: the biggest square in the middle of the picture. */
    fun centreSquare(w: Int, h: Int): Crop {
        val side = minOf(w, h)
        return Crop((w - side) / 2, (h - side) / 2, side)
    }

    /** Whether [bytes] look like a whole JPEG: it starts FF D8 FF and ends FF D9. */
    fun isJpeg(bytes: ByteArray): Boolean = bytes.size >= 8 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() &&
        bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()

    /**
     * The middle square of [source], made [SIZE] x [SIZE] and compressed to a JPEG, as base64. The quality goes down
     * until it fits [MAX_BASE64]; null if it never does.
     */
    fun fromBitmap(source: Bitmap): String? = squareJpeg(source, SIZE, MAX_BASE64)

    /** The middle square of [source], made [size] x [size] and compressed to a JPEG as base64 of at most [maxBase64] characters (the quality goes down until it fits); null if it never does. */
    fun squareJpeg(source: Bitmap, size: Int, maxBase64: Int): String? {
        val c = centreSquare(source.width, source.height)
        if (c.side <= 0) return null
        val square = Bitmap.createBitmap(source, c.left, c.top, c.side, c.side)
        val small = Bitmap.createScaledBitmap(square, size, size, true)
        for (quality in listOf(85, 75, 65, 55, 45)) {
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val text = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (text.length <= maxBase64) return text
        }
        return null
    }

    /** The most a received photo may declare as its width or height; ours are [SIZE], so anything bigger is not one of ours. */
    const val MAX_SIDE = 256

    /** [bitmap] turned and flipped as an EXIF [orientation] says, so what was shot sideways is upright. */
    fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** The power of two that shrinks a picture [w] x [h] while its short side stays at least twice [SIZE]. */
    private fun sampleFor(w: Int, h: Int, size: Int = SIZE): Int {
        var sample = 1
        while (minOf(w, h) / (sample * 2) >= size * 2) sample *= 2
        return sample
    }

    /**
     * A picture from the photo picker made into a face ([fromBitmap]), off the main thread. It is read at a size near
     * what is needed, so a big photo costs little memory, and stood upright by its EXIF orientation: from API 28 by
     * ImageDecoder, which applies it, before that by [ExifInterface] and [upright].
     */
    suspend fun fromUri(context: Context, uri: Uri): String? = fromUri(context, uri, SIZE, MAX_BASE64)

    /** [fromUri] for a picture of [size] x [size] and at most [maxBase64] characters (Memory Pairs' photos). */
    suspend fun fromUri(context: Context, uri: Uri, size: Int, maxBase64: Int): String? = withContext(Dispatchers.Default) {
        runCatching {
            val resolver = context.contentResolver
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(sampleFor(info.size.width, info.size.height, size))
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, size) }
                val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
                val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                upright(raw, orientation ?: ExifInterface.ORIENTATION_NORMAL)
            }
            squareJpeg(bitmap, size, maxBase64)
        }.getOrNull()
    }

    private val decoded = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 16
    }

    /** A picture [decode] already made, or null; never decodes, so it is safe on the main thread. */
    fun peek(photo: String?): ImageBitmap? = photo?.let { synchronized(decoded) { decoded[it] } }

    /**
     * The picture in [photo] (base64), or null if it is not a usable one: too long, damaged, not a JPEG, or declaring
     * a side over [MAX_SIDE] (a small file can declare a huge picture, so the size is read before anything is decoded).
     * Decoding is work: call it off the main thread (Dispatchers.Default). Results are kept for the next ask.
     */
    fun decode(photo: String?): ImageBitmap? = decodeInto(decoded, photo, MAX_BASE64, MAX_SIDE)

    /** [decode] for another kind of picture: at most [maxBase64] characters and [maxSide] pixels a side, kept in [cache]. */
    internal fun decodeInto(cache: MutableMap<String, ImageBitmap>, photo: String?, maxBase64: Int, maxSide: Int): ImageBitmap? {
        if (photo.isNullOrEmpty() || photo.length > maxBase64) return null
        synchronized(cache) { cache[photo] }?.let { return it }
        val image = runCatching {
            val bytes = Base64.decode(photo, Base64.DEFAULT)
            if (!isJpeg(bytes)) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..maxSide || bounds.outHeight !in 1..maxSide) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull() ?: return null
        synchronized(cache) { cache[photo] = image }
        return image
    }

    /** What to do with the saved partner's kept photo, given who is in the room now. */
    sealed interface PartnerFace {
        data object Keep : PartnerFace
        data object Clear : PartnerFace
        data class Set(val photo: String) : PartnerFace
    }

    /**
     * The saved [partner]'s photo, from the [others] in the room ([fetched] by id): only when exactly one player still
     * there has their name, and, where both phones' ids are known, they match. If that player has no photo and the room's
     * server has the cap, the kept photo is cleared.
     */
    fun partnerFace(partner: SavedPartner, others: List<PlayerInfo>, caps: List<String>, fetched: Map<Int, String>): PartnerFace {
        val same = others.filter { !it.left && it.name == partner.name }
        val other = same.singleOrNull() ?: return PartnerFace.Keep
        if (partner.deviceId != null && other.deviceId != null && partner.deviceId != other.deviceId) return PartnerFace.Keep
        val id = other.photoId ?: return if (CAP in caps) PartnerFace.Clear else PartnerFace.Keep
        return fetched[id]?.let { PartnerFace.Set(it) } ?: PartnerFace.Keep
    }

    /** The photo a re-saved partner keeps: the old one only for the same name and the same phone (both ids known and equal). */
    fun carryOver(old: SavedPartner?, name: String, deviceId: String?): String? =
        old?.takeIf { it.name == name && deviceId != null && it.deviceId == deviceId }?.face

    /** What the app last sent the server about this player's own photo: for which room, and which photo (null: removed). */
    data class Sent(val code: String, val photo: String?)

    /** What to tell the server about this player's own photo. */
    sealed interface Share {
        data object Nothing : Share
        data class Send(val photo: String) : Share
        data object Remove : Share
    }

    /**
     * The one gate for sending a photo: nothing unless the room's server lists [CAP]. Then [mine] is sent if it has
     * not been sent to this room yet; with no photo, a photo the server holds ([myPhotoId]) is removed.
     */
    fun share(caps: List<String>, code: String, mine: String?, myPhotoId: Int?, last: Sent?): Share = when {
        CAP !in caps -> Share.Nothing
        last == Sent(code, mine) -> Share.Nothing
        mine == null -> if (myPhotoId == null) Share.Nothing else Share.Remove
        else -> Share.Send(mine)
    }
}
