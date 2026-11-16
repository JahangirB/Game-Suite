package com.pippop.ui

import com.pippop.ui.screens.Tone
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pippop.BuildConfig
import com.pippop.data.QuietEntry
import com.pippop.data.QuietHints
import com.pippop.data.QuietStore
import com.pippop.data.RunningApp
import com.pippop.data.UpdateInfo
import com.pippop.data.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val available: UpdateInfo? = null,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    /** The user chose "Later"; the offer comes back on the next launch. */
    val dismissed: Boolean = false,
    /** Android needs the user to allow this app to install updates. */
    val needsPermission: Boolean = false,
    val message: String? = null,
    val messageTone: Tone = Tone.Neutral,
    /** The message stays longer (the note about an update that happened while the app was closed). */
    val messageLong: Boolean = false,
    /** The "Update by itself" setting. */
    val quietOn: Boolean = true,
    /** Whether this Android can update by itself at all (12 and later); the switch is hidden otherwise. */
    val quietAvailable: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
    /** What the quiet updater did lately, newest first. */
    val quietLog: List<QuietEntry> = emptyList(),
    /** The version that could not install by itself because Android wanted a tap; the offer says so. */
    val quietTapFor: Int? = null,
) {
    val showOffer get() = available != null && !dismissed
}

/** Looks for a newer release when the app starts, and installs it when the user agrees. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = Updater(app)
    private val quiet = QuietStore.get(app)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloaded: Pair<Int, File>? = null

    init {
        _state.update { it.copy(quietOn = quiet.view.value.enabled, quietLog = quiet.view.value.entries) }
        takeHint()
        viewModelScope.launch {
            quiet.view.collect { v -> _state.update { it.copy(quietOn = v.enabled, quietLog = v.entries) } }
        }
        check(manual = false)
    }

    /** What happened while the app was closed, said once as a toast. Called when the app opens and each time it comes back. */
    fun takeHint() {
        val hint = QuietHints.take(quiet, RunningApp(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME))
        _state.update {
            val tap = QuietHints.neededTapFor(quiet)
            if (hint == null) it.copy(quietTapFor = tap)
            else it.copy(message = hint.text, messageTone = if (hint.good) Tone.Good else Tone.Warning, messageLong = true, quietTapFor = tap)
        }
    }

    /** The game going or not, as the quiet updater should know it when the app leaves the front. */
    fun gameGoing(going: Boolean) {
        quiet.gameGoing = going
    }

    fun check(manual: Boolean) {
        if (_state.value.checking) return
        // Builds made outside CI don't know which repository to update from.
        if (BuildConfig.UPDATE_BASE_URL.isEmpty()) {
            if (manual) _state.update { it.copy(message = "This build doesn't update itself.", messageTone = Tone.Warning) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            try {
                val latest = updater.latest()
                _state.update {
                    if (latest.isNewer) {
                        it.copy(available = latest, dismissed = false)
                    } else {
                        // Never wipe a message that is waiting to be shown (the quiet update's note).
                        if (manual) it.copy(available = null, message = "You have the latest version.", messageTone = Tone.Good) else it.copy(available = null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed automatic check is silent; the game works offline.
                if (manual) _state.update { it.copy(message = "Could not check for updates. Are you online?", messageTone = Tone.Bad) }
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    fun dismiss() = _state.update { it.copy(dismissed = true, needsPermission = false) }
    fun dismissMessage() = _state.update { it.copy(message = null, messageLong = false) }

    /** The profile's "Update by itself" switch. */
    fun setQuiet(on: Boolean) {
        quiet.enabled = on
    }

    fun update() {
        val info = _state.value.available ?: return
        if (_state.value.downloading) return
        if (!updater.canInstall()) {
            _state.update { it.copy(needsPermission = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, progress = 0f, needsPermission = false) }
            try {
                val apk = downloaded?.takeIf { it.first == info.versionCode && it.second.exists() }?.second
                    ?: updater.download { p -> _state.update { it.copy(progress = p) } }
                        .also { downloaded = info.versionCode to it }
                updater.start(updater.installIntent(apk))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Update failed: ${e.message ?: "network problem"}", messageTone = Tone.Bad) }
            } finally {
                _state.update { it.copy(downloading = false) }
            }
        }
    }

    /** Opens Android's "install unknown apps" switch for this app. */
    fun allowInstalls() {
        _state.update { it.copy(needsPermission = false) }
        updater.start(updater.permissionIntent())
    }
}
