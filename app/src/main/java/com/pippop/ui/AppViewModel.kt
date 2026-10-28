package com.pippop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pippop.data.Avatar
import com.pippop.data.GameKind
import com.pippop.data.PlayMode
import com.pippop.data.Profile
import com.pippop.data.ProfileStore
import com.pippop.data.ShipStyle
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
    fun setTheme(mode: ThemeMode) = store.setTheme(mode)
    fun setShipStyle(style: ShipStyle) = store.setShipStyle(style)
    fun setPlayMode(mode: PlayMode) = store.setPlayMode(mode)
    fun setGameOrder(order: List<GameKind>?) = store.setGameOrder(order)
    fun finishFirstRun() = store.finishFirstRun()
    fun setVibration(on: Boolean) = store.setVibration(on)
    fun setSound(on: Boolean) = store.setSound(on)
    fun notificationsAnswered() = store.notificationsAnswered()
    fun tutorialSeen(kind: GameKind) = store.tutorialSeen(kind)
}
