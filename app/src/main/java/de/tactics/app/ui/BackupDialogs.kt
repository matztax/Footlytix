package de.tactics.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.tactics.app.BackupCrypto
import de.tactics.app.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Eine angefragte Wiederherstellung: [source] erscheint in der Rückfrage, [read] liefert den Dateiinhalt. */
class RestoreRequest(val source: String, val read: suspend () -> String)

private enum class RestoreStage { Confirm, Password, Busy }

/**
 * Einheitlicher Ablauf für jede Wiederherstellung:
 * Rückfrage → (bei verschlüsselten Backups) Passwort → Sicherheitskopie + Wiederherstellen → Meldung.
 */
@Composable
fun RestoreFlow(
    vm: MainViewModel,
    request: RestoreRequest?,
    onClose: () -> Unit,
    onRestored: () -> Unit = {}
) {
    if (request == null) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember(request) { mutableStateOf(RestoreStage.Confirm) }
    var password by remember(request) { mutableStateOf("") }
    var passwordHint by remember(request) { mutableStateOf<String?>(null) }

    fun finish(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        onClose()
    }

    fun startRestore(pw: String?) {
        stage = RestoreStage.Busy
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { request.read() }
                val safety = vm.restoreBackup(text, pw)
                onRestored()
                finish(
                    if (safety != null) "Backup wiederhergestellt. Der vorherige Stand liegt als Sicherheitskopie unter „Sicherheit & Backup“."
                    else "Backup wiederhergestellt."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupCrypto.PasswordRequiredException) {
                passwordHint = null
                stage = RestoreStage.Password
            } catch (e: BackupCrypto.WrongPasswordException) {
                passwordHint = if (pw == null) "Das gespeicherte Backup-Passwort passt nicht zu dieser Datei."
                else "Falsches Passwort."
                stage = RestoreStage.Password
            } catch (e: Exception) {
                finish("Wiederherstellung fehlgeschlagen: " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    when (stage) {
        RestoreStage.Confirm -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Backup wiederherstellen?") },
            text = {
                Text(
                    "Alle aktuellen Daten werden durch den Stand aus „${request.source}“ ersetzt.\n\n" +
                        "Der aktuelle Stand wird vorher automatisch als Sicherheitskopie gespeichert."
                )
            },
            confirmButton = { TextButton(onClick = { startRestore(null) }) { Text("Wiederherstellen") } },
            dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } }
        )

        RestoreStage.Password -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Backup-Passwort") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dieses Backup ist verschlüsselt. Bitte das Passwort eingeben, mit dem es erstellt wurde.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    passwordHint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty(), onClick = { startRestore(password) }) { Text("Entschlüsseln") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } }
        )

        RestoreStage.Busy -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Bitte warten") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Backup wird wiederhergestellt …")
                }
            },
            confirmButton = {}
        )
    }
}

/**
 * Backup-Passwort setzen, ändern oder entfernen.
 * @param onSave neues Passwort oder null zum Entfernen
 */
@Composable
fun BackupPasswordDialog(hasPassword: Boolean, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = pw.isNotEmpty() && pw.length < MIN_PASSWORD_LENGTH
    val mismatch = repeat.isNotEmpty() && repeat != pw
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) "Backup-Passwort ändern" else "Backup-Passwort festlegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Backups im gewählten Ordner, geteilte und exportierte Backups werden damit verschlüsselt. " +
                        "Ohne dieses Passwort lassen sie sich nicht wiederherstellen – bitte gut aufbewahren.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("Neues Passwort") },
                    singleLine = true,
                    isError = tooShort,
                    supportingText = { if (tooShort) Text("Mindestens $MIN_PASSWORD_LENGTH Zeichen") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Passwort wiederholen") },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = { if (mismatch) Text("Stimmt nicht überein") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pw.length >= MIN_PASSWORD_LENGTH && pw == repeat,
                onClick = { onSave(pw) }
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (hasPassword) TextButton(onClick = { onSave(null) }) { Text("Entfernen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}

private const val MIN_PASSWORD_LENGTH = 6
