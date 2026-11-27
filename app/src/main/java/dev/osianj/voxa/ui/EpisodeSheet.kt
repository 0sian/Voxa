package dev.osianj.voxa.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.isAudiobook

/** Per-episode actions, opened from any episode's kebab (last screen of the mock). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSheet(
    episodeId: Long,
    vm: VoxaViewModel,
    ps: PlayerUiState,
    downloads: DownloadActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val episode by remember(episodeId) { vm.repo.observeEpisode(episodeId) }.collectAsState(initial = null)
    val shows by vm.everyShow.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val ep: Episode = episode ?: return
    val show = shows?.firstOrNull { it.id == ep.showId }
    val isCurrent = ps.mediaId == ep.id.toString()
    val downloading = progress[ep.id]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(show?.artworkUrl, Modifier.size(56.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        ep.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        show?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        episodeMeta(ep.publishedAt, ep.durationSec),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            if (show != null) {
                SheetAction(
                    icon = if (isCurrent && ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (isCurrent && ps.isPlaying) "Pause" else "Play episode",
                ) { togglePlay(vm, ps, ep, show); onDismiss() }
            }
            // a book chapter is a file you already have: nothing to download or remove
            if (ep.localPath == null && show?.isAudiobook != true) {
                if (downloading != null) {
                    SheetAction(Icons.Filled.Download, "Downloading ${(downloading * 100).toInt()}%", enabled = false) {}
                } else {
                    SheetAction(Icons.Filled.Download, "Download") { downloads.downloadEpisode(ep); onDismiss() }
                }
            }
            if (show != null && !isCurrent) {
                SheetAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue") {
                    vm.playback.addToQueue(ep, show)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
            if (ep.played || ep.positionMs > 0) {
                SheetAction(Icons.Filled.Restore, "Clear progress") { vm.clearProgress(ep); onDismiss() }
            }
            if (ep.localPath != null) {
                SheetAction(Icons.Filled.DeleteOutline, "Remove download") { vm.deleteDownload(ep); onDismiss() }
            }
            SheetAction(
                icon = if (ep.hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                label = if (ep.hidden) "Unhide episode" else "Hide episode",
                color = if (ep.hidden) null else MaterialTheme.colorScheme.error,
            ) { vm.setHidden(ep, !ep.hidden); onDismiss() }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    color: Color? = null,
    onClick: () -> Unit,
) {
    val tint = color ?: MaterialTheme.colorScheme.onSurface
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.5f))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) tint else tint.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 20.dp),
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
