package com.arcade.sudoku.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arcade.sudoku.data.GameKind
import com.arcade.sudoku.ui.art.GameIcon
import com.arcade.sudoku.ui.art.color
import com.arcade.sudoku.ui.art.tagline
import com.arcade.sudoku.ui.theme.AppTheme
import com.arcade.sudoku.ui.theme.DisplayFont
import com.arcade.sudoku.ui.theme.Shapes
import com.arcade.sudoku.ui.theme.Space
import com.arcade.sudoku.ui.theme.TextSize

private val ROW_HEIGHT = 64.dp

/**
 * The home's games in your own order: hold a game and drag it to its place, or move it with
 * the arrows. Every move is saved on this phone straight away.
 */
@Composable
fun ArrangeGamesScreen(order: List<GameKind>, onChange: (List<GameKind>?) -> Unit, onBack: () -> Unit) {
    val p = AppTheme.palette
    var games by remember { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<GameKind?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // One step down the list: a row and the gap under it.
    val step = with(LocalDensity.current) { (ROW_HEIGHT + Space.S).toPx() }

    fun move(from: Int, to: Int) {
        if (to !in games.indices || from == to) return
        games = games.toMutableList().apply { add(to, removeAt(from)) }
        onChange(games)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Arrange games", onBack) {
            TextButton(onClick = onBack) { Text("Done", color = p.primaryText, fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            Text("Hold a game and drag it to its new place, or use the arrows. Your order is saved on this phone.", color = p.muted, fontSize = TextSize.Body)
            games.forEachIndexed { index, kind ->
                key(kind) {
                    val lifted = dragging == kind
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
                            .pointerInput(kind) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = kind; offset = 0f },
                                    onDragEnd = { dragging = null; offset = 0f },
                                    onDragCancel = { dragging = null; offset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val at = games.indexOf(kind)
                                    // Past half a row, the game swaps with its neighbour and the offset carries on from there.
                                    if (offset > step / 2 && at < games.lastIndex) {
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
                                Text(kind.label, color = p.text, fontSize = TextSize.BodyL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
                                Text(kind.tagline, color = p.muted, fontSize = TextSize.Label, maxLines = 1)
                            }
                            IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Move ${kind.label} up", tint = if (index > 0) p.text else p.hairline)
                            }
                            IconButton(onClick = { move(index, index + 1) }, enabled = index < games.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Move ${kind.label} down", tint = if (index < games.lastIndex) p.text else p.hairline)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = {
                games = GameKind.entries
                onChange(null)
            }) { Text("Reset to the default order", color = p.primaryText) }
        }
    }
}
