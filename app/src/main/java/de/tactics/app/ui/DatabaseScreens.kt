@file:OptIn(ExperimentalMaterial3Api::class)

package de.tactics.app.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tactics.app.MainViewModel
import de.tactics.app.data.*

/** Tab "Datenbank": Spieler, Vereine (eigene und Gegner) und der Attribut-Katalog. */
@Composable
fun DatabaseHost(vm: MainViewModel, onOpenPlayer: (Long) -> Unit, onOpenClub: (Long) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Spieler") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Vereine") })
            FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Attribute") })
        }
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> PlayersScreen(vm, onOpenPlayer)
                1 -> ClubsScreen(vm, onOpenClub)
                else -> AttributesScreen(vm)
            }
        }
    }
}

// ------------------------------------------------------------------ Spieler
@Composable
fun PlayersScreen(vm: MainViewModel, onOpenPlayer: (Long) -> Unit) {
    val players by vm.players.collectAsStateWithLifecycle()
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val availabilities by vm.availabilities.collectAsStateWithLifecycle()
    // -2 = eigene Vereine, -1 = alle, 0 = vereinslos, >0 = bestimmter Verein
    var filter by rememberSaveable { mutableStateOf(-2L) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Player?>(null) }

    val ownIds = clubs.filter { it.isOwn }.map { it.id }.toSet()
    val shown = players.filter {
        when (filter) {
            -2L -> it.clubId in ownIds
            -1L -> true
            0L -> it.clubId == 0L
            else -> it.clubId == filter
        }
    }
    val clubNames = clubs.associate { it.id to it.name }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = filter == -2L, onClick = { filter = -2L }, label = { Text("Eigene") })
                FilterChip(selected = filter == -1L, onClick = { filter = -1L }, label = { Text("Alle") })
                clubs.forEach { c ->
                    FilterChip(selected = filter == c.id, onClick = { filter = c.id }, label = { Text(c.name) })
                }
                FilterChip(selected = filter == 0L, onClick = { filter = 0L }, label = { Text("Vereinslos") })
            }
            Box(Modifier.weight(1f)) {
                if (shown.isEmpty()) {
                    Text(
                        "Keine Spieler in dieser Ansicht.\nMit + einen Spieler hinzufügen.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(shown, key = { it.id }) { p ->
                        val avg = p.averageRating()
                        val info = listOfNotNull(
                            p.position.ifBlank { null },
                            if (filter < 1) (clubNames[p.clubId] ?: "Vereinslos") else null,
                            avg?.let { "★ " + "%.1f".format(it) },
                            activeAvailability(availabilities, p.id)?.let { availLabel(it.type) }
                        ).joinToString("  ·  ")
                        ListItem(
                            modifier = Modifier.clickable { onOpenPlayer(p.id) },
                            leadingContent = { Text("${p.number}", style = MaterialTheme.typography.titleLarge) },
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(if (info.isBlank()) "Profil öffnen" else info) },
                            trailingContent = {
                                IconButton(onClick = { deleting = p }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Spieler hinzufügen") }
    }

    if (creating) {
        NewPlayerDialog(
            clubs = clubs,
            defaultClubId = if (filter > 0) filter else clubs.firstOrNull { it.isOwn }?.id ?: 0L,
            onDismiss = { creating = false },
            onSave = { vm.savePlayer(it); creating = false }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Spieler löschen?") },
            text = { Text("${p.name} wird samt Attributen, Analysen und Transfers gelöscht.") },
            confirmButton = { TextButton(onClick = { vm.deletePlayer(p); deleting = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
fun NewPlayerDialog(clubs: List<Club>, defaultClubId: Long, onDismiss: () -> Unit, onSave: (Player) -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var clubId by remember { mutableStateOf(defaultClubId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spieler hinzufügen") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    number, { number = it.filter(Char::isDigit).take(2) },
                    label = { Text("Rückennummer") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(position, { position = it }, label = { Text("Position (z. B. IV, ZM)") }, singleLine = true)
                Text("Verein", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clubs.forEach { c ->
                        FilterChip(selected = clubId == c.id, onClick = { clubId = c.id }, label = { Text(c.name) })
                    }
                    FilterChip(selected = clubId == 0L, onClick = { clubId = 0L }, label = { Text("Vereinslos") })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(Player(name = name.trim(), number = number.toIntOrNull() ?: 0, position = position.trim(), clubId = clubId))
                }
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

// ------------------------------------------------------------------ Vereine
@Composable
fun ClubsScreen(vm: MainViewModel, onOpenClub: (Long) -> Unit) {
    val clubs by vm.clubs.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val analyses by vm.analyses.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(clubs, key = { it.id }) { c ->
                val count = players.count { it.clubId == c.id }
                val notes = analyses.count { it.targetType == "club" && it.targetId == c.id }
                val info = listOfNotNull(
                    if (c.isOwn) "Eigener Verein" else "Gegner",
                    c.system.ifBlank { null },
                    "$count Spieler",
                    if (notes > 0) "$notes Analysen" else null
                ).joinToString("  ·  ")
                ListItem(
                    modifier = Modifier.clickable { onOpenClub(c.id) },
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(info) }
                )
                Divider()
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Verein hinzufügen") }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var own by remember { mutableStateOf(false) }
        var system by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Neuer Verein") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !own, onClick = { own = false }, label = { Text("Gegner") })
                        FilterChip(selected = own, onClick = { own = true }, label = { Text("Eigener Verein") })
                    }
                    OutlinedTextField(system, { system = it }, label = { Text("Übliches System (z. B. 4-4-2)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.saveClub(Club(name = name.trim(), isOwn = own, system = system.trim()))
                        creating = false
                    }
                ) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Abbrechen") } }
        )
    }
}

// ------------------------------------------------------------------ Attribut-Katalog
@Composable
fun AttributesScreen(vm: MainViewModel) {
    val defs by vm.attributeDefs.collectAsStateWithLifecycle()
    val links by vm.attributeLinks.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AttributeDef?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (defs.isEmpty()) {
            Text(
                "Noch keine Attribute.\nMit + frei positive oder negative Attribute anlegen, z. B. „Kopfballstark“ oder „Schwacher linker Fuß“. Zugeordnet werden sie am Spieler oder am Verein.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(defs.sortedWith(compareByDescending<AttributeDef> { it.positive }.thenBy { it.name.lowercase() }), key = { it.id }) { d ->
                val used = links.count { it.attributeId == d.id }
                ListItem(
                    modifier = Modifier.clickable { editing = d },
                    leadingContent = {
                        Text(
                            if (d.positive) "＋" else "−",
                            color = if (d.positive) PositiveColor else NegativeColor,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text(if (d.positive) "Positiv · $used× vergeben" else "Negativ · $used× vergeben") }
                )
                Divider()
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Attribut hinzufügen") }
    }

    if (creating || editing != null) {
        val initial = editing
        var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
        var positive by remember(initial?.id) { mutableStateOf(initial?.positive ?: true) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (initial == null) "Neues Attribut" else "Attribut bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = positive, onClick = { positive = true }, label = { Text("＋ Positiv") })
                        FilterChip(selected = !positive, onClick = { positive = false }, label = { Text("− Negativ") })
                    }
                    if (initial != null) {
                        TextButton(onClick = { vm.deleteAttributeDef(initial); editing = null }) {
                            Text("Attribut löschen (auch bei allen Spielern und Vereinen)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.saveAttributeDef((initial ?: AttributeDef(name = "", positive = positive)).copy(name = name.trim(), positive = positive))
                        creating = false; editing = null
                    }
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Abbrechen") } }
        )
    }
}
