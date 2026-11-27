package dev.osianj.voxa.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Floating card from the mock: artwork, show + episode, play/pause, and a thin progress bar along
 * the bottom edge. Tap to open the full player; swipe left or right to dismiss it (clears the queue).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(ps: PlayerUiState, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val c = ps.controller ?: return
    if (!ps.hasItem) return

    val shape = RoundedCornerShape(20.dp)
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDismiss()
            value != SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {},
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, shape),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpen)
                        .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(ps.artwork, Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            ps.artist.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            ps.title.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { if (ps.isPlaying) c.pause() else c.play() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (ps.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                val fraction = if (ps.durationMs > 0) (ps.positionMs.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                        .fillMaxWidth().height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Lime))
                }
            }
        }
    }
}
