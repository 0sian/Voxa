package dev.osianj.voxa.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.Show
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow

private data class BookStat(val totalSec: Long, val doneSec: Long) {
    val fraction: Float get() = if (totalSec <= 0) 0f else (doneSec.toFloat() / totalSec).coerceIn(0f, 1f)
}

/** Audiobooks found in Documents/Audiobooks: one card per book, tap to open its chapters. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BooksTab(vm: VoxaViewModel, ps: PlayerUiState, onOpenShow: (Show) -> Unit) {
    val context = LocalContext.current
    val books by vm.audiobooks.collectAsState()
    val episodes by vm.audiobookEpisodes.collectAsState()
    val status by vm.scanStatus.collectAsState()
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var toRemove by remember { mutableStateOf<Show?>(null) }
    var cardMenu by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }

    // coming back from the settings page: re-check the permission
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = Environment.isExternalStorageManager()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(hasAccess) { if (hasAccess) vm.scanAudiobooks() }

    val stats = remember(episodes) {
        episodes.groupBy { it.showId }.mapValues { (_, eps) ->
            BookStat(
                totalSec = eps.sumOf { it.durationSec.toLong() },
                doneSec = eps.sumOf { if (it.played) it.durationSec.toLong() else (it.positionMs / 1000).coerceAtMost(it.durationSec.toLong().coerceAtLeast(0)) },
            )
        }
    }
    val byShow = remember(episodes) { episodes.groupBy { it.showId } }
    val list = books ?: return

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Caps(status ?: "${list.size} audiobook${if (list.size == 1) "" else "s"}", Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Books menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rescan folder") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        enabled = hasAccess,
                        onClick = { menu = false; vm.scanAudiobooks() },
                    )
                    DropdownMenuItem(
                        text = { Text("Show removed books again") },
                        onClick = { menu = false; vm.restoreRemovedBooks() },
                    )
                }
            }
        }

        when {
            !hasAccess -> Notice(
                title = "Voxa needs to see your audiobooks",
                body = "Allow file access once, and the Documents/Audiobooks folder becomes your library. Voxa only reads the files.",
                action = "ALLOW ACCESS",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                },
            )
            list.isEmpty() && status == null -> Notice(
                title = "No audiobooks yet",
                body = "Put each book in its own folder inside Documents/Audiobooks (one file per chapter), or drop a single .m4b in there. A cover.jpg in the folder is used as the cover.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { book ->
                    val stat = stats[book.id]
                    val chapters = byShow[book.id].orEmpty()
                    val playingHere = chapters.any { ps.mediaId == it.id.toString() }
                    Column(
                        Modifier.combinedClickable(
                            // tap plays: resumes where you left off, or starts at the first chapter; tap again to pause
                            onClick = {
                                val target = chapters.firstOrNull { ps.mediaId == it.id.toString() } ?: resumeTarget(chapters)
                                if (target != null) togglePlay(vm, ps, target, book) else onOpenShow(book)
                            },
                            // hold for the chapter page and the remove option
                            onLongClick = { cardMenu = book.id },
                        ),
                    ) {
                        Box {
                            Artwork(book.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 8.dp, contentDescription = book.title)
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).clip(CircleShape).background(Lime),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (playingHere && ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (playingHere && ps.isPlaying) "Pause" else "Play", tint = Ink, modifier = Modifier.size(22.dp),
                                )
                            }
                            DropdownMenu(expanded = cardMenu == book.id, onDismissRequest = { cardMenu = null }) {
                                DropdownMenuItem(
                                    text = { Text("Chapters") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                    onClick = { cardMenu = null; onOpenShow(book) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove from library", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { cardMenu = null; toRemove = book },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (book.author.isNotBlank()) {
                            Text(
                                book.author, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressLine(stat)
                    }
                }
            }
        }
    }

    toRemove?.let { book ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove ${book.title}?") },
            text = { Text("This only removes it from Voxa. Your audio files are not touched, and it won't come back on a rescan. You can bring removed books back from the menu.") },
            confirmButton = { TextButton(onClick = { vm.removeShow(book.id); toRemove = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}

/** Where tapping a book should start: the chapter you were part-way through, else the first one you haven't finished, else the start. */
private fun resumeTarget(chapters: List<Episode>): Episode? {
    val ordered = chapters.filter { !it.hidden }.sortedBy { it.publishedAt }
    return ordered.firstOrNull { it.positionMs > 0 && !it.played }
        ?: ordered.firstOrNull { !it.played }
        ?: ordered.firstOrNull()
}

@Composable
private fun ProgressLine(stat: BookStat?) {
    val f = stat?.fraction ?: 0f
    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(3.dp).background(Lime))
    }
    val label = when {
        stat == null || stat.totalSec <= 0 -> ""
        f >= 0.98f -> "Finished"
        f <= 0f -> formatDuration(stat.totalSec.toInt())
        else -> "${(f * 100).toInt()}% • ${formatDuration((stat.totalSec - stat.doneSec).toInt())} left"
    }
    if (label.isNotEmpty()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Notice(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(10.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.size(24.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Lime).clickable(onClick = onAction).padding(horizontal = 22.dp, vertical = 12.dp),
            ) { Text(action, color = Ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
        }
    }
}
