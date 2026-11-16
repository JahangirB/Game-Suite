package com.pippop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.Profile
import com.pippop.data.ProfileStore
import com.pippop.data.ShipStyle
import com.pippop.data.ColourTheme
import com.pippop.data.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.pippop.push.Push

/** The profile screen's settings: who you are, which server you use and how the app looks. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore.get(app)

    val profile: StateFlow<Profile> = store.profile

    init {
        // Keep the battle servers up to date with this phone's push address.
        viewModelScope.launch { Push.registerEverywhere(app) }
    }

    fun setName(value: String) = store.setName(value)
    fun setScriptUrl(value: String) = store.setScriptUrl(value)
    fun setServerPassword(value: String) = store.setServerPassword(value)
    fun setAvatar(value: Avatar?) = store.setAvatar(value)
    /** Your own photo ([com.pippop.data.Faces]: a base64 JPEG), or null to remove it. */
    fun setFace(photo: String?) = store.setFace(photo)
    fun setTheme(mode: ThemeMode) = store.setTheme(mode)
    fun setColourTheme(theme: ColourTheme) = store.setColourTheme(theme)
    fun setShipStyle(style: ShipStyle) = store.setShipStyle(style)
    fun setPlayMode(mode: PlayMode) = store.setPlayMode(mode)
    /** The arrange screen's result: [order] holds every game, and the ones in [putAway] (below its line) are back in the Store. */
    fun setArrangement(order: List<GameKind>?, putAway: Set<GameKind>) = store.setShelf((order ?: GameKind.entries).filter { it !in putAway })
    fun addToShelf(game: GameKind) = store.addToShelf(game)
    fun removeFromShelf(game: GameKind) = store.removeFromShelf(game)
    fun moveOnShelf(from: Int, to: Int) = store.moveOnShelf(from, to)
    /** The one-time "The other games are in the Game Store" note has been seen. */
    fun markShelfNoteShown() = store.markShelfNoteShown()
    fun noteGamePlayed(game: GameKind) = store.noteGamePlayed(game)
    /** Remembers how a game was last set up to play alone. */
    fun setSoloSetup(game: GameKind, values: Map<String, String>) = store.setSoloSetup(game, values)
    fun finishFirstRun() = store.finishFirstRun()
    fun setVibration(on: Boolean) = store.setVibration(on)
    fun setLoudness(value: com.pippop.data.Loudness) = store.setLoudness(value)
    fun setOthersMoves(on: Boolean) = store.setOthersMoves(on)
    fun notificationsAnswered() = store.notificationsAnswered()
    fun tutorialSeen(kind: GameKind) = store.tutorialSeen(kind)
    fun setCupShown(month: java.time.YearMonth) = store.setCupShown(month)
}
