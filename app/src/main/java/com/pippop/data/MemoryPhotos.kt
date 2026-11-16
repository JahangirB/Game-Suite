package com.pippop.data

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Memory Pairs with your own photos. Whoever opened the game picks as many photos as the deck has pairs; each is cropped
 * square, made [SIZE] x [SIZE] and compressed to a JPEG of at most [MAX_BASE64] characters, and sent once, to a server
 * whose room state lists the cap [CAP] while every player has the token [CAP] (`RoomState.memoryPhotosOn`). Phones fetch
 * each photo once by the id the room state carries per pair (`RoomState.memoryPhotoIds`). Until a pair's photo has
 * arrived its cards show the pair's plain animal.
 */
object MemoryPhotos {
    /** The server cap, and the token an app adds to the games it says it knows; photos are used only where both are there. */
    const val CAP = "memory-photos"

    /** The sides of a photo, in pixels. A card is about 90 dp wide, so this is sharp on any phone. */
    const val SIZE = 160

    /**
     * The most base64 characters one photo may take; the servers refuse more. A 160 x 160 JPEG of a busy photo is about
     * 9,000 to 13,000 bytes at quality 85, which is 12,000 to 17,000 characters; at quality 45, where the quality ladder stops, about half
     * that. So 16,000 is never a problem for [fromBitmap]'s ladder, and a deck of 18 is under 290,000 characters.
     */
    const val MAX_BASE64 = 16_000

    /** The most a received photo may declare as its width or height; ours are [SIZE], so anything bigger is not one of ours. */
    const val MAX_SIDE = 256

    /** The most photos a deck needs: the pairs of the biggest deck (36 cards). */
    const val MAX_PAIRS = 18

    /** Whether the deck may be the players' photos: the server has the cap and says every player has the token. */
    fun offered(caps: List<String>, everyoneHasToken: Boolean): Boolean = CAP in caps && everyoneHasToken

    /** Photos to ask the picker for: as many as the deck has pairs ([cards] cards). */
    fun pairsFor(cards: Int): Int = cards / 2

    /** The photo for one place, made from [uri] off the main thread ([Faces.fromUri] at this size); null if it cannot be read. */
    suspend fun fromUri(context: Context, uri: Uri): String? = Faces.fromUri(context, uri, SIZE, MAX_BASE64)

    /** The photo for one place, made from a bitmap already in memory (tests, and what [fromUri] ends with). */
    fun fromBitmap(source: android.graphics.Bitmap): String? = Faces.squareJpeg(source, SIZE, MAX_BASE64)

    private val decoded = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > MAX_PAIRS + 6
    }

    /** A picture [decode] already made, or null; never decodes, so it is safe on the main thread. */
    fun peek(photo: String?): ImageBitmap? = photo?.let { synchronized(decoded) { decoded[it] } }

    /**
     * The picture in [photo] (base64), or null if it is not a usable one: too long, damaged, not a JPEG, or declaring a
     * side over [MAX_SIDE] (checked before anything is decoded). Decoding is work: call it off the main thread
     * (Dispatchers.Default). Results are kept, for a whole deck.
     */
    fun decode(photo: String?): ImageBitmap? = Faces.decodeInto(decoded, photo, MAX_BASE64, MAX_SIDE)

    /**
     * What to do about the photo ids the room state names: the ids in [ids] not yet [asked] are fetched, once each;
     * [asked] is updated. A photo that is replaced or removed gets a new id, so it is fetched once more.
     */
    fun toFetch(ids: List<Int?>, asked: MutableSet<Int>): List<Int> = ids.filterNotNull().filter { asked.add(it) }

    /** How long the app waits between two photos it sends: a little more than the servers' pace limit (150 ms). */
    const val SEND_GAP_MS = 200L

    /**
     * Where each photo goes when [picked] photos were chosen for a deck of [pairs] pairs whose places hold [ids]:
     * a list of (place, index of the picked photo, or null to empty the place). With [onlyMissing] the picked photos
     * fill the empty places in order ("Choose three more"); otherwise they replace the set from the first place on, and
     * any places after them are emptied, so the deck is never part old and part new. Photos beyond the deck's places are not used.
     */
    fun plan(pairs: Int, ids: List<Int?>, picked: Int, onlyMissing: Boolean): List<Pair<Int, Int?>> =
        if (onlyMissing) (0 until pairs).filter { ids.getOrNull(it) == null }.take(picked).mapIndexed { n, slot -> slot to n }
        else (0 until pairs).map { slot -> slot to slot.takeIf { it < picked } }.filter { (slot, from) -> from != null || ids.getOrNull(slot) != null }

    /** "3 of 8 sent": how far the sending of a deck's photos has got. */
    fun progress(have: Int, pairs: Int) = "$have of $pairs sent"

    /** What to say under the photos: how many the deck needs, and, while some are missing, how many are still to pick. */
    fun note(pairs: Int, have: Int): String {
        val deck = "${count(pairs)} make ${cardWord(pairs * 2)} cards."
        val missing = pairs - have
        return when {
            missing <= 0 -> deck
            have == 0 -> "$deck Choose them to start."
            else -> "$deck Choose ${word(missing)} more to start."
        }
    }

    /** What the Ready bar says to the opener while photos are missing: all of them to choose, or how many more. */
    fun readyLine(pairs: Int, have: Int): String = if (have == 0) "Choose the photos to start." else "Choose ${word(pairs - have)} more to start."

    /**
     * What the picker does and asks for, from the places now filled: [fillMissing] (some, not all, are filled: the pick fills the
     * empty ones) and the most photos to ask the phone's picker for. The picker needs a choice of at least two.
     */
    fun pickRule(pairs: Int, have: Int): Pair<Boolean, Int> {
        val fillMissing = have in 1 until pairs
        return fillMissing to if (fillMissing) maxOf(pairs - have, 2) else pairs
    }

    /** The picker's button: choose them all, the ones still missing, or others instead of a full set. */
    fun buttonLabel(pairs: Int, have: Int): String = when {
        have >= pairs -> "Choose other photos"
        have == 0 -> "Choose photos"
        else -> "Choose ${word(pairs - have)} more"
    }

    private val WORDS = mapOf(1 to "One", 2 to "Two", 3 to "Three", 4 to "Four", 5 to "Five", 6 to "Six", 7 to "Seven", 8 to "Eight", 9 to "Nine", 10 to "Ten", 11 to "Eleven", 12 to "Twelve", 13 to "Thirteen", 14 to "Fourteen", 15 to "Fifteen", 16 to "Sixteen", 17 to "Seventeen", 18 to "Eighteen")
    private fun word(n: Int) = WORDS[n]?.lowercase() ?: "$n"
    private fun count(n: Int) = (WORDS[n] ?: "$n") + if (n == 1) " photo" else " photos"
    private fun cardWord(n: Int) = word(n)
}
