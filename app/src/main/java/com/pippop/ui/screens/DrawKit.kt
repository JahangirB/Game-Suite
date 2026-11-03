package com.pippop.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.pippop.data.DrawStroke
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.GameColors
import com.pippop.ui.theme.Pieces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The pen colours, by index as the servers store them: ink, red, blue, green, orange, then the
 * eraser (white, [ERASER]), then yellow, purple and pink. Eight colours and the eraser.
 */
internal val PenColors = listOf(
    Board.Ink, Pieces.Red, Pieces.Blue, GameColors.PenGreen, GameColors.PenOrange, Color.White,
    Pieces.Yellow, Pieces.Purple, Pieces.Pink,
)

/** The eraser's index in [PenColors]. */
internal const val ERASER = 5

/** The tray's colours in order (the eraser has its own button). */
internal val TrayColors = listOf(0, 1, 2, 3, 4, 6, 7, 8)

/** A stroke's line width as a share of the canvas: thin, thick, huge. */
internal fun penWidth(width: Int) = when (width) {
    3 -> 0.07f
    2 -> 0.03f
    else -> 0.012f
}

/**
 * The drawing as a picture [px] wide, strokes in order: lines with round ends, and fills that
 * pour their colour into the closed shape round their point (a flood fill on the pixels).
 */
internal fun renderDrawing(strokes: List<DrawStroke>, px: Int = 480): ImageBitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (s in strokes) {
        val c = PenColors.getOrElse(s.color) { PenColors[0] }.toArgb()
        if (s.fill) {
            if (s.points.size >= 2) floodFill(bmp, (s.points[0] * px).toInt(), (s.points[1] * px).toInt(), c)
            continue
        }
        paint.color = c
        paint.strokeWidth = penWidth(s.width) * px
        if (s.points.size == 2) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(s.points[0] * px, s.points[1] * px, paint.strokeWidth / 2, paint)
            paint.style = Paint.Style.STROKE
            continue
        }
        val path = android.graphics.Path()
        path.moveTo(s.points[0] * px, s.points[1] * px)
        for (i in 1 until s.points.size / 2) path.lineTo(s.points[i * 2] * px, s.points[i * 2 + 1] * px)
        canvas.drawPath(path, paint)
    }
    return bmp.asImageBitmap()
}

/** Fills the area of like colour round (x, y) with [color]; soft edges count as a wall. */
private fun floodFill(bmp: Bitmap, x0: Int, y0: Int, color: Int) {
    val w = bmp.width
    val h = bmp.height
    if (x0 !in 0 until w || y0 !in 0 until h) return
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val seed = px[y0 * w + x0]
    if (seed == color) return
    fun like(c: Int): Boolean {
        val dr = ((c shr 16) and 0xFF) - ((seed shr 16) and 0xFF)
        val dg = ((c shr 8) and 0xFF) - ((seed shr 8) and 0xFF)
        val db = (c and 0xFF) - (seed and 0xFF)
        return dr * dr + dg * dg + db * db < 60 * 60
    }
    val done = BooleanArray(w * h)
    val stack = ArrayDeque<Int>()
    stack.addLast(y0 * w + x0)
    while (stack.isNotEmpty()) {
        val i = stack.removeLast()
        if (done[i] || !like(px[i])) continue
        // Run left and right along the row, then queue the rows above and below.
        var l = i % w
        val y = i / w
        while (l > 0 && !done[y * w + l - 1] && like(px[y * w + l - 1])) l--
        var r = i % w
        while (r < w - 1 && !done[y * w + r + 1] && like(px[y * w + r + 1])) r++
        for (x in l..r) {
            val k = y * w + x
            done[k] = true
            px[k] = color
            if (y > 0 && !done[k - w]) stack.addLast(k - w)
            if (y < h - 1 && !done[k + w]) stack.addLast(k + w)
        }
    }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
}

/** A drawing kept in the sketchbook: the word, who drew it, when, and its strokes. */
data class Sketch(val at: Long, val word: String, val drawer: String, val strokes: List<DrawStroke>) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("word", word).put("drawer", drawer).put("strokes", JSONArray(strokes.map { it.toJson() }))

    companion object {
        fun from(o: JSONObject) = Sketch(
            o.optLong("at"), o.optString("word"), o.optString("drawer"),
            o.optJSONArray("strokes")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(DrawStroke::from) } }.orEmpty(),
        )
    }
}

/** The shared sketchbook: the drawings you chose to keep, newest first, on this phone. */
class Sketchbook private constructor(context: Context) {
    private val file = File(context.filesDir, "sketchbook.jsonl")
    private val _sketches = MutableStateFlow(load())
    val sketches: StateFlow<List<Sketch>> = _sketches.asStateFlow()

    private fun load(): List<Sketch> = runCatching {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { runCatching { Sketch.from(JSONObject(it)) }.getOrNull() }.reversed()
    }.getOrDefault(emptyList())

    @Synchronized
    fun keep(sketch: Sketch) {
        if (_sketches.value.any { it.word == sketch.word && it.at / 60_000 == sketch.at / 60_000 }) return
        _sketches.value = listOf(sketch) + _sketches.value
        runCatching { file.appendText(sketch.toJson().toString() + "\n") }
    }

    companion object {
        @Volatile
        private var instance: Sketchbook? = null

        fun get(context: Context): Sketchbook = instance ?: synchronized(this) {
            instance ?: Sketchbook(context.applicationContext).also { instance = it }
        }
    }
}
