@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import de.tactics.app.Importer
import de.tactics.app.MainViewModel

/** Import von Spielerlisten (CSV) und Spielplänen (.ics) aus SpielerPlus, Verbandsportalen und Tabellen. */
@Composable
fun ImportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val seasons by vm.seasons.collectAsStateWithLifecycle()

    var playersRaw by remember { mutableStateOf("") }
    var playersFile by remember { mutableStateOf("") }
    var targetClub by remember { mutableStateOf(-1L) }

    var icsRaw by remember { mutableStateOf("") }
    var icsFile by remember { mutableStateOf("") }
    var ownClubId by remember { mutableStateOf(-1L) }
    var seasonId by remember { mutableStateOf(-1L) }
    var createClubs by remember { mutableStateOf(true) }
    var onlyFuture by remember { mutableStateOf(false) }

    val ownClubs = clubs.filter { it.isOwn }
    val club = clubs.firstOrNull { it.id == targetClub } ?: ownClubs.firstOrNull()
    val ownClub = ownClubs.firstOrNull { it.id == ownClubId } ?: ownClubs.firstOrNull()
    val season = seasons.firstOrNull { it.id == seasonId } ?: seasons.firstOrNull { it.isActive } ?: seasons.firstOrNull()

    val playerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { playersRaw = Importer.readText(ctx, uri); playersFile = uri.lastPathSegment ?: "Datei" }
                .onFailure { Toast.makeText(ctx, "Datei konnte nicht gelesen werden", Toast.LENGTH_LONG).show() }
        }
    }
    val icsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { icsRaw = Importer.readText(ctx, uri); icsFile = uri.lastPathSegment ?: "Datei" }
                .onFailure { Toast.makeText(ctx, "Datei konnte nicht gelesen werden", Toast.LENGTH_LONG).show() }
        }
    }

    val parsedPlayers = remember(playersRaw) { Importer.parsePlayers(Importer.parseCsv(playersRaw)) }
    val parsedMatches = remember(icsRaw, ownClub?.name) { Importer.parseIcs(icsRaw, ownClub?.name ?: "") }
    val shownMatches = if (onlyFuture) parsedMatches.filter { it.dateMillis >= System.currentTimeMillis() - 86_400_000L } else parsedMatches

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("Import", style = MaterialTheme.typography.titleLarge)
        Text(
            "Lies Spielerlisten und Spielpläne aus Dateien ein, statt sie abzutippen. Alles bleibt auf deinem Gerät. Exportiere die Datei vorher in SpielerPlus oder im Verbandsportal (Tabelle als CSV, Kalender als .ics).",
            style = MaterialTheme.typography.bodySmall
        )

        // ---------------- Spieler
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spielerliste (CSV)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Erkannt werden die Spalten Name (oder Vorname und Nachname), Nummer und Position. Excel-Dateien vorher als CSV speichern.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { playerPicker.launch(arrayOf("*/*")) }) {
                    Text(if (playersFile.isEmpty()) "CSV-Datei wählen" else "Andere Datei wählen")
                }
                if (playersFile.isNotEmpty()) {
                    Text("$playersFile: ${parsedPlayers.size} Spieler erkannt", style = MaterialTheme.typography.titleSmall)
                    parsedPlayers.take(6).forEach {
                        Text("${if (it.number > 0) it.number.toString() else "–"}  ${it.name}  ${it.position}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (parsedPlayers.size > 6) Text("… und ${parsedPlayers.size - 6} weitere", style = MaterialTheme.typography.bodySmall)
                    Text("In diesen Verein importieren:", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        clubs.forEach { c ->
                            FilterChip(selected = club?.id == c.id, onClick = { targetClub = c.id }, label = { Text(c.name) })
                        }
                    }
                    Button(
                        enabled = parsedPlayers.isNotEmpty() && club != null,
                        onClick = {
                            vm.importPlayers(club!!.id, parsedPlayers) { added, skipped ->
                                Toast.makeText(ctx, "$added neu, $skipped schon vorhanden", Toast.LENGTH_LONG).show()
                            }
                            playersRaw = ""; playersFile = ""
                        }
                    ) { Text("${parsedPlayers.size} Spieler importieren") }
                }
            }
        }

        // ---------------- Spielplan
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spielplan (.ics)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aus Terminen wie „TSV Beispiel – FC Muster“ werden Gegner und Heim/Auswärts erkannt. Doppelte Spiele werden übersprungen.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { icsPicker.launch(arrayOf("*/*")) }) {
                    Text(if (icsFile.isEmpty()) "Kalenderdatei wählen" else "Andere Datei wählen")
                }
                if (icsFile.isNotEmpty()) {
                    Text("$icsFile: ${shownMatches.size} Termine", style = MaterialTheme.typography.titleSmall)
                    shownMatches.take(6).forEach {
                        Text(
                            formatDate(it.dateMillis) + "  " + (if (it.home) "Heim" else "Auswärts") + " vs. " + it.opponent,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (shownMatches.size > 6) Text("… und ${shownMatches.size - 6} weitere", style = MaterialTheme.typography.bodySmall)
                    Text("Dein Verein (zur Erkennung von Heim/Auswärts):", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ownClubs.forEach { c ->
                            FilterChip(selected = ownClub?.id == c.id, onClick = { ownClubId = c.id }, label = { Text(c.name) })
                        }
                    }
                    Text("Saison:", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        seasons.forEach { s ->
                            FilterChip(selected = season?.id == s.id, onClick = { seasonId = s.id }, label = { Text(s.name) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = createClubs, onClick = { createClubs = !createClubs }, label = { Text("Gegner anlegen") })
                        FilterChip(selected = onlyFuture, onClick = { onlyFuture = !onlyFuture }, label = { Text("Nur kommende") })
                    }
                    Button(
                        enabled = shownMatches.isNotEmpty() && season != null,
                        onClick = {
                            vm.importMatches(season!!.id, ownClub?.id ?: 0L, shownMatches, createClubs) { added, skipped ->
                                Toast.makeText(ctx, "$added neue Spiele, $skipped übersprungen", Toast.LENGTH_LONG).show()
                            }
                            icsRaw = ""; icsFile = ""
                        }
                    ) { Text("${shownMatches.size} Spiele importieren") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
