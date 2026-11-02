package com.pippop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pippop.data.RemoteSkin
import com.pippop.data.ShipStyle
import com.pippop.data.SkinCatalog
import com.pippop.data.SkinStore
import com.pippop.game.ships.Ship
import com.pippop.ui.art.ShipLook
import com.pippop.ui.art.drawSeaArt
import com.pippop.ui.art.drawShipArt
import com.pippop.ui.art.drawShotArt
import com.pippop.ui.art.shipKinds
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/** How many skins listed online you haven't looked at yet: the dot on the Skins button. */
@Composable
fun newSkinCount(): Int {
    val catalog by SkinStore.catalog.collectAsState()
    val seen by SkinStore.seen.collectAsState()
    val installed by SkinStore.installed.collectAsState()
    val listed = (catalog as? SkinCatalog.Ready)?.skins.orEmpty()
    return listed.count { r -> r.id !in seen && installed.none { it.id == r.id } }
}

/**
 * Ship Battle's skins, from the Skins button in the game: the skins on this phone (the two
 * built in and any downloaded), and the ones you can download. A new skin shows up under
 * Available as soon as it's published, with no app update.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinStoreSheet(builtIn: ShipStyle, onBuiltIn: (ShipStyle) -> Unit, onDone: () -> Unit) {
    val p = AppTheme.palette
    val installed by SkinStore.installed.collectAsState()
    val catalog by SkinStore.catalog.collectAsState()
    val previews by SkinStore.previews.collectAsState()
    val downloading by SkinStore.downloading.collectAsState()
    val selected by SkinStore.selected.collectAsState()
    val seen by SkinStore.seen.collectAsState()
    val message by SkinStore.message.collectAsState()
    // Seen when the window opened: what was New then stays marked New while it's open.
    val seenBefore = remember { seen }
    LaunchedEffect(Unit) { SkinStore.refresh() }
    val listed = (catalog as? SkinCatalog.Ready)?.skins.orEmpty()
    // Online skins not on the phone, or with a newer version than the phone's.
    val available = listed.filter { r -> installed.none { it.id == r.id && it.version >= r.version } }
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(tab, available.size) { if (tab == 1) SkinStore.markSeen(available.map { it.id }) }
    val using = installed.firstOrNull { it.id == selected }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.card) {
        Column(Modifier.padding(horizontal = Space.L).padding(bottom = Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ship skins", color = p.text, fontSize = TextSize.Title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Done", color = p.primaryText, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold) }
            }
            // The two tabs, as one segmented bar.
            Row(Modifier.fillMaxWidth().clip(Shapes.M).background(p.background).padding(Space.XS)) {
                listOf("On your phone (${installed.size + ShipStyle.entries.size})", "Available (${available.size})").forEachIndexed { i, label ->
                    Text(
                        label,
                        color = if (tab == i) p.text else p.muted,
                        fontSize = TextSize.Body,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(Shapes.S).background(if (tab == i) p.card else p.background)
                            .clickable { tab = i }.padding(vertical = Space.S),
                    )
                }
            }
            message?.let { m ->
                Text(m, color = p.bad, fontSize = TextSize.Body, modifier = Modifier.clickable { SkinStore.messageShown() })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(Space.M),
                verticalArrangement = Arrangement.spacedBy(Space.M),
                modifier = Modifier.heightIn(max = 560.dp),
            ) {
                if (tab == 0) {
                    items(ShipStyle.entries, key = { "built-in-${it.name}" }) { style ->
                        val inUse = using == null && builtIn == style
                        SkinCard(
                            name = style.label, tag = "Built in",
                            picture = { BoardPreview(ShipLook.BuiltIn(style)) },
                            button = if (inUse) "In use" else "Use", enabled = !inUse,
                            onClick = {
                                onBuiltIn(style)
                                SkinStore.use(null)
                            },
                        )
                    }
                    items(installed, key = { it.id }) { skin ->
                        val inUse = using?.id == skin.id
                        SkinCard(
                            name = skin.name, tag = skin.tag,
                            picture = { BoardPreview(ShipLook.Downloaded(skin)) },
                            button = if (inUse) "In use" else "Use", enabled = !inUse,
                            onClick = { SkinStore.use(skin.id) },
                            onRemove = { SkinStore.remove(skin.id) },
                        )
                    }
                } else {
                    when (val c = catalog) {
                        SkinCatalog.NotSetUp -> item(span = { GridItemSpan(2) }) { Note("This copy of the app wasn't built with a skin store. Builds made by the project's GitHub Actions have one.") }
                        SkinCatalog.Loading -> item(span = { GridItemSpan(2) }) { Note("Looking for skins…") }
                        is SkinCatalog.Failed -> item(span = { GridItemSpan(2) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
                                Note(c.why)
                                SecondaryButton("Try again", { SkinStore.refresh() }, Modifier.fillMaxWidth())
                            }
                        }
                        is SkinCatalog.Ready -> {
                            if (available.isEmpty()) item(span = { GridItemSpan(2) }) { Note("You have every skin there is. New ones show up here when they're published.") }
                            items(available, key = { it.id }) { skin ->
                                val update = installed.any { it.id == skin.id }
                                SkinCard(
                                    name = skin.name, tag = skin.tag,
                                    picture = {
                                        val picture = previews[skin.id]
                                        if (picture != null) {
                                            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                                        } else {
                                            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(p.background))
                                        }
                                    },
                                    isNew = skin.id !in seenBefore && !update,
                                    button = when {
                                        skin.id in downloading -> "Downloading…"
                                        update -> "Update · ${kb(skin)}"
                                        else -> "Download · ${kb(skin)}"
                                    },
                                    enabled = skin.id !in downloading,
                                    onClick = { SkinStore.download(skin, use = !update) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun kb(skin: RemoteSkin) = "${maxOf(1, (skin.size + 512) / 1024)} KB"

@Composable
private fun Note(text: String) {
    Text(text, color = AppTheme.palette.muted, fontSize = TextSize.Body, modifier = Modifier.padding(vertical = Space.L))
}

/** A skin in the store: its picture, name and line, and one button. */
@Composable
private fun SkinCard(
    name: String,
    tag: String,
    picture: @Composable () -> Unit,
    button: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isNew: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    val p = AppTheme.palette
    Column(
        Modifier.clip(Shapes.M).border(2.dp, p.hairline, Shapes.M).padding(Space.S),
        verticalArrangement = Arrangement.spacedBy(Space.XS),
    ) {
        Box {
            Box(Modifier.clip(Shapes.S)) { picture() }
            if (isNew) {
                Text(
                    "New", color = p.text, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(Space.XS).clip(Shapes.S).background(p.warning).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Text(name, color = p.text, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(tag, color = p.muted, fontSize = TextSize.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        PrimaryButton(button, onClick, Modifier.fillMaxWidth(), enabled = enabled)
        onRemove?.let {
            Text(
                "Remove", color = p.muted, fontSize = TextSize.Label, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = it).padding(vertical = 2.dp),
            )
        }
    }
}

private val PREVIEW_FLEET = listOf(Ship(1, 1, 5, true), Ship(8, 0, 4, false), Ship(2, 5, 3, true), Ship(6, 6, 3, false), Ship(1, 8, 2, true))

/** A board in [look]: the fleet, a few misses and hits, as the store shows a skin. */
@Composable
private fun BoardPreview(look: ShipLook) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val s = size.width / 10
        drawSeaArt(look, 10, s)
        PREVIEW_FLEET.zip(shipKinds(PREVIEW_FLEET)).forEach { (ship, kind) -> drawShipArt(look, kind, ship, s, sunk = false) }
        for ((x, y) in listOf(0 to 3, 5 to 3, 7 to 4, 4 to 7, 9 to 8, 3 to 9)) drawShotArt(look, x, y, false, s)
        for ((x, y) in listOf(2 to 1, 3 to 1, 6 to 7)) drawShotArt(look, x, y, true, s)
    }
}
