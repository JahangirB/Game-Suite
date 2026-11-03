package com.pippop.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import org.json.JSONArray
import org.json.JSONObject

/**
 * A downloaded Ship Battle skin: its sea, a picture for each kind of ship and its hit and miss
 * marks, each a list of shapes to paint (made by scripts/make-skins.js; the format is described
 * there). Everything is in squares: the sea is 10 x 10, a ship lies from (0, 0) to (length, 1),
 * a mark fills (0, 0) to (1, 1).
 */
class SkinArt(
    val id: String,
    val name: String,
    val tag: String,
    val about: String,
    val version: Int,
    val sea: List<SkinOp>,
    val ships: Map<ShipKind, List<SkinOp>>,
    val hit: List<SkinOp>,
    val miss: List<SkinOp>,
    /** "3d": Ship Battle draws in 3D with this skin (ui/screens/Ships3D.kt); its flat art is for older apps. */
    val kind: String = "flat",
) {
    companion object {
        private val KINDS = mapOf(
            "carrier" to ShipKind.CARRIER, "battleship" to ShipKind.BATTLESHIP, "cruiser" to ShipKind.CRUISER,
            "submarine" to ShipKind.SUBMARINE, "destroyer" to ShipKind.DESTROYER,
        )

        /** Reads a skin file; throws if it isn't one (so a broken download is never kept). */
        fun parse(text: String): SkinArt {
            val o = JSONObject(text)
            val ships = o.getJSONObject("ships")
            val parsed = KINDS.mapValues { (key, _) -> ops(ships.getJSONArray(key)) }.mapKeys { (key, _) -> KINDS.getValue(key) }
            return SkinArt(
                id = o.getString("id").also { require(it.matches(Regex("[a-z0-9-]{1,40}"))) { "bad skin id" } },
                name = o.getString("name"),
                tag = o.optString("tag"),
                about = o.optString("about"),
                version = o.optInt("version", 1),
                sea = ops(o.getJSONArray("sea")),
                ships = parsed,
                hit = ops(o.getJSONArray("hit")),
                miss = ops(o.getJSONArray("miss")),
                kind = o.optString("kind", "flat"),
            )
        }

        private fun ops(a: JSONArray): List<SkinOp> = (0 until a.length()).mapNotNull { i ->
            val op = a.getJSONArray(i)
            when (op.getString(0)) {
                "(" -> SkinOp.Save
                ")" -> SkinOp.Restore
                "c" -> SkinOp.Clip(op.getString(1))
                "f", "F" -> SkinOp.Fill(op.getString(1), paint(op.get(2)), evenOdd = op.getString(0) == "F")
                "s" -> SkinOp.Stroke(
                    op.getString(1), paint(op.get(2)), op.getDouble(3).toFloat(), op.optInt(4), op.optInt(5),
                    op.optJSONArray(6)?.let { d -> FloatArray(d.length()) { d.getDouble(it).toFloat() } },
                )
                "t" -> SkinOp.Text(
                    op.getString(1), op.getDouble(2).toFloat(), op.getDouble(3).toFloat(), op.getDouble(4).toFloat(),
                    paint(op.get(5)), centred = op.optInt(6) == 1, bold = op.optInt(7) == 1,
                )
                else -> null   // something newer than this app: left out
            }
        }

        private fun color(hex: String): Color {
            require(hex.length == 9 && hex[0] == '#') { "bad colour $hex" }
            return Color(hex.substring(1).toLong(16).toInt())
        }

        private fun stops(a: JSONArray) = (0 until a.length()).map { i ->
            a.getJSONArray(i).let { it.getDouble(0).toFloat().coerceIn(0f, 1f) to color(it.getString(1)) }
        }

        private fun paint(p: Any): SkinPaint = when (p) {
            is String -> SkinPaint.Solid(color(p))
            is JSONObject -> p.optJSONArray("l")?.let { l ->
                SkinPaint.Linear(l.getDouble(0).toFloat(), l.getDouble(1).toFloat(), l.getDouble(2).toFloat(), l.getDouble(3).toFloat(), stops(p.getJSONArray("stops")))
            } ?: p.getJSONArray("r").let { r ->
                SkinPaint.Radial(r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(), stops(p.getJSONArray("stops")))
            }
            else -> throw IllegalArgumentException("bad paint")
        }
    }
}

sealed interface SkinPaint {
    data class Solid(val color: Color) : SkinPaint
    data class Linear(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val stops: List<Pair<Float, Color>>) : SkinPaint
    data class Radial(val cx: Float, val cy: Float, val r: Float, val stops: List<Pair<Float, Color>>) : SkinPaint
}

