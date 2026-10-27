package com.arcade.sudoku.ui.screens

import com.arcade.sudoku.ui.theme.TextSize
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.GameColors
import com.arcade.sudoku.ui.theme.Board
import com.arcade.sudoku.ui.theme.Pieces
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.Icons
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.art.Outcome
import com.arcade.sudoku.data.GameKind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcade.sudoku.data.PlayerInfo
import com.arcade.sudoku.data.RoomState
import com.arcade.sudoku.data.RoomStatus
import com.arcade.sudoku.data.ShipStyle
import com.arcade.sudoku.game.ships.Fleet
import com.arcade.sudoku.game.ships.Sea
import com.arcade.sudoku.game.ships.Ship
import com.arcade.sudoku.game.ships.Shot
import com.arcade.sudoku.ui.BattleUiState
import com.arcade.sudoku.ui.BattleViewModel
import com.arcade.sudoku.ui.SoloShipsUiState
import com.arcade.sudoku.ui.SoloShipsViewModel
import com.arcade.sudoku.ui.art.drawSeaArt
import com.arcade.sudoku.ui.art.drawShipArt
import com.arcade.sudoku.ui.art.drawShotArt
import com.arcade.sudoku.ui.art.shipKinds
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.OpponentColor

/** How ships look, from the player's profile. */
val LocalShipStyle = staticCompositionLocalOf { ShipStyle.TOY }

/**
 * A 10x10 sea in the player's ship style. Draws [ships] (your fleet, or enemy ships once
 * revealed), [shots] that landed here as hit and miss marks, [sunk] ships shaded dark and a
 * ring on [lastShot]. With [onTap] set, tapping a square calls it with its column and row.
 */
@Composable
fun ShipsSea(
    ships: List<Ship>,
    shots: List<Shot>,
    modifier: Modifier = Modifier,
    sunk: List<Ship> = emptyList(),
    lastShot: Shot? = null,
    onTap: ((Int, Int) -> Unit)? = null,
    /** A shot on its way: marked with a target until the server says hit or miss. */
    pending: Pair<Int, Int>? = null,
) {
    val p = AppTheme.palette
    val style = LocalShipStyle.current
    // The sea moves: wavelets drift and ships bob. [time] goes round once every few seconds.
    val time by rememberInfiniteTransition(label = "sea").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "sea",
    )
    // A new shot lands with a blast (hit) or a splash (miss); shots already there when the
    // screen opened stay still.
    val blast = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(lastShot) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        if (lastShot == null) return@LaunchedEffect
        blast.snapTo(0f)
        blast.animateTo(1f, tween(BLAST_MS, easing = LinearEasing))
    }
    val tapModifier = if (onTap == null) Modifier else Modifier.pointerInput(onTap) {
        detectTapGestures { offset ->
            val cell = size.width / Sea.SIZE.toFloat()
            val x = (offset.x / cell).toInt().coerceIn(0, Sea.SIZE - 1)
            val y = (offset.y / cell).toInt().coerceIn(0, Sea.SIZE - 1)
            onTap(x, y)
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.M).then(tapModifier)) {
        val cell = size.width / Sea.SIZE
        drawSeaArt(style, Sea.SIZE, cell)
        wavelets(time, cell)
        // Sunk ships the list doesn't already hold (revealed enemy ships) are drawn too.
        val all = ships + sunk.filter { it !in ships }
        all.zip(shipKinds(all)).forEachIndexed { i, (ship, kind) ->
            val down = ship in sunk
            // Afloat, a ship rocks gently on the swell; a sunk one lies still.
            val bob = if (down) 0f else sin(time + i * 1.3f) * cell * 0.05f
            translate(0f, bob) { drawShipArt(style, kind, ship, cell, sunk = down) }
        }
        for (shot in shots) drawShotArt(style, shot.x, shot.y, shot.hit, cell)
        lastShot?.takeIf { blast.value < 1f }?.let { boom(it, blast.value, cell) }
        lastShot?.let {
            drawCircle(
                p.primary,
                radius = cell * 0.45f,
                center = Offset((it.x + 0.5f) * cell, (it.y + 0.5f) * cell),
                style = Stroke(width = cell * 0.08f),
            )
        }
        pending?.let { (x, y) ->
            val center = Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
            drawCircle(p.primary, radius = cell * 0.38f, center = center, style = Stroke(width = cell * 0.08f))
            drawCircle(p.primary, radius = cell * 0.1f, center = center)
        }
    }
}

/** How long a shot's blast or splash plays. */
private const val BLAST_MS = 900

