@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.tactics.app.AppPrefs
import de.tactics.app.AutoBackup
import de.tactics.app.Exporter
import de.tactics.app.MainViewModel
import de.tactics.app.Security
import de.tactics.app.applySecureFlag
import de.tactics.app.findFragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findFragmentActivity()
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(ctx) }
    var lock by remember { mutableStateOf(prefs.lockEnabled) }
    var auto by remember { mutableStateOf(prefs.autoBackup) }
    var folder by remember { mutableStateOf(prefs.backupFolder) }
    var lastTime by remember { mutableStateOf(prefs.lastBackupTime) }
    var files by remember { mutableStateOf(AutoBackup.list(ctx)) }
    var restoreRequest by remember { mutableStateOf<RestoreRequest?>(null) }
    var busy by remember { mutableStateOf(false) }
    var hasPassword by remember { mutableStateOf(prefs.backupPassword != null) }
    var passwordDialog by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf(prefs.lastFolderError) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            prefs.backupFolder = uri.toString()
            folder = uri.toString()
        }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
        }
        Text("Sicherheit & Backup", style = MaterialTheme.typography.titleLarge)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App-Sperre", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Beim Öffnen und nach einer Minute im Hintergrund wird Fingerabdruck, Gesicht oder die PIN bzw. das Muster deines Geräts verlangt. Die Daten der Sperre liest die App nie selbst, das übernimmt Android.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = lock,
                    onCheckedChange = { want ->
                        if (activity == null) {
                            toast("Sperre ist hier nicht verfügbar.")
                        } else if (want) {
                            Security.prompt(
                                activity, "Sperre aktivieren",
                                onSuccess = {
                                prefs.lockEnabled = true; lock = true
                                activity.applySecureFlag(true)
                            },
                                onFail = { toast(it) }
                            )
                        } else {
                            Security.prompt(
                                activity, "Sperre ausschalten",
                                onSuccess = {
                                prefs.lockEnabled = false; lock = false
                                activity.applySecureFlag(false)
                            },
                                onFail = { toast(it) }
                            )
                        }
                    }
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatisches Backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Einmal pro Tag beim Start; die letzten 7 Sicherungen bleiben auf dem Gerät erhalten.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = auto, onCheckedChange = { auto = it; prefs.autoBackup = it })
                }
                Text(
                    "Letztes Backup: " + (if (lastTime == 0L) "noch nie" else formatDate(lastTime) + " " +
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(java.util.Date(lastTime))),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val f = vm.backupNow()
                            busy = false
                            lastTime = prefs.lastBackupTime
                            folderError = prefs.lastFolderError
                            files = AutoBackup.list(ctx)
                            toast(if (f != null) "Backup gespeichert" else "Backup fehlgeschlagen")
                        }
                    }
                ) { Text(if (busy) "Sichere …" else "Jetzt sichern") }

                Text(
                    "Zusätzlicher Ordner: " + (folder?.let { Uri.parse(it).lastPathSegment ?: it } ?: "nicht gewählt"),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (folder != null && folderError != null) {
                    Text(
                        "Letzte Kopie in den Ordner fehlgeschlagen: $folderError. Bitte den Ordner neu wählen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Ordner wählen") }
                    if (folder != null) {
                        OutlinedButton(onClick = {
                            prefs.backupFolder = null; folder = null
                            prefs.lastFolderError = null; folderError = null
                        }) { Text("Entfernen") }
                    }
                }
                Text(
                    "Mit einem Ordner (z. B. Google Drive oder SD-Karte) liegt jede Sicherung zusätzlich außerhalb der App. " +
                        "Die App-Daten werden nicht in die Google-Gerätesicherung übertragen – richte deshalb einen Ordner ein " +
                        "oder exportiere regelmäßig ein Backup.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Backup-Passwort", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (hasPassword) "Aktiv: Backups im Ordner sowie geteilte und exportierte Backups sind verschlüsselt (AES-256)."
                    else "Nicht gesetzt: Backups außerhalb der App sind lesbar für jeden, der die Datei erhält. " +
                        "Spielerdaten wie Verletzungen sind besonders schützenswert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasPassword || folder == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = { passwordDialog = true }) {
                    Text(if (hasPassword) "Passwort ändern / entfernen" else "Passwort festlegen")
                }
            }
        }

        Text("Gespeicherte Sicherungen", style = MaterialTheme.typography.titleMedium)
        if (files.isEmpty()) Text("Noch keine automatische Sicherung vorhanden.", style = MaterialTheme.typography.bodySmall)
        files.forEach { f ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(AutoBackup.label(f), style = MaterialTheme.typography.titleSmall)
                        Text("${f.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val shareFile = runCatching { withContext(Dispatchers.Default) { AutoBackup.shareableCopy(ctx, f) } }
                            shareFile.onSuccess { sf ->
                                Exporter.attempt(ctx) { Exporter.share(ctx, sf, "application/json", "Backup teilen") }
                            }.onFailure { toast("Teilen fehlgeschlagen: " + (it.message ?: "")) }
                        }
                    }) { Text("Teilen") }
                    TextButton(onClick = {
                        restoreRequest = RestoreRequest(AutoBackup.label(f)) { f.readText() }
                    }) { Text("Wiederherstellen") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    RestoreFlow(vm, restoreRequest, onClose = { restoreRequest = null }, onRestored = { files = AutoBackup.list(ctx) })

    if (passwordDialog) {
        BackupPasswordDialog(
            hasPassword = hasPassword,
            onSave = { pw ->
                prefs.backupPassword = pw
                hasPassword = pw != null
                passwordDialog = false
                toast(if (pw != null) "Backup-Passwort gespeichert" else "Backup-Passwort entfernt")
            },
            onDismiss = { passwordDialog = false }
        )
    }
}
