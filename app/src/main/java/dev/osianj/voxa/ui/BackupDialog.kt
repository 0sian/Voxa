package dev.osianj.voxa.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Back up / restore subscriptions and progress (JSON), or move subscriptions to another app (OPML). */
@Composable
fun BackupDialog(vm: VoxaViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun run(kind: String, uri: Uri?) {
        if (uri == null) return
        Toast.makeText(context, "Working…", Toast.LENGTH_SHORT).show()
        vm.backupAction(kind, uri) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { run("export", it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { run("restore", it) }
    val saveOpml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { run("opml_export", it) }
    val openOpml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { run("opml_import", it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup & restore") },
        text = {
            Column {
                Text(
                    "A backup keeps your subscriptions, their order, where you are in each episode, played and hidden episodes, and your settings. " +
                        "Downloaded audio files aren't included. Restoring re-fetches each feed, so you need to be online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TextButton({ onDismiss(); save.launch("voxa-backup.json") }, Modifier.fillMaxWidth()) { Text("Back up to file…", Modifier.fillMaxWidth()) }
                TextButton({ onDismiss(); restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Restore from file…", Modifier.fillMaxWidth()) }
                TextButton({ onDismiss(); saveOpml.launch("voxa-subscriptions.opml") }, Modifier.fillMaxWidth()) { Text("Export subscriptions (OPML)…", Modifier.fillMaxWidth()) }
                TextButton({ onDismiss(); openOpml.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Import subscriptions (OPML)…", Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