/** Little white wave crests drifting across the sea, a few to a row. */
private fun DrawScope.wavelets(time: Float, cell: Float) {
    val w = size.width
    for (row in 0 until Sea.SIZE) {
        for (k in 0 until 3) {
            val drift = ((k / 3f + time / (2 * PI).toFloat() * (if (row % 2 == 0) 1f else -1f) + row * 0.17f) % 1f + 1f) % 1f
            val x = drift * w
            val y = (row + 0.5f) * cell + sin(time * 2 + row + k) * cell * 0.12f
            drawArc(
                Color.White.copy(alpha = 0.28f), 200f, 140f, false,
                Offset(x - cell * 0.3f, y - cell * 0.15f), Size(cell * 0.6f, cell * 0.3f), style = Stroke(cell * 0.05f, cap = StrokeCap.Round),
            )
        }
    }
}

/** A hit: a fireball, sparks and smoke. A miss: a ring of splash and droplets. [t] from 0 to 1. */
private fun DrawScope.boom(shot: Shot, t: Float, cell: Float) {
    val c = Offset((shot.x + 0.5f) * cell, (shot.y + 0.5f) * cell)
    val fade = 1f - t
    if (shot.hit) {
        val r = cell * (0.25f + 0.9f * minOf(1f, t * 2.2f))
        drawCircle(Pieces.Orange.copy(alpha = fade), r, c)
        drawCircle(Board.Glow.copy(alpha = fade), r * 0.6f, c)
        drawCircle(Color.White.copy(alpha = fade * fade), r * 0.25f, c)
        for (k in 0 until 10) {
            val a = k * PI.toFloat() / 5 + 0.3f
            val d = cell * (0.3f + 1.4f * t)
            val end = c + Offset(cos(a) * d, sin(a) * d + cell * t * t)
            drawLine(GameColors.Spark.copy(alpha = fade), end - Offset(cos(a), sin(a)) * (cell * 0.18f), end, cell * 0.07f, StrokeCap.Round)
        }
        // Smoke rising after the flash.
        if (t > 0.3f) {
            val k = (t - 0.3f) / 0.7f
            drawCircle(GameColors.Smoke.copy(alpha = 0.45f * (1f - k)), cell * (0.3f + 0.4f * k), c - Offset(0f, cell * 0.9f * k))
        }
    } else {
        for (ring in 0..1) {
            val k = (t - ring * 0.2f).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = (1f - k) * 0.9f), cell * (0.2f + 0.8f * k), c, style = Stroke(cell * 0.08f))
        }
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3
            val d = cell * 0.6f * t
            drawCircle(GameColors.Spray.copy(alpha = fade), cell * 0.07f, c + Offset(cos(a) * d, sin(a) * d - cell * 0.8f * t * (1f - t)))
        }
    }
}

