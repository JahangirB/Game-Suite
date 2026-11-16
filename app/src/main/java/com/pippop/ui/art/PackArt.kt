package com.pippop.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pippop.data.PackSticker
import com.pippop.ui.ShownReaction

/**
 * The drawing for a downloaded sticker, or null if its shapes cannot be drawn (the file was read as data, so
 * this is the last check: a path the drawing code cannot read is dropped here, and the sticker is not shown).
 */
internal fun PackSticker.toSticker(): Sticker? = runCatching {
    val back = backdrop?.let { b ->
        val n = b.n
        when (b.type) {
            "linear" -> Backdrop.linear(b.colours, n[0], n[1], n[2], n[3])
            "radial" -> Backdrop.radial(b.colours, n[0], n[1], n[2])
            else -> Backdrop.solid(b.colours.first())
        }
    }
    val drawn = Sticker(
        back,
        parts.map { Part(it.d, it.fill, it.stroke, it.width, it.dash?.toFloatArray(), it.roundCap, it.roundJoin, it.anim, it.group) },
    )
    // Read every path and every group's box now, where a failure is caught, not while drawing, and make the
    // backdrop's shader now: anything that would fail while drawing fails here, and the sticker is not shown.
    drawn.parts.forEach { it.path }
    drawn.bounds
    (drawn.backdrop?.brush as? ShaderBrush)?.createShader(Size(120f, 120f))
    // A dashed line is drawn one dash at a time: refuse one with a hopeless number of them.
    parts.forEachIndexed { i, part ->
        val dash = part.dash
        if (dash != null) {
            val measure = android.graphics.PathMeasure(drawn.parts[i].path.asAndroidPath(), false)
            var length = 0f
            do { length += measure.length } while (measure.nextContour())
            require(length / dash.sum() <= MAX_DASHES) { "too many dashes" }
        }
    }
    drawn
}.getOrNull()

/** The most dashes one dashed line may be cut into. */
private const val MAX_DASHES = 5000

/** A downloaded sticker at rest, [size] square (the tray, the packs sheet, a player's chip). */
@Composable
fun PackStickerImage(pack: PackSticker, size: Dp, modifier: Modifier = Modifier) {
    val sticker = remember(pack) { pack.toSticker() } ?: return
    Canvas(modifier.size(size)) { drawSticker(sticker) }
}

/** What a player's chip draws for their [shown] reaction: the reaction, or a downloaded sticker. A line has no picture. */
@Composable
fun ShownReactionImage(shown: ShownReaction, size: Dp, modifier: Modifier = Modifier) {
    val reaction = shown.reaction
    val pack = shown.pack
    when {
        reaction != null -> ReactionImage(reaction, size, modifier, startedAt = shown.at)
        pack != null -> PackStickerImage(pack, size, modifier)
    }
}
