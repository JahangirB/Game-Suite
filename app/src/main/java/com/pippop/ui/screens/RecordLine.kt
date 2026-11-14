package com.pippop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pippop.data.BrokenRecord
import com.pippop.data.GameKind
import com.pippop.data.SoloKey
import com.pippop.data.SoloMetric
import com.pippop.ui.theme.Board
import com.pippop.ui.theme.Mark
import com.pippop.ui.theme.MarkIcon
import com.pippop.ui.theme.Shapes
import com.pippop.ui.theme.TextSize

/**
 * The record the game on screen just set against the partner, for [MatchPopup] to say. The app
 * provides it for the round that set it ([BattleUiState.record]); null otherwise.
 */
val LocalRecord = compositionLocalOf<BrokenRecord?> { null }

/** What the gold line says. */
object RecordLines {
    /** "Your quickest win at Connect Four: 11 discs", "Your biggest win at Dominoes: by 42 points", "Your best at Slide Race (4 × 4): 1:12". */
    fun text(record: BrokenRecord): String = when (record) {
        is BrokenRecord.Quickest -> "Your quickest win at ${record.game.label}: " + when {
            record.count != null -> SoloLines.bestText(countMetric(record.game), record.count.toLong())
            else -> SoloLines.bestText(SoloMetric.TIME, record.ms ?: 0)
        }
        is BrokenRecord.Biggest -> "Your biggest win at ${record.game.label}: by ${margin(record.game, record.margin)}"
        is BrokenRecord.Solo -> if (record.key.metric == SoloMetric.LONGEST_WORD) {
            val level = soloLevel(record.key)
            "Your longest word at ${record.game.label}" + (if (level.isEmpty()) "" else " ($level)") + ": " + SoloLines.bestText(record.key.metric, record.value)
        } else {
            val level = soloLevel(record.key)
            "Your best at ${record.game.label}" + (if (level.isEmpty()) "" else " ($level)") + ": " + SoloLines.bestText(record.key.metric, record.value)
        }
    }

    private fun margin(game: GameKind, n: Int): String {
        val many = scoreUnit(game)
        val one = when (many) { "boxes" -> "box"; "pairs" -> "pair"; else -> many.removeSuffix("s") }
        return "$n ${if (n == 1) one else many}"
    }

    /** The level, size or setting in the words of the Solo tab; none for the games whose line already says what is counted. */
    private fun soloLevel(key: SoloKey): String = when {
        key.game == GameKind.SHIPS || key.game == GameKind.BALLOON -> ""
        key.game == GameKind.CONNECT4 -> if (key.level.isEmpty()) "" else "against ${SoloLines.levelWords(key).substringAfter("against ")}"
        else -> SoloLines.levelWords(key)
    }
}

/**
 * The gold line on a result that is a record: a star, then [text]. The star and the words say it
 * together (nothing by colour alone); the ink on the soft gold is dark on light in both themes.
 * Steps 31 and 32 play the `record` sound and buzz where this appears.
 */
@Composable
fun RecordLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(Shapes.Full).background(Board.GlowSoft).border(1.5.dp, Board.Ink, Shapes.Full)
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = "New record. $text" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MarkIcon(Mark.Star, Modifier.size(14.dp), color = Board.Ink)
        Text(text, color = Board.Ink, fontSize = TextSize.Label, fontWeight = FontWeight.Bold, lineHeight = com.pippop.ui.theme.LineHeight.Label, maxLines = 2)
    }
}
