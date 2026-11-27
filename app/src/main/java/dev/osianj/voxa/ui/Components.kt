package dev.osianj.voxa.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.osianj.voxa.VoxaApplication
import dev.osianj.voxa.data.DownloadScope
import dev.osianj.voxa.data.DownloadWorker
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.Show

/** Small uppercase label, used for section headers and captions. */
@Composable
fun Caps(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false,
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else null,
    )
}

/** Home tab: selected = lime pill with an ink outline. */
@Composable
fun PillTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .then(
                if (selected) Modifier
                    .background(Lime)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Show page tabs: text with a lime underline under the selected one. */
@Composable
fun UnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            tabs.forEachIndexed { i, label ->
                Column(
                    Modifier.weight(1f).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(3.dp)
                            .background(if (i == selected) Lime else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)
    }
}

/** Artwork with a neutral placeholder. [model] can be a URL string or a Uri. */
@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corner: Dp = 6.dp, contentDescription: String? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Round outlined play/pause button used in episode rows. */
@Composable
fun PlayCircleButton(isPlaying: Boolean, onClick: () -> Unit, size: Dp = 38.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp = 12.dp, width: Dp = 1.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

/** Tap a row to play it, or pause/resume if it's the episode already loaded. */
fun togglePlay(vm: VoxaViewModel, ps: PlayerUiState, ep: Episode, show: Show) {
    val c = ps.controller
    if (ps.mediaId == ep.id.toString() && c != null) {
        if (ps.isPlaying) c.pause() else c.play()
    } else {
        vm.playFrom(ep, show)
    }
}

class DownloadActions(
    val downloadShow: (showId: Long, scope: DownloadScope, oldestFirst: Boolean) -> Unit,
    val downloadEpisode: (episode: Episode) -> Unit,
    /** Opens the system folder picker. */
    val chooseFolder: () -> Unit,
    /** Forget the chosen folder and go back to private app storage. */
    val useAppStorage: () -> Unit,
    private val folderLabelState: State<String>,
) {
    /** Where downloads currently go, for display. */
    val folderLabel: String get() = folderLabelState.value
    val hasCustomFolder: Boolean get() = !folderLabel.startsWith("App storage")
}

/**
 * Starts downloads. First makes sure a download folder has been chosen (asking once if not),
 * then asks for the Android 13+ notification permission if needed.
 */
@Composable
fun rememberDownloadActions(): DownloadActions {
    val context = LocalContext.current
    val storage = remember { (context.applicationContext as VoxaApplication).storage }
    val label = remember { mutableStateOf(storage.label()) }
    var afterFolder by remember { mutableStateOf<(() -> Unit)?>(null) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Download either way; the permission only controls whether the notification shows.
        afterPermission?.invoke()
        afterPermission = null
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val next = afterFolder
        afterFolder = null
        if (uri != null) {
            try {
                storage.setTree(uri)
                label.value = storage.label()
                next?.invoke()
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't use that folder: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else if (next != null) {
            Toast.makeText(context, "Pick a folder to download into", Toast.LENGTH_SHORT).show()
        }
    }

    return remember {
        fun withNotificationPermission(action: () -> Unit) {
            val needs = Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needs) {
                afterPermission = action
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else action()
        }

        fun run(action: () -> Unit) {
            label.value = storage.label() // may have changed if Android revoked access
            if (!storage.hasChosenFolderOrOptedOut()) {
                afterFolder = { withNotificationPermission(action) }
                folderLauncher.launch(null)
            } else withNotificationPermission(action)
        }

        DownloadActions(
            downloadShow = { showId, scope, oldestFirst ->
                run {
                    DownloadWorker.enqueue(context.applicationContext, showId, scope, oldestFirst)
                    Toast.makeText(context, "Download started. Progress is in the notification", Toast.LENGTH_SHORT).show()
                }
            },
            downloadEpisode = { ep ->
                run {
                    DownloadWorker.enqueueEpisode(context.applicationContext, ep.showId, ep.id)
                    Toast.makeText(context, "Downloading episode", Toast.LENGTH_SHORT).show()
                }
            },
            chooseFolder = { afterFolder = null; folderLauncher.launch(null) },
            useAppStorage = {
                storage.useAppStorage()
                label.value = storage.label()
            },
            folderLabelState = label,
        )
    }
}

/** Shows where downloads go and lets you change it. */
@Composable
fun DownloadFolderDialog(downloads: DownloadActions, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download folder") },
        text = {
            Column {
                Text(downloads.folderLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Each podcast gets its own subfolder here, and files are named like " +
                        "\"2024-04-12 - Episode title.mp3\".\n\n" +
                        "Android won't let apps use a drive's root or the Downloads folder itself, " +
                        "so pick or create a folder such as Podcasts. Episodes already downloaded stay where they are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { downloads.chooseFolder(); onDismiss() }) {
                Text("Choose folder…")
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (downloads.hasCustomFolder) {
                    androidx.compose.material3.TextButton(onClick = { downloads.useAppStorage(); onDismiss() }) {
                        Text("Use app storage")
                    }
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