/** Your fleet before a battle: arrange it (or shuffle) until you like it, then get ready. */
@Composable
fun FleetCard(
    fleet: List<Ship>,
    ready: Boolean,
    opponent: PlayerInfo?,
    busy: Boolean,
    onShuffle: () -> Unit,
    onReady: () -> Unit,
    onMove: (List<Ship>) -> Unit = {},
) {
    val p = AppTheme.palette
    AppCard("Your fleet") {
        FleetEditor(fleet, if (ready || busy) null else onMove)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Shuffle", onShuffle, Modifier.weight(1f), enabled = !ready && !busy)
            PrimaryButton(if (ready) "Not ready" else "Ready", onReady, Modifier.weight(1f), enabled = opponent != null, loading = busy)
        }
        Text(
            when {
                opponent == null -> "Waiting for an opponent to join."
                ready && opponent.ready -> "Both fleets are in. Starting…"
                ready -> "Your fleet is ready. Waiting for ${opponent.name}…"
                opponent.ready -> "${opponent.name} is ready. Place your fleet and tap Ready."
                else -> "Drag ships to move them, tap one to turn it, or shuffle. Tap Ready when you like your fleet."
            },
            color = if (opponent?.ready == true && !ready) p.good else p.muted,
            fontSize = TextSize.Body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A Ship Battle against your partner. */
@Composable
fun ShipsGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val ships = room.ships ?: return
    val opponent = room.opponent
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = ships.turn == "me"

    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.SHIPS) { showRules = false }
    val players = listOfNotNull(
        ChipPlayer("You", room.me.avatar, p.primary, "${room.me.progress ?: 0}% of fleet hit", visibleReaction(state.myReaction, now), active = myTurn),
        opponent?.let {
            ChipPlayer(
                it.name + if (it.online == false) " (offline)" else "", it.avatar, OpponentColor,
                "${it.progress ?: 0}% of fleet hit", visibleReaction(state.opponentReaction, now), active = ships.turn == "opponent",
            )
        },
    )

    GameFrame(
        title = "Ship Battle",
        subtitle = "Round ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        swipeToFocus = true,
        players = players,
        glow = GameKind.SHIPS.color,
        dock = battleDock(finished, onLeave, if (opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            val name = opponent?.name ?: "Your opponent"
            MatchPopup(
                room, GameKind.SHIPS, outcomeOf(room.winner), resultTitle(room.winner, room.winnerName ?: name),
                when (room.endReason) {
                    "sunk" -> if (room.winner == "me") "You sank $name's whole fleet." else "$name sank your whole fleet."
                    "forfeit" -> if (room.winner == "me") "$name left the battle." else "You left the battle."
                    "timeout" -> if (room.winner == "me") "$name lost connection." else "You were disconnected for too long."
                    else -> ""
                },
                onRematch = vm::readyShips, onFinish = onLeave,
            ) {
                // The fleet for the next round, ready to shuffle before tapping Rematch.
                if (opponent != null && !opponent.left) {
                    Text("Your next fleet", color = p.muted, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
                    FleetEditor(if (room.me.ready) ships.myFleet else state.fleetDraft, if (room.me.ready) null else vm::setFleet, Modifier.fillMaxWidth(0.6f))
                    if (!room.me.ready) SecondaryButton("Shuffle", vm::shuffleFleet)
                }
            }
        },
    ) {
        TurnBanner(myTurn && !finished, opponent?.name ?: "Your opponent", finished)
        SeaPair(
            enemy = {
                ShipsSea(
                    ships = ships.enemyFleet ?: emptyList(),
                    shots = ships.myShots,
                    sunk = ships.sunkEnemy,
                    lastShot = ships.myShots.lastOrNull()?.takeIf { ships.lastShotByMe == true },
                    onTap = if (myTurn && !finished && !state.firing) vm::fire else null,
                    pending = state.pendingShot,
                    modifier = it,
                )
            },
            mine = {
                ShipsSea(
                    ships = ships.myFleet,
                    shots = ships.theirShots,
                    sunk = ships.myFleet.filter { s -> Fleet.isSunk(s, ships.theirShots) },
                    lastShot = ships.theirShots.lastOrNull()?.takeIf { ships.lastShotByMe == false },
                    modifier = it,
                )
            },
        )
    }
}

/** The enemy sea (large, to aim at) above your own (smaller), both sized to fit the screen. */
@Composable
private fun ColumnScope.SeaPair(enemy: @Composable (Modifier) -> Unit, mine: @Composable (Modifier) -> Unit) {
    val p = AppTheme.palette
    Text("Enemy sea", color = p.text, fontWeight = FontWeight.SemiBold)
    BoardArea(1f, weight = 0.62f, content = enemy)
    Text("Your sea", color = p.text, fontWeight = FontWeight.SemiBold)
    BoardArea(1f, weight = 0.38f, content = mine)
}

@Composable
private fun TurnBanner(myTurn: Boolean, opponentName: String, over: Boolean = false) {
    val p = AppTheme.palette
    Surface(
        color = if (myTurn) p.primary else p.accent,
        shape = Shapes.M,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            when {
                over -> "Battle over"
                myTurn -> "Your turn: tap the enemy sea to fire"
                else -> "$opponentName is aiming…"
            },
            color = if (myTurn) p.onPrimary else p.text,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Ship Battle against the computer. */
@Composable
fun SoloShipsScreen(state: SoloShipsUiState, vm: SoloShipsViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    val game = state.game
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.SHIPS) { showRules = false }
    GameFrame(
        title = "Ship Battle",
        subtitle = "Against the computer",
        swipeToFocus = game != null,
        players = if (game == null) emptyList() else listOf(
            ChipPlayer("You", null, p.primary, "${game.myProgress}% of fleet hit", active = game.myTurn && game.winner == null),
            ChipPlayer("Computer", null, OpponentColor, "${game.theirProgress}% of fleet hit", active = !game.myTurn && game.winner == null),
        ),
        glow = GameKind.SHIPS.color,
        dock = listOf(
            DockAction("Home", Icons.Filled.Home, onClick = onHome),
            DockAction("Rules", Icons.Filled.Info) { showRules = true },
            DockAction("New battle", Icons.Filled.Refresh, enabled = game != null, onClick = vm::newBattle),
        ),
        scrollable = game == null,
        popup = game?.winner?.let { winner -> popupContent {
            ResultPopup(
                game = GameKind.SHIPS,
                outcome = if (winner == "me") Outcome.WIN else Outcome.LOSE,
                title = if (winner == "me") "You win!" else "The computer wins",
                detail = if (winner == "me") "You sank the whole fleet." else "The computer sank your fleet.",
                finish = PopupAction("Home", onHome),
                again = PopupAction("New battle", vm::newBattle),
            )
        } },
    ) {
        if (game == null) {
            AppCard("Your fleet") {
                Text(
                    "Sink the computer's five ships before it sinks yours. A hit lets you fire again. " +
                        "Drag your ships to move them, tap one to turn it.",
                    color = p.muted,
                    fontSize = TextSize.Body,
                )
                FleetEditor(state.draft, vm::setDraft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Shuffle", vm::shuffle, Modifier.weight(1f))
                    PrimaryButton("Start battle", vm::start, Modifier.weight(1f))
                }
            }
        } else {
            SoloShipsBattle(game, vm)
        }
    }
}

@Composable
private fun ColumnScope.SoloShipsBattle(game: com.arcade.sudoku.game.ships.SoloShips, vm: SoloShipsViewModel) {
    TurnBanner(game.myTurn && game.winner == null, "The computer", game.winner != null)
    SeaPair(
        enemy = {
            ShipsSea(
                ships = if (game.winner != null) game.enemyFleet else emptyList(),
                shots = game.myShots,
                sunk = game.sunkEnemy,
                lastShot = game.myShots.lastOrNull(),
                onTap = if (game.myTurn && game.winner == null) vm::fire else null,
                modifier = it,
            )
        },
        mine = {
            ShipsSea(
                ships = game.myFleet,
                shots = game.theirShots,
                sunk = game.sunkMine,
                lastShot = game.theirShots.lastOrNull(),
                modifier = it,
            )
        },
    )
}

/**
 * Your fleet, to arrange by hand: drag a ship to move it (it shows green where it can go, red
 * where it can't, and snaps back if let go there), tap a ship to turn it. Ships can't touch.
 * With [onChange] null the fleet only shows.
 */
@Composable
fun FleetEditor(fleet: List<Ship>, onChange: ((List<Ship>) -> Unit)?, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    // The ship being dragged: its index, where it would land, and whether it may.
    var drag by remember { mutableStateOf<Triple<Int, Ship, Boolean>?>(null) }
    val current by rememberUpdatedState(fleet)
    val report by rememberUpdatedState(onChange)
    val shown = drag?.let { (i, ship, _) -> fleet.toMutableList().also { it[i] = ship } } ?: fleet
    val gestures = if (onChange == null) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val cell = size.width / Sea.SIZE.toFloat()
            fun cellAt(o: Offset) = (o.x / cell).toInt().coerceIn(0, Sea.SIZE - 1) to (o.y / cell).toInt().coerceIn(0, Sea.SIZE - 1)
            val (gx, gy) = cellAt(down.position)
            val fleetNow = current
            val index = fleetNow.indexOfFirst { Sea.cell(gx, gy) in it.cells }
            if (index < 0) return@awaitEachGesture
            down.consume()
            val ship = fleetNow[index]
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!pointer.pressed) break
                if (!moved && (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (moved) {
                    pointer.consume()
                    val (cx, cy) = cellAt(pointer.position)
                    val target = ship.copy(x = ship.x + cx - gx, y = ship.y + cy - gy)
                    drag = Triple(index, target, Fleet.canPlace(fleetNow, index, target))
                }
            }
            val landed = drag
            drag = null
            if (moved) {
                if (landed != null && landed.third) report?.invoke(fleetNow.toMutableList().also { it[index] = landed.second })
            } else {
                Fleet.rotate(fleetNow, index)?.let { report?.invoke(it) }
            }
        }
    }
    Box(modifier.fillMaxWidth().aspectRatio(1f).then(gestures)) {
        ShipsSea(ships = shown, shots = emptyList())
        drag?.let { (_, ship, ok) ->
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / Sea.SIZE
                val tint = if (ok) p.good else Pieces.Red
                for (c in ship.cells.filter { it in 0 until Sea.SIZE * Sea.SIZE }) {
                    drawRect(tint.copy(alpha = 0.35f), Offset((c % Sea.SIZE) * cell, (c / Sea.SIZE) * cell), Size(cell, cell))
                }
            }
        }
    }
}
