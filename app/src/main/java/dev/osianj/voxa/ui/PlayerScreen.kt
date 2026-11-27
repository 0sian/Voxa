package dev.osianj.voxa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.content.Context
import androidx.compose.ui.text.font.FontWeight as FW
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.osianj.voxa.data.Episode
import kotlinx.coroutines.delay

/** Full-screen player: artwork, seek bar, -10 / play / +30, and speed / sleep / download / queue. */
@Composable
fun PlayerScreen(
    vm: VoxaViewModel,
    ps: PlayerUiState,
    downloads: DownloadActions,
    onClose: () -> Unit,
) {
    val c = ps.controller ?: return
    val prefs = LocalContext.current.getSharedPreferences("voxa", Context.MODE_PRIVATE)
    var backSec by remember { mutableStateOf(prefs.getInt("skip_back_s", 10)) }
    var fwdSec by remember { mutableStateOf(prefs.getInt("skip_fwd_s", 30)) }
    val episodeId = ps.mediaId?.toLongOrNull()
    val episode by remember(episodeId) {
        if (episodeId != null) vm.repo.observeEpisode(episodeId) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)
    val progress by vm.downloadProgress.collectAsState()
    val sleepEndsAt by vm.sleepEndsAt.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }

    var drag by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    val skipSilence by vm.skipSilence.collectAsState()
    val volumeBoost by vm.volumeBoost.collectAsState()
    val monoMix by vm.mono.collectAsState()

    val fraction = if (ps.durationMs > 0) (ps.positionMs.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = drag?.let { (it * ps.durationMs).toLong() } ?: ps.positionMs

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Close player", Modifier.size(32.dp))
                }
            }

            Artwork(
                ps.artwork,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                corner = 8.dp,
            )

            Text(
                ps.title.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                ps.artist.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Slider(
                value = drag ?: fraction,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { c.seekTo((it * ps.durationMs).toLong()) }
                    drag = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = Lime,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.padding(top = 20.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock(shownMs), style = MaterialTheme.typography.bodySmall)
                Text(
                    "-" + formatClock(ps.durationMs - shownMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipButton(Icons.Filled.SkipPrevious, "Previous episode", enabled = true, size = 44.dp) {
                    if (ps.currentIndex > 0) c.seekToPreviousMediaItem() else c.seekTo(0)
                }
                SkipTimeButton(forward = false, seconds = backSec, onPick = { backSec = it; prefs.edit().putInt("skip_back_s", it).apply() }) {
                    c.seekTo((c.currentPosition - backSec * 1000L).coerceAtLeast(0))
                }
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Lime)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { if (ps.isPlaying) c.pause() else c.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (ps.isPlaying) "Pause" else "Play",
                        tint = Ink,
                        modifier = Modifier.size(44.dp),
                    )
                }
                SkipTimeButton(forward = true, seconds = fwdSec, onPick = { fwdSec = it; prefs.edit().putInt("skip_fwd_s", it).apply() }) {
                    val end = if (ps.durationMs > 0) ps.durationMs else Long.MAX_VALUE
                    c.seekTo((c.currentPosition + fwdSec * 1000L).coerceAtMost(end))
                }
                SkipButton(Icons.Filled.SkipNext, "Next episode", enabled = ps.currentIndex < ps.queue.size - 1, size = 44.dp) {
                    c.seekToNextMediaItem()
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PlayerAction(label = "Speed", onClick = { vm.cycleSpeed() }) {
                    Text(
                        speedLabel(ps.speed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.height(24.dp),
                    )
                }
                val minutesLeft = sleepEndsAt?.let { (((it - now) + 59_999) / 60_000).coerceAtLeast(1) }
                PlayerAction(
                    label = if (minutesLeft != null) "${minutesLeft}m left" else "Sleep",
                    onClick = { showSleep = true },
                ) { Icon(Icons.Filled.Bedtime, null) }

                val ep: Episode? = episode
                val fractionDownloading = episodeId?.let { progress[it] }
                PlayerAction(
                    label = when {
                        ep?.localPath != null -> "Downloaded"
                        fractionDownloading != null -> "${(fractionDownloading * 100).toInt()}%"
                        else -> "Download"
                    },
                    onClick = {
                        if (ep != null && ep.localPath == null && fractionDownloading == null) downloads.downloadEpisode(ep)
                    },
                ) { Icon(if (ep?.localPath != null) Icons.Filled.DownloadDone else Icons.Filled.Download, null) }

                PlayerAction(
                    label = if (ps.queue.size > 1) "Queue ${ps.queue.size}" else "Queue",
                    onClick = { showQueue = true },
                ) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PlayerAction(label = "Notes", onClick = { showNotes = true }) { Icon(Icons.Filled.Description, null) }
                PlayerAction(label = "Skip silence", onClick = { vm.setSkipSilence(!skipSilence) }) {
                    ToggleIcon(Icons.Filled.GraphicEq, skipSilence)
                }
                PlayerAction(label = "Boost", onClick = { vm.setVolumeBoost(!volumeBoost) }) {
                    ToggleIcon(Icons.AutoMirrored.Filled.VolumeUp, volumeBoost)
                }
                PlayerAction(label = "Mono", onClick = { vm.setMono(!monoMix) }) {
                    ToggleIcon(Icons.Filled.Headphones, monoMix)
                }
            }
        }
    }

    if (showSleep) {
        SleepDialog(
            active = sleepEndsAt != null,
            onPick = { vm.setSleep(it); showSleep = false },
            onDismiss = { showSleep = false },
        )
    }
    if (showQueue) QueueSheet(ps = ps, onDismiss = { showQueue = false })
    if (showNotes) NotesSheet(vm, episode, ps, onDismiss = { showNotes = false })
}

