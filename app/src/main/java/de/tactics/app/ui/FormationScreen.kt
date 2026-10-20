@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)

package de.tactics.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.Exporter
import de.tactics.app.MainViewModel
import de.tactics.app.data.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FormationScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val formations by vm.formations.collectAsStateWithLifecycle()
    val allPlayers by vm.players.collectAsStateWithLifecycle()
    val ownPlayers by vm.ownPlayers.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val defs by vm.attributeDefs.collectAsStateWithLifecycle()
    val links by vm.attributeLinks.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    val availabilities by vm.availabilities.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf(-1L) }
    var phaseIdx by rememberSaveable { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var showMatchup by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var arrowMode by remember { mutableStateOf(false) }
    var confirmDelFormation by remember { mutableStateOf(false) }
    var pickIndex by remember { mutableIntStateOf(-1) }

    val selected = formations.firstOrNull { it.id == selectedId } ?: formations.firstOrNull()
    val phases = remember(selected?.id, selected?.phasesJson) {
        selected?.let { decodePhases(it.phasesJson) } ?: emptyList()
    }
    val idx = phaseIdx.coerceIn(0, (phases.size - 1).coerceAtLeast(0))

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            formations.forEach { f ->
                FilterChip(
                    selected = f.id == selected?.id,
                    onClick = { selectedId = f.id; phaseIdx = 0 },
                    label = { Text(f.name) }
                )
            }
            AssistChip(onClick = { showNew = true }, label = { Text("+ Neue Aufstellung") })
        }

        if (selected == null || phases.isEmpty()) {
            Text("Lege eine Aufstellung an und wähle dein Team und den Gegner aus der Datenbank. Danach tippst du auf eine Figur, um einen Spieler zuzuweisen, und verschiebst sie per Finger. Mit „Pfeile“ zeichnest du Laufwege.")
        } else {
            val ownClub = clubs.firstOrNull { it.id == selected.ownClubId }
            val opponentClub = clubs.firstOrNull { it.id == selected.opponentClubId }
            val squad = if (ownClub != null) allPlayers.filter { it.clubId == ownClub.id } else ownPlayers
            val oppPlayers = if (opponentClub != null) allPlayers.filter { it.clubId == opponentClub.id } else emptyList()
            val byId = allPlayers.associateBy { it.id }

            val tokens = phases[idx].tokens
            val arrows = phases[idx].arrows
            val assignedOwn = tokens.filter { !it.opponent && it.playerId >= 0 }.map { it.playerId }.toSet()
            val assignedOpp = tokens.filter { it.opponent && it.playerId >= 0 }.map { it.playerId }.toSet()
            val bench = squad.filter { it.id !in assignedOwn }

            fun unavailable(p: Player): Boolean = activeAvailability(availabilities, p.id) != null
            fun availText(p: Player): String? = activeAvailability(availabilities, p.id)?.let { availLabel(it.type) }

            fun savePhase(t: List<Token>, a: List<Scribble>) {
                val updated = phases.toMutableList().also { it[idx] = it[idx].copy(tokens = t, arrows = a) }
                vm.saveFormation(selected.copy(phasesJson = encodePhases(updated)))
            }

            fun autoFill(opponent: Boolean, pool: List<Player>, assigned: Set<Long>) {
                val free = pool.filter { it.id !in assigned && !unavailable(it) }.sortedBy { it.number }.toMutableList()
                var first = true
                val updated = tokens.map { t ->
                    if (t.opponent != opponent || t.playerId >= 0 || free.isEmpty()) {
                        t
                    } else {
                        val gk = free.indexOfFirst { p ->
                            val pos = p.position.lowercase()
                            pos.startsWith("tw") || pos.startsWith("gk") || pos.contains("tor")
                        }
                        val pick = if (first && gk >= 0) free.removeAt(gk) else free.removeAt(0)
                        first = false
                        t.copy(playerId = pick.id)
                    }
                }
                savePhase(updated, arrows)
            }

            val header: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (ownClub?.name ?: "Eigenes Team") + "  vs.  " + (opponentClub?.name ?: "Gegner offen"),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (opponentClub != null && opponentClub.system.isNotBlank()) {
                            Text("Gegner-System: ${opponentClub.system}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = { showMatchup = true }) { Text("Teams") }
                    Button(enabled = opponentClub != null, onClick = { showInfo = true }) { Text("Gegner-Info") }
                }
            }

            val phaseChips: @Composable () -> Unit = {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    phases.forEachIndexed { i, p ->
                        FilterChip(selected = i == idx, onClick = { phaseIdx = i }, label = { Text(p.name) })
                    }
                }
            }

            val tools: @Composable () -> Unit = {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(selected = !arrowMode, onClick = { arrowMode = false }, label = { Text("Verschieben") })
                    FilterChip(selected = arrowMode, onClick = { arrowMode = true }, label = { Text("Pfeile") })
                    OutlinedButton(
                        enabled = arrows.isNotEmpty(),
                        onClick = { savePhase(tokens, arrows.dropLast(1)) }
                    ) { Text("Pfeil zurück") }
                    OutlinedButton(
                        enabled = arrows.isNotEmpty(),
                        onClick = { savePhase(tokens, emptyList()) }
                    ) { Text("Pfeile löschen") }
                }
            }

            val benchText: @Composable () -> Unit = {
                Text(
                    if (squad.isEmpty()) "Dein Team hat noch keine Spieler – lege sie im Tab „Datenbank“ an."
                    else if (bench.isEmpty()) "Alle Spieler sind aufgestellt."
                    else "Bank: " + bench.joinToString(", ") {
                        "${it.number} ${it.name}" + (availText(it)?.let { s -> " ($s)" } ?: "")
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val buttons: @Composable () -> Unit = {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = squad.isNotEmpty(),
                        onClick = { autoFill(false, squad, assignedOwn) }
                    ) { Text("Kader zuweisen") }
                    OutlinedButton(
                        enabled = oppPlayers.isNotEmpty(),
                        onClick = { autoFill(true, oppPlayers, assignedOpp) }
                    ) { Text("Gegner zuweisen") }
                    OutlinedButton(onClick = { savePhase(tokens.map { it.copy(playerId = -1L) }, arrows) }) {
                        Text("Spieler leeren")
                    }
                    Button(onClick = {
                        val copy = Phase("Phase ${phases.size + 1}", tokens, arrows)
                        vm.saveFormation(selected.copy(phasesJson = encodePhases(phases + copy)))
                        phaseIdx = phases.size
                    }) { Text("Phase +") }
                    OutlinedButton(
                        enabled = phases.size > 1,
                        onClick = {
                            val updated = phases.toMutableList().also { it.removeAt(idx) }
                            vm.saveFormation(selected.copy(phasesJson = encodePhases(updated)))
                            phaseIdx = 0
                        }
                    ) { Text("Phase löschen") }
                    OutlinedButton(onClick = {
                        Exporter.attempt(ctx) {
                            val sub = (ownClub?.name ?: "Eigenes Team") + " vs. " + (opponentClub?.name ?: "Gegner")
                            val f = Exporter.formationPdf(ctx, selected.name, sub, phases, byId)
                            Exporter.share(ctx, f, "application/pdf", "Aufstellung teilen")
                        }
                    }) { Text("PDF") }
                    OutlinedButton(onClick = {
                        Exporter.attempt(ctx) {
                            val f = Exporter.formationPng(ctx, selected.name, phases[idx], byId)
                            Exporter.share(ctx, f, "image/png", "Aufstellung teilen")
                        }
                    }) { Text("Bild") }
                    OutlinedButton(onClick = { confirmDelFormation = true }) {
                        Text("Aufstellung löschen")
                    }
                }
            }

            val pitch: @Composable (Modifier) -> Unit = { m ->
                PitchBoard(
                    tokens = tokens,
                    arrows = arrows,
                    players = allPlayers,
                    arrowMode = arrowMode,
                    onChange = { savePhase(it, arrows) },
                    onArrowsChange = { savePhase(tokens, it) },
                    onTokenTap = { pickIndex = it },
                    modifier = m
                )
            }

            if (wide) {
                // Tablet bzw. Querformat: Feld links, Bedienung rechts
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1.6f).fillMaxHeight().padding(bottom = 8.dp)) { pitch(Modifier.fillMaxSize()) }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        header()
                        phaseChips()
                        tools()
                        benchText()
                        buttons()
                    }
                }
            } else {
                header()
                phaseChips()
                tools()
                pitch(Modifier.fillMaxWidth().weight(1f))
                benchText()
                buttons()
            }

            if (confirmDelFormation) {
                ConfirmDialog(
                    title = "Aufstellung löschen?",
                    text = "„${selected.name}“ mit allen Phasen und Pfeilen wird gelöscht.",
                    onConfirm = {
                        vm.deleteFormation(selected)
                        selectedId = -1L
                        phaseIdx = 0
                        confirmDelFormation = false
                    },
                    onDismiss = { confirmDelFormation = false }
                )
            }

            if (pickIndex in tokens.indices) {
                val token = tokens[pickIndex]
                TokenDialog(
                    token = token,
                    players = if (token.opponent) oppPlayers else squad,
                    assigned = if (token.opponent) assignedOpp else assignedOwn,
                    availText = { p -> availText(p) },
                    onDismiss = { pickIndex = -1 },
                    onPick = { pid ->
                        val at = pickIndex
                        val side = tokens[at].opponent
                        savePhase(
                            tokens.mapIndexed { i, t ->
                                when {
                                    i == at -> t.copy(playerId = pid)
                                    t.opponent == side && pid >= 0 && t.playerId == pid -> t.copy(playerId = -1L)
                                    else -> t
                                }
                            },
                            arrows
                        )
                        pickIndex = -1
                    },
                    onLabel = { text ->
                        val at = pickIndex
                        savePhase(tokens.mapIndexed { i, t -> if (i == at) t.copy(label = text, playerId = -1L) else t }, arrows)
                        pickIndex = -1
                    }
                )
            }

            if (showMatchup) {
                MatchupDialog(
                    clubs = clubs,
                    ownClubId = selected.ownClubId,
                    opponentClubId = selected.opponentClubId,
                    onDismiss = { showMatchup = false },
                    onSave = { ownId, oppId, applySystem ->
                        var updatedPhases = phases
                        val oppNew = clubs.firstOrNull { it.id == oppId }
                        if (applySystem && oppNew != null && oppNew.system.isNotBlank()) {
                            val fresh = buildTokens(oppNew.system, true)
                            updatedPhases = phases.map { ph ->
                                ph.copy(tokens = ph.tokens.filter { !it.opponent } + fresh)
                            }
                        }
                        vm.saveFormation(
                            selected.copy(
                                ownClubId = ownId,
                                opponentClubId = oppId,
                                phasesJson = encodePhases(updatedPhases)
                            )
                        )
                        showMatchup = false
                    }
                )
            }

            if (showInfo && opponentClub != null) {
                val featured = if (assignedOpp.isNotEmpty()) oppPlayers.filter { it.id in assignedOpp } else oppPlayers
                val report = buildOpponentReport(opponentClub, featured, defs, links, analyses)
                AlertDialog(
                    onDismissRequest = { showInfo = false },
                    title = { Text("Gegner-Info") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) { Text(report) }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.saveBriefing(Briefing(title = "Gegner: ${opponentClub.name}", text = report))
                            showInfo = false
                        }) { Text("Als Briefing speichern") }
                    },
                    dismissButton = { TextButton(onClick = { showInfo = false }) { Text("Schließen") } }
                )
            }
        }
    }

    if (showNew) {
        NewFormationDialog(
            clubs = clubs,
            onDismiss = { showNew = false },
            onCreate = { name, own, opp, ownId, oppId ->
                vm.createFormation(name, own, opp, ownId, oppId) { id -> selectedId = id; phaseIdx = 0 }
                showNew = false
            }
        )
    }
}

