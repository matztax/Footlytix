@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.Player

/** Spielerprofil: Stammdaten, Stärken, Schwächen, Ziele, Notizen und Bewertung. */
@Composable
fun PlayerDetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val players by vm.players.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val p = players.firstOrNull { it.id == id }

    if (p == null) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Spieler nicht gefunden.")
        }
        return
    }

    var name by remember(p.id) { mutableStateOf(p.name) }
    var number by remember(p.id) { mutableStateOf(p.number.toString()) }
    var position by remember(p.id) { mutableStateOf(p.position) }
    var strengths by remember(p.id) { mutableStateOf(p.strengths) }
    var weaknesses by remember(p.id) { mutableStateOf(p.weaknesses) }
    var goals by remember(p.id) { mutableStateOf(p.goals) }
    var notes by remember(p.id) { mutableStateOf(p.notes) }
    var rTech by remember(p.id) { mutableStateOf(p.ratingTechnik) }
    var rTakt by remember(p.id) { mutableStateOf(p.ratingTaktik) }
    var rAth by remember(p.id) { mutableStateOf(p.ratingAthletik) }
    var rMent by remember(p.id) { mutableStateOf(p.ratingMental) }
    var transferring by remember { mutableStateOf(false) }
    fun clubName(cid: Long): String = clubs.firstOrNull { it.id == cid }?.name ?: "Vereinslos"

    val rated = listOf(rTech, rTakt, rAth, rMent).filter { it > 0 }
    val avg = if (rated.isEmpty()) null else rated.average()

    val dirty = name.trim() != p.name || number != p.number.toString() || position.trim() != p.position ||
        strengths.trim() != p.strengths || weaknesses.trim() != p.weaknesses || goals.trim() != p.goals ||
        notes.trim() != p.notes || rTech != p.ratingTechnik || rTakt != p.ratingTaktik ||
        rAth != p.ratingAthletik || rMent != p.ratingMental
    val goBack = rememberBackGuard(dirty, onBack)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = goBack) { Text("← Zurück") }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                vm.savePlayer(
                    p.copy(
                        name = name.trim().ifBlank { p.name },
                        number = number.toIntOrNull() ?: 0,
                        position = position.trim(),
                        strengths = strengths.trim(),
                        weaknesses = weaknesses.trim(),
                        goals = goals.trim(),
                        notes = notes.trim(),
                        ratingTechnik = rTech,
                        ratingTaktik = rTakt,
                        ratingAthletik = rAth,
                        ratingMental = rMent
                    )
                )
                Toast.makeText(ctx, "Gespeichert", Toast.LENGTH_SHORT).show()
            }) { Text("Speichern") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                number, { number = it.filter(Char::isDigit).take(2) },
                label = { Text("Nr.") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp)
            )
            OutlinedTextField(
                name, { name = it }, label = { Text("Name") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            position, { position = it }, label = { Text("Position") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Bewertung", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        RatingRow("Technik", rTech) { rTech = it }
        RatingRow("Taktik", rTakt) { rTakt = it }
        RatingRow("Athletik", rAth) { rAth = it }
        RatingRow("Mentalität", rMent) { rMent = it }
        Text(
            if (avg == null) "Gesamt: noch nicht bewertet" else "Gesamt: ★ " + "%.1f".format(avg) + " von 5",
            style = MaterialTheme.typography.bodyMedium
        )

        DevelopmentSection(vm, p.id, rTech, rTakt, rAth, rMent)

        OutlinedTextField(
            strengths, { strengths = it }, label = { Text("Stärken") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            weaknesses, { weaknesses = it }, label = { Text("Schwächen") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            goals, { goals = it }, label = { Text("Ziele / Entwicklung") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            notes, { notes = it }, label = { Text("Weitere Notizen") },
            minLines = 3, modifier = Modifier.fillMaxWidth()
        )

        AvailabilitySection(vm, p.id)
        PlayerStatsSection(vm, p.id)

        Text("Verein & Transfers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Aktueller Verein: ${clubName(p.clubId)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Bei einem Transfer bleiben alle Attribute, Analysen, Notizen und Bewertungen des Spielers erhalten.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { transferring = true }) { Text("Transferieren") }
            }
        }
        transfers.filter { it.playerId == p.id }.forEach { t ->
            Text(
                "${t.date}: ${clubName(t.fromClubId)} → ${clubName(t.toClubId)}" + (if (t.note.isNotBlank()) "  (${t.note})" else ""),
                style = MaterialTheme.typography.bodySmall
            )
        }

        AttributeSection(vm, "player", p.id)
        AnalysisSection(vm, "player", p.id)
        Spacer(Modifier.height(16.dp))
    }

    if (transferring) {
        TransferDialog(
            player = p,
            clubs = clubs,
            onDismiss = { transferring = false },
            onTransfer = { toId, note ->
                vm.transferPlayer(p, toId, note)
                transferring = false
            }
        )
    }
}

@Composable
private fun TransferDialog(
    player: Player,
    clubs: List<de.tactics.app.data.Club>,
    onDismiss: () -> Unit,
    onTransfer: (Long, String) -> Unit
) {
    var target by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${player.name} transferieren") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Neuer Verein:", style = MaterialTheme.typography.labelLarge)
                clubs.filter { it.id != player.clubId }.forEach { c ->
                    FilterChip(
                        selected = target == c.id,
                        onClick = { target = c.id },
                        label = { Text(c.name + if (c.isOwn) " (eigener Verein)" else "") }
                    )
                }
                if (player.clubId != 0L) {
                    FilterChip(selected = target == 0L, onClick = { target = 0L }, label = { Text("Vereinslos") })
                }
                OutlinedTextField(
                    note, { note = it }, label = { Text("Notiz (optional, z. B. Wechsel zur Winterpause)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = { onTransfer(target ?: 0L, note.trim()) }) { Text("Transferieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