private fun speedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0')
    return "$text×"
}

@Composable
private fun PlayerAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Caps(label, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SleepDialog(active: Boolean, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(5, 15, 30, 45, 60).forEach { m ->
                    TextButton(onClick = { onPick(m) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$m minutes", modifier = Modifier.fillMaxWidth())
                    }
                }
                if (active) {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Turn off", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What's playing now and what's coming next. Tap to jump, X to drop an item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(ps: PlayerUiState, onDismiss: () -> Unit) {
    val c = ps.controller ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Caps("Queue", Modifier.padding(start = 20.dp, bottom = 8.dp))
            ps.queue.forEachIndexed { index, item ->
                val current = index == ps.currentIndex
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { c.seekToDefaultPosition(index) }
                            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.mediaMetadata.title?.toString().orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                (if (current) "Now playing · " else "") + item.mediaMetadata.artist?.toString().orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!current) {
                            IconButton(onClick = { c.removeMediaItem(index) }) {
                                Icon(Icons.Filled.Close, "Remove from queue")
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}


@Composable
private fun SkipButton(icon: ImageVector, label: String, enabled: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(icon, label, Modifier.size(size - 8.dp))
    }
}

/** Skip back/forward by a chosen number of seconds; long-press to pick 5 / 10 / 15 / 30. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkipTimeButton(forward: Boolean, seconds: Int, onPick: (Int) -> Unit, onSkip: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onSkip,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Replay,
                contentDescription = (if (forward) "Forward " else "Back ") + seconds + " seconds",
                modifier = Modifier.size(52.dp).graphicsLayer { scaleX = if (forward) -1f else 1f },
            )
            Text(seconds.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FW.Bold)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(5, 10, 15, 30).forEach { sec ->
                DropdownMenuItem(
                    text = { Text("$sec seconds", fontWeight = if (sec == seconds) FW.Bold else FW.Normal) },
                    onClick = { onPick(sec); menu = false },
                )
            }
        }
    }
}


/** A player action icon that fills with the accent colour when its feature is on. */
@Composable
private fun ToggleIcon(icon: ImageVector, on: Boolean) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).then(if (on) Modifier.background(Lime) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (on) Ink else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(if (on) 18.dp else 24.dp))
    }
}

/** Episode notes, plus chapters (tap to jump) when the feed provides them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheet(vm: VoxaViewModel, episode: Episode?, ps: PlayerUiState, onDismiss: () -> Unit) {
    val c = ps.controller ?: return
    val chapters by produceState(emptyList<dev.osianj.voxa.data.Chapter>(), episode?.chaptersUrl) {
        val url = episode?.chaptersUrl
        value = if (url == null) emptyList() else try { vm.repo.fetchChapters(url) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }
    val currentChapter = chapters.indexOfLast { it.startMs <= ps.positionMs }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(episode?.title ?: ps.title.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(ps.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            if (chapters.isNotEmpty()) {
                Caps("Chapters", Modifier.padding(bottom = 4.dp))
                chapters.forEachIndexed { i, ch ->
                    Row(
                        Modifier.fillMaxWidth().clickable { c.seekTo(ch.startMs) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatClock(ch.startMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                        Text(ch.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == currentChapter) FontWeight.Bold else FontWeight.Normal)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
            Caps("Notes", Modifier.padding(bottom = 6.dp))
            val notes = stripHtml(episode?.description.orEmpty())
            Text(
                notes.ifBlank { "No notes for this episode." },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
