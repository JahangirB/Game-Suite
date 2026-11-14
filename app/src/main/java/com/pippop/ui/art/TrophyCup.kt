package com.pippop.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pippop.data.Avatar
import com.pippop.ui.theme.readableOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pippop.ui.screens.darker
import com.pippop.ui.screens.lighter
import com.pippop.ui.theme.Board

/**
 * A gold cup with handles on a wooden foot: the home's button for the scoreboard. On the shelf
 * it carries the month's winner's [face] on its bowl (or, with no picture, their [initial] on
 * [initialFill]), and a [tie] is silver, not gold.
 */
@Composable
fun TrophyCup(modifier: Modifier = Modifier, face: Avatar? = null, tie: Boolean = false, initial: String? = null, initialFill: Color = Color.Unspecified) {
  Box(modifier, contentAlignment = Alignment.TopCenter) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val gold = if (tie) Board.Silver else Board.Glow
        val line = Stroke(maxOf(1.5.dp.toPx(), w * 0.022f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val grip = w * 0.075f
        for ((x, start) in listOf(w * 0.03f to 95f, w * 0.63f to -85f)) {
            drawArc(Board.Ink, start, 170f, false, Offset(x, h * 0.1f), Size(w * 0.34f, h * 0.32f), style = Stroke(grip + line.width * 2, cap = StrokeCap.Round))
            drawArc(gold, start, 170f, false, Offset(x, h * 0.1f), Size(w * 0.34f, h * 0.32f), style = Stroke(grip, cap = StrokeCap.Round))
        }
        val bowl = Path().apply {
            moveTo(w * 0.19f, h * 0.05f)
            lineTo(w * 0.81f, h * 0.05f)
            lineTo(w * 0.77f, h * 0.38f)
            quadraticTo(w * 0.72f, h * 0.6f, w * 0.5f, h * 0.62f)
            quadraticTo(w * 0.28f, h * 0.6f, w * 0.23f, h * 0.38f)
            close()
        }
        drawPath(bowl, gold)
        drawPath(Path().apply { moveTo(w * 0.25f, h * 0.09f); lineTo(w * 0.31f, h * 0.09f); lineTo(w * 0.34f, h * 0.44f); lineTo(w * 0.29f, h * 0.4f); close() }, Color.White.copy(alpha = 0.45f))
        drawPath(bowl, Board.Ink, style = line)
        drawRect(gold.darker(0.14f), Offset(w * 0.455f, h * 0.62f), Size(w * 0.09f, h * 0.12f))
        drawRect(Board.Ink, Offset(w * 0.455f, h * 0.62f), Size(w * 0.09f, h * 0.12f), style = line)
        drawRoundRect(gold, Offset(w * 0.36f, h * 0.72f), Size(w * 0.28f, h * 0.06f), CornerRadius(h * 0.03f))
        drawRoundRect(Board.Ink, Offset(w * 0.36f, h * 0.72f), Size(w * 0.28f, h * 0.06f), CornerRadius(h * 0.03f), style = line)
        drawRoundRect(Board.Wood, Offset(w * 0.27f, h * 0.78f), Size(w * 0.46f, h * 0.18f), CornerRadius(h * 0.03f))
        drawRoundRect(Board.Wood.lighter(0.25f), Offset(w * 0.33f, h * 0.83f), Size(w * 0.34f, h * 0.08f), CornerRadius(h * 0.02f))
        drawRoundRect(Board.Ink, Offset(w * 0.27f, h * 0.78f), Size(w * 0.46f, h * 0.18f), CornerRadius(h * 0.03f), style = line)
    }
    if (face != null || initial != null) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.6f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.4f).aspectRatio(1f).clip(CircleShape).background(Board.DieWhite).border(1.5.dp, Board.Ink, CircleShape), contentAlignment = Alignment.Center) {
                if (face != null) AvatarImage(face, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(initialFill), contentAlignment = Alignment.Center) {
                    BasicText(initial.orEmpty(), style = TextStyle(color = readableOn(initialFill), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
  }
}
