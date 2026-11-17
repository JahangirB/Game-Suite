package com.pippop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pippop.data.PackCatalog
import com.pippop.data.PackListing
import com.pippop.data.PackSticker
import com.pippop.data.StickerStore
import com.pippop.ui.art.PackStickerImage
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.DisplayFont
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/** The last place in the tray: opens the packs. Only drawn where the server has the "packs" cap. */
@Composable
internal fun PacksPlus(width: Dp, onClick: () -> Unit) {
    val p = AppTheme.palette
    val art = minOf(width - 6.dp, 42.dp)
    // The whole cell is the touch target; the picture inside leaves half a gap on every side.
    Box(
        Modifier.size(width, 48.dp).semantics { contentDescription = "More stickers" }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(art).clip(Shapes.S).background(p.accent).border(1.5.dp, p.ink, Shapes.S), contentAlignment = Alignment.Center) {
            Text("+", color = p.text, fontSize = TextSize.Title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** The packs: the stickers listed online, to add to the tray. A new sticker shows up as soon as it is published, with no app update. */
@Composable
fun PacksSheet(store: StickerStore, onDismiss: () -> Unit) {
    val catalog by store.catalog.collectAsState()
    val installed by store.installed.collectAsState()
    val downloading by store.downloading.collectAsState()
    val message by store.message.collectAsState()
    LaunchedEffect(Unit) { store.refresh() }
    ToySheet(onDismiss = onDismiss, skipPartiallyExpanded = true) {
        PacksSheetBody(
            catalog, installed, downloading, message,
            onAdd = store::add, onRetry = store::refresh, onMessageSeen = store::messageShown, onDone = onDismiss,
        )
    }
}

/** What the packs sheet holds (also drawn by screenshots, where the sheet's own window is not in the root). */
@Composable
internal fun PacksSheetBody(
    catalog: PackCatalog,
    installed: List<PackSticker>,
    downloading: Set<String>,
    message: String?,
    onAdd: (PackListing) -> Unit,
    onRetry: () -> Unit,
    onMessageSeen: () -> Unit,
    onDone: () -> Unit,
) {
    val p = AppTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.XL).padding(bottom = Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        Text("More stickers", color = p.text, fontSize = TextSize.DisplayM, fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
        Text(
            "Add one and it joins your tray. You can send it to anyone in the game.",
            color = p.muted, fontSize = TextSize.Body, modifier = Modifier.padding(bottom = Space.XS),
        )
        message?.let { m -> Text(m, color = p.bad, fontSize = TextSize.Body, modifier = Modifier.clickable(onClick = onMessageSeen)) }
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            when (catalog) {
                PackCatalog.NotSetUp -> Note("This copy of the app wasn't built with sticker packs. Builds made by the project's GitHub Actions have them.")
                PackCatalog.Loading -> Note("Looking for stickers…")
                is PackCatalog.Failed -> {
                    Note(catalog.why)
                    SecondaryButton("Try again", onRetry, Modifier.fillMaxWidth())
                }
                is PackCatalog.Ready -> {
                    if (catalog.stickers.isEmpty()) Note("No stickers yet. New ones show up here when they're published.")
                    catalog.stickers.forEach { listing ->
                        val have = installed.firstOrNull { it.id == listing.id }
                        PackRow(listing, have, busy = listing.id in downloading, onAdd = { onAdd(listing) })
                    }
                }
            }
        }
        PrimaryButton("Done", onDone, Modifier.fillMaxWidth().padding(top = Space.XS))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = AppTheme.palette.muted, fontSize = TextSize.Body, modifier = Modifier.padding(vertical = Space.M))
}

/** A sticker in the list: its picture once it is on the phone, its name and line, and one button. */
@Composable
private fun PackRow(listing: PackListing, have: PackSticker?, busy: Boolean, onAdd: () -> Unit) {
    val p = AppTheme.palette
    val update = have != null && have.version < listing.version
    Row(
        Modifier.fillMaxWidth().clip(Shapes.M).border(2.dp, p.hairline, Shapes.M).padding(Space.S),
        horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(Shapes.S).background(p.background), contentAlignment = Alignment.Center) {
            if (have != null) PackStickerImage(have, 56.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(listing.name, color = p.text, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (listing.tag.isNotEmpty()) Text(listing.tag, color = p.muted, fontSize = TextSize.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val button = when {
            busy -> "Adding…"
            have == null -> if (listing.size > 0) "Add · ${kb(listing.size)}" else "Add"
            update -> "Update"
            else -> "Added"
        }
        val available = !busy && (have == null || update)
        if (available) PrimaryButton(button, onAdd, Modifier.width(120.dp)) else SecondaryButton(button, {}, Modifier.width(120.dp), enabled = false)
    }
}

private fun kb(size: Int) = "${maxOf(1, (size + 512) / 1024)} KB"
