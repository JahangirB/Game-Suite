package com.pippop.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.pippop.data.Reaction
import com.pippop.ui.ReactionLock
import com.pippop.ui.ShownReaction
import com.pippop.ui.art.ReactionImage
import com.pippop.ui.theme.AppTheme
import com.pippop.ui.theme.Shapes
import kotlinx.coroutines.delay

/** How long a reaction stays next to a player's name. */
private const val REACTION_SHOWN_MS = 3500L

/**
 * The predefined reactions; tap one to show it to your opponent. While a reaction that takes the
 * game over is playing (Kylian Dictador), the bar is dimmed and nothing can be sent.
 */
@Composable
fun ReactionBar(onReact: (Reaction) -> Unit, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val lockedUntil by ReactionLock.until.collectAsState()
    val locked = rememberTicker() < lockedUntil
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).alpha(if (locked) 0.35f else 1f),
    ) {
        Reaction.entries.forEach { reaction ->
            if (reaction.sticker) {
                Surface(onClick = { onReact(reaction) }, enabled = !locked, shape = Shapes.S, modifier = Modifier.size(42.dp)) {
                    ReactionImage(reaction, 42.dp)
                }
            } else {
                Surface(onClick = { onReact(reaction) }, enabled = !locked, shape = CircleShape, color = p.accent, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { ReactionImage(reaction, 30.dp) }
                }
            }
        }
    }
}

/** [shown] while it should stay on screen, or null once it has been there long enough. */
fun visibleReaction(shown: ShownReaction?, now: Long): ShownReaction? =
    shown?.takeIf { now - it.at < REACTION_SHOWN_MS }

/** The local clock, ticking a few times a second so reactions disappear on time. */
@Composable
fun rememberTicker(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(250)
            value = System.currentTimeMillis()
        }
    }
    return now
}
