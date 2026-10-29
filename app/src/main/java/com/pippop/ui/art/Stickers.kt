package com.pippop.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos

/** One shape of a sticker drawing (see StickerArt): SVG path data, how it's painted, and its animation. */
internal class Part(
    d: String,
    fill: Long?,
    stroke: Long?,
    width: Float,
    dash: FloatArray?,
    roundCap: Boolean,
    roundJoin: Boolean,
    /** The animation it moves with ("iris", "flame b", ...), see [pose]; the [group] moves as one. */
    val anim: String? = null,
    val group: Int = -1,
) {
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
    val fill = fill?.let { Color(it) }
    val stroke = stroke?.let { Color(it) }
    val style = Stroke(
        width = width,
        cap = if (roundCap) StrokeCap.Round else StrokeCap.Butt,
        join = if (roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
        pathEffect = dash?.let { PathEffect.dashPathEffect(it) },
    )
}

/** What's behind a sticker's drawing, on its 120-unit grid. */
internal class Backdrop(val brush: Brush) {
    companion object {
        fun solid(colour: Long) = Backdrop(SolidColor(Color(colour)))

        fun linear(colours: List<Long>, x1: Float, y1: Float, x2: Float, y2: Float) =
            Backdrop(Brush.linearGradient(colours.map { Color(it) }, Offset(x1, y1), Offset(x2, y2)))

        fun radial(colours: List<Long>, cx: Float, cy: Float, r: Float) =
            Backdrop(Brush.radialGradient(colours.map { Color(it) }, Offset(cx, cy), r))
    }
}

/**
 * A sticker drawing. With a [backdrop] it's a square meme card with a frame; without one
 * (Kylian) it's just the figure.
 */
internal class Sticker(val backdrop: Backdrop?, val parts: List<Part>) {
    /** Each animated group's bounds: the box its moves turn and grow about. */
    val bounds: Map<Int, Rect> by lazy {
        parts.filter { it.group >= 0 }.groupBy { it.group }.mapValues { (_, ps) ->
            ps.map { it.path.getBounds() }.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        }
    }
}

private const val GRID = 120f
private val FRAME = Color(0xFF14121C)

/**
 * Draws [sticker] filling this draw area, [ms] into its animation; null draws it at rest (the
 * finished pose), for the picker and the name chips.
 */
internal fun DrawScope.drawSticker(sticker: Sticker, ms: Long? = null) {
    scale(size.minDimension / GRID, Offset.Zero) {
        val backdrop = sticker.backdrop
        if (backdrop == null) {
            clipRect(0f, 0f, GRID, GRID) { drawParts(sticker, ms) }
            return@scale
        }
        val card = Path().apply { addRoundRect(RoundRect(0f, 0f, GRID, GRID, CornerRadius(12f))) }
        clipPath(card) {
            drawRect(backdrop.brush, size = Size(GRID, GRID))
            drawParts(sticker, ms)
        }
        drawRoundRect(FRAME, Offset(1.5f, 1.5f), Size(GRID - 3f, GRID - 3f), CornerRadius(11f), style = Stroke(3f))
    }
}

private fun DrawScope.drawParts(sticker: Sticker, ms: Long?) {
    val poses = HashMap<Int, Pose>()
    sticker.parts.forEach { part ->
        val anim = part.anim
        if (anim == null) return@forEach drawPart(part, part.path, 1f)
        val pose = poses.getOrPut(part.group) { pose(anim, ms) }
        val box = sticker.bounds.getValue(part.group)
        val pivot = when (pose.pivot) {
            Pivot.CENTER -> box.center
            Pivot.TOP -> Offset(box.center.x, box.top)
            Pivot.BOTTOM -> Offset(box.center.x, box.bottom)
            Pivot.LEFT_BOTTOM -> Offset(box.left, box.bottom)
        }
        val path = if (pose.trim < 1f) trimmed(part.path, pose.trim) else part.path
        withTransform({
            translate(pose.dx, pose.dy)
            rotate(pose.rotation, pivot)
            scale(pose.sx, pose.sy, pivot)
        }) { drawPart(part, path, pose.alpha) }
    }
}

private fun DrawScope.drawPart(part: Part, path: Path, alpha: Float) {
    part.fill?.let { drawPath(path, it, alpha = alpha) }
    part.stroke?.let { drawPath(path, it, alpha = alpha, style = part.style) }
}

/** The first [fraction] of [path], for a line that draws itself. */
private fun trimmed(path: Path, fraction: Float): Path {
    val measure = PathMeasure().apply { setPath(path, false) }
    return Path().also { measure.getSegment(0f, measure.length * fraction, it, true) }
}

private enum class Pivot { CENTER, TOP, BOTTOM, LEFT_BOTTOM }

private data class Pose(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotation: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val alpha: Float = 1f,
    /** How much of a line is drawn, for one drawing itself. */
    val trim: Float = 1f,
    val pivot: Pivot = Pivot.CENTER,
)

/**
 * Where a group is [ms] into its sticker (null: at rest). The same moves as the catalogue
 * drawings in scripts/art, played once over about 2.5 s; flames, stars and glows keep going.
 */
private fun pose(anim: String, ms: Long?): Pose {
    val name = anim.substringBefore(' ')
    val variant = anim.substringAfter(' ', "")
    val t = ms?.toFloat()
    return when (name) {
        "flame" -> {
            val (period, ahead) = when (variant) { "b" -> 660f to 200f; "c" -> 480f to 400f; else -> 550f to 0f }
            val k = if (t == null) 0f else wave((t + ahead) / period)
            Pose(sx = 1f - 0.12f * k, sy = 1f + 0.2f * k, pivot = Pivot.BOTTOM)
        }
        "bubble" -> Pose(sx = pop(t, 0f), sy = pop(t, 0f), pivot = Pivot.LEFT_BOTTOM)
        "sip" -> {
            val k = if (t == null) 0f else easeOut(span(t, 1500f, 1800f)) - easeOut(span(t, 2160f, 2460f))
            Pose(dx = -3f * k, dy = -5f * k, rotation = -18f * k)
        }
        "brow-up" -> Pose(dy = -4f * (if (t == null) 1f else easeOut(span(t, 1020f, 1260f))))
        "iris" -> Pose(dx = 9f * (if (t == null) 1f else easeOut(span(t, 360f, 780f))))
        "lid" -> Pose(sy = if (t == null) 1f else 0.15f + 0.85f * easeOut(span(t, 660f, 1020f)), pivot = Pivot.TOP)
        "rise" -> Pose(trim = if (t == null) 1f else easeInOut(span(t, 0f, 1260f)))
        "tip" -> Pose(sx = pop(t, 1100f), sy = pop(t, 1100f))
        "smug" -> {
            val k = if (t == null) 0f else easeOut(span(t, 1200f, 1500f)) - easeOut(span(t, 1500f, 1800f))
            Pose(dy = -3f * k, rotation = -4f * k)
        }
        "glow" -> {
            val k = if (t == null) 1f else wave(t / 1600f)
            Pose(sx = 0.92f + 0.2f * k, sy = 0.92f + 0.2f * k, alpha = 0.55f + 0.45f * k)
        }
        "rays" -> Pose(rotation = if (t == null) 0f else 360f * ((t / 6000f) % 1f))
        "tw" -> {
            val ahead = when (variant) { "b" -> 500f; "c" -> 900f; else -> 0f }
            Pose(alpha = 0.25f + 0.75f * (if (t == null) 1f else wave((t + ahead) / 1300f)))
        }
        else -> Pose()
    }
}

/** 0 → 1 → 0 once per unit of [x]: a smooth back-and-forth. */
private fun wave(x: Float) = ((1 - cos(2 * PI * x)) / 2).toFloat()

private fun span(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(x: Float) = 1 - (1 - x) * (1 - x)

private fun easeInOut(x: Float) = if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2) * (-2 * x + 2) / 2

/** A pop-in [delay] ms in: from nothing to a little too big, then settling. */
private fun pop(t: Float?, delay: Float): Float {
    if (t == null) return 1f
    val u = t - delay
    return when {
        u <= 0f -> 0f
        u < 300f -> 1.25f * easeOut(u / 300f)
        u < 450f -> 1.25f - 0.25f * ((u - 300f) / 150f)
        else -> 1f
    }
}
