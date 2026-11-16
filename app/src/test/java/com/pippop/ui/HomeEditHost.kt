package com.pippop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.Profile
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.Tone
import com.pippop.ui.screens.Toast
import com.pippop.ui.theme.LocalReduceMotion

/**
 * The home as the app wires it, for the shelf-editing tests: the shelf lives in state and the callbacks do what the
 * view model does (remove, move one place to another, put back), and a removal's toast shows with its Undo button.
 */
class HomeEditHost(start: List<GameKind>) {
    var shelf by mutableStateOf(start)
    val moves = mutableListOf<Pair<Int, Int>>()
    val opened = mutableListOf<GameKind>()
    val messages = mutableListOf<String>()
    var profileOpened = 0
    var scoresOpened = 0
    var toast by mutableStateOf<Pair<String, () -> Unit>?>(null)
    var lastUndo: (() -> Unit)? = null

    @Composable
    fun Content(reduceMotion: Boolean = true) {
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    solo = SoloUiState(),
                    profile = Profile(name = "Sam", shelf = shelf, tutorialsSeen = GameKind.entries.toSet()),
                    onContinue = {}, onProfile = { profileOpened++ }, onSolo = { g, _ -> opened += g }, onScores = { scoresOpened++ },
                    onRemoveFromShelf = { g -> shelf = shelf - g },
                    onMoveOnShelf = { from, to ->
                        moves += from to to
                        shelf = shelf.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    onRestoreToShelf = { g, at -> shelf = shelf.toMutableList().apply { add(at.coerceIn(0, size), g) } },
                    partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {}, connected = false, onConnect = {},
                    onMessage = { messages += it },
                    onUndoable = { m, undo -> lastUndo = undo; toast = m to undo },
                    now = 0L,
                )
                toast?.let { (text, undo) ->
                    Toast(text, Tone.Good, Modifier.align(Alignment.BottomCenter).padding(16.dp), actionLabel = "Undo", onAction = { undo(); toast = null })
                }
            }
        }
    }
}
