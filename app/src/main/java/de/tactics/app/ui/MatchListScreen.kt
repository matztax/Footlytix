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

internal val COMPETITIONS = listOf("Liga", "Pokal", "Testspiel", "Turnier")

/** Tab "Spiele": Saisons, Spielliste und Statistik. */
@Composable
fun MatchesHost(vm: MainViewModel, onOpenMatch: (Long) -> Unit) {
    val seasons by vm.seasons.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(0) }
    var seasonId by rememberSaveable { mutableStateOf(-1L) }
    var newSeason by remember { mutableStateOf(false) }
    val season = seasons.firstOrNull { it.id == seasonId }
        ?: seasons.firstOrNull { it.isActive }
        ?: seasons.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            seasons.forEach { s ->
                FilterChip(
                    selected = s.id == season?.id,
                    onClick = { seasonId = s.id },
                    label = { Text(s.name) }
                )
            }
            AssistChip(onClick = { newSeason = true }, label = { Text("+ Saison") })
        }
        Row(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Spiele") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Statistik") })
        }
        Box(Modifier.weight(1f)) {
            if (season == null) {
                Text("Lege zuerst eine Saison an.", modifier = Modifier.align(Alignment.Center).padding(24.dp))
            } else if (mode == 0) {
                MatchListScreen(vm, season, onOpenMatch)
            } else {
                StatsScreen(vm, season)
            }
        }
    }

    if (newSeason) {
        NameDialog(
            title = "Neue Saison",
            initial = "Saison",
            onDismiss = { newSeason = false },
            onSave = { name -> vm.saveSeason(Season(name = name)); newSeason = false }
        )
    }
}

private fun resultText(m: Match): String = if (m.played) "${m.goalsFor}:${m.goalsAgainst}" else "geplant"

@Composable
private fun MatchListScreen(vm: MainViewModel, season: Season, onOpen: (Long) -> Unit) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val mine = matches.filter { it.seasonId == season.id }

    Box(Modifier.fillMaxSize()) {
        if (mine.isEmpty()) {
            Text(
                "Noch keine Spiele in dieser Saison.\nMit + ein Spiel anlegen.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp)) {
            items(mine, key = { it.id }) { m ->
                val opp = clubs.firstOrNull { it.id == m.opponentClubId }?.name ?: m.opponentName.ifBlank { "Gegner" }
                val resultColor = when {
                    !m.played -> MaterialTheme.colorScheme.onSurfaceVariant
                    m.goalsFor > m.goalsAgainst -> PositiveColor
                    m.goalsFor < m.goalsAgainst -> NegativeColor
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpen(m.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (m.home) "Heim" else "Auswärts") + " vs. " + opp, style = MaterialTheme.typography.titleMedium)
                            Text(formatDate(m.dateMillis) + " · " + m.competition, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(resultText(m), color = resultColor, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Spiel hinzufügen") }
    }

    if (creating) {
        NewMatchDialog(
            clubs = clubs,
            onDismiss = { creating = false },
            onCreate = { date, oppId, oppName, home, comp ->
                val own = clubs.firstOrNull { it.isOwn }?.id ?: 0L
                vm.saveMatch(
                    Match(
                        seasonId = season.id, dateMillis = date, ownClubId = own,
                        opponentClubId = oppId, opponentName = oppName, home = home, competition = comp
                    )
                ) { id -> onOpen(id) }
                creating = false
            }
        )
    }
}

@Composable
private fun NewMatchDialog(
    clubs: List<Club>,
    onDismiss: () -> Unit,
    onCreate: (Long, Long, String, Boolean, String) -> Unit
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var oppId by remember { mutableStateOf(0L) }
    var oppName by remember { mutableStateOf("") }
    var home by remember { mutableStateOf(true) }
    var comp by remember { mutableStateOf(COMPETITIONS[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Spiel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker(ctx, date) { date = it } }) { Text("Datum: " + formatDate(date)) }
                Text("Gegner", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = oppId == 0L, onClick = { oppId = 0L }, label = { Text("frei eingeben") })
                    clubs.filter { !it.isOwn }.forEach { c ->
                        FilterChip(selected = oppId == c.id, onClick = { oppId = c.id }, label = { Text(c.name) })
                    }
                }
                if (oppId == 0L) {
                    OutlinedTextField(oppName, { oppName = it }, label = { Text("Name des Gegners") }, singleLine = true)
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = oppId != 0L || oppName.isNotBlank(),
                onClick = { onCreate(date, oppId, oppName.trim(), home, comp) }
            ) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

// ------------------------------------------------------------------ Statistik
@Composable
private fun StatsScreen(vm: MainViewModel, season: Season) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    val rows by vm.matchPlayers.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val ownPlayers by vm.ownPlayers.collectAsStateWithLifecycle()

    val played = matches.filter { it.seasonId == season.id && it.played }
    val wins = played.count { it.goalsFor > it.goalsAgainst }
    val draws = played.count { it.goalsFor == it.goalsAgainst }
    val losses = played.count { it.goalsFor < it.goalsAgainst }
    val gf = played.sumOf { it.goalsFor }
    val ga = played.sumOf { it.goalsAgainst }

    val list = (ownPlayers + players.filter { p -> rows.any { it.playerId == p.id } }).distinctBy { it.id }
        .map { it to computePlayerStats(it.id, season.id, matches, rows) }
        .filter { it.second.games > 0 || it.first in ownPlayers }
        .sortedWith(compareByDescending<Pair<Player, PlayerStats>> { it.second.goals }.thenByDescending { it.second.games })

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Bilanz ${season.name}", style = MaterialTheme.typography.titleMedium)
                Text("${played.size} Spiele: $wins S · $draws U · $losses N")
                Text("Tore: $gf : $ga (Differenz ${gf - ga})")
            }
        }
        StatRow("Spieler", "Sp", "Min", "T", "V", "G", "R", header = true)
        list.forEach { (p, s) ->
            StatRow(
                "${p.number} ${p.name}",
                s.games.toString(), s.minutes.toString(), s.goals.toString(),
                s.assists.toString(), s.yellow.toString(), s.red.toString()
            )
        }
        Text(
            "Sp = Einsätze, Min = Minuten, T = Tore, V = Vorlagen, G = Gelbe, R = Rote Karten. Es zählen nur Spiele, die als „gespielt“ markiert sind.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatRow(name: String, a: String, b: String, c: String, d: String, e: String, f: String, header: Boolean = false) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), fontWeight = weight, maxLines = 1)
        listOf(a, b, c, d, e, f).forEach {
            Text(it, Modifier.width(38.dp), fontWeight = weight, maxLines = 1)
        }
    }
}
