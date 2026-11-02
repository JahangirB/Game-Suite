package com.pippop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.GameKind
import com.pippop.data.Reaction
import com.pippop.data.RoomState
import com.pippop.data.RoomStatus
import com.pippop.game.golf.Box
import com.pippop.game.golf.Frame
import com.pippop.game.golf.Golf
import com.pippop.game.golf.GolfLevel
import com.pippop.game.golf.GolfView
import com.pippop.game.golf.Hole
import com.pippop.game.golf.Pt
import com.pippop.game.golf.ShotKind
import com.pippop.game.golf.ZoneType
import com.pippop.ui.BattleUiState
import com.pippop.ui.BattleViewModel
import com.pippop.ui.SoloGolfUiState
import com.pippop.ui.SoloGolfViewModel
import com.pippop.ui.Sfx
import com.pippop.ui.Sound
import com.pippop.ui.Takeover
import com.pippop.ui.art.Outcome
import com.pippop.ui.art.color
import com.pippop.ui.rememberSfx
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.GameColors
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize
import kotlinx.coroutines.delay
import kotlin.math.hypot

private val Grass = GameColors.Grass
private val CourseSide = GameColors.CourseSide
private val Sand = GameColors.Sand
private val FlagRed = GameColors.Flag
private val Water = GameColors.Water
private val WaterEdge = GameColors.WaterEdge
private val Wood = GameColors.Wood
private val WoodDark = GameColors.WoodDark

/**
 * The hole as a flat map from above: islands, bridges, walls, sand, ponds, sticker spots, the
 * cup and the balls. The game itself is the 3D view ([GolfPlay]); this is for the tutorial.
 */
@Composable
fun GolfMap(hole: Hole, balls: Map<Char, Pt?>, modifier: Modifier = Modifier) {
    val ink = AppTheme.palette.ink
    Canvas(modifier) {
        val s = minOf(size.width / Golf.W, size.height / hole.len).toFloat()
        val ox = (size.width - Golf.W.toFloat() * s) / 2
        val oy = (size.height - hole.len.toFloat() * s) / 2
        fun o(p: Pt) = Offset(ox + (p.x * s).toFloat(), oy + (p.y * s).toFloat())
        fun rect(b: Box) = Rect(o(Pt(b.x0, b.y0)), o(Pt(b.x1, b.y1)))
        fun rounded(b: Box, r: Double) = Path().apply { addRoundRect(RoundRect(rect(b), CornerRadius((r * s).toFloat()))) }
        drawRect(Brush.verticalGradient(listOf(Water, WaterEdge)))
        hole.bridges.forEach { b ->
            drawRect(Wood, rect(b).topLeft, rect(b).size)
            drawRect(WoodDark, rect(b).topLeft, rect(b).size, style = Stroke(0.006f * s))
        }
        hole.islands.forEach { b ->
            translate(0f, 0.02f * s) { drawPath(rounded(b, Golf.CORNER), CourseSide) }
            drawPath(rounded(b, Golf.CORNER), Grass)
        }
        hole.sand.forEach { drawPath(rounded(it, 0.03), Sand) }
        hole.water.forEach { w ->
            val box = rect(Box(w.x - w.rx, w.y - w.ry, w.x + w.rx, w.y + w.ry))
            drawOval(Water, box.topLeft, box.size)
            drawOval(WaterEdge, box.topLeft, box.size, style = Stroke(0.006f * s))
        }
        hole.walls.forEach { b ->
            drawRect(Wood, rect(b).topLeft, rect(b).size)
            drawRect(WoodDark, rect(b).topLeft, rect(b).size, style = Stroke(0.005f * s))
        }
        hole.zones.forEach { z ->
            val color = when (z.type) {
                ZoneType.KYLIAN -> GameColors.ZoneKylian
                ZoneType.FIRE -> GameColors.ZoneFire
                ZoneType.STONKS -> GameColors.ZoneStonks
                ZoneType.PORTAL -> GameColors.ZonePortal
                ZoneType.EYES -> GameColors.ZoneEyes
            }
            z.box?.let { drawPath(rounded(it, 0.02), color) }
            z.at?.let { drawCircle(color, (Golf.PORTAL * s).toFloat(), o(it)) }
        }
        val cup = o(hole.cup)
        drawCircle(GameColors.Cup, (Golf.CUP * s).toFloat(), cup)
        drawLine(ink, cup, cup + Offset(0f, -0.14f * s), 0.008f * s, StrokeCap.Round)
        drawPath(Path().apply { moveTo(cup.x, cup.y - 0.14f * s); lineTo(cup.x + 0.08f * s, cup.y - 0.115f * s); lineTo(cup.x, cup.y - 0.09f * s); close() }, FlagRed)
        balls.forEach { (m, p) ->
            if (p == null) return@forEach
            val r = (Golf.R * s * 1.4).toFloat()
            drawCircle(markColor(m), r, o(p))
            drawCircle(ink, r, o(p), style = Stroke(s * 0.004f))
        }
    }
}

