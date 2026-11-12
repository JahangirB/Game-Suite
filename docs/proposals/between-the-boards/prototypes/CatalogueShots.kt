@file:OptIn(ExperimentalRoborazziApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pippop.ui.proto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.pippop.ui.SoloC4UiState
import com.pippop.ui.SoloConnectFourViewModel
import com.pippop.ui.SoloGolfUiState
import com.pippop.ui.SoloGolfViewModel
import com.pippop.ui.art.KeyArt
import com.pippop.ui.art.tagline
import com.pippop.ui.screens.PrimaryButton
import com.pippop.ui.screens.SecondaryButton
import com.pippop.ui.screens.SoloConnectFourScreen
import com.pippop.ui.screens.SoloGolfScreen
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.pippop.data.Avatar
import com.pippop.data.Difficulty
import com.pippop.data.GameKind
import com.pippop.data.MatchRecord
import com.pippop.data.PlayMode
import com.pippop.data.PlayerInfo
import com.pippop.data.Profile
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.data.SavedPartner
import com.pippop.data.ScoreStore
import com.pippop.data.TurnsView
import com.pippop.game.BoardState
import com.pippop.game.SoloGame
import com.pippop.game.dominoes.DominoOptions
import com.pippop.ui.AppViewModel
import com.pippop.ui.BattleMode
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.ScreenshotBase
import com.pippop.ui.SoloUiState
import com.pippop.ui.UpdateUiState
import com.pippop.ui.screens.ConnectKind
import com.pippop.ui.screens.ConnectSheet
import com.pippop.ui.screens.GameMenu
import com.pippop.ui.screens.GameMenuSheet
import com.pippop.ui.screens.HomeScreen
import com.pippop.ui.screens.LobbyScreen
import com.pippop.ui.screens.LocalMatchSession
import com.pippop.ui.screens.LocalRoomControls
import com.pippop.ui.screens.MatchSession
import com.pippop.ui.screens.ProfileScreen
import com.pippop.ui.screens.RoomControls
import com.pippop.ui.screens.RulesDialog
import com.pippop.ui.screens.ScoreboardScreen
import com.pippop.ui.screens.TurnGameScreen
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.LocalPalette
import com.pippop.ui.theme.PipPopTheme
import com.pippop.ui.theme.SudokuPalette
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/** Scratch: every picture in the catalogue. "-now" is the app as it is; the rest are the proposals. Not committed. */
class CatalogueShots : ScreenshotBase() {
    private val app get() = RuntimeEnvironment.getApplication()
    private val out = "build/outputs/cat"
    private val isDark = mutableStateOf(false)

    private fun set(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PipPopTheme(darkTheme = isDark.value) {
                Box(Modifier.fillMaxSize().background(AppTheme.palette.background)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
    }

    /** One window, in light and (unless [dark] is off) in dark. */
    private fun shots(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        set(content)
        compose.onRoot().captureRoboImage("$out/$name.png")
        if (dark) {
            isDark.value = true
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeBy(800)
            compose.onRoot().captureRoboImage("$out/$name-dark.png")
        }
    }

    /** Every window: a dialog or a sheet of the app's own over the screen. */
    private fun screen(name: String, after: () -> Unit = {}, content: @Composable () -> Unit) {
        set(content)
        after()
        compose.mainClock.advanceTimeBy(1500)
        captureScreenRoboImage("$out/$name.png")
    }

    // ---- The app as it is ---------------------------------------------------------------------

    private fun player(name: String, avatar: Avatar?, ready: Boolean = false, at: GameKind? = null, level: Difficulty? = null) = PlayerInfo(
        name = name, avatar = avatar, deviceId = null, emoji = null, ready = ready, difficulty = level,
        progress = null, finishMs = null, left = false, idleMs = 0, online = true, at = at,
    )

    private fun room(
        game: GameKind, status: RoomStatus, others: List<PlayerInfo>, meAt: GameKind? = game, round: Int = 0,
        winner: String? = null, turns: TurnsView? = null, options: DominoOptions? = null, meReady: Boolean = false, meLevel: Difficulty? = null,
    ) = RoomState(
        code = "K7Q2NB", role = "host", game = game, status = status, difficulty = null, round = round, startAt = null,
        puzzle = null, solution = null, winner = winner, endReason = if (winner != null) "four" else null,
        me = player("Sam", Avatar.FOX, ready = meReady, at = meAt, level = meLevel), opponent = others.firstOrNull(), ships = null, turns = turns,
        serverTime = System.currentTimeMillis(), others = others, maxPlayers = game.maxPlayers, options = options, inSession = true,
        winnerName = if (winner == "opponent") others.firstOrNull()?.name else null,
    )

    private val partner = SavedPartner("https://game-suite.sam.workers.dev", "", "K7Q2NB", "Maya", Avatar.PANDA, "device", "me")
    private val me = Profile(name = "Sam", avatar = Avatar.FOX, tutorialsSeen = GameKind.entries.toSet(), firstRunDone = true, playMode = PlayMode.ONLINE, scriptUrl = "https://game-suite.sam.workers.dev", partner = partner)

    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
    private fun saved(): SoloGame {
        val sol = solution.map { it.digitToInt() }
        var board = BoardState.fromPuzzle(puzzle)
        board.cells.indices.filter { !board.givens[it] }.take(14).forEach { board = board.place(it, sol[it]) }
        return SoloGame(Difficulty.MEDIUM, puzzle, sol, board, elapsedMs = 38 * 60_000L + 12_000L)
    }

    @Composable
    private fun home(room: RoomState? = null, solo: SoloUiState = SoloUiState(game = saved())) = HomeScreen(
        solo = solo, profile = me, onContinue = {}, onNewGame = { _, _ -> }, onProfile = {}, onSolo = {}, onArrange = {},
        partnerBusy = false, onPlayWithPartner = {}, onInvitePartner = {}, onPlayMode = {}, connected = room != null,
        onConnectNearby = {}, onStartRoom = {}, onJoinRoom = {}, room = room,
    )

    private val maya get() = player("Maya", Avatar.PANDA)
    private val c4 = listOf(".......", ".......", "...o...", "..mo...", ".mooo..", "mmomo.o").joinToString("")
    private val c4Won = listOf(".......", ".......", "...o...", "..mo...", ".moom..", "mmmmoo.").joinToString("")
    private val c4Lost = listOf(".......", ".......", "...m...", "..om...", ".ommo..", "oooomm.").joinToString("")

    @Composable
    private fun game(r: RoomState, vm: BattleViewModel, tally: Map<String, Int> = mapOf("You" to 2, "Maya" to 2)) {
        val controls = RoomControls(
            room = r, mode = null, setup = null, rematch = {}, newGame = {}, switchTo = {},
            games = GameKind.entries.filter { it != r.game }, setupOpen = mutableStateOf(false), busy = false,
        )
        CompositionLocalProvider(
            LocalMatchSession provides MatchSession(tally, GameKind.entries.filter { it != r.game }, {}),
            LocalRoomControls provides controls,
        ) { TurnGameScreen(BattleUiState(room = r, mode = BattleMode.ONLINE), vm, onLeave = {}) }
    }

    private fun playing() = room(GameKind.CONNECT4, RoomStatus.PLAYING, listOf(player("Maya", Avatar.PANDA, at = GameKind.CONNECT4)), round = 6, turns = TurnsView("me", c4, emptyList(), false, null))

    @Test fun homeNow() = shots("home-now", dark = false) { home() }

    @Test fun home2Now() = shots("home2-now", dark = false) {
        home(room(GameKind.SUDOKU, RoomStatus.LOBBY, listOf(maya, player("Leo", Avatar.OWL)), meAt = null), SoloUiState())
    }

    @Test fun homePlainNow() = shots("home-plain-now", dark = false) { home(solo = SoloUiState()) }

    @Test fun homeTwoNow() = shots("home2-two-now", dark = false) {
        home(room(GameKind.LUDO, RoomStatus.LOBBY, listOf(player("Maya", Avatar.PANDA, at = GameKind.LUDO)), meAt = null), SoloUiState())
    }

    @Test fun sheetNow() = screen("sheet-now") {
        val p = AppTheme.palette
        val kind = GameKind.CONNECT4
        home(solo = SoloUiState())
        ModalBottomSheet(onDismissRequest = {}, containerColor = p.card) {
            // The body of HomeScreen's GameSheet as it is today (it is private there): saved partner, not connected.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KeyArt(kind, Modifier.fillMaxWidth().height(150.dp).clip(Shapes.L).border(2.dp, p.ink, Shapes.L))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(kind.label, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM)
                        Text(kind.tagline, color = p.muted, fontSize = TextSize.Body)
                    }
                    TextButton(onClick = {}) { Text("How to play", color = p.primaryText, fontWeight = FontWeight.Bold) }
                }
                PrimaryButton("Play with Maya", {})
                SecondaryButton("Play solo", {})
                Text("To play together, connect first, at the top of the home.", color = p.muted, fontSize = TextSize.Body)
            }
        }
    }

    @Test fun golfSetupNow() = shots("golf-setup-now", dark = false) { SoloGolfScreen(SoloGolfUiState(), SoloGolfViewModel(), onHome = {}) }

    @Test fun rulesRaw() = screen("rules-raw") {
        SoloGolfScreen(SoloGolfUiState(), SoloGolfViewModel(), onHome = {})
        RulesDialog(GameKind.GOLF) {}
    }

    @Test fun soloLevelNow() = shots("solo-level-now", dark = false) { SoloConnectFourScreen(SoloC4UiState(), SoloConnectFourViewModel(), onHome = {}) }

    @Test fun lobbyNow() {
        val vm = BattleViewModel(app)
        shots("lobby-now", dark = false) {
            LobbyScreen(
                BattleUiState(room = room(GameKind.DOMINOES, RoomStatus.LOBBY, listOf(player("Maya", Avatar.PANDA, ready = true, at = GameKind.DOMINOES)), options = DominoOptions()), mode = BattleMode.ONLINE),
                me, vm, onLeave = {},
            )
        }
    }

    @Test fun lobbySudokuNow() {
        val vm = BattleViewModel(app)
        shots("lobby-sudoku-now", dark = false) {
            LobbyScreen(
                BattleUiState(
                    room = room(
                        GameKind.SUDOKU, RoomStatus.LOBBY, listOf(player("Maya", Avatar.PANDA, ready = true, at = GameKind.SUDOKU, level = Difficulty.EASY)),
                        meReady = true, meLevel = Difficulty.HARD,
                    ),
                    mode = BattleMode.ONLINE,
                ),
                me, vm, onLeave = {},
            )
        }
    }

    @Test fun gameNow() {
        val vm = BattleViewModel(app)
        shots("game-now", dark = false) { game(playing(), vm) }
    }

    @Test fun reactNow() {
        val vm = BattleViewModel(app)
        set { game(playing(), vm) }
        compose.onNodeWithText("React").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("$out/react-now.png")
    }

    @Test fun menuNow() {
        val vm = BattleViewModel(app)
        screen("menu-now") {
            game(playing(), vm)
            GameMenuSheet(GameMenu(GameKind.CONNECT4, onRules = {}, onLeave = {}, mode = "Classic", onNewGame = {}, onChangeMode = {}, onSwitchGame = {})) {}
        }
    }

    @Test fun resultNow() {
        val vm = BattleViewModel(app)
        shots("result-now", dark = false) {
            game(room(GameKind.CONNECT4, RoomStatus.FINISHED, listOf(player("Maya", Avatar.PANDA, ready = true, at = GameKind.CONNECT4)), round = 6, winner = "me", turns = TurnsView(null, c4Won, listOf(35, 36, 37, 38), true, 38)), vm, mapOf("You" to 3, "Maya" to 2))
        }
    }

    @Test fun resultLoseNow() {
        val vm = BattleViewModel(app)
        shots("result-lose-now", dark = false) {
            game(room(GameKind.CONNECT4, RoomStatus.FINISHED, listOf(player("Maya", Avatar.PANDA, at = GameKind.CONNECT4)), round = 6, winner = "opponent", turns = TurnsView(null, c4Lost, listOf(35, 36, 37, 38), false, 38)), vm, mapOf("You" to 2, "Maya" to 3))
        }
    }

    @Test fun dialogRaw() = screen("dialog-raw") {
        val p = AppTheme.palette
        home(room(GameKind.LUDO, RoomStatus.LOBBY, listOf(player("Maya", Avatar.PANDA, at = GameKind.LUDO)), meAt = null), SoloUiState())
        // The same dialog PipPopApp shows for Leave on the home.
        AlertDialog(
            onDismissRequest = {},
            containerColor = p.card,
            title = { Text("Leave the room?", color = p.text) },
            text = { Text("You'll disconnect. A game you're in ends for everyone in it.", color = p.muted) },
            confirmButton = { TextButton(onClick = {}) { Text("Leave", color = p.bad) } },
            dismissButton = { TextButton(onClick = {}) { Text("Stay", color = p.primary) } },
        )
    }

    @Test fun joinNow() {
        val vm = BattleViewModel(app)
        screen("join-now") {
            home(solo = SoloUiState())
            ModalBottomSheet(onDismissRequest = {}, containerColor = AppTheme.palette.card) { ConnectSheet(ConnectKind.JOIN_ONLINE, BattleUiState(joinCode = "K7Q2"), vm) }
        }
    }

    @Test fun bitsNow() = shots("bits-now", dark = false) {
        Box(Modifier.fillMaxSize()) {
            home(solo = SoloUiState(generating = true))
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text("Maya can't play right now.") }
        }
    }

    @Test fun profileNow() {
        val vm = BattleViewModel(app)
        val appVm = AppViewModel(app)
        shots("profile-now", dark = false) { ProfileScreen(me, false, appVm, vm, UpdateUiState(currentVersion = "1.0.163"), {}, {}, {}) }
    }

    @Test fun scoresNow() {
        val store = ScoreStore.get(app)
        if (store.records.value.isEmpty()) {
            val day = 86_400_000L
            val now = System.currentTimeMillis()
            val thisMonth = listOf(
                GameKind.LUDO to "You", GameKind.LUDO to "Maya", GameKind.LUDO to "Maya", GameKind.CONNECT4 to "You", GameKind.CONNECT4 to "You",
                GameKind.CONNECT4 to "Maya", GameKind.SHIPS to "You", GameKind.SHIPS to "Maya", GameKind.WORDS to "You", GameKind.WORDS to "You",
                GameKind.WORDS to "You", GameKind.DOMINOES to "Maya", GameKind.DOMINOES to "Maya", GameKind.GOLF to "You", GameKind.CLASH to "Maya",
                GameKind.CLASH to "You", GameKind.SUDOKU to "You", GameKind.SUDOKU to "Maya", GameKind.MEMORY to "You", GameKind.BINGO to "Maya", GameKind.DOTS to "You",
            )
            thisMonth.forEachIndexed { i, (g, w) -> store.add(MatchRecord(now - (thisMonth.size - i) * (day / 4), g, listOf("Maya"), w)) }
            repeat(25) { i -> store.add(MatchRecord(now - 40 * day - i * day / 3, GameKind.entries[i % 8], listOf("Maya"), if (i % 5 < 2) "You" else "Maya")) }
            repeat(22) { i -> store.add(MatchRecord(now - 70 * day - i * day / 3, GameKind.entries[i % 8], listOf("Maya"), if (i % 5 < 3) "You" else "Maya")) }
        }
        shots("scores-now", dark = false) { ScoreboardScreen(onBack = {}) }
    }

    // ---- The proposals ------------------------------------------------------------------------

    @Composable
    private fun Over(base: @Composable () -> Unit, over: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        Box(Modifier.fillMaxSize()) {
            base()
            over()
        }
    }

    @Composable
    private fun Themed(p: SudokuPalette, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalPalette provides p) { Box(Modifier.fillMaxSize().background(p.background)) { content() } }
    }

    @Test fun homeNew() = shots("home-new") { HomeNew(HomeMode.SOLO) }
    @Test fun home2New() = shots("home2-new") { HomeNew(HomeMode.TWO) }
    @Test fun home2Three() = shots("home2-three", dark = false) { HomeNew(HomeMode.THREE) }
    @Test fun sheetNew() = shots("sheet-new") { Over({ HomeNew(HomeMode.SOLO) }) { GameSheetNew() } }
    @Test fun rulesNew() = shots("rules-new") { Over({ SoloGolfScreen(SoloGolfUiState(), SoloGolfViewModel(), onHome = {}) }) { RulesSheetNew() } }
    @Test fun lobbyNew() = shots("lobby-new") { LobbyNew() }
    @Test fun lobbySudokuNew() = shots("lobby-sudoku-new", dark = false) { LobbyNew(sudoku = true) }
    @Test fun gameNew() = shots("game-new") { GameNew() }
    @Test fun gameLater() = shots("game-later", dark = false) { GameNew(hint = null) }
    @Test fun resultNew() = shots("result-new") { GameNew(finished = "me") }
    @Test fun resultLoseNew() = shots("result-lose-new", dark = false) { GameNew(finished = "them") }
    @Test fun resultSwitch() = shots("result-switch", dark = false) { Over({ GameNew(finished = "me") }) { AnotherGameSheet() } }
    @Test fun nightHome() = shots("night-home", dark = false) { HomeNew(HomeMode.NIGHT) }
    @Test fun nightGame() = shots("night-game", dark = false) { GameNew(target = 3) }
    @Test fun nightDecider() = shots("night-decider", dark = false) { LobbyNew(decider = true) }
    @Test fun nightFinale() = shots("night-finale") { NightFinale() }
    @Test fun scoresNew() = shots("scores-new") { ScoresNew() }
    @Test fun scoresGame() = shots("scores-game", dark = false) { Over({ ScoresNew() }) { GameStorySheet() } }
    @Test fun scoresSolo() = shots("scores-solo", dark = false) { ScoresNew(tab = 2) }
    @Test fun scoresTrophy() = shots("scores-trophy") { TrophyMoment() }
    @Test fun profileNew() = shots("profile-new") { ProfileNew() }
    @Test fun dialogNew() = shots("dialog-new") { Over({ HomeNew(HomeMode.TWO) }) { LeaveDialogNew() } }
    @Test fun joinNew() = shots("join-new") { Over({ HomeNew(HomeMode.SOLO) }) { JoinSheetNew() } }
    @Test fun menuNew() = shots("menu-new") { Over({ GameNew() }) { MenuSheetNew() } }
    @Test fun bitsNew() = shots("bits-new") { Over({ HomeNew(HomeMode.SOLO) }) { BitsNew() } }
    @Test fun reactNew() = shots("react-new") { GameNew(tray = true, bubble = "One more?") }
    @Test fun reactLines() = shots("react-lines", dark = false) { Over({ GameNew() }) { LinesSheet() } }
    @Test fun looksNew() = shots("looks-new") { Over({ GameNew() }) { LooksSheet() } }
    @Test fun memoryPhotos() = shots("memory-photos", dark = false) { LobbyMemoryPhotos() }
    @Test fun themeMint() = shots("theme-mint", dark = false) { Themed(Themes.Mint) { HomeNew(HomeMode.SOLO) } }
    @Test fun themePeach() = shots("theme-peach", dark = false) { Themed(Themes.Peach) { HomeNew(HomeMode.SOLO) } }
    @Test fun themeSky() = shots("theme-sky", dark = false) { Themed(Themes.Sky) { HomeNew(HomeMode.SOLO) } }
}
