package com.pippop.ui.screens

import com.pippop.ui.theme.TextSize
import com.pippop.ui.theme.Shapes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Radius

/** Undo, Erase, Notes and (when [hintsLeft] is given) Hint, like the tool row of popular Sudoku apps. */
@Composable
fun ToolRow(
    notesMode: Boolean,
    canUndo: Boolean,
    enabled: Boolean,
    hintsLeft: Int?,
    onUndo: () -> Unit,
    onErase: () -> Unit,
    onNotes: () -> Unit,
    onHint: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Tool("Undo", GameIcons.Undo, enabled && canUndo, onClick = onUndo)
        Tool("Erase", GameIcons.Eraser, enabled, onClick = onErase)
        Tool("Notes", Icons.Filled.Edit, enabled, badge = if (notesMode) "ON" else "OFF", highlighted = notesMode, onClick = onNotes)
        if (hintsLeft != null) {
            Tool("Hint", GameIcons.Hint, enabled && hintsLeft > 0, badge = hintsLeft.toString(), highlighted = true, onClick = onHint)
        }
    }
}

@Composable
private fun Tool(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
    highlighted: Boolean = false,
) {
    val p = AppTheme.palette
    val tint = if (enabled) p.text else p.muted.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Box {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.padding(6.dp).size(28.dp))
            if (badge != null) {
                Text(
                    badge,
                    color = if (highlighted && enabled) p.onPrimary else p.muted,
                    fontSize = TextSize.Micro,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-2).dp)
                        .background(if (highlighted && enabled) p.primary else p.accent, Shapes.S)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(label, color = tint, fontSize = TextSize.Label)
    }
}

/**
 * The digits 1-9 under the board, each with what's left: [remaining] gives how many of each digit
 * are still missing, shown as dots (or the number, past nine). A digit that's all placed turns
 * pale green with a tick and can't be pressed.
 */
@Composable
fun NumberPad(remaining: (Int) -> Int, enabled: Boolean, onDigit: (Int) -> Unit) {
    val p = AppTheme.palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (digit in 1..9) {
            val left = remaining(digit)
            val done = left <= 0
            Box(Modifier.weight(1f).height(60.dp).padding(bottom = 3.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        // A key on its pressed edge, like the app's buttons; a finished one sits
                        // flat on the table, pressed down for good.
                        .then(if (done) Modifier.offset(y = 3.dp) else Modifier.pressedEdge(p.ink, Radius.S))
                        .clip(Shapes.S)
                        .background(if (done) lerp(p.card, p.good, 0.18f) else p.card)
                        .border(2.dp, if (done) p.good.copy(alpha = 0.5f) else p.ink, Shapes.S)
                        .semantics { contentDescription = if (done) "$digit, all placed" else "$digit, $left left" }
                        .clickable(
                            enabled = enabled && !done,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onDigit(digit) },
                ) {
                    val ink = when {
                        done -> p.muted.copy(alpha = 0.6f)
                        enabled -> p.text
                        else -> p.text.copy(alpha = 0.4f)
                    }
                    Text(
                        digit.toString(),
                        fontSize = TextSize.DisplayM,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        modifier = Modifier.offset(y = (-4).dp),
                    )
                    val count = if (enabled) p.entry else p.entry.copy(alpha = 0.4f)
                    val under = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp)
                    when {
                        done -> Canvas(Modifier.align(Alignment.TopEnd).padding(3.dp).size(12.dp)) {
                            doneTick(center, size.minDimension / 2, p.good, 1f)
                        }
                        // Past nine (the big boards) dots would not fit: the number instead.
                        left > 9 -> Text(left.toString(), color = count, fontSize = TextSize.Micro, lineHeight = LineHeight.Micro, fontWeight = FontWeight.Bold, modifier = under)
                        else -> Canvas(under.fillMaxWidth().height(4.dp)) {
                            // One dot per copy left, centred in a row that fits nine across the key.
                            val step = minOf(6.dp.toPx(), (size.width - 4.dp.toPx()) / 9)
                            val radius = minOf(1.6.dp.toPx(), step * 0.36f)
                            val start = center.x - step * (left - 1) / 2
                            for (k in 0 until left) drawCircle(count, radius, Offset(start + k * step, center.y))
                        }
                    }
                }
            }
        }
    }
}

/** A thin tinted pill used for small status labels. */
@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = TextSize.Label,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), Shapes.S)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