/**
 * The hole being played, in 3D: everyone's ball still out ([current] waits on the tee for its
 * first shot), and each new last shot played out on every phone, the shooter's included, with
 * its sounds. A shot that stops on Kylian takes the next turn, with his song on every phone
 * (quieter the further your ball is) and his statue in the 3D view.
 *
 * [game] tells one game from the next: a restarted game counts its shots from 1 again, so the
 * shots already seen are kept per game. (Before, a restart kept the old count, every new shot
 * looked already seen, and the ball jumped to where it stopped without rolling there.)
 */
@Composable
fun GolfPlay(view: GolfView, current: Char?, canShoot: Boolean, game: Any, onShoot: (ShotKind, Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val sfx = rememberSfx()
    val last = view.last
    // Shots already played when the screen opened (or the game started) don't play again.
    var seen by remember(game) { mutableStateOf(last?.seq) }
    var shot by remember(game) { mutableStateOf<Golf3DShot?>(null) }
    LaunchedEffect(game, last?.seq) {
        if (last == null || last.seq == seen) return@LaunchedEffect
        seen = last.seq
        val hole = view.holeAt(last.hole)
        val worked = Golf.simulate(hole, last.from ?: hole.tee, last.kind, last.angle, last.power)
        // The shooter's phone said where it stopped; end there whatever this phone worked out.
        val frames = if (worked.hazard == null && !worked.holed) worked.frames.dropLast(1) + Frame(last.to.x, last.to.y, 0.0) else worked.frames
        shot = Golf3DShot("$game-${last.seq}", "$game-${last.hole}", hole, last.who, markColor(last.who), worked.copy(frames = frames), last.hole + 1)
        sfx.play(Sound.GOLF_PUTT, (if (last.who == 'm') 1f else Sfx.OTHERS) * (0.6f + 0.4f * last.power.toFloat()))
    }
    // Nobody shoots while Kylian has the game.
    val lockedUntil by Takeover.until.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        now = System.currentTimeMillis()
        if (lockedUntil > now) {
            delay(lockedUntil - now)
            now = System.currentTimeMillis()
        }
    }
    val hole = view.holeAt(view.hole.coerceIn(0, view.holes - 1))
    // Every ball still out; one not hit yet is on the tee, shown when it's that player's turn
    // (yours always: your camera stands behind it).
    val balls = view.balls.mapNotNull { (m, p) ->
        if (view.done[m] == true || (p == null && m != current && m != 'm')) null else Golf3DBall(m, p ?: hole.tee, markColor(m))
    }
    val waiting = current?.takeIf { c -> view.done[c] != true && balls.none { it.id == c } }?.let { Golf3DBall(it, hole.tee, markColor(it)) }
    val mine = view.balls['m']
    val state = Golf3DState(
        key = "$game-${view.hole}",
        hole = hole,
        balls = balls + listOfNotNull(waiting),
        turn = current,
        canShoot = canShoot && view.done['m'] != true && now >= lockedUntil,
        noGuide = mine != null && hole.zoneAt(mine)?.type == ZoneType.EYES,
        number = view.hole + 1,
        meDone = view.done['m'] == true,
    )
    Golf3D(
        state, shot, onShoot,
        onEvent = { what ->
            val v = if (shot?.who == 'm') 1f else Sfx.OTHERS
            when (what) {
                "wall", "stonks", "portal" -> sfx.play(Sound.GOLF_WALL, v)
                "land" -> sfx.play(Sound.GOLF_PUTT, v * 0.5f)
                "drop" -> sfx.play(Sound.GOLF_DROP, v)
                "sea", "water", "fire" -> sfx.play(Sound.GOLF_SPLASH, v)
                "holed" -> sfx.play(Sound.GOLF_CUP)
                "kylian" -> {
                    // Kylian Dictador takes the next turn, and nobody shoots while his song plays;
                    // his statue comes down in the 3D view. The song is as loud as your ball is near
                    // him (full on your own shot, or once you've finished and are watching).
                    Takeover.takeOver(Reaction.KYLIAN, System.currentTimeMillis())
                    val played = shot
                    val volume = if (played == null || played.who == 'm' || view.done['m'] == true) 1f
                    else kylianVolume(hypot((view.balls['m'] ?: hole.tee).x - played.shot.end.x, (view.balls['m'] ?: hole.tee).y - played.shot.end.y))
                    sfx.play(Sound.KYLIAN_DICTADOR, volume)
                }
            }
        },
        modifier = modifier.clip(Shapes.M),
    )
}

