package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pippop.data.GameKind
import com.pippop.ui.art.GameIcon
import com.pippop.ui.art.color
import com.pippop.ui.art.tagline
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

private val ROW_HEIGHT = 64.dp

/**
 * The home's games in your own order: hold a game and drag it to its place, or move it with
 * the arrows. A "Put away" line sits in the list: games under it leave the shelf, games above
 * it are on it. Nothing is deleted, and every move is saved on this phone straight away.
 */
@Composable
fun ArrangeGamesScreen(
    order: List<GameKind>,
    putAway: Set<GameKind>,
    onChange: (order: List<GameKind>?, putAway: Set<GameKind>) -> Unit,
    onBack: () -> Unit,
) {
    val p = AppTheme.palette
    // The shelf, the line (null) and then what is put away: the line moves like a game does.
    var rows by remember { mutableStateOf(rowsOf(order, putAway)) }
    var dragging by remember { mutableStateOf<GameKind?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // One step down the list: a row and the gap under it.
    val step = with(LocalDensity.current) { (ROW_HEIGHT + Space.S).toPx() }

    fun save() {
        val line = rows.indexOf(null)
        onChange(rows.filterNotNull(), rows.drop(line + 1).filterNotNull().toSet())
    }

    fun move(from: Int, to: Int) {
        if (to !in rows.indices || from == to) return
        rows = rows.toMutableList().apply { add(to, removeAt(from)) }
        save()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Arrange games", onBack) {
            TextButton(onClick = onBack) { Text("Done", color = p.primaryText, fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            Text(
                "Hold a game and drag it to its new place, or use the arrows. Games under the Put away line leave the shelf; move them back above it any time. Your order is saved on this phone.",
                color = p.muted,
                fontSize = TextSize.Body,
            )
            val line = rows.indexOf(null)
            rows.forEachIndexed { index, kind ->
                if (kind == null) {
                    PutAwayLine()
                    return@forEachIndexed
                }
                key(kind) {
                    val lifted = dragging == kind
                    val away = index > line
                    Surface(
                        color = p.card,
                        shape = Shapes.M,
                        border = BorderStroke(if (lifted) 2.dp else 1.dp, if (lifted) p.ink else p.hairline),
                        shadowElevation = if (lifted) 6.dp else 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) offset else 0f }
                            .semantics { stateDescription = if (away) "Put away" else "On the shelf" }
                            .pointerInput(kind) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = kind; offset = 0f },
                                    onDragEnd = { dragging = null; offset = 0f },
                                    onDragCancel = { dragging = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val at = rows.indexOf(kind)
                                    // Past half a row, the game swaps with its neighbour (the line too) and the offset carries on from there.
                                    if (offset > step / 2 && at < rows.lastIndex) {
                                        move(at, at + 1)
                                        offset -= step
                                    } else if (offset < -step / 2 && at > 0) {
                                        move(at, at - 1)
                                        offset += step
                                    }
                                }
                            },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Space.M)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(40.dp).clip(Shapes.S).background(kind.color).border(2.dp, p.ink, Shapes.S),
                            ) { GameIcon(kind, Modifier.size(24.dp)) }
                            Spacer(Modifier.width(Space.M))
                            Column(Modifier.weight(1f)) {
                                Text(kind.label, color = if (away) p.muted else p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                                Text(kind.tagline, color = p.muted, fontSize = TextSize.Label, maxLines = 1)
                            }
                            IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Move ${kind.label} up", tint = if (index > 0) p.text else p.hairline)
                            }
                            IconButton(onClick = { move(index, index + 1) }, enabled = index < rows.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Move ${kind.label} down", tint = if (index < rows.lastIndex) p.text else p.hairline)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = {
                val away = rows.drop(rows.indexOf(null) + 1).filterNotNull().toSet()
                rows = rowsOf(GameKind.entries, away)
                onChange(null, away)
            }) { Text("Reset to the default order", color = p.primaryText) }
        }
    }
}

/** The shelf's games, then the line (null), then the games put away, each in [order]. */
private fun rowsOf(order: List<GameKind>, putAway: Set<GameKind>): List<GameKind?> =
    order.filter { it !in putAway } + listOf(null) + order.filter { it in putAway }

/** The "Put away" line: a divider as tall as a game, so dragging by whole rows keeps its sums. */
@Composable
private fun PutAwayLine() {
    val p = AppTheme.palette
    Column(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.M)) {
            HorizontalDivider(Modifier.weight(1f), color = p.ink, thickness = 2.dp)
            Text("Put away", color = p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.weight(1f), color = p.ink, thickness = 2.dp)
        }
        Text("Off the shelf, not deleted", color = p.muted, fontSize = TextSize.Label)
    }
}
