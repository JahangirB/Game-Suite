package com.arcade.sudoku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.ui.screens.LocalTutorialHost
import com.arcade.sudoku.ui.screens.TutorialHost
import com.arcade.sudoku.ui.screens.TutorialLayer
import com.arcade.sudoku.ui.screens.ArrangeGamesScreen
import com.arcade.sudoku.ui.screens.FirstRunScreen
import com.arcade.sudoku.ui.screens.ConnectKind
import com.arcade.sudoku.ui.screens.ConnectSheet
import com.arcade.sudoku.ui.screens.ConnectionBanners
import com.arcade.sudoku.ui.screens.LocalMatchSession
import com.arcade.sudoku.ui.screens.MatchSession
import com.arcade.sudoku.data.PlayMode
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.arcade.sudoku.ui.screens.SoloConnectFourScreen
import com.arcade.sudoku.ui.screens.SoloDotsScreen
import com.arcade.sudoku.ui.screens.SoloMemoryScreen
import com.arcade.sudoku.ui.screens.TurnGameScreen
import com.arcade.sudoku.ui.screens.SoloTttScreen
import com.arcade.sudoku.ui.screens.SoloDominoesScreen
import com.arcade.sudoku.ui.screens.DominoesGameScreen
import com.arcade.sudoku.ui.screens.NumberCrossScreen
import com.arcade.sudoku.ui.screens.HockeyScreen
import com.arcade.sudoku.ui.screens.ClashGameScreen
import com.arcade.sudoku.ui.screens.LudoGameScreen
import com.arcade.sudoku.ui.screens.GridGameScreen
import com.arcade.sudoku.ui.screens.BalloonGameScreen
import com.arcade.sudoku.ui.screens.BingoGameScreen
import com.arcade.sudoku.ui.screens.CodeGameScreen
import com.arcade.sudoku.ui.screens.SoloBingoScreen
import com.arcade.sudoku.ui.screens.GolfGameScreen
import com.arcade.sudoku.ui.screens.SoloGolfScreen
import com.arcade.sudoku.ui.screens.BlocksGameScreen
import com.arcade.sudoku.ui.screens.SoloBlocksScreen
import com.arcade.sudoku.ui.screens.SlideGameScreen
import com.arcade.sudoku.ui.screens.SoloSlideScreen
import com.arcade.sudoku.ui.screens.SoloCodeScreen
import com.arcade.sudoku.ui.screens.SoloBalloonScreen
import com.arcade.sudoku.ui.screens.SoloGridScreen
import com.arcade.sudoku.ui.screens.SoloLudoScreen
import com.arcade.sudoku.ui.screens.SoloClashScreen
import com.arcade.sudoku.ui.screens.SoloHockeyScreen
import com.arcade.sudoku.ui.screens.SoloNumbersScreen
import com.arcade.sudoku.ui.screens.SoloWordsScreen
import com.arcade.sudoku.ui.screens.WordRaceScreen
import com.arcade.sudoku.ui.screens.DrawGuessScreen
import com.arcade.sudoku.ui.screens.GameScreen
import com.arcade.sudoku.ui.screens.HomeScreen
import com.arcade.sudoku.ui.screens.LobbyScreen
import com.arcade.sudoku.ui.screens.ProfileScreen
import com.arcade.sudoku.ui.screens.ShipsGameScreen
import com.arcade.sudoku.ui.screens.SoloScreen
import com.arcade.sudoku.ui.screens.SoloShipsScreen
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.ui.screens.UpdateDialog
import com.arcade.sudoku.ui.art.ReactionBurst
import com.arcade.sudoku.ui.screens.LocalShipStyle
import com.arcade.sudoku.ui.theme.AppTheme