/**
 * How loud Kylian's song is on your phone when someone else's ball stops on him [distance] away
 * (course units): full right beside him, fading as you go further (the 3D view's thump fades the same).
 */
internal fun kylianVolume(distance: Double): Float = (1.0 / (1.0 + (distance / 0.6) * (distance / 0.6))).coerceIn(0.06, 1.0).toFloat()

/** "Hole 3 of 9 · par 3" and everyone's total so far. */
@Composable
private fun GolfHeader(view: GolfView, names: List<Pair<Char, String>>) {
    val p = AppTheme.palette
    val hole = view.holeAt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Hole ${view.hole + 1} of ${view.holes} · par ${hole.par}", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
        Text(names.joinToString("  ") { (m, n) -> "$n ${view.total(m)}" }, color = p.muted, fontSize = TextSize.Body)
    }
}

/**
 * Once someone is in, the countdown for everyone still out: "Someone's in! Shots left: You 3 ·
 * Ben 2". Nothing before that.
 */
@Composable
private fun GolfCountdown(view: GolfView, names: List<Pair<Char, String>>) {
    if (view.left.isEmpty()) return
    val p = AppTheme.palette
    val counts = names.mapNotNull { (m, n) -> view.left[m]?.let { "$n $it" } }
    Text(
        "Someone's in! Shots left to finish: ${counts.joinToString(" · ")}",
        color = p.primaryText, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
    )
}

/** What the status line says on your shot: the countdown once someone is in. */
private fun yourShot(view: GolfView) = when (val left = view.left['m']) {
    null -> "Your shot: slide up and let go"
    1 -> "Your last shot to get in!"
    else -> "Your shot: $left left to get in"
}

/** Scores per player for the result popup: total and against par. */
private fun golfScores(view: GolfView, names: List<Pair<Char, String>>): List<String> {
    val par = (0 until view.holes).sumOf { view.holeAt(it).par }
    return names.map { (m, n) ->
        val t = view.total(m)
        val vs = t - par
        "$n $t (${if (vs > 0) "+$vs" else if (vs == 0) "par" else "$vs"})"
    }
}

// ---- Against others ---------------------------------------------------------------------------

@Composable
fun GolfGameScreen(state: BattleUiState, vm: BattleViewModel, onLeave: () -> Unit) {
    val p = AppTheme.palette
    val room = state.room ?: return
    val turns = room.turns ?: return
    val view = turns.golf ?: return
    val finished = room.status == RoomStatus.FINISHED
    val now = rememberTicker()
    val myTurn = turns.turn == "me" && !finished
    var reacting by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GOLF) { showRules = false }
    fun letterOf(index: Int) = "opq"[index]
    val turnName = turns.turnName ?: room.opponent?.name
    val current = if (myTurn) 'm' else room.others.indexOfFirst { it.name == turnName }.takeIf { it >= 0 }?.let(::letterOf)
    val names = listOf('m' to "You") + room.others.mapIndexed { i, o -> letterOf(i) to o.name }

    GameFrame(
        title = "Mini Golf",
        subtitle = "Game ${room.round}" + if (state.reconnecting) " · reconnecting…" else "",
        players = battleChips(
            room, state, now, MyDisc,
            detail = { player, isMe ->
                val mark = if (isMe) 'm' else letterOf(room.others.indexOf(player).coerceAtLeast(0))
                "${view.strokes[mark]?.getOrNull(view.hole) ?: 0} this hole"
            },
            active = { player, isMe -> !finished && if (isMe) myTurn else turns.turn == "opponent" && player.name == turnName },
            otherColor = { i -> markColor(letterOf(i)) },
        ),
        glow = GameKind.GOLF.color,
        dock = battleDock(finished, onLeave, if (room.opponent != null) ({ reacting = !reacting }) else null) { showRules = true },
        trayVisible = reacting,
        tray = { ReactionBar(onReact = { vm.react(it); reacting = false }) },
        popup = if (!finished) null else popupContent {
            MatchPopup(
                room, GameKind.GOLF,
                if (room.winner == null) Outcome.TIE else outcomeOf(room.winner),
                if (room.endReason == "left") "Game stopped" else resultTitle(room.winner, room.winnerName ?: "The other player"),
                when (room.endReason) {
                    "strokes" -> "Nine holes played: the fewest strokes wins."
                    "draw" -> "Level after nine holes."
                    "left" -> "A player left, so the game stopped."
                    else -> ""
                },
                onRematch = vm::readyTurns, onFinish = onLeave,
                scores = golfScores(view, names),
            )
        },
    ) {
        GolfHeader(view, names)
        GolfCountdown(view, names)
        TurnLine(
            when {
                finished -> "Round over"
                view.done['m'] == true -> "You're in! Waiting for the others…"
                myTurn -> yourShot(view)
                else -> "${turnName ?: "Someone"} is shooting…"
            },
            myTurn, GameKind.GOLF.color,
        )
        GolfPlay(view, current, myTurn && !state.firing, room.round, vm::shootGolf, Modifier.weight(1f).fillMaxWidth())
    }
}

