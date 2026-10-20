@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

// ------------------------------------------------------------------ Spieldetail
@Composable
fun MatchDetailScreen(vm: MainViewModel, id: Long, onLive: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val matches by vm.matches.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val ownPlayers by vm.ownPlayers.collectAsStateWithLifecycle()
    val formations by vm.formations.collectAsStateWithLifecycle()
    val allRows by vm.matchPlayers.collectAsStateWithLifecycle()
    val availabilities by vm.availabilities.collectAsStateWithLifecycle()
    val defs by vm.attributeDefs.collectAsStateWithLifecycle()
    val links by vm.attributeLinks.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val match = matches.firstOrNull { it.id == id }

    if (match == null) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Spiel nicht gefunden.")
        }
        return
    }

    val saved = allRows.filter { it.matchId == match.id }
    var date by remember(match.id) { mutableStateOf(match.dateMillis) }
    var oppId by remember(match.id) { mutableStateOf(match.opponentClubId) }
    var oppName by remember(match.id) { mutableStateOf(match.opponentName) }
    var home by remember(match.id) { mutableStateOf(match.home) }
    var comp by remember(match.id) { mutableStateOf(match.competition) }
    var playedFlag by remember(match.id) { mutableStateOf(match.played) }
    var gf by remember(match.id) { mutableStateOf(match.goalsFor.toString()) }
    var ga by remember(match.id) { mutableStateOf(match.goalsAgainst.toString()) }
    var formationId by remember(match.id) { mutableStateOf(match.formationId) }
    var notes by remember(match.id) { mutableStateOf(match.notes) }
    val rows = remember(match.id, saved.size) { mutableStateListOf<MatchPlayer>().apply { addAll(saved) } }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val squad = (if (match.ownClubId > 0) players.filter { it.clubId == match.ownClubId } else ownPlayers).sortedBy { it.number }
    val oppClub = clubs.firstOrNull { it.id == oppId }

    val dirty = date != match.dateMillis || oppId != match.opponentClubId || oppName.trim() != match.opponentName ||
        home != match.home || comp != match.competition || playedFlag != match.played ||
        (gf.toIntOrNull() ?: 0) != match.goalsFor || (ga.toIntOrNull() ?: 0) != match.goalsAgainst ||
        formationId != match.formationId || notes.trim() != match.notes ||
        rows.toList().map { it.copy(id = 0, matchId = 0) } != saved.map { it.copy(id = 0, matchId = 0) }
    val goBack = rememberBackGuard(dirty, onBack)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = goBack) { Text("← Zurück") }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                vm.saveMatch(
                    match.copy(
                        dateMillis = date, opponentClubId = oppId, opponentName = oppName.trim(),
                        home = home, competition = comp, played = playedFlag,
                        goalsFor = gf.toIntOrNull() ?: 0, goalsAgainst = ga.toIntOrNull() ?: 0,
                        formationId = formationId, notes = notes.trim()
                    ),
                    rows.toList()
                )
                Toast.makeText(ctx, "Gespeichert", Toast.LENGTH_SHORT).show()
            }) { Text("Speichern") }
        }

        Button(onClick = onLive, modifier = Modifier.fillMaxWidth()) { Text("Live-Modus (Spieltag)") }
        OutlinedButton(onClick = { showDatePicker(ctx, date) { date = it } }) { Text("Datum: " + formatDate(date)) }
        Text("Gegner", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = oppId == 0L, onClick = { oppId = 0L }, label = { Text("frei") })
            clubs.filter { !it.isOwn }.forEach { c ->
                FilterChip(selected = oppId == c.id, onClick = { oppId = c.id }, label = { Text(c.name) })
            }
        }
        if (oppId == 0L) {
            OutlinedTextField(
                oppName, { oppName = it }, label = { Text("Name des Gegners") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedButton(onClick = { showInfo = true }) { Text("Gegner-Info anzeigen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = home, onClick = { home = true }, label = { Text("Heim") })
            FilterChip(selected = !home, onClick = { home = false }, label = { Text("Auswärts") })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COMPETITIONS.forEach { c ->
                FilterChip(selected = comp == c, onClick = { comp = c }, label = { Text(c) })
            }
        }

        Text("Ergebnis", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !playedFlag, onClick = { playedFlag = false }, label = { Text("Geplant") })
            FilterChip(selected = playedFlag, onClick = { playedFlag = true }, label = { Text("Gespielt") })
        }
        if (playedFlag) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    gf, { gf = it.filter(Char::isDigit).take(2) }, label = { Text("Unsere Tore") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Text(":")
                OutlinedTextField(
                    ga, { ga = it.filter(Char::isDigit).take(2) }, label = { Text("Gegentore") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("Aufstellung", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = formationId == 0L, onClick = { formationId = 0L }, label = { Text("keine") })
            formations.forEach { f ->
                FilterChip(selected = formationId == f.id, onClick = { formationId = f.id }, label = { Text(f.name) })
            }
        }
        OutlinedButton(
            enabled = formationId != 0L,
            onClick = {
                val f = formations.firstOrNull { it.id == formationId }
                val ph = f?.let { decodePhases(it.phasesJson).firstOrNull() }
                ph?.tokens?.filter { !it.opponent && it.playerId >= 0 }?.forEach { t ->
                    val i = rows.indexOfFirst { it.playerId == t.playerId }
                    if (i >= 0) {
                        rows[i] = rows[i].copy(started = true, minutes = if (rows[i].minutes == 0) 90 else rows[i].minutes)
                    } else {
                        rows.add(MatchPlayer(playerId = t.playerId, started = true, minutes = 90))
                    }
                }
            }
        ) { Text("Startelf aus Aufstellung übernehmen") }

        Text("Einsätze & Statistik", style = MaterialTheme.typography.titleMedium)
        Text("Tippe auf einen Spieler, um Einsatz, Minuten, Tore, Vorlagen, Karten und Verletzung einzutragen.", style = MaterialTheme.typography.bodySmall)
        squad.forEach { p ->
            val r = rows.firstOrNull { it.playerId == p.id }
            val avail = activeAvailability(availabilities, p.id)
            val roleText = when {
                r == null -> "nicht dabei"
                r.started -> "Startelf"
                else -> "Bank"
            }
            val stats = if (r != null) listOfNotNull(
                if (r.minutes > 0) "${r.minutes}'" else null,
                if (r.goals > 0) "${r.goals} Tor(e)" else null,
                if (r.assists > 0) "${r.assists} Vorl." else null,
                if (r.yellow > 0) "Gelb" else null,
                if (r.red > 0) "Rot" else null,
                if (r.injured) "verletzt" else null
            ).joinToString(" · ") else ""
            ListItem(
                modifier = Modifier.clickable { editingPlayer = p },
                leadingContent = { Text("${p.number}", style = MaterialTheme.typography.titleLarge) },
                headlineContent = { Text(p.name) },
                supportingContent = {
                    Text(
                        listOfNotNull(roleText, stats.ifBlank { null }, avail?.let { availLabel(it.type) }).joinToString(" · ")
                    )
                }
            )
        }

        OutlinedTextField(
            notes, { notes = it }, label = { Text("Notizen zum Spiel") },
            minLines = 3, modifier = Modifier.fillMaxWidth()
        )

        AnalysisSection(vm, "match", match.id)

        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Spiel löschen") }
        Spacer(Modifier.height(16.dp))
    }

    editingPlayer?.let { p ->
        EinsatzDialog(
            player = p,
            row = rows.firstOrNull { it.playerId == p.id },
            onDismiss = { editingPlayer = null },
            onSave = { newRow ->
                val i = rows.indexOfFirst { it.playerId == p.id }
                if (newRow == null) {
                    if (i >= 0) rows.removeAt(i)
                } else if (i >= 0) {
                    rows[i] = newRow
                } else {
                    rows.add(newRow)
                }
                editingPlayer = null
            }
        )
    }

    if (showInfo && oppClub != null) {
        val report = buildOpponentReport(oppClub, players.filter { it.clubId == oppClub.id }, defs, links, analyses)
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Gegner-Info") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(report) } },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveBriefing(Briefing(title = "Gegner: ${oppClub.name}", text = report))
                    showInfo = false
                }) { Text("Als Briefing speichern") }
            },
            dismissButton = { TextButton(onClick = { showInfo = false }) { Text("Schließen") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Spiel löschen?") },
            text = { Text("Das Spiel samt Einsätzen und Analysen wird gelöscht.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMatch(match); confirmDelete = false; onBack() }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun EinsatzDialog(player: Player, row: MatchPlayer?, onDismiss: () -> Unit, onSave: (MatchPlayer?) -> Unit) {
    // 0 = nicht dabei, 1 = Bank, 2 = Startelf
    var role by remember { mutableStateOf(if (row == null) 0 else if (row.started) 2 else 1) }
    var minutes by remember { mutableStateOf((row?.minutes ?: 0).toString()) }
    var goals by remember { mutableStateOf(row?.goals ?: 0) }
    var assists by remember { mutableStateOf(row?.assists ?: 0) }
    var yellow by remember { mutableStateOf(row?.yellow ?: 0) }
    var red by remember { mutableStateOf(row?.red ?: 0) }
    var injured by remember { mutableStateOf(row?.injured ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${player.number} ${player.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = role == 0, onClick = { role = 0 }, label = { Text("Nicht dabei") })
                    FilterChip(selected = role == 1, onClick = { role = 1 }, label = { Text("Bank") })
                    FilterChip(selected = role == 2, onClick = { role = 2 }, label = { Text("Startelf") })
                }
                if (role > 0) {
                    OutlinedTextField(
                        minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("Minuten") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Stepper("Tore", goals) { goals = it }
                    Stepper("Vorlagen", assists) { assists = it }
                    Stepper("Gelbe Karten", yellow) { yellow = it }
                    Stepper("Rote Karten", red) { red = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Im Spiel verletzt", Modifier.weight(1f))
                        Switch(checked = injured, onCheckedChange = { injured = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (role == 0) {
                    onSave(null)
                } else {
                    onSave(
                        MatchPlayer(
                            id = row?.id ?: 0L, matchId = row?.matchId ?: 0L, playerId = player.id,
                            started = role == 2, minutes = minutes.toIntOrNull() ?: 0,
                            goals = goals, assists = assists, yellow = yellow, red = red, injured = injured
                        )
                    )
                }
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { if (value > 0) onChange(value - 1) }) { Text("−") }
        Text(value.toString(), Modifier.width(24.dp))
        OutlinedButton(onClick = { onChange(value + 1) }) { Text("+") }
    }
}