private enum class Dest { HOME, PROFILE, ARRANGE, SOLO, SHIPS, CONNECT4, DOTS, MEMORY, WORDS, TTT, DOMINOES, NUMBERS, HOCKEY, CLASH, LUDO, GRID, BALLOON, CODE, SLIDE, BINGO, GOLF, BLOCKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcadeSudokuApp(
    app: AppViewModel = viewModel(),
    battle: BattleViewModel = viewModel(),
    solo: SoloViewModel = viewModel(),
    updates: UpdateViewModel = viewModel(),
    ships: SoloShipsViewModel = viewModel(),
    connectFour: SoloConnectFourViewModel = viewModel(),
    dots: SoloDotsViewModel = viewModel(),
    memory: SoloMemoryViewModel = viewModel(),
    words: SoloWordsViewModel = viewModel(),
    ttt: SoloTttViewModel = viewModel(),
    dominoes: SoloDominoesViewModel = viewModel(),
    numbers: SoloNumbersViewModel = viewModel(),
    hockey: SoloHockeyViewModel = viewModel(),
    clash: SoloClashViewModel = viewModel(),
    ludo: SoloLudoViewModel = viewModel(),
    grid: SoloGridViewModel = viewModel(),
    balloon: SoloBalloonViewModel = viewModel(),
    codeBreaker: SoloCodeViewModel = viewModel(),
    slide: SoloSlideViewModel = viewModel(),
    bingo: SoloBingoViewModel = viewModel(),
    golf: SoloGolfViewModel = viewModel(),
    blocks: SoloBlocksViewModel = viewModel(),
) {
    val p = AppTheme.palette
    val profile by app.profile.collectAsStateWithLifecycle()
    val battleState by battle.state.collectAsStateWithLifecycle()
    val soloState by solo.state.collectAsStateWithLifecycle()
    val updateState by updates.state.collectAsStateWithLifecycle()
    val shipsState by ships.state.collectAsStateWithLifecycle()
    val connectFourState by connectFour.state.collectAsStateWithLifecycle()
    val dotsState by dots.state.collectAsStateWithLifecycle()
    val memoryState by memory.state.collectAsStateWithLifecycle()
    val wordsState by words.state.collectAsStateWithLifecycle()
    val tttState by ttt.state.collectAsStateWithLifecycle()
    val dominoesState by dominoes.state.collectAsStateWithLifecycle()
    val numbersState by numbers.state.collectAsStateWithLifecycle()
    val hockeyState by hockey.state.collectAsStateWithLifecycle()
    val clashState by clash.state.collectAsStateWithLifecycle()
    val ludoState by ludo.state.collectAsStateWithLifecycle()
    val gridState by grid.state.collectAsStateWithLifecycle()
    val balloonState by balloon.state.collectAsStateWithLifecycle()
    val codeState by codeBreaker.state.collectAsStateWithLifecycle()
    val slideState by slide.state.collectAsStateWithLifecycle()
    val bingoState by bingo.state.collectAsStateWithLifecycle()
    val golfState by golf.state.collectAsStateWithLifecycle()
    val blocksState by blocks.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dest by rememberSaveable { mutableStateOf(Dest.HOME) }
    // The home's connect sheet: a nearby room, or joining an online one.
    var connectSheet by rememberSaveable { mutableStateOf<ConnectKind?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    // Leaving the room altogether (from the home, while connected).
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(battleState.message) {
        battleState.message?.let {
            snackbar.showSnackbar(it)
            battle.dismissMessage()
        }
    }

    // Once connected, the sheet has done its job: the home shows who you're with.
    LaunchedEffect(battleState.room?.code) {
        if (battleState.room != null) connectSheet = null
    }

    LaunchedEffect(updateState.message) {
        updateState.message?.let {
            snackbar.showSnackbar(it)
            updates.dismissMessage()
        }
    }

    val room = battleState.room
    // Your screen is on the room's game; otherwise you're in the menu and still connected.
    val inRoomGame = room != null && room.atGame
    val requestLeave: () -> Unit = {
        val inMatch = room?.status == RoomStatus.COUNTDOWN || room?.status == RoomStatus.PLAYING
        when {
            // Back from a lobby or a result goes to the games and never disconnects.
            room?.inSession == true && !inMatch -> battle.backToGames()
            // Leaving a match forfeits it and a host leaving a one-game room closes it, so ask first.
            room?.status == RoomStatus.FINISHED -> battle.leave()
            else -> confirmLeave = true
        }
    }
    BackHandler(enabled = inRoomGame, onBack = requestLeave)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { battle.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { battle.setForeground(false) }
    BackHandler(enabled = !inRoomGame && dest != Dest.HOME) {
        if (dest == Dest.SOLO) solo.pause()
        dest = Dest.HOME
    }

    val tutorials = remember { TutorialHost() }
    CompositionLocalProvider(LocalTutorialHost provides tutorials) {
        Scaffold(
            containerColor = p.background,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            // The IME insets shrink every screen above the keyboard, so a focused field stays visible.
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                // A result in a connection that plays game after game shows the score since
                // connecting and a couple of games to play next.
                val session = room?.takeIf { it.inSession }?.let { r ->
                    MatchSession(
                        tally = battleState.tally,
                        nextGames = profile.gameOrder.filter { it != r.game && it.maxPlayers >= r.present.size + 1 },
                        onOpenGame = battle::openGame,
                    )
                }
                CompositionLocalProvider(LocalShipStyle provides profile.shipStyle, LocalMatchSession provides session) {
                    when {
                        !profile.firstRunDone && room == null -> FirstRunScreen(profile, app, onDone = app::finishFirstRun)
                        inRoomGame && room != null && (room.status == RoomStatus.WAITING || room.status == RoomStatus.LOBBY) ->
                            LobbyScreen(battleState, profile, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.SHIPS -> ShipsGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.SLIDE -> SlideGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.BINGO -> BingoGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.GOLF -> GolfGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.BLOCKS -> BlocksGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.CODE -> CodeGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.BALLOON -> BalloonGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.GRID -> GridGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.LUDO -> LudoGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.CLASH -> ClashGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.HOCKEY -> HockeyScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.NUMBERS -> NumberCrossScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.DOMINOES -> DominoesGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game.turnBased -> TurnGameScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.WORDS -> WordRaceScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null && room.game == GameKind.DRAW -> DrawGuessScreen(battleState, battle, onLeave = requestLeave)
                        inRoomGame && room != null -> GameScreen(battleState, battle, onLeave = requestLeave)
                        dest == Dest.SHIPS -> SoloShipsScreen(shipsState, ships, onHome = { dest = Dest.HOME })
                        dest == Dest.CONNECT4 -> SoloConnectFourScreen(connectFourState, connectFour, onHome = { dest = Dest.HOME })
                        dest == Dest.DOTS -> SoloDotsScreen(dotsState, dots, onHome = { dest = Dest.HOME })
                        dest == Dest.MEMORY -> SoloMemoryScreen(memoryState, memory, onHome = { dest = Dest.HOME })
                        dest == Dest.WORDS -> SoloWordsScreen(wordsState, words, onHome = { dest = Dest.HOME })
                        dest == Dest.TTT -> SoloTttScreen(tttState, ttt, onHome = { dest = Dest.HOME })
                        dest == Dest.DOMINOES -> SoloDominoesScreen(dominoesState, dominoes, onHome = { dest = Dest.HOME })
                        dest == Dest.NUMBERS -> SoloNumbersScreen(numbersState, numbers, onHome = { dest = Dest.HOME })
                        dest == Dest.HOCKEY -> SoloHockeyScreen(hockeyState, hockey, onHome = { dest = Dest.HOME })
                        dest == Dest.CLASH -> SoloClashScreen(clashState, clash, onHome = { dest = Dest.HOME })
                        dest == Dest.LUDO -> SoloLudoScreen(ludoState, ludo, onHome = { dest = Dest.HOME })
                        dest == Dest.GRID -> SoloGridScreen(gridState, grid, onHome = { dest = Dest.HOME })
                        dest == Dest.BALLOON -> SoloBalloonScreen(balloonState, balloon, onHome = { dest = Dest.HOME })
                        dest == Dest.CODE -> SoloCodeScreen(codeState, codeBreaker, onHome = { dest = Dest.HOME })
                        dest == Dest.SLIDE -> SoloSlideScreen(slideState, slide, onHome = { dest = Dest.HOME })
                        dest == Dest.BINGO -> SoloBingoScreen(bingoState, bingo, onHome = { dest = Dest.HOME })
                        dest == Dest.GOLF -> SoloGolfScreen(golfState, golf, onHome = { dest = Dest.HOME })
                        dest == Dest.BLOCKS -> SoloBlocksScreen(blocksState, blocks, onHome = { dest = Dest.HOME })
                        dest == Dest.SOLO -> SoloScreen(soloState, solo, onHome = { dest = Dest.HOME })
                        dest == Dest.PROFILE -> ProfileScreen(
                            profile, battleState.busy, app, battle, updateState,
                            onCheckUpdates = { updates.check(manual = true) },
                            onUpdate = updates::update,
                            onBack = { dest = Dest.HOME },
                        )
                        dest == Dest.ARRANGE -> ArrangeGamesScreen(profile.gameOrder, app::setGameOrder, onBack = { dest = Dest.HOME })
                        else -> HomeScreen(
                            solo = soloState,
                            profile = profile,
                            onContinue = {
                                solo.resume()
                                dest = Dest.SOLO
                            },
                            onNewGame = {
                                solo.startNew(it)
                                dest = Dest.SOLO
                            },
                            onProfile = { dest = Dest.PROFILE },
                            onSolo = { game ->
                                dest = when (game) {
                                    GameKind.SHIPS -> Dest.SHIPS
                                    GameKind.CONNECT4 -> Dest.CONNECT4
                                    GameKind.DOTS -> Dest.DOTS
                                    GameKind.MEMORY -> Dest.MEMORY
                                    GameKind.WORDS -> Dest.WORDS
                                    GameKind.TTT -> Dest.TTT
                                    GameKind.DOMINOES -> Dest.DOMINOES
                                    GameKind.NUMBERS -> Dest.NUMBERS
                                    GameKind.HOCKEY -> Dest.HOCKEY
                                    GameKind.CLASH -> Dest.CLASH
                                    GameKind.LUDO -> Dest.LUDO
                                    GameKind.GRID -> Dest.GRID
                                    GameKind.BALLOON -> Dest.BALLOON
                                    GameKind.CODE -> Dest.CODE
                                    GameKind.SLIDE -> Dest.SLIDE
                                    GameKind.BINGO -> Dest.BINGO
                                    GameKind.GOLF -> Dest.GOLF
                                    GameKind.BLOCKS -> Dest.BLOCKS
                                    // Draw & Guess needs two players; its sheet offers no solo game.
                                    GameKind.DRAW -> Dest.HOME
                                    GameKind.SUDOKU -> Dest.SOLO
                                }
                            },
                            onArrange = { dest = Dest.ARRANGE },
                            partnerBusy = battleState.busy,
                            onPlayWithPartner = battle::playWithPartner,
                            onInvitePartner = battle::invitePartner,
                            onPlayMode = app::setPlayMode,
                            connected = room != null,
                            onConnectNearby = { connectSheet = ConnectKind.NEARBY },
                            onStartRoom = battle::host,
                            onJoinRoom = { connectSheet = ConnectKind.JOIN_ONLINE },
                            room = room,
                            nearby = battleState.mode == BattleMode.NEARBY,
                            onOpenGame = battle::openGame,
                            onLeaveRoom = { confirmDisconnect = true },
                            onNotificationsAnswered = app::notificationsAnswered,
                            onTutorialSeen = app::tutorialSeen,
                        )
                    }
                }
                // A reaction from your opponent plays large over the battle screen.
                if (inRoomGame) ReactionBurst(battleState.opponentReaction)
                if (inRoomGame) ConnectionBanners(battleState, onBack = requestLeave, modifier = Modifier.align(Alignment.TopCenter))
                // A game's tutorial, over everything, in the same safe area as every screen.
                TutorialLayer(tutorials)
            }
        }
    }

    battleState.problem?.let { problem ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = battle::dismissProblem,
            containerColor = p.card,
            title = { Text(problem.title, color = p.text) },
            text = { Text(problem.why, color = p.muted) },
            confirmButton = {
                val fix = problem.fix
                if (fix != null) {
                    TextButton(onClick = {
                        battle.dismissProblem()
                        when (fix) {
                            Fix.BLUETOOTH -> context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            Fix.PERMISSION -> context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                            Fix.GO_OFFLINE -> {
                                app.setPlayMode(PlayMode.OFFLINE)
                                connectSheet = ConnectKind.NEARBY
                            }
                            Fix.EDIT_CODE -> connectSheet = if (battleState.mode == BattleMode.NEARBY || profile.playMode == PlayMode.OFFLINE) ConnectKind.NEARBY else ConnectKind.JOIN_ONLINE
                        }
                    }) { Text(fix.label, color = p.primary) }
                }
            },
            dismissButton = { TextButton(onClick = battle::dismissProblem) { Text(if (problem.fix == null) "OK" else "Not now", color = p.muted) } },
        )
    }

    connectSheet?.let { kind ->
        ModalBottomSheet(onDismissRequest = { connectSheet = null }, containerColor = p.card) {
            ConnectSheet(kind, battleState, battle)
        }
    }

    // Never interrupt a battle with the update offer.
    if (room == null && (updateState.showOffer || updateState.needsPermission)) {
        UpdateDialog(updateState, onUpdate = updates::update, onAllow = updates::allowInstalls, onLater = updates::dismiss)
    }

    if (confirmDisconnect && room != null) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            containerColor = p.card,
            title = { Text("Leave the room?", color = p.text) },
            text = { Text("You'll disconnect. A game you're in ends for everyone in it.", color = p.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    battle.leave()
                }) { Text("Leave", color = p.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Stay", color = p.primary) } },
        )
    }

    if (confirmLeave && room != null && room.inSession) {
        // Back to the games during a match: you stay connected but the match is lost.
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = p.card,
            title = { Text("Give up this game?", color = p.text) },
            text = { Text("It counts as a loss. You stay connected and go back to the games.", color = p.muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    battle.backToGames()
                }) { Text("Give up", color = p.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep playing", color = p.primary) } },
        )
    }

    if (confirmLeave && room != null && !room.inSession) {
        val inMatch = room.status == RoomStatus.COUNTDOWN || room.status == RoomStatus.PLAYING
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = p.card,
            title = { Text("Leave the game?", color = p.text) },
            text = {
                Text(
                    when {
                        inMatch -> "Leaving now counts as a loss."
                        room.isHost -> "Everyone else is disconnected."
                        else -> "You can join again with the same code."
                    },
                    color = p.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    battle.leave()
                }) { Text("Leave", color = p.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay", color = p.primary) } },
        )
    }
}
