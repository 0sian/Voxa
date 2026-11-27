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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.Show
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val HomeTabs = listOf("PODCASTS", "BOOKS", "EPISODES", "DOWNLOADS")

/** voxa wordmark, Podcasts / Books / Episodes / Downloads tabs. */
@Composable
fun HomeScreen(
    vm: VoxaViewModel,
    ps: PlayerUiState,
    downloads: DownloadActions,
    padding: PaddingValues,
    onOpenShow: (Show) -> Unit,
    onEpisodeMenu: (Episode) -> Unit,
) {
    var showFolderDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    val shows by vm.shows.collectAsState()

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("voxa", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add RSS feed") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { menuOpen = false; showAddDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit order") },
                        leadingIcon = { Icon(Icons.Filled.SwapVert, null) },
                        enabled = (shows?.size ?: 0) > 1,
                        onClick = { menuOpen = false; tabIndex = 0; reordering = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Download folder") },
                        leadingIcon = { Icon(Icons.Filled.Folder, null) },
                        onClick = { menuOpen = false; showFolderDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Backup & restore") },
                        leadingIcon = { Icon(Icons.Filled.Backup, null) },
                        onClick = { menuOpen = false; showBackupDialog = true },
                    )
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HomeTabs.forEachIndexed { i, label ->
                PillTab(label, selected = i == tabIndex) { tabIndex = i; reordering = false }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline)

        when (tabIndex) {
            0 -> PodcastsTab(
                vm = vm,
                reordering = reordering,
                onDoneReordering = { reordering = false },
                onAddRss = { showAddDialog = true },
                onOpenShow = onOpenShow,
            )
            1 -> BooksTab(vm = vm, ps = ps, onOpenShow = onOpenShow)
            2 -> EpisodesTab(
                vm = vm, ps = ps,
                episodes = vm.recentEpisodes.collectAsState().value,
                header = "RECENT EPISODES",
                emptyText = "No episodes yet.\nAdd a podcast to get started.",
                onEpisodeMenu = onEpisodeMenu,
            )
            else -> EpisodesTab(
                vm = vm, ps = ps,
                episodes = vm.downloadedEpisodes.collectAsState().value,
                header = "DOWNLOADED",
                emptyText = "Nothing downloaded yet.\nUse an episode's menu to download it.",
                onEpisodeMenu = onEpisodeMenu,
            )
        }
    }

    if (showBackupDialog) BackupDialog(vm, onDismiss = { showBackupDialog = false })
    if (showFolderDialog) DownloadFolderDialog(downloads, onDismiss = { showFolderDialog = false })

    if (showAddDialog) {
        AddRssDialog(onDismiss = { showAddDialog = false }, onAdd = { url -> vm.repo.addShow(url) })
    }
}

