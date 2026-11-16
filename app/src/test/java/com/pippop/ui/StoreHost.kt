package com.pippop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.ui.screens.GamePageScreen
import com.pippop.ui.screens.StoreFilter
import com.pippop.ui.screens.StoreScreen
import com.pippop.ui.screens.Tone
import com.pippop.ui.screens.Toast
import com.pippop.ui.screens.backInStoreToast
import com.pippop.ui.screens.onShelfToast

/**
 * The Store as the app wires it, for the Store's tests: the shelf lives in state; Get and Add put a game on it with the
 * toast, Remove takes it off, and "Play"/"Open" are recorded.
 */
class StoreHost(start: List<GameKind>, filter: StoreFilter = StoreFilter.ALL, page: GameKind? = null, toast: String? = null, val roomSeats: Int? = null, val roomGame: GameKind? = null) {
    var shelf by mutableStateOf(start)
    var filter by mutableStateOf(filter)
    var page by mutableStateOf(page)
    var toast by mutableStateOf(toast)
    var backed = 0
    val played = mutableListOf<GameKind>()

    @Composable
    fun Content() {
        Box(Modifier.fillMaxSize()) {
            val open = page
            if (open == null) {
                StoreScreen(
                    shelf, filter, { filter = it }, onBack = { backed++ },
                    onGet = { g -> shelf = shelf + g; toast = onShelfToast(g) },
                    onOpenPage = { page = it }, onOpen = { played += it }, roomSeats = roomSeats, roomGame = roomGame,
                )
            } else {
                GamePageScreen(
                    open, open in shelf, onBack = { page = null },
                    onAdd = { shelf = shelf + open; toast = onShelfToast(open) },
                    onRemove = { shelf = shelf - open; toast = backInStoreToast(open) },
                    onPlay = { played += open }, roomSeats = roomSeats, roomGame = roomGame,
                )
            }
            toast?.let { Toast(it, Tone.Good, Modifier.align(Alignment.BottomCenter).padding(16.dp)) }
        }
    }
}
