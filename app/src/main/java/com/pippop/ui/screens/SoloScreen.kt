package com.pippop.ui.screens

import androidx.compose.foundation.layout.size
import com.pippop.ui.theme.Digits
import com.pippop.ui.theme.TextSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.art.color
import com.pippop.data.GameKind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pippop.game.SoloGame
import com.pippop.game.SoloStatus
import com.pippop.ui.SoloUiState
import com.pippop.ui.SoloViewModel
import com.pippop.ui.art.Outcome
import com.pippop.ui.theme.AppTheme

@Composable
fun SoloScreen(
    state: SoloUiState,
    vm: SoloViewModel,
    onHome: () -> Unit,
) {
    val p = AppTheme.palette
    val game = state.game

    // A running clock must never tick while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pause() }

    if (state.generating || game == null) {
        // After the app was restored with nothing to show, go back instead of loading forever.
        LaunchedEffect(state.generating, game == null) {
            if (!state.generating && game == null) onHome()
        }
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("", onHome)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PipPopLoader(Modifier.size(width = 96.dp, height = 60.dp))
                Text("Creating your puzzle…", color = p.text, modifier = Modifier.padding(top = 16.dp))
            }
        }
        return
    }

    val errors = game.wrongCells
    val paused = game.status == SoloStatus.PAUSED
    val playing = game.status == SoloStatus.PLAYING

    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesSheet(GameKind.SUDOKU) { showRules = false }
    var menuPaused by remember { mutableStateOf(false) }
    GameFrame(
        title = "Sudoku",
        subtitle = (if (game.shape.big) "${game.shape.label} · " else "") + "${game.difficulty.label} · Mistakes ${game.mistakes}/${game.maxMistakes}",
        players = emptyList(),
        glow = GameKind.SUDOKU.color,
        // Solo Sudoku pauses while the menu is open.
        menu = GameMenu(
            GameKind.SUDOKU, onRules = { showRules = true }, leaveLabel = "Home",
            onLeave = {
                vm.pause()
                onHome()
            },
            onOpen = { open ->
                if (open && !paused && game.isActive) {
                    vm.pause()
                    menuPaused = true
                } else if (!open && menuPaused) {
                    vm.resume()
                    menuPaused = false
                }
            },
        ),
        tools = if (game.status == SoloStatus.WON || game.status == SoloStatus.LOST) emptyList() else listOf(
            DockTool(if (paused) "Resume" else "Pause", if (paused) Icons.Filled.PlayArrow else GameIcons.Pause, enabled = game.isActive) {
                if (paused) vm.resume() else vm.pause()
            },
            DockTool("Fill all", GameIcons.FillAll, enabled = game.isActive && !paused) { vm.fillNotes(null) },
        ),
        trailing = {
            Text(style = Digits, text = formatDuration(game.elapsedMs), color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        },
        resultUnder = true,
        popup = when (game.status) {
            SoloStatus.WON -> popupContent {
                ResultPopup(
                    game = GameKind.SUDOKU,
                    outcome = Outcome.WIN,
                    title = "Well done!",
                    detail = "Solved in ${formatDuration(game.elapsedMs)} with ${game.mistakes} mistake" + if (game.mistakes == 1) "." else "s.",
                    finish = PopupAction("Home", onHome),
                    again = PopupAction("New puzzle", { vm.startNew(game.difficulty, game.shape) }),
                )
            }
            SoloStatus.LOST -> popupContent {
                ResultPopup(
                    game = GameKind.SUDOKU,
                    outcome = Outcome.LOSE,
                    title = "Game over",
                    detail = "${game.maxMistakes} mistakes. Try the same puzzle again or start a fresh one.",
                    finish = PopupAction("New puzzle", { vm.startNew(game.difficulty, game.shape) }),
                    again = PopupAction("Try again", vm::restart),
                )
            }
            else -> null
        },
    ) {
        BoardArea(1f) { size -> Box(size, contentAlignment = Alignment.Center) {
            if (game.shape.big) {
                BigSudokuGrid(board = game.board, errors = errors, selected = game.selected, hidden = paused, solution = null, onSelect = vm::select, onLongPress = vm::fillNotes, celebrate = true)
            } else {
                SudokuGrid(
                    board = game.board,
                    errors = errors,
                    selected = game.selected,
                    hidden = paused,
                    solution = null,
                    onSelect = vm::select,
                    onLongPress = vm::fillNotes,
                    celebrate = true,
                )
            }
            if (paused) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Paused", color = p.text, fontSize = TextSize.DisplayM, fontWeight = FontWeight.Bold)
                    PrimaryButton("Resume", vm::resume, Modifier.padding(horizontal = 48.dp))
                }
            }
        } }

        val over = game.status == SoloStatus.WON || game.status == SoloStatus.LOST
        if (!over) ToolRow(
            notesMode = game.notesMode,
            canUndo = game.board.canUndo,
            enabled = playing,
            hintsLeft = game.hintsLeft,
            onUndo = vm::undo,
            onErase = vm::erase,
            onNotes = vm::toggleNotes,
            onHint = vm::hint,
        )
        if (!over) NumberPad(
            remaining = remainingDigits(game.board, errors),
            enabled = playing && game.selected != null,
            onDigit = vm::enter,
        )
    }
}
