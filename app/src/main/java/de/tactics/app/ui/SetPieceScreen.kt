@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

private val TYPES = listOf("Ecke", "Freistoß", "Einwurf", "Elfmeter", "Anstoß")
private val DEFAULT_ROLES = listOf("Schütze", "Zielspieler", "Blocker", "Sperrspieler", "Absicherung", "Läufer")

@Composable
fun SetPieceScreen(vm: MainViewModel) {
    val sets by vm.setPieces.collectAsStateWithLifecycle()
    val players by vm.ownPlayers.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SetPiece?>(null) }
    var diagramFor by remember { mutableStateOf<SetPiece?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteSet by remember { mutableStateOf<SetPiece?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (sets.isEmpty()) {
            Text(
                "Noch keine Standards.\nMit + einen Standard anlegen, Rollen zuweisen und auf dem Spielfeld einzeichnen.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(sets, key = { it.id }) { s ->
                val roles = decodeRoles(s.rolesJson).filter { it.player.isNotBlank() }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { editing = s }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(s.type, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { deleteSet = s }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen")
                            }
                        }
                        roles.forEach { Text("${it.role}: ${it.player}") }
                        if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodySmall)
                        val d = if (s.diagramJson.isBlank()) null else decodePhase(s.diagramJson)
                        OutlinedButton(onClick = { diagramFor = s }) {
                            Text(if (d == null || (d.tokens.isEmpty() && d.arrows.isEmpty())) "Auf Spielfeld einzeichnen" else "Spielfeld bearbeiten")
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Standard hinzufügen") }
    }

    if (creating || editing != null) {
        SetPieceDialog(
            initial = editing,
            players = players,
            onDismiss = { creating = false; editing = null },
            onSave = { vm.saveSetPiece(it); creating = false; editing = null }
        )
    }
    deleteSet?.let { sp ->
        ConfirmDialog(
            title = "Standard löschen?",
            text = "„${sp.name}“ wird gelöscht.",
            onConfirm = { vm.deleteSetPiece(sp); deleteSet = null },
            onDismiss = { deleteSet = null }
        )
    }
    diagramFor?.let { sp ->
        SetPieceDiagramDialog(
            sp = sp,
            players = players,
            onDismiss = { diagramFor = null },
            onSave = { json -> vm.saveSetPiece(sp.copy(diagramJson = json)); diagramFor = null }
        )
    }
}

/** Vollbild-Dialog: Standard auf dem Spielfeld mit Figuren und Laufwegen. */
@Composable
private fun SetPieceDiagramDialog(
    sp: SetPiece,
    players: List<Player>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var phase by remember { mutableStateOf(if (sp.diagramJson.isBlank()) Phase(sp.name, emptyList()) else decodePhase(sp.diagramJson)) }
    var arrowMode by remember { mutableStateOf(false) }
    var pickIndex by remember { mutableStateOf(-1) }

    fun addToken(opponent: Boolean) {
        val n = phase.tokens.count { it.opponent == opponent } + 1
        val k = phase.tokens.size
        val t = Token(
            label = n.toString(),
            x = 0.5f + ((k % 5) - 2) * 0.08f,
            y = if (opponent) 0.22f else 0.32f,
            opponent = opponent
        )
        phase = phase.copy(tokens = phase.tokens + t)
    }

    fun placeRoles() {
        val roles = decodeRoles(sp.rolesJson).filter { it.player.isNotBlank() }
        val already = phase.tokens.filter { !it.opponent }.map { it.playerId }.toSet()
        val fresh = mutableListOf<Token>()
        roles.forEachIndexed { i, r ->
            val pl = players.firstOrNull { it.name == r.player }
            if (pl != null && pl.id !in already) {
                val x = 0.18f + (i % 6) * 0.13f
                fresh += Token(label = pl.number.toString(), x = x, y = 0.30f + (i % 2) * 0.08f, opponent = false, playerId = pl.id)
            }
        }
        phase = phase.copy(tokens = phase.tokens + fresh)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sp.name + " – Spielfeld", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(selected = !arrowMode, onClick = { arrowMode = false }, label = { Text("Verschieben") })
                    FilterChip(selected = arrowMode, onClick = { arrowMode = true }, label = { Text("Pfeile") })
                    OutlinedButton(onClick = { addToken(false) }) { Text("+ Eigener") }
                    OutlinedButton(onClick = { addToken(true) }) { Text("+ Gegner") }
                    OutlinedButton(onClick = { placeRoles() }) { Text("Rollen platzieren") }
                    OutlinedButton(
                        enabled = phase.arrows.isNotEmpty(),
                        onClick = { phase = phase.copy(arrows = phase.arrows.dropLast(1)) }
                    ) { Text("Pfeil zurück") }
                    OutlinedButton(onClick = { phase = phase.copy(tokens = emptyList(), arrows = emptyList()) }) { Text("Alles löschen") }
                }
                PitchBoard(
                    tokens = phase.tokens,
                    arrows = phase.arrows,
                    players = players,
                    arrowMode = arrowMode,
                    onChange = { phase = phase.copy(tokens = it) },
                    onArrowsChange = { phase = phase.copy(arrows = it) },
                    onTokenTap = { pickIndex = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(onClick = { onSave(encodePhase(phase)) }) { Text("Speichern") }
                }
            }
        }
    }

    if (pickIndex in phase.tokens.indices) {
        val token = phase.tokens[pickIndex]
        TokenDialog(
            token = token,
            players = if (token.opponent) emptyList() else players,
            assigned = phase.tokens.filter { !it.opponent && it.playerId >= 0 }.map { it.playerId }.toSet(),
            onDismiss = { pickIndex = -1 },
            onPick = { pid ->
                val at = pickIndex
                phase = phase.copy(
                    tokens = phase.tokens.mapIndexed { i, t ->
                        when {
                            i == at -> t.copy(playerId = pid)
                            !t.opponent && pid >= 0 && t.playerId == pid -> t.copy(playerId = -1L)
                            else -> t
                        }
                    }
                )
                pickIndex = -1
            },
            onLabel = { text ->
                val at = pickIndex
                phase = phase.copy(tokens = phase.tokens.mapIndexed { i, t -> if (i == at) t.copy(label = text, playerId = -1L) else t })
                pickIndex = -1
            }
        )
    }
}

@Composable
private fun SetPieceDialog(
    initial: SetPiece?,
    players: List<Player>,
    onDismiss: () -> Unit,
    onSave: (SetPiece) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: TYPES[0]) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    val roles = remember {
        mutableStateListOf<RoleAssignment>().apply {
            addAll(
                if (initial != null && decodeRoles(initial.rolesJson).isNotEmpty()) decodeRoles(initial.rolesJson)
                else DEFAULT_ROLES.map { RoleAssignment(it, "") }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Standard" else "Standard bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (z. B. Ecke links kurz)") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TYPES.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
                roles.forEachIndexed { i, r ->
                    RoleRow(r.role, r.player, players) { roles[i] = r.copy(player = it) }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notizen / Laufwege") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = initial ?: SetPiece(name = "", type = type, rolesJson = "")
                    onSave(base.copy(name = name.trim(), type = type, rolesJson = encodeRoles(roles.toList()), notes = notes.trim()))
                }
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun RoleRow(role: String, selected: String, players: List<Player>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(role, Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(selected.ifBlank { "wählen" }) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("– niemand –") }, onClick = { onSelect(""); open = false })
                players.forEach { p ->
                    DropdownMenuItem(
                        text = { Text("${p.number} ${p.name}") },
                        onClick = { onSelect(p.name); open = false }
                    )
                }
            }
        }
    }
}
