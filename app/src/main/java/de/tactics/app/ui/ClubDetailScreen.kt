@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

/** Verein (eigen oder Gegner): Stammdaten, Attribute, Analysen und Spieler. */
@Composable
fun ClubDetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, onOpenPlayer: (Long) -> Unit) {
    val ctx = LocalContext.current
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val club = clubs.firstOrNull { it.id == id }

    if (club == null) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Verein nicht gefunden.")
        }
        return
    }

    var name by remember(club.id) { mutableStateOf(club.name) }
    var own by remember(club.id) { mutableStateOf(club.isOwn) }
    var system by remember(club.id) { mutableStateOf(club.system) }
    var notes by remember(club.id) { mutableStateOf(club.notes) }
    var addingPlayer by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val members = players.filter { it.clubId == club.id }.sortedBy { it.number }

    val dirty = name.trim() != club.name || own != club.isOwn || system.trim() != club.system || notes.trim() != club.notes
    val goBack = rememberBackGuard(dirty, onBack)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = goBack) { Text("← Zurück") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    vm.saveClub(club.copy(name = name.trim(), isOwn = own, system = system.trim(), notes = notes.trim()))
                    Toast.makeText(ctx, "Gespeichert", Toast.LENGTH_SHORT).show()
                }
            ) { Text("Speichern") }
        }

        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !own, onClick = { own = false }, label = { Text("Gegner") })
            FilterChip(selected = own, onClick = { own = true }, label = { Text("Eigener Verein") })
        }
        OutlinedTextField(
            system, { system = it }, label = { Text("Übliches System (z. B. 4-4-2)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            notes, { notes = it }, label = { Text("Notizen zum Verein") },
            minLines = 3, modifier = Modifier.fillMaxWidth()
        )

        AttributeSection(vm, "club", club.id)
        AnalysisSection(vm, "club", club.id)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spieler (${members.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { addingPlayer = true }) { Text("+ Spieler") }
        }
        if (members.isEmpty()) Text("Noch keine Spieler in diesem Verein.", style = MaterialTheme.typography.bodySmall)
        members.forEach { p ->
            ListItem(
                modifier = Modifier.clickable { onOpenPlayer(p.id) },
                leadingContent = { Text("${p.number}", style = MaterialTheme.typography.titleLarge) },
                headlineContent = { Text(p.name) },
                supportingContent = { if (p.position.isNotBlank()) Text(p.position) }
            )
        }

        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Verein löschen") }
        Spacer(Modifier.height(16.dp))
    }

    if (addingPlayer) {
        NewPlayerDialog(
            clubs = clubs,
            defaultClubId = club.id,
            onDismiss = { addingPlayer = false },
            onSave = { vm.savePlayer(it); addingPlayer = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Verein löschen?") },
            text = { Text("Die Spieler bleiben mit allen Attributen erhalten und werden vereinslos. Attribute und Analysen des Vereins werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteClub(club); confirmDelete = false; onBack() }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}
