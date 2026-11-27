package dev.osianj.voxa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.osianj.voxa.VoxaApplication

/**
 * Home (Podcasts / Episodes / Downloads) and the Show page, with the mini player in the Scaffold's
 * bottom bar and the full player sliding over the top. The episode sheet is shared by every list.
 */
@Composable
fun VoxaRoot(app: VoxaApplication) {
    val vm: VoxaViewModel = viewModel(factory = viewModelFactory { initializer { VoxaViewModel(app) } })
    val controller by app.playback.controller.collectAsState()
    val ps = rememberPlayerUiState(controller)
    val downloads = rememberDownloadActions()

    var openShowId by rememberSaveable { mutableStateOf<Long?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var sheetEpisodeId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && openShowId != null) { openShowId = null }

    Box {
        Scaffold(bottomBar = { MiniPlayer(ps, onOpen = { playerOpen = true }, onDismiss = { vm.dismissPlayer(); playerOpen = false }) }) { padding ->
            val id = openShowId
            if (id == null) {
                HomeScreen(
                    vm = vm, ps = ps, downloads = downloads, padding = padding,
                    onOpenShow = { openShowId = it.id },
                    onEpisodeMenu = { sheetEpisodeId = it.id },
                )
            } else {
                ShowScreen(
                    vm = vm, showId = id, ps = ps, downloads = downloads, padding = padding,
                    onBack = { openShowId = null },
                    onEpisodeMenu = { sheetEpisodeId = it.id },
                )
            }
        }

        AnimatedVisibility(
            visible = playerOpen && ps.hasItem,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PlayerScreen(vm, ps, downloads, onClose = { playerOpen = false })
        }
    }

    sheetEpisodeId?.let { id ->
        EpisodeSheet(id, vm, ps, downloads, onDismiss = { sheetEpisodeId = null })
    }
}
