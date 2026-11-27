package dev.osianj.voxa

import android.app.Application
import dev.osianj.voxa.data.DownloadStorage
import dev.osianj.voxa.data.VoxaDatabase
import dev.osianj.voxa.data.VoxaRepository
import dev.osianj.voxa.playback.PlaybackConnection

class VoxaApplication : Application() {
    lateinit var repository: VoxaRepository
        private set
    lateinit var playback: PlaybackConnection
        private set
    lateinit var storage: DownloadStorage
        private set

    override fun onCreate() {
        super.onCreate()
        storage = DownloadStorage(this, filesDir)
        repository = VoxaRepository(VoxaDatabase.build(this), storage)
        playback = PlaybackConnection(this)
        dev.osianj.voxa.data.RefreshWorker.schedule(this)
    }
}
