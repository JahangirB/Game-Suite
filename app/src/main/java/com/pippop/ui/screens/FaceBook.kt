package com.pippop.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.pippop.data.Faces
import com.pippop.data.RoomState
import com.pippop.data.SavedPartner

/**
 * The photos [PlayerAvatar] can show, found by the player's name (the label "You" is this player). A name two players in
 * the room share has no photo, and a photo that is missing or cannot be read is simply not here, so the animal shows.
 */
class FaceBook(private val byName: Map<String, ImageBitmap> = emptyMap(), private val mine: ImageBitmap? = null) {
    fun of(name: String): ImageBitmap? = if (name == YOU) mine else byName[name]

    companion object {
        const val YOU = "You"
        val None = FaceBook()

        /**
         * Your own photo is always shown on your own phone. In a room, the others' photos are the ones fetched for
         * the ids the room state carries ([fetched], by id); the saved partner's last photo shows for them outside a room.
         */
        fun build(myName: String, myFace: String?, partner: SavedPartner?, room: RoomState?, fetched: Map<Int, String>): FaceBook {
            val mine = Faces.decode(myFace)
            val map = HashMap<String, ImageBitmap>()
            var sharedNames = emptySet<String>()
            val kept = partner?.takeIf { it.name.isNotBlank() && it.name != myName }?.let { p -> Faces.decode(p.face)?.let { p.name to it } }
            if (room == null) {
                kept?.let { map[it.first] = it.second }
            } else {
                val names = (listOf(room.me.name) + room.others.map { it.name })
                val shared = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                for (other in room.others) {
                    val face = other.photoId?.let { fetched[it] }?.let { Faces.decode(it) } ?: continue
                    map[other.name] = face
                }
                // The saved partner's last photo, only for a name nobody in the room has (a score kept after they left).
                if (kept != null && kept.first !in names) map[kept.first] = kept.second
                shared.forEach { map.remove(it) }
                sharedNames = shared
            }
            // Another player with your name shows their own photo or their animal, never yours.
            if (mine != null && myName.isNotBlank() && !(room != null && myName in sharedNames)) map[myName] = mine
            return FaceBook(map, mine)
        }
    }
}

/** The photos shown in place of animals; empty unless the app provides them (it does, at its root). */
val LocalFaces = staticCompositionLocalOf { FaceBook.None }
