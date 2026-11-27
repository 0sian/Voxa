package dev.osianj.voxa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.osianj.voxa.ui.VoxaRoot
import dev.osianj.voxa.ui.VoxaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationsOnce()
        val app = application as VoxaApplication
        app.playback.connect()
        setContent {
            VoxaTheme {
                VoxaRoot(app)
            }
        }
    }

    /** New-episode alerts need the Android 13+ notification permission; ask once, on first launch. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("voxa", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
