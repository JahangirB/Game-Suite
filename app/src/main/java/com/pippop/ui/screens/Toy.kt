package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pippop.ui.theme.Tracking
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.BodyFont
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Radius
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Size
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/**
 * A question in the middle of the screen, in the app's look: a card with an ink outline, the
 * heading font and real buttons. The safe answer goes in a [SecondaryButton]; an answer that ends
 * something (leave, give up) is a [PrimaryButton] in the palette's `bad` colour. [actions] are laid
 * out in a row, each sharing the width; [content] sits between the words and the buttons.
 * Outside taps and Back call [onDismiss] unless [dismissible] is false.
 */
@Composable
fun ToyDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false,
        ),
    ) {
        ToyDialogCard(title, modifier, body, content, actions)
    }
}

/** The card of a [ToyDialog], without the window round it (so a screenshot can show it). */
@Composable
internal fun ToyDialogCard(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    val p = AppTheme.palette
    // Room round the card for its pressed edge, which is drawn outside it.
    Box(modifier.fillMaxWidth().padding(horizontal = Space.XL, vertical = Space.S), contentAlignment = Alignment.Center) {
        Surface(
            color = p.card, shape = Shapes.L, border = BorderStroke(2.dp, p.ink),
            modifier = Modifier.widthIn(max = Size.DialogMax).fillMaxWidth().pressedEdge(p.ink, Radius.L, 4.dp)
                .semantics { paneTitle = title },
        ) {
            Column(Modifier.padding(Space.XL), verticalArrangement = Arrangement.spacedBy(Space.M)) {
                Text(
                    title, modifier = Modifier.semantics { heading() }, color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold, lineHeight = LineHeight.DisplayM,
                )
                if (body != null) Text(body, color = p.muted, fontSize = TextSize.BodyL, lineHeight = LineHeight.BodyL)
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.M), content = actions)
            }
        }
    }
}

/**
 * A sheet rising from the bottom, as the result sheet looks: the card colour, an ink edge along
 * the top and the app's handle. It wraps [ModalBottomSheet], so dragging, insets and nested
 * scrolling behave as they always did; [skipPartiallyExpanded] is passed on. Put a sheet's
 * own scrolling in [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToySheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    containerColor: Color = AppTheme.palette.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Material's own surface is see-through; the card inside it carries the colour and the ink edge,
    // so the edge travels with the sheet. The insets are the ones Material applies by default.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        shape = Shapes.Top,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        ToySheetCard(containerColor = containerColor, onDismiss = onDismiss) {
            Column(Modifier.windowInsetsPadding(BottomSheetDefaults.windowInsets), content = content)
        }
    }
}

/** The small bar at the top of a sheet. It keeps the dismiss action screen readers get from Material's handle. */
@Composable
internal fun ToyHandle(onDismiss: (() -> Unit)? = null) {
    val p = AppTheme.palette
    Box(
        Modifier.fillMaxWidth().padding(top = Space.S, bottom = Space.XS).semantics {
            contentDescription = "Drag handle"
            if (onDismiss != null) dismiss { onDismiss(); true }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 44.dp, height = 5.dp).clip(Shapes.Full).background(p.hairline))
    }
}

/** What a [ToySheet] draws: the card colour, an ink edge along the top and the handle. Also shown by screenshots. */
@Composable
internal fun ToySheetCard(
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.palette.card,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = AppTheme.palette
    Surface(color = containerColor, shape = Shapes.Top, border = BorderStroke(3.dp, p.ink), modifier = modifier.fillMaxWidth()) {
        Column {
            ToyHandle(onDismiss)
            content()
        }
    }
}

/**
 * A text field in the app's look: its [label] above it (also its accessible name), an ink outline
 * that turns the primary colour while it has focus, room for a thumb, and an optional [help] line
 * under it. A field with no label is named by its [placeholder]. [trailing] sits inside the box,
 * at the end.
 */
@Composable
fun ToyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    help: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = AppTheme.palette
    var focused by remember { mutableStateOf(false) }
    val name = label ?: placeholder
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.XS)) {
        if (label != null) Text(label, color = p.muted, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(color = if (enabled) p.text else p.muted, fontSize = TextSize.BodyL, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(p.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { if (name != null) contentDescription = name },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Shapes.M).background(p.card)
                        .border(if (focused) 3.dp else 2.dp, if (focused) p.primary else if (enabled) p.ink else p.hairline, Shapes.M)
                        .padding(horizontal = Space.L, vertical = Space.S),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, color = p.muted, fontSize = TextSize.BodyL, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {})
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (help != null) Text(help, color = p.muted, fontSize = TextSize.Body)
    }
}

