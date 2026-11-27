package dev.osianj.voxa.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.osianj.voxa.data.DownloadScope
import dev.osianj.voxa.data.isAudiobook
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.Show

private val ShowTabs = listOf("EPISODES", "ABOUT", "SETTINGS")

@Composable
fun ShowScreen(
    vm: VoxaViewModel,
    showId: Long,
    ps: PlayerUiState,
    downloads: DownloadActions,
    padding: PaddingValues,
    onBack: () -> Unit,
    onEpisodeMenu: (Episode) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("voxa", Context.MODE_PRIVATE) }
    val shows by vm.everyShow.collectAsState()
    val show = shows?.firstOrNull { it.id == showId }
    val isBook = show?.isAudiobook == true
    val allEpisodes by remember(showId) { vm.episodes(showId) }.collectAsState(initial = emptyList())
    val progress by vm.downloadProgress.collectAsState()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var topMenu by remember { mutableStateOf(false) }
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    // Sort order and "show hidden" are remembered per show.
    var oldestFirst by remember(showId) { mutableStateOf(prefs.getBoolean("oldest_first_$showId", false)) }
    var showHidden by remember(showId) { mutableStateOf(prefs.getBoolean("show_hidden_$showId", false)) }

    // Pull new episodes whenever the show is opened; offline just keeps the cached list.
    LaunchedEffect(showId) { vm.refresh(showId) }

    // Episode numbers: 1 = oldest, counting hidden ones too so numbers stay stable.
    val numbers = remember(allEpisodes) {
        allEpisodes.sortedBy { it.publishedAt }.mapIndexed { i, e -> e.id to i + 1 }.toMap()
    }
    val episodes = remember(allEpisodes, showHidden, oldestFirst) {
        val visible = if (showHidden) allEpisodes else allEpisodes.filter { !it.hidden }
        if (oldestFirst) visible.reversed() else visible // DAO returns newest first
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Box(Modifier.weight(1f))
            Box {
                IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isBook) "Rescan library" else "Refresh feed") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = { topMenu = false; if (isBook) vm.scanAudiobooks() else vm.refresh(showId) },
                    )
                }
            }
        }

        if (show == null) return@Column

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Hero(show, isBook, onSubscribedClick = { confirmUnsubscribe = true }, onSettingsClick = { tabIndex = 2 })
            }
            item { UnderlineTabs(if (isBook) listOf("CHAPTERS", "ABOUT", "SETTINGS") else ShowTabs, tabIndex) { tabIndex = it } }

            when (tabIndex) {
                0 -> {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Caps(
                                when {
                                    isBook -> if (showHidden) "Chapters (incl. hidden)" else "Chapters"
                                    showHidden -> "All episodes (incl. hidden)"
                                    else -> "All episodes"
                                },
                                Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                oldestFirst = !oldestFirst
                                prefs.edit().putBoolean("oldest_first_$showId", oldestFirst).apply()
                            }) {
                                Icon(
                                    Icons.Filled.SwapVert,
                                    contentDescription = if (oldestFirst) "Oldest first" else "Newest first",
                                )
                            }
                            if (!isBook) IconButton(onClick = { showDownloadDialog = true }) {
                                Icon(Icons.Filled.Download, "Download episodes")
                            }
                        }
                        HorizontalDivider(
                            Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    if (episodes.isEmpty()) {
                        item {
                            Text(
                                "No episodes yet",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(episodes, key = { it.id }) { ep ->
                        ShowEpisodeRow(
                            number = numbers[ep.id] ?: 0,
                            ep = ep,
                            downloadFraction = progress[ep.id],
                            isCurrent = ps.mediaId == ep.id.toString(),
                            isPlaying = ps.isPlaying,
                            onPlay = { togglePlay(vm, ps, ep, show) },
                            onMenu = { onEpisodeMenu(ep) },
                        )
                    }
                    // a single .m4b carries its chapter list inside the file
                    if (isBook && allEpisodes.size == 1) item { EmbeddedChapters(vm, allEpisodes[0], show, ps) }
                }
                1 -> item { AboutTab(show, allEpisodes.size) }
                else -> item {
                    SettingsTab(
                        isBook = isBook,
                        showHidden = showHidden,
                        onShowHidden = {
                            showHidden = it
                            prefs.edit().putBoolean("show_hidden_$showId", it).apply()
                        },
                        onDownload = { showDownloadDialog = true },
                        folderLabel = downloads.folderLabel,
                        onFolder = { showFolderDialog = true },
                        onRefresh = { if (isBook) vm.scanAudiobooks() else vm.refresh(showId) },
                        onUnsubscribe = { confirmUnsubscribe = true },
                    )
                }
            }
        }
    }

    if (showFolderDialog) DownloadFolderDialog(downloads, onDismiss = { showFolderDialog = false })

    if (showDownloadDialog) {
        DownloadDialog(
            episodes = allEpisodes,
            onPick = { scope ->
                showDownloadDialog = false
                downloads.downloadShow(showId, scope, oldestFirst)
            },
            onDismiss = { showDownloadDialog = false },
        )
    }

    if (confirmUnsubscribe && show != null) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text(if (isBook) "Remove ${show.title}?" else "Unsubscribe from ${show.title}?") },
            text = {
                Text(
                    if (isBook) "This only removes it from Voxa. Your audio files are not touched, and it won't come back on a rescan."
                    else "This removes the show and deletes any downloaded episodes."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsubscribe = false
                    vm.removeShow(show.id)
                    onBack()
                }) { Text(if (isBook) "Remove" else "Unsubscribe", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUnsubscribe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Hero(show: Show, isBook: Boolean, onSubscribedClick: () -> Unit, onSettingsClick: () -> Unit) {
    val description = remember(show.description) { stripHtml(show.description) }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(show.artworkUrl, Modifier.size(128.dp), contentDescription = show.title)
            Column(Modifier.weight(1f)) {
                Text(show.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (show.author.isNotBlank()) {
                    Caps(show.author, Modifier.padding(top = 6.dp), bold = true)
                }
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Lime)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onSubscribedClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = Ink, modifier = Modifier.size(16.dp))
                Text(
                    if (isBook) "  IN LIBRARY" else "  SUBSCRIBED",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Settings, "Settings", Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ShowEpisodeRow(
    number: Int,
    ep: Episode,
    downloadFraction: Float?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
) {
    val meta = remember(ep.publishedAt, ep.durationSec) { episodeMeta(ep.publishedAt, ep.durationSec) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp).width(32.dp),
            )
            Column(Modifier.weight(1f).alpha(if (ep.hidden) 0.45f else if (ep.played) 0.6f else 1f)) {
                Text(
                    ep.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meta + if (ep.hidden) " • hidden" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ep.localPath != null) {
                        Icon(
                            Icons.Filled.DownloadDone, "Downloaded",
                            Modifier.padding(start = 6.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (downloadFraction != null) {
                    LinearProgressIndicator(
                        progress = { downloadFraction },
                        color = Lime,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                } else if (!ep.played && ep.positionMs > 0 && ep.durationSec > 0) {
                    LinearProgressIndicator(
                        progress = { (ep.positionMs / (ep.durationSec * 1000f)).coerceIn(0f, 1f) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            PlayCircleButton(isCurrent && isPlaying, onClick = onPlay)
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, "Episode options") }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AboutTab(show: Show, episodeCount: Int) {
    val description = remember(show.description) { stripHtml(show.description) }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
        if (show.author.isNotBlank()) AboutRow("Author", show.author)
        AboutRow("Episodes", episodeCount.toString())
        if (show.isAudiobook) {
            AboutRow("Added", formatDate(show.lastRefreshed))
            AboutRow("Folder", show.sourcePath.orEmpty())
        } else {
            if (show.lastRefreshed > 0) AboutRow("Last refreshed", formatDate(show.lastRefreshed))
            AboutRow("Feed", show.feedUrl)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column {
        Caps(label)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SettingsTab(
    isBook: Boolean,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    onDownload: () -> Unit,
    folderLabel: String,
    onFolder: () -> Unit,
    onRefresh: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    Column {
        if (!isBook) {
            SettingsRow("Download episodes…", "Latest, unplayed or the entire show", onClick = onDownload)
            SettingsRow("Download folder", folderLabel, onClick = onFolder)
        }
        SettingsRow(if (isBook) "Rescan library now" else "Refresh feed now", null, onClick = onRefresh)
        Row(
            Modifier.fillMaxWidth().clickable { onShowHidden(!showHidden) }.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (isBook) "Show hidden chapters" else "Show hidden episodes", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isBook) "So you can unhide them from the chapter menu" else "So you can unhide them from the episode menu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showHidden,
                onCheckedChange = onShowHidden,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Lime,
                    checkedThumbColor = Ink,
                    checkedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        if (isBook) SettingsRow("Remove from library", "Keeps your audio files", onClick = onUnsubscribe, destructive = true)
        else SettingsRow("Unsubscribe", "Removes the show and its downloads", onClick = onUnsubscribe, destructive = true)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, onClick: () -> Unit, destructive: Boolean = false) {
    Column {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Bulk download chooser. Sizes are estimates (about 1 MB per minute, i.e. 128 kbps) because feeds
 * rarely say how big a file is; options that wouldn't fit in free storage are disabled.
 */
@Composable
private fun DownloadDialog(
    episodes: List<Episode>,
    onPick: (DownloadScope) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val free = remember { context.filesDir.usableSpace }
    // Same filter the downloader uses: not hidden, not already on disk. List is newest first.
    val pending = remember(episodes) { episodes.filter { !it.hidden && it.localPath == null } }

    data class Option(val label: String, val scope: DownloadScope, val list: List<Episode>)
    val options = remember(pending) {
        listOf(
            Option("Latest 5", DownloadScope.Latest(5), pending.take(5)),
            Option("Latest 10", DownloadScope.Latest(10), pending.take(10)),
            Option("All unplayed", DownloadScope.Unplayed, pending.filter { !it.played }),
            Option("Entire show", DownloadScope.All, pending),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        text = {
            Column {
                options.forEach { opt ->
                    val bytes = opt.list.sumOf { it.durationSec.toLong() } * 16_000L
                    val empty = opt.list.isEmpty()
                    val tooBig = bytes > free
                    val enabled = !empty && !tooBig
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onPick(opt.scope) }
                            .padding(vertical = 10.dp)
                            .alpha(if (enabled) 1f else 0.5f)
                    ) {
                        Text(opt.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                empty -> "Nothing to download"
                                tooBig -> "${opt.list.size} episodes · about ${formatBytes(bytes)} · not enough space"
                                else -> "${opt.list.size} episodes · about ${formatBytes(bytes)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tooBig) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Free space: ${formatBytes(free)}. Skips hidden and already downloaded episodes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A single .m4b: its chapter list lives inside the file. Tap a chapter to start there. */
@Composable
private fun EmbeddedChapters(vm: VoxaViewModel, ep: Episode, show: Show, ps: PlayerUiState) {
    val chapters by androidx.compose.runtime.produceState(emptyList<dev.osianj.voxa.data.Chapter>(), ep.chaptersUrl) {
        val url = ep.chaptersUrl
        value = if (url == null) emptyList() else try {
            vm.repo.fetchChapters(url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    if (chapters.isEmpty()) return
    val current = if (ps.mediaId == ep.id.toString()) chapters.indexOfLast { it.startMs <= ps.positionMs } else -1
    Column {
        Caps("In this book", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
        chapters.forEachIndexed { i, ch ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.playback.play(ep.copy(positionMs = ch.startMs), show) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatClock(ch.startMs), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp),
                )
                Text(ch.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