/** Mini Golf's level: whoever opened the game picks how many islands every hole has; everyone else sees it. */
@Composable
fun GolfLevelCard(room: RoomState, busy: Boolean, onPick: (GolfLevel) -> Unit) {
    val p = AppTheme.palette
    val level = room.golfLevel ?: return
    AppCard("Which holes?") {
        if (room.setsOptions) {
            OptionGroup(GolfLevel.entries.map { it to it.label }, level, enabled = !busy, onPick = onPick)
        } else {
            Text("${level.label}, picked by whoever opened the game.", color = p.muted, fontSize = TextSize.Body)
        }
        Text("All nine holes are this kind, new from the 1,000 each game.", color = p.muted, fontSize = TextSize.Body)
    }
}

// ---- On your own ------------------------------------------------------------------------------

@Composable
fun SoloGolfScreen(state: SoloGolfUiState, vm: SoloGolfViewModel, onHome: () -> Unit) {
    val p = AppTheme.palette
    var showRules by remember { mutableStateOf(false) }
    if (showRules) RulesDialog(GameKind.GOLF) { showRules = false }
    val view = state.view
    val names = listOf('m' to "You") + (1..state.computers).map { "opq"[it - 1] to "CPU $it" }
    GameFrame(
        title = "Mini Golf",
        subtitle = if (state.computers == 0) "On your own" + (state.best?.let { " · best $it" } ?: "") else "Against ${state.computers} computer${if (state.computers > 1) "s" else ""}",
        players = emptyList(),
        glow = GameKind.GOLF.color,
        dock = soloDock(onHome, { showRules = true }, if (view != null) vm::start else null),
        scrollable = view == null,
        popup = if (view == null || state.result == null) null else popupContent {
            SoloPopup(
                GameKind.GOLF, state.result,
                golfScores(view, names).joinToString(" · "),
                onAgain = vm::start, onHome = onHome, finishLabel = "Change players", onFinish = vm::changeSetup,
            )
        },
    ) {
        if (view == null) {
            AppCard("Nine holes") {
                Text("Swipe to turn, pinch to zoom, and slide up the bar on the right to shoot: low rolls, high jumps over gaps and walls. Fewest strokes over nine holes wins.", color = p.muted, fontSize = TextSize.Body)
                Text("Holes", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                OptionGroup(GolfLevel.entries.map { it to it.label }, state.level, enabled = true, onPick = vm::setLevel)
                Text("Computers", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 0..3) {
                        val label = if (n == 0) "None" else "$n"
                        if (state.computers == n) PrimaryButton(label, {}, Modifier.weight(1f)) else SecondaryButton(label, { vm.setComputers(n) }, Modifier.weight(1f))
                    }
                }
                if (state.computers > 0) {
                    Text("How good", color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Easy", "Medium", "Hard").forEachIndexed { i, label ->
                            if (state.skill == i) PrimaryButton(label, {}, Modifier.weight(1f)) else SecondaryButton(label, { vm.setSkill(i) }, Modifier.weight(1f))
                        }
                    }
                }
                PrimaryButton("Tee off", vm::start, Modifier.fillMaxWidth())
            }
        } else {
            GolfHeader(view, names)
            GolfCountdown(view, names)
            val myTurn = state.turn == 'm' && state.result == null
            TurnLine(
                when {
                    state.result != null -> "Round over"
                    myTurn -> yourShot(view)
                    else -> "${names.firstOrNull { it.first == state.turn }?.second ?: "The computer"} is shooting…"
                },
                myTurn, GameKind.GOLF.color,
            )
            GolfPlay(view, state.turn, myTurn, state.game, vm::shoot, Modifier.weight(1f).fillMaxWidth())
        }
    }
}
