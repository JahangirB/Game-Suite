package com.pippop.ui.screens

import androidx.compose.foundation.BorderStroke
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
