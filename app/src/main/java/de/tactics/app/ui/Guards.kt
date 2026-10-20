package de.tactics.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Schutz vor ungewolltem Verlust: Solange [dirty] gilt, fragt die Zurück-Taste
 * (auch die des Systems) vor dem Verlassen nach. Liefert die Aktion für den eigenen Zurück-Button.
 */
@Composable
fun rememberBackGuard(dirty: Boolean, onBack: () -> Unit): () -> Unit {
    var ask by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { ask = true }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Du hast noch nicht gespeichert. Beim Verlassen gehen die Änderungen verloren.") },
            confirmButton = { TextButton(onClick = { ask = false; onBack() }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Weiter bearbeiten") } }
        )
    }
    return { if (dirty) ask = true else onBack() }
}

/** Einfache Sicherheitsabfrage vor dem Löschen. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Löschen",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
