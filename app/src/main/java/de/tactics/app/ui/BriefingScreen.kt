@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.Exporter
import de.tactics.app.MainViewModel
import de.tactics.app.data.Briefing

private const val TEMPLATE = "Gegner:\nSystem:\n\nUnser Plan:\n\nAufgaben:\n\nStandards:\n"

private fun share(ctx: Context, b: Briefing) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, b.title)
        putExtra(Intent.EXTRA_TEXT, "${b.title}\n\n${b.text}")
    }
    ctx.startActivity(Intent.createChooser(intent, "Briefing teilen"))
}

@Composable
fun BriefingScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val list by vm.briefings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Briefing?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Briefing?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            Text(
                "Noch keine Briefings.\nMit + ein Spieler-Briefing schreiben und per Messenger teilen.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(list, key = { it.id }) { b ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { editing = b }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(b.title, style = MaterialTheme.typography.titleMedium)
                        Text(b.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { share(ctx, b) }) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Text("  Teilen")
                            }
                            TextButton(onClick = {
                                Exporter.attempt(ctx) {
                                    Exporter.share(ctx, Exporter.briefingPdf(ctx, b), "application/pdf", "Briefing teilen")
                                }
                            }) { Text("PDF") }
                            TextButton(onClick = { deleting = b }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Text("  Löschen")
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Briefing hinzufügen") }
    }

    deleting?.let { b ->
        ConfirmDialog(
            title = "Briefing löschen?",
            text = "„${b.title}“ wird gelöscht.",
            onConfirm = { vm.deleteBriefing(b); deleting = null },
            onDismiss = { deleting = null }
        )
    }
    if (creating || editing != null) {
        BriefingDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveBriefing(it); creating = false; editing = null }
        )
    }
}

@Composable
private fun BriefingDialog(initial: Briefing?, onDismiss: () -> Unit, onSave: (Briefing) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var text by remember { mutableStateOf(initial?.text ?: TEMPLATE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neues Briefing" else "Briefing bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("Inhalt") }, minLines = 8)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = initial ?: Briefing(title = "", text = "")
                    onSave(base.copy(title = title.trim(), text = text))
                }
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