@Composable
private fun MatchupDialog(
    clubs: List<Club>,
    ownClubId: Long,
    opponentClubId: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Boolean) -> Unit
) {
    var ownId by remember { mutableStateOf(ownClubId) }
    var oppId by remember { mutableStateOf(opponentClubId) }
    var applySystem by remember { mutableStateOf(false) }
    val oppClub = clubs.firstOrNull { it.id == oppId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teams wählen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Eigenes Team", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clubs.filter { it.isOwn }.forEach { c ->
                        FilterChip(selected = ownId == c.id, onClick = { ownId = c.id }, label = { Text(c.name) })
                    }
                }
                Text("Gegner", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = oppId == 0L, onClick = { oppId = 0L }, label = { Text("offen") })
                    clubs.filter { !it.isOwn }.forEach { c ->
                        FilterChip(selected = oppId == c.id, onClick = { oppId = c.id }, label = { Text(c.name) })
                    }
                }
                if (clubs.none { !it.isOwn }) {
                    Text("Noch keine Gegner in der Datenbank. Lege sie im Tab „Datenbank“ unter „Vereine“ an.", style = MaterialTheme.typography.bodySmall)
                }
                if (oppClub != null && oppClub.system.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = applySystem,
                            onClick = { applySystem = !applySystem },
                            label = { Text("System ${oppClub.system} übernehmen") }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ownId, oppId, applySystem) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
