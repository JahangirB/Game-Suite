package com.pippop.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.MemoryPhotos
import com.pippop.data.RoomState
import com.pippop.ui.MemoryPicking
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.LineHeight
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.Space
import com.pippop.ui.theme.TextSize

/** What the lobby's "Pictures" row needs to show and change the deck's photos (Memory Pairs, where [RoomState.memoryPhotosOffered]). */
class MemoryPhotosPanel(
    /** The deck's photos fetched so far, by id ([com.pippop.ui.BattleUiState.memoryPics]). */
    val pics: Map<Int, String>,
    /** The photos being sent now, or null. */
    val picking: MemoryPicking?,
    /** Picks the animals (false) or "Your photos" (true). */
    val onDeck: (Boolean) -> Unit,
    /** The photos the opener chose; true fills the empty places, false replaces the set. */
    val onChosen: (List<Uri>, Boolean) -> Unit,
)

/** How one place of the photos deck looks in the lobby. */
internal sealed interface Thumb {
    /** Nothing sent for this place yet. */
    data object Empty : Thumb

    /** Sent, and its picture has not reached this phone yet (or could not be read). */
    data object Sent : Thumb

    /** Sent, and here is the picture. */
    data class Picture(val image: ImageBitmap) : Thumb
}

/** The places of the deck as thumbnails: from the photos being sent first, then from the ids the room holds and the pictures that have arrived. */
internal fun thumbsFor(room: RoomState, pics: Map<Int, String>, picking: MemoryPicking?): List<Thumb> = List(room.memoryPairs) { slot ->
    val local = picking?.thumbs?.get(slot)
    val id = room.memoryPhotoIds.getOrNull(slot)
    when {
        local != null -> MemoryPhotos.peek(local)?.let { Thumb.Picture(it) } ?: Thumb.Sent
        id == null -> Thumb.Empty
        else -> pics[id]?.let { MemoryPhotos.peek(it) }?.let { Thumb.Picture(it) } ?: Thumb.Sent
    }
}

/**
 * The deck's photos in the Memory lobby: a row of small pictures (one per pair), what to do next ("Eight photos make
 * sixteen cards."; while sending "3 of 8 sent"), the button that opens the phone's photo picker, and who sees the photos.
 * Only whoever opened the game ([enabled]) picks; the others see the pictures as they arrive.
 */
@Composable
internal fun MemoryPhotoPicker(room: RoomState, panel: MemoryPhotosPanel, enabled: Boolean) {
    val p = AppTheme.palette
    val pairs = room.memoryPairs
    val picking = panel.picking
    val thumbs = thumbsFor(room, panel.pics, picking)
    val have = thumbs.count { it !is Thumb.Empty }
    val missing = pairs - have
    // From the places filled now (not from the last tap): fill the empty ones, or (none or all filled) choose the whole set.
    val (fillMissing, ask) = MemoryPhotos.pickRule(pairs, have)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ask)) { uris ->
        if (uris.isNotEmpty()) panel.onChosen(uris, fillMissing)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.M)) {
        PhotoThumbs(thumbs)
        Text(
            if (picking != null) MemoryPhotos.progress(picking.sent, picking.total) else MemoryPhotos.note(pairs, have),
            color = if (picking != null) p.text else p.muted, fontSize = TextSize.Label, lineHeight = LineHeight.Label,
            fontWeight = if (picking != null) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (enabled || picking != null) SecondaryButton(
            if (picking != null) "Sending…" else MemoryPhotos.buttonLabel(pairs, have),
            {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            Modifier.fillMaxWidth(),
            enabled = enabled && picking == null,
        )
        Text(PRIVACY, color = p.muted, fontSize = TextSize.Label, lineHeight = LineHeight.Label)
    }
}

/** Who sees the photos and when they go (docs/COPY.md: short sentences, "game" and "room"). */
internal const val PRIVACY = "Photos go only to the players in this game. They are deleted when the room ends."

/** The thumbnails, up to eight in a row (a bigger deck makes more rows of six). */
@Composable
internal fun PhotoThumbs(thumbs: List<Thumb>) {
    val perRow = if (thumbs.size <= 8) thumbs.size.coerceAtLeast(1) else 6
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S)) {
        thumbs.chunked(perRow).forEachIndexed { row, cells ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S)) {
                cells.forEachIndexed { i, thumb -> PhotoThumb(thumb, row * perRow + i + 1, Modifier.weight(1f)) }
                repeat(perRow - cells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One place of the deck: the photo when it has arrived, a tick on a plain tile when it is sent but not here yet, and
 * an empty outlined tile when nothing was sent. The tick and the words ("Photo 3, sent") mean it is not told by colour alone.
 */
@Composable
internal fun PhotoThumb(thumb: Thumb, number: Int, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val words = when (thumb) {
        Thumb.Empty -> "Photo $number, not sent yet"
        else -> "Photo $number, sent"
    }
    Box(
        modifier.aspectRatio(1f).clip(Shapes.S)
            .background(if (thumb is Thumb.Empty) p.background else p.card)
            .border(if (thumb is Thumb.Empty) 1.dp else 1.5.dp, if (thumb is Thumb.Empty) p.hairline else p.ink, Shapes.S)
            .semantics { contentDescription = words },
        contentAlignment = Alignment.Center,
    ) {
        when (thumb) {
            is Thumb.Picture -> Image(thumb.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Thumb.Sent -> Text("✓", color = p.good, fontSize = TextSize.BodyL, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
            Thumb.Empty -> Unit
        }
    }
}