/** One step of a skin picture. Outlines are SVG path data, read into a [Path] the first time they're drawn. */
sealed interface SkinOp {
    data object Save : SkinOp
    data object Restore : SkinOp

    sealed class Shape(val d: String) : SkinOp {
        val path: Path by lazy { PathParser().parsePathString(d).toPath() }
    }

    class Clip(d: String) : Shape(d)
    class Fill(d: String, val paint: SkinPaint, val evenOdd: Boolean) : Shape(d)
    class Stroke(d: String, val paint: SkinPaint, val width: Float, val cap: Int, val join: Int, val dash: FloatArray?) : Shape(d)
    data class Text(val text: String, val x: Float, val y: Float, val size: Float, val paint: SkinPaint, val centred: Boolean, val bold: Boolean) : SkinOp
}

private fun SkinPaint.brush(): Brush = when (this) {
    is SkinPaint.Solid -> androidx.compose.ui.graphics.SolidColor(color)
    is SkinPaint.Linear -> if (stops.size < 2) androidx.compose.ui.graphics.SolidColor(stops.firstOrNull()?.second ?: Color.Transparent) else Brush.linearGradient(*stops.toTypedArray(), start = Offset(x0, y0), end = Offset(x1, y1))
    is SkinPaint.Radial -> if (stops.size < 2 || r <= 0f) androidx.compose.ui.graphics.SolidColor(stops.lastOrNull()?.second ?: Color.Transparent) else Brush.radialGradient(*stops.toTypedArray(), center = Offset(cx, cy), radius = r)
}

private fun SkinPaint.argb(): Int = when (this) {
    is SkinPaint.Solid -> color.toArgb()
    is SkinPaint.Linear -> (stops.firstOrNull()?.second ?: Color.Black).toArgb()
    is SkinPaint.Radial -> (stops.firstOrNull()?.second ?: Color.Black).toArgb()
}

/**
 * Paints [ops] at ([x], [y]) in squares of [s] pixels. Lines are never thinner than a pixel,
 * as on the canvas the skin was drawn on.
 */
fun DrawScope.drawSkinOps(ops: List<SkinOp>, s: Float, x: Float = 0f, y: Float = 0f) {
    withTransform({ translate(x * s, y * s); scale(s, s, Offset.Zero) }) { paintOps(ops, 0, s) }
}

/** Paints from [from] up to the matching restore (or the end); returns where it stopped. */
private fun DrawScope.paintOps(ops: List<SkinOp>, from: Int, s: Float): Int {
    var i = from
    while (i < ops.size) {
        when (val op = ops[i]) {
            SkinOp.Save -> {
                // What follows up to the matching restore is drawn in its own layer of state.
                var end = i + 1
                drawIntoCanvas { it.save() }
                end = paintOps(ops, end, s)
                drawIntoCanvas { it.restore() }
                i = end
            }
            SkinOp.Restore -> return i + 1
            is SkinOp.Clip -> {
                // A clip holds until the save it's in is restored: draw the rest of that run inside it.
                var end = 0
                clipPath(op.path) { end = paintOps(ops, i + 1, s) }
                return end
            }
            is SkinOp.Fill -> {
                op.path.fillType = if (op.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                drawPath(op.path, op.paint.brush())
            }
            is SkinOp.Stroke -> drawPath(
                op.path, op.paint.brush(),
                style = Stroke(
                    width = maxOf(op.width, 1f / s),
                    cap = when (op.cap) { 1 -> StrokeCap.Round; 2 -> StrokeCap.Square; else -> StrokeCap.Butt },
                    join = when (op.join) { 1 -> StrokeJoin.Round; 2 -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
                    pathEffect = op.dash?.takeIf { it.isNotEmpty() && it.all { d -> d >= 0f } && it.any { d -> d > 0f } }?.let {
                        PathEffect.dashPathEffect(if (it.size % 2 == 0) it else it + it)
                    },
                ),
            )
            is SkinOp.Text -> drawIntoCanvas { canvas ->
                // Text is drawn at its real size, not scaled up from a tiny one.
                val native = canvas.nativeCanvas
                native.save()
                native.scale(1f / s, 1f / s)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = op.paint.argb()
                    textSize = op.size * s
                    typeface = if (op.bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    textAlign = if (op.centred) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT
                }
                native.drawText(op.text, op.x * s, op.y * s, paint)
                native.restore()
            }
        }
        i++
    }
    return i
}