@Composable
private fun PodcastsTab(
    vm: VoxaViewModel,
    reordering: Boolean,
    onDoneReordering: () -> Unit,
    onAddRss: () -> Unit,
    onOpenShow: (Show) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("voxa", Context.MODE_PRIVATE) }
    val shows by vm.shows.collectAsState()
    val latest by vm.latestByShow.collectAsState()
    var grid by remember { mutableStateOf(prefs.getBoolean("home_grid", true)) }
    var alpha by remember { mutableStateOf(prefs.getBoolean("home_alpha", false)) }
    var toRemove by remember { mutableStateOf<Show?>(null) }

    val list = shows ?: return
    // Custom order unless A-Z is on; reordering always works on the custom order.
    val ordered = remember(list, alpha, reordering) {
        if (alpha && !reordering) list.sortedBy { it.title.lowercase().removePrefix("the ") } else list
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reordering) {
                Caps("Editing order", Modifier.weight(1f))
                TextButton(onClick = onDoneReordering) { Text("DONE", fontWeight = FontWeight.Bold) }
            } else {
                Caps("${list.size} podcasts", Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (alpha) Lime else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { alpha = !alpha; prefs.edit().putBoolean("home_alpha", alpha).apply() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "A↕Z",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (alpha) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { grid = !grid; prefs.edit().putBoolean("home_grid", grid).apply() }) {
                    Icon(
                        if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = if (grid) "List view" else "Grid view",
                    )
                }
            }
        }

        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.size(48.dp))
                Text(
                    "No podcasts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(24.dp))
                AddRssCard(onAddRss)
            }
            return@Column
        }

        val cardMenu: @Composable (Show) -> Unit = { show -> CardMenu(onRefresh = { vm.refresh(show.id) }, onRemove = { toRemove = show }) }

        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(ordered, key = { _, s -> s.id }) { index, show ->
                    PodcastCard(
                        show = show,
                        updated = latest[show.id],
                        reordering = reordering,
                        canMoveEarlier = index > 0,
                        canMoveLater = index < ordered.lastIndex,
                        onClick = { onOpenShow(show) },
                        onMove = { d -> vm.move(show.id, d) },
                        menu = { cardMenu(show) },
                    )
                }
                if (!reordering) {
                    item(span = { GridItemSpan(maxLineSpan) }) { AddRssCard(onAddRss) }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(ordered, key = { it.id }) { show ->
                    val index = ordered.indexOf(show)
                    PodcastRow(
                        show = show,
                        updated = latest[show.id],
                        reordering = reordering,
                        canMoveEarlier = index > 0,
                        canMoveLater = index < ordered.lastIndex,
                        onClick = { onOpenShow(show) },
                        onMove = { d -> vm.move(show.id, d) },
                        menu = { cardMenu(show) },
                    )
                }
                if (!reordering) item { Box(Modifier.padding(top = 12.dp, end = 8.dp)) { AddRssCard(onAddRss) } }
            }
        }
    }

    toRemove?.let { show ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove ${show.title}?") },
            text = { Text("This unsubscribes and deletes any downloaded episodes.") },
            confirmButton = {
                TextButton(onClick = { vm.removeShow(show.id); toRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CardMenu(onRefresh: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, "Options", Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                onClick = { open = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onRemove() },
            )
        }
    }
}

@Composable
private fun ReorderArrows(canEarlier: Boolean, canLater: Boolean, onMove: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = { onMove(-1) }, enabled = canEarlier) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move earlier")
        }
        FilledTonalIconButton(onClick = { onMove(1) }, enabled = canLater) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move later")
        }
    }
}

@Composable
private fun PodcastCard(
    show: Show,
    updated: Long?,
    reordering: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    menu: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = !reordering, onClick = onClick)
            .padding(8.dp)
    ) {
        Box {
            Artwork(show.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), contentDescription = show.title)
            if (reordering) {
                ReorderArrows(
                    canMoveEarlier, canMoveLater, onMove,
                    Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    show.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Updated ${ago(updated ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!reordering) menu()
        }
    }
}

@Composable
private fun PodcastRow(
    show: Show,
    updated: Long?,
    reordering: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    menu: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !reordering, onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(show.artworkUrl, Modifier.size(64.dp), contentDescription = show.title)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    show.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Updated ${ago(updated ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reordering) ReorderArrows(canMoveEarlier, canMoveLater, onMove) else menu()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Dashed "Add RSS feed" card from the mock. */
@Composable
private fun AddRssCard(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .dashedBorder(MaterialTheme.colorScheme.onSurfaceVariant)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Lime)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = Ink) }
        Text(
            "Add RSS feed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Paste a feed URL to get started",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Recent episodes across all shows, or just the downloaded ones. */
@Composable
private fun EpisodesTab(
    vm: VoxaViewModel,
    ps: PlayerUiState,
    episodes: List<Episode>,
    header: String,
    emptyText: String,
    onEpisodeMenu: (Episode) -> Unit,
) {
    val shows by vm.shows.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val byId = remember(shows) { shows.orEmpty().associateBy { it.id } }

    if (episodes.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Caps(
                "$header · ${episodes.size}",
                Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        items(episodes, key = { it.id }) { ep ->
            val show = byId[ep.showId]
            val isCurrent = ps.mediaId == ep.id.toString()
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = show != null) { show?.let { togglePlay(vm, ps, ep, it) } }
                        .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(show?.artworkUrl, Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            ep.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(show?.title, episodeMeta(ep.publishedAt, ep.durationSec).ifEmpty { null })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        progress[ep.id]?.let { f ->
                            LinearProgressIndicator(
                                progress = { f },
                                color = Lime,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                    }
                    PlayCircleButton(isCurrent && ps.isPlaying, onClick = { show?.let { togglePlay(vm, ps, ep, it) } })
                    IconButton(onClick = { onEpisodeMenu(ep) }) { Icon(Icons.Filled.MoreVert, "Episode options") }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun AddRssDialog(onDismiss: () -> Unit, onAdd: suspend (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add RSS feed") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    singleLine = true,
                    placeholder = { Text("https://example.com/feed.xml") },
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !busy,
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = Lime)
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    val trimmed = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    scope.launch {
                        try {
                            onAdd(trimmed)
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't load that feed"
                            busy = false
                        }
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
