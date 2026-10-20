@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.*
import kotlinx.coroutines.delay

/**
 * Spieltag-Modus: Uhr, große Tasten für Tor, Gegentor, Karten, Wechsel und Verletzung.
 * Beim Beenden berechnet die App Ergebnis, Minuten, Tore, Vorlagen und Karten selbst.
 */
@Composable
fun LiveMatchScreen(vm: MainViewModel, matchId: Long, onBack: () -> Unit) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val ownPlayers by vm.ownPlayers.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val allRows by vm.matchPlayers.collectAsStateWithLifecycle()
    val events by vm.matchEvents.collectAsStateWithLifecycle()
    val match = matches.firstOrNull { it.id == matchId }

    if (match == null) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Spiel nicht gefunden.")
        }
        return
    }

    LaunchedEffect(matchId) {
        vm.liveEnter(matchId)
        vm.ensureLineup(match)
    }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick = System.currentTimeMillis()
        }
    }
    val running = vm.liveRunning
    val ms = remember(tick, running) { vm.liveElapsedMs() }
    val minute = (ms / 60_000L).toInt() + 1
    val clock = "%02d:%02d".format(ms / 60_000L, (ms / 1000L) % 60)

    val squad = (if (match.ownClubId > 0) players.filter { it.clubId == match.ownClubId } else ownPlayers).sortedBy { it.number }
    val rowsNow = allRows.filter { it.matchId == match.id }
    val evs = events.filter { it.matchId == match.id }
    val onField = mutableSetOf<Long>()
    rowsNow.filter { it.started }.forEach { onField.add(it.playerId) }
    evs.sortedBy { it.minute }.forEach { e ->
        if (e.type == "sub") {
            onField.remove(e.playerId)
            if (e.otherPlayerId > 0) onField.add(e.otherPlayerId)
        } else if (e.type == "red") {
            onField.remove(e.playerId)
        }
    }
    val wasOff = evs.filter { it.type == "sub" || it.type == "red" }.map { it.playerId }.toSet()
    val goalsFor = evs.count { it.type == "goal" }
    val goalsAgainst = evs.count { it.type == "conceded" }
    val oppName = clubs.firstOrNull { it.id == match.opponentClubId }?.name ?: match.opponentName.ifBlank { "Gegner" }
    val ownName = clubs.firstOrNull { it.id == match.ownClubId }?.name ?: "Wir"

    var pendingType by remember { mutableStateOf<String?>(null) }
    var pendingMinute by remember { mutableIntStateOf(1) }
    var firstPick by remember { mutableLongStateOf(0L) }
    var step by remember { mutableIntStateOf(0) }
    var finishing by remember { mutableStateOf(false) }

    fun label(id: Long): String =
        players.firstOrNull { it.id == id }?.let { "${it.number} ${it.name}" } ?: "?"

    fun describe(e: MatchEvent): String = when (e.type) {
        "goal" -> "Tor: " + label(e.playerId) + (if (e.otherPlayerId > 0) " (Vorlage " + label(e.otherPlayerId) + ")" else "")
        "conceded" -> "Gegentor"
        "yellow" -> "Gelbe Karte: " + label(e.playerId)
        "red" -> "Rote Karte: " + label(e.playerId)
        "sub" -> "Wechsel: " + label(e.playerId) + " → " + label(e.otherPlayerId)
        "injury" -> "Verletzt: " + label(e.playerId)
        else -> e.type
    }

    fun begin(type: String) {
        if (type == "conceded") {
            vm.addMatchEvent(MatchEvent(matchId = match.id, minute = minute, type = type))
        } else {
            pendingType = type
            pendingMinute = minute
            step = 1
            firstPick = 0L
        }
    }

    fun record(p: Long, o: Long) {
        val type = pendingType ?: return
        vm.addMatchEvent(MatchEvent(matchId = match.id, minute = pendingMinute, type = type, playerId = p, otherPlayerId = o))
        pendingType = null
        step = 0
    }

    val fieldPlayers = squad.filter { it.id in onField }.ifEmpty { squad }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { finishing = true }) { Text("Spiel beenden") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$ownName  $goalsFor : $goalsAgainst  $oppName", style = MaterialTheme.typography.titleLarge)
                Text(clock, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text("${minute}. Minute", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.liveAdjustMinutes(-1) }) { Text("−1") }
                    Button(onClick = { vm.liveToggle() }) { Text(if (running) "Pause" else if (ms == 0L) "Anpfiff" else "Weiter") }
                    OutlinedButton(onClick = { vm.liveAdjustMinutes(1) }) { Text("+1") }
                    OutlinedButton(onClick = { vm.liveAdjustMinutes(45 - (ms / 60_000L).toInt()) }) { Text("→ 45'") }
                }
            }
        }

        if (rowsNow.none { it.started }) {
            Text(
                "Noch keine Startelf. Lege sie im Spiel unter „Einsätze“ fest oder verknüpfe eine Aufstellung, dann übernimmt der Spieltag-Modus sie.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { begin("goal") }, modifier = Modifier.weight(1f).height(64.dp)) { Text("⚽ Tor") }
            OutlinedButton(onClick = { begin("conceded") }, modifier = Modifier.weight(1f).height(64.dp)) { Text("Gegentor") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { begin("yellow") }, modifier = Modifier.weight(1f).height(56.dp)) { Text("🟨 Gelb") }
            OutlinedButton(onClick = { begin("red") }, modifier = Modifier.weight(1f).height(56.dp)) { Text("🟥 Rot") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { begin("sub") }, modifier = Modifier.weight(1f).height(56.dp)) { Text("🔁 Wechsel") }
            OutlinedButton(onClick = { begin("injury") }, modifier = Modifier.weight(1f).height(56.dp)) { Text("🩹 Verletzt") }
        }

        Text("Verlauf", style = MaterialTheme.typography.titleMedium)
        if (evs.isEmpty()) Text("Noch keine Ereignisse.", style = MaterialTheme.typography.bodySmall)
        evs.sortedWith(compareByDescending<MatchEvent> { it.minute }.thenByDescending { it.id }).forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${e.minute}'", Modifier.width(44.dp), fontWeight = FontWeight.Bold)
                Text(describe(e), Modifier.weight(1f))
                TextButton(onClick = { vm.deleteMatchEvent(e) }) { Text("✕") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    // Spielerauswahl für das gerade begonnene Ereignis
    val type = pendingType
    if (type != null) {
        val title: String
        val candidates: List<Player>
        var noneLabel: String? = null
        when {
            type == "goal" && step == 1 -> { title = "Torschütze"; candidates = fieldPlayers }
            type == "goal" -> {
                title = "Vorlage"
                candidates = fieldPlayers.filter { it.id != firstPick }
                noneLabel = "Keine Vorlage"
            }
            type == "sub" && step == 1 -> { title = "Wer geht raus?"; candidates = fieldPlayers }
            type == "sub" -> {
                title = "Wer kommt rein?"
                candidates = squad.filter { it.id !in onField && it.id !in wasOff }
            }
            type == "yellow" -> { title = "Gelbe Karte für"; candidates = fieldPlayers }
            type == "red" -> { title = "Rote Karte für"; candidates = fieldPlayers }
            else -> { title = "Wer ist verletzt?"; candidates = fieldPlayers }
        }
        PlayerPickDialog(
            title = "$title (${pendingMinute}.)",
            candidates = candidates,
            noneLabel = noneLabel,
            onDismiss = { pendingType = null; step = 0 },
            onPick = { id ->
                if ((type == "goal" || type == "sub") && step == 1) {
                    firstPick = id
                    step = 2
                } else if (type == "goal" || type == "sub") {
                    record(firstPick, id)
                } else {
                    record(id, 0L)
                }
            }
        )
    }

    if (finishing) {
        var endText by remember { mutableStateOf(maxOf(90, minute - 1).toString()) }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Spiel beenden?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ergebnis $goalsFor : $goalsAgainst. Minuten, Tore, Vorlagen, Karten und Verletzungen werden für alle Spieler berechnet.")
                    OutlinedTextField(
                        endText, { endText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Spielende in Minute") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    finishing = false
                    vm.finishMatch(match, endText.toIntOrNull() ?: 90) { onBack() }
                }) { Text("Beenden") }
            },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun PlayerPickDialog(
    title: String,
    candidates: List<Player>,
    noneLabel: String?,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (noneLabel != null) {
                    Text(
                        noneLabel,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(0L) }.padding(vertical = 14.dp)
                    )
                }
                if (candidates.isEmpty()) Text("Keine Spieler verfügbar.")
                candidates.forEach { p ->
                    Text(
                        "${p.number}  ${p.name}",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(vertical = 14.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
