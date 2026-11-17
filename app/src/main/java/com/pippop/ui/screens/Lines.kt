package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pippop.data.ChatLines
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/** The touch target of a line chip and of the pencil. */
private val ChipTarget = 48.dp

/** One of the player's lines as a chip in the tray: a tap says it. The whole 48 dp is the target. */
@Composable
internal fun LineChip(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Box(
        modifier.heightIn(min = ChipTarget).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.pressedEdge(p.ink, 18.dp, 2.dp).clip(Shapes.Full).background(p.card).border(2.dp, p.ink, Shapes.Full)
                .padding(horizontal = Space.M, vertical = 7.dp),
        )
    }
}

/** The row above the reaction grid: the pencil that edits your lines, then the lines. */
@Composable
internal fun LinesRow(lines: List<String>, enabled: Boolean, onLine: (String) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ChipTarget).semantics { contentDescription = "Edit your lines" }.clickable(role = Role.Button, onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(p.accent).border(1.5.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Edit, null, tint = p.text, modifier = Modifier.size(18.dp))
            }
        }
        lines.forEach { line -> LineChip(line, enabled, onClick = { onLine(line) }) }
    }
}

/**
 * Something a player said with one tap, by their chip. A screen reader says "[speaker] says: [text]"
 * when it appears; for [mine] it reads "You said: [text]" and does not announce it. It shows for as long as a reaction does ([visibleReaction] decides).
 */
@Composable
fun Bubble(text: String, speaker: String, modifier: Modifier = Modifier, mine: Boolean = false) {
    val p = AppTheme.palette
    Surface(
        color = p.card, shape = RoundedCornerShape(topStart = Radius.L, topEnd = Radius.XS, bottomEnd = Radius.L, bottomStart = Radius.L),
        border = BorderStroke(2.dp, p.ink),
        modifier = modifier.pressedEdge(p.ink, Radius.L, 2.dp).semantics(mergeDescendants = true) {
            // Your own line is not announced: you just tapped it.
            contentDescription = if (mine) "You said: $text" else "$speaker says: $text"
            if (!mine) liveRegion = LiveRegionMode.Polite
        },
    ) {
        Text(
            text, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Space.M, vertical = 7.dp).clearAndSetSemantics {},
        )
    }
}

/** Your six lines, written once and kept on your phone. [onSave] gets them as typed; the store cleans them. */
@Composable
fun LinesSheet(lines: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(lines) }
    fun finish() { onSave(draft); onDismiss() }
    ToySheet(onDismiss = ::finish, skipPartiallyExpanded = true) {
        LinesSheetBody(draft, onChange = { draft = it }, onDone = ::finish)
    }
}

/** What the lines sheet holds (also drawn by screenshots, where the sheet's own window is not in the root). */
@Composable
internal fun LinesSheetBody(draft: List<String>, onChange: (List<String>) -> Unit, onDone: () -> Unit) {
    val p = AppTheme.palette
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.XL).padding(bottom = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        Text("Your lines", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text(
            "Six things to say with one tap, up to ${ChatLines.MAX} letters each. Leave one empty to get the first one back.",
            color = p.muted, fontSize = TextSize.Body,
            modifier = Modifier.padding(bottom = Space.XS),
        )
        for (i in 0 until ChatLines.COUNT) {
            val value = draft.getOrElse(i) { "" }
            ToyField(
                value = value,
                onValueChange = { typed -> onChange(List(ChatLines.COUNT) { j -> if (j == i) ChatLines.limit(typed) else draft.getOrElse(j) { "" } }) },
                placeholder = ChatLines.DEFAULTS[i],
                name = "Line ${i + 1}",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (i == ChatLines.COUNT - 1) ImeAction.Done else ImeAction.Next),
                trailing = {
                    // Hidden while the field is empty (its placeholder is the default it falls back to).
                    val n = ChatLines.length(value)
                    Text(
                        "$n/${ChatLines.MAX}", color = p.muted, fontSize = TextSize.Label,
                        modifier = Modifier.padding(start = Space.S).alpha(if (value.isEmpty()) 0f else 1f)
                            .clearAndSetSemantics { if (value.isNotEmpty()) contentDescription = "$n of ${ChatLines.MAX}" },
                    )
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S), modifier = Modifier.padding(top = Space.XS)) {
            SecondaryButton("Reset to defaults", { onChange(ChatLines.DEFAULTS) }, Modifier.weight(1f))
            PrimaryButton("Done", onDone, Modifier.weight(1f))
        }
    }
}