/**
 * A room code in one big box, its letters spaced out in the heading font. It takes what
 * the view model's `cleanCode` allows (letters and digits, up to 12): its setters clean what is typed. [onNew] adds a button at the
 * end that makes a new code; [onGo] is what the keyboard's Go does.
 */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Room code",
    onNew: (() -> Unit)? = null,
    onGo: (() -> Unit)? = null,
) {
    val p = AppTheme.palette
    var focused by remember { mutableStateOf(false) }
    val edge = if (focused) p.primary else p.ink
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = p.text, fontSize = TextSize.DisplayL, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
            letterSpacing = Tracking.CodeLarge, textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(p.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = if (onGo != null) ImeAction.Go else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onGo = { onGo?.invoke() }),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(72.dp).pressedEdge(edge, Radius.L).clip(Shapes.L).background(p.card)
                    .border(3.dp, edge, Shapes.L).padding(horizontal = Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Balances the button at the end, so the letters stay in the middle.
                if (onNew != null) Box(Modifier.size(48.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { inner() }
                if (onNew != null) IconButton(onClick = onNew) { Icon(Icons.Filled.Refresh, "New code", tint = p.text) }
            }
        },
    )
}

/**
 * One choice among a few: the [options] (value to label) side by side in one pill, the chosen one
 * filled. Up to four sit in one row; more wrap into rows of equal cells. Each part is a radio
 * button (so a screen reader says "selected" and "1 of 3"), 48 dp tall, and the chosen one is bold
 * as well as filled. [off] lists options that cannot be picked now (greyed); [enabled] false
 * freezes all of them. Picking the chosen one again does nothing. [perRow] sets how many parts share a row (1 stacks them full width, for long labels).
 */
@Composable
fun <T> Choices(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    off: Set<T> = emptySet(),
    fill: Color = AppTheme.palette.primary,
    onFill: Color = AppTheme.palette.onPrimary,
    perRow: Int? = null,
) {
    val bigText = LocalDensity.current.fontScale > 1.2f
    val rowLimit = if (bigText && options.size >= 4) 2 else 4
    val rows = (options.size + rowLimit - 1) / rowLimit
    val perRow = (perRow ?: ((options.size + rows - 1) / rows)).coerceAtLeast(1)
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        options.chunked(perRow).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth()) {
                ChoicesPill(Modifier.weight(rowOptions.size.toFloat())) {
                    rowOptions.forEach { (value, text) ->
                        val on = value == selected
                        val usable = enabled && value !in off
                        Box(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                                .background(if (on) fill else Color.Transparent)
                                .selectable(selected = on, enabled = usable, role = Role.RadioButton) { if (!on) onPick(value) }
                                .padding(horizontal = Space.M),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text, color = if (on) onFill else AppTheme.palette.text, fontSize = TextSize.Body,
                                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(if (usable || on) 1f else 0.4f),
                            )
                        }
                    }
                }
                // A short last row keeps its cells the same width as the rows above it.
                val gap = perRow - rowOptions.size
                if (gap > 0) Spacer(Modifier.weight(gap.toFloat()))
            }
        }
    }
}

@Composable
private fun ChoicesPill(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.height(IntrinsicSize.Min).clip(Shapes.Full).background(AppTheme.palette.card).border(2.dp, AppTheme.palette.ink, Shapes.Full), content = content)
}

/**
 * One setting: its [label], the [Choices] beside it, and a [note] under them only where a choice
 * needs explaining (it lines up with the choices).
 */
@Composable
fun <T> OptionRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
    off: Set<T> = emptySet(),
) {
    val p = AppTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.XS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.width(OptionLabelWidth))
            Choices(options, selected, onPick, Modifier.weight(1f), enabled = enabled, off = off)
        }
        if (note != null) Text(note, color = p.muted, fontSize = TextSize.Label, lineHeight = LineHeight.Label, modifier = Modifier.padding(start = OptionLabelWidth))
    }
}

private val OptionLabelWidth = 92.dp

/**
 * The chunky switch: the knob sits left when off and right when on, so the state shows in more
 * than colour. With [onCheckedChange] it is a 48 dp switch of its own; pass null when the row
 * round it is the toggle (as Material's switch allows) and it only shows the state.
 */
@Composable
fun ToySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = AppTheme.palette
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else Modifier
    Box(modifier.then(toggle).size(width = 54.dp, height = if (onCheckedChange != null) 48.dp else 32.dp).alpha(if (enabled) 1f else 0.5f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = 54.dp, height = 32.dp).clip(Shapes.Full).background(if (checked) p.primary else p.accent).border(2.dp, p.ink, Shapes.Full),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(horizontal = Space.XS).size(22.dp).pressedEdge(p.ink, 11.dp, 2.dp).clip(CircleShape).background(p.card).border(2.dp, p.ink, CircleShape))
        }
    }
}