fun TokenDialog(
    token: Token,
    players: List<Player>,
    assigned: Set<Long>,
    availText: (Player) -> String? = { null },
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onLabel: (String) -> Unit
) {
    var text by remember { mutableStateOf(token.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (token.opponent) "Gegner-Position" else "Spieler wählen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (players.isEmpty()) {
                    Text(
                        if (token.opponent) "Für diesen Gegner sind keine Spieler gespeichert. Du kannst unten eine freie Beschriftung eingeben."
                        else "Lege zuerst Spieler im Tab „Datenbank“ an."
                    )
                }
                TextButton(onClick = { onPick(-1L) }) { Text("– Platz frei lassen –") }
                players.forEach { p ->
                    val used = p.id in assigned && p.id != token.playerId
                    val status = availText(p)
                    Text(
                        "${p.number}  ${p.name}" +
                            (if (p.position.isNotBlank()) "  (${p.position})" else "") +
                            (if (status != null) "  – $status" else "") +
                            (if (used) "  – bereits aufgestellt" else ""),
                        color = if (status != null) NegativeColor else Color.Unspecified,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p.id) }
                            .padding(vertical = 12.dp)
                    )
                }
                OutlinedTextField(
                    text, { text = it.take(12) },
                    label = { Text("Freie Beschriftung, z. B. 9") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onLabel(text) }) { Text("Beschriftung speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun NewFormationDialog(
    clubs: List<Club>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var own by remember { mutableStateOf("4-2-3-1") }
    var opp by remember { mutableStateOf("4-4-2") }
    var ownId by remember { mutableStateOf(clubs.firstOrNull { it.isOwn }?.id ?: 0L) }
    var oppId by remember { mutableStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Aufstellung") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (z. B. Spiel gegen TSV)") }, singleLine = true)
                Text("Eigenes Team", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clubs.filter { it.isOwn }.forEach { c ->
                        FilterChip(
                            selected = ownId == c.id,
                            onClick = { ownId = c.id; if (c.system.isNotBlank()) own = c.system },
                            label = { Text(c.name) }
                        )
                    }
                }
                OutlinedTextField(own, { own = it }, label = { Text("Eigenes System") }, singleLine = true)
                Text("Gegner aus der Datenbank", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = oppId == 0L, onClick = { oppId = 0L }, label = { Text("offen") })
                    clubs.filter { !it.isOwn }.forEach { c ->
                        FilterChip(
                            selected = oppId == c.id,
                            onClick = { oppId = c.id; if (c.system.isNotBlank()) opp = c.system },
                            label = { Text(c.name) }
                        )
                    }
                }
                OutlinedTextField(opp, { opp = it }, label = { Text("Gegnerisches System") }, singleLine = true)
                Text("Systeme als Zahlen, die 10 ergeben (ohne Torwart), z. B. 4-3-3 oder 3-5-2.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), own, opp, ownId, oppId) }
            ) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
